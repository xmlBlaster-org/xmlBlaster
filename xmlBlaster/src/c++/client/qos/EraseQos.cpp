/*------------------------------------------------------------------------------
Name:      EraseQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/


/**
 * This class encapsulates the QoS of an erase() request. 
 * <p />
 * A full specified <b>erase</b> qos could look like this:<br />
 * <pre>
 *&lt;qos>
 *   &lt;!-- The subscribers shall not be notified when this message is destroyed -->
 *   &lt;notify>false&lt;/notify> <!-- currently not implemented -->
 *&lt;/qos>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 * @see org.xmlBlaster.util.qos.QueryQosData
 * @see org.xmlBlaster.util.qos.QueryQosSaxFactory
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.erase.html">erase interface</a>
 */


#include <client/qos/EraseQos.h>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;

namespace org { namespace xmlBlaster { namespace client { namespace qos {

EraseQos::EraseQos(Global& global) : UnSubscribeQos(global)
{
   ME = "EraseQos";
}

EraseQos::EraseQos(Global& global, const QueryQosData& data)
   : UnSubscribeQos(global, data)
{
   ME = "EraseQos";
}

EraseQos::EraseQos(const EraseQos& qos) : UnSubscribeQos(qos)
{
}

EraseQos& EraseQos::operator =(const EraseQos& qos)
{
   data_ = qos.data_;
   return *this;
}

void EraseQos::setForceDestroy(bool forceDestroy)
{
   data_.setForceDestroy(forceDestroy);
}

void EraseQos::setHistoryQos(HistoryQos historyQos)
{
   data_.setHistoryQos(historyQos);
}

/*
 * NOT IMPLEMENTED
 * @param notify true - notify subscribers that message is erased (default is true)
void EraseQos::setWantNotify(bool notify) 
{
   data_.setWantNotify(notify);
}
*/

}}}} // namespace


