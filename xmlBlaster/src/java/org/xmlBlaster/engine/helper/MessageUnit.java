/*------------------------------------------------------------------------------
Name:      MessageUnit.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Container for a message.
Version:   $Id: MessageUnit.java,v 1.3 2001/08/19 23:07:54 ruff Exp $
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
   public MessageUnit(String xmlKey, byte[] content, String qos)
   {
      this.xmlKey = xmlKey;
      this.content = content;
      this.qos = qos;
   }

   public void setKey(String xmlKey){
      this.xmlKey = xmlKey;
   }

   public String getXmlKey() {
      return xmlKey;
   }
   public byte[] getContent() {
      return content;
   }
   public String getQos() {
      return qos;
   }
   /**
    * Clone this message unit.
    * <p />
    * You can't manipulate the data in the original MessageUnit
    */
   public MessageUnit getClone() {
      byte[] newContent = new byte[content.length];
      for (int ii=0; ii<content.length; ii++)
         newContent[ii] = content[ii];
      return new MessageUnit(xmlKey, content, qos);
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

      sb.append(offset + "<MessageUnit>");
      sb.append(offset + xmlKey);
      sb.append(offset + "   <content>");
      sb.append(offset + "   " + new String(content));
      sb.append(offset + "   </content>\n");
      sb.append(offset + qos);
      sb.append(offset + "</MessageUnit>\n");

      return sb.toString();
   }
}
