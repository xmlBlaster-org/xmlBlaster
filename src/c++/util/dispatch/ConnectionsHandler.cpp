/*------------------------------------------------------------------------------
Name:      ConnectionsHandler.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handles the I_XmlBlasterConnections 
------------------------------------------------------------------------------*/

#include <util/dispatch/ConnectionsHandler.h>
#include <util/Global.h>
#include <util/Timeout.hpp>
#include <util/Timestamp.h>
#include <util/Constants.h>
#include <util/lexical_cast.h>
#include <util/queue/QueueFactory.h>
#include <util/queue/PublishQueueEntry.h>
#include <util/queue/ConnectQueueEntry.h>
#include <util/queue/SubscribeQueueEntry.h>

namespace org { namespace xmlBlaster { namespace util { namespace dispatch {

using namespace std;
using namespace org::xmlBlaster::client::protocol;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::util::qos::storage;
using namespace org::xmlBlaster::util::queue;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

ConnectionsHandler::ConnectionsHandler(org::xmlBlaster::util::Global& global,
                                       const string& instanceName)
   : ME(string("ConnectionsHandler-") + instanceName), 
     connectQos_((ConnectQos*)0),
     connectReturnQos_((ConnectReturnQos*)0),
     status_(START), 
     global_(global), 
     log_(global.getLog("org.xmlBlaster.util.dispatch")),
     connectMutex_(),
     publishMutex_(),
     postSendListener_(0),
     instanceName_(instanceName)
{
   ClientQueueProperty prop(global_, "");
   connectionProblemsListener_ = NULL;
   connection_         = NULL;
   queue_              = NULL;
   retries_            = -1;
   currentRetry_       = 0;
   pingPollTimerKey_   = 0;
   doStopPing_         = false;
   if (log_.call()) log_.call(ME, "constructor");
}

ConnectionsHandler::~ConnectionsHandler()
{
   if (log_.call()) log_.call(ME, "destructor");
   if (pingPollTimerKey_ != 0) {
      global_.getPingTimer().removeTimeoutListener(pingPollTimerKey_);
      pingPollTimerKey_ = 0;
   }
   doStopPing_ = true;
   /*
   while (pingIsStarted_) {
      Thread::sleep(200);
   }
   */
   Lock lock(connectMutex_);
   string type = (connectQos_.isNull()) ? org::xmlBlaster::util::Global::getDefaultProtocol() : connectQos_->getAddress()->getType(); // "SOCKET"
   string version = "1.0"; // currently hardcoded
   if (connection_) {
      global_.getDispatchManager().releasePlugin(instanceName_, type, version);
      connection_ = NULL;
   }
   if ( queue_ ) {
      delete queue_;
      queue_ = NULL;
   }
   if (log_.trace()) log_.trace(ME, "destructor: going to delete the connectQos");
   status_ = END;
   if (log_.trace()) log_.trace(ME, "destructor ended");
} 


ConnectReturnQosRef ConnectionsHandler::connect(const ConnectQosRef& qos)
{
   if (log_.call()) log_.call(ME, string("::connect status is '") + lexical_cast<std::string>(status_) + "'");
   if (qos.isNull()) {
      throw XmlBlasterException(INTERNAL_ILLEGALARGUMENT, ME + "::connect", "your connectQos is null");
   }
   if (log_.dump()) log_.dump(ME, string("::connect, the qos is: ") + qos->toXml());
   Lock lock(connectMutex_);
   if (isConnected()) {
      log_.warn(ME, "connect: you are already connected");
      return connectReturnQos_;
   }

   connectQos_ = qos;

   global_.setSessionName(connectQos_->getSessionQos().getSessionName());
   global_.setImmutableId(connectQos_->getSessionQos().getRelativeName());
   global_.setId(connectQos_->getSessionQos().getAbsoluteName()); // temporary
   //log_.info(ME, "BEFORE id=" + global_.getId() + " immutable=" + global_.getImmutableId() + " sessionName=" + global_.getSessionName()->getAbsoluteName());

   retries_ = connectQos_->getAddress()->getRetries();
   long pingInterval = connectQos_->getAddress()->getPingInterval();
   if (log_.trace()) {
      log_.trace(ME, string("connect: number of retries during communication failure: ") + lexical_cast<std::string>(retries_));
      log_.trace(ME, string("connect: Ping Interval: ") + lexical_cast<std::string>(pingInterval));
   }

   string type = connectQos_->getAddress()->getType();
   string version = "1.0"; // currently hardcoded
   if (!connection_) {
      connection_ = &(global_.getDispatchManager().getPlugin(instanceName_, type, version));
   }

   try {
      connectReturnQos_ = connection_->connect(*connectQos_);
      global_.setSessionName(connectReturnQos_->getSessionQos().getSessionName());
      // For "joe/1" it remains immutable; For "joe" there is added the server side generated sessionId "joe/-33":
      global_.setImmutableId(connectReturnQos_->getSessionQos().getRelativeName());
      global_.setId(connectReturnQos_->getSessionQos().getAbsoluteName());
                //log_.info(ME, "AFTER id=" + global_.getId() + " immutable=" + global_.getImmutableId() + " sessionName=" + global_.getSessionName()->getAbsoluteName());
   }
   catch (XmlBlasterException &ex) {
      if ((ex.isCommunication() || ex.getErrorCodeStr().find("user.configuration") == 0)) {
         log_.warn(ME, "Got exception when connecting, polling now: " + ex.toString());
         if (pingPollTimerKey_ == 0)
            startPinger(false);
         return queueConnect();
      }
      else {
         if (log_.trace()) log_.trace(ME, string("the exception in connect is ") + ex.toXml());
         throw ex;
      }
   }                                                                                                                                                                                                                                                                                    
   
   log_.info(ME, string("successfully connected with sessionId = '") + connectReturnQos_->getSessionQos().getSecretSessionId() + "'");
   connectQos_->getSessionQos().setSecretSessionId(connectReturnQos_->getSessionQos().getSecretSessionId());

   enum States oldState = status_;
   status_ = ALIVE;
   if (connectionProblemsListener_) connectionProblemsListener_->reachedAlive(oldState, this);
   // start the ping if in failsafe, i.e. if delay > 0
   startPinger(false);
   if (log_.dump()) log_.dump(ME, string("::connect, the return qos is: ") + connectReturnQos_->toXml());

   flushQueue();

   return connectReturnQos_;
}

bool ConnectionsHandler::disconnect(const DisconnectQos& qos)
{
   Lock lock(connectMutex_);
   if (log_.call()) log_.call(ME, org::xmlBlaster::util::MethodName::DISCONNECT);
   if (log_.dump()) log_.dump(ME, string("::disconnect, the qos is: ") + qos.toXml());

   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, org::xmlBlaster::util::MethodName::DISCONNECT);
   if (status_ == DEAD) {
      log_.warn(ME, "already disconnected");
      return false;
   }
   if (putToQueue()) throw XmlBlasterException(COMMUNICATION_NOCONNECTION_POLLING, ME, org::xmlBlaster::util::MethodName::DISCONNECT);

