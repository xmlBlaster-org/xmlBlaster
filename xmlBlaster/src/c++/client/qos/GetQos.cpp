/*------------------------------------------------------------------------------
Name:      GetQos.cpp
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

#include <client/qos/GetQos.h>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;

namespace org { namespace xmlBlaster { namespace client { namespace qos {

GetQos::GetQos(Global& global) : UnSubscribeQos(global)
{
   ME = "GetQos";
}

GetQos::GetQos(Global& global, const QueryQosData& data)
   : UnSubscribeQos(global, data)
{
   ME = "GetQos";
}


GetQos::GetQos(const GetQos& qos) : UnSubscribeQos(qos)
{
}

GetQos& GetQos::operator =(const GetQos& qos)
{
   data_ = qos.data_;
   return *this;
}

/**
 * If false, the update contains not the content (it is a notify of change only)
 * TODO: Implement in server!!!
 */
void GetQos::setWantContent(bool content)
{
   data_.setWantContent(content);
}

/**
 * Adds your subplied subscribe filter
 */
void GetQos::addAccessFilter(const AccessFilterQos& filter)
{
   data_.addAccessFilter(filter);
}

/**
 * Set the QoS which describes the history query settings. 
 */
void GetQos::setHistoryQos(const HistoryQos& historyQos)
{
   data_.setHistoryQos(historyQos);
}

}}}} // namespace

