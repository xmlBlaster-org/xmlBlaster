/*------------------------------------------------------------------------------
Name:      DeliveryStatistic.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch;

import org.xmlBlaster.util.enum.Constants;

/**
 * Collecting data on how many messages are successfully delivered. 
 */
public class DeliveryStatistic
{
   private long numUpdate = 0L;
   private long numPublish = 0L;

   /**
    * Add count messages which where updated
    * @param count The additional number of messages
    */
   public final void incrNumUpdate(long count) {
      this.numUpdate += count; // Not synchronized since we have only one DeliveryWorker thread
   }

   /**
    * How many update where sent for this client, the sum of all session and
    * subject queues of this clients. 
    */ 
   public final long getNumUpdate() {
      return this.numUpdate;
   }   

   /**
    * Add count messages which where published
    * @param count The additional number of messages
    */
   public final void incrNumPublish(long count) {
      this.numPublish += count; // Not synchronized since we have only one DeliveryWorker thread
   }

   /**
    * How many messages where published. 
    */ 
   public final long getNumPublish() {
      return this.numPublish;
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of SessionInfo as a XML ASCII string
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;
      sb.append(offset).append("<DeliveryStatistic numUpdate='").append(getNumUpdate()).append("' numPublish='").append(getNumPublish()).append("'/>");
      return sb.toString();
   }
}

