/*------------------------------------------------------------------------------
Name:      MsgQueueEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Wrapping the CORBA MessageUnit to allow some nicer usage
Version:   $Id: MsgQueueEntry.java,v 1.4 2002/04/23 15:07:30 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.queue;

import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.RequestBroker;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.xml2java.PublishQos;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.persistence.I_PersistenceDriver;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.engine.SubscriptionInfo;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.authentication.SubjectInfo;
import java.util.*;


/**
 * Wrapping the MessageUnit and adding some attributes, used as a queue entry
 * for MsgQueue
 * <p />
 * This allows queueing history of messages with same oid.
 */
public class MsgQueueEntry
{
   private final static String ME = "MsgQueueEntry";

   private final Global glob;

   /** The SubscriptionInfo if a Pub/Sub message, is null for PtP messages */
   private final SubscriptionInfo subscriptionInfo;

   /** The security context of the receiver, is needed to export the message */
   private SessionInfo receiverSessionInfo;
   /** The subjectInfo of the receiver for PtP messages */
   private final SubjectInfo receiverSubjectInfo;
   private boolean isSubjectQueue = false;

   /** The MessageUnitWrapper containing the MessageUnit and the parsed PublishQos */
   private final MessageUnitWrapper msgUnitWrapper;

   /** The MessageUnit with key/content/qos (raw struct) */
   private MessageUnit msgUnit;

   /** The temporary publish qos of the current message, may differ from the PublishQos of the first message with same oid */
   private PublishQos publishQosCurrent;

   /**
    * Use this constructor if a new PtP message object is fed by method publish() and
    * the sessionInfo is not yet known (possibly user is not logged in).
    * <p />
    * @param receiverSubjectInfo Destinationsubject
    * @param msgUnitWrapper contains the parsed message
    */
   public MsgQueueEntry(Global glob, SubjectInfo receiverSubjectInfo, MessageUnitWrapper msgUnitWrapper) throws XmlBlasterException {
      if (receiverSubjectInfo == null || msgUnitWrapper == null) {
         Log.error(ME, "Invalid constructor parameter");
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(ME, "Invalid constructor parameter");
      }

      this.glob = glob;
      this.isSubjectQueue = true;
      this.subscriptionInfo = null;
      this.receiverSubjectInfo = receiverSubjectInfo;
      this.receiverSessionInfo = null;
      this.msgUnitWrapper = msgUnitWrapper;
      initialize();

      if (Log.TRACE) Log.trace(ME, "Creating new MsgQueueEntry for published message, key oid=" + getUniqueKey());
   }

   /**
    * Use this constructor if a new PtP message object is fed by method publish().
    * <p />
    * @param receiverSessionInfo Session of the destination, to export the message
    * @param msgUnitWrapper contains the parsed message
    */
   public MsgQueueEntry(Global glob, SessionInfo receiverSessionInfo, MessageUnitWrapper msgUnitWrapper) throws XmlBlasterException {
      if (receiverSessionInfo == null || msgUnitWrapper == null) {
         Log.error(ME, "Invalid constructor parameter");
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(ME, "Invalid constructor parameter");
      }
      
      this.glob = glob;
      this.subscriptionInfo = null;
      this.receiverSessionInfo = receiverSessionInfo;
      this.receiverSubjectInfo = receiverSessionInfo.getSubjectInfo();
      this.msgUnitWrapper = msgUnitWrapper;
      initialize();

      if (Log.TRACE) Log.trace(ME, "Creating new MsgQueueEntry for published message, key oid=" + getUniqueKey());
   }

   /**
    * Use this constructor if a new Pub/Sub message object is fed by method publish().
    * <p />
    * @param subscriptionInfo Of the subscriber, to export the message
    * @param msgUnitWrapper contains the parsed message
    */
   public MsgQueueEntry(Global glob, SubscriptionInfo subscriptionInfo, MessageUnitWrapper msgUnitWrapper) throws XmlBlasterException {
      if (subscriptionInfo == null || msgUnitWrapper == null) {
         Log.error(ME, "Invalid constructor parameter with subscriptionInfo");
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(ME, "Invalid constructor parameter");
      }
      
      this.glob = glob;
      this.subscriptionInfo = subscriptionInfo;
      this.receiverSessionInfo = subscriptionInfo.getSessionInfo();
      this.receiverSubjectInfo = this.receiverSessionInfo.getSubjectInfo();
      this.msgUnitWrapper = msgUnitWrapper;
      initialize();

      if (Log.TRACE) Log.trace(ME, "Creating new MsgQueueEntry for published message, key oid=" + getUniqueKey());
   }

