/*------------------------------------------------------------------------------
Name:      DispatchStatistic.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch;

import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.xbformat.I_ProgressListener;

/**
 * Collecting data on how many messages / bytes are successfully delivered. 
 */
public class DispatchStatistic implements I_ProgressListener
{
   private long numUpdate = 0L;
   private long numUpdateOneway = 0L;
   private long numPublish = 0L;
   private long numSubscribe = 0L;
   private long numUnSubscribe = 0L;
   private long numConnect = 0L;
   private long numErase = 0L;
   private long numGet = 0L;
   private long numDisconnect = 0L;
   
   /** Holds the last exception text for JMX. */
   private String lastDeliveryException = "";
   /** Count the exceptions occurred since startup. */
   private int numDeliveryExceptions = 0;
   
   /** The number of bytes read from the currently incoming message */
   private long currBytesRead;
   /** The size of the currently incoming message */
   private long numBytesToRead;
   /** Overall bytes received since startup */
   private long overallBytesRead;
   /* The time-stamp when the last message was fully read */
   /*private long lastReadTimestamp; Not yet implemented as we should ignore the ping */
   
   /** The number of bytes written from the currently outgoing message */
   private long currBytesWritten;
   /** The size of the currently outgoing message */
   private long numBytesToWrite;
   /** Overall bytes send since startup */
   private long overallBytesWritten;
   
   protected long pingRoundTripDelay;
   protected long roundTripDelay;

   /* The time-stamp when the last message was fully written */
   /*private long lastWrittenTimestamp; Not yet implemented as we should ignore the ping */

   /**
    * Implements I_ProgressListener interface. 
    */
   public void progressRead(String name, long currBytesRead, long numBytes) {
      if (currBytesRead == numBytes) {
          this.overallBytesRead += numBytes;
      }
      this.currBytesRead = currBytesRead;
      this.numBytesToRead = numBytes;
   }

   /**
    * Implements I_ProgressListener interface. 
    */
   public void progressWrite(String name, long currBytesWritten, long numBytes) {
      if (currBytesWritten == numBytes) {
          this.overallBytesWritten += numBytes;
      }
      this.currBytesWritten = currBytesWritten;
      this.numBytesToWrite = numBytes;
   }

   /** The number of bytes read from the currently incoming message */
   public final long getCurrBytesRead() {
      return this.currBytesRead;
   }
   /** The size of the currently incoming message */
   public final long getNumBytesToRead() {
      return this.numBytesToRead;
   }
   /** Overall bytes received since startup */
   public final long getOverallBytesRead() {
      return this.overallBytesRead;
   }

   /** The number of bytes read from the currently outgoing message or response */
   public final long getCurrBytesWritten() {
      return this.currBytesWritten;
   }
   /** The size of the currently outgoing message or response */
   public final long getNumBytesToWrite() {
      return this.numBytesToWrite;
   }
   /** Overall bytes send since startup */
   public final long getOverallBytesWritten() {
      return this.overallBytesWritten;
   }

   /**
    * Add count messages which where updated
    * @param count The additional number of messages
    */
   public final void incrNumUpdate(long count) {
      this.numUpdate += count; // Not synchronized since we have only one DispatchWorker thread
   }

   /**
    * How many update where sent for this client, the sum of all session and
    * subject queues of this clients. 
    */ 
   public final long getNumUpdate() {
      return this.numUpdate;
   }   

   /**
    * Add count messages which where updated
    * @param count The additional number of messages
    */
   public final void incrNumUpdateOneway(long count) {
      this.numUpdateOneway += count; // Not synchronized since we have only one DispatchWorker thread
   }

   /**
    * How many update where sent for this client, the sum of all session and
    * subject queues of this clients. 
    */ 
   public final long getNumUpdateOneway() {
      return this.numUpdateOneway;
   }   

   /**
    * Add count messages which where published
    * @param count The additional number of messages
    */
   public final void incrNumPublish(long count) {
      this.numPublish += count; // Not synchronized since we have only one DispatchWorker thread
   }

   /**
    * How many messages where published. 
    */ 
   public final long getNumPublish() {
      return this.numPublish;
   }

   /**
    * Add count subscribe requests. 
    * @param count The additional number of subscribe requests
    */
   public final void incrNumSubscribe(long count) {
      this.numSubscribe += count; // Sync for client side?
   }

