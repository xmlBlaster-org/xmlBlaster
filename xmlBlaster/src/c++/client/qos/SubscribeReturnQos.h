/*------------------------------------------------------------------------------
Name:      SubscribeReturnQos.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Handling the returned QoS (quality of service) of a subscribe() call.
 * <p />
 * If you are a Java client and use the XmlBlasterConnection helper class
 * you get this object as the subscribe() return value.
 * <p />
 * Example:
 * <pre>
 *   &lt;qos>
 *     &lt;state id='OK' info='QUEUED[bilbo]'/>
 *     &lt;subscribe id='_subId:1/>
 *  &lt;/qos>
 * </pre>
 * @see org.xmlBlaster.test.classtest.qos.StatusQosFactoryTest
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html" target="others">the interface.subscribe requirement</a>
 */

#ifndef _CLIENT_QOS_SUBSCRIBERETURNQOS_H
#define _CLIENT_QOS_SUBSCRIBERETURNQOS_H

#include <util/qos/StatusQosData.h>

namespace org { namespace xmlBlaster { namespace client { namespace qos {

class Dll_Export SubscribeReturnQos
{
private:
   std::string ME;
   org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::qos::StatusQosData data_;

public:

   /**
    * Constructs the specialized quality of service object for status informations,
    * e.g. for a return of a subscribe() call
    * <p>
    * The state defaults to Constants::STATE_OK
    * </p>
    * @param The factory which knows how to serialize and parse me
    */
   SubscribeReturnQos(org::xmlBlaster::util::Global& global,
                      const org::xmlBlaster::util::qos::StatusQosData& data);

   SubscribeReturnQos(const SubscribeReturnQos& data);

   SubscribeReturnQos operator =(const SubscribeReturnQos& data);

   /**
    * Access state of message on update().
    * @return "OK", "ERROR" etc. See Constants::java
    */
   std::string getState() const;

   /**
    * Access state of message on update().
    * @return The human readable info text
    */
   std::string getStateInfo() const;

   /**
    * Access key oid. 
    * @return The unique identifier of a message
    */
   std::string getSubscriptionId() const;

   /**
    * gets the subscription status: persistent (true) or not persistent 
    * (false). If it is true it means that the subscription has implicitly
    * made the associated session persistent and both are surviving a
    * server crash. 
    */
   bool isPersistent() const;

   /**
    * Dump state of this object into a XML ASCII std::string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the status as a XML ASCII std::string
    */
   std::string toXml(const std::string& extraOffset="") const;

   const org::xmlBlaster::util::qos::StatusQosData& getData() const;

};

}}}} // namespace

#endif
