/*------------------------------------------------------------------------------
Name:      CallbackJdbcDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using jdbc interface.
Version:   $Id: CallbackJdbcDriver.java,v 1.1 2000/07/02 18:06:47 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.jdbc;

import org.jutils.log.Log;

import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.MessageUnitWrapper;
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
   public final void sendUpdate(ClientInfo clientInfo, MessageUnitWrapper msgUnitWrapper, org.xmlBlaster.engine.helper.MessageUnit[] msgUnitArr) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Received message update '" + new String(msgUnitArr[0].content) + "' from sender '" + msgUnitWrapper.getPublisherName() + "'");
      for (int ii=0; ii<msgUnitArr.length; ii++) {
         JdbcDriver.instance.update(msgUnitWrapper.getPublisherName(), msgUnitArr[ii].content);
      }
   }


   /**
    * This method shuts down the driver.
    * <p />
    */
   public void shutdown()
   {
   }
}
