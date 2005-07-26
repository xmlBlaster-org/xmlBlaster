/*------------------------------------------------------------------------------
Name:      XmlBlasterAccessMBean.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.XmlBlasterException;

/**
 * Export methods for JMX to monitor and control java client connections. 
 */
public interface XmlBlasterAccessMBean {
   /**
    * A unique name for this client, for logging only
    * @return e.g. "/node/heron/client/joe/3"
    */
   String getId();

   /** Get the login name. */
   public String getLoginName();
   /** Check if a callback was configured, if client has passed a callback address on connect. */
   public boolean isCallbackConfigured();
   /**
    * Get the connection state. 
    * @return "UNDEF", "ALIVE", "POLLING", "DEAD"
    */
   public String getConnectionState();
   /** Get the sessions public session id. */
   public long getPublicSessionId();
   /** Get date when client logged in. */
   public String getLoginDate();
   /**
    * Send an event to xmlBlaster to refresh the login session life time.
    * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.qos.login.session.html">session requirement</a>
    * @throws XmlBlasterException like ErrorCode.USER_NOT_CONNECTED and others
    */
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
   public long getClientQueueNumMsgs();
   /** How many messages are max. allowed in this clients session callback queue */
   public long getClientQueueMaxMsgs();
   /**
    * Invoke operation to unSubscribe one/many topics. 
    * @param url The topic oid/xpath to unSubscribe (e.g. "Hello" or "xpath://key")
    * @param qos The qos XML string (e.g. "" or "<qos/>")
    * @return The status string
    */
   //public String[] unSubscribe(String url, String qos) throws XmlBlasterException;
   /**
    * Invoke operation to unSubscribe one topic by index listed. 
    * @param index 0 will kill the first listed subscription, 1 the second and so forth
    * @param qos The qos XML string (e.g. "" or "<qos/>")
    * @return The status string
    */
   //public String[] unSubscribeByIndex(int index, String qos) throws XmlBlasterException;
   /**
    * Invoke operation to subscribe the topic, given by its oid
    * @param url The topic oid/xpath to subscribe (e.g. "Hello" or "xpath://key")
    * @param qos The qos XML string (e.g. "" or "<qos><persistent/></qos>")
    * @return The status string
    */
   //public String subscribe(String url, String qos) throws XmlBlasterException;
   /** activates/inhibits the dispatch of messages to this client */
   public void setDispatcherActive(boolean dispatcherActive);
   /** true if the dispatcher is currently able to dispatch asyncronously */
   public boolean getDispatcherActive();

   /**
    * The cluster node id (name) to which we want to connect.
    * <p />
    * Needed only for nicer logging when running in a cluster.<br />
    * Is configurable with "-server.node.id golan"
    * @return e.g. "golan", defaults to "xmlBlaster"
    */
   String getServerNodeId();

   /**
    * Peek messages from client queue, they are not removed
    * @param numOfEntries The number of messages to peek, taken from the front
    * @return The dump of the messages
    */
   public String[] peekClientMessages(int numOfEntries) throws XmlBlasterException;
   /**
    * Peek messages from client queue and dump them to a file, they are not removed. 
    * @param numOfEntries The number of messages to peek, taken from the front
    * @param path The path to dump the messages to, it is automatically created if missing.
    * @return The absolute file names dumped
    */
   public String[] peekClientMessagesToFile(int numOfEntries, String path) throws XmlBlasterException;
}

