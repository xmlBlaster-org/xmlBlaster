/*------------------------------------------------------------------------------
Name:      MsgUnit.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.qos.QosData;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.key.KeyData;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.util.enum.Constants;

/**
 * Encapsulates the parsed xmlKey and QoS, and the raw content. 
 * <p />
 * The constructor arguments are checked to be not null and corrected
 * to "" or 'new byte[0]' if they are null
 */
public final class MsgUnit implements java.io.Serializable
{
   private transient static final byte[] EMPTY_BYTEARR = new byte[0];
   private transient Global glob;
   private QosData qosData;
   private byte[] content;
   private KeyData keyData;

   /**
    * Uses the default global and assumes a PUBLISH. 
    * @see #MsgUnit(Global, String, byte[], String, MethodName)
    */
   public MsgUnit(String key, byte[] content, String qos) throws XmlBlasterException {
      this((Global)null, key, content, qos);
   }

   /**
    * Assumes a PUBLISH. 
    * @see #MsgUnit(Global, String, byte[], String, MethodName)
    */
   public MsgUnit(Global glob, String key, byte[] content, String qos) throws XmlBlasterException {
      this(glob, key, content, qos, MethodName.PUBLISH);
   }

   /**
    * @see #MsgUnit(Global, String, byte[], String, MethodName)
    */
   public MsgUnit(Global glob, MsgUnitRaw msgUnitRaw, MethodName methodName) throws XmlBlasterException {
      this(glob, msgUnitRaw.getKey(), msgUnitRaw.getContent(), msgUnitRaw.getQos(), methodName);
   }

