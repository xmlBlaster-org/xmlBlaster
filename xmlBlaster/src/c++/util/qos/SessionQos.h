/*------------------------------------------------------------------------------
Name:      SessionQos.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Factory for SessionQosData (for org::xmlBlaster::util::qos::ConnectReturnQos and org::xmlBlaster::util::qos::ConnectQos)
------------------------------------------------------------------------------*/

#ifndef _UTIL_QOS_SESSIONQOS_H
#define _UTIL_QOS_SESSIONQOS_H

#include <util/xmlBlasterDef.h>
#include <string>
#include <util/XmlQoSBase.h>
#include <util/StringTrim.h>
#include <util/SessionName.h>

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



class Dll_Export SessionQosData : public org::xmlBlaster::util::ReferenceCounterBase
{
private:
   const std::string ME;
   long         timeout_;
   int          maxSessions_;
   bool         clearSessions_;
   bool         reconnectSameClientOnly_;
   std::string  sessionId_; // secret !
   org::xmlBlaster::util::SessionNameRef  sessionName_;
   org::xmlBlaster::util::Global& global_;

   friend class SessionQosFactory;

   void copy(const SessionQosData& data);

   void initialize();

public:
   virtual ~SessionQosData();

   /**
    * When using this constructor you can let it assign the defaults by passing an empty std::string as the 
    * 'absoluteName' argument, or you force the SessionQos to set the SessionName to what you specify in the
    * 'absoluteName' argument. 
    * @param absoluteName the sessionId to assign to this SessionQos. You can either pass an absolute name,
    * or a relative name or an empty std::string.
    */
   SessionQosData(org::xmlBlaster::util::Global& global, const std::string& absoluteName="");

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
   SessionQosData(org::xmlBlaster::util::Global& global, const std::string& defaultUserName, long publicSessionId);
   SessionQosData(const SessionQosData& data);
   SessionQosData& operator =(const SessionQosData& data);
   long getTimeout() const;
   void setTimeout(long timeout);
   int getMaxSessions() const;
   void setMaxSessions(int maxSessions);
   bool getClearSessions() const;
   void setClearSessions(bool clearSessions);
   bool getReconnectSameClientOnly() const;
   void setReconnectSameClientOnly(bool reconnectSameClientOnly);

   /**
    * Sets the absolute name. It checks if it really is an absolute name,
    * i.e. if it contains the well known structure '/node/....' it parses it,
    * otherwise it leaves it untouched (i.e. it will not parse it).
    */

   /**
    * Sets the absolute name. Note that you can overwrite the nodeId here. It returns 'true' if the
    * name was absolute, 'false' otherwise.
    * This is a convenience access for getSessionName()->setAbsoluteName(name)
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
   /**
    * Your changes outside change the internal sessionName. 
    * @return A reference counted SessionName. 
    */
   org::xmlBlaster::util::SessionNameRef getSessionName();

   std::string getSecretSessionId() const;
   void setSecretSessionId(const std::string& sessionId);
   std::string toXml(const std::string& extraOffset="") const;
   /**
    * Get a usage string for the session parameters
    */
   static std::string usage();
};

class Dll_Export SessionQosFactory: public util::XmlQoSBase
{
private:
   const std::string ME;
   SessionQosData* sessionQos_;

public:
   SessionQosFactory(org::xmlBlaster::util::Global& global);

   ~SessionQosFactory();

   /**
    * This characters emulates the java version but keep in mind that it is
    * not the virtual method inherited from DocumentHandler !!
    */
   void characters(const std::string &ch);

   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   void startElement(const std::string &name, const parser::AttributeMap& attrs);

   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   void endElement(const std::string &name);

   void reset();

   const SessionQosData& getData() const;

   SessionQosData readObject(const std::string& qos);

};

typedef SessionQosData SessionQos;

typedef SessionQosData SessionReturnQos;

typedef org::xmlBlaster::util::ReferenceHolder<SessionQosData> SessionQosRef;
typedef org::xmlBlaster::util::ReferenceHolder<SessionReturnQos> SessionReturnQosRef;

}}}} // namespaces

#endif
