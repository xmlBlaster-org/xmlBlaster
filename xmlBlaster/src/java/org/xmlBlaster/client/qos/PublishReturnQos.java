/*------------------------------------------------------------------------------
Name:      PublishReturnQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.StatusQosData;


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
public final class PublishReturnQos
{
   private String ME = "PublishReturnQos";
   private final StatusQosData statusQosData;

   /**
    * Constructor which parses XML string.
    */
   public PublishReturnQos(Global glob, String xmlQos) throws XmlBlasterException {
      this.statusQosData = glob.getStatusQosFactory().readObject(xmlQos);
   }

   /**
    * Constructor which reuses a StatusQosData object.
    */
   public PublishReturnQos(Global glob, StatusQosData statusQosData) {
      this.statusQosData = statusQosData;
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
    * @see org.xmlBlaster.util.enum.Constants
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
      return this.statusQosData.toXml(extraOffset);
   }

   public final String toString() {
      return toXml(null);
   }
}
