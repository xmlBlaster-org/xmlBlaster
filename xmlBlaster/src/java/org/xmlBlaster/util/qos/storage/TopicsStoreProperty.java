/*------------------------------------------------------------------------------
Name:      TopicsStoreProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos.storage;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.enum.Constants;
import org.xml.sax.Attributes;

/**
 * Helper class holding properties of the Topics storage. 
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */
public class TopicsStoreProperty extends QueuePropertyBase
{
   private static final String ME = "TopicsStoreProperty";
   private final LogChannel log;

   /**
    * @see QueuePropertyBase#(Global, String)
    */
   public TopicsStoreProperty(Global glob, String nodeId) {
      super(glob, nodeId);
      this.log = glob.getLog("core");
      relating = Constants.RELATING_TOPICCACHE;
      initialize();
   }

   /**
    * Configure property settings
    */
   protected void initialize() {
      super.initialize(null);
      //super.initialize("topic"); //--> topic.msgUnitStore.maxMsg
   }

   public final boolean onOverflowDeadMessage() {
      if (Constants.ONOVERFLOW_DEADMESSAGE.equalsIgnoreCase(getOnOverflow()))
         return true;
      return false;
   }

   /**
    * The tag name for configuration, here it is &lt;msgUnitStore ...>
    */
   public String getRootTagName() {
      return "topicsStore";
   }

   /** For testing: java org.xmlBlaster.util.qos.storage.TopicsStoreProperty */
   public static void main(String[] args) {
      TopicsStoreProperty prop = new TopicsStoreProperty(new Global(args), null);
      System.out.println(prop.toXml());
   }
}


