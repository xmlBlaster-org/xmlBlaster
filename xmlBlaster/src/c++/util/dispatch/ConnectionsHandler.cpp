/*------------------------------------------------------------------------------
Name:      ConnectionsHandler.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handles the I_XmlBlasterConnections 
------------------------------------------------------------------------------*/

#include <util/dispatch/ConnectionsHandler.h>
#include <util/dispatch/DeliveryManager.h>
#include <util/Global.h>
#include <util/Timeout.h>
#include <boost/lexical_cast.hpp>

using boost::lexical_cast;

using namespace org::xmlBlaster::client::protocol;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::util::thread;

namespace org { namespace xmlBlaster { namespace util { namespace dispatch {

ConnectionsHandler::ConnectionsHandler(Global& global, DeliveryManager& deliveryManager)
   : ME("ConnectionsHandler"), 
     deliveryManager_(deliveryManager), 
     status_(START), 
     global_(global), 
     log_(global.getLog("delivery")),
     connectionMutex_()
{
   connectQos_         = NULL;
   connectionProblems_ = NULL;
   connectReturnQos_   = NULL;
   connection_         = NULL;
   queue_              = NULL;
   retries_            = -1;
   currentRetry_       = 0;
   timestamp_          = 0;
}

ConnectionsHandler::~ConnectionsHandler()
{
   if (timestamp_ != 0) {
      Lock lock(connectionMutex_);
      global_.getPingTimer().removeTimeoutListener(timestamp_);
      timestamp_ = 0;
   }
   if ( queue_ ) {
      Lock lock(connectionMutex_);
      delete queue_;
      queue_ = NULL;
   }
   delete connectQos_;
   delete connectReturnQos_;
   status_ = END;
} 


ConnectReturnQos ConnectionsHandler::connect(const ConnectQos& qos)
{
   Lock lock(connectionMutex_);
   if (log_.CALL) log_.call(ME, "::connect");
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
   connection_ = &(deliveryManager_.getPlugin(type, version));
   if (connectReturnQos_) {
      delete connectReturnQos_;
      connectReturnQos_ = NULL;
   }

   connectReturnQos_ = new ConnectReturnQos(connection_->connect(*connectQos_));
   status_ = CONNECTED;
   // start the ping 
   timestamp_ = global_.getPingTimer().addTimeoutListener(this, pingInterval, NULL);
   return *connectReturnQos_;
}

bool ConnectionsHandler::disconnect(const DisconnectQos& qos)
{
   if (log_.CALL) log_.call(ME, "disconnect");

   Lock lock(connectionMutex_);

   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "disconnect");
   if (status_ == POLLING) throw XmlBlasterException(COMMUNICATION_NOCONNECTION_POLLING, ME, "disconnect");
   if (status_ == DEAD) {
      log_.warn(ME, "already disconnected");
      return false;
   }

   int i = 0;
   while (i < retries_ || retries_ < 0) {
      try {
         bool ret = connection_->disconnect(qos);
         status_ = DEAD;
         return ret;
      }
      catch (XmlBlasterException &ex) {
         i++;
         log_.warn(ME, string("disconnect: exception on trial ") + lexical_cast<string>(i) + " of " + lexical_cast<string>(retries_));
         Thread::sleep(connectQos_->getAddress().getDelay());
      }
   }
   if (status_ == CONNECTED) status_ = DEAD;
   throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "disconnect");
}


string ConnectionsHandler::getProtocol()
{
   return connection_->getProtocol();
}

string ConnectionsHandler::loginRaw()
{
   return connection_->loginRaw();
}

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
   Lock lock(connectionMutex_);
   return connection_->ping(qos);
}

SubscribeReturnQos ConnectionsHandler::subscribe(const SubscribeKey& key, const SubscribeQos& qos)
{
   if (log_.CALL) log_.call(ME, "subscribe");

   Lock lock(connectionMutex_);

   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "subscribe");
   if (status_ == POLLING) throw XmlBlasterException(COMMUNICATION_NOCONNECTION_POLLING, ME, "subscribe");
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "subscribe");
/*
   int i = 0;
   while (i < retries_ || retries_ < 0) {
      try {
         return connection_->subscribe(key, qos);
      }
      catch (XmlBlasterException &ex) {
         i++;
         log_.warn(ME, string("subscribe: exception on trial ") + lexical_cast<string>(i) + " of " + lexical_cast<string>(retries_));
         Thread::sleep(connectQos_->getAddress().getDelay());
      }
   }
   if (status_ == CONNECTED) status_ = DEAD;
   throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "subscribe");
*/
   try {
      return connection_->subscribe(key, qos);
   }   
   catch (XmlBlasterException& ex) {
      if ( ex.isCommunication() ) {
         status_ = POLLING;
      }
      throw ex;
   }
}


