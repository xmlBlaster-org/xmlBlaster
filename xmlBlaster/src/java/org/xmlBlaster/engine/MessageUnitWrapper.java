/*------------------------------------------------------------------------------
Name:      MessageUnitWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Wrapping the CORBA MessageUnit to allow some nicer usage
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.jutils.log.LogChannel;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.qos.PublishQosServer;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.persistence.I_PersistenceDriver;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.enum.PriorityEnum;
import java.util.*;


/**
 * Wrapping the raw MessageUnit to allow some nicer usage.
 * <p />
 * If you look at the CORBA generated MessageUnit or the over RMI transferred MessageUnit,
 * its very raw and does not allow any modifications.<br />
 * So this Wrapper encapsulates the simple MessageUnit and adds
 * some value to it.
 * For example it holds the readily parsed XML QoS and XmlKey of a message.
 * @see org.xmlBlaster.engine.helper.MessageUnit
 * @see org.xmlBlaster.protocol.corba.serverIdl.MessageUnit
 * @author ruff@swand.lake.de
 */
public final class MessageUnitWrapper implements I_Timeout
{
   private String ME;

   /** The broker which manages me */
   private RequestBroker requestBroker;
   private final Global glob;
   private final LogChannel log;

   /** The MessageUnitHandler which manages me */
   private MessageUnitHandler messageUnitHandler = null;

   /** The MessageUnit with key/content/qos (raw struct) */
   private MessageUnit msgUnit;

   /** the meta data describing this message */
   private XmlKey xmlKey;

   /** the flags from the publisher */
   private PublishQosServer publishQos;

   /** Attribute oid of key tag: <key oid="..."> </key> */
   private String uniqueKey;

   /** Handle on the persistence driver */
   private I_PersistenceDriver persistenceDriver;

   /** Message expiration as specified by publisher */
   private Timeout messageTimer;
   private Timestamp timerKey = null;
   private boolean isExpired = false;


   /**
    * Count how often this messages is put into a queue
    * When callback succeeded, the counter is reduced
    * If the message isVolatile() we can delete it when all
    * callbacks succeeded
    */
   private int enqueueCounter = 0;
   private boolean isErased = false;

   /**
    * Use this constructor if a new message object is fed by method publish().
    * <p />
    * @param xmlKey Since it is parsed in the calling method, we don't need to do it again from msgUnit.getKey()_literal
    * @param msgUnit the CORBA MessageUnit data container
    * @param publishQos the quality of service
    */
   public MessageUnitWrapper(Global glob, RequestBroker requestBroker, XmlKey xmlKey, MessageUnit msgUnit, PublishQosServer publishQos) throws XmlBlasterException
   {
      if (xmlKey == null || msgUnit == null || publishQos == null) {
         Global.instance().getLog("core").error(ME(), "Invalid constructor parameter");
         throw new XmlBlasterException(ME(), "Invalid constructor parameter");
      }
      
      this.requestBroker = requestBroker;
      this.glob = glob;
      this.log = requestBroker.getLog();
      this.msgUnit = msgUnit;
      this.xmlKey = xmlKey;
      this.uniqueKey = this.xmlKey.getUniqueKey();
      this.publishQos = publishQos;
      this.persistenceDriver = requestBroker.getPersistenceDriver();
      this.messageTimer = requestBroker.getGlobal().getMessageTimer();

      if (this.xmlKey.isGeneratedOid())  // if the oid is generated, we need to update the msgUnit.getKey() as well
         this.msgUnit = new MessageUnit(this.msgUnit, xmlKey.literal(), null, null);

      if (persistenceDriver != null && publishQos.isDurable() && !publishQos.isFromPersistenceStore()) {
         persistenceDriver.store(this);
         if(log.TRACE) log.trace(ME(),"Storing MessageUnit with key oid="+xmlKey.getKeyOid());
      }

      publishQos.setFromPersistenceStore(false);

      if (publishQos.getRemainingLife() > 0L) { // -1 is unlimited. 0 is volatile?
         log.info(ME(), "Setting expiry timer for " + getUniqueKey() + " to " + publishQos.getRemainingLife() + " msec");
         timerKey = this.messageTimer.addTimeoutListener(this, publishQos.getRemainingLife(), null);
      }

      if (log.TRACE) log.trace(ME(), "Creating new MessageUnitWrapper for published message");
      //Thread.currentThread().dumpStack();
   }

