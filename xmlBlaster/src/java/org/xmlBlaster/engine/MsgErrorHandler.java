/*------------------------------------------------------------------------------
Name:      MsgErrorHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.jutils.log.LogChannel;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.queuemsg.ReferenceEntry;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.dispatch.DeliveryManager;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.error.I_MsgErrorHandler;
import org.xmlBlaster.util.error.I_MsgErrorInfo;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.authentication.SubjectInfo;
import org.xmlBlaster.client.qos.DisconnectQos;

import java.util.ArrayList;

/**
 * The default error recovery implementation for messages which are lost
 * in time and universe.
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */
public final class MsgErrorHandler implements I_MsgErrorHandler
{
   private final String ME;
   private final Global glob;
   private final LogChannel log;
   private /*final -> shutdown*/ SessionInfo sessionInfo;

   /**
    */
   public MsgErrorHandler(Global glob, SessionInfo sessionInfo) {
      this.ME = "MsgErrorHandler-" + sessionInfo.getId();
      this.glob = glob;
      this.log = glob.getLog("core");
      this.sessionInfo = sessionInfo;
   }

   /**
    * The final recovery, all informations necessary are transported in msgErrorInfo
    * We expect three error types:
    * <table border="1">
    *   <tr><th>ErrorCode</th><th>Action</th></tr>
    *   <tr>
    *     <td>ErrorCode.USER*</td>
    *     <td> User errors are thrown from remote clients update() method,
    *       we handle only the bounced back messages.
    *       We take care to remove them from the queue</td>
    *   </tr>
    *   <tr>
    *     <td>ErrorCode.COMMUNICATION*</td>
    *     <td>The connection went to state==DEAD, we recover all
    *       messages from the queue</td>
    *   </tr>
    *   <tr>
    *     <td>Other exceptions from mime access plugin</td>
    *     <td>Those messages have not entered the queue yet, they have no impact on
    *         the dispatcher framework (no queue flushing etc.)</td>
    *  </tr>
    *   <tr>
    *     <td>Other exceptions from dispatcher framework</td>
    *     <td>They are handled as internal problems,
    *       we do the same as with COMMUNICATION* exception</td>
    *  </tr>
    * </table>
    */
   public void handleError(I_MsgErrorInfo msgErrorInfo) {
      if (msgErrorInfo == null) return;

      XmlBlasterException xmlBlasterException = msgErrorInfo.getXmlBlasterException();
      ErrorCode errorCode = xmlBlasterException.getErrorCode();
      String message = xmlBlasterException.getMessage();
      MsgQueueEntry[] msgQueueEntries = msgErrorInfo.getMsgQueueEntries();
      DeliveryManager deliveryManager = sessionInfo.getDeliveryManager();
      I_Queue msgQueue = msgErrorInfo.getQueue();  // is null if entry is not yet in queue

      if (log.CALL) log.call(ME, "Error handling started: " + msgErrorInfo.toString());

      // Try to safe some of the PtP messages
      try {
         msgQueueEntries = putPtPBackToSubjectQueue(this.sessionInfo, msgQueueEntries);
      }
      catch (XmlBlasterException e) {
         log.error(ME, "handleError() problems: " + e.getMessage());
      }

      if (msgQueueEntries != null && msgQueueEntries.length > 0) {
         // 1. Generate dead letters from passed messages
         glob.getRequestBroker().deadMessage(msgQueueEntries, msgQueue, message);

         if (msgQueue != null) {
            // Remove the above published dead message from the queue
            try {
               if (log.TRACE) log.trace(ME, "Removing " + msgQueueEntries.length + " dead messages from queue");
               long removed = 0L;
               boolean tmp[] = msgQueue.removeRandom(msgQueueEntries);
               for (int i=0; i < tmp.length; i++) if (tmp[i]) removed++;
               if (removed != msgQueueEntries.length) {
                  log.warn(ME, "Expected to remove " + msgQueueEntries.length + " messages from queue but where only " + removed + ": " + message);
               }
            }
            catch (XmlBlasterException e) {
               log.warn(ME, "Can't remove " + msgQueueEntries.length + " messages from queue: " + e.getMessage() + ". Original cause was: " + message);
            }
         }
      }

      if (xmlBlasterException.isUser()) {
         // The update() method from the client has thrown a ErrorCode.USER* error
         if (log.TRACE) log.trace(ME, "Error handlig for exception " + errorCode.toString() + " done");
         return;
      }

      // 2. Generate dead letters if there are some in the queue
      long size = (msgQueue == null) ? 0 : msgQueue.getNumOfEntries();
      if (log.TRACE) log.trace(ME, "Flushing " + size + " remaining message from queue");
      if (size > 0) {
         try {
            QueuePropertyBase queueProperty = (QueuePropertyBase)msgQueue.getProperties();
            if (queueProperty == null || queueProperty.onFailureDeadMessage()) {
               // TODO: loop with small amounts to avoid OutOfMemory !
               ArrayList list = msgQueue.peek(-1, -1L);
               MsgQueueEntry[] msgArrAll = (MsgQueueEntry[])list.toArray(new MsgQueueEntry[list.size()]);
               MsgQueueEntry[] msgArr = putPtPBackToSubjectQueue(this.sessionInfo, msgArrAll);
               if (msgArr.length > 0) {
                  glob.getRequestBroker().deadMessage(msgArr, (I_Queue)null, message);
               }
               if (msgArrAll.length > 0) {
                  msgQueue.removeRandom(msgArrAll);
               }
            }
            else {
               log.error(ME, "PANIC: Only onFailure='" + Constants.ONOVERFLOW_DEADMESSAGE +
                     "' is implemented, " + msgQueue.getNumOfEntries() + " messages are lost: " + message);
            }
         }
         catch(Throwable e) {
            e.printStackTrace();
            log.error(ME, "PANIC: givingUpDelivery failed, " + size +
                           " messages are lost: " + message + ": " + e.toString());
         }
      }

      if (deliveryManager == null || deliveryManager.isDead()) {
         if (log.TRACE) log.trace(ME, "Doing error handling for dead connection state ...");

         if (deliveryManager!=null) deliveryManager.shutdown();

         // 3. Kill login session
         if (this.sessionInfo != null) {
            try {
               //if (address == null || address.getOnExhaustKillSession()) {
                  log.warn(ME, "Callback server is lost, killing login session of client " +
                                ((msgQueue == null) ? "unknown" : msgQueue.getStorageId().toString()) +
                                ": " + message);
                  try {
                     DisconnectQos disconnectQos = new DisconnectQos(glob);
                     disconnectQos.deleteSubjectQueue(false);
                     glob.getAuthenticate().disconnect(sessionInfo.getSecretSessionId(), disconnectQos.toXml());
                  }
                  catch (Throwable e) {
                     log.error(ME, "PANIC: givingUpDelivery error handling failed, " +
                                    size + " messages are lost: " + message + ": " + e.toString());
                  }
               //}
               //else {
               //   log.error(ME, "PANIC: givingUpDelivery error handling failed, '" + address.getOnExhaust() +
               //       "' is not supported, " + size + " messages are lost: " + message);
               //}
            }
            catch(Throwable e) {
               log.error(ME, "PANIC: givingUpDelivery error handling failed, " + size +
                              " messages are lost: " + message + ": " + e.toString());
            }
         }
      }
   }

