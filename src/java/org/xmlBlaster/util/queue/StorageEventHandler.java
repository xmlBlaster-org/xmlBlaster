/*------------------------------------------------------------------------------
Name:      StorageEventHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue;

import java.util.Map;

import org.xmlBlaster.util.I_EventDispatcher;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * StorageEventHandler
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public abstract class StorageEventHandler {

   protected Map events;
   protected I_EventDispatcher eventDispatcher;
   
   public final static String EVENT_HANDLER = "eventHandler";
   
   public StorageEventHandler(I_EventDispatcher dispatcher) {
      this.eventDispatcher = dispatcher;
   }
   
   public void registerListener(I_Storage storage) throws XmlBlasterException {
      StorageId storageId = storage.getStorageId();
      EventHelper helper = generateEventHelper(storageId);
      helper = getMatchingEvent(helper, storage);
      if (helper != null)
         storage.addStorageSizeListener(helper);
   }
   
   public void removeListeners(Map storageMap) throws XmlBlasterException {
      I_Storage[] storages = (I_Storage[])storageMap.values().toArray(new I_Storage[storageMap.size()]);
      for (int i=0; i < storages.length; i++) {
         I_StorageSizeListener[] listeners = storages[i].getStorageSizeListeners();
         for (int j=0; j < listeners.length; j++) {
            if (listeners[j] instanceof EventHelper)
               storages[i].removeStorageSizeListener(listeners[j]);
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
         EventHelper tmp = new EventHelper(null, helper.getType(), helper.getId2(), "*", "0", eventDispatcher);
         event = (EventHelper)this.events.get(tmp.getKey());
         if (event != null)
            return event.getCopy(storage);
         tmp = new EventHelper(null, helper.getType(), "*", helper.getId2(), "0", eventDispatcher);
         event = (EventHelper)this.events.get(tmp.getKey());
         if (event != null)
            return event.getCopy(storage);
         tmp = new EventHelper(null, helper.getType(), "*", "*", "0", eventDispatcher);
         event = (EventHelper)this.events.get(tmp.getKey());
         if (event != null)
            return event.getCopy(storage);
      }
      return null;
   }

   public abstract void registerEvent(I_EventDispatcher dispatcher, String event) throws XmlBlasterException;

   public void initialRegistration(Map storageMap) throws XmlBlasterException {
      I_Storage[] storages = (I_Storage[])storageMap.values().toArray(new I_Storage[storageMap.size()]);
      for (int i=0; i < storages.length; i++)
         registerListener(storages[i]);
   }
   
   public EventHelper generateEventHelper(StorageId storageId) throws XmlBlasterException {
      String type = storageId.getPrefix();
      String postfix = storageId.getPostfix();
      if ("history".equals(type) || "subject".equals(type) || "msgUnitStore".equals(type) || "topicStore".equals(type)) {
         int pos = postfix.lastIndexOf('/');
         if (pos > -1) {
            String id = postfix.substring(pos+1);
            return new EventHelper(null, type, id, "", "0", eventDispatcher); // fake
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
               return new EventHelper(null, type, subjectId, sessionId, "0", eventDispatcher); // fake
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
