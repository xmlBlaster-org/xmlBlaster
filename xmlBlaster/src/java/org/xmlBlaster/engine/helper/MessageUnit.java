/*------------------------------------------------------------------------------
Name:      MessageUnit.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Container for a message.
Version:   $Id: MessageUnit.java,v 1.8 2002/04/26 21:31:51 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.helper;


/**
 * Encapsulates the xmlKey, content and qos.
 * <p />
 * The data is NOT interpreted or parsed in the container, e.g.
 * qos contains the literal, XML based ASCII string.
 * <p />
 * Keep this class slim, it is serialized and passed with RMI
 */
public class MessageUnit implements java.io.Serializable
{
   public String xmlKey;
   public byte[] content;
   public String qos;

   /**
    * This is a temporary constructor used for the javascript (rhino) client
    */
   public MessageUnit(String xmlKey, String contentAsString, String qos)
   {
      setKey(xmlKey);
      setContent(contentAsString.getBytes());
      setQos(qos);
   }

   /**
    * The only constructor guarantees any attribute to be not null
    */
   public MessageUnit(String xmlKey, byte[] content, String qos)
   {
      setKey(xmlKey);
      setContent(content);
      setQos(qos);
   }

   public final void setKey(String xmlKey){
      if (xmlKey == null)
         this.xmlKey = "";
      else
         this.xmlKey = xmlKey;
   }

   public final void setQos(String qos){
      if (qos == null)
         this.qos = "";
      else
         this.qos = qos;
   }

   public final void setContent(byte[] content) {
      if (content == null)
         this.content = new byte[0];
      else
         this.content = content;
   }

   public final String getXmlKey() {
      return this.xmlKey;
   }
   public final byte[] getContent() {
      return this.content;
   }
   public final String getContentStr() {
      return new String(this.content);
   }
   public final String getQos() {
      return this.qos;
   }
   /**
    * Clone this message unit.
    * <p />
    * You can't manipulate the data in the original MessageUnit
    */
   public final MessageUnit getClone() {
      byte[] newContent = new byte[content.length];
      System.arraycopy(content, 0,  newContent, 0, content.length);
      return new MessageUnit(xmlKey, content, qos);
   }

   /** 
    * The number of bytes of qos+key+content
    */
   public final long size() {
      return qos.length() + xmlKey.length() + content.length;
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return The data of this MessageUnit as a XML ASCII string
    */
   public final String toXml()
   {
      return toXml((String)null);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return The data of this MessageUnit as a XML ASCII string
    */
   public final String toXml(String extraOffset)
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<MessageUnit>");
      sb.append(offset).append(xmlKey);
      sb.append(offset).append("   <content>");
      sb.append(offset).append("   ").append(new String(content));
      sb.append(offset).append("   </content>\n");
      sb.append(offset).append(qos);
      sb.append(offset).append("</MessageUnit>\n");

      return sb.toString();
   }
}
