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
 *     &lt;key oid='HelloWorld'/>
 *  &lt;/qos>
 * </pre>
 * @see org.xmlBlaster.test.classtest.qos.StatusQosFactoryTest
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html" target="others">the interface.publish requirement</a>
 */

#ifndef _CLIENT_QOS_PUBLISHRETURNQOS_H
#define _CLIENT_QOS_PUBLISHRETURNQOS_H

#include <util/qos/StatusQosData.h>

using org::xmlBlaster::util::qos::StatusQosData;
using namespace std;

namespace org { namespace xmlBlaster { namespace client { namespace qos {

class Dll_Export PublishReturnQos
{
private:
   string        ME;
   Global&       global_;
   StatusQosData data_;

public:

   /**
    * Constructs the specialized quality of service object for status informations,
    * e.g. for a return of a subscribe() call
    * <p>
    * The state defaults to Constants::STATE_OK
    * </p>
    * @param The factory which knows how to serialize and parse me
    */
   PublishReturnQos(Global& global, const StatusQosData& data);

   PublishReturnQos(Global& global);

   PublishReturnQos(const PublishReturnQos& data);

   PublishReturnQos operator =(const PublishReturnQos& data);

   /**
    * Access state of message on update().
    * @return "OK", "ERROR" etc. See Constants::java
    */
   string getState() const;

   /**
    * Sets the state (used when queuing messages and giving back the status to the client
    */
    void setState(const string& state);

   /**
    * Access state of message on update().
    * @return The human readable info text
    */
   string getStateInfo() const;

   /**
    * Access key oid. 
    * @return The unique identifier of a message
    */
   string getKeyOid() const;

   /**
    * Sets the soid (used when queuing messages and giving back the status to the client
    */
    void setKeyOid(const string& oid);

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the status as a XML ASCII string
    */
   string toXml(const string& extraOffset="") const;

};

}}}} // namespace

#endif
