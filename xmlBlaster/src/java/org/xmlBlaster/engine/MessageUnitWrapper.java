/*------------------------------------------------------------------------------
Name:      MessageUnitWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Wrapping the CORBA MessageUnit to allow some nicer usage
Version:   $Id: MessageUnitWrapper.java,v 1.46 2002/10/25 08:30:33 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.jutils.log.LogChannel;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.xml2java.PublishQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.persistence.I_PersistenceDriver;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.I_Timeout;
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
   private PublishQos publishQos;

   /** Attribute oid of key tag: <key oid="..."> </key> */
   private String uniqueKey;

   /** Handle on the persistence driver */
   private I_PersistenceDriver persistenceDriver;

   private Timeout messageTimer;
   private Timestamp timerKey = null;
   private boolean isExpired = false;

   // TODO: Pass with client QoS!!!
   private static final boolean recieveTimestampHumanReadable = Global.instance().getProperty().get("cb.recieveTimestampHumanReadable", false);

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
    * @param xmlKey Since it is parsed in the calling method, we don't need to do it again from msgUnit.xmlKey_literal
    * @param msgUnit the CORBA MessageUnit data container
    * @param publishQos the quality of service
    */
   public MessageUnitWrapper(Global glob, RequestBroker requestBroker, XmlKey xmlKey, MessageUnit msgUnit, PublishQos publishQos) throws XmlBlasterException
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

      if (this.msgUnit.content == null)
         this.msgUnit.content = new byte[0];

      if (this.xmlKey.isGeneratedOid())  // if the oid is generated, we need to update the msgUnit.xmlKey as well
         this.msgUnit.xmlKey = xmlKey.literal();

      if (persistenceDriver != null && publishQos.isDurable() && !publishQos.isFromPersistenceStore())
      {
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
         this.ME = "MessageUnitWrapper" + this.glob.getLogPraefixDashed() + "/msg/" + uniqueKey;
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
         log.warn(ME(), "Message " + getUniqueKey() + " is expired, timeout event occurred - not implemented, tests are missing!");
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
    * Tagged form of message receive, e.g.:<br />
    * &lt;rcvTimestamp nanos='1007764305862000004'/>
    *
    * @see org.xmlBlaster.util.Timestamp
    */
   public final String getXmlRcvTimestamp()
   {
      if (recieveTimestampHumanReadable)
         return getRcvTimestamp().toXml(null, true);
      else
         return getRcvTimestamp().toXml();
   }


   /**
    * Compares bytes if the given content is identical to the
    * internal content
    * @return true content is identical
    */
   final boolean sameContent(byte[] newContent)
   {
      if (newContent == null) {
         if (this.msgUnit.content.length < 1)
            return true;
         return false;
      }
      if (this.msgUnit.content.length != newContent.length)
         return false;
      for (int ii=0; ii<newContent.length; ii++)
         if (this.msgUnit.content[ii] != newContent[ii])
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
    * This is the unique key of the msgUnit
    */
   public final String getUniqueKey()
   {
      return uniqueKey;
   }

   /**
    * Access the flags from the publisher
    */
   public final PublishQos getPublishQos()
   {
      return publishQos;
   }

   /**
    * Priority of a message. 
    * @see org.xmlBlaster.engine.helper.Constants
    */
   public int getPriority()
   {
      return publishQos.getPriority();
   }

   /**
    * Access the unique login name of the (last) publisher.
    * <p />
    * The sender of this message.
    * @return loginName of the data source which last updated this message
    *         If not known, en empty string "" is returned
    */
   public String getPublisherName()
   {
      if (publishQos.getSender() == null)
         return "";
      return publishQos.getSender();
   }

   /**
    * This is the unique key of the msgUnit
    */
   final void setXmlKey(XmlKey xmlKey)
   {
      this.xmlKey = xmlKey;
      this.msgUnit.xmlKey = xmlKey.literal();
   }

   /**
    */
   public final String getContentMime() throws XmlBlasterException
   {
      if (getMessageUnit().xmlKey == null) {
         log.error(ME() + ".UnknownMime", "Sorry, mime type not yet known for " + getUniqueKey());
         throw new XmlBlasterException(ME() + ".UnknownMime", "Sorry, mime type not yet known for " + getUniqueKey());
      }
      return xmlKey.getContentMime();
   }

   /**
    */
   public final String getContentMimeExtended() throws XmlBlasterException
   {
      if (getMessageUnit().xmlKey == null) {
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
   public final MessageUnit getMessageUnit() throws XmlBlasterException
   {
      if (msgUnit == null) {
         log.error(ME() + ".EmptyMessageUnit", "Internal problem, msgUnit = null");
         throw new XmlBlasterException(ME() + ".EmptyMessageUnit", "Internal problem, msgUnit = null");
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

      byte[] oldContent = this.msgUnit.content;
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
      this.msgUnit.content = content;
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
         size += this.msgUnit.content.length;
      }

      // These are references on the original MessageUnitWrapper and consume almost no memory:
      // size += xmlKey;
      // size += publishQos;
      // size += uniqueKey.size() + objectHandlingBytes;
      return size;
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
      sb.append(offset).append("   <content>").append((msgUnit.content==null ? "null" : msgUnit.content.toString())).append("</content>");

      //sb.append(offset).append("   ").append(getXmlRcvTimestamp()); // is dumped in publishQos already

      sb.append(offset).append("</MessageUnitWrapper>");
      return sb.toString();
   }
}
