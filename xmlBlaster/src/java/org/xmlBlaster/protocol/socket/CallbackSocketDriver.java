/*------------------------------------------------------------------------------
Name:      CallbackSocketDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Sending messages to clients
Version:   $Id: CallbackSocketDriver.java,v 1.2 2002/02/15 19:05:57 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.helper.CallbackAddress;


/**
 * One instance of this for each client to him callback. 
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

   public String sendUpdate(ClientInfo clientInfo, MessageUnitWrapper msgUnitWrapper, MessageUnit[] messageUnitArr) throws XmlBlasterException {
      return handler.sendUpdate(clientInfo, msgUnitWrapper, messageUnitArr);
   }

   public void shutdown() {
      handler.shutdown();
   }
}

