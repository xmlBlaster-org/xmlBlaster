/*------------------------------------------------------------------------------
Name:      PublishKeyWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey
Version:   $Id: PublishKeyWrapper.java,v 1.15 2002/09/13 23:17:54 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

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
   /** value from attribute <key oid="" domain="..."> */
   private String domain = null;
   private String clientTags = null;


   /**
    * Constructor with given oid and contentMime.
    * @param oid is optional and will be generated if ""
    * @param contentMime the MIME type of the content e.g. "text/xml" or "image/gif"
    */
   public PublishKeyWrapper(String oid, String contentMime)
   {
      super(oid);
      if (contentMime != null)
         this.contentMime = contentMime;
   }


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
    * Constructor with given oid and contentMime.
    * @param oid is optional and will be generated if ""
    * @param contentMime the MIME type of the content e.g. "text/xml" or "image/gif"
    * @param contentMimeExtended Use it for whatever, e.g. the version number or parser infos for your content<br />
    *        set to null if not needed
    */
   public PublishKeyWrapper(String oid, String contentMime, String contentMimeExtended, String domain)
   {
      super(oid);
      if (contentMime != null)
         this.contentMime = contentMime;
      if (contentMimeExtended != null)
         this.contentMimeExtended = contentMimeExtended;
      if (domain != null)
         this.domain = domain;
   }


   /**
    * Access the domain setting
    * @return A domain string or null
    */
   public String getDomain()
   {
      return this.domain;
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
      StringBuffer sb = new StringBuffer(256);
      sb.append("<key oid='").append(oid).append("'");
      sb.append(" contentMime='").append(contentMime).append("'");
      if (contentMimeExtended != null && contentMimeExtended.length() > 0)
         sb.append(" contentMimeExtended='").append(contentMimeExtended).append("'");
      if (domain != null && domain.length() > 0)
         sb.append(" domain='").append(domain).append("'");
      if (clientTags != null && clientTags.trim().length() > 0) {
         sb.append(">\n");
         sb.append(clientTags.trim());
         sb.append("\n</key>");
      }
      else
         sb.append("/>");
      return sb.toString();
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
      return this.toXml();
   }
}
