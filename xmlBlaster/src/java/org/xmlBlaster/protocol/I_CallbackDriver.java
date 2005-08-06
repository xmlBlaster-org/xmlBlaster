/*------------------------------------------------------------------------------
Name:      I_CallbackDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.MsgUnitRaw;


/**
 * This interface hides the real protocol used to send a client a callback message
 * <p>
 * @author xmlBlaster@marcelruff.info
 */
public interface I_CallbackDriver extends I_Plugin
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
    * @return e.g. "IOR" "EMAIL" "XMLRPC" depending on driver
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
    * @param msgArr Array of all messages to send, is guaranteed to never be null
    * @return Clients should return a qos as follows.
    *         An empty qos string "" is valid as well and
    *         interpreted as OK
    * <pre>
    *  &lt;qos>
    *     &lt;state id='OK'/>  &lt;!-- Client processing state OK | ERROR ... see Constants.java -->
    *  &lt;/qos>
    * </pre>
    * @exception On callback problems you need to throw an XmlBlasterException and
    *            the message will queued until the client logs in again.<br />
    * NOTE: A remote user may only throw ErrorCode.USER*, you have to check the received ErrorCode
    *       and transform it to a e.g ErrorCode.USER_UPDATE_ERROR if it is no user error.<br />
    * NOTE: All connection problems need to be thrown as ErrorCode.COMMUNICATION* errors.
    */
   public String[] sendUpdate(MsgUnitRaw[] msgArr) throws XmlBlasterException;

   /**
    * The oneway variant, without return value
    * @param msgArr Array of all messages to send, is guaranteed to never be null
    * @exception XmlBlasterException Is never from the client (oneway).
    */
   public void sendUpdateOneway(MsgUnitRaw[] msgArr) throws XmlBlasterException;

   /**
    * Ping to check if the clients callback server instance is alive. 
    * This ping checks the availability on the application level, a future implementation
    * could return other values than 'OK' for example to signalize a standby mode
    * of this client.
    *
    * @param qos Currently not looked at, please pass an empty string ""
    * @return "&lt;qos>&lt;state id='OK'/>&lt;/qos>"
    * @exception On connection error
    * @see org.xmlBlaster.protocol.I_XmlBlaster#ping(String)
    */
   public String ping(String qos) throws XmlBlasterException;

   /**
    * @return true if the plugin is still alive, false otherwise
    */
   public boolean isAlive();

   /**
    * Register a listener for to receive information about the progress of incoming data. 
    * Only one listener is supported, the last call overwrites older calls.
    * @param listener Your listener, pass 0 to unregister.
    * @return The previously registered listener or 0
    */
   public I_ProgressListener registerProgressListener(I_ProgressListener listener);

   /**
    * Shut down the driver.
    * <p />
    */
//   public void shutdown();
}

