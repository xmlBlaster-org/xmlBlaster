/*------------------------------------------------------------------------------
Name:      CallbackSocketDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Sending messages to clients
Version:   $Id: CallbackSocketDriver.java,v 1.3 2002/02/15 22:45:54 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.client.protocol.ConnectionException;


/**
 * One instance of this for each client to send him callback. 
 * <p />
 * This is sort of a dummy needed by the plugin framework which
 * assumed for CORBA/RMI/XML-RPC a separate callback connection
 */
public class CallbackSocketDriver implements I_CallbackDriver
{
   private final String ME = "CallbackSocketDriver";
   private String loginName;
   private HandleClient handler;

   /* Should not be instantiated by plugin loader.
   public CallbackSocketDriver() {
      Log.error(ME, "Empty Constructor!");
      (new Exception("")).printStackTrace();
   }
   */

   public CallbackSocketDriver(String loginName, HandleClient handler) {
      if (Log.CALL) Log.call(ME, "Constructor"); // (new Exception("")).printStackTrace());
      this.loginName = loginName;
      this.handler = handler;
   }

   public String getName() {
      return this.loginName;
   }

   public void init(CallbackAddress callbackAddress) {
      Log.warn(ME, "Implement init()");
   }

   public String sendUpdate(ClientInfo clientInfo, MessageUnitWrapper msgUnitWrapper, MessageUnit[] messageUnitArr)
                              throws XmlBlasterException, ConnectionException {
      return handler.sendUpdate(clientInfo, msgUnitWrapper, messageUnitArr);
   }

   public void shutdown() {
      handler.shutdown();
   }
}

