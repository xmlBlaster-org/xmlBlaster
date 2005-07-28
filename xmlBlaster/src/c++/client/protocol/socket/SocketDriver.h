/*------------------------------------------------------------------------------
Name:      SocketDriver.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The client driver for the socket protocol
------------------------------------------------------------------------------*/
#ifndef _CLIENT_PROTOCOL_SOCKET_SOCKET_DRIVER
#define _CLIENT_PROTOCOL_SOCKET_SOCKET_DRIVER

#include <util/xmlBlasterDef.h>
#include <string>
#include <vector>
#include <util/MessageUnit.h>
#include <client/I_Callback.h>
#include <client/protocol/I_CallbackServer.h>
#include <client/protocol/I_XmlBlasterConnection.h>
#include <util/XmlBlasterException.h>
#include <util/qos/StatusQosFactory.h>
#include <util/qos/MsgQosFactory.h>
#include <util/key/MsgKeyFactory.h>
#include <util/plugin/I_Plugin.h>
//#include <util/Global.h>    // For org::xmlBlaster::util::ArgsStruct_T argsStruct_
/* Don't include this in this header to avoid dependency: */
//#include <XmlBlasterAccessUnparsed.h> // The C SOCKET client library

struct XmlBlasterAccessUnparsed;
struct ExceptionStruct;
namespace org {
 namespace xmlBlaster {
   namespace util {
    struct ArgsStruct;
}}}

namespace org {
 namespace xmlBlaster {
  namespace client {
   namespace protocol {
    namespace socket {

   class Dll_Export SocketDriver
      : public virtual org::xmlBlaster::client::protocol::I_CallbackServer, 
        public virtual org::xmlBlaster::client::protocol::I_XmlBlasterConnection,
        public virtual org::xmlBlaster::util::plugin::I_Plugin
   {
   friend class SocketDriverFactory; // To be able to create a SocketDriver instance

   private:
      org::xmlBlaster::util::thread::Mutex mutex_;
      std::string instanceName_;
      struct ::XmlBlasterAccessUnparsed* connection_;
      std::string ME;
      struct org::xmlBlaster::util::ArgsStruct *argsStructP_;
      org::xmlBlaster::util::Global& global_;
      org::xmlBlaster::util::I_Log& log_;
      org::xmlBlaster::util::qos::StatusQosFactory statusQosFactory_;
      std::string secretSessionId_;
      std::string loginName_;
      org::xmlBlaster::util::key::MsgKeyFactory msgKeyFactory_;
      org::xmlBlaster::util::qos::MsgQosFactory msgQosFactory_;
      I_Callback* callbackClient_;

      /**
       * frees the resources used. It only frees the resource specified with
       * 'true'.
       */
      void freeResources(bool deleteConnection=true);

      /** 
       * Called on polling, try to reconnect to server
       */
      void reconnectOnIpLevel(void);

      /**
       * The only constructor. 
       * @param global
       * @param isRunning    Feedback is doRun has stopped
       * @param instanceName
       */
      SocketDriver(org::xmlBlaster::util::Global& global, const std::string instanceName);

      SocketDriver(const SocketDriver& socketDriver);

      SocketDriver& operator =(const SocketDriver& socketDriver);

      virtual ~SocketDriver();

   public:
      org::xmlBlaster::client::protocol::I_ProgressListener *progressListener_;

      // methods inherited from org::xmlBlaster::client::protocol::I_CallbackServer
      void initialize(const std::string& name, org::xmlBlaster::client::I_Callback &client);
      std::string getCbProtocol();
      std::string getCbAddress();

      org::xmlBlaster::util::I_Log& getLog() const { return log_; }

      /**
       * @return 0 if the client has not registered its update()
       */
      I_Callback* getCallbackClient();
      bool shutdownCb();

      //bool myUpdate(::MsgUnitArr *msgUnitArr, void *userData,
      //               ::ExceptionStruct *exception);

      // methods inherited from org::xmlBlaster::client::protocol::I_XmlBlasterConnection
      // Note: the const is cast away, the declaration should be changed
      org::xmlBlaster::util::qos::ConnectReturnQosRef connect(const org::xmlBlaster::util::qos::ConnectQosRef& qos);
            //throw (org::xmlBlaster::util::ExceptionStruct);
      bool disconnect(const org::xmlBlaster::util::qos::DisconnectQos& qos);
      std::string getProtocol();
      bool shutdown();
      std::string getLoginName();
      bool isLoggedIn();

      std::string ping(const std::string& qos);

      org::xmlBlaster::client::qos::SubscribeReturnQos subscribe(const org::xmlBlaster::client::key::SubscribeKey& key, const org::xmlBlaster::client::qos::SubscribeQos& qos);

      std::vector<org::xmlBlaster::util::MessageUnit> get(const org::xmlBlaster::client::key::GetKey& key, const org::xmlBlaster::client::qos::GetQos& qos);

      std::vector<org::xmlBlaster::client::qos::UnSubscribeReturnQos> unSubscribe(const org::xmlBlaster::client::key::UnSubscribeKey& key, const org::xmlBlaster::client::qos::UnSubscribeQos& qos);

      org::xmlBlaster::client::qos::PublishReturnQos publish(const org::xmlBlaster::util::MessageUnit& msgUnit);

      void publishOneway(const std::vector<org::xmlBlaster::util::MessageUnit> &msgUnitArr);

      std::vector<org::xmlBlaster::client::qos::PublishReturnQos> publishArr(const std::vector<org::xmlBlaster::util::MessageUnit> &msgUnitArr);

      std::vector<org::xmlBlaster::client::qos::EraseReturnQos> erase(const org::xmlBlaster::client::key::EraseKey& key, const org::xmlBlaster::client::qos::EraseQos& qos);

      const std::string& me() { return ME; }
      org::xmlBlaster::util::Global& getGlobal() { return global_; }
      org::xmlBlaster::util::I_Log& getLog() { return log_; }
      org::xmlBlaster::util::key::MsgKeyFactory& getMsgKeyFactory() { return msgKeyFactory_; }
      org::xmlBlaster::util::qos::MsgQosFactory& getMsgQosFactory() { return msgQosFactory_; }

      // following methods are not defined in any parent class
      static std::string usage();
      // Exception conversion ....
      org::xmlBlaster::util::XmlBlasterException
        convertFromSocketException(const struct ::ExceptionStruct & ex) const;
      static struct ::ExceptionStruct
        convertToSocketException(org::xmlBlaster::util::XmlBlasterException& ex);

      /**
       * Get the name of the plugin. 
       * @return "SOCKET"
       * @enforcedBy I_Plugin
       */
      std::string getType() { static std::string type = "SOCKET"; return type; }

      /**
       * Get the version of the plugin. 
       * @return "1.0"
       * @enforcedBy I_Plugin
       */
      std::string getVersion() { static std::string version = "1.0"; return version; }

      /**
       * @see org::xmlBlaster::client::protocol::I_CallbackServer#registerProgressListener
       */
      org::xmlBlaster::client::protocol::I_ProgressListener* registerProgressListener(org::xmlBlaster::client::protocol::I_ProgressListener *listener);
   };

}}}}} // namespaces

#endif