   if (qos.getClearClientQueue() && queue_ != 0) queue_->clear();

   bool ret = connection_->disconnect(qos);
   enum States oldState = status_;
   status_ = DEAD;
   if (connectionProblemsListener_) connectionProblemsListener_->reachedDead(oldState, this);
   return ret;
}

string ConnectionsHandler::getProtocol()
{
   return connection_->getProtocol();
}

/*
string ConnectionsHandler::loginRaw()
{
   return connection_->loginRaw();
}
*/

bool ConnectionsHandler::shutdown()
{
   if (connection_) {
      return connection_->shutdown();
   }
   return false;
}

string ConnectionsHandler::getLoginName() 
{
   return connection_->getLoginName();
}

bool ConnectionsHandler::isLoggedIn()
{
   return connection_->isLoggedIn();
}

string ConnectionsHandler::ping(const string& qos)
{
//   Lock lock(connectionMutex_);
   return connection_->ping(qos);
}

SubscribeReturnQos ConnectionsHandler::subscribe(const SubscribeKey& key, const SubscribeQos& qos)
{
   if (log_.call()) log_.call(ME, MethodName::SUBSCRIBE);
   if (log_.dump()) log_.dump(ME, string("::subscribe, the key is: ") + key.toXml());
   if (log_.dump()) log_.dump(ME, string("::subscribe, the qos is: ") + qos.toXml());

//   Lock lock(connectionMutex_);

   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, MethodName::SUBSCRIBE);
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, MethodName::SUBSCRIBE);
   if (putToQueue()) return queueSubscribe(key, qos);
   try {
      SubscribeReturnQos ret = connection_->subscribe(key, qos);
      return ret;
   }   
   catch (XmlBlasterException& ex) {
      toPollingOrDead(&ex);
      if (putToQueue() && isRecoverable(&ex)) {
         log_.info(ME, string("::subscribe ") + key.getOid() + " is queued, exception=" + ex.getMessage());
         return queueSubscribe(key, qos);
      }
      else {
         log_.warn(ME, string("::subscribe failed throwing now exception: ") + key.toXml() + qos.toXml() + " exception=" + ex.getMessage());
         throw ex;
      }
   }
}


vector<MessageUnit> ConnectionsHandler::get(const GetKey& key, const GetQos& qos)
{
   if (log_.call()) log_.call(ME, "get");
   if (log_.dump()) log_.dump(ME, string("::get, the key is: ") + key.toXml());
   if (log_.dump()) log_.dump(ME, string("::get, the qos is: ") + qos.toXml());
   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "get");
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "get");
   if (putToQueue()) throw XmlBlasterException(COMMUNICATION_NOCONNECTION_POLLING, ME, "get");
   try {
      return connection_->get(key, qos);
   }   
   catch (XmlBlasterException& ex) {
      toPollingOrDead(&ex);
      throw ex;
   }
}


