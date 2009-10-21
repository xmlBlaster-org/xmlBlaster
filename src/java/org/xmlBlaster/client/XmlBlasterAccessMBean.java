/*------------------------------------------------------------------------------
Name:      XmlBlasterAccessMBean.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
See:       http://java.sun.com/products/JavaManagement/best-practices.html
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.XmlBlasterException;

/**
 * Export methods for JMX to monitor and control the java client connection. 
 */
public interface XmlBlasterAccessMBean {
   /**
    * A unique name for this client, for logging only
    * @return e.g. "/node/heron/client/joe/3"
    */
   String getId();

   /**
    * Get the login name.
    * @return For example "joe"
    */
   public String getLoginName();

   /**
    * Check if a callback was configured, if client has passed a callback address on connect.
    * @return true if a callback is configured
    */
   public boolean isCallbackConfigured();
   
   /**
    * Get the connection state. 
    * @return "UNDEF", "ALIVE", "POLLING", "DEAD"
    */
   public String getConnectionState();

   /**
    * Get the sessions public session id.
    * @return For example -1
    */
   public long getPublicSessionId();

   /**
    * Access the unique id of my client side queue. 
    * @return For example "connection:SUB17692784598"
    */
   public String getConnectionQueueId();

   /**
    * Get date when client logged in.
    * @return For example "2005-07-25 12:34:16.79"
    */
   public String getLoginDate();

   /**
    * Send an event to xmlBlaster to refresh the login session life time.
    * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.qos.login.session.html">session requirement</a>
    * @throws XmlBlasterException like ErrorCode.USER_NOT_CONNECTED and others
    */
   public void refreshSession() throws XmlBlasterException;
   
   /** Uptime in seconds */
   public long getUptime();
   
   /** How many messages where send by this clients login session */
   public long getNumPublish();
   /** How many subscribe requests where send by this clients login session */
   public long getNumSubscribe();
   /** How many unsubscribe requests where send by this clients login session */
   public long getNumUnSubscribe();
   /** How many get requests where send by this clients login session */
   public long getNumGet();
   /** How many subscribe requests where send by this clients login session */
   public long getNumErase();
   /** How many oneway messages where received by this clients login session */
   public long getNumUpdateOneway();
   /** How many messages where received by this clients login session */
   public long getNumUpdate();
   
   /** How many messages are in this client side queue */
   public long getConnectionQueueNumMsgs();
   
   /** How many messages are max. allowed in this client side queue */
   public long getConnectionQueueMaxMsgs();

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
   
   public boolean forcePollingForTesting();

   /**
    * Publish a message. 
    * @param key The publish key (e.g. &lt;key oid="Hello">&lt;South/>&lt;/key>)
    * @param qos The qos XML string (e.g. "" or "&lt;qos>&lt;persistent/>&lt;/qos>")
    * @return The status string
    */
   public String invokePublish(String key, String content, String qos) throws Exception;

   /**
    * Invoke operation to unSubscribe one/many topics. 
    * @param url The topic oid/xpath to unSubscribe (e.g. "Hello" or "xpath://key")
    * @param qos The qos XML string (e.g. "" or "<qos/>")
    * @return The status string
    */
   public String[] invokeUnSubscribe(String url, String qos) throws Exception;

   /**
    * Invoke operation to subscribe the topic, given by its oid
    * @param url The topic oid/xpath to subscribe (e.g. "Hello" or "xpath://key")
    * @param qos The qos XML string (e.g. "" or "<qos><persistent/></qos>")
    * @return The status string
    */
   public String invokeSubscribe(String url, String qos) throws Exception;

   /**
    * Invoke operation to get() one/many topics. 
    * @param url The topic oid/xpath to retrieve (e.g. "Hello" or "xpath://key")
    * @param qos The qos XML string (e.g. "" or "<qos/>")
    * @return The status string
    */
   public String[] invokeGet(String url, String qos) throws Exception;

   /**
    * Invoke operation to erase() one/many topics. 
    * @param url The topic oid/xpath to erase (e.g. "Hello" or "xpath://key")
    * @param qos The qos XML string (e.g. "" or "<qos/>")
    * @return The status string
    */
   public String[] invokeErase(String url, String qos) throws Exception;

   /**
    * Activates/inhibits the dispatch of messages to this client. 
    */
   public void setDispatcherActive(boolean dispatcherActive);
   
   /**
    * Access the dispatcher state. 
    * @return true if the dispatcher is currently able to dispatch asyncronously
    */
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
   public String[] peekClientMessages(int numOfEntries) throws Exception;

   /**
    * Peek messages from client queue and dump them to a file, they are not removed. 
    * @param numOfEntries The number of messages to peek, taken from the front
    * @param path The path to dump the messages to, it is automatically created if missing.
    * @return The absolute file names dumped
    */
   public String[] peekClientMessagesToFile(int numOfEntries, String path) throws Exception;
   
   /**
    * Get the xmlBlaster client library version number. 
    * @return For example "1.0.4"
    */
   public String getVersion();
   /**
    * Returns the xmlBlaster client library SVN version control revision number. 
    * @return The subversion revision number of the monitored instance,
    *         for example "13593"
    */
   public String getRevisionNumber();
   /**
    * Returns the date when xmlBlaster client library was compiled. 
    * @return For example "07/28/2005 03:47 PM"
    */
   public String getBuildTimestamp();
   /**
    * The java vendor of the compiler. 
    * @return For example "Sun Microsystems Inc."
    */
   public String getBuildJavaVendor();
   /**
    * The compiler java version. 
    * @return For example "1.5.0"
    */
   public String getBuildJavaVersion();
   
   public String leaveServer();
   
   public String disconnect(String disconnectQos);
}

