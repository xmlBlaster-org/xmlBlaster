/*------------------------------------------------------------------------------
Name:      MessageUnit.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Container for a message.
Version:   $Id: MessageUnit.java,v 1.1 2000/06/25 19:09:45 ruff Exp $
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
}
