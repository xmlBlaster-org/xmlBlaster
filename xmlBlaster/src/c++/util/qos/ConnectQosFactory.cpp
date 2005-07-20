/*------------------------------------------------------------------------------
Name:      ConnectQosFactory.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Factory for ConnectQosData (for ConnectReturnQos and ConnectQos)
------------------------------------------------------------------------------*/

#include <util/qos/ConnectQosFactory.h>
#include <util/Global.h>
// #include <util/XmlBlasterException>
#include <util/lexical_cast.h>

namespace org { namespace xmlBlaster { namespace util { namespace qos {

using namespace std;
using namespace org::xmlBlaster::authentication;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::parser;
using namespace org::xmlBlaster::util::qos::storage;

ConnectQosFactory::ConnectQosFactory(Global& global)
   : XmlHandlerBase(global),
     ME("ConnectQosFactory"),
     sessionQosFactory_(global),
     securityQosFactory_(global),
     queuePropertyFactory_(global),
     addressFactory_(global),
//     securityQos_(global),
//     serverRef_(Global::getDefaultProtocol()), //"IOR"),
     clientProperty_(0),
     connectQos_((ConnectQos*)0)
{
   log_.call(ME, "constructor");
   prep();
}

void ConnectQosFactory::prep()
{
   inSecurityService_ = false;
   inServerRef_       = false;
   inSession_         = false;
   inRefreshSession_  = false;
   inInstanceId_      = false;
   inReconnected_     = false;
   inIsPersistent_    = false;
   serverRefType_     = "";
   subFactory_        = NULL;
   inClientProperty_  = false;
   inQos_             = false;
}

ConnectQosFactory::~ConnectQosFactory()
{
   log_.call(ME, "destructor");
   if (clientProperty_ != 0) {
      delete(clientProperty_);
   }
   //if (securityQos_ != NULL) delete securityQos_;
   //if (serverRef_ != NULL) delete serverRef_;
}

void ConnectQosFactory::characters(const string &ch)
{
   if (subFactory_) {
      subFactory_->characters(ch);
      return;
   }

   if (inSession_) {
      sessionQosFactory_.characters(ch);
      return;
   }

   character_ += StringTrim::trim(ch);
   if (log_.trace()) log_.trace(ME, string("characters, character:'") + character_ + string("'"));
}

void ConnectQosFactory::startElement(const string& name, const AttributeMap& attrs) {
   //if (log_.call()) log_.call(ME, "startElement: " + getStartElementAsString(name, attrs));

   if (name.compare("qos") == 0) {
     inQos_ = true;
     connectQos_ = new ConnectQos(global_); // kind of reset
     return;
   }

   if (name.compare("queue") == 0) {
      if (!inQos_) return;
      subFactory_ = &queuePropertyFactory_;
   }

   if (subFactory_) {
      subFactory_->startElement(name, attrs);
      return;
   }

   if (name.compare("securityService") == 0) {
      if (!inQos_) return;
      inSecurityService_ = true;
      character_ = getStartElementAsString(name, attrs);
      return;
   }

   if (name.compare("session") == 0) {
      if (!inQos_) return;
      inSession_ = true;
      sessionQosFactory_.reset();
   }
   if (inSession_) {
      sessionQosFactory_.startElement(name, attrs);
      return;
   }

   if (name.compare("ptp") == 0) {
      if (!inQos_) return;
      connectQos_->setPtp(true);
      character_.erase();
      return;
   }

   if (name.compare("clusterNode") == 0) {
      if (!inQos_) return;
      connectQos_->setClusterNode(true);
      character_.erase();
      return;
   }

   if (name.compare("refreshSession") == 0) {
      if (!inQos_) return;
      inRefreshSession_ = true;
      connectQos_->setRefreshSession(true);
      character_.erase();
      return;
   }

   if (name.compare("duplicateUpdates") == 0) {
      if (!inQos_) return;
      connectQos_->setDuplicateUpdates(true);
      character_.erase();
      return;
   }

   if (name.compare("reconnected") == 0) {
      if (!inQos_) return;
      inReconnected_ = true;
      connectQos_->setReconnected(true);
      character_.erase();
      return;
   }

   if (name.compare("instanceId") == 0) {
      if (!inQos_) return;
      inInstanceId_ = true;
      character_.erase();
      return;
   }

   if (name.compare("persistent") == 0) {
      if (!inQos_) return;
      inIsPersistent_ = true;
      connectQos_->setPersistent(true);
      character_.erase();
      return;
   }

   if (name.compare("serverRef") == 0) {
      if (!inQos_) return;
      character_.erase();
      inServerRef_ = true;
      AttributeMap::const_iterator iter = attrs.begin();
      while (iter != attrs.end()) {
         if (  ((*iter).first).compare("type") == 0) {
            serverRefType_ = (*iter).second;
         }
         iter++;
      }
   }
   
   if (name.compare("clientProperty") == 0) {
      if (!inQos_) return;
      inClientProperty_ = true;
      character_.erase();
      string name;
      AttributeMap::const_iterator iter = attrs.find("name");
      if (iter != attrs.end()) name = (*iter).second;
      string encoding;
      iter = attrs.find("encoding");
      if (iter != attrs.end()) encoding = (*iter).second;
      string type;
      iter = attrs.find("type");
      if (iter != attrs.end()) type = (*iter).second;
      clientProperty_ = new ClientProperty(true, name, type, encoding);
   }
}

void ConnectQosFactory::endElement(const string &name) {
   //if (log_.call()) log_.call(ME, "endElement");
   //if (log_.trace()) log_.trace(ME, string("endElement. name:'") + name + string("' character: '") + character_ + string("'"));   

   if (inQos_ && subFactory_) {
      subFactory_->endElement(name);
      if (name.compare("queue") == 0) {
         // determine wether it is a callback or a client queue ...
         QueuePropertyBase help = queuePropertyFactory_.getQueueProperty();
         if (help.getRelating() == Constants::RELATING_CLIENT) {
            ClientQueueProperty prop = help;
            connectQos_->addClientQueueProperty(prop);
         }
         else {
             CbQueueProperty prop = help;
             connectQos_->setSessionCbQueueProperty(prop);
         }
         subFactory_ = NULL;
      }
      return;
   }

   if (name.compare("qos") == 0) {
     inQos_ = false;
     character_.erase();
     return;
   }

   if (!inQos_) {
      return;
   }

   if (name.compare("securityService") == 0) {
      inSecurityService_ = false;
      character_ += string("\n</securityService>\n");
//      securityQos_ = securityQosFactory_.parse(character_);
      connectQos_->setSecurityQos(securityQosFactory_.parse(character_));
      character_.erase();
      return;
   }

   if (name.compare("session") == 0) {
      sessionQosFactory_.endElement(name);
      inSession_ = false;
      return;
   }

   if (name.compare("ptp") == 0) {
      connectQos_->setPtp(StringTrim::isTrueTrim(character_));
      character_.erase();
      return;
   }

   if (name.compare("clusterNode") == 0) {
      connectQos_->setClusterNode(StringTrim::isTrueTrim(character_));
      character_.erase();
      return;
   }

   if(name.compare("refreshSession") == 0) {
      inRefreshSession_ = false;
      connectQos_->setRefreshSession(StringTrim::isTrueTrim(character_));
      character_.erase();
      return;
   }

   if (name.compare("duplicateUpdates") == 0) {
      connectQos_->setDuplicateUpdates(StringTrim::isTrueTrim(character_));
      character_.erase();
      return;
   }

   if(name.compare("instanceId") == 0) {
      inInstanceId_ = false;
      StringTrim::trim(character_);
      connectQos_->setInstanceId(character_);
      character_.erase();
      return;
   }

   if(name.compare("reconnected") == 0) {
      inReconnected_ = false;
      connectQos_->setReconnected(StringTrim::isTrueTrim(character_));
      character_.erase();
      return;
   }

   if(name.compare("persistent") == 0) {
      inIsPersistent_ = false;
      connectQos_->setPersistent(StringTrim::isTrueTrim(character_));
      character_.erase();
      return;
   }

   if (inSession_) {
      sessionQosFactory_.endElement(name);
      return;
   }

   if (name.compare("serverRef") == 0) {
      inServerRef_ = false;
      string address = character_;
//      serverRef_ = ServerRef(serverRefType_, address);
      connectQos_->addServerRef(ServerRef(serverRefType_, address));
      character_.erase();
   }

   if (name.compare("clientProperty") == 0) {
      inClientProperty_ = false;
      clientProperty_->setValueRaw(character_);
      connectQos_->addClientProperty(*clientProperty_);
      delete clientProperty_;
      clientProperty_ = 0;
      character_.erase();
   }

   return;
}


ConnectQosDataRef ConnectQosFactory::readObject(const string& qos)
{
   // this should be synchronized here ....
//   userId_ = "";
   prep();
   delete clientProperty_;
   clientProperty_ = 0;
   init(qos);
//   ConnectQosData data(global_);
//   connectQos_->setSecurityQos(securityQos_);
//   connectQos_->addServerRef(serverRef_);
   connectQos_->setSessionQos(sessionQosFactory_.getData());
//   connectQos_->setPtp(isPtp_);

   return connectQos_;
}

}}}} // namespaces


