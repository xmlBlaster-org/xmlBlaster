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
#include <vector>
#include <string>
#include <util/Log.h>
#include <util/qos/storage/TopicCacheProperty.h>
#include <util/qos/storage/HistoryQueueProperty.h>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos::storage;

using namespace std;

namespace org { namespace xmlBlaster { namespace util { namespace qos {


/**
 * A topic is destroyed 60 sec after state=UNREFERENCED is reached
 * This default can be modified in xmlBlaster.properties:
 * <pre>
 *    topic.destroyDelay=3600000 # One hour [millisec]
 * </pre>
 * Every message can set the destroyDelay value between 1 and destroyDelay_DEFAULT,
 * -1L sets the life cycle on forever.
 */ // TODO: Change to use glob instead of Global singleton! What about performance? Put variable into Global?
extern Dll_Export const long destroyDelay_DEFAULT_DEFAULT;
/** Is readonly allows only one initial message */
extern Dll_Export const bool DEFAULT_readonly;

class Dll_Export TopicProperty
{
private:
   string                ME;
   Global&               global_;
   Log&                  log_;
   TopicCacheProperty*   topicCacheProperty_;
   HistoryQueueProperty* historyQueueProperty_;

   /* If Pub/Sub style update: contains the subscribe ID which caused this topic */
   //string subscriptionId;

   bool readonly_; //  = DEFAULT_readonly;

   /** 
    * A topic is destroyed 60 sec after state=UNREFERENCED is reached
    * This is the configured destroyDelay in millis
    */
   long destroyDelay_;

   long destroyDelay_DEFAULT; // = Global.instance().getProperty().get("topic.destroyDelay", destroyDelay_DEFAULT_DEFAULT);

   void copy(const TopicProperty& prop);

public:
   /**
    * Constructs the specialized quality of service object for a publish() or update() call.
    */
   TopicProperty(Global& global);

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

   bool hasTopicCacheProperty();

   /**
    * @return the configuration of the message store, is never null
    */
   TopicCacheProperty getTopicCacheProperty();

   void setTopicCacheProperty(const TopicCacheProperty& topicCacheProperty);

   bool hasHistoryQueueProperty();

   /**
    * @return the configuration of the history queue, is never null
    */
   HistoryQueueProperty getHistoryQueueProperty();

   void setHistoryQueueProperty(const HistoryQueueProperty& historyQueueProperty);

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the message QoS as a XML ASCII string, never null but "" if all values are default
    */
   string toXml(const string& extraOffset="");
};

}}}}
#endif
