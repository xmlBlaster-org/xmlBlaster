/*------------------------------------------------------------------------------
Name:      MsgKeyData.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.key;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.engine.helper.Constants;

/**
 * This class encapsulates the Message meta data and unique identifier (key)
 * of a publish()/update() or get()-return message.
 * <p />
 * A typical key could look like this:<br />
 * <pre>
 *     &lt;key oid='4711' contentMime='text/xml'>
 *        &lt;AGENT id='192.168.124.20' subId='1' type='generic'>
 *           &lt;DRIVER id='FileProof' pollingFreq='10'>
 *           &lt;/DRIVER>
 *        &lt;/AGENT>
 *     &lt;/key>
 * </pre>
 * <br />
 * Note that the AGENT and DRIVER tags are application know how, which you have
 * to supply to the setClientTags() method.<br />
 * A well designed xml hierarchy of your problem domain is essential for a proper working xmlBlaster
 * <p />
 * <p>
 * NOTE: Message oid starting with "__" is reserved for internal usage.
 * </p>
 * <p>
 * NOTE: Message oid starting with "_" is reserved for xmlBlaster plugins.
 * </p>
 * @see org.xmlBlaster.util.key.MsgKeySaxFactory
 */
public final class MsgKeyData
{
   private final static String ME = "MsgKeyData";
   private final Global glob;
   private transient final I_MsgKeyFactory factory;
   private final String serialData; // can be null - in this case use toXml()
   /** value from attribute <key oid="..."> */
   private String oid;
   /** The default content MIME type is "text/plain" */
   public static final String CONTENTMIME_DEFAULT = QueryKeyData.CONTENTMIME_DEFAULT;
   /** value from attribute <key oid="" contentMime="..."> */
   private String contentMime = "text/plain";
   /** value from attribute <key oid="" contentMimeExtended="..."> */
   private String contentMimeExtended;
   /** value from attribute <key oid="" domain="..."> */
   private String domain;
   private String clientTags;

   /**
    * Minimal constructor.
    */
   public MsgKeyData(Global glob) {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.factory = glob.getMsgKeyFactory();
      this.serialData = null; // toXml() ?
   }

   /**
    * Constructor to parse a message. 
    * @param factory If null, the default factory from Global is used.
    */
   public MsgKeyData(Global glob, I_MsgKeyFactory factory, String serialData) {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.factory = (factory == null) ? this.glob.getMsgKeyFactory() : factory;
      this.serialData = serialData;
   }

   public void setOid(String oid) {
      this.oid = oid;
   }

   public String getOid() {
      return this.oid;
   }

   /**
    * Test if oid is '__sys__deadMessage'. 
    * <p />
    * Dead letters are unrecoverable lost messages, usually an administrator
    * should subscribe to those messages.
    * <p>
    * This is an internal message (isInternal() returns true)
    * </p>
    */
   public final boolean isDeadMessage() {
      return Constants.OID_DEAD_LETTER.equals(getOid());
   }

   /**
    * Messages starting with "_" are reserved for usage in plugins
    */
   public final boolean isPluginInternal() {
      return (this.oid == null) ? false : (getOid().startsWith(Constants.INTERNAL_OID_PREFIX_FOR_PLUGINS) && !getOid().startsWith(Constants.INTERNAL_OID_PREFIX_FOR_CORE));
   }

   /**
    * Messages starting with "__" are reserved for internal usage
    */
   public final boolean isInternal() {
      return (this.oid == null) ? false : getOid().startsWith(Constants.INTERNAL_OID_PREFIX_FOR_CORE);
   }

   public void setContentMime(String contentMime) {
      this.contentMime = contentMime;
   }

   public String getContentMime() {
      return this.contentMime;
   }

   public void setContentMimeExtended(String contentMimeExtended) {
      this.contentMimeExtended = contentMimeExtended;
   }

   public String getContentMimeExtended() {
      return this.contentMimeExtended;
   }

   public void setDomain(String domain) {
      this.domain = domain;
   }

   /**
    * Access the domain setting
    * @return A domain string or null
    */
   public String getDomain() {
      return this.domain;
   }

   /**
    * Set client specific meta inforamtions. 
    * <p />
    * May be used to integrate your application tags, for example:
    * <p />
    * <pre>
    *&lt;key oid='4711' contentMime='text/xml'>
    *   &lt;AGENT id='192.168.124.20' subId='1' type='generic'>
    *      &lt;DRIVER id='FileProof' pollingFreq='10'>
    *      &lt;/DRIVER>
    *   &lt;/AGENT>
    *&lt;/key>
    * </pre>
    * @param str Your tags in ASCII XML syntax
    */
   public void setClientTags(String tags) {
      this.clientTags = tags;
   }

   public String getClientTags() {
      return this.clientTags;
   }

   /**
    * Converts the data in XML ASCII string.
    * @return An XML ASCII string
    */
   public String toString() {
      return toXml();
   }

   /**
    * Converts the data in XML ASCII string.
    * @return An XML ASCII string
    */
   public String toXml() {
      return this.factory.writeObject(this, null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the query as a XML ASCII string
    */
   public String toXml(String extraOffset) {
      return factory.writeObject(this, extraOffset);
   }
}