#ifdef _XMLBLASTER_CLASSTEST

using namespace std;
using namespace org::xmlBlaster::util::qos;

/** For testing: java org.xmlBlaster.authentication.plugins.simple.SecurityQos */
int main(int args, char* argv[])
{
   string qos =
   string("<qos>\n") +
   string("   <securityService type='htpasswd' version='1.0'>\n") +
   string("     <![CDATA[\n") +
   string("     <user>joe</user>\n") +
   string("     <passwd>secret</passwd>\n") +
   string("     ]]>\n") +
   string("   </securityService>\n") +
   string("   <session name='/node/heron/client/joe/-9' timeout='3600000' maxSessions='10' clearSessions='false' sessionId='4e56890ghdFzj0'/>\n") +
   string("   <ptp>true</ptp>\n") +
   string("   <!-- The client side queue: -->\n") +
   string("   <queue relating='client' type='CACHE' version='1.0' maxEntries='1000' maxBytes='4000' onOverflow='exception'>\n") +
   string("      <address type='IOR' sessionId='4e56890ghdFzj0'>\n") +
   string("         IOR:10000010033200000099000010....\n") +
   string("      </address>\n") +
   string("   </queue>\n") +
   string("   <!-- The server side callback queue: -->\n") +
   string("   <queue relating='callback' type='CACHE' version='1.0' maxEntries='1000' maxBytes='4000' onOverflow='deadMessage'>\n") +
   string("      <callback type='IOR' sessionId='4e56890ghdFzj0'>\n") +
   string("         IOR:10000010033200000099000010....\n") +
   string("         <burstMode collectTime='400' />\n") +
   string("      </callback>\n") +
   string("   </queue>\n") +
   string("   <serverRef type='IOR'>IOR:100000100332...</serverRef>\n") +
   string("</qos>\n");

   Global& glob = Global::getInstance();
   glob.initialize(args, argv);
   ConnectQosFactory factory(glob);
   ConnectQosDataRef data = factory.readObject(qos);
   cout << "sessionId    : " << data->getSecretSessionId() << endl;
   cout << "userId       : " << data->getUserId() << endl;
   cout << " type: " << data->getCallbackType() << endl;
   cout << "is ptp       : " << data->getBoolAsString(data->getPtp()) << endl;
   cout << "securityQos  : " << data->getSecurityQos().toXml() << endl;
   cout << "sessionQos   : " << data->getSessionQos().toXml() << endl;

   ServerRef ref = data->getServerRef();
   cout << "server reference:  " << ref.toXml() << endl;

   return 0;
}

#endif
