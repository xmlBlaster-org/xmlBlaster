/*------------------------------------------------------------------------------
Name:      SessionName.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   
------------------------------------------------------------------------------*/
#include <util/SessionName.h>
#include <stdlib.h>
#include <util/lexical_cast.h>
#include <util/StringStripper.h>
#include <util/StringTrim.h>
#include <util/Global.h>

namespace org { namespace xmlBlaster { namespace util {

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::parser;
using namespace std;


/*---------------------------- SessionName --------------------------------*/

SessionName::SessionName(Global& global, const string& defaultUserName, long publicSessionId)
    : ReferenceCounterBase(), ME("SessionName"), global_(global)
{
   initialize("", defaultUserName, publicSessionId);   
}

SessionName::SessionName(Global& global, const string& absoluteName)
    : ReferenceCounterBase(), ME("SessionName"), global_(global)
{
   initialize(absoluteName, "", 0);
}

SessionName::~SessionName()
{
}

void SessionName::copy(const SessionName& data)
{
   clusterNodeId_ = data.clusterNodeId_;
   subjectId_     = data.subjectId_;
   pubSessionId_  = data.pubSessionId_;
   useSessionMarker_  = data.useSessionMarker_;
}


void SessionName::initialize(const string& absoluteName, const string& defaultUserName, long publicSessionId)
{
   pubSessionId_ = publicSessionId;

   // Change default to true on version 2.0!
   useSessionMarker_ = global_.getProperty().getBoolProperty("xmlBlaster/useSessionMarker", false);

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

   subjectId_ = global_.getProperty().getStringProperty("user.name", "guest");
}


SessionName::SessionName(const SessionName& data) : ReferenceCounterBase(), ME(data.ME), global_(data.global_)
{
   copy(data);
}

SessionName& SessionName::operator =(const SessionName& data)
{
  copy(data);
  return *this;
}


void SessionName::setAbsoluteName(const string& name)
{
   pubSessionId_ = 0; // resets the value if previously set
   string relative = "";
   if (name.empty())
      throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::setAbsoluteName", "the absolute name is empty");

   if (name[0] == '/') { // then it is an absolute name
      StringStripper stripper("/");
      vector<std::string> help = stripper.strip(name);
      help.erase(help.begin()); // since it is empty for sure.
      if (help.size() < 4) 
         throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::setAbsoluteName", "the absolute name '" + name + "' is not allowed");
      if (help[0] == "node") clusterNodeId_ = help[1];
      else throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::setAbsoluteName", "the absolute name '" + name + "' is not allowed. It should start with '/node'");
      if (help[2] != "client") 
         throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::setAbsoluteName", "the absolute name '" + name + "' is not allowed. '/client' is missing");
   
      for (size_t i=3; i < help.size(); i++) {
         relative += help[i];
        if ( i < help.size()-1) relative += "/";
      }
   }
   else relative = name;

   StringStripper relStripper("/");
   vector<std::string> relHelp = relStripper.strip(relative);
   if (relHelp.empty()) {
      throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::setAbsoluteName", "there is no relative name information: '" + name + "' is not allowed");
   }

   size_t ii = 0;
   if ( relHelp.size() > ii ) {
      string tmp = relHelp[ii++];
      if ( tmp == "client" ) {
         if ( relHelp.size() > ii ) {
            subjectId_ = relHelp[ii++];
         }
         else {
            throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::setAbsoluteName", "there is no relative name information: '" + name + "' is not allowed");
         }
      }
      else subjectId_ = tmp;
   }
   if ( relHelp.size() > ii ) {
      string tmp = relHelp[ii++];
      if ( tmp == "session" ) {
         if ( relHelp.size() > ii ) {
            pubSessionId_ = lexical_cast<long>(relHelp[ii++]);
         }
         else {
            throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::setAbsoluteName", "there is no relative session number given: '" + name + "' is not allowed");
         }
      }
      else pubSessionId_ = lexical_cast<long>(tmp);
   }
}

string SessionName::getRelativeName() const
{
    return getRelativeName(false);
}

string SessionName::getRelativeName(bool forceSessionMarker) const
{
   string ret = string("client/") + subjectId_;
   if (pubSessionId_ != 0) {
      if (useSessionMarker_ || forceSessionMarker)
         ret += string("/session/") + lexical_cast<std::string>(pubSessionId_);
      else
         ret += string("/") + lexical_cast<std::string>(pubSessionId_);
   }
   return ret;
}

string SessionName::getAbsoluteName() const
{
   if (clusterNodeId_.empty()) return getRelativeName();
   return string("/node/") + clusterNodeId_ + string("/") + getRelativeName();
}

string SessionName::getClusterNodeId() const
{
   return clusterNodeId_;
}

void SessionName::setClusterNodeId(const string& clusterNodeId)
{
   clusterNodeId_ = clusterNodeId;
}

string SessionName::getSubjectId() const
{
   return subjectId_;
}

void SessionName::setSubjectId(const string& subjectId)
{
    subjectId_ = subjectId;
}

long SessionName::getPubSessionId() const
{
   return pubSessionId_;
}

void SessionName::setPubSessionId(const long pubSessionId)
{
   pubSessionId_ = pubSessionId;
}

string SessionName::toXml(const string& extraOffset) const
{
   string offset = Constants::OFFSET + extraOffset;
   string ret;
   
   ret += offset + string("<session");
   ret += string(" name='")  + getAbsoluteName() + string("'");
   ret += string("/>\n");
   return ret;
}

}}}
