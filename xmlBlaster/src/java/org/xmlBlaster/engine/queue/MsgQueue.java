/*------------------------------------------------------------------------------
Name:      MsgQueue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding messages waiting on client callback.
Version:   $Id: MsgQueue.java,v 1.15 2002/05/26 20:16:17 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.queue;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.helper.CbQueueProperty;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.callback.CbInfo;
import org.xmlBlaster.engine.callback.CbWorkerPool;
import org.xmlBlaster.engine.callback.CbWorker;

import java.util.Comparator;
import EDU.oswego.cs.dl.util.concurrent.BoundedPriorityQueue;


/**
 * Queueing messages to send back to a client. 
 */
public class MsgQueue extends BoundedPriorityQueue implements I_Timeout
{
   private String ME = "MsgQueue";
   private String name;
   protected CbQueueProperty property;
   protected final Global glob;
   private final CbWorkerPool cbWorkerPool;
   protected CbInfo cbInfo = null;
   protected final LogChannel log;
   private final Timeout burstModeTimer;
   private Timestamp timerKey = null;
   private boolean cbWorkerIsActive = false;
   /** Contains how many callbacks failed */
   private int errorCounter = 0;
   private boolean isShutdown = false;


   /**
    * @param queueName "subject:joe", "subject:jack", "session:c0xfrt", "unrelated:XML-RPC:http://www.xy.com:8080"
    * @param prop The behavior of the queue
    */
   public MsgQueue(String queueName, CbQueueProperty prop, Global glob) throws XmlBlasterException
   {
      super(prop.getMaxMsg(), new MsgComparator());
      this.glob = glob;
      this.log = glob.getLog("cb");
      this.ME = "MsgQueue:" + queueName;
      this.name = queueName;
      this.cbWorkerPool = glob.getCbWorkerPool();
      this.burstModeTimer = glob.getBurstModeTimer();
      setProperty(prop);
   }

   public void finalize()
   {
      if (timerKey != null) {
         this.burstModeTimer.removeTimeoutListener(timerKey);
         timerKey = null;
      }

      if (log.TRACE) log.trace(ME, "finalize - garbage collected");
   }

   public void shutdown()
   {
      log.info(ME, "Entering shutdown(" + super.size() + ")");
      //Thread.currentThread().dumpStack();
      synchronized (this) {
         if (super.size() > 0) {
            log.warn(ME, "Shutting down queue which contains " + super.size() + " messages");
            handleFailure(null);
         }
         isShutdown = true;
      }
      if (log.CALL) log.call(ME, "shutdown() of queue " + this.name);
      if (timerKey != null) {
         this.burstModeTimer.removeTimeoutListener(timerKey);
         timerKey = null;
      }
      // this.log = null;
      // this.cbWorkerPool = null;
      // this.burstModeTimer = null;
      // this.name = null; We need it in finalize()
      // this.glob = null;     We need glob for dead letter recovery
      // this.property = null; We need the props for dead letter recovery
   }

   /**
    * @param msg The messages to handle, if null we take all messages from queue to recover. 
    * @return true failure handled
    */
   private final boolean handleFailure(MsgQueueEntry[] msg)
   {
      if (property != null) {
         if (property.onFailureDeadLetter()) {
            if (msg == null) {
               msg = takeMsgs();
               glob.getRequestBroker().deadLetter(msg);
               checkForVolatileErase(msg);
            }
            else
               glob.getRequestBroker().deadLetter(msg);
            return true;
         }
         else {
            log.error(ME, "PANIC: Only onFailure='deadLetter' is implemented, " + msg.length + " messages are lost.");
            return false;
         }
      }
      else {
         log.error(ME, "PANIC: onFailure='deadLetter' failed, " + msg.length + " messages are lost.");
         return false;
      }
   }

