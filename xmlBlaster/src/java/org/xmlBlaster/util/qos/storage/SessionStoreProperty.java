/*------------------------------------------------------------------------------
Name:      SessionStoreProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos.storage;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.enum.Constants;

/**
 * Helper class holding properties of the Session storage. 
 * <p>
 * Here the real message including the message content is stored.
 */
public class SessionStoreProperty extends QueuePropertyBase
{
   private static final String ME = "SessionStoreProperty";
   private final LogChannel log;

   /**
    * Increases the default bytes in the storage to 25 MB for each topic
    * @see QueuePropertyBase#QueuePropertyBase(Global, String)
    */
   public SessionStoreProperty(Global glob, String nodeId) {
      super(glob, nodeId);
      this.log = glob.getLog("core");
      relating = Constants.RELATING_SESSION;

      super.initialize(Constants.RELATING_SESSION);
      
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
    * The tag name for configuration, here it is &lt;session ...>
    */
   public String getRootTagName() {
      return "persistence";
   }

   /** For testing: java org.xmlBlaster.util.qos.storage.SessionStoreProperty */
   public static void main(String[] args) {
      SessionStoreProperty prop = new SessionStoreProperty(new Global(args), null);
      System.out.println(prop.toXml());
   }
}


