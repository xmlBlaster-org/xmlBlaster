/*------------------------------------------------------------------------------
Name:      SubscribeQosWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.engine.mime.Query;
import org.xmlBlaster.util.qos.AccessFilterQos;

/**
 * @deprecated Use org.xmlBlaster.client.qos.SubscribeQos instead, this class
 * will be removed in one of the next releases.
 */
public class SubscribeQosWrapper
{
   private final SubscribeQos subscribeQos;

   public SubscribeQosWrapper() {
      this.subscribeQos = new SubscribeQos(Global.instance());
   }

   public SubscribeQosWrapper(boolean content) {
      this();
      this.subscribeQos.setWantContent(content);
   }

   public final void setSubscriptionId(String subId) {
      this.subscribeQos.setSubscriptionId(subId);
   }

   public void setInitialUpdate(boolean initialUpdate) {
      this.subscribeQos.setWantInitialUpdate(initialUpdate);
   }

   public void setLocal(boolean local) {
      this.subscribeQos.setWantLocal(local);
   }

   public void setContent(boolean content) {
      this.subscribeQos.setWantContent(content);
   }

   public void addAccessFilter(AccessFilterQos filter) {
      this.subscribeQos.addAccessFilter(filter);
   }

   public String toString() {
      return toXml();
   }

   public String toXml() {
      return this.subscribeQos.toXml();
   }

   /** For testing: java org.xmlBlaster.client.SubscribeQosWrapper */
   public static void main(String[] args) {
      Global glob = new Global(args);
      try {
         SubscribeQosWrapper qos = new SubscribeQosWrapper();
         qos.setContent(false);
         qos.addAccessFilter(new AccessFilterQos(glob, "ContentLenFilter", "1.0", new Query(glob, "800")));
         qos.addAccessFilter(new AccessFilterQos(glob, "ContentLenFilter", "3.2", new Query(glob, "a<10")));
         System.out.println(qos.toXml());
      }
      catch (Throwable e) {
         System.out.println("Test failed: " + e.toString());
      }
   }
}
