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
     connectQos_(global)
{
   log_.call(ME, "constructor");
   prep();
}

/*
ConnectQosFactory::~ConnectQosFactory()
{
   log_.call(ME, "destructor");
   if (securityQos_ != NULL) delete securityQos_;
   if (serverRef_ != NULL) delete serverRef_;
}
*/

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
   log_.call(ME, "startElement");
   if (log_.trace()) {
      log_.trace(ME, string("startElement. name:'") + name + string("' character: '") + character_ + string("'"));
   }

   if (name.compare("qos") == 0) {
     connectQos_ = ConnectQos(global_); // kind of reset
     return;
   }

   if (name.compare("queue") == 0) {
      subFactory_ = &queuePropertyFactory_;
   }

   if (subFactory_) {
      subFactory_->startElement(name, attrs);
      return;
   }

   if (name.compare("securityService") == 0) {
      inSecurityService_ = true;
      character_ = getStartElementAsString(name, attrs);
      return;
   }

   if (name.compare("session") == 0) {
      inSession_ = true;
      sessionQosFactory_.reset();
   }
   if (inSession_) {
      sessionQosFactory_.startElement(name, attrs);
      return;
   }

   if (name.compare("ptp") == 0) {
      connectQos_.setPtp(true);
      character_.erase();
      return;
   }

   if (name.compare("clusterNode") == 0) {
      connectQos_.setClusterNode(true);
      character_.erase();
      return;
   }

   if (name.compare("duplicateUpdates") == 0) {
      connectQos_.setDuplicateUpdates(true);
      character_.erase();
      return;
   }

   if (name.compare("serverRef") == 0) {
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
      character_.erase();
      AttributeMap::const_iterator iter = attrs.find("name");
      if (iter != attrs.end()) clientPropertyKey_ = (*iter).second;
   }
   
}

void ConnectQosFactory::endElement(const string &name) {
   log_.trace(ME, "endElement");
   if (log_.trace()) {
      log_.trace(ME, string("endElement. name:'") + name + string("' character: '") + character_ + string("'"));   
   }

   if (subFactory_) {
      subFactory_->endElement(name);
      if (name.compare("queue") == 0) {
         // determine wether it is a callback or a client queue ...
         QueuePropertyBase help = queuePropertyFactory_.getQueueProperty();
         if (help.getRelating() == Constants::RELATING_CLIENT) {
            ClientQueueProperty prop = help;
            connectQos_.addClientQueueProperty(prop);
         }
         else {
             CbQueueProperty prop = help;
             connectQos_.setSessionCbQueueProperty(prop);
         }
         subFactory_ = NULL;
      }
      return;
   }

   if (name.compare("qos") == 0) {
     character_.erase();
     return;
   }

   if (name.compare("securityService") == 0) {
      inSecurityService_ = false;
      character_ += string("\n</securityService>\n");
//      securityQos_ = securityQosFactory_.parse(character_);
      connectQos_.setSecurityQos(securityQosFactory_.parse(character_));
      character_.erase();
      return;
   }

   if (name.compare("session") == 0) {
      sessionQosFactory_.endElement(name);
      inSession_ = false;
      return;
   }

   if (name.compare("ptp") == 0) {
      connectQos_.setPtp(StringTrim::isTrueTrim(character_));
      character_.erase();
      return;
   }

   if (name.compare("clusterNode") == 0) {
      connectQos_.setClusterNode(StringTrim::isTrueTrim(character_));
      character_.erase();
      return;
   }

   if (name.compare("duplicateUpdates") == 0) {
      connectQos_.setDuplicateUpdates(StringTrim::isTrueTrim(character_));
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
      connectQos_.addServerRef(ServerRef(serverRefType_, address));
      character_.erase();
   }

   if (name.compare("clientProperty") == 0) {
      connectQos_.addClientProperty(clientPropertyKey_, character_);
      character_.erase();
   }

   return;
}


ConnectQosData ConnectQosFactory::readObject(const string& qos)
{
   // this should be synchronized here ....
//   userId_ = "";
   init(qos);
//   ConnectQosData data(global_);
//   connectQos_.setSecurityQos(securityQos_);
//   connectQos_.addServerRef(serverRef_);
   connectQos_.setSessionQos(sessionQosFactory_.getData());
//   connectQos_.setPtp(isPtp_);

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
   ConnectQosData data = factory.readObject(qos);
   cout << "sessionId    : " << data.getSecretSessionId() << endl;
   cout << "userId       : " << data.getUserId() << endl;
   cout << " type: " << data.getCallbackType() << endl;
   cout << "is ptp       : " << data.getBoolAsString(data.getPtp()) << endl;
   cout << "securityQos  : " << data.getSecurityQos().toXml() << endl;
   cout << "sessionQos   : " << data.getSessionQos().toXml() << endl;

   ServerRef ref = data.getServerRef();
   cout << "server reference:  " << ref.toXml() << endl;

   return 0;
}

#endif
