/*------------------------------------------------------------------------------
Name:      UpdateMsgUnit.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.activex;

//import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;

/**
 * Encapsulates the received message. 
 */
public final class UpdateMsgUnit /*extends MsgUnit*/ implements java.io.Serializable
{
   String cbSessionId;
   UpdateKey updateKey;
   byte[] content;
   UpdateQos updateQos;

   /**
    * This is a constructor suitable for update clients. 
    */
   public UpdateMsgUnit(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      //super(key.getData(), content, qos.getData());
      this.cbSessionId = cbSessionId;
      this.updateKey = updateKey;
      this.content = content;
      this.updateQos = updateQos;
   }

   public String getCbSessionId() {
      return this.cbSessionId;
   }

   public UpdateKey getUpdateKey() {
      return this.updateKey;
   }

   public String getContentStr() {
      return new String(this.content);
   }

   public byte[] getContent() {
      return this.content;
   }

   public UpdateQos getUpdateQos() {
      return this.updateQos;
   }

   public String toXml() {
      return toXml("", -1);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @param maxContentLen For huge content length you can choose to display
    *        only the given size of the content (from the beginning), the rest is not dumped.<br />
    *        -1 dumps the complete content<br />
    *        0 dumps no content
    * @return The data of this MsgUnit as a XML ASCII string
    */
   public String toXml(String extraOffset, int maxContentLen) {
      StringBuffer sb = new StringBuffer(1024);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<UpdateMsgUnit ").append(this.cbSessionId).append(">");
      sb.append(this.updateKey.toXml(extraOffset+Constants.INDENT));
      if (maxContentLen < 0 || this.content.length < maxContentLen) {
         sb.append(offset).append("  <content><![CDATA[").append(new String(this.content)).append("]]></content>");
      }
      else if (maxContentLen > 0) {
         sb.append(offset).append("  <content size='").append(content.length).append("'><![CDATA[");
         sb.append(new String(this.content, 0, maxContentLen)).append("...]]></content>");
      }
      sb.append(this.updateQos.toXml(extraOffset+Constants.INDENT));
      sb.append(offset).append("</UpdateMsgUnit>\n");

      return sb.toString();
   }
}
