/*------------------------------------------------------------------------------
Name:      CallbackCorbaDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using CORBA
Version:   $Id: CallbackCorbaDriver.java,v 1.20 2002/03/22 08:18:48 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.corba;

import org.xmlBlaster.engine.queue.MsgQueueEntry;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallback;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackHelper;


/**
 * This object sends a MessageUnit back to a client using Corba.
 * <p>
 * The BlasterCallback.update() method of the client will be invoked
 *
 * @version $Revision: 1.20 $
 * @author $Author: ruff $
 */
public class CallbackCorbaDriver implements I_CallbackDriver
{
   private String ME = "CallbackCorbaDriver";
   private BlasterCallback cb = null;
   private CallbackAddress callbackAddress = null;


   /** Get a human readable name of this driver */
   public String getName()
   {
      return ME;
   }


   /**
    * Get callback reference here.
    * @param  callbackAddress Contains the stringified CORBA callback handle of the client
    */
   public void init(CallbackAddress callbackAddress) throws XmlBlasterException
   {
      this.callbackAddress = callbackAddress;
      String callbackIOR = callbackAddress.getAddress();
      try {
         cb = BlasterCallbackHelper.narrow(CorbaDriver.getOrb().string_to_object(callbackIOR));
         if (Log.TRACE) Log.trace(ME, "Accessing client callback reference using given IOR string");
      }
      catch (Exception e) {
         Log.error(ME, "The given callback IOR ='" + callbackIOR + "' is invalid: " + e.toString());
         throw new XmlBlasterException("CallbackHandleInvalid", "The given callback IOR is invalid: " + e.toString());
      }
   }


   /**
    * This sends the update to the client.
    * @exception e.id="CallbackFailed", should be caught and handled appropriate
    */
   public final String[] sendUpdate(MsgQueueEntry[] msg) throws XmlBlasterException
   {
      if (msg == null || msg.length < 1) throw new XmlBlasterException(ME, "Illegal update argument");
      if (Log.TRACE) Log.trace(ME, "xmlBlaster.update(" + msg[0].getUniqueKey() + ") to " + callbackAddress.getAddress());
      Log.info(ME, "xmlBlaster.update(" + msg.length + ")");

      org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] updateArr = new org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[msg.length];
      for (int ii=0; ii<msg.length; ii++) {
         updateArr[ii] = convert(msg[ii].getMessageUnit());
      }

      try {
         return cb.update(callbackAddress.getSessionId(), updateArr);
      } catch (org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
         throw new XmlBlasterException("CallbackFailed", "CORBA Callback of " + msg.length + " messages '" + msg[0].getUniqueKey() + "' to client [" + callbackAddress.getSessionId() + "] from [" + msg[0].getPublisherName() + "] failed.\nException thrown by client: id=" + e.id + " reason=" + e.reason);
      } catch (Throwable e) {
         throw new XmlBlasterException("CallbackFailed", "CORBA Callback of " + msg.length + " messages '" + msg[0].getUniqueKey() + "' to client [" + callbackAddress.getSessionId() + "] from [" + msg[0].getPublisherName() + "] failed, reason=" + e.toString());
      }
   }

   /**
    * The oneway variant, without return value. 
    * @exception XmlBlasterException Is never from the client (oneway).
    */
   public void sendUpdateOneway(MsgQueueEntry[] msg) throws XmlBlasterException
   {
      if (msg == null || msg.length < 1) throw new XmlBlasterException(ME, "Illegal updateOneway argument");
      if (Log.TRACE) Log.trace(ME, "xmlBlaster.updateOneway(" + msg[0].getUniqueKey() + ") to " + callbackAddress.getAddress());
      Log.info(ME, "xmlBlaster.updateOneway(" + msg.length + ")");

      org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] updateArr = new org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[msg.length];
      for (int ii=0; ii<msg.length; ii++) {
         updateArr[ii] = convert(msg[ii].getMessageUnit());
      }

      try {
         cb.updateOneway(callbackAddress.getSessionId(), updateArr);
      } catch (Throwable e) {
         throw new XmlBlasterException("CallbackOnewayFailed", "CORBA oneway callback of " + msg.length + " messages '" + msg[0].getUniqueKey() + "' to client [" + callbackAddress.getSessionId() + "] from [" + msg[0].getPublisherName() + "] failed, reason=" + e.toString());
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
         return cb.ping(qos);
      } catch (Throwable e) {
         throw new XmlBlasterException("CallbackPingFailed", "CORBA callback ping failed: " + e.toString());
      }
   }

   /**
    * Converts the internal MessageUnit to the CORBA message unit.
    */
   public static final org.xmlBlaster.protocol.corba.serverIdl.MessageUnit convert(org.xmlBlaster.engine.helper.MessageUnit mu)
   {
      return new org.xmlBlaster.protocol.corba.serverIdl.MessageUnit(mu.xmlKey, mu.content, mu.qos);
   }


   /**
    * This method shuts down the driver.
    * <p />
    */
   public void shutdown()
   {
      // CorbaDriver.getOrb().disconnect(cb); TODO: !!! must be called delayed, otherwise the logout() call from the client is aborted with a CORBA exception
      cb._release();
      //cbfactory.releaseCb(cb);
      cb = null;
      callbackAddress = null;
      Log.info(ME, "Shutdown of CORBA callback client done.");
   }
}
