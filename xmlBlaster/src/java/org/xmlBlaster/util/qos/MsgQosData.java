/*------------------------------------------------------------------------------
Name:      MsgQosData.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.RcvTimestamp;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.SessionName;

import org.xmlBlaster.engine.helper.Destination;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.cluster.NodeId;
import org.xmlBlaster.engine.cluster.RouteInfo;

import java.util.ArrayList;


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
 * @author ruff@swand.lake.de
 */
public final class MsgQosData implements java.io.Serializable
{
   private String ME = "MsgQosData";
   private transient Global glob;
   private transient LogChannel log;
   private transient I_MsgQosFactory factory;
   private final String serialData;
   private transient boolean isExpired = false; // cache the expired state for performance reasons

   /**
    * A message lease lasts forever if not otherwise specified. <p />
    * The default message life cycle can be modified in xmlBlaster.properties:<br />
    * <code>message.lease.maxLifeTime=3600000 # One hour lease</code><br />
    * Every message can set the lifeTime value between 1 and maxLifeTime, 
    * -1L sets the life cycle on forever.
    */ // TODO: Change to use glob instead of Global singleton! What about performance? Put variable into Global?
   private static final long maxLifeTime = Global.instance().getProperty().get("message.maxLifeTime", -1L);

   /** the state of the message, defaults to "OK" if no state is returned */
   private String state = Constants.STATE_OK;
   /** Human readable information */
   private String stateInfo;

   /** If Pub/Sub style update: contains the subscribe ID which caused this update */
   private String subscriptionId;

   /** the number of resend tries on failure */
   private int redeliver;
   private long queueIndex = -1L;
   private long queueSize = -1L;

   /** Internal use only, is this message sent from the persistence layer? */
   private boolean fromPersistenceStore = false;

   public static boolean DEFAULT_isVolatile = false;
   private boolean volatileFlag = DEFAULT_isVolatile;

   public static boolean DEFAULT_isDurable = false;
   private boolean durable = DEFAULT_isDurable;

   /**
    * Send message to subscriber even the content is the same as the previous?
    * <br />
    * Default is that xmlBlaster does send messages to subscribed clients, even the content didn't change.
    */
   public static boolean DEFAULT_forceUpdate = true;
   private boolean forceUpdate = DEFAULT_forceUpdate;

   public static boolean DEFAULT_readonly = false;
   private boolean readonly = DEFAULT_readonly;

   /** 
    * The receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In nanoseconds elapsed since midnight, January 1, 1970 UTC
    */
   private Timestamp rcvTimestamp;
   private boolean rcvTimestampFound = false;

   /** 
    * A message expires after some time and will be discarded.
    * Clients will get a notify about expiration.
    * This is the configured lifeTime in millis of the message.
    */
   private long lifeTime = -1L;

   private long remainingLifeStatic = -1L;

   /** the sender (publisher) of this message (unique loginName) */
   private SessionName sender;

   /** The priority of the message */
   private PriorityEnum priority = PriorityEnum.NORM_PRIORITY;

   /**
    * ArrayList for loginQoS, holding all destination addresses (Destination objects)
    */
   protected ArrayList destinationList;
   protected Destination destination;

   /**
    * ArrayList containing RouteInfo objects
    */
   protected ArrayList routeNodeList;
   /** Cache for RouteInfo in an array */
   protected RouteInfo[] routeNodes;
   private static RouteInfo[] ROUTE_INFO_ARR_DUMMY = new RouteInfo[0];

   public long size;

   // TODO: Pass with client QoS!!!
   private static final boolean recieveTimestampHumanReadable = Global.instance().getProperty().get("cb.recieveTimestampHumanReadable", false);

   /**
    * Constructs the specialized quality of service object for a publish() or update() call.
    * @param The factory which knows how to serialize and parse me
    */
   public MsgQosData(Global glob) {
      this(glob, null, null);
   }

   /**
    * Constructs the specialized quality of service object for a publish() or update() call.
    * @param The factory which knows how to serialize and parse me
    */
   public MsgQosData(Global glob, String serialData) {
      this(glob, null, serialData);
   }

   /**
    * Constructs the specialized quality of service object for a publish() or update() call.
    * @param The factory which knows how to serialize and parse me
    */
   public MsgQosData(Global glob, I_MsgQosFactory factory) {
      this(glob, factory, null);
   }

