/*------------------------------------------------------------------------------
Name:      CallbackCorbaDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.corba;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.xbformat.I_ProgressListener;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallback;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackHelper;


/**
 * This object sends a MsgUnitRaw back to a client using Corba.
 * <p>
 * The BlasterCallback.update() method of the client will be invoked
 *
 * @author xmlBlaster@marcelruff.info
 */
public class CallbackCorbaDriver implements I_CallbackDriver
{
   private String ME = "CallbackCorbaDriver";
   private Global glob;
   private LogChannel log;
   private BlasterCallback cb;
   private CallbackAddress callbackAddress;
   private OrbInstanceWrapper orbInstanceWrapper;
   private org.omg.CORBA.ORB orb;

   public CallbackCorbaDriver () {
   }

   /** Get a human readable name of this driver */
   public final String getName() {
      return ME;
   }

   /**
    * Get callback reference here (== connectLowLevel()).
    * @param  callbackAddress Contains the stringified CORBA callback handle of the client
    */
   public final void init(Global glob, CallbackAddress callbackAddress) throws XmlBlasterException {
      this.glob = glob;
      this.log = glob.getLog("corba");
      this.callbackAddress = callbackAddress;
      String callbackIOR = callbackAddress.getRawAddress();
      try {
         this.orbInstanceWrapper = OrbInstanceFactory.getOrbInstanceWrapper(this.glob, Constants.RELATING_CALLBACK);
         this.orb = this.orbInstanceWrapper.getOrb((String[])null, this.glob.getProperty().getProperties(), this.callbackAddress);
         this.cb = BlasterCallbackHelper.narrow(this.orb.string_to_object(callbackIOR));
         if (log.TRACE) log.trace(ME, "Accessing client callback reference using given IOR string");
      }
      catch (Throwable e) {
         log.error(ME, "The given callback IOR ='" + callbackIOR + "' is invalid: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, "Corba-CallbackHandleInvalid", "The given callback IOR is invalid: " + e.toString());
      }
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return "IOR"
    */
   public final String getProtocolId() {
      return "IOR";
   }

   /**
    * Enforced by I_Plugin
    * @return "IOR"
    */
   public String getType() {
      return getProtocolId();
   }

   /**
    * Enforced by I_Plugin
    * @return "1.0"
    */
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
    * @return "IOR:00034500350..."
    */
   public final String getRawAddress() {
      return callbackAddress.getRawAddress();
   }

   /**
    * This sends the update to the client.
    * @exception e.id="CallbackFailed", should be caught and handled appropriate
    */
   public final String[] sendUpdate(MsgUnitRaw[] msgArr) throws XmlBlasterException {
      if (msgArr == null || msgArr.length < 1 || msgArr[0] == null) {
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal sendUpdate() argument");
      }

      org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] updateArr = new org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[msgArr.length];
      for (int ii=0; ii<msgArr.length; ii++) {
         updateArr[ii] = convert(msgArr[ii]);
      }

      try {
         return this.cb.update(callbackAddress.getSecretSessionId(), updateArr);
      } catch (org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException ex) {
         XmlBlasterException xmlBlasterException = CorbaDriver.convert(glob, ex);

         // WE ONLY ACCEPT ErrorCode.USER... FROM CLIENTS !
         if (xmlBlasterException.isUser())
            throw xmlBlasterException;

         if (callbackAddress == null) {
            throw new XmlBlasterException(glob, ErrorCode.USER_UPDATE_ERROR, ME,
                   "CORBA Callback of " + msgArr.length + " messages failed",
                   xmlBlasterException);
         }
         else {
            throw new XmlBlasterException(glob, ErrorCode.USER_UPDATE_ERROR, ME,
                   "CORBA Callback of " + msgArr.length + " messages to client [" +
                   callbackAddress.getSecretSessionId() + "] failed.", xmlBlasterException);
         }
      } catch (Throwable e) {
         if (callbackAddress == null)
            throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                "CORBA Callback of " + msgArr.length + " messages failed", e);
         else
            throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                "CORBA Callback of " + msgArr.length + " messages to client ["
                 + callbackAddress.getSecretSessionId() + "] failed", e);
      }
   }

   /**
    * The oneway variant, without return value. 
    * @exception XmlBlasterException Is never from the client (oneway).
    */
   public final void sendUpdateOneway(MsgUnitRaw[] msgArr) throws XmlBlasterException
   {
      if (msgArr == null || msgArr.length < 1)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal sendUpdateOneway() argument");
      if (log.TRACE) log.trace(ME, "xmlBlaster.updateOneway() to " + callbackAddress.getRawAddress());
      //log.info(ME, "xmlBlaster.updateOneway(" + msgArr.length + ")");

      org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] updateArr = new org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[msgArr.length];
      for (int ii=0; ii<msgArr.length; ii++) {
         updateArr[ii] = convert(msgArr[ii]);
      }

      try {
         this.cb.updateOneway(callbackAddress.getSecretSessionId(), updateArr);
      } catch (Throwable e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
               "CORBA oneway callback of " + msgArr.length + " messages to client [" +
               callbackAddress.getSecretSessionId() + "] failed", e);
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
      if (log.CALL) log.call(ME, "ping client");
      try {
         return this.cb.ping(qos);
      } catch (Throwable e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                     "CORBA callback ping failed", e);
      }
   }

   /**
    * Converts the internal MessageUnit to the CORBA message unit.
    */
   public static final org.xmlBlaster.protocol.corba.serverIdl.MessageUnit convert(org.xmlBlaster.util.MsgUnitRaw mu)
   {
      return new org.xmlBlaster.protocol.corba.serverIdl.MessageUnit(mu.getKey(), mu.getContent(), mu.getQos());
   }

   /**
    * @return true if the plugin is still alive, false otherwise
    */
   public boolean isAlive() {
      return true;
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
      if (log != null && log.CALL) log.call(ME, "Entering shutdown ...");
      if (this.cb != null) {
         // CorbaDriver.getOrb().disconnect(this.cb); TODO: !!! must be called delayed, otherwise the logout() call from the client is aborted with a CORBA exception
         this.cb._release();
         //cbfactory.releaseCb(this.cb);
         this.cb = null;
      }
      callbackAddress = null;

      if (this.orbInstanceWrapper != null) {
         this.orbInstanceWrapper.releaseOrb(false);
      }

      // On disconnect: called once for sessionQueue and for last session for subjectQueue as well
      if (log != null && log.TRACE) log.trace(ME, "Shutdown of CORBA callback client done.");
   }

}
