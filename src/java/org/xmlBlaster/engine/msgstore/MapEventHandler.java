package org.xmlBlaster.engine.msgstore;

import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.util.I_EventDispatcher;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.queue.EventHelper;
import org.xmlBlaster.util.queue.StorageEventHandler;

public class MapEventHandler extends StorageEventHandler {
   private ServerScope serverScope;

   public MapEventHandler(ServerScope serverScope, I_EventDispatcher dispatcher) {
      super(dispatcher);
      this.serverScope = serverScope;
   }

   /**
    * Is called from EventPlugin to register the configured eventTypes
    * @param eventType e.g. "topic/[topicId]/persistence/msgUnitStore/event/threshold.90%"
    */
   public void registerEventType(I_EventDispatcher dispatcher, String eventType)
         throws XmlBlasterException {
      // threshold is for entries only (not bytes nor cachedEntries)

      // topic/[topicId]/persistence/msgUnitStore/event/threshold.90%

      // map/topicStore/event/threshold.90%
      // map/session/event/threshold.80
      // map/subscribe/event/threshold.99%

      // */persistence/*/event/threshold*

      String end = "/event/threshold.";
      int index = eventType.lastIndexOf(end);
      String value = eventType.substring(index + end.length());

      String tmp = eventType.substring(0, index);
      end = "/persistence/";
      index = tmp.lastIndexOf(end);
      String type = tmp.substring(index + end.length());
      String id1 = null;
      String id2 = null;
      // TODO THE OTHER MAPS SUCH AS topicStore, session + subscribe
      if (Constants.RELATING_MSGUNITSTORE.equals(type)) { // we need only the
                                                            // topicId
         // topic/[topicId]/persistence/msgUnitStore/event/threshold.90%
         //
         tmp = tmp.substring(0, index);
         // topicId
         end = "/";
         index = tmp.lastIndexOf(end);
         id1 = tmp.substring(index + end.length());
         id2 = "";
      } else {
         throw new XmlBlasterException(this.serverScope,
               ErrorCode.USER_CONFIGURATION,
               "StoragePluginManager.registerEvent", "event '" + eventType
                     + "' is not supported");
      }

      if (this.eventDispatcher == null) // ??
         this.eventDispatcher = dispatcher;
      EventHelper helper = new EventHelper(eventType, type, id1, id2, value,
            this.eventDispatcher);
      synchronized (this.wantedEventsMap) {
         this.wantedEventsMap.put(helper.getKey(), helper);
      }
   }
}
