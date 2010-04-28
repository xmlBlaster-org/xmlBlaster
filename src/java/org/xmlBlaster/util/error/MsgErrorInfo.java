/*------------------------------------------------------------------------------
Name:      MsgErrorInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.error;

import org.xmlBlaster.util.dispatch.DispatchManager;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.Constants;


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
   private static final long serialVersionUID = 1L;
   private final Global glob;
   private final SessionName sessionName;
   private final MsgUnitRaw msgUnitRaw;
   private final MsgUnit msgUnit;
   private final MsgQueueEntry[] msgQueueEntries;
   private final DispatchManager dispatchManager;
   private final XmlBlasterException xmlBlasterException;
   
   /**
    * Called for raw messages e.g. during parsing problems.
    * Creates an error info instance from server side XmlBlasterImpl.publish failure (remote method invokation)
    * without a queue involved
    * @param sessionName The sender client
    * @param msgUnitRaw the message to handle, if it was not possible to parse it
    * @param throwable Creates an error info instance with errorCode="internal.unknown" <br />
    *        if throwable instanceof XmlBlasterException we use the given exception
    */
   public MsgErrorInfo(Global glob, SessionName sessionName, MsgUnitRaw msgUnitRaw, Throwable throwable) {
      this.glob = glob;
	  this.sessionName = sessionName;
	  this.msgUnitRaw = msgUnitRaw;
	  this.msgUnit = null;
      this.msgQueueEntries = new MsgQueueEntry[0];
      this.dispatchManager = null;
	  this.xmlBlasterException = (throwable instanceof XmlBlasterException) ? (XmlBlasterException)throwable :
             new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, 
                "MsgErrorInfo.INTERNAL", (throwable==null) ? "NO INFO" : throwable.toString());
   }

   /**
    * Creates an error info instance from server side XmlBlasterImpl.publish failure (remote method invokation)
    * without a queue involved 
    * @param sessionName The sender client
    * @param msgUnit the message to handle
    * @param throwable Creates an error info instance with errorCode="internal.unknown" <br />
    *        if throwable instanceof XmlBlasterException we use the given exception
    */
   public MsgErrorInfo(Global glob, SessionName sessionName, MsgUnit msgUnit, Throwable throwable) {
      this.glob = glob;
	  this.sessionName = sessionName;
	  this.msgUnitRaw = null;
	  this.msgUnit = msgUnit;
      this.msgQueueEntries = new MsgQueueEntry[0];
      this.dispatchManager = null;
      if (throwable != null) {
    	  this.xmlBlasterException = (throwable instanceof XmlBlasterException) ? (XmlBlasterException)throwable :
             new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, 
                "MsgErrorInfo.INTERNAL", "", throwable);
      }
      else {
          this.xmlBlasterException = new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, 
                  "MsgErrorInfo.INTERNAL", "NO INFO");  
      }
   }

   /**
    * Creates an error info instance with errorCode="internal.unknown"
    * @param dispatchManager The dispatchManager/queue where the entry is inside, null if the entry is not in a queue
    * @param throwable Creates an error info instance with errorCode="internal.unknown" <br />
    *        if throwable instanceof XmlBlasterException we use the given exception
    */
   public MsgErrorInfo(Global glob, MsgQueueEntry msgQueueEntry, DispatchManager dispatchManager, Throwable throwable) {
      this(glob, (msgQueueEntry == null) ? new MsgQueueEntry[0] : new MsgQueueEntry[]{ msgQueueEntry },
                                                                      dispatchManager, throwable);
   }

   /**
    * Creates an error info instance for an array of message queue entries
    * @param dispatchManager The dispatchManager/queue where the entry is inside, null if the entry is not in a queue
    * @param throwable Creates an error info instance with errorCode="internal.unknown" <br />
    *        if throwable instanceof XmlBlasterException we use the given exception
    */
   public MsgErrorInfo(Global glob, MsgQueueEntry[] msgQueueEntries, DispatchManager dispatchManager, Throwable throwable) {
      if (throwable == null) {
         Thread.dumpStack();
         throw new IllegalArgumentException("MsgErrorInfo: xmlBlasterException may not be null");
      }
      this.glob = glob;
	  this.sessionName = (dispatchManager == null) ? null : dispatchManager.getSessionName();
	  this.msgUnitRaw = null;
	  this.msgUnit = null;
      this.msgQueueEntries = (msgQueueEntries == null) ? (new MsgQueueEntry[0]) : msgQueueEntries;
      this.dispatchManager = dispatchManager;
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
      return (this.dispatchManager != null) ? this.dispatchManager.getQueue() : null;
   }

   public DispatchManager getDispatchManager() {
      return this.dispatchManager;
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

   public Global getGlob() {
      return glob;
   }

   public SessionName getSessionName() {
     return sessionName;
   }

   public MsgUnit getMsgUnit() {
      return msgUnit;
   }

   public MsgUnitRaw getMsgUnitRaw() {
	  return msgUnitRaw;
   }
}

