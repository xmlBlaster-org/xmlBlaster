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

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;

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
       * @return ConnectReturnQos
       */
      virtual ConnectReturnQos connect(const ConnectQos& qos) = 0;

      /**
       * Logout from xmlBlaster.
       * @param qos The QoS or null
       */
      virtual bool disconnect(const DisconnectQos& qos) = 0;

      // Could make sense to the SOCKET driver, returns new SocketCallbackImpl
      //public I_CallbackServer getCbServerInstance() throws XmlBlasterException;

      /**
       * @return The connection protocol name "IOR" or "RMI" etc.
       */
      virtual string getProtocol() = 0;

      /**
       * Is invoked when we poll for the server, for example after we have lost the connection.
       */
//      virtual string loginRaw() = 0;

      virtual bool shutdown() = 0;

      /** Reset the driver on problems */
      virtual void resetConnection() = 0;

      virtual string getLoginName() = 0;

      virtual bool isLoggedIn() = 0;

      virtual string ping(const string& qos) = 0;

      virtual SubscribeReturnQos subscribe(const SubscribeKey& key, const SubscribeQos& qos) = 0;

      virtual vector<MessageUnit> get(const GetKey& key, const GetQos& qos) = 0;

      virtual vector<UnSubscribeReturnQos> 
         unSubscribe(const UnSubscribeKey& key, const UnSubscribeQos& qos) = 0;

      virtual PublishReturnQos publish(const MessageUnit& msgUnit) = 0;

      virtual void publishOneway(const vector<MessageUnit> &msgUnitArr) = 0;

      virtual vector<PublishReturnQos> publishArr(vector<MessageUnit> msgUnitArr) = 0;

      virtual vector<EraseReturnQos> erase(const EraseKey& key, const EraseQos& qos) = 0;
   };

}}}} // namespaces

#endif
