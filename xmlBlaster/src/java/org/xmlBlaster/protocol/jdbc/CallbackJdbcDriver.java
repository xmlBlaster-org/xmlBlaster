/*------------------------------------------------------------------------------
Name:      CallbackJdbcDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using jdbc interface.
Version:   $Id: CallbackJdbcDriver.java,v 1.12 2002/11/26 12:39:10 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.jdbc;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.queuemsg.MsgQueueUpdateEntry;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;


/**
 * This object sends a MessageUnit back to a client using jdbc interface, in
 * the same JVM.
 * <p>
 * The I_CallbackDriver.update() method of the client will be invoked
 *
 * @author ruff@swand.lake.de
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
   public final String[] sendUpdate(MsgQueueUpdateEntry[] msg) throws XmlBlasterException
   {
      try {
         if (msg == null || msg.length < 1)
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal sendUpdate() argument");
         if (log.TRACE) log.trace(ME, "xmlBlaster.update(" + msg[0].getLogId() + ") to " + callbackAddress.getAddress());

         String id ="JdbcDriver-"+glob.getId(); 
         JdbcDriver driver = (JdbcDriver)glob.getObjectEntry(id);
         if (driver == null) {
            log.error(ME, "Can't find JdbcDriver instance");
            Thread.currentThread().dumpStack();
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "Internal error, can't find JdbcDriver instance '" + id + "'");
         }
         for (int ii=0; ii<msg.length; ii++) {
            driver.update(msg[ii].getSender().getAbsoluteName(), msg[ii].getMessageUnit().getContent());
         }
         String[] ret = new String[msg.length];
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
   public void sendUpdateOneway(MsgQueueUpdateEntry[] msg) throws XmlBlasterException
   {
      if (msg == null || msg.length < 1) 
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal sendUpdateOneway() argument");
      if (log.TRACE) log.trace(ME, "xmlBlaster.update(" + msg[0].getLogId() + ") to " + callbackAddress.getAddress());

      String id = "JdbcDriver-"+glob.getId();
      JdbcDriver driver = (JdbcDriver)glob.getObjectEntry(id);
      if (driver == null) {
         log.error(ME, "Can't find JdbcDriver instance");
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "Internal error, can't find JdbcDriver instance '" + id + "'");
      }
      for (int ii=0; ii<msg.length; ii++) {
         try {
            driver.update(msg[ii].getSender().getAbsoluteName(), msg[ii].getMessageUnit().getContent());
         } catch (Throwable e) {
            throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
               "JDBC Callback of " + ii + "th message '" + msg[ii].getLogId() + "' to client [" + callbackAddress.getSessionId() + "] from [" + msg[ii].getSender() + "] failed.", e);
         }
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
