/*------------------------------------------------------------------------------
Name:      ClientErrorHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.dispatch.DeliveryManager;
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
   private final XmlBlasterAccess xmlBlasterAccess;

   /**
    */
   public ClientErrorHandler(Global glob, XmlBlasterAccess xmlBlasterAccess) {
      this.ME = "ClientErrorHandler-" + xmlBlasterAccess.getId();
      this.glob = glob;
      this.log = glob.getLog("dispatch");
      this.xmlBlasterAccess = xmlBlasterAccess;
   }

   public void handleError(I_MsgErrorInfo msgErrorInfo) {
      if (msgErrorInfo == null) return;
      if (log.CALL) log.call(ME, "Entering handleError for " + msgErrorInfo.getMsgQueueEntries().length + " messages");
      MsgQueueEntry[] entries = msgErrorInfo.getMsgQueueEntries();
      for (int i=0; i<entries.length; i++) {
         log.error(ME, "PANIC: handleError error handling NOT IMPLEMENTED, message '" +
                       entries[i].getLogId() + "' is lost");
      }
   }

   public void shutdown() {
   }
}

