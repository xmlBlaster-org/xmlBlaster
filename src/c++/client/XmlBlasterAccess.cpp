/*------------------------------------------------------------------------------
Name:      XmlBlasterAccess.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <client/XmlBlasterAccess.h>
#include <util/Global.h>
#include <util/lexical_cast.h>
#include <util/Timestamp.h>
#include <util/dispatch/DispatchManager.h>
#include <util/parser/ParserFactory.h>
#include <util/queue/MsgQueueEntry.h>
#include <util/queue/I_Queue.h>


namespace org { namespace xmlBlaster { namespace client {

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::dispatch;
using namespace org::xmlBlaster::util::queue;
using namespace org::xmlBlaster::util::qos::storage;
using namespace org::xmlBlaster::util::qos::address;
using namespace org::xmlBlaster::authentication;
using namespace org::xmlBlaster::client::protocol;
using namespace org::xmlBlaster::client::key;
using namespace org::xmlBlaster::client::qos;

XmlBlasterAccess::XmlBlasterAccess(Global& global)
   : ME(string("XmlBlasterAccess-UNCONNECTED")),
     global_(global), 
     globalRef_(NULL), 
     log_(global.getLog("org.xmlBlaster.client")),
     serverNodeId_("xmlBlaster"), 
     connectQos_(new ConnectQos(global)), 
     connectReturnQos_((ConnectReturnQos*)0),
     subscriptionCallbackMap_(),
     updateMutex_(),
     invocationMutex_(global.getProperty().get("xmlBlaster/invocationMutex/recursive", true)),
     postSendListener_(0)
{
   log_.call(ME, "::constructor");
   cbServer_           = NULL;
   updateClient_       = NULL;
   connection_         = NULL;
   dispatchManager_    = NULL;
   connectionProblems_ = NULL;
   instanceName_       = lexical_cast<std::string>(TimestampFactory::getInstance().getTimestamp());

   // Hack for Windows: Initialize it from main thread, using the callback thread fails undeterminable (with xerces)
   org::xmlBlaster::util::parser::ParserFactory::getFactory().initialize(global);
}

XmlBlasterAccess::XmlBlasterAccess(GlobalRef globalRef)
   : ME(string("XmlBlasterAccess-UNCONNECTED")),
     global_(*globalRef), 
     globalRef_(globalRef), 
     log_(global_.getLog("org.xmlBlaster.client")),
     serverNodeId_("xmlBlaster"), 
     connectQos_(new ConnectQos(global_)), 
     connectReturnQos_((ConnectReturnQos*)0),
     subscriptionCallbackMap_(),
     updateMutex_(),
     invocationMutex_(globalRef->getProperty().get("xmlBlaster/invocationMutex/recursive", true)),
     postSendListener_(0)
{
   log_.call(ME, "::constructor");
   cbServer_           = NULL;
   updateClient_       = NULL;
   connection_         = NULL;
   dispatchManager_    = NULL;
   connectionProblems_ = NULL;
   instanceName_       = lexical_cast<std::string>(TimestampFactory::getInstance().getTimestamp());

   // Hack for Windows: Initialize it from main thread, using the callback thread fails undeterminable (with xerces)
   org::xmlBlaster::util::parser::ParserFactory::getFactory().initialize(global_);
}

XmlBlasterAccess::~XmlBlasterAccess()
{
   if (log_.call()) log_.call(ME, "destructor");
   cleanup(true);
   dispatchManager_    = NULL;
   updateClient_       = NULL;
   connectionProblems_ = NULL;
   if (log_.trace()) log_.trace(ME, "destructor ended");
}

void XmlBlasterAccess::cleanup(bool doLock)
{
   if (log_.call()) log_.call(ME, "cleanup");
   if (doLock) {
      // synchronization
      org::xmlBlaster::util::thread::Lock lock(invocationMutex_);
      org::xmlBlaster::util::thread::Lock lock1(updateMutex_);
      subscriptionCallbackMap_.clear();
   }
   else {
      org::xmlBlaster::util::thread::Lock lock1(updateMutex_);
      subscriptionCallbackMap_.clear();
   }

   if (cbServer_) {
      CbQueueProperty prop = connectQos_->getSessionCbQueueProperty(); // Creates a default property for us if none is available
      const AddressBaseRef& addr = prop.getCurrentCallbackAddress(); // c++ may not return null
      global_.getCbServerPluginManager().releasePlugin( instanceName_, addr->getType(), addr->getVersion() );
      cbServer_ = NULL;
   }
   if (connection_) {
      if (log_.trace()) log_.trace(ME, "destructor: going to delete the connection");
      connection_->shutdown();
      delete connection_;
      connection_ = NULL;
   }
}


ConnectReturnQos XmlBlasterAccess::connect(const ConnectQos& qos, I_Callback *clientCb)
{
   ME = string("XmlBlasterAccess-") + qos.getSessionQos().getAbsoluteName();
   if (log_.call()) log_.call(ME, "::connect");
   if (log_.dump()) log_.dump(ME, string("::connect: qos: ") + qos.toXml());

   org::xmlBlaster::util::thread::Lock lock(invocationMutex_);

   cleanup(false);

   global_.setId(qos.getSessionQos().getAbsoluteName()); // global_.setId(loginName + currentTimeMillis());
   connectQos_ = new ConnectQos(qos);
   connectQos_->setInstanceId(global_.getInstanceId());

   SecurityQos securityQos = connectQos_->getSecurityQos();

   ME = string("XmlBlasterAccess-") + getId();
   string typeVersion = global_.getProperty().getStringProperty("queue/defaultPlugin", "CACHE,1.0");
   typeVersion = global_.getProperty().getStringProperty("queue/connection/defaultPlugin", "typeVersion");
   updateClient_ = clientCb;

   if (updateClient_) createDefaultCbServer();

   if (log_.trace()) log_.trace(ME, string("::connect. CbServer done"));
   // currently the simple version will do it ...
   if (!dispatchManager_) dispatchManager_ = &(global_.getDispatchManager());

   if (!connection_) {
      connection_ = dispatchManager_->getConnectionsHandler(instanceName_);
      connection_->registerPostSendListener(this);
   }

   if (connectionProblems_) {
      if (log_.trace()) log_.trace(ME, "::connect. Registering initFailsafe");
      connection_->initFailsafe(connectionProblems_);
      connectionProblems_ = NULL;
   }
   if (log_.trace()) log_.trace(ME, string("::connect. connectQos: ") + connectQos_->toXml());
   // do connect() now:
   connectReturnQos_ = connection_->connect(connectQos_);

   ME = string("XmlBlasterAccess-") + connectReturnQos_->getSessionQos().getAbsoluteName();

   setServerNodeId(connectReturnQos_->getSessionQos().getClusterNodeId());
   
   // Is done in ConnectionsHandler.cpp
   //global_.setId(connectReturnQos_->getSessionQos().getAbsoluteName());

   return *connectReturnQos_;
}

org::xmlBlaster::util::Global& XmlBlasterAccess::getGlobal()
{
   return this->global_;
}

org::xmlBlaster::util::queue::I_Queue* XmlBlasterAccess::getQueue()
{
   if (connection_) {
      return connection_->getQueue();
   }
   return 0;
}


org::xmlBlaster::client::I_Callback* XmlBlasterAccess::getCallback()
{
   return this->updateClient_;
}

void XmlBlasterAccess::setCallbackDispatcherActive(bool isActive)
{
   string command = getSessionName() + "/?dispatcherActive=" + lexical_cast<string>(isActive);
   sendAdministrativeCommand(command);
   connectQos_->getCbAddress()->setDispatcherActive(isActive);
}

string XmlBlasterAccess::sendAdministrativeCommand(const string &command, PublishQos *publishQosP)
{
   bool isGet = command.find("get ") == 0 || command.find("GET ") == 0;
   bool isSet = command.find("set ") == 0 || command.find("SET ") == 0;
   const string cmd = ((isGet || isSet)) ? command.substr(4) : command;
   
   if (publishQosP ||(isSet || (!isGet && cmd.find("=") != string::npos)) ) {
      string oid = string("__cmd:") + cmd;
      PublishKey  key(global_, oid); // oid="__cmd:/client/joe/1/?dispatcherActive=false"
      MessageUnit msgUnit(key, "", ( publishQosP ) ? *publishQosP : PublishQos(global_) );
      try {
         PublishReturnQos ret = publish(msgUnit);
         if (log_.trace()) log_.trace(ME, "Send '" + cmd + " '");
         return ret.getState();
      }
      catch (XmlBlasterException &e) {
         if (log_.trace()) log_.trace(ME, "Sending of '" + cmd + " ' failed: " + e.getMessage());
         throw e;
      }
   }
   else {
      string oid = string("__cmd:") + cmd;
      GetKey getKey(global_);
      getKey.setOid(oid);
      GetQos getQos(global_);
      try {
         vector<MessageUnit> msgVec = get(getKey, getQos);
         if (log_.trace()) log_.trace(ME, "Send '" + cmd + " ', got array of size " + lexical_cast<string>(msgVec.size()));
         if (msgVec.size() == 0)
            return "";
         return msgVec[0].getContentStr();
      }
      catch (XmlBlasterException &e) {
         if (log_.trace()) log_.trace(ME, "Sending of '" + cmd + " ' failed: " + e.getMessage());
         throw e;
      }
   }
}


void XmlBlasterAccess::createDefaultCbServer()
{
   log_.call(ME, "::createDefaultCbServer");

   CbQueueProperty prop = connectQos_->getSessionCbQueueProperty(); // Creates a default property for us if none is available
   const AddressBaseRef &addr = prop.getCurrentCallbackAddress();

   if(!cbServer_)
     cbServer_ = initCbServer(getLoginName(), addr->getType(), addr->getVersion());

   addr->setAddress(cbServer_->getCbAddress());
   addr->setType(cbServer_->getCbProtocol());
   // !!!!! prop.setCallbackAddress(addr);
   connectQos_->setSessionCbQueueProperty(prop);
   if (log_.trace()) log_.trace(ME, string("::createDefaultCbServer: connectQos: ") + connectQos_->toXml());
   log_.info(ME, "Callback settings: " + prop.getSettings());
}

I_CallbackServer*
XmlBlasterAccess::initCbServer(const string& loginName, const string& type, const string& version)
{
   if (log_.call()) log_.call(ME, string("::initCbServer: loginName='") + loginName + "' type='" + type + "' version='" + version +"'");
   if (log_.trace()) log_.trace(ME, string("Using 'client.cbProtocol=") + type + string("' to be used by ") + getServerNodeId() + string(", trying to create the callback server ..."));
   I_CallbackServer* server = &(global_.getCbServerPluginManager().getPlugin(instanceName_, type, version));
   if (log_.trace()) log_.trace(ME, "After callback plugin creation");
   server->initialize(loginName, *this);
   if (log_.trace()) log_.trace(ME, "After callback plugin initialize");
   return server;
}

org::xmlBlaster::util::dispatch::I_PostSendListener* XmlBlasterAccess::registerPostSendListener(org::xmlBlaster::util::dispatch::I_PostSendListener *listener) {
   I_PostSendListener* old = this->postSendListener_;
   this->postSendListener_ = listener;
   //if (connection_)
   //   return connection_->registerPostSendListener(this);
   return old;
}

// I_PostSendListener
void XmlBlasterAccess::postSend(const std::vector<EntryType> &entries)
{
   I_PostSendListener* l = this->postSendListener_;
   if (l)
      l->postSend(entries);
}

// I_PostSendListener
bool XmlBlasterAccess::sendingFailed(const std::vector<EntryType> &entries, const XmlBlasterException &exception)
{
   I_PostSendListener* l = this->postSendListener_;
   if (l)
      return l->sendingFailed(entries, exception);
   return false;
}

org::xmlBlaster::client::protocol::I_ProgressListener* XmlBlasterAccess::registerProgressListener(org::xmlBlaster::client::protocol::I_ProgressListener *listener)
{
   return (this->cbServer_) ? this->cbServer_->registerProgressListener(listener) : 0;
}

org::xmlBlaster::util::qos::ConnectQosRef XmlBlasterAccess::getConnectQos() {
   return connectQos_;
}

//org::xmlBlaster::util::qos::ConnectReturnQosRef XmlBlasterAccess::getConnectReturnQos() {
//}

void
XmlBlasterAccess::initSecuritySettings(const string& /*secMechanism*/, const string& /*secVersion*/)
{
   log_.error(ME, "initSecuritySettings not implemented yet");
}

