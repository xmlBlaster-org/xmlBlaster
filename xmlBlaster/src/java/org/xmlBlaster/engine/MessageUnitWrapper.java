/*------------------------------------------------------------------------------
Name:      MessageUnitWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Wrapping the CORBA MessageUnit to allow some nicer usage
Version:   $Id: MessageUnitWrapper.java,v 1.17 2000/06/13 13:03:59 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.xml2java.PublishQoS;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.engine.persistence.I_PersistenceDriver;
import java.util.*;


/**
 * Wrapping the CORBA MessageUnit to allow some nicer usage.
 * <p />
 * If you look at the CORBA generated MessageUnit, its very raw and
 * does not allow any modifications.<br />
 * So this Wrapper encapsulates the CORBA generated MessageUnit and adds
 * some value to it.
 */
public class MessageUnitWrapper
{
   private final static String ME = "MessageUnitWrapper";

   /** The broker which manages me */
   private RequestBroker requestBroker;

   /** The CORBA MessageUnit (raw struct) */
   private MessageUnit msgUnit;

   /** the meta data describing this message */
   private XmlKey xmlKey;

   /** the flags from the publisher */
   private PublishQoS publishQoS;

   /** the unique loginName of the publisher */
   private String publisherName;

   /** Attribute oid of key tag: <key oid="..."> </key> */
   private String uniqueKey;

   /** Handle on the persistence driver */
   private I_PersistenceDriver persistenceDriver;


   /**
    * Use this constructor if a new message object is fed by method publish().
    * <p />
    * @param xmlKey Since it is parsed in the calling method, we don't need to do it again from msgUnit.xmlKey_literal
    * @param msgUnit the CORBA MessageUnit data container
    * @param publishQoS the quality of service
    * @param publisherName the unique loginName of the publisher
    */
   public MessageUnitWrapper(RequestBroker requestBroker, XmlKey xmlKey, MessageUnit msgUnit, PublishQoS publishQoS, String publisherName) throws XmlBlasterException
   {
      if (xmlKey == null || msgUnit == null || publishQoS == null) {
         Log.error(ME, "Invalid constructor parameter");
         throw new XmlBlasterException(ME, "Invalid constructor parameter");
      }
      this.requestBroker = requestBroker;
      this.msgUnit = msgUnit;
      this.xmlKey = xmlKey;
      this.uniqueKey = this.xmlKey.getUniqueKey();
      this.publishQoS = publishQoS;
      this.publisherName = publisherName;
      this.persistenceDriver = requestBroker.getPersistenceDriver();

      if (publisherName == null)
         publisherName = "";

      if (this.msgUnit.content == null)
         this.msgUnit.content = new byte[0];

      if (this.xmlKey.isGeneratedOid())  // if the oid is generated, we need to update the msgUnit.xmlKey as well
         this.msgUnit.xmlKey = xmlKey.literal();

      if (persistenceDriver != null && publishQoS.isDurable() && !publishQoS.fromPersistenceStore())
         persistenceDriver.store(this);

      publishQoS.setFromPersistenceStore(false);

      if (Log.TRACE) Log.trace(ME, "Creating new MessageUnitWrapper for published message, key oid=" + uniqueKey);
   }


   /**
    * Accessing the key of this message
    */
   public final XmlKey getXmlKey()
   {
      return xmlKey;
   }


   /**
    * Setting update of a changed content.
    * <p />
    * @param newContent The new data blob
    * @param publisherName The source of the data (unique login name)
    * @return changed? true:  if content has changed
    *                  false: if content didn't change
    */
   public final boolean setContent(byte[] newContent, String publisherName) throws XmlBlasterException
   {
      // if (Log.TRACE) Log.trace(ME, "Updating xmlKey " + uniqueKey + " from " + publisherName + ", new newContent=" + new String(newContent));

      if (publishQoS.readonly()) {
         Log.warning(ME+".Readonly", "Sorry, new published message rejected, message is readonly.");
         throw new XmlBlasterException(ME+".Readonly", "Sorry, new published message rejected, message is readonly.");
      }

      if (newContent == null)
         newContent = new byte[0];

      if (publisherName != null)
         this.publisherName = publisherName;

      boolean changed = false;
      if (this.msgUnit.content.length != newContent.length) {
         changed = true;
      }
      else {
         for (int ii=0; ii<newContent.length; ii++)
            if (this.msgUnit.content[ii] != newContent[ii]) {
               changed = true;
               break;
            }
      }

      if (changed) {  // new content is not the same as old one
         this.msgUnit.content = newContent;
         if (persistenceDriver != null && publishQoS.isDurable()) // && !publishQoS.fromPersistenceStore())
            persistenceDriver.store(xmlKey, newContent);
         return true;
      }
      else {
         return false;
      }
   }

   /**
    * If the message was durable, erase it from the persistent store.
    */
   final void erase() throws XmlBlasterException
   {
      if (persistenceDriver != null && publishQoS.isDurable())
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
   public final PublishQoS getPublishQoS()
   {
      return publishQoS;
   }


   /**
    * Access the unique login name of the (last) publisher
    * @return loginName of the data source which last updated this message
    */
   public String getPublisherName()
   {
      return publisherName;
   }


   /**
    * This is the unique key of the msgUnit
    */
   public final void setXmlKey(XmlKey xmlKey)
   {
      this.xmlKey = xmlKey;
      this.msgUnit.xmlKey = xmlKey.literal();
   }


   /**
    */
   public final String getContentMime() throws XmlBlasterException
   {
      if (getMessageUnit().xmlKey == null) {
         Log.error(ME + ".UnknownMime", "Sorry, mime type not yet known for " + getUniqueKey());
         throw new XmlBlasterException(ME + ".UnknownMime", "Sorry, mime type not yet known for " + getUniqueKey());
      }
      return xmlKey.getContentMime();
   }


   /**
    * This is the unique key of the msgUnit
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
    * Clones this MessageUnitWrapper to a new one, the only attributes
    * which is cloned is the MessageUnit.content. <p />
    * All other attributes are references to the original ones
    *
    * @return a new MessageUnitWrapper with a cloned MessageUnit.content
    */
   public MessageUnitWrapper cloneContent() throws XmlBlasterException
   {
      MessageUnitWrapper newWrapper = new MessageUnitWrapper(requestBroker, xmlKey, msgUnit, publishQoS, publisherName);

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
      // size += publishQoS;
      // size += uniqueKey.size() + objectHandlingBytes;
      return size;
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @return XML state of MessageUnitWrapper
    */
   public final StringBuffer printOn() throws XmlBlasterException
   {
      return printOn((String)null);
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of MessageUnitWrapper
    */
   public final StringBuffer printOn(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<MessageUnitWrapper>");
      sb.append(offset + "   <uniqueKey>" + getUniqueKey() + "</uniqueKey>");
      if (xmlKey==null)
         sb.append(offset + "   <XmlKey>null</XmlKey>");
      else
         sb.append(xmlKey.printOn(extraOffset + "   ").toString());
      if (publishQoS==null)
         sb.append(offset + "   <PublishQoS>null</PublishQoS>");
      else
         sb.append(publishQoS.printOn(extraOffset + "   ").toString());
      sb.append(offset + "   <content>" + (msgUnit.content==null ? "null" : msgUnit.content.toString()) + "</content>");
      sb.append(offset + "</MessageUnitWrapper>\n");
      return sb;
   }
}