   /**
    * Constructs the specialized quality of service object for a publish() call.
    * For internal use only, this message is sent from the persistence layer
    * @param the XML based ASCII string
    * @param true
    */
   public MsgQosData(Global glob, I_MsgQosFactory factory, String serialData) {
      this.glob = glob;
      this.log = glob.getLog("core");
      this.factory = (factory == null) ? glob.getMsgQosFactory() : factory;
      this.serialData = serialData;
      this.size = (serialData == null) ? 0 : serialData.length();
   }

   /**
    * Test if Publish/Subscribe style is used.
    *
    * @return true if Publish/Subscribe style is used
    *         false if addressing of the destination is used
    */
   public boolean isPubSubStyle() {
      return destinationList == null;
   }

   /**
    * Test if Point to Point addressing style is used.
    *
    * @return true if addressing of the destination is used
    *         false if Publish/Subscribe style is used
    */
   public boolean isPtp() {
      return !isPubSubStyle();
   }

   /**
    * @param state The state of an update message
    */
   public void setState(String state) {
      this.state = state;
   }

   /**
    * Access state of message on update().
    * @return OK (Other values are not yet supported)
    */
   public String getState() {
      return state;
   }

   /**
    * @param state The human readable state text of an update message
    */
   public void setStateInfo(String stateInfo) {
      this.stateInfo = stateInfo;
   }

   /**
    * Access state of message on update().
    * @return The human readable info text
    */
   public String getStateInfo() {
      return this.stateInfo;
   }

   /**
    * True if the message is OK on update(). 
    */
   public boolean isOk() {
      return Constants.STATE_OK.equals(state);
   }

   /**
    * True if the message was erased by timer or by a
    * client invoking erase(). 
    */
   public boolean isErased() {
      return Constants.STATE_ERASED.equals(state);
   }

   /**
    * True if a timeout on this message occurred. 
    * <p />
    * Timeouts are spanned by the publisher and thrown by xmlBlaster
    * on timeout to indicate for example
    * STALE messages or any other user problem domain specific event.
    */
   public final boolean isTimeout() {
      return Constants.STATE_TIMEOUT.equals(state);
   }

   /**
    * True on cluster forward problems
    */
   public final boolean isForwardError() {
      return Constants.STATE_FORWARD_ERROR.equals(state);
   }

   /**
    * @param volatile true/false
    */
   public void setVolatile(boolean volatileFlag) {
      this.volatileFlag = volatileFlag;
   }

   /**
    * @return true/false
    */
   public boolean isVolatile() {
      return this.volatileFlag;
   }

   /**
    * @return true If the default is the current setting. 
    */
   public boolean isVolatileDefault() {
      return this.DEFAULT_isVolatile == this.volatileFlag;
   }

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
    * @param isDurable mark a message as persistent
    */
   public void setDurable(boolean durable) {
      this.durable = durable;
   }

   /**
    * @return true/false
    */
   public boolean isDurable() {
      return this.durable;
   }

   /**
    * @param forceUpdate Mark a PtP message to be queued if receiver is not available. 
    */
   public void setForceUpdate(boolean forceUpdate) {
      this.forceUpdate = forceUpdate;
   }

   /**
    * @return true/false
    */
   public boolean isForceUpdate() {
      return this.forceUpdate;
   }

   /**
    * @return true if we have default setting
    */
   public boolean isForceUpdateDefault() {
      return DEFAULT_forceUpdate == forceUpdate;
   }

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
    * Access sender login name.
    * @return loginName of sender or null if not known
   public String getSender() {
      return (sender == null) ? (String)null : sender.getLoginName();
   }
    */

   /**
    * Access sender unified naming object.
    * @return sessionName of sender or null if not known
    */
   public SessionName getSender() {
      return sender;
   }

   /**
    * Set the sender name. 
    * <p>
    * Prefer setSender(SessionName)
    * </p>
    * @param loginName, relative name or absolute name of the sender
   public void setSender(String sender) {
      this.sender = new SessionName(glob, sender);
   }
    */