   /** For performance reasons we create it only if logging is requested */
   private final String ME() {
      if (this.ME == null)
         this.ME = "MessageUnitWrapper" + this.glob.getLogPrefixDashed() + "/msg/" + uniqueKey;
      return this.ME;
   }

   public void finalize()
   {
      if (timerKey != null) {
         this.messageTimer.removeTimeoutListener(timerKey);
         timerKey = null;
      }

      if (log.TRACE) log.trace(ME(), "finalize - garbage collected " + this.uniqueKey);
   }

   public void shutdown()
   {
      if (log.CALL) log.call(ME(), "shutdown() of message " + this.uniqueKey);
      isExpired = true;
      if (timerKey != null) {
         this.messageTimer.removeTimeoutListener(timerKey);
         timerKey = null;
         this.messageTimer = null;
      }
   }

   public boolean isExpired()
   {
      return this.isExpired;
   }

   /**
    * @param +1 to add, -1 to subtract
    */
   public synchronized void addEnqueueCounter(int val)
   {
      this.enqueueCounter += val;
   }

   /**
    * Count how often this messages is put into a queue
    * When callback succeeded, the counter is reduced
    * If the message isVolatile() we can delete it when all
    * callbacks succeeded
    */
   public synchronized int getEnqueueCounter()
   {
      return this.enqueueCounter;
   }

   /**
    * Marks the message that it is erased or going to be erased. 
    * @return true if message was in erased or shutdown state already
    */
   public boolean doesErase() {
      synchronized (this) {
         if (isErased) return true;
         isErased = true;
      }
      return false;
   }


   /**
    * We are notified when this session expires. 
    * @param userData You get bounced back your userData which you passed
    *                 with Timeout.addTimeoutListener()
    */
   public final void timeout(Object userData)
   {
      synchronized (this) {
         isExpired = true;
         timerKey = null;
         log.warn(ME(), "Message " + getUniqueKey() + " is expired, timeout event occurred not implemented - tests are missing!");
         // !!! We expire the Wrapper or the MessageUnit which may have a new Wrapper which is not expired??
         //requestBroker.eraseExpired(null, this);
      }
   }

   /**
    * Accessing the key of this message
    */
   public final XmlKey getXmlKey()
   {
      return xmlKey;
   }

   void setMessageUnitHandler(MessageUnitHandler h)
   {
      this.messageUnitHandler = h;
   }

   /** May be null */
   public MessageUnitHandler getMessageUnitHandler()
   {
      return this.messageUnitHandler;
   }

   /** 
    * The approximate receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In nanoseconds elapsed since midnight, January 1, 1970 UTC
    */
   public final Timestamp getRcvTimestamp()
   {
      return publishQos.getRcvTimestamp();
   }

   /**
    * Compares bytes if the given content is identical to the
    * internal content
    * @return true content is identical
    */
   final boolean sameContent(byte[] newContent)
   {
      if (newContent == null) {
         if (this.msgUnit.getContent().length < 1)
            return true;
         return false;
      }
      if (this.msgUnit.getContent().length != newContent.length)
         return false;
      for (int ii=0; ii<newContent.length; ii++)
         if (this.msgUnit.getContent()[ii] != newContent[ii])
            return false;
      return true;
   }

   /**
    * If the message was durable, erase it from the persistent store.
    */
   final void erase() throws XmlBlasterException
   {
      if (persistenceDriver != null && publishQos.isDurable())
         persistenceDriver.erase(xmlKey);
   }

   /**
    * This is the unique key 'oid' of the msgUnit
    */
   public final String getUniqueKey()
   {
      return uniqueKey;
   }

   /**
    * Access the flags from the publisher
    */
   public final PublishQosServer getPublishQos()
   {
      return publishQos;
   }

   /**
    * Priority of a message. 
    * @see org.xmlBlaster.engine.helper.Constants
    */
   public PriorityEnum getPriority()
   {
      return publishQos.getPriority();
   }

   /**
    * This is the unique key of the msgUnit
    */
   final void setXmlKey(XmlKey xmlKey)
   {
      this.xmlKey = xmlKey;
      this.msgUnit = new MessageUnit(this.msgUnit, xmlKey.literal(), null, null);
   }

