/*------------------------------------------------------------------------------
Name:      UpdateKey.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.key;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * Parses the key of returned MessageUnit of update(). 
 * <p>
 * See MsgKeySaxFactory for a syntax description of the allowed xml structure
 * </p>
 * @see org.xmlBlaster.util.key.MsgKeySaxFactory
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.update.html" target="others">the interface.update requirement</a>
 */
public class UpdateKey
{
   private final static String ME = "UpdateKey";
   private final MsgKeyData msgKeyData;

   /**
    * Parse the given xml data. 
    */
   public UpdateKey(Global glob, String xmlKey) throws XmlBlasterException {
      this.msgKeyData = glob.getMsgKeyFactory().readObject(xmlKey);
   }

   public MsgKeyData getData() {
      return this.msgKeyData;
   }

   /**
    * Access the &lt;key oid="...">.
    * @return The unique key oid
    */
   public String getOid() {
      return this.msgKeyData.getOid();
   }

   /**
    * Dead letters are unrecoverable lost messages, usually an administrator
    * should subscribe to those messages.
    * <p>
    * This is an internal message (isInternal() returns true)
    * </p>
    */
   public boolean isDeadMessage() {
      return this.msgKeyData.isDeadMessage();
   }

   /**
    * Messages starting with "_" are reserved for usage in plugins
    */
   public final boolean isPluginInternal() {
      return this.msgKeyData.isPluginInternal();
   }

   /**
    * Messages starting with "__" are reserved for internal usage
    */
   public final boolean isInternal() {
      return this.msgKeyData.isInternal();
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
   public String getContentMimeExtended() {
      return this.msgKeyData.getContentMimeExtended();
   }

   /**
    * Access the domain setting
    * @return A domain string or null
    */
   public String getDomain() {
      return this.msgKeyData.getDomain();
   }

   /**
    * Your specific application tags.
    */
   public String getClientTags() {
      return this.msgKeyData.getClientTags();
   }

   /**
    * Converts the data in XML ASCII string.
    * @return An XML ASCII string
    */
   public String toXml() {
      return this.msgKeyData.toXml();
   }
}

