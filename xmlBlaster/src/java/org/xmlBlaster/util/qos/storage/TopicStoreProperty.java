/*------------------------------------------------------------------------------
Name:      TopicStoreProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos.storage;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.enum.Constants;

/**
 * Helper class holding properties of the Topics storage. 
 * <p>
 * Theses properties are server side only and must be set before startup of xmlBlaster, e.g.:
 * </p>
 * <pre>
 * topicStore.persistence.maxMsg=1000000
 * topicStore.persistence.maxMsgCache=10
 * </pre>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.persistence.html">The engine.persistence requirement</a>
 */
public class TopicStoreProperty extends QueuePropertyBase
{
   /**
    * Enforces a high default setting for maxMsg and maxBytes
    * @see QueuePropertyBase#(Global, String)
    */
   public TopicStoreProperty(Global glob, String nodeId) {
      super(glob, nodeId);
      setRelating(Constants.RELATING_TOPICSTORE);

      // Check environment settings
      super.initialize(Constants.RELATING_TOPICSTORE); //related='topicStore'--> -topicStore.persistence.maxMsg

      // Enforces a high default setting for maxMsg and maxBytes
      super.maxMsgCache.setDefaultValue(2);
      super.maxMsg.setDefaultValue(Integer.MAX_VALUE);
      super.maxBytesCache.setDefaultValue(Integer.MAX_VALUE);
      super.maxBytes.setDefaultValue(Integer.MAX_VALUE);
   }

   /**
    * The tag name for configuration, here it is &lt;topicStore ...>
    */
   public String getRootTagName() {
      return "persistence";
   }

   /** For testing: java org.xmlBlaster.util.qos.storage.TopicStoreProperty */
   public static void main(String[] args) {
      TopicStoreProperty prop = new TopicStoreProperty(new Global(args), null);
      System.out.println(prop.toXml());
   }
}


