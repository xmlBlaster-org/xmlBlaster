/*------------------------------------------------------------------------------
Name:      PublishQos.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * This class encapsulates the qos of a publish() message.
 * <p />
 * So you don't need to type the 'ugly' XML ASCII string by yourself.
 * After construction access the ASCII-XML string with the toXml() method.
 * <br />
 * A typical <b>publish</b> qos in Publish/Subcribe mode could look like this:<br />
 * <pre>
 *  &lt;qos>
 *     &lt;priority>5&lt;/priority>
 *     &lt;expiration lifeTime='60000'/>
 *     &lt;isDurable />  &lt;!-- The message shall be recoverable if xmlBlaster crashes -->
 *     &lt;forceUpdate>true&lt;/forceUpdate>
 *     &lt;readonly />
 *  &lt;/qos>
 * </pre>
 * A typical <b>publish</b> qos in PtP mode could look like this:<br />
 * <pre>
 *  &lt;qos>
 *     &lt;destination queryType='EXACT' forceQueuing='true'>
 *        joe
 *     &lt;/destination>
 *     &lt;destination>
 *        /node/heron/client/Tim/-2
 *     &lt;/destination>
 *  &lt;/qos>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 * @see org.xmlBlaster.util.qos.MsgQosSaxFactory
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html">publish interface</a>
 */

#ifndef UTIL_QOS_PUBLISHQOS_H
#define UTIL_QOS_PUBLISHQOS_H

#include <util/xmlBlasterDef.h>
#include <util/queue/QueuePropertyBase.h>
#include <util/PriorityEnum.h>
#include <util/qos/MsgQosData.h>

using namespace org::xmlBlaster::util;
using namespace std;

namespace org { namespace xmlBlaster { namespace util { namespace qos {

class Dll_Export PublishQos
{
private:
   string     ME;
   Global&    global_;
   MsgQosData msgQosData_;

public:
   /**
    * Default constructor for transient messages.
    */
   PublishQos(Global& global);

   /**
    * Default constructor for transient PtP messages.
    * <p />
    * To make the message persistent, use the durable() method
    * @param destination The object containing the destination address.<br />
    *        To add more destinations, us the addDestination() method.
    */
   PublishQos(Global& global, const Destination& destination);

   /**
    * @param isDurable true = store the message persistently
    */
   PublishQos(Global& global, bool durable);

   MsgQosData getData() const;

   /**
    * Message priority.
    * @return priority 0 (=Lowest) - 9 (=Highest)
    */
   PriorityEnum getPriority() const;

   /**
    * Set message priority value, PriorityEnum::NORM_PRIORITY (5) is default.
    * PriorityEnum::MIN_PRIORITY (0) is slowest
    * whereas PriorityEnum.MAX_PRIORITY (9) is highest priority.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.publish.priority.html">The engine.qos.publish.priority requirement</a>
    */
   void setPriority(PriorityEnum priority);

   /**
    * Send message to subscriber even if the content is the same as the previous?
    * <br />
    * Default is that xmlBlaster does send messages to subscribed clients, even the content didn't change.
    */
   void setForceUpdate(bool force);

   /**
    * Mark a message to be readonly.
    * <br />
    * Only the first publish() will be accepted, followers are denied.
    */
   void setReadonly(bool readonly);

   /**
    * Mark a message to be volatile or not.
    * <br />
    * A non-volatile messages stays in memory as long as the server runs<br />
    * A volatile messages exists only during publish and processing it (doing the updates).<br />
    * Defaults to false.
    */
   void setVolatile(bool volatileFlag);

   /**
    * @see #isVolatile()
    */
   bool isVolatile();

   /**
    * Mark a message to be persistent.
    */
   void setDurable(bool durable);

   /**
    * The message expires after given milliseconds (message is erased).<p />
    * Clients will get a notify about expiration.<br />
    * This value is calculated relative to the rcvTimestamp in the xmlBlaster server.<br />
    * Passing -1 milliseconds asks the server for unlimited livespan, which
    * the server may or may not grant.
    * @param lifeTime in milliseconds
    */
   void setLifeTime(long lifeTime);

   /**
    * Add a destination where to send the message.
    * <p />
    * Note you can invoke this multiple times to send to multiple destinations.
    * @param destination  The loginName of a receiver or some destination XPath query
    */
   void addDestination(const Destination& destination);

   /**
    * Access sender name.
    * @return loginName of sender or null if not known
    */
   SessionQos getSender();

   /**
    * Access sender name.
    * @param loginName of sender
    */
   void setSender(const SessionQos& sender);

   /**
    * @param state The state to return to the server.
    *   e.g. Contants.STATE_OK, see Constants::java
    */
   void setState(const string& state);

   string getState();

   /**
    * @param stateInfo The state info attribute to return to the server.
    */
   void setStateInfo(const string& stateInfo);

   string getStateInfo();

   /**
    * Administer/configure the message topic. 
    */
   void setTopicProperty(const TopicProperty& topicProperty);

   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   string toString();

   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   string toXml();
};

}}}}

#endif
