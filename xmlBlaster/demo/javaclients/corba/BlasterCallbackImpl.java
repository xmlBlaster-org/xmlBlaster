/*------------------------------------------------------------------------------
Name:      BlasterCallbackImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client callback
           YOU MAY USE THIS AS YOUR Callback implementation, JUST TAKE A COPY OF IT
Version:   $Id: BlasterCallbackImpl.java,v 1.14 2002/12/20 15:28:54 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients.corba;

import org.xmlBlaster.util.Global;
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.enum.Constants;
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
   private final Global glob;
   private final LogChannel log;

   /**
    * Construct the client CORBA side callback server. 
    */
   public BlasterCallbackImpl(java.lang.String name) {
      this.ME = "BlasterCallbackImpl-" + name;
      this.glob = Global.instance();
      this.log = glob.getLog("client");
      if (log.CALL) log.trace(ME, "Entering constructor with argument");
   }

   /**
    * Construct the client CORBA side callback server. 
    */
   public BlasterCallbackImpl() {
      this("");
   }

   /**
    * This is the callback method invoked from the server
    * informing the client in an asynchronous mode about new messages
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String[] update(String cbSessionId, org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] corbaMsgUnitArr)
   {
      log.info(ME, "#================== BlasterCallback update START =============");
      log.info(ME, "cbSessionId=" + cbSessionId);
      String[] ret = new String[corbaMsgUnitArr.length];
      try {
         MsgUnitRaw[] msgUnitArr = org.xmlBlaster.protocol.corba.CorbaDriver.convert(glob, corbaMsgUnitArr);
         for (int ii=0; ii<msgUnitArr.length; ii++) {
            MsgUnitRaw msgUnit = msgUnitArr[ii];
            UpdateKey xmlKey = null;
            try {
               xmlKey = new UpdateKey(null, msgUnit.getKey());
            } catch (XmlBlasterException e) {
               log.error(ME, e.getMessage());
            }
            log.info(ME, "Callback invoked for " + xmlKey.toString() + " content length = " + msgUnit.getContent().length);
            log.info(ME, new String(msgUnit.getContent()));
            ret[ii] = Constants.RET_OK; // "<qos><state id='OK'/></qos>";
         }
      }
      catch (XmlBlasterException e) {
         log.error(ME, e.getMessage());
      }
      log.info(ME, "#================== BlasterCallback update END ===============");
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
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public void updateOneway(String cbSessionId, org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] msgUnitArr)
   {
      try {
         update(cbSessionId, msgUnitArr);
      }
      catch (Throwable e) {
         log.error(ME, "updateOneway() failed, exception is not sent to xmlBlaster: " + e.toString());
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
      if (log.CALL) log.call(ME, "Entering ping() ...");
      return "";
   }
}
