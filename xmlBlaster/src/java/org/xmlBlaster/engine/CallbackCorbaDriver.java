/*------------------------------------------------------------------------------
Name:      CallbackCorbaDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using CORBA
Version:   $Id: CallbackCorbaDriver.java,v 1.1 1999/12/01 22:17:28 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.MessageUnit;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.clientIdl.BlasterCallback;


/**
 * This singleton sends a MessageUnit back to a client using Corba. 
 * <p>
 * The BlasterCallback.update() method of the client will be invoked
 *
 * @version $Revision: 1.1 $
 * @author $Author: ruff $
 */
public class CallbackCorbaDriver implements I_CallbackDriver
{
   private String ME = "CallbackCorbaDriver";
   private static CallbackCorbaDriver callbackCorbaDriver = new CallbackCorbaDriver();

   private CallbackCorbaDriver()
   {
   }


   /**
    */
   public static final CallbackCorbaDriver getInstance()
   {
      return callbackCorbaDriver;
   }


   /**
    * This sends the update to the client. 
    */
   public final void sendUpdate(ClientInfo clientInfo, MessageUnitWrapper messageUnitWrapper) throws XmlBlasterException
   {
      BlasterCallback cb = clientInfo.getCB();

      XmlQoSUpdate xmlQoS = new XmlQoSUpdate();

      MessageUnit[] updateMsgArr = new MessageUnit[1];
      updateMsgArr[0] = messageUnitWrapper.getMessageUnit();

      String[] qarr = new String[1];
      qarr[0] = xmlQoS.toString();

      if (Log.TRACE) Log.trace(ME, "xmlBlaster.update(" + messageUnitWrapper.getXmlKey().getUniqueKey() + ") to " + clientInfo.toString());
      
      cb.update(updateMsgArr, qarr);
   }

}
