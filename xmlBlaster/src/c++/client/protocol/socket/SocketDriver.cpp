/*------------------------------------------------------------------------------
Name:      SocketDriver.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The client driver for the socket protocol
------------------------------------------------------------------------------*/
#include <client/protocol/socket/SocketDriver.h>
#include <util/ErrorCode.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include <util/lexical_cast.h>
#include <XmlBlasterAccessUnparsed.h> // The C SOCKET client library
#include <util/qos/ConnectQosFactory.h>

namespace org {
 namespace xmlBlaster {
  namespace client {
   namespace protocol {
    namespace socket {

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::client::key;
using namespace org::xmlBlaster::client::qos;

void SocketDriver::freeResources(bool deleteConnection)
{
   freeXmlBlasterAccessUnparsed(connection_);
   connection_ = NULL;
}

#define _COMM_TRY try {


#define _COMM_CATCH(methodName, deleteConnection)                     \
   }                                                                  \
   catch(::XmlBlasterException &ex) {                                 \
      freeResources(deleteConnection);                                \
      throw convertFromSocketException(ex);                           \
   }                                                                  \
   catch(const exception &ex) {                                       \
      freeResources(deleteConnection);                                \
      throw org::xmlBlaster::util::XmlBlasterException(INTERNAL_UNKNOWN, \
                       "unknown node", ME + string(methodName), "en", \
                       "client-c++", "", "",                          \
                       string("type='exception', msg='")              \
                        + ex.what() + "'");                           \
   }                                                                  \
   catch(const string &ex) {                                          \
      freeResources(deleteConnection);                                \
      throw org::xmlBlaster::util::XmlBlasterException(INTERNAL_UNKNOWN, \
                       "unknown node", ME + string(methodName), "en", \
                       "client-c++", "", "",                          \
                       string("type='string', msg='") + ex + "'");    \
   }                                                                  \
   catch(const char *ex) {                                            \
      freeResources(deleteConnection);                                \
      throw org::xmlBlaster::util::XmlBlasterException(INTERNAL_UNKNOWN, \
                       "unknown node", ME + string(methodName), "en", \
                       "client-c++", "", "",                          \
                       string("type='char*', msg='") + ex + "'");     \
   }                                                                  \
   catch(int ex) {                                                    \
      freeResources(deleteConnection);                                \
      throw org::xmlBlaster::util::XmlBlasterException(INTERNAL_UNKNOWN, \
                       "unknown node", ME + string(methodName), "en", \
                       "client-c++", "", "",                          \
       string("type='int', msg='") + lexical_cast<std::string>(ex) + "'"); \
   }                                                                  \
   catch (...) {                                                      \
      freeResources(deleteConnection);                                \
      throw org::xmlBlaster::util::XmlBlasterException(INTERNAL_UNKNOWN, \
                       "unknown node", ME + string(methodName), "en");\
   }

SocketDriver::SocketDriver(const SocketDriver& socketDriver)
   : mutex_(socketDriver.mutex_),
     ME("SocketDriver"), 
     global_(socketDriver.global_), 
     log_(socketDriver.log_),
     statusQosFactory_(socketDriver.global_) 
{
   // no instantiation of these since this should never be invoked (just to make it private)
   connection_      = NULL;
   if (log_.call()) log_.call("SocketDriver", string("Constructor"));
}

SocketDriver& SocketDriver::operator =(const SocketDriver& /*socketDriver*/)
{
   return *this;
}


SocketDriver::SocketDriver(Global& global, Mutex& mutex, const string instanceName)
   : mutex_(mutex),
     instanceName_(instanceName),
     connection_(NULL),
     ME(string("SocketDriver-") + instanceName), 
     global_(global), 
     log_(global.getLog("socket")),
     statusQosFactory_(global)
{
   if (log_.call()) log_.call("SocketDriver", string("getInstance for ") + instanceName);
   int argc = 0;
   char **argv = 0;
   _COMM_TRY
      connection_ = getXmlBlasterAccessUnparsed(argc, argv);
   _COMM_CATCH("::Constructor", true)
}

SocketDriver::~SocketDriver()
{
   try {
      if (connection_ != 0) {
         freeXmlBlasterAccessUnparsed(connection_);
         connection_ = 0;
      }
   }
   catch (...) {
      log_.error(ME, "Unexpected catch in ~SocketDriver()");
   }
}

bool myUpdate(::MsgUnitArr *msgUnitArr, void *userData,
                     ::XmlBlasterException *exception)
{
# ifdef DUMMY_
   size_t i;
   bool testException = false;
   /* XmlBlasterAccessUnparsed *xa = (XmlBlasterAccessUnparsed *)userData; */
   if (userData != 0) ;  /* Supress compiler warning */

   for (i=0; i<msgUnitArr->len; i++) {
      char *xml = messageUnitToXml(&msgUnitArr->msgUnitArr[i]);
      printf("[client] CALLBACK update(): Asynchronous message update arrived:%s\n",
             xml);
      free(xml);
      msgUnitArr->msgUnitArr[i].responseQos = 
                  strcpyAlloc("<qos><state id='OK'/></qos>");
      /* Return QoS: Everything is OK */
   }
   if (testException) {
      strncpy0(exception->errorCode, "user.clientCode",
               XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      strncpy0(exception->message, "I don't want these messages",
               XMLBLASTEREXCEPTION_MESSAGE_LEN);
      return false;
   }
# endif
   return true;
}

void SocketDriver::initialize(const string& name, I_Callback &client)
{
   ::XmlBlasterException socketException;
   Lock lock(mutex_);
   _COMM_TRY
      if (log_.trace()) log_.trace(ME, "Before createCallbackServer");
      if (connection_->initialize(connection_, myUpdate, &socketException) == false) {
         log_.error(ME, "Connection to xmlBlaster failed,"
                " please start the server or check your configuration\n");
         freeXmlBlasterAccessUnparsed(connection_);
         connection_ = NULL;
      }
      if (log_.trace()) log_.trace(ME, "After createCallbackServer");
   _COMM_CATCH("::initialize", true)
}

string SocketDriver::getCbProtocol()
{
    return "SOCKET";
}                             

string SocketDriver::getCbAddress()
{
   _COMM_TRY
      return string("socket://") + string(connection_->callbackP->hostCB) + ":" +
             lexical_cast<std::string>(connection_->callbackP->portCB);
   _COMM_CATCH("::getCbAddress", false)
}

bool SocketDriver::shutdownCb()
{
   _COMM_TRY
      connection_->callbackP->shutdown(connection_->callbackP);
      return true;
   _COMM_CATCH("::shutdownCb", false)
}

ConnectReturnQos SocketDriver::connect(const ConnectQos& qos)
{
   ::XmlBlasterException socketException;
   Lock lock(mutex_);
   _COMM_TRY
      loginName_ = qos.getUserId();
      char *retQos = connection_->connect(connection_, qos.toXml().c_str(),
                                          myUpdate, &socketException);
      if (*socketException.errorCode != 0) {
         throw socketException;
      }
      ConnectQosFactory factory(global_);
      ConnectReturnQos connectReturnQos = factory.readObject(retQos);
      xmlBlasterFree(retQos);
      secretSessionId_ = connectReturnQos.getSecretSessionId();
      return connectReturnQos;
   _COMM_CATCH("::connect", false)
}

bool SocketDriver::disconnect(const DisconnectQos& qos)
{
# ifdef TODO
   Lock lock(mutex_);
   _COMM_TRY
      return connection_->disconnect(connection_, qos.toXml());
   _COMM_CATCH("::disconnect", false)
# endif
   return true;
}

string SocketDriver::getProtocol()
{
   return "SOCKET";
}

bool SocketDriver::shutdown()
{
# ifdef TODO
   Lock lock(mutex_);
   _COMM_TRY
      return connection_->shutdown();
   _COMM_CATCH("::shutdown", false)
# endif
   return true;
}

void SocketDriver::resetConnection()
{
   log_.error(ME, "'SocketDriver::resetConnection' not implemented");
}

string SocketDriver::getLoginName()
{
   return loginName_;
}

bool SocketDriver::isLoggedIn()
{
   return connection_->isConnected(connection_);
}

string SocketDriver::ping(const string& qos)
{
   Lock lock(mutex_);
   _COMM_TRY
      return connection_->ping(connection_, qos.c_str());
   _COMM_CATCH("::ping", false)
}

SubscribeReturnQos SocketDriver::subscribe(const SubscribeKey& key, const SubscribeQos& qos)
{
# ifdef TODO
   Lock lock(mutex_);
   _COMM_TRY
      string ret = connection_->subscribe(key.toXml(), qos.toXml());
      return SubscribeReturnQos(global_, statusQosFactory_.readObject(ret));
   _COMM_CATCH("::subscribe", false)
# endif
   StatusQosData q(global_);
   return SubscribeReturnQos(global_, q);
}

vector<MessageUnit> SocketDriver::get(const GetKey& key, const GetQos& qos)
{
# ifdef TODO
   Lock lock(mutex_);
   _COMM_TRY
      return connection_->get(key.toXml(), qos.toXml());
   _COMM_CATCH("::get", false)
# endif
   return vector<MessageUnit>();
}

vector<UnSubscribeReturnQos>
SocketDriver::unSubscribe(const UnSubscribeKey& key, const UnSubscribeQos& qos)
{
# ifdef TODO
   Lock lock(mutex_);
   _COMM_TRY
      vector<std::string> tmp = connection_->unSubscribe(key.toXml(), qos.toXml());
      vector<std::string>::const_iterator iter = tmp.begin();
      vector<UnSubscribeReturnQos> ret;
      while (iter != tmp.end()) {
         ret.insert(ret.end(),  UnSubscribeReturnQos(global_, statusQosFactory_.readObject(*iter)));
         iter++;
      }
      return ret;
   _COMM_CATCH("::unSubscribe", false)
# endif
   return vector<UnSubscribeReturnQos>();
}

PublishReturnQos SocketDriver::publish(const MessageUnit& msgUnit)
{
# ifdef TODO
   Lock lock(mutex_);
   _COMM_TRY
      if (log_.call()) log_.call(ME, "publish");
      string ret = connection_->publish(msgUnit);
      if (log_.trace()) log_.trace(ME, "successfully published");
      return PublishReturnQos(global_, statusQosFactory_.readObject(ret));
   _COMM_CATCH("::publish", false)
# endif
   StatusQosData q(global_);
   return PublishReturnQos(global_, q);
}

void SocketDriver::publishOneway(const vector<MessageUnit> &msgUnitArr)
{
# ifdef TODO
   Lock lock(mutex_);
   _COMM_TRY
      connection_->publishOneway(msgUnitArr);
   _COMM_CATCH("::publishOneway", false)
# endif
}

vector<PublishReturnQos> SocketDriver::publishArr(vector<MessageUnit> msgUnitArr)
{
# ifdef TODO
   Lock lock(mutex_);
   _COMM_TRY
      vector<std::string> tmp = connection_->publishArr(msgUnitArr);
      vector<std::string>::const_iterator iter = tmp.begin();
      vector<PublishReturnQos> ret;
      while (iter != tmp.end()) {
         ret.insert(ret.end(),  PublishReturnQos(global_, statusQosFactory_.readObject(*iter)) );
         iter++;
      }
      return ret;
   _COMM_CATCH("::publishArr", false)
# endif
   return vector<PublishReturnQos>();
}

vector<EraseReturnQos> SocketDriver::erase(const EraseKey& key, const EraseQos& qos)
{
# ifdef TODO
   _COMM_TRY
   Lock lock(mutex_);
      vector<std::string> tmp = connection_->erase(key.toXml(), qos.toXml());
      vector<std::string>::const_iterator iter = tmp.begin();
      vector<EraseReturnQos> ret;
      while (iter != tmp.end()) {
         ret.insert(ret.end(),  EraseReturnQos(global_, statusQosFactory_.readObject(*iter)) );
         iter++;
      }
      return ret;
   _COMM_CATCH("::erase", false)
# endif
   return vector<EraseReturnQos>();
}

string SocketDriver::usage()
{
   char usage[XMLBLASTER_MAX_USAGE_LEN];
   ::xmlBlasterAccessUnparsedUsage(usage);
   return string(usage);
}

// Exception conversion ....
org::xmlBlaster::util::XmlBlasterException SocketDriver::convertFromSocketException(const ::XmlBlasterException& ex)
{
   string tmp = "";     // Missing: serverSide?
   return org::xmlBlaster::util::XmlBlasterException(
            ex.errorCode==0?tmp:string(ex.errorCode),
            tmp,
            tmp,
            tmp,
            (*ex.message=='\0')?tmp:string(ex.message),
            tmp,
            tmp,
            tmp,
            tmp,
            tmp);
}


::XmlBlasterException SocketDriver::convertToSocketException(org::xmlBlaster::util::XmlBlasterException& ex)
{
   ::XmlBlasterException exSocket;
   ::initializeXmlBlasterException(&exSocket);
   strncpy0(exSocket.errorCode, ex.getErrorCodeStr().c_str(), XMLBLASTEREXCEPTION_ERRORCODE_LEN);
   strncpy0(exSocket.message, ex.getMessage().c_str(), XMLBLASTEREXCEPTION_MESSAGE_LEN);
   //exSocket.remote = ??
   return exSocket;
}

}}}}} // namespaces

