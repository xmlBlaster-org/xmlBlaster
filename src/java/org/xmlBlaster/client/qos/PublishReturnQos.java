/*------------------------------------------------------------------------------
Name:      PublishReturnQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.qos;

import java.util.Properties;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.StatusQosData;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.def.MethodName;

/**
 * Handling the returned QoS (quality of service) of a publish() call.
 * <p />
 * If you are a Java client and use the I_XmlBlasterAccess interface
 * you get this object as the publish() return value.
 * <p />
 * Example:
 * <pre>
 *   &lt;qos>
 *     &lt;state id='OK' info='QUEUED[bilbo]'/>
 *     &lt;key oid='HelloWorld'/>
 *     &lt;rcvTimestamp nanos='1007764305862000002'/>
 *     &lt;!-- UTC time when message was created in xmlBlaster server with a publish() call, in nanoseconds since 1970 -->
 *  &lt;/qos>
 * </pre>
 * @see org.xmlBlaster.test.classtest.qos.StatusQosFactoryTest
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html" target="others">the interface.publish requirement</a>
 */
public final class PublishReturnQos
{
   private final StatusQosData statusQosData;

   /**
    * Constructor which parses XML string.
    */
   public PublishReturnQos(Global glob, String xmlQos) throws XmlBlasterException {
      this.statusQosData = glob.getStatusQosFactory().readObject(xmlQos);
      this.statusQosData.setMethod(MethodName.PUBLISH);
   }

   /**
    * Constructor which reuses a StatusQosData object.
    */
   public PublishReturnQos(Global glob, StatusQosData statusQosData) {
      this.statusQosData = statusQosData;
      this.statusQosData.setMethod(MethodName.PUBLISH);
   }

   /**
    * Access the raw data object, usually you shouldn't do it.
    * @return The raw data object
    */
   public StatusQosData getData() {
      return statusQosData;
   }

   /**
    * Access the state of message.
    * @return OK (Other values are not yet supported)
    */
   public final String getState() {
      return this.statusQosData.getState();
   }

   /**
    * Additional structured information about a state.
    * @return "QUEUED" or "QUEUED[bilbo]"
    * @see org.xmlBlaster.util.def.Constants
    */
   public final String getStateInfo() {
      return this.statusQosData.getStateInfo();
   }

   /**
    * Access key oid. 
    * @return The unique identifier of a message
    */
   public final String getKeyOid() {
      return this.statusQosData.getKeyOid();
   }

   /** 
    * The approximate receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In milliseconds elapsed since midnight, January 1, 1970 UTC<br />
    * <p>
    * This timestamp is unique for a message instance published and may be
    * used to identify this message. For example a publisher and a receiver
    * of a message can identify this message by its topic (key oid) and its
    * receive timestamp.
    * </p>
    * <p>
    * To get a human readable view on the timestamp try:
    * </p>
    * <pre>
    * String time = publishReturnQos.getRcvTimestamp().toString();
    *
    * -> "2002-02-10 10:52:40.879456789"
    * </pre>
    */
   public final Timestamp getRcvTimestamp() {
      return this.statusQosData.getRcvTimestamp();
   }

   /**
    * @see #toXml(String)
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * @param extraOffset indenting of tags for nice output
    * @return The XML representation
    */
   public final String toXml(String extraOffset) {
      return this.statusQosData.toXml(extraOffset, (Properties)null);
   }

   public final String toString() {
      return toXml(null);
   }
}
