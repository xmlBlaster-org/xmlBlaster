/*------------------------------------------------------------------------------
Name:      CallbackHttpDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using HTTP
Version:   $Id: CallbackHttpDriver.java,v 1.2 2000/02/20 17:38:54 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http;

import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallback;
import org.xmlBlaster.util.Log;


/**
 * This singleton sends a MessageUnit back to a client using Http.
 * <p>
 * THIS DRIVER IS NOT YET IMPLEMENTED
 *
 * @version $Revision: 1.2 $
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
   public final void sendUpdate(ClientInfo clientInfo, MessageUnitWrapper messageUnitWrapper, String updateQoS) throws XmlBlasterException
   {
      Log.error(ME + ".NoHttpProtocol", "Sorry, HTTP callbacks are not yet supported");
      throw new XmlBlasterException(ME + ".NoHttpProtocol", "Sorry, HTTP callbacks are not yet supported");
   }

}
