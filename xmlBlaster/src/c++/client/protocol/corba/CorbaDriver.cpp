/*------------------------------------------------------------------------------
Name:      CorbaDriver.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The client driver for the corba protocol
------------------------------------------------------------------------------*/

#include <client/protocol/corba/CorbaDriver.h>
#include <util/ErrorCode.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include <boost/lexical_cast.hpp>

using namespace org::xmlBlaster::util;
using namespace boost;

using namespace std;

namespace org {
 namespace xmlBlaster {
  namespace client {
   namespace protocol {
    namespace corba {


void CorbaDriver::freeResources(bool deleteConnection, bool deleteCallback)
{
   if (deleteConnection) {
      delete connection_;
      connection_ = NULL;
   }
   if (deleteCallback) {
      delete defaultCallback_;
      defaultCallback_ = NULL;
   }
}

#define _COMM_TRY try {


#define _COMM_CATCH(methodName, deleteConnection, deleteCallback)     \
   }                                                                  \
   catch(serverIdl::XmlBlasterException &ex) {                        \
      freeResources(deleteConnection, deleteCallback);                \
      throw convertFromCorbaException(ex);                            \
   }                                                                  \
   catch(CORBA::Exception &ex) {                                      \
      freeResources(deleteConnection, deleteCallback);                \
      throw XmlBlasterException(COMMUNICATION_NOCONNECTION,           \
                       "unknown node", ME + string(methodName), "en", \
                       "client-c++", "", "", to_string(ex));          \
   }                                                                  \
   catch(XmlBlasterException &ex) {                                   \
      throw ex;                                                       \
   }                                                                  \
   catch (...) {                                                      \
      freeResources(deleteConnection, deleteCallback);                \
      throw XmlBlasterException(INTERNAL_UNKNOWN,                     \
                       "unknown node", ME + string(methodName), "en");\
   }

/*

static bool dummy;

CorbaDriver::CorbaDriver()
   : doRun_(dummy),
     isRunning_(dummy),
     mutex_(),
     count_(0),
     ME("CorbaDriver"), 
     global_(Global::getInstance()), 
     log_(global_.getLog("client")),
     statusQosFactory_(global_), 
     msgQosFactory_(global_)
{
   connection_      = NULL;
   defaultCallback_ = NULL;
   _COMM_TRY
      connection_ = new CorbaConnection(global_, false);
   _COMM_CATCH("::Constructor", true, false)
}
*/

CorbaDriver::CorbaDriver(const CorbaDriver& corbaDriver)
   : Thread(), 
     doRun_(corbaDriver.doRun_),
     isRunning_(corbaDriver.isRunning_),
     mutex_(corbaDriver.mutex_),
     count_(corbaDriver.count_),
     ME("CorbaDriver"), 
     global_(corbaDriver.global_), 
     log_(corbaDriver.log_),
     statusQosFactory_(corbaDriver.global_), 
     msgQosFactory_(corbaDriver.global_)
{
   // no instantiation of these since this should never be invoked (just to make it private)
   connection_      = NULL;
   defaultCallback_ = NULL;
   doRun_           = true;
   mutex_           = Mutex();
}

CorbaDriver& CorbaDriver::operator =(const CorbaDriver& corbaDriver)
{
   return *this;
}


DriverMap& CorbaDriver::getDrivers()
{
   static DriverMap drivers;
   return drivers;
}


CorbaDriver& CorbaDriver::getInstance(Global& global, const string& instanceName, bool isOrbOwner, CORBA::ORB_ptr orb)
{
   static Mutex mutex;
   static bool  isRunning = false;
   static bool  doRun     = false;
//       Lock lock(mutex);
   Log& log = global.getLog("corba");
   if (log.call()) log.call("CorbaDriver", string("getInstance for ") + instanceName);
   CorbaDriver*  driver = NULL;
   DriverMap& driverMap = CorbaDriver::getDrivers();
   DriverMap::iterator iter = driverMap.find(instanceName);
   if (iter == driverMap.end()) {
      if (log.trace()) log.trace("CorbaDriver", string("created a new instance for ") + instanceName);
      driver = new CorbaDriver(global, mutex, doRun, isRunning, instanceName, isOrbOwner, orb);
      driverMap.insert(DriverMap::value_type(instanceName, driver));
      if (!isRunning && (orb == NULL)) driver->start(); // check if this is really what it should be ...
   }
   else driver = (*iter).second;
   int count = driver->count_++;
   if (log.trace()) log.trace("CorbaDriver", string("number of instances for '") + instanceName + "' are " + lexical_cast<string>(count));
   return *driver;
}

int CorbaDriver::killInstance(const string& instanceName)
{
   DriverMap& driverMap = CorbaDriver::getDrivers();
   DriverMap::iterator iter = driverMap.find(instanceName);
   if (iter == driverMap.end()) return -1;
   int help = --(*iter).second->count_;
   (*iter).second->log_.trace("CorbaDriver", string("instances before deleting ") + lexical_cast<string>(help));
   if (help <= 0) {
      bool doRestartThread = false;
      (*iter).second->log_.trace("CorbaDriver", string("kill instance '") + instanceName + "' will be deleted now");
      if (iter == driverMap.begin()) { // then it is the one which has the running thread
         (*iter).second->log_.trace("CorbaDriver", string("kill instance '") + instanceName + "' the running thread will be moved to another instance");
         doRestartThread = true;
             (*iter).second->doRun_ = false; // stop the running thread.
             while ( (*iter).second->isRunning_) { // wait until it really has stopped
            Thread::sleep(10);
         }
      }
      // do remove it since the counter is zero
      CorbaDriver* driver = (*iter).second;
      driverMap.erase(iter);
      delete driver;
      // and now restart (if necessary) the thread for the first entry in the map (if any)
      if (driverMap.empty()) return 0;
      (*driverMap.begin()).second->start();
   }
   int ret = (*iter).second->count_;
         (*iter).second->log_.trace("CorbaDriver", string("kill instance '") + instanceName + "' the number of references is " + lexical_cast<string>(ret));
   return ret;
}


void CorbaDriver::run()
{
   log_.info(ME, "the corba loop starts now");
   doRun_ = true;
   if (isRunning_) return;
   isRunning_ = true;
   while (doRun_) {
      {
         Lock lock(mutex_);
//         if (log_.trace()) log_.trace(ME, "sweep in running thread");
         connection_->orbPerformWork();
      }
      sleep(20); // sleep 20 milliseconds
   }
   log_.info(ME, "the corba loop has ended now");
   isRunning_ = false;
}

CorbaDriver::CorbaDriver(Global& global, Mutex& mutex, bool& doRun, bool& isRunning, const string instanceName, bool connectionOwner, CORBA::ORB_ptr orb)
   : doRun_(doRun),
     isRunning_(isRunning),
     mutex_(mutex),
     count_(0),
     ME(string("CorbaDriver-") + instanceName), 
     global_(global), 
     log_(global.getLog("corba")),
     statusQosFactory_(global), 
     msgQosFactory_(global)
{
   connection_      = NULL;
   defaultCallback_ = NULL;

   _COMM_TRY
      connection_ = new CorbaConnection(global_, connectionOwner, orb);
   _COMM_CATCH("::Constructor", true, false)
}

CorbaDriver::~CorbaDriver()
{
   try {
      delete defaultCallback_;
      delete connection_;
   }
   catch (...) {
   }
}

void CorbaDriver::initialize(const string& name, I_Callback &client)
{
   Lock lock(mutex_);
   _COMM_TRY
      if (defaultCallback_ != NULL) delete defaultCallback_;
      defaultCallback_ = NULL;
      defaultCallback_ =  new DefaultCallback(global_, name, &client, 0);
//      if (connection_ != NULL) delete connection_;
//      connection_ = NULL;
      connection_->createCallbackServer(defaultCallback_);
   _COMM_CATCH("::initialize", true, true)
}

string CorbaDriver::getCbProtocol()
{
    return "IOR";
}

string CorbaDriver::getCbAddress()
{
   _COMM_TRY
      return connection_->getCbAddress();
   _COMM_CATCH("::getCbAddress", false, false)
}

bool CorbaDriver::shutdownCb()
{
   _COMM_TRY
      return connection_->shutdownCb();
   _COMM_CATCH("::shutdownCb", false, false)
}

ConnectReturnQos CorbaDriver::connect(const ConnectQos& qos)
{
   Lock lock(mutex_);
   _COMM_TRY
      return connection_->connect(qos);
   _COMM_CATCH("::connect", false, false)
}

bool CorbaDriver::disconnect(const DisconnectQos& qos)
{
   Lock lock(mutex_);
   _COMM_TRY
      return connection_->disconnect(qos.toXml());
   _COMM_CATCH("::disconnect", false, false)
}

string CorbaDriver::getProtocol()
{
   return "IOR";
}

/*
string CorbaDriver::loginRaw()
{
   _COMM_TRY
      connection_->loginRaw();
      return getLoginName();
   _COMM_CATCH("::loginRaw", false, false)
}
*/

bool CorbaDriver::shutdown()
{
   Lock lock(mutex_);
   _COMM_TRY
      return connection_->shutdown();
   _COMM_CATCH("::shutdown", false, false)
}

void CorbaDriver::resetConnection()
{
   std::cerr << "'CorbaDriver::resetConnection' not implemented" << std::endl;
}

string CorbaDriver::getLoginName()
{
   _COMM_TRY
      return connection_->getLoginName();
   _COMM_CATCH("::getLoginName", false, false)
}

bool CorbaDriver::isLoggedIn()
{
   _COMM_TRY
      return connection_->isLoggedIn();
   _COMM_CATCH("::isLoggedIn", false, false)
}

string CorbaDriver::ping(const string& qos)
{
   Lock lock(mutex_);
   _COMM_TRY
      return connection_->ping(qos);
   _COMM_CATCH("::ping", false, false)
}

SubscribeReturnQos CorbaDriver::subscribe(const SubscribeKey& key, const SubscribeQos& qos)
{
   Lock lock(mutex_);
   _COMM_TRY
      string ret = connection_->subscribe(key.toXml(), qos.toXml());
      return SubscribeReturnQos(global_, statusQosFactory_.readObject(ret));
   _COMM_CATCH("::subscribe", false, false)
}

vector<MessageUnit> CorbaDriver::get(const GetKey& key, const GetQos& qos)
{
   Lock lock(mutex_);
   _COMM_TRY
      return connection_->get(key.toXml(), qos.toXml());
   _COMM_CATCH("::get", false, false)
}

vector<UnSubscribeReturnQos>
CorbaDriver::unSubscribe(const UnSubscribeKey& key, const UnSubscribeQos& qos)
{
   Lock lock(mutex_);
   _COMM_TRY
      vector<string> tmp = connection_->unSubscribe(key.toXml(), qos.toXml());
      vector<string>::const_iterator iter = tmp.begin();
      vector<UnSubscribeReturnQos> ret;
      while (iter != tmp.end()) {
         ret.insert(ret.end(),  UnSubscribeReturnQos(global_, statusQosFactory_.readObject(*iter)));
         iter++;
      }
      return ret;
   _COMM_CATCH("::unSubscribe", false, false)
}

PublishReturnQos CorbaDriver::publish(const MessageUnit& msgUnit)
{
   Lock lock(mutex_);
   _COMM_TRY
      if (log_.call()) log_.call(ME, "publish");
      string ret = connection_->publish(msgUnit);
      if (log_.trace()) log_.trace(ME, "successfully published");
      return PublishReturnQos(global_, statusQosFactory_.readObject(ret));
   _COMM_CATCH("::publish", false, false)
}

void CorbaDriver::publishOneway(const vector<MessageUnit> &msgUnitArr)
{
   Lock lock(mutex_);
   _COMM_TRY
      connection_->publishOneway(msgUnitArr);
   _COMM_CATCH("::publishOneway", false, false)
}

vector<PublishReturnQos> CorbaDriver::publishArr(vector<MessageUnit> msgUnitArr)
{
   Lock lock(mutex_);
   _COMM_TRY
      vector<string> tmp = connection_->publishArr(msgUnitArr);
      vector<string>::const_iterator iter = tmp.begin();
      vector<PublishReturnQos> ret;
      while (iter != tmp.end()) {
         ret.insert(ret.end(),  PublishReturnQos(global_, statusQosFactory_.readObject(*iter)) );
         iter++;
      }
      return ret;
   _COMM_CATCH("::publishArr", false, false)
}

vector<EraseReturnQos> CorbaDriver::erase(const EraseKey& key, const EraseQos& qos)
{
   _COMM_TRY
   Lock lock(mutex_);
      vector<string> tmp = connection_->erase(key.toXml(), qos.toXml());
      vector<string>::const_iterator iter = tmp.begin();
      vector<EraseReturnQos> ret;
      while (iter != tmp.end()) {
         ret.insert(ret.end(),  EraseReturnQos(global_, statusQosFactory_.readObject(*iter)) );
         iter++;
      }
      return ret;
   _COMM_CATCH("::erase", false, false)
}

void CorbaDriver::usage()
{
      CorbaConnection::usage();
}


// Exception conversion ....
org::xmlBlaster::util::XmlBlasterException
CorbaDriver::convertFromCorbaException(const serverIdl::XmlBlasterException& ex)
{
   string tmp = "";
   return org::xmlBlaster::util::XmlBlasterException(ex.errorCodeStr==NULL?tmp:string(ex.errorCodeStr),
                                                     ex.node==NULL?tmp:string(ex.node),
                                                     ex.location==NULL?tmp:string(ex.location),
                                                     ex.lang==NULL?tmp:string(ex.lang),
                                                     ex.message==NULL?tmp:string(ex.message),
                                                     ex.versionInfo==NULL?tmp:string(ex.versionInfo),
                                                     ex.timestampStr==NULL?tmp:string(ex.timestampStr),
                                                     ex.stackTrace==NULL?tmp:string(ex.stackTrace),
                                                     ex.embeddedMessage==NULL?tmp:string(ex.embeddedMessage),
                                                     ex.transactionInfo==NULL?tmp:string(ex.transactionInfo));
}

serverIdl::XmlBlasterException
CorbaDriver::convertToCorbaException(org::xmlBlaster::util::XmlBlasterException& ex)
{
   return serverIdl::XmlBlasterException(ex.getErrorCodeStr().c_str(),
                                         ex.getNode().c_str(),
                                         ex.getLocation().c_str(),
                                         ex.getLang().c_str(),
                                         ex.getMessage().c_str(),
                                         ex.getVersionInfo().c_str(),
                                         ex.getTimestamp().c_str(),
                                         ex.getStackTraceStr().c_str(),
                                         ex.getEmbeddedMessage().c_str(),
                                         ex.getTransactionInfo().c_str(), "");
}

}}}}} // namespaces

