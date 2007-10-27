package org.xmlBlaster.util.queue;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_EventDispatcher;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;

public class QueueEventHandler extends StorageEventHandler {

   private Global global;
   public QueueEventHandler(Global global, I_EventDispatcher dispatcher) {
      super(dispatcher);
      this.global = global;
   }

   /**
    * Is called from EventPlugin to register the configured eventTypes
    */
   public void registerEventType(I_EventDispatcher dispatcher, String eventType) throws XmlBlasterException {
      // client/*/session/[publicSessionId]/queue/callback/event/threshold.90%
      // client/[subjectId]/session/[publicSessionId]/queue/callback/event/threshold.90%
      // topic/[topicId]/queue/history/event/threshold.90%
      // */queue/*/event/threshold*

      String end = "/event/threshold.";
      int index = eventType.lastIndexOf(end);
      String value = eventType.substring(index + end.length());

      String tmp = eventType.substring(0, index);
      end = "/queue/";
      index = tmp.lastIndexOf(end);
      String type = tmp.substring(index + end.length());
      String id1 = null;
      String id2 = null;
      if (Constants.RELATING_HISTORY.equals(type)) { // we need only the topicId
         // topic/[topicId]/queue/history/event/threshold.90%
         tmp = tmp.substring(0, index);
         // sessionId or topicId or subjectId
         end = "/";
         index = tmp.lastIndexOf(end);
         id1 = tmp.substring(index + end.length());
         id2 = "";
      }
      else if (Constants.RELATING_CALLBACK.equals(type)) {
         // client/[subjectId]/session/[publicSessionId]/queue/callback/event/threshold.90%
         tmp = tmp.substring(0, index);
         // sessionId or topicId or subjectId
         end = "/";
         index = tmp.lastIndexOf(end);
         id2 = tmp.substring(index + end.length());
         tmp = tmp.substring(0, index);
         index = tmp.lastIndexOf(end);
         if (index > -1)
            tmp = tmp.substring(0, index);
         index = tmp.lastIndexOf(end);
         if (index > -1)
            id1 = tmp.substring(index+1);
         else
            id1 = tmp;
      }
      else if (Constants.RELATING_SUBJECT.equals(type)) {
         // client/[subjectId]/queue/subject/event/threshold.66
         tmp = tmp.substring(0, index);
         end = "/";
         index = tmp.lastIndexOf(end);
         id1 = tmp.substring(index + end.length());
         id2 = "";
      }
      else {
         throw new XmlBlasterException(this.global, ErrorCode.USER_CONFIGURATION, "QueuePluginManager.registerEvent", "event '" + eventType + "' is not supported");
      }

      if (this.eventDispatcher == null)
         this.eventDispatcher = dispatcher;
      EventHelper helper = new EventHelper(eventType, type, id1, id2, value, this.eventDispatcher);
      synchronized(this.wantedEventsMap) {
         this.wantedEventsMap.put(helper.getKey(), helper);
      }
   }
}
