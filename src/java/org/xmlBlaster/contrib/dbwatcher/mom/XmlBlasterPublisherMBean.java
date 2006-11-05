/*------------------------------------------------------------------------------
Name:      XmlBlasterPublisherMBean.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwatcher.mom;

/**
 * XmlBlasterPublisherMBean
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public interface XmlBlasterPublisherMBean {
   
   String getAdminKey();
   void setAdminKey(String adminKey);
   String getAlertSubscribeKey();
   void setAlertSubscribeKey(String alertSubscribeKey);
   String getAlertSubscribeQos();
   void setAlertSubscribeQos(String alertSubscribeQos);
   String getAlertSubscriptionId();
   void setAlertSubscriptionId(String alertSubscriptionId);
   int getCompressSize();
   void setCompressSize(int compressSize);
   String getConnectQos();
   boolean isEraseOnDelete();
   void setEraseOnDelete(boolean eraseOnDelete);
   boolean isEraseOnDrop();
   void setEraseOnDrop(boolean eraseOnDrop);
   String getPublishKey();
   void setPublishKey(String publishKey);
   String getPublishQos();
   void setPublishQos(String publishQos);
   boolean isThrowAwayMessages();
   void setThrowAwayMessages(boolean throwAwayMessages);
   String getTopicNameTemplate();
   void setTopicNameTemplate(String topicNameTemplate);
   String getLoginName();
   long getLastPublishTime();
}
