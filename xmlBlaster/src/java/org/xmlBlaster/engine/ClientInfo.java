/*------------------------------------------------------------------------------
Name:      ClientInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.jutils.init.Property;

import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.callback.CbInfo;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.authentication.AuthenticationInfo;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.engine.helper.Destination;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.authentication.plugins.PluginManager;

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
 * @author $Author: ruff $
 */
public class ClientInfo
{
   public  static long sentMessages = 0L;
   private        String                   ME = "ClientInfo";
   private        String            loginName = null; // the unique client identifier
   private        AuthenticationInfo authInfo = null; // all client informations
   private        I_Session sessionSecurityCtx = null;
   /** Holding the callback connections */
   private        CbInfo        cbInfo = new CbInfo();
   private static long instanceCounter = 0L;
   private        long      instanceId = 0L;

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
   public ClientInfo(AuthenticationInfo authInfo, I_Session sessionSecurityCtx)
          throws XmlBlasterException
   {
      if (sessionSecurityCtx==null) {
         Log.error(ME+".illegalArgument",
          "ClientInfo(sessionSecurityCtx==null);" +
          " // a correct security manager must be set!!!");
         throw new XmlBlasterException(ME+".illegalArgument",
                                       "ClientInfo(sessionSecurityCtx==null);" +
                                       " // the correct session context must be set!!!");
      }
      this.sessionSecurityCtx = sessionSecurityCtx;
      instanceId = instanceCounter;
      instanceCounter++;
      if (Log.CALL) Log.trace(ME, "Creating new ClientInfo " + authInfo.toString());
      notifyAboutLogin(authInfo);
   }


   /**
    * Create this instance when a message is sent to this client, but he is not logged in
    * <p />
    * @param loginName The unique login name
    */
   public ClientInfo(String loginName) throws XmlBlasterException
   {
      instanceId = instanceCounter++;
      PluginManager pLdr = PluginManager.getInstance();
      this.sessionSecurityCtx = pLdr.getDummyManager().reserveSession(loginName);
      if (Log.CALL) Log.trace(ME, "Creating new empty ClientInfo for " + loginName);
      this.loginName = loginName;
   }