vector<UnSubscribeReturnQos> 
   ConnectionsHandler::unSubscribe(const UnSubscribeKey& key, const UnSubscribeQos& qos)
{
   if (log_.call()) log_.call(ME, org::xmlBlaster::util::MethodName::UNSUBSCRIBE);
   if (log_.dump()) log_.dump(ME, string("::unSubscribe, the key is: ") + key.toXml());
   if (log_.dump()) log_.dump(ME, string("::unSubscribe, the qos is: ") + qos.toXml());
   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, org::xmlBlaster::util::MethodName::UNSUBSCRIBE);
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, org::xmlBlaster::util::MethodName::UNSUBSCRIBE);
   if (putToQueue()) throw XmlBlasterException(COMMUNICATION_NOCONNECTION_POLLING, ME, org::xmlBlaster::util::MethodName::UNSUBSCRIBE);
   try {
      vector<UnSubscribeReturnQos> ret = connection_->unSubscribe(key, qos);
      return ret;
   }   
   catch (XmlBlasterException& ex) {
      toPollingOrDead(&ex);
      throw ex;
   }
}

bool ConnectionsHandler::putToQueue() {
   if (status_ == POLLING) return true;
   if (queue_ && queue_->getNumOfEntries() > 0) {
      return true; // guarantee sequence
   }
   return false;
}

PublishReturnQos ConnectionsHandler::publish(const MessageUnit& msgUnit)
{
   if (log_.call()) log_.call(ME, org::xmlBlaster::util::MethodName::PUBLISH);
   if (log_.dump()) log_.dump(ME, string("::publish, the msgUnit is: ") + msgUnit.toXml());
   Lock lock(publishMutex_);
   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, org::xmlBlaster::util::MethodName::PUBLISH);
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, org::xmlBlaster::util::MethodName::PUBLISH);
   if (putToQueue()) return queuePublish(msgUnit);
   try {
      // fill in the sender absolute name
      if (!connectReturnQos_.isNull()) {
         msgUnit.getQos().setSender(connectReturnQos_->getSessionQos().getSessionName());
      }
      return connection_->publish(msgUnit);
   }   
   catch (XmlBlasterException& ex) {
      toPollingOrDead(&ex);
      if (putToQueue() && isRecoverable(&ex)) {
         log_.info(ME, string("::publish ") + msgUnit.getKey().getOid() + " is queued, exception=" + ex.getMessage());
         return queuePublish(msgUnit);
      }
      else {
         log_.warn(ME, string("::publish failed throwing now exception, the msgUnit is: ") + msgUnit.toXml() + " exception=" + ex.getMessage());
         throw ex;
      }
   }
}


void ConnectionsHandler::publishOneway(const vector<MessageUnit> &msgUnitArr)
{
   if (log_.call()) log_.call(ME, "publishOneway");
   Lock lock(publishMutex_);

   // fill in the sender absolute name
   if (!connectReturnQos_.isNull()) {
      for (vector<MessageUnit>::size_type i=0;i<msgUnitArr.size();i++) {
         msgUnitArr[i].getQos().setSender(connectReturnQos_->getSessionQos().getSessionName());
      }
   }

   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "publishOneway");
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "publishOneway");
   if (putToQueue()) {
      for (size_t i=0; i < msgUnitArr.size(); i++) queuePublish(msgUnitArr[i]);
   }

   try {
      connection_->publishOneway(msgUnitArr);
   }   
   catch (XmlBlasterException& ex) {
      toPollingOrDead(&ex);
      if (putToQueue() && isRecoverable(&ex)) {
         for (size_t i=0; i < msgUnitArr.size(); i++) queuePublish(msgUnitArr[i]);
      }
      else
         throw ex;
   }
}


