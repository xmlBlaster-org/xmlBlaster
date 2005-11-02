/*------------------------------------------------------------------------------
Name:      CallbackRmiDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.rmi;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.xbformat.I_ProgressListener;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.RMISecurityManager;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.net.MalformedURLException;


/**
 * This object sends a MsgUnitRaw back to a client using RMI.
 * <p />
 * The BlasterCallback.update() method of the client will be invoked
 * <p />
 * This callback rmi client can be used independent from the RmiDriver.
 * <p />
 * Your client needs to have a callback server implementing interface
 * I_XmlBlasterCallback running and registered with rmi-registry.
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public class CallbackRmiDriver implements I_CallbackDriver
{
   private String ME = "CallbackRmiDriver";
   private I_XmlBlasterCallback cb = null;
   private CallbackAddress callbackAddress = null;
   private Global glob = null;
   private LogChannel log = null;


   /** Get a human readable name of this driver */
   public String getName() {
      return ME;
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return "RMI"
    */
   public String getProtocolId() {
      return "RMI";
   }

   /** Enforced by I_Plugin */
   public String getType() {
      return getProtocolId();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return "1.0";
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
   }

   /**
    * Get the address how to access this driver. 
    * @return "rmi://www.mars.universe:1099/I_AuthServer"
    */
   public String getRawAddress() {
      return callbackAddress.getRawAddress();
   }

   /**
    * Get callback reference here.
    * @param  callbackAddress Contains the rmi registry name of the client callback server
    */
   public void init(Global glob, CallbackAddress callbackAddress) throws XmlBlasterException {
      this.glob = glob;
      this.log = glob.getLog("rmi");
      // Create and install a security manager
      if (System.getSecurityManager() == null) {
         System.setSecurityManager(new RMISecurityManager());
         if (log.TRACE) log.trace(ME, "Started RMISecurityManager");
      }

      this.callbackAddress = callbackAddress;
      String addr = callbackAddress.getRawAddress(); // e.g. "rmi://localhost/xmlBlaster"
      Remote rem = null;
      try {
         rem = Naming.lookup(addr);
      }
      catch (RemoteException e) {
         log.error(ME, "Can't access callback address ='" + addr + "', no client rmi registry running");
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, "Can't access callback address ='" + addr + "', no client rmi registry running", e);
      }
      catch (NotBoundException e) {
         log.error(ME, "The given callback address ='" + addr + "' is not bound to rmi registry: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, "The given callback address '" + addr + "' is not bound to rmi registry", e);
      }
      catch (MalformedURLException e) {
         log.error(ME, "The given callback address ='" + addr + "' is invalid: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, "The given callback address '" + addr + "' is invalid.", e);
      }
      catch (Throwable e) {
         log.error(ME, "The given callback address ='" + addr + "' is invalid : " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, "The given callback address '" + addr + "' is invalid.", e);
      }

      if (rem instanceof org.xmlBlaster.protocol.rmi.I_XmlBlasterCallback) {
         cb = (I_XmlBlasterCallback)rem;
         log.info(ME, "Accessing client callback reference using given '" + addr + "' string");
      }
      else {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, "No callback to '" + addr + "' possible, class needs to implement interface I_XmlBlasterCallback.");
      }
   }

   private I_XmlBlasterCallback getCb() throws XmlBlasterException {
      if (cb == null)
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, "No callback to '" + callbackAddress.getRawAddress() + "' possible, no connection.");
      return cb;
   }

   /**
    * This sends the update to the client.
    * @exception Exceptions thrown from client are re thrown as ErrorCode.USER*
    */
   public final String[] sendUpdate(MsgUnitRaw[] msgArr) throws XmlBlasterException
   {
      if (msgArr == null || msgArr.length < 1)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal sendUpdate() argument");
      if (log.TRACE) log.trace(ME, "xmlBlaster.update() to " + callbackAddress.getSecretSessionId());

      try {
         return getCb().update(callbackAddress.getSecretSessionId(), msgArr);
      } catch (RemoteException remote) {
         Throwable nested = remote.detail;
         if (nested != null && nested instanceof XmlBlasterException) {
            XmlBlasterException xmlBlasterException = (XmlBlasterException)nested;

            // WE ONLY ACCEPT ErrorCode.USER... FROM CLIENTS !
            if (xmlBlasterException.isUser())
               throw xmlBlasterException;

            throw new XmlBlasterException(glob, ErrorCode.USER_UPDATE_ERROR, ME,
                   "RMI Callback of " + msgArr.length +
                   " messages to client [" + callbackAddress.getSecretSessionId() + "] failed.", xmlBlasterException);
         }
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                     "RMI Callback of " + msgArr.length + " messages to client [" + callbackAddress.getSecretSessionId() + "] failed", remote);
      }
   }

   /**
    * The oneway variant, without return value. 
    * @exception XmlBlasterException Is never from the client (oneway).
    */
   public void sendUpdateOneway(MsgUnitRaw[] msgArr) throws XmlBlasterException
   {
      if (msgArr == null || msgArr.length < 1)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal sendUpdateOneway() argument");

      if (log.TRACE) log.trace(ME, "xmlBlaster.updateOneway() to " + callbackAddress.getSecretSessionId());

      try {
         getCb().updateOneway(callbackAddress.getSecretSessionId(), msgArr);
      } catch (Throwable e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
            "RMI oneway callback of message to client [" + callbackAddress.getSecretSessionId() + "] failed", e);
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
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                     "RMI callback ping failed", e);
      }
   }

   public I_ProgressListener registerProgressListener(I_ProgressListener listener) {
      if (log.TRACE) log.trace(ME, "Registering I_ProgressListener is not supported with this protocol plugin");
      return null;
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
      if (log.TRACE) log.trace(ME, "Shutdown implementation is missing");
   }

   /**
    * @return true if the plugin is still alive, false otherwise
    */
   public boolean isAlive() {
      return true;
   }


}
