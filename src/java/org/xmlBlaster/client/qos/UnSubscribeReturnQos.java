/*------------------------------------------------------------------------------
Name:      UnSubscribeReturnQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.qos;

import java.util.Properties;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.StatusQosData;
import org.xmlBlaster.util.def.MethodName;

/**
 * Handling the returned QoS (quality of service) of a unSubscribe() call. 
 * <p />
 * If you are a Java client and use the I_XmlBlasterAccess interface
 * you get this object as the unSubscribe() return value.
 * <p />
 * Example:
 * <pre>
 *   &lt;qos>
 *     &lt;state id='OK' info='QUEUED[bilbo]'/>
 *     &lt;subscribe id='_subId:1/>
 *  &lt;/qos>
 * </pre>
 * @see org.xmlBlaster.test.classtest.qos.StatusQosFactoryTest
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.unSubscribe.html" target="others">the interface.unSubscribe requirement</a>
 */
public final class UnSubscribeReturnQos
{
   private final StatusQosData statusQosData;

   /**
    * Constructor which parses XML string. 
    */
   public UnSubscribeReturnQos(Global glob, String xmlQos) throws XmlBlasterException {
      this.statusQosData = glob.getStatusQosFactory().readObject(xmlQos);
      this.statusQosData.setMethod(MethodName.UNSUBSCRIBE);
   }

   /**
    * Constructor which reuses a StatusQosData object.
    */
   public UnSubscribeReturnQos(Global glob, StatusQosData statusQosData) {
      this.statusQosData = statusQosData;
      this.statusQosData.setMethod(MethodName.UNSUBSCRIBE);
   }

   /**
    * Access the raw data object. 
    */
   public StatusQosData getData() {
      return this.statusQosData;
   }

   /**
    * Access the state of message. 
    * @return OK (Other values are not yet supported)
    */
   public String getState() {
      return this.statusQosData.getState();
   }

   /**
    * Additional structured information about a state. 
    * @return "QUEUED" or "QUEUED[bilbo]"
    * @see org.xmlBlaster.util.def.Constants
    */
   public String getStateInfo() {
      return this.statusQosData.getStateInfo();
   }

   /**
    * Get the subscription-identifier (unique handle) which was unsubscribed. 
    */
   public String getSubscriptionId() {
      return this.statusQosData.getSubscriptionId();
   }

   /**
    * @see #toXml(String)
    */
   public String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * @param extraOffset indenting of tags for nice output
    * @return The XML representation
    */
   public String toXml(String extraOffset) {
      return this.statusQosData.toXml(extraOffset, (Properties)null);
   }

   public String toString() {
      return toXml(null);
   }
}
