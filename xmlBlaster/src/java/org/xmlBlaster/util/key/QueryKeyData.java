/*------------------------------------------------------------------------------
Name:      QueryKeyData.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.key;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.Constants;
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
      this(glob, null, null);
   }

   /**
    * Constructor to parse a message. 
    * @param factory If null, the default factory from Global is used.
    */
   public QueryKeyData(Global glob, I_QueryKeyFactory factory, String serialData) {
      super(glob, serialData);
      this.factory = (factory == null) ? this.glob.getQueryKeyFactory() : factory;
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
