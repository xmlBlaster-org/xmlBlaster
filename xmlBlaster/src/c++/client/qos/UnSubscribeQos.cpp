/*------------------------------------------------------------------------------
Name:      UnSubscribeQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * This class encapsulates the QoS of an unSubcribe() request. 
 * <p />
 * A full specified <b>unSubcribe</b> qos could look like this:<br />
 * <pre>
 *&lt;qos>
 *&lt;/qos>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 * @see org.xmlBlaster.util.qos.QueryQosData
 * @see org.xmlBlaster.util.qos.QueryQosSaxFactory
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.unSubscribe.html">unSubscribe interface</a>
 */

#include <client/qos/UnSubscribeQos.h>
#include <util/qos/QueryQosData.h>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;

namespace org { namespace xmlBlaster { namespace client { namespace qos {

/**
 * Constructor for default qos (quality of service).
 */
UnSubscribeQos::UnSubscribeQos(Global& global)
   : ME("UnSubscribeQos"), global_(global), data_(QueryQosData(global))
{
}


UnSubscribeQos::UnSubscribeQos(Global& global, const QueryQosData& data)
   : ME("UnSubscribeQos"), global_(global), data_(data)
{
}

UnSubscribeQos::UnSubscribeQos(const UnSubscribeQos& qos)
   : ME(qos.ME), global_(qos.global_), data_(qos.data_)
{
}
UnSubscribeQos& UnSubscribeQos::operator =(const UnSubscribeQos& qos)
{
   data_ = qos.data_;
   return *this;
}

string UnSubscribeQos::toXml() const
{
   return data_.toXml();
}

const QueryQosData& UnSubscribeQos::getData() const
{
   return data_;
}

}}}}
