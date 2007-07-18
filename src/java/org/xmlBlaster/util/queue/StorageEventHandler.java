/*------------------------------------------------------------------------------
Name:      StorageEventHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue;

import java.util.HashMap;
import java.util.Map;

import org.xmlBlaster.util.I_EventDispatcher;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * StorageEventHandler
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public abstract class StorageEventHandler {

   protected Map events;
   protected Map unprocessedEvents; // needed in case the EventPlugin is instantiated after the first queue
   protected Map processedEvents;
   protected I_EventDispatcher eventDispatcher;
   protected I_StorageSizeListener listener;
   public StorageEventHandler(I_StorageSizeListener listener) {
      this.listener = listener;
   }
   
   public void registerListener(I_Storage storage, EventHelper helper) throws XmlBlasterException {
      if (this.processedEvents != null) {
         synchronized(this.processedEvents) {
            if (this.processedEvents.containsKey(storage))
               return;
            helper = getMatchingEvent(helper, storage);
            if (helper != null) {
               this.processedEvents.put(storage, helper);
               storage.addStorageSizeListener(this.listener);
            }
         }
      }
      else {
         if (this.unprocessedEvents == null)
            this.unprocessedEvents = new HashMap();
         synchronized(this.unprocessedEvents) {
            this.unprocessedEvents.put(storage, helper);
         }
      }
   }
   
   private EventHelper getMatchingEvent(EventHelper helper, I_Storage storage) throws XmlBlasterException {
      if (helper == null || this.events == null)
         return null;

      synchronized(this.events) {
         EventHelper event = (EventHelper)this.events.get(helper.getKey());
         if (event != null)
            return event.getCopy(storage);
         EventHelper tmp = new EventHelper(null, helper.getType(), helper.getId2(), "*", "0");
         event = (EventHelper)this.events.get(tmp.getKey());
         if (event != null)
            return event.getCopy(storage);
         tmp = new EventHelper(null, helper.getType(), "*", helper.getId2(), "0");
         event = (EventHelper)this.events.get(tmp.getKey());
         if (event != null)
            return event.getCopy(storage);
         tmp = new EventHelper(null, helper.getType(), "*", "*", "0");
         event = (EventHelper)this.events.get(tmp.getKey());
         if (event != null)
            return event.getCopy(storage);
      }
      return null;
   }

   public abstract void registerEvent(I_EventDispatcher dispatcher, String event) throws XmlBlasterException;

   public void registerFinished() throws XmlBlasterException {
      if (this.unprocessedEvents != null) {
         synchronized (this.unprocessedEvents) {
            I_Storage[] keys = (I_Storage[])this.unprocessedEvents.keySet().toArray(new I_Storage[this.unprocessedEvents.size()]);
            this.processedEvents = new HashMap();
            synchronized(this.processedEvents) {
               for (int i=0; i < keys.length; i++) {
                  I_Storage storage = keys[i];
                  EventHelper tmpHelper = (EventHelper)this.unprocessedEvents.remove(storage);
                  registerListener(storage, tmpHelper);
               }
            }
         }
      }
   }
   
   /**
    * Enforced by I_StorageSizeListener
    * @param queue
    * @param numEntries
    * @param numBytes
    * @param isShutdown
    */
   public void changed(I_Storage storage, long numEntries, long numBytes, boolean isShutdown) {
      if (this.processedEvents == null)
         return;
      EventHelper helper = (EventHelper)this.processedEvents.get(storage);
      if (helper == null)
         return;
      if (!isShutdown && helper.shallTrigger(numEntries)) {
         String txt = "The '" + storage.getStorageId().getId() + "' has reached its treshold: '" + numEntries + "' of max '" + storage.getMaxNumOfEntries() + "' (message sent only once)";
         if (this.eventDispatcher != null) {
            String summary = "[" + new java.sql.Timestamp(System.currentTimeMillis()).toString()
             + " " + Thread.currentThread().getName()
             + " " + QueuePluginManager.class.getName() + "]";


            String description = txt;
            String eventType = helper.getEventType();
            this.eventDispatcher.dispatchEvent(summary, description, eventType);
         }
      }
   }
   
   public EventHelper generateEventHelper(StorageId storageId) throws XmlBlasterException {
      String type = storageId.getPrefix();
      String postfix = storageId.getPostfix();
      if ("history".equals(type) || "subject".equals(type) || "msgUnitStore".equals(type) || "topicStore".equals(type)) {
         int pos = postfix.lastIndexOf('/');
         if (pos > -1) {
            String id = postfix.substring(pos+1);
            return new EventHelper(null, type, id, "", "0"); // fake
         }
         else
            return null; 
      }
      else if ("callback".equals(type)) {
         int pos = postfix.lastIndexOf('/');
         if (pos > -1) {
            String sessionId = postfix.substring(pos+1);
            String tmp = postfix.substring(0, pos);
            pos = tmp.lastIndexOf('/');
            String subjectId = tmp.substring(pos+1);
            if (pos > -1) {
               return new EventHelper(null, type, subjectId, sessionId, "0"); // fake
            }
            else
               return null;
         }
         else
            return null;
      }
      else
         return null;
   }
   
   

}
