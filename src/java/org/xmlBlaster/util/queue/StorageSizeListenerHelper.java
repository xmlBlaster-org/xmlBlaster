/*------------------------------------------------------------------------------
Name:      StorageSizeListenerHelper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * StorageSizeListenerHelper
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class StorageSizeListenerHelper {
   
   private ArrayList storageSizeListeners;
   private Object storageSizeListenersSync = new Object();
   private I_Storage storage;
   private final static Logger log = Logger.getLogger(StorageSizeListenerHelper.class.getName());
   
   public StorageSizeListenerHelper(I_Storage storage) {
      this.storage = storage;
   }
   
   /**
    * @see I_Queue#addStorageSizeListener(I_StorageSizeListener)
    */
   public void addStorageSizeListener(I_StorageSizeListener listener) {
      if (listener == null) 
         throw new IllegalArgumentException(storage.getStorageId().getId() + ": addStorageSizeListener(null) is not allowed");
      synchronized(this.storageSizeListenersSync) {
         if (this.storageSizeListeners == null)
            this.storageSizeListeners = new ArrayList();
         this.storageSizeListeners.add(listener);
      }
   }
   
   /**
    * @see I_Queue#removeStorageSizeListener(I_StorageSizeListener)
    */
   public void removeStorageSizeListener(I_StorageSizeListener listener) {
      synchronized(this.storageSizeListenersSync) {
         if (listener == null) this.storageSizeListeners = null;
         else {
            if ((this.storageSizeListeners) == null)
               return;
            if (!this.storageSizeListeners.remove(listener))
               log.warning("removeStorageSizeListener: could not remove listener '" + listener.toString() + "' since not registered");

            if (this.storageSizeListeners.size() == 0) 
               this.storageSizeListeners = null;
         }
      }
   }
   
   public final void invokeStorageSizeListener() {
      if (this.storageSizeListeners != null) {
         I_StorageSizeListener[] listeners = null;
         synchronized(this.storageSizeListenersSync) {
             listeners = (I_StorageSizeListener[])this.storageSizeListeners.toArray(new I_StorageSizeListener[this.storageSizeListeners.size()]);
         }
         for (int i=0; i < listeners.length; i++) {
            try {
               listeners[i].changed(storage, storage.getNumOfEntries(), storage.getNumOfBytes(), storage.isShutdown());
            }
            catch (NullPointerException e) {
               if (log.isLoggable(Level.FINE)) log.fine("invokeStorageSizeListener() call is not possible as another thread has removed storageSizeListeners, this is OK to prevent a synchronize.");
            }
         }
      }
   }

   /**
    * @see I_Queue#hasStorageSizeListener(I_StorageSizeListener)
    */
   public boolean hasStorageSizeListener(I_StorageSizeListener listener) {
      if (listener == null)
         return this.storageSizeListeners != null;
      else {
         synchronized(this.storageSizeListenersSync) {
            if (this.storageSizeListeners == null) return false;
            return this.storageSizeListeners.contains(listener);
         }
      }
   }

   /**
    * @see I_Queue#getStorageSizeListeners()
    */
   public I_StorageSizeListener[] getStorageSizeListeners() {
      if (this.storageSizeListeners == null)
         return new I_StorageSizeListener[0];
      synchronized (this.storageSizeListenersSync) {
         int size = storageSizeListeners.size();
         return (I_StorageSizeListener[])storageSizeListeners.toArray(new I_StorageSizeListener[size]);
      }
   }

}
