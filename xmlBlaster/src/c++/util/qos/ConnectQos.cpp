/*------------------------------------------------------------------------------
Name:      ConnectQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Factory for ConnectQosData (for ConnectReturnQos and ConnectQos)
------------------------------------------------------------------------------*/

#include <util/qos/ConnectQos.h>
// #include <util/XmlBlasterException>

namespace org { namespace xmlBlaster { namespace util { namespace qos {

using namespace org::xmlBlaster::authentication;
using namespace org::xmlBlaster::util;

/*---------------------------- ConnectQosData --------------------------------*/

ConnectQosData::ConnectQosData() : securityQos_(), serverRef_("")
{
   sessionId_ = "";
   isDirty_ = true;
}

void ConnectQosData::setLiteral(const string& literal)
{
   literal_ = literal;
   isDirty_ = false;
}

void ConnectQosData::setSessionId(const string& sessionId)
{
   sessionId_ = sessionId;
   isDirty_ = true;
}

string ConnectQosData::getSessionId() const
{
   return sessionId_;
}

string ConnectQosData::getUserId() const
{
   return securityQos_.getUserId();
}

string ConnectQosData::getCallbackType() const
{
   return serverRef_.getType();
}

void ConnectQosData::setSecurityQos(const SecurityQos& securityQos)
{
   securityQos_ = securityQos;
   isDirty_ = true;
}

SecurityQos ConnectQosData::getSecurityQos() const
{
   return securityQos_;
}

void ConnectQosData::setServerRef(const ServerRef& serverRef)
{
   serverRef_ = serverRef;
   isDirty_ = true;
}

ServerRef ConnectQosData::getServerRef() const
{
   return serverRef_;
}

string ConnectQosData::toXml() const
{
   if (isDirty_) ConnectQosFactory::writeObject(*this);
   return literal_;
}


/*-------------------------- ConnectQosFactory -------------------------------*/

ConnectQosFactory::ConnectQosFactory(int args, const char * const argc[])
   : XmlQoSBase(args, argc), ME("ConnectQosFactory")
{
   log_.call(ME, "constructor");
   prep(args, argc);
}

ConnectQosFactory::~ConnectQosFactory()
{
   log_.call(ME, "destructor");
   if (securityQos_ != NULL) delete securityQos_;
   if (serverRef_ != NULL) delete serverRef_;
}

void ConnectQosFactory::characters(const XMLCh* const ch, const unsigned int length)
{
   char *chHelper = XMLString::transcode(ch);
   if (chHelper != NULL) {
      char *trimmedCh = trim_.trim(chHelper);
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
      log_.trace(ME, string("startElement. name:'") + string(XMLString::transcode(name)) + string("' character: '") + character_ + string("'"));
   }

   if (util::XmlQoSBase::startElementBase(name, attrs)) return;

   if (SaxHandlerBase::caseCompare(name, "securityService")) {
      inSecurityService_ = true;
      character_ = SaxHandlerBase::getStartElementAsString(name, attrs);
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "callback")) {
      character_.erase();
      inCallback_ = true;
      int len = attrs.getLength();
      for (int i = 0; i < len; i++) {
         if (SaxHandlerBase::caseCompare(attrs.getName(i), "type")) {
            callbackType_ = trim_.trim(XMLString::transcode(attrs.getValue(i))); // .trim();
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "sessionId")) {
            sessionId_ = trim_.trim(XMLString::transcode(attrs.getValue(i))); //.trim();
         }
      }
   }
}

void ConnectQosFactory::endElement(const XMLCh* const name) {
   log_.trace(ME, "endElement");
   if (log_.TRACE)
      log_.trace(ME, string("endElement. name:'") + string(XMLString::transcode(name)) + string("' character: '") + character_ + string("'"));
   if (util::XmlQoSBase::endElementBase(name)) return;

   if (SaxHandlerBase::caseCompare(name, "securityService")) {
      try {
         inSecurityService_ = false;
         delete securityQos_;
         securityQos_ = new SecurityQos(args_, argc_);
         character_ += string("\n</securityService>\n");
         securityQos_->parse(character_);
         character_.erase();
      }
      catch (...) {
         delete securityQos_;
         securityQos_ = NULL;
         log_.error(ME, "error when parsing security Service");
         // throw exception here
      }
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "callback")) {
      try {
         inCallback_ = false;
         string address = character_;
         serverRef_ = new ServerRef(callbackType_, address);
      }
      catch (...) {
         delete securityQos_;
         securityQos_ = NULL;
         log_.error(ME, "error when parsing security Service");
         // throw exception here
      }
      character_.erase();
   }

   return;
}


ConnectQosData ConnectQosFactory::readObject(const string& qos)
{
   // this should be synchronized here ....
   sessionId_ = "";
   userId_ = "";
   delete securityQos_ ;
   delete serverRef_;
   securityQos_ = NULL;
   serverRef_ = NULL;
   init(qos);
   ConnectQosData data;
   data.setSessionId(sessionId_);
   if (securityQos_ != NULL) data.setSecurityQos(*securityQos_);
   if (serverRef_ != NULL) data.setServerRef(*serverRef_);
   data.setLiteral(qos);
   return data;
}


string ConnectQosFactory::writeObject(const ConnectQosData& qos)
{
   std::cout << "ConnectQosFactory::writeObject not implemented yet" << std::endl;
   return string("");
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
       string("   <queue relating='client' type='CACHE' version='1.0' maxMsg='1000' maxSize='4000' onOverflow='exception'>\n") +
       string("      <address type='IOR' sessionId='4e56890ghdFzj0'>\n") +
       string("         IOR:10000010033200000099000010....\n") +
       string("      </address>\n") +
       string("   </queue>\n") +
       string("   <!-- The server side callback queue: -->\n") +
       string("   <queue relating='session' type='CACHE' version='1.0' maxMsg='1000' maxSize='4000' onOverflow='deadMessage'>\n") +
       string("      <callback type='IOR' sessionId='4e56890ghdFzj0'>\n") +
       string("         IOR:10000010033200000099000010....\n") +
       string("         <burstMode collectTime='400' />\n") +
       string("      </callback>\n") +
       string("   </queue>\n") +
       string("</qos>\n");

       ConnectQosFactory factory(args, argv);
       ConnectQosData data = factory.readObject(qos);
       cout << "sessionId    : " << data.getSessionId() << endl;
       cout << "userId       : " << data.getUserId() << endl;
       cout << "callback type: " << data.getCallbackType() << endl;
       cout << "server ref   : " << data.getServerRef().toXml() << endl;
       cout << "securityQos  : " << data.getSecurityQos().toXml() << endl;

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
