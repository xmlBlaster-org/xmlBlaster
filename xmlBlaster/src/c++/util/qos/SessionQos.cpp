/*------------------------------------------------------------------------------
Name:      SessionQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Factory for SessionQosData (for ConnectReturnQos and ConnectQos)
------------------------------------------------------------------------------*/

#include <util/qos/SessionQos.h>
// #include <util/XmlBlasterException>
#include <stdlib.h>
#include <boost/lexical_cast.hpp>

namespace org { namespace xmlBlaster { namespace util { namespace qos {

using namespace org::xmlBlaster::util;
using namespace std;
using boost::lexical_cast;


/*---------------------------- SessionQosData --------------------------------*/

SessionQosData::SessionQosData()
{
   timeout_       = 86400000;
   maxSessions_   = 10;
   clearSessions_ = false;
   name_          = "";
   sessionId_     = "";
   isDirty_       = true;
}

long SessionQosData::getTimeout() const
{
   return timeout_;
}

void SessionQosData::setTimeout(long timeout)
{
   timeout_ = timeout;
   isDirty_ = true;
}

int SessionQosData::getMaxSessions() const
{
   return maxSessions_;
}

void SessionQosData::setMaxSessions(int maxSessions)
{
   maxSessions_ = maxSessions;
   isDirty_ = true;
}

bool SessionQosData::getClearSessions() const
{
   return clearSessions_;
}

void SessionQosData::setClearSessions(bool clearSessions)
{
   clearSessions_ = clearSessions;
   isDirty_       = true;
}

string SessionQosData::getName() const
{
   return name_;
}

void SessionQosData::setName(const string& name)
{
   name_ = name;
   isDirty_ = true;
}

string SessionQosData::getSessionId() const
{
   return sessionId_;
}

void SessionQosData::setSessionId(const string& sessionId)
{
   sessionId_ = sessionId;
   isDirty_ = true;
}

void SessionQosData::setLiteral(const string& literal)
{
   literal_ = literal;
   isDirty_ = false;
}

string SessionQosData::toXml() const
{
   if (isDirty_) return SessionQosFactory::writeObject(*this);
   return literal_;
}



/*-------------------------- SessionQosFactory -------------------------------*/

SessionQosFactory::SessionQosFactory(int args, const char * const argc[])
   : XmlQoSBase(args, argc), ME("SessionQosFactory"), sessionQos_()
{
   log_.call(ME, "constructor");
   prep(args, argc);
}

void SessionQosFactory::characters(const XMLCh* const ch, const unsigned int length)
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

void SessionQosFactory::startElement(const XMLCh* const name, AttributeList& attrs) {
   log_.call(ME, "startElement");
   if (log_.TRACE) {
      log_.trace(ME, string("startElement. name:'") + string(XMLString::transcode(name)) + string("' character: '") + character_ + string("'"));
   }

   if (util::XmlQoSBase::startElementBase(name, attrs)) return;

   if (SaxHandlerBase::caseCompare(name, "session")) {
      // get all attributes which are needed ...
      int len = attrs.getLength();
      for (int i = 0; i < len; i++) {
         if (SaxHandlerBase::caseCompare(attrs.getName(i), "timeout")) {
            sessionQos_.timeout_ = SaxHandlerBase::getLongValue(attrs.getValue(i));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "maxSessions")) {
            sessionQos_.maxSessions_ = SaxHandlerBase::getIntValue(attrs.getValue(i));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "clearSessions")) {
            string help = SaxHandlerBase::getStringValue(attrs.getValue(i));
            if (help == "true") sessionQos_.clearSessions_ = true;
            else sessionQos_.clearSessions_ = false;
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "name")) {
            sessionQos_.name_ = SaxHandlerBase::getStringValue(attrs.getValue(i));
         }
      }
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "sessionId")) {
      character_.erase();
   }
}

void SessionQosFactory::endElement(const XMLCh* const name) {
   log_.trace(ME, "endElement");
   if (util::XmlQoSBase::endElementBase(name)) return;

   if (SaxHandlerBase::caseCompare(name, "session")) {
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "sessionId")) {
      sessionQos_.sessionId_ = character_;
   }
}

void SessionQosFactory::reset()
{
   sessionQos_ = SessionQosData();
}

SessionQosData SessionQosFactory::getData() const
{
   return sessionQos_;
}

SessionQosData SessionQosFactory::readObject(const string& qos)
{
   // this should be synchronized here ....
   init(qos);
   return sessionQos_;
}


string SessionQosFactory::writeObject(const SessionQosData& qos)
{
   string ret = string("<session timeout='") + lexical_cast<string>(qos.getTimeout()) +
                string("' maxSessions='") + lexical_cast<string>(qos.getMaxSessions()) +
                string("' clearSessions='") + lexical_cast<string>(qos.getClearSessions()) +
                string("' name='")  + qos.getName() + "'>\n";
   ret += string("  <sessionId>") + qos.getSessionId() + string("</sessionId>\n");
   ret += string("</session>\n");
   return ret;
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

       string qos = "<session timeout='86400000' maxSessions='10' \n" +
             string("         clearSessions='false'\n") +
             string("         name='/node/http:/client/ticheta/-3'>\n") +
             string("   <sessionId>IIOP:01110728321B0222011028</sessionId>\n") +
             string("</session>\n");

       SessionQosFactory factory(args, argv);
       SessionQosData data = factory.readObject(qos);

       string ret = factory.writeObject(data);
       cout << ret << endl;
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
