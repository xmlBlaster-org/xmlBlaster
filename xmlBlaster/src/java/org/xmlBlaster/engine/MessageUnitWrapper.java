/*------------------------------------------------------------------------------
Name:      MessageUnitWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Wrapping the CORBA MessageUnit to allow some nicer usage
Version:   $Id: MessageUnitWrapper.java,v 1.3 1999/12/08 12:16:17 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.serverIdl.MessageUnit;
import org.xmlBlaster.clientIdl.BlasterCallback;
import java.util.*;


/**
 * Wrapping the CORBA MessageUnit to allow some nicer usage.
 * <p />
 * If you look at the CORBA generated MessageUnit, its very raw and
 * dos not allow any modifications.<br />
 * So this Wrapper encapsulates the CORBA generated MessageUnit and adds
 * some value to it.
 */
public class MessageUnitWrapper
{
   private final static String ME = "MessageUnitWrapper";

   private MessageUnit messageUnit;  // The CORBA MessageUnit (raw struct)
   private XmlKey xmlKey;            // the meta data describing this message
   private PublishQoS publishQoS;    // the flags from the publisher
   private String uniqueKey;         // Attribute oid of key tag: <key oid="..."> </key>


   /**
    * Use this constructor if a new message object is fed by method publish().
    * <p />
    * @param xmlKey Since it is parsed in the calling method, we don't need to do it again from messageUnit.xmlKey_literal
    * @param messageUnit the CORBA MessageUnit data container
    * @param publishQoS the quality of service
    */
   public MessageUnitWrapper(XmlKey xmlKey, MessageUnit messageUnit, PublishQoS publishQoS) throws XmlBlasterException
   {
      if (xmlKey == null || messageUnit == null || publishQoS == null) {
         Log.error(ME, "Invalid constructor parameter");
         throw new XmlBlasterException(ME, "Invalid constructor parameter");
      }
      this.messageUnit = messageUnit;
      this.xmlKey = xmlKey;
      this.uniqueKey = this.xmlKey.getUniqueKey();
      this.publishQoS = publishQoS;

      if (this.messageUnit.content == null)
         this.messageUnit.content = new byte[0];

      if (Log.CALLS) Log.trace(ME, "Creating new MessageUnitWrapper because of subscription. Key=" + uniqueKey);
   }


   /**
    * Accessing the key of this message
    */
   public final XmlKey getXmlKey()
   {
      return xmlKey;
   }


   /**
    * setting update of a changed content
    * @return changed? true:  if content has changed
    *                  false: if content didn't change
    */
   public final boolean setContent(byte[] content)
   {
      if (Log.CALLS) Log.trace(ME, "Updating xmlKey " + uniqueKey);

      if (content == null)
         content = new byte[0];

      boolean changed = false;
      if (this.messageUnit.content.length != content.length) {
         changed = true;
      }
      else {
         for (int ii=0; ii<content.length; ii++)
            if (this.messageUnit.content[ii] != content[ii]) {
               changed = true;
               break;
            }
      }

      if (changed) {  // new content is not the same as old one
         this.messageUnit.content = content;
         return true;
      }
      else {
         return false;
      }
   }


   /**
    * This is the unique key of the messageUnit
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
    * This is the unique key of the messageUnit
    */
   public final void setXmlKey(XmlKey xmlKey)
   {
      this.xmlKey = xmlKey;
      this.messageUnit.xmlKey = xmlKey.literal();
   }


   /**
    */
   public final String getMimeType() throws XmlBlasterException
   {
      if (getMessageUnit().xmlKey == null) {
         Log.error(ME + ".UnknownMime", "Sorry, mime type not yet known for " + getUniqueKey());
         throw new XmlBlasterException(ME + ".UnknownMime", "Sorry, mime type not yet known for " + getUniqueKey());
      }
      return xmlKey.getMimeType();
   }


   /**
    * This is the unique key of the messageUnit
    */
   public final MessageUnit getMessageUnit() throws XmlBlasterException
   {
      if (messageUnit == null) {
         Log.error(ME + ".EmptyMessageUnit", "Internal problem, messageUnit = null");
         throw new XmlBlasterException(ME + ".EmptyMessageUnit", "Internal problem, messageUnit = null");
      }
      return messageUnit;
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
      MessageUnitWrapper newWrapper = new MessageUnitWrapper(xmlKey, messageUnit, publishQoS);

      byte[] oldContent = this.messageUnit.content;
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
      this.messageUnit.content = content;
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
      if (messageUnit != null) {
         // size += xmlKey.size() + objectHandlingBytes;
         size += this.messageUnit.content.length;
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
      sb.append(offset + "   <content>" + (messageUnit.content==null ? "null" : messageUnit.content.toString()) + "</content>");
      sb.append(offset + "</MessageUnitWrapper>\n");
      return sb;
   }
}
