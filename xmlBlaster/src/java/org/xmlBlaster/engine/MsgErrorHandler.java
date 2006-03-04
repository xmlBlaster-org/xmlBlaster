/*------------------------------------------------------------------------------
Name:      MsgErrorHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.dispatch.DispatchManager;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.error.I_MsgErrorHandler;
import org.xmlBlaster.util.error.I_MsgErrorInfo;
import org.xmlBlaster.authentication.SessionInfo;
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
   private final long MAX_BYTES = 1000000L; // to avoid out of mem, max 1 MB during error handling
   
   private final Global glob;
   private static Logger log = Logger.getLogger(MsgErrorHandler.class.getName());
   private /*final -> shutdown*/ SessionInfo sessionInfo;

   /**
    * @param sessionInfo Can be null (e.g. for Subject errors)
    */
   public MsgErrorHandler(Global glob, SessionInfo sessionInfo) {
      this.ME = "MsgErrorHandler-" + ((sessionInfo==null) ? "" : sessionInfo.getId());
      this.glob = glob;

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
      DispatchManager dispatchManager = (this.sessionInfo == null) ? null : this.sessionInfo.getDispatchManager();
      I_Queue msgQueue = msgErrorInfo.getQueue();  // is null if entry is not yet in queue

      if (log.isLoggable(Level.FINER)) log.finer("Error handling started: " + msgErrorInfo.toString());

      if (msgQueueEntries != null && msgQueueEntries.length > 0) {
         // 1. Generate dead letters from passed messages
         glob.getRequestBroker().deadMessage(msgQueueEntries, msgQueue, message);

         if (msgQueue != null) {
            // Remove the above published dead message from the queue
            try {
               if (log.isLoggable(Level.FINE)) log.fine("Removing " + msgQueueEntries.length + " dead messages from queue");
               long removed = 0L;
               boolean tmp[] = msgQueue.removeRandom(msgQueueEntries);
               for (int i=0; i < tmp.length; i++) if (tmp[i]) removed++;
               if (removed != msgQueueEntries.length) {
                  log.warning("Expected to remove " + msgQueueEntries.length + " messages from queue but where only " + removed + ": " + message);
               }
            }
            catch (XmlBlasterException e) {
               log.warning("Can't remove " + msgQueueEntries.length + " messages from queue: " + e.getMessage() + ". Original cause was: " + message);
            }
         }
      }

      if (xmlBlasterException.isUser()) {
         // The update() method from the client has thrown a ErrorCode.USER* error
         if (log.isLoggable(Level.FINE)) log.fine("Error handlig for exception " + errorCode.toString() + " done");
         return;
      }

      // 2. Generate dead letters if there are some entries in the queue
      long size = (msgQueue == null) ? 0 : msgQueue.getNumOfEntries();
      if (log.isLoggable(Level.FINE)) log.fine("Flushing " + size + " remaining message from queue");
      if (size > 0) {
         try {
            QueuePropertyBase queueProperty = (QueuePropertyBase)msgQueue.getProperties();
            if (queueProperty == null || queueProperty.onFailureDeadMessage()) {
               while (msgQueue.getNumOfEntries() > 0L) {
                  ArrayList list = msgQueue.peek(-1, MAX_BYTES);
                  MsgQueueEntry[] msgArr = (MsgQueueEntry[])list.toArray(new MsgQueueEntry[list.size()]);
                  if (msgArr.length > 0) {
                     glob.getRequestBroker().deadMessage(msgArr, (I_Queue)null, message);
                  }
                  if (msgArr.length > 0) {
                     msgQueue.removeRandom(msgArr);
                  }
               }
            }
            else {
               log.severe("PANIC: Only onFailure='" + Constants.ONOVERFLOW_DEADMESSAGE +
                     "' is implemented, " + msgQueue.getNumOfEntries() + " messages are lost: " + message);
            }
         }
         catch(Throwable e) {
            e.printStackTrace();
            log.severe("PANIC: givingUpDelivery failed, " + size +
                           " messages are lost: " + message + ": " + e.toString());
         }
      }

      // We do a auto logout if the callback is down
      if (dispatchManager == null || dispatchManager.isDead()) {
         if (log.isLoggable(Level.FINE)) log.fine("Doing error handling for dead connection state ...");

         if (dispatchManager!=null) dispatchManager.shutdown();

         // 3. Kill login session
         if (this.sessionInfo != null && // if callback has been configured (async) 
             sessionInfo.getConnectQos().getSessionCbQueueProperty().getCallbackAddresses().length > 0) {
            
            try {
               //if (address == null || address.getOnExhaustKillSession()) {
                  log.warning("Callback server is lost, killing login session of client " +
                                ((msgQueue == null) ? "unknown" : msgQueue.getStorageId().toString()) +
                                ": " + message);
                  try {
                     DisconnectQos disconnectQos = new DisconnectQos(glob);
                     disconnectQos.deleteSubjectQueue(false);
                     glob.getAuthenticate().disconnect(this.sessionInfo.getAddressServer(), 
                                         this.sessionInfo.getSecretSessionId(), disconnectQos.toXml());
                  }
                  catch (Throwable e) {
                     log.severe("PANIC: givingUpDelivery error handling failed, " +
                                    size + " messages are lost: " + message + ": " + e.toString());
                  }
               //}
               //else {
               //   log.error(ME, "PANIC: givingUpDelivery error handling failed, '" + address.getOnExhaust() +
               //       "' is not supported, " + size + " messages are lost: " + message);
               //}
            }
            catch(Throwable e) {
               log.severe("PANIC: givingUpDelivery error handling failed, " + size +
                              " messages are lost: " + message + ": " + e.toString());
            }
         }
      }
   }

   /**
    * This should never happen on server side, so we just call handleError(I_MsgErrorInfo). 
    * @exception XmlBlasterException is thrown if we are in sync mode and we have no COMMUNICATION problem,
    * the client shall handle it himself
    */
   public void handleErrorSync(I_MsgErrorInfo msgErrorInfo) throws XmlBlasterException {
      if (log.isLoggable(Level.FINE)) log.fine("Unexpected sync error handling invocation, we try our best");
      //Thread.currentThread().dumpStack();
      handleError(msgErrorInfo);
   }

   public void shutdown() {
      //this.sessionInfo = null;
   }
}

