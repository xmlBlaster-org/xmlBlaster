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
    : ME("SessionQosData"), global_(global)
{
   initialize("", defaultUserName, publicSessionId);   
}

SessionQosData::SessionQosData(Global& global, const string& absoluteName)
    : ME("SessionQosData"), global_(global)
{
   initialize(absoluteName, "", 0);
}


void SessionQosData::initialize(const string& absoluteName, const string& defaultUserName, long publicSessionId)
{
   pubSessionId_ = publicSessionId;
   timeout_ = global_.getProperty().getLongProperty("session.timeout", 86400000);
   maxSessions_ = global_.getProperty().getIntProperty("session.maxSessions", 10);
   clearSessions_ = global_.getProperty().getBoolProperty("session.clearSessions", false);
   sessionId_ = global_.getProperty().getStringProperty("session.sessionId", "");

   if (!absoluteName.empty()) {
      setAbsoluteName(absoluteName);
      return;
   }

   string name = global_.getProperty().getStringProperty("session.name", "");
   if (!name.empty()) {
      setAbsoluteName(name);
      return;
   }

   clusterNodeId_ = global_.getProperty().getStringProperty("session.clusterNodeId", "");

   if (!defaultUserName.empty()) {
      subjectId_ = defaultUserName;
      return;
   }

   string subjectId = global_.getProperty().getStringProperty("USER", "guest");
   subjectId_ = global_.getProperty().getStringProperty("user", subjectId);
}


SessionQosData::SessionQosData(const SessionQosData& data) : ME(data.ME), global_(data.global_)
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
   pubSessionId_ = 0; // resets the value if previously set
   string relative = "";
   if (name.empty())
      throw new XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::setAbsoluteName", "the absolute name is empty");

   if (name[0] == '/') { // then it is an absolute name
      StringStripper stripper("/");
      vector<std::string> help = stripper.strip(name);
      help.erase(help.begin()); // since it is empty for sure.
      if (help.size() < 4) 
         throw new XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::setAbsoluteName", "the absolute name '" + name + "' is not allowed");
      if (help[0] == "node") clusterNodeId_ = help[1];
      else throw new XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::setAbsoluteName", "the absolute name '" + name + "' is not allowed. It should start with '/node'");
      if (help[2] != "client") 
         throw new XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::setAbsoluteName", "the absolute name '" + name + "' is not allowed. '/client' is missing");
   
      for (size_t i=3; i < help.size(); i++) {
         relative += help[i];
        if ( i < help.size()-1) relative += "/";
      }
   }
   else relative = name;

   StringStripper relStripper("/");
   vector<std::string> relHelp = relStripper.strip(relative);
   if (relHelp.empty()) {
                throw new XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::setAbsoluteName", "there is no relative name information: '" + name + "' is not allowed");
   }

   size_t ii = 0;
   if ( relHelp.size() > ii ) {
      string tmp = relHelp[ii++];
      if ( tmp == "client" ) {
         if ( relHelp.size() > ii ) {
            subjectId_ = relHelp[ii++];
         }
         else {
                      throw new XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::setAbsoluteName", "there is no relative name information: '" + name + "' is not allowed");
         }
      }
      else subjectId_ = tmp;
   }
   if ( relHelp.size() > ii ) {
      pubSessionId_ = lexical_cast<long>(relHelp[ii]);
   }
}

string SessionQosData::getRelativeName() const
{
   string ret = string("client/") + subjectId_;
   if (pubSessionId_ != 0) ret += string("/") + lexical_cast<std::string>(pubSessionId_);
   return ret;
}

string SessionQosData::getAbsoluteName() const
{
   if (clusterNodeId_.empty()) return getRelativeName();
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

long SessionQosData::getPubSessionId() const
{
   return pubSessionId_;
}

void SessionQosData::setPubSessionId(const long pubSessionId)
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
   string offset = extraOffset; // currently unused.
   string ret = string("<session");

   ret += string(" name='")  + getAbsoluteName() + string("'");
   ret += string(" timeout='") + lexical_cast<std::string>(getTimeout()) + string("'") + 
          string(" maxSessions='") + lexical_cast<std::string>(getMaxSessions()) + string("'") +
          string(" clearSessions='") + Global::getBoolAsString(clearSessions_) + string("'");
   
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
   log_.call(ME, "startElement");
   if (log_.trace()) {
      log_.trace(ME, string("startElement. name:'") + name + string("' character: '") + character_ + string("'"));
   }

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
            if (tmpValue == "true") sessionQos_->clearSessions_ = true;
            else sessionQos_->clearSessions_ = false;
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

using namespace std;
using namespace org::xmlBlaster::util::qos;

/** For testing: java org.xmlBlaster.authentication.plugins.simple.SecurityQos */
int main(int args, char* argv[])
{
    {
       string qos = "<session name='/node/http:/client/ticheta/-3' timeout='86400000' maxSessions='10' \n" +
             string("         clearSessions='false' sessionId='IIOP:01110728321B0222011028'/>\n");

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
