/*------------------------------------------------------------------------------
Name:      QosData.cpp
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

#include <util/qos/QosData.h>
#include <util/Constants.h>
#include <boost/lexical_cast.hpp>
#include <util/Global.h>

using boost::lexical_cast;

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::cluster;

using namespace std;

namespace org { namespace xmlBlaster { namespace util { namespace qos {
                                          
Dll_Export const bool DEFAULT_isSubscribeable = true;
Dll_Export const bool DEFAULT_isVolatile   = false;
Dll_Export const bool DEFAULT_isDurable    = false;
Dll_Export const bool DEFAULT_forceUpdate  = true;
Dll_Export const bool DEFAULT_forceDestroy = false;

   void QosData::init()
   {
      state_ = Constants::STATE_OK;
      stateInfo_ = "";
      rcvTimestamp_ = 0;
      rcvTimestampFound_ = false;
//      size_ = 0;
      serialData_ = "";
   }

   void QosData::copy(const QosData& data)
   {
      state_ = data.state_;
      stateInfo_ = data.stateInfo_;
      rcvTimestamp_ = data.rcvTimestamp_;
      rcvTimestampFound_ = data.rcvTimestampFound_;
  //    size_ = data.size_;
      serialData_ = data.serialData_;
   }


   QosData::QosData(Global& global, const string& serialData)
      : ME("QosData"),
        global_(global),
        log_(global.getLog("core")),
        routeNodeList_()
   {
      init();
      serialData_ = serialData;

   }


   QosData::QosData(const QosData& data)
      : ME(data.ME),
        global_(data.global_),
        log_(data.log_),
//        sender_(data.sender_),
//        destinationList_(data.destinationList_),
//        destination_(data.destination_),
        routeNodeList_(data.routeNodeList_)
   {
      copy(data);
   }

   QosData& QosData::operator=(const QosData& data)
   {
      copy(data);
      return *this;
   }


   QosData::~QosData()
   {
   }

   void QosData::setState(const string& state)
   {
      state_ = state;
   }

   string QosData::getState() const
   {
      return state_;
   }

   /**
    * @param state The human readable state text of an update message
    */
   void QosData::setStateInfo(const string& stateInfo)
   {
      stateInfo_ = stateInfo;
   }

   /**
    * Access state of message on update().
    * @return The human readable info text
    */
   string QosData::getStateInfo() const
   {
      return stateInfo_;
   }

   /**
    * True if the message is OK on update(). 
    */
   bool QosData::isOk() const
   {
      return Constants::STATE_OK == state_;
   }

   /**
    * True if the message was erased by timer or by a
    * client invoking erase(). 
    */
   bool QosData::isErased() const
   {
      return Constants::STATE_ERASED == state_;
   }

   /**
    * True if a timeout on this message occurred. 
    * <p />
    * Timeouts are spanned by the publisher and thrown by xmlBlaster
    * on timeout to indicate for example
    * STALE messages or any other user problem domain specific event.
    */
   bool QosData::isTimeout() const
   {
      return Constants::STATE_TIMEOUT == state_;
   }

   /**
    * True on cluster forward problems
    */
   bool QosData::isForwardError() const
   {
      return Constants::STATE_FORWARD_ERROR == state_;
   }

   /**
    * Adds a new route hop to the QoS of this message. 
    * The added routeInfo is assumed to be one stratum closer to the master
    * So we will rearrange the stratum here. The given stratum in routeInfo
    * is used to recalculate the other nodes as well.
    */
   void QosData::addRouteInfo(const RouteInfo& routeInfo)
   {
      routeNodeList_.insert(routeNodeList_.end(), routeInfo);

      // Set stratum to new values
      int offset = routeInfo.getStratum();
      if (offset < 0) offset = 0;

      vector<RouteInfo>::reverse_iterator iter = routeNodeList_.rbegin();
      while (iter != routeNodeList_.rend()) {
         (*iter).setStratum(offset++);
         iter++;
      }
   }

   /**
    * Check if the message has already been at the given node (circulating message). 
    * @return How often the message has travelled the node already
    */
   int QosData::count(const NodeId& nodeId) const
   {
      int cnt = 0;
      if (routeNodeList_.empty()) return cnt;
      vector<RouteInfo>::const_iterator iter = routeNodeList_.begin();
      while (iter != routeNodeList_.end()) {
         if ((*iter).getNodeId() == nodeId) cnt++;
         iter++;
      }
      return cnt;
   }

   /**
    * Check if the message has already been at the given node (circulating message). 
    * @return How often the message has travelled the node already
    */
   bool QosData::dirtyRead(NodeId nodeId) const
   {
      if (routeNodeList_.empty()) return false;
      vector<RouteInfo>::const_iterator iter = routeNodeList_.begin();
      while (iter != routeNodeList_.end()) {
         if ((*iter).getNodeId() == nodeId) return (*iter).getDirtyRead();
      }
      return false;
   }

   /**
    * The approximate receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In milliseconds elapsed since midnight, January 1, 1970 UTC
    */
   void QosData::setRcvTimestamp(Timestamp rcvTimestamp)
   {
      rcvTimestamp_ = rcvTimestamp;
   }

   /** 
    * The approximate receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In milliseconds elapsed since midnight, January 1, 1970 UTC
    */
   Timestamp QosData::getRcvTimestamp() const
   {
      return rcvTimestamp_;
   }

   /**
    * Set timestamp to current time.
    */
   void QosData::touchRcvTimestamp()
   {
      rcvTimestamp_ = TimestampFactory::getInstance().getTimestamp();
   }

   /**
    * @return never null, but may have length==0
    */
   RouteVector QosData::getRouteNodes() const
   {
      return routeNodeList_;
   }

   void QosData::clearRoutes()
   {
      routeNodeList_.erase(routeNodeList_.begin(), routeNodeList_.end());
   }

   int QosData::size() const
   {
      return toXml().size();
   }

}}}}

