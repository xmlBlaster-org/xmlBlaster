/*------------------------------------------------------------------------------
Name:      CallbackSocketDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Sending messages to clients
Version:   $Id: CallbackSocketDriver.java,v 1.1 2002/02/14 22:53:37 ruff Exp $
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
   private String loginName;
   private HandleClient handler;

   public CallbackSocketDriver() {}

   public CallbackSocketDriver(String loginName, HandleClient handler) {
      this.loginName = loginName;
      this.handler = handler;
   }

   public String getName() {
      return this.loginName;
   }

   public void init(CallbackAddress callbackAddress) {
   }

   public String sendUpdate(ClientInfo clientInfo, MessageUnitWrapper msgUnitWrapper, MessageUnit[] messageUnitArr) throws XmlBlasterException {
      return handler.sendUpdate(clientInfo, msgUnitWrapper, messageUnitArr);
   }

   public void shutdown() {
   }
}

