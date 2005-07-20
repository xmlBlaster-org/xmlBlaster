/*------------------------------------------------------------------------------
Name:      I_XmlBlasterConnection.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface (virtual class)
------------------------------------------------------------------------------*/

/**
 * Interface for XmlBlaster, the supported methods on c++ client side. This is
 * a pure virtual class.
 * <p />
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
 */
#ifndef _CLIENT_PROTOCOL_I_XMLBLASTERCONNECTION_H
#define _CLIENT_PROTOCOL_I_XMLBLASTERCONNECTION_H

#include <string>
#include <vector>
#include <util/xmlBlasterDef.h>
#include <util/MessageUnit.h>
#include <util/qos/ConnectQos.h>
#include <util/qos/DisconnectQos.h> 
#include <client/qos/EraseQos.h>
#include <client/qos/EraseReturnQos.h>
#include <client/qos/GetQos.h>
#include <client/qos/GetReturnQos.h>
#include <client/qos/PublishQos.h>
#include <client/qos/PublishReturnQos.h>
#include <client/qos/SubscribeQos.h>
#include <client/qos/SubscribeReturnQos.h>
#include <client/qos/UnSubscribeQos.h>
#include <client/qos/UnSubscribeReturnQos.h>
#include <client/key/EraseKey.h>
#include <client/key/GetKey.h>
#include <client/key/GetReturnKey.h>
#include <client/key/PublishKey.h>
#include <client/key/SubscribeKey.h>
#include <client/key/UnSubscribeKey.h>

/* Would be nicer to declare only instead of including them all:
namespace org { namespace xmlBlaster { namespace util {
   class MessageUnit;
}}}

namespace org { namespace xmlBlaster { namespace client { namespace qos {
   class EraseQos;
   class EraseReturnQos;
   class GetQos;
   class GetReturnQos;
   class PublishQos;
   class PublishReturnQos;
   class SubscribeQos;
   class SubscribeReturnQos;
   class UnSubscribeQos;
   class UnSubscribeReturnQos;  // is a typedef -> would all compilers eat it?
}}}}

namespace org { namespace xmlBlaster { namespace client { namespace key {
   class EraseKey;
   class GetKey;
   class GetReturnKey;
   class PublishKey;
   class SubscribeKey;
   class UnSubscribeKey;
}}}}
*/

namespace org { namespace xmlBlaster { namespace client { namespace protocol {

   class Dll_Export I_XmlBlasterConnection
   {
   public:
      virtual ~I_XmlBlasterConnection() {}

      /**
       * connect() is a login or authentication as well, the authentication schema
       * is transported in the qos.
       *
       * @param qos The authentication and other informations
       * @param client A handle to your callback if desired or null
       * @return org::xmlBlaster::util::qos::ConnectReturnQos
       */
      virtual org::xmlBlaster::util::qos::ConnectReturnQosRef connect(const org::xmlBlaster::util::qos::ConnectQosRef& qos) = 0;

      /**
       * Logout from xmlBlaster.
       * @param qos The QoS or null
       */
      virtual bool disconnect(const org::xmlBlaster::util::qos::DisconnectQos& qos) = 0;

      // Could make sense to the SOCKET driver, returns new SocketCallbackImpl
      //public org::xmlBlaster::client::I_CallbackServer getCbServerInstance() throws org::xmlBlaster::util::XmlBlasterException;

      /**
       * @return The connection protocol name "IOR" or "SOCKET" etc.
       */
      virtual std::string getProtocol() = 0;

      /**
       * Is called when we disconnect or automatically when
       * are going to POLLING mode. 
       */
      virtual bool shutdown() = 0;

      virtual std::string getLoginName() = 0;

      virtual bool isLoggedIn() = 0;

      virtual std::string ping(const std::string& qos) = 0;

      virtual org::xmlBlaster::client::qos::SubscribeReturnQos subscribe(const org::xmlBlaster::client::key::SubscribeKey& key, const org::xmlBlaster::client::qos::SubscribeQos& qos) = 0;

      virtual std::vector<org::xmlBlaster::util::MessageUnit> get(const org::xmlBlaster::client::key::GetKey& key, const org::xmlBlaster::client::qos::GetQos& qos) = 0;

      virtual std::vector<org::xmlBlaster::client::qos::UnSubscribeReturnQos> 
         unSubscribe(const org::xmlBlaster::client::key::UnSubscribeKey& key, const org::xmlBlaster::client::qos::UnSubscribeQos& qos) = 0;

      virtual org::xmlBlaster::client::qos::PublishReturnQos publish(const org::xmlBlaster::util::MessageUnit& msgUnit) = 0;

      virtual void publishOneway(const std::vector<org::xmlBlaster::util::MessageUnit> &msgUnitArr) = 0;

      virtual std::vector<org::xmlBlaster::client::qos::PublishReturnQos> publishArr(const std::vector<org::xmlBlaster::util::MessageUnit> &msgUnitArr) = 0;

      virtual std::vector<org::xmlBlaster::client::qos::EraseReturnQos> erase(const org::xmlBlaster::client::key::EraseKey& key, const org::xmlBlaster::client::qos::EraseQos& qos) = 0;
   };

}}}} // namespaces

#endif
