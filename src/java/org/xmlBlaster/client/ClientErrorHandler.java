/*------------------------------------------------------------------------------
Name:      ClientErrorHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.error.I_MsgErrorHandler;
import org.xmlBlaster.util.error.I_MsgErrorInfo;

/**
 * The default error recovery implementation for messages which are lost
 * in time and universe.
 * @author xmlBlaster@marcelruff.info
 * @author michele@laghi.eu
 */
public final class ClientErrorHandler implements I_MsgErrorHandler
{
   private final String ME;
   private final Global glob;
   private static Logger log = Logger.getLogger(ClientErrorHandler.class.getName());
   private final I_XmlBlasterAccess xmlBlasterAccess;

   /**
    */
   public ClientErrorHandler(Global glob, I_XmlBlasterAccess xmlBlasterAccess) {
      this.ME = "ClientErrorHandler-" + xmlBlasterAccess.getId();
      this.glob = glob;

      this.xmlBlasterAccess = xmlBlasterAccess;
   }

   /**
    * Handle errors in async mode, we have nobody we can throw an exception to
    * so we handle everything here. 
    */
   public void handleError(I_MsgErrorInfo msgErrorInfo) {
      if (msgErrorInfo == null) return;
      if (log.isLoggable(Level.FINER)) log.finer("Entering handleError for " + msgErrorInfo.getMsgQueueEntries().length + " messages");

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
            log.warning("Default error handling: Message '" + entries[i].getEmbeddedType() + "' '" +
                       entries[i].getLogId() + "' is lost: " + msgErrorInfo.getXmlBlasterException().getMessage() +
                       ". You can add your own client side error handler with I_XmlBlasterAccess.setClientErrorHandler() if desired.");
         }
      }

      if (shutdown) {
         log.severe("Connection failed: " + msgErrorInfo.getXmlBlasterException().getMessage());
         if (msgErrorInfo.getDispatchManager() != null) {
            msgErrorInfo.getDispatchManager().toDead(ConnectionStateEnum.UNDEF, msgErrorInfo.getXmlBlasterException());
            //if (xmlBlasterAccess.getQueue() != null)
            //   xmlBlasterAccess.getQueue().clear();
            msgErrorInfo.getDispatchManager().shutdown();
            return;
         }
      }

      Thread.dumpStack();
      //if (xmlBlasterAccess.getQueue() != null)
      //   xmlBlasterAccess.getQueue().clear();
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

