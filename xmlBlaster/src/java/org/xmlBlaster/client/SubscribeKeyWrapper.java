/*------------------------------------------------------------------------------
Name:      SubscribeKeyWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey
Version:   $Id: SubscribeKeyWrapper.java,v 1.9 2002/06/15 16:03:28 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.util.XmlBlasterException;


/**
 * This class encapsulates the Message meta data and unique identifier (key) of a subscribe() or get() message.
 * <p />
 * A typical <b>subscribe</b> key could look like this:<br />
 * <pre>
 *     &lt;key oid='4711' queryType='EXACT'>
 *     &lt;/key>
 * </pre>
 * or like this:
 * <pre>
 *     &lt;key oid='' queryType='XPATH'>
 *        //AGENT
 *     &lt;/key>
 * </pre>
 *
 * @see org.xmlBlaster.client.KeyWrapper
 * <p />
 * see xmlBlaster/src/dtd/XmlKey.xml
 * <p />
 * see http://www.w3.org/TR/xpath
 */
public class SubscribeKeyWrapper extends KeyWrapper
{
   private String ME = "SubscribeKeyWrapper";
   private String queryString = "";
   /** value from attribute <key oid="" queryType="..."> */
   private String queryType = "EXACT";


   /**
    * Constructor with given oid.
    * @param oid Subscribe to a well known oid.
    */
   public SubscribeKeyWrapper(String oid)
   {
      super(oid);
   }


   /**
    * Constructor with given oid.
    * @param queryString  The String with e.g. XPath syntax
    * @param queryType    The query syntax, only "XPATH" for the moment
    */
   public SubscribeKeyWrapper(String queryString, String queryType) throws XmlBlasterException
   {
      super("");
      this.queryType = queryType;
      if (queryType.equals(Constants.EXACT))
         oid = queryString;
      /*
      else if (queryType.equals(Constants.DOMAIN))
         this.domain = queryString;
      */
      else if (queryType.equals(Constants.XPATH))
         this.queryString = queryString;
      else
         throw new XmlBlasterException(ME, "Your queryType=" + queryType + " is invalid, use one of \"EXACT\", \"XPATH\"");
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
      sb.append("<key");
      if (queryType.equals(Constants.EXACT)) {
         sb.append(" oid='").append(oid).append("'");
      }
      if (domain.length() > 0) {
         sb.append(" domain='").append(domain).append("'");
      }
      if (queryType.equals(Constants.XPATH)) {
         sb.append(" queryType='").append(queryType).append("'>\n");
         sb.append(queryString);
         sb.append("\n</key>");
      }
      else {
         sb.append("/>");
      }

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
      queryString = str;
      return toXml();
   }
}
