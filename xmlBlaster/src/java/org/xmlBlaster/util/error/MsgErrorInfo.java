/*------------------------------------------------------------------------------
Name:      MsgErrorInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.error;

import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;


/**
 * Encapsulates all necessary information to allow error handling
 * of a lost message. 
 * <p>
 * Instances of this class are immutable.
 * </p>
 * @author ruff@swand.lake.de
 */
public final class MsgErrorInfo implements I_MsgErrorInfo, java.io.Serializable
{
   private final MsgQueueEntry[] msgQueueEntries;
   private final XmlBlasterException xmlBlasterException;

   /**
    * Creates an error info instance with errorCode="internal.unknown"
    * @param throwable Creates an error info instance with errorCode="internal.unknown" <br />
    *        if throwable instanceof XmlBlasterException we use the given exception
    */
   public MsgErrorInfo(Global glob, MsgQueueEntry msgQueueEntry, Throwable throwable) {
      this(glob, (msgQueueEntry == null) ? new MsgQueueEntry[0] : new MsgQueueEntry[]{ msgQueueEntry }, throwable);
   }

   /**
    * Creates an error info instance for an array of message queue entries
    * @param throwable Creates an error info instance with errorCode="internal.unknown" <br />
    *        if throwable instanceof XmlBlasterException we use the given exception
    */
   public MsgErrorInfo(Global glob, MsgQueueEntry[] msgQueueEntries, Throwable throwable) {
      if (throwable == null) {
         Thread.currentThread().dumpStack();
         throw new IllegalArgumentException("MsgErrorInfo: xmlBlasterException may not be null");
      }
      this.msgQueueEntries = (msgQueueEntries == null) ? (new MsgQueueEntry[0]) : msgQueueEntries;
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
}

