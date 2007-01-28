/*------------------------------------------------------------------------------
Name:      QueryKeyData.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.key;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.qos.AccessFilterQos;

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
public final class QueryKeyData extends KeyData implements java.io.Serializable, Cloneable
{
   // NOTE: We parse all key attributes, but only a few are used for queries
   // e.g. contentMime and contentMimeExtended are not useful (the decorater classes hide them)
   private static final long serialVersionUID = 1L;
   private final static String ME = "QueryKeyData";
   private transient I_QueryKeyFactory factory;

   /**
    * subscribe(), get() and cluster configuration keys may contain a filter rule
    */
   protected ArrayList filterList = null;   // To collect the <filter> when sax parsing
   protected transient AccessFilterQos[] filterArr = null; // To cache the filters in an array

   /**
    * Minimal constructor.
    */
   public QueryKeyData(Global glob) {
      this(glob, (I_QueryKeyFactory)null, (String)null);
   }

   /**
    * @param glob
    * @param query The query string
    *        For example a topic oid like "Hello" or "oid:Hello"
    *        or a query like "xpath://key", "domain:CLUSTER"
    */
   public QueryKeyData(Global glob, String query) {
      this(glob);
      if (query == null || query.length() == 0) {
         throw new IllegalArgumentException("QueryKeyData got query=null argument");
      }

      if (query.startsWith("xpath:")) {
         this.queryType = Constants.XPATH;
         if (query.length() <= "xpath:".length())
            throw new IllegalArgumentException("QueryKeyData got query='xpath:' with no query");
         this.queryString = query.substring("xpath:".length());
      }
      else if (query.startsWith("subscriptionId:")) {
         this.queryType = Constants.EXACT;
         if (query.length() <= "subscriptionId:".length())
            throw new IllegalArgumentException("QueryKeyData got query='subscriptionId:' with empy id");
         setOid(query.substring("subscriptionId:".length()));
      }
      else if (query.startsWith("oid:")) {
         this.queryType = Constants.EXACT;
         if (query.length() <= "oid:".length())
            throw new IllegalArgumentException("QueryKeyData got query='oid:' with empy id");
         if (query.indexOf("\"") != -1 || query.indexOf("\'") != -1)
            throw new IllegalArgumentException("Please pass a valid topic oid without apostrophe \" or '");
         setOid(query.substring("oid:".length()));
      }
      else if (query.startsWith("domain:")) {
         this.queryType = Constants.EXACT;
         if (query.length() <= "domain:".length())
            throw new IllegalArgumentException("QueryKeyData got query='domain:' with empy id");
         setDomain(query.substring("domain:".length()));
      }
      else {
         this.queryType = Constants.EXACT;
         if (query.indexOf("\"") != -1 || query.indexOf("\'") != -1)
            throw new IllegalArgumentException("Please pass a valid topic oid without apostrophe \" or '");
         setOid(query);
      }
   }

   /**
    * @param glob
    * @param query The query string (syntax is depending on queryType)
    * @param queryType Constants.EXACT | Constants.XPATH | Constants.DOMAIN
    */
   public QueryKeyData(Global glob, String query, String queryType) throws XmlBlasterException {
      this(glob);
      if (query == null) {
         throw new IllegalArgumentException("QueryKeyData got query=null argument");
      }
      this.queryType = checkQueryType(queryType);
      if (isExact()) {
         setOid(query);
      }
      else if (isXPath()) {
         this.queryString = query;
      }
      else if (isDomain()) {
         setDomain(query);
      }
      else {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "Your queryType=" + queryType + " is invalid, implementation is missing");
      }
   }

   /**
    * Constructor to parse a message. 
    * @param factory If null, the default factory from Global is used.
    */
   public QueryKeyData(Global glob, I_QueryKeyFactory factory, String serialData) {
      super(glob, serialData);
      this.factory = (factory == null) ? this.glob.getQueryKeyFactory() : factory;
   }

   /**
    * Check the query type, Constants.XPATH, Constants.EXACT, Constants.DOMAIN are currently supported
    */
   public String checkQueryType(String queryType) throws XmlBlasterException {
      queryType = queryType.toUpperCase();
      if (!Constants.EXACT.equals(queryType) &&
          !Constants.XPATH.equals(queryType) &&
          !Constants.DOMAIN.equals(queryType))
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME,
              "Your queryType=" + queryType + " is invalid, use one of '"
               + Constants.EXACT + "' , '" + Constants.XPATH + "', '" + Constants.DOMAIN + "'");
      return queryType;
   }

   /**
    * Should be avoided to call directly.
    */
   public void setQueryType(String queryType) throws XmlBlasterException {
      this.queryType = checkQueryType(queryType);
      /*
      checkQueryType(queryType);
      String oldType = this.queryType;
      this.queryType = queryType.toUpperCase();
      if (!this.queryType.equals(oldType)) {
         if (isExact() && getOid()==null && oldType.equals(Constants.XPATH)) {
            super.setOid(this.queryString);
            this.queryString = null;
         }
         else if (isExact() && getOid()==null && oldType.equals(Constants.DOMAIN)) {
            super.setOid(getDomain());
            super.setDomain(null);
         }
         else if (isXPath() && this.queryString==null && oldType.equals(Constants.EXACT)) {
            this.queryString = getOid();
            //super.setOid(null);
         }
         else if (isXPath() && this.queryString==null && oldType.equals(Constants.DOMAIN)) {
            this.queryString = getDomain();
            super.setDomain(null);
         }
         else if (isDomain() && getDomain()==null && oldType.equals(Constants.EXACT)) {
            super.setDomain(getOid());
            //super.setOid(null);
         }
         else if (isDomain() && getDomain()==null && oldType.equals(Constants.XPATH)) {
            super.setDomain(this.queryString);
            this.queryString = null;
         }
      }
      */
   }

   /**
    * Use for domain specific query
    */
   public void setOid(String oid) {
      this.queryType = Constants.EXACT;
      super.setOid(oid);
   }

   /**
    * Use for domain specific query
    */
   public void setDomain(String domain) {
      // We don't force the queryType to DOMAIN here as this lets
      // the cluster tests e.g.
      //   java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.cluster.DirtyReadTest
      // fail. For clustering the domain='' is just an attribute used to
      // find a master.
      // On the other hand there is query support for DOMAIN in RequestBroker (in none cluster environment)
      // --> We need to resolve this!
      //this.queryType = Constants.DOMAIN;
      super.setDomain(domain);
   }

   /**
    * Your XPath query string. 
    * @param query The query string, e.g. "//key"
    */
   public void setQueryString(String query) {
      this.queryType = Constants.XPATH;
      this.queryString = query;
   }

   /**
    * Same as setQueryString() but allows you to call it more than once
    * the strings are concatenated. 
    * @param query The query string, e.g. "//key"
    */
   public void appendQueryString(String query) {
      this.queryType = Constants.XPATH;
      if (this.queryString == null) {
         this.queryString = query;
      }
      else {
         this.queryString += query;
      }
   }

   public String getQueryString() {
      return this.queryString;
   }

   /**
    * Helper which returns the oid OR the xpath query for nice logging. 
    */
   public String getNiceString() {
      if (getOid() != null && getOid().length() > 0) {
         return getOid();
      }
      return getQueryString();
   }

   /**
    * Return the filters or array with size==0 if none is specified. 
    * <p />
    * For subscribe() and get() and cluster messages.
    * @return never null
    */
   public synchronized AccessFilterQos[] getAccessFilterArr() {
      if (filterArr != null)
         return filterArr;

      if (filterList == null)
         return null;

      filterArr = new AccessFilterQos[filterList.size()];
      filterList.toArray(filterArr);
      return filterArr;
   }

   /*
    * For cluster master lookup only
    */
   public synchronized void addFilter(AccessFilterQos qos) {
      if (filterList == null) filterList = new ArrayList();
      filterList.add(qos);
      filterArr = null;
   }

   private I_QueryKeyFactory getFactory() {
      if (this.factory == null) {
         this.factory = this.glob.getQueryKeyFactory();
      }
      return this.factory;
   }

   /**
    * Converts the data in XML ASCII string.
    * @return An XML ASCII string
    */
   public String toXml() {
      return getFactory().writeObject(this, null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the query as a XML ASCII string
    */
   public String toXml(String extraOffset) {
      return getFactory().writeObject(this, extraOffset);
   }

   /**
    * Returns a shallow clone, you can change savely all basic or immutable types
    * like boolean, String, int.
    * Currently AccessFilterQos is not deep cloned (so don't change it)
    */
   public Object clone() {
      return super.clone();
   }
}
