/*------------------------------------------------------------------------------
Name:      MsgErrorInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.error;

import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.enum.Constants;


/**
 * Encapsulates all necessary information to allow error handling
 * of a lost message. 
 * <p>
 * Instances of this class are immutable.
 * </p>
 * @author xmlBlaster@marcelruff.info
 */
public final class MsgErrorInfo implements I_MsgErrorInfo, java.io.Serializable
{
   private final MsgQueueEntry[] msgQueueEntries;
   private final I_Queue queue;
   private final XmlBlasterException xmlBlasterException;

   /**
    * Creates an error info instance with errorCode="internal.unknown"
    * @param queue The queue where the entry is inside, null if the entry is not in a queue
    * @param throwable Creates an error info instance with errorCode="internal.unknown" <br />
    *        if throwable instanceof XmlBlasterException we use the given exception
    */
   public MsgErrorInfo(Global glob, MsgQueueEntry msgQueueEntry, I_Queue queue, Throwable throwable) {
      this(glob, (msgQueueEntry == null) ? new MsgQueueEntry[0] : new MsgQueueEntry[]{ msgQueueEntry },
                                                                      queue, throwable);
   }

   /**
    * Creates an error info instance for an array of message queue entries
    * @param queue The queue where the entry is inside, null if the entry is not in a queue
    * @param throwable Creates an error info instance with errorCode="internal.unknown" <br />
    *        if throwable instanceof XmlBlasterException we use the given exception
    */
   public MsgErrorInfo(Global glob, MsgQueueEntry[] msgQueueEntries, I_Queue queue, Throwable throwable) {
      if (throwable == null) {
         Thread.currentThread().dumpStack();
         throw new IllegalArgumentException("MsgErrorInfo: xmlBlasterException may not be null");
      }
      this.msgQueueEntries = (msgQueueEntries == null) ? (new MsgQueueEntry[0]) : msgQueueEntries;
      this.queue = queue;
      this.xmlBlasterException = (throwable instanceof XmlBlasterException) ? (XmlBlasterException)throwable :
                         new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, 
                            "MsgErrorInfo.INTERNAL", (throwable==null) ? "NO INFO" : throwable.toString());
   }

   /**
    * Access the message entry object to be handled
    * @return never null
    */
   public MsgQueueEntry[] getMsgQueueEntries() {
      return this.msgQueueEntries;
   }

   /**
    * Access the queue where the entries are inside. 
    * @return null if entries are not in a queue
    */
   public I_Queue getQueue() {
      return this.queue;
   }

   /**
    * Access the exception object describing the problem
    * @return never null
    */
   public XmlBlasterException getXmlBlasterException() {
      return this.xmlBlasterException;
   }

   public String toString() {
      if (msgQueueEntries.length != 1)
         return "Problems with " + msgQueueEntries.length + " MsgQueueEntries " + " - " + this.xmlBlasterException.getMessage();
      else
         return this.msgQueueEntries[0].getLogId() + " - " + this.xmlBlasterException.getMessage();
   }

   /**
    * Dump state of this object into XML.
    * <br>
    * @return XML state of MsgErrorInfo
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of MsgErrorInfo
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(1000);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<MsgErrorInfo>");
      for(int i=0; i<msgQueueEntries.length; i++)
         sb.append(msgQueueEntries[i].toXml(extraOffset+Constants.INDENT));
      sb.append(xmlBlasterException.toXml(extraOffset+Constants.INDENT));
      sb.append(offset).append("</MsgErrorInfo>");
      return sb.toString();
   }
}