void XmlBlasterAccess::leaveServer(const StringMap &/*map*/)
{
   org::xmlBlaster::util::thread::Lock lock(invocationMutex_);
   if (!isConnected()) {
      throw XmlBlasterException(USER_NOT_CONNECTED, ME + "::leaveServer", "You are not connected to the xmlBlaster");
   }
   
   if (cbServer_) {
      if (log_.trace()) log_.trace(ME, "destructor: going to delete the callback connection");
      cbServer_->shutdownCb();
   }
   
   if (connection_) {
      if (log_.trace()) log_.trace(ME, "destructor: going to delete the connection");
      connection_->shutdown();
   }
   
   if (cbServer_) {
      CbQueueProperty prop = connectQos_->getSessionCbQueueProperty(); // Creates a default property for us if none is available
      AddressBaseRef addr = prop.getCurrentCallbackAddress(); // c++ may not return null
      global_.getCbServerPluginManager().releasePlugin( instanceName_, addr->getType(), addr->getVersion() );
      cbServer_ = NULL;
   }
   
   if (connection_) {
      delete connection_;
      connection_ = NULL;
   }
   log_.info(ME, "leaveServer() done");
}


bool
XmlBlasterAccess::disconnect(const DisconnectQos& qos, bool flush, bool shutdown, bool shutdownCb)
{
   // locking until finished 
   org::xmlBlaster::util::thread::Lock lock(invocationMutex_);
   bool ret1 = true;
   bool ret3 = true;
   if (log_.call()) {
      log_.call(ME, string("disconnect called with flush='") + Global::getBoolAsString(flush) + 
                              "' shutdown='" + Global::getBoolAsString(shutdown) + 
                    "' shutdownCb='" + Global::getBoolAsString(shutdownCb) + "'");
   }

   if (log_.trace()) log_.trace(ME, "disconnecting the client connection");
   if (log_.dump()) log_.dump(ME, string("disconnect: the qos is:\n") + qos.toXml());
   if (connection_ != NULL) {
      ret1  = connection_->disconnect(qos);
      if (shutdown) connection_->shutdown();
   }
   else {
      ret1 = false;
   }
   if (shutdownCb) {
      if (cbServer_) {
         ret3 = cbServer_->shutdownCb();

         CbQueueProperty prop = connectQos_->getSessionCbQueueProperty(); // Creates a default property for us if none is available
         const AddressBaseRef &addr = prop.getCurrentCallbackAddress();
         global_.getCbServerPluginManager().releasePlugin( instanceName_, addr->getType(), addr->getVersion() );
         cbServer_ = NULL;
      }
      else ret3 = false;
   }
   return ret1 && ret3;
}

