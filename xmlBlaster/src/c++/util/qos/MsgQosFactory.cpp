/*------------------------------------------------------------------------------
Name:      MsgQosSaxFactory.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/qos/MsgQosFactory.h>
#include <util/Global.h>
#include <boost/lexical_cast.hpp>

using boost::lexical_cast;

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::cluster;
using namespace org::xmlBlaster::util::qos::storage;
using namespace std;

namespace org { namespace xmlBlaster { namespace util { namespace qos {

MsgQosFactory::MsgQosFactory(Global& global)
   : SaxHandlerBase(global), 
     ME("MsgQosFactory"),
     msgQosData_(global), 
     destination_(global), 
     routeInfo_(global),
     queuePropertyFactory_(global)
{
   ME                 = string("MsgQosFactory");
   LIFE_TIME          = XMLString::transcode("lifeTime");
   FORCE_DESTROY      = XMLString::transcode("forceDestroy");
   REMAINING_LIFE     = XMLString::transcode("remainingLife");
   READ_ONLY          = XMLString::transcode("readOnly");
   DESTROY_DELAY      = XMLString::transcode("destroyDelay");
   CREATE_DOM_ENTRY   = XMLString::transcode("createDomEntry");
   NANOS              = XMLString::transcode("nanos");
   ID                 = XMLString::transcode("id");
   STRATUM            = XMLString::transcode("stratum");
   TIMESTAMP          = XMLString::transcode("timestamp");
   DIRTY_READ         = XMLString::transcode("dirtyRead");
   INDEX              = XMLString::transcode("index");
   SIZE               = XMLString::transcode("size");
   inState_           = false;
   inSubscribe_       = false;
   inRedeliver_       = false;
   inTopic_           = false;
   inQueue_           = false;
   inMsgstore_        = false;
   inDestination_     = false;
   inSender_          = false;
   inPriority_        = false;
   inExpiration_      = false;
   inRcvTimestamp_    = false;
   inIsVolatile_      = false;
   inIsDurable_       = false;
   inReadonly_        = false;
   inRoute_           = false;
   sendRemainingLife_ = true;
   inQos_             = false;
}

MsgQosFactory::~MsgQosFactory() 
{
   delete LIFE_TIME;
   delete FORCE_DESTROY;
   delete REMAINING_LIFE;
   delete READ_ONLY;
   delete DESTROY_DELAY;
   delete CREATE_DOM_ENTRY;
   delete NANOS;
   delete ID;
   delete STRATUM;
   delete TIMESTAMP;
   delete DIRTY_READ;
   delete INDEX;
   delete SIZE;
}                

MsgQosData MsgQosFactory::readObject(const string& xmlQos)
{
   if (xmlQos.empty()) init("<qos/>");
   else init(xmlQos);
   return msgQosData_;
}

void MsgQosFactory::startElement(const XMLCh* const name, AttributeList& attrs)
{
   bool      tmpBool;
//      int       tmpInt;
   string    tmpString;
   long      tmpLong;
   Timestamp tmpTimestamp;
   if (SaxHandlerBase::caseCompare(name, "qos")) {
     msgQosData_ = MsgQosData(global_); // kind of reset
     inQos_ = true;
     return;
   }
   if ( SaxHandlerBase::caseCompare(name, "queue") || inQueue_) {
      if (!inQos_) return;
      inQueue_ = true;
      // get the index and the size in the queue
      if (getLongAttr(attrs, INDEX, tmpLong)) msgQosData_.setQueueIndex(tmpLong);
      if (getLongAttr(attrs, SIZE, tmpLong)) msgQosData_.setQueueSize(tmpLong);

      queuePropertyFactory_.startElement(name, attrs);
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "msgstore") || inMsgstore_) {
      if (!inQos_) return;
      inMsgstore_ = true;
      queuePropertyFactory_.startElement(name, attrs);
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "state")) {
      if (!inQos_) return;
      inState_ = true;
      int len = attrs.getLength();
      for (int i = 0; i < len; i++) {
         if (SaxHandlerBase::caseCompare(attrs.getName(i), "id")) {
            msgQosData_.setState(SaxHandlerBase::getStringValue(attrs.getValue(i)));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(i), "info")) {
            msgQosData_.setStateInfo(SaxHandlerBase::getStringValue(attrs.getValue(i)));
         }
      }
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "destination")) {
      if (!inQos_) return;
      inDestination_ = true;
      destination_ = Destination(global_);
      int len = attrs.getLength();

      for (int i = 0; i < len; i++)   {
         if (SaxHandlerBase::caseCompare(attrs.getName(i), "queryType")) {
            string queryType = SaxHandlerBase::getStringValue(attrs.getValue(i));
            if (queryType == "EXACT")      destination_.setQueryType(queryType);
            else if (queryType == "XPATH") destination_.setQueryType(queryType);
            else log_.error(ME, string("Sorry, destination queryType='") + queryType + "' is not supported");
         }
         else if( SaxHandlerBase::caseCompare(attrs.getName(i), "forceQueuing") ) {
            destination_.forceQueuing(SaxHandlerBase::getBoolValue(attrs.getValue(i)));
         }
      }
      char* help = charTrimmer_.trim(character_.c_str());
      if (help) {
         destination_.setDestination(SessionQos(global_, help)); // set address or XPath query string if it is before inner tags
         character_.erase();
         delete help;
      }
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "sender")) {
      if (!inQos_) return;
      inSender_ = true;
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "priority")) {
      if (!inQos_) return;
      inPriority_ = true;
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "expiration")) {
      if (!inQos_) return;
      inExpiration_ = true;
//         int len = attrs.getLength();
      if (getLongAttr(attrs, LIFE_TIME, tmpLong)) msgQosData_.setLifeTime(tmpLong);
      else {
         log_.warn(ME, string("QoS <expiration> misses lifeTime attribute, setting default of ") + lexical_cast<string>(msgQosData_.getMaxLifeTime()));
         msgQosData_.setLifeTime(msgQosData_.getMaxLifeTime());
      }
      if (getBoolAttr(attrs, FORCE_DESTROY, tmpBool)) msgQosData_.setForceDestroy(tmpBool);
      if (getLongAttr(attrs, REMAINING_LIFE, tmpLong)) msgQosData_.setRemainingLifeStatic(tmpLong);
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "topic")) {
      if (!inQos_) return;
      inTopic_ = true;
      TopicProperty tmpProp(global_);
      if (getBoolAttr(attrs, READ_ONLY, tmpBool)) tmpProp.setReadonly(tmpBool);
      if (getLongAttr(attrs, DESTROY_DELAY, tmpLong)) tmpProp.setDestroyDelay(tmpLong);
//       if (getBoolAttr(attrs, CREATE_DOM_ENTRY, tmpBool)) tmpProp.setCreateDomEntry(tmpBool);
      msgQosData_.setTopicProperty(tmpProp);
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "rcvTimestamp")) {
      if (!inQos_) return;
      if (getTimestampAttr(attrs, NANOS, tmpTimestamp)) msgQosData_.setRcvTimestamp(tmpTimestamp);
      inRcvTimestamp_ = true;
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "redeliver")) {
      if (!inQos_) return;
      inRedeliver_ = true;
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "route")) {
      if (!inQos_) return;
      inRoute_ = true;
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "node")) {
      if (!inRoute_) {
         log_.error(ME, "Ignoring <node>, it is not inside <route>");
         return;
      }
      if (attrs.getLength() > 0) {
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
         if (log_.TRACE) log_.trace(ME, "Found node tag");
         routeInfo_ = RouteInfo(nodeId, stratum, timestamp);
         if (getBoolAttr(attrs, DIRTY_READ, tmpBool)) routeInfo_.setDirtyRead(tmpBool);
      }
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "subscribe")) {
      if (!inQos_) return;
      inSubscribe_ = true;

      int len = attrs.getLength();
      for (int i = 0; i < len; i++) {
         if (SaxHandlerBase::caseCompare(attrs.getName(i), "id")) {
            msgQosData_.setSubscriptionId(SaxHandlerBase::getStringValue(attrs.getValue(i)));
         }
      }
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "isVolatile")) { // deprecated
      if (!inQos_) return;
      inIsVolatile_ = true;
      return;
   }
   if (SaxHandlerBase::caseCompare(name, "isDurable")) {
      if (!inQos_) return;
      msgQosData_.setDurable(true);
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "forceUpdate")) {
      if (!inQos_) return;
      msgQosData_.setForceUpdate(true);
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "readonly")) {
      if (!inQos_) return;
      msgQosData_.setReadonly(true);
      log_.error(ME, "<qos><readonly/></qos> is deprecated, please use readonly as topic attribute <qos><topic readonly='true'></qos>");
      return;
   }
}


void MsgQosFactory::characters(const XMLCh* const ch, const unsigned int length) 
{
   SaxHandlerBase::characters(ch, length);
   if (inQueue_ || inMsgstore_) queuePropertyFactory_.characters(ch, length);
}


void MsgQosFactory::endElement(const XMLCh* const name) 
{
   if (SaxHandlerBase::caseCompare(name, "qos")) {
      inQos_ = false;
      return;
   }
   if (inQueue_ || inMsgstore_) {
      queuePropertyFactory_.endElement(name);
      if(SaxHandlerBase::caseCompare(name, "queue")) {
         inQueue_ = false;
         character_.erase();
         QueuePropertyBase tmp = queuePropertyFactory_.getQueueProperty();
         string relating = tmp.getRelating();
         TopicProperty tmpProp = msgQosData_.getTopicProperty();
         if (relating == Constants::RELATING_HISTORY) {
            tmpProp.setHistoryQueueProperty(tmp);
            msgQosData_.setTopicProperty(tmpProp);
         }
         else if (relating == Constants::RELATING_TOPICCACHE) {
            tmpProp.setTopicCacheProperty(tmp);
            msgQosData_.setTopicProperty(tmpProp);
         }
         return;
      }

      if(SaxHandlerBase::caseCompare(name, "msgstore")) {
         inMsgstore_ = false;
         character_.erase();
         QueuePropertyBase tmp = queuePropertyFactory_.getQueueProperty();
         TopicProperty tmpProp = msgQosData_.getTopicProperty();
         tmpProp.setTopicCacheProperty(tmp);
         msgQosData_.setTopicProperty(tmpProp);
         return;
      }
   }

   if (SaxHandlerBase::caseCompare(name, "state")) {
      inState_ = false;
      character_.erase();
      return;
   }

   if( SaxHandlerBase::caseCompare(name, "destination") ) {
      inDestination_ = false;
      string tmp = stringTrim(character_); // The address or XPath query string
      if (!tmp.empty()) {
         destination_.setDestination(SessionQos(global_, tmp)); // set address or XPath query string if it is before the forceQueuing tag
         character_.erase();
      }
      msgQosData_.addDestination(destination_);
      return;
   }

   if(SaxHandlerBase::caseCompare(name, "sender")) {
      inSender_ = false;
      msgQosData_.setSender(SessionQos(global_, stringTrim(character_)));
      // if (log.TRACE) log.trace(ME, "Found message sender login name = " + msgQosData.getSender());
      character_.erase();
      return;
   }

   if(SaxHandlerBase::caseCompare(name, "priority")) {
      inPriority_ = false;
      int prio = atoi(character_.c_str());
      msgQosData_.setPriority(int2Priority(prio));
      character_.erase();
      return;
   }

   if(SaxHandlerBase::caseCompare(name, "expiration")) {
      inExpiration_ = false;
      character_.erase();
      return;
   }
   if(SaxHandlerBase::caseCompare(name, "topic")) {
      inTopic_ = false;
      character_.erase();
      return;
   }

   if(SaxHandlerBase::caseCompare(name, "rcvTimestamp")) {
      inRcvTimestamp_ = false;
      character_.erase();
      return;
   }

   if(SaxHandlerBase::caseCompare(name, "forceUpdate")) {
      inIsVolatile_ = false;
      string tmp = stringTrim(character_);
      if (!tmp.empty()) {
         if (tmp == "true") msgQosData_.setForceUpdate(true);
         else msgQosData_.setForceUpdate(false);
      }
      // if (log.TRACE) log.trace(ME, "Found forceUpdate = " + msgQosData.getForceUpdate());
      character_.erase();
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "subscribe")) {
      inSubscribe_ = false;
      character_.erase();
      return;
   }

   if(SaxHandlerBase::caseCompare(name, "isVolatile")) { // deprecated
      inIsVolatile_ = false;
      string tmp = stringTrim(character_);
      if (!tmp.empty()) {
         if (tmp == "true") msgQosData_.setVolatile(true);
         else  msgQosData_.setVolatile(false);
         if (msgQosData_.isVolatile())  {
            log_.warn(ME, string("Found 'isVolatile=") + global_.getBoolAsString(msgQosData_.isVolatile()) + "' which is deprecated, use lifeTime==0&&forceDestroy==false instead");
         }
      }
      character_.erase();
      return;
   }

   if(SaxHandlerBase::caseCompare(name, "isDurable")) {
      inIsDurable_ = false;
      string tmp = stringTrim(character_);
      if (!tmp.empty())
         if (tmp == "true") msgQosData_.setDurable(true);
         else  msgQosData_.setDurable(false);
      // if (log.TRACE) log.trace(ME, "Found isDurable = " + msgQosData.getIsDurable());
      character_.erase();
      return;
   }

   if(SaxHandlerBase::caseCompare(name, "readonly")) {
      inReadonly_ = false;
      string tmp = stringTrim(character_);
      if (!tmp.empty())
         if (tmp == "true") msgQosData_.setReadonly(true);
         else  msgQosData_.setReadonly(false);
      // if (log.TRACE) log.trace(ME, "Found readonly = " + msgQosData.readonly());
      character_.erase();
      return;
   }

   if(SaxHandlerBase::caseCompare(name, "redeliver")) {
      inRedeliver_ = false;
      string tmp = stringTrim(character_);
      msgQosData_.setRedeliver(atoi(tmp.c_str()));
      character_.erase();
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "node")) {
      msgQosData_.addRouteInfo(routeInfo_);
      character_.erase();
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "route")) {
      inRoute_ = false;
      character_.erase();
      return;
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

#include <util/PlatformUtils.hpp>

using namespace std;
using namespace org::xmlBlaster::util::qos;

int main(int args, char* argv[])
{
    // Init the XML platform
    try
    {
       XMLPlatformUtils::Initialize();

       Global& glob = Global::getInstance();
       glob.initialize(args, argv);

       MsgQosData    data1(glob);
       MsgQosFactory factory(glob);
       string        qos   = data1.toXml();
       MsgQosData    data2 = factory.readObject(qos);

       cout << "data before parsing: " << data1.toXml() << endl;
       cout << "data after parsing : " << data2.toXml() << endl;
    }
    catch(const XMLException& toCatch)  {
       cout << "Error during platform init! Message:\n";
       cout <<toCatch.getMessage() << endl;
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
    <isDurable/>
    <redeliver>4</redeliver>             <!-- Only for updates -->
    <route>
       <node id='heron'/>
    </route>
    <topic readonly='false' destroyDelay='60000' createDomEntry='true'>
       <queue relating='topic' type='CACHE' version='1.0' maxMsg='1000' maxBytes='4000000' onOverflow='deadMessage'/>
       <queue relating='history' type='CACHE' version='1.0' maxMsg='1000' maxBytes='4000000' onOverflow='exception'/>
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



