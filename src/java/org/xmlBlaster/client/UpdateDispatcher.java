/*------------------------------------------------------------------------------
Name:      UpdateDispatcher.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBuffer;

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
   public synchronized void addCallback(String subscriptionId, I_Callback callback, boolean isPersistent) {
      if (subscriptionId == null) {
         throw new IllegalArgumentException("Null argument not allowed in addCallback: subscriptionId=" + subscriptionId + " callback=" + callback);
      }
      this.callbackMap.put(subscriptionId, new CallbackInfo(callback, isPersistent));
   }

   /**
    * Access a callback interface for the given subscriptionId
    * @return null if not found
    */
   public synchronized I_Callback getCallback(String subscriptionId) {
      if (subscriptionId == null) {
         throw new IllegalArgumentException("The subscriptionId is null");
      }
      CallbackInfo info = (CallbackInfo)this.callbackMap.get(subscriptionId);
      
      if (info == null) {
         String tmp = XmlBuffer.unEscapeXml(subscriptionId);
         info = (CallbackInfo)this.callbackMap.get(tmp);
      }
      
      return (info == null) ? null: info.callback;
   }
   
   /**
    * Mark that a subscribe() invocation returned (is the server ACK). 
    * @param subscriptionId The subcribe
    */
   public synchronized void ackSubscription(String subscriptionId) {
      if (subscriptionId == null) {
         throw new IllegalArgumentException("The subscriptionId is null");
      }
      CallbackInfo info = (CallbackInfo)this.callbackMap.get(subscriptionId);
      if (info != null) info.ack = true;
   }
   
   /**
    * Returns a current snapshot (shallow copy). 
    */
   public synchronized I_Callback[] getCallbacks() {
      Set values = this.callbackMap.entrySet();
      if (values == null || values.size() < 1) {
         return new I_Callback[0];
      }
      CallbackInfo[] infoArr = (CallbackInfo[])values.toArray(new CallbackInfo[values.size()]);
      I_Callback[] cbArr = new I_Callback[infoArr.length];
      for (int i=0; i<infoArr.length; i++) {
         cbArr[i] = infoArr[i].callback;
      }
      return cbArr;
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
      CallbackInfo info = (CallbackInfo)this.callbackMap.remove(subscriptionId);
      return (info == null) ? null : info.callback;
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

   /**
    * Clear all subscribes which where marked as acknowledged
    * @return number of removed entries
    */
   public synchronized int clearAckNonPersistentSubscriptions() {
      String[] ids = getSubscriptionIds();
      int count = 0;
      for (int i=0; i<ids.length; i++) {
         CallbackInfo info = (CallbackInfo)this.callbackMap.get(ids[i]);
         if (info != null && info.ack && !info.isPersistent()) {
            this.callbackMap.remove(ids[i]);
            count++;
         }
      }
      return count;
   }

   /**
    * Clear all subscribes which where not marked as acknowledged
    */
   public synchronized void clearNAKSubscriptions() {
      String[] ids = getSubscriptionIds();
      for (int i=0; i<ids.length; i++) {
         CallbackInfo info = (CallbackInfo)this.callbackMap.get(ids[i]);
         if (info != null && info.ack == false) {
            this.callbackMap.remove(ids[i]);
         }
      }
   }

   /**
    * Inner helper class to hold ACK/NAK state of subscriptions. 
    * We need this to be able to garbage collect map entries on
    * reconnect to a different session.
    */
   class CallbackInfo {
      I_Callback callback;
      boolean persistent;
      boolean ack = false; // Is the subscribe acknowledged from the server (the subscribe() method returned?)
      CallbackInfo(I_Callback callback, boolean persistent) {
         this.callback = callback;
         this.persistent = persistent;
      }
      boolean isPersistent() {
         return this.persistent;
      }
   };
}