string XmlBlasterAccess::getId()
{
   return getSessionName();
}

SessionNameRef XmlBlasterAccess::getSessionNameRef()
{
   if (!connectReturnQos_.isNull()) return connectReturnQos_->getSessionQos().getSessionName();
   return connectQos_->getSessionQos().getSessionName();
}

string XmlBlasterAccess::getSessionName()
{
   string ret;
   if (!connectReturnQos_.isNull()) ret = connectReturnQos_->getSessionQos().getAbsoluteName();
   if (ret == "") ret = connectQos_->getSessionQos().getAbsoluteName();
   return ret;
}

string XmlBlasterAccess::getLoginName()
{
   try {
      string nm = connectQos_->getSecurityQos().getUserId();
      if (nm != "") return nm;
   }
   catch (XmlBlasterException e) {
      log_.warn(ME, e.toString());
   }
   return string("client?");
}

void XmlBlasterAccess::setServerNodeId(const string& nodeId)
{
   serverNodeId_ = nodeId;
}

string XmlBlasterAccess::getServerNodeId() const
{
   return serverNodeId_;
}

/*
MsgQueueEntry
XmlBlasterAccess::queueMessage(const MsgQueueEntry& entry)
{
 return entry;
}

vector<MsgQueueEntry*>
XmlBlasterAccess::queueMessage(const vector<MsgQueueEntry*>& entries)
{
   return entries;
}
*/

