/*------------------------------------------------------------------------------
Name:      CallbackEmailDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using email
Version:   $Id: CallbackEmailDriver.java,v 1.4 2000/05/16 20:57:38 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.email;

import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallback;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.CallbackAddress;


/**
 * This singleton sends a MessageUnit back to a client using Email.
 * <p>
 * THIS DRIVER IS NOT YET IMPLEMENTED
 *
 * @version $Revision: 1.4 $
 * @author $Author: ruff $
 */
public class CallbackEmailDriver implements I_CallbackDriver
{
   private String ME = "CallbackEmailDriver";
   private CallbackAddress callbackAddress = null;

   /**
    * @param  callbackAddress Contains the email TO: address
    */
   public CallbackEmailDriver(CallbackAddress callbackAddress)
   {
      this.callbackAddress = callbackAddress;
   }


   /**
    * This sends the update to the client.
    */
   public final void sendUpdate(ClientInfo clientInfo, MessageUnitWrapper msgUnitWrapper, String updateQoS) throws XmlBlasterException
   {
      Log.error(ME + ".NoEmailProtocol", "Sorry, email callbacks are not yet supported, no mail sent to " + callbackAddress.getAddress());
      throw new XmlBlasterException(ME + ".NoEmailProtocol", "Sorry, email callbacks are not yet supported, no mail sent to " + callbackAddress.getAddress());
   }
}
