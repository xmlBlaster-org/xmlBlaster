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

namespace org { namespace xmlBlaster { namespace client { namespace qos {

class Dll_Export UnSubscribeQos
{
protected:
   std::string ME;
   org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::qos::QueryQosData data_;

public:
   /**
    * Constructor for default qos (quality of service).
    */
   UnSubscribeQos(org::xmlBlaster::util::Global& global);

   UnSubscribeQos(org::xmlBlaster::util::Global& global,
                  const org::xmlBlaster::util::qos::QueryQosData& data);

   UnSubscribeQos(const UnSubscribeQos& qos);

   UnSubscribeQos& operator =(const UnSubscribeQos& qos);

   /**
    * Sets a client property to the given value.
    */	
   void setClientProperty(const std::string& key, const std::string& value);

   /**
    * Converts the data into a valid XML ASCII std::string.
    * @return An XML ASCII std::string
    */
   std::string toXml() const;

   org::xmlBlaster::util::qos::QueryQosData getData() const;
};

}}}}

#endif