vector<PublishReturnQos> ConnectionsHandler::publishArr(const vector<MessageUnit> &msgUnitArr)
{
   if (log_.call()) log_.call(ME, "publishArr");
   Lock lock(publishMutex_);

   // fill in the sender absolute name
   if (!connectReturnQos_.isNull()) {
      for (vector<MessageUnit>::size_type i=0;i<msgUnitArr.size();i++) {
         msgUnitArr[i].getQos().setSender(connectReturnQos_->getSessionQos().getSessionName());
      }
   }

   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "publishArr");
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "publishArr");
   if (putToQueue()) {
      vector<PublishReturnQos> retQos;
      for (size_t i=0; i < msgUnitArr.size(); i++) {
         retQos.insert(retQos.end(), queuePublish(msgUnitArr[i]));
      }
      return retQos;
   }
   try {
      return connection_->publishArr(msgUnitArr);
   }   
   catch (XmlBlasterException& ex) {
      toPollingOrDead(&ex);
      if (putToQueue() && isRecoverable(&ex)) {
         vector<PublishReturnQos> retQos;
         for (size_t i=0; i < msgUnitArr.size(); i++) {
            retQos.insert(retQos.end(), queuePublish(msgUnitArr[i]));
         }
         return retQos;
      }
      else throw ex;
   }
}


vector<EraseReturnQos> ConnectionsHandler::erase(const EraseKey& key, const EraseQos& qos)
{
   if (log_.call()) log_.call(ME, org::xmlBlaster::util::MethodName::ERASE);
   if (log_.dump()) log_.dump(ME, string("::erase, the key is: ") + key.toXml());
   if (log_.dump()) log_.dump(ME, string("::erase, the qos is: ") + qos.toXml());

   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, org::xmlBlaster::util::MethodName::ERASE);
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, org::xmlBlaster::util::MethodName::ERASE);
   if (putToQueue()) throw XmlBlasterException(COMMUNICATION_NOCONNECTION_POLLING, ME, org::xmlBlaster::util::MethodName::ERASE);

   try {
      return connection_->erase(key, qos);
   }   
   catch (XmlBlasterException& ex) {
      toPollingOrDead(&ex);
      throw ex;
   }
}

void ConnectionsHandler::initFailsafe(I_ConnectionProblems* connectionProblems)
{
//   Lock lock(connectionMutex_);
   if (log_.trace()) log_.trace(ME, "Register initFailsafe " + lexical_cast<string>(connectionProblems!=0));
   connectionProblemsListener_ = connectionProblems;
}

// If recoverable we queue a msgUnit, else we throw an exception
bool ConnectionsHandler::isRecoverable(const org::xmlBlaster::util::XmlBlasterException* reason)
{
        // TODO: Authorization could also be recoverable (by a server admin)
        //       Such decision must be left to the user (we need a callback to the user here)
        // As a default all communication problems are assumed to be recoverable
        if (reason == 0)
                return true;
        bool ret = reason->isCommunication();
    if (log_.call()) log_.call(ME, "isRecoverable " + lexical_cast<string>(ret));
        return ret;
}

void ConnectionsHandler::toDead(const org::xmlBlaster::util::XmlBlasterException* reason)
{
   if (log_.call()) log_.call(ME, "toDead");
   enum States oldState = status_;
   log_.info(ME, "going into DEAD status" + ((reason != 0) ? (": " + reason->getMessage()) : ""));
   status_ = DEAD;
   connection_->shutdown(); // close socket/corba connection
   if (connectionProblemsListener_) connectionProblemsListener_->reachedDead(oldState, this);
}

void ConnectionsHandler::toPolling(const org::xmlBlaster::util::XmlBlasterException* reason)
{
   log_.info(ME, "going into POLLING status:" + ((reason != 0) ? (": " + reason->getMessage()) : ""));
   enum States oldState = status_;
   status_ = POLLING;
   currentRetry_ = 0;
   /*
   try {
      DisconnectQos discQos(global_);
      connection_->disconnect(discQos);
   }
   catch (...) {
      log_.warn(ME, "exception when trying to disconnect");
   }
   */
   connection_->shutdown(); // close socket/corba connection
   if (connectionProblemsListener_) connectionProblemsListener_->reachedPolling(oldState, this);
   startPinger(true);
}

void ConnectionsHandler::toPollingOrDead(const org::xmlBlaster::util::XmlBlasterException* reason)
{
   if (reason == 0)
      return;
   if (!reason->isCommunication())
      return;

   if (log_.call()) log_.call(ME, "toPollingOrDead");

   if (!isFailsafe()) {
      log_.info(ME, "going into DEAD status since not in failsafe mode. "
                    "For failsafe mode set 'delay' to a positive long value, for example on the cmd line: -delay 10000" +
                    ((reason != 0) ? (": " + reason->getMessage()) : ""));
      toDead(reason);
      return;
   }

   toPolling(reason);
}


