/*------------------------------------------------------------------------------
Name:      MsgUnitStoreProperty.java
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
public class MsgUnitStoreProperty extends QueuePropertyBase
{
   private static final String ME = "MsgUnitStoreProperty";
   private final LogChannel log;

   /**
    * @see QueuePropertyBase#(Global, String)
    */
   public MsgUnitStoreProperty(Global glob, String nodeId) {
      super(glob, nodeId);
      this.log = glob.getLog("core");
      relating = Constants.RELATING_MSGUNITSTORE;
      super.initialize(Constants.RELATING_MSGUNITSTORE);
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


