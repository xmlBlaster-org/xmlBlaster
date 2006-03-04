/*------------------------------------------------------------------------------
Name:      I_AdminTopic.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to access information about a client instance
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin;

import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.admin.I_AdminUsage;

/**
 * Declares available methods of a topic for administration. 
 * <p />
 * JMX, SNMP or telnet tools can access only the here declared properties.<br />
 * This interface is implemented by TopicHandler.java, delivering the meat.
 * @author xmlBlaster@marcelruff.info
 * @since 1.0.4
 */
public interface I_AdminTopic extends I_AdminUsage {
   /**
    * The unique identifier of this topic. 
    * @return e.g. "/node/heron/topic/Hello"
    */
   public java.lang.String getId();
   /**
    * Check if this topic is query-able by XPath. 
    * @return true if the DOM entry exists
    */
   public boolean getDomTreeExists();
   public boolean getTopicXmlExists();
   /**
    * Access the topics xml key. 
    * @return The XML string
    */
   public String getTopicXml() throws org.xmlBlaster.util.XmlBlasterException;
   //org.xmlBlaster.engine.SubscriptionInfo removeSubscriber(java.lang.String);
   /**
    * Access the topic key oid. 
    * @return Value of &lt;key oid=''/>
    */
   public java.lang.String getUniqueKey();
   /**
    * Access the topics content mime. 
    * @return Value of &lt;key contentMime=''/>
    */
   public java.lang.String getContentMime();
   /**
    * Access the topics content mime extended. 
    * @return Value of &lt;key contentMimeExtended=''/>
    */
   public java.lang.String getContentMimeExtended();
   /**
    * Access the number of registered subscribers on this topic. 
    * @return Number of subscriptions
    */
   public int getNumSubscribers();
   public boolean getExactSubscribersExist();
   /**
    * Get number of queued history messages. 
    * @return Number of messages
    */
   public long getNumOfHistoryEntries();
   /**
    * Get number of queued history messages which are in the cache. 
    * @return Number of cached messages
    */
   public long getNumOfCacheEntries();
   /*
   public boolean isUndef();
   public boolean isUnconfigured();
   public boolean isAlive();
   public boolean isUnreferenced();
   public boolean isSoftErased();
   public boolean isDead();
   */
   /**
    * Get the life cycle status of this topic. 
    * @return "ALIVE" or "DEAD" etc.
    */
   public java.lang.String getStateStr();
   /**
    * Get a list of all subscribers of this topic. 
    * @return array with absolute session names
    */
   public String[] getSubscribers();
   /**
    * Invoke operation to unSubscribe one client by index of getSubscribers() listed. 
    * Note: The subscriber does not get any notification that his subscription is lost.
    * @param index 0 will kill the first listed subscribing client, 1 the second and so forth
    * @param qos The qos XML string (e.g. "" or "<qos/>")
    * @return The status string
    */
   public String[] unSubscribeByIndex(int index, String qos) throws XmlBlasterException;
   /**
    * Invoke operation to unSubscribe one client by index of getSubscribers() listed. 
    * Note: The subscriber does not get any notification that his subscription is lost.
    * @param sessionName You can specify a relative name "client/joe/1" or an absolute name "/node/heron/client/joe/1"
    * @param qos The qos XML string (e.g. "" or "<qos/>")
    * @return The status string
    */
   public String[] unSubscribeBySessionName(String sessionName, String qos) throws XmlBlasterException;
   /**
    * Invoke operation to unSubscribe all clients. 
    * Note: The subscribers don't get any notification that their subscription is lost.
    * @param qos The qos XML string (e.g. "" or "<qos/>")
    * @return The status string
    */
   public String[] unSubscribeAll(String qos) throws XmlBlasterException;
   /**
    * Get status dump. 
    */
   public java.lang.String toXml();

   /**
    * Peek messages from history queue, they are not removed. 
    * @param numOfEntries The number of messages to peek, the newest first
    * @return The dump of the messages
    */
   public String[] peekHistoryMessages(int numOfEntries) throws XmlBlasterException;

   /**
    * Peek messages from history queue and dump them to a file, they are not removed. 
    * @param numOfEntries The number of messages to peek, taken from the front
    * @param path The path to dump the messages to, it is automatically created if missing.
    * @return The absolute file names dumped
    */
   public String[] peekHistoryMessagesToFile(int numOfEntries, String path) throws Exception;

   /**
    * Query the history queue, can be peeking or consuming. 
    * @param querySpec Can be configured to be consuming
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.queryspec.QueueQuery.html">The engine.qos.queryspec.QueueQuery requirement</a>
    */
   public MsgUnit[] getHistoryQueueEntries(String querySpec) throws XmlBlasterException;
   
   /**
    * Invoke operation to erase the topic
    * @return A status message
    */
   public String eraseTopic() throws org.xmlBlaster.util.XmlBlasterException;
}
