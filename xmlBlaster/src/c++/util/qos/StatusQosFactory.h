/*------------------------------------------------------------------------------
Name:      StatusQosFactory.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#ifndef _UTIL_QOS_STATUSQOSFACTORY_H
#define _UTIL_QOS_STATUSQOSFACTORY_H

#include <util/xmlBlasterDef.h>
#include <util/qos/StatusQosData.h>
#include <util/parser/XmlHandlerBase.h>

/**
 * Parsing xml QoS (quality of service) of return status. 
 * <p />
 * <pre>
 *  &lt;qos>
 *     &lt;state id='OK' info='QUEUED[bilbo]'/>
 *     &lt;key oid='yourMessageOid'/> <!-- org::xmlBlaster::client::qos::PublishReturnQos and EraseReturnQos only -->
 *     &lt;subscribe id='_subId:1/> <!-- org::xmlBlaster::client::qos::SubscribeReturnQos and org::xmlBlaster::client::qos::UnSubscribeQos only -->
 *  &lt;/qos>
 * </pre>
 * @see org.xmlBlaster.util.qos.StatusQosData
 * @see org.xmlBlaster.test.classtest.qos.StatusQosFactoryTest
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */

namespace org { namespace xmlBlaster { namespace util { namespace qos {

class Dll_Export StatusQosFactory: public org::xmlBlaster::util::parser::XmlHandlerBase
{
private:
   std::string                    ME;
   org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::I_Log&    log_;
   StatusQosData                  statusQosData_;

   /** helper flag for SAX parsing: parsing inside <state> ? */
   bool inState_; //     = false;
   bool inSubscribe_; //  = false;
   bool inKey_; //       = false;
   bool inQos_;
   bool inIsPersistent_; // QosData

   void prep();

public:
   StatusQosFactory(org::xmlBlaster::util::Global& global);

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

   StatusQosData readObject(const std::string& qos);

};

}}}} // namespaces

#endif