   /**
    * Delete volatile messages if there are in no queue anymore
    */
   public void checkForVolatileErase(MsgQueueEntry[] msgs)
   {
      if (msgs == null || msgs.length < 1)
         return;
      for (int ii=0; ii<msgs.length; ii++) {
         //if (log.TRACE) log.trace(ME, "oid=" + msgs[ii].getMessageUnitWrapper().getUniqueKey() + " EnqueueCounter=" + msgs[ii].getMessageUnitWrapper().getEnqueueCounter() + " isVolatile=" + msgs[ii].getMessageUnitWrapper().getPublishQos().isVolatile());
         if (msgs[ii].getMessageUnitWrapper().getEnqueueCounter() == 0 &&
               msgs[ii].getMessageUnitWrapper().getPublishQos().isVolatile()) {
            if (this instanceof SessionMsgQueue) {
               SessionMsgQueue q = (SessionMsgQueue)this;
               try {
                  glob.getRequestBroker().eraseVolatile(q.getSessionInfo(), msgs[ii].getMessageUnitWrapper().getMessageUnitHandler());
               }
               catch (XmlBlasterException ex) {
                  ex.printStackTrace();
                  log.error(ME, "Erasing of volatile message failed");
               }
            }
         }
      }
   }

   /**
    * Access the settings of this queue. 
    */
   public final CbQueueProperty getProperty()
   {
      return property;
   }

   /**
    * Get the unique name of this queue. 
    * @return The queue name, e.g. "subject:joe", "subject:jack", "session:c0xfrt", "unrelated:XML-RPC:http://www.xy.com:8080"
    */
   public final String getName()
   {
      return this.name;
   }

   /**
    * Called by CbWorker on callback success
    */
   public final void resetErrorCounter()
   {
      this.errorCounter = 0;
   }

   /**
    * Called by CbWorker on callback to determine if message is redelivered
    */
   public final int getErrorCounter()
   {
      return this.errorCounter;
   }

   /**
    * Invoked when callback fails (ping or update)
    */
   public final void connectionLost()
   {
      this.errorCounter++;
      log.warn(ME, "Lost callback connection for " + getName() + " errorCounter=" + this.errorCounter);
   }

   /**
    * We are notified about the burst mode timeout through this method.
    * @param userData You get bounced back your userData which you passed
    *                 with Timeout.addTimeoutListener()
    */
   public final void timeout(Object userData)
   {
      synchronized (this) {
         timerKey = null;
         if (cbWorkerIsActive) {
            //log.info(ME, "Burst mode timeout occurred, last callback worker thread is not finished - we do nothing (the worker thread will give us a kick)");
            return;
         }
         //log.info(ME, "Burst mode timeout occurred, starting callback worker thread ...");
         try {
            cbWorkerPool.execute(this, new CbWorker(glob, this));
         } catch (XmlBlasterException e) {
            log.error(ME, "PANIC: Error occurred, not handled: " + e.toString());
         }
      }
   }

   public final void setCbWorkerIsActive(boolean val)
   {
      cbWorkerIsActive = val;
   }

   public final boolean isShutdown()
   {
      return this.isShutdown;
   }

   /**
    * Allows to overwrite queue property, will be only written if prop!= null. 
    * <br />
    * This is overwritten in the SubjectMsgQueue to allow multiple callbacks
    */
   public void setProperty(CbQueueProperty  prop) throws XmlBlasterException
   {
      if (prop != null) {
         this.property = prop;
         CallbackAddress[] addr = this.property.getCallbackAddresses();
         if (addr.length > 1) {
            log.error(ME, "Ignoring multiple callback address");
         }
         if (addr.length > 0) {
            log.info(ME, "Queue settings: " + this.property.getSettings());
         }
         cbInfo = new CbInfo(glob, addr);
      }
   }

   /**
    * @return The cbInfo instance or null
    */
   public final CbInfo getCbInfo()
   {
      return this.cbInfo;
   }

