/*------------------------------------------------------------------------------
Name:      MsgQosData.h
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

#ifndef _UTIL_QOS_MSGQOSDATA_H
#define _UTIL_QOS_MSGQOSDATA_H

#include <util/xmlBlasterDef.h>
#include <util/qos/QosData.h>

// #include <util/Constants.h>
#include <util/Destination.h>
#include <util/qos/SessionQos.h>
#include <util/qos/TopicProperty.h>
// #include <util/Timestamp.h>
#include <util/PriorityEnum.h>
#include <util/cluster/RouteInfo.h>
#include <util/cluster/NodeId.h>
#include <util/Prop.h>
#include <vector>
#include <string>



// 



namespace org { namespace xmlBlaster { namespace util { namespace qos {

class Dll_Export MsgQosData : public QosData
{
private:

   mutable bool isExpired_; // = false; // cache the expired state for performance reasons

   org::xmlBlaster::util::qos::TopicProperty* topicProperty_;

   /**
    * A message lease lasts forever if not otherwise specified. <p />
    * The default message life cycle can be modified in xmlBlaster.properties:<br />
    * <code>message.lease.maxLifeTime=3600000 # One hour lease</code><br />
    * Every message can set the lifeTime value between 1 and maxLifeTime, 
    * -1L sets the life cycle on forever.
    */ // TODO: Change to use glob instead of org::xmlBlaster::util::Global singleton! What about performance? Put variable into org::xmlBlaster::util::Global?
   long maxLifeTime_;

/*
   {
      return org::xmlBlaster::util::Global.instance().getProperty().get("message.maxLifeTime", -1L);
   }
*/

   /** If Pub/Sub style update: contains the subscribe ID which caused this update */
   std::string subscriptionId_;

   Prop<bool> subscribable_;

   /** the number of resend tries on failure */
   int redeliver_;
   long queueIndex_; //  = -1L;
   long queueSize_; // = -1L;

   /** Internal use only, is this message sent from the persistence layer? */
   bool fromPersistenceStore_; // = false;

   bool persistent_; // = DEFAULT_persistent;

   /**
    * Send message to subscriber even the content is the same as the previous?
    * <br />
    * Default is that xmlBlaster does send messages to subscribed clients, even the content didn't change.
    */
   bool forceUpdate_; // = DEFAULT_forceUpdate;

   /**
    * A message expires after some time and will be discarded.
    * Clients will get a notify about expiration.
    * This is the configured lifeTime in millis of the message.
    */
   long lifeTime_; // = -1;

   long remainingLifeStatic_; // = -1;

   /** the sender (publisher) of this message (unique loginName) */
   mutable org::xmlBlaster::util::qos::SessionQos sender_;

   /** The priority of the message */
   org::xmlBlaster::util::PriorityEnum priority_; // = org::xmlBlaster::util::PriorityEnum.NORM_PRIORITY;

   void init();

protected:
   /**
    * ArrayList for loginQoS, holding all destination addresses (Destination objects)
    */
   std::vector<Destination> destinationList_;


   // TODO: Pass with client QoS!!!
   bool receiveTimestampHumanReadable_; // = org::xmlBlaster::util::Global.instance().getProperty().get("cb.receiveTimestampHumanReadable", false);

   Prop<bool> forceDestroy_;

   void copy(const MsgQosData& data);

public:
   /**
    * Constructs the specialized quality of service object for a publish() or update() call.
    * @param The factory which knows how to serialize and parse me
    */
