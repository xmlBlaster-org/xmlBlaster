/*------------------------------------------------------------------------------
Name:      StatusQosFactory.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#ifndef _UTIL_QOS_STATUSQOSFACTORY_H
#define _UTIL_QOS_STATUSQOSFACTORY_H

#include <util/xmlBlasterDef.h>
#include <util/qos/StatusQosData.h>
#include <util/SaxHandlerBase.h>
#include <util/Log.h>

/**
 * Parsing xml QoS (quality of service) of return status. 
 * <p />
 * <pre>
 *  &lt;qos>
 *     &lt;state id='OK' info='QUEUED[bilbo]'/>
 *     &lt;key oid='yourMessageOid'/> <!-- PublishReturnQos and EraseReturnQos only -->
 *     &lt;subscribe id='_subId:1/> <!-- SubscribeReturnQos and UnSubscribeQos only -->
 *  &lt;/qos>
 * </pre>
 * @see org.xmlBlaster.util.qos.StatusQosData
 * @see org.xmlBlaster.test.classtest.qos.StatusQosFactoryTest
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */

namespace org { namespace xmlBlaster { namespace util { namespace qos {

using namespace org::xmlBlaster::util;

class Dll_Export StatusQosFactory: public util::SaxHandlerBase
{
private:
   string        ME;
   Global&       global_;
   Log&          log_;
   StatusQosData statusQosData_;

   /** helper flag for SAX parsing: parsing inside <state> ? */
   bool inState_; //     = false;
   bool inSubscribe_; //  = false;
   bool inKey_; //       = false;
   bool inQos_;

   void prep();

public:
   StatusQosFactory(Global& global);

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

   StatusQosData readObject(const string& qos);

};

}}}} // namespaces

#endif