void ConnectionsHandler::timeout(void * /*userData*/)
{
                                                    
  Lock lock(connectMutex_);
   pingPollTimerKey_ = 0;
   if (doStopPing_) return; // then it must stop
   if ( log_.call() ) log_.call(ME, string("ping timeout occured with status '") + getStatusString() + "'" );
   if (status_ == ALIVE) { // then I am pinging
      if ( log_.trace() ) log_.trace(ME, "ping timeout: status is 'ALIVE'");
      try {
         if (connection_) {
            connection_->ping("<qos/>");
            if ( log_.trace() ) log_.trace(ME, "lowlevel ping returned: status is 'ALIVE'");
            startPinger(false);
         }
      }
      catch (XmlBlasterException& ex) {
         if ( log_.trace() ) log_.trace(ME, "lowlevel ping failed: " + ex.toString());
         toPollingOrDead(&ex);
      }
      return;
   }
 
   if (status_ == POLLING) {
      if ( log_.trace() ) log_.trace(ME, "ping timeout: status is 'POLLING'");
      try {
         if (connection_ && !connectQos_.isNull()) {
            if ( log_.trace() ) log_.trace(ME, "ping timeout: going to retry a connection");
 
            string lastSessionId = connectQos_->getSessionQos().getSecretSessionId();
            connectReturnQos_ = connection_->connect(*connectQos_);
            if (log_.trace()) log_.trace(ME, string("Successfully reconnected, ConnectRetQos: ") + connectReturnQos_->toXml());
            string sessionId = connectReturnQos_->getSessionQos().getSecretSessionId();
            log_.info(ME, string("Successfully reconnected as '") + connectReturnQos_->getSessionQos().getAbsoluteName() +
                          "' after " + lexical_cast<string>(currentRetry_) + " attempts");
            connectQos_->getSessionQos().setSecretSessionId(sessionId);
 
            if ( log_.trace() ) {
               log_.trace(ME, string("ping timeout: re-connection, the new connect returnQos: ") + connectReturnQos_->toXml());
            }
 
            bool doFlush = true;
            enum States oldState = status_;
            status_ = ALIVE;
            if ( connectionProblemsListener_ ) doFlush = connectionProblemsListener_->reachedAlive(oldState, this);
 
            Lock lockPub(publishMutex_); // lock here to avoid publishing while flushing queue (to ensure sequence)
            if (sessionId != lastSessionId) {
               log_.trace(ME, string("When reconnecting the sessionId changed from '") + lastSessionId + "' to '" + sessionId + "'");
            }
 
            if (doFlush) {
               try {
                  flushQueueUnlocked(queue_, true);
               }
               catch (const XmlBlasterException &ex) {
                  log_.warn(ME, "An exception occured when trying to asynchronously flush the contents of the queue. Probably not all messages have been sent. These unsent messages are still in the queue:" + ex.getMessage());
               }
               catch (...) {
                  log_.warn(ME, "An exception occured when trying to asynchronously flush the contents of the queue. Probably not all messages have been sent. These unsent messages are still in the queue");
               }
            }
            startPinger(false);
         }
      }
      catch (XmlBlasterException ex) {
         if (log_.trace()) log_.trace(ME, "timeout got exception: " + ex.getMessage());
         currentRetry_++;
         if ( currentRetry_ < retries_ || retries_ < 0) { // continue to poll
            startPinger(false);
         }
         else {
            enum States oldState = status_;
            status_ = DEAD;
            if ( connectionProblemsListener_ ) {
               connectionProblemsListener_->reachedDead(oldState, this);
               // stopping
            }
         }
      }
      return;
   }
 
   // if it comes here it will stop
 
}

SubscribeReturnQos ConnectionsHandler::queueSubscribe(const SubscribeKey& key, const SubscribeQos& qos)
{
   if (!queue_) {
      if (connectQos_.isNull()) {
         throw XmlBlasterException(INTERNAL_SUBSCRIBE, ME + "::queueSubscribe", "need to create a queue but the connectQos is NULL (probably never connected)");
      }
      if (log_.trace()) log_.trace(ME+":queueSubscribe", "creating a client queue ...");
      queue_ = &QueueFactory::getFactory().getPlugin(global_, connectQos_->getClientQueueProperty());
      if (log_.trace()) log_.trace(ME+":queueSubscribe", "created a client queue");
   }
   SubscribeReturnQos retQos(global_);
   SubscribeQos& q = const_cast<SubscribeQos&>(qos);
   SessionNameRef sessionName = global_.getSessionName();
   std::string subscriptionId = q.generateSubscriptionId(sessionName, key);
   retQos.getData().setSubscriptionId(subscriptionId);
   retQos.getData().setState(Constants::STATE_OK);
   retQos.getData().setStateInfo(Constants::INFO_QUEUED); // "QUEUED"
   qos.setSubscriptionId(subscriptionId);
   SubscribeQueueEntry entry(global_, key, qos, qos.getData().getPriority());
   queue_->put(entry);
   //if (log_.trace()) 
      log_.warn(ME, string("queueSubscribe: entry '") + key.getOid() +
                     "' has been queued with client side generated subscriptionId=" + subscriptionId);
   return retQos;
}

