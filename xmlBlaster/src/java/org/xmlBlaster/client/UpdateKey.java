/*------------------------------------------------------------------------------
Name:      UpdateKey.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * @deprecated Use org.xmlBlaster.client.key.UpdateKey instead
 */
public class UpdateKey
{
   private String ME = "UpdateKey";
   private org.xmlBlaster.client.key.UpdateKey updateKey;

   /**
    * Constructs an initialized UpdateKey object.
    * @param xmlKey The ASCII XML key to parse
    */
   public UpdateKey(Global glob, String xmlKey) throws XmlBlasterException {
      this.updateKey = new org.xmlBlaster.client.key.UpdateKey(glob, xmlKey);
   }

   /**
    * Access the $lt;key oid="...">.
    * @return The unique key oid
    */
   public String getOid() {
      return this.updateKey.getOid();
   }

   /**
    * Access the $lt;key oid="...">.
    * @return The unique key oid
    * @see #getOid()
    */
   public String getUniqueKey() {
      return getOid();
   }

   /**
    * Test if oid is '__sys__deadMessage'. 
    * <p />
    * Dead letters are unrecoverable lost messages, usually an administrator
    * should subscribe to those messages.
    */
   public boolean isDeadMessage() {
      return this.updateKey.isDeadMessage();
   }

   public final boolean isInternal() {
      return this.updateKey.isInternal();
   }

   public final boolean isNotInternal() {
      return !isInternal();
   }

   /**
    * Find out which mime type (syntax) the content of the message has.
    * @return e.g "text/xml" or "image/png"
    *         defaults to "text/plain"
    */
   public String getContentMime() {
      return this.updateKey.getContentMime();
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
      return this.updateKey.getContentMimeExtended();
   }

   /**
    * The cluster domain. 
    */
   public String getDomain() {
      return this.updateKey.getDomain();
   }

   /**
    * Dump state of this object into XML.
    * <br>
    * @return The unparsed and literal XML string as delivered from xmlBlaster
    */
   public final String toXml() {
      return this.updateKey.toXml();
   }

   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of UpdateKey
    * @deprecated Use toXml() instead
    */
   public final String toXml(String extraOffset) {
      return this.updateKey.getData().toXml(extraOffset);
   }
}
