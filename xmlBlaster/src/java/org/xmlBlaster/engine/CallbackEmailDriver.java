/*------------------------------------------------------------------------------
Name:      CallbackEmailDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using email
Version:   $Id: CallbackEmailDriver.java,v 1.1 1999/12/01 22:17:28 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.clientIdl.BlasterCallback;


/**
 * This singleton sends a MessageUnit back to a client using Email.
 * <p>
 * THIS DRIVER IS NOT YET IMPLEMENTED
 *
 * @version $Revision: 1.1 $
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
   public final void sendUpdate(ClientInfo clientInfo, MessageUnitWrapper messageUnitWrapper) throws XmlBlasterException
   {
      Log.error(ME + ".NoEmailProtocol", "Sorry, email callbacks are not yet supported");
      throw new XmlBlasterException(ME + ".NoEmailProtocol", "Sorry, email callbacks are not yet supported");
   }

}
