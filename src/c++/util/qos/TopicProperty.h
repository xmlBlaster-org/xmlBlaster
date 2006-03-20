/*------------------------------------------------------------------------------
Name:      TopicProperty.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Data container handling properties of a message topic. 
 * <p />
 * QoS Informations sent from the client to the server via the publish() method and back via the update() method<br />
 * They are needed to control xmlBlaster and inform the client.
 * <p />
 * <p>
 * This data holder is accessible through decorators, each of them allowing a specialized view on the data
 * </p>
 * @see org.xmlBlaster.util.qos.MsgQosSaxFactory
 * @see org.xmlBlaster.test.classtest.qos.MsgQosFactoryTest
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */

#ifndef _UTIL_QOS_TOPICPROPERTY_H
#define _UTIL_QOS_TOPICPROPERTY_H

#include <util/xmlBlasterDef.h>
#include <string>
#include <util/I_Log.h>
#include <util/qos/storage/MsgUnitStoreProperty.h>
#include <util/qos/storage/HistoryQueueProperty.h>
#include <util/Prop.h>

namespace org { namespace xmlBlaster { namespace util { namespace qos {

/**
 * A topic is destroyed 60 sec after state=UNREFERENCED is reached
 * This default can be modified in xmlBlaster.properties:
 * <pre>
 *    topic.destroyDelay=3600000 # One hour [millisec]
 * </pre>
 * Every message can set the destroyDelay value between 1 and destroyDelay_DEFAULT,
 * -1L sets the life cycle on forever.
 */ // TODO: Change to use glob instead of org::xmlBlaster::util::Global singleton! What about performance? Put variable into org::xmlBlaster::util::Global?
extern Dll_Export const long destroyDelay_DEFAULT_DEFAULT;
/** Is readonly allows only one initial message */
extern Dll_Export const bool DEFAULT_readonly;

class Dll_Export TopicProperty
{
private:
   std::string ME;
   org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::I_Log& log_;
   org::xmlBlaster::util::qos::storage::MsgUnitStoreProperty* msgUnitStoreProperty_;
   org::xmlBlaster::util::qos::storage::HistoryQueueProperty* historyQueueProperty_;

   /* If Pub/Sub style update: contains the subscribe ID which caused this topic */
   //std::string subscriptionId;

   bool readonly_; //  = DEFAULT_readonly;

   /** 
    * A topic is destroyed 60 sec after state=UNREFERENCED is reached
    * This is the configured destroyDelay in millis
    */
   long destroyDelay_;

   long destroyDelay_DEFAULT; // = org::xmlBlaster::util::Global.instance().getProperty().get("topic.destroyDelay", destroyDelay_DEFAULT_DEFAULT);

   Prop<bool> createDomEntry_;

   void copy(const TopicProperty& prop);

public:
   /**
    * Constructs the specialized quality of service object for a publish() or update() call.
    */
   TopicProperty(org::xmlBlaster::util::Global& global);

   TopicProperty(const TopicProperty& prop);
   TopicProperty& operator =(const TopicProperty& prop);

   ~TopicProperty();

   /**
    * @return readonly Once published the message can't be changed. 
    */
   void setReadonly(bool readonly);

   /**
    * @return true/false
    */
   bool isReadonly();

   /**
    * The life time of the message topic in state UNREFERENCED
    */
   long getDestroyDelay();

   /**
    * The life time of the message topic in state UNREFERENCED
    */
   void setDestroyDelay(long destroyDelay);

   /**
    * Is the topic available in the internal DOM tree? 
    * @return true This is default and the topic is queryable with XPATH<br />
    *    false: No DOM tree is created for the topic and the topic is onvisible to XPATH queries
    */
   bool createDomEntry() const;

   /**
    * Set if the topic is available in the internal DOM tree. 
    * @param true This is default and the topic is queryable with XPATH<br />
    *    false: No DOM tree is created for the topic and the topic is onvisible to XPATH queries
    */
   void setCreateDomEntry(bool createDomEntry);

   bool hasMsgUnitStoreProperty();

   /**
    * @return the configuration of the message store, is never null
    */
   org::xmlBlaster::util::qos::storage::MsgUnitStoreProperty getMsgUnitStoreProperty();

   void setMsgUnitStoreProperty(const org::xmlBlaster::util::qos::storage::MsgUnitStoreProperty& msgUnitStoreProperty);

   bool hasHistoryQueueProperty();

   /**
    * @return the configuration of the history queue, is never null
    */
   org::xmlBlaster::util::qos::storage::HistoryQueueProperty getHistoryQueueProperty();

   void setHistoryQueueProperty(const org::xmlBlaster::util::qos::storage::HistoryQueueProperty& historyQueueProperty);

   /**
    * Dump state of this object into a XML ASCII std::string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the message QoS as a XML ASCII std::string, never null but "" if all values are default
    */
   std::string toXml(const std::string& extraOffset="");
};

}}}}
#endif