   /**
    * Access sender name.
    * @param loginName of sender
    */
   public void setSender(SessionName senderSessionName) {
      this.sender = senderSessionName;
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
    * Adds a new route hop to the QoS of this message. 
    * The added routeInfo is assumed to be one stratum closer to the master
    * So we will rearrange the stratum here. The given stratum in routeInfo
    * is used to recalculate the other nodes as well.
    */
   public void addRouteInfo(RouteInfo routeInfo) {
      if (routeInfo == null) {
         log.error(ME, "Adding null routeInfo");
         return;
      }

      this.routeNodes = null; // clear cache

      if (routeNodeList == null)
         routeNodeList = new ArrayList();
      routeNodeList.add(routeInfo);

      // Set stratum to new values
      int offset = routeInfo.getStratum();
      if (offset < 0) offset = 0;

      for (int ii=routeNodeList.size()-1; ii>=0; ii--) {
         RouteInfo ri = (RouteInfo)routeNodeList.get(ii);
         ri.setStratum(offset++);
      }
   }

   /**
    * @return never null, but may have length==0
    */
   public RouteInfo[] getRouteNodes() {
      if (routeNodeList == null)
         this.routeNodes = ROUTE_INFO_ARR_DUMMY;
      if (this.routeNodes == null)
         this.routeNodes = (RouteInfo[]) routeNodeList.toArray(new RouteInfo[routeNodeList.size()]);
      return this.routeNodes;
   }

   /**
    * Check if the message has already been at the given node (circulating message). 
    * @return How often the message has travelled the node already
    */
   public int count(NodeId nodeId) {
      int count = 0;
      if (routeNodeList == null)
         return count;
      for (int ii=0; ii<routeNodeList.size(); ii++) {
         RouteInfo ri = (RouteInfo)routeNodeList.get(ii);
         if (ri.getNodeId().equals(nodeId))
            count++;
      }
      return count;
   }

   /**
    * Check if the message has already been at the given node (circulating message). 
    * @return How often the message has travelled the node already
    */
   public boolean dirtyRead(NodeId nodeId) {
      int count = 0;
      if (routeNodeList == null || nodeId == null)
         return false;
      for (int ii=0; ii<routeNodeList.size(); ii++) {
         RouteInfo ri = (RouteInfo)routeNodeList.get(ii);
         if (ri.getNodeId().equals(nodeId)) {
            return ri.getDirtyRead();
         }
      }
      return false;
   }

   /**
    * Message priority.
    * @return priority 0-9
    * @see org.xmlBlaster.engine.helper.Constants
    */
   public PriorityEnum getPriority() {
      return priority;
   }

   /**
    * Set message priority value, PriorityEnum.NORM_PRIORITY (5) is default. 
    * PriorityEnum.MIN_PRIORITY (0) is slowest
    * whereas PriorityEnum.MAX_PRIORITY (9) is highest priority.
    * @see org.xmlBlaster.engine.helper.Constants
    */
   public void setPriority(PriorityEnum priority) {
      this.priority = priority;
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
      return this.lifeTime;
   }

   /**
    * The life time of the message or -1L if forever
    */
   public void setLifeTime(long lifeTime) {
      this.lifeTime = lifeTime;
   }

   /**
    * @return Milliseconds until message expiration (from now) or -1L if forever
    *         if 0L the message is expired
    */
   public long getRemainingLife() {
      if (this.lifeTime > 0L && this.lifeTime < Long.MAX_VALUE && getRcvTimestamp() != null) {
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
      if (lifeTime == Long.MAX_VALUE || lifeTime <= 0L) {
         return false; // lifes forever
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
    * The approximate receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In milliseconds elapsed since midnight, January 1, 1970 UTC
    */
   public void setRcvTimestamp(Timestamp rcvTimestamp) {
      this.rcvTimestamp = rcvTimestamp;
   }

   /** 
    * The approximate receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In milliseconds elapsed since midnight, January 1, 1970 UTC
    */
   public Timestamp getRcvTimestamp() {
      return rcvTimestamp;
   }

   /**
    * Tagged form of message receive, e.g.:<br />
    * &lt;rcvTimestamp nanos='1007764305862000004'/>
    *
    * @see org.xmlBlaster.util.Timestamp
    */
   public String getXmlRcvTimestamp() {
      if (recieveTimestampHumanReadable)
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
      return rcvTimestamp.toString();
   }

   /**
    * Set timestamp to current time.
    */
   public void touchRcvTimestamp() {
      rcvTimestamp = new RcvTimestamp();
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
      return destinationList;
   }

   /**
    * Add a destination. 
    */
   public void addDestination(Destination destination) {
      if (destination == null) return;
      if (destinationList == null) destinationList = new ArrayList();
      destinationList.add(destination);
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
    * @return internal state of the message QoS as a XML ASCII string
    */
   public String toXml(String extraOffset) {
      return factory.writeObject(this, extraOffset);
   }


   /**
    * Sets the global object (used whendeserializing the object)
    */
   public void setGlobal(Global glob) {
      this.glob = glob;
      this.log = glob.getLog("core");
      this.factory = glob.getMsgQosFactory();
   }
}
