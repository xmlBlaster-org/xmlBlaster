/*------------------------------------------------------------------------------
Name:      DbWatcherConstants.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwatcher;

/**
 * DbWatcherConstants
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public interface DbWatcherConstants {
   
   // these are used in the XmlBlasterPublisher
   public final static String MOM_CONNECT_QOS = "mom.connectQos";
   public final static String MOM_PROPS_TO_ADD_TO_CONNECT = "mom.propsToAddToConnect";
   public final static String MOM_TOPIC_NAME = "mom.topicName";
   public final static String MOM_STATUS_TOPIC_NAME = "mom.statusTopicName";   
   public final static String MOM_LOGIN_NAME = "mom.loginName";
   public final static String MOM_PASSWORD = "mom.password";
   public final static String MOM_ERASE_ON_DROP = "mom.eraseOnDrop";
   public final static String MOM_ERASE_ON_DELETE = "mom.eraseOnDelete";
   public final static String MOM_PUBLISH_KEY = "mom.publishKey";
   public final static String MOM_PUBLISH_QOS = "mom.publishQos";
   public final static String MOM_ALERT_SUBSCRIBE_KEY = "mom.alertSubscribeKey";
   public final static String MOM_ALERT_SUBSCRIBE_QOS = "mom.alertSubscribeQos";
   public final static String MOM_MAX_SESSIONS = "mom.maxSessions";
   
}