   private final void initialize() throws XmlBlasterException
   {
      this.msgUnit = msgUnitWrapper.getMessageUnit();
      this.publishQosCurrent = msgUnitWrapper.getPublishQos();
   }

   public void finalize()
   {
      if (Log.TRACE) Log.trace(ME, "finalize - garbage collect");
   }

   public final boolean isExpired()
   {
      return this.msgUnitWrapper.isExpired();
   }

   public final String getUniqueKey()
   {
      return msgUnitWrapper.getUniqueKey();
   }

   /**
    * The sessionInfo object of the receiver
    */
   public final SessionInfo getSessionInfo()
   {
      if (this.receiverSessionInfo != null)
         return this.receiverSessionInfo;
      
      if (this.receiverSessionInfo == null && this.subscriptionInfo == null && this.receiverSubjectInfo == null) {
         Log.error(ME, "Internal error: Nobody has set the receiverSessionInfo");
         Thread.currentThread().dumpStack();
      }
      else if (this.subscriptionInfo != null) {
         this.receiverSessionInfo = this.subscriptionInfo.getSessionInfo();
      }
      else if (this.receiverSubjectInfo != null) {
         // buggy, we always take the first session, the exportMessage() could fail if different message interceptors for different sessions are used!!!
         this.receiverSessionInfo = this.receiverSubjectInfo.getFirstSession();
      }

      return this.receiverSessionInfo;
   }

   public final MsgQueue getMsgQueue()
   {
      if (this.subscriptionInfo != null)
         return this.subscriptionInfo.getMsgQueue();
      else if (isSubjectQueue)
         return this.receiverSubjectInfo.getSubjectQueue();
      else if (this.receiverSessionInfo != null)
         return this.receiverSessionInfo.getSessionQueue();
      Log.error(ME, "Unknown queue");
      return null;
   }

   public final boolean isPubSub()
   {
      return (subscriptionInfo != null);
   }

   /** 
    * The approximate receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In nanoseconds elapsed since midnight, January 1, 1970 UTC
    */
   public final Timestamp getRcvTimestamp() {
      return publishQosCurrent.getRcvTimestamp();
   }

   /**
    * @return The priority of this message
    */
   public final int getPriority() {
      return publishQosCurrent.getPriority();
   }

   /**
    * Access the unique login name of the (last) publisher.
    * <p />
    * The sender of this message.
    * @return loginName of the data source which last updated this message
    *         If not known, en empty string "" is returned
    */
   public final String getPublisherName() {
      if (publishQosCurrent.getSender() == null)
         return "";
      return publishQosCurrent.getSender();
   }

   /**
    * Get the QoS string, don't invoke multiple time, it is not cached. 
    * @param index the current index when sending
    * @param max the size of the sended array
    * @param redeliver If redelivered on failure
    */
   private String getUpdateQos(int index, int max, int redeliver) throws XmlBlasterException
   {
      String subscriptionId = (subscriptionInfo == null) ? null : subscriptionInfo.getUniqueKey();
      return UpdateQoS.toXml(subscriptionId, msgUnitWrapper,
                          index, getMsgQueue().size()+max, Constants.STATE_OK, redeliver, glob.getId());
   }

   /**
    * Returns a handle on our message wrapper
    */
   public final MessageUnitWrapper getMessageUnitWrapper()
   {
      return msgUnitWrapper;
   }

   /**
    * Get the message unit, creates the update QoS for you. 
    * @see #getUpdateQos(int,int)
    */
   public final MessageUnit getMessageUnit(int index, int max, int redeliver) throws XmlBlasterException
   {
      if (msgUnit == null) {
         Log.error(ME + ".EmptyMessageUnit", "Internal problem, msgUnit = null");
         throw new XmlBlasterException(ME + ".EmptyMessageUnit", "Internal problem, msgUnit = null");
      }
      msgUnit.qos = getUpdateQos(index, max, redeliver);
      return msgUnit;
   }