vector<MessageUnit> ConnectionsHandler::get(const GetKey& key, const GetQos& qos)
{
   if (log_.CALL) log_.call(ME, "get");

   Lock lock(connectionMutex_);

   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "get");
   if (status_ == POLLING) throw XmlBlasterException(COMMUNICATION_NOCONNECTION_POLLING, ME, "get");
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "get");
   try {
      return connection_->get(key, qos);
   }   
   catch (XmlBlasterException& ex) {
      if ( ex.isCommunication() ) {
         status_ = POLLING;
      }
      throw ex;
   }
}


vector<UnSubscribeReturnQos> 
   ConnectionsHandler::unSubscribe(const UnSubscribeKey& key, const UnSubscribeQos& qos)
{
   if (log_.CALL) log_.call(ME, "unSubscribe");

   Lock lock(connectionMutex_);

   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "unSubscribe");
   if (status_ == POLLING) throw XmlBlasterException(COMMUNICATION_NOCONNECTION_POLLING, ME, "unSubscribe");
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "unSubscribe");
   try {
      return connection_->unSubscribe(key, qos);
   }   
   catch (XmlBlasterException& ex) {
      if ( ex.isCommunication() ) {
         status_ = POLLING;
      }
      throw ex;
   }
}


PublishReturnQos ConnectionsHandler::publish(const MessageUnit& msgUnit)
{
   if (log_.CALL) log_.call(ME, "publish");

   Lock lock(connectionMutex_);

   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "publish");
   if (status_ == POLLING) return queuePublish(msgUnit);
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "publish");
   try {
      return connection_->publish(msgUnit);
   }   
   catch (XmlBlasterException& ex) {
      if ( ex.isCommunication() ) {
         status_ = POLLING;
         return queuePublish(msgUnit);
      }
      else throw ex;
   }
}


void ConnectionsHandler::publishOneway(const vector<MessageUnit> &msgUnitArr)
{
   if (log_.CALL) log_.call(ME, "publishOneway");

   Lock lock(connectionMutex_);

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
         status_ = POLLING;
         for (size_t i=0; i < msgUnitArr.size(); i++) queuePublish(msgUnitArr[i]);
      }
      else throw ex;
   }
}


vector<PublishReturnQos> ConnectionsHandler::publishArr(vector<MessageUnit> msgUnitArr)
{
   if (log_.CALL) log_.call(ME, "publishArr");

   Lock lock(connectionMutex_);

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
         status_ = POLLING;
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

   Lock lock(connectionMutex_);

   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "erase");
   if (status_ == POLLING) throw XmlBlasterException(COMMUNICATION_NOCONNECTION_POLLING, ME, "erase");
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "erase");

   try {
      return connection_->erase(key, qos);
   }   
   catch (XmlBlasterException& ex) {
      if ( ex.isCommunication() ) {
         status_ = POLLING;
      }
      throw ex;
   }
}

void ConnectionsHandler::initFailsafe(I_ConnectionProblems* connectionProblems)
{
   Lock lock(connectionMutex_);
   connectionProblems_ = connectionProblems;
}

