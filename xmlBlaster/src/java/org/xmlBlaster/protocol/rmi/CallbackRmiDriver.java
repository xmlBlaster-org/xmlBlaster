/*------------------------------------------------------------------------------
Name:      CallbackRmiDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using RMI
Version:   $Id: CallbackRmiDriver.java,v 1.3 2000/06/18 15:22:01 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.rmi;

import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.jutils.log.Log;
import org.xmlBlaster.util.CallbackAddress;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.protocol.I_CallbackDriver;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.RMISecurityManager;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.net.MalformedURLException;


/**
 * This object sends a MessageUnit back to a client using RMI.
 * <p />
 * The BlasterCallback.update() method of the client will be invoked
 * <p />
 * This callback rmi client can be used independent from the RmiDriver.
 * <p />
 * Your client needs to have a callback server implementing interface
 * I_XmlBlasterCallback running and registered with rmi-registry.
 *
 * @version $Revision: 1.3 $
 * @author $Author: ruff $
 */
public class CallbackRmiDriver implements I_CallbackDriver
{
   private String ME = "CallbackRmiDriver";
   private I_XmlBlasterCallback cb = null;
   private CallbackAddress callbackAddress = null;


   /** Get a human readable name of this driver */
   public String getName()
   {
      return ME;
   }


   /**
    * Get callback reference here.
    * @param  callbackAddress Contains the rmi registry name of the client callback server
    */
   public void init(CallbackAddress callbackAddress) throws XmlBlasterException
   {
      // Create and install a security manager
      if (System.getSecurityManager() == null) {
         System.setSecurityManager(new RMISecurityManager());
         if (Log.TRACE) Log.trace(ME, "Started RMISecurityManager");
      }

      this.callbackAddress = callbackAddress;
      String addr = callbackAddress.getAddress(); // e.g. "rmi://localhost/xmlBlaster"
      Remote rem = null;
      try {
         rem = Naming.lookup(addr);
      }
      catch (RemoteException e) {
         Log.error(ME, "Can't access callback address ='" + addr + "', no client rmi registry running");
         throw new XmlBlasterException("CallbackHandleInvalid", "Can't access callback address ='" + addr + "', no client rmi registry running");
      }
      catch (NotBoundException e) {
         Log.error(ME, "The given callback address ='" + addr + "' is not bound to rmi registry: " + e.toString());
         throw new XmlBlasterException("CallbackHandleInvalid", "The given callback address '" + addr + "' is not bound to rmi registry: " + e.toString());
      }
      catch (MalformedURLException e) {
         Log.error(ME, "The given callback address ='" + addr + "' is invalid: " + e.toString());
         throw new XmlBlasterException("CallbackHandleInvalid", "The given callback address '" + addr + "' is invalid: " + e.toString());
      }
      catch (Throwable e) {
         Log.error(ME, "The given callback address ='" + addr + "' is invalid : " + e.toString());
         throw new XmlBlasterException("CallbackHandleInvalid", "The given callback address '" + addr + "' is invalid : " + e.toString());
      }

      if (rem instanceof org.xmlBlaster.protocol.rmi.I_XmlBlasterCallback) {
         cb = (I_XmlBlasterCallback)rem;
         Log.info(ME, "Accessing client callback reference using given '" + addr + "' string");
      }
      else {
         throw new XmlBlasterException("InvalidRmiCallback", "No callback to '" + addr + "' possible, class needs to implement interface I_XmlBlasterCallback.");
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
      } catch (RemoteException e) {
         throw new XmlBlasterException("CallbackFailed", "RMI Callback of message '" + msgUnitWrapper.getXmlKey().getUniqueKey() + "' to client [" + clientInfo.getLoginName() + "] failed, reason=" + e.toString());
      }
   }


   /**
    * This method shuts down the driver.
    * <p />
    */
   public void shutdown()
   {
      // How do we close the socket??
      cb = null;
      Log.warning(ME, "shutdown implementation is missing");
   }
}
