/*------------------------------------------------------------------------------
Name:      UpdateReturnQos.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Handling the returned QoS (quality of service) of an update() call. 
 * <p />
 * If you are a Java client you can use this class to generate the QoS
 * which you need to return in an update()
 * <p />
 * Example:
 * <pre>
 *   &lt;qos>
 *     &lt;state id='OK'/>
 *  &lt;/qos>
 * </pre>
 * @see org.xmlBlaster.test.classtest.qos.StatusQosFactoryTest
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.update.html" target="others">the interface.update requirement</a>
 */

#ifndef _CLIENT_QOS_UPDATERETURNQOS_H
#define _CLIENT_QOS_UPDATERETURNQOS_H

#include <util/qos/StatusQosData.h>





namespace org { namespace xmlBlaster { namespace client { namespace qos {

class Dll_Export UpdateReturnQos
{
private:
   std::string        ME;
   org::xmlBlaster::util::Global&       global_;
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
   UpdateReturnQos(org::xmlBlaster::util::Global& global, const org::xmlBlaster::util::qos::StatusQosData& data);

   UpdateReturnQos(const UpdateReturnQos& data);

   UpdateReturnQos operator =(const UpdateReturnQos& data);

   /**
    * Set state of message on update().
    * @return "OK", "ERROR" etc. See Constants::java
    */
   void setState(const std::string& state);

   /**
    * Set state of message on update().
    * @return The human readable info text
    */
   void setStateInfo(const std::string& stateInfo);

   /**
    * Dump state of this object into a XML ASCII std::string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the status as a XML ASCII std::string
    */
   std::string toXml(const std::string& extraOffset="") const;

};

}}}} // namespace

#endif
