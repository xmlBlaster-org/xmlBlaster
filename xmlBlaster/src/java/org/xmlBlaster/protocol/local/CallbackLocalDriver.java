/*
 * Copyright (c) 2003 Peter Antman, Teknik i Media  <peter.antman@tim.se>
 *
 * $Id$
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.xmlBlaster.protocol.local;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.xbformat.I_ProgressListener;

/**
 * A server callback driver for in jvm calls.
 * <p>The requirements for this driver is that an object of type {@link I_LocalCallback} is registered in objectEntry at callbackAddress.getRawAddress().</p>
 *
 * @author <a href="mailto:pra@tim.se">Peter Antman</a>
 * @version $Revision: 1.3 $
 * @see org.xmlBlaster.client.protocol.local.LocalCallbackImpl
 */

public class CallbackLocalDriver implements I_CallbackDriver {
   private String ME = "CallbackLocalDriver";
   private Global glob;
   private static Logger log = Logger.getLogger(CallbackLocalDriver.class.getName());
   private I_CallbackExtended callback;
   private CallbackAddress callbackAddress;
   
   public CallbackLocalDriver (){
      
   }
   
   /** Get a human readable name of this driver */
   public final String getName() {
      return ME;
   }
   
   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return "LOCAL"
    */
   public String getProtocolId() {
      return "LOCAL";
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
    * This method is not used, since callbacks plugins are actually factories.
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
   }

   /**
    * Get the address how to access this driver. 
    * @return "rmi://www.mars.universe:1099/I_AuthServer"
    */
   public final String getRawAddress() {
      return callbackAddress.getRawAddress();
   }

   /**
    * 
    * @return The callback to be invoked. Note that this is known only when the client side
    * has invoked the init method. Before that the callback will be unknown and this method
    * will throw a Communication Exception.
    * @throws XmlBlasterException
    */
   private final I_CallbackExtended getCallback() throws XmlBlasterException {
      if (this.callback != null)
         return this.callback;
      synchronized (this) {
         if (this.callback != null)
            return this.callback;
         if (this.callbackAddress == null)
            throw new XmlBlasterException(this.glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "getCallback");
         org.xmlBlaster.engine.Global
            engineGlob = (org.xmlBlaster.engine.Global)this.glob.getObjectEntry("ServerNodeScope");
         if (engineGlob == null)
            throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "could not retreive the ServerNodeScope. Am I really on the server side ?");
         this.callback = (I_CallbackExtended)engineGlob.getObjectEntry(getRawAddress());
         if (this.callback == null)
            throw new XmlBlasterException(this.glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "getCallback");
         return this.callback;
      }
   }
   
   /**
    * Get callback reference here (== connectLowLevel()).
    * @param  callbackAddress Contains the stringified local object in in Global callback handle of the client
    */
   public final synchronized void init(Global glob, CallbackAddress callbackAddress) throws XmlBlasterException {
      this.glob = glob;

      this.callbackAddress = callbackAddress;
   }
   
   /**
    * This sends the update to the client.
    * @exception Exceptions thrown from client are re thrown as ErrorCode.USER*
    */
   public final String[] sendUpdate(MsgUnitRaw[] msgArr) throws XmlBlasterException {
      if (msgArr == null || msgArr.length < 1)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal sendUpdate() argument");
      if (log.isLoggable(Level.FINE)) 
         log.fine("xmlBlaster.update() to " + callbackAddress.getSecretSessionId());
      
      try {
         return getCallback().update(callbackAddress.getSecretSessionId(), msgArr);
      } 
      catch (XmlBlasterException e) { // WE ONLY ACCEPT ErrorCode.USER... FROM CLIENTS !
         throw XmlBlasterException.tranformCallbackException(e);
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
      
      if (log.isLoggable(Level.FINE)) log.fine("xmlBlaster.updateOneway() to " + callbackAddress.getSecretSessionId());
      
      try {
         getCallback().updateOneway(callbackAddress.getSecretSessionId(), msgArr);
      } 
      catch (XmlBlasterException e) {
         throw XmlBlasterException.tranformCallbackException(e);
      }
      catch (Throwable e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
               "Local oneway callback of message to client [" + callbackAddress.getSecretSessionId() + "] failed", e);
      }
   }
   
   /**
    * Ping to check if callback server is alive. 
    * This ping checks the availability on the application level.
    * @param qos Currently an empty string ""
    * @return    Currently an empty string ""
    * @exception XmlBlasterException If client not reachable
    */
   public final String ping(String qos) throws XmlBlasterException {
      getCallback();
      return Constants.RET_OK;
   }

   public I_ProgressListener registerProgressListener(I_ProgressListener listener) {
      if (log.isLoggable(Level.FINE)) log.fine("Registering I_ProgressListener is not supported with this protocol plugin");
      return null;
   }

   /**
    * This method shuts down the driver.
    * <p />
    */
   public synchronized void shutdown() {
      this.callback = null;
      this.callbackAddress = null;
      if (log.isLoggable(Level.FINE)) log.fine("Shutdown implementation is missing");
   }

   /**
    * @return true if the plugin is still alive, false otherwise
    */
   public boolean isAlive() {
      return true;
   }


}// CallbackLocalDriver