PublishReturnQos ConnectionsHandler::queuePublish(const MessageUnit& msgUnit)
{
   if (log_.call()) log_.call(ME, "queuePublish");
   if (!queue_) {
      if (connectQos_.isNull()) {
         throw XmlBlasterException(INTERNAL_PUBLISH, ME + "::queuePublish", "need to create a queue but the connectQos is NULL (probably never connected)");
      }
      if (log_.trace()) log_.trace(ME+":queuePublish", "creating a client queue ...");
      queue_ = &QueueFactory::getFactory().getPlugin(global_, connectQos_->getClientQueueProperty());
      if (log_.trace()) log_.trace(ME+":queuePublish", "created a client queue");
   }
   if (log_.trace()) 
      log_.trace(ME, string("queuePublish: entry '") + msgUnit.getKey().getOid() + "' has been queued");
   PublishReturnQos retQos(global_);
   retQos.setKeyOid(msgUnit.getKey().getOid());
   retQos.setState(Constants::STATE_OK);
   retQos.getData().setStateInfo(Constants::INFO_QUEUED); // "QUEUED"
   PublishQueueEntry entry(global_, msgUnit, msgUnit.getQos().getPriority());
   queue_->put(entry);
   return retQos;
}

ConnectReturnQosRef& ConnectionsHandler::queueConnect()
{
   if (log_.call()) log_.call(ME, string("::queueConnect with sessionQos: '") + connectQos_->getSessionQos().getAbsoluteName() + "'");
   long tmp = connectQos_->getSessionQos().getPubSessionId(); 
   if ( tmp <= 0) {
      if (log_.trace()) log_.trace(ME, string("::queueConnect, the public session id is '") + lexical_cast<std::string>(tmp));
      throw XmlBlasterException(USER_CONNECT, ME + "::queueConnect", "queueing connection request not possible because you did not specify a positive public sessionId");
   }

   if (!queue_) {
      if (log_.trace()) log_.info(ME, "::queueConnect: created a client queue");
      queue_ = &QueueFactory::getFactory().getPlugin(global_, connectQos_->getClientQueueProperty());
   }
   if (log_.trace()) 
      log_.trace(ME, string("queueConnect: entry '") + connectQos_->getSessionQos().getAbsoluteName() + "' has been queued");

   connectReturnQos_ = new ConnectReturnQos(*connectQos_);

   /* Michele thinks we should not queue the ConnectQos
   ConnectQueueEntry entry(global_, *connectQos_);
   queue_->put(entry);
   */
   enum States oldState = status_;
   status_ = POLLING;
   if ( connectionProblemsListener_ ) {
      connectionProblemsListener_->reachedPolling(oldState, this);
      // stopping
   }
   startPinger(true);
   return connectReturnQos_;
}

I_PostSendListener* ConnectionsHandler::registerPostSendListener(I_PostSendListener *listener) {
   I_PostSendListener* old = postSendListener_; 
   postSendListener_ = listener;
   return old;
}

/**
 * Flushes all entries in the queue, i.e. the entries of the queue are sent to xmlBlaster.
 * If the queue is empty or NULL, then 0 is returned. If the state is in POLLING or DEAD, or the 
 * connection is not established yet (i.e. connection_ = NULL),  then -1 is
 * returned.. This method blocks until all entries in the queue have been sent.
 */
long ConnectionsHandler::flushQueue()
{
   if (log_.call()) log_.call(ME, "flushQueue");
   //   Lock lock(connectionMutex_);

   if (!queue_) {
      if (connectQos_.isNull()) {
         log_.error(ME+".flusgQueue", "need to create a queue but the connectQos is NULL (probably never connected)");
      }
      if (log_.trace()) log_.trace(ME+".flushQueue", "creating the client queue ...");
      queue_ = &QueueFactory::getFactory().getPlugin(global_, connectQos_->getClientQueueProperty());
      if (queue_->getNumOfEntries() < 1) {
         if (log_.trace()) log_.trace(ME+".flushQueue", "Created queue [" + queue_->getType() + "][" + queue_->getVersion() +
                                                        "], it is empty, nothing to do.");
         return 0;
      }
      log_.info(ME, "Created queue [" + queue_->getType() + "][" + queue_->getVersion() + "] which contains " +
                    lexical_cast<string>(queue_->getNumOfEntries()) + " entries.");
   }

   return flushQueueUnlocked(queue_, true);
}  

