/*------------------------------------------------------------------------------
Name:      CorbaDriver.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The client driver for the corba protocol
------------------------------------------------------------------------------*/

#ifndef _CLIENT_PROTOCOL_CORBA_CORBA_DRIVER
#define _CLIENT_PROTOCOL_CORBA_CORBA_DRIVER

#include <util/xmlBlasterDef.h>
#include <client/protocol/corba/CorbaConnection.h>
#include <client/protocol/corba/DefaultCallback.h>
#include <string>
#include <vector>
#include <util/MessageUnit.h>
#include <client/I_Callback.h>
#include <client/protocol/I_CallbackServer.h>
#include <client/protocol/I_XmlBlasterConnection.h>
#include <util/XmlBlasterException.h>

#include <client/xmlBlasterClient.h>
#include <util/qos/StatusQosFactory.h>
#include <util/qos/MsgQosFactory.h>

using org::xmlBlaster::util::MessageUnit;
using org::xmlBlaster::util::Global;
using namespace org::xmlBlaster::util::qos;
using namespace std;

namespace org {
 namespace xmlBlaster {
  namespace client {
   namespace protocol {
    namespace corba {

   using namespace org::xmlBlaster::util::qos;

   class CorbaDriver : public virtual I_CallbackServer, public virtual I_XmlBlasterConnection
   {
   friend CorbaDriver& getInstance(Global& global);

   private:
      CorbaConnection* connection_;
      DefaultCallback* defaultCallback_;
      const string     ME;
      Global&          global_;
      Log&             log_;
      StatusQosFactory statusQosFactory_;
      MsgQosFactory    msgQosFactory_;

      /**
       * frees the resources used. It only frees the resource specified with
       * 'true'.
       */
      void freeResources(bool deleteConnection=true, bool deleteCallback=true);

      CorbaDriver(Global& global, bool connectionOwner = false);

      CorbaDriver();

      CorbaDriver(const CorbaDriver& corbaDriver);

      CorbaDriver& operator =(const CorbaDriver& corbaDriver);

      virtual ~CorbaDriver();

   public:

      static CorbaDriver& getInstance(Global& global);

      // methods inherited from I_CallbackServer
      void initialize(const string& name, I_Callback &client);
      string getCbProtocol();
      string getCbAddress();
      bool shutdownCb();

      // methods inherited from I_XmlBlasterConnection
      ConnectReturnQos connect(const ConnectQos& qos);
      bool disconnect(const DisconnectQos& qos);
      string getProtocol();
      string loginRaw();
      bool shutdown();
      void resetConnection();
      string getLoginName();
      bool isLoggedIn();

      string ping(const string& qos);

      SubscribeReturnQos subscribe(const SubscribeKey& key, const SubscribeQos& qos);

      vector<MessageUnit> get(const GetKey& key, const GetQos& qos);

      vector<UnSubscribeReturnQos> unSubscribe(const UnSubscribeKey& key, const UnSubscribeQos& qos);

      PublishReturnQos publish(const MessageUnit& msgUnit);

      void publishOneway(const vector<MessageUnit> &msgUnitArr);

      vector<PublishReturnQos> publishArr(vector<MessageUnit> msgUnitArr);

      vector<EraseReturnQos> erase(const EraseKey& key, const EraseQos& qos);



      // following methods are not defined in any parent class
      static void usage();
      // Exception conversion ....
      static org::xmlBlaster::util::XmlBlasterException
        convertFromCorbaException(const serverIdl::XmlBlasterException& ex);
      static serverIdl::XmlBlasterException
        convertToCorbaException(org::xmlBlaster::util::XmlBlasterException& ex);
   };

}}}}} // namespaces

#endif
