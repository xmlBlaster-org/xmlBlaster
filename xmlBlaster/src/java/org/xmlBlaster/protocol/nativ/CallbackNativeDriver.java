/*------------------------------------------------------------------------------
Name:      CallbackNativeDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using native interface.
Version:   $Id: CallbackNativeDriver.java,v 1.12 2002/11/26 12:39:13 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.nativ;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.queuemsg.MsgQueueUpdateEntry;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;


/**
 * This object sends a MessageUnit back to a client using native interface, in
 * the same JVM.
 * <p>
 * The I_CallbackDriver.update() method of the client will be invoked
 *
 * @author ruff@swand.lake.de
 * @see org.xmlBlaster.protocol.nativ.NativeDriver
 */
public class CallbackNativeDriver implements I_CallbackDriver
{
   private String ME = "CallbackNativeDriver";
   private Global glob = null;
   private LogChannel log = null;
   private CallbackAddress callbackAddress = null;


   /** Get a human readable name of this driver */
   public String getName() {
      return ME;
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return "NativeDemo"
    */
   public String getProtocolId() {
      return "NativeDemo";
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
    * @return null
    */
   public String getRawAddress() {
      log.error(ME+".getRawAddress()", "No external access address available");
      return null;
   }

   /**
    * Get callback reference here.
    * <p />
    * This method is enforced by interface I_CallbackDriver and is called by
    * xmlBlaster after instantiation of this class, telling us
    * the address to callback.
    * @param  callbackAddress Contains the stringified native callback handle of the client
    */
   public void init(Global glob, CallbackAddress callbackAddress) throws XmlBlasterException
   {
      this.glob = glob;
      this.log = glob.getLog("nativ");
      this.callbackAddress = callbackAddress;
   }


   /**
    * This sends the update to the client.
    * <p />
    * This method is enforced by interface I_CallbackDriver and is called by
    * @exception e.id="CallbackFailed", should be caught and handled appropriate
    */
   public final String[] sendUpdate(MsgQueueUpdateEntry[] msg) throws XmlBlasterException
   {
      try {
         if (msg == null || msg.length < 1) 
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal sendUpdate() argument");
         log.info(ME, "xmlBlaster.update(" + msg[0].getLogId() + ") from sender " + msg[0].getSender() + " to " + callbackAddress.getAddress());

         String[] ret = new String[msg.length];
         for (int ii=0; ii<ret.length; ii++)
            ret[ii] = Constants.RET_OK; // "<qos><state id='OK'/></qos>";
         return ret;
      }
      catch (XmlBlasterException xmlBlasterException) {

         // WE ONLY ACCEPT ErrorCode.USER... FROM CLIENTS !
         if (xmlBlasterException.isUser())
            throw xmlBlasterException;

         throw new XmlBlasterException(glob, ErrorCode.USER_UPDATE_ERROR, ME,
                   "JDBC processing problem", xmlBlasterException);
      }
      catch (Throwable throwable) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME,
                   "Internal JDBC processing problem", throwable);
      }
   }


   /**
    * The oneway variant, without return value. 
    * @exception XmlBlasterException Is never from the client (oneway).
    */
   public void sendUpdateOneway(MsgQueueUpdateEntry[] msg) throws XmlBlasterException
   {
      if (msg == null || msg.length < 1) 
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal sendUpdateOneway() argument");
      log.info(ME, "xmlBlaster.updateOneway(" + msg[0].getLogId() + ") from sender " + msg[0].getSender() + " to " + callbackAddress.getAddress());
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
      return "";
   }

   /**
    * This method shuts down the driver.
    * <p />
    */
   public void shutdown()
   {
   }
}
