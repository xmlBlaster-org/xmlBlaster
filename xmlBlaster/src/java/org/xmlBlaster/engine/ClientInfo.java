/*------------------------------------------------------------------------------
Name:      ClientInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Version:   $Id: ClientInfo.java,v 1.35 2000/06/18 15:21:59 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.authentication.AuthenticationInfo;
import org.jutils.log.Log;
import org.jutils.init.Property;
import org.xmlBlaster.util.Destination;
import org.xmlBlaster.util.CallbackAddress;
import org.xmlBlaster.util.XmlBlasterException;

import java.util.*;


/**
 * ClientInfo stores all known data about a client.
 * <p />
 * The driver supporting the desired Callback protocol (CORBA/EMAIL/HTTP)
 * is instantiated here.<br />
 * Note that only CORBA is supported in this version.<br />
 * To add a new driver protocol, you only need to implement the empty
 * CallbackEmailDriver.java or any other protocol.
 * <p />
 * It also contains a message queue, where messages are stored
 * until they are delivered at the next login of this client.
 *
 * @version $Revision: 1.35 $
 * @author $Author: ruff $
 */
public class ClientInfo
{
   public static long sentMessages = 0L;
   private String ME = "ClientInfo";
   private String loginName = null;            // the unique client identifier
   private AuthenticationInfo authInfo = null; // all client informations
   private I_CallbackDriver[] callbackDrivers = new I_CallbackDriver[0];
   private static long instanceCounter = 0L;
   private long instanceId = 0L;
   /** Map holding the Class of all protocol I_CallbackDriver.java implementations, e.g. CallbackCorbaDriver */
   private Hashtable protocols = null;

   /**
    * All MessageUnit which can't be delivered to the client (if he is not logged in)
    * are queued here and are delivered when the client comes on line.
    * <p>
    * Node objects = MessageUnit object
    */
   private ClientUpdateQueue messageQueue = null;   // list = Collections.synchronizedList(new LinkedList());


   /**
    * Create this instance when a client did a login.
    * <p />
    * @param authInfo the AuthenticationInfo with the login informations for this client
    */
   public ClientInfo(AuthenticationInfo authInfo) throws XmlBlasterException
   {
      instanceId = instanceCounter;
      instanceCounter++;
      if (Log.CALLS) Log.trace(ME, "Creating new ClientInfo " + authInfo.toString());
      notifyAboutLogin(authInfo);
   }


   /**
    * Create this instance when a message is sent to this client, but he is not logged in
    * <p />
    * @param loginName The unique login name
    */
   public ClientInfo(String loginName)
   {
      instanceId = instanceCounter;
      instanceCounter++;
      if (Log.CALLS) Log.trace(ME, "Creating new empty ClientInfo for " + loginName);
      this.loginName = loginName;
   }


   /**
    * Load the callback drivers from xmlBlaster.properties.
    * <p />
    * Accessing the CallbackDriver for this client, supporting the
    * desired protocol (CORBA, EMAIL, HTTP).
    * <p />
    * Default is "Protocol.Drivers=IOR:org.xmlBlaster.protocol.corba.CorbaDriver"
    */
   private final void loadDrivers()
   {
      if (protocols != null)
         return;

      protocols = new Hashtable();
      String drivers = Property.getProperty("Protocol.CallbackDrivers", "IOR:org.xmlBlaster.protocol.corba.CallbackCorbaDriver");
      StringTokenizer st = new StringTokenizer(drivers, ",");
      int numDrivers = st.countTokens();
      for (int ii=0; ii<numDrivers; ii++) {
         String token = st.nextToken().trim();
         int index = token.indexOf(":");
         if (index < 0) {
            Log.error(ME, "Wrong syntax in xmlBlaster.properties Protocol.CallbackDrivers, driver ignored: " + token);
            continue;
         }
         String protocol = token.substring(0, index).trim();
         String driverId = token.substring(index+1).trim();

         // Load the protocol driver ...
         try {
            if (Log.TRACE) Log.trace(ME, "Trying Class.forName('" + driverId + "') ...");
            Class cl = java.lang.Class.forName(driverId);
            protocols.put(protocol, cl);
            // Log.info(ME, "Found callback driver class '" + driverId + "' for protocol '" + protocol + "'");
         }
         catch (SecurityException e) {
            Log.error(ME, "No right to access the driver class or initializer '" + driverId + "'");
         }
         catch (Throwable e) {
            Log.error(ME, "The driver class or initializer '" + driverId + "' is invalid\n -> check the driver name in xmlBlaster.properties and/or the CLASSPATH to the driver file: " + e.toString());
         }
      }
   }


