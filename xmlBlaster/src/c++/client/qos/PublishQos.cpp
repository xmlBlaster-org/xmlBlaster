/*------------------------------------------------------------------------------
Name:      PublishQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <client/qos/PublishQos.h>
#include <util/Global.h>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace std;

namespace org { namespace xmlBlaster { namespace client { namespace qos {

   PublishQos::PublishQos(Global& global)
      : ME("PublishQos"), global_(global), msgQosData_(global, "")
   {
      // deprecated
      //setLifeTime(global_.getProperty().getLongProperty("message.lifeTime", -1));
   }

   PublishQos::PublishQos(Global& global, const Destination& destination)
      : ME("PublishQos"), global_(global), msgQosData_(global, "")
   {
      addDestination(destination);
   }

   PublishQos::PublishQos(Global& global, bool persistent)
      : ME("PublishQos"), global_(global), msgQosData_(global, "")
   {
      setPersistent(persistent);
   }

   const MsgQosData& PublishQos::getData()
   {
      return msgQosData_;
   }

   PriorityEnum PublishQos::getPriority() const
   {
      return msgQosData_.getPriority();
   }

   /**
    * Set message priority value, PriorityEnum::NORM_PRIORITY (5) is default.
    * PriorityEnum::MIN_PRIORITY (0) is slowest
    * whereas PriorityEnum.MAX_PRIORITY (9) is highest priority.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.publish.priority.html">The engine.qos.publish.priority requirement</a>
    */
   void PublishQos::setPriority(PriorityEnum priority)
   {
      msgQosData_.setPriority(priority);
   }

   /**
    * Send message to subscriber even if the content is the same as the previous?
    * <br />
    * Default is that xmlBlaster does send messages to subscribed clients, even the content didn't change.
    */
   void PublishQos::setForceUpdate(bool force)
   {
      msgQosData_.setForceUpdate(force);
   }

   /**
    * Control message life cycle on message expiry. 
    * @param forceDestroy true Force message destroy on message expire<br />
    *        false On message expiry messages which are already in callback queues are delivered.
    */
   void PublishQos::setForceDestroy(bool forceDestroy)
   {
      msgQosData_.setForceDestroy(forceDestroy);
   }

   void PublishQos::setSubscribable(bool isSubcribeable)
   {
      msgQosData_.setSubscribable(isSubcribeable);
   }

   /**
    * Mark a message to be readonly.
    * <br />
    * Only the first publish() will be accepted, followers are denied.
    */
   void PublishQos::setReadonly(bool readonly)
   {
      msgQosData_.setReadonly(readonly);
   }

   /**
    * Mark a message to be volatile or not.
    * <br />
    * A non-volatile messages stays in memory as long as the server runs<br />
    * A volatile messages exists only during publish and processing it (doing the updates).<br />
    * Defaults to false.
    */
   void PublishQos::setVolatile(bool volatileFlag)
   {
      msgQosData_.setVolatile(volatileFlag);
   }

   /**
    * @see #isVolatile()
    */
   bool PublishQos::isVolatile()
   {
      return msgQosData_.isVolatile();
   }

   /**
    * Mark a message to be persistent.
    */
   void PublishQos::setPersistent(bool persistent)
   {
      msgQosData_.setPersistent(persistent);
   }

   /**
    * The message expires after given milliseconds (message is erased).<p />
    * Clients will get a notify about expiration.<br />
    * This value is calculated relative to the rcvTimestamp in the xmlBlaster server.<br />
    * Passing -1 milliseconds asks the server for unlimited livespan, which
    * the server may or may not grant.
    * @param lifeTime in milliseconds
    */
   void PublishQos::setLifeTime(long lifeTime)
   {
      msgQosData_.setLifeTime(lifeTime);
   }

   /**
    * Add a destination where to send the message.
    * <p />
    * Note you can invoke this multiple times to send to multiple destinations.
    * @param destination  The loginName of a receiver or some destination XPath query
    */
   void PublishQos::addDestination(const Destination& destination)
   {
      msgQosData_.addDestination(destination);
   }

   /**
    * Access sender name.
    * @return loginName of sender or null if not known
    */
   SessionNameRef PublishQos::getSender()
   {
      return msgQosData_.getSender();
   }

   /*
   void PublishQos::setSender(const SessionName& sender)
   {
      msgQosData_.setSender(sender);
   }
   */

   /**
    * @param state The state to return to the server.
    *   e.g. Contants.STATE_OK, see Constants::java
    */
   void PublishQos::setState(const string& state)
   {
      msgQosData_.setState(state);
   }

   string PublishQos::getState()
   {
      return msgQosData_.getState();
   }

   /**
    * @param stateInfo The state info attribute to return to the server.
    */
   void PublishQos::setStateInfo(const string& stateInfo)
   {
      msgQosData_.setStateInfo(stateInfo);
   }

   string PublishQos::getStateInfo()
   {
      return msgQosData_.getStateInfo();
   }

   /**
    * Administer/configure the message topic. 
    */
   void PublishQos::setTopicProperty(const TopicProperty& topicProperty)
   {
      msgQosData_.setTopicProperty(topicProperty);
   }

   void PublishQos::setClientProperties(const QosData::ClientPropertyMap& cm)
   {
      msgQosData_.setClientProperties(cm);
   }

   const QosData::ClientPropertyMap& PublishQos::getClientProperties() const
   {
      return msgQosData_.getClientProperties();
   }

   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   string PublishQos::toString()
   {
      return toXml();
   }

   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   string PublishQos::toXml()
   {
      return msgQosData_.toXml();
   }

}}}}
