/*------------------------------------------------------------------------------
Name:      ClientInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Version:   $Id: ClientInfo.java,v 1.24 2000/02/21 10:15:56 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.protocol.corba.CallbackCorbaDriver;
import org.xmlBlaster.protocol.email.CallbackEmailDriver;
import org.xmlBlaster.authentication.AuthenticationInfo;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Destination;
import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallback;


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
 * @version $Revision: 1.24 $
 * @author $Author: ruff $
 */
public class ClientInfo
{
   public static long sentMessages = 0L;
   private String ME = "ClientInfo";
   private String loginName = null;            // the unique client identifier
   private AuthenticationInfo authInfo = null; // all client informations
   private I_CallbackDriver myCallbackDriver = null;
   private static long instanceCounter = 0L;
   private long instanceId = 0L;

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
    * Accessing the CallbackDriver for this client, supporting the
    * desired protocol (CORBA, EMAIL, HTTP).
    *
    * @return the CallbackDriver for this client
    */
   public final I_CallbackDriver getCallbackDriver()
   {
      return myCallbackDriver;
   }


   /**
    * This sends the update to the client, or stores it in the client queue or throws an exception.
    * @param messageUnitWrapper Wraps the messageUnit with some more infos
    * @param destination The Destination object of the receiver (is null in Pub/Sub mode!)
    */
   public final void sendUpdate(MessageUnitWrapper messageUnitWrapper, Destination destination) throws XmlBlasterException
   {
      if (isLoggedIn()) {
         if (Log.TRACE) Log.trace(ME, "Client [" + loginName + "] is logged in, sending message");
         getCallbackDriver().sendUpdate(this, messageUnitWrapper, getUpdateQoS(messageUnitWrapper));
         sentMessages++;
      }
      else {
         if (destination == null) {
            Log.error(ME+".Internal", "Client '" + getLoginName() + "' is not logged in, can't deliver message: In Pub/Sub mode this should not happen!");
            throw new XmlBlasterException(ME+".Internal", "Client '" + getLoginName() + "' is not logged in, can't deliver message '" + messageUnitWrapper.getUniqueKey() + "': In Pub/Sub mode this should not happen!");
         }

         if (!destination.forceQueuing()) {
            Log.warning(ME+".NotLoggedIn", "Client '" + getLoginName() + "' is not logged in, can't deliver message");
            throw new XmlBlasterException(ME+".NotLoggedIn", "Client '" + getLoginName() + "' is not logged in, can't deliver PtP message '" + messageUnitWrapper.getUniqueKey() + "'");
         }

         if (messageQueue == null) {
            messageQueue = new ClientUpdateQueue();
         }
         if (Log.TRACE) Log.trace(ME, "Client [" + loginName + "] is not logged in, queing message");
         messageQueue.push(messageUnitWrapper);
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

      // Get the appropriate callback protocol driver (Future: add driver by reflection with xmlBlaster.properties)
      if (authInfo.useCorbaCB())
         myCallbackDriver = CallbackCorbaDriver.getInstance();
      else if (authInfo.useEmailCB())
         myCallbackDriver = CallbackEmailDriver.getInstance();
      else {
         Log.error(ME, "No callback protocol specified");
         return;
      }

      // send messages to client, if there are any in the queue
      if (messageQueue != null) {
         while (true) {
            MessageUnitWrapper messageUnitWrapper = messageQueue.pull();
            if (messageUnitWrapper == null)
               break;

            getCallbackDriver().sendUpdate(this, messageUnitWrapper, getUpdateQoS(messageUnitWrapper));
            sentMessages++;
         }
      }
   }


   /**
    * The QoS for the update callback, containing the <sender> name.
    * @param messageUnitWrapper The wrapper containing all message infos
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
   private String getUpdateQoS(MessageUnitWrapper messageUnitWrapper)
   {
      return "\n<qos>\n   <sender>\n      " + messageUnitWrapper.getPublisherName() + "\n   </sender>\n</qos>";
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
            MessageUnitWrapper messageUnitWrapper = messageQueue.pull();
            if (messageUnitWrapper == null)
               break;
            Log.warning(ME, "Logout of client " + toString() + " wich still has messages in the queue");
            messageUnitWrapper = null;
         }
         messageQueue = null;
      }

      this.authInfo = null;
      this.myCallbackDriver = null;
   }


   /**
    * Accessing the CORBA Callback reference of the client.
    * <p />
    * @return BlasterCallback reference <br />
    *         null if the client has no callback
    */
   public final BlasterCallback getCB() throws XmlBlasterException
   {
      return authInfo.getCB();
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
    * The uniqueKey in hex notation.
    * <p />
    * @return the uniqueKey in hex notation for dumping it (readable form)
    */
   public final String getUniqueKeyHex() throws XmlBlasterException
   {
      return jacorb.poa.util.POAUtil.convert(getUniqueKey().getBytes(), true);
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
    * Accessing the CORBA Callback reference of the client in string notation.
    * <p />
    * @return BlasterCallback-IOR The CORBA callback reference in string notation
    */
   public final String getCallbackIOR() throws XmlBlasterException
   {
      return authInfo.getCallbackIOR();
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
      if (myCallbackDriver == null)
         sb.append(offset + "   <noCallbackDriver />");
      else if (myCallbackDriver instanceof CallbackCorbaDriver)
         sb.append(offset + "   <CallbackCorbaDriver />");
      else if (myCallbackDriver instanceof CallbackEmailDriver)
         sb.append(offset + "   <CallbackEmailDriver />");
      sb.append(offset + "</ClientInfo>\n");

      return sb;
   }
}
