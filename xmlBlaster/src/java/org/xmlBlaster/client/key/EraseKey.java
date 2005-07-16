/*------------------------------------------------------------------------------
Name:      EraseKey.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.key;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * Access to an erase key. 
 * <p>
 * See QueryKeySaxFactory for a syntax description of the allowed xml structure
 * </p>
 * @see org.xmlBlaster.util.key.QueryKeySaxFactory
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.erase.html" target="others">the interface.erase requirement</a>
 */
public class EraseKey
{
   private final static String ME = "EraseKey";
   private final QueryKeyData queryKeyData;

   /**
    * Create a key data holder to erase message(s). 
    * @param query The query string
    *        For example a topic oid like "Hello" or "oid:Hello"
    *        or a query like "xpath://key", "domain:CLUSTER")
    */
   public EraseKey(Global glob, String query) {
      this.queryKeyData = new QueryKeyData(glob, query);
   }

   /**
    * Constructor with given oid.
    * @param queryString  The String with e.g. XPath syntax
    * @param queryType    The query syntax, e.g. Constants.XPATH
    * @param XmlBlasterException for invalid queryType
    */
   public EraseKey(Global glob, String queryString, String queryType) throws XmlBlasterException {
      this.queryKeyData = new QueryKeyData(glob, queryString, queryType);
   }

   /**
    * Constructor for internal use. 
    * @param queryKeyData The struct holding the data
    */
   public EraseKey(Global glob, QueryKeyData queryKeyData) {
      this.queryKeyData = queryKeyData;
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
    * Access the query type "XPATH" or "EXACT"
    * @return A queryType string or null
    */
   public String getQueryType() {
      return this.queryKeyData.getQueryType();
   }

   public boolean isExact() {
      return this.queryKeyData.isExact();
   }

   public boolean isQuery() {
      return this.queryKeyData.isQuery();
   }

   public boolean isXPath() {
      return this.queryKeyData.isXPath();
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

   public void setDomain(String domain) {
      this.queryKeyData.setDomain(domain);
   }

   public String getDomain() {
      return this.queryKeyData.getDomain();
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
