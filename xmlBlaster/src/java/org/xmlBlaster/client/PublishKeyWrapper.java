/*------------------------------------------------------------------------------
Name:      PublishKeyWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey
Version:   $Id: PublishKeyWrapper.java,v 1.1 1999/12/14 23:18:00 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;


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
 * @see org.xmlBlaster.util.PublishKeyWrapperBase
 * <p />
 * see xmlBlaster/src/dtd/PublishKeyWrapper.xml
 * <p />
 * see http://www.w3.org/TR/xpath
 */
public class PublishKeyWrapper extends KeyWrapper
{
   private String ME = "PublishKeyWrapper";
   private String mimeType = "text/plain";


   /**
    * Constructor with given oid and mimeType. 
    * @param oid is optional and will be generated if ""
    * @param mimeType the MIME type of the content e.g. "text/xml" or "image/gif"
    */
   public PublishKeyWrapper(String oid, String mimeType)
   {
      super(oid);
      if (mimeType != null)
         this.mimeType = mimeType;
   }

   /**
    * Converts the data in XML ASCII string. 
    * @return An XML ASCII string
    */
   public String toString()
   {
      return this.wrap("");
   }


   /**
    * May be used to integrate your application tags. 
    * <p />
    * Derive your special PublishKey class from this.
    * @param str Your tags in ASCII XML syntax
    */
   public String wrap(String str)
   {
      StringBuffer sb = new StringBuffer();
      sb.append("<key oid='").append(oid).append("' contentMime='").append(mimeType).append("'>").append(str).append("</key>");
      return sb.toString();
   }
}
