/*------------------------------------------------------------------------------
Name:      GetQosWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.util.qos.AccessFilterQos;

/**
 * This class encapsulates the qos of a get() message.
 * @deprecated Use org.xmlBlaster.client.qos.GetQos instead, this class
 * will be removed in one of the next releases.
 */
public class GetQosWrapper
{
   private String ME = "GetQosWrapper";
   private final GetQos getQos;

   public GetQosWrapper() {
      getQos = new GetQos(Global.instance());
   }

   public void addAccessFilter(AccessFilterQos filter) {
      this.getQos.addAccessFilter(filter);
   }

   public void setNoContent() {
      this.getQos.setWantContent(false);
   }

   public String toString() {
      return toXml();
   }

   public String toXml() {
      return this.getQos.toXml();
   }
}
