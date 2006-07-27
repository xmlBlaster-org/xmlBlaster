/*------------------------------------------------------------------------------
Name:      MsgQosData.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.property.PropEntry;
import org.xmlBlaster.util.property.PropLong;
import org.xmlBlaster.util.property.PropBoolean;

import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.def.MethodName;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;



/**
 * Data container handling of publish() and update() quality of services. 
 * <p />
 * QoS Informations sent from the client to the server via the publish() method and back via the update() method<br />
 * They are needed to control xmlBlaster and inform the client.
 * <p />
 * <p>
 * This data holder is accessible through 4 decorators, each of them allowing a specialized view on the data:
 * </p>
 * <ul>
 * <li>PublishQosServer Server side access</i>
 * <li>PublishQos Client side access</i>
 * <li>UpdateQosServer Server side access facade</i>
 * <li>UpdateQos Client side access facade</i>
 * </ul>
 * <p>
 * For the xml representation see MsgQosSaxFactory.
 * </p>
 * @see org.xmlBlaster.util.qos.MsgQosSaxFactory
 * @see org.xmlBlaster.test.classtest.qos.MsgQosFactoryTest
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
 * @author xmlBlaster@marcelruff.info
 */
public final class MsgQosData extends QosData implements java.io.Serializable, Cloneable
{
   private static final long serialVersionUID = 1L;
   private transient I_MsgQosFactory factory;
   private transient boolean isExpired = false; // cache the expired state for performance reasons

   private TopicProperty topicProperty;

   /**
    * A PubSub message lease lasts forever if not otherwise specified. <p />
    * The default message life cycle can be modified in xmlBlaster.properties:<br />
    * <code>message.lease.maxLifeTime=3600000 # One hour lease</code><br />
    * Every message can set the lifeTime value between 1 and maxLifeTime, 
    * -1L sets the life cycle on forever.
    */ // TODO: Change to use glob instead of Global singleton! What about performance? Put variable into Global?
   private static final long maxLifeTime = Global.instance().getProperty().get("message.maxLifeTime", -1L);

   /** If Pub/Sub style update: contains the subscribe ID which caused this update */
   private String subscriptionId;

   public transient final static boolean DEFAULT_isSubscribable = true;
   /** As default you can subscribe even PtP messages, set it to false if you don't want any subscriber to see your PtP message */
   private PropBoolean subscribable = new PropBoolean(DEFAULT_isSubscribable);

   /** the number of resend tries on failure */
   private int redeliver;
   private long queueIndex = -1L;
   private long queueSize = -1L;

   /** Internal use only, is this message sent from the persistence layer? */
   private boolean fromPersistenceStore = false;

   //public transient final static boolean DEFAULT_isVolatile = false;
   //private boolean volatileFlag = DEFAULT_isVolatile;


   /**
    * Send message to subscriber even the content is the same as the previous?
    * <br />
    * Default is that xmlBlaster does send messages to subscribed clients, even the content didn't change.
    */
   public transient final static boolean DEFAULT_forceUpdate = true;
   private PropBoolean forceUpdate = new PropBoolean(DEFAULT_forceUpdate);

   public final static long DEFAULT_lifeTime = maxLifeTime;
   /** 
    * A message expires after some time and will be discarded.
    * Clients will get a notify about expiration.
    * This is the configured lifeTime in millis of the message.
    * It defaults to -1L (== forever).
    */
   private PropLong lifeTime = new PropLong(DEFAULT_lifeTime);

   private long remainingLifeStatic = -1L;

   public transient final static boolean DEFAULT_administrative = false;
   private PropBoolean administrative = new PropBoolean(DEFAULT_administrative);

   public transient final static boolean DEFAULT_forceDestroy = false;
   private PropBoolean forceDestroy = new PropBoolean(DEFAULT_forceDestroy);

   /** The priority of the message */
   private PriorityEnum priority = PriorityEnum.NORM_PRIORITY;
   private boolean priorityIsModified = false;

