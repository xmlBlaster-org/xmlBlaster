/*------------------------------------------------------------------------------
Name:      SessionQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Factory for SessionQosData (for ConnectReturnQos and ConnectQos)
------------------------------------------------------------------------------*/

#include <util/qos/SessionQos.h>
#include <stdlib.h>
#include <boost/lexical_cast.hpp>
#include <util/StringStripper.h>
#include <util/Global.h>

namespace org { namespace xmlBlaster { namespace util { namespace qos {

using namespace org::xmlBlaster::util;
using namespace std;
using boost::lexical_cast;


/*---------------------------- SessionQosData --------------------------------*/

SessionQosData::SessionQosData(Global& global, const string& absoluteName="")
    : global_(global)
{
   timeout_ = global_.getProperty().getTimestampProperty("session.timeout", 86400000ll);
   maxSessions_ = global_.getProperty().getIntProperty("session.maxSessions", 10);
   clearSessions_ = global_.getProperty().getBoolProperty("session.clearSessions", false);
   sessionId_ = global_.getProperty().getStringProperty("session.sessionId", "");

   clusterNodeId_ = global_.getProperty().getStringProperty("session.clusterNodeId", "");
   if (clusterNodeId_ == "")
      clusterNodeId_ = global_.getProperty().getStringProperty("hostname", "");
   if (clusterNodeId_ == "")
      clusterNodeId_ = global_.getProperty().getStringProperty("HOSTNAME", "unknown");

   subjectId_ = global_.getProperty().getStringProperty("session.subjectId", "");
   if (subjectId_ == "")
      subjectId_ = global_.getProperty().getStringProperty("user", "");
   if (subjectId_ == "")
      subjectId_ = global_.getProperty().getStringProperty("USER", "unknown");

   pubSessionId_ = global_.getProperty().getStringProperty("session.pubSessionId", "1");

   string name = global_.getProperty().getStringProperty("session.name", "");
   if (name != "") setAbsoluteName(name);
   if (absoluteName != "") setAbsoluteName(absoluteName);
}

SessionQosData::SessionQosData(const SessionQosData& data) : global_(data.global_)
{
   copy(data);
}

SessionQosData& SessionQosData::operator =(const SessionQosData& data)
{
  copy(data);
  return *this;
}


void SessionQosData::setAbsoluteName(const string& name)
{
   StringStripper stripper("/");
   vector<string> help = stripper.strip(name);

   if (help.size() < 3) subjectId_ = name;
   return;

   string clusterNode = "";

   unsigned int i = 2;
   while (i<(help.size()-1)) {
      if (help[i] != "client") {
         if (i != 2) clusterNode += "/";
         clusterNode += help[i];
      }
      else break;
      i++;
   }
   i++;
   unsigned int ref = i;
   string subjectId = "";
   while (i < (help.size()-1)) {
      if (i != ref) subjectId += "/";
      subjectId += help[i];
      i++;
   }
   string pubSessionId = help[help.size()-1];

   setClusterNodeId(clusterNode);
   setSubjectId(subjectId);
   setPubSessionId(pubSessionId);
}

string SessionQosData::getRelativeName() const
{
   return string("client/") + subjectId_ + string("/") + pubSessionId_;
}

string SessionQosData::getAbsoluteName() const
{
   return string("/node/") + clusterNodeId_ + string("/") + getRelativeName();
}

string SessionQosData::getClusterNodeId() const
{
   return clusterNodeId_;
}

void SessionQosData::setClusterNodeId(const string& clusterNodeId)
{
   clusterNodeId_ = clusterNodeId;
}

string SessionQosData::getSubjectId() const
{
   return subjectId_;
}

void SessionQosData::setSubjectId(const string& subjectId)
{
    subjectId_ = subjectId;
}

string SessionQosData::getPubSessionId() const
{
   return pubSessionId_;
}

void SessionQosData::setPubSessionId(const string& pubSessionId)
{
   pubSessionId_ = pubSessionId;
}

long SessionQosData::getTimeout() const
{
   return timeout_;
}

void SessionQosData::setTimeout(long timeout)
{
   timeout_ = timeout;
}

int SessionQosData::getMaxSessions() const
{
   return maxSessions_;
}

void SessionQosData::setMaxSessions(int maxSessions)
{
   maxSessions_ = maxSessions;
}

bool SessionQosData::getClearSessions() const
{
   return clearSessions_;
}

void SessionQosData::setClearSessions(bool clearSessions)
{
   clearSessions_ = clearSessions;
}

string SessionQosData::getSessionId() const
{
   return sessionId_;
}

void SessionQosData::setSessionId(const string& sessionId)
{
   sessionId_ = sessionId;
}

string SessionQosData::toXml(const string& extraOffset, bool isClient) const
{
   string offset = extraOffset; // currently unused.
   string ret = string("<session timeout='") + lexical_cast<string>(getTimeout()) +
                string("' maxSessions='") + lexical_cast<string>(getMaxSessions()) +
                string("' clearSessions='") + lexical_cast<string>(getClearSessions());
   if (isClient) ret += string("' name='")  + getSubjectId() + "'>\n";
   else ret += string("' name='")  + getAbsoluteName() + "'>\n";
   ret += string("  <sessionId>") + getSessionId() + string("</sessionId>\n");
   ret += string("</session>\n");
   return ret;
}


/*-------------------------- SessionQosFactory -------------------------------*/

SessionQosFactory::SessionQosFactory(Global& global)
   : XmlQoSBase(global), ME("SessionQosFactory")
{
   sessionQos_ = NULL;
   log_.call(ME, "constructor");
}


SessionQosFactory::~SessionQosFactory()
{
   delete sessionQos_;
}

void SessionQosFactory::characters(const XMLCh* const ch, const unsigned int length)
{
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

void SessionQosFactory::startElement(const XMLCh* const name, AttributeList& attrs) {
   log_.call(ME, "startElement");
   if (log_.TRACE) {
      char *help = XMLString::transcode(name);
      log_.trace(ME, string("startElement. name:'") + string(help) + string("' character: '") + character_ + string("'"));
      delete help;
   }

   if (util::XmlQoSBase::startElementBase(name, attrs)) return;

   if (SaxHandlerBase::caseCompare(name, "session")) {
      // get all attributes which are needed ...
      int len = attrs.getLength();
      for (int i = 0; i < len; i++) {
         if (SaxHandlerBase::caseCompare(attrs.getName(i), "timeout")) {
            sessionQos_->timeout_ = SaxHandlerBase::getLongValue(attrs.getValue(i));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "maxSessions")) {
            sessionQos_->maxSessions_ = SaxHandlerBase::getIntValue(attrs.getValue(i));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "clearSessions")) {
            string help = SaxHandlerBase::getStringValue(attrs.getValue(i));
            if (help == "true") sessionQos_->clearSessions_ = true;
            else sessionQos_->clearSessions_ = false;
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "name")) {
            sessionQos_->setAbsoluteName(SaxHandlerBase::getStringValue(attrs.getValue(i)));
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
      sessionQos_->sessionId_ = character_;
   }
}

void SessionQosFactory::reset()
{
   if (sessionQos_ != NULL) delete sessionQos_;
   sessionQos_ = NULL;
   sessionQos_ = new SessionQosData(global_);
}

SessionQosData SessionQosFactory::getData() const
{
   return *sessionQos_;
}

SessionQosData SessionQosFactory::readObject(const string& qos)
{
   // this should be synchronized here ....
   reset();
   init(qos);
   return *sessionQos_;
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

       Global& glob = Global::getInstance();
       glob.initialize(args, argv);
       SessionQosFactory factory(glob);
       SessionQosData data = factory.readObject(qos);

       string ret = data.toXml();
       cout << ret << endl;

       cout << data.getPubSessionId() << endl;
       cout << data.getSubjectId() << endl;
       cout << data.getClusterNodeId() << endl << endl;

       SessionQosData data2(glob);
       cout << "second session qos: " << endl;
       cout << data2.toXml() << endl;
       cout << data2.getPubSessionId() << endl;
       cout << data2.getSubjectId() << endl;
       cout << data2.getClusterNodeId() << endl << endl;



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
