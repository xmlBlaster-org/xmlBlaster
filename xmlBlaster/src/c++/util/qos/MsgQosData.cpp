/*------------------------------------------------------------------------------
Name:      MsgQosData.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

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
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */

#include <util/qos/MsgQosData.h>
#include <limits.h>
#include <boost/lexical_cast.hpp>

using boost::lexical_cast;

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::cluster;
using org::xmlBlaster::util::qos::SessionQos;

using namespace std;

namespace org { namespace xmlBlaster { namespace util { namespace qos {

Dll_Export const bool DEFAULT_isVolatile  = false;
Dll_Export const bool DEFAULT_isDurable   = false;
Dll_Export const bool DEFAULT_forceUpdate = true;
Dll_Export const bool DEFAULT_readonly    = false;

   void MsgQosData::init()
   {
      state_ = Constants::STATE_OK;
      stateInfo_ = "";
      subscriptionId_ = "";
      redeliver_ = 0;
      queueIndex_ = -1;
      queueSize_ = -1;
      fromPersistenceStore_ = false;
      volatileFlag_ = DEFAULT_isVolatile;
      durable_ = DEFAULT_isDurable;
      forceUpdate_= DEFAULT_forceUpdate;
      readonly_ = DEFAULT_readonly;
      rcvTimestamp_ = 0;
      rcvTimestampFound_ = false;
      lifeTime_ = -1;
      remainingLifeStatic_ = -1;
      priority_ = NORM_PRIORITY;
      size_ = 0;
      serialData_ = "";
      isExpired_ = false; // cache the expired state for performance reasons
      maxLifeTime_ = global_.getProperty().getLongProperty("message.maxLifeTime", -1);
      receiveTimestampHumanReadable_ = global_.getProperty().getBoolProperty("cb.receiveTimestampHumanReadable", false);
   }

   void MsgQosData::copy(const MsgQosData& data)
   {
      state_ = data.state_;
      stateInfo_ = data.stateInfo_;
      subscriptionId_ = data.subscriptionId_;
      redeliver_ = data.redeliver_;
      queueIndex_ = data.queueIndex_;
      queueSize_ = data.queueSize_;
      fromPersistenceStore_ = data.fromPersistenceStore_;
      volatileFlag_ = data.volatileFlag_;
      durable_ = data.durable_;
      forceUpdate_= data.forceUpdate_;
      readonly_ = data.readonly_;
      rcvTimestamp_ = data.rcvTimestamp_;
      rcvTimestampFound_ = data.rcvTimestampFound_;
      lifeTime_ = data.lifeTime_;
      remainingLifeStatic_ = data.remainingLifeStatic_;
      priority_ = data.priority_;
      size_ = data.size_;
      serialData_ = data.serialData_;
      isExpired_ = data.isExpired_;
      maxLifeTime_ = data.maxLifeTime_;
      receiveTimestampHumanReadable_ = data.receiveTimestampHumanReadable_;
   }


   MsgQosData::MsgQosData(Global& global, const string& serialData)
      : ME("MsgQosData"),
        global_(global),
        log_(global.getLog("core")),
        sender_(SessionQos(global)),
        destinationList_(),
        destination_(),
        routeNodeList_()
   {
      init();
      serialData_ = serialData;

   }


   MsgQosData::MsgQosData(const MsgQosData& data)
      : ME(data.ME),
        global_(data.global_),
        log_(data.log_),
        sender_(data.sender_),
        destinationList_(data.destinationList_),
        destination_(data.destination_),
        routeNodeList_(data.routeNodeList_)
   {
      copy(data);
   }

   MsgQosData& MsgQosData::operator=(const MsgQosData& data)
   {
      copy(data);
      return *this;
   }


   MsgQosData::~MsgQosData()
   {
   }

   bool MsgQosData::isPubSubStyle() const
   {
      return (destinationList_.empty());
   }

   bool MsgQosData::isPtp()
   {
      return !isPubSubStyle();
   }

   void MsgQosData::setState(const string& state)
   {
      state_ = state;
   }

   string MsgQosData::getState() const
   {
      return state_;
   }

