/*------------------------------------------------------------------------------
Name:      TopicProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.storage.MsgUnitStoreProperty;
import org.xmlBlaster.util.qos.storage.HistoryQueueProperty;
import org.xmlBlaster.util.property.PropBoolean;
import org.xmlBlaster.util.property.PropLong;
import org.xmlBlaster.util.property.PropString;


/**
 * Data container handling properties of a message topic. 
 * <p />
 * QoS Informations sent from the client to the server via the publish() method<br />
 * They are needed to control xmlBlaster topics.
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
   private static final long serialVersionUID = -8978046284014075499L;
   private String ME = "TopicProperty";
   private transient Global glob;
   private static Logger log = Logger.getLogger(TopicProperty.class.getName());
   private transient MsgUnitStoreProperty msgUnitStoreProperty;
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
   private PropBoolean readonly = new PropBoolean(DEFAULT_readonly);

   /** 
    * A topic is destroyed 60 sec after state=UNREFERENCED is reached
    * This is the configured destroyDelay in millis
    */
   private PropLong destroyDelay = new PropLong(destroyDelay_DEFAULT);

   private PropBoolean createDomEntry = new PropBoolean(true);

   private PropString msgDistributor = new PropString("undef,1.0");

   /**
    * Constructs the specialized quality of service object for a publish() or update() call.
    */
   public TopicProperty(Global glob) {
      setGlobal(glob);
      if (this.glob.isServerSide()) 
         this.msgDistributor.setFromEnv(this.glob, null, "MsgDistributorPlugin/defaultPlugin");
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
      this.readonly.setValue(readonly);
   }

   /**
    * @return true/false
    */
   public boolean isReadonly() {
      return this.readonly.getValue();
   }

   /**
    * The life time of the message topic in state UNREFERENCED
    * @param The destroy delay in milliseconds
    */
   public long getDestroyDelay() {
      return this.destroyDelay.getValue();
   }

   /**
    * The life time of the message topic in state UNREFERENCED
    * @param destroyDelay: > 0 The topic is automatically destroyed after the given millis in state UNREFERENCED<br />
    *        < 0 The topic is only erased by an explicit erase() invocation<br />
    *        == 0 The topic is destroyed immediately when reaching state==UNREFERENCED
    */
   public void setDestroyDelay(long destroyDelay) {
      this.destroyDelay.setValue(destroyDelay);
   }

   /**
    * Is the topic available in the internal DOM tree? 
    * @return true This is default and the topic is queryable with XPATH<br />
    *    false: No DOM tree is created for the topic and the topic is onvisible to XPATH queries
    */
   public boolean createDomEntry() {
      return this.createDomEntry.getValue();
   }

   /**
    * Set if the topic is available in the internal DOM tree. 
    * @param true This is default and the topic is queryable with XPATH<br />
    *    false: No DOM tree is created for the topic and the topic is onvisible to XPATH queries
    */
   public void setCreateDomEntry(boolean createDomEntry) {
      this.createDomEntry.setValue(createDomEntry);
   }

   public boolean hasMsgUnitStoreProperty() {
      return this.msgUnitStoreProperty != null;
   }

   /**
    * @return the configuration of the message store, is never null
    */
   public MsgUnitStoreProperty getMsgUnitStoreProperty() {
      if (this.msgUnitStoreProperty == null) {
         this.msgUnitStoreProperty = new MsgUnitStoreProperty(glob, glob.getStrippedId());
      }
      return this.msgUnitStoreProperty;
   }

   public void setMsgUnitStoreProperty(MsgUnitStoreProperty msgUnitStoreProperty) {
      this.msgUnitStoreProperty = msgUnitStoreProperty;
   }

   public boolean hasHistoryQueueProperty() {
      return this.historyQueueProperty != null;
   }

   /**
    * @return the configuration of the history queue, is never null
    */
   public HistoryQueueProperty getHistoryQueueProperty() {
      if (this.historyQueueProperty == null) {
         this.historyQueueProperty = new HistoryQueueProperty(glob, glob.getStrippedId());
      }
      return this.historyQueueProperty;
   }

   public void setHistoryQueueProperty(HistoryQueueProperty historyQueueProperty) {
      this.historyQueueProperty = historyQueueProperty;
   }

   /**
    * Tells the server which plugin to use (if any) for the distribution of the
    * message entries for this topic 
    * @param type the type of the plugin to use (default "undef") 
    * @param version the version of the plugin to use (default "1.0")
    * @see org.xmlBlaster.engine.distributor.I_MsgDistributor
    */
   public void setMsgDistributor(String typeVersion) {
      if (typeVersion == null) typeVersion = "undef,1.0";
      this.msgDistributor.setValue(typeVersion);
   }

   /**
    * 
    * @return the comma separated type and version like "undef,1.0"
    */
   public String getMsgDistributor() {
      return this.msgDistributor.getValue();
   }

   public boolean isMsgDistributorModified() {
      return this.msgDistributor.isModified();
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
      if (this.readonly.isModified()) {
         sb.append(" readonly='").append(isReadonly()).append("'");
      }
      if (this.destroyDelay.isModified()) {
         sb.append(" destroyDelay='").append(getDestroyDelay()).append("'");
      }
      if (this.createDomEntry.isModified()) {
         sb.append(" createDomEntry='").append(createDomEntry()).append("'");
      }
      sb.append(">");
      //private String subscriptionId;

      if (this.msgDistributor.isModified()) {
         sb.append(offset + "  ").append("<msgDistributor typeVersion='").append(this.msgDistributor.getValue()).append("'/>");
      }

      if (hasMsgUnitStoreProperty()) {
         sb.append(getMsgUnitStoreProperty().toXml(extraOffset+Constants.INDENT));
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

   }
}
