/*------------------------------------------------------------------------------
Name:      CallbackJdbcDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using jdbc interface.
Version:   $Id: CallbackJdbcDriver.java,v 1.8 2002/05/30 09:53:28 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.jdbc;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.engine.queue.MsgQueueEntry;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.util.XmlBlasterException;


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
   private CallbackAddress callbackAddress = null;


   /** Get a human readable name of this driver */
   public String getName()
   {
      return ME;
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return "JDBC"
    */
   public String getProtocolId()
   {
      return "JDBC";
   }

   /**
    * Get the address how to access this driver. 
    * @return null
    */
   public String getRawAddress()
   {
      if (Log.TRACE) Log.trace(ME+".getRawAddress()", "No external access address available");
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
      this.callbackAddress = callbackAddress;
   }


   /**
    * This sends the SQL query to the JDBC service for processing.
    * <p />
    * This method is enforced by interface I_CallbackDriver and is called by xmlBlaster
    * @exception e.id="CallbackFailed", should be caught and handled appropriate
    */
   public final String[] sendUpdate(MsgQueueEntry[] msg) throws XmlBlasterException
   {
      if (msg == null || msg.length < 1) throw new XmlBlasterException(ME, "Illegal update argument");
      if (Log.TRACE) Log.trace(ME, "xmlBlaster.update(" + msg[0].getUniqueKey() + ") to " + callbackAddress.getAddress());

      JdbcDriver driver = (JdbcDriver)glob.getObjectEntry("JdbcDriver-"+glob.getId());
      if (driver == null) {
         Log.error(ME, "Can't find JdbcDriver instance");
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(ME, "Internal error, can't find JdbcDriver instance");
      }
      for (int ii=0; ii<msg.length; ii++) {
         driver.update(msg[ii].getPublisherName(), msg[ii].getMessageUnit().getContent());
      }
      String[] ret = new String[msg.length];
      for (int ii=0; ii<ret.length; ii++)
         ret[ii] = Constants.RET_OK;
      return ret;
   }

   /**
    * The oneway variant, without return value. 
    * @exception XmlBlasterException Is never from the client (oneway).
    */
   public void sendUpdateOneway(MsgQueueEntry[] msg) throws XmlBlasterException
   {
      if (msg == null || msg.length < 1) throw new XmlBlasterException(ME, "Illegal update argument");
      if (Log.TRACE) Log.trace(ME, "xmlBlaster.update(" + msg[0].getUniqueKey() + ") to " + callbackAddress.getAddress());

      JdbcDriver driver = (JdbcDriver)glob.getObjectEntry("JdbcDriver-"+glob.getId());
      if (driver == null) {
         Log.error(ME, "Can't find JdbcDriver instance");
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(ME, "Internal error, can't find JdbcDriver instance");
      }
      for (int ii=0; ii<msg.length; ii++) {
         try {
            driver.update(msg[ii].getPublisherName(), msg[ii].getMessageUnit().getContent());
         } catch (Throwable e) {
            throw new XmlBlasterException("CallbackOnewayFailed", "JDBC Callback of " + ii + "th message '" + msg[ii].getUniqueKey() + "' to client [" + callbackAddress.getSessionId() + "] from [" + msg[ii].getPublisherName() + "] failed, reason=" + e.toString());
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
