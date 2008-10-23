/*------------------------------------------------------------------------------
Name:      I_AdminSession.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to access information about a client instance
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin;

import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.admin.I_AdminUsage;
import org.xmlBlaster.util.qos.ConnectQosDataMBean;

/**
 * Declares available methods of a session for administration.
 * <p />
 * SNMP or telnet tools can access only the here declared properties.<br />
 * This interface is implemented by SessionInfo.java, delivering the meat.
 * @author xmlBlaster@marcelruff.info
 * @since 0.79f
 */
public interface I_AdminSession extends ConnectQosDataMBean, I_AdminUsage {
   // TODO: Access ConnectQos and protocol informations

   /** Access the cluster wide unique identifier: /node/heron/client/<loginName>/<publicSessionId> */
   public String getId();
   /** Access the configuration */
   public String getQos();
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
   /** Get the estimated date when the session expires if no refresh occures */
   public String getSessionTimeoutExpireDate();
   /**
    * Get the date when last time a ALIVE state was reached.
    * An alive client is reachable with its callback connection.
    * @return For example "2005-03-21 11:18:12.622"
    */
   public String getAliveSinceDate();
   /**
    * Get the date when last time a POLLING state was reached.
    * A polling client is not reachable with its callback connection.
    * @return For example "2005-03-21 11:18:12.622"
    */
   public String getPollingSinceDate();

   /**
    * Holds the last exception text for JMX.
    * Typically a user exception thrown from the remote client or a communication exception.
    * @return The exception text or "" but never null
    */
   public String getLastCallbackException();

   /**
    * Clear the last exception text.
    */
   public void clearLastCallbackException();

   /**
    * Holds the total amount of exceptions since startup.
    * @return The number of exceptions occurred
    */
   public int getNumCallbackExceptions();

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
   /* The number of bytes read from the currently incoming message */
   /*public long getCurrBytesRead(); Not yet implemented */
   /* The size of the currently incoming message */
   /*public long getNumBytesToRead();*/
   /* Overall bytes received since startup */
   /*public long getOverallBytesRead(); */
   /**
    * The number of bytes read from the currently outgoing message or response.
    * Note: Currently only implemented by the SOCKET protocol driver
    * @return Number of bytes written
    */
   public long getCurrBytesWritten();
   /**
    * The size of the currently outgoing message or response
    * Note: Currently only implemented by the SOCKET protocol driver
    * @return Number of bytes written
    */
   public long getNumBytesToWrite();
   /**
    * Overall bytes send since startup
    * Note: Currently only implemented by the SOCKET protocol driver
    * @return Number of bytes written
    */
   public long getOverallBytesWritten();
   /** How many messages are in this clients session callback queue */
   public long getCbQueueNumMsgs();
   /**
    * Number of bytes used by the callback queue.
    * Note that this is the bytes occupied by the references (which point to the real message in msgUnitStore)
    * @return the approximately occupied bytes
    */
   public long getCbQueueBytes();
   /** How many messages are in this clients session callback queue and are cached
    * @return -1 if no cach queue is used
    */
   public long getCbQueueNumMsgsCache();
   /**
    * Number of bytes used on the RAM part of the callback queue.
    * Note that this is the bytes occupied by the references (which point to the real message in msgUnitStore)
    * @return the approximately occupied bytes; -1L if not applicable (e.g. only a persistent queue is configured)
    */
   public long getCbQueueBytesCache();

   /** How many messages are max. allowed in this clients session callback queue */
   public long getCbQueueMaxMsgs();
   /** How many messages are max. allowed in the cache of the callback queue,
    * @return is -1 if no cache queue implementation is used
    */
   public long getCbQueueMaxMsgsCache();

   /**
    * Send a ping to the clients callback server.
    * @return Returns the pingRoundTripDelay.
    */
   public String pingClientCallbackServer();

   /**
    * Measures the round trip for the last ping() invocation in milli seconds.
    * @return Returns the pingRoundTripDelay.
    */
   public long getPingRoundTripDelay();

