/*------------------------------------------------------------------------------
Name:      QueryQosData.cpp
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

#include <util/qos/QueryQosData.h>
# include <util/Constants.h>
# include <util/Global.h>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::cluster;

using namespace std;

namespace org { namespace xmlBlaster { namespace util { namespace qos {

void QueryQosData::init() 
{
//   QosData::init();
   subscriptionId_ = "";
   forceDestroy_   = false;
   meta_           = true;
   content_        = true;
   multiSubscribe_ = true;
   local_          = true;
   initialUpdate_  = true;
   updateOneway_   = false;
   notify_         = true;
}

void QueryQosData::copy(const QueryQosData& data) 
{
   QosData::copy(data);
   subscriptionId_ = data.subscriptionId_;
   forceDestroy_   = data.forceDestroy_;
   meta_           = data.meta_;
   content_        = data.content_;
   multiSubscribe_ = data.multiSubscribe_;
   local_          = data.local_;
   initialUpdate_  = data.initialUpdate_;
   updateOneway_   = data.updateOneway_;
   notify_         = data.notify_;
}


/**
 * Constructs the specialized quality of service object for query informations,
 * e.g. for a subscribe() call
 * @param The factory which knows how to serialize and parse me
 */
QueryQosData::QueryQosData(Global& global)
   : QosData(global),
      forceDestroy_(false),
      meta_(true), 
      content_(true),
      multiSubscribe_(true),
      local_(true),
      initialUpdate_(true),
      updateOneway_(false),
      notify_(true),
      filters_(),
      historyQos_(global)
{
   ME = "QueryQosData";
}


/**
 * Do we want to have an initial update on subscribe if the message
 * exists already?
 *
 * @return true if initial update wanted
 *         false if only updates on new publishes are sent
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.subscribe.initialUpdate.html">The engine.qos.subscribe.initialUpdate requirement</a>
 */
void QueryQosData::setWantInitialUpdate(bool initialUpdate)
{
   initialUpdate_ = initialUpdate;
}

bool QueryQosData::getWantInitialUpdate() const
{
   return initialUpdate_;
}

/**
 * Do we want the callback messages of this subscription as oneway with <tt>updateOneway()</tt> or with
 * the acknowledged <tt>update()</tt>. 
 * @param updateOneway Defaults to false. 
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html">The interface.subscribe requirement</a>
 * @see QueryQosData#setWantUpdateOneway(boolean)
 */
void QueryQosData::setWantUpdateOneway(bool updateOneway)
{
   updateOneway_ = updateOneway;
}

bool QueryQosData::getWantUpdateOneway() const
{
   return updateOneway_;
}

void QueryQosData::setWantNotify(bool notify)
{
   notify_ = notify;
}

bool QueryQosData::getWantNotify() const
{
   return notify_;
}

void QueryQosData::setMultiSubscribe(bool multiSubscribe)
{
   multiSubscribe_ = multiSubscribe;
}

bool QueryQosData::getMultiSubscribe() const
{
   return multiSubscribe_;
}

/**
 * false Inhibit the delivery of messages to myself if i have published it.
 */
void QueryQosData::setWantLocal(bool local)
{
   local_ = local;
}

/**
 * false Inhibit the delivery of messages to myself if i have published it.
 */
bool QueryQosData::getWantLocal() const
{
   return local_;
}

/**
 * @param meta false: Don't send me the meta information of a message key
 */
void QueryQosData::setWantMeta(bool meta)
{
   meta_ = meta;
}

/**
 * @return false: Don't send me the meta information of a message key
 */
bool QueryQosData::getWantMeta() const
{
   return meta_;
}

/**
 * If false, the update contains not the content (it is a notify of change only)
 * TODO: Implement in server!!!
 */
void QueryQosData::setWantContent(bool content)
{
   content_ = content;
}

bool QueryQosData::getWantContent() const
{
   return content_;
}

/**
 * @param forceDestroy
 */
void QueryQosData::setForceDestroy(bool forceDestroy)
{
   forceDestroy_ = forceDestroy;
}

/**
 * @return for erase behaviour
 */
bool QueryQosData::getForceDestroy() const
{
   return forceDestroy_;
}

/**
 * Adds your subplied subscribe filter
 */
void QueryQosData::addAccessFilter(const AccessFilterQos& filter)
{
   filters_.insert(filters_.begin(), filter);
}

AccessFilterVector QueryQosData::getAccessFilters() const
{
   return filters_;
}


/**
 * Set the QoS which describes the history query settings. 
 */
void QueryQosData::setHistoryQos(const HistoryQos& historyQos)
{
   historyQos_ = historyQos;
}

/**
 * Get the QoS which describes the history query settings. 
 * @return never null
 */
HistoryQos QueryQosData::getHistoryQos() const
{
   return historyQos_;
}

/**
 * Get the identifier (unique handle) for this subscription. 
 * @return The id or null if not specified by client.
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.subscribe.id.html">The engine.qos.subscribe.id requirement</a>
 */
string QueryQosData::getSubscriptionId() const
{
   return subscriptionId_;
}

/**
 * Force the identifier (unique handle) for this subscription. 
 * Usually you let the identifier be generated by xmlBlaster.
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.subscribe.id.html">The engine.qos.subscribe.id requirement</a>
 */
void QueryQosData::setSubscriptionId(const string& subscriptionId)
{
   subscriptionId_ = subscriptionId;
}


/**
 * Dump state of this object into a XML ASCII string.
 * <br>
 * @param extraOffset indenting of tags for nice output
 * @return internal state of the query as a XML ASCII string
 */
string QueryQosData::toXml(const string& extraOffset) const
{
   string ret;
   string offset = Constants::OFFSET + extraOffset;

   ret += offset + "<qos>"; // <!-- SubscribeRetQos -->");
   if (!subscriptionId_.empty())
      ret += offset + " <subscribe id='" + subscriptionId_ + "'/>";
   ret += offset + " <erase forceDestroy='" + Global::getBoolAsString(forceDestroy_) + "'/>";

   if (!meta_) ret += offset + " <meta>false</meta>";
   if (!content_) ret += offset + " <content>false</content>";
   if (!multiSubscribe_) ret += offset + " <multiSubscribe>false</multiSubscribe>";
   if (!local_) ret += offset + " <local>false</local>";
   if (!initialUpdate_) ret += offset + " <initialUpdate>false</initialUpdate>";
   if (updateOneway_) ret += offset + " <updateOneway/>";
   if (!notify_) ret += offset + " <notify>false</notify>";
   if (isPersistent())
      ret += offset + " <persistent/>";

   AccessFilterVector::const_iterator iter = filters_.begin();
   while (iter != filters_.end()) {
      ret += (*iter).toXml(extraOffset + Constants::INDENT);
      iter++;
   }
   ret += historyQos_.toXml(extraOffset + Constants::INDENT);
        bool clearText = false;
   ret += dumpClientProperties(extraOffset + Constants::INDENT, clearText);
   ret += offset + "</qos>";

   if (ret.length() < 16)
      return "<qos/>";  // minimal footprint

   return ret;
}

QueryQosData* QueryQosData::getClone() const
{
   return new QueryQosData(*this);
}

}}}} // namespace


