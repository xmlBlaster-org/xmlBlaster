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
import org.xmlBlaster.util.def.Constants;

/**
 * StorageEventHandler is base class for MapEventHandler and QueueEventHandler.
 * There is exactly zero or one instance per StoragePluginManager,
 * it hold all EventHelper instances (one for each configured rule for each EventPlugin)
 *
 * TODO: Should be one for each EventPlugin to support multiple eventPlugins using thresholds
 * as the EventPlugin must be the listener for the events (to transport them to the configured sinks)
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public abstract class StorageEventHandler {

   /**
    * Contains the configured eventTypes from EventPlugin (typically one only)
    */
   protected Map/*<String(eventHelper.getKey(), EventHelper>*/ wantedEventsMap = new HashMap();

   protected I_EventDispatcher eventDispatcher;

   public final static String EVENT_HANDLER = "eventHandler";

   public StorageEventHandler(I_EventDispatcher dispatcher) {
      this.eventDispatcher = dispatcher;
   }

   public void registerListener(I_Storage storage) throws XmlBlasterException {
      StorageId storageId = storage.getStorageId();
      EventHelper helper = generateEventHelper(storageId); // The perfect match (no wildcard) for this storageId
      helper = getMatchingEvent(helper, storage);
      if (helper != null)
         storage.addStorageSizeListener(helper);
   }

   public void removeListener(I_Storage storage) {
      // TODO Do we need to cleanup any map with EventHelpers?
      //System.out.println("!!!!!!Not implemented to remove storage eventlistener " + storage.getStorageId().toString());
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
      synchronized(this.wantedEventsMap) {
         return this.wantedEventsMap.size() > 0;
      }
   }

   public EventHelper[] getEventHelpers() {
      synchronized(this.wantedEventsMap) {
         return (EventHelper[])this.wantedEventsMap.values().toArray(new EventHelper[this.wantedEventsMap.size()]);
      }
   }

   public void unRegisterEventHelpers(I_EventDispatcher l) {
      EventHelper[] arr = getEventHelpers();
      for (int i=0; i<arr.length; i++) {
         EventHelper helper = arr[i];
         if (helper.getEventDispatcher() == l) {
            synchronized(this.wantedEventsMap) {
               this.wantedEventsMap.remove(helper.getKey());
            }
         }
      }
   }

   /**
    * Check if the given storageId matches any of the wanted events.
    *
    * TODO: entries of this.wantedEventsMap never removed!!!
    *
    * @param helper The exact matching EventHelper (no wildcards) which wants to be notified
    * @param storage The storage to check
    * @return null if storage is not of interest
    * @throws XmlBlasterException
    */
   private EventHelper getMatchingEvent(EventHelper helper, I_Storage storage) throws XmlBlasterException {
      if (helper == null)
         return null;

      synchronized(this.wantedEventsMap) {
         if (this.wantedEventsMap.size() == 0)
            return null;
         EventHelper eventHelper = (EventHelper)this.wantedEventsMap.get(helper.getKey());
         if (eventHelper != null)
            return eventHelper.getCopy(storage);
         EventHelper tmp = new EventHelper(null, helper.getType(), helper.getId2(), "*", "0", eventDispatcher);
         eventHelper = (EventHelper)this.wantedEventsMap.get(tmp.getKey());
         if (eventHelper != null)
            return eventHelper.getCopy(storage);
         tmp = new EventHelper(null, helper.getType(), "*", helper.getId2(), "0", eventDispatcher);
         eventHelper = (EventHelper)this.wantedEventsMap.get(tmp.getKey());
         if (eventHelper != null)
            return eventHelper.getCopy(storage);
         tmp = new EventHelper(null, helper.getType(), "*", "*", "0", eventDispatcher);
         eventHelper = (EventHelper)this.wantedEventsMap.get(tmp.getKey());
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
   public abstract void registerEventType(I_EventDispatcher dispatcher, String event) throws XmlBlasterException;

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

   /**
    * Create an EventHelper instance which matches exactly the given storageId
    * (no wildcards).
    * 
    * @param storageId
    * @return null if unknown storageId format
    * @throws XmlBlasterException
    */
   public EventHelper generateEventHelper(StorageId storageId) throws XmlBlasterException {
      String relating = storageId.getXBStore().getType();
      if (Constants.RELATING_HISTORY.equals(relating) || Constants.RELATING_SUBJECT.equals(relating)
            || Constants.RELATING_MSGUNITSTORE.equals(relating) || Constants.RELATING_TOPICSTORE.equals(relating)) {
         if (storageId.getPostfix1().length() > 0)
            return new EventHelper(null, relating, storageId.getPostfix1(), "", "0", eventDispatcher); // fake
      }
      else if (Constants.RELATING_CALLBACK.equals(relating) || Constants.RELATING_CLIENT.equals(relating)) {
         if (storageId.getPostfix2().length() > 0)
            return new EventHelper(null, relating, storageId.getPostfix1(), storageId.getPostfix2(), "0", eventDispatcher); // fake
      }
      return null;
   }

}
