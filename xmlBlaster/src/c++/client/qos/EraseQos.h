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
#include <util/qos/HistoryQos.h>

namespace org { namespace xmlBlaster { namespace client { namespace qos {

class Dll_Export EraseQos : public org::xmlBlaster::client::qos::UnSubscribeQos
{
public:
   EraseQos(org::xmlBlaster::util::Global& global);

   EraseQos(org::xmlBlaster::util::Global& global, const org::xmlBlaster::util::qos::QueryQosData& data);

   EraseQos(const EraseQos& qos);

   EraseQos& operator =(const EraseQos& qos);

   /**
    * Add a client property key and value. 
    * @param name The unique key, a duplicate key will overwrite the old setting
    * @param value "vector<unsigned char>" and "unsigned char *" types are treated as a blob
    * @see ClientProperty::#ClientProperty
    */
   template <typename T_VALUE> void addClientProperty(
            const std::string& name,
            const T_VALUE& value,
            const std::string& type="",
            const std::string& encoding="") {
      data_.addClientProperty(name, value, type, encoding);
   }

   /**
    * Defaults to false: If a topic is still referenced by callback messages
    * it will be not erased immediately but we wait until all pending messages are delivered. 
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.message.lifecycle.html">engine.message.lifecycle requirement</a>
    */
   void setForceDestroy(bool forceDestroy);

   void setHistoryQos(org::xmlBlaster::util::qos::HistoryQos historyQos);

   /*
    * Mark the erase request to be persistent. 
    * <p>
    * NOTE: The request is only persistent in the client side
    * queue if we are polling for xmlBlaster.
    * </p>
   void setPersistent(bool persistent);
   */

   /*
    * NOT IMPLEMENTED
    * @param notify true - notify subscribers that message is erased (default is true)
   void setWantNotify(bool notify);
   */
};

}}}} // namespace

#endif

