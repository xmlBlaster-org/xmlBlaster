/*------------------------------------------------------------------------------
Name:      GetKey.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.key;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * Wraps an XML key for a get() invocation. 
 * <p>
 * See QueryKeySaxFactory for a syntax description of the allowed xml structure
 * </p>
 * @see org.xmlBlaster.util.key.QueryKeySaxFactory
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.get.html" target="others">the interface.get requirement</a>
 */
public class GetKey
{
   private final static String ME = "GetKey";
   private final QueryKeyData queryKeyData;

   /**
    * Create a key data holder.  
    * @param oid Get to a well known message oid.
    */
   public GetKey(Global glob, String oid) {
      this.queryKeyData = new QueryKeyData(glob);
      this.queryKeyData.setOid(oid);
   }

   /**
    * Constructor for XPath query. 
    * @param queryString  The String with e.g. XPath syntax
    * @param queryType    The query syntax, e.g. Constants.XPATH
    * @param XmlBlasterException for invalid queryType
    */
   public GetKey(Global glob, String queryString, String queryType) throws XmlBlasterException {
      this.queryKeyData = new QueryKeyData(glob);
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
    * Give a hint to which cluster domain this Key belongs. 
    */
   public void setDomain(String domain) {
      this.queryKeyData.setDomain(domain);
   }

   /**
    * Access the domain setting
    * @return A domain string or null
    */
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
