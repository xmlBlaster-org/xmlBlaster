/*------------------------------------------------------------------------------
Name:      TopicCacheProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos.storage;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.enum.Constants;
import org.xml.sax.Attributes;

/**
 * Helper class holding properties of the MsgUnit storage. 
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */
public class TopicCacheProperty extends QueuePropertyBase
{
   private static final String ME = "TopicCacheProperty";
   private final LogChannel log;

   /**
    * @param nodeId    If not null, the command line properties will look for prop[nodeId] as well,
    * e.g. -queue.maxMsg and -queue.maxMsg[heron] will be searched
    */
   public TopicCacheProperty(Global glob, String nodeId) {
      super(glob, nodeId);
      this.log = glob.getLog("core");
      relating = Constants.RELATING_TOPICCACHE;
      initialize();
   }

   /**
    * Configure property settings
    */
   protected void initialize() {
      //super.initialize(null);  // would allow to name different "msgstore" e.g. "topic.msgstore"
      super.initialize("topic");
   }

   public final boolean onOverflowDeadMessage() {
      if (Constants.ONOVERFLOW_DEADMESSAGE.equalsIgnoreCase(getOnOverflow()))
         return true;
      return false;
   }

   /**
    * The tag name for configuration, here it is &lt;msgstore ...>
    */
   public String getRootTagName() {
      return "msgstore";
   }

   /** For testing: java org.xmlBlaster.util.qos.storage.TopicCacheProperty */
   public static void main(String[] args) {
      TopicCacheProperty prop = new TopicCacheProperty(new Global(args), null);
      System.out.println(prop.toXml());
   }
}


