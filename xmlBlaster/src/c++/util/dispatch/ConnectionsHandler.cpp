/*------------------------------------------------------------------------------
Name:      ConnectionsHandler.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handles the I_XmlBlasterConnections 
------------------------------------------------------------------------------*/

#include <util/dispatch/ConnectionsHandler.h>
#include <util/Global.h>
#include <util/Timeout.h>
#include <boost/lexical_cast.hpp>

using namespace boost;

using namespace org::xmlBlaster::client::protocol;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::util::thread;

namespace org { namespace xmlBlaster { namespace util { namespace dispatch {

ConnectionsHandler::ConnectionsHandler(Global& global, const string& instanceName)
   : ME(string("ConnectionsHandler-") + instanceName), 
     status_(START), 
     global_(global), 
     log_(global.getLog("dispatch")),
     connectMutex_(),
     publishMutex_(),
     instanceName_(instanceName)
{
   QueueProperty prop(global_, "");
   adminQueue_ = new MsgQueue(global, prop);
   connectQos_         = NULL;
   connectionProblems_ = NULL;
   connectReturnQos_   = NULL;
   connection_         = NULL;
   queue_              = NULL;
   retries_            = -1;
   currentRetry_       = 0;
   timestamp_          = 0;
   pingIsStarted_      = false;
   lastSessionId_      = "";
   doStopPing_         = false;
}

ConnectionsHandler::~ConnectionsHandler()
{
   doStopPing_ = true;
   while (pingIsStarted_) {
      Thread::sleep(200);
   }
   Lock lock(connectMutex_);
   string type = connectQos_->getServerRef().getType();
   string version = "1.0"; // currently hardcoded
   if (connection_) {
      global_.getDeliveryManager().releasePlugin(instanceName_, type, version);
      connection_ = NULL;
   }

   if (timestamp_ != 0) {
      global_.getPingTimer().removeTimeoutListener(timestamp_);
      timestamp_ = 0;
   }
   if ( queue_ ) {
      delete queue_;
      queue_ = NULL;
   }
   delete adminQueue_;
   adminQueue_ = NULL;
   delete connectQos_;
   delete connectReturnQos_;
   status_ = END;
} 


ConnectReturnQos ConnectionsHandler::connect(const ConnectQos& qos)
{
   if (log_.CALL) log_.call(ME, string("::connect status is '") + lexical_cast<string>(status_) + "'");
   if (log_.DUMP) log_.dump(ME, string("::connect, the qos is: ") + qos.toXml());
   Lock lock(connectMutex_);
   if (isConnected()) {
      log_.warn(ME, "connect: you are already connected");
      return *connectReturnQos_;
   }

   if (connectQos_) {
      delete connectQos_;
      connectQos_ = NULL;
   }
   connectQos_ = new ConnectQos(qos);
   retries_ = connectQos_->getAddress().getRetries();
   long pingInterval = connectQos_->getAddress().getPingInterval();
   if (log_.TRACE) {
      log_.trace(ME, string("connect: number of retries during communication failure: ") + lexical_cast<string>(retries_));
      log_.trace(ME, string("connect: Ping Interval: ") + lexical_cast<string>(pingInterval));
   }

   string type = connectQos_->getServerRef().getType();
   string version = "1.0"; // currently hardcoded
   if (!connection_) {
      connection_ = &(global_.getDeliveryManager().getPlugin(instanceName_, type, version));
   }
   if (connectReturnQos_) {
      delete connectReturnQos_;
      connectReturnQos_ = NULL;
   }

   try {
      connectReturnQos_ = new ConnectReturnQos(connection_->connect(*connectQos_));
   }
   catch (XmlBlasterException &ex) {
      if (log_.TRACE) log_.trace(ME, "exception occured when connecting");
      if ( ex.isCommunication() ) return queueConnect();
   }                                                                                                                                                                                                                                                                                    
   
   lastSessionId_ = connectReturnQos_->getSessionQos().getSecretSessionId();
   log_.info(ME, string("successfully connected with sessionId = '") + lastSessionId_ + "'");

   /*
   SessionQos tmp = connectQos_->getSessionQos();
   tmp.setSecretSessionId(lastSessionId_);
   connectQos_->setSessionQos(tmp);
   */
   connectQos_->getSessionQos().setSecretSessionId(lastSessionId_);

   if (log_.TRACE) {
      log_.trace(ME, string("return qos after connection: ") + connectReturnQos_->toXml());
   }
   enum States oldState = status_;
   status_ = CONNECTED;
   if (connectionProblems_) connectionProblems_->reachedAlive(oldState, this);
   // start the ping if in failsafe, i.e. if delay > 0
   startPinger();
   if (log_.DUMP) log_.dump(ME, string("::connect, the return qos is: ") + connectReturnQos_->toXml());
   return *connectReturnQos_;
}

bool ConnectionsHandler::disconnect(const DisconnectQos& qos)
{
   Lock lock(connectMutex_);
   if (log_.CALL) log_.call(ME, "disconnect");
   if (log_.DUMP) log_.dump(ME, string("::disconnect, the qos is: ") + qos.toXml());

   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "disconnect");
   if (status_ == POLLING) throw XmlBlasterException(COMMUNICATION_NOCONNECTION_POLLING, ME, "disconnect");
   if (status_ == DEAD) {
      log_.warn(ME, "already disconnected");
      return false;
   }

