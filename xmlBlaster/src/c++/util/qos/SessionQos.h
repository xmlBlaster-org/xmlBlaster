/*------------------------------------------------------------------------------
Name:      SessionQos.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Factory for SessionQosData (for ConnectReturnQos and ConnectQos)
------------------------------------------------------------------------------*/

#ifndef _UTIL_QOS_SESSIONQOS_H
#define _UTIL_QOS_SESSIONQOS_H

#include <util/xmlBlasterDef.h>
#include <string>
#include <util/XmlQoSBase.h>
#include <util/StringTrim.h>

/**
 *
 *  &lt;session timeout='86400000'
 *           maxSessions='10'
 *           clearSessions='false'
 *           name='/node/http:/client/ticheta/-3'>
 *     &lt;sessionId>IIOP:01110728321B0222011028&lt;/sessionId>
 *  &lt;/session>
 *
 */

namespace org { namespace xmlBlaster { namespace util { namespace qos {

using namespace org::xmlBlaster::util;

class Dll_Export SessionQosData
{
private:
   const string ME;
   long         timeout_;
   int          maxSessions_;
   bool         clearSessions_;
   string       sessionId_;
   string       clusterNodeId_;
   string       subjectId_;
   long         pubSessionId_;
   Global&      global_;

   friend class SessionQosFactory;

   void copy(const SessionQosData& data)
   {
      timeout_       = data.timeout_;
      maxSessions_   = data.maxSessions_;
      clearSessions_ = data.clearSessions_;
      sessionId_     = data.sessionId_;
      clusterNodeId_ = data.clusterNodeId_;
      subjectId_     = data.subjectId_;
      pubSessionId_  = data.pubSessionId_;
   }

   void initialize(const string& absoluteName, const string& defaultUserName, long publicSessionId);

public:
   /**
    * When using this constructor you can let it assign the defaults by passing an empty string as the 
    * 'absoluteName' argument, or you force the SessionQos to set the SessionName to what you specify in the
    * 'absoluteName' argument. 
    * @param absoluteName the sessionId to assign to this SessionQos. You can either pass an absolute name,
    * or a relative name or an empty string.
    */
   SessionQosData(Global& global, const string& absoluteName="");

   /**
    * @defaultUserName is the name to use as a default for the user (the subjectId). It is stronger than the
    * properties set but if an empty string is used, then the default name is taken from the 'user' property. 
    * This is just a default and as such it is weaker than the property 'session.name'. In other words, even
    * if defaultUserName is not empty, it will be overwritten by eventual setting of the property 'session.name'.
    *
    * @publicSessionId is the public sessionId to be used. Note that this is just a suggestion, so if you 
    * have the 'session.name' property set, it will overwrite this. If the publicSessionId is '0', then it
    * is omitted (the server will assign one for you).
    */
   SessionQosData(Global& global, const string& defaultUserName, long publicSessionId);
   SessionQosData(const SessionQosData& data);
   SessionQosData& operator =(const SessionQosData& data);
   long getTimeout() const;
   void setTimeout(long timeout);
   int getMaxSessions() const;
   void setMaxSessions(int maxSessions);
   bool getClearSessions() const;
   void setClearSessions(bool clearSessions);

   /**
    * Sets the absolute name. It checks if it really is an absolute name,
    * i.e. if it contains the well known structure '/node/....' it parses it,
    * otherwise it leaves it untouched (i.e. it will not parse it).
    */

   /**
    * Sets the absolute name. Note that you can overwrite the nodeId here. It returns 'true' if the
    * name was absolute, 'false' otherwise.
    */
   void setAbsoluteName(/*const string nodeId="",*/ const string& name);
   string getRelativeName() const;
   string getAbsoluteName() const;
   string getClusterNodeId() const;
   void setClusterNodeId(const string& clusterNodeId);
   string getSubjectId() const;
   void setSubjectId(const string& subjectId);
   long getPubSessionId() const;
   void setPubSessionId(const long pubSessionId);

   string getSessionId() const;
   void setSessionId(const string& sessionId);
   string toXml(const string& extraOffset="") const;
};

class Dll_Export SessionQosFactory: public util::XmlQoSBase
{
private:
   const string ME;
   SessionQosData* sessionQos_;

public:
   SessionQosFactory(Global& global);

   ~SessionQosFactory();

   /**
    * This characters emulates the java version but keep in mind that it is
    * not the virtual method inherited from DocumentHandler !!
    */
   void characters(const XMLCh* const ch, const unsigned int length);

   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   void startElement(const XMLCh* const name, AttributeList& attrs);

   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   void endElement(const XMLCh* const name);

   void reset();

   SessionQosData getData() const;

   SessionQosData readObject(const string& qos);

};

typedef SessionQosData SessionQos;

typedef SessionQosData SessionReturnQos;

}}}} // namespaces

#endif
