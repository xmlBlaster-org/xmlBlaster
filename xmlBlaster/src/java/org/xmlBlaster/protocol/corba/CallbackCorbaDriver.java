/*------------------------------------------------------------------------------
Name:      CallbackCorbaDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using CORBA
Version:   $Id: CallbackCorbaDriver.java,v 1.7 2000/06/04 23:44:46 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.corba;

import org.xmlBlaster.Main;
import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.util.CallbackAddress;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallback;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackHelper;


/**
 * This object sends a MessageUnit back to a client using Corba.
 * <p>
 * The BlasterCallback.update() method of the client will be invoked
 *
 * @version $Revision: 1.7 $
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
   public final void sendUpdate(ClientInfo clientInfo, MessageUnitWrapper msgUnitWrapper, String updateQoS) throws XmlBlasterException
   {
      MessageUnit[] updateMsgArr = new MessageUnit[1];
      updateMsgArr[0] = msgUnitWrapper.getMessageUnit();

      String[] qarr = new String[1];
      qarr[0] = updateQoS;

      /* not performing enough, but better OOD
         UpdateQoS xmlQoS = new UpdateQoS(clientInfo.getLoginName(), xytag);
         qarr[0] = xmlQoS.toString();
      */

      if (Log.TRACE) Log.trace(ME, "xmlBlaster.update(" + msgUnitWrapper.getXmlKey().getUniqueKey() + ") to " + clientInfo.toString());

      try {
         cb.update(updateMsgArr, qarr);
      } catch (Exception e) {
         throw new XmlBlasterException("CallbackFailed", "CORBA Callback of message '" + msgUnitWrapper.getXmlKey().getUniqueKey() + "' to client [" + clientInfo.getLoginName() + "] failed, reason=" + e.toString());
      }
   }


   /**
    * This method shuts down the driver. 
    * <p />
    */
   public void shutdown()
   {
      Log.warning(ME, "shutdown implementation is missing");
   }
}