// Notify client code
bool ConnectionsHandler::sendingFailedNotification(const std::vector<org::xmlBlaster::util::queue::EntryType> &entries, const org::xmlBlaster::util::XmlBlasterException &exception)
{
    I_PostSendListener *p = postSendListener_;
    bool isHandled = false; 
    if (p) {
       try {
          isHandled = p->sendingFailed(entries, exception); // Notify client code
       }
       catch (...) {
          log_.error(ME, "flushQueueUnlocked(async sending mode): Ignoring exception thrown from user code sendingFailed(...)");
       }
    }
    return isHandled;
}


/**
 * Called synchronously by connect() 
 * or async by timeout of reconnect poller
 */
long ConnectionsHandler::flushQueueUnlocked(I_Queue *queueToFlush, bool doRemove)
{
   if ( log_.call() ) log_.call(ME, "flushQueueUnlocked");
           if (!queueToFlush || queueToFlush->empty()) return 0;
   if (status_ != ALIVE || connection_ == NULL) return -1;

   long ret = 0;
   if (!queueToFlush->empty()) {
      log_.info(ME, "Queue [" + queue_->getType() + "][" + queue_->getVersion() + "] contains " +
                  lexical_cast<string>(queue_->getNumOfEntries()) + " entries, we send them to the server");
   }
   while (!queueToFlush->empty()) { 
      long maxNumOfEntries= (doRemove) ? 1 : -1; // doRemove==false makes no sense, TODO: remove this arg
      if (log_.trace()) log_.trace(ME, "flushQueueUnlocked: flushing one priority sweep maxNumOfEntries=" + lexical_cast<string>(maxNumOfEntries));
      const vector<EntryType> entries = queueToFlush->peekWithSamePriority(maxNumOfEntries);
      vector<EntryType>::const_iterator iter = entries.begin();
      bool isHandled = false;
      while (iter != entries.end()) {
         const EntryType entry = (*iter);
         iter++;
         const MsgQueueEntry &entry2 = *entry;

         try {
            if (log_.trace()) log_.trace(ME, "sending the content to xmlBlaster: " + entry->toXml());
            {
               MsgQueueEntry &entry3 = const_cast<MsgQueueEntry&>(entry2);
               entry3.setSender(connectReturnQos_->getSessionQos().getSessionName());
            }
            entry2.send(*this); // entry2 contains the PublishReturnQos after calling send
            isHandled = false;
            if (log_.trace()) log_.trace(ME, "content to xmlBlaster successfully sent");
         }
         catch (XmlBlasterException &ex) {
            if (ex.isCommunication()) {
               toPollingOrDead(&ex);
               throw ex; // Terminate timer thread //return;
            }
            log_.warn(ME, "flushQueueUnlocked(async sending mode): can't send queued message " + entry->toXml() + " to server: " + ex.getMessage());
            isHandled = sendingFailedNotification(entries, ex); // Notify client code 
            if (isHandled) {
               if (log_.trace()) log_.trace(ME, "message is handled by user code, we remove it now from queue: " + entry->toXml());
            }
            else {
               toDead(&ex);
               // Instead of toDead we should set dispatcher active false so that a C++ client can later reactivate it?
               throw ex; // Up to timer thread or back to user
            }
         }
         catch (...) {
            XmlBlasterException ex = XmlBlasterException(INTERNAL_UNKNOWN, ME, "flushQueueUnlocked(async sending mode): can't send queued message, reason is unknown");
            log_.warn(ME, "flushQueueUnlocked(async sending mode): can't send queued message " + entry->toXml() + " to server because of unknown exception");
            isHandled = sendingFailedNotification(entries, ex); // Notify client code 
            if (isHandled) {
               if (log_.trace()) log_.trace(ME, "message is handled by user code, we remove it now from queue: " + entry->toXml());
            }
            else {
               toDead(&ex);
               // We should set dispatcher active false so that a C++ client can later reactivate it?
               throw ex;
            }
         }
      }
      if (doRemove) {
          //log_.trace(ME, "remove send message from client queue");
          ret += queueToFlush->randomRemove(entries.begin(), entries.end());
      }
      if (!isHandled) {
              I_PostSendListener *p = postSendListener_;
              if (p) {
                  p->postSend(entries);
              }
      }
   }
   return ret;
}

