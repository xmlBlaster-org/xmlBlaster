/*------------------------------------------------------------------------------
Name:      PublishKey.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.key;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * Wrap the XML key data for publish() invocations.
 * <p>
 * See MsgKeySaxFactory for a syntax description of the allowed xml structure
 * </p>
 * @see org.xmlBlaster.util.key.MsgKeySaxFactory
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html" target="others">the interface.publish requirement</a>
 */
public class PublishKey
{
   private final static String ME = "PublishKey";
   private final MsgKeyData msgKeyData;

   /**
    * Create a key data holder, the message oid is randomly generated.
    */
   public PublishKey(Global glob) {
      this.msgKeyData = new MsgKeyData(glob);
   }

   /**
    * Create a key data holder with the given message oid.
    */
   public PublishKey(Global glob, String oid) {
      this.msgKeyData = new MsgKeyData(glob);
      setOid(oid);
   }

   /**
    * Create a key data holder with the given message oid and its mime type.
    */
   public PublishKey(Global glob, String oid, String contentMime) {
      this.msgKeyData = new MsgKeyData(glob);
      setOid(oid);
      setContentMime(contentMime);
   }

   /**
    * Create a key data holder with the given message oid and its mime types.
    */
   public PublishKey(Global glob, String oid, String contentMime, String contentMimeExtended) {
      this.msgKeyData = new MsgKeyData(glob);
      setOid(oid);
      setContentMime(contentMime);
      setContentMimeExtended(contentMimeExtended);
   }

   /**
    * Create a key data holder with the given message oid and its mime types.
    * @param domain The cluster domain
    */
   public PublishKey(Global glob, String oid, String contentMime, String contentMimeExtended, String domain) {
      this.msgKeyData = new MsgKeyData(glob);
      setOid(oid);
      setContentMime(contentMime);
      setContentMimeExtended(contentMimeExtended);
      setDomain(domain);
   }

   /**
    * Pass a pre filled data object.
    */
   public PublishKey(Global glob, MsgKeyData msgKeyData) {
      this.msgKeyData = msgKeyData;
      //glob.getMsgKeyFactory().readObject(xmlKey);
   }

   public MsgKeyData getData() {
      return this.msgKeyData;
   }

   /**
    * Set the &lt;key oid="...">.
    * @param The unique key oid
    */
   public void setOid(String oid) {
      this.msgKeyData.setOid(oid);
   }

   /**
    * Access the &lt;key oid="...">.
    * @return The unique key oid
    */
   public String getOid() {
      return this.msgKeyData.getOid();
   }

   /**
    * A MIME type like "image/gif"
    */
   public void setContentMime(String contentMime) {
      this.msgKeyData.setContentMime(contentMime);
   }

   /**
    * A MIME type like "image/gif"
    */
   public String getContentMime() {
      return this.msgKeyData.getContentMime();
   }

   /**
    * For example a version number of the mime type
    */
   public void setContentMimeExtended(String contentMimeExtended) {
      this.msgKeyData.setContentMimeExtended(contentMimeExtended);
   }

   /**
    * For example a version number of the mime type
    */
   public String getContentMimeExtended() {
      return this.msgKeyData.getContentMimeExtended();
   }

   /**
    * Allows to give cluster a hint about who is the master
    * or can be used for your own purposes
    */
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
    * May be used to integrate your application tags.
    * <p />
    * @param str The ASCII XML key containing your tags
    */
   public void setClientTags(String clientTags) {
      this.msgKeyData.setClientTags(clientTags);
   }

   /**
    * Your specific application tags.
    */
   public String getClientTags() {
      return this.msgKeyData.getClientTags();
   }

   public Global getGlobal() {
      return this.msgKeyData.getGlobal();
   }

   /**
    * Converts the data in XML ASCII string.
    * @return An XML ASCII string
    */
   public String toXml() {
      return this.msgKeyData.toXml();
   }
}
