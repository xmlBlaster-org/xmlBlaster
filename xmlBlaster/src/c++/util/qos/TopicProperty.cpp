/*------------------------------------------------------------------------------
Name:      TopicProperty.cpp
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

#include <util/qos/TopicProperty.h>

#include <util/Constants.h>
#include <util/Global.h>
#include <util/lexical_cast.h>



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
const long destroyDelay_DEFAULT_DEFAULT = 60*1000L;
/** Is readonly allows only one initial message */
const bool DEFAULT_readonly = false;


   void TopicProperty::copy(const TopicProperty& prop)
   {
     msgUnitStoreProperty_ = NULL;
     historyQueueProperty_ = NULL;
     if (prop.msgUnitStoreProperty_)
        msgUnitStoreProperty_ = new MsgUnitStoreProperty(*prop.msgUnitStoreProperty_);
     if (prop.historyQueueProperty_)
        historyQueueProperty_ = new HistoryQueueProperty(*prop.historyQueueProperty_);

     destroyDelay_DEFAULT = prop.destroyDelay_DEFAULT;
     destroyDelay_ = prop.destroyDelay_;
     readonly_ = prop.readonly_;
   }



   TopicProperty::TopicProperty(Global& global)
      : ME("TopicProperty"), global_(global), log_(global.getLog("org.xmlBlaster.util.qos")),
        createDomEntry_(Prop<bool>(true))
   {
      msgUnitStoreProperty_  = NULL;
      historyQueueProperty_= NULL;
      destroyDelay_DEFAULT = global_.getProperty().getLongProperty("topic.destroyDelay", destroyDelay_DEFAULT_DEFAULT);

      setDestroyDelay(destroyDelay_DEFAULT);
      destroyDelay_ = destroyDelay_DEFAULT;
      readonly_ = DEFAULT_readonly;
   }

   TopicProperty::TopicProperty(const TopicProperty& prop)
      : ME(prop.ME), global_(prop.global_), log_(prop.log_),
        createDomEntry_(prop.createDomEntry_)
   {
      copy(prop);
   }

   TopicProperty& TopicProperty::operator =(const TopicProperty& prop)
   {
      createDomEntry_ = prop.createDomEntry_;
      copy(prop);
      return *this;
   }

   TopicProperty::~TopicProperty()
   {
      delete msgUnitStoreProperty_;
      delete historyQueueProperty_;
   }

   /**
    * @return readonly Once published the message can't be changed. 
    */
   void TopicProperty::setReadonly(bool readonly)
   {
      readonly_ = readonly;
   }

   /**
    * @return true/false
    */
   bool TopicProperty::isReadonly()
   {
      return readonly_;
   }

   /**
    * The life time of the message topic in state UNREFERENCED
    */
   long TopicProperty::getDestroyDelay()
   {
      return destroyDelay_;
   }

   /**
    * The life time of the message topic in state UNREFERENCED
    */
   void TopicProperty::setDestroyDelay(long destroyDelay)
   {
      destroyDelay_ = destroyDelay;
   }

   /**
    * Is the topic available in the internal DOM tree? 
    * @return true This is default and the topic is queryable with XPATH<br />
    *    false: No DOM tree is created for the topic and the topic is onvisible to XPATH queries
    */
   bool TopicProperty::createDomEntry() const
   {
      return createDomEntry_.getValue();
   }

   /**
    * Set if the topic is available in the internal DOM tree. 
    * @param true This is default and the topic is queryable with XPATH<br />
    *    false: No DOM tree is created for the topic and the topic is onvisible to XPATH queries
    */
   void TopicProperty::setCreateDomEntry(bool createDomEntry) {
      createDomEntry_.setValue(createDomEntry);
   }

   bool TopicProperty::hasMsgUnitStoreProperty()
   {
      return (msgUnitStoreProperty_ != NULL);
   }

   /**
    * @return the configuration of the message store, is never null
    */
   MsgUnitStoreProperty TopicProperty::getMsgUnitStoreProperty()
   {
      if (msgUnitStoreProperty_ == NULL) {
         msgUnitStoreProperty_ = new MsgUnitStoreProperty(global_, /*global_.getId()*/ "");
      }
      return *msgUnitStoreProperty_;
   }

   void TopicProperty::setMsgUnitStoreProperty(const MsgUnitStoreProperty& msgUnitStoreProperty)
   {
      if (msgUnitStoreProperty_) {
         delete msgUnitStoreProperty_;
         msgUnitStoreProperty_ = NULL;
      }
      msgUnitStoreProperty_ = new MsgUnitStoreProperty(msgUnitStoreProperty);
   }

   bool TopicProperty::hasHistoryQueueProperty()
   {
      return (historyQueueProperty_ != NULL);
   }

   /**
    * @return the configuration of the history queue, is never null
    */
   HistoryQueueProperty TopicProperty::getHistoryQueueProperty()
   {
      if (historyQueueProperty_ == NULL)  {
         historyQueueProperty_ = new HistoryQueueProperty(global_, /*global_.getId()*/ "");
      }
      return *historyQueueProperty_;
   }

   void TopicProperty::setHistoryQueueProperty(const HistoryQueueProperty& historyQueueProperty)
   {
      if (historyQueueProperty_) {
         delete historyQueueProperty_;
         historyQueueProperty_ = NULL;
      }
      historyQueueProperty_ = new HistoryQueueProperty(historyQueueProperty);
   }

   string TopicProperty::toXml(const string& extraOffset)
   {
      string ret;
      string offset = Constants::OFFSET + extraOffset;

      ret += offset + "<topic";
      if (DEFAULT_readonly != readonly_) {
         ret += " readonly='" + lexical_cast<std::string>(readonly_) + "'";
      }
      if (destroyDelay_DEFAULT_DEFAULT != destroyDelay_) {
         ret += " destroyDelay='" + lexical_cast<std::string>(destroyDelay_) + "'";
      }
      ret += ">";
      //string subscriptionId;

      if (hasMsgUnitStoreProperty()) {
         ret += getMsgUnitStoreProperty().toXml(extraOffset+Constants::INDENT);
      }
      if (hasHistoryQueueProperty()) {
         ret += getHistoryQueueProperty().toXml(extraOffset+Constants::INDENT);
      }
      ret += offset + "</topic>";

      if (ret.length() < 22) {
         return "";
      }

      return ret;
   }

}}}}
