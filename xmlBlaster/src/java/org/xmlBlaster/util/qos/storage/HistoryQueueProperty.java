/*------------------------------------------------------------------------------
Name:      HistoryQueueProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos.storage;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.enum.Constants;
import org.xml.sax.Attributes;

/**
 * Helper class holding history queue properties.
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */
public class HistoryQueueProperty extends QueuePropertyBase
{
   private static final String ME = "HistoryQueueProperty";
   private final LogChannel log;

   /**
    * @param nodeId    If not null, the command line properties will look for prop[nodeId] as well,
    * e.g. -queue/history/maxEntries and -/node/heron/queue/history/maxEntries will be searched
    */
   public HistoryQueueProperty(Global glob, String nodeId) {
      super(glob, nodeId);
      this.log = glob.getLog("core");
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

   /** For testing: java org.xmlBlaster.engine.helper.HistoryQueueProperty */
   public static void main(String[] args) {
      HistoryQueueProperty prop = new HistoryQueueProperty(new Global(args), null);
      System.out.println(prop.toXml());
   }
}


