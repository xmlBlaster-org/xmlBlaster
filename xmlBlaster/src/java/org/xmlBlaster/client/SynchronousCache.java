/*------------------------------------------------------------------------------
Name:      BlasterCache.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.qos.GetQos;

import java.util.*;

/**
 * Caches the messages updated from xmlBlaster.
 * <p />
 * It is used to allow local (client side) cached messages
 * which you can access with the <strong>synchronous</strong>
 * getCached() method.
 * <p />
 * If XmlBlasterAccess has switched this cache on,
 * a getCached() automatically makes a subscribe() behind the scenes as well
 * and subsequent getCached() are high performing local calls.
 * @author konrad.krafft@doubleslash.de
 * @author xmlblaster@marcelruff.info
 * @see org.xmlBlaster.client.XmlBlasterAccess#getCached(GetKey, GetQos)
 * @see org.xmlBlaster.test.client.TestSynchronousCache
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.cache.html">client.cache requirement</a>
 */
public final class SynchronousCache
{
   private static final String ME = "SynchronousCache";
   private final Global glob;

   /** key==getQueryString(GetKey getKey), value=subscriptionId */
   private Hashtable query2SubId = null;
   /**
    * key==subscriptionId, value=dataHashtable<br />
    * And dataHashtable key=keyOid, value=MsgUnit
    */
   private Hashtable subscriptions = null;
   private int maxQueriesCached = 0;


   /**
    * Create a cache instance. 
    */
   public SynchronousCache(Global glob, int maxQueriesCached) {
      this.glob = glob;
      this.query2SubId = new Hashtable();
      this.subscriptions = new Hashtable();
      this.maxQueriesCached = maxQueriesCached;
   }

   /**
    * Remove a cache entry with the given subscriptionId. 
    * <p>
    * This is usually called by the update() ERASE event
    * </p>
    */
   public void removeEntry(String subId) {
      Hashtable dataHashtable = (Hashtable)this.subscriptions.remove(subId);
      if (dataHashtable == null) {
         System.err.println("Expected to remove subId=" + subId + " " + toXml(""));
         return;
      }
      synchronized (dataHashtable) {
         dataHashtable.clear();
      }

      String query = getQueryString(subId);
      if (query != null) {
         this.query2SubId.remove(query);
      }
      else {
         System.err.println("Expected to remove subId=" + subId + " from query2SubId: " + toXml(""));
      }
   }

   /**
    * Remove a MsgUnit from cache with the given query key string and keyOid. 
    * <p>
    * This is usually called by the update() ERASE event for XPATH queries,
    * when the last oid disappears the cache entry is removed
    * </p>
    */
   public void removeEntryByQueryString(String query, String keyOid) {
      String subId = (String)this.query2SubId.get(query);
      if (subId != null) {
         Hashtable dataHashtable = (Hashtable)this.subscriptions.get(subId);
         if (dataHashtable != null) {
            synchronized (dataHashtable) {
               dataHashtable.remove(keyOid);
               if (dataHashtable.size() < 1) {
                  this.subscriptions.remove(subId);
                  this.query2SubId.remove(query);
               }
            }
         }
      }
   }

   /**
    * Remove a cache entry with the given query key string. 
    * <p>
    * This is usually called by the update() ERASE event for EXACT queries.
    * </p>
    */
   public void removeEntryByQueryString(String query) {
      String subId = (String)this.query2SubId.remove(query);
      if (subId != null) {
         Hashtable dataHashtable = (Hashtable)this.subscriptions.remove(subId);
         if (dataHashtable != null) {
            synchronized (dataHashtable) {
               dataHashtable.clear();
            }
         }
      }
   }

   /**
    * Access the query key for a given subscriptionId. 
    *
    * Slow linear lookup ...
    * @return null if not found
    */
   private String getQueryString(String subscriptionId) {
      Enumeration queryEnum = this.query2SubId.keys();
      while (queryEnum.hasMoreElements()) {
         String query = (String)queryEnum.nextElement();
         String tmpSubscriptionId = (String)this.query2SubId.get(query);
         if (tmpSubscriptionId.equals(subscriptionId)) {
            return query;
         }
      }
      return null;
   }

