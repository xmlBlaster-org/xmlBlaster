/*------------------------------------------------------------------------------
Name:      EventHelper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue;

import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_EventDispatcher;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

public class EventHelper implements I_StorageSizeListener {

   private final static Logger log = Logger.getLogger(EventHelper.class.getName());
   private String eventType;
   private String type;
   private String id1;
   private String id2;
   long value;
   private String key;
   private boolean procent;
   private boolean alreadyAlarmed;
   private I_EventDispatcher eventDispatcher;

   private EventHelper() {

   }

   /**
    * @param eventType
    *           "client/joe/session/1/queue/callback/event/threshold.90%"
    * @param type
    *           callback
    * @param id1
    *           joe
    * @param id2
    *           1
    * @param val
    *           90%
    * @param eventDispatcher
    * @throws XmlBlasterException
    */
   public EventHelper(String eventType, String type, String id1, String id2, String val, I_EventDispatcher eventDispatcher) throws XmlBlasterException {
      if (id2 == null)
         id2 = "";
      this.eventType = eventType;
      this.type = type;
      this.id1 = id1;
      this.id2 = id2;
      this.key = type + "/" + id1 + "/" + id2;
      int pos = val.lastIndexOf('%');
      if (pos > -1) {
         this.procent = true;
         val = val.substring(0, pos);
      }
      try {
        this.value = Long.parseLong(val);
      }
      catch (Throwable ex) {
         throw new XmlBlasterException(Global.instance(), ErrorCode.USER_CONFIGURATION, "EventHelper", "could not parse treshold string '" + val + "' to a long", ex);
      }
      if (eventDispatcher == null) {
         throw new XmlBlasterException(new Global(), ErrorCode.INTERNAL, "EventHelper: The event dispatcher can not be null");
      }

      this.eventDispatcher = eventDispatcher;

   }

   protected Object clone() {
      EventHelper helper = new EventHelper();
      helper.type = this.type;
      helper.id1 = this.id1;
      helper.id2 = this.id2;
      helper.key = this.key;
      helper.value = this.value;
      helper.procent = this.procent;
      helper.eventType = this.eventType;
      helper.eventDispatcher = this.eventDispatcher;
      helper.alreadyAlarmed = this.alreadyAlarmed;
      return helper;
   }

   public EventHelper getCopy(I_Storage storage) {
      EventHelper ret = (EventHelper)clone();
      long maxValue = 1L;
      if (storage != null)
         maxValue = storage.getMaxNumOfEntries();
      if (maxValue < 0L)
         maxValue = 1L;
      if (ret.procent)
         ret.value = (long)(0.01 * ret.value * maxValue);
      if (ret.value > maxValue) {
         log.warning("The treshold for queue '" + storage.getStorageId().getId() + "' was set to '" + ret.value + "' which is bigger than the maximum value '" + maxValue + "'. will set it to the maximum value");
         ret.value = maxValue;
      }
      return ret;
   }

   public boolean shallTrigger(long numEntries) {
      if (numEntries < this.value) {
         if (this.alreadyAlarmed)
            this.alreadyAlarmed = false; // clear flag since treshold not reached anymore
         return false;
      }
      else {
         if (this.alreadyAlarmed)
            return false;
         this.alreadyAlarmed = true;
         return true;
      }
   }

   public String getKey() {
      return this.key;
   }

   public String getId1() {
      return id1;
   }

   public String getId2() {
      return id2;
   }

   public String getType() {
      return type;
   }

   public long getValue() {
      return value;
   }

   public String getEventType() {
      return this.eventType;
   }

   /**
    * Enforced by I_StorageSizeListener
    * @param queue
    * @param numEntries
    * @param numBytes
    * @param isShutdown
    */
   public void changed(I_Storage storage, long numEntries, long numBytes, boolean isShutdown) {
      if (!isShutdown && shallTrigger(numEntries)) {
         String description = "The '" + storage.getStorageId().getId() + "' has reached its treshold: '" + numEntries + "' of max '" + storage.getMaxNumOfEntries() + "' (message sent only once)";
         StringBuffer summary = new StringBuffer(1024);
         summary.append("[").append(new java.sql.Timestamp(System.currentTimeMillis()).toString());
         summary.append(" ").append(Thread.currentThread().getName()).append(" ");
         summary.append(QueuePluginManager.class.getName()).append("]");
         eventDispatcher.dispatchEvent(summary.toString(), description, eventType);
      }
   }

   /**
    * @return the eventDispatcher
    */
   public I_EventDispatcher getEventDispatcher() {
      return this.eventDispatcher;
   }

}


