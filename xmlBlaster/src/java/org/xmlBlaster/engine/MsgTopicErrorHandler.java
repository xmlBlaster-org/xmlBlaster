/*------------------------------------------------------------------------------
Name:      MsgTopicErrorHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.jutils.log.LogChannel;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.TopicHandler;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.error.I_MsgErrorHandler;
import org.xmlBlaster.util.error.I_MsgErrorInfo;

import java.util.ArrayList;

/**
 * The default error recovery implementation for messages which are lost
 * in time and universe.
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */
public final class MsgTopicErrorHandler implements I_MsgErrorHandler
{
   private final String ME;
   private final Global glob;
   private final LogChannel log;
   private final TopicHandler topicHandler;

   /**
    */
   public MsgTopicErrorHandler(Global glob, TopicHandler topicHandler) {
      this.ME = "MsgTopicErrorHandler-" + topicHandler.getUniqueKey();
      this.glob = glob;
      this.log = glob.getLog("core");
      this.topicHandler = topicHandler;
   }

   /**
    * The final recovery, all informations necessary are transported in msgErrorInfo
    */
   public void handleError(I_MsgErrorInfo msgErrorInfo) {
      if (msgErrorInfo == null) return;

      XmlBlasterException xmlBlasterException = msgErrorInfo.getXmlBlasterException();
      ErrorCode errorCode = xmlBlasterException.getErrorCode();
      String message = xmlBlasterException.getMessage();
      MsgQueueEntry[] msgQueueEntries = msgErrorInfo.getMsgQueueEntries();

      log.error(ME, "PANIC: MESSAGE ERROR HANDLING IS NOT IMPLELEMENTED , messages are lost: " + msgErrorInfo.toString());
   }

   /**
    * @exception XmlBlasterException is thrown if we are in sync mode and we have no COMMUNICATION problem,
    * the client shall handle it himself
    */
   public void handleErrorSync(I_MsgErrorInfo msgErrorInfo) throws XmlBlasterException {
      log.error(ME, "PANIC: handleErrorSync(): MESSAGE ERROR HANDLING IS NOT IMPLELEMENTED , messages are lost: " + msgErrorInfo.toString());
      throw msgErrorInfo.getXmlBlasterException();
   }

   public void shutdown() {
   }
}

