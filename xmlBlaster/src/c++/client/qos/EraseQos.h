/*------------------------------------------------------------------------------
Name:      EraseQos.h
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

#ifndef _CLIENT_QOS_ERASEQOS_H
#define _CLIENT_QOS_ERASEQOS_H

#include <util/xmlBlasterDef.h>
#include <client/qos/UnSubscribeQos.h>

namespace org { namespace xmlBlaster { namespace client { namespace qos {

class Dll_Export EraseQos : public org::xmlBlaster::client::qos::UnSubscribeQos
{
public:
   EraseQos(org::xmlBlaster::util::Global& global);

   EraseQos(const EraseQos& qos);

   EraseQos& operator =(const EraseQos& qos);

   void setClientProperty(const std::string& key, const std::string& value);

   /**
    * NOT IMPLEMENTED
    * @param notify true - notify subscribers that message is erased (default is true)
    */
   void setWantNotify(bool notify);
};

}}}} // namespace

#endif

