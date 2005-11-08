/*------------------------------------------------------------------------------
Name:      MsgUnitRaw.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.io.OutputStream;
import java.io.IOException;

/**
 * Encapsulates the xmlKey, content and qos. 
 * <p />
 * Keep this class slim, it is serialized and passed with RMI
 * <p />
 * The constructor arguments are checked to be not null and corrected
 * to "" or 'new byte[0]' if they are null
 */
public final class MsgUnitRaw // implements java.io.Serializable // Is serializable for RMI calls, but should not for J2ME!?
{
   private transient static final byte[] EMPTY_BYTEARR = new byte[0];
   private transient static final String EMPTY_STRING = "";
   private transient final Object msgUnit; // transport temporary the parsed instance MsgUnit as well
   private final String qos;
   private final String key;
   private final byte[] content;

   /**
    * @param msgUnit Temporary object with parsed information, this is not evaluated internally
    * @param key
    * @param content
    * @param qos
    */
   public MsgUnitRaw(Object msgUnit, String key, byte[] content, String qos) {
      this.msgUnit = msgUnit;
      this.qos = (qos == null) ? EMPTY_STRING : qos;
      this.key = (key == null) ? EMPTY_STRING : key;
      this.content = (content == null) ? EMPTY_BYTEARR : content;
   }

   /**
    */
   public MsgUnitRaw(String key, byte[] content, String qos) {
      this(null, key, content, qos);
   }

   /**
    * The raw XML string, never null
    */
   public String getKey() {
      return this.key;
   }

   /**
    * Get the raw content, never null
    */
   public byte[] getContent() {
      return this.content;
   }

   /**
    * Get the raw content, never null
    */
   public String getContentStr() {
      return new String(this.content);
   }

   /**
    * The raw QoS XML string, never null
    */
   public String getQos() {
      return this.qos;
   }

   /** 
    * The number of bytes of qos+key+content
    */
   public long size() {
      return this.qos.length() + this.key.length() + this.content.length;
   }

   /**
    * You can decide to pass with the constructor a parsed MsgUnit
    * @return MsgUnit with holds the parsed information, please treat as immutable, or null
    */
   public Object getMsgUnit() {
      return this.msgUnit;
   }

   public String toXml() {
      return toXml((String)null);
   }

   public String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer();
      String offset = "\n";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<MsgUnitRaw>");
      sb.append(this.key);
      sb.append(offset).append(" <content><![CDATA[").append(new String(this.content)).append("]]></content>");
      sb.append(this.qos);
      sb.append(offset).append("</MsgUnitRaw>\n");

      return sb.toString();
   }

   public void toXml(String extraOffset, OutputStream out) throws IOException {
      StringBuffer sb = new StringBuffer(qos.length() + key.length() + 256);
      String offset = "\n";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(qos);
      sb.append(key);

      // TODO: Potential charset problem when not Base64 protected
      boolean doEncode = false;
      int len = content.length - 2;
      for (int i=0; i<len; i++) {
         if (content[i] == (byte)']' && content[i+1] == (byte)']' && content[i+2] == (byte)'>') {
            doEncode = true;
            break;
         }
      }

      // TODO: Port to EncodableData! But what about J2ME?
      // Needs to be parseable by XmlScriptInterpreter
      if (doEncode) { // Constants.ENCODING_BASE64="base64"
         // link=''?  name=null, size=1000L type=Constants.TYPE_BLOB encoding=Constants.ENCODING_BASE64
         sb.append(offset).append("<content size='").append(content.length).append("' type='byte[]' encoding='base64'>");
         out.write(sb.toString().getBytes());

         String encoded = Base64.encode(content);
         out.write(encoded.getBytes());

         out.write("</content>".getBytes());
      }
      else {
         sb.append(offset).append("<content size='").append(content.length).append("' type='byte[]'><![CDATA[");
         out.write(sb.toString().getBytes());
         
         out.write(content);

         out.write("]]></content>".getBytes());
      }
   }
}
