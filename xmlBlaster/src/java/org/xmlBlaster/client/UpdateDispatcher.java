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
   public void addCallback(String subscriptionId, I_Callback callback) {
      if (subscriptionId == null) {
         throw new IllegalArgumentException("Null argument not allowed in addCallback: subscriptionId=" + subscriptionId + " callback=" + callback);
      }
      callbackMap.put(subscriptionId, callback);
   }

   /**
    * Access a callbacl interface for the given subscriptionId
    * @return null if not found
    */
   public I_Callback getCallback(String subscriptionId) {
      if (subscriptionId == null) {
         throw new IllegalArgumentException("The subscriptionId is null");
      }
      synchronized (callbackMap) {
         return (I_Callback)callbackMap.get(subscriptionId);
      }
   }
   
   /**
    * Returns a current snapshot (shallow copy). 
    */
   public I_Callback[] getCallbacks() {
      Set values = null;
      synchronized (callbackMap) {
         values = callbackMap.entrySet();
      }
      if (values == null || values.size() < 1) {
         return new I_Callback[0];
      }
      return (I_Callback[])values.toArray(new I_Callback[values.size()]);
   }

   /**
    * Remove the callback interface for the given subscriptionId
    * @return the removed entry of null if subscriptionId is unknown
    */
   public I_Callback removeCallback(String subscriptionId) {
      return (I_Callback)callbackMap.remove(subscriptionId);
   }

   /**
    * Remove all callback registrations
    */
   public void clear() {
      callbackMap.clear();
   }
}
