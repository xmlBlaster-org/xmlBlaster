/*------------------------------------------------------------------------------
Name:      PublishQos.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * This class encapsulates the qos of a publish() message.
 * <p />
 * So you don't need to type the 'ugly' XML ASCII std::string by yourself.
 * After construction access the ASCII-XML std::string with the toXml() method.
 * <br />
 * A typical <b>publish</b> qos in Publish/Subcribe mode could look like this:<br />
 * <pre>
 *  &lt;qos>
 *     &lt;priority>5&lt;/priority>
 *     &lt;expiration lifeTime='60000'/>
 *     &lt;persistent />  &lt;!-- The message shall be recoverable if xmlBlaster crashes -->
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

#ifndef _CLIENT_QOS_PUBLISHQOS_H
#define _CLIENT_QOS_PUBLISHQOS_H

#include <util/xmlBlasterDef.h>
#include <util/qos/storage/QueuePropertyBase.h>
#include <util/PriorityEnum.h>
#include <util/qos/MsgQosData.h>




namespace org { namespace xmlBlaster { namespace client { namespace qos {

class Dll_Export PublishQos
{
private:
   std::string     ME;
   org::xmlBlaster::util::Global&    global_;
   org::xmlBlaster::util::qos::MsgQosData msgQosData_;

public:
   /**
    * Default constructor for transient messages.
    */
   PublishQos(org::xmlBlaster::util::Global& global);

   /**
    * Default constructor for transient PtP messages.
    * <p />
    * To make the message persistent, use the setPersistent(true) method
    * @param destination The object containing the destination address.<br />
    *        To add more destinations, us the addDestination() method.
    */
   PublishQos(org::xmlBlaster::util::Global& global, const org::xmlBlaster::util::Destination& destination);

   /**
    * @param persistent true = store the message persistently
    */
   PublishQos(org::xmlBlaster::util::Global& global, bool persistent);

   /**
    * Returns the immutable internal data holder. 
    */
   const org::xmlBlaster::util::qos::MsgQosData& getData();

   /**
    * Message priority.
    * @return priority 0 (=Lowest) - 9 (=Highest)
    */
   org::xmlBlaster::util::PriorityEnum getPriority() const;

   /**
    * Set message priority value, org::xmlBlaster::util::PriorityEnum::NORM_PRIORITY (5) is default.
    * org::xmlBlaster::util::PriorityEnum::MIN_PRIORITY (0) is slowest
    * whereas org::xmlBlaster::util::PriorityEnum.MAX_PRIORITY (9) is highest priority.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.publish.priority.html">The engine.qos.publish.priority requirement</a>
    */
   void setPriority(org::xmlBlaster::util::PriorityEnum priority);

   /**
    * Send message to subscriber even if the content is the same as the previous?
    * <br />
    * Default is that xmlBlaster does send messages to subscribed clients, even the content didn't change.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.publish.forceUpdate.html">The engine.qos.publish.forceUpdate requirement</a>
    */
   void setForceUpdate(bool force);

   /**
    * Control message life cycle on message expiry. 
    * @param forceDestroy true Force message destroy on message expire<br />
    *        false On message expiry messages which are already in callback queues are delivered.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.publish.isVolatile.html">The engine.qos.publish.isVolatile requirement</a>
    */
   void setForceDestroy(bool forceDestroy);

   /**
    * As a default setting you can subscribe on all messages (PtP or PubSub). 
    * @param isSubscribable true if Publish/Subscribe style is used<br />
    *         false Only possible for PtP messages to keep PtP secret (you can't subscribe them)
    */
   void setSubscribable(bool isSubcribeable);

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
   void setPersistent(bool persistent);

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
   void addDestination(const org::xmlBlaster::util::Destination& destination);

   /**
    * Access sender name.
    * @return loginName of sender or null if not known
    */
   org::xmlBlaster::util::SessionNameRef getSender();

   /*
    * Access sender name.
    * @param loginName of sender
    * @deprecated The sender is forced to the correct client name automatically
    */
   //void setSender(const org::xmlBlaster::util::SessionName& sender);

   /**
    * @param state The state to return to the server.
    *   e.g. Contants.STATE_OK, see Constants::java
    */
   void setState(const std::string& state);

   std::string getState();

   /**
    * @param stateInfo The state info attribute to return to the server.
    */
   void setStateInfo(const std::string& stateInfo);

   std::string getStateInfo();

   /**
    * Administer/configure the message topic. 
    */
   void setTopicProperty(const org::xmlBlaster::util::qos::TopicProperty& topicProperty);

   /**
    * Add a client property key and value. 
    * A typical example is:
    * <pre>
    * publishQos.addClientProperty("myKey", "myValue");
    * </pre>
    * If you want to send a string in your own locale character set:
    * <pre>
    * publishQos.addClientProperty("myKey", "myValue", Constants::TYPE_STRING, Constants::ENCODING_BASE64, "windows-1252");
    * </pre>
    * @param name  The unique property key in US-ASCII encoding (7-bit), UTF-8 should work as well
    *              A duplicate key will overwrite the old setting
    * @param value Your data . The type (like "float") is guessed from T_VALUE
    *              NOTE: "vector<unsigned char>" "unsigned char*" are
    *                    treated as BLOBs and will be transferred Base64 encoded.
    * @param type The data type of the value, optional, e.g. Constants::TYPE_FLOAT ("float")
    * @param encoding How the data is transferred, org::xmlBlaster::util::Constants::ENCODING_BASE64 or ""
    * @param charset XmlBlaster expects all XML strings as UTF-8, however you can send your client properties
    * in any other charset but you must then encode it with ENCODING_BASE64 and pass the charset used, for example "windows-1252".
    * Please use the official IANA charset names.
    * @see http://www.iana.org/assignments/charset-reg/
    * @see ClientProperty::#ClientProperty
   */
   template <typename T_VALUE> void addClientProperty(
            const std::string& name,
            const T_VALUE& value,
            const std::string& type="",
            const std::string& encoding="",
            const std::string& charset="") {
      msgQosData_.addClientProperty(name, value, type, encoding, charset);
   }

   /**
    * Set all clientProperties at once, overwrites all existing. 
    * @param cm The new properties
    */
   void setClientProperties(const org::xmlBlaster::util::qos::QosData::ClientPropertyMap& cm);

   /**
    * Get a map containing all send client properties
    */
   const org::xmlBlaster::util::qos::QosData::ClientPropertyMap& getClientProperties() const;

   /**
    * Converts the data into a valid XML ASCII std::string.
    * @return An XML ASCII std::string
    */
   std::string toString();

   /**
    * Converts the data into a valid XML ASCII std::string.
    * @return An XML ASCII std::string
    */
   std::string toXml();
};

}}}}

#endif
