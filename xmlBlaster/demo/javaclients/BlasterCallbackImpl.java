/*------------------------------------------------------------------------------
Name:      BlasterCallbackImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client callback
           YOU MAY USE THIS AS YOUR Callback implementation, JUST TAKE A COPY OF IT
Version:   $Id: BlasterCallbackImpl.java,v 1.4 2000/02/20 17:38:48 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlKeyBase;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
import org.xmlBlaster.protocol.corba.clientIdl.*;


/**
 * Client implementation of the callback.
 * <p />
 * YOU MAY USE THIS AS YOUR CALLBACK IMPLEMENTATION, JUST TAKE A COPY OF IT.
 * <p />
 * Note that there is a default Callback implementation in CorbaConnect.java which is
 * usually sufficient, so you don't really need to implement the callback code yourself.<br />
 * see ClientXml.java for an example how to use the DefaultCallback.
 * <p />
 */
//public class BlasterCallbackImpl extends BlasterCallbackPOA {         // inheritance approach
public class BlasterCallbackImpl implements BlasterCallbackOperations { // tie approach
   final String ME;

   /**
    * Construct a persistently named object.
    */
   public BlasterCallbackImpl(java.lang.String name) {
      this.ME = "BlasterCallbackImpl-" + name;
      if (Log.CALLS) Log.trace(ME, "Entering constructor with argument");
   }


   /**
    * Construct a transient object.
    */
   public BlasterCallbackImpl() {
      super();
      this.ME = "BlasterCallbackImpl";
      if (Log.CALLS) Log.trace(ME, "Entering constructor without argument");
   }


   /**
    * This is the callback method invoked from the server
    * informing the client in an asynchronous mode about new messages
    */
   public void update(MessageUnit[] messageUnitArr, String[] qos_literal_Arr)
   {
      for (int ii=0; ii<messageUnitArr.length; ii++) {
         MessageUnit messageUnit = messageUnitArr[ii];
         XmlKeyBase xmlKey = null;
         try {
            xmlKey = new XmlKeyBase(messageUnit.xmlKey);
         } catch (XmlBlasterException e) {
            Log.error(ME, e.reason);
         }
         Log.info(ME, "#================== BlasterCallback update START =============");
         Log.info(ME, "Callback invoked for " + xmlKey.toString() + " content length = " + messageUnit.content.length);
         Log.info(ME, new String(messageUnit.content));
         Log.info(ME, "#================== BlasterCallback update END ===============");
      }
   }
}
