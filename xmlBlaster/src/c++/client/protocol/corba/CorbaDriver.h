/*------------------------------------------------------------------------------
Name:      CorbaDriver.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The client driver for the corba protocol
------------------------------------------------------------------------------*/

#ifndef _CLIENT_PROTOCOL_CORBA_CORBA_DRIVER
#define _CLIENT_PROTOCOL_CORBA_CORBA_DRIVER

#include <util/xmlBlasterDef.h>
#include <util/plugin/I_Plugin.h>
#include <client/protocol/corba/CorbaConnection.h>
#include <client/protocol/corba/DefaultCallback.h>
#include <string>
#include <vector>
#include <util/MessageUnit.h>
#include <client/I_Callback.h>
#include <client/protocol/I_CallbackServer.h>
#include <client/protocol/I_XmlBlasterConnection.h>
#include <util/XmlBlasterException.h>

#include <util/qos/StatusQosFactory.h>
#include <util/qos/MsgQosFactory.h>
#include <util/thread/ThreadImpl.h>
#include <map>

namespace org {
 namespace xmlBlaster {
  namespace client {
   namespace protocol {
    namespace corba {

   class Dll_Export CorbaDriver 
      : public virtual org::xmlBlaster::client::protocol::I_CallbackServer, 
        public virtual org::xmlBlaster::client::protocol::I_XmlBlasterConnection,
        public virtual org::xmlBlaster::util::plugin::I_Plugin
   {
   friend class CorbaDriverFactory; // To be able to create a CorbaDriver instance

   private:
      org::xmlBlaster::util::thread::Mutex&           mutex_;
      std::string           instanceName_;
      CorbaConnection* connection_;
      DefaultCallback* defaultCallback_;
      const std::string     ME;
      org::xmlBlaster::util::Global&          global_;
      org::xmlBlaster::util::I_Log&             log_;
      org::xmlBlaster::util::qos::StatusQosFactory statusQosFactory_;
      bool             orbIsThreadSafe_;

      /**
       * frees the resources used. It only frees the resource specified with
       * 'true'.
       */
      void freeResources(bool deleteConnection=true, bool deleteCallback=true);

      /**
       * Only used by getInstance()
       * @param global
       * @param mutex   org::xmlBlaster::util::Global thread synchronization (to avoid static variable)
       * @param doRun   Only for internal main loop for single threaded orbs. false stops the loop
       *                
       * @param isRunning    Feedback is doRun has stopped
       * @param instanceName
       * @param orb
       */
      CorbaDriver(org::xmlBlaster::util::Global& global, org::xmlBlaster::util::thread::Mutex& mutex, const std::string instanceName, CORBA::ORB_ptr orb=NULL);

      CorbaDriver(const CorbaDriver& corbaDriver);

      CorbaDriver& operator =(const CorbaDriver& corbaDriver);

      virtual ~CorbaDriver();

      /**
       * For single threaded CORBA implementations only (like MICO).
       * One instance (the first) starts a main loop and checks if the
       * orb has some work to perform (every 20 millis).
       * In your real application this should be done by your main loop (e.g. from X-Window)
       * E.g. mico has a helper implementation to register its file descriptors with another main loop.
       */
//      void run();

   public:

      bool orbIsThreadSafe() const {
         return this->orbIsThreadSafe_;
      }

      // methods inherited from org::xmlBlaster::client::protocol::I_CallbackServer
      void initialize(const std::string& name, org::xmlBlaster::client::I_Callback &client);
      std::string getCbProtocol();
      std::string getCbAddress();
      bool shutdownCb();

      // methods inherited from org::xmlBlaster::client::protocol::I_XmlBlasterConnection
      org::xmlBlaster::util::qos::ConnectReturnQosRef connect(const org::xmlBlaster::util::qos::ConnectQosRef& qos);
      bool disconnect(const org::xmlBlaster::util::qos::DisconnectQos& qos);
      std::string getProtocol();
//      std::string loginRaw();
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

      /**
       * @see org::xmlBlaster::client::protocol::I_CallbackServer#registerProgressListener
       */
      org::xmlBlaster::client::protocol::I_ProgressListener* registerProgressListener(org::xmlBlaster::client::protocol::I_ProgressListener *listener);


      // following methods are not defined in any parent class
      static std::string usage();
      // Exception conversion ....
      static org::xmlBlaster::util::XmlBlasterException
        convertFromCorbaException(const serverIdl::XmlBlasterException& ex);
      static serverIdl::XmlBlasterException
        convertToCorbaException(org::xmlBlaster::util::XmlBlasterException& ex);

      /**
       * Get the name of the plugin. 
       * @return "IOR"
       * @enforcedBy I_Plugin
       */
      std::string getType() { static std::string type = "IOR"; return type; }

      /**
       * Get the version of the plugin. 
       * @return "1.0"
       * @enforcedBy I_Plugin
       */
      std::string getVersion() { static std::string version = "1.0"; return version; }
   };

}}}}} // namespaces

#endif
