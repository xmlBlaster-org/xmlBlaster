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

#include <util/xmlBlasterDef.h>
#include <util/MessageUnit.h>
#include <util/qos/ConnectQos.h>
#include <string>
#include <vector>
#include <client/xmlBlasterClient.h>

namespace org { namespace xmlBlaster { namespace client { namespace protocol {

   class Dll_Export I_XmlBlasterConnection
   {
   public:

/*
      virtual ~I_XmlBlasterConnection() 
      {
      }
*/
      /**
       * connect() is a login or authentication as well, the authentication schema
       * is transported in the qos.
       * It is more general then the login() method, since it allows
       * to transport any authentication info in the xml based qos.
       *
       * You can still use login() for simple name/password based authentication.
       *
       * @param qos The authentication and other informations
       * @param client A handle to your callback if desired or null
       * @return org::xmlBlaster::util::qos::ConnectReturnQos
       */
      virtual org::xmlBlaster::util::qos::ConnectReturnQos connect(const org::xmlBlaster::util::qos::ConnectQos& qos) = 0;

      /**
       * org::xmlBlaster::util::Logout from xmlBlaster.
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
       * Is invoked when we poll for the server, for example after we have lost the connection.
       */
//      virtual std::string loginRaw() = 0;

      virtual bool shutdown() = 0;

      /** Reset the driver on problems */
      virtual void resetConnection() = 0;

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