   bool ret = connection_->disconnect(qos);
   status_ = DEAD;
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
   return connection_->shutdown();
}

void ConnectionsHandler::resetConnection()
{
   return connection_->resetConnection();
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
   if (log_.CALL) log_.call(ME, "subscribe");
   if (log_.DUMP) log_.dump(ME, string("::subscribe, the key is: ") + key.toXml());
   if (log_.DUMP) log_.dump(ME, string("::subscribe, the qos is: ") + qos.toXml());

//   Lock lock(connectionMutex_);

   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "subscribe");
   if (status_ == POLLING) throw XmlBlasterException(COMMUNICATION_NOCONNECTION_POLLING, ME, "subscribe");
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "subscribe");
   try {
      SubscribeReturnQos ret = connection_->subscribe(key, qos);
      SubscribeQueueEntry entry(global_, key, qos);
      adminQueue_->put(entry);
      return ret;
   }   
   catch (XmlBlasterException& ex) {
      if ( ex.isCommunication() ) toPollingOrDead();
      throw ex;
   }
}


vector<MessageUnit> ConnectionsHandler::get(const GetKey& key, const GetQos& qos)
{
   if (log_.CALL) log_.call(ME, "get");
   if (log_.DUMP) log_.dump(ME, string("::get, the key is: ") + key.toXml());
   if (log_.DUMP) log_.dump(ME, string("::get, the qos is: ") + qos.toXml());
   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "get");
   if (status_ == POLLING) throw XmlBlasterException(COMMUNICATION_NOCONNECTION_POLLING, ME, "get");
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "get");
   try {
      return connection_->get(key, qos);
   }   
   catch (XmlBlasterException& ex) {
      if ( ex.isCommunication() ) toPollingOrDead();
      throw ex;
   }
}


vector<UnSubscribeReturnQos> 
   ConnectionsHandler::unSubscribe(const UnSubscribeKey& key, const UnSubscribeQos& qos)
{
   if (log_.CALL) log_.call(ME, "unSubscribe");
   if (log_.DUMP) log_.dump(ME, string("::unSubscribe, the key is: ") + key.toXml());
   if (log_.DUMP) log_.dump(ME, string("::unSubscribe, the qos is: ") + qos.toXml());
   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "unSubscribe");
   if (status_ == POLLING) throw XmlBlasterException(COMMUNICATION_NOCONNECTION_POLLING, ME, "unSubscribe");
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "unSubscribe");
   try {
      vector<UnSubscribeReturnQos> ret = connection_->unSubscribe(key, qos);
      UnSubscribeQueueEntry entry(global_, key, qos);
      adminQueue_->put(entry);
      return ret;
   }   
   catch (XmlBlasterException& ex) {
      if ( ex.isCommunication() ) toPollingOrDead();
      throw ex;
   }
}


PublishReturnQos ConnectionsHandler::publish(const MessageUnit& msgUnit)
{
   if (log_.CALL) log_.call(ME, "publish");
   if (log_.DUMP) log_.dump(ME, string("::publish, the msgUnit is: ") + msgUnit.toXml());
   Lock lock(publishMutex_);
   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "publish");
   if (status_ == POLLING) return queuePublish(msgUnit);
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "publish");
   try {
      // fill in the sender absolute name
      if (connectReturnQos_) {
         msgUnit.getQos().setSender(connectReturnQos_->getSessionQos());
      }
      return connection_->publish(msgUnit);
      if (log_.TRACE) log_.trace(ME, "publish successful");
   }   
   catch (XmlBlasterException& ex) {
      if ( ex.isCommunication() ) {
         toPollingOrDead();
         return queuePublish(msgUnit);
      }
      else throw ex;
   }
}


