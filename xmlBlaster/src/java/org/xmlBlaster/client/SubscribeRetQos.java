/*------------------------------------------------------------------------------
Name:      SubscribeRetQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the returned QoS (quality of service)
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.SubscribeReturnQos;


/**
 * Handling the returned QoS (quality of service) of a subscribe() call. 
 * @deprecated Use org.xmlBlaster.client.qos.SubscribeReturnQos instead
 */
public final class SubscribeRetQos implements I_RetQos
{
   private String ME = "SubscribeRetQos";
   private final SubscribeReturnQos retQos;

   /**
    * @deprecated Use org.xmlBlaster.client.qos.SubscribeReturnQos instead
    */
   public SubscribeRetQos(Global glob, String xmlQos) throws XmlBlasterException {
      this.retQos = new SubscribeReturnQos(glob, xmlQos);
   }

   public final String getStateId() {
      return this.retQos.getState();
   }

   public final String getStateInfo() {
      return this.retQos.getStateInfo();
   }

   public final String getSubscriptionId() {
      return this.retQos.getSubscriptionId();
   }

   public final String toXml() {
      return toXml((String)null);
   }

   public final String toXml(String extraOffset) {
      return this.retQos.toXml(extraOffset);
   }
}
