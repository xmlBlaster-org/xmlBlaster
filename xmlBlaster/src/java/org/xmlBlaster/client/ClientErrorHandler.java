/*------------------------------------------------------------------------------
Name:      ClientErrorHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.dispatch.DeliveryManager;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.error.I_MsgErrorHandler;
import org.xmlBlaster.util.error.I_MsgErrorInfo;
import org.xmlBlaster.client.XmlBlasterAccess;

import java.util.ArrayList;

/**
 * The default error recovery implementation for messages which are lost
 * in time and universe.
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */
public final class ClientErrorHandler implements I_MsgErrorHandler
{
   private final String ME;
   private final Global glob;
   private final LogChannel log;
   private final I_XmlBlasterAccess xmlBlasterAccess;

   /**
    */
   public ClientErrorHandler(Global glob, I_XmlBlasterAccess xmlBlasterAccess) {
      this.ME = "ClientErrorHandler-" + xmlBlasterAccess.getId();
      this.glob = glob;
      this.log = glob.getLog("dispatch");
      this.xmlBlasterAccess = xmlBlasterAccess;
   }

   /**
    * Handle errors in async mode, we have nobody we can throw an exception to
    * so we handle everything here. 
    */
   public void handleError(I_MsgErrorInfo msgErrorInfo) {
      if (msgErrorInfo == null) return;
      if (log.CALL) log.call(ME, "Entering handleError for " + msgErrorInfo.getMsgQueueEntries().length + " messages");

      boolean shutdown = false;
      XmlBlasterException ex = msgErrorInfo.getXmlBlasterException();
      if (ex.isUser()) {
         shutdown = true;
      }

      MsgQueueEntry[] entries = msgErrorInfo.getMsgQueueEntries();
      for (int i=0; i<entries.length; i++) {
         if (entries[i].getMethodName() == MethodName.CONNECT) {
            shutdown = true;
         }
         else {
            log.warn(ME, "Default error handling: Message '" + entries[i].getEmbeddedType() + "' '" +
                       entries[i].getLogId() + "' is lost: " + msgErrorInfo.getXmlBlasterException().getMessage() +
                       ". You can add your own client side error handler with I_XmlBlasterAccess.setClientErrorHandler() if desired.");
         }
      }

      if (shutdown) {
         log.error(ME, "Connection failed: " + msgErrorInfo.getXmlBlasterException().getMessage());
         if (msgErrorInfo.getDeliveryManager() != null) {
            msgErrorInfo.getDeliveryManager().toDead(ConnectionStateEnum.UNDEF, msgErrorInfo.getXmlBlasterException());
            if (xmlBlasterAccess.getQueue() != null)
               xmlBlasterAccess.getQueue().clear();
            msgErrorInfo.getDeliveryManager().shutdown();
            return;
         }
      }

      Thread.currentThread().dumpStack();
      if (xmlBlasterAccess.getQueue() != null)
         xmlBlasterAccess.getQueue().clear();
   }

   /**
    * @exception XmlBlasterException is thrown if we are in sync mode and we have no COMMUNICATION problem,
    * the client shall handle it himself
    */
   public void handleErrorSync(I_MsgErrorInfo msgErrorInfo) throws XmlBlasterException {
      if (msgErrorInfo.getXmlBlasterException().isCommunication()) {
         handleError(msgErrorInfo);
         return;
      }
      throw msgErrorInfo.getXmlBlasterException(); // Throw back to client
   }

   public void shutdown() {
   }
}