   /**
    * ArrayList for loginQoS, holding all destination addresses (Destination objects)
    */
   protected ArrayList destinationList;
   protected transient Destination[] destinationArrCache;
   public final static Destination[] EMPTY_DESTINATION_ARR = new Destination[0];

   // TODO: Pass with client QoS!!!
   private static final boolean receiveTimestampHumanReadable = Global.instance().getProperty().get("cb.receiveTimestampHumanReadable", false);

   /**
    * Constructs the specialized quality of service object for a publish() or update() call.
    */
   public MsgQosData(Global glob, MethodName methodName) {
      this(glob, null, null, methodName);
   }

   /**
    * Constructs the specialized quality of service object for a publish() or update() call.
    * @param the XML based ASCII string
   public MsgQosData(Global glob, String serialData) {
      this(glob, null, serialData);
   }
    */

   /**
    * Constructs the specialized quality of service object for a publish() or update() call.
    * @param factory The factory which knows how to serialize and parse me
    */
   public MsgQosData(Global glob, I_MsgQosFactory factory, MethodName methodName) {
      this(glob, factory, null, methodName);
   }

   /**
    * Constructs the specialized quality of service object for a publish() call.
    * For internal use only, this message is sent from the persistence layer
    * @param the XML based ASCII string
    */
   public MsgQosData(Global glob, I_MsgQosFactory factory, String serialData, MethodName methodName) {
      super(glob, serialData, methodName);
      this.factory = (factory == null) ? this.glob.getMsgQosFactory() : factory;
   }

   /**
    * @see #isSubscribable()
    */
   public void setSubscribable(boolean isSubscribable) {
      this.subscribable.setValue(isSubscribable);
   }

   /**
    * Test if Publish/Subscribe style is used.
    *
    * @return true if Publish/Subscribe style is used<br />
    *         false Only possible for PtP messages to keep PtP secret (you can't subscribe them)
    */
   public boolean isSubscribable() {
      return this.subscribable.getValue();
   }

   public PropBoolean getSubscribableProp() {
      return this.subscribable;
   }

   /**
    * Test if Point to Point addressing style is used.
    *
    * @return true if addressing of the destination is used
    *         false if Publish/Subscribe style is used
    */
   public boolean isPtp() {
      return this.destinationList != null;
   }

   /**
    * @param volatile true sets lifeTime=0 and forceDestroy=false<br />
    *        false: does nothing
    */
   public void setVolatile(boolean volatileFlag) {
      if (volatileFlag) {
         setLifeTime(0L);
         setForceDestroy(false);
         setRemainingLifeStatic(0L); // not needed as server does set it
      }
      else {
         //setLifeTime(maxLifeTime);
         //setForceDestroy(false);
      }
      //this.volatileFlag = volatileFlag;
   }

   /**
    * @return true/false
    */
   public boolean isVolatile() {
      return getLifeTime()==0L && isForceDestroy()==false;
      //return this.volatileFlag;
   }

   /*
    * @return true If the default is the current setting. 
   public boolean isVolatileDefault() {
      return this.DEFAULT_isVolatile == this.volatileFlag;
   }
    */

   /**
    * If Pub/Sub style update: contains the subscribe ID which caused this update
    * @param subscriptionId null if PtP message
    */
   public void setSubscriptionId(String subscriptionId) {
      this.subscriptionId = subscriptionId;
   }

   /**
    * If Pub/Sub style update: contains the subscribe ID which caused this update
    * @return subscribeId or null if PtP message
    */
   public String getSubscriptionId() {
      return subscriptionId;
   }

   /**
    * Send message to subscriber even if the content is the same as the previous. 
    * @param forceUpdate
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.publish.forceUpdate.html">The engine.qos.publish.forceUpdate requirement</a>
    */
   public void setForceUpdate(boolean forceUpdate) {
      this.forceUpdate.setValue(forceUpdate);
   }

   /**
    * @return true/false
    */
   public boolean isForceUpdate() {
      return this.forceUpdate.getValue();
   }

   public PropBoolean getForceUpdateProp() {
      return this.forceUpdate;
   }

