/*------------------------------------------------------------------------------
Name:      SubscribeKeyWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey
Version:   $Id: SubscribeKeyWrapper.java,v 1.1 2000/01/21 19:06:04 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;


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
 * @see org.xmlBlaster.util.KeyWrapper
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
   public SubscribeKeyWrapper(String queryString, String queryType)
   {
      super("");
      this.queryString = queryString;
      this.queryType = queryType;
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
      return this.wrap(queryString);
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

      StringBuffer sb = new StringBuffer();
      sb.append("<key oid='").append(oid).append("'");
      sb.append(" queryType='").append(queryType).append("'");
      sb.append(">\n");
      sb.append(queryString);
      sb.append("\n</key>");
      return sb.toString();
   }
}
