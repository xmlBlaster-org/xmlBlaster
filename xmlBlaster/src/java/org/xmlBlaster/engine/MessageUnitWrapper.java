/*------------------------------------------------------------------------------
Name:      MessageUnitWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Wrapping the CORBA MessageUnit to allow some nicer usage
Version:   $Id: MessageUnitWrapper.java,v 1.1 1999/12/01 22:17:28 ruff Exp $
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
   private XmlQoS publishQoS;        // the flags from the publisher
   private String uniqueKey;         // Attribute oid of key tag: <key oid="..."> </key>


   /**
    * Use this constructor if a subscription is made on a yet unknown object. 
    * <p />
    */
   /*
   public MessageUnitHandler(XmlKey xmlKey) throws XmlBlasterException
   {
      if (xmlKey == null) {
         Log.error(ME, "Invalid constructor parameter xmlKey");
         throw new XmlBlasterException(ME, "Invalid constructor parameter xmlKey");
      }

      this.uniqueKey = xmlKey.getUniqueKey();
      // this.xmlKey = xmlKey; this is not the real xmlKey from a publish, its only the subscription syntax
      this.messageUnit = new MessageUnit(xmlKey.literal(), new byte[0]);

      if (Log.CALLS) Log.trace(ME, "Creating new MessageUnitHandler because of subscription. Key=" + uniqueKey);

      // mimeType and content remains unknown until first data is fed
   }
   */


   /**
    * Use this constructor if a new message object is fed by method publish(). 
    * <p />
    * @param xmlKey Since it is parsed in the calling method, we don't need to do it again from messageUnit.xmlKey_literal
    * @param messageUnit the CORBA MessageUnit data container
    * @param publishQoS the quality of service
    */
   public MessageUnitWrapper(XmlKey xmlKey, MessageUnit messageUnit, XmlQoS publishQoS) throws XmlBlasterException
   {
      if (xmlKey == null || messageUnit == null) {
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
