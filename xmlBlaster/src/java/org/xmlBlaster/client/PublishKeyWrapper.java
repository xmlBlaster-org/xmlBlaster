/*------------------------------------------------------------------------------
Name:      PublishKeyWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.key.MsgKeyData;

/**
 * @deprecated Use org.xmlBlaster.client.key.PublishKey instead
 */
public class PublishKeyWrapper
{
   private final static String ME = "PublishKeyWrapper";
   private final MsgKeyData msgKeyData;

   /**
    * Constructor with given oid and contentMime.
    * @param oid is optional and will be generated if ""
    * @param contentMime the MIME type of the content e.g. "text/xml" or "image/gif"
    */
   public PublishKeyWrapper(String oid, String contentMime) {
      this(oid, contentMime, null);
   }

   /**
    * Constructor with given oid and contentMime.
    * @param oid is optional and will be generated if ""
    * @param contentMime the MIME type of the content e.g. "text/xml" or "image/gif"
    * @param contentMimeExtended Use it for whatever, e.g. the version number or parser infos for your content<br />
    *        set to null if not needed
    */
   public PublishKeyWrapper(String oid, String contentMime, String contentMimeExtended) {
      this(oid, contentMime, contentMimeExtended, null);
   }

   /**
    * Constructor with given oid and contentMime.
    * @param oid is optional and will be generated if ""
    * @param contentMime the MIME type of the content e.g. "text/xml" or "image/gif"
    * @param contentMimeExtended Use it for whatever, e.g. the version number or parser infos for your content<br />
    *        set to null if not needed
    */
   public PublishKeyWrapper(String oid, String contentMime, String contentMimeExtended, String domain) {
      this.msgKeyData = new MsgKeyData(Global.instance());
      this.msgKeyData.setOid(oid);
      this.msgKeyData.setContentMime(contentMime);
      this.msgKeyData.setContentMimeExtended(contentMimeExtended);
      this.msgKeyData.setDomain(domain);
   }

   public MsgKeyData getData() {
      return this.msgKeyData;
   }

   /**
    * Access the $lt;key oid="...">.
    * @return The unique key oid
    */
   public final String getOid() {
      return this.msgKeyData.getOid();
   }

   /**
    * Set the $lt;key oid="...">.
    * @param The unique key oid
    */
   public final void setOid(String oid) {
      this.msgKeyData.setOid(oid);
   }

   public void setDomain(String domain) {
      this.msgKeyData.setDomain(domain);
   }

   /**
    * Access the domain setting
    * @return A domain string or null
    */
   public String getDomain() {
      return this.msgKeyData.getDomain();
   }

   /**
    * Converts the data in XML ASCII string.
    * @return An XML ASCII string
    */
   public String toString() {
      return this.msgKeyData.toString();
   }

   /**
    * Converts the data in XML ASCII string.
    * @return An XML ASCII string
    */
   public String toXml() {
      return this.msgKeyData.toXml();
   }

   /**
    * May be used to integrate your application tags.
    * <p />
    * Derive your special PublishKey class from this.
    * @param str Your tags in ASCII XML syntax
    */
   public String wrap(String str) {
      this.msgKeyData.setClientTags(str);
      return this.msgKeyData.toXml();
   }
}