//   long size_;

   MsgQosData(org::xmlBlaster::util::Global& global, const std::string& serialData="");

   MsgQosData(const MsgQosData& data);

   MsgQosData& operator=(const MsgQosData& data);

   virtual ~MsgQosData();

   void setSubscribable(const bool isSubcribeable);

   /**
    * Test if Publish/Subscribe style is used for PtP messages.
    *
    * @return false if PtP message is invisible for subscribes
    */
   bool isSubscribable() const;

   /**
    * Test if Point to Point addressing style is used.
    *
    * @return true if addressing of the destination is used
    *         false No destinations given
    */
   bool isPtp() const;

   /**
    * @param volatile true/false
    */
   void setVolatile(bool volatileFlag);

   /**
    * @return true/false
    */
   bool isVolatile() const;

   /**
    * @return true If the default is the current setting. 
    */
   bool isVolatileDefault() const;

   /**
    * If Pub/Sub style update: contains the subscribe ID which caused this update
    * @param subscriptionId null if PtP message
    */
   void setSubscriptionId(const std::string& subscriptionId);

   /**
    * If Pub/Sub style update: contains the subscribe ID which caused this update
    * @return subscribeId or null if PtP message
    */
   std::string getSubscriptionId() const;

   /**
    * @param persistent mark a message as persistent
    */
   void setPersistent(bool persistent);

   /**
    * @return true/false
    */
   bool isPersistent() const;

   /**
    * Send message to subscriber even if the content is the same as the previous. 
    * @param forceUpdate
    */
   void setForceUpdate(bool forceUpdate);

   /**
    * @return true/false
    */
   bool isForceUpdate() const;

   /**
    * @return true if we have default setting
    */
   bool isForceUpdateDefault() const;

   /**
    * @return readonly Once published the message can't be changed. 
    */
   void setReadonly(bool readonly);

   /**
    * @return true/false
    */
   bool isReadonly() const;

   /**
    * Access sender unified naming object.
    * @return sessionName of sender or null if not known
    */
   org::xmlBlaster::util::qos::SessionQos getSender() const;

   /**
    * Access sender name.
    * @param loginName of sender
    */
   void setSender(const org::xmlBlaster::util::qos::SessionQos& senderSessionQos) const;

   /**
    * Set > 0 if the message probably is redelivered (number of retries). 
    * @param redeliver if == 0 The message is guaranteed to be delivered only once.
    */
   void setRedeliver(int redeliver);

   /**
    * Increment the redeliver counter
    */
   void incrRedeliver();

   /**
    * Returns > 0 if the message probably is redelivered. 
    * @return == 0 The message is guaranteed to be delivered only once.
    */
   int getRedeliver() const;

  /**
    * @param queueSize The number of queued messages
    */
   void setQueueSize(long queueSize);

    /**
    * @return The number of queued messages
    */
   long getQueueSize() const;

   /**
    * @param queueIndex The index of the message in the queue
    */
   void setQueueIndex(long queueIndex);

   /**
    * @return The index of the message in the queue
    */
   long getQueueIndex() const;

   /**
    * Message priority.
    * @return priority 0-9
    * @see org.xmlBlaster.util.enum.PriorityEnum
    */
   org::xmlBlaster::util::PriorityEnum getPriority() const;

   /**
    * Set message priority value, org::xmlBlaster::util::PriorityEnum.NORM_PRIORITY (5) is default. 
    * org::xmlBlaster::util::PriorityEnum.MIN_PRIORITY (0) is slowest
    * whereas org::xmlBlaster::util::PriorityEnum.MAX_PRIORITY (9) is highest priority.
    * @see org.xmlBlaster.util.enum.PriorityEnum
    */
   void setPriority(org::xmlBlaster::util::PriorityEnum priority);

   /**
    * Internal use only, is this message sent from the persistence layer?
    * @return true/false
    */
   bool isFromPersistenceStore() const;

   /**
    * Internal use only, set if this message sent from the persistence layer
    * @param true/false
    */
   void setFromPersistenceStore(bool fromPersistenceStore);

   /**
    * The life time of the message or -1L if forever
    */
   long getLifeTime()const;

   /**
    * The life time of the message or -1L if forever
    */
   void setLifeTime(long lifeTime);

   /**
    * @return Milliseconds until message expiration (from now) or -1L if forever
    *         if 0L the message is expired
    */
   long getRemainingLife() const;

   /**
    * This is the value delivered in the QoS (as it was calculated by the server on sending)
    * and is NOT dynamically recalculated.
    * So trust this value only if your client clock is out of date (or not trusted) and
    * if you know the message sending latency is not too big.
    * @return Milliseconds until message expiration (from now) or -1L if forever
    *         if 0L the message is expired
    */
   long getRemainingLifeStatic() const;

   void setRemainingLifeStatic(long remainingLifeStatic);

   /**
    * Calculates if we are expired
    */
   bool isExpired() const;

   /**
    * The server default for max. span of life,
    * adjustable with property "message.maxLifeTime"
    * @return max span of life for a message
    */
   long getMaxLifeTime() const;

   /**
    * Tagged form of message receive, e.g.:<br />
    * &lt;rcvTimestamp nanos='1007764305862000004'/>
    *
    * @see org.xmlBlaster.util.Timestamp
    */
   std::string getXmlRcvTimestamp();

   /**
    * Set timestamp to current time.
    */
   void touchRcvTimestamp();

   /**
    * Get all the destinations of this message.
    * This should only be used with PTP style messaging<br />
    * Check <code>if (isPtp()) ...</code> before calling this method
    *
    * @return a valid ArrayList containing 0 - n std::strings with destination names (loginName of clients)<br />
    *         null if Publish/Subscribe style is used
    */
   std::vector<Destination> getDestinations() const;

   /**
    * Add a destination. 
    * The destination is copied into a list - your instance can be savly disappear
    */
   void addDestination(const Destination& destination);

   /**
    * Dump state of this object into a XML ASCII std::string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the message QoS as a XML ASCII std::string
    */
   std::string toXml(const std::string& extraOffset="") const;

   void setTopicProperty(const org::xmlBlaster::util::qos::TopicProperty& prop);

   org::xmlBlaster::util::qos::TopicProperty getTopicProperty();

   bool hasTopicProperty() const;

   void setForceDestroy(bool forceDestroy);

   bool getForceDestroy() const;

};

}}}}

#endif
