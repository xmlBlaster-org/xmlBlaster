/*------------------------------------------------------------------------------
Name:      SubscribeQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * This class encapsulates the QoS of an subscribe() request. 
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 * @see org.xmlBlaster.util.qos.QueryQosData
 * @see org.xmlBlaster.util.qos.QueryQosSaxFactory
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.subscribe.html">subscribe interface</a>
 */

#include <client/qos/SubscribeQos.h>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;

namespace org { namespace xmlBlaster { namespace client { namespace qos {

SubscribeQos::SubscribeQos(Global& global) : GetQos(global)
{
   ME = "SubscribeQos";
}

SubscribeQos::SubscribeQos(Global& global, const QueryQosData& data)
   : GetQos(global, data)
{
   ME = "SubscribeQos";
}

SubscribeQos::SubscribeQos(const SubscribeQos& qos) : GetQos(qos)
{
}

SubscribeQos& SubscribeQos::operator =(const SubscribeQos& qos)
{
   data_ = qos.data_;
   return *this;
}


/**
 * Do we want to have an initial update on subscribe if the message
 * exists already?
 *
 * @return true if initial update wanted
 *         false if only updates on new publishes are sent
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.subscribe.initialUpdate.html">The engine.qos.subscribe.initialUpdate requirement</a>
 */
void SubscribeQos::setWantInitialUpdate(bool initialUpdate)
{
   data_.setWantInitialUpdate(initialUpdate);
}

/**
 * Do we want the callback messages of this subscription as oneway with <tt>updateOneway()</tt> or with
 * the acknowledged <tt>update()</tt>. 
 * @param updateOneway Defaults to false. 
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html">The interface.subscribe requirement</a>
 * @see QueryQosData#setWantUpdateOneway(boolean)
 */
void SubscribeQos::setWantUpdateOneway(bool updateOneway)
{
   data_.setWantUpdateOneway(updateOneway);
}

/**
 * Are multiple subscribes allowed?
 * Defaults to true. 
 * @return true Multiple subscribes deliver multiple updates
 *         false Ignore more than one subscribes on same oid
 */
void SubscribeQos::setMultiSubscribe(bool multiSubscribe)
{
   data_.setMultiSubscribe(multiSubscribe);
}

/**
 * false Inhibit the delivery of messages to myself if i have published it.
 */
void SubscribeQos::setWantLocal(bool local)
{
   data_.setWantLocal(local);
}

void SubscribeQos::setWantNotify(bool notifyOnErase)
{
   data_.setWantNotify(notifyOnErase);
}

/**
 * Force the identifier (unique handle) for this subscription. 
 * Usually you let the identifier be generated by xmlBlaster.
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.subscribe.id.html">The engine.qos.subscribe.id requirement</a>
 */
void SubscribeQos::setSubscriptionId(const string& subscriptionId)
{
   data_.setSubscriptionId(subscriptionId);
}

void SubscribeQos::setPersistent(bool persistent) {
   data_.setPersistent(persistent);
}

}}}} // namespace

