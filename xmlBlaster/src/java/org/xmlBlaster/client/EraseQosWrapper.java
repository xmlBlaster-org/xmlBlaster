/*------------------------------------------------------------------------------
Name:      EraseQosWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.EraseQos;

/**
 * @deprecated Use org.xmlBlaster.client.qos.EraseQos instead, this class
 * will be removed in one of the next releases.
 */
public class EraseQosWrapper
{
   private String ME = "EraseQosWrapper";
   private final EraseQos eraseQos;

   public EraseQosWrapper() {
      eraseQos = new EraseQos(Global.instance());
   }

   public EraseQosWrapper(boolean notify) {
      this();
   }

   public String toString() {
      return toXml();
   }

   public String toXml() {
      return this.eraseQos.toXml();
   }
}
