/*------------------------------------------------------------------------------
Name:      MessageUnit.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.helper;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.PublishQos;

/**
 * Encapsulates the xmlKey, content and qos. 
 * <p />
 * The data is NOT interpreted or parsed in the container, e.g.
 * qos contains the literal, XML based ASCII string.
 * <p />
 * Keep this class slim, it is serialized and passed with RMI
 * <p />
 * The constructor arguments are checked to be not null and corrected
 * to "" or 'new byte[0]' if they are null
 */
public final class MessageUnit implements java.io.Serializable
{
   private String xmlKey;
   private byte[] content;
   private String qos;

   private transient Global glob;
   private transient MsgQosData msgQosData; // cache
   private transient MsgKeyData msgKeyData; // cache

   /**
    * The normal constructor. 
    */
   public MessageUnit(String xmlKey, byte[] content, String qos) {
      this((Global)null, xmlKey, content, qos);
   }

   /**
    * The normal constructor. 
    */
   public MessageUnit(Global glob, String xmlKey, byte[] content, String qos) {
      this.glob = (glob == null) ? Global.instance() : glob;
      setKey(xmlKey);
      setContent(content);
      setQos(qos);
   }

   /**
    * This is a temporary constructor used for the javascript (rhino) client
    */
   public MessageUnit(String xmlKey, String contentAsString, String qos) {
      this(null, xmlKey, contentAsString, qos);
   }

   /**
    * This is a temporary constructor used for the javascript (rhino) client
    */
   public MessageUnit(Global glob, String xmlKey, String contentAsString, String qos) {
      this.glob = (glob == null) ? Global.instance() : glob;
      setKey(xmlKey);
      setContent(contentAsString.getBytes());
      setQos(qos);
   }

   /**
    * This is a constructor suitable for clients. 
    */
   public MessageUnit(Global glob, PublishKey key, String contentAsString, PublishQos qos) {
      this(glob, key, contentAsString.getBytes(), qos);
   }

   /**
    * This is a constructor suitable for clients. 
    */
   public MessageUnit(Global glob, PublishKey key, byte[] content, PublishQos qos) {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.msgKeyData = key.getData();
      this.msgQosData = qos.getData();
      setKey(key.toXml());
      setContent(content);
      setQos(qos.toXml());
   }

   /**
    * Shallow clone this message unit.
    * <p>
    *  In order to keep up with performance we don't encapsulate the content in an immutable object.
    *  Keep in mind however that you should never (ever) change the content of a MessageUnit since
    *  such a change would affect the messages to all other reference of this message (e.g. subscribers)
    *  and therefore it might lead to unpredictable (and undesired) results.
    * </p>
    * <p>Example:</p>
    * <pre> 
    * byte[] content = msgUnit.getContent();
    * content[6] = (byte)'A';  // NOT ALLOWED !
    * </pre> 
    * @parameter content  If you pass null note that the byte[] is a reference
    *                     to the original and you should not manipulate it
    */
   public MessageUnit(MessageUnit old, String xmlKey, byte[] content, String qos) {
      glob = old.getGlobal();
      setKey( (xmlKey==null) ? old.getKey() : xmlKey);
      setContent( (content==null) ? old.getContent() : content);
      setQos( (qos==null) ? old.getQos() : qos);
   }

   Global getGlobal() {
      return glob;
   }

   public void setGlobal(Global glob) {
      this.glob = glob;
   }

   private final void setKey(String xmlKey){
      if (xmlKey == null)
         this.xmlKey = "";
      else
         this.xmlKey = xmlKey;
   }

   private final void setQos(String qos){
      if (qos == null)
         this.qos = "";
      else
         this.qos = qos;
   }

   private final void setContent(byte[] content) {
      if (content == null)
         this.content = new byte[0];
      else
         this.content = content;
   }

   /**
    * The raw string
    */
   public final String getKey() {
      return this.xmlKey;
   }

   /**
    * @deprecated use getKey()
    */
   public final String getXmlKey() {
      return getKey();
   }

   /**
    * Get the raw content. 
    * <p>
    * For performance reasons you get a reference to the internal byte[] buffer and no copy.
    * Note that you are not allowed to manipulate the returned byte[].
    * </p>
    */
   public final byte[] getContent() {
      return this.content;
   }

   public final String getContentStr() {
      return new String(this.content);
   }

   /**
    * The QoS
    */
   public final String getQos() {
      return this.qos;
   }

   public MsgKeyData getMsgKeyData() {
      if (this.msgKeyData == null) {
         this.msgKeyData = new MsgKeyData(glob, (org.xmlBlaster.util.key.I_MsgKeyFactory)null, this.xmlKey);
      }
      return this.msgKeyData;
   }

   public MsgQosData getMsgQosData() {
      if (this.msgQosData == null) {
         this.msgQosData = new MsgQosData(glob, this.qos);
      }
      return this.msgQosData;
   }

   /**
    * Deep clone this message unit. 
    * <p />
    * You can't manipulate the data in the original MessageUnit
    * The content byte[] is new allocated (a copy)
    */
   public final MessageUnit getClone() {
      byte[] newContent = new byte[content.length];
      System.arraycopy(content, 0,  newContent, 0, content.length);
      return new MessageUnit(glob, xmlKey, content, qos);
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
      String offset = "\n";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      //sb.append(offset).append("<MessageUnit>");
      sb.append(offset).append(xmlKey);
      sb.append(offset).append("  <content>").append(new String(content)).append("</content>");
      sb.append(offset).append(qos);
      //sb.append(offset).append("</MessageUnit>\n");

      return sb.toString();
   }
}
