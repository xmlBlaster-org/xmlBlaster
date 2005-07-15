/*------------------------------------------------------------------------------
Name:      MsgQosSaxFactory.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
#include <util/qos/MsgQosFactory.h>
#include <util/MethodName.h>
#include <util/Global.h>
#include <util/StringTrim.h>
#include <util/lexical_cast.h>


using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::parser;
using namespace org::xmlBlaster::util::cluster;
using namespace org::xmlBlaster::util::qos::storage;
using namespace std;

namespace org { namespace xmlBlaster { namespace util { namespace qos {

MsgQosFactory::MsgQosFactory(Global& global)
   : XmlHandlerBase(global), 
     ME("MsgQosFactory"),
     msgQosDataP_(0), 
     destination_(global), 
     routeInfo_(global),
     queuePropertyFactory_(global),
     clientProperty_(0)
{
   ME                 = string("MsgQosFactory");
   LIFE_TIME          = string("lifeTime");
   FORCE_DESTROY      = string("forceDestroy");
   REMAINING_LIFE     = string("remainingLife");
   READ_ONLY          = string("readOnly");
   DESTROY_DELAY      = string("destroyDelay");
   CREATE_DOM_ENTRY   = string("createDomEntry");
   NANOS              = string("nanos");
   ID                 = string("id");
   STRATUM            = string("stratum");
   TIMESTAMP          = string("timestamp");
   DIRTY_READ         = string("dirtyRead");
   INDEX              = string("index");
   SIZE               = string("size");
   inState_           = false;
   inSubscribe_       = false;
   inRedeliver_       = false;
   inQueue_           = false;
   inPersistence_     = false;
   inDestination_     = false;
   inSender_          = false;
   inPriority_        = false;
   inClientProperty_  = false;
   inExpiration_      = false;
   inRcvTimestamp_    = false;
   inIsVolatile_      = false;
   inIsPersistent_    = false;
   inReadonly_        = false;
   inRoute_           = false;
   sendRemainingLife_ = true;
   inQos_             = false;
}

MsgQosFactory::~MsgQosFactory() 
{
   if (clientProperty_ != 0) {
      delete(clientProperty_);
   }
   if (msgQosDataP_ != 0) {
      delete(msgQosDataP_);
   }
}                

MsgQosData MsgQosFactory::readObject(const string& xmlQos)
{
   delete msgQosDataP_;
   msgQosDataP_ = new MsgQosData(global_);
   routeInfo_ = RouteInfo(global_);
   //queuePropertyFactory_ = QueuePropertyFactory(global_);
   delete clientProperty_;
   clientProperty_ = 0;

   if (xmlQos.empty()) init("<qos/>");
   else init(xmlQos);
   return *msgQosDataP_;
}

void MsgQosFactory::startElement(const string &name, const AttributeMap& attrs)
{
   bool      tmpBool;
   string    tmpString;
   long      tmpLong;
   Timestamp tmpTimestamp;
   if (name.compare("qos") == 0) {
     inQos_ = true;
     return;
   }
   if (name.compare("persistence") == 0 || inPersistence_) {
      if (!inQos_) return;
      inPersistence_ = true;
      queuePropertyFactory_.startElement(name, attrs);
      return;
   }
   if (name.compare("state") == 0) {
      if (!inQos_) return;
      inState_ = true;
      AttributeMap::const_iterator iter = attrs.begin();
      string tmpName = (*iter).first;
      string tmpValue = (*iter).second;
      while (iter != attrs.end()) {
         if (tmpName.compare("id") == 0) {
            msgQosDataP_->setState(tmpValue);
         }
         else if (tmpName.compare("info") == 0) {
            msgQosDataP_->setStateInfo(tmpValue);
         }
         iter++;
      }
      return;
   }
   if (name.compare("destination") == 0) {
      if (!inQos_) return;
      inDestination_ = true;
      destination_ = Destination(global_);
      AttributeMap::const_iterator iter = attrs.begin();
      while (iter != attrs.end()) {
         string tmpName = (*iter).first;
         string tmpValue = (*iter).second;
         if (tmpName.compare("queryType") == 0) {
            string queryType = tmpValue;
            if (queryType == "EXACT")      destination_.setQueryType(queryType);
            else if (queryType == "XPATH") destination_.setQueryType(queryType);
            else log_.error(ME, string("Sorry, destination queryType='") + queryType + string("' is not supported"));
         }
         else if( tmpName.compare("forceQueuing") == 0) {
            destination_.forceQueuing(XmlHandlerBase::getBoolValue(tmpValue));
         }
         iter++;
      }

      return;
   }
   if (name.compare("sender") == 0) {
      if (!inQos_) return;
      inSender_ = true;
      return;
   }
   if (name.compare("priority") == 0) {
      if (!inQos_) return;
      inPriority_ = true;
      return;
   }
   if (name.compare("expiration") == 0) {
      if (!inQos_) return;
      inExpiration_ = true;
//         int len = attrs.getLength();
      if (getLongAttr(attrs, LIFE_TIME, tmpLong)) msgQosDataP_->setLifeTime(tmpLong);
      else {
         log_.warn(ME, string("QoS <expiration> misses lifeTime attribute, setting default of ") + lexical_cast<std::string>(msgQosDataP_->getMaxLifeTime()));
         msgQosDataP_->setLifeTime(msgQosDataP_->getMaxLifeTime());
      }
      if (getBoolAttr(attrs, FORCE_DESTROY, tmpBool)) msgQosDataP_->setForceDestroy(tmpBool);
      if (getLongAttr(attrs, REMAINING_LIFE, tmpLong)) msgQosDataP_->setRemainingLifeStatic(tmpLong);
      return;
   }
   if (name.compare("rcvTimestamp") == 0) {
      if (!inQos_) return;
      if (getTimestampAttr(attrs, NANOS, tmpTimestamp)) msgQosDataP_->setRcvTimestamp(tmpTimestamp);
      inRcvTimestamp_ = true;
      return;
   }
   if (name.compare("redeliver") == 0) {
      if (!inQos_) return;
      inRedeliver_ = true;
      return;
   }
   if (name.compare("route") == 0) {
      if (!inQos_) return;
      inRoute_ = true;
      return;
   }
   if (name.compare("node") == 0) {
      if (!inRoute_) {
         log_.error(ME, "Ignoring <node>, it is not inside <route>");
         return;
      }
      if (attrs.size() > 0) {
         if (!getStringAttr(attrs, ID, tmpString)) {
            log_.error(ME, "QoS <route><node> misses id attribute, ignoring node");
            return;
         }
         NodeId nodeId(global_, tmpString); // where tmpString is the id
         int stratum = 0;
         if (!getIntAttr(attrs, STRATUM, stratum)) {
            log_.warn(ME, "QoS <route><node> misses stratum attribute, setting to 0: ");
            //Thread.currentThread().dumpStack();
         }
         Timestamp timestamp = 0;
         if (!getTimestampAttr(attrs, TIMESTAMP, timestamp)) {
            log_.warn(ME, "QoS <route><node> misses receive timestamp attribute, setting to 0");
         }
    //      bool dirtyRead = org::xmlBlaster::util::cluster::DEFAULT_dirtyRead;
         if (log_.trace()) log_.trace(ME, "Found node tag");
         routeInfo_ = RouteInfo(nodeId, stratum, timestamp);
         if (getBoolAttr(attrs, DIRTY_READ, tmpBool)) routeInfo_.setDirtyRead(tmpBool);
      }
      return;
   }
   if (name.compare(MethodName::SUBSCRIBE) == 0) {
      if (!inQos_) return;
      inSubscribe_ = true;
      AttributeMap::const_iterator iter = attrs.begin();
      while (iter != attrs.end()) {
         if ( ((*iter).first).compare("id") == 0) {
            msgQosDataP_->setSubscriptionId( (*iter).second );
         }
         iter++;
      }
      return;
   }

   if (name.compare("persistent") == 0) {
      if (!inQos_) return;
      msgQosDataP_->setPersistent(true);
      return;
   }

   if (name.compare("forceUpdate") == 0) {
      if (!inQos_) return;
      msgQosDataP_->setForceUpdate(true);
      return;
   }

   if (name.compare("readonly") == 0) {
      if (!inQos_) return;
      msgQosDataP_->setReadonly(true);
      log_.error(ME, "<qos><readonly/></qos> is deprecated, please use readonly as topic attribute <qos><topic readonly='true'></qos>");
      return;
   }
   
   if (name.compare("clientProperty") == 0) {
      if (!inQos_) return;
      inClientProperty_ = true;
      character_.erase();
      string name;
      AttributeMap::const_iterator iter = attrs.find("name");
      if (iter != attrs.end()) name = (*iter).second;
      string encoding;
      iter = attrs.find("encoding");
      if (iter != attrs.end()) encoding = (*iter).second;
      string type;
      iter = attrs.find("type");
      if (iter != attrs.end()) type = (*iter).second;
      clientProperty_ = new ClientProperty(true, name, type, encoding);
   }
      
}


void MsgQosFactory::characters(const string &ch) 
{
   XmlHandlerBase::characters(ch);
   if (inQueue_ || inPersistence_) queuePropertyFactory_.characters(ch);
}


void MsgQosFactory::endElement(const string &name) 
{
   if (name.compare("qos") == 0) {
      inQos_ = false;
      return;
   }
   //log_.error(ME, "endElement: name=" + name + " character_=" + character_);
   if (inQueue_ || inPersistence_) {
      queuePropertyFactory_.endElement(name);
      if(name.compare("queue") == 0) {
         inQueue_ = false;
         character_.erase();
         QueuePropertyBase tmp = queuePropertyFactory_.getQueueProperty();
         string relating = tmp.getRelating();
         TopicProperty tmpProp = msgQosDataP_->getTopicProperty();
         if (relating == Constants::RELATING_HISTORY) {
            tmpProp.setHistoryQueueProperty(tmp);
            msgQosDataP_->setTopicProperty(tmpProp);
         }
         else {
            log_.error(ME, string("Ignoring unknown <queue relating='") + relating + "'/> configuration");
         }
         return;
      }

      if(name.compare("persistence") == 0) { // topic: RELATING_MSGUNITSTORE
         inPersistence_ = false;
         character_.erase();
         QueuePropertyBase tmp = queuePropertyFactory_.getQueueProperty();
         TopicProperty tmpProp = msgQosDataP_->getTopicProperty();
         tmpProp.setMsgUnitStoreProperty(tmp);
         msgQosDataP_->setTopicProperty(tmpProp);
         return;
      }
   }

   if (name.compare("state") == 0) {
      inState_ = false;
      character_.erase();
      return;
   }

   if( name.compare("destination") == 0) {
      inDestination_ = false;
      StringTrim::trim(character_); // The address or XPath query string
      if (!character_.empty()) {
         destination_.getDestination()->setAbsoluteName(character_); // set address or XPath query string if it is before the forceQueuing tag
         character_.erase();
      }
      msgQosDataP_->addDestination(destination_);
      return;
   }

   if(name.compare("sender") == 0) {
      inSender_ = false;
      StringTrim::trim(character_);
      msgQosDataP_->getSender()->setAbsoluteName(character_);
      // if (log.trace()) log.trace(ME, "Found message sender login name = " + msgQosData.getSender());
      character_.erase();
      return;
   }

   if(name.compare("priority") == 0) {
      inPriority_ = false;
      msgQosDataP_->setPriority(str2Priority(character_));
      character_.erase();
      return;
   }

   if(name.compare("expiration") == 0) {
      inExpiration_ = false;
      character_.erase();
      return;
   }

   if(name.compare("rcvTimestamp") == 0) {
      inRcvTimestamp_ = false;
      character_.erase();
      return;
   }

   if(name.compare("forceUpdate") == 0) {
      inIsVolatile_ = false;
      msgQosDataP_->setForceUpdate(StringTrim::isTrueTrim(character_));
      character_.erase();
      return;
   }

   if (name.compare(MethodName::SUBSCRIBE) == 0) {
      inSubscribe_ = false;
      character_.erase();
      return;
   }

   if(name.compare("persistent") == 0) {
      inIsPersistent_ = false;
      msgQosDataP_->setPersistent(StringTrim::isTrueTrim(character_));
      character_.erase();
      return;
   }

   if(name.compare("readonly") == 0) {
      inReadonly_ = false;
      msgQosDataP_->setReadonly(StringTrim::isTrueTrim(character_));
      character_.erase();
      return;
   }

   if(name.compare("redeliver") == 0) {
      inRedeliver_ = false;
      StringTrim::trim(character_);
      msgQosDataP_->setRedeliver(atoi(character_.c_str()));
      character_.erase();
      return;
   }

   if (name.compare("node") == 0) {
      msgQosDataP_->addRouteInfo(routeInfo_);
      character_.erase();
      return;
   }

   if (name.compare("route") == 0) {
      inRoute_ = false;
      character_.erase();
      return;
   }

   if (name.compare("clientProperty") == 0) {
      inClientProperty_ = false;
      clientProperty_->setValueRaw(character_);
      msgQosDataP_->addClientProperty(*clientProperty_);
      delete clientProperty_;
      clientProperty_ = 0;
      character_.erase();
   }

   character_.erase(); // reset data from unknown tags
}


/** Configure if remaingLife is sent in Qos (redesign approach to work with all QoS attributes */
void MsgQosFactory::sendRemainingLife(bool sendRemainingLife) 
{ 
   sendRemainingLife_ = sendRemainingLife; 
}