   /**
    * @param state The human readable state text of an update message
    */
   void MsgQosData::setStateInfo(const string& stateInfo)
   {
      stateInfo_ = stateInfo;
   }

   /**
    * Access state of message on update().
    * @return The human readable info text
    */
   string MsgQosData::getStateInfo() const
   {
      return stateInfo_;
   }

   /**
    * True if the message is OK on update(). 
    */
   bool MsgQosData::isOk() const
   {
      return Constants::STATE_OK == state_;
   }

   /**
    * True if the message was erased by timer or by a
    * client invoking erase(). 
    */
   bool MsgQosData::isErased() const
   {
      return Constants::STATE_ERASED == state_;
   }

   /**
    * True if a timeout on this message occurred. 
    * <p />
    * Timeouts are spanned by the publisher and thrown by xmlBlaster
    * on timeout to indicate for example
    * STALE messages or any other user problem domain specific event.
    */
   bool MsgQosData::isTimeout() const
   {
      return Constants::STATE_TIMEOUT == state_;
   }

   /**
    * True on cluster forward problems
    */
   bool MsgQosData::isForwardError() const
   {
      return Constants::STATE_FORWARD_ERROR == state_;
   }

   /**
    * @param volatile true/false
    */
   void MsgQosData::setVolatile(bool volatileFlag)
   {
      volatileFlag_ = volatileFlag;
   }

   /**
    * @return true/false
    */
   bool MsgQosData::isVolatile() const
   {
      return volatileFlag_;
   }

   /**
    * @return true If the default is the current setting. 
    */
   bool MsgQosData::isVolatileDefault() const
   {
      return DEFAULT_isVolatile == volatileFlag_;
   }

   /**
    * If Pub/Sub style update: contains the subscribe ID which caused this update
    * @param subscriptionId null if PtP message
    */
   void MsgQosData::setSubscriptionId(const string& subscriptionId)
   {
      subscriptionId_ = subscriptionId;
   }

   /**
    * If Pub/Sub style update: contains the subscribe ID which caused this update
    * @return subscribeId or null if PtP message
    */
   string MsgQosData::getSubscriptionId() const
   {
      return subscriptionId_;
   }

   /**
    * @param isDurable mark a message as persistent
    */
   void MsgQosData::setDurable(bool durable)
   {
      durable_ = durable;
   }

   /**
    * @return true/false
    */
   bool MsgQosData::isDurable() const
   {
      return durable_;
   }

   /**
    * @param forceUpdate Mark a PtP message to be queued if receiver is not available. 
    */
   void MsgQosData::setForceUpdate(bool forceUpdate)
   {
      forceUpdate_ = forceUpdate;
   }

   /**
    * @return true/false
    */
   bool MsgQosData::isForceUpdate() const
   {
      return forceUpdate_;
   }

   /**
    * @return true if we have default setting
    */
   bool MsgQosData::isForceUpdateDefault() const
   {
      return DEFAULT_forceUpdate == forceUpdate_;
   }

   /**
    * @return readonly Once published the message can't be changed. 
    */
   void MsgQosData::setReadonly(bool readonly)
   {
      readonly_ = readonly;
   }

   /**
    * @return true/false
    */
   bool MsgQosData::isReadonly()
   {
      return readonly_;
   }

   /**
    * Access sender login name.
    * @return loginName of sender or null if not known
   string getSender()
   {
      return (sender == null) ? (const string&)null : sender.getLoginName();
   }
    */

   /**
    * Access sender unified naming object.
    * @return sessionName of sender or null if not known
    */
   SessionQos MsgQosData::getSender()
   {
      return sender_;
   }

   /**
    * Set the sender name. 
    * <p>
    * Prefer setSender(SessionName)
    * </p>
    * @param loginName, relative name or absolute name of the sender
   void setSender(const string& sender)
   {
      sender = new SessionName(glob, sender);
   }
    */

   /**
    * Access sender name.
    * @param loginName of sender
    */
   void MsgQosData::setSender(const SessionQos& senderSessionQos)
   {
      sender_ = senderSessionQos;
   }

