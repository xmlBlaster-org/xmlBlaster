/*------------------------------------------------------------------------------
Name:      BlasterCallbackImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client callback
           YOU MAY USE THIS AS YOUR Callback implementation, JUST TAKE A COPY OF IT
Version:   $Id: BlasterCallbackImpl.java,v 1.8 2002/04/30 16:42:45 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients.corba;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlKeyBase;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Constants;
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
    * Construct the client CORBA side callback server. 
    */
   public BlasterCallbackImpl(java.lang.String name) {
      this.ME = "BlasterCallbackImpl-" + name;
      if (Log.CALL) Log.trace(ME, "Entering constructor with argument");
   }

   /**
    * Construct the client CORBA side callback server. 
    */
   public BlasterCallbackImpl() {
      super();
      this.ME = "BlasterCallbackImpl";
      if (Log.CALL) Log.trace(ME, "Entering constructor without argument");
   }

   /**
    * This is the callback method invoked from the server
    * informing the client in an asynchronous mode about new messages
    * @see xmlBlaster.idl
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
            xmlKey = new XmlKeyBase(null, msgUnit.xmlKey);
         } catch (XmlBlasterException e) {
            Log.error(ME, e.reason);
         }
         Log.info(ME, "Callback invoked for " + xmlKey.toString() + " content length = " + msgUnit.content.length);
         Log.info(ME, new String(msgUnit.content));
         ret[ii] = Constants.RET_OK; // "<qos><state id='OK'/></qos>";
      }
      Log.info(ME, "#================== BlasterCallback update END ===============");
      return ret;
   }

   /**
    * This is the callback method invoked from the CORBA server
    * informing the client in an asynchronous mode about new messages.
    * <p />
    * This oneway method does not return something, it is high performing but
    * you loose the application level hand shake.
    *
    * @param msgUnitArr Contains a MessageUnit structs (your message) for CORBA
    * @see xmlBlaster.idl
    */
   public void updateOneway(String cbSessionId, org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] msgUnitArr)
   {
      try {
         update(cbSessionId, msgUnitArr);
      }
      catch (Throwable e) {
         Log.error(ME, "updateOneway() failed, exception is not sent to xmlBlaster: " + e.toString());
         e.printStackTrace();
      }
   }

   /**
    * Ping to check if the callback server is alive.
    * @param qos ""
    * @return ""
    */
   public String ping(String qos)
   {
      if (Log.CALL) Log.call(ME, "Entering ping() ...");
      return "";
   }
}
