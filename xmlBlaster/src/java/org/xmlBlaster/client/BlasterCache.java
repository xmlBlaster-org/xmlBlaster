/*------------------------------------------------------------------------------
Name:      BlasterCache.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to cache messages from xmlBlaster.
Version:   $Id: BlasterCache.java,v 1.6 2000/06/18 15:21:58 ruff Exp $
Author:    konrad.krafft@doubleslash.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.jutils.log.Log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnitContainer;

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
   private CorbaConnection corbaConnection   = null;
   private int size                          = 0;


   public BlasterCache(CorbaConnection corbaConnection, int size)
   {
      this.corbaConnection = corbaConnection;
      query2SubId = new Hashtable();
      subscriptions = new Hashtable();
      this.size = size;
   }

   public void addSubscription(String query, String subId)
   {
      if(Log.CALLS) Log.calls(ME,"Adding new subscription to BlasterCache(query="+query+", subId="+subId+")");
      query2SubId.put(query,subId);
      subscriptions.put(subId, new Hashtable());
   }


   public boolean update(String subId, String xmlKey, byte[] content)
   {
      if(Log.CALLS) Log.calls(ME,"Entering update of BlasterCache for subId="+subId);
      Object obj = subscriptions.get( subId );
      if( obj == null ) {
         Log.info(ME, "No subscriptionId('"+subId+"') found in BlasterCache.");
         return false;
      }
      else {
         Hashtable messages = (Hashtable) obj;
         messages.put(xmlKey, content);
         return true;
      }
   }


   public MessageUnitContainer[] get( String xmlKey, String xmlQos ) throws XmlBlasterException
   {
      MessageUnitContainer[] units = null;

      //Look into cache if xmlKey is already there
      String subId = (String)query2SubId.get(xmlKey);

      //if yes, return the content of the cache entry
      if(subId != null) {
         Hashtable messages = (Hashtable)subscriptions.get( subId );
         units = new MessageUnitContainer[messages.size()];
         int i = 0;
         Enumeration keys = messages.keys();
         Enumeration content = messages.elements();
         while( keys.hasMoreElements() && content.hasMoreElements() ) {
            units[i] = new MessageUnitContainer();
            units[i].msgUnit = new MessageUnit();
            units[i].msgUnit.xmlKey=(String)keys.nextElement();
            units[i].msgUnit.content=(byte[])content.nextElement();
            units[i].qos="<qos></qos>";
            i++;
         }
      }

      return units;
   }


   /**
    * creates an new entry in the cache
    * <p />
    * @return true - entry has been created
    *         false- cache is full
    */
   public boolean newEntry( String subId, String xmlKey, MessageUnitContainer[] units )
   {
      if(query2SubId.size() < size) {
         addSubscription( xmlKey, subId );
         for( int i = 0; i < units.length; i++ )
            update( subId, units[i].msgUnit.xmlKey, units[i].msgUnit.content );
         return true;
      }
      else
         return false;
   }
} // BlasterCache
