/*------------------------------------------------------------------------------
Name:      ConnectQosFactory.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Factory for ConnectQosData (for ConnectReturnQos and ConnectQos)
------------------------------------------------------------------------------*/

/**
 */

#ifndef _UTIL_QOS_CONNECTQOS_H
#define _UTIL_QOS_CONNECTQOS_H

#include <string>
#include <util/XmlQoSBase.h>
#include <util/StringTrim.h>
#include <util/ServerRef.h>
#include <authentication/SecurityQos.h>

namespace org { namespace xmlBlaster { namespace util { namespace qos {

using namespace org::xmlBlaster::authentication;
using namespace org::xmlBlaster::util;

class ConnectQosData
{
private:
   string      sessionId_;
   SecurityQos securityQos_;
   ServerRef   serverRef_;

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
};

class ConnectQosFactory: public util::XmlQoSBase
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
   char** argc_;
   util::StringTrim<char> trim_;

   void prep(int args, char* argc[])
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
   ConnectQosFactory(int args=0, char *argc[]=0);

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
};

typedef ConnectQosData ConnectQos;

typedef ConnectQosData ConnectReturnQos;


}}}} // namespaces

#endif
