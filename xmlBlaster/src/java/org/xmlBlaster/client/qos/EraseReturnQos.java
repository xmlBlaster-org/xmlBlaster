/*------------------------------------------------------------------------------
Name:      EraseReturnQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.StatusQosData;
import org.xmlBlaster.util.def.MethodName;


/**
 * Handling the returned QoS (quality of service) of a erase() call. 
 * <p />
 * If you are a Java client and use the interface I_XmlBlasterAccess
 * you get this object as the erase() return value.
 * <p />
 * Example:
 * <pre>
 *   &lt;qos>
 *     &lt;state id='OK' info='QUEUED[bilbo]'/>
 *     &lt;key oid='HelloWorld/>
 *  &lt;/qos>
 * </pre>
 * @see org.xmlBlaster.test.classtest.qos.StatusQosFactoryTest
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.erase.html" target="others">the interface.erase requirement</a>
 */
public final class EraseReturnQos
{
   private String ME = "EraseReturnQos";
   private final StatusQosData statusQosData;

   /**
    * Constructor which parses XML string. 
    */
   public EraseReturnQos(Global glob, String xmlQos) throws XmlBlasterException {
      this.statusQosData = glob.getStatusQosFactory().readObject(xmlQos);
      this.statusQosData.setMethod(MethodName.ERASE);
   }

   /**
    * Constructor which reuses a StatusQosData object. 
    */
   public EraseReturnQos(Global glob, StatusQosData statusQosData) {
      this.statusQosData = statusQosData;
      this.statusQosData.setMethod(MethodName.ERASE);
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
    * Get the oid of the message erased.
    */
   public String getKeyOid() {
      return this.statusQosData.getKeyOid();
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
      return this.statusQosData.toXml(extraOffset);
   }

   public String toString() {
      return toXml(null);
   }
}
