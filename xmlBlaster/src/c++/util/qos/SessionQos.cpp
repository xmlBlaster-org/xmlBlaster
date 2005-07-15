/*------------------------------------------------------------------------------
Name:      SessionQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Factory for SessionQosData (for ConnectReturnQos and ConnectQos)
------------------------------------------------------------------------------*/

#include <util/qos/SessionQos.h>
#include <stdlib.h>
#include <util/lexical_cast.h>
#include <util/StringStripper.h>
#include <util/StringTrim.h>
#include <util/Global.h>

namespace org { namespace xmlBlaster { namespace util { namespace qos {

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::parser;
using namespace std;


/*---------------------------- SessionQosData --------------------------------*/

SessionQosData::SessionQosData(Global& global, const string& defaultUserName, long publicSessionId)
  : ReferenceCounterBase(), ME("SessionQosData"), sessionName_(0), global_(global)
{
   initialize();   
   SessionName *p = new SessionName(global, defaultUserName, publicSessionId);
   SessionNameRef r(p);
   sessionName_ = r;
}

SessionQosData::SessionQosData(Global& global, const string& absoluteName)
  : ReferenceCounterBase(), ME("SessionQosData"), sessionName_(0), global_(global)
{
   initialize();
   SessionName *p = new SessionName(global, absoluteName);
   SessionNameRef r(p);
   sessionName_ = r;
}

SessionQosData::~SessionQosData()
{
}

void SessionQosData::copy(const SessionQosData& data)
{
   SessionName *p = new SessionName(global_, data.sessionName_->getAbsoluteName());
   SessionNameRef r(p);
   sessionName_ = r;

   timeout_       = data.timeout_;
   maxSessions_   = data.maxSessions_;
   clearSessions_ = data.clearSessions_;
   reconnectSameClientOnly_ = data.reconnectSameClientOnly_;
   sessionId_     = data.sessionId_;
}


void SessionQosData::initialize()
{
   timeout_ = global_.getProperty().getLongProperty("session.timeout", 86400000);
   maxSessions_ = global_.getProperty().getIntProperty("session.maxSessions", 10);
   clearSessions_ = global_.getProperty().getBoolProperty("session.clearSessions", false);
   reconnectSameClientOnly_ = global_.getProperty().getBoolProperty("session.reconnectSameClientOnly", false);
   sessionId_ = global_.getProperty().getStringProperty("session.secretSessionId", "");
}


SessionQosData::SessionQosData(const SessionQosData& data) :
  ReferenceCounterBase(), ME(data.ME), sessionName_(0), global_(data.global_)
{
   copy(data);
}

SessionQosData& SessionQosData::operator =(const SessionQosData& data)
{
  copy(data);
  return *this;
}


SessionNameRef SessionQosData::getSessionName()
{
   return sessionName_;
}

void SessionQosData::setAbsoluteName(const string& name)
{
   sessionName_->setAbsoluteName(name);
}

string SessionQosData::getRelativeName() const
{
   return sessionName_->getRelativeName();
}

string SessionQosData::getAbsoluteName() const
{
   return sessionName_->getAbsoluteName();
}

string SessionQosData::getClusterNodeId() const
{
   return sessionName_->getClusterNodeId();
}

void SessionQosData::setClusterNodeId(const string& clusterNodeId)
{
   return sessionName_->setClusterNodeId(clusterNodeId);
}

string SessionQosData::getSubjectId() const
{
   return sessionName_->getSubjectId();
}

void SessionQosData::setSubjectId(const string& subjectId)
{
   return sessionName_->setSubjectId(subjectId);
}

long SessionQosData::getPubSessionId() const
{
   return sessionName_->getPubSessionId();
}

void SessionQosData::setPubSessionId(const long pubSessionId)
{
   return sessionName_->setPubSessionId(pubSessionId);
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

bool SessionQosData::getReconnectSameClientOnly() const
{
   return reconnectSameClientOnly_;
}

void SessionQosData::setReconnectSameClientOnly(bool reconnectSameClientOnly)
{
   reconnectSameClientOnly_ = reconnectSameClientOnly;
}

string SessionQosData::getSecretSessionId() const
{
   return sessionId_;
}

void SessionQosData::setSecretSessionId(const string& sessionId)
{
   sessionId_ = sessionId;
}

string SessionQosData::toXml(const string& extraOffset) const
{
   string offset = Constants::OFFSET + extraOffset;
   string ret;
   
   ret += offset + string("<session");
   ret += string(" name='")  + getAbsoluteName() + string("'");
   ret += string(" timeout='") + lexical_cast<std::string>(getTimeout()) + string("'");
   ret += string(" maxSessions='") + lexical_cast<std::string>(getMaxSessions()) + string("'");
   ret += string(" clearSessions='") + Global::getBoolAsString(clearSessions_) + string("'");
   ret += string(" reconnectSameClientOnly='") + Global::getBoolAsString(reconnectSameClientOnly_) + string("'");
   
   if (!sessionId_.empty()) {
      ret += string(" sessionId='") + sessionId_ + string("'");
   }
   ret += string("/>\n");
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

void SessionQosFactory::characters(const string &ch)
{
   string trimmedCh = StringTrim::trim(ch);
   character_ += trimmedCh;
   if (log_.trace())
      log_.trace(ME, string("characters, character:'") + ch + string("'"));
}

void SessionQosFactory::startElement(const string &name, const AttributeMap& attrs) {
   if (log_.call()) log_.call(ME, "startElement: " + getStartElementAsString(name, attrs));

   if (util::XmlQoSBase::startElementBase(name, attrs)) return;

   if (name.compare("session") == 0) {
      // get all attributes which are needed ...
      AttributeMap::const_iterator iter = attrs.begin();
      while (iter != attrs.end()) {
         string tmpName = (*iter).first;
         string tmpValue = (*iter).second;
         if (tmpName.compare("name") == 0) {
            sessionQos_->setAbsoluteName(tmpValue);
         }
         else if (tmpName.compare("timeout") == 0) {
            sessionQos_->timeout_ = XmlHandlerBase::getLongValue(tmpValue);
         }
         else if (tmpName.compare("maxSessions") == 0) {
            sessionQos_->maxSessions_ = XmlHandlerBase::getIntValue(tmpValue);
         }
         else if (tmpName.compare("clearSessions") == 0) {
            sessionQos_->clearSessions_ = StringTrim::isTrueTrim(tmpValue);
         }
         else if (tmpName.compare("reconnectSameClientOnly") == 0) {
            sessionQos_->reconnectSameClientOnly_ = StringTrim::isTrueTrim(tmpValue);
         }
         else if (tmpName.compare("sessionId") == 0) {
            sessionQos_->sessionId_ = tmpValue;
         }
         iter++;
      }
      return;
   }
}

void SessionQosFactory::endElement(const string &name) {
   log_.trace(ME, "endElement");
   if (util::XmlQoSBase::endElementBase(name)) return;

   if (name.compare("session") == 0) {
      return;
   }
}

void SessionQosFactory::reset()
{
   if (sessionQos_ != NULL) delete sessionQos_;
   sessionQos_ = NULL;
   sessionQos_ = new SessionQosData(global_);
}

const SessionQosData& SessionQosFactory::getData() const
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

   /**
    * Get a usage string for the connection parameters
    */
   string SessionQosData::usage()
   {
      string text;
      text += string("Control my login session settings:\n");
      text += string("   -session.name []\n");
      text += string("                       The name for login, e.g. 'joe' or with public session ID 'joe/2'.\n");
      text += string("   -session.timeout ["+lexical_cast<std::string>((int)Constants::DAY_IN_MILLIS)+"], defaults to one day.\n");
      text += string("                       How long lasts our login session in milliseconds, 0 is forever.\n");
      text +=        "   -session.maxSessions [10]\n";
      text += string("                       Maximum number of simultanous logins per client.\n");
      text += string("   -session.clearSessions [false]\n");
      text += string("                       Kill other sessions running under my login name.\n");
      text += string("   -session.reconnectSameClientOnly [false]\n");
      text += string("                       Only creator client may reconnect to session.\n");
      text += string("   -session.secretSessionId []\n");
      text += string("                       The secret sessionId.\n");
      return text;
   }
}}}} // namespaces


#ifdef _XMLBLASTER_CLASSTEST

using namespace std;
using namespace org::xmlBlaster::util::qos;

/** For testing: java org.xmlBlaster.authentication.plugins.simple.SecurityQos */
int main(int args, char* argv[])
{
    {
       string qos = "<session name='/node/http:/client/ticheta/-3' timeout='86400000' maxSessions='10' \n" +
             string("         clearSessions='false' reconnectSameClientOnly='false' sessionId='IIOP:01110728321B0222011028'/>\n");

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
   return 0;
}

#endif