   /**
    * @return readonly Once published the message can't be changed. 
    */
   public void setReadonly(boolean readonly) {
      TopicProperty prop = getTopicProperty();
      prop.setReadonly(true);
   }

   /**
    * @return true/false
    */
   public boolean isReadonly() {
      return getTopicProperty().isReadonly();
   }

   /**
    * Set > 0 if the message probably is redelivered (number of retries). 
    * @param redeliver if == 0 The message is guaranteed to be delivered only once.
    */
   public void setRedeliver(int redeliver) {
      this.redeliver = redeliver;
   }

   /**
    * Increment the redeliver counter
    */
   public void incrRedeliver() {
      this.redeliver++;
   }

   /**
    * Returns > 0 if the message probably is redelivered. 
    * @return == 0 The message is guaranteed to be delivered only once.
    */
   public int getRedeliver() {
      return redeliver;
   }

  /**
    * @param queueSize The number of queued messages
    */
   public void setQueueSize(long queueSize) {
      this.queueSize = queueSize;
   }

    /**
    * @return The number of queued messages
    */
   public long getQueueSize() {
      return queueSize;
   }

   /**
    * @param queueIndex The index of the message in the queue
    */
   public void setQueueIndex(long queueIndex) {
      this.queueIndex = queueIndex;
   }

   /**
    * @return The index of the message in the queue
    */
   public long getQueueIndex() {
      return queueIndex;
   }

   /**
    * Message priority.
    * @return priority 0-9
    * @see org.xmlBlaster.util.def.PriorityEnum
    */
   public PriorityEnum getPriority() {
      return priority;
   }

   /**
    * Set message priority value, PriorityEnum.NORM_PRIORITY (5) is default. 
    * PriorityEnum.MIN_PRIORITY (0) is slowest
    * whereas PriorityEnum.MAX_PRIORITY (9) is highest priority.
    * @see org.xmlBlaster.util.def.PriorityEnum
    */
   public void setPriority(PriorityEnum priority) {
      this.priority = priority;
      this.priorityIsModified = true;
   }

   /**
    * Internal use only, is this message sent from the persistence layer?
    * @return true/false
    */
   public boolean isFromPersistenceStore() {
      return fromPersistenceStore;
   }

   /**
    * Internal use only, set if this message sent from the persistence layer
    * @param true/false
    */
   public void setFromPersistenceStore(boolean fromPersistenceStore) {
      this.fromPersistenceStore = fromPersistenceStore;
   }

   /**
    * The life time of the message or -1L if forever
    */
   public long getLifeTime() {
      return this.lifeTime.getValue();
   }

   public PropLong getLifeTimeProp() {
      return this.lifeTime;
   }

   /**
    * Control the life time of a message. 
    * <p/>
    * This value is calculated relative to the rcvTimestamp in the xmlBlaster server.
    * <p/>
    * Passing -1 milliseconds asks the server for unlimited livespan, which
    * the server may or may not grant.

    * @param lifeTime The life time of the message or -1L if forever.
    * <p>  
    * Setting to 0 will behave as a volatile message (see setVolatile())
    * and the message will be invisible directly after being pushed into the subscribers
    * callback queues, in the callback queues it will stay until retrieved by the subscriber.
    * <p>
    * Setting it to a value > 0 will expire the message after the given milliseconds,
    * even if they remain in any callback queue.
    */
   public void setLifeTime(long lifeTime) {
      this.lifeTime.setValue(lifeTime);
   }

   /**
    * @return Milliseconds until message expiration (from now) or -1L if forever
    *         if 0L the message is expired
    */
   public long getRemainingLife() {
      if (getLifeTime() > 0L && getLifeTime() < Long.MAX_VALUE && getRcvTimestamp() != null) {
         long ttl = getRcvTimestamp().getMillis() + getLifeTime() - System.currentTimeMillis();
         return ( ttl < 0L ) ? 0L : ttl;
      }
      else
         return -1L;
   }

