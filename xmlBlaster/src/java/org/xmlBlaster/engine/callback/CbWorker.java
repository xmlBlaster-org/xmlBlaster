/*------------------------------------------------------------------------------
Name:      CbWorker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding messages waiting on client callback.
Version:   $Id: CbWorker.java,v 1.4 2002/03/17 13:33:48 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.callback;

import org.jutils.runtime.Sleeper;
import org.xmlBlaster.util.Log;
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

   public CbWorker(Global glob, MsgQueue q)
   {
      ME = "CbWorker:" + q.getName(); 
      this.glob = glob;
      this.msgQueue = q;
   }

   public void run()
   {
      if (Log.CALL) Log.call(ME, "Starting callback job with " + msgQueue.size() + " entries.");
      MsgQueueEntry[] entries = null;
      try {
         CbInfo cbInfo = this.msgQueue.getCbInfo();
         if (cbInfo != null) {
            synchronized (this.msgQueue.getMonitor()) {
               entries = this.msgQueue.takeMsgs();
            }
            if (entries == null || entries.length < 1) {
               Log.warn(ME, "Got zero messages from queue, expected at least one");
               return;
            }
            
            Log.info(ME, "Sending now " + entries.length + " messages back ...");
            
            cbInfo.sendUpdate(entries, this.msgQueue.getErrorCounter()); // redeliver == errorCounter
            
            this.msgQueue.resetErrorCounter(); // callback is fine

            // Delete volatile messages ...
            for (int ii=0; ii<entries.length; ii++) {
               entries[ii].getMessageUnitWrapper().addEnqueueCounter(-1);
               if (entries[ii].getMessageUnitWrapper().getEnqueueCounter() == 0 &&
                     entries[ii].getMessageUnitWrapper().getPublishQos().isVolatile()) {
                  if (msgQueue instanceof SessionMsgQueue) {
                     SessionMsgQueue q = (SessionMsgQueue)msgQueue;
                     try {
                        glob.getRequestBroker().eraseVolatile(q.getSessionInfo(), entries[ii].getMessageUnitWrapper().getMessageUnitHandler());
                     }
                     catch (XmlBlasterException ex) {
                        ex.printStackTrace();
                        Log.error(ME, "Erasing of volatile message failed");
                     }
                  }
               }
            }

            //Log.info(ME, "Sending of " + entries.length + " messages done");
         }
         else
            Log.error(ME, "No CbInfo available");
      }
      catch(Throwable e) {
         if (e instanceof XmlBlasterException) {
            Log.warn(ME, "Callback failed: " + e.toString());
         }
         else {
            Log.error(ME, "Callback failed: " + e.toString());
            e.printStackTrace();
         }
         if (entries != null) {
            Log.info(ME, "Recovering " + entries.length + " messages");
            try {
               this.msgQueue.putMsgs(entries); // recover the messages
            }
            catch (Throwable e2) {
               e2.printStackTrace();
               Log.error(ME, "Disaster: Can't recover " + entries.length + " messages - the messages are lost: " + e2.toString());
            }
         }
         msgQueue.connectionLost();  // TODO: We should distinguish which address in CbInfo failed
      }
      finally {
         //synchronized (this.msgQueue.getMonitor()) {
            this.msgQueue.setCbWorkerIsActive(false);
            if (msgQueue.size() > 0 && !msgQueue.isShutdown()) {
               Log.info(ME, "Finished callback job. Giving a kick to send the remaining " + msgQueue.size() + " messages.");
               try{ msgQueue.activateCallbackWorker(); } catch(Throwable e) { Log.error(ME, e.toString()); }// Assure the queue is flushed with another worker
            }
         //}
      }

      Log.info(ME, "Finished callback job. " + entries.length + " sent, " + msgQueue.size() + " messages in the queue. " + msgQueue.getCbWorkerPoolStatistic());
   }
}