   /**
    * How many subscribe requests sent. 
    */ 
   public final long getNumSubscribe() {
      return this.numSubscribe;
   }

   /**
    * Add count unSubscribe requests. 
    * @param count The additional number of unSubscribe requests
    */
   public final void incrNumUnSubscribe(long count) {
      this.numUnSubscribe += count; // Sync for client side?
   }

   /**
    * How many unSubscribe requests sent. 
    */ 
   public final long getNumUnSubscribe() {
      return this.numUnSubscribe;
   }

   /**
    * Add count erase requests. 
    * @param count The additional number of erase requests
    */
   public final void incrNumErase(long count) {
      this.numErase += count; // Sync for client side?
   }

   /**
    * How many erase requests sent. 
    */ 
   public final long getNumErase() {
      return this.numErase;
   }

   /**
    * Add count get requests. 
    * @param count The additional number of get equests
    */
   public final void incrNumGet(long count) {
      this.numGet += count; // Sync for client side?
   }

   /**
    * How many synchronous get requests sent. 
    */ 
   public final long getNumGet() {
      return this.numGet;
   }

   /**
    * Add count connect requests. 
    * @param count The additional number of connect requests
    */
   public final void incrNumConnect(long count) {
      this.numConnect += count; // Sync for client side?
   }

   /**
    * How many connect requests sent. 
    */ 
   public final long getNumConnect() {
      return this.numConnect;
   }

   /**
    * Add count disconnect requests. 
    * @param count The additional number of disconnect requests
    */
   public final void incrNumDisconnect(long count) {
      this.numDisconnect += count; // Sync for client side?
   }

   /**
    * How many disconnect requests sent. 
    */ 
   public final long getNumDisconnect() {
      return this.numDisconnect;
   }

   /**
    * Holds the last exception text for JMX. 
    */
   public final String getLastDeliveryException() {
      return this.lastDeliveryException;
   }

   /**
    * Set the last exception text for JMX. 
    */
   public final void setLastDeliveryException(String lastDeliveryException) {
      this.lastDeliveryException = lastDeliveryException;
   }

   /**
    * Count the exceptions occurred since startup. 
    */
   public final void incrNumDeliveryExceptions(int count) {
      this.numDeliveryExceptions += count;
   }

   /**
    * Count the exceptions occurred since startup. 
    */
   public final int getNumDeliveryExceptions() {
      return this.numDeliveryExceptions;
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
      sb.append(offset).append("<DispatchStatistic");
      if (getNumUpdate() > 0) {
         sb.append("' numUpdate='").append(getNumUpdate());
      }
      if (getNumUpdateOneway() > 0) {
         sb.append("' numUpdateOneway='").append(getNumUpdateOneway());
      }
      if (getNumPublish() > 0) {
         sb.append("' numPublish='").append(getNumPublish());
      }
      //sb.append("' numGet='").append(getNumGet());
      if (getNumSubscribe() > 0) {
         sb.append("' numSubscribe='").append(getNumSubscribe()).append("'/>");
      }
      if (getNumUnSubscribe() > 0) {
         sb.append("' numUnSubscribe='").append(getNumUnSubscribe()).append("'/>");
      }
      if (getNumErase() > 0) {
         sb.append("' numErase='").append(getNumErase()).append("'/>");
      }
      if (getNumGet() > 0) {
         sb.append("' numGet='").append(getNumGet()).append("'/>");
      }
      if (getNumConnect() > 0) {
         sb.append("' numConnect='").append(getNumConnect()).append("'/>");
      }
      if (getNumDisconnect() > 0) {
         sb.append("' numDisconnect='").append(getNumDisconnect()).append("'/>");
      }
      return sb.toString();
   }

   /**
    * @return Returns the pingRoundTripDelay.
    */
   public long getPingRoundTripDelay() {
      return this.pingRoundTripDelay;
   }

   /**
    * @param pingRoundTripDelay The pingRoundTripDelay to set.
    */
   public void setPingRoundTripDelay(long pingRoundTripDelay) {
      this.pingRoundTripDelay = pingRoundTripDelay;
   }

   /**
    * @return Returns the roundTripDelay.
    */
   public long getRoundTripDelay() {
      return this.roundTripDelay;
   }

   /**
    * @param roundTripDelay The roundTripDelay to set.
    */
   public void setRoundTripDelay(long roundTripDelay) {
      this.roundTripDelay = roundTripDelay;
   }
}

