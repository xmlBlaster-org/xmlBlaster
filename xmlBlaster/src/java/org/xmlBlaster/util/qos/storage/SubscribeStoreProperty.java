/*------------------------------------------------------------------------------
Name:      SubscribeStoreProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos.storage;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.Constants;

/**
 * Helper class holding properties of the Subscribe storage. 
 * <p>
 * Here the real message including the message content is stored.
 */
public class SubscribeStoreProperty extends QueuePropertyBase
{
   private static final String ME = "SubscribeStoreProperty";
   private static Logger log = Logger.getLogger(SubscribeStoreProperty.class.getName());

   /**
    * Increases the default bytes in the storage to 25 MB for each topic
    * @see QueuePropertyBase#QueuePropertyBase(Global, String)
    */
   public SubscribeStoreProperty(Global glob, String nodeId) {
      super(glob, nodeId);

      relating = Constants.RELATING_SUBSCRIBE;

      super.initialize(Constants.RELATING_SUBSCRIBE);
      
      //super.maxEntriesCache.setDefaultValue(2000);
      super.maxEntries.setDefaultValue(Integer.MAX_VALUE);
      //super.maxBytesCache.setDefaultValue(Integer.MAX_VALUE);
      super.maxBytes.setDefaultValue(Integer.MAX_VALUE);
   }

   public final boolean onOverflowDeadMessage() {
      if (Constants.ONOVERFLOW_DEADMESSAGE.equalsIgnoreCase(getOnOverflow()))
         return true;
      return false;
   }

   /**
    * The tag name for configuration, here it is &lt;subecribe ...>
    */
   public String getRootTagName() {
      return "persistence";
   }

   /** For testing: java org.xmlBlaster.util.qos.storage.SubscribeStoreProperty */
   public static void main(String[] args) {
      SubscribeStoreProperty prop = new SubscribeStoreProperty(new Global(args), null);
      System.out.println(prop.toXml());
   }
}


