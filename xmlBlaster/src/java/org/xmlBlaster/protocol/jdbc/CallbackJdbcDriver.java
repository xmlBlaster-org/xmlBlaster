/*------------------------------------------------------------------------------
Name:      CallbackJdbcDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using jdbc interface.
Version:   $Id: CallbackJdbcDriver.java,v 1.5 2002/03/18 00:29:32 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.jdbc;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.engine.queue.MsgQueueEntry;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.helper.CallbackAddress;
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
   private CallbackAddress callbackAddress = null;


   /** Get a human readable name of this driver */
   public String getName()
   {
      return ME;
   }


   /**
    * Get callback reference here.
    * <p />
    * This method is enforced by interface I_CallbackDriver and is called by
    * xmlBlaster after instantiation of this class, telling us
    * the address to callback.
    * @param  callbackAddress Contains the stringified jdbc callback handle of the client
    */
   public void init(CallbackAddress callbackAddress) throws XmlBlasterException
   {
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

      for (int ii=0; ii<msg.length; ii++) {
         JdbcDriver.instance.update(msg[ii].getPublisherName(), msg[ii].getMessageUnit().getContent());
      }
      String[] ret = new String[msg.length];
      for (int ii=0; ii<ret.length; ii++)
         ret[ii] = "<qos><state>OK</state></qos>";
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

      for (int ii=0; ii<msg.length; ii++) {
         try {
            JdbcDriver.instance.update(msg[ii].getPublisherName(), msg[ii].getMessageUnit().getContent());
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
