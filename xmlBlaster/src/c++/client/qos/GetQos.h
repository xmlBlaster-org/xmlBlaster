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

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;

namespace org { namespace xmlBlaster { namespace client { namespace qos {

class Dll_Export GetQos : public UnSubscribeQos
{
public:
   GetQos(Global& global);

   GetQos(Global& global, const QueryQosData& data);

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
   void addAccessFilter(const AccessFilterQos& filter);

   /**
    * Set the QoS which describes the history query settings. 
    */
   void setHistoryQos(const HistoryQos& historyQos);

};

}}}} // namespace

#endif

