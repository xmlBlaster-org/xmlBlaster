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
 */

namespace org { namespace xmlBlaster { namespace util { namespace qos {

using namespace org::xmlBlaster::util;

class Dll_Export SessionQosData
{
private:
   long        timeout_; // ='86400000'
   int         maxSessions_; // s='10'
   bool        clearSessions_; // ='false'
   string      name_; // ='/node/http:/client/ticheta/-3'>
   string      sessionId_; // >IIOP:01110728321B0222011028&lt;/sessionId>

   bool        isDirty_;
   string      literal_;

   void setLiteral(const string& literal);

   friend class SessionQosFactory;

public:
   SessionQosData();
   long getTimeout() const;
   void setTimeout(long timeout);
   int getMaxSessions() const;
   void setMaxSessions(int maxSessions);
   bool getClearSessions() const;
   void setClearSessions(bool clearSessions);
   string getName() const;
   void setName(const string& name);
   string getSessionId() const;
   void setSessionId(const string& sessionId);
   string toXml() const;
};

class Dll_Export SessionQosFactory: public util::XmlQoSBase
{
private:
   const string ME;

   SessionQosData sessionQos_;
   // helper flags for SAX parsing
   int args_;
   const char * const* argc_;
   util::StringTrim<char> trim_;

   void prep(int args, const char * const argc[])
   {
      args_ = args;
      argc_ = argc;
   }

public:
   SessionQosFactory(int args=0, const char * const argc[]=0);

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

   SessionQosData readObject(const string& qos);

   static string writeObject(const SessionQosData& qos);

};

typedef SessionQosData SessionQos;

typedef SessionQosData ConnectReturnQos;

}}}} // namespaces

#endif