   /**
    * The normal constructor. 
    * @param glob The specific Global handle
    * @param key The XML key
    * @param qos The XML qos
    * @param methodName The method you want to invoked (like PUBLISH or SUBSCRIBE)
    */
   public MsgUnit(Global glob, String key, byte[] content, String qos, MethodName methodName) throws XmlBlasterException {
      this.glob = (glob == null) ? Global.instance() : glob;
      setContent(content);
      if (methodName == MethodName.PUBLISH) {
         // The proper way, but then we need to import package engine.qos
         //PublishQosServer qos = new PublishQosServer(glob, qos); // sets timestamp etc.
         //this.qosData = qos.getData();
         this.qosData = this.glob.getMsgQosFactory().readObject(qos);
         //boolean fromPersistenceStore = false;
         //if (!fromPersistenceStore)
         //   this.qosData.touchRcvTimestamp();
         this.keyData = this.glob.getMsgKeyFactory().readObject(key);
      }
      else if (methodName == MethodName.UPDATE) {
         this.qosData = this.glob.getMsgQosFactory().readObject(qos);
         this.keyData = this.glob.getMsgKeyFactory().readObject(key);
      }
      else if (methodName == MethodName.SUBSCRIBE || methodName == MethodName.UNSUBSCRIBE ||
               methodName == MethodName.GET || methodName == MethodName.ERASE) {
         this.qosData = this.glob.getQueryQosFactory().readObject(qos);
         this.keyData = this.glob.getQueryKeyFactory().readObject(key);
      }
      else {
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, "MsgUnit", "Sorry method support for '" + methodName.toString() + "' is missing");
      }
   }

   /**
    * Uses the default global and assumes a PUBLISH. 
    * @see #MsgUnit(Global, String, byte[], String, MethodName)
    */
   public MsgUnit(String key, String contentAsString, String qos) throws XmlBlasterException {
      this(null, key, contentAsString, qos);
   }

   /**
    * Assumes a PUBLISH. 
    * @see #MsgUnit(Global, String, byte[], String, MethodName)
    */
   public MsgUnit(Global glob, String key, String contentAsString, String qos) throws XmlBlasterException {
      this(glob, key, contentAsString.getBytes(), qos, MethodName.PUBLISH);
   }

   /**
    * This is a constructor suitable for clients. 
    */
   public MsgUnit(PublishKey key, String contentAsString, PublishQos qos) {
      this(key, contentAsString.getBytes(), qos);
   }

   /**
    * This is a constructor suitable for clients. 
    */
   public MsgUnit(PublishKey key, byte[] content, PublishQos qos) {
      this(key.getData(), content, qos.getData());
   }

   /**
    */
   public MsgUnit(KeyData key, byte[] content, QosData qos) {
      if (key == null && qos == null) {
         Thread.currentThread().dumpStack();
         throw new IllegalArgumentException("MsgUnit constructor with key=="+key+" AND qos="+qos+" is invalid");
      }
      this.glob = (key == null) ? qos.getGlobal() : key.getGlobal();
      this.keyData = key;
      this.qosData = qos;
      setContent(content);
   }

   /**
    * Clone this message unit (but not the content). 
    * <p>
    *  In order to keep up with performance we don't encapsulate the content in an immutable object.
    *  Keep in mind however that you should never (ever) change the content of a MsgUnit since
    *  such a change would affect the messages to all other reference of this message (e.g. subscribers)
    *  and therefore it might lead to unpredictable (and undesired) results.
    * </p>
    * <p>Example:</p>
    * <pre> 
    * byte[] content = msgUnit.getContent();
    * content[6] = (byte)'A';  // NOT ALLOWED !
    * </pre> 
    * @param old      The MsgUnit to clone
    * @param key      The new key to use, if you pass 'null' the old is shallow cloned
    * @param content  If you pass null note that the byte[] is a reference
    *                     to the original and you should not manipulate it
    * @param qos      The new qos to use, if you pass 'null' the old is shallow cloned
    */
   public MsgUnit(MsgUnit old, KeyData key, byte[] content, QosData qos) {
      glob = old.getGlobal();
      this.qosData = (qos==null) ? (QosData)old.getQosData().clone() : qos;
      this.keyData = (key==null) ? (KeyData)old.getKeyData().clone() : key;
      setContent( (content==null) ? old.getContent() : content);
   }

  /*
   public MsgUnit(MsgUnit old, MsgKeyData key, byte[] content, MsgQosData qos) {
      glob = old.getGlobal();
      this.qosData = (qos==null) ? (QosData)old.getQosData().clone() : qos;
      this.keyData = (key==null) ? (KeyData)old.getKeyData().clone() : key;
      setContent( (content==null) ? old.getContent() : content);
   }
   */

   public Global getGlobal() {
      return glob;
   }

   public void setGlobal(Global glob) {
      this.glob = glob;
      this.keyData.setGlobal(this.glob);
      this.qosData.setGlobal(this.glob);
   }

   /**
    * Used internally and my be used by publish mime plugin
    */
   public void setContent(byte[] content) {
      if (content == null)
         this.content = EMPTY_BYTEARR;
      else
         this.content = content;
   }

   /**
    * The key oid, can be null if not a PUBLISH or UPDATE
    */
   public String getKeyOid() {
      if (this.keyData != null) {
         return this.keyData.getOid();
      }
      return null;
   }

   /**
    * The raw XML string for PUBLISH/UPDATE never null, otherwise it may be null
    */
   public String getKey() {
      if (this.keyData != null) {
         return this.keyData.toXml();
      }
      return null;
   }

   /**
    * Get the raw content. 
    * <p>
    * For performance reasons you get a reference to the internal byte[] buffer and no copy.
    * Note that you are not allowed to manipulate the returned byte[].
    * </p>
    */
   public byte[] getContent() {
      return this.content;
   }

   public String getContentStr() {
      return new String(this.content);
   }

   /**
    * The QoS XML string
    */
   public String getQos() {
      return this.qosData.toXml();
   }

   public KeyData getKeyData() {
      return this.keyData;
   }

   public QosData getQosData() {
      return this.qosData;
   }

   public String getLogId() {
      return getKeyOid() + "/" + getQosData().getRcvTimestamp();
   }

   /**
    * @return null if not known
    */
   public String getContentMime() {
      return (this.keyData != null) ? this.keyData.getContentMime() : null;
   }

   /**
    * @return null if not known
    */
   public String getContentMimeExtended() {
      return (this.keyData != null) ? this.keyData.getContentMimeExtended() : null;
   }

   /**
    * @return null if not known
    */
   public String getDomain() {
      return (this.keyData != null) ? this.keyData.getDomain() : null;
   }

   /**
    * Deep clone this message unit. 
    * <p />
    * The content byte[] is new allocated (a copy), the key an qos objects
    * are shallow cloned (the String, int etc are clones there)
    */
   public MsgUnit getClone() {
      byte[] newContent = new byte[content.length];
      System.arraycopy(this.content, 0,  newContent, 0, this.content.length);
      return new MsgUnit((KeyData)this.keyData.clone(), content, (QosData)this.qosData.clone());
   }

   public MsgUnitRaw getMsgUnitRaw() {
      return new MsgUnitRaw(this,
                            (this.keyData == null) ? null : this.keyData.toXml(),
                            this.content,
                            (this.qosData == null) ? null : this.qosData.toXml());
   }

   /**
    * Compares bytes if the given content is identical to the
    * internal content
    * @return true content is identical
    */
   public boolean sameContent(byte[] newContent) {
      if (newContent == null) {
         if (this.content.length < 1)
            return true;
         return false;
      }
      if (this.content.length != newContent.length)
         return false;
      for (int ii=0; ii<newContent.length; ii++)
         if (this.content[ii] != newContent[ii])
            return false;
      return true;
   }

   /** 
    * The number of bytes of qos+key+content
    */
   public long size() {
      //glob.getLog("core").info("MsgUnit", this.qosData.toXml() + "qosSize=" + this.qosData.size() + " keySize=" + this.keyData.size() + " contentSize=" + this.content.length + this.qosData.toXml());
      return this.qosData.size() + this.keyData.size() + this.content.length;
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return The data of this MsgUnit as a XML ASCII string
    */
   public String toXml() {
      return toXml((String)null, -1);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return The data of this MsgUnit as a XML ASCII string
    */
   public String toXml(String extraOffset) {
      return toXml(extraOffset, -1);
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

      //sb.append(offset).append("<MsgUnit>");
      sb.append(this.keyData.toXml(extraOffset+Constants.INDENT));
      if (maxContentLen < 0 || this.content.length < maxContentLen) {
         sb.append(offset).append("  <content><![CDATA[").append(new String(this.content)).append("]]></content>");
      }
      else if (maxContentLen > 0) {
         sb.append(offset).append("  <content size='").append(content.length).append("'><![CDATA[");
         sb.append(new String(this.content, 0, maxContentLen)).append("...]]></content>");
      }
      sb.append(this.qosData.toXml(extraOffset+Constants.INDENT));
      //sb.append(offset).append("</MsgUnit>\n");

      return sb.toString();
   }
}
