/*------------------------------------------------------------------------------
Name:      DispatchWorker.java
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
import org.xmlBlaster.util.dispatch.plugins.I_MsgDispatchInterceptor;
import org.xmlBlaster.engine.MsgUnitWrapper;

import java.util.ArrayList;


/**
 * Takes messages from the queue and tries to send them back to a client. 
 * @author xmlBlaster@marcelruff.info
 */
public final class DispatchWorker implements Runnable
{
   public final String ME;
   private final Global glob;
   private final LogChannel log;

   private DispatchManager dispatchManager;
   private I_Queue msgQueue;

   public DispatchWorker(Global glob, DispatchManager mgr) {
      this.glob = glob;
      this.log = glob.getLog("dispatch");
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
      if (log.CALL) log.call(ME, "Starting push remote dispatch of " + ((entryList!=null)?entryList.size():0) + " entries.");
      MsgQueueEntry[] entries = null;
      try {
         I_MsgDispatchInterceptor msgInterceptor = dispatchManager.getMsgDispatchInterceptor();
         if (msgInterceptor != null) {
            log.error(ME, "Communication dispatch plugin support is missing in sync mode - not implemented");
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
            if (log.TRACE) log.trace(ME, "Got zero messages from to deliver, expected at least one");
            return;
         }

         entries = (MsgQueueEntry[])entryList.toArray(new MsgQueueEntry[entryList.size()]);
         
         if (log.TRACE) log.trace(ME, "Sending now " + entries.length + " messages ...");
         
         dispatchManager.getDispatchConnectionsHandler().send(entries); // entries are filled with return values
      }
      catch(Throwable throwable) {
         dispatchManager.handleSyncWorkerException(entryList, throwable);
      }
   }

   /**
    * Asynchronous pull mode, invoked by DispatchWorkerPool.execute() -> see DispatchManager calling it
    */
   public void run() {
      if (log.CALL) log.call(ME, "Starting remote dispatch with " + this.msgQueue.getNumOfEntries() + " entries.");
      ArrayList entryList = null;
      ArrayList entryListChecked = null;
      try {
         I_MsgDispatchInterceptor msgInterceptor = dispatchManager.getMsgDispatchInterceptor();
         if (msgInterceptor != null) {
               entryListChecked = msgInterceptor.handleNextMessages(dispatchManager, null); // should call prepareMsgsFromQueue() immediately
               entryList = entryListChecked;
         }
         else {
            synchronized (this.msgQueue) {
               //entryList = (MsgQueueEntry[])this.msgQueue.take(-1); --> get()
               // not blocking and only all of the same priority:
               entryList = this.msgQueue.peekSamePriority(-1, -1L);
               entryListChecked = dispatchManager.prepareMsgsFromQueue(entryList);
            }
         }

         if (entryList == null || entryList.size() < 1) {
            if (log.TRACE) log.trace(ME, "Got zero messages from queue, expected at least one, can happen if messages expired or client disconnected in the mean time.");
            return;
         }

         if (entryListChecked != null && entryListChecked.size() > 0) {
            MsgQueueEntry[] entries = (MsgQueueEntry[])entryListChecked.toArray(new MsgQueueEntry[entryListChecked.size()]);
            
            if (log.TRACE) log.trace(ME, "Sending now " + entries.length + " messages ..., current queue size is " + this.msgQueue.getNumOfEntries() + " '" + entries[0].getLogId() + "'");
            
            dispatchManager.getDispatchConnectionsHandler().send(entries);
            
            if (log.TRACE) log.trace(ME, "Sending of " + entries.length + " messages done, current queue size is " + this.msgQueue.getNumOfEntries());
         }

         if (false) {
            int n = entryList.size();
            for(int i=0; i<n; i++) {
               MsgUnitWrapper msgUnitWrapper = ((org.xmlBlaster.engine.queuemsg.MsgQueueUpdateEntry)entryList.get(i)).getMsgUnitWrapper();
               log.info(ME, "DEBUG ONLY - after sent size=" + msgUnitWrapper.getSizeInBytes() + ":" + msgUnitWrapper.toXml());
            }
         }

         // messages are successfully sent, remove them now from queue (sort of a commit()):
         // We remove filtered/destroyed messages as well (which doen't show up in entryListChecked)
         MsgQueueEntry[] entries = (MsgQueueEntry[])entryList.toArray(new MsgQueueEntry[entryList.size()]);
         this.msgQueue.removeRandom(entries);

         if (log.TRACE) log.trace(ME, "Commit of successful sending of " + entries.length + " messages done, current queue size is " + this.msgQueue.getNumOfEntries() + " '" + entries[0].getLogId() + "'");
      }
      catch(Throwable throwable) {
         dispatchManager.handleWorkerException(entryList, throwable);
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

