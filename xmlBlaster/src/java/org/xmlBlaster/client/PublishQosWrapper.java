/*------------------------------------------------------------------------------
Name:      PublishQosWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.util.qos.address.Destination;


/**
 * This class encapsulates the qos of a publish() message.
 * @deprecated Use org.xmlBlaster.client.qos.PublishQos instead, this class
 * will be removed in one of the next releases.
 */
public class PublishQosWrapper
{
   private final PublishQos publishQos;

   public PublishQosWrapper() {
      this.publishQos = new PublishQos(Global.instance());
   }

   public PublishQosWrapper(Destination destination) {
      this.publishQos = new PublishQos(Global.instance(), destination);
   }

   public PublishQosWrapper(boolean isDurable) {
      this.publishQos = new PublishQos(Global.instance(), isDurable);
   }

   public PriorityEnum getPriority() {
      return this.publishQos.getPriority();
   }

   public void setPriority(PriorityEnum priority) {
      this.publishQos.setPriority(priority);
   }

   public void setForceUpdate(boolean force) {
      this.publishQos.setForceUpdate(force);
   }

   public void setReadonly(boolean readonly) {
      this.publishQos.setReadonly(readonly);
   }

   public void isVolatile(boolean isVolatile) {
      this.publishQos.setVolatile(isVolatile);
   }

   public boolean isVolatile() {
      return this.publishQos.isVolatile();
   }

   public void setDurable(boolean durable) {
      this.publishQos.setDurable(durable);
   }

   /**
    * WARNING: This method is renamed to setLifeTime() in the new PublishQos class
    */
   public void setRemainingLife(long remainingLife) {
      this.publishQos.setLifeTime(remainingLife);
   }

   public void addDestination(Destination destination) {
      this.publishQos.addDestination(destination);
   }

   public String toString() {
      return this.publishQos.toString();
   }

   public String toXml() {
      return this.publishQos.toXml();
   }

   /**
    *  For testing invoke: java org.xmlBlaster.client.PublishQosWrapper
    */
   public static void main( String[] args ) throws XmlBlasterException
   {
      {
         PublishQosWrapper qos =new PublishQosWrapper(new Destination(new SessionName(Global.instance(), "joe")));
         qos.addDestination(new Destination(new SessionName(Global.instance(), "Tim")));
         qos.setPriority(PriorityEnum.HIGH_PRIORITY);
         qos.setDurable(true);
         qos.setForceUpdate(true);
         qos.setReadonly(true);
         qos.setRemainingLife(60000);
         System.out.println(qos.toXml());
      }
      {
         PublishQosWrapper qos =new PublishQosWrapper();
         System.out.println("Minimal '" + qos.toXml() + "'");
      }
   }
}