   /**
    * Get the message unit, you must call getUpdateQos(int,int,int) before to generate the update QoS. 
    * @see #getUpdateQos(int,int,int)
    */
   public final MessageUnit getMessageUnit() throws XmlBlasterException
   {
      if (msgUnit == null) {
         Log.error(ME + ".EmptyMessageUnit", "Internal problem, msgUnit = null");
         throw new XmlBlasterException(ME + ".EmptyMessageUnit", "Internal problem, msgUnit = null");
      }
      return msgUnit;
   }

   /**
    * Get the message unit.
    */
   public final void setMessageUnit(MessageUnit msg) throws XmlBlasterException
   {
      this.msgUnit = msg;
   }

   /**
    * Try to find out the approximate memory consumption of this message.
    * <p />
    * It counts the message content bytes but NOT the xmlKey, xmlQoS etc. bytes<br />
    * This is because its used for the MessageQueue to figure out
    * how many bytes the client consumes in his queue.<p />
    *
    * As the key and qos will usually not change with follow up messages
    * (with same oid), the message only need to clone the content, and
    * that is quoted for the client.<p />
    *
    * Note that when the same message is queued for many clients, the
    * server load is duplicated for each client (needs to be optimized).<p />
    *
    * @return the approximate size in bytes of this object which contributes to a ClientUpdateQueue memory consumption
    */
   public final long getSizeInBytes() throws XmlBlasterException
   {
      long size = 0L;
      int objectHandlingBytes = 20; // a totally intuitive number

      size += objectHandlingBytes;  // this MsgQueueEntry instance
      if (msgUnit != null) {
         // size += xmlKey.size() + objectHandlingBytes;
         size += this.msgUnit.content.length;
      }

      // These are references on the original MsgQueueEntry and consume almost no memory:
      // size += xmlKey;
      // size += publishQosCurrent;
      // size += uniqueKey.size() + objectHandlingBytes;
      return size;
   }

   /**
    * Needed for sorting the queue. 
    * Comparing the longs directly is 20% faster than having a
    * String compound key
    * <br /><br />
    * Sorts the messages
    * <ol>
    *   <li>Priority</li>
    *   <li>Timestamp</li>
    * </ol>
    */
   public final int compare(MsgQueueEntry m2) {
      //Log.info(ME, "Entering compare A=" + getRcvTimestamp().getTimestamp() + " B=" + m2.getRcvTimestamp().getTimestamp());
      int diff = m2.getPriority() - getPriority();
      if (diff != 0) // The higher prio wins
         return diff;

      long dif = m2.getRcvTimestamp().getTimestamp() - getRcvTimestamp().getTimestamp();
      if (dif < 0L)
         return 1;  // The older message wins
      else if (dif > 0L)
         return -1;
      return 0;

      //System.out.println("Comparing " + getSortKey() + " : " + d2.getSortKey());
      // return getSortKey().compareTo(d2.getSortKey());
   }

   /**
    * Needed for sorting in queue
    */
   public final boolean equals(MsgQueueEntry m2) {
      return (getPriority() == m2.getPriority() && getRcvTimestamp() == m2.getRcvTimestamp());
   }

   /**
    * Dump state of this object into XML.
    * <br>
    * @return XML state of MsgQueueEntry
    */
   public final String toXml() throws XmlBlasterException
   {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of MsgQueueEntry
    */
   public final String toXml(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset != null) offset += extraOffset;

      sb.append(offset).append("<MsgQueueEntry>");
      sb.append(offset).append("   <uniqueKey>").append(msgUnitWrapper.getUniqueKey()).append("</uniqueKey>");
      if (publishQosCurrent==null)
         sb.append(offset).append("   <PublishQos>null</PublishQos>");
      else
         sb.append(publishQosCurrent.toXml(extraOffset + "   "));
      sb.append(offset).append("   <content>").append((msgUnit.content==null ? "null" : msgUnit.content.toString())).append("</content>");

      sb.append(offset).append("</MsgQueueEntry>\n");
      return sb.toString();
   }
}