   /**
    * Measures the round trip for the last remote method invocation in milli seconds.
    * For example <code>update(), publish(), subscribe()</code>
    * @return Returns the roundTripDelay.
    */
   public long getRoundTripDelay();
   /** Comma separated list of all subscribed topic oids of this login session */
   public String[] getSubscribedTopics();
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
   /**
    * Access a list of all subscriptionId of this login session
    * @return An array with subscriptionId
    */
   public String[] getSubscriptions() throws Exception;
   /** An XML dump of all subscriptions of this login session */
   public String getSubscriptionDump() throws XmlBlasterException;
   /** Invoke operation to destroy the session (force logout) */
   public String killSession() throws XmlBlasterException;
   /**
    * Activates/inhibits the dispatch of messages to this session.
    * This is a very helpful operation as you can temporary stop sending
    * messages to a client, they are nicely queued and after activating again
    * the messages are send.
    * @param dispatcherActive true: callback messages are send to the client if connected
    *        false: messages are hold back
    */
   public void setDispatcherActive(boolean dispatcherActive);
   /** true if the dispatcher is currently able to dispatch asyncronously */
   public boolean getDispatcherActive();
   /**
    * Peek messages from callback queue, they are not removed
    * @param numOfEntries The number of messages to peek, taken from the front
    * @return The dump of the messages
    */
   public String[] peekCallbackMessages(int numOfEntries) throws XmlBlasterException;
   /**
    * Peek messages from callback queue and dump them to a file, they are not removed.
    * @param numOfEntries The number of messages to peek, taken from the front
    * @param path The path to dump the messages to, it is automatically created if missing.
    * @return The absolute file names dumped
    */
   public String[] peekCallbackMessagesToFile(int numOfEntries, String path) throws Exception;

   /**
    *  Gets the entries in the callback queue according
    *  to what is specified in the querySpec
    *  @param querySpec maxEntries=3&maxSize=-1&consumable=false&waitingDelay=0
    *  @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.qos.queryspec.QueueQuery.html
    */
   public MsgUnit[] getCallbackQueueEntries(String querySpec) throws XmlBlasterException;

   /**
    * Removes all callback entries.
    * @return The number of entries erased
    */
   public long clearCallbackQueue();

   /**
    * Removes max num messages.
    * This method does not block.
    * @param numOfEntries Erase num entries or less if less entries are available, -1 erases everything
    * @return Number of entries erased
    * @throws Exception if the underlying implementation gets an exception.
    */
   public long removeFromCallbackQueue(long numOfEntries) throws Exception;

   /**
    * Access all known remote properties kept in this session.
    * The remote properties are client side settings send to the
    * server, they contain informations which, for example,
    * can be used by the EventPlugin to alert administrators.
    * @return The properties in notation key=value
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.events.html">The admin.events requirement</a>
    */
   public String[] getRemoteProperties();

   /**
    * Remove remote properties which key starts with the given prefix
    * @param prefix For examlpe "logging/"
    * @return A string describing success
    */
   public String clearRemotePropertiesStartingWith(String prefix);

   /**
    * Remove all remote properties from this session.
    * @return Some receipt text
    */
   public String clearRemoteProperties();

   /**
    * Add a remote property.
    * Usually this is done by a publish of a client, but for
    * testing reasons we can to it here manually.
    * If the key exists, its value is overwritten
    * @param key The unique key (no multimap)
    * @param value The value, it is assumed to be of type "String"
    * @return Some receipt text
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.events.html">The admin.events requirement</a>
    */
   public String addRemoteProperty(String key, String value);

   // Used internally
   public ConnectQosServer getConnectQos();

   /**
    * Returns true if the connection is currently stalled (detected in the dispatch statistics when pinging).
    * @return
    */
   public boolean isStalled();

   /**
    * Configure server with '-xmlBlaster/acceptWrongSenderAddress true' or "-xmlBlaster/acceptWrongSenderAddress/joe true".
    * @return true: We accept wrong sender address in PublishQos.getSender() (not myself)
    */
   public boolean isAcceptWrongSenderAddress();

   /**
    * @param acceptWrongSenderAddress the acceptWrongSenderAddress to set
    */
   public void setAcceptWrongSenderAddress(boolean acceptWrongSenderAddress);

   /**
    * Prevent clients session from login.
    * 
    * @return true if clients session may not login
    */
   public boolean isBlockClientSessionLogin();

   /**
    * Allow or prevent the clients session to login. Note this is for going into
    * maintenance mode only as you can't hit this button (there is no Session
    * showing this button) if the client hasn't been here and is not a fail save
    * client.
    * 
    * @param blockClient
    *           true to prevent this clients session login
    */
   public String setBlockClientSessionLogin(boolean blockClient);

   public String disconnectClientKeepSession();
}
