/*------------------------------------------------------------------------------
Name:      ConnectionsHandler.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handles the I_XmlBlasterConnections 
------------------------------------------------------------------------------*/

#include <util/dispatch/ConnectionsHandler.h>
#include <util/dispatch/DeliveryManager.h>
#include <util/Global.h>
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
   retries_            = -1;
}

ConnectionsHandler::~ConnectionsHandler()
{
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

   string type = connectQos_->getServerRef().getType();
   string version = "1.0"; // currently hardcoded
   connection_ = &(deliveryManager_.getPlugin(type, version));
   if (connectReturnQos_) {
      delete connectReturnQos_;
      connectReturnQos_ = NULL;
   }
   connectReturnQos_ = new ConnectReturnQos(connection_->connect(*connectQos_));
   status_ = CONNECTED;
   if (connectionProblems_) connectionProblems_->reConnected();
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
}


vector<MessageUnit> ConnectionsHandler::get(const GetKey& key, const GetQos& qos)
{
   if (log_.CALL) log_.call(ME, "get");

   Lock lock(connectionMutex_);

   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "get");
   if (status_ == POLLING) throw XmlBlasterException(COMMUNICATION_NOCONNECTION_POLLING, ME, "get");
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "get");

   int i = 0;
   while (i < retries_ || retries_ < 0) {
      try {
         return connection_->get(key, qos);
      }
      catch (XmlBlasterException &ex) {
         i++;
         log_.warn(ME, string("get: exception on trial ") + lexical_cast<string>(i) + " of " + lexical_cast<string>(retries_));
         Thread::sleep(connectQos_->getAddress().getDelay());
      }
   }
   if (status_ == CONNECTED) status_ = DEAD;
   throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "get");
}


vector<UnSubscribeReturnQos> 
   ConnectionsHandler::unSubscribe(const UnSubscribeKey& key, const UnSubscribeQos& qos)
{
   if (log_.CALL) log_.call(ME, "unSubscribe");

   Lock lock(connectionMutex_);

   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "unSubscribe");
   if (status_ == POLLING) throw XmlBlasterException(COMMUNICATION_NOCONNECTION_POLLING, ME, "unSubscribe");
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "unSubscribe");

   int i = 0;
   while (i < retries_ || retries_ < 0) {
      try {
         return connection_->unSubscribe(key, qos);
      }
      catch (XmlBlasterException &ex) {
         i++;
         log_.warn(ME, string("unSubscribe: exception on trial ") + lexical_cast<string>(i) + " of " + lexical_cast<string>(retries_));
         Thread::sleep(connectQos_->getAddress().getDelay());
      }
   }
   if (status_ == CONNECTED) status_ = DEAD;
   throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "unSubscribe");
}

PublishReturnQos ConnectionsHandler::publish(const MessageUnit& msgUnit)
{
   if (log_.CALL) log_.call(ME, "publish");

   Lock lock(connectionMutex_);

   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "publish");
   if (status_ == POLLING) throw XmlBlasterException(COMMUNICATION_NOCONNECTION_POLLING, ME, "publish");
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "publish");

   int i = 0;
   while (i < retries_ || retries_ < 0) {
      try {
         return connection_->publish(msgUnit);
      }
      catch (XmlBlasterException &ex) {
         i++;
         log_.warn(ME, string("publish: exception on trial ") + lexical_cast<string>(i) + " of " + lexical_cast<string>(retries_));
         Thread::sleep(connectQos_->getAddress().getDelay());
      }
   }
   if (status_ == CONNECTED) status_ = DEAD;
   throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "publish");
}


void ConnectionsHandler::publishOneway(const vector<MessageUnit> &msgUnitArr)
{
   if (log_.CALL) log_.call(ME, "publishOneway");

   Lock lock(connectionMutex_);

   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "publishOneway");
   if (status_ == POLLING) throw XmlBlasterException(COMMUNICATION_NOCONNECTION_POLLING, ME, "publishOneway");
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "publishOneway");

   int i = 0;
   while (i < retries_ || retries_ < 0) {
      try {
         connection_->publishOneway(msgUnitArr);
         return;
      }
      catch (XmlBlasterException &ex) {
         i++;
         log_.warn(ME, string("publishOneway: exception on trial ") + lexical_cast<string>(i) + " of " + lexical_cast<string>(retries_));
         Thread::sleep(connectQos_->getAddress().getDelay());
      }
   }
   if (status_ == CONNECTED) status_ = DEAD;
   throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "publishOneway");
}


vector<PublishReturnQos> ConnectionsHandler::publishArr(vector<MessageUnit> msgUnitArr)
{
   if (log_.CALL) log_.call(ME, "publishArr");

   Lock lock(connectionMutex_);

   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "publishArr");
   if (status_ == POLLING) throw XmlBlasterException(COMMUNICATION_NOCONNECTION_POLLING, ME, "publishArr");
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "publishArr");

   int i = 0;
   while (i < retries_ || retries_ < 0) {
      try {
         return connection_->publishArr(msgUnitArr);
      }
      catch (XmlBlasterException &ex) {
         i++;
         log_.warn(ME, string("publishArr: exception on trial ") + lexical_cast<string>(i) + " of " + lexical_cast<string>(retries_));
         Thread::sleep(connectQos_->getAddress().getDelay());
      }
   }
   if (status_ == CONNECTED) status_ = DEAD;
   throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "publishArr");
}


vector<EraseReturnQos> ConnectionsHandler::erase(const EraseKey& key, const EraseQos& qos)
{
   if (log_.CALL) log_.call(ME, "erase");

   Lock lock(connectionMutex_);

   if (status_ == START)   throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "erase");
   if (status_ == POLLING) throw XmlBlasterException(COMMUNICATION_NOCONNECTION_POLLING, ME, "erase");
   if (status_ == DEAD)    throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "erase");

   int i = 0;
   while (i < retries_ || retries_ < 0) {
      try {
         return connection_->erase(key, qos);
      }
      catch (XmlBlasterException &ex) {
         i++;
         log_.warn(ME, string("erase: exception on trial ") + lexical_cast<string>(i) + " of " + lexical_cast<string>(retries_));
         Thread::sleep(connectQos_->getAddress().getDelay());
      }
   }
   if (status_ == CONNECTED) status_ = DEAD;
   throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "erase");
}

void ConnectionsHandler::initFailsafe(I_ConnectionProblems* connectionProblems)
{
   Lock lock(connectionMutex_);
   connectionProblems_ = connectionProblems;
}


}}}} // namespaces


