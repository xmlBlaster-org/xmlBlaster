/*------------------------------------------------------------------------------
Name:      Container.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;
import java.lang.ref.WeakReference;

/**
 * Helper holding the callback interface an some user data to be 
 * looped through.
 */
final class Container {
   private final boolean useWeakReference;
   private Object callback;
   private Object userData;
   final long creation;
   
   /** @param callback The handle to callback a client (is checked already to be not null) */
   Container(boolean useWeakReference, I_Timeout callback, Object userData) {
      this.useWeakReference = useWeakReference;
      if (this.useWeakReference) {
         this.callback = new WeakReference(callback);
         if (userData != null) 
            this.userData = new WeakReference(userData);
      }
      else {
         this.callback = callback;
         this.userData = userData;
      }
      this.creation = System.currentTimeMillis();
   }

   /** @return The callback handle can be null for weak references */
   I_Timeout getCallback() {
      if (this.useWeakReference) {
         WeakReference weak = (WeakReference)this.callback;
         return (I_Timeout)weak.get();
      }
      else {
         return (I_Timeout)this.callback;
      }
   }
   /** @return The userData, can be null for weak references */
   Object getUserData() {
      if (this.userData == null) {
         return null;
      }
      if (this.useWeakReference) {
         WeakReference weak = (WeakReference)this.userData;
         return weak.get();
      }
      else {
         return this.userData;
      }
   }

   void reset() {
      if (this.callback != null && useWeakReference) {
         ((WeakReference)this.callback).clear();
      }
      this.callback = null;

      if (this.userData != null && useWeakReference) {
         ((WeakReference)this.userData).clear();
      }
      this.userData = null;
   }
}


