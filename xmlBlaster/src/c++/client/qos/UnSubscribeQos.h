/*------------------------------------------------------------------------------
Name:      UnSubscribeQos.h
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

#ifndef _CLIENT_QOS_UNSUBSCRIBEQOS_H
#define _CLIENT_QOS_UNSUBSCRIBEQOS_H

#include <util/xmlBlasterDef.h>
#include <util/qos/QueryQosData.h>

using namespace std;
using org::xmlBlaster::util::qos::QueryQosData;

namespace org { namespace xmlBlaster { namespace client { namespace qos {

class Dll_Export UnSubscribeQos
{
protected:
   string       ME;
   Global&      global_;
   QueryQosData data_;

public:
   /**
    * Constructor for default qos (quality of service).
    */
   UnSubscribeQos(Global& global);

   UnSubscribeQos(Global& global, const QueryQosData& data);

   UnSubscribeQos(const UnSubscribeQos& qos);

   UnSubscribeQos& operator =(const UnSubscribeQos& qos);

   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   string toXml() const;

   QueryQosData getData() const;
};

}}}}

#endif
