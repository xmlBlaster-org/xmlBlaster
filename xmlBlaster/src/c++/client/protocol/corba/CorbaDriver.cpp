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
#include <util/lexical_cast.h>

namespace org {
 namespace xmlBlaster {
  namespace client {
   namespace protocol {
    namespace corba {

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::client::protocol;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

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
   catch(const CosNaming::NamingContext::CannotProceed &ex) {         \
      freeResources(deleteConnection, deleteCallback);                \
      throw XmlBlasterException(COMMUNICATION_NOCONNECTION,           \
             "unknown node", ME + string(methodName), "en",           \
             global_.getVersion() + " " + global_.getBuildTimestamp(),\
             "", "", to_string(ex));                                  \
   }                                                                  \
   catch(const CosNaming::NamingContext::InvalidName &ex) {           \
      freeResources(deleteConnection, deleteCallback);                \
      throw XmlBlasterException(COMMUNICATION_NOCONNECTION,           \
                       "unknown node", ME + string(methodName), "en", \
             global_.getVersion() + " " + global_.getBuildTimestamp(),\
                       "", "", to_string(ex));                        \
   }                                                                  \
   catch(const CosNaming::NamingContext::AlreadyBound &ex) {          \
      freeResources(deleteConnection, deleteCallback);                \
      throw XmlBlasterException(COMMUNICATION_NOCONNECTION,           \
             "unknown node", ME + string(methodName), "en",           \
             global_.getVersion() + " " + global_.getBuildTimestamp(),\
             "", "", to_string(ex));                                  \
   }                                                                  \
   catch(const CosNaming::NamingContext::NotEmpty &ex) {              \
      freeResources(deleteConnection, deleteCallback);                \
      throw XmlBlasterException(COMMUNICATION_NOCONNECTION,           \
             "unknown node", ME + string(methodName), "en",           \
             global_.getVersion() + " " + global_.getBuildTimestamp(),\
             "", "", to_string(ex));                                  \
   }                                                                  \
   catch(const CosNaming::NamingContext::NotFound &ex) {              \
      freeResources(deleteConnection, deleteCallback);                \
      throw XmlBlasterException(COMMUNICATION_NOCONNECTION,           \
             "unknown node", ME + string(methodName), "en",           \
             global_.getVersion() + " " + global_.getBuildTimestamp(),\
             "", "", to_string(ex));                                  \
   }                                                                  \
   catch(const CORBA::Exception &ex) {                                \
      freeResources(deleteConnection, deleteCallback);                \
      throw XmlBlasterException(COMMUNICATION_NOCONNECTION,           \
             "unknown node", ME + string(methodName), "en",           \
             global_.getVersion() + " " + global_.getBuildTimestamp(),\
             "", "", to_string(ex));                                  \
   }                                                                  \
   catch(const XmlBlasterException &ex) {                             \
      freeResources(deleteConnection, deleteCallback);                \
      throw ex;                                                       \
   }                                                                  \
   catch(const XmlBlasterException *ex) {                             \
      freeResources(deleteConnection, deleteCallback);                \
      throw ex;                                                       \
   }                                                                  \
   catch(const exception &ex) {                                       \
      freeResources(deleteConnection, deleteCallback);                \
      throw XmlBlasterException(INTERNAL_UNKNOWN,                     \
             "unknown node", ME + string(methodName), "en",           \
             global_.getVersion() + " " + global_.getBuildTimestamp(),\
             "", "",                                                  \
             string("type='exception', msg='") + ex.what() + "'");    \
   }                                                                  \
   catch(const string &ex) {                                          \
      freeResources(deleteConnection, deleteCallback);                \
      throw XmlBlasterException(INTERNAL_UNKNOWN,                     \
             "unknown node", ME + string(methodName), "en",           \
             global_.getVersion() + " " + global_.getBuildTimestamp(),\
             "", "",                                                  \
             string("type='string', msg='") + ex + "'");              \
   }                                                                  \
   catch(const char *ex) {                                            \
      freeResources(deleteConnection, deleteCallback);                \
      throw XmlBlasterException(INTERNAL_UNKNOWN,                     \
             "unknown node", ME + string(methodName), "en",           \
             global_.getVersion() + " " + global_.getBuildTimestamp(),\
             "", "",                                                  \
             string("type='char*', msg='") + ex + "'");               \
   }                                                                  \
   catch(int ex) {                                                    \
      freeResources(deleteConnection, deleteCallback);                \
      throw XmlBlasterException(INTERNAL_UNKNOWN,                     \
             "unknown node", ME + string(methodName), "en",           \
             global_.getVersion() + " " + global_.getBuildTimestamp(),\
             "", "",                                                  \
       string("type='int', msg='") + lexical_cast<std::string>(ex) + "'"); \
   }                                                                  \
   catch (...) {                                                      \
      freeResources(deleteConnection, deleteCallback);                \
      throw XmlBlasterException(INTERNAL_UNKNOWN,                     \
           "unknown node", ME + string(methodName), "en",             \
           global_.getVersion() + " " + global_.getBuildTimestamp()); \
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
     log_(global_.getLog("org.xmlBlaster.client.protocol.corba")),
     statusQosFactory_(global_), 
{
   connection_      = NULL;
   defaultCallback_ = NULL;
   _COMM_TRY
      connection_ = new CorbaConnection(global_, false);
   _COMM_CATCH("::Constructor", true, false)
}
*/

CorbaDriver::CorbaDriver(const CorbaDriver& corbaDriver)
   : mutex_(corbaDriver.mutex_),
     ME("CorbaDriver"), 
     global_(corbaDriver.global_), 
     log_(corbaDriver.log_),
     statusQosFactory_(corbaDriver.global_), 
     orbIsThreadSafe_(ORB_IS_THREAD_SAFE)
{
   // no instantiation of these since this should never be invoked (just to make it private)
   connection_      = NULL;
   defaultCallback_ = NULL;
   if (log_.call()) log_.call("CorbaDriver", string("Constructor orbIsThreadSafe_=") + lexical_cast<std::string>(orbIsThreadSafe_));
}

CorbaDriver& CorbaDriver::operator =(const CorbaDriver& /*corbaDriver*/)
{
   return *this;
}


CorbaDriver::CorbaDriver(Global& global, Mutex& mutex, const string instanceName, CORBA::ORB_ptr orb)
   : mutex_(mutex),
     ME(string("CorbaDriver-") + instanceName), 
     global_(global), 
     log_(global.getLog("org.xmlBlaster.client.protocol.corba")),
     statusQosFactory_(global), 
     orbIsThreadSafe_(ORB_IS_THREAD_SAFE)
{
   connection_      = NULL;
   defaultCallback_ = NULL;

   if (log_.call()) log_.call("CorbaDriver", string("getInstance for ") + instanceName +
                              " orbIsThreadSafe_=" + lexical_cast<std::string>(orbIsThreadSafe_));

   _COMM_TRY
      connection_ = new CorbaConnection(global_, orb);
   _COMM_CATCH("::Constructor", true, false)
}

CorbaDriver::~CorbaDriver()
{
   if (log_.call()) log_.call(ME, "~CorbaDriver()");
   try {
//      delete defaultCallback_; // Is a memory leak, but we need to track down the valgrind issue first
      delete connection_;
   }
   catch (...) {
   }
}

void CorbaDriver::initialize(const string& name, I_Callback &client)
{
   Lock lock(mutex_, orbIsThreadSafe_);
   _COMM_TRY
//      if (defaultCallback_ != NULL) delete defaultCallback_;
      defaultCallback_ = NULL;
      defaultCallback_ =  new DefaultCallback(global_, name, &client, 0);
//      if (connection_ != NULL) delete connection_;
//      connection_ = NULL;
      if (log_.trace()) log_.trace(ME, "Before createCallbackServer");
      connection_->createCallbackServer(defaultCallback_);
      if (log_.trace()) log_.trace(ME, "After createCallbackServer");
   _COMM_CATCH("::initialize", true, true)
}

string CorbaDriver::getCbProtocol()
{
    return Constants::IOR; // "IOR";
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

ConnectReturnQosRef CorbaDriver::connect(const ConnectQosRef& qos)
{
   Lock lock(mutex_, orbIsThreadSafe_);
   _COMM_TRY
      return connection_->connect(qos);
   _COMM_CATCH("::connect", false, false)
}

bool CorbaDriver::disconnect(const DisconnectQos& qos)
{
   Lock lock(mutex_, orbIsThreadSafe_);
   _COMM_TRY
      return connection_->disconnect(qos.toXml());
   _COMM_CATCH("::disconnect", false, false)
}

string CorbaDriver::getProtocol()
{
   return Constants::IOR; // "IOR";
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
   Lock lock(mutex_, orbIsThreadSafe_);
   _COMM_TRY
      return connection_->shutdown();
   _COMM_CATCH("::shutdown", false, false)
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
   Lock lock(mutex_, orbIsThreadSafe_);
   _COMM_TRY
      return connection_->ping(qos);
   _COMM_CATCH("::ping", false, false)
}

SubscribeReturnQos CorbaDriver::subscribe(const SubscribeKey& key, const SubscribeQos& qos)
{
   Lock lock(mutex_, orbIsThreadSafe_);
   _COMM_TRY
      string ret = connection_->subscribe(key.toXml(), qos.toXml());
      return SubscribeReturnQos(global_, statusQosFactory_.readObject(ret));
   _COMM_CATCH("::subscribe", false, false)
}

vector<MessageUnit> CorbaDriver::get(const GetKey& key, const GetQos& qos)
{
   Lock lock(mutex_, orbIsThreadSafe_);
   _COMM_TRY
      return connection_->get(key.toXml(), qos.toXml());
   _COMM_CATCH("::get", false, false)
}

vector<UnSubscribeReturnQos>
CorbaDriver::unSubscribe(const UnSubscribeKey& key, const UnSubscribeQos& qos)
{
   Lock lock(mutex_, orbIsThreadSafe_);
   _COMM_TRY
      vector<std::string> tmp = connection_->unSubscribe(key.toXml(), qos.toXml());
      vector<std::string>::const_iterator iter = tmp.begin();
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
   Lock lock(mutex_, orbIsThreadSafe_);
   _COMM_TRY
      if (log_.call()) log_.call(ME, "publish");
      string ret = connection_->publish(msgUnit);
      if (log_.trace()) log_.trace(ME, "successfully published");
      return PublishReturnQos(global_, statusQosFactory_.readObject(ret));
   _COMM_CATCH("::publish", false, false)
}

void CorbaDriver::publishOneway(const vector<MessageUnit> &msgUnitArr)
{
   Lock lock(mutex_, orbIsThreadSafe_);
   _COMM_TRY
      connection_->publishOneway(msgUnitArr);
   _COMM_CATCH("::publishOneway", false, false)
}

vector<PublishReturnQos> CorbaDriver::publishArr(const vector<MessageUnit> &msgUnitArr)
{
   Lock lock(mutex_, orbIsThreadSafe_);
   _COMM_TRY
      vector<std::string> tmp = connection_->publishArr(msgUnitArr);
      vector<std::string>::const_iterator iter = tmp.begin();
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
   Lock lock(mutex_, orbIsThreadSafe_);
      vector<std::string> tmp = connection_->erase(key.toXml(), qos.toXml());
      vector<std::string>::const_iterator iter = tmp.begin();
      vector<EraseReturnQos> ret;
      while (iter != tmp.end()) {
         ret.insert(ret.end(),  EraseReturnQos(global_, statusQosFactory_.readObject(*iter)) );
         iter++;
      }
      return ret;
   _COMM_CATCH("::erase", false, false)
}

I_ProgressListener* CorbaDriver::registerProgressListener(I_ProgressListener *listener) {
   log_.warn("CorbaDriver", "registerProgressListener() is not implemented, we ignore the provided listener.");
   return 0;
}

std::string CorbaDriver::usage()
{
   return CorbaConnection::usage();
}


// Exception conversion ....
org::xmlBlaster::util::XmlBlasterException
CorbaDriver::convertFromCorbaException(const serverIdl::XmlBlasterException& ex)
{
   string tmp = "";
   return org::xmlBlaster::util::XmlBlasterException(ex.errorCodeStr.in()==0?tmp:string(ex.errorCodeStr),
                                                     ex.node.in()==0?tmp:string(ex.node),
                                                     ex.location.in()==0?tmp:string(ex.location),
                                                     ex.lang.in()==0?tmp:string(ex.lang),
                                                     ex.message.in()==0?tmp:string(ex.message),
                                                     ex.versionInfo.in()==0?tmp:string(ex.versionInfo),
                                                     ex.timestampStr.in()==0?tmp:string(ex.timestampStr),
                                                     ex.stackTrace.in()==0?tmp:string(ex.stackTrace),
                                                     ex.embeddedMessage.in()==0?tmp:string(ex.embeddedMessage),
                                                     ex.transactionInfo.in()==0?tmp:string(ex.transactionInfo));
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

