/*------------------------------------------------------------------------------
Name:      GetReturnKey.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.key;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * Parses the key of returned MsgUnit of get() invocations
 * <p>
 * See MsgKeySaxFactory for a syntax description of the xml structure
 * </p>
 * @see org.xmlBlaster.util.key.MsgKeySaxFactory
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.get.html" target="others">the interface.get requirement</a>
 */
public class GetReturnKey
{
   private final static String ME = "GetReturnKey";
   private final MsgKeyData msgKeyData;

   /**
    * Parse the given xml data. 
    */
   public GetReturnKey(Global glob, String xmlKey) throws XmlBlasterException {
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

