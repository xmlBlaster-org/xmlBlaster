/*------------------------------------------------------------------------------
Name:      HistoryQueueProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos.storage;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.Constants;

/**
 * Helper class holding history queue properties.
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */
public class HistoryQueueProperty extends QueuePropertyBase
{
   private static final String ME = "HistoryQueueProperty";
   private static Logger log = Logger.getLogger(HistoryQueueProperty.class.getName());

   /**
    * @param nodeId    If not null, the command line properties will look for prop[nodeId] as well,
    * e.g. -queue/history/maxEntries and -/node/heron/queue/history/maxEntries will be searched
    */
   public HistoryQueueProperty(Global glob, String nodeId) {
      super(glob, nodeId);

      this.maxEntries.setDefaultValue(10);
      this.maxEntriesCache.setDefaultValue(10);
      setRelating(Constants.RELATING_HISTORY);
      super.initialize(Constants.RELATING_HISTORY); //related='history'--> -queue/history/maxEntries
   }

   /**
    * Show some important settings for logging
    */
   public final String getSettings() {
      StringBuffer buf = new StringBuffer(256);
      buf.append("type=").append(getType()).append(" onOverflow=").append(getOnOverflow()).append(" onFailure=").append(getOnFailure()).append(" maxEntries=").append(getMaxEntries());
      return buf.toString();
   }

   public final boolean onOverflowDeadMessage() {
      if (Constants.ONOVERFLOW_DEADMESSAGE.equalsIgnoreCase(getOnOverflow()))
         return true;
      return false;
   }

   /**
    * Get a usage string for queue configuration (in xmlBlaster.properties or on command line)
    */
   public String usage(String headerline) {
      String prefix = getPrefix();
      String text = "";
      text += "\n" + headerline + "\n";
      text += "   -"+prefix+"debug    Sets a debug flag on the queue [false].\n";
      text += "                       Currently used for consistency asserts on jdbc queue.\n";
      text += "   -"+prefix+"maxEntries\n";
      text += "                       The maximum allowed number of messages [" + this.maxEntries.getDefaultValue() + "].\n";
      text += "   -"+prefix+"maxEntriesCache\n";
      text += "                       The maximum allowed number of messages in the cache [" + this.maxEntriesCache.getDefaultValue() + "].\n";
      text += "   -"+prefix+"maxBytes\n";
      text += "                       The maximum size in bytes of the storage [" + this.maxBytes.getDefaultValue() + "].\n";
      text += "   -"+prefix+"maxBytesCache.\n";
      text += "                       The maximum size in bytes in the cache [" + this.maxBytesCache.getDefaultValue() + "].\n";
      //text += "   -"+prefix+"onOverflow\n";
      //text += "                       What happens if storage is full [" + this.onOverflow.getDefaultValue() + "]\n";
      //text += "   -"+prefix+"onFailure\n";
      //text += "                       Error handling when storage failed [" + this.onFailure.getDefaultValue() + "]\n";
      text += "   -"+prefix+"type\n";
      text += "                       The plugin type [" + this.type.getDefaultValue() + "]\n";
      text += "   -"+prefix+"version\n";
      text += "                       The plugin version [" + this.version.getDefaultValue() + "]\n";
      text += "   -"+prefix+"defaultPlugin\n";
      text += "                       The plugin type,version (short form) [" + this.type.getDefaultValue()+","+this.version.getDefaultValue() + "]\n";
      return text;
   }

   /** For testing: java org.xmlBlaster.engine.helper.HistoryQueueProperty */
   public static void main(String[] args) {
      HistoryQueueProperty prop = new HistoryQueueProperty(new Global(args), null);
      System.out.println(prop.toXml());
   }
}


