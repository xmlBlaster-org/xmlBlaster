/*------------------------------------------------------------------------------
Name:      DeliveryWorker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch;

import org.jutils.runtime.Sleeper;
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.error.MsgErrorInfo;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.dispatch.plugins.I_MsgDeliveryInterceptor;

import java.util.ArrayList;


/**
 * Takes messages from the queue and tries to send them back to a client. 
 * @author xmlBlaster@marcelruff.info
 */
public final class DeliveryWorker implements Runnable
{
   public final String ME;
   private final Global glob;
   private final LogChannel log;

   private DeliveryManager deliveryManager;
   private I_Queue msgQueue;

   public DeliveryWorker(Global glob, DeliveryManager mgr) {
      this.glob = glob;
      this.log = glob.getLog("dispatch");
      this.deliveryManager = mgr;
      this.msgQueue = mgr.getQueue();
      ME = "DeliveryWorker-" + this.msgQueue.getStorageId(); 
   }

   /**
    * Synchronous push mode
    * @return The 'synchronous' ACK or null if entryList was empty.
    *         The array contains pre parsed objects like SubscribeQosReturn etc.
    */
   public Object run(ArrayList entryList) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Starting push remote delivery of " + ((entryList!=null)?entryList.size():0) + " entries.");
      MsgQueueEntry[] entries = null;
      try {
         I_MsgDeliveryInterceptor msgInterceptor = deliveryManager.getMsgDeliveryInterceptor();
         if (msgInterceptor != null) {
            log.warn(ME, "Communication plugin support is missing - not implemented");
            /*!!! filter or whatever
            try {
               entryList = msgInterceptor.handleNextMessages(deliveryManager, entryList); // should call prepareMsgsFromQueue() immediately
            }
            catch (Throwable e) {
               entries = (MsgQueueEntry[])entryList.toArray(new MsgQueueEntry[entryList.size()]);
               throw e;
            }
            */
         }

         if (entryList == null || entryList.size() < 1) {
            if (log.TRACE) log.trace(ME, "Got zero messages from to deliver, expected at least one");
            return null;
         }

         entries = (MsgQueueEntry[])entryList.toArray(new MsgQueueEntry[entryList.size()]);
         
         if (log.TRACE) log.trace(ME, "Sending now " + entries.length + " messages ...");
         
         return deliveryManager.getDeliveryConnectionsHandler().send(entries);
      }
      catch(Throwable throwable) {
         return deliveryManager.handleSyncWorkerException(entryList, throwable);
      }
   }

   /*
    * Simulate return values, and manipulate missing informations into entries. 
    * <p>
    * For example for the PublishQos an oid is generated if none is available
    * or for the SubscriptionQos a subscriptionId is created
    * </p>
   private Object[] generateReturnValues(MsgQueueEntry[] entries) {
      Object[] returnQos = new Object[entries.length];
      for (int ii=0; ii<entries.length; ii++) {
         StatusQosData statRetQos = new StatusQosData(glob);
         statRetQos.setStateInfo(Constants.INFO_QUEUED);
         if (Constants.UPDATE.equals(entries[ii].getEmbeddedType())) {
            returnQos[ii] = new UpdateReturnQosServer(glob, statRetQos);
         }
         else if (Constants.PUBLISH.equals(entries[ii].getEmbeddedType())) {
            // TODO: See XmlBlasterConnection.getAndReplaceOid
            //statRetQos.setKeyOid(oid);
            returnQos[ii] = new PublishReturnQos(glob, statRetQos);
         }
      }
      return returnQos;
   }
    */

   /**
    * Asynchronous pull mode, invoked by DeliveryWorkerPool.execute() -> see DeliveryManager calling it
    */
   public void run() {
      if (log.CALL) log.call(ME, "Starting remote delivery with " + this.msgQueue.getNumOfEntries() + " entries.");
      ArrayList entryList = null;
      ArrayList entryListChecked = null;
      try {
         I_MsgDeliveryInterceptor msgInterceptor = deliveryManager.getMsgDeliveryInterceptor();
         if (msgInterceptor != null) {
               entryListChecked = msgInterceptor.handleNextMessages(deliveryManager, null); // should call prepareMsgsFromQueue() immediately
               entryList = entryListChecked;
         }
         else {
            synchronized (this.msgQueue) {
               //entryList = (MsgQueueEntry[])this.msgQueue.take(-1); --> get()
               // not blocking and only all of the same priority:
               entryList = this.msgQueue.peekSamePriority(-1, -1L);
               entryListChecked = deliveryManager.prepareMsgsFromQueue(entryList);
            }
         }

         if (entryList == null || entryList.size() < 1) {
            if (log.TRACE) log.trace(ME, "Got zero messages from queue, expected at least one, can happen if messages expired or client disconnected in the mean time.");
            return;
         }

         if (entryListChecked != null && entryListChecked.size() > 0) {
            MsgQueueEntry[] entries = (MsgQueueEntry[])entryListChecked.toArray(new MsgQueueEntry[entryListChecked.size()]);
            
            if (log.TRACE) log.trace(ME, "Sending now " + entries.length + " messages ..., current queue size is " + this.msgQueue.getNumOfEntries() + " '" + entries[0].getLogId() + "'");
            
            Object returnVals = deliveryManager.getDeliveryConnectionsHandler().send(entries);
            
            if (log.TRACE) log.trace(ME, "Sending of " + entries.length + " messages done, current queue size is " + this.msgQueue.getNumOfEntries());
         }

         /*{
            int n = entries.length;
            for(int i=0; i<n; i++)
               log.error(ME, "DEBUG ONLY - after sent" + ((org.xmlBlaster.engine.queuemsg.MsgQueueUpdateEntry)entries[i]).getMsgUnit().toXml());
         }*/

         // messages are successfully sent, remove them now from queue (sort of a commit()):
         // We remove filtered/destroyed messages as well (which doen't show up in entryListChecked)
         MsgQueueEntry[] entries = (MsgQueueEntry[])entryList.toArray(new MsgQueueEntry[entryList.size()]);
         this.msgQueue.removeRandom(entries);

         if (log.TRACE) log.trace(ME, "Commit of successful sending of " + entries.length + " messages done, current queue size is " + this.msgQueue.getNumOfEntries() + " '" + entries[0].getLogId() + "'");
      }
      catch(Throwable throwable) {
         deliveryManager.handleWorkerException(entryList, throwable);
      }
      finally {
         this.deliveryManager.setDeliveryWorkerIsActive(false);
         entryList = null;
         shutdown();
      }
   }

   void shutdown() {
      this.deliveryManager = null;
      this.msgQueue = null;
   }
}

