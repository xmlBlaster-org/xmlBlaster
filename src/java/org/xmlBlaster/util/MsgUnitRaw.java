/*------------------------------------------------------------------------------
Name:      MsgUnitRaw.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

import org.xmlBlaster.util.def.Constants;

/**
 * Encapsulates the xmlKey, content and qos.
 * <p />
 * Keep this class slim, it is serialized and passed with RMI
 * <p />
 * The constructor arguments are checked to be not null and corrected
 * to "" or 'new byte[0]' if they are null
 */
public final class MsgUnitRaw implements java.io.Serializable // Is serializable for RMI calls, but should not for J2ME!?
{
   private transient static final byte[] EMPTY_BYTEARR = new byte[0];
   //private transient static final String EMPTY_STRING = "";
   private transient final Object msgUnit; // transport temporary the parsed instance MsgUnit as well
   private final byte[] qos;
   private final byte[] key;
   private final byte[] content; // One of 'this.content' or 'this.encodedContent' is allways null
   private final EncodableData encodedContent;
   public static final String KEY_TAG = "key";
   public static final String CONTENT_TAG = "content";
   public static final String QOS_TAG = "qos";

   /**
    * @param msgUnit Temporary object with parsed information, this is not evaluated internally
    * @param key
    * @param content
    * @param qos
    */
   public MsgUnitRaw(Object msgUnit, byte[] key, byte[] content, byte[] qos) {
      this.msgUnit = msgUnit;
      this.qos = (qos == null) ? EMPTY_BYTEARR : qos;
      this.key = (key == null) ? EMPTY_BYTEARR : key;
      this.encodedContent = null;
      this.content = (content == null) ? EMPTY_BYTEARR : content;
   }

   /**
    */
   public MsgUnitRaw(String key, byte[] content, String qos) {
      this(null, Constants.toUtf8Bytes(key), content, Constants.toUtf8Bytes(qos));
   }

   public MsgUnitRaw(byte[] key, EncodableData encodedContent, byte[] qos) {
      this.msgUnit = null;
      this.qos = (qos == null) ? EMPTY_BYTEARR : qos;
      this.key = (key == null) ? EMPTY_BYTEARR : key;
      this.encodedContent = encodedContent;
      if (this.encodedContent == null)
         this.content = EMPTY_BYTEARR;
      else
         this.content = null;
   }

   /**
    * The raw XML string, never null
    */
   public String getKey() {
      return Constants.toUtf8String(this.key);
   }

   public byte[] getKeyBytes() {
      return this.key;
   }

   /**
    * Get the raw content, never null
    */
   public byte[] getContent() {
      if (this.encodedContent != null)
         return this.encodedContent.getBlobValue();
      return this.content;
   }

   /**
    * Get the raw content, never null
    */
   public String getContentStr() {
      return new String(getContent());
   }

   /**
    * The raw QoS XML string, never null
    */
   public String getQos() {
      return Constants.toUtf8String(this.qos);
   }

   /**
    * The raw QoS XML string, never null
    */
   public byte[] getQosBytes() {
      return this.qos;
   }

   /**
    * The number of bytes of qos+key+content
    */
   public long size() {
      if (this.encodedContent != null)
         return this.qos.length + this.key.length + this.encodedContent.getSize();
      else
         return this.qos.length + this.key.length + this.content.length;
   }

   /**
    * You can decide to pass with the constructor a parsed MsgUnit
    * @return MsgUnit with holds the parsed information, please treat as immutable, or null
    */
   public Object getMsgUnit() {
      return this.msgUnit;
   }

   public String toXml(String contentCharset) {
      return toXml((String)null, contentCharset);
   }

