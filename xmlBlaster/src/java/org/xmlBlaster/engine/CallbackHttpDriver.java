/*------------------------------------------------------------------------------
Name:      CallbackHttpDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using HTTP
Version:   $Id: CallbackHttpDriver.java,v 1.1 1999/12/01 22:17:28 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.clientIdl.BlasterCallback;


/**
 * This singleton sends a MessageUnit back to a client using Http.
 * <p>
 * THIS DRIVER IS NOT YET IMPLEMENTED
 *
 * @version $Revision: 1.1 $
 * @author $Author: ruff $
 */
public class CallbackHttpDriver implements I_CallbackDriver
{
   private String ME = "CallbackHttpDriver";
   private static CallbackHttpDriver callbackHttpDriver = new CallbackHttpDriver();

   private CallbackHttpDriver()
   {
   }


   /**
    */
   public static final CallbackHttpDriver getInstance()
   {
      return callbackHttpDriver;
   }


   /**
    * This sends the update to the client.
    */
   public final void sendUpdate(ClientInfo clientInfo, MessageUnitWrapper messageUnitWrapper) throws XmlBlasterException
   {
      Log.error(ME + ".NoHttpProtocol", "Sorry, HTTP callbacks are not yet supported");
      throw new XmlBlasterException(ME + ".NoHttpProtocol", "Sorry, HTTP callbacks are not yet supported");
   }

}