   /**
    * This is the value delivered in the QoS (as it was calculated by the server on sending)
    * and is NOT dynamically recalculated.
    * So trust this value only if your client clock is out of date (or not trusted) and
    * if you know the message sending latency is not too big.
    * @return Milliseconds until message expiration (from now) or -1L if forever
    *         if 0L the message is expired
    */
   public long getRemainingLifeStatic() {
      return this.remainingLifeStatic;
   }

   public void setRemainingLifeStatic(long remainingLifeStatic) {
      this.remainingLifeStatic = remainingLifeStatic;
   }

   /**
    * Calculates if we are expired
    */
   public boolean isExpired() {
      if (getLifeTime() == Long.MAX_VALUE || getLifeTime() <= 0L) {
         return false; // lifes forever
      }
      if (getRcvTimestamp() == null) {
         return false;
      }
      if (isExpired) { // cache
         return true;
      }
      isExpired = System.currentTimeMillis() > (getRcvTimestamp().getMillis() + getLifeTime());
      return isExpired;
   }

      /*
      if (lifeTime < 0L && getMaxLifeTime() < 0L)
         this.expirationTimestamp = Long.MAX_VALUE;
      else if (lifeTime >= 0L && getMaxLifeTime() < 0L)
         this.expirationTimestamp = getRcvTimestamp().getMillis() + lifeTime;
      else if (lifeTime < 0L && getMaxLifeTime() >= 0L)
         this.expirationTimestamp = getRcvTimestamp().getMillis() + getMaxLifeTime();
      else if (lifeTime >= 0L && getMaxLifeTime() >= 0L) {
         if (lifeTime <= getMaxLifeTime())
            this.expirationTimestamp = getRcvTimestamp().getMillis() + lifeTime;
         else
            this.expirationTimestamp = getRcvTimestamp().getMillis() + getMaxLifeTime();
      }
      */

   /**
    * The server default for max. span of life,
    * adjustable with property "message.maxLifeTime"
    * @return max span of life for a message
    */
   public static long getMaxLifeTime() {
      return maxLifeTime;
   }

   /**
    * Tagged form of message receive, e.g.:<br />
    * &lt;rcvTimestamp nanos='1007764305862000004'/>
    *
    * @see org.xmlBlaster.util.Timestamp
    */
   public String getXmlRcvTimestamp() {
      if (getRcvTimestamp() == null) return "";
      if (receiveTimestampHumanReadable)
         return getRcvTimestamp().toXml(null, true);
      else
         return getRcvTimestamp().toXml();
   }

   /**
    * Human readable form of message receive time in xmlBlaster server,
    * in SQL representation e.g.:<br />
    * 2001-12-07 23:31:45.862000004
    * @deprecated Use getXmlRcvTimestamp()
    */
   public String getRcvTime() {
      return (rcvTimestamp != null) ? rcvTimestamp.toString() : "";
   }

   /**
    * Control message life cycle on message expiry, defaults to false. 
    * @param forceDestroy true Force message destroy on message expire<br />
    *        false On message expiry messages which are already in callback queues are delivered.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.publish.isVolatile.html">The engine.qos.publish.isVolatile requirement</a>
    */
   public void setForceDestroy(boolean forceDestroy) {
      this.forceDestroy.setValue(forceDestroy);
   }

   /**
    * @return true/false, defaults to false
    */
   public boolean isForceDestroy() {
      return this.forceDestroy.getValue();
   }

   public PropBoolean getForceDestroyProp() {
      return this.forceDestroy;
   }

   /**
    * Marks a message to be administrative only, in this case the topic is configured only.
    * Note the administrative messages have a default priority of MAX_PRIORITY
    * @param administrative true The message is only used to configure the topic<br />
    *        false The message contains useful content (and may as initial publish configure the topic as well)
    * @see org.xmlBlaster.util.def.PriorityEnum
    */
   public void setAdministrative(boolean administrative) {
      this.administrative.setValue(administrative);
      if (!this.priorityIsModified) {
         this.priority = (administrative) ? PriorityEnum.MAX_PRIORITY : PriorityEnum.NORM_PRIORITY;
      }
   }