SubscribeReturnQos XmlBlasterAccess::subscribe(const SubscribeKey& key, const SubscribeQos& qos, I_Callback *callback)
{
   // locking until finished 
   org::xmlBlaster::util::thread::Lock lock(invocationMutex_);
   
   if (!isConnected()) {
      throw XmlBlasterException(USER_NOT_CONNECTED, ME + "::subscribe", "you are not connected to the xmlBlaster");
   }
   if (log_.call()) log_.call(ME, "subscribe");
   if (log_.dump()) {
      log_.dump(ME, string("subscribe. The key:\n") + key.toXml());
      log_.dump(ME, string("subscribe. The Qos:\n") + qos.toXml());
   }

   SessionNameRef sessionName = getSessionNameRef();
   if (sessionName->getPubSessionId() > 0 &&
      qos.getMultiSubscribe()==false &&
      !qos.hasSubscriptionId()) {
      // For failsave clients we generate on client side the subscriptionId
      // In case of offline/clientSideQueued operation we guarantee like this a not changing
      // subscriptionId and the client code can reliably use the subscriptionId for further dispatching
      // of update() messages.
      SubscribeQos& q = const_cast<SubscribeQos&>(qos);
      q.generateSubscriptionId(sessionName, key);
      if (log_.trace()) log_.trace(ME, "subscribe: generated client side subscriptionId=" + q.getData().getSubscriptionId());
   }

   if (callback != 0) { // using a subscribe specific callback?
      if (log_.trace()) log_.trace(ME, "subscribe: inserting individual callback in callback map");
      org::xmlBlaster::util::thread::Lock lockUpdate(updateMutex_);
      SubscribeReturnQos retQos = connection_->subscribe(key, qos);
      std::string subId = retQos.getSubscriptionId();
      subscriptionCallbackMap_.insert(std::map<std::string, I_Callback*>::value_type(subId, callback));
      return retQos;
   }
   else {
      if (log_.trace()) log_.trace(ME, "subscribe: no specific callback");
      return connection_->subscribe(key, qos);
   }
}

