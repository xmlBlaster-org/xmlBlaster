/*------------------------------------------------------------------------------
Name:      KeyData.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.key;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.def.Constants;

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
public abstract class KeyData implements java.io.Serializable, Cloneable
{
   protected transient Global glob;
   protected transient final String serialData; // can be null - in this case use toXml() - original without generated oid
   /** value from attribute <key oid="..."> */
   private String oid;
   /** The default content MIME type is "text/plain" */
   public transient static final String CONTENTMIME_DEFAULT = "text/plain";
   /** value from attribute <key oid="" contentMime="..."> */
   private String contentMime = CONTENTMIME_DEFAULT;
   /** The default content MIME extended type is null */
   public transient static final String CONTENTMIMEEXTENDED_DEFAULT = null;
   /** value from attribute <key oid="" contentMimeExtended="..."> */
   private String contentMimeExtended = CONTENTMIMEEXTENDED_DEFAULT;
   /** is "" */
   public transient static final String DEFAULT_DOMAIN = null;
   /** value from attribute <key oid="" domain="..."> */
   private String domain = DEFAULT_DOMAIN;
   /** The default queryType is "EXACT" */
   public transient static final String QUERYTYPE_DEFAULT = Constants.EXACT;
   /** The query type */
   protected String queryType = QUERYTYPE_DEFAULT;
   /** The query string */
   protected String queryString;

   /**
    * Minimal constructor.
    */
   public KeyData(Global glob, String serialData) {
      setGlobal(glob);
      this.serialData = serialData;
   }

   /**
    * Sets the global object (used when deserializing the object)
    */
   public void setGlobal(Global glob) {
      this.glob = (glob == null) ? Global.instance() : glob;

   }

   public void setOid(String oid) {
      this.oid = oid;
   }

   /**
    *  @return The key oid or null if not set (see MsgKeyData.getOid() which generates the oid if it was null).
    */
   public String getOid() {
      return this.oid;
   }

   public boolean hasOid() {
      return this.oid != null;
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
      return Constants.OID_DEAD_LETTER.equals(this.oid);
   }

   /**
    * __sys__remoteProperties
    * @return
    */
   public final boolean isRemoteProperties() {
      return Constants.INTERNAL_OID_REMOTE_PROPERTIES.equals(this.oid);
   }

   /**
    * Messages starting with "_" are reserved for usage in plugins
    */
   public final boolean isPluginInternal() {
      return (this.oid == null) ? false : (this.oid.startsWith(Constants.INTERNAL_OID_PREFIX_FOR_PLUGINS) && !this.oid.startsWith(Constants.INTERNAL_OID_PREFIX_FOR_CORE));
   }

   /**
    * Messages starting with "__" are reserved for internal usage
    */
   public final boolean isInternal() {
      return (this.oid == null) ? false : this.oid.startsWith(Constants.INTERNAL_OID_PREFIX_FOR_CORE);
   }

   /**
    * Messages starting with "__cmd:" are administrative messages
    */
   public final boolean isAdministrative() {
      return (this.oid == null) ? false : this.oid.startsWith(Constants.INTERNAL_OID_ADMIN_CMD);
   }

   /**
    * Set mime type (syntax) of the message content. 
    * @return The MIME type, for example "text/xml" in &lt;key oid='' contentMime='text/xml'><br />
    *         default is "text/plain" if not set
    * @see <a href="ftp://ftp.std.com/customers3/src/mail/imap-3.3/RFC1521.TXT">RFC1521 - MIME (Multipurpose Internet Mail Extensions)</a>
    */
   public void setContentMime(String contentMime) {
      this.contentMime = contentMime;
   }

   /**
    * Find out which mime type (syntax) the content of the message has.
    * @return The MIME type, for example "text/xml" in &lt;key oid='' contentMime='text/xml'><br />
    *         default is "text/plain" if not set
    * @see <a href="ftp://ftp.std.com/customers3/src/mail/imap-3.3/RFC1521.TXT">RFC1521 - MIME (Multipurpose Internet Mail Extensions)</a>
    */
   public String getContentMime() {
      return this.contentMime;
   }

   /**
    * Some further specifying information of the content.
    * <p />
    * For example the application version number the document in the content.<br />
    * You may use this attribute for you own purposes.
    * @param The MIME-extended info, for example<br />
    *         "Version 1.1" in &lt;key oid='' contentMime='text/xml' contentMimeExtended='Version 1.1'><br />
    *         or "" (empty string) if not known
    */
   public void setContentMimeExtended(String contentMimeExtended) {
      this.contentMimeExtended = contentMimeExtended;
   }

   /**
    * Some further specifying information of the content.
    * <p />
    * For example the application version number the document in the content.<br />
    * You may use this attribute for you own purposes.
    * @return The MIME-extended info, for example<br />
    *         "Version 1.1" in &lt;key oid='' contentMime='text/xml' contentMimeExtended='Version 1.1'><br />
    *         or "" (empty string) if not known
    */
   public String getContentMimeExtended() {
      return this.contentMimeExtended;
   }

   /**
    * Set the domain for this message, can be used for a simple grouping of
    * messages to their master node with xmlBlaster clusters. 
    * @param The domain, any chosen string in your problem domain, e.g. "RUGBY" or "RADAR_TRACK"
    *         defaults to "" where the local xmlBlaster instance is the master of the message.
    * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/cluster.html">The cluster requirement</a>
    */
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
    * @return true if no domain is given (null or empty string). 
    */
   public final boolean isDefaultDomain() {
      if (domain == null || domain.equals(DEFAULT_DOMAIN))
         return true;
      return false;
   }

   /**
    * Access the query type "XPATH" or "EXACT"
    * @return A queryType string or null
    */
   public String getQueryType() {
      return this.queryType;
   }

   public void setQueryType(String queryType)  throws XmlBlasterException {
      this.queryType = queryType;
   }

   /**
    * Access the query string like "//key"
    * @return A query string or null
    */
   public String getQueryString() {
      return this.queryString;
   }

   public boolean isExact() {
      return Constants.EXACT.equals(this.queryType);
   }

   public boolean isQuery() {
      return Constants.XPATH.equals(this.queryType) || 
             Constants.REGEX.equals(this.queryType);
   }

   public boolean isXPath() {
      return Constants.XPATH.equals(this.queryType);
   }

   public boolean isDomain() {
      return Constants.DOMAIN.equals(this.queryType);
   }

   /**
    * Check if same query is used
    */
   public boolean equals(KeyData other) {
      return isExact() && other.isExact() && this.oid != null && this.oid.equals(other.getOid()) ||
             isQuery() && other.isQuery() && this.queryString != null && this.queryString.trim().equals(other.getQueryString().trim()) ||
             isDomain() && other.isDomain() && this.domain != null && this.domain.trim().equals(other.getDomain().trim());
   }

   /**
    * The size in bytes of the data in XML form. 
    */
   public int size() {
      return toXml().length();
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
   public abstract String toXml();

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the query as a XML ASCII string
    */
   public abstract String toXml(String extraOffset);

   /**
    * Generates a unique key oid in scope of a cluster node (on server or on client side).
    * @param glob.getStrippedId() on server side
    */
   public String generateOid(String uniquePrefix) {
      StringBuffer oid = new StringBuffer(80);
      Timestamp timestamp = new Timestamp();
      oid.append(uniquePrefix).append("-").append(timestamp.getTimestamp());
      return oid.toString();
   }
   
   public final Global getGlobal() {
      return this.glob;
   }

   /**
    * Returns a shallow clone, you can change savely all basic or immutable types
    * like boolean, String, int.
    * Currently RouteInfo is not cloned (so don't change it)
    */
   public Object clone() {
      try {
         return super.clone();
      }
      catch (CloneNotSupportedException e) {
         return null;
      }
   }
}