void ConnectionsHandler::publishOneway(const vector<MessageUnit> &msgUnitArr)
{
   if (log_.CALL) log_.call(ME, "publishOneway");
   Lock lock(publishMutex_);

   // fill in the sender absolute name
   if (connectReturnQos_) {
                for (int i=0;i<msgUnitArr.size();i++) {
         msgUnitArr[i].getQos().setSender(connectReturnQos_->getSessionQos());
      }
   }

   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "publishOneway");
   if (status_ == POLLING) {
      for (size_t i=0; i < msgUnitArr.size(); i++) queuePublish(msgUnitArr[i]);
   }
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "publishOneway");

   try {
      connection_->publishArr(msgUnitArr);
   }   
   catch (XmlBlasterException& ex) {
      if ( ex.isCommunication() ) {
         toPollingOrDead();
         for (size_t i=0; i < msgUnitArr.size(); i++) queuePublish(msgUnitArr[i]);
      }
      else throw ex;
   }
}


vector<PublishReturnQos> ConnectionsHandler::publishArr(vector<MessageUnit> msgUnitArr)
{
   if (log_.CALL) log_.call(ME, "publishArr");
   Lock lock(publishMutex_);

   // fill in the sender absolute name
   if (connectReturnQos_) {
                for (int i=0;i<msgUnitArr.size();i++) {
         msgUnitArr[i].getQos().setSender(connectReturnQos_->getSessionQos());
      }
   }

   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "publishArr");
   if (status_ == POLLING) {
      vector<PublishReturnQos> retQos;
      for (size_t i=0; i < msgUnitArr.size(); i++) {
         retQos.insert(retQos.end(), queuePublish(msgUnitArr[i]));
      }
      return retQos;
   }
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "publishArr");
   try {
      return connection_->publishArr(msgUnitArr);
   }   
   catch (XmlBlasterException& ex) {
      if ( ex.isCommunication() ) {
         toPollingOrDead(); 
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
   if (log_.CALL) log_.call(ME, "erase");
   if (log_.DUMP) log_.dump(ME, string("::erase, the key is: ") + key.toXml());
   if (log_.DUMP) log_.dump(ME, string("::erase, the qos is: ") + qos.toXml());

   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "erase");
   if (status_ == POLLING) throw XmlBlasterException(COMMUNICATION_NOCONNECTION_POLLING, ME, "erase");
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "erase");

   try {
      return connection_->erase(key, qos);
   }   
   catch (XmlBlasterException& ex) {
      if ( ex.isCommunication() ) toPollingOrDead();
      throw ex;
   }
}

void ConnectionsHandler::initFailsafe(I_ConnectionProblems* connectionProblems)
{
//   Lock lock(connectionMutex_);
   connectionProblems_ = connectionProblems;
}

void ConnectionsHandler::toPollingOrDead()
{
   enum States oldState = status_;
   if (!isFailsafe()) {
      log_.info(ME, "going into DEAD status since not in failsafe mode. For failsafe mode set 'delay' to a positive long value, for example on the cmd line: -delay 10000");
      status_ = DEAD;
      connection_->shutdown();
      if (connectionProblems_) connectionProblems_->reachedDead(oldState, this);
      return;
   }

   log_.info(ME, "going into POLLING status");
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
   connection_->shutdown();
   if (connectionProblems_) connectionProblems_->reachedPolling(oldState, this);
   startPinger();
}


