/*------------------------------------------------------------------------------
Name:      MsgErrorHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.jutils.log.LogChannel;
import org.xmlBlaster.engine.Global;
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
      this.log = glob.getLog("dispatch");
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
      I_Queue msgQueue = (deliveryManager == null) ? null: deliveryManager.getQueue();

      if (log.CALL) log.call(ME, "Error handling started: " + msgErrorInfo.toString());

      // 1. Generate dead letters from passed messages
      glob.getRequestBroker().deadMessage(msgQueueEntries, msgQueue, message);

      // Remove the above published dead message from the queue
      try {
         if (log.TRACE) log.trace(ME, "Removing " + msgQueueEntries.length + " dead messages from queue");
         long removed = msgQueue.removeRandom(msgQueueEntries);
         if (removed != msgQueueEntries.length) {
            log.warn(ME, "Expected to remove " + msgQueueEntries.length + " messages from queue but where only " + removed + ", exception comes from mime access plugin: " + message);
            return;  // Seems to come from mime access plugin as the message where not in the queue
         }
      }
      catch (XmlBlasterException e) {
         log.warn(ME, "Can't remove " + msgQueueEntries.length + " messages from queue: " + e.getMessage() + ". Original cause was: " + message);
      }

      if (xmlBlasterException.isUser()) {
         // The update() method from the client has thrown a ErrorCode.USER* error
         if (log.TRACE) log.trace(ME, "Error handlig for exception " + errorCode.toString() + " done");
         return;
      }

      // 2. Generate dead letters if there are some in the queue
      long size = msgQueue.getNumOfEntries();
      if (log.TRACE) log.trace(ME, "Flushing " + size + " remaining message from queue");
      if (size > 0) {
         try {
            QueuePropertyBase queueProperty = (QueuePropertyBase)msgQueue.getProperties();
            if (queueProperty == null || queueProperty.onFailureDeadMessage()) {
               ArrayList list = msgQueue.take(-1, -1L); // Is not crash save peek() and after deadMessage remove() would be save, change this?
               MsgQueueEntry[] msgArr = (MsgQueueEntry[])list.toArray(new MsgQueueEntry[list.size()]);
               glob.getRequestBroker().deadMessage(msgArr, (I_Queue)null, message);
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
         try {
            //if (address == null || address.getOnExhaustKillSession()) {
               log.warn(ME, "Callback server is lost, killing login session of client " +
                              msgQueue.getStorageId() + ": " + message);
               try {
                  glob.getAuthenticate().disconnect(sessionInfo.getSessionId(), null);
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

   public void shutdown() {
      this.sessionInfo = null;
   }
}

