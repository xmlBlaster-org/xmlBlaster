/*------------------------------------------------------------------------------
Name:      PublishReturnQos.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Handling the returned QoS (quality of service) of a publish() call.
 * <p />
 * If you are a Java client and use the XmlBlasterConnection helper class
 * you get this object as the publish() return value.
 * <p />
 * Example:
 * <pre>
 *   &lt;qos>
 *     &lt;state id='OK' info='QUEUED[bilbo]'/>
 *     &lt;key oid='HelloWorld'/>
 *  &lt;/qos>
 * </pre>
 * @see org.xmlBlaster.test.classtest.qos.StatusQosFactoryTest
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html" target="others">the interface.publish requirement</a>
 */

#ifndef _CLIENT_QOS_PUBLISHRETURNQOS_H
#define _CLIENT_QOS_PUBLISHRETURNQOS_H

#include <util/qos/StatusQosData.h>

namespace org { namespace xmlBlaster { namespace client { namespace qos {

class Dll_Export PublishReturnQos
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
   PublishReturnQos(org::xmlBlaster::util::Global& global, const org::xmlBlaster::util::qos::StatusQosData& data);

   PublishReturnQos(org::xmlBlaster::util::Global& global);

   PublishReturnQos(const PublishReturnQos& data);

   PublishReturnQos operator =(const PublishReturnQos& data);

   /**
    * Access state of message on update().
    * @return "OK", "ERROR" etc. See Constants::java
    */
   std::string getState() const;

   /**
    * Sets the state (used when queuing messages and giving back the status to the client
    */
    void setState(const std::string& state);

   /**
    * Access state of message on update().
    * @return The human readable info text
    */
   std::string getStateInfo() const;

   /**
    * Access key oid. 
    * @return The unique identifier of a message
    */
   std::string getKeyOid() const;

   /**
    * Sets the soid (used when queuing messages and giving back the status to the client
    */
    void setKeyOid(const std::string& oid);

   /** 
    * The approximate receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In milliseconds elapsed since midnight, January 1, 1970 UTC
    */
   org::xmlBlaster::util::Timestamp getRcvTimestamp() const;

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