void ConnectionsHandler::timeout(void *userData)
{

//  Lock lock(publishMutex_);
   pingIsStarted_ = false;
   if (doStopPing_) return; // then it must stop
   timestamp_ = 0;
   if ( log_.CALL ) log_.call(ME, "ping timeout occured");
   if (status_ == CONNECTED) { // then I am pinging
      if ( log_.TRACE ) log_.trace(ME, "ping timeout: status is 'CONNECTED'");
      try {
         if (connection_) {
            connection_->ping("<qos/>");
            startPinger();
         }
      }
      catch (XmlBlasterException& ex) {
         toPollingOrDead();
      }
      return;
   }
 
   if (status_ == POLLING) {
      if ( log_.TRACE ) log_.trace(ME, "ping timeout: status is 'POLLING'");
      try {
         if ((connection_) && (connectQos_)) {
            if ( log_.TRACE ) log_.trace(ME, "ping timeout: going to retry a connection");
 
            ConnectReturnQos retQos = connection_->connect(*connectQos_);
            if (connectReturnQos_) delete connectReturnQos_;
            connectReturnQos_ = new ConnectReturnQos(retQos);
            string sessionId = connectReturnQos_->getSessionQos().getSecretSessionId();
            log_.info(ME, string("successfully re-connected with sessionId = '") + sessionId + "', the connectQos was: " + connectQos_->toXml());
 
            if ( log_.TRACE ) {
               log_.trace(ME, "ping timeout: re-connection was successful");
               log_.trace(ME, string("ping timeout: the new connect returnQos: ") + connectReturnQos_->toXml());
            }
 
            bool doFlush = true;
            enum States oldState = status_;
            status_ = CONNECTED;
            if ( connectionProblems_ ) doFlush = connectionProblems_->reachedAlive(oldState, this);
 
            Lock lock(publishMutex_); // lock here to avoid publishing while flushing queue (to ensure sequence)
            if (sessionId != lastSessionId_) {
               log_.info(ME, string("when reconnecting the sessionId changed from '") + lastSessionId_ + "' to '" + sessionId + "'");
               lastSessionId_ = sessionId;
               MsgQueue tmpQueue = *adminQueue_;
               flushQueueUnlocked(&tmpQueue, true); // don't remove entries (in case of a future failure) 
            }
 
            if (doFlush) {
               try {
                  flushQueueUnlocked(queue_, true);
               }
               catch (...) {
                  log_.warn(ME, "an exception occured when trying to asynchroneously flush the contents of the queue. Probably not all messages have been sent. These unsent messages are still in the queue");
               }
            }
            startPinger();
         }
      }
      catch (XmlBlasterException ex) {
         currentRetry_++;
         if ( currentRetry_ < retries_ || retries_ < 0) { // continue to poll
            startPinger();
         }
         else {
            enum States oldState = status_;
            status_ = DEAD;
            if ( connectionProblems_ ) {
               connectionProblems_->reachedDead(oldState, this);
               // stopping
            }
         }
      }
      return;
   }
 
   // if it comes here it will stop
 
}


PublishReturnQos ConnectionsHandler::queuePublish(const MessageUnit& msgUnit)
{
   if (!queue_) {
      if (!connectQos_) {
         throw XmlBlasterException(INTERNAL_PUBLISH, ME + "::queuePublish", "need to create a queue but the connectQos is NULL (probably never connected)");
      }
      log_.info(ME, "created a client queue");
      queue_ = new MsgQueue(global_, connectQos_->getClientQueueProperty());

   }
   if (log_.TRACE) 
      log_.trace(ME, string("queuePublish: entry '") + msgUnit.getKey().getOid() + "' has been queued");
   PublishReturnQos retQos(global_);
   retQos.setKeyOid(msgUnit.getKey().getOid());
   retQos.setState("QUEUED");
   PublishQueueEntry entry(global_, msgUnit);
   queue_->put(entry);
   return retQos;
}

ConnectReturnQos& ConnectionsHandler::queueConnect()
{
   if (log_.CALL) log_.call(ME, string("::queueConnect with sessionQos: '") + connectQos_->getSessionQos().getAbsoluteName() + "'");
   long tmp = connectQos_->getSessionQos().getPubSessionId(); 
   if ( tmp <= 0) {
      if (log_.TRACE) log_.trace(ME, string("::queueConnect, the public session id is '") + lexical_cast<string>(tmp));
      throw XmlBlasterException(USER_CONNECT, ME + "::queueConnect", "queueing connection request not possible because you did not specify a positive public sessionId");
   }

   if (!queue_) {
      log_.info(ME, "::queueConnect: created a client queue");
      queue_ = new MsgQueue(global_, connectQos_->getClientQueueProperty());
   }
   if (log_.TRACE) 
      log_.trace(ME, string("queueConnect: entry '") + connectQos_->getSessionQos().getAbsoluteName() + "' has been queued");

   connectReturnQos_ = new ConnectReturnQos(*connectQos_);

   ConnectQueueEntry entry(global_, *connectQos_);
   queue_->put(entry);
   enum States oldState = status_;
   status_ = POLLING;
   if ( connectionProblems_ ) {
      connectionProblems_->reachedPolling(oldState, this);
      // stopping
   }
   startPinger();
   return *connectReturnQos_;
}