vector<MessageUnit> XmlBlasterAccess::get(const GetKey& key, const GetQos& qos)
{
   // locking until finished 
   org::xmlBlaster::util::thread::Lock lock(invocationMutex_);
   if (!isConnected()) {
      throw XmlBlasterException(USER_NOT_CONNECTED, ME + "::get", "you are not connected to the xmlBlaster");
   }
   if (log_.call()) log_.call(ME, "get");
   if (log_.dump()) {
      log_.dump(ME, string("get. The key:\n") + key.toXml());
      log_.dump(ME, string("get. The Qos:\n") + qos.toXml());
   }
   return connection_->get(key, qos);
}

vector<MessageUnit> XmlBlasterAccess::receive(string oid, int maxEntries, long timeout, bool consumable) {
   if (!isConnected()) {
      throw XmlBlasterException(USER_NOT_CONNECTED, ME + "::receive", "you are not connected to the xmlBlaster");
   }
   if (log_.call()) log_.call(ME, "receive");
   
    //topic/hello          to access a history queue,
    //client/joe           to access a subject queue or
    //client/joe/session/1 
   if (oid.find("topic") != string::npos)
      oid = "__cmd:"+oid+"/?historyQueueEntries"; // "__cmd:topic/hello/?historyQueueEntries"
   else if (oid.find("session") != string::npos)
      oid = "__cmd:"+oid+"/?callbackQueueEntries"; // "__cmd:client/joe/session/1/?callbackQueueEntries";
   else if (oid.find("subject") != string::npos)
      oid = "__cmd:"+oid+"/?subjectQueueEntries"; // "__cmd:client/joe/?subjectQueueEntries"
   else
      throw XmlBlasterException(USER_NOT_CONNECTED, ME + "::receive", "Can't parse '" + oid + "'");

   GetKey getKey(global_, oid);
   QueryQosData data(global_);
   data.setQueryQos(maxEntries, timeout, consumable);
   GetQos getQos(global_, data);
   vector<MessageUnit> msgs = get(getKey, getQos);
   if (log_.trace()) log_.trace(ME, string("receive - got '") + lexical_cast<std::string>(msgs.size()) + "'");
   return msgs;
}

