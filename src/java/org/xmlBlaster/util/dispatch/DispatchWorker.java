/*------------------------------------------------------------------------------
Name:      DispatchWorker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.dispatch.plugins.I_MsgDispatchInterceptor;
import java.util.ArrayList;


/**
 * Takes messages from the queue and tries to send them back to a client. 
 * @author xmlBlaster@marcelruff.info
 */
public final class DispatchWorker implements Runnable
{
   public final String ME;
   private static Logger log = Logger.getLogger(DispatchWorker.class.getName());

   private DispatchManager dispatchManager;
   private I_Queue msgQueue;

   public DispatchWorker(Global glob, DispatchManager mgr) {
      this.dispatchManager = mgr;
      this.msgQueue = mgr.getQueue();
      ME = "DispatchWorker-" + this.msgQueue.getStorageId(); 
   }

   /**
    * Synchronous push mode. 
    * The 'synchronous' ACK is transported in entryList(i).getReturnObj()
    * which contains pre parsed objects like SubscribeQosReturn etc.
    */
   public void run(ArrayList entryList) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Starting push remote dispatch of " + ((entryList!=null)?entryList.size():0) + " entries.");
      MsgQueueEntry[] entries = null;
      try {
         I_MsgDispatchInterceptor msgInterceptor = dispatchManager.getMsgDispatchInterceptor();
         if (msgInterceptor != null) {
            log.severe("Communication dispatch plugin support is missing in sync mode - not implemented");
            /*!!! filter or whatever
            try {
               entryList = msgInterceptor.handleNextMessages(dispatchManager, entryList); // should call prepareMsgsFromQueue() immediately
            }
            catch (Throwable e) {
               entries = (MsgQueueEntry[])entryList.toArray(new MsgQueueEntry[entryList.size()]);
               throw e;
            }
            */
         }

         if (entryList == null || entryList.size() < 1) {
            if (log.isLoggable(Level.FINE)) log.fine("Got zero messages from to deliver, expected at least one");
            return;
         }

         entries = (MsgQueueEntry[])entryList.toArray(new MsgQueueEntry[entryList.size()]);
         
         if (log.isLoggable(Level.FINE)) log.fine("Sending now " + entries.length + " messages ...");
         
         dispatchManager.getDispatchConnectionsHandler().send(entries); // entries are filled with return values

         /*ArrayList defaultEntries = */this.dispatchManager.filterDistributorEntries(entryList, null);
         if (log.isLoggable(Level.FINE)) log.fine("Commit of successful sending of " + entryList.size() + " messages done, current queue size is " + this.msgQueue.getNumOfEntries() + " '" + ((MsgQueueEntry)entryList.get(0)).getLogId() + "'");
      }
      catch(Throwable throwable) {
         ArrayList entriesWithNoDistributor = this.dispatchManager.filterDistributorEntries(entryList, throwable);
         if (entriesWithNoDistributor.size() > 0) dispatchManager.handleSyncWorkerException(entriesWithNoDistributor, throwable);
      }
   }


   /**
    * Asynchronous pull mode, invoked by DispatchWorkerPool.execute() -> see DispatchManager calling it
    */
   public void run() {
      if (log.isLoggable(Level.FINER)) log.finer("Starting remote dispatch with " + this.msgQueue.getNumOfEntries() + " entries.");
      ArrayList entryList = null;
      ArrayList entryListChecked = null;
      try {
         I_MsgDispatchInterceptor msgInterceptor = dispatchManager.getMsgDispatchInterceptor();
         if (msgInterceptor != null) {
               entryListChecked = msgInterceptor.handleNextMessages(dispatchManager, null); // should call prepareMsgsFromQueue() immediately
               entryList = entryListChecked;
         }
         else {
            //synchronized (this.msgQueue) {
               //entryList = (MsgQueueEntry[])this.msgQueue.take(-1); --> get()
               // not blocking and only all of the same priority:
               entryList = this.msgQueue.peekSamePriority(dispatchManager.getBurstModeMaxEntries(), dispatchManager.getBurstModeMaxBytes()); // -1, -1L -> get all entries in cache
               entryListChecked = dispatchManager.prepareMsgsFromQueue(entryList);
            //}
         }

         if (entryList == null || entryList.size() < 1) {
            if (log.isLoggable(Level.FINE)) log.fine("Got zero messages from queue, expected at least one, can happen if messages expired or client disconnected in the mean time.");
            return;
         }

         if (entryListChecked != null && entryListChecked.size() > 0) {
            MsgQueueEntry[] entries = (MsgQueueEntry[])entryListChecked.toArray(new MsgQueueEntry[entryListChecked.size()]);
            
            if (log.isLoggable(Level.FINE)) log.fine("Sending now " + entries.length + " messages ..., current queue size is " + this.msgQueue.getNumOfEntries() + " '" + entries[0].getLogId() + "'");
            
            dispatchManager.getDispatchConnectionsHandler().send(entries);

            // Here an exception is thrown or
            // the RETURN value is transferred in the entries[i].getReturnObj(), for oneway updates it is null
            
            if (log.isLoggable(Level.FINE)) log.fine("Sending of " + entries.length + " messages done, current queue size is " + this.msgQueue.getNumOfEntries());
         }
         // messages are successfully sent, remove them now from queue (sort of a commit()):
         // We remove filtered/destroyed messages as well (which doen't show up in entryListChecked)
         MsgQueueEntry[] entries = (MsgQueueEntry[])entryList.toArray(new MsgQueueEntry[entryList.size()]);
         MsgUnit[] msgUnits = null;
         if (msgInterceptor != null) { // we need to do this before removal since the msgUnits are weak references and would be deleted by gc
            msgUnits = new MsgUnit[entries.length];
            for (int i=0; i < msgUnits.length; i++) {
               msgUnits[i] = entries[i].getMsgUnit();
            }
         }
         this.msgQueue.removeRandom(entries);
         /*(currently only done in sync invocation)
         ArrayList defaultEntries = sendAsyncResponseEvent(entryList);
         if (defaultEntries.size() > 0) {
            MsgQueueEntry[] entries = (MsgQueueEntry[])defaultEntries.toArray(new MsgQueueEntry[defaultEntries.size()]);
            this.msgQueue.removeRandom(entries);
         }
         */
         if (msgInterceptor != null) {
            msgInterceptor.postHandleNextMessages(dispatchManager, msgUnits);
         }
         if (log.isLoggable(Level.FINE)) log.fine("Commit of successful sending of " + entryList.size() + " messages done, current queue size is " + this.msgQueue.getNumOfEntries() + " '" + ((MsgQueueEntry)entryList.get(0)).getLogId() + "'");
      }
      catch(Throwable throwable) {
         dispatchManager.handleWorkerException(entryList, throwable);
         // ArrayList entriesWithNoDistributor = this.sendAsyncResponseEvent(entryList);
         // if (entriesWithNoDistributor.size() > 0) dispatchManager.handleWorkerException(entriesWithNoDistributor, throwable);
      }
      finally {
         this.dispatchManager.setDispatchWorkerIsActive(false);
         entryList = null;
         shutdown();
      }
   }

   void shutdown() {
      // Commented out to avoid NPE
      //this.dispatchManager = null;
      //this.msgQueue = null;
   }
}

