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

using namespace std;
using namespace boost;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::cluster;

namespace org { namespace xmlBlaster { namespace util { namespace qos {

void MsgQosData::init()
{
   ME = "MsgQosData";
   subscriptionId_ = "";
   isSubscribeable_.setValue(global_.getProperty(), "isSubscribeable"); // true;
   redeliver_ = 0;
   queueIndex_ = -1;
   queueSize_ = -1;
   fromPersistenceStore_ = false;
   persistent_ = DEFAULT_persistent;
   forceUpdate_= DEFAULT_forceUpdate;
   lifeTime_ = -1;
   remainingLifeStatic_ = -1;
   priority_ = NORM_PRIORITY;
   isExpired_ = false; // cache the expired state for performance reasons
   maxLifeTime_ = global_.getProperty().getLongProperty("message.maxLifeTime", -1);
   receiveTimestampHumanReadable_ = global_.getProperty().getBoolProperty("cb.receiveTimestampHumanReadable", false);
   topicProperty_ = NULL; 
   forceDestroy_.setValue(global_.getProperty(), "forceDestroy");
}

void MsgQosData::copy(const MsgQosData& data)
{
   subscriptionId_ = data.subscriptionId_;
   isSubscribeable_ = data.isSubscribeable_;
   redeliver_ = data.redeliver_;
   queueIndex_ = data.queueIndex_;
   queueSize_ = data.queueSize_;
   fromPersistenceStore_ = data.fromPersistenceStore_;
   persistent_ = data.persistent_;
   forceUpdate_= data.forceUpdate_;
   lifeTime_ = data.lifeTime_;
   remainingLifeStatic_ = data.remainingLifeStatic_;
   priority_ = data.priority_;
   isExpired_ = data.isExpired_;
   maxLifeTime_ = data.maxLifeTime_;
   receiveTimestampHumanReadable_ = data.receiveTimestampHumanReadable_;
   topicProperty_ = NULL;
   if (data.topicProperty_)
      topicProperty_ = new TopicProperty(*data.topicProperty_);
   forceDestroy_ = data.forceDestroy_;
}


MsgQosData::MsgQosData(Global& global, const string& serialData)
   : QosData(global, serialData),
     isSubscribeable_(Prop<bool>(DEFAULT_isSubscribeable)),
     sender_(SessionQos(global)),
     destinationList_(),
     forceDestroy_(Prop<bool>(DEFAULT_forceDestroy))
{
   init();
}


MsgQosData::MsgQosData(const MsgQosData& data)
   : QosData(data),
     isSubscribeable_(Prop<bool>(DEFAULT_isSubscribeable)),
     sender_(data.sender_),
     destinationList_(data.destinationList_),
     forceDestroy_(Prop<bool>(DEFAULT_forceDestroy))
{
   copy(data);
}

MsgQosData& MsgQosData::operator=(const MsgQosData& data)
{
   QosData::copy(data);
   copy(data);
   return *this;
}


MsgQosData::~MsgQosData()
{
   delete topicProperty_;
}

/**
 * @param isSubscribeable if false PtP messages are invisible for subscriptions
 */
void MsgQosData::setIsSubscribeable(const bool isSubscribeable)
{
   isSubscribeable_ = isSubscribeable;
}

bool MsgQosData::isSubscribeable() const
{
   return isSubscribeable_.getValue();
}

bool MsgQosData::isPtp() const
{
   return !destinationList_.empty();
}


void MsgQosData::setReadonly(bool readonly)
{
   if (topicProperty_ == NULL)
     topicProperty_ = new TopicProperty(global_);
   topicProperty_->setReadonly(readonly);
}

bool MsgQosData::isReadonly() const
{
   if (topicProperty_ == NULL) return false;
   return topicProperty_->isReadonly();
}

/**
 * @param volatile true/false
 */
void MsgQosData::setVolatile(bool volatileFlag)
{
   if (volatileFlag) {
      setLifeTime(0L);
      setForceDestroy(false);
      setRemainingLifeStatic(0L); // not needed as server does set it
   }
}

/**
 * @return true/false
 */
bool MsgQosData::isVolatile() const
{
   return getLifeTime()==0L && getForceDestroy()==false;
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
 * @param persistent mark a message as persistent
 */
void MsgQosData::setPersistent(bool persistent)
{
   persistent_ = persistent;
}

/**
 * @return true/false
 */
bool MsgQosData::isPersistent() const
{
   return persistent_;
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
 * Access sender unified naming object.
 * @return sessionName of sender or null if not known
 */
SessionQos MsgQosData::getSender() const
{
   return sender_;
}

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
 * Message priority.
 * @return priority 0-9
 * @see org.xmlBlaster.util.enum.Constants
 */
PriorityEnum MsgQosData::getPriority() const
{
   return priority_;
}

/**
 * Set message priority value, PriorityEnum.NORM_PRIORITY (5) is default. 
 * PriorityEnum.MIN_PRIORITY (0) is slowest
 * whereas PriorityEnum.MAX_PRIORITY (9) is highest priority.
 * @see org.xmlBlaster.util.enum.Constants
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
long MsgQosData::getLifeTime() const
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
long MsgQosData::getRemainingLife() const
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
bool MsgQosData::isExpired() const
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
string MsgQosData::toXml(const string& extraOffset) const
{
   string ret;

   string offset = "\n " + extraOffset;
   string extraOffset1 = extraOffset + " ";

   // WARNING: This dump must be valid, as it could be used by the
   //          persistent store
   ret += offset + "<qos>";

   if (!getState().empty() || !getStateInfo().empty()) {
      ret += offset + " <state id='" + getState();
      if (!getStateInfo().empty())
         ret += "' info='" + getStateInfo();
      ret += "'/>";
   }

   if (isSubscribeable_.isModified())
      ret += offset + " <subscribeable>" + Global::getBoolAsString(isSubscribeable_.getValue()) + "</subscribeable>";

   vector<Destination>::const_iterator iter = destinationList_.begin();
   while (iter != destinationList_.end()) {
      ret += (*iter).toXml(extraOffset1);
      iter++;
   }

   ret += offset + " <sender>" + sender_.getAbsoluteName() + "</sender>";

   if (NORM_PRIORITY != priority_)
      ret += offset + " <priority>" + lexical_cast<string>(priority_) + "</priority>";

   if (!subscriptionId_.empty())
      ret += offset + " <subscribe id='" + subscriptionId_ + "'/>";

   if (getLifeTime() > 0) {
      ret += offset + " <expiration lifeTime='" + lexical_cast<string>(getLifeTime());
      bool sendRemainingLife = true; // make it configurable !!!
      if (sendRemainingLife) {
         if (getRemainingLife() > 0)
            ret += "' remainingLife='" + lexical_cast<string>(getRemainingLife());
         else if (getRemainingLifeStatic() > 0)
            ret += "' remainingLife='" + lexical_cast<string>(getRemainingLifeStatic());
      }
      ret +=  "'/>";
   }

   if (getRcvTimestamp() != 0)
      ret += TimestampFactory::toXml(getRcvTimestamp(), extraOffset1, false);
   if(getQueueSize() > 0)
      ret += offset + " <queue index='" + lexical_cast<string>(getQueueIndex()) + "' size='" + lexical_cast<string>(getQueueSize()) + "'/>";
   if (getRedeliver() > 0)
      ret += offset + " <redeliver>" + lexical_cast<string>(getRedeliver()) + "</redeliver>";
   if (isPersistent())
      ret += offset + " <persistent/>";
   if (!isForceUpdateDefault())
      ret += offset + " <forceUpdate>" + Global::getBoolAsString(isForceUpdate()) + "</forceUpdate>";
   if (forceDestroy_.isModified())
      ret += offset + " <forceDestroy>" + Global::getBoolAsString(forceDestroy_.getValue()) + "</forceDestroy>";

   RouteVector::const_iterator routeIter = routeNodeList_.begin();
   ret += offset + " <route>";
   while (routeIter != routeNodeList_.end()) {
      ret += (*routeIter).toXml(extraOffset1);
      routeIter++;
   }
   ret += offset + " </route>";
   ret += offset + "</qos>";

   if (ret.length() < 16) return "";

   return ret;
}


void MsgQosData::setTopicProperty(const TopicProperty& prop)
{
   if (topicProperty_ != NULL) {
     delete topicProperty_;
     topicProperty_ = NULL;
   }
   topicProperty_ = new TopicProperty(prop);
}

TopicProperty MsgQosData::getTopicProperty()
{
   if (topicProperty_ != NULL) return *topicProperty_;
   topicProperty_ = new TopicProperty(global_);
   return *topicProperty_;
}

bool MsgQosData::hasTopicProperty() const
{
   return (topicProperty_ != NULL);
}


bool MsgQosData::getForceDestroy() const
{
   return forceDestroy_.getValue();
}

void MsgQosData::setForceDestroy(bool forceDestroy)
{
   forceDestroy_.setValue(forceDestroy, CREATED_BY_SETTER);
}

}}}}

