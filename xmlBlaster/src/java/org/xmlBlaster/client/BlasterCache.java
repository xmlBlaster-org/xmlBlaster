/*------------------------------------------------------------------------------
Name:      BlasterCache.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to cache messages from xmlBlaster.
Version:   $Id: BlasterCache.java,v 1.11 2002/05/01 21:40:00 ruff Exp $
Author:    konrad.krafft@doubleslash.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;

import java.util.*;

/**
 * Caches the messages updated from xmlBlaster.
 * <p />
 * It is used to allow local (client side) cached messages
 * which you can access with the <strong>synchronous</strong>
 * get() method.
 * <p />
 * If the CorbaConnection has switched this cache on,
 * a get() automatically makes a subscribe() behind the scenes as well
 * and subsequent get()s are high performing local calls.
 */
public class BlasterCache
{
   private static final String ME = "BlasterCache";

   private Hashtable query2SubId             = null;
   private Hashtable subscriptions           = null;
   private int size                          = 0;


   public BlasterCache(int size)
   {
      query2SubId = new Hashtable();
      subscriptions = new Hashtable();
      this.size = size;
   }

   public void addSubscription(String query, String subId)
   {
      if(Log.CALL) Log.call(ME,"Adding new subscription to BlasterCache(query="+query+", subId="+subId+")");
      query2SubId.put(query,subId);
      subscriptions.put(subId, new Hashtable());
   }


   public boolean update(String subId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      return update(subId, updateKey.toXml(), content, updateQos.toXml());
   }
   public boolean update(String subId, String updateKey, byte[] content, String updateQos)
   {
      if(Log.CALL) Log.call(ME,"Entering update of BlasterCache for subId="+subId);
      Object obj = subscriptions.get( subId );
      if( obj == null ) {
         Log.info(ME, "No subscriptionId('"+subId+"') found in BlasterCache.");
         return false;
      }
      else {
         Hashtable messages = (Hashtable) obj;
         messages.put(subId, new MessageUnit(updateKey, content, updateQos));
         return true;
      }
   }

   public MessageUnit[] get( String xmlKey, String xmlQos ) throws XmlBlasterException
   {
      MessageUnit[] messageUnits = null;

      //Look into cache if xmlKey is already there
      String subId = (String)query2SubId.get(xmlKey);

      //if yes, return the content of the cache entry
      if(subId != null) {
         Hashtable messages = (Hashtable)subscriptions.get( subId );
         messageUnits = new MessageUnit[messages.size()];
         int i = 0;
         Enumeration keys = messages.keys();
         Enumeration values = messages.elements();
         while( keys.hasMoreElements() && values.hasMoreElements() ) {
            messageUnits[i] = (MessageUnit)values.nextElement();
            i++;
         }
      }

      return messageUnits;
   }


   /**
    * creates an new entry in the cache
    * <p />
    * @return true - entry has been created
    *         false- cache is full
    */
   public boolean newEntry( String subId, String xmlKey, MessageUnit[] units )
   {
      if(query2SubId.size() < size) {
         addSubscription( xmlKey, subId );
         for( int i = 0; i < units.length; i++ )
            update( subId, units[i].xmlKey, units[i].content, units[i].qos );
         return true;
      }
      else
         return false;
   }
} // BlasterCache
