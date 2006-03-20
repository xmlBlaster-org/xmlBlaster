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
 * Parses the key of returned MsgUnit of update(). 
 * <p>
 * See MsgKeySaxFactory for a syntax description of the allowed xml structure
 * </p>
 * A typical key could look like this:<br />
 * <pre>
 *     &lt;key oid='4711' contentMime='text/xml'>
 *        &lt;AGENT id='192.168.124.20' subId='1' type='generic'>
 *           &lt;DRIVER id='FileProof' pollingFreq='10'>
 *           &lt;/DRIVER>
 *        &lt;/AGENT>
 *     &lt;/key>
 * </pre>
 * <p>
 * NOTE: The key attributes like <i>oid</i> or <i>contentMime</i> are parsed already
 * and available with the getter methods in this class and the superclass.
 * <br />
 * The application specific tags and their attributes (like AGENT or DRIVER in the above example)
 * are received as a 'raw' XML ASCII string with the <i>getClientTags()</i> or <i>toXml()</i> methods.
 * If you want to look at them you need to parse them yourself, usually by using an XML parser (DOM or SAX parser).
 * </p>
 * @see org.xmlBlaster.util.key.MsgKeySaxFactory
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.update.html" target="others">the interface.update requirement</a>
 */
public class UpdateKey
{
   private final Global glob;
   private final static String ME = "UpdateKey";
   private final MsgKeyData msgKeyData;

   /**
    * Parse the given xml data. 
    */
   public UpdateKey(Global glob, String xmlKey) throws XmlBlasterException {
      this.glob = glob;
      this.msgKeyData = glob.getMsgKeyFactory().readObject(xmlKey);
   }

   public MsgKeyData getData() {
      return this.msgKeyData;
   }

   public Global getGlobal() {
      return this.glob;
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
   public String toXml(String extraOffset) {
      return this.msgKeyData.toXml(extraOffset);
   }

   /**
    * Converts the data in XML ASCII string.
    * @return An XML ASCII string
    */
   public String toXml() {
      return this.msgKeyData.toXml();
   }

   public String toString() {
      return toXml().trim();
   }
}