   /**
    * All PtP messages which were sent to a destination subject (without a pubSessionId)
    * are put back to the subject queue to be delivered later with another login session
    * of this user.
    * @return The remaining entries to be error handled
    */
   public MsgQueueEntry[] putPtPBackToSubjectQueue(SessionInfo sessionInfo, MsgQueueEntry[] entries) 
         throws XmlBlasterException {
      if (entries == null || entries.length < 1) return entries;
      if (log.CALL) log.call(ME, "Entering putPtPBackToSubjectQueue() for " + entries.length + " entries");
      if (sessionInfo == null) {
         return entries;
      }
      SubjectInfo subjectInfo = sessionInfo.getSubjectInfo();
      if (subjectInfo == null) {
         return entries;
      }
      I_Queue subjectQueue = subjectInfo.getSubjectQueue();
      if (subjectQueue == null) {
         return entries;
      }

      try {
         ArrayList list = new ArrayList(entries.length);
         for(int ii=0; ii<entries.length; ii++) {
            ReferenceEntry en = (ReferenceEntry)entries[ii];
            if (en.getMsgQosData().isPtp() && !en.getReceiver().isSession() &&
                en.getMsgUnitWrapper().getReferenceCounter() <= 2) {
               // The getReferenceCounter() check is buggy (Marcel 2003.03.20):
               // 1. It includes a history entry and this entry but the history is optional
               // 2. We may send the same message twice with another session if such a callback references the message
               //    and we stuff the message back to the subject queue
               // -> We need to specify a PtP load balancer plugin framework and than resolve this issue
               log.info(ME, "We are the last session taking care on PtP message '" + en.getLogId() + "', putting it back to subject queue");
               try {
                  subjectQueue.put(en, false);
                  continue;
               }
               catch (XmlBlasterException e) {
                  log.error(ME, "Failed to put entry '" + en.getLogId() + "' into subject queue, forwarding it to error handling manager: " + e.getMessage());
               }
            }
            list.add(entries[ii]);
         }
         return (MsgQueueEntry[])list.toArray(new MsgQueueEntry[list.size()]);
      }
      catch (XmlBlasterException e) {
         log.warn(ME, "Couldn't stuff " + entries.length + " messages back to subject queue of " + sessionInfo.getId() + ": " + e.getMessage() +
                       ((sessionInfo.getDeliveryManager() != null) ? sessionInfo.getDeliveryManager().toXml("") : ""));
         return entries;
      }
      catch (Throwable e) {
         log.warn(ME, "Couldn't stuff " + entries.length + " messages back to subject queue of " + sessionInfo.getId() + ": " + e.toString() +
                 ((sessionInfo.getDeliveryManager() != null) ? sessionInfo.getDeliveryManager().toXml("") : ""));
         return entries;
      }
   }

   /**
    * This should never happen on server side, so we just call handleError(I_MsgErrorInfo). 
    * @exception XmlBlasterException is thrown if we are in sync mode and we have no COMMUNICATION problem,
    * the client shall handle it himself
    */
   public void handleErrorSync(I_MsgErrorInfo msgErrorInfo) throws XmlBlasterException {
      log.error(ME, "Unexpected error handling invocation, we try our best");
      Thread.currentThread().dumpStack();
      handleError(msgErrorInfo);
   }

   public void shutdown() {
      //this.sessionInfo = null;
   }
}

