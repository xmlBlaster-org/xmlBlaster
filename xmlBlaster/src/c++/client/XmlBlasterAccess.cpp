/*------------------------------------------------------------------------------
Name:      XmlBlasterAccess.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <client/XmlBlasterAccess.h>

using org::xmlBlaster::util::MessageUnit;
using org::xmlBlaster::util::dispatch::DeliveryManager;

namespace org { namespace xmlBlaster { namespace client {

using org::xmlBlaster::util::qos::ConnectQos;
using org::xmlBlaster::util::qos::ConnectReturnQos;
using namespace org::xmlBlaster::client::protocol;
using org::xmlBlaster::util::Log;
using org::xmlBlaster::util::MessageUnit;

XmlBlasterAccess::XmlBlasterAccess(Global& global)
   : ME("XmlBlasterAccess"),
     serverNodeId_("xmlBlaster"), connectQos_(global), connectReturnQos_(global),
     global_(global), log_(global.getLog("client"))
{
   cbServer_        = NULL;
   updateClient_    = NULL;
   connection_      = NULL;
   deliveryManager_ = NULL;
}

XmlBlasterAccess::~XmlBlasterAccess()
{
   // don't forget to implement ...
}

ConnectReturnQos XmlBlasterAccess::connect(const ConnectQos& qos, I_Callback *clientAddr)
{
   connectQos_ = qos;
   SecurityQos securityQos = connectQos_.getSecurityQos();
   initSecuritySettings(securityQos.getPluginType(), securityQos.getPluginVersion());
   ME = string("XmlBlasterAccess-") + getId();
   string typeVersion = global_.getProperty().getStringProperty("queue.defaultPlugin", "CACHE,1.0");
   string queueId = string("client:") + getId();
   if (clientAddr != NULL) { // Start a default callback server using same protocol
      updateClient_ = clientAddr;
      createDefaultCbServer();
   }

   // currently the simple version will do it ...
   deliveryManager_ = &(global_.getDeliveryManager());
   string type = connectQos_.getServerRef().getType();
   string version = "1.0";
   connection_ = &(deliveryManager_->getPlugin(type, version));
   return connection_->connect(connectQos_);
}

void XmlBlasterAccess::createDefaultCbServer()
{
   CbQueueProperty prop = connectQos_.getCbQueueProperty(); // Creates a default property for us if none is available
   CallbackAddress addr = prop.getCurrentCallbackAddress(); // c++ may not return null

   cbServer_ = initCbServer(getLoginName(), addr.getType(), addr.getVersion());

   addr.setAddress(cbServer_->getCbAddress());
   addr.setType(cbServer_->getCbProtocol());
   prop.setCallbackAddress(addr);
   connectQos_.setCbQueueProperty(prop);
   log_.info(ME, "Callback settings: " + prop.getSettings());
}

I_CallbackServer*
XmlBlasterAccess::initCbServer(const string& loginName, const string& type, const string& version)
{
   log_.error(ME, "initCbServer not implemented yet");
   if (log_.TRACE) log_.trace(ME, string("Using 'client.cbProtocol=") + type + string("' to be used by ") + getServerNodeId() + string(", trying to create the callback server ..."));
   I_CallbackServer* server = &(global_.getCbServerPluginManager().getPlugin(type, version));
   server->initialize(loginName, *this);
   return server;
}

void
XmlBlasterAccess::initSecuritySettings(const string& secMechanism, const string& secVersion)
{
   log_.error(ME, "initSecuritySettings not implemented yet");
}

bool XmlBlasterAccess::disconnect(const string& qos)
{
   return disconnect(qos, true, true, true);
}

bool
XmlBlasterAccess::disconnect(const string& qos, bool flush, bool shutdown, bool shutdownCb)
{
   if (connection_ == NULL) return false;
   return connection_->disconnect(qos);
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

string
XmlBlasterAccess::subscribe(const string& xmlKey, const string& qos)
{
   return connection_->subscribe(xmlKey, qos);
}

vector<MessageUnit>
XmlBlasterAccess::get(const string&  xmlKey, const string& qos)
{
   return connection_->get(xmlKey, qos);
}

vector<string>
XmlBlasterAccess::unSubscribe(const string&  xmlKey, const string&  qos)
{
    return connection_->unSubscribe(xmlKey, qos);
}

string XmlBlasterAccess::publish(const MessageUnit& msgUnit)
{
   return connection_->publish(msgUnit);
}

void XmlBlasterAccess::publishOneway(const vector<MessageUnit>& msgUnitArr)
{
   return connection_->publishOneway(msgUnitArr);
}

vector<string>
XmlBlasterAccess::publishArr(const vector<MessageUnit>& msgUnitArr)
{
   return connection_->publishArr(msgUnitArr);
}

vector<string> XmlBlasterAccess::erase(const string& xmlKey, const string& qos)
{
   return connection_->erase(xmlKey, qos);
}

string
XmlBlasterAccess::update(const string &sessionId, UpdateKey &updateKey, void *content, long contentSize, UpdateQos &updateQos)
{
   if (updateClient_)
      return updateClient_->update(sessionId, updateKey, content, contentSize, updateQos);
   std::cout << "UPDATE INVOCATION" << std::endl;
   return "<qos><state id='OK'/></qos>";
}

void XmlBlasterAccess::usage()
{
   string text = string("\n");
   text += string("Choose a connection protocol:\n");
   text += string("   -client.protocol    Specify a protocol to talk with xmlBlaster, 'SOCKET' or 'IOR' or 'RMI' or 'SOAP' or 'XML-RPC'.\n");
   text += string("                       Current setting is '") + global_.getProperty().getStringProperty("client.protocol", "IOR") + string("'. See below for protocol settings.\n");
   text += string("                       Example: java MyApp -client.protocol RMI -rmi.hostname 192.168.10.34\n");
   text += string("\n");
   text += string("Security features:\n");
   text += string("   -Security.Client.DefaultPlugin \"gui,1.0\"\n");
   text += string("                       Force the given authentication schema, here the GUI is enforced\n");
   text += string("                       Clients can overwrite this with ConnectQos.java\n");

   std::cout << text << std::endl;
}

}}} // namespaces


#ifdef _XMLBLASTER_CLASSTEST

#include <util/PlatformUtils.hpp>
#include <util/Timestamp.h>

using namespace std;
using namespace org::xmlBlaster::client;

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
       ConnectQos qos(glob);
       ConnectReturnQos retQos = xmlBlasterAccess.connect(qos, NULL);
       log.info("", "Successfully connect to xmlBlaster");
       MessageUnit msgUnit(string("<key oid='HelloWorld'/>"), string("Hi"), string("<qos/>"));

       string pubRetQos = xmlBlasterAccess.publish(msgUnit);
       log.info("", "Successfully published a message to xmlBlaster");
       log.info("", "Sleeping");

       Timestamp delay = 10000000000ll; // 10 seconds
       TimestampFactory::getInstance().sleep(delay);
   }
   catch (XmlBlasterException &ex) {
      std::cout << ex.toXml() << std::endl;
   }
   return 0;
}

#endif
