/*------------------------------------------------------------------------------
Name:      GetQos.h
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

#ifndef _CLIENT_QOS_GETQOS_H
#define _CLIENT_QOS_GETQOS_H

#include <util/xmlBlasterDef.h>
#include <client/qos/UnSubscribeQos.h>





namespace org { namespace xmlBlaster { namespace client { namespace qos {

class Dll_Export GetQos : public org::xmlBlaster::client::qos::UnSubscribeQos
{
public:
   GetQos(org::xmlBlaster::util::Global& global);

   GetQos(org::xmlBlaster::util::Global& global, const org::xmlBlaster::util::qos::QueryQosData& data);

   GetQos(const GetQos& qos);

   GetQos& operator =(const GetQos& qos);

   /**
    * If false, the update contains not the content (it is a notify of change only)
    * TODO: Implement in server!!!
    */
   void setWantContent(bool content);

   /**
    * Adds your subplied subscribe filter
    */
   void addAccessFilter(const org::xmlBlaster::util::qos::AccessFilterQos& filter);

   /**
    * Set the QoS which describes the history query settings. 
    */
   void setHistoryQos(const org::xmlBlaster::util::qos::HistoryQos& historyQos);

   /**
    * Sets a client property to the given value.
    */	
   void setClientProperty(const std::string& key, const std::string& value);

};

}}}} // namespace

#endif

