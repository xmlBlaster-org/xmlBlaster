/*------------------------------------------------------------------------------
Name:      SubscribeQos.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * This class encapsulates the QoS (quality of service) of a subscribe() request. 
 * <p />
 * A full specified <b>subscribe</b> qos could look like this:<br />
 * <pre>
 *&lt;qos>
 *   &lt;id>__subId:/node/heron/client/joe/3/34&lt;/id> &lt; Force a subscription ID from client side -->
 *   &lt;meta>false&lt;/meta>       &lt;!-- Don't send me the xmlKey meta data on updates -->
 *   &lt;content>false&lt;/content> &lt;!-- Don't send me the content data on updates (notify only) -->
 *   &lt;local>false&lt;/local>     &lt;!-- Inhibit the delivery of messages to myself if i have published it -->
 *   &lt;initialUpdate>false&lt;/initialUpdate>;
 *   &lt;filter type='myPlugin' version='1.0'>a!=100&lt;/filter>
 *                                  &lt;!-- Filters messages i have subscribed as implemented in your plugin -->
 *   &lt;history numEntries='20'/>  &lt;!-- Default is to deliver the current entry (numEntries='1'), '-1' deliver all -->
 *&lt;/qos>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 * @see org.xmlBlaster.util.qos.QueryQosData
 * @see org.xmlBlaster.util.qos.QueryQosSaxFactory
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.subscribe.html">subscribe interface</a>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/mime.plugin.accessfilter.html">MIME access filter requirement</a>
 */

#ifndef _CLIENT_QOS_SUBSCRIBEQOS_H
#define _CLIENT_QOS_SUBSCRIBEQOS_H

#include <util/xmlBlasterDef.h>
#include <client/qos/GetQos.h>

using namespace std;
// using namespace org::xmlBlaster::util; <-- VC CRASH
// using namespace org::xmlBlaster::util::qos; <-- VC CRASH

namespace org { namespace xmlBlaster { namespace client { namespace qos {

class Dll_Export SubscribeQos : public GetQos
{
public:
   SubscribeQos(Global& global);

   SubscribeQos(Global& global, const QueryQosData& data);

   SubscribeQos(const SubscribeQos& qos);

   SubscribeQos& operator =(const SubscribeQos& qos);

   /**
    * Do we want to have an initial update on subscribe if the message
    * exists already?
    *
    * @return true if initial update wanted
    *         false if only updates on new publishes are sent
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.subscribe.initialUpdate.html">The engine.qos.subscribe.initialUpdate requirement</a>
    */
   void setWantInitialUpdate(bool initialUpdate);
   /**
    * false Inhibit the delivery of messages to myself if i have published it.
    */
   void setWantLocal(bool local);

   /**
    * Force the identifier (unique handle) for this subscription. 
    * Usually you let the identifier be generated by xmlBlaster.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.subscribe.id.html">The engine.qos.subscribe.id requirement</a>
    */
   void setSubscriptionId(const string& subscriptionId);

};

}}}} // namespace

#endif