bool MsgQosFactory::sendRemainingLife() 
{ 
   return sendRemainingLife_; 
}

}}}}


#ifdef _XMLBLASTER_CLASSTEST

using namespace std;
using namespace org::xmlBlaster::util::qos;

int main(int args, char* argv[])
{
    try
    {
       Global& glob = Global::getInstance();
       glob.initialize(args, argv);

       MsgQosData    data1(glob);
       MsgQosFactory factory(glob);
       string        qos   = data1.toXml();
       MsgQosData    data2 = factory.readObject(qos);

       cout << "data before parsing: " << data1.toXml() << endl;
       cout << "data after parsing : " << data2.toXml() << endl;
    }
    catch(...)  {
       cout << "exception occured\n";
       return 1;
    }
   return 0;
}

#endif

/*
 <qos>
    <state id='OK' info='Keep on running"/> <!-- Only for updates and PtP -->
    <sender>Tim</sender>
    <priority>5</priority>
    <subscribe id='__subId:1'/>     <!-- Only for updates, id='__subId:PtP' for point to point messages -->
    <rcvTimestamp nanos='1007764305862000002'> <!-- UTC time when message was created in xmlBlaster server with a publish() call, in nanoseconds since 1970 -->
          2001-12-07 23:31:45.862000002   <!-- The nanos from above but human readable -->
    </rcvTimestamp>
    <expiration lifeTime='129595811' forceDestroy='false'/> <!-- Only for persistence layer -->
    <queue index='0' of='1'/> <!-- If queued messages are flushed on login -->
    <persistent/>
    <redeliver>4</redeliver>             <!-- Only for updates -->
    <route>
       <node id='heron'/>
    </route>
    <topic readonly='false' destroyDelay='60000' createDomEntry='true'>
       <queue relating='topic' type='CACHE' version='1.0' maxEntries='1000' maxBytes='4000000' onOverflow='deadMessage'/>
       <queue relating='history' type='CACHE' version='1.0' maxEntries='1000' maxBytes='4000000' onOverflow='exception'/>
    </topic>
 </qos>




 <qos>
    <destination queryType='EXACT' forceQueuing='true'>
       Tim
    </destination>
    <destination queryType='EXACT'>
       /node/heron/client/Ben
    </destination>
    <destination queryType='XPATH'>   <!-- Not supported yet -->
       //[GROUP='Manager']
    </destination>
    <destination queryType='XPATH'>   <!-- Not supported yet -->
       //ROLE/[@id='Developer']
    </destination>
    <sender>
       Gesa
    </sender>
    <priority>7</priority>
    <route>
       <node id='bilbo' stratum='2' timestamp='34460239640' dirtyRead='true'/>
    </route>
 </qos>

*/



