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
   public java.lang.String toXml();
   /**
    * Invoke operation to erase the topic
    * @return A status message
    */
   public String eraseTopic() throws org.xmlBlaster.util.XmlBlasterException;
}
