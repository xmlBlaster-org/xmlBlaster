/*------------------------------------------------------------------------------
Name:      XmlBlasterAccess.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <client/XmlBlasterAccess.h>
#include <util/Global.h>
#include <boost/lexical_cast.hpp>

using namespace boost;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::dispatch;
using namespace org::xmlBlaster::client::protocol;

namespace org { namespace xmlBlaster { namespace client {


XmlBlasterAccess::XmlBlasterAccess(Global& global, const string& instanceName)
   : ME(string("XmlBlasterAccess-") + instanceName),
     serverNodeId_("xmlBlaster"), 
     connectQos_(global), 
     connectReturnQos_(global),
     global_(global), 
     log_(global.getLog("client")), 
     instanceName_(instanceName)
{
   log_.call(ME, "::constructor");
   cbServer_           = NULL;
   updateClient_       = NULL;
   connection_         = NULL;
   deliveryManager_    = NULL;
   connectionProblems_ = NULL;
}

XmlBlasterAccess::~XmlBlasterAccess()
{
   if (log_.call()) log_.call(ME, "destructor");
   if (cbServer_) {
      CbQueueProperty prop = connectQos_.getSessionCbQueueProperty(); // Creates a default property for us if none is available
      CallbackAddress addr = prop.getCurrentCallbackAddress(); // c++ may not return null
      global_.getCbServerPluginManager().releasePlugin( instanceName_, addr.getType(), addr.getVersion() );
      cbServer_ = NULL;
   }
   if (log_.trace()) log_.trace(ME, "destructor: going to delete the connection");
   if (connection_) {
      connection_->shutdown();
      delete connection_;
      connection_ = NULL;
   }
   deliveryManager_    = NULL;
   updateClient_       = NULL;
   connectionProblems_ = NULL;
   if (log_.trace()) log_.trace(ME, "destructor ended");
}


ConnectReturnQos XmlBlasterAccess::connect(const ConnectQos& qos, I_Callback *clientAddr)
{
   log_.call(ME, "::connect");
   connectQos_ = qos;
   SecurityQos securityQos = connectQos_.getSecurityQos();
//   initSecuritySettings(securityQos.getPluginType(), securityQos.getPluginVersion());
   ME = string("XmlBlasterAccess-") + getId();
   string typeVersion = global_.getProperty().getStringProperty("queue.defaultPlugin", "CACHE,1.0");
   string queueId = string("client:") + getId();
   updateClient_ = clientAddr;
   if (!cbServer_) createDefaultCbServer();

   // currently the simple version will do it ...
   if (!deliveryManager_) deliveryManager_ = &(global_.getDeliveryManager());

/*
   string type = connectQos_.getServerRef().getType();
   string version = "1.0";
   connection_ = &(deliveryManager_->getPlugin(type, version));
*/
   if (!connection_) {
      connection_ = deliveryManager_->getConnectionsHandler(instanceName_);
   }

   if (connectionProblems_) {
      connection_->initFailsafe(connectionProblems_);
      connectionProblems_ = NULL;
   }
   if (log_.trace()) log_.trace(ME, string("::connect. connectQos: ") + connectQos_.toXml());
   connectReturnQos_ = connection_->connect(connectQos_);
   return connectReturnQos_;
}

void XmlBlasterAccess::createDefaultCbServer()
{
   log_.call(ME, "::createDefaultCbServer");

   CbQueueProperty prop = connectQos_.getSessionCbQueueProperty(); // Creates a default property for us if none is available
   CallbackAddress addr = prop.getCurrentCallbackAddress(); // c++ may not return null
   cbServer_ = initCbServer(getLoginName(), addr.getType(), addr.getVersion());

   addr.setAddress(cbServer_->getCbAddress());
   addr.setType(cbServer_->getCbProtocol());
   prop.setCallbackAddress(addr);
   connectQos_.setSessionCbQueueProperty(prop);
   if (log_.trace()) log_.trace(ME, string("::createDefaultCbServer: connectQos: ") + connectQos_.toXml());
   log_.info(ME, "Callback settings: " + prop.getSettings());
}