   /**
    * PtP mode: This sends the update to the client, or stores it in the client queue or throws an exception.
    * @param msgUnitWrapper Wraps the msgUnit with some more infos
    * @param destination The Destination object of the receiver
    */
   final void sendUpdate(MessageUnitWrapper msgUnitWrapper, Destination destination) throws XmlBlasterException
   {
      if (isLoggedIn()) {
         if (Log.TRACE) Log.trace(ME, "Client [" + loginName + "] is logged in and has registered " + callbackDrivers.length + " callback drivers, sending message");
         try {
            for (int ii=0; ii<callbackDrivers.length; ii++) {
               callbackDrivers[ii].sendUpdate(this, msgUnitWrapper, getUpdateQoS((String)null, msgUnitWrapper));
            }
            sentMessages++;
         } catch(XmlBlasterException e) {
            Log.error(ME, "Callback failed, " + e.reason + ". Trying to queue the message ...");
            queueMessage(msgUnitWrapper, destination);
            Log.error(ME, "TODO: Should the receiver be auto logged out if his callback is broken???"); // !!!
            throw e;
         }
      }
      else
         queueMessage(msgUnitWrapper, destination);
   }


   /**
    * PtP mode: If the qos is set to forceQueuing the message is queued.
    * @param msgUnitWrapper Wraps the msgUnit with some more infos
    * @param destination The Destination object of the receiver
    */
   final void queueMessage(MessageUnitWrapper msgUnitWrapper, Destination destination) throws XmlBlasterException
   {
      if (destination == null) {
         Log.error(ME+".Internal", "Destination is missing, can't deliver message '" + msgUnitWrapper.getUniqueKey() + "', message is lost.");
         throw new XmlBlasterException(ME+".Internal", "Destination is missing, can't deliver message '" + msgUnitWrapper.getUniqueKey() + "', message is lost.");
      }

      if (!destination.forceQueuing()) {
         Log.warning(ME+".NotLoggedIn", "Can't deliver message '" + msgUnitWrapper.getXmlKey().getUniqueKey() + "' to client '" + getLoginName() + "', <ForceQueing> is not set, message is lost.");
         throw new XmlBlasterException(ME+".NotLoggedIn", "Can't deliver message '" + msgUnitWrapper.getXmlKey().getUniqueKey() + "' to client '" + getLoginName() + "', <ForceQueing> is not set, message is lost.");
      }

      if (messageQueue == null) {
         messageQueue = new ClientUpdateQueue();
      }
      if (Log.TRACE) Log.trace(ME, "Client [" + loginName + "] is not logged in, queing message");
      messageQueue.push(msgUnitWrapper);
   }


   /**
    * Pub/Sub mode: This sends the update to the client, or stores it in the client queue or throws an exception.
    * @param subInfo Container for all infos about this subscription
    */
   final void sendUpdate(SubscriptionInfo subInfo) throws XmlBlasterException
   {
      MessageUnitWrapper msgUnitWrapper = subInfo.getMessageUnitWrapper();
      if (isLoggedIn()) {
         if (Log.TRACE) Log.trace(ME, "Client [" + loginName + "] is logged in and has registered " + callbackDrivers.length + " callback drivers, sending message");
         for (int ii=0; ii<callbackDrivers.length; ii++) {
            callbackDrivers[ii].sendUpdate(this, msgUnitWrapper, getUpdateQoS(subInfo.getSubSourceUniqueKey(), subInfo.getMessageUnitWrapper()));
         }
         sentMessages++;
      }
      else {
         Log.error(ME+".Internal", "Client [" + getLoginName() + "] is not logged in, can't deliver message: In Pub/Sub mode this should not happen!");
         throw new XmlBlasterException(ME+".Internal", "Client [" + getLoginName() + "] is not logged in, can't deliver message '" + msgUnitWrapper.getUniqueKey() + "': In Pub/Sub mode this should not happen!");
      }
   }


   /**
    * Is the client currently logged in?
    * @return true yes
    *         false client is not on line
    */
   public boolean isLoggedIn()
   {
      return authInfo != null;
   }


   /**
    * Get notification that the client did a login.
    * <p />
    * This instance may exist before a login was done, for example
    * when some messages where directly addressed to this client.<br />
    * This notifies about a client login.
    *
    * @param authInfo the AuthenticationInfo with the login informations for this client
    */
   public final void notifyAboutLogin(AuthenticationInfo authInfo) throws XmlBlasterException
   {
      if (authInfo == null) {
         Log.error(ME, "authInfo==null");
         return;
      }

      this.authInfo = authInfo;
      this.loginName = authInfo.getLoginName();

      if (Log.TRACE) Log.trace(ME, "notifyAboutLogin()");

      // Get the appropriate callback protocol driver, add driver by reflection with xmlBlaster.properties
      // How to protect the misuse of other email addresses??
      loadDrivers();
      CallbackAddress[] cbArr = authInfo.getCallbackAddresses();
      if (cbArr == null) {
         callbackDrivers = new I_CallbackDriver[0];
      }
      else {
         callbackDrivers = new I_CallbackDriver[cbArr.length];
         for (int ii=0; ii<cbArr.length; ii++) {
            // Load the protocol driver ...
            Class cl = (Class)protocols.get(cbArr[ii].getType());
            if (cl == null) {
               Log.error(ME+".UnknownCallbackProtocol", "Sorry, callback type='" + cbArr[ii].getType() + "' is not supported");
               throw new XmlBlasterException("UnknownCallbackProtocol", "Sorry, callback type='" + cbArr[ii].getType() + "' is not supported");
            }


            try {
               callbackDrivers[ii] = (I_CallbackDriver)cl.newInstance();
               callbackDrivers[ii].init(cbArr[ii]);
               if (Log.TRACE) Log.trace(ME, "Created callback driver for protocol '" + cbArr[ii].getType() + "'");
            }
            catch (IllegalAccessException e) {
               Log.error(ME, "The driver class '" + cbArr[ii].getType() + "' is not accessible\n -> check the driver name and/or the CLASSPATH to the driver");
            }
            catch (SecurityException e) {
               Log.error(ME, "No right to access the driver class or initializer '" + cbArr[ii].getType() + "'");
            }
            catch (Throwable e) {
               Log.error(ME, "The driver class or initializer '" + cbArr[ii].getType() + "' is invalid\n -> check the driver name and/or the CLASSPATH to the driver file: " + e.toString());
            }
         }
      }

      // send messages to client, if there are any in the queue
      if (messageQueue != null) {
         while (true) {
            MessageUnitWrapper msgUnitWrapper = messageQueue.pull();
            if (msgUnitWrapper == null)
               break;

            for (int ii=0; ii<callbackDrivers.length; ii++) {
               // TODO: emails can also be sent to the logged off client!
               callbackDrivers[ii].sendUpdate(this, msgUnitWrapper, getUpdateQoS((String)null, msgUnitWrapper));
            }
            sentMessages++;
         }
      }
   }


