/*------------------------------------------------------------------------------
Name:      CallbackNativeDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using native interface.
Version:   $Id: CallbackNativeDriver.java,v 1.3 2002/01/22 17:21:28 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.nativ;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.util.XmlBlasterException;


/**
 * This object sends a MessageUnit back to a client using native interface, in
 * the same JVM.
 * <p>
 * The I_CallbackDriver.update() method of the client will be invoked
 *
 * @author ruff@swand.lake.de
 * @see org.xmlBlaster.protocol.nativ.NativeDriver
 */
public class CallbackNativeDriver implements I_CallbackDriver
{
   private String ME = "CallbackNativeDriver";
   private CallbackAddress callbackAddress = null;


   /** Get a human readable name of this driver */
   public String getName()
   {
      return ME;
   }


   /**
    * Get callback reference here.
    * <p />
    * This method is enforced by interface I_CallbackDriver and is called by
    * xmlBlaster after instantiation of this class, telling us
    * the address to callback.
    * @param  callbackAddress Contains the stringified native callback handle of the client
    */
   public void init(CallbackAddress callbackAddress) throws XmlBlasterException
   {
      this.callbackAddress = callbackAddress;
   }


   /**
    * This sends the update to the client.
    * <p />
    * This method is enforced by interface I_CallbackDriver and is called by
    * @exception e.id="CallbackFailed", should be caught and handled appropriate
    */
   public final String sendUpdate(ClientInfo clientInfo, MessageUnitWrapper msgUnitWrapper, org.xmlBlaster.engine.helper.MessageUnit[] msgUnitArr) throws XmlBlasterException
   {
      Log.info(ME, "Received message update '" + new String(msgUnitArr[0].content) + "' from sender '" + clientInfo.toString() + "'");
      return "<qos><state>OK</state></qos>";
   }


   /**
    * This method shuts down the driver.
    * <p />
    */
   public void shutdown()
   {
   }
}
