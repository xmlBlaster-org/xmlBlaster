/*------------------------------------------------------------------------------
Name:      CallbackNativeDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using native interface.
Version:   $Id: CallbackNativeDriver.java,v 1.8 2002/05/30 09:53:28 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.nativ;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.engine.queue.MsgQueueEntry;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.util.XmlBlasterException;


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
   private CallbackAddress callbackAddress = null;


   /** Get a human readable name of this driver */
   public String getName()
   {
      return ME;
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return "NativeDemo"
    */
   public String getProtocolId()
   {
      return "NativeDemo";
   }

   /**
    * Get the address how to access this driver. 
    * @return null
    */
   public String getRawAddress()
   {
      Log.error(ME+".getRawAddress()", "No external access address available");
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
      this.callbackAddress = callbackAddress;
   }


   /**
    * This sends the update to the client.
    * <p />
    * This method is enforced by interface I_CallbackDriver and is called by
    * @exception e.id="CallbackFailed", should be caught and handled appropriate
    */
   public final String[] sendUpdate(MsgQueueEntry[] msg) throws XmlBlasterException
   {
      if (msg == null || msg.length < 1) throw new XmlBlasterException(ME, "Illegal update argument");
      Log.info(ME, "xmlBlaster.update(" + msg[0].getUniqueKey() + ") from sender " + msg[0].getPublisherName() + " to " + callbackAddress.getAddress());

      String[] ret = new String[msg.length];
      for (int ii=0; ii<ret.length; ii++)
         ret[ii] = Constants.RET_OK; // "<qos><state id='OK'/></qos>";
      return ret;
   }


   /**
    * The oneway variant, without return value. 
    * @exception XmlBlasterException Is never from the client (oneway).
    */
   public void sendUpdateOneway(MsgQueueEntry[] msg) throws XmlBlasterException
   {
      if (msg == null || msg.length < 1) throw new XmlBlasterException(ME, "Illegal updateOneway argument");
      Log.info(ME, "xmlBlaster.updateOneway(" + msg[0].getUniqueKey() + ") from sender " + msg[0].getPublisherName() + " to " + callbackAddress.getAddress());
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
