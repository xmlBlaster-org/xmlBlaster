/*------------------------------------------------------------------------------
Name:      UnSubscribeKeyWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.key.QueryKeyData;


/**
 * Create a key for a unSubscribe() invocation. 
 * @deprecated Please use org.xmlBlaster.client.key.UnSubscribeKey
 */
public class UnSubscribeKeyWrapper
{
   private String ME = "UnSubscribeKeyWrapper";
   private final QueryKeyData queryKeyData;

   /**
    * Constructor with given oid or subscriptionId.
    * @param oid UnSubscribe to a well known oid or a subscriptionId.
    */
   public UnSubscribeKeyWrapper(String oid) {
      this.queryKeyData = new QueryKeyData(Global.instance());
      this.queryKeyData.setOid(oid);
   }

   /**
    * @param queryString  The String with e.g. XPath syntax
    * @param queryType    The query syntax, only "XPATH" for the moment
    */
   public UnSubscribeKeyWrapper(String queryString, String queryType) throws XmlBlasterException {
      this.queryKeyData = new QueryKeyData(null, queryString, queryType);
   }

   public String getOid() {
      return this.queryKeyData.getOid();
   }

   public String getContentMime() {
      return this.queryKeyData.getContentMime();
   }

   public String getContentMimeExtended() {
      return this.queryKeyData.getContentMimeExtended();
   }

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
      return toXml();
   }

   /**
    * Converts the data in XML ASCII string.
    * @return An XML ASCII string
    */
   public String toXml() {
      return this.queryKeyData.toXml();
   }

   /**
    * May be used to integrate your XPath query. 
    * <p />
    * @param str Your tags in ASCII XML syntax
    */
   public String wrap(String str) {
      this.queryKeyData.setQueryString(str);
      return this.queryKeyData.toXml();
   }
}
