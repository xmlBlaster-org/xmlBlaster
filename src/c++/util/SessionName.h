/*------------------------------------------------------------------------------
Name:      SessionName.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   
------------------------------------------------------------------------------*/

#ifndef XMLBLASTER_UTIL_SessionName_H
#define XMLBLASTER_UTIL_SessionName_H

#include <util/xmlBlasterDef.h>
#include <string>
#include <util/ReferenceCounterBase.h>
#include <util/ReferenceHolder.h>
#include <util/StringTrim.h>

namespace org { namespace xmlBlaster { namespace util {

class Dll_Export SessionName : public org::xmlBlaster::util::ReferenceCounterBase
{
private:
   const std::string ME;
   std::string  sessionId_;
   std::string  clusterNodeId_;
   std::string  subjectId_;
   long         pubSessionId_;
   org::xmlBlaster::util::Global& global_;
   bool         useSessionMarker_; // In future default to true, remove after Version 2.0

   void copy(const SessionName& data);

   void initialize(const std::string& absoluteName, const std::string& defaultUserName, long publicSessionId);

public:
   /**
    * When using this constructor you can let it assign the defaults by passing an empty std::string as the 
    * 'absoluteName' argument, or you force the SessionName to set the SessionName to what you specify in the
    * 'absoluteName' argument. 
    * @param absoluteName the sessionId to assign to this SessionName. You can either pass an absolute name,
    * or a relative name or an empty std::string.
    */
   SessionName(org::xmlBlaster::util::Global& global, const std::string& absoluteName="");

   /**
    * @defaultUserName is the name to use as a default for the user (the subjectId). It is stronger than the
    * properties set but if an empty std::string is used, then the default name is taken from the 'user' property. 
    * This is just a default and as such it is weaker than the property 'session.name'. In other words, even
    * if defaultUserName is not empty, it will be overwritten by eventual setting of the property 'session.name'.
    *
    * @publicSessionId is the public sessionId to be used. Note that this is just a suggestion, so if you 
    * have the 'session.name' property set, it will overwrite this. If the publicSessionId is '0', then it
    * is omitted (the server will assign one for you).
    */
   SessionName(org::xmlBlaster::util::Global& global, const std::string& defaultUserName, long publicSessionId);
   SessionName(const SessionName& data);
   SessionName& operator =(const SessionName& data);

   virtual ~SessionName();

   /**
    * Sets the absolute name. It checks if it really is an absolute name,
    * i.e. if it contains the well known structure '/node/....' it parses it,
    * otherwise it leaves it untouched (i.e. it will not parse it).
    */

   /**
    * Sets the absolute name. Note that you can overwrite the nodeId here. It returns 'true' if the
    * name was absolute, 'false' otherwise.
    */
   void setAbsoluteName(/*const std::string nodeId="",*/ const std::string& name);
   std::string getRelativeName() const;
   std::string getAbsoluteName() const;
   std::string getClusterNodeId() const;
   void setClusterNodeId(const std::string& clusterNodeId);
   std::string getSubjectId() const;
   void setSubjectId(const std::string& subjectId);
   long getPubSessionId() const;
   void setPubSessionId(const long pubSessionId);
   
   // remove with version 2.0
   bool useSessionMarker() const { return useSessionMarker_; }

   std::string getSecretSessionId() const;
   void setSecretSessionId(const std::string& sessionId);
   std::string toXml(const std::string& extraOffset="") const;
};

typedef org::xmlBlaster::util::ReferenceHolder<SessionName> SessionNameRef;

}}} // namespaces

#endif
