/*------------------------------------------------------------------------------
Name:      MsgUnitRaw.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

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
      sb.append(offset).append(" <content>").append(new String(this.content)).append("</content>");
      sb.append(this.qos);
      sb.append(offset).append("</MsgUnitRaw>\n");

      return sb.toString();
   }
}