   /**
    * @return true/false, defaults to false
    */
   public boolean isAdministrative() {
      return this.administrative.getValue();
   }

   public PropBoolean getAdministrativeProp() {
      return this.administrative;
   }

   /**
    * Get all the destinations of this message.
    * This should only be used with PTP style messaging<br />
    * Check <code>if (isPtp()) ...</code> before calling this method
    *
    * @return a valid ArrayList containing 0 - n Strings with destination names (loginName of clients)<br />
    *         null if Publish/Subscribe style is used
    */
   public ArrayList getDestinations() {
      return this.destinationList;
   }

   public int getNumDestinations() {
      if (this.destinationList == null) {
         return 0;
      }
      return this.destinationList.size();
   }

   /**
    * @return The destinations in array form
    */
   public Destination[] getDestinationArr() {
      if (this.destinationArrCache == null) {
         ArrayList dd = this.destinationList;
         if (dd == null) {
            this.destinationArrCache = EMPTY_DESTINATION_ARR;
         }
         else {
            this.destinationArrCache = (Destination[])dd.toArray(new Destination[dd.size()]);
         }
      }
      return this.destinationArrCache;
   }

   /**
    * Add a destination. 
    * Note that the default lifeTime is set to 0 (PtP are volatile as default)
    */
   public void addDestination(Destination destination) {
      if (destination == null) return;
      if (this.destinationList == null) this.destinationList = new ArrayList();
      this.destinationArrCache = null;
      this.destinationList.add(destination);
      if (!this.lifeTime.isModified()) {
         // Change default setting for PtP to 'volatile'
         this.lifeTime.setValue(0L, PropEntry.CREATED_BY_DEFAULT);
      }
   }

   /**
    * Remove a destination. 
    */
   public void removeDestination(Destination destination) {
      if (destination == null || this.destinationList == null) return;
      this.destinationArrCache = null;
      this.destinationList.remove(destination);
      if (this.destinationList.size() < 1) {
         this.destinationList = null;
      }
   }

   /**
    * The getTopicProperty() creates an initial TopicHandler,
    * this method allows to check without creation
    */
   public boolean hasTopicProperty() {
      return this.topicProperty != null;
   }

   /**
    * The configuration for the TopicHandler (topic)
    * @return never null (a default is created if none is available)
    */
   public TopicProperty getTopicProperty() {
      if (this.topicProperty == null) {
         this.topicProperty = new TopicProperty(glob);
      }
      return this.topicProperty;
   }

   /**
    * @param The new topicProperty, usually you should create the instance with getTopicProperty()
    *        to not loose any readonly settings.<br />
    *        null resets the settings
    */
   public void setTopicProperty(TopicProperty topicProperty) {
      this.topicProperty = topicProperty;
   }