   /**
    * Set > 0 if the message probably is redelivered (number of retries). 
    * @param redeliver if == 0 The message is guaranteed to be delivered only once.
    */
   void MsgQosData::setRedeliver(int redeliver)
   {
      redeliver_ = redeliver;
   }

   /**
    * Increment the redeliver counter
    */
   void MsgQosData::incrRedeliver()
   {
      redeliver_++;
   }

   /**
    * Returns > 0 if the message probably is redelivered. 
    * @return == 0 The message is guaranteed to be delivered only once.
    */
   int MsgQosData::getRedeliver() const
   {
      return redeliver_;
   }

  /**
    * @param queueSize The number of queued messages
    */
   void MsgQosData::setQueueSize(long queueSize)
   {
      queueSize_ = queueSize;
   }

    /**
    * @return The number of queued messages
    */
   long MsgQosData::getQueueSize() const
   {
      return queueSize_;
   }

   /**
    * @param queueIndex The index of the message in the queue
    */
   void MsgQosData::setQueueIndex(long queueIndex)
   {
      queueIndex_ = queueIndex;
   }

   /**
    * @return The index of the message in the queue
    */
   long MsgQosData::getQueueIndex() const
   {
      return queueIndex_;
   }

   /**
    * Adds a new route hop to the QoS of this message. 
    * The added routeInfo is assumed to be one stratum closer to the master
    * So we will rearrange the stratum here. The given stratum in routeInfo
    * is used to recalculate the other nodes as well.
    */
   void MsgQosData::addRouteInfo(const RouteInfo& routeInfo)
   {
      routeNodeList_.insert(routeNodeList_.end(), routeInfo);

      // Set stratum to new values
      int offset = routeInfo.getStratum();
      if (offset < 0) offset = 0;

      vector<RouteInfo>::reverse_iterator iter = routeNodeList_.rbegin();
      while (iter != routeNodeList_.rend()) {
         (*iter).setStratum(offset++);
         iter++;
      }
   }

   /**
    * Check if the message has already been at the given node (circulating message). 
    * @return How often the message has travelled the node already
    */
   int MsgQosData::count(const NodeId& nodeId) const
   {
      int cnt = 0;
      if (routeNodeList_.empty()) return cnt;
      vector<RouteInfo>::const_iterator iter = routeNodeList_.begin();
      while (iter != routeNodeList_.end()) {
         if ((*iter).getNodeId() == nodeId) cnt++;
         iter++;
      }
      return cnt;
   }

   /**
    * Check if the message has already been at the given node (circulating message). 
    * @return How often the message has travelled the node already
    */
   bool MsgQosData::dirtyRead(NodeId nodeId)
   {
      if (routeNodeList_.empty()) return false;
      vector<RouteInfo>::iterator iter = routeNodeList_.begin();
      while (iter != routeNodeList_.end()) {
         if ((*iter).getNodeId() == nodeId) return (*iter).getDirtyRead();
      }
      return false;
   }

   /**
    * Message priority.
    * @return priority 0-9
    * @see org.xmlBlaster.engine.helper.Constants
    */
   PriorityEnum MsgQosData::getPriority() const
   {
      return priority_;
   }

   /**
    * Set message priority value, PriorityEnum.NORM_PRIORITY (5) is default. 
    * PriorityEnum.MIN_PRIORITY (0) is slowest
    * whereas PriorityEnum.MAX_PRIORITY (9) is highest priority.
    * @see org.xmlBlaster.engine.helper.Constants
    */
   void MsgQosData::setPriority(PriorityEnum priority)
   {
      priority_ = priority;
   }

   /**
    * Internal use only, is this message sent from the persistence layer?
    * @return true/false
    */
   bool MsgQosData::isFromPersistenceStore() const
   {
      return fromPersistenceStore_;
   }

   /**
    * Internal use only, set if this message sent from the persistence layer
    * @param true/false
    */
   void MsgQosData::setFromPersistenceStore(bool fromPersistenceStore)
   {
      fromPersistenceStore_ = fromPersistenceStore;
   }