   /**
    * PtP mode: This sends the update to the client, or stores it in the client queue or throws an exception.
    * @param msgUnitWrapper Wraps the msgUnit with some more infos
    * @param destination The Destination object of the receiver
    */
   final void sendUpdate(MessageUnitWrapper msgUnitWrapper, Destination destination) throws XmlBlasterException
   {
      if (isLoggedIn()) {
         if (Log.TRACE) Log.trace(ME, "Client [" + loginName + "] is logged in and has registered " + cbInfo.getSize() + " callback drivers, sending message");
         try {
            MessageUnit msg = msgUnitWrapper.getMessageUnitClone();
            msg.qos = getUpdateQoS((String)null, msgUnitWrapper, -1, -1);
            MessageUnit[] arr = new MessageUnit[1];
            arr[0] = msg;
            cbInfo.sendUpdate(this, msgUnitWrapper, arr);
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
         Log.warn(ME+".NotLoggedIn", "Can't deliver message '" + msgUnitWrapper.getXmlKey().getUniqueKey() + "' to client '" + getLoginName() + "', <ForceQueing> is not set, message is lost.");
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
    * @exception e.id="CallbackFailed", should be caught and handled appropriate
    *            This Exception is thrown if the callback server is not reachable
    */
   final void sendUpdate(SubscriptionInfo subInfo) throws XmlBlasterException
   {
      MessageUnitWrapper msgUnitWrapper = subInfo.getMessageUnitWrapper();
      if (isLoggedIn()) {
         if (Log.TRACE) Log.trace(ME, "Client [" + loginName + "] is logged in and has registered " + cbInfo.getSize() + " callback drivers, sending message");
         MessageUnit msg = msgUnitWrapper.getMessageUnitClone();
         msg.qos = getUpdateQoS(subInfo.getSubSourceUniqueKey(), subInfo.getMessageUnitWrapper(), -1, -1);
         MessageUnit[] arr = new MessageUnit[1];
         arr[0] = msg;
         cbInfo.sendUpdate(this, msgUnitWrapper, arr);
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
   public final boolean isLoggedIn()
   {
      return (authInfo != null);
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
      this.cbInfo = new CbInfo(authInfo.getCallbackAddresses());

      // send messages to client, if there are any in the queue
      if (messageQueue != null) {
         int iMessage = 0;
         int numMessages = messageQueue.getSize();
         if (Log.TRACE) Log.trace(ME, "Flushing " + numMessages + " queued messages to client");
         while (true) {
            MessageUnitWrapper msgUnitWrapper = messageQueue.pull();
            if (msgUnitWrapper == null)
               break;
            MessageUnit msg = msgUnitWrapper.getMessageUnitClone();
            msg.qos = getUpdateQoS((String)null, msgUnitWrapper, iMessage, numMessages);
            if (Log.TRACE) Log.trace(ME, "Flushing message #" + iMessage + ": " + msg.qos);
            MessageUnit[] arr = new MessageUnit[1];
            arr[0] = msg;
            // TODO: emails can also be sent to the logged off client!
            cbInfo.sendUpdate(this, msgUnitWrapper,arr);
            sentMessages++;
            iMessage++;
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
   private String getUpdateQoS(String subscriptionId, MessageUnitWrapper msgUnitWrapper, int index, int max) throws XmlBlasterException
   {
      StringBuffer buf = new StringBuffer();
      buf.append("\n<qos>\n");

      // OK | EXPIRED | ERASED !!! not yet supported
      buf.append("   <state>").append("OK").append("</state>\n");

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
      buf.append("   ").append(msgUnitWrapper.getXmlRcvTimestamp()).append("\n");
      if (max > 0) {
         buf.append("   <queue index='").append(index).append("' size='").append(max).append("'>\n");
         buf.append("   </queue>\n");
      }


// wkl to be implemented
// append information of the server userSession etc.
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
            Log.warn(ME, "Logout of client " + toString() + " wich still has messages in the queue");
            msgUnitWrapper = null;
         }
         messageQueue = null;
      }

      // TODO: !!! must be called delayed, otherwise the logout() call from the client is aborted with a CORBA exception
      cbInfo.shutdown();

      this.authInfo = null;
      this.cbInfo = new CbInfo();
   }


   /**
    * This is the unique identifier of the client,
    * it is currently the byte[] oid from the POA active object map
    * with a counter added.
    * <p />
    * @return oid
    */
   public final String getUniqueKey() throws XmlBlasterException
   {
      //return loginName;
      if (authInfo == null) return loginName;
      return authInfo.getUniqueKey();
   }


   /**
    * Access the unique login name of a client.
    * <br />
    * If not known, its unique key (sessionId) is delivered
    * @return loginName
    * @todo The sessionId is a security risk, what else
    *        can we use? !!!
    */
   public final String getLoginName()
   {
      if (loginName == null)
         return authInfo.getLoginName();
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


   public I_Session getSecuritySession() {
      return sessionSecurityCtx;
   }

   public void setSecuritySession(I_Session ctx) {
      this.sessionSecurityCtx = ctx;
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
   public final String toXml() throws XmlBlasterException
   {
      return toXml((String)null);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of ClientInfo as a XML ASCII string
    */
   public final String toXml(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<ClientInfo id='" + instanceId + "' sessionId='" + getUniqueKey() + "'>");
      sb.append(offset + "   <loginName>" + loginName + "</loginName>");
      if (isLoggedIn())
         sb.append(offset + "   <isLoggedIn />");
      else
         sb.append(offset + "   <isNotLoggedIn />");
      sb.append(cbInfo.toXml(extraOffset + "   "));
      sb.append(offset + "</ClientInfo>\n");

      return sb.toString();
   }
}