   /**
    * @param extraOffset
    * @param contentCharset TODO
    * @return
    */
   public String toXml(String extraOffset, String contentCharset) {
      StringBuffer sb = new StringBuffer();
      String offset = "\n";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
      sb.append(offset).append("<MsgUnitRaw>");
      try {
         toXml(extraOffset, out, (Properties)null, contentCharset);
      } catch (IOException e) {
         e.printStackTrace();
      }
      sb.append(offset).append("</MsgUnitRaw>\n");
      sb.append(out.toByteArray());
      return sb.toString();
      /*
      StringBuffer sb = new StringBuffer();
      String offset = "\n";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<MsgUnitRaw>");
      sb.append(this.key);
      if (this.encodedContent != null)
         sb.append(offset).append(this.encodedContent.toXml(extraOffset, CONTENT_TAG));
      else
         sb.append(offset).append(" <content><![CDATA[").append(new String(this.content)).append("]]></content>");
      sb.append(this.qos);
      sb.append(offset).append("</MsgUnitRaw>\n");

      return sb.toString();
      */
   }

   /**
    * Standard message dump.
    * Used for logging and XmlScripting
    * @param extraOffset
    * @param out
    * @param contentCharset TODO
    * @throws IOException
    */
   public void toXml(String extraOffset, OutputStream out, Properties props, String contentCharset) throws IOException {
      boolean forceReadable = (props!=null && props.containsKey(Constants.TOXML_FORCEREADABLE)) ?
            (Boolean.valueOf(props.getProperty(Constants.TOXML_FORCEREADABLE)).booleanValue()) : false;
            
      boolean inhibitContentCDATAWrapper = (props!=null && props.containsKey(Constants.INHIBIT_CONTENT_CDATA_WRAPPING)) ?
         (Boolean.valueOf(props.getProperty(Constants.INHIBIT_CONTENT_CDATA_WRAPPING)).booleanValue()) : false;
           
      String offsetStr = "\n";
      if (extraOffset == null) extraOffset = "";
      offsetStr += extraOffset;
      byte[] offset = Constants.toUtf8Bytes(offsetStr);
      if (this.qos.length > 0) {
         out.write(offset);
         out.write(qos);
      }
      if (this.key.length > 0) {
         out.write(offset);
         out.write(key);
      }

      if (this.content == null && this.encodedContent != null && this.encodedContent.getSize() == 0 ||
          this.content != null && this.content.length == 0) {
         return;
      }

      if (this.encodedContent != null) {
         out.write(this.encodedContent.toXml(extraOffset, MsgUnitRaw.CONTENT_TAG, forceReadable).getBytes());
         return;
      }

      dumpContent(extraOffset, out, this.content, forceReadable, inhibitContentCDATAWrapper, contentCharset);
   }

   public static void dumpContent(String extraOffset, OutputStream out, byte[] content, boolean forceReadable, String charset) throws IOException {
      dumpContent(extraOffset, out, content, forceReadable, false, charset);
   }
   
   public static void dumpContent(String extraOffset, OutputStream out, byte[] content, boolean forceReadable, boolean inhibitContentCDATAWrapper, String contentCharset) throws IOException {

      // TODO: Potential charset problem when not Base64 protected
      boolean doEncode = false;
      int len = content.length - 2;
      for (int i=0; i<content.length; i++) {
         if (i < len && content[i] == (byte)']' && content[i+1] == (byte)']' && content[i+2] == (byte)'>') {
            doEncode = true;
            break;
         }
         if (content[i] == 0) { // avoid zeros
            doEncode = true;
            break;
         }
      }

      // Needs to be parseable by XmlScriptInterpreter
      if (doEncode) {
         // link=''?  name=null, size=1000L type=Constants.TYPE_BLOB encoding=Constants.ENCODING_BASE64
         EncodableData data = new EncodableData(MsgUnitRaw.CONTENT_TAG, null, content);
         String contentXml = data.toXml(extraOffset, MsgUnitRaw.CONTENT_TAG, forceReadable, inhibitContentCDATAWrapper);
         out.write(contentXml.getBytes());
      }
      else {
         String name = null;
         EncodableData data = new EncodableData(MsgUnitRaw.CONTENT_TAG,
               name,
               Constants.TYPE_STRING,
               Constants.ENCODING_NONE,
               Constants.toEncodedString(content, contentCharset)); 
         String contentXml = data.toXml(extraOffset, MsgUnitRaw.CONTENT_TAG, forceReadable, inhibitContentCDATAWrapper);
         out.write(Constants.toUtf8Bytes(contentXml));
      }
   }
}