   /**
    */
   public final String getContentMime() throws XmlBlasterException
   {
      if (getMessageUnit().getKey().length() < 1) {
         log.error(ME() + ".UnknownMime", "Sorry, mime type not yet known for " + getUniqueKey());
         throw new XmlBlasterException(ME() + ".UnknownMime", "Sorry, mime type not yet known for " + getUniqueKey());
      }
      return xmlKey.getContentMime();
   }

   /**
    */
   public final String getContentMimeExtended() throws XmlBlasterException
   {
      if (getMessageUnit().getKey().length() < 1) {
         log.error(ME() + ".UnknownMime", "Sorry, extended mime type not yet known for " + getUniqueKey());
         throw new XmlBlasterException(ME() + ".UnknownMime", "Sorry, extended mime type not yet known for " + getUniqueKey());
      }
      return xmlKey.getContentMimeExtended();
   }

   /**
    * Get the message unit.
    * Note it has package scope access only.
    * If you want to access it from elsewhere, use the getMessageUnitClone() method
    */
   public final MessageUnit getMessageUnit()
   {
      if (msgUnit == null) {
         log.error(ME() + ".EmptyMessageUnit", "Internal problem, msgUnit = null");
         throw new IllegalArgumentException("Internal problem, msgUnit = null in MessageUnitWrapper");
      }
      return msgUnit;
   }

   /**
    * Get the cloned message unit.
    */
   public final MessageUnit getMessageUnitClone() throws XmlBlasterException
   {
      if (msgUnit == null) {
         log.error(ME() + ".EmptyMessageUnit", "Internal problem, msgUnit = null");
         throw new XmlBlasterException(ME() + ".EmptyMessageUnit", "Internal problem, msgUnit = null");
      }
      return msgUnit.getClone();
   }

   /**
    * Clones this MessageUnitWrapper to a new one, the only attributes
    * which is cloned is the MessageUnit.content. <p />
    * All other attributes are references to the original ones
    *
    * @return a new MessageUnitWrapper with a cloned MessageUnit.content
    */
   MessageUnitWrapper cloneContent() throws XmlBlasterException
   {
      MessageUnitWrapper newWrapper = new MessageUnitWrapper(glob, requestBroker, xmlKey, msgUnit, publishQos);

      byte[] oldContent = this.msgUnit.getContent();
      byte[] newContent = new byte[oldContent.length];

      for (int ii=0; ii<oldContent.length; ii++)
         newContent[ii] = oldContent[ii];

      newWrapper.setContentRaw(newContent);

      return newWrapper;
   }

   /**
    * Used by clone() to assign the message content.
    * @param the new MessageUnit.content
    */
   final void setContentRaw(byte[] content)
   {
      this.msgUnit = new MessageUnit(this.msgUnit, null, content, null);
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

      size += objectHandlingBytes;  // this MessageUnitWrapper instance
      if (msgUnit != null) {
         // size += xmlKey.size() + objectHandlingBytes;
         size += this.msgUnit.getContent().length;
      }

      // These are references on the original MessageUnitWrapper and consume almost no memory:
      // size += xmlKey;
      // size += publishQos;
      // size += uniqueKey.size() + objectHandlingBytes;
      return size;
   }

   /**
    * Helper to convert an array of MessageUnitWrapper to an array of MessageUnit
    */
   public static final MessageUnit[] toMessageUnitArr(MessageUnitWrapper[] wrapperArr) {
      MessageUnit[] msgUnitArr = new MessageUnit[wrapperArr.length];
      for (int ii=0; ii<wrapperArr.length; ii++)
         msgUnitArr[ii] = wrapperArr[ii].getMessageUnit();
      return msgUnitArr;
   }

   /**
    * Dump state of this object into XML.
    * <br>
    * @return XML state of MessageUnitWrapper
    */
   public final String toXml() throws XmlBlasterException
   {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of MessageUnitWrapper
    */
   public final String toXml(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<MessageUnitWrapper>");
      sb.append(offset).append("   <uniqueKey>").append(getUniqueKey()).append("</uniqueKey>");
      if (publishQos==null)
         sb.append(offset).append("   <publishQos>null</publishQos>");
      else
         sb.append(publishQos.toXml(extraOffset + "   "));
      sb.append(offset).append("   <content>").append((msgUnit.getContent()==null ? "null" : msgUnit.getContent().toString())).append("</content>");

      //sb.append(offset).append("   ").append(getXmlRcvTimestamp()); // is dumped in publishQos already

      sb.append(offset).append("</MessageUnitWrapper>");
      return sb.toString();
   }
}
