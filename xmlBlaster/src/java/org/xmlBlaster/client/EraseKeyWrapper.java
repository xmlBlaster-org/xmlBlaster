/*------------------------------------------------------------------------------
Name:      EraseKeyWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey
Version:   $Id: EraseKeyWrapper.java,v 1.1 2001/12/23 11:40:00 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;


/**
 * This class encapsulates the Message meta data and unique identifier (key) of a erase() call
 * <p />
 * A typical <b>erase</b> key could look like this:<br />
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
public class EraseKeyWrapper extends KeyWrapper
{
   private String ME = "EraseKeyWrapper";
   private String queryString = "";
   /** value from attribute <key oid="" queryType="..."> */
   private String queryType = "EXACT";


   /**
    * Constructor with given oid.
    * @param oid Erase to a well known oid.
    */
   public EraseKeyWrapper(String oid)
   {
      super(oid);
   }


   /**
    * Constructor with given oid.
    * @param queryString  The String with e.g. XPath syntax
    * @param queryType    The query syntax, only "XPATH" for the moment
    */
   public EraseKeyWrapper(String queryString, String queryType) throws XmlBlasterException
   {
      super("");
      this.queryType = queryType;
      if (queryType == "EXACT")
         oid = queryString;
      else if (queryType == "XPATH")
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