vector<MessageUnit> XmlBlasterAccess::request(MessageUnit &msgUnit, long timeout, int maxEntries) {
   if (log_.call()) log_.call(ME, "request");

   // Create a temporary reply topic ...
   long destroyDelay = timeout+86400000; // on client crash, cleanup after one day; //long destroyDelay = -1;
   string tempTopicOid = createTemporaryTopic(destroyDelay, maxEntries);

   try {
      // Send the request ...
      // "__jms:JMSReplyTo"
      org::xmlBlaster::util::qos::QosData &qos =  const_cast<org::xmlBlaster::util::qos::QosData&>(msgUnit.getQos());
      qos.addClientProperty(string(Constants::JMS_REPLY_TO), tempTopicOid);
      publish(msgUnit);
      // Access the reply ...
      vector<MessageUnit> msgs = receive("topic/"+tempTopicOid, maxEntries, timeout, true);
      {  // Clean up temporary topic ...
         EraseKey ek(global_, tempTopicOid);
         EraseQos eq(global_);
         eq.setForceDestroy(true);
         erase(ek, eq);
      }
      return msgs;
   }
   catch (exception &ex) {
      {  // Clean up temporary topic ...
         EraseKey ek(global_, tempTopicOid);
         EraseQos eq(global_);
         eq.setForceDestroy(true);
         erase(ek, eq);
      }
      throw ex;
   }
}

std::string XmlBlasterAccess::createTemporaryTopic(long destroyDelay, int historyMaxMsg) {
   PublishKey pk(global_);
   PublishQos pq(global_);
   TopicProperty topicProperty(global_);
   topicProperty.setDestroyDelay(destroyDelay);
   topicProperty.setCreateDomEntry(false);
   topicProperty.setReadonly(false);
   pq.setAdministrative(true);
   if (historyMaxMsg >= 0L) {
      HistoryQueueProperty prop(global_, "");
      prop.setMaxEntries(historyMaxMsg);
      topicProperty.setHistoryQueueProperty(prop);
   }
   pq.setTopicProperty(topicProperty);
   MessageUnit msgUnit(pk, "", pq);
   PublishReturnQos prq = publish(msgUnit);
   if (log_.call()) log_.call(ME, string("Created temporary topic ") + prq.getKeyOid());
   return prq.getKeyOid();
}


vector<UnSubscribeReturnQos>
XmlBlasterAccess::unSubscribe(const UnSubscribeKey& key, const UnSubscribeQos& qos)
{
   // locking until finished 
   org::xmlBlaster::util::thread::Lock lock(invocationMutex_);
   if (!isConnected()) {
      throw XmlBlasterException(USER_NOT_CONNECTED, ME + "::unsSubscribe", "you are not connected to the xmlBlaster");
   }
   if (log_.call()) log_.call(ME, "unSubscribe");
   if (log_.dump()) {
      log_.dump(ME, string("unSubscribe. The key:\n") + key.toXml());
      log_.dump(ME, string("unSubscribe. The Qos:\n") + qos.toXml());
   }
   // synchronization
   org::xmlBlaster::util::thread::Lock lock1(updateMutex_);
   vector<UnSubscribeReturnQos> ret = connection_->unSubscribe(key, qos);
   vector<UnSubscribeReturnQos>::iterator iter = ret.begin();
   while (iter != ret.end()) {
      if (log_.trace()) log_.trace(ME, std::string("unSubscribe: removing callback for '") + (*iter).getSubscriptionId() + "'");
      subscriptionCallbackMap_.erase((*iter).getSubscriptionId());
      iter++;
   }
   return ret;
}

PublishReturnQos XmlBlasterAccess::publish(const MessageUnit& msgUnit)
{
   // locking until finished 
   org::xmlBlaster::util::thread::Lock lock(invocationMutex_);
   if (!isConnected()) {
      throw XmlBlasterException(USER_NOT_CONNECTED, ME + "::publish", "you are not connected to the xmlBlaster");
   }
   if (log_.call()) log_.call(ME, "publish");
   if (log_.dump()) {
      log_.dump(ME, string("publish. The msgUnit:\n") + msgUnit.toXml());
   }
   return connection_->publish(msgUnit);
}