   /**
    * takes the next message and blocks if none available
    * check with size() if any is available
    * @return null on error
    */
   public final MsgQueueEntry[] takeMsgs()
   {
      if (isShutdown) {
         log.error(ME, "The queue is shutdown, no message access is possible.");
         Thread.currentThread().dumpStack();
         return new MsgQueueEntry[0];
      }
      int size = super.size();
      //if (log.TRACE) log.trace(ME, "Accessing " + size + " messages from queue");
      MsgQueueEntry[] entries = new MsgQueueEntry[size];
      int ii = 0;
      for (int jj=0; jj<size; jj++) {
         try {
            MsgQueueEntry entry = (MsgQueueEntry)super.take();
            entry.getMessageUnitWrapper().addEnqueueCounter(-1);
            if (entry.isExpired() == true) {
               log.warn(ME, "Removed expired message " + entries[ii].getUniqueKey());
            }
            else {
               entries[ii] = entry;
               ii++;
            }
            //try { log.info(ME, "Taking " + new String(entries[ii].getMessageUnit().getContent())); } catch(Exception e) {}
         }
         catch(InterruptedException e) {
            log.error(ME, "Caught unexpected InterruptedException in take(): " + e);
            return null;
         }
      }
      if (ii < size) {
         MsgQueueEntry[] tmp = new MsgQueueEntry[ii];
         for (int kk=0; kk<ii; kk++) {
            tmp[kk] = entries[kk];
         }
         return tmp;
      }

      return entries;
   }

   /**
    * Put a message into the queue, blocks if take thread blocks synchronize
    */
   public final void putMsg(MsgQueueEntry msg) throws XmlBlasterException
   {
      MsgQueueEntry[] arr = new MsgQueueEntry[1];
      arr[0] = msg;
      putMsgs(arr);
   }

   public final Object getMonitor()
   {
      return this;
   }

   /**
    * Put messages into the queue, blocks if take thread blocks synchronize
    */
   public final void putMsgs(MsgQueueEntry[] msg) throws XmlBlasterException
   {
      //if (log.CALL) log.call(ME, "Entering putMsgs(" + msg.length + ")");
      if (msg == null) {
         log.error(ME, "msg==null");
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(ME, "Illegal null argument fir putMsgs()");
      }
      if (isShutdown) {
         log.error(ME, "The queue is shutdown, putMsgs() of " + msg.length + " messages failed, starting error handling ...");
         Thread.currentThread().dumpStack();
         handleFailure(msg);
         return;
      }
      try {
         //synchronized (this) {
            if (msg.length + size() > property.getMaxMsg()) {
               if (property.onOverflowBlock()) {
                  if (this instanceof SessionMsgQueue) {
                     SessionMsgQueue q = (SessionMsgQueue)this;
                     log.warn(ME, "Adding " + msg.length + " messages, queue of client " + q.getSessionInfo().getLoginName() + " will block since max capacity " + property.getMaxMsg() + " reached");
                  }
                  else
                     log.warn(ME, "Adding " + msg.length + " messages, queue will block since max capacity " + property.getMaxMsg() + " reached");
               }
               else if (property.onOverflowDeadLetter()) { // not tested yet!!!
                  glob.getRequestBroker().deadLetter(msg);
                  return;
               }
               else {
                  log.error(ME, "PANIC: onOverflow='" + property.getOnOverflow() + "' is not implemented, messages are lost.");
               }
            }
            for (int ii=0; ii<msg.length; ii++) {
               msg[ii].getMessageUnitWrapper().addEnqueueCounter(1);
               super.put(msg[ii]);
            }
            activateCallbackWorker();
         //}
      }
      catch(InterruptedException e) {
         log.error(ME, "Caught unexpected InterruptedException in take(): " + e);
         throw new XmlBlasterException(ME, "Caught unexpected InterruptedException in put(): " + e);
      }
   }