   /**
    * Updated the cache (add a new entry or replaces an existing or removes one). 
    * @return true if message was for cache, false if we are not interested in such a message. 
    */
   public boolean update(String subId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      Object obj = this.subscriptions.get(subId);
      if(obj == null) {
         return false;
      }
      if (updateQos.isErased()) {
         String query = getQueryString(subId);
         if (query != null) {
            if (query.startsWith(Constants.EXACT)) {
               removeEntryByQueryString(query);
               return true;
            }
            else {
               removeEntryByQueryString(query, updateKey.getOid());
               return true;
            }
         }
         else
            return true;
      }
      Hashtable dataHashtable = (Hashtable)obj;
      synchronized (dataHashtable) {
         dataHashtable.put(updateKey.getOid(), new MsgUnit(updateKey.getData(), content, updateQos.getData()));
      }
      return true;
   }

   /**
    * Access messages from cache
    * @return null if no messages are found in cache
    */
   public MsgUnit[] get(GetKey getKey, GetQos getQos) throws XmlBlasterException {
      MsgUnit[] messageUnits = null;

      //Look into cache if xmlKey is already there
      String subId = (String)this.query2SubId.get(getQueryString(getKey));

      //if yes, return the content of the cache entry
      if(subId != null) {
         Hashtable dataHashtable = (Hashtable)this.subscriptions.get(subId);
         if (dataHashtable != null) {
            synchronized (dataHashtable) {
               messageUnits = new MsgUnit[dataHashtable.size()];
               int i = 0;
               Enumeration values = dataHashtable.elements();
               while (values.hasMoreElements()) {
                  messageUnits[i] = (MsgUnit)values.nextElement();
                  i++;
               }
            }
         }
      }

      return messageUnits;
   }

   /**
    * Create a unique key for our Hashtable from a GetKey
    */
   public String getQueryString(GetKey getKey) {
      if (getKey.getData().isExact()) {
         return Constants.EXACT+getKey.getOid();
      }
      else {
         return getKey.getData().getQueryType()+getKey.getData().getQueryString().trim();
      }
   }

   /**
    * Creates an new entry in the cache
    * <p />
    * @return true - entry has been created
    *         false- cache is full
    */
   public boolean newEntry(String subId, GetKey getKey, MsgUnit[] units) throws XmlBlasterException {
      String query = getQueryString(getKey);
      if (this.query2SubId.get(query) != null)
         return true; // Existed already

      if(this.query2SubId.size() < this.maxQueriesCached) {
         this.query2SubId.put(query, subId);
         Hashtable dataHashtable = new Hashtable();
         for( int i = 0; i < units.length; i++ )
            dataHashtable.put(units[i].getKeyOid(), units[i]);
         this.subscriptions.put(subId, dataHashtable);
         return true;
      }
      else
         return false;
   }

   /**
    * Destroy all entries in the cash
    */
   public synchronized void clear() {
      this.query2SubId.clear();
      this.subscriptions.clear();
   }

   /**
    * Return how full is this cache. 
    */
   public int getNumQueriesCached() {
      return this.subscriptions.size();
   }

   /**
    * Return the registered subscriptions, for internal use only (to check cache). 
    * @return key==getQueryString(GetKey getKey), value=subscriptionId
    */
   public Hashtable getSubscriptions() {
      return this.subscriptions;
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of SynchronousCache as a XML ASCII string
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(1024);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<SynchronousCache maxQueriesCached='").append(this.maxQueriesCached).append("'>");
      Enumeration subIdEnum = this.subscriptions.keys();
      while (subIdEnum.hasMoreElements()) {
         String subscriptionId = (String)subIdEnum.nextElement();
         Hashtable hash = (Hashtable)this.subscriptions.get(subscriptionId);
         sb.append(offset).append("  <subscribe id='").append(subscriptionId).append("'/>");
      }
      Enumeration queryEnum = this.query2SubId.keys();
      while (queryEnum.hasMoreElements()) {
         String query = (String)queryEnum.nextElement();
         String subscriptionId = (String)this.query2SubId.get(query);
         sb.append(offset).append("  <query id='").append(query);
         sb.append("' subscriptionId='").append(subscriptionId).append("'/>");
      }
      sb.append(offset).append("</SynchronousCache>");

      return sb.toString();
   }
} // SynchronousCache
