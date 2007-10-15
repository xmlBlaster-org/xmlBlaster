/*------------------------------------------------------------------------------
Name:      ConnectionsHandler.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handles the org::xmlBlaster::client::protocol::I_XmlBlasterConnections 
------------------------------------------------------------------------------*/
#ifndef _UTIL_DISPATCH_CONNECTIONSHANDLER_H
#define _UTIL_DISPATCH_CONNECTIONSHANDLER_H

#include <vector>
#include <util/xmlBlasterDef.h>
#include <util/dispatch/I_ConnectionsHandler.h>
#include <util/dispatch/I_PostSendListener.h>
#include <client/I_ConnectionProblems.h>
#include <util/XmlBlasterException.h>
#include <util/thread/ThreadImpl.h>
#include <util/I_Timeout.h>
//#include <util/queue/I_Queue.h>
// #include <util/queue/PublishQueueEntry.h>
// #include <util/queue/ConnectQueueEntry.h>

#ifndef _UTIL_QUEUE_I_QUEUE_H
namespace org { namespace xmlBlaster { namespace util { namespace queue {
class I_Queue;
}}}}
#endif

namespace org { namespace xmlBlaster { namespace util { namespace dispatch {

/**
 * Interface for XmlBlaster, the supported methods on c++ client side. This is
 * a pure virtual class.
 * <p />
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
 */
class Dll_Export ConnectionsHandler : public I_Timeout, public org::xmlBlaster::util::dispatch::I_ConnectionsHandler
{
private:
   const std::string ME;
   org::xmlBlaster::util::qos::ConnectQosRef connectQos_;
   org::xmlBlaster::util::qos::ConnectReturnQosRef connectReturnQos_;
   org::xmlBlaster::client::I_ConnectionProblems* connectionProblemsListener_;
   org::xmlBlaster::client::protocol::I_XmlBlasterConnection* connection_;
   enum States status_;
   org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::I_Log& log_;
   org::xmlBlaster::util::thread::Mutex connectMutex_;
   org::xmlBlaster::util::thread::Mutex publishMutex_;
   int retries_;
   int currentRetry_;
   org::xmlBlaster::util::Timestamp timestamp_;
   org::xmlBlaster::util::queue::I_Queue* queue_;
   org::xmlBlaster::util::dispatch::I_PostSendListener* postSendListener_; 
   bool pingIsStarted_;
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
   org::xmlBlaster::util::qos::ConnectReturnQosRef connect(const org::xmlBlaster::util::qos::ConnectQosRef& qos);

   /**
    * Logout from xmlBlaster. If the status is DEAD it returns false and writes a warning. If the status
    * is ALIVE it disconnects. If the status is something else, it throws an exception.
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

   /**
    * Register a listener for to receive information about the progress of incoming data. 
    * Only one listener is supported, the last call overwrites older calls.
    * @param listener Your listener, pass 0 to unregister.
    * @return The previously registered listener or 0
    */
   org::xmlBlaster::util::dispatch::I_PostSendListener* registerPostSendListener(org::xmlBlaster::util::dispatch::I_PostSendListener *listener);

   void initFailsafe(org::xmlBlaster::client::I_ConnectionProblems* connectionProblems);

   void timeout(void *userData);

   /**
    * On POLLING or if the client side queue contains entries
    * further messages need to be put to the queue to maintain sequence. 
    * @return true if the message must be put to queue.
    */
   bool putToQueue();

   /**
    * Flushes all entries in the queue, i.e. the entries of the queue are sent to xmlBlaster.
    * If the queue is empty or NULL, then 0 is returned. If the state is in POLLING or DEAD, then -1 is
    * returned.. This method blocks until all entries in the queue have been sent.
    */
   long flushQueue();

   org::xmlBlaster::util::queue::I_Queue* getQueue();

   bool isFailsafe() const;

   /**
    * Same as isAlive() || isPolling()
    * @return true if connect() call was successful, even if we are polling
    */
   bool isConnected() const;

   /**
    * @return true if connected with server and ready
    */
   bool isAlive() const;

   /**
    * @return true if polling for the server
    */
   bool isPolling() const;

   /**
    * @return true if we have given up
    */
   bool isDead() const;

   /**
    * Get connection status string for logging. 
    * @return "ALIVE" | "POLLING" | "DEAD"
    */
   std::string getStatusString() const;

   org::xmlBlaster::util::qos::ConnectReturnQosRef connectRaw(const org::xmlBlaster::util::qos::ConnectQosRef& connectQos);

   virtual org::xmlBlaster::client::protocol::I_XmlBlasterConnection& getConnection() const;

   virtual org::xmlBlaster::util::qos::ConnectReturnQosRef getConnectReturnQos();

   virtual org::xmlBlaster::util::qos::ConnectQosRef getConnectQos();

protected:
   /** only used inside the class to avoid deadlock */
   long flushQueueUnlocked(org::xmlBlaster::util::queue::I_Queue *queueToFlush, bool doRemove=true);
   org::xmlBlaster::client::qos::PublishReturnQos queuePublish(const org::xmlBlaster::util::MessageUnit& msgUnit);
   org::xmlBlaster::client::qos::SubscribeReturnQos queueSubscribe(const org::xmlBlaster::client::key::SubscribeKey& key, const org::xmlBlaster::client::qos::SubscribeQos& qos);
   org::xmlBlaster::util::qos::ConnectReturnQosRef& queueConnect();
   /**
    * @param withInitialPing If true do an immediate ping without delay
    */
   bool startPinger(bool withInitialPing);

   /**
    * Going to polling status in case we are in failsafe mode or to DEAD if we are not in failsafe mode.
    */
   void toPollingOrDead(const org::xmlBlaster::util::XmlBlasterException* reason);
};


}}}} // namespaces

#endif