   /**
    * The QoS for the update callback, containing the <sender> name.
    * @param subscritpionId The subscription id which triggered this update
    *        May be of interest for the client getting the update
    * @param msgUnitWrapper
    * @return the QoS (quality of service) for the update callback<br />
    *   Example:<br />
    *   <pre>
    *      &lt;qos>
    *         &lt;sender>
    *            Tim
    *         &lt;/sender>
    *      &lt;/qos>
    *   </pre>
    */
   private String getUpdateQoS(String subscriptionId, MessageUnitWrapper msgUnitWrapper) throws XmlBlasterException
   {
      StringBuffer buf = new StringBuffer();
      buf.append("\n<qos>\n");

      buf.append("   <state>");  // !!! not yet supported
      buf.append("      OK");    // OK | EXPIRED | ERASED
      buf.append("   </state>");

      if (msgUnitWrapper != null) {
         buf.append("   <sender>\n");
         buf.append("      ").append(msgUnitWrapper.getPublisherName());
         buf.append("\n   </sender>\n");
      }
      if (subscriptionId != null) {
         buf.append("   <subscriptionId>\n");
         buf.append("      ").append(subscriptionId);
         buf.append("\n   </subscriptionId>\n");
      }
      buf.append("</qos>");
      return buf.toString();
   }


   /**
    * Get notification that the client did a logout.
    * <br />
    * Note that the loginName is not reset.
    * @param clearQueue Shall the message queue of the client be destroyed as well?
    */
   public final void notifyAboutLogout(boolean clearQueue) throws XmlBlasterException
   {
      if (clearQueue && messageQueue != null) {
         while (true) {
            MessageUnitWrapper msgUnitWrapper = messageQueue.pull();
            if (msgUnitWrapper == null)
               break;
            Log.warning(ME, "Logout of client " + toString() + " wich still has messages in the queue");
            msgUnitWrapper = null;
         }
         messageQueue = null;
      }

      this.authInfo = null;
      this.callbackDrivers = new I_CallbackDriver[0];
   }


   /**
    * This is the unique identifier of the client,
    * it is currently the byte[] oid from the POA active object map.
    * <p />
    * @return oid
    */
   public final String getUniqueKey() throws XmlBlasterException
   {
      return authInfo.getUniqueKey();
   }


   /**
    * Access the unique login name of a client.
    * @return loginName
    */
   public final String getLoginName()
   {
      return loginName;
   }


   /**
    * Accessing the AuthenticationInfo object
    * <p />
    * @return AuthenticationInfo
    */
   public final AuthenticationInfo getAuthenticationInfo() throws XmlBlasterException
   {
      return authInfo;
   }


   /**
    * The unique login name.
    * <p />
    * @return the loginName
    */
   public final String toString()
   {
      return loginName;
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of ClientInfo as a XML ASCII string
    */
   public final StringBuffer printOn() throws XmlBlasterException
   {
      return printOn((String)null);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of ClientInfo as a XML ASCII string
    */
   public final StringBuffer printOn(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<ClientInfo id='" + instanceId + "'>");
      sb.append(offset + "   <loginName>" + loginName + "</loginName>");
      if (isLoggedIn())
         sb.append(offset + "   <isLoggedIn />");
      else
         sb.append(offset + "   <isNotLoggedIn />");
      if (callbackDrivers.length < 1)
         sb.append(offset + "   <noCallbackDriver />");
      else {
         for (int ii=0; ii<callbackDrivers.length; ii++) {
            sb.append(offset + "   <" + callbackDrivers[ii].getName() + " />");
         }
      }
      sb.append(offset + "</ClientInfo>\n");

      return sb;
   }
}
