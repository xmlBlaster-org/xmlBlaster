/*------------------------------------------------------------------------------
Name:      UpdateReturnQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.qos;

import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.qos.StatusQosData;
import org.xmlBlaster.util.def.MethodName;

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
public final class UpdateReturnQos
{
   private String ME = "UpdateReturnQos";
   private final StatusQosData statusQosData;

   /**
    * Create an instance with Constants.STATE_OK
    */
   public UpdateReturnQos(Global glob) {
      this(glob, Constants.STATE_OK);
   }

   /**
    * Constructor which allows you to set the return state. 
    * @param state The state to return to the server. See Constants.java
    */
   public UpdateReturnQos(Global glob, String state) {
      this.statusQosData = new StatusQosData(glob, MethodName.UPDATE);
      this.statusQosData.setState(state);
   }

   /**
    * Constructor which allows you to set the return state. 
    * @param statusQosData The pre filled raw data object
    */
   public UpdateReturnQos(Global glob, StatusQosData statusQosData) {
      this.statusQosData = statusQosData;
   }

   /**
    * @param state The state to return to the server.
    *   e.g. Contants.STATE_OK, see Constants.java
    */
   public void setState(String state) {
      this.statusQosData.setState(state);
   }

   /**
    * @param stateInfo The state info attribute to return to the server.
    */
   public void setStateInfo(String stateInfo) {
      this.statusQosData.setStateInfo(stateInfo);
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
      return toXml((String)null);
   }
}
