/*------------------------------------------------------------------------------
Name:      CallbackEmailDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using email
Version:   $Id: CallbackEmailDriver.java,v 1.3 2000/02/24 22:19:53 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.email;

import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallback;
import org.xmlBlaster.util.Log;


/**
 * This singleton sends a MessageUnit back to a client using Email.
 * <p>
 * THIS DRIVER IS NOT YET IMPLEMENTED
 *
 * @version $Revision: 1.3 $
 * @author $Author: ruff $
 */
public class CallbackEmailDriver implements I_CallbackDriver
{
   private String ME = "CallbackEmailDriver";
   private static CallbackEmailDriver callbackEmailDriver = new CallbackEmailDriver();

   private CallbackEmailDriver()
   {
   }


   /**
    */
   public static final CallbackEmailDriver getInstance()
   {
      return callbackEmailDriver;
   }


   /**
    * This sends the update to the client.
    */
   public final void sendUpdate(ClientInfo clientInfo, MessageUnitWrapper msgUnitWrapper, String updateQoS) throws XmlBlasterException
   {
      Log.error(ME + ".NoEmailProtocol", "Sorry, email callbacks are not yet supported");
      throw new XmlBlasterException(ME + ".NoEmailProtocol", "Sorry, email callbacks are not yet supported");
   }

}
