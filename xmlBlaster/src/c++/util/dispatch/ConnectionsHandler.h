/*------------------------------------------------------------------------------
Name:      ConnectionsHandler.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handles the I_XmlBlasterConnections 
------------------------------------------------------------------------------*/

/**
 * Interface for XmlBlaster, the supported methods on c++ client side. This is
 * a pure virtual class.
 * <p />
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
 */


#ifndef _UTIL_DISPATCH_CONNECTIONSHANDLER_H
#define _UTIL_DISPATCH_CONNECTIONSHANDLER_H

#include <util/xmlBlasterDef.h>
#include <client/protocol/I_XmlBlasterConnection.h>
// #include <util/dispatch/DeliveryManager.h>
#include <client/I_ConnectionProblems.h>
#include <util/XmlBlasterException.h>
#include <util/thread/Thread.h>
#include <util/I_Timeout.h>

using namespace org::xmlBlaster::client::protocol;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::util;

namespace org { namespace xmlBlaster { namespace util { namespace dispatch {

#ifndef _UTIL_DISPATCH_DELIVERYMANAGER_H
   class DeliveryManager;
#endif


enum States {START, CONNECTED, POLLING, DEAD, END};

class Dll_Export ConnectionsHandler : public I_Timeout
{
private:
   const string            ME;
   ConnectQos*             connectQos_;
   ConnectReturnQos*       connectReturnQos_;
   DeliveryManager&        deliveryManager_;
   I_ConnectionProblems*   connectionProblems_;
   I_XmlBlasterConnection* connection_;
   enum States             status_;
   Global&                 global_;
   Log&                    log_;
   Mutex                   connectionMutex_;
   int                     retries_;
   int                     currentRetry_;
   Timestamp               timestamp_;

public:
   ConnectionsHandler(Global& global, DeliveryManager& deliveryManager);

   virtual ~ConnectionsHandler();


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
   ConnectReturnQos connect(const ConnectQos& qos);

   /**
    * Logout from xmlBlaster. If the status is DEAD it returns false and writes a warning. If the status
    * is CONNECTED it disconnects. If the status is something else, it throws an exception.
    * @param qos The QoS or null
    */
   bool disconnect(const DisconnectQos& qos);

   /**
    * @return The connection protocol name "IOR" or "RMI" etc.
    */
   string getProtocol();

   /**
    * Is invoked when we poll for the server, for example after we have lost the connection.
    */
   string loginRaw();

   bool shutdown();

   /** Reset the driver on problems */
   void resetConnection();

   string getLoginName();

   bool isLoggedIn();

   string ping(const string& qos);

   SubscribeReturnQos subscribe(const SubscribeKey& key, const SubscribeQos& qos);

   vector<MessageUnit> get(const GetKey& key, const GetQos& qos);

   vector<UnSubscribeReturnQos> 
      unSubscribe(const UnSubscribeKey& key, const UnSubscribeQos& qos);

   PublishReturnQos publish(const MessageUnit& msgUnit);

   void publishOneway(const vector<MessageUnit> &msgUnitArr);

   vector<PublishReturnQos> publishArr(vector<MessageUnit> msgUnitArr);

   vector<EraseReturnQos> erase(const EraseKey& key, const EraseQos& qos);

   void initFailsafe(I_ConnectionProblems* connectionProblems);

   void timeout(void *userData);

};

}}}} // namespaces

#endif