void ConnectionsHandler::timeout(void *userData)
{
  Lock lock(connectionMutex_);
  timestamp_ = 0;
  if ( log_.CALL ) log_.call(ME, "ping timeout occured");
  if (status_ == CONNECTED) { // then I am pinging
     if ( log_.TRACE ) log_.trace(ME, "ping timeout: status is 'CONNECTED'");
     try {
        if (connection_) {
	   connection_->ping("<qos/>");
	   long pingInterval = 10000;
	   if (connectQos_) pingInterval = connectQos_->getAddress().getPingInterval();
	   timestamp_ = global_.getPingTimer().addTimeoutListener(this, pingInterval, NULL);
        }
     }
     catch (XmlBlasterException& ex) {
        status_ = POLLING;
	currentRetry_ = 0;
	try {
	   DisconnectQos discQos(global_);
	   connection_->disconnect(discQos);
	}
	catch (...) {
	   log_.warn(ME, "exception when trying to disconnect");
	}
	
	connection_->shutdown();

	if (connectionProblems_) {
	   connectionProblems_->toPolling();
	}
	long retryDelay = 1000;
        if (connectQos_) retryDelay = connectQos_->getAddress().getDelay();
	timestamp_ = global_.getPingTimer().addTimeoutListener(this, retryDelay, NULL);
     }
     return;
  }

  if (status_ == POLLING) {
     if ( log_.TRACE ) log_.trace(ME, "ping timeout: status is 'POLLING'");
     try {
        if ((connection_) && (connectQos_)) {
           if ( log_.TRACE ) log_.trace(ME, "ping timeout: going to retry a connection");
	   ConnectReturnQos retQos = connection_->connect(*connectQos_);
           if ( log_.TRACE ) log_.trace(ME, "ping timeout: re-connection was successful");
	   if (connectReturnQos_) delete connectReturnQos_;
           connectReturnQos_ = new ConnectReturnQos(retQos);
	   bool doFlush = true;
           if ( connectionProblems_ ) doFlush = connectionProblems_->reConnected();

	   status_ = CONNECTED;
	   if (doFlush) {
	      try {
		 flushQueueUnlocked();		 
	      }
	      catch (...) {
		 log_.warn(ME, "an exception occured when trying to asynchroneously flush the contents of the queue. Probably not all messages have been sent. These unsent messages are still in the queue");
	      }
	   }
	
           long pingInterval = 10000;
           if (connectQos_) pingInterval = connectQos_->getAddress().getPingInterval();
           if ( log_.TRACE ) log_.trace(ME, "ping timeout: re-spanning the timer");
           timestamp_ = global_.getPingTimer().addTimeoutListener(this, pingInterval, NULL);
        }
     }
     catch (XmlBlasterException ex) {
        currentRetry_++;
	if ( currentRetry_ < retries_ || retries_ < 0) { // continue to poll
  	   long retryDelay = 1000;
           if (connectQos_) retryDelay = connectQos_->getAddress().getDelay();
           if ( log_.TRACE ) log_.trace(ME, "ping timeout: retry nr. " + lexical_cast<string>(currentRetry_) + " of " + lexical_cast<string>(retries_) + " retry delay: " + lexical_cast<string>(retryDelay));
	   timestamp_ = global_.getPingTimer().addTimeoutListener(this, retryDelay, NULL);
	}
	else {
	   status_ = DEAD;
	   if ( connectionProblems_ ) {
	      connectionProblems_->lostConnection();
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
   PublishQueueEntry entry(msgUnit);
   queue_->put(entry);
   return retQos;
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
   Lock lock(connectionMutex_);
   return flushQueueUnlocked();
}  

   
long ConnectionsHandler::flushQueueUnlocked()
{
   if (!queue_ || queue_->empty()) return 0;
   if (status_ != CONNECTED || connection_ == NULL) return -1;

   long ret = 0;
   while (!queue_->empty()) {
      vector<EntryType> entries = queue_->peekWithSamePriority();
      vector<EntryType>::const_iterator iter = entries.begin();
      while (iter != entries.end()) {
         try {
	    (*iter)->send(*connection_);
	 }
	 catch (XmlBlasterException &ex) {
	   if (ex.isCommunication()) {
	      status_ = POLLING;
	      connection_->shutdown();
	   }
	   queue_->randomRemove(entries.begin(), iter);
	   throw ex;
	 }
	 iter++;
      }
      ret += queue_->randomRemove(entries.begin(), entries.end());
   }
   return ret;
}

/**
 * Creates and returns a copy of the client queue. if 'eraseOriginalQueueEntries' is 'true', then the
 * original queue (the client queue) is cleared.
 */
MsgQueue ConnectionsHandler::getCopyOfQueue(bool eraseOriginalQueueEntries)
{
   if (log_.CALL) log_.call(ME, "getCopyOfQueue");
   if (!queue_) {
      if (!connectQos_) {
         ConnectQos tmp(global_);
         return MsgQueue(global_, tmp.getClientQueueProperty());
      }
      return MsgQueue(global_, connectQos_->getClientQueueProperty());
   }
   MsgQueue ret = *queue_;
   if (eraseOriginalQueueEntries) ret.clear();
   return ret;
}



}}}} // namespaces


