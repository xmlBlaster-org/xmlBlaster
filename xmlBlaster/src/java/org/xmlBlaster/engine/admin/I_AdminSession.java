/*------------------------------------------------------------------------------
Name:      I_AdminSession.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to access information about a client instance
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin;

import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.qos.ConnectQosDataMBean;

/**
 * Declares available methods of a session for administration. 
 * <p />
 * SNMP or telnet tools can access only the here declared properties.<br />
 * This interface is implemented by SessionInfo.java, delivering the meat.
 * @author xmlBlaster@marcelruff.info
 * @since 0.79f
 */
public interface I_AdminSession extends ConnectQosDataMBean {
   // TODO: Access ConnectQos and protocol informations

   /** Access the cluster wide unique identifier: /node/heron/client/<loginName>/<publicSessionId> */
   public String getId();
   /** Get the login name. */
   public String getLoginName();
   /** Check if a callback was configured, if client has passed a callback address on connect. */
   public boolean isCallbackConfigured();
   /** Access the callback connection status. */
   public String getConnectionState();
   /** Get the sessions public session id. */
   public long getPublicSessionId();
   /** Get date when client logged in. */
   public String getLoginDate();
   /* Get the estimated date when the session expires if no refresh occures */
   public String getSessionTimeoutExpireDate();
   /** Invoke operation to reactivate the session expiry to full value */
   public void refreshSession() throws XmlBlasterException;
   /** Uptime in seconds */
   public long getUptime();
   /** How many messages where received from this clients login session */
   public long getNumPublish();
   /** How many subscribe requests where received from this clients login session */
   public long getNumSubscribe();
   /** How many unsubscribe requests where received from this clients login session */
   public long getNumUnSubscribe();
   /** How many get requests where received from this clients login session */
   public long getNumGet();
   /** How many subscribe requests where received from this clients login session */
   public long getNumErase();
   /** How many oneway messages where sent to this clients login session */
   public long getNumUpdateOneway();
   /** How many messages where sent to this clients login session */
   public long getNumUpdate();
   /** How many messages are in this clients session callback queue */
   public long getCbQueueNumMsgs();
   /** How many messages are max. allowed in this clients session callback queue */
   public long getCbQueueMaxMsgs();
   /** Comma separated list of all subscribed topic oids of this login session */
   public String[] getSubscribedTopics();
   /** How many topics are currently subscribed */
   public long getNumSubscriptions();
   /** Comma separated list of all subscriptionId of this login session */
   public String getSubscriptionList() throws XmlBlasterException;
   /**
    * Invoke operation to unSubscribe one/many topics. 
    * @param url The topic oid/xpath to unSubscribe (e.g. "Hello" or "xpath://key")
    * @param qos The qos XML string (e.g. "" or "<qos/>")
    * @return The status string
    */
   public String[] unSubscribe(String url, String qos) throws XmlBlasterException;
   /**
    * Invoke operation to unSubscribe one topic by index listed. 
    * @param index 0 will kill the first listed subscription, 1 the second and so forth
    * @param qos The qos XML string (e.g. "" or "<qos/>")
    * @return The status string
    */
   public String[] unSubscribeByIndex(int index, String qos) throws XmlBlasterException;
   /**
    * Invoke operation to subscribe the topic, given by its oid
    * @param url The topic oid/xpath to subscribe (e.g. "Hello" or "xpath://key")
    * @param qos The qos XML string (e.g. "" or "<qos><persistent/></qos>")
    * @return The status string
    */
   public String subscribe(String url, String qos) throws XmlBlasterException;
   /** Get all subscriptionId of this login session */
   public String[] getSubscriptions() throws XmlBlasterException;
   /** An XML dump of all subscriptions of this login session */
   public String getSubscriptionDump() throws XmlBlasterException;
   /** Invoke operation to destroy the session (force logout) */
   public String killSession() throws XmlBlasterException;
   /** activates/inhibits the dispatch of messages to this session */
   public void setDispatcherActive(boolean dispatcherActive);
   /** true if the dispatcher is currently able to dispatch asyncronously */
   public boolean getDispatcherActive();
   /**
    * Peek messages from queue, they are not removed
    * @param num The number of messages to peek, taken from the front
    * @return The XML dump of the messages
    */
   public String[] peekCallbackMessages(int numOfEntries) throws XmlBlasterException;
   /** gets the entries in the callback queue according to what is specified in the qosData object */
   public MsgUnit[] getCbQueueEntries(QueryKeyData keyData, QueryQosData qosData) throws XmlBlasterException;
   
}
