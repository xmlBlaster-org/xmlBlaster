/*------------------------------------------------------------------------------
Name:      QosData.h
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
 * @author ruff@swand.lake.de
 * @author laghi@swissinfo.org
 */

#ifndef _UTIL_QOS_QOSDATA_H
#define _UTIL_QOS_QOSDATA_H

#include <util/xmlBlasterDef.h>
#include <util/cluster/RouteInfo.h>
#include <vector>
#include <map>
#include <string>

namespace org { namespace xmlBlaster { namespace util { namespace qos {

extern Dll_Export const bool DEFAULT_isSubscribable;
extern Dll_Export const bool DEFAULT_isVolatile;
extern Dll_Export const bool DEFAULT_persistent;
extern Dll_Export const bool DEFAULT_forceUpdate;
extern Dll_Export const bool DEFAULT_forceDestroy;

// extern Dll_Export const bool DEFAULT_readonly;

typedef std::vector<org::xmlBlaster::util::cluster::RouteInfo> RouteVector;

class Dll_Export QosData
{

private:
   void init();

protected:
   std::string  ME;
   org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::Log&    log_;
   std::string  serialData_;

   /** the state of the message, defaults to "OK" if no state is returned */
   std::string state_; // = Constants::STATE_OK;
   /** Human readable information */
   std::string stateInfo_;

   /** 
    * The receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In nanoseconds elapsed since midnight, January 1, 1970 UTC
    */
   Timestamp rcvTimestamp_;
   bool rcvTimestampFound_; // = false;

   /**
    * ArrayList containing org::xmlBlaster::util::cluster::RouteInfo objects
    */
   RouteVector routeNodeList_;

   typedef std::map<std::string, std::string> ClientPropertyMap;
   ClientPropertyMap clientProperties_;	

   void copy(const QosData& data);

   std::string dumpClientProperties(const std::string& extraOffset) const;

public:
   /**
    * Constructs the specialized quality of service object for a publish() or update() call.
    * @param The factory which knows how to serialize and parse me
    */
//   long size_;

   QosData(org::xmlBlaster::util::Global& global, const std::string& serialData="");

   QosData(const QosData& data);

   QosData& operator=(const QosData& data);

   virtual ~QosData();

   /**
    * @param state The state of an update message
    */
   void setState(const std::string& state);

   /**
    * Access state of message on update().
    * @return OK (Other values are not yet supported)
    */
   std::string getState() const;

   /**
    * @param state The human readable state text of an update message
    */
   void setStateInfo(const std::string& stateInfo);

   /**
    * Access state of message on update().
    * @return The human readable info text
    */
   std::string getStateInfo() const;

   /**
    * True if the message is OK on update(). 
    */
   bool isOk() const;

   /**
    * True if the message was erased by timer or by a
    * client invoking erase(). 
    */
   bool isErased() const;

   /**
    * True if a timeout on this message occurred. 
    * <p />
    * Timeouts are spanned by the publisher and thrown by xmlBlaster
    * on timeout to indicate for example
    * STALE messages or any other user problem domain specific event.
    */
   bool isTimeout() const;

   /**
    * True on cluster forward problems
    */
   bool isForwardError() const;

   /**
    * Adds a new route hop to the QoS of this message. 
    * The added routeInfo is assumed to be one stratum closer to the master
    * So we will rearrange the stratum here. The given stratum in routeInfo
    * is used to recalculate the other nodes as well.
    */
   void addRouteInfo(const org::xmlBlaster::util::cluster::RouteInfo& routeInfo);

   /**
    * Check if the message has already been at the given node (circulating message). 
    * @return How often the message has travelled the node already
    */
   int count(const org::xmlBlaster::util::cluster::NodeId& nodeId) const;

   /**
    * Check if the message has already been at the given node (circulating message). 
    * @return How often the message has travelled the node already
    */
   bool dirtyRead(org::xmlBlaster::util::cluster::NodeId nodeId) const;

   /**
    * The approximate receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In milliseconds elapsed since midnight, January 1, 1970 UTC
    */
   void setRcvTimestamp(Timestamp rcvTimestamp);

   /** 
    * The approximate receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In milliseconds elapsed since midnight, January 1, 1970 UTC
    */
   Timestamp getRcvTimestamp() const;

   /**
    * Set timestamp to current time.
    */
   void touchRcvTimestamp();

   void setClientProperty(const std::string& key, const std::string& value);
	
   const ClientPropertyMap& getClientProperties() const;

   /**
    * Dump state of this object into a XML ASCII std::string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the message QoS as a XML ASCII std::string
    */
   virtual std::string toXml(const std::string& extraOffset="") const = 0;

    // the following where not present before ...
   RouteVector getRouteNodes() const;

   void clearRoutes();

   int size() const;

   

   // copy constructor plus assignment operator ...

};

}}}}

#endif