void XmlBlasterAccess::publishOneway(const vector<MessageUnit>& msgUnitArr)
{
   // locking until finished 
   org::xmlBlaster::util::thread::Lock lock(invocationMutex_);
   if (!isConnected()) {
      throw XmlBlasterException(USER_NOT_CONNECTED, ME + "::publishOneway", "you are not connected to the xmlBlaster");
   }
   if (log_.call()) log_.call(ME, "publishOneway");
   if (log_.dump()) {
      for (vector<MessageUnit>::size_type i=0; i < msgUnitArr.size(); i++) {
         log_.dump(ME, string("publishOneway. The msgUnit[") + lexical_cast<std::string>(i) + "]:\n" + msgUnitArr[i].toXml());
      }
   }
   connection_->publishOneway(msgUnitArr);
}

vector<PublishReturnQos> XmlBlasterAccess::publishArr(const vector<MessageUnit> &msgUnitArr)
{
   // locking until finished 
   org::xmlBlaster::util::thread::Lock lock(invocationMutex_);
   if (!isConnected()) {
      throw XmlBlasterException(USER_NOT_CONNECTED, ME + "::publishArr", "you are not connected to the xmlBlaster");
   }
   if (log_.call()) log_.call(ME, "publishArr");
   if (log_.dump()) {
      for (vector<MessageUnit>::size_type i=0; i < msgUnitArr.size(); i++) {
         log_.dump(ME, string("publishArr. The msgUnit[") + lexical_cast<std::string>(i) + "]:\n" + msgUnitArr[i].toXml());
      }
   }
   return connection_->publishArr(msgUnitArr);
}

vector<EraseReturnQos> XmlBlasterAccess::erase(const EraseKey& key, const EraseQos& qos)
{
   // locking until finished 
   org::xmlBlaster::util::thread::Lock lock(invocationMutex_);
   if (!isConnected()) {
      throw XmlBlasterException(USER_NOT_CONNECTED, ME + "::erase", "you are not connected to the xmlBlaster");
   }
   if (log_.call()) log_.call(ME, "erase");
   if (log_.dump()) {
      log_.dump(ME, string("erase. The key:\n") + key.toXml());
      log_.dump(ME, string("erase. The Qos:\n") + qos.toXml());
   }
   return connection_->erase(key, qos);
}

string
XmlBlasterAccess::update(const string &sessionId, UpdateKey &updateKey, const unsigned char *content, long contentSize, UpdateQos &updateQos)
{
   if (log_.call()) log_.call(ME, "::update");
   if (log_.trace()) log_.trace(ME, string("update. The sessionId is '") + sessionId + "'");
   if (log_.dump()) {
      log_.dump(ME, string("update. The key:\n") + updateKey.toXml());
      log_.dump(ME, string("update. The Qos:\n") + updateQos.toXml());
   }

   if (!subscriptionCallbackMap_.empty()) {
      // This is synchronized but you must ensure the callback is still in scope when the update method is 
      // invoked. This could be more robust with a reference counted I_Callback.
      I_Callback* subscriptionCallback = 0;
      {
         org::xmlBlaster::util::thread::Lock lock(updateMutex_);
         CallbackMapType::iterator iter = subscriptionCallbackMap_.end();
         iter = subscriptionCallbackMap_.find(updateQos.getSubscriptionId());
         if (iter != subscriptionCallbackMap_.end()) subscriptionCallback = (*iter).second;
      }

      if (subscriptionCallback != 0) {
         if (log_.trace()) log_.trace(ME, std::string("update: invoking specific subscription callback"));
         return subscriptionCallback->update(sessionId, updateKey, content, contentSize, updateQos);
      }
   }

   if (updateClient_)
      return updateClient_->update(sessionId, updateKey, content, contentSize, updateQos);
   else {
      // See similar behavior in XmlBlasterAccess.java
      log_.error(ME, string("Ignoring unexpected update message as client has not registered a callback: ") + updateKey.toXml() + "" + updateQos.toXml());
   }

   return Constants::RET_OK; // "<qos><state id='OK'/></qos>";
}