I_CallbackServer*
XmlBlasterAccess::initCbServer(const string& loginName, const string& type, const string& version)
{
   log_.call(ME, "::initCbServer");
   if (log_.trace()) log_.trace(ME, string("Using 'client.cbProtocol=") + type + string("' to be used by ") + getServerNodeId() + string(", trying to create the callback server ..."));
   I_CallbackServer* server = &(global_.getCbServerPluginManager().getPlugin(instanceName_, type, version));
   server->initialize(loginName, *this);
   return server;
}

void
XmlBlasterAccess::initSecuritySettings(const string& secMechanism, const string& secVersion)
{
   log_.error(ME, "initSecuritySettings not implemented yet");
}

bool
XmlBlasterAccess::disconnect(const DisconnectQos& qos, bool flush, bool shutdown, bool shutdownCb)
{
   bool ret1 = true;
   bool ret2 = true;
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
            if (shutdown) ret2 = connection_->shutdown();
   }
   else {
      ret1 = false;
      ret2 = false;
   }
   if (shutdownCb) {
      if (cbServer_) {
         ret3 = cbServer_->shutdownCb();

         CbQueueProperty prop = connectQos_.getSessionCbQueueProperty(); // Creates a default property for us if none is available
         CallbackAddress addr = prop.getCurrentCallbackAddress(); // c++ may not return null
         global_.getCbServerPluginManager().releasePlugin( instanceName_, addr.getType(), addr.getVersion() );
         cbServer_ = NULL;
      }
      else ret3 = false;
   }
   return ret1 && ret2 && ret3;
}

string XmlBlasterAccess::getId()
{
   return getSessionName();
}

string XmlBlasterAccess::getSessionName()
{
   string ret = connectReturnQos_.getSessionQos().getAbsoluteName();
   if (ret == "") ret = connectQos_.getSessionQos().getAbsoluteName();
   return ret;
}

