/*------------------------------------------------------------------------------
Name:      ConnectQosFactory.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Factory for ConnectQosData (for ConnectReturnQos and ConnectQos)
------------------------------------------------------------------------------*/

#include <util/qos/ConnectQosFactory.h>
#include <util/Global.h>
// #include <util/XmlBlasterException>
#include <boost/lexical_cast.hpp>

namespace org { namespace xmlBlaster { namespace util { namespace qos {

using namespace org::xmlBlaster::authentication;
using namespace org::xmlBlaster::util;
using namespace boost;


ConnectQosFactory::ConnectQosFactory(Global& global)
   : SaxHandlerBase(global),
     ME("ConnectQosFactory"),
     sessionQosFactory_(global),
     securityQosFactory_(global),
     queuePropertyFactory_(global),
     addressFactory_(global),
//     securityQos_(global),
//     serverRef_("IOR"),
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

bool ConnectQosFactory::getBoolFromString(const string& val) const
{
   bool ret   = false;
   char *help = NULL;
   try {
      help = charTrimmer_.trim(val.c_str());
      if (help != NULL) {
         if (string("true") == string(help)) ret = true;
      }
   }
   catch (...) {
   }
   delete help;
   return ret;
}

void ConnectQosFactory::characters(const XMLCh* const ch, const unsigned int length)
{
   if (subFactory_) {
      subFactory_->characters(ch, length);
      return;
   }

   if (inSession_) {
      sessionQosFactory_.characters(ch, length);
      return;
   }

   char *chHelper = XMLString::transcode(ch);
   if (chHelper != NULL) {
      char *trimmedCh = charTrimmer_.trim(chHelper);
      delete chHelper;
      if (trimmedCh != NULL) {
         character_ += string(trimmedCh);
         if (log_.TRACE)
            log_.trace(ME, string("characters, character:'") + character_ + string("'"));
         delete trimmedCh;
      }
   }
}

void ConnectQosFactory::startElement(const XMLCh* const name, AttributeList& attrs) {
   log_.call(ME, "startElement");
   if (log_.TRACE) {
      char *help = XMLString::transcode(name);
      log_.trace(ME, string("startElement. name:'") + string(help) + string("' character: '") + character_ + string("'"));
      delete help;
   }

   if (SaxHandlerBase::caseCompare(name, "qos")) {
     connectQos_ = ConnectQos(global_); // kind of reset
     return;
   }

   if (SaxHandlerBase::caseCompare(name, "queue")) {
      subFactory_ = &queuePropertyFactory_;
   }

   if (subFactory_) {
      subFactory_->startElement(name, attrs);
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "securityService")) {
      inSecurityService_ = true;
      character_ = SaxHandlerBase::getStartElementAsString(name, attrs);
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "session")) {
      inSession_ = true;
      sessionQosFactory_.reset();
   }
   if (inSession_) {
      sessionQosFactory_.startElement(name, attrs);
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "ptp")) {
      character_.erase();
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "isClusterNode")) {
      character_.erase();
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "duplicateUpdates")) {
      character_.erase();
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "serverRef")) {
      character_.erase();
      inServerRef_ = true;
      int len = attrs.getLength();
      for (int i = 0; i < len; i++) {
         if (SaxHandlerBase::caseCompare(attrs.getName(i), "type")) {
            serverRefType_ = SaxHandlerBase::getStringValue(attrs.getValue(i));
         }
      }
   }
}

void ConnectQosFactory::endElement(const XMLCh* const name) {
   log_.trace(ME, "endElement");
   if (log_.TRACE) {
      char *help = XMLString::transcode(name);
      log_.trace(ME, string("endElement. name:'") + string(help) + string("' character: '") + character_ + string("'"));
      delete help;
   }

   if (subFactory_) {
      subFactory_->endElement(name);
      if (SaxHandlerBase::caseCompare(name, "queue")) {
         // determine wether it is a callback or a client queue ...
         QueuePropertyBase help = queuePropertyFactory_.getQueueProperty();
         if (help.getRelating() == Constants::RELATING_CLIENT) {
            QueueProperty prop = help;
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

   if (SaxHandlerBase::caseCompare(name, "qos")) {
     character_.erase();
     return;
   }

   if (SaxHandlerBase::caseCompare(name, "securityService")) {
      inSecurityService_ = false;
      character_ += string("\n</securityService>\n");
//      securityQos_ = securityQosFactory_.parse(character_);
      connectQos_.setSecurityQos(securityQosFactory_.parse(character_));
      character_.erase();
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "session")) {
      sessionQosFactory_.endElement(name);
      inSession_ = false;
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "ptp")) {
      connectQos_.setPtp(getBoolFromString(character_));
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "isClusterNode")) {
      connectQos_.setClusterNode(getBoolFromString(character_));
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "duplicateUpdates")) {
      connectQos_.setDuplicateUpdates(getBoolFromString(character_));
      return;
   }

   if (inSession_) {
      sessionQosFactory_.endElement(name);
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "serverRef")) {
      inServerRef_ = false;
      string address = character_;
//      serverRef_ = ServerRef(serverRefType_, address);
      connectQos_.addServerRef(ServerRef(serverRefType_, address));
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

#include <util/PlatformUtils.hpp>

using namespace std;
using namespace org::xmlBlaster::util::qos;

/** For testing: java org.xmlBlaster.authentication.plugins.simple.SecurityQos */
int main(int args, char* argv[])
{
    // Init the XML platform
    try
    {
       XMLPlatformUtils::Initialize();
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
       string("   <queue relating='client' type='CACHE' version='1.0' maxMsg='1000' maxBytes='4000' onOverflow='exception'>\n") +
       string("      <address type='IOR' sessionId='4e56890ghdFzj0'>\n") +
       string("         IOR:10000010033200000099000010....\n") +
       string("      </address>\n") +
       string("   </queue>\n") +
       string("   <!-- The server side callback queue: -->\n") +
       string("   <queue relating='callback' type='CACHE' version='1.0' maxMsg='1000' maxBytes='4000' onOverflow='deadMessage'>\n") +
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
//       cout << "server ref   : " << data.getServerRef().toXml() << endl;
       cout << "securityQos  : " << data.getSecurityQos().toXml() << endl;
       cout << "sessionQos   : " << data.getSessionQos().toXml() << endl;

       ServerRef ref = data.getServerRef();
       cout << "server reference:  " << ref.toXml() << endl;

    }

    catch(const XMLException& toCatch)
    {


       cout << "Error during platform init! Message:\n";
       cout <<toCatch.getMessage() << endl;
       return 1;
    }

   return 0;
}

#endif
