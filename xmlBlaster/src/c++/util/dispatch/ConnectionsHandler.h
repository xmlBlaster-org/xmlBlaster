/*------------------------------------------------------------------------------
Name:      ConnectionsHandler.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handles the org::xmlBlaster::client::protocol::I_XmlBlasterConnections 
------------------------------------------------------------------------------*/

/**
 * Interface for XmlBlaster, the supported methods on c++ client side. This is
 * a pure virtual class.
 * <p />
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
 */
#ifndef _UTIL_DISPATCH_CONNECTIONSHANDLER_H
#define _UTIL_DISPATCH_CONNECTIONSHANDLER_H

#include <vector>
#include <util/xmlBlasterDef.h>
#include <util/dispatch/I_ConnectionsHandler.h>
#include <client/I_ConnectionProblems.h>
#include <util/XmlBlasterException.h>
#include <util/thread/ThreadImpl.h>
#include <util/I_Timeout.h>
#include <util/queue/MsgQueue.h>
// #include <util/queue/PublishQueueEntry.h>
// #include <util/queue/ConnectQueueEntry.h>

namespace org { namespace xmlBlaster { namespace util { namespace dispatch {

class Dll_Export ConnectionsHandler : public I_Timeout, public org::xmlBlaster::util::dispatch::I_ConnectionsHandler
{
private:
   const std::string ME;
   org::xmlBlaster::util::qos::ConnectQos* connectQos_;
   org::xmlBlaster::util::qos::ConnectReturnQos* connectReturnQos_;
   org::xmlBlaster::client::I_ConnectionProblems* connectionProblems_;
   org::xmlBlaster::client::protocol::I_XmlBlasterConnection* connection_;
   enum States status_;
   org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::Log& log_;
   org::xmlBlaster::util::thread::Mutex connectMutex_;
   org::xmlBlaster::util::thread::Mutex publishMutex_;
   int retries_;
   int currentRetry_;
   org::xmlBlaster::util::Timestamp timestamp_;
   org::xmlBlaster::util::queue::MsgQueue* queue_;
   bool pingIsStarted_;
   /**
    * Temporary hack until the server will give back the same sessionId. Here all subscriptions and 
    * unSubscriptions are stored. When reconnecting a check is made to see if we got the same sessionId. If
    * the id differe, then all subscribe and unSubscribe are repeated.
    */
   org::xmlBlaster::util::queue::MsgQueue* adminQueue_; // used to temporarly store the subscriptions 
   const std::string instanceName_;
   bool doStopPing_; // used to stop the pinger when destroying the object

public:
   ConnectionsHandler(org::xmlBlaster::util::Global& global, const std::string& instanceName);

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
    * @return org::xmlBlaster::util::qos::ConnectReturnQos
    */
   org::xmlBlaster::util::qos::ConnectReturnQos connect(const org::xmlBlaster::util::qos::ConnectQos& qos);

   /**
    * org::xmlBlaster::util::Logout from xmlBlaster. If the status is DEAD it returns false and writes a warning. If the status
    * is CONNECTED it disconnects. If the status is something else, it throws an exception.
    * @param qos The QoS or null
    */
   bool disconnect(const org::xmlBlaster::util::qos::DisconnectQos& qos);

   /**
    * @return The connection protocol name "IOR" or "SOCKET" etc.
    */
   std::string getProtocol();

   /**
    * Is invoked when we poll for the server, for example after we have lost the connection.
    */
//   std::string loginRaw();

   bool shutdown();

   /** Reset the driver on problems */
   void resetConnection();

   std::string getLoginName();

   bool isLoggedIn();

   std::string ping(const std::string& qos);

   org::xmlBlaster::client::qos::SubscribeReturnQos subscribe(const org::xmlBlaster::client::key::SubscribeKey& key, const org::xmlBlaster::client::qos::SubscribeQos& qos);
                                                                                
   std::vector<org::xmlBlaster::util::MessageUnit> get(const org::xmlBlaster::client::key::GetKey& key, const org::xmlBlaster::client::qos::GetQos& qos);

   std::vector<org::xmlBlaster::client::qos::UnSubscribeReturnQos> 
      unSubscribe(const org::xmlBlaster::client::key::UnSubscribeKey& key, const org::xmlBlaster::client::qos::UnSubscribeQos& qos);

   org::xmlBlaster::client::qos::PublishReturnQos publish(const org::xmlBlaster::util::MessageUnit& msgUnit);

   void publishOneway(const std::vector<org::xmlBlaster::util::MessageUnit> &msgUnitArr);

   std::vector<org::xmlBlaster::client::qos::PublishReturnQos> publishArr(const std::vector<org::xmlBlaster::util::MessageUnit> &msgUnitArr);

   std::vector<org::xmlBlaster::client::qos::EraseReturnQos> erase(const org::xmlBlaster::client::key::EraseKey& key, const org::xmlBlaster::client::qos::EraseQos& qos);

   void initFailsafe(org::xmlBlaster::client::I_ConnectionProblems* connectionProblems);

   void timeout(void *userData);

   /**
    * Flushes all entries in the queue, i.e. the entries of the queue are sent to xmlBlaster.
    * If the queue is empty or NULL, then 0 is returned. If the state is in POLLING or DEAD, then -1 is
    * returned.. This method blocks until all entries in the queue have been sent.
    */
   long flushQueue();

   org::xmlBlaster::util::queue::Queue* getQueue();

   bool isFailsafe() const;

   bool isConnected() const;

   org::xmlBlaster::util::qos::ConnectReturnQos connectRaw(const org::xmlBlaster::util::qos::ConnectQos& connectQos);

   virtual org::xmlBlaster::client::protocol::I_XmlBlasterConnection& getConnection();

   virtual org::xmlBlaster::util::qos::ConnectReturnQos* getConnectReturnQos();

   virtual org::xmlBlaster::util::qos::ConnectQos* getConnectQos();

protected:
   /** only used inside the class to avoid deadlock */
   long flushQueueUnlocked(org::xmlBlaster::util::queue::MsgQueue *queueToFlush, bool doRemove=true);
   org::xmlBlaster::client::qos::PublishReturnQos queuePublish(const org::xmlBlaster::util::MessageUnit& msgUnit);
   org::xmlBlaster::util::qos::ConnectReturnQos& queueConnect();
   bool startPinger();

   /**
    * Going to polling status in case we are in failsafe mode or to DEAD if we are not in failsafe mode.
    */
   void toPollingOrDead(const org::xmlBlaster::util::XmlBlasterException* reason);
};


}}}} // namespaces

#endif