   /**
    * Dump the QoS to a flattened JXPath representation. 
    * <p>
    * This is experimental code for the simple Applet client
    * </p>
    * <pre>
    *   /qos/rcvTimestamp/@nanos                  -> 1042815836675000001
    *   /qos/methodName/text()                    -> update
    *   /qos/clientProperty[@name='myAge']/text() -> 12
    *   /qos/state/@id                            -> OK
    * </pre>
    * <p>
    * Currently only an UpdateQos dump is supported
    * @see <a href="http://jakarta.apache.org/commons/jxpath/">Apache JXPath</a>
    */
   public Hashtable toJXPath() {
      /* Problems with current java objects / JXPath mapping:
        1. <persistent />:  "/qos/persistent/text()"  -> returns nothing instead of true
        2.  getState() returns the <state id=''> instead of a state object with state.getId(), state.getInfo()
        3. "/qos/route/node/@id" returns three nodes -> we need something like nodeList
        4. Priority is returned as '4' or as 'LOW': With java this is handled by PriorityEnum.java
      */

      Hashtable map = new Hashtable();
      map.put("/qos/rcvTimestamp/@nanos", ""+getRcvTimestamp());
      map.put("/qos/rcvTimestamp/text()", ""+getRcvTime());
      MethodName methodName = getMethod();
      if (methodName != null) map.put("/qos/methodName/text()", methodName.toString());
      map.put("/qos/persistent/text()", ""+isPersistent());

      Map pMap = getClientProperties();
      Iterator it = pMap.keySet().iterator();
      while (it.hasNext()) {
         String key = (String)it.next();
         ClientProperty p = (ClientProperty)pMap.get(key);
         map.put("/qos/clientProperty[@name='"+key+"']/text()", p.getValueRaw()); 
         map.put("/qos/clientProperty[@name='"+key+"']/@type", p.getType());
         map.put("/qos/clientProperty[@name='"+key+"']/@encoding", p.getEncoding());
      }

      if (isUpdate() || isGet()) {
         org.xmlBlaster.util.cluster.RouteInfo[] routes = getRouteNodes();
         for (int i=0; i<routes.length; i++) {
            map.put("/qos/route/node[@id='"+routes[i].getId()+"']/@stratum", ""+routes[i].getStratum());
            map.put("/qos/route/node[@id='"+routes[i].getId()+"']/@timestamp", ""+routes[i].getTimestamp());
            map.put("/qos/route/node[@id='"+routes[i].getId()+"']/@dirtyRead", ""+routes[i].getDirtyRead());
         }
         if (getState() != null) map.put("/qos/state/@id", getState());
         if (getStateInfo() != null) map.put("/qos/state/@info", getStateInfo());
      }

      if (isUpdate()) {
         if (getSubscriptionId() != null) map.put("/qos/subscribe/@id", getSubscriptionId());
         map.put("/qos/queue/@index", ""+getQueueIndex());
         map.put("/qos/queue/@size", ""+getQueueSize());
         map.put("/qos/redeliver/text()", ""+getRedeliver());
      }

      SessionName sender = getSender();
      if (sender != null) {
         map.put("/qos/sender/text()", sender.toString());
      }
      map.put("/qos/expiration/@lifeTime", ""+getLifeTime());
      map.put("/qos/expiration/@remainingLife", ""+getRemainingLife());

      if (isPublish()) {
         // TopicProperty ?
      }
      return map;
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of the message QoS as a XML ASCII string
    */
   public String toXml() {
      return toXml((String)null, (Properties)null);
   }

   public String toXml(String extraOffset) {
      return toXml(extraOffset, (Properties)null);
   }
   
   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @param forceReadable If true, any base64 is decoded to be more human readable 
    * @return internal state of the message QoS as a XML ASCII string
    */
   public String toXml(String extraOffset, Properties props) {
      return this.factory.writeObject(this, extraOffset, props);
   }

   /**
    * Returns a partly deep clone, you can change safely all basic or immutable types
    * like boolean, String, int.
    * Currently TopicProperty is not cloned (so don't change it)
    */
   public Object clone() {
      MsgQosData newOne = null;
      //try {
         newOne = (MsgQosData)super.clone();
         synchronized(this) {
            newOne.subscribable = (PropBoolean)this.subscribable.clone();
            newOne.forceUpdate = (PropBoolean)this.forceUpdate.clone();
            newOne.lifeTime = (PropLong)this.lifeTime.clone();
            newOne.administrative = (PropBoolean)this.administrative.clone();
            newOne.forceDestroy = (PropBoolean)this.forceDestroy.clone();
         }
         return newOne;
      //}
      //catch (CloneNotSupportedException e) {
      //   return null;
      //}
   }

   /**
    * Sets the global object (used when deserializing the object)
    */
   public void setGlobal(Global glob) {
      super.setGlobal(glob);
      this.factory = glob.getMsgQosFactory();
   }

   public static void main(String[] args) {
      MsgQosData md = new MsgQosData(new Global(args), MethodName.UPDATE);
      Map map = md.toJXPath();
      Iterator it = map.keySet().iterator();
      while (it.hasNext()) {
         String key = (String)it.next();
         System.out.println(key + " -> '" + map.get(key) + "'");
      }
   }
}
