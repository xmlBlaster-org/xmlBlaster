/*------------------------------------------------------------------------------
Name:      UnSubscribeKey.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.key;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * Wrap a XML key for an unSubscribe() invocation. 
 * <p>
 * See QueryKeySaxFactory for a syntax description of the allowed xml structure
 * </p>
 * @see org.xmlBlaster.util.key.QueryKeySaxFactory
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.unSubscribe.html" target="others">the interface.unSubscribe requirement</a>
 */
public class UnSubscribeKey
{
   private final static String ME = "UnSubscribeKey";
   private final QueryKeyData queryKeyData;

   /**
    * Constructor with given oid.
    * @param oid UnSubscribe to a well known message oid.
    */
   public UnSubscribeKey(Global glob, String oid) {
      this.queryKeyData = new QueryKeyData(glob);
      this.queryKeyData.setOid(oid);
   }

   /**
    * Constructor with query parameters. 
    * @param queryString  The String with e.g. XPath syntax
    * @param queryType    The query syntax, only "XPATH" for the moment
    * @param XmlBlasterException for invalid queryType
    */
   public UnSubscribeKey(Global glob, String queryString, String queryType) throws XmlBlasterException {
      this.queryKeyData = new QueryKeyData(glob);
      this.queryKeyData.setQueryString(queryString);
      this.queryKeyData.setQueryType(queryType);
   }

   public QueryKeyData getData() {
      return this.queryKeyData;
   }

   /**
    * Set the $lt;key oid="...">.
    * @param The unique key oid
    */
   public final void setOid(String oid) {
      this.queryKeyData.setOid(oid);
   }

   /**
    * Access the &lt;key oid="...">.
    * @return The unique key oid
    */
   public final String getOid() {
      return this.queryKeyData.getOid();
   }

   /**
    * Query type "XPATH" or "EXACT" (see Constants.java)
    */
   public void setQueryType(String queryType) throws XmlBlasterException {
      this.queryKeyData.setQueryType(queryType);
   }

   /**
    * Access the query type "XPATH" or "EXACT"
    * @return A queryType string or null
    */
   public String getQueryType() {
      return this.queryKeyData.getQueryType();
   }

   /**
    * Your XPath query string. 
    * @param str Your tags in ASCII XML syntax
    */
   public void setQueryString(String tags) {
      this.queryKeyData.setQueryString(tags);
   }

   public String getQueryString() {
      return this.queryKeyData.getQueryString();
   }

   /**
    * Converts the data in XML ASCII string.
    * @return An XML ASCII string
    */
   public String toString() {
      return this.queryKeyData.toString();
   }

   /**
    * Converts the data in XML ASCII string.
    * @return An XML ASCII string
    */
   public String toXml() {
      return this.queryKeyData.toXml();
   }

   /**
    * May be used to integrate your application tags.
    * @param str Your tags
    * @return The ASCII XML key containing the key tag and your tags
    */
   public String wrap(String str) {
      this.queryKeyData.setQueryString(str);
      return this.queryKeyData.toXml();
   }
}
