/*------------------------------------------------------------------------------
Name:      UnSubscribeQosWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.UnSubscribeQos;

/**
 * @deprecated Use org.xmlBlaster.client.qos.UnSubscribeQos instead, this class
 * will be removed in one of the next releases.
 */
public class UnSubscribeQosWrapper
{
   private final UnSubscribeQos unSubscribeQos;

   public UnSubscribeQosWrapper() {
      unSubscribeQos = new UnSubscribeQos(Global.instance());
   }

   public String toString() {
      return toXml();
   }

   public String toXml() {
      return this.unSubscribeQos.toXml();
   }
}
