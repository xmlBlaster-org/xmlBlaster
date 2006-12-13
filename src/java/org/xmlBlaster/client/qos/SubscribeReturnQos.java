/*------------------------------------------------------------------------------
Name:      SubscribeReturnQos.java
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
 * Handling the returned QoS (quality of service) of a subscribe() call.
 * <p />
 * If you are a Java client and use the I_XmlBlasterAccess interface
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
public final class SubscribeReturnQos
{
   private final StatusQosData statusQosData;
   private final boolean isFakedReturn;

   /**
    * Constructor which parses XML string.
    * Use this for real returns from a server (-> isFakedReturn=false).
    */
   public SubscribeReturnQos(Global glob, String xmlQos) throws XmlBlasterException {
      this(glob, xmlQos, false);
   }

   /**
    * Constructor which parses XML string.
    * @param isFakedReturn true if the return value is faked from the client (on missing server connection)
    */
   public SubscribeReturnQos(Global glob, String xmlQos, boolean isFakedReturn) throws XmlBlasterException {
      this.isFakedReturn = isFakedReturn;
      this.statusQosData = glob.getStatusQosFactory().readObject(xmlQos);
      this.statusQosData.setMethod(MethodName.SUBSCRIBE);
   }

   /**
    * Constructor which reuses a StatusQosData object.
    */
   public SubscribeReturnQos(Global glob, StatusQosData statusQosData) {
      this(glob, statusQosData, false);
   }

   /**
    * Constructor which reuses a StatusQosData object.
    * @param isFakedReturn true if the return value is faked from the client (on missing server connection)
    */
   public SubscribeReturnQos(Global glob, StatusQosData statusQosData, boolean isFakedReturn) {
      this.isFakedReturn = isFakedReturn;
      this.statusQosData = statusQosData;
      this.statusQosData.setMethod(MethodName.SUBSCRIBE);
   }

   /**
    * Access the raw data object, usually you shouldn't do it.
    * @return The raw data object
    */
   public StatusQosData getData() {
      return statusQosData;
   }

   /**
    * Check if the subscription is queued on client side. 
    * <p>
    * This happens if the connection is polling.
    * </p>
    * <p>
    * The getStateInfo() is set to "QUEUED..." in such a case
    * </p>
    * @return true if the subscribe return value is faked from the client library (on missing server connection)
    */
   public boolean isFakedReturn() {
      return this.isFakedReturn;
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
    * Get the identifier (unique handle) for this subscription.
    */
   public final String getSubscriptionId() {
      return this.statusQosData.getSubscriptionId();
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
