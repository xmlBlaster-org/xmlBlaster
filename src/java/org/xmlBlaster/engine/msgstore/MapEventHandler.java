package org.xmlBlaster.engine.msgstore;

import java.util.HashMap;

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
   
   public void registerEvent(I_EventDispatcher dispatcher, String event) throws XmlBlasterException {
      // threshold is for entries only (not bytes nor cachedEntries)
      
      // topic/[topicId]/persistence/msgUnitStore/event/threshold.90%

      // map/topicStore/event/threshold.90%
      // map/session/event/threshold.80
      // map/subscribe/event/threshold.99%
      
      // */persistence/*/event/threshold*

      String end = "/event/threshold.";
      int index = event.lastIndexOf(end);
      String value = event.substring(index + end.length());

      String tmp = event.substring(0, index);
      end = "/persistence/";
      index = tmp.lastIndexOf(end);
      String type = tmp.substring(index + end.length());
      String id1 = null;
      String id2 = null;
      // TODO THE OTHER MAPS SUCH AS topicStore, session + subscribe
      if (Constants.RELATING_MSGUNITSTORE.equals(type)) { // we need only the topicId
         // topic/[topicId]/persistence/msgUnitStore/event/threshold.90%
         // 
         tmp = tmp.substring(0, index);
         // topicId
         end = "/";
         index = tmp.lastIndexOf(end);
         id1 = tmp.substring(index + end.length());
         id2 = "";
      }
      else {
         throw new XmlBlasterException(this.serverScope, ErrorCode.USER_CONFIGURATION, "StoragePluginManager.registerEvent", "event '" + event + "' is not supported");
      }
      
      if (this.events == null)
         this.events = new HashMap();
      if (this.eventDispatcher == null)
         this.eventDispatcher = dispatcher;
      EventHelper helper = new EventHelper(event, type, id1, id2, value, this.eventDispatcher);
      synchronized(this.events) {
         this.events.put(helper.getKey(), helper);
      }
   }
}

