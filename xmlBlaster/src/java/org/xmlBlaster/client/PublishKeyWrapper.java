/*------------------------------------------------------------------------------
Name:      PublishKeyWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey
Version:   $Id: PublishKeyWrapper.java,v 1.10 2000/09/15 17:16:14 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;


/**
 * This class encapsulates the Message meta data and unique identifier (key) of a publish() message.
 * <p />
 * A typical <b>publish</b> key could look like this:<br />
 * <pre>
 *     &lt;key oid='4711' contentMime='text/xml'>
 *        &lt;AGENT id='192.168.124.20' subId='1' type='generic'>
 *           &lt;DRIVER id='FileProof' pollingFreq='10'>
 *           &lt;/DRIVER>
 *        &lt;/AGENT>
 *     &lt;/key>
 * </pre>
 * <br />
 * Note that the AGENT and DRIVER tags are application know how, which you have to supply to the wrap() method.<br />
 * A well designed xml hierarchy of your problem domain is essential for a proper working xmlBlaster
 * <p />
 * This is exactly the key how it was published from the data source.
 *
 * @see org.xmlBlaster.client.KeyWrapper
 * <p />
 * see xmlBlaster/src/dtd/PublishKeyWrapper.xml
 * <p />
 * see http://www.w3.org/TR/xpath
 */
public class PublishKeyWrapper extends KeyWrapper
{
   private final static String ME = "PublishKeyWrapper";
   /** value from attribute <key oid="" contentMime="..."> */
   private String contentMime = "text/plain";
   /** value from attribute <key oid="" contentMimeExtended="..."> */
   private String contentMimeExtended = null;
   private String clientTags = "";


   /**
    * Constructor with given oid and contentMime.
    * @param oid is optional and will be generated if ""
    * @param contentMime the MIME type of the content e.g. "text/xml" or "image/gif"
    * @param contentMimeExtended Use it for whatever, e.g. the version number or parser infos for your content<br />
    *        set to null if not needed
    */
   public PublishKeyWrapper(String oid, String contentMime, String contentMimeExtended)
   {
      super(oid);
      if (contentMime != null)
         this.contentMime = contentMime;
      if (contentMimeExtended != null)
         this.contentMimeExtended = contentMimeExtended;
   }


   /**
    * Converts the data in XML ASCII string.
    * @return An XML ASCII string
    */
   public String toString()
   {
      return toXml();
   }


   /**
    * Converts the data in XML ASCII string.
    * @return An XML ASCII string
    */
   public String toXml()
   {
      return this.wrap(clientTags);
   }


   /**
    * May be used to integrate your application tags.
    * <p />
    * Derive your special PublishKey class from this.
    * @param str Your tags in ASCII XML syntax
    */
   public String wrap(String str)
   {
      clientTags = str;
      StringBuffer sb = new StringBuffer();
      sb.append("<key oid='").append(oid).append("'");
      sb.append(" contentMime='").append(contentMime).append("'");
      if (contentMimeExtended != null)
         sb.append(" contentMimeExtended='").append(contentMimeExtended).append("'");
      sb.append(">\n");
      sb.append(clientTags);
      sb.append("\n</key>");
      return sb.toString();
   }
}
