/*------------------------------------------------------------------------------
Name:      TopicProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.qos.storage.TopicCacheProperty;
import org.xmlBlaster.util.qos.storage.HistoryQueueProperty;


/**
 * Data container handling properties of a message topic. 
 * <p />
 * QoS Informations sent from the client to the server via the publish() method and back via the update() method<br />
 * They are needed to control xmlBlaster and inform the client.
 * <p />
 * <p>
 * This data holder is accessible through decorators, each of them allowing a specialized view on the data
 * </p>
 * @see org.xmlBlaster.util.qos.MsgQosSaxFactory
 * @see org.xmlBlaster.test.classtest.qos.MsgQosFactoryTest
 * @author xmlBlaster@marcelruff.info
 */
public final class TopicProperty implements java.io.Serializable
{
   private String ME = "TopicProperty";
   private transient Global glob;
   private transient LogChannel log;
   private transient TopicCacheProperty topicCacheProperty;
   private transient HistoryQueueProperty historyQueueProperty;

   /**
    * A topic is destroyed 60 sec after state=UNREFERENCED is reached
    * This default can be modified in xmlBlaster.properties:
    * <pre>
    *    topic.destroyDelay=3600000 # One hour [millisec]
    * </pre>
    * Every message can set the destroyDelay value between 1 and destroyDelay_DEFAULT, 
    * -1L sets the life cycle on forever.
    */ // TODO: Change to use glob instead of Global singleton! What about performance? Put variable into Global?
   private static final long destroyDelay_DEFAULT_DEFAULT = 60*1000L;
   public static final long destroyDelay_DEFAULT = Global.instance().getProperty().get("topic.destroyDelay", destroyDelay_DEFAULT_DEFAULT);

   /* If Pub/Sub style update: contains the subscribe ID which caused this topic */
   //private String subscriptionId;

   /** Is readonly allows only one initial message */
   public static boolean DEFAULT_readonly = false;
   private boolean readonly = DEFAULT_readonly;

   /** 
    * A topic is destroyed 60 sec after state=UNREFERENCED is reached
    * This is the configured destroyDelay in millis
    */
   private long destroyDelay;

   /**
    * Constructs the specialized quality of service object for a publish() or update() call.
    */
   public TopicProperty(Global glob) {
      setGlobal(glob);
      setDestroyDelay(destroyDelay_DEFAULT);
   }

   /*
    * If Pub/Sub style update: contains the subscribe ID which caused this update
    * @param subscriptionId null if PtP message
    */
   //public void setSubscriptionId(String subscriptionId) {
   //   this.subscriptionId = subscriptionId;
   //}

   /*
    * If Pub/Sub style update: contains the subscribe ID which caused this update
    * @return subscribeId or null if PtP message
    */
   //public String getSubscriptionId() {
   //   return subscriptionId;
   //}

   /**
    * @return readonly Once published the message can't be changed. 
    */
   public void setReadonly(boolean readonly) {
      this.readonly = readonly;
   }

   /**
    * @return true/false
    */
   public boolean isReadonly() {
      return readonly;
   }

   /**
    * The life time of the message topic in state UNREFERENCED
    */
   public long getDestroyDelay() {
      return this.destroyDelay;
   }

   /**
    * The life time of the message topic in state UNREFERENCED
    */
   public void setDestroyDelay(long destroyDelay) {
      this.destroyDelay = destroyDelay;
   }

   public boolean hasTopicCacheProperty() {
      return this.topicCacheProperty != null;
   }

   /**
    * @return the configuration of the message store, is never null
    */
   public TopicCacheProperty getTopicCacheProperty() {
      if (this.topicCacheProperty == null) {
         this.topicCacheProperty = new TopicCacheProperty(glob, glob.getId());
      }
      return this.topicCacheProperty;
   }

   public void setTopicCacheProperty(TopicCacheProperty topicCacheProperty) {
      this.topicCacheProperty = topicCacheProperty;
   }

   public boolean hasHistoryQueueProperty() {
      return this.historyQueueProperty != null;
   }

   /**
    * @return the configuration of the history queue, is never null
    */
   public HistoryQueueProperty getHistoryQueueProperty() {
      if (this.historyQueueProperty == null) {
         this.historyQueueProperty = new HistoryQueueProperty(glob, glob.getId());
      }
      return this.historyQueueProperty;
   }

   public void setHistoryQueueProperty(HistoryQueueProperty historyQueueProperty) {
      this.historyQueueProperty = historyQueueProperty;
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of the message QoS as a XML ASCII string
    */
   public String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the message QoS as a XML ASCII string, never null but "" if all values are default
    */
   public String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(512);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<topic");
      if (DEFAULT_readonly != this.readonly) {
         sb.append(" readonly='").append(readonly).append("'");
      }
      if (destroyDelay_DEFAULT_DEFAULT != this.destroyDelay) {
         sb.append(" destroyDelay='").append(this.destroyDelay).append("'");
      }
      sb.append(">");
      //private String subscriptionId;

      if (hasTopicCacheProperty()) {
         sb.append(getTopicCacheProperty().toXml(extraOffset+Constants.INDENT));
      }
      if (hasHistoryQueueProperty()) {
         sb.append(getHistoryQueueProperty().toXml(extraOffset+Constants.INDENT));
      }
      sb.append(offset).append("</topic>");

      if (sb.length() < 22) {
         return "";
      }

      return sb.toString();
   }

   /**
    * Sets the global object (used when deserializing the object)
    */
   public void setGlobal(Global glob) {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.log = this.glob.getLog("core");
   }
}
