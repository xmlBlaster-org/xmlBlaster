/*------------------------------------------------------------------------------
Name:      QueryQosData.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Data container handling of query / access QoS. 
 * <p>
 * This data holder is accessible through decorators, each of them allowing a specialized view on the data:
 * </p>
 * <ul>
 * <li>SubscribeQos QoS of a subscribe() invocation (Client side)</i>
 * <li>UnSubscribeQos QoS of a unSubscribe() invocation (Client side)</i>
 * <li>EraseQos QoS of an erase() invocation (Client side)</i>
 * <li>GetQos QoS of an get() invocation (Client side)</i>
 * </ul>
 * <p>
 * For the xml representation see QueryQosSaxFactory.
 * </p>
 * @see org.xmlBlaster.util.qos.QueryQosSaxFactory
 * @see org.xmlBlaster.test.classtest.qos.QueryQosFactoryTest
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */

#ifndef _UTIL_QOS_QUERYQOSDATA_H
#define _UTIL_QOS_QUERYQOSDATA_H

#include <util/xmlBlasterDef.h>
#include <util/qos/QosData.h>
#include <util/qos/AccessFilterQos.h>
#include <util/qos/HistoryQos.h>
#include <vector>
#include <string>






namespace org { namespace xmlBlaster { namespace util { namespace qos {

typedef std::vector<org::xmlBlaster::util::qos::AccessFilterQos> AccessFilterVector;

class Dll_Export QueryQosData : public QosData
{
   /** A client can force a specific subscription id */
   std::string subscriptionId_;

   /** On erase forceDestroy */
   bool forceDestroy_;

   /** not yet supported */
   bool meta_;

   /** not yet supported */
   bool content_;

   bool multiSubscribe_;

   bool local_;

   /** send on subscribe an initial update with the current message */
   bool initialUpdate_;

   /** Deliver callback messages with <tt>updateOneway()</tt> */
   bool updateOneway_;

   /** for subscribe(): Notify the subscribers on erase? */
   bool notify_;

   /** Mime based filter rules */
   AccessFilterVector filters_;

   /** Query history messages */
   org::xmlBlaster::util::qos::HistoryQos historyQos_;

   void init();

   void copy(const QueryQosData& data);

public:

   /**
    * Constructs the specialized quality of service object for query informations,
    * e.g. for a subscribe() call
    * @param The factory which knows how to serialize and parse me
    */
   QueryQosData(org::xmlBlaster::util::Global& global);

   void setMultiSubscribe(bool multiSubscribe);

   /**
    * Allow to subscribe multiple times to the same query/oid
    * @return defaults to true
    */
   bool getMultiSubscribe() const;

   /**
    * Do we want to have an initial update on subscribe if the message
    * exists already?
    *
    * @return true if initial update wanted
    *         false if only updates on new publishes are sent
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.subscribe.initialUpdate.html">The engine.qos.subscribe.initialUpdate requirement</a>
    */
   void setWantInitialUpdate(bool initialUpdate);

   bool getWantInitialUpdate() const;

   /**
    * Do we want the callback messages of this subscription as oneway with <tt>updateOneway()</tt> or with
    * the acknowledged <tt>update()</tt>. 
    * @param updateOneway Defaults to false. 
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html">The interface.subscribe requirement</a>
    * @see QueryQosData#setWantUpdateOneway(boolean)
    */
   void setWantUpdateOneway(bool updateOneway);

   bool getWantUpdateOneway() const;

   void setWantNotify(bool notify);

   bool getWantNotify() const;

   /**
    * false Inhibit the delivery of messages to myself if i have published it.
    */
   void setWantLocal(bool local);

   /**
    * false Inhibit the delivery of messages to myself if i have published it.
    */
   bool getWantLocal() const;

   /**
    * @param meta false: Don't send me the meta information of a message key
    */
   void setWantMeta(bool meta);

   /**
    * @return false: Don't send me the meta information of a message key
    */
   bool getWantMeta() const;

   /**
    * If false, the update contains not the content (it is a notify of change only)
    * TODO: Implement in server!!!
    */
   void setWantContent(bool content);

   bool getWantContent() const;

   /**
    * @param forceDestroy
    */
   void setForceDestroy(bool forceDestroy);

   /**
    * @return for erase behaviour
    */
   bool getForceDestroy() const;

   /**
    * Adds your subplied subscribe filter
    */
   void addAccessFilter(const org::xmlBlaster::util::qos::AccessFilterQos& filter);

   AccessFilterVector getAccessFilters() const;

   /**
    * Set the QoS which describes the history query settings. 
    */
   void setHistoryQos(const org::xmlBlaster::util::qos::HistoryQos& historyQos);

   /**
    * Get the QoS which describes the history query settings. 
    * @return never null
    */
   org::xmlBlaster::util::qos::HistoryQos getHistoryQos() const;

   /**
    * Get the identifier (unique handle) for this subscription. 
    * @return The id or null if not specified by client.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.subscribe.id.html">The engine.qos.subscribe.id requirement</a>
    */
   std::string getSubscriptionId() const;

   /**
    * Force the identifier (unique handle) for this subscription. 
    * Usually you let the identifier be generated by xmlBlaster.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.subscribe.id.html">The engine.qos.subscribe.id requirement</a>
    */
   void setSubscriptionId(const std::string& subscriptionId);

   /**
    * Dump state of this object into a XML ASCII std::string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the query as a XML ASCII std::string
    */
   std::string toXml(const std::string& extraOffset="") const;

   /**
    * Allocate a clone. 
    * @return The caller needs to free it with 'delete'.
    */
   QueryQosData* getClone() const;
};

}}}} // namespace

#endif


