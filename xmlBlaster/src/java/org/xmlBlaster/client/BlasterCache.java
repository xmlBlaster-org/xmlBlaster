/*------------------------------------------------------------------------------
Name:      BlasterCache.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;

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
 * @author konrad.krafft@doubleslash.de
 * @deprecated Please use SynchronousCache instead
 */
public class BlasterCache
{
   private static final String ME = "BlasterCache";
   private final Global glob;

   private Hashtable query2SubId             = null;
   private Hashtable subscriptions           = null;
   private int size                          = 0;


   public BlasterCache(Global glob, int size)
   {
      this.glob = glob;
      query2SubId = new Hashtable();
      subscriptions = new Hashtable();
      this.size = size;
   }

   public void addSubscription(String query, String subId)
   {
      query2SubId.put(query,subId);
      subscriptions.put(subId, new Hashtable());
   }


   public boolean update(String subId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException
   {
      return update(subId, updateKey.toXml(), content, updateQos.toXml());
   }
   public boolean update(String subId, String updateKey, byte[] content, String updateQos) throws XmlBlasterException
   {
      Object obj = subscriptions.get( subId );
      if( obj == null ) {
         return false;
      }
      else {
         Hashtable messages = (Hashtable) obj;
         messages.put(subId, new MsgUnit(updateKey, content, updateQos));
         return true;
      }
   }

   public MsgUnit[] get( String xmlKey, String xmlQos ) throws XmlBlasterException
   {
      MsgUnit[] messageUnits = null;

      //Look into cache if xmlKey is already there
      String subId = (String)query2SubId.get(xmlKey);

      //if yes, return the content of the cache entry
      if(subId != null) {
         Hashtable messages = (Hashtable)subscriptions.get( subId );
         messageUnits = new MsgUnit[messages.size()];
         int i = 0;
         Enumeration keys = messages.keys();
         Enumeration values = messages.elements();
         while( keys.hasMoreElements() && values.hasMoreElements() ) {
            messageUnits[i] = (MsgUnit)values.nextElement();
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
   public boolean newEntry( String subId, String xmlKey, MsgUnit[] units ) throws XmlBlasterException
   {
      if(query2SubId.size() < size) {
         addSubscription( xmlKey, subId );
         for( int i = 0; i < units.length; i++ )
            update( subId, units[i].getKey(), units[i].getContent(), units[i].getQos() );
         return true;
      }
      else
         return false;
   }
} // BlasterCache