std::string XmlBlasterAccess::usage()
{
   string text = string("\n");
   text += string("Choose a connection protocol:\n");
   text += string("   -protocol           Specify a protocol to talk with xmlBlaster, choose 'SOCKET' or 'IOR' depending on your compilation.\n");
   text += string("                       Current setting is '") + Global::getInstance().getProperty().getStringProperty("protocol", Global::getDefaultProtocol());
   text += string("\n\n");
   text += string("Security features:\n");
   text += string("   -Security.Client.DefaultPlugin \"gui,1.0\"\n");
   text += string("                       Force the given authentication schema, here the GUI is enforced\n");
   text += string("                       Clients can overwrite this with ConnectQos.java\n");

   return text; // std::cout << text << std::endl;
}

void XmlBlasterAccess::initFailsafe(I_ConnectionProblems* connectionProblems)
{
   if (connection_) connection_->initFailsafe(connectionProblems);
   else connectionProblems_ = connectionProblems;   
}

string XmlBlasterAccess::ping()
{
   // locking until finished 
   org::xmlBlaster::util::thread::Lock lock(invocationMutex_);
   return connection_->ping("<qos/>");
}

long XmlBlasterAccess::flushQueue()
{
   if (!connection_) {
      throw XmlBlasterException(INTERNAL_NULLPOINTER, ME + "::flushQueue", "no connection exists when trying to flush the queue: try to connect to xmlBlaster first");
   }
   return connection_->flushQueue();
}


bool XmlBlasterAccess::isConnected() const
{
   if (!connection_) return false;
   return connection_->isConnected();
}

bool XmlBlasterAccess::isAlive() const
{
   if (!connection_) return false;
   return connection_->isAlive();
}

bool XmlBlasterAccess::isPolling() const
{
   if (!connection_) return false;
   return connection_->isPolling();
}

bool XmlBlasterAccess::isDead() const
{
   if (!connection_) return false;
   return connection_->isDead();
}
 

std::string XmlBlasterAccess::getStatusString() const
{
   if (!connection_) return "DEAD";
   return connection_->getStatusString();
}


}}} // namespaces


#ifdef _XMLBLASTER_CLASSTEST

#include <util/Timestamp.h>
#include <util/thread/ThreadImpl.h>

using namespace std;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::util::thread;

int main(int args, char* argv[])
{
    // Init the XML platform
    try
    {
       Global& glob = Global::getInstance();
       glob.initialize(args, argv);
       Log& log = glob.getLog("org.xmlBlaster.client");

       XmlBlasterAccess xmlBlasterAccess(glob);
       ConnectQos connectQos(glob);

       log.info("main", string("the connect qos is: ") + connectQos.toXml());

       ConnectReturnQosRef retQos = xmlBlasterAccess.connect(connectQos, NULL);
       log.info("", "Successfully connect to xmlBlaster");

       if (log.trace()) log.trace("main", "Subscribing using XPath syntax ...");
       SubscribeKey subKey(glob,"//test","XPATH");
       log.info("main", string("subscribe key: ") + subKey.toXml());
       SubscribeQos subQos(glob);
       log.info("main", string("subscribe qos: ")  + subQos.toXml());
       try {
          SubscribeReturnQos subReturnQos = xmlBlasterAccess.subscribe(subKey, subQos);
          log.info("main", string("Success: Subscribe return qos=") +
                   subReturnQos.toXml() + " done");
       }
       catch (XmlBlasterException &ex) {
          log.error("main", ex.toXml());
       }

       PublishKey pubKey(glob);
       pubKey.setOid("HelloWorld");
       pubKey.setClientTags("<test></test>");
       PublishQos pubQos(glob);
       MessageUnit msgUnit(pubKey, string("Hi"), pubQos);

       PublishReturnQos pubRetQos = xmlBlasterAccess.publish(msgUnit);
       log.info("main", string("successfully published, publish return qos: ") + pubRetQos.toXml());

       log.info("", "Successfully published a message to xmlBlaster");
       log.info("", "Sleeping");
       Timestamp delay = 10000000000ll; // 10 seconds
       Thread::sleep(delay);
   }
   catch (XmlBlasterException &ex) {
      std::cout << ex.toXml() << std::endl;
   }
   return 0;
}

#endif
