/*------------------------------------------------------------------------------
Name:      CbWorker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding messages waiting on client callback.
Version:   $Id: CbWorker.java,v 1.9 2002/06/18 10:17:08 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.callback;

import org.jutils.runtime.Sleeper;
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.queue.MsgQueue;
import org.xmlBlaster.engine.queue.SessionMsgQueue;
import org.xmlBlaster.engine.queue.MsgQueueEntry;


/**
 * Takes messages from the queue and tries to send them back to a client. 
 */
public class CbWorker implements Runnable
{
   public final String ME;
   private MsgQueue msgQueue;
   private Global glob;
   private final LogChannel log;

   public CbWorker(Global glob, MsgQueue q)
   {
      ME = "CbWorker:" + q.getName(); 
      this.glob = glob;
      this.log = glob.getLog("cb");
      this.msgQueue = q;
   }

   public void run()
   {
      if (log.CALL) log.call(ME, "Starting callback job with " + msgQueue.size() + " entries.");
      MsgQueueEntry[] entries = null;
      try {
         CbManager cbManager = this.msgQueue.getCbManager();
         synchronized (this.msgQueue.getMonitor()) {
            entries = this.msgQueue.takeMsgs();
         }
         if (entries == null || entries.length < 1) {
            log.warn(ME, "Got zero messages from queue, expected at least one");
            return;
         }
         
         //log.info(ME, "Sending now " + entries.length + " messages back ...");
         
         String[] returnVals = cbManager.sendUpdate(entries, this.msgQueue.getErrorCounter()); // redeliver == errorCounter

         this.msgQueue.resetErrorCounter(); // callback is fine

         this.msgQueue.incrNumUpdate(returnVals.length);

         // Delete volatile messages ...
         this.msgQueue.checkForVolatileErase(entries);

         //log.info(ME, "Sending of " + entries.length + " messages done");
      }
      catch(Throwable e) {
         if (e instanceof XmlBlasterException) {
            log.warn(ME, "Callback failed: " + e.toString());
         }
         else {
            log.error(ME, "Callback failed: " + e.toString());
            e.printStackTrace();
         }
         if (entries != null) {
            if (log.TRACE) log.trace(ME, "Recovering " + entries.length + " messages");
            try {
               this.msgQueue.putMsgs(entries); // recover the messages
            }
            catch (Throwable e2) {
               e2.printStackTrace();
               log.error(ME, "Disaster: Can't recover " + entries.length + " messages - the messages are lost: " + e2.toString());
            }
         }
         msgQueue.connectionLost();  // TODO: We should distinguish which address in CbInfo failed
      }
      finally {
         //synchronized (this.msgQueue.getMonitor()) {
            this.msgQueue.setCbWorkerIsActive(false);
            if (msgQueue.size() > 0 && !msgQueue.isShutdown()) {
               // log.info(ME, "Finished callback job. Giving a kick to send the remaining " + msgQueue.size() + " messages.");
               if (log.TRACE) log.trace(ME, "Finished callback job. Giving a kick to send the remaining " + msgQueue.size() + " messages.");
               try{ msgQueue.activateCallbackWorker(); } catch(Throwable e) { log.error(ME, e.toString()); e.printStackTrace(); }// Assure the queue is flushed with another worker
            }
         //}
      }

      //log.info(ME, "Finished callback job. " + entries.length + " sent, " + msgQueue.size() + " messages in the queue. " + msgQueue.getCbWorkerPoolStatistic());
   }
}

