/*------------------------------------------------------------------------------
Name:      I_CallbackDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface hiding the real callback protocol
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.queue.MsgQueueEntry;


/**
 * This interface hides the real protocol used to send a client a callback message
 * <p>
 * @author ruff
 */
public interface I_CallbackDriver
{
   /** Get a human readable name of this driver */
   public String getName();

   /**
    * Intialize the driver.
    * @param  callbackAddress Contains the callback address,
    *         e.g. the stringified CORBA callback handle of the client or his email address.
    */
   public void init(Global glob, CallbackAddress callbackAddress) throws XmlBlasterException;

   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return e.g. "IOR" "EMAIL" "XML-RPC" depending on driver
    */
   public String getProtocolId();

   /**
    * Return the address how to access this driver. 
    * @return e.g. "http:/www.mars.universe:8080/RPC2" or "IOR:000034100..."
    */
   public String getRawAddress();

   /**
    * Send the message update to the client.
    * <p />
    * The protocol for sending is implemented in the derived class
    *
    * @param sessionInfo Data about a specific client
    * @param msgUnitWrapper For Logoutput only (deprecated?)
    * @param messageUnitArr Array of all messages to send
    * @return Clients should return a qos as follows.
    *         An empty qos string "" is valid as well and
    *         interpreted as OK
    * <pre>
    *  &lt;qos>
    *     &lt;state id='OK'/>  &lt;!-- Client processing state OK | ERROR ... see Constants.java -->
    *  &lt;/qos>
    * </pre>
    * @exception On callback problems you need to throw a XmlBlasterException e.id="CallbackFailed",
    *            the message will queued until the client logs in again
    */
   public String[] sendUpdate(MsgQueueEntry[] msg) throws XmlBlasterException;

   /**
    * The oneway variant, without return value
    * @exception XmlBlasterException Is never from the client (oneway).
    */
   public void sendUpdateOneway(MsgQueueEntry[] msg) throws XmlBlasterException;

   /**
    * Ping to check if xmlBlaster is alive. 
    * This ping checks the availability on the application level.
    * @param qos Currently an empty string ""
    * @return    Currently an empty string ""
    * @exception On connection error
    */
   public String ping(String qos) throws XmlBlasterException;

   /**
    * Shut down the driver.
    * <p />
    */
   public void shutdown();
}

