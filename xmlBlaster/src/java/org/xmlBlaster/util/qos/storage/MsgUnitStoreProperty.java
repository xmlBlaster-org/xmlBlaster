/*------------------------------------------------------------------------------
Name:      MsgUnitStoreProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos.storage;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.property.PropEntry;
import org.xml.sax.Attributes;

/**
 * Helper class holding properties of the MsgUnit storage. 
 * <p>
 * Here the real message including the message content is stored.
 * </p>
 * See ConnectQos for XML syntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */
public class MsgUnitStoreProperty extends QueuePropertyBase
{
   private static final String ME = "MsgUnitStoreProperty";
   private final LogChannel log;

   /**
    * Increases the default bytes in the storage to 25 MB for each topic
    * @see QueuePropertyBase#QueuePropertyBase(Global, String)
    */
   public MsgUnitStoreProperty(Global glob, String nodeId) {
      super(glob, nodeId);
      this.log = glob.getLog("core");
      relating = Constants.RELATING_MSGUNITSTORE;

      super.initialize(Constants.RELATING_MSGUNITSTORE);
      
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
    * The tag name for configuration, here it is &lt;msgUnitStore ...>
    */
   public String getRootTagName() {
      return "persistence";
   }

   /** For testing: java org.xmlBlaster.util.qos.storage.MsgUnitStoreProperty */
   public static void main(String[] args) {
      MsgUnitStoreProperty prop = new MsgUnitStoreProperty(new Global(args), null);
      System.out.println(prop.toXml());
   }
}


