/*------------------------------------------------------------------------------
Name:      QueryKeyData.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.key;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.AccessFilterQos;

import java.util.ArrayList;

/**
 * This class encapsulates the Message key information of query invocations. 
 * <p />
 * <ul>
 * <li>SubscribeKey Client side access facade</i>
 * <li>UnSubscribeKey Client side access facade</i>
 * <li>GetKey Client side access facade</i>
 * <li>EraseKey Client side access facade</i>
 * </ul>
 * <p>
 * For the xml representation see MsgKeySaxFactory.
 * </p>
 * @see org.xmlBlaster.util.key.QueryKeySaxFactory
 */
public final class QueryKeyData
{
   // NOTE: We parse all key attributes, but only a few are used for queries
   // e.g. contentMime and contentMimeExtended are not useful (the decorater classes hide them)

   private final static String ME = "QueryKeyData";
   private final Global glob;
   private transient final I_QueryKeyFactory factory;
   private final String serialData; // can be null - in this case use toXml()
   /** value from attribute <key oid="..."> */
   private String oid;
   /** The default content MIME type is "text/plain" */
   public static final String CONTENTMIME_DEFAULT = "text/plain";
   /** value from attribute <key oid="" contentMime="..."> */
   private String contentMime = CONTENTMIME_DEFAULT;
   /** value from attribute <key oid="" contentMimeExtended="..."> */
   private String contentMimeExtended;
   /** value from attribute <key oid="" domain="..."> */
   private String domain;
   /** The default queryType is "EXACT" */
   public static final String QUERYTYPE_DEFAULT = Constants.EXACT;
   /** The query type */
   private String queryType = QUERYTYPE_DEFAULT;
   /** The query string */
   private String queryString;

   /**
    * subscribe(), get() and cluster configuration keys may contain a filter rule
    */
   protected ArrayList filterList = null;   // To collect the <filter> when sax parsing
   protected transient AccessFilterQos[] filterArr = null; // To cache the filters in an array

   /**
    * Minimal constructor.
    */
   public QueryKeyData(Global glob) {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.factory = glob.getQueryKeyFactory();
      this.serialData = null; // toXml() ?
   }

   /**
    * Constructor to parse a message. 
    * @param factory If null, the default factory from Global is used.
    */
   public QueryKeyData(Global glob, I_QueryKeyFactory factory, String serialData) {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.factory = (factory == null) ? this.glob.getQueryKeyFactory() : factory;
      this.serialData = serialData;
   }

   public void setOid(String oid) {
      this.oid = oid;
   }

   public String getOid() {
      return this.oid;
   }

   public void setContentMime(String contentMime) {
      this.contentMime = contentMime;
   }

   /**
    * Find out which mime type (syntax) the content of the message has.
    * @return e.g "text/xml" or "image/png"
    *         defaults to "text/plain"
    */
   public String getContentMime() {
      return this.contentMime;
   }

   /**
    * Some further specifying information of the content.
    * <p />
    * For example the application version number the document in the content.<br />
    * You may use this attribute for you own purposes.
    * @return The MIME-extended info, for example<br />
    *         "Version 1.1" in &lt;key oid='' contentMime='text/xml' contentMimeExtended='Version 1.1'><br />
    *         or "" (empty string) if not known
    */
   public void setContentMimeExtended(String contentMimeExtended) {
      this.contentMimeExtended = contentMimeExtended;
   }

   /**
    * Some further specifying information of the content.
    * <p />
    * For example the application version number the document in the content.<br />
    * You may use this attribute for you own purposes.
    * @return The MIME-extended info, for example<br />
    *         "Version 1.1" in &lt;key oid='' contentMime='text/xml' contentMimeExtended='Version 1.1'><br />
    *         or "" (empty string) if not known
    */
   public String getContentMimeExtended() {
      return this.contentMimeExtended;
   }

   public void setDomain(String domain) {
      this.domain = domain;
   }

   public String getDomain() {
      return this.domain;
   }

   public void setQueryType(String queryType) throws XmlBlasterException {
      /*
      if (queryType.equals(Constants.EXACT))
         oid = queryString;
      else if (queryType.equals(Constants.DOMAIN))
         this.domain = queryString;
      else if (queryType.equals(Constants.XPATH))
         this.queryString = queryString;
      else
      */
      if (!Constants.EXACT.equalsIgnoreCase(queryType) && !Constants.XPATH.equalsIgnoreCase(queryType))
         throw new XmlBlasterException(ME, "Your queryType=" + queryType + " is invalid, use one of '" + Constants.EXACT + "' , '" + Constants.XPATH + "'");
      this.queryType = queryType.toUpperCase();
   }

   /**
    * Access the query type "XPATH" or "EXACT"
    * @return A queryType string or null
    */
   public String getQueryType() {
      return this.queryType;
   }

   /**
    * Your XPath query string. 
    * @param str Your tags in ASCII XML syntax
    */
   public void setQueryString(String tags) {
      this.queryString = tags;
   }

   public String getQueryString() {
      return this.queryString;
   }

   /**
    * Return the filters or array with size==0 if none is specified. 
    * <p />
    * For subscribe() and get() and cluster messages.
    * @return never null
    */
   public AccessFilterQos[] getAccessFilterArr() {
      if (filterArr != null)
         return filterArr;

      if (filterList == null)
         return null;

      filterArr = new AccessFilterQos[filterList.size()];
      filterList.toArray(filterArr);
      return filterArr;
   }

   public void addFilter(AccessFilterQos qos) {
      if (filterList == null) filterList = new ArrayList();
      filterList.add(qos);
      filterArr = null;
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
      return this.factory.writeObject(this, null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the query as a XML ASCII string
    */
   public String toXml(String extraOffset) {
      return factory.writeObject(this, extraOffset);
   }
}
