/*------------------------------------------------------------------------------
Name:      EraseRetQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.EraseReturnQos;


/**
 * Handling the returned QoS (quality of service) of a erase() call. 
 * <p />
 * @deprecated Use org.xmlBlaster.client.qos.EraseReturnQos instead
 */
public final class EraseRetQos implements I_RetQos
{
   private String ME = "EraseRetQos";
   private final EraseReturnQos retQos;

   /**
    * @deprecated Use org.xmlBlaster.client.qos.EraseReturnQos instead
    */
   public EraseRetQos(Global glob, String xmlQos) throws XmlBlasterException {
      this.retQos = new EraseReturnQos(glob, xmlQos);
   }

   public final String getStateId() {
      return this.retQos.getState();
   }

   public final String getStateInfo() {
      return this.retQos.getStateInfo();
   }

   public final String getOid() {
      return this.retQos.getKeyOid();
   }

   public final String toXml() {
      return toXml((String)null);
   }

   public final String toXml(String extraOffset) {
      return this.retQos.toXml(extraOffset);
   }
}
