/*------------------------------------------------------------------------------
Name:      UpdateDispatcher.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Global;
import java.util.HashMap;
import java.util.Set;

/**
 * Dispatches callback messages depending on the subscriptionId to the specific client listener. 
 * @author xmlBlaster@marcelruff.info
 */
public final class UpdateDispatcher
{
   private static final String ME = "UpdateDispatcher";
   private final Global glob;
   /** This map contains the registered callback interfaces for given subscriptions.
       The key is the subscription ID, the value is the I_Callback instance */
   private final HashMap callbackMap;

   public UpdateDispatcher(Global glob) {
      this.glob = glob;
      this.callbackMap = new HashMap();
   }

   /**
    * Register a callback interface with the given subscriptionId
    */
   public synchronized void addCallback(String subscriptionId, I_Callback callback) {
      if (subscriptionId == null) {
         throw new IllegalArgumentException("Null argument not allowed in addCallback: subscriptionId=" + subscriptionId + " callback=" + callback);
      }
      this.callbackMap.put(subscriptionId, callback);
   }

   /**
    * Access a callbacl interface for the given subscriptionId
    * @return null if not found
    */
   public synchronized I_Callback getCallback(String subscriptionId) {
      if (subscriptionId == null) {
         throw new IllegalArgumentException("The subscriptionId is null");
      }
      return (I_Callback)this.callbackMap.get(subscriptionId);
   }
   
   /**
    * Returns a current snapshot (shallow copy). 
    */
   public synchronized I_Callback[] getCallbacks() {
      Set values = this.callbackMap.entrySet();
      if (values == null || values.size() < 1) {
         return new I_Callback[0];
      }
      return (I_Callback[])values.toArray(new I_Callback[values.size()]);
   }

   /**
    * Returns a current snapshot. 
    */
   public synchronized String[] getSubscriptionIds() {
      Set keys = this.callbackMap.keySet();
      if (keys == null || keys.size() < 1) {
         return new String[0];
      }
      return (String[])keys.toArray(new String[keys.size()]);
   }

   /**
    * Remove the callback interface for the given subscriptionId
    * @return the removed entry of null if subscriptionId is unknown
    */
   public synchronized I_Callback removeCallback(String subscriptionId) {
      return (I_Callback)this.callbackMap.remove(subscriptionId);
   }

   /**
    * Get number of callback registrations
    */
   public synchronized int size() {
      return this.callbackMap.size();
   }
   /**
    * Remove all callback registrations
    */
   public synchronized void clear() {
      this.callbackMap.clear();
   }
}
