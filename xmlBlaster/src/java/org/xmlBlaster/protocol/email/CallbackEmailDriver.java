/*------------------------------------------------------------------------------
Name:      CallbackEmailDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using email
Version:   $Id: CallbackEmailDriver.java,v 1.10 2000/09/15 17:16:18 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.email;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.helper.CallbackAddress;


/**
 * This singleton sends a MessageUnit back to a client using Email.
 * <p>
 * THIS DRIVER IS NOT YET IMPLEMENTED
 *
 * @version $Revision: 1.10 $
 * @author $Author: ruff $
 */
public class CallbackEmailDriver implements I_CallbackDriver
{
   private String ME = "CallbackEmailDriver";
   private CallbackAddress callbackAddress = null;

   /** Get a human readable name of this driver */
   public String getName()
   {
      return ME;
   }


   /**
    * @param  callbackAddress Contains the email TO: address
    */
   public void init(CallbackAddress callbackAddress)
   {
      this.callbackAddress = callbackAddress;
   }


   /**
    * This sends the update to the client.
    */
   public final void sendUpdate(ClientInfo clientInfo, MessageUnitWrapper msgUnitWrapper, org.xmlBlaster.engine.helper.MessageUnit[] messageUnitArr) throws XmlBlasterException
   {
      Log.error(ME + ".NoEmailProtocol", "Sorry, email callbacks are not yet supported, no mail sent to " + callbackAddress.getAddress());
      throw new XmlBlasterException(ME + ".NoEmailProtocol", "Sorry, email callbacks are not yet supported, no mail sent to " + callbackAddress.getAddress());
   }


   /**
    * This method shuts down the driver.
    * <p />
    */
   public void shutdown()
   {
      Log.warn(ME, "shutdown implementation is missing");
   }
}
