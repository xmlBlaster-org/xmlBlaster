/*------------------------------------------------------------------------------
Name:      BlasterCallbackImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client callback
           YOU MAY USE THIS AS YOUR Callback implementation, JUST TAKE A COPY OF IT
Version:   $Id: BlasterCallbackImpl.java,v 1.5 2002/03/13 16:41:05 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients.corba;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlKeyBase;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.protocol.corba.clientIdl.*;


/**
 * Demo Client implementation of the CORBA callback.
 * <p />
 * YOU MAY USE THIS AS YOUR CALLBACK IMPLEMENTATION, JUST TAKE A COPY OF IT.
 * <p />
 * Note that there is a default Callback implementation hidden behind XmlBlasterConnection.java<br />
 * (see xmlBlaster/src/java/org/xmlBlaster/client/protocol/corba/CorbaCallbackServer.java)
 * which is usually sufficient, so you don't really need to implement the callback code yourself.<br />
 * See xmlBlaster/demo/javaclients/ClientSub.java for an example how to use it.
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
      if (Log.CALL) Log.trace(ME, "Entering constructor with argument");
   }


   /**
    * Construct a transient object.
    */
   public BlasterCallbackImpl() {
      super();
      this.ME = "BlasterCallbackImpl";
      if (Log.CALL) Log.trace(ME, "Entering constructor without argument");
   }


   /**
    * This is the callback method invoked from the server
    * informing the client in an asynchronous mode about new messages
    */
   public String[] update(String cbSessionId, org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] corbaMsgUnitArr)
   {
      Log.info(ME, "#================== BlasterCallback update START =============");
      Log.info(ME, "cbSessionId=" + cbSessionId);
      MessageUnit[] msgUnitArr = org.xmlBlaster.protocol.corba.CorbaDriver.convert(corbaMsgUnitArr);
      String[] ret = new String[msgUnitArr.length];
      for (int ii=0; ii<msgUnitArr.length; ii++) {
         MessageUnit msgUnit = msgUnitArr[ii];
         XmlKeyBase xmlKey = null;
         try {
            xmlKey = new XmlKeyBase(msgUnit.xmlKey);
         } catch (XmlBlasterException e) {
            Log.error(ME, e.reason);
         }
         Log.info(ME, "Callback invoked for " + xmlKey.toString() + " content length = " + msgUnit.content.length);
         Log.info(ME, new String(msgUnit.content));
         ret[ii] = "<qos><state>OK</state></qos>";
      }
      Log.info(ME, "#================== BlasterCallback update END ===============");
      return ret;
   }
}
