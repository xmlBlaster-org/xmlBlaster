/*------------------------------------------------------------------------------
Name:      CallbackRmiDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using RMI
Version:   $Id: CallbackRmiDriver.java,v 1.15 2002/05/30 09:53:29 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.rmi;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.queue.MsgQueueEntry;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.MessageUnit;

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
 * @version $Revision: 1.15 $
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
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
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return "RMI"
    */
   public String getProtocolId()
   {
      return "RMI";
   }

   /**
    * Get the address how to access this driver. 
    * @return "rmi://www.mars.universe:1099/I_AuthServer"
    */
   public String getRawAddress()
   {
      return callbackAddress.getAddress();
   }

   /**
    * Get callback reference here.
    * @param  callbackAddress Contains the rmi registry name of the client callback server
    */
   public void init(Global glob, CallbackAddress callbackAddress) throws XmlBlasterException
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

   private I_XmlBlasterCallback getCb() throws XmlBlasterException
   {
      if (cb == null)
         throw new XmlBlasterException("InvalidRmiCallback", "No callback to '" + callbackAddress.getAddress() + "' possible, no connection.");
      return cb;
   }

   /**
    * This sends the update to the client.
    * @exception e.id="CallbackFailed", should be caught and handled appropriate
    */
   public final String[] sendUpdate(MsgQueueEntry[] msg) throws XmlBlasterException
   {
      if (msg == null || msg.length < 1) throw new XmlBlasterException(ME, "Illegal update argument");
      if (Log.TRACE) Log.trace(ME, "xmlBlaster.update() to " + callbackAddress.getSessionId());

      try {
         MessageUnit[] updateArr = new MessageUnit[msg.length];
         for (int ii=0; ii<msg.length; ii++)
            updateArr[ii] = msg[ii].getMessageUnit();
         return getCb().update(callbackAddress.getSessionId(), updateArr);
      } catch (RemoteException e) {
         String str;
         if (msg.length > 1)
            str = "RMI Callback of " + msg.length + " messages to client [" + callbackAddress.getSessionId() + "] failed, reason=" + e.toString();
         else
            str = "RMI Callback of message '" + msg[0].getMessageUnit().getXmlKey() + "' to client [" + callbackAddress.getSessionId() + "] failed, reason=" + e.toString();
         throw new XmlBlasterException("CallbackFailed", str);
      }
   }

   /**
    * The oneway variant, without return value. 
    * @exception XmlBlasterException Is never from the client (oneway).
    */
   public void sendUpdateOneway(MsgQueueEntry[] msg) throws XmlBlasterException
   {
      if (msg == null || msg.length < 1) throw new XmlBlasterException(ME, "Illegal updateOneway argument");
      if (Log.TRACE) Log.trace(ME, "xmlBlaster.updateOneway() to " + callbackAddress.getSessionId());

      try {
         MessageUnit[] updateArr = new MessageUnit[msg.length];
         for (int ii=0; ii<msg.length; ii++)
            updateArr[ii] = msg[ii].getMessageUnit();
         getCb().updateOneway(callbackAddress.getSessionId(), updateArr);
      } catch (Throwable e) {
         String str;
         if (msg.length > 1)
            str = "RMI oneway callback of " + msg.length + " messages to client [" + callbackAddress.getSessionId() + "] failed, reason=" + e.toString();
         else
            str = "RMI oneway callback of message '" + msg[0].getMessageUnit().getXmlKey() + "' to client [" + callbackAddress.getSessionId() + "] failed, reason=" + e.toString();
         throw new XmlBlasterException("CallbackFailed", str);
      }
   }

   /**
    * Ping to check if callback server is alive. 
    * This ping checks the availability on the application level.
    * @param qos Currently an empty string ""
    * @return    Currently an empty string ""
    * @exception XmlBlasterException If client not reachable
    */
   public final String ping(String qos) throws XmlBlasterException
   {
      try {
         return getCb().ping(qos);
      } catch (Throwable e) {
         throw new XmlBlasterException("CallbackPingFailed", "RMI callback ping failed: " + e.toString());
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
      callbackAddress = null;
      if (Log.TRACE) Log.trace(ME, "Shutdown implementation is missing");
   }
}
