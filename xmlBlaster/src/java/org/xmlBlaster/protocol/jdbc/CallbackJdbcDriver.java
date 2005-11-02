/*------------------------------------------------------------------------------
Name:      CallbackJdbcDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.jdbc;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.xbformat.I_ProgressListener;
import org.xmlBlaster.util.def.Constants;


/**
 * This object sends a MsgUnitRaw back to a client using jdbc interface, in
 * the same JVM.
 * <p>
 * The I_CallbackDriver.update() method of the client will be invoked
 *
 * @author xmlBlaster@marcelruff.info
 * @see org.xmlBlaster.protocol.jdbc.JdbcDriver
 */
public class CallbackJdbcDriver implements I_CallbackDriver
{
   private String ME = "CallbackJdbcDriver";
   private Global glob = null;
   private LogChannel log = null;
   private CallbackAddress callbackAddress = null;


   /** Get a human readable name of this driver */
   public String getName() {
      return ME;
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return "JDBC"
    */
   public String getProtocolId() {
      return "JDBC";
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
      if (log.TRACE) log.trace(ME+".getRawAddress()", "No external access address available");
      return null;
   }

   /**
    * Get callback reference here.
    * <p />
    * This method is enforced by interface I_CallbackDriver and is called by
    * xmlBlaster after instantiation of this class, telling us
    * the address to callback.
    * @param  callbackAddress Contains the stringified jdbc callback handle of the client
    */
   public void init(Global glob, CallbackAddress callbackAddress) throws XmlBlasterException
   {
      this.glob = glob;
      this.log = glob.getLog("jdbc");
      if (log.CALL) log.call(ME, "Entering init");
      this.callbackAddress = callbackAddress;
   }


   /**
    * This sends the SQL query to the JDBC service for processing.
    * <p />
    * This method is enforced by interface I_CallbackDriver and is called by xmlBlaster
    * @exception e.id="CallbackFailed", should be caught and handled appropriate
    */
   public final String[] sendUpdate(MsgUnitRaw[] msgArr) throws XmlBlasterException
   {
      try {
         if (msgArr == null || msgArr.length < 1)
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal sendUpdate() argument");

         String id ="JdbcDriver-"+glob.getId(); 
         JdbcDriver driver = (JdbcDriver)glob.getObjectEntry(id);
         if (driver == null) {
            log.error(ME, "Can't find JdbcDriver instance");
            Thread.currentThread().dumpStack();
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "Internal error, can't find JdbcDriver instance '" + id + "'");
         }
         for (int ii=0; ii<msgArr.length; ii++) {
            MsgQosData msgQosData = (MsgQosData)((MsgUnit)msgArr[ii].getMsgUnit()).getQosData();
            driver.update(msgQosData.getSender().getAbsoluteName(), msgArr[ii].getContent());
         }
         String[] ret = new String[msgArr.length];
         for (int ii=0; ii<ret.length; ii++)
            ret[ii] = Constants.RET_OK;
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
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                   "Internal JDBC processing problem", throwable);
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

      String id = "JdbcDriver-"+glob.getId();
      JdbcDriver driver = (JdbcDriver)glob.getObjectEntry(id);
      if (driver == null) {
         log.error(ME, "Can't find JdbcDriver instance");
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "Internal error, can't find JdbcDriver instance '" + id + "'");
      }
      for (int ii=0; ii<msgArr.length; ii++) {
         try {
            MsgQosData msgQosData = (MsgQosData)((MsgUnit)msgArr[ii].getMsgUnit()).getQosData();
            driver.update(msgQosData.getSender().getAbsoluteName(), msgArr[ii].getContent());
         } catch (Throwable e) {
            throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
               "JDBC Callback of " + ii + "th message to client [" + callbackAddress.getSecretSessionId() + "] failed.", e);
         }
      }
   }

   /**
    * Ping to check if callback server is alive. 
    * @see org.xmlBlaster.protocol.I_CallbackDriver#ping(String)
    */
   public final String ping(String qos) throws XmlBlasterException
   {
      return Constants.RET_OK;
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
   }
   
   /**
    * @return true if the plugin is still alive, false otherwise
    */
   public boolean isAlive() {
      return true;
   }

   

}