   /**
    * Give the callback worker thread a kick to deliver the messages. 
    */
   public final void activateCallbackWorker() throws XmlBlasterException
   {
      if (isShutdown) {
         log.error(ME, "The queue is shutdown, can't activate callback worker thread.");
         Thread.currentThread().dumpStack();
         return;
      }
      //if (log.CALL) log.call(ME, "Entering activateCallbackWorker()  cbWorkerIsActive=" + cbWorkerIsActive);
      CallbackAddress addr = property.getCurrentCallbackAddress();
      if (addr != null) {
         // TODO: A SubjectQueue may have many sessions to send the messages, here we use the collectTime of the first!!!!
         // We would need to add a worker for each callback to allow specifying distinct collectTimes
         // Possibly this 'bug' is not important enough to change the code.

         if (this.errorCounter > 0) {
            if (addr.getRetries() != -1 && this.errorCounter > addr.getRetries()) {
               burstModeTimer.removeTimeoutListener(timerKey);
               log.warn(ME, "Giving up after " + addr.getRetries() + " retries to send message back to client, producing now dead letters.");

               handleFailure(null);

               if (this instanceof SessionMsgQueue) {
                  SessionMsgQueue q = (SessionMsgQueue)this;
                  log.warn(ME, "Callback server is lost, killing login session of client " + q.getSessionInfo().getLoginName() + ".");
                  glob.getAuthenticate().disconnect(q.getSessionId(), null);
               }
               else
                  log.error(ME, "Recovery handling is not coded yet");

               shutdown();
               return;
            }
            long delay = addr.getDelay();
            if (delay > 0L) {
               synchronized (this) {
                  if (timerKey == null) {
                     log.info(ME, "Starting error recovery timer with " + delay + " msec, retry #" + this.errorCounter + " of " + addr.getRetries());
                     timerKey = burstModeTimer.addTimeoutListener(this, delay, null);
                  }
               }
               return;
            }
         }

         long collectTime = addr.getCollectTime(); // burst mode if > 0L
         //log.info(ME, "Entering activateCallbackWorker() collectTime=" + collectTime + " cbWorkerIsActive=" + cbWorkerIsActive);
         if (collectTime > 0L) {
            synchronized (this) {
               if (timerKey == null) {
                  //log.info(ME, "Starting burstMode timer with " + collectTime + " msec");
                  timerKey = burstModeTimer.addTimeoutListener(this, collectTime, null);
               }
            }
         }
         else if (cbWorkerIsActive == false) {
            cbWorkerPool.execute(this, new CbWorker(glob, this));
         }
      }
      else {
         if (log.TRACE) log.trace(ME, "No callback address available");
      }
   }

   public final String getCbWorkerPoolStatistic()
   {
      if (cbWorkerPool == null) return "IsShutdown";
      return cbWorkerPool.getStatistic();
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of MsgQueue as a XML ASCII string
    */
   public final String toXml() throws XmlBlasterException
   {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of MsgQueue as a XML ASCII string
    */
   public final String toXml(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer(256);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<MsgQueue name='").append(name).append("'>");
      sb.append(property.toXml(extraOffset+"   "));
      //sb.append(cbWorkerPool.toXml(extraOffset+"   "));
      sb.append(cbInfo.toXml(extraOffset+"   "));
      sb.append(offset).append("   <cbWorkerIsActive>").append(cbWorkerIsActive).append("</cbWorkerIsActive>");
      sb.append(offset).append("</MsgQueue>");

      return sb.toString();
   }
}


/**
   * Sorts the messages
   * <ol>
   *   <li>Priority</li>
   *   <li>Timestamp</li>
   * </ol>
   */
class MsgComparator implements Comparator
{
   /**
      * Comparing the longs directly is 20% faster than having a
      * String compound key
      */
   public final int compare(Object o1, Object o2) {
      MsgQueueEntry d1 = (MsgQueueEntry)o1;
      MsgQueueEntry d2 = (MsgQueueEntry)o2;
      return d1.compare(d2);
   }
}

