/*------------------------------------------------------------------------------
Name:      CallbackCorbaDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using CORBA
Version:   $Id: CallbackCorbaDriver.java,v 1.15 2000/10/29 20:23:13 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.corba;

import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallback;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackHelper;


/**
 * This object sends a MessageUnit back to a client using Corba.
 * <p>
 * The BlasterCallback.update() method of the client will be invoked
 *
 * @version $Revision: 1.15 $
 * @author $Author: ruff $
 */
public class CallbackCorbaDriver implements I_CallbackDriver
{
   private String ME = "CallbackCorbaDriver";
   private BlasterCallback cb = null;
   private CallbackAddress callbackAddress = null;


   /** Get a human readable name of this driver */
   public String getName()
   {
      return ME;
   }


   /**
    * Get callback reference here.
    * @param  callbackAddress Contains the stringified CORBA callback handle of the client
    */
   public void init(CallbackAddress callbackAddress) throws XmlBlasterException
   {
      this.callbackAddress = callbackAddress;
      String callbackIOR = callbackAddress.getAddress();
      try {
         cb = BlasterCallbackHelper.narrow(CorbaDriver.getOrb().string_to_object(callbackIOR));
         Log.info(ME, "Accessing client callback reference using given IOR string");
      }
      catch (Exception e) {
         Log.error(ME, "The given callback IOR ='" + callbackIOR + "' is invalid: " + e.toString());
         throw new XmlBlasterException("CallbackHandleInvalid", "The given callback IOR is invalid: " + e.toString());
      }
   }


   /**
    * This sends the update to the client.
    * @exception e.id="CallbackFailed", should be caught and handled appropriate
    */
   public final void sendUpdate(ClientInfo clientInfo, MessageUnitWrapper msgUnitWrapper, org.xmlBlaster.engine.helper.MessageUnit[] messageUnitArr) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "xmlBlaster.update(" + msgUnitWrapper.getUniqueKey() + ") to " + clientInfo.toString());

      org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] updateArr = new org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[messageUnitArr.length];
      for (int ii=0; ii<messageUnitArr.length; ii++) {
         updateArr[ii] = convert(messageUnitArr[ii]);
      }
      try {
         cb.update(updateArr);
      } catch (Exception e) {
         throw new XmlBlasterException("CallbackFailed", "CORBA Callback of message '" + msgUnitWrapper.getUniqueKey() + "' to client [" + clientInfo.getLoginName() + "] failed, reason=" + e.toString());
      }
   }


   /**
    * Converts the internal MessageUnit to the CORBA message unit.
    */
   public static final org.xmlBlaster.protocol.corba.serverIdl.MessageUnit convert(org.xmlBlaster.engine.helper.MessageUnit mu)
   {
      return new org.xmlBlaster.protocol.corba.serverIdl.MessageUnit(mu.xmlKey, mu.content, mu.qos);
   }


   /**
    * This method shuts down the driver.
    * <p />
    */
   public void shutdown()
   {
      // CorbaDriver.getOrb().disconnect(cb); TODO: !!! must be called delayed, otherwise the logout() call from the client is aborted with a CORBA exception
      cb = null;
      callbackAddress = null;
      Log.info(ME, "Shutdown of CORBA callback client done.");
   }
}