/**
 * Flushes all entries in the queue, i.e. the entries of the queue are sent to xmlBlaster.
 * If the queue is empty or NULL, then 0 is returned. If the state is in POLLING or DEAD, or the 
 * connection is not established yet (i.e. connection_ = NULL),  then -1 is
 * returned.. This method blocks until all entries in the queue have been sent.
 */
long ConnectionsHandler::flushQueue()
{
   if (log_.CALL) log_.call(ME, "flushQueue");
//   Lock lock(connectionMutex_);
   return flushQueueUnlocked(queue_, true);
}  

   
long ConnectionsHandler::flushQueueUnlocked(MsgQueue *queueToFlush, bool doRemove)
{
   if ( log_.CALL ) log_.call(ME, "flushQueueUnlocked");
           if (!queueToFlush || queueToFlush->empty()) return 0;
   if (status_ != CONNECTED || connection_ == NULL) return -1;

   long ret = 0;
   while (!queueToFlush->empty()) {
      log_.trace(ME, "flushQueueUnlocked: flushing one priority sweep");
      vector<EntryType> entries = queueToFlush->peekWithSamePriority();
      vector<EntryType>::const_iterator iter = entries.begin();
      while (iter != entries.end()) {
         try {
            if (log_.TRACE) log_.trace(ME, "sending the content to xmlBlaster: " + (*iter)->toXml());
            (*iter)->send(*this);
            if (log_.TRACE) log_.trace(ME, "content to xmlBlaster successfully sent");
         }
         catch (XmlBlasterException &ex) {
           if (ex.isCommunication()) toPollingOrDead();
           if (doRemove) queueToFlush->randomRemove(entries.begin(), iter);
           throw ex;
         }
         iter++;
      }
      if (doRemove) ret += queueToFlush->randomRemove(entries.begin(), entries.end());
   }
   return ret;
}

Queue* ConnectionsHandler::getQueue()
{
   return queue_;
}

bool ConnectionsHandler::isFailsafe() const
{
   if (!connectQos_) return false;
   return connectQos_->getAddress().getDelay() > 0;
}

bool ConnectionsHandler::startPinger()
{
   if (doStopPing_) return false;

   log_.call(ME, "startPinger");
   if (pingIsStarted_) {
      log_.warn(ME, "startPinger: the pinger is already running. I will return without starting a new thread");
      return false;  
   }

   long delay        = 10000;
   long pingInterval = 0;
   if ( connectQos_ ) {
      delay        = connectQos_->getAddress().getDelay();
      pingInterval = connectQos_->getAddress().getPingInterval();
   }
   else {
      ConnectQos tmp(global_);
      delay        = tmp.getAddress().getDelay();
      pingInterval = tmp.getAddress().getPingInterval();
   }
   if (log_.TRACE) {
      log_.trace(ME, string("startPinger: parameters are: delay '") + lexical_cast<string>(delay)  + "' and pingInterval '" + lexical_cast<string>(pingInterval));
   }
   if (delay > 0 && pingInterval > 0) {
      long delta = delay;
      if (status_ == CONNECTED) delta = pingInterval;
      timestamp_ = global_.getPingTimer().addTimeoutListener(this, delta, NULL);
      pingIsStarted_ = true;
   }
   return true;
}


bool ConnectionsHandler::isConnected() const
{
   return status_ == CONNECTED || status_ == POLLING;
}

ConnectReturnQos ConnectionsHandler::connectRaw(const ConnectQos& connectQos)
{
   ConnectReturnQos retQos = connection_->connect(connectQos);
   if (connectQos_) {
      delete connectQos_;
      connectQos_ = NULL;
   }
   connectQos_ = new ConnectQos(connectQos);
   if (connectReturnQos_) {
      delete connectReturnQos_;
      connectReturnQos_ = NULL;
   }
   connectReturnQos_ = new ConnectReturnQos(retQos);
   return *connectReturnQos_;
}


I_XmlBlasterConnection& ConnectionsHandler::getConnection()
{
   if (!connection_) {
      throw XmlBlasterException(INTERNAL_ILLEGALARGUMENT, ME + "::getConnection", "the connection is still NULL: it is not assigned yet. You probably called this method before a connection was made");
   }
   return *connection_;
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


