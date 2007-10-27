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
 * StorageEventHandler is base class for MapEventHandler and QueueEventHandler.
 * There is exactly zero or one instance per StoragePluginManager,
 * it hold all EventHelper instances (one for each configured rule for each EventPlugin)
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public abstract class StorageEventHandler {

   protected Map/*<String(eventHelper.getKey(), EventHelper>*/ eventsHelperMap;
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

   public void removeListener(I_Storage storage) {
      // TODO !!!!!!!!!!!!!!!!
      System.out.println("!!!!!!Not implemented to remove storage eventlistener " + storage.getStorageId().toString());
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

   public boolean hasRegisteredEventHelpers() {
      synchronized(this.eventsHelperMap) {
         return this.eventsHelperMap.size() > 0;
      }
   }

   // TODO: entries of this.eventsHelperMap never removed!!!
   private EventHelper getMatchingEvent(EventHelper helper, I_Storage storage) throws XmlBlasterException {
      if (helper == null || this.eventsHelperMap == null)
         return null;

      synchronized(this.eventsHelperMap) {
         EventHelper eventHelper = (EventHelper)this.eventsHelperMap.get(helper.getKey());
         if (eventHelper != null)
            return eventHelper.getCopy(storage);
         EventHelper tmp = new EventHelper(null, helper.getType(), helper.getId2(), "*", "0", eventDispatcher);
         eventHelper = (EventHelper)this.eventsHelperMap.get(tmp.getKey());
         if (eventHelper != null)
            return eventHelper.getCopy(storage);
         tmp = new EventHelper(null, helper.getType(), "*", helper.getId2(), "0", eventDispatcher);
         eventHelper = (EventHelper)this.eventsHelperMap.get(tmp.getKey());
         if (eventHelper != null)
            return eventHelper.getCopy(storage);
         tmp = new EventHelper(null, helper.getType(), "*", "*", "0", eventDispatcher);
         eventHelper = (EventHelper)this.eventsHelperMap.get(tmp.getKey());
         if (eventHelper != null)
            return eventHelper.getCopy(storage);
      }
      return null;
   }

   /**
    * Base class fills this.eventsHelperMap
    * @param dispatcher
    * @param event
    * @throws XmlBlasterException
    */
   public abstract void registerEvent(I_EventDispatcher dispatcher, String event) throws XmlBlasterException;

   /**
    * Callback from StoragePluginManager on registration
    * @param storageMap The caller has synchronized the storageMap
    * @throws XmlBlasterException
    */
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