I_Queue* ConnectionsHandler::getQueue()
{
   if (!queue_) {
      if (log_.trace()) log_.trace(ME+".getQueue", "creating the client queue ...");
      queue_ = &QueueFactory::getFactory().getPlugin(global_, connectQos_->getClientQueueProperty());
      log_.info(ME, "Created queue [" + queue_->getType() + "][" + queue_->getVersion() + "] which contains " +
                    lexical_cast<string>(queue_->getNumOfEntries()) + " entries.");
   }
   return queue_;
}

bool ConnectionsHandler::isFailsafe() const
{
   if (connectQos_.isNull()) return false;
   return connectQos_->getAddress()->getDelay() > 0;
}

// pinger or poller
bool ConnectionsHandler::startPinger(bool withInitialPing)
{
   if (log_.call()) log_.call(ME, "startPinger");
   if (doStopPing_) return false;

   if (log_.trace()) log_.trace(ME, "startPinger (no request to stop the pinger is active for the moment)");
   if (pingPollTimerKey_ != 0 && !withInitialPing) {
      if (log_.trace()) log_.trace(ME, "startPinger: the pinger is already running. I will return without starting a new thread");
      return false;  
   }

   long delay        = 10000;
   long pingInterval = 0;
   if (connectQos_.isNull()) {
      ConnectQos tmp(global_);
      delay        = tmp.getAddress()->getDelay();
      pingInterval = tmp.getAddress()->getPingInterval();
   }
   else {
      delay        = connectQos_->getAddress()->getDelay();
      pingInterval = connectQos_->getAddress()->getPingInterval();
   }
   if (log_.trace()) {
      log_.trace(ME, string("startPinger(status=") + 
               getStatusString() +
               "): parameters are: delay '" + lexical_cast<std::string>(delay) +
               "' and pingInterval '" + lexical_cast<std::string>(pingInterval) +
               " withInitialPing=" + lexical_cast<string>(withInitialPing));
   }
   if (delay > 0 && pingInterval > 0) {
      long delta = delay;
      if (status_ == ALIVE) delta = pingInterval;
      if (withInitialPing) delta = 400;
      pingPollTimerKey_ = global_.getPingTimer().addOrRefreshTimeoutListener(this, delta, NULL, pingPollTimerKey_);
   }
   return true;
}

string ConnectionsHandler::getStatusString() const
{
   if (status_ == ALIVE) return "ALIVE";
   else if (status_ == POLLING) return "POLLING";
   else if (status_ == DEAD) return "DEAD";
   else if (status_ == START) return "START";
   return "END";;
}


bool ConnectionsHandler::isConnected() const
{
   return status_ == ALIVE || status_ == POLLING;
}

bool ConnectionsHandler::isAlive() const
{
   return status_ == ALIVE;
}

bool ConnectionsHandler::isPolling() const
{
   return status_ == POLLING;
}

bool ConnectionsHandler::isDead() const
{
   return status_ == DEAD;
}

ConnectReturnQosRef ConnectionsHandler::connectRaw(const ConnectQosRef& connectQos)
{
   if (log_.call()) log_.call(ME, "::connectRaw");
   connectReturnQos_ = connection_->connect(connectQos);
   connectQos_ = connectQos;
   log_.info(ME, string("Successfully connected with sessionId = '") + connectReturnQos_->getSessionQos().getSecretSessionId() + "'");
   connectQos_->getSessionQos().setSecretSessionId(connectReturnQos_->getSessionQos().getSecretSessionId());
   return connectReturnQos_;
}


I_XmlBlasterConnection& ConnectionsHandler::getConnection() const
{
   if (!connection_) {
      throw XmlBlasterException(INTERNAL_ILLEGALARGUMENT, ME + "::getConnection", "the connection is still NULL: it is not assigned yet. You probably called this method before a connection was made");
   }
   return *connection_;
}


ConnectReturnQosRef ConnectionsHandler::getConnectReturnQos()
{
   return connectReturnQos_;
}

ConnectQosRef ConnectionsHandler::getConnectQos()
{
   return connectReturnQos_; // contains everything and is typedef on ConnectQos
}

/*
void ConnectionsHandler::setConnectReturnQos(const connectReturnQos& retQos)
{
   if (connectReturnQos_)  {
      delete connectReturnQos_;
      connectReturnQos_ = NULL;
   }
   connectReturnQos_ = new ConnectReturnQos(retQos);
}
*/

}}}} // namespaces


