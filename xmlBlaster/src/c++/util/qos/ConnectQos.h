/*------------------------------------------------------------------------------
Name:      ConnectQos.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Factory for ConnectQosData (for ConnectReturnQos and ConnectQos)
------------------------------------------------------------------------------*/

#ifndef _UTIL_QOS_CONNECTQOS_H
#define _UTIL_QOS_CONNECTQOS_H

#include <util/xmlBlasterDef.h>
#include <string>
#include <util/XmlQoSBase.h>
#include <util/StringTrim.h>
#include <util/ServerRef.h>
#include <authentication/SecurityQos.h>


/**
 * <qos>\n") +
 *    <securityService type='htpasswd' version='1.0'>
 *      <![CDATA[
 *      <user>joe</user>
 *      <passwd>secret</passwd>
 *      ]]>
 *    </securityService>
 *    <session name='/node/heron/client/joe/-9' timeout='3600000' maxSessions='10' clearSessions='false' sessionId='4e56890ghdFzj0'/>
 *    <ptp>true</ptp>
 *    <!-- The client side queue: -->
 *    <queue relating='client' type='CACHE' version='1.0' maxMsg='1000' maxSize='4000' onOverflow='exception'>
 *       <address type='IOR' sessionId='4e56890ghdFzj0'>
 *          IOR:10000010033200000099000010....
 *       </address>
 *    </queue>
 *    <!-- The server side callback queue: -->
 *    <queue relating='session' type='CACHE' version='1.0' maxMsg='1000' maxSize='4000' onOverflow='deadMessage'>
 *       <callback type='IOR' sessionId='4e56890ghdFzj0'>
 *          IOR:10000010033200000099000010....
 *          <burstMode collectTime='400' />
 *       </callback>
 *    </queue>
 * </qos>
 */




namespace org { namespace xmlBlaster { namespace util { namespace qos {

using namespace org::xmlBlaster::authentication;
using namespace org::xmlBlaster::util;

class Dll_Export ConnectQosData
{
private:
   string      sessionId_;
   SecurityQos securityQos_;
   ServerRef   serverRef_;
   bool        isDirty_;
   string      literal_;

   void setLiteral(const string& literal);

   friend class ConnectQosFactory;

public:
   ConnectQosData();
   void setSessionId(const string& sessionId);
   string getSessionId() const;
   string getUserId() const;
   string getCallbackType() const;
   void setSecurityQos(const SecurityQos& securityQos);
   SecurityQos getSecurityQos() const;
   void setServerRef(const ServerRef& serverRef);
   ServerRef getServerRef() const;
   string toXml() const;
};

class Dll_Export ConnectQosFactory: public util::XmlQoSBase
{
private:
   const string ME;

   string       sessionId_;
   string       userId_;
   SecurityQos* securityQos_;
   ServerRef*   serverRef_;
   string       callbackType_;

   // helper flags for SAX parsing
   bool inSecurityService_;
   bool inCallback_;
   int args_;
   const char * const* argc_;
   util::StringTrim<char> trim_;

   void prep(int args, const char * const argc[])
   {
      args_ = args;
      argc_ = argc;
      inSecurityService_ = false;
      inCallback_ = false;
      callbackType_ = "";
      securityQos_ = NULL;
      serverRef_ = NULL;
   }

public:
   ConnectQosFactory(int args=0, const char * const argc[]=0);

   ~ConnectQosFactory();

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

   ConnectQosData readObject(const string& qos);

   static string writeObject(const ConnectQosData& qos);

};

typedef ConnectQosData ConnectQos;

typedef ConnectQosData ConnectReturnQos;


}}}} // namespaces

#endif
