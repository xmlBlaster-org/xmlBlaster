/*------------------------------------------------------------------------------
Name:      ConnectQosFactory.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Factory for org::xmlBlaster::util::qos::ConnectQosData (for org::xmlBlaster::util::qos::ConnectReturnQos and org::xmlBlaster::util::qos::ConnectQos)
------------------------------------------------------------------------------*/
#ifndef _UTIL_QOS_CONNECTQOSFACTORY_H
#define _UTIL_QOS_CONNECTQOSFACTORY_H

#include <util/xmlBlasterDef.h>
#include <util/qos/ConnectQos.h>
#include <authentication/SecurityQosFactory.h>

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
 *    <queue relating='client' type='CACHE' version='1.0' maxEntries='1000' maxBytes='4000' onOverflow='exception'>
 *       <address type='IOR' sessionId='4e56890ghdFzj0'>
 *          IOR:10000010033200000099000010....
 *       </address>
 *    </queue>
 *    <!-- The server side callback queue: -->
 *    <queue relating='callback' type='CACHE' version='1.0' maxEntries='1000' maxBytes='4000' onOverflow='deadMessage'>
 *       <callback type='IOR' sessionId='4e56890ghdFzj0'>
 *          IOR:10000010033200000099000010....
 *          <burstMode collectTime='400' />
 *       </callback>
 *    </queue>
 * </qos>
 */

namespace org { namespace xmlBlaster { namespace util { namespace qos {

class Dll_Export ConnectQosFactory: public org::xmlBlaster::util::SaxHandlerBase
{
private:
   const std::string ME;
   org::xmlBlaster::util::qos::SessionQosFactory sessionQosFactory_;
   org::xmlBlaster::authentication::SecurityQosFactory securityQosFactory_;
   org::xmlBlaster::util::qos::storage::QueuePropertyFactory queuePropertyFactory_;
   org::xmlBlaster::util::qos::address::AddressFactory addressFactory_;
   std::string serverRefType_;

   // helper flags for SAX parsing
   bool inSecurityService_;
   bool inServerRef_;
   bool inSession_;

   org::xmlBlaster::util::qos::ConnectQos connectQos_;
   /** when the current parsing point should be handled by another qos factory*/
   SaxHandlerBase* subFactory_;

   void prep()
   {
      inSecurityService_ = false;
      inServerRef_       = false;
      inSession_         = false;
      serverRefType_     = "";
      subFactory_        = NULL;
   }

public:
   ConnectQosFactory(org::xmlBlaster::util::Global& global);

//   ~ConnectQosFactory();

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

   org::xmlBlaster::util::qos::ConnectQosData readObject(const std::string& qos);
};

}}}} // namespaces

#endif
