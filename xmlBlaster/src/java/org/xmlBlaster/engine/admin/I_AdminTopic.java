/*------------------------------------------------------------------------------
Name:      I_AdminTopic.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to access information about a client instance
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin;

import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.qos.QueryQosData;

/**
 * Declares available methods of a topic for administration. 
 * <p />
 * JMX, SNMP or telnet tools can access only the here declared properties.<br />
 * This interface is implemented by TopicHandler.java, delivering the meat.
 * @author xmlBlaster@marcelruff.info
 * @since 1.0.4
 */
public interface I_AdminTopic {
   public java.lang.String getId();
   public boolean getDomTreeExists();
   public boolean getTopicXmlExists();
   public String getTopicXml() throws org.xmlBlaster.util.XmlBlasterException;
   //org.xmlBlaster.engine.SubscriptionInfo removeSubscriber(java.lang.String);
   public java.lang.String getUniqueKey();
   public java.lang.String getContentMime();
   public java.lang.String getContentMimeExtended();
   public int getNumSubscribers();
   public boolean getExactSubscribersExist();
   public long getNumOfHistoryEntries();
   public long getNumOfCacheEntries();
   public boolean isUndef();
   public boolean isUnconfigured();
   public boolean isAlive();
   public boolean isUnreferenced();
   public boolean isSoftErased();
   public boolean isDead();
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
    * Invoke operation to erase the topic
    * @return A status message
    */
   public String eraseTopic() throws org.xmlBlaster.util.XmlBlasterException;
}
