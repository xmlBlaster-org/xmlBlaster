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
#include <util/thread/ThreadImpl.h>
#include <map>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::thread;
using namespace std;

namespace org {
 namespace xmlBlaster {
  namespace client {
   namespace protocol {
    namespace corba {

/*
   class CorbaDriver;
   typedef map<string, CorbaDriver*> DriverMap;
*/

   using namespace org::xmlBlaster::util::qos;

   class Dll_Export CorbaDriver 
      : public virtual I_CallbackServer, 
        public virtual I_XmlBlasterConnection
   {
   friend class CorbaDriverFactory;

   private:
      Mutex&           mutex_;
      string           instanceName_;
      CorbaConnection* connection_;
      DefaultCallback* defaultCallback_;
      const string     ME;
      Global&          global_;
      Log&             log_;
      StatusQosFactory statusQosFactory_;
      MsgQosFactory    msgQosFactory_;
      bool             orbIsThreadSafe_;

      /**
       * frees the resources used. It only frees the resource specified with
       * 'true'.
       */
      void freeResources(bool deleteConnection=true, bool deleteCallback=true);

      /**
       * Only used by getInstance()
       * @param global
       * @param mutex   Global thread synchronization (to avoid static variable)
       * @param doRun   Only for internal main loop for single threaded orbs. false stops the loop
       *                
       * @param isRunning    Feedback is doRun has stopped
       * @param instanceName
       * @param orb
       */
      CorbaDriver(Global& global, Mutex& mutex, const string instanceName, CORBA::ORB_ptr orb=NULL);

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

      // methods inherited from I_CallbackServer
      void initialize(const string& name, I_Callback &client);
      string getCbProtocol();
      string getCbAddress();
      bool shutdownCb();

      // methods inherited from I_XmlBlasterConnection
      ConnectReturnQos connect(const ConnectQos& qos);
      bool disconnect(const DisconnectQos& qos);
      string getProtocol();
//      string loginRaw();
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