string XmlBlasterAccess::getLoginName()
{
   try {
      string nm = connectQos_.getSecurityQos().getUserId();
      if (nm != "") return nm;
   }
   catch (XmlBlasterException e) {}
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

SubscribeReturnQos XmlBlasterAccess::subscribe(const SubscribeKey& key, const SubscribeQos& qos)
{
   if (log_.call()) log_.call(ME, "subscribe");
   if (log_.dump()) {
      log_.dump(ME, string("subscribe. The key:\n") + key.toXml());
      log_.dump(ME, string("subscribe. The Qos:\n") + qos.toXml());
   }
   return connection_->subscribe(key, qos);
}

vector<MessageUnit> XmlBlasterAccess::get(const GetKey& key, const GetQos& qos)
{
   if (log_.call()) log_.call(ME, "get");
   if (log_.dump()) {
      log_.dump(ME, string("get. The key:\n") + key.toXml());
      log_.dump(ME, string("get. The Qos:\n") + qos.toXml());
   }
   return connection_->get(key, qos);
}

vector<UnSubscribeReturnQos> 
XmlBlasterAccess::unSubscribe(const UnSubscribeKey& key, const UnSubscribeQos& qos)
{
   if (log_.call()) log_.call(ME, "unSubscribe");
   if (log_.dump()) {
      log_.dump(ME, string("unSubscribe. The key:\n") + key.toXml());
      log_.dump(ME, string("unSubscribe. The Qos:\n") + qos.toXml());
   }
   return connection_->unSubscribe(key, qos);
}

PublishReturnQos XmlBlasterAccess::publish(const MessageUnit& msgUnit)
{
   if (log_.call()) log_.call(ME, "publish");
   if (log_.dump()) {
      log_.dump(ME, string("publish. The msgUnit:\n") + msgUnit.toXml());
   }
   return connection_->publish(msgUnit);
}

void XmlBlasterAccess::publishOneway(const vector<MessageUnit>& msgUnitArr)
{
   if (log_.call()) log_.call(ME, "publishOneway");
   if (log_.dump()) {
      for (int i=0; i < msgUnitArr.size(); i++) {
             log_.dump(ME, string("publishOneway. The msgUnit[") + lexical_cast<string>(i) + "]:\n" + msgUnitArr[i].toXml());
      }
   }
   connection_->publishOneway(msgUnitArr);
}

vector<PublishReturnQos> XmlBlasterAccess::publishArr(vector<MessageUnit> msgUnitArr)
{
   if (log_.call()) log_.call(ME, "publishArr");
   if (log_.dump()) {
      for (int i=0; i < msgUnitArr.size(); i++) {
             log_.dump(ME, string("publishArr. The msgUnit[") + lexical_cast<string>(i) + "]:\n" + msgUnitArr[i].toXml());
      }
   }
   return connection_->publishArr(msgUnitArr);
}

vector<EraseReturnQos> XmlBlasterAccess::erase(const EraseKey& key, const EraseQos& qos)
{
   if (log_.call()) log_.call(ME, "erase");
   if (log_.dump()) {
      log_.dump(ME, string("erase. The key:\n") + key.toXml());
      log_.dump(ME, string("erase. The Qos:\n") + qos.toXml());
   }
   return connection_->erase(key, qos);
}

string
XmlBlasterAccess::update(const string &sessionId, UpdateKey &updateKey, void *content, long contentSize, UpdateQos &updateQos)
{
   if (log_.call()) log_.call(ME, "::update");
   if (log_.trace()) log_.trace(ME, string("update. The sessionId is '") + sessionId + "'");
   if (log_.dump()) {
      log_.dump(ME, string("update. The key:\n") + updateKey.toXml());
      log_.dump(ME, string("update. The Qos:\n") + updateQos.toXml());
   }
   if (updateClient_)
      return updateClient_->update(sessionId, updateKey, content, contentSize, updateQos);
   return "<qos><state id='OK'/></qos>";
}

void XmlBlasterAccess::usage()
{
   string text = string("\n");
   text += string("Choose a connection protocol:\n");
   text += string("   -client.protocol    Specify a protocol to talk with xmlBlaster, 'SOCKET' or 'IOR' or 'RMI' or 'SOAP' or 'XML-RPC'.\n");
   text += string("                       Current setting is '") + Global::getInstance().getProperty().getStringProperty("client.protocol", "IOR") + string("'. See below for protocol settings.\n");
   text += string("                       Example: java MyApp -client.protocol RMI -rmi.hostname 192.168.10.34\n");
   text += string("\n");
   text += string("Security features:\n");
   text += string("   -Security.Client.DefaultPlugin \"gui,1.0\"\n");
   text += string("                       Force the given authentication schema, here the GUI is enforced\n");
   text += string("                       Clients can overwrite this with ConnectQos.java\n");

   std::cout << text << std::endl;
}

void XmlBlasterAccess::initFailsafe(I_ConnectionProblems* connectionProblems)
{
   if (connection_) connection_->initFailsafe(connectionProblems);
   else connectionProblems_ = connectionProblems;   
}

string XmlBlasterAccess::ping()
{
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
 

}}} // namespaces


#ifdef _XMLBLASTER_CLASSTEST

#include <util/PlatformUtils.hpp>
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
       XMLPlatformUtils::Initialize();
       Global& glob = Global::getInstance();
       glob.initialize(args, argv);
       Log& log = glob.getLog("client");

       XmlBlasterAccess xmlBlasterAccess(glob);
       ConnectQos connectQos(glob);

       log.info("main", string("the connect qos is: ") + connectQos.toXml());

       ConnectReturnQos retQos = xmlBlasterAccess.connect(connectQos, NULL);
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
