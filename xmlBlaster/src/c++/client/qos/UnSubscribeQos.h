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
   mutable org::xmlBlaster::util::qos::QueryQosData data_;

public:
   /**
    * Constructor for default qos (quality of service).
    */
   UnSubscribeQos(org::xmlBlaster::util::Global& global);

   /**
    * Create the instance. 
    * @param global
    * @param data The const'ness may be broken when put to persistence. 
    */
   UnSubscribeQos(org::xmlBlaster::util::Global& global,
                  const org::xmlBlaster::util::qos::QueryQosData& data);

   UnSubscribeQos(const UnSubscribeQos& qos);

   UnSubscribeQos& operator =(const UnSubscribeQos& qos);

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
    * Access the value for the given name, if not found returns the defaultValue. 
    */
   template <typename T_VALUE> T_VALUE getClientProperty(
            const std::string& name,
            const T_VALUE& defaultValue) {
      return data_.getClientProperty(name, defaultValue);
   }

   bool hasClientProperty(const std::string& name) const {
      return data_.hasClientProperty(name);
   }
        
   /**
    * Converts the data into a valid XML ASCII std::string.
    * @return An XML ASCII std::string
    */
   std::string toXml() const;

   const org::xmlBlaster::util::qos::QueryQosData& getData() const;
};

}}}}

#endif
