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
 * @author michele@laghi.eu
 */

#include <util/qos/MsgQosData.h>
#include <limits.h>
#include <util/lexical_cast.h>
#include <util/Global.h>

using namespace std;

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::cluster;

namespace org { namespace xmlBlaster { namespace util { namespace qos {

void MsgQosData::init()
{
   ME = "MsgQosData";
   subscriptionId_ = "";
   subscribable_.setValue(global_.getProperty(), "isSubscribable"); // true;
   redeliver_ = 0;
   queueIndex_ = -1;
   queueSize_ = -1;
   forceUpdate_.setValue(global_.getProperty(), "forceUpdate");
   forceDestroy_.setValue(global_.getProperty(), "forceDestroy");
   lifeTime_ = -1;
   remainingLifeStatic_ = -1;
   isExpired_ = false; // cache the expired state for performance reasons
   administrative_ = false;
   maxLifeTime_ = global_.getProperty().getLongProperty("message.maxLifeTime", -1);
   receiveTimestampHumanReadable_ = global_.getProperty().getBoolProperty("cb.receiveTimestampHumanReadable", false);
   topicProperty_ = NULL; 
}

void MsgQosData::copy(const MsgQosData& data)
{
   QosData::copy(data);

   subscriptionId_ = data.subscriptionId_;
   subscribable_ = data.subscribable_;
   redeliver_ = data.redeliver_;
   queueIndex_ = data.queueIndex_;
   queueSize_ = data.queueSize_;
   forceUpdate_= data.forceUpdate_;
   forceDestroy_ = data.forceDestroy_;
   lifeTime_ = data.lifeTime_;
   remainingLifeStatic_ = data.remainingLifeStatic_;
   isExpired_ = data.isExpired_;
   administrative_ = data.administrative_;
   maxLifeTime_ = data.maxLifeTime_;
   receiveTimestampHumanReadable_ = data.receiveTimestampHumanReadable_;
   topicProperty_ = NULL;
   if (data.topicProperty_)
      topicProperty_ = new TopicProperty(*data.topicProperty_);
}


MsgQosData::MsgQosData(Global& global, const string& serialData)
   : QosData(global, serialData),
     subscribable_(Prop<bool>(DEFAULT_isSubscribable)),
     forceUpdate_(Prop<bool>(DEFAULT_forceUpdate)),
     forceDestroy_(Prop<bool>(DEFAULT_forceDestroy)),
     destinationList_()
{
   init();
}


MsgQosData::MsgQosData(const MsgQosData& data)
   : QosData(data),
     destinationList_(data.destinationList_)
{
   copy(data);
}

MsgQosData& MsgQosData::operator=(const MsgQosData& data)
{
   QosData::copy(data);
   destinationList_ = data.destinationList_;
   copy(data);
   return *this;
}


MsgQosData::~MsgQosData()
{
   delete topicProperty_;
}

/**
 * @param isSubscribable if false PtP messages are invisible for subscriptions
 */
void MsgQosData::setSubscribable(const bool isSubscribable)
{
   subscribable_ = isSubscribable;
}

bool MsgQosData::isSubscribable() const
{
   return subscribable_.getValue();
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
   return getLifeTime()==0L && isForceDestroy()==false;
}

void MsgQosData::setAdministrative(bool administrative)
{
   administrative_ = administrative;
}

bool MsgQosData::isAdministrative() const
{
   return administrative_;
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
 * Send message to subscriber even if the content is the same as the previous. 
 * @param forceUpdate true update identical messages
 */
void MsgQosData::setForceUpdate(bool forceUpdate)
{
   forceUpdate_.setValue(forceUpdate, CREATED_BY_SETTER);
}

/**
 * @return true/false
 */
bool MsgQosData::isForceUpdate() const
{
   return forceUpdate_.getValue();
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

string MsgQosData::toXml(const string& extraOffset) const
{
   return toXml(false, extraOffset);
}

string MsgQosData::toXml(bool clearText, const string& extraOffset) const
{
   string ret;

   string offset = Constants::OFFSET + extraOffset;
   string extraOffset1 = extraOffset + Constants::INDENT;

   // WARNING: This dump must be valid, as it could be used by the
   //          persistent store
   ret += offset + "<qos>";

   if (!getState().empty() || !getStateInfo().empty()) {
      ret += offset + " <state id='" + getState();
      if (!getStateInfo().empty())
         ret += "' info='" + getStateInfo();
      ret += "'/>";
   }

   if (subscribable_.isModified())
      ret += offset + " <subscribable>" + Global::getBoolAsString(subscribable_.getValue()) + "</subscribable>";

   vector<Destination>::const_iterator iter = destinationList_.begin();
   while (iter != destinationList_.end()) {
      ret += (*iter).toXml(extraOffset1);
      iter++;
   }

   ret += offset + " <sender>" + sender_->getAbsoluteName() + "</sender>";

   if (NORM_PRIORITY != priority_)
      ret += offset + " <priority>" + lexical_cast<std::string>(priority_) + "</priority>";

   if (administrative_)
      ret += offset + " <administrative>" + lexical_cast<std::string>(administrative_) + "</administrative>";

   if (!subscriptionId_.empty())
      ret += offset + " <subscribe id='" + subscriptionId_ + "'/>";

   if (getLifeTime() > 0) {
      ret += offset + " <expiration lifeTime='" + lexical_cast<std::string>(getLifeTime());
      bool sendRemainingLife = true; // make it configurable !!!
      if (sendRemainingLife) {
         if (getRemainingLife() > 0)
            ret += "' remainingLife='" + lexical_cast<std::string>(getRemainingLife());
         else if (getRemainingLifeStatic() > 0)
            ret += "' remainingLife='" + lexical_cast<std::string>(getRemainingLifeStatic());
      }
      ret +=  "'/>";
   }

   if (getRcvTimestamp() != 0)
      ret += TimestampFactory::toXml(getRcvTimestamp(), extraOffset1, false);
   if(getQueueSize() > 0)
      ret += offset + " <queue index='" + lexical_cast<std::string>(getQueueIndex()) + "' size='" + lexical_cast<std::string>(getQueueSize()) + "'/>";
   if (getRedeliver() > 0)
      ret += offset + " <redeliver>" + lexical_cast<std::string>(getRedeliver()) + "</redeliver>";
   if (isPersistent())
      ret += offset + " <persistent/>";
   if (forceUpdate_.isModified())
      ret += offset + " <forceUpdate>" + lexical_cast<string>(forceUpdate_.getValue()) + "</forceUpdate>";
   if (forceDestroy_.isModified())
      ret += offset + " <forceDestroy>" + lexical_cast<string>(forceDestroy_.getValue()) + "</forceDestroy>";

   if (topicProperty_ != NULL) {
      ret += topicProperty_->toXml(extraOffset1);
   }

   RouteVector::const_iterator routeIter = routeNodeList_.begin();
   ret += offset + " <route>";
   while (routeIter != routeNodeList_.end()) {
      ret += (*routeIter).toXml(extraOffset1);
      routeIter++;
   }
   ret += offset + " </route>";
   ret += dumpClientProperties(extraOffset + Constants::INDENT, clearText);
   ret += offset + "</qos>";

   if (ret.length() < 16) return "";

   return ret;
}

MsgQosData* MsgQosData::getClone() const
{
   return new MsgQosData(*this);
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


bool MsgQosData::isForceDestroy() const
{
   return forceDestroy_.getValue();
}

void MsgQosData::setForceDestroy(bool forceDestroy)
{
   forceDestroy_.setValue(forceDestroy, CREATED_BY_SETTER);
}

}}}}