   /**
    * The life time of the message or -1L if forever
    */
   long MsgQosData::getLifeTime()const
   {
      return lifeTime_;
   }

   /**
    * The life time of the message or -1L if forever
    */
   void MsgQosData::setLifeTime(long lifeTime)
   {
      lifeTime_ = lifeTime;
   }

   /**
    * @return Milliseconds until message expiration (from now) or -1L if forever
    *         if 0L the message is expired
    */
   long MsgQosData::getRemainingLife()
   {
      if (lifeTime_ > 0 && lifeTime_ < LONG_MAX && getRcvTimestamp() != 0) {
         Timestamp now = TimestampFactory::getInstance().getTimestamp();
         long ttl = (long)((getRcvTimestamp()-now)/1000000l) + getLifeTime();
         if (ttl < 0) return 0;
         return ttl;
      }
      return -1;
   }

   /**
    * This is the value delivered in the QoS (as it was calculated by the server on sending)
    * and is NOT dynamically recalculated.
    * So trust this value only if your client clock is out of date (or not trusted) and
    * if you know the message sending latency is not too big.
    * @return Milliseconds until message expiration (from now) or -1L if forever
    *         if 0L the message is expired
    */
   long MsgQosData::getRemainingLifeStatic() const
   {
      return remainingLifeStatic_;
   }

   void MsgQosData::setRemainingLifeStatic(long remainingLifeStatic)
   {
      remainingLifeStatic_ = remainingLifeStatic;
   }

   /**
    * Calculates if we are expired
    */
   bool MsgQosData::isExpired()
   {
      if (lifeTime_ == LONG_MAX || lifeTime_ <= 0) {
         return false; // lifes forever
      }
      if (isExpired_) { // cache
         return true;
      }
      isExpired_ = (getRemainingLife() <= 0);
      return isExpired_;
   }

   /**
    * The server default for max. span of life,
    * adjustable with property "message.maxLifeTime"
    * @return max span of life for a message
    */
   long MsgQosData::getMaxLifeTime() const
   {
      return maxLifeTime_;
   }

   /** 
    * The approximate receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In milliseconds elapsed since midnight, January 1, 1970 UTC
    */
   void MsgQosData::setRcvTimestamp(Timestamp rcvTimestamp)
   {
      rcvTimestamp_ = rcvTimestamp;
   }

   /** 
    * The approximate receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In milliseconds elapsed since midnight, January 1, 1970 UTC
    */
   Timestamp MsgQosData::getRcvTimestamp() const
   {
      return rcvTimestamp_;
   }

   /**
    * Tagged form of message receive, e.g.:<br />
    * &lt;rcvTimestamp nanos='1007764305862000004'/>
    *
    * @see org.xmlBlaster.util.Timestamp
    */
   string MsgQosData::getXmlRcvTimestamp()
   {
      // if human readable is not considered here. It could be implemented in
      // the TimestampFactory as a static method.
      return string("<timestamp>") + lexical_cast<string>(getRcvTimestamp()) + "</timestamp>";
   }

   /**
    * Set timestamp to current time.
    */
   void MsgQosData::touchRcvTimestamp()
   {
      rcvTimestamp_ = TimestampFactory::getInstance().getTimestamp();
   }

   /**
    * Get all the destinations of this message.
    * This should only be used with PTP style messaging<br />
    * Check <code>if (isPtp()) ...</code> before calling this method
    *
    * @return a valid ArrayList containing 0 - n strings with destination names (loginName of clients)<br />
    *         null if Publish/Subscribe style is used
    */
   vector<Destination> MsgQosData::getDestinations() const
   {
      return destinationList_;
   }

   /**
    * Add a destination. 
    */
   void MsgQosData::addDestination(const Destination& destination)
   {
      destinationList_.insert(destinationList_.end(), destination);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the message QoS as a XML ASCII string
    */
   string MsgQosData::toXml(const string& extraOffset="")
   {
      log_.warn(ME, "toXml not implemented yet");
//      return factory.writeObject(this, extraOffset);
      return "";
   }

}}}}

