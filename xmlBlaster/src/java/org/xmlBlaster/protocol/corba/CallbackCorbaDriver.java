/*------------------------------------------------------------------------------
Name:      CallbackCorbaDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using CORBA
Version:   $Id: CallbackCorbaDriver.java,v 1.3 2000/02/24 22:19:53 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.corba;

import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallback;


/**
 * This singleton sends a MessageUnit back to a client using Corba.
 * <p>
 * The BlasterCallback.update() method of the client will be invoked
 *
 * @version $Revision: 1.3 $
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
   public final void sendUpdate(ClientInfo clientInfo, MessageUnitWrapper msgUnitWrapper, String updateQoS) throws XmlBlasterException
   {
      BlasterCallback cb = clientInfo.getCB();

      MessageUnit[] updateMsgArr = new MessageUnit[1];
      updateMsgArr[0] = msgUnitWrapper.getMessageUnit();

      String[] qarr = new String[1];
      qarr[0] = updateQoS;

      /* not performing enough, but better OOD
         UpdateQoS xmlQoS = new UpdateQoS(clientInfo.getLoginName(), xytag);
         qarr[0] = xmlQoS.toString();
      */

      if (Log.TRACE) Log.trace(ME, "xmlBlaster.update(" + msgUnitWrapper.getXmlKey().getUniqueKey() + ") to " + clientInfo.toString());

      cb.update(updateMsgArr, qarr);
   }

}
