/*------------------------------------------------------------------------------
Name:      CorbaDriver.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The client driver for the corba protocol
------------------------------------------------------------------------------*/

#include <client/protocol/corba/CorbaDriver.h>
#include <util/ErrorCode.h>
#include <util/XmlBlasterException.h>

using org::xmlBlaster::util::MessageUnit;
using org::xmlBlaster::util::XmlBlasterException;
using org::xmlBlaster::util::ErrorCode;
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


CorbaDriver::CorbaDriver(int args, const char * const argc[],
                         bool connectionOwner) : ME("CorbaDriver")
{
   connection_      = NULL;
   defaultCallback_ = NULL;
   _COMM_TRY
      connection_ = new CorbaConnection(args, argc, connectionOwner);
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
   _COMM_TRY
      if (defaultCallback_ != NULL) delete defaultCallback_;
      defaultCallback_ = NULL;
      defaultCallback_ =  new DefaultCallback(name, &client, 0);
      if (connection_ != NULL) delete connection_;
      connection_ = NULL;
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
      return connection_->getAddress();
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
   _COMM_TRY
      return connection_->connect(qos);
   _COMM_CATCH("::connect", false, false)
}

bool CorbaDriver::disconnect(const string& qos)
{
   _COMM_TRY
      return connection_->disconnect(qos);
   _COMM_CATCH("::disconnect", false, false)
}

string CorbaDriver::getProtocol()
{
   return "IOR";
}

string CorbaDriver::loginRaw()
{
   _COMM_TRY
      connection_->loginRaw();
      return getLoginName();
   _COMM_CATCH("::loginRaw", false, false)
}

bool CorbaDriver::shutdown()
{
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
   _COMM_TRY
      return connection_->ping(qos);
   _COMM_CATCH("::ping", false, false)
}

string CorbaDriver::subscribe(const string& xmlKey, const string& qos)
{
   _COMM_TRY
      return connection_->subscribe(xmlKey, qos);
   _COMM_CATCH("::subscribe", false, false)
}

vector<MessageUnit> CorbaDriver::get(const string& xmlKey, const string& qos)
{
   _COMM_TRY
      return connection_->get(xmlKey, qos);
   _COMM_CATCH("::get", false, false)
}

vector<string>
CorbaDriver::unSubscribe(const string& xmlKey, const string& qos)
{
   _COMM_TRY
      return connection_->unSubscribe(xmlKey, qos);
   _COMM_CATCH("::unSubscribe", false, false)
}

string CorbaDriver::publish(const MessageUnit& msgUnit)
{
   _COMM_TRY
      return connection_->publish(msgUnit);
   _COMM_CATCH("::publish", false, false)
}

void CorbaDriver::publishOneway(const vector<MessageUnit> &msgUnitArr)
{
   _COMM_TRY
      connection_->publishOneway(msgUnitArr);
   _COMM_CATCH("::publishOneway", false, false)
}

vector<string> CorbaDriver::publishArr(vector<MessageUnit> msgUnitArr)
{
   _COMM_TRY
      return connection_->publishArr(msgUnitArr);
   _COMM_CATCH("::publishArr", false, false)
}

vector<string> CorbaDriver::erase(const string& xmlKey, const string& qos)
{
   _COMM_TRY
      return connection_->erase(xmlKey, qos);
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
   return org::xmlBlaster::util::XmlBlasterException(string(ex.errorCodeStr),
                                                     string(ex.node),
                                                     string(ex.location),
                                                     string(ex.lang),
                                                     string(ex.message),
                                                     string(ex.versionInfo),
                                                     string(ex.timestampStr),
                                                     string(ex.stackTrace),
                                                     string(ex.embeddedMessage),
                                                     string(ex.transactionInfo));
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

