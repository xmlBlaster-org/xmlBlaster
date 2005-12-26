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
#include <util/Properties.h>
#include <string>
#include <stdarg.h> // va_start
#include <stdio.h> // vsnprintf for g++ 2.9x only

static void myLogger(void *logUserP, 
                     XMLBLASTER_LOG_LEVEL currLevel,
                     XMLBLASTER_LOG_LEVEL level,
                     const char *location, const char *fmt, ...);

//static ::XmlBlasterNumReadFunc callbackProgressListener;  // what's wrong with this?
static void callbackProgressListener(void *userP, const size_t currBytesRead, const size_t nbytes);

/**
 * Customized logging output is handled by this method. 
 * <p>
 * We register this function with 
 * </p>
 * <pre>
 * xa->log = myLogger;
 * </pre>
 * @param currLevel The actual log level of the client
 * @param level The level of this log entry
 * @param location A string describing the code place
 * @param fmt The formatting string
 * @param ... Other variables to log, corresponds to 'fmt'
 * @see xmlBlaster/src/c/msgUtil.c: xmlBlasterDefaultLogging() is the default
 *      implementation
 */
static void myLogger(void *logUserP, 
                     XMLBLASTER_LOG_LEVEL currLevel,
                     XMLBLASTER_LOG_LEVEL level,
                     const char *location, const char *fmt, ...)
{
   /* Guess we need no more than 200 bytes. */
   int n, size = 200;
   char *p = 0;
   va_list ap;
   org::xmlBlaster::client::protocol::socket::SocketDriver *sd =
         (org::xmlBlaster::client::protocol::socket::SocketDriver *)logUserP;
   org::xmlBlaster::util::I_Log& log = sd->getLog();

   if (level > currLevel) { /* XMLBLASTER_LOG_ERROR, XMLBLASTER_LOG_WARN, XMLBLASTER_LOG_INFO, XMLBLASTER_LOG_TRACE */
      return;
   }
   if ((p = (char *)malloc (size)) == NULL)
      return;

   for (;;) {
      /* Try to print in the allocated space. */
      va_start(ap, fmt);
      n = VSNPRINTF(p, size, fmt, ap); /* UNIX: vsnprintf(), WINDOWS: _vsnprintf() */
      va_end(ap);
      /* If that worked, print the string to console. */
      if (n > -1 && n < size) {
         if (level == XMLBLASTER_LOG_INFO)
            log.info(location, p);
         else if (level == XMLBLASTER_LOG_WARN)
            log.warn(location, p);
         else if (level == XMLBLASTER_LOG_ERROR)
            log.error(location, p);
         else
            log.trace(location, p);
         free(p);
         return;
      }
      /* Else try again with more space. */
      if (n > -1)    /* glibc 2.1 */
         size = n+1; /* precisely what is needed */
      else           /* glibc 2.0 */
         size *= 2;  /* twice the old size */
      if ((p = (char *)realloc (p, size)) == NULL) {
         return;
      }
   }
}

/**
 * Access the read socket progress. 
 * You need to register this function pointer if you want to see the progress of huge messages
 * on slow connections.
 */
static void callbackProgressListener(void *userP, const size_t currBytesRead, const size_t nbytes) {
   org::xmlBlaster::client::protocol::socket::SocketDriver *sd =
         (org::xmlBlaster::client::protocol::socket::SocketDriver *)userP;
   //org::xmlBlaster::util::I_Log& log = sd->getLog();
   //if (log.trace()) log.trace("SocketDriver", "Update data progress currBytesRead=" +
   //                          org::xmlBlaster::util::lexical_cast<std::string>(currBytesRead) +
   //                          " nbytes=" + org::xmlBlaster::util::lexical_cast<std::string>(nbytes));
   if (sd != 0 && sd->progressListener_ != 0) {
      sd->progressListener_->progress("", currBytesRead, nbytes);
   }
}

namespace org {
 namespace xmlBlaster {
  namespace client {
   namespace protocol {
    namespace socket {

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::key;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::client::protocol;
using namespace org::xmlBlaster::client::key;
using namespace org::xmlBlaster::client::qos;

static XMLBLASTER_C_bool myUpdate(::MsgUnitArr *msgUnitArr, void *userData,
                     ::ExceptionStruct *exception);

void SocketDriver::freeResources(bool deleteConnection)
{
   if (log_.call()) log_.call(ME, "freeResources("+lexical_cast<std::string>(deleteConnection)+") connection_=" + ((connection_==0)?"0":lexical_cast<std::string>(connection_)));
   if (deleteConnection && connection_ != 0) {
      freeXmlBlasterAccessUnparsed(connection_);
      connection_ = 0;
   }
   if (deleteConnection && argsStructP_ != 0) {
      global_.freeArgs(*argsStructP_);
      delete argsStructP_;
      argsStructP_ = 0;
   }
}

/*
 Note on exception handling:
 If we throw an exception, our master ConnectionsHandler.cpp will
 catch it and to a shutdown() on us. This will cleanup the resources.
 */
#define catch_MACRO(methodName, deleteConnection)                     \
   catch(const XmlBlasterException *ex) {                             \
      freeResources(deleteConnection);                                \
      throw ex;                                                       \
   }                                                                  \
   catch(XmlBlasterException &ex) {                                   \
      freeResources(deleteConnection);                                \
      ex.setLocation(ME + string(methodName));                        \
      throw ex;                                                       \
   }                                                                  \
   catch(const ::ExceptionStruct *ex) {                               \
      freeResources(deleteConnection);                                \
      org::xmlBlaster::util::XmlBlasterException xx = convertFromSocketException(*ex); \
      delete ex;                                                      \
      throw xx;                                                       \
   }                                                                  \
   catch(const ::ExceptionStruct &ex) {                               \
      freeResources(deleteConnection);                                \
      throw convertFromSocketException(ex);                           \
   }                                                                  \
   catch(const exception &ex) {                                       \
      freeResources(deleteConnection);                                \
      throw org::xmlBlaster::util::XmlBlasterException(INTERNAL_UNKNOWN, \
                       loginName_, ME + string(methodName), "en", \
                       global_.getVersion() + " " + global_.getBuildTimestamp(), "", "", \
                       string("type='exception', msg='")              \
                        + ex.what() + "'");                           \
   }                                                                  \
   catch(const string &ex) {                                          \
      freeResources(deleteConnection);                                \
      throw org::xmlBlaster::util::XmlBlasterException(INTERNAL_UNKNOWN, \
                       loginName_, ME + string(methodName), "en", \
                       global_.getVersion() + " " + global_.getBuildTimestamp(), "", "", \
                       string("type='string', msg='") + ex + "'");    \
   }                                                                  \
   catch(const char *ex) {                                            \
      freeResources(deleteConnection);                                \
      throw org::xmlBlaster::util::XmlBlasterException(INTERNAL_UNKNOWN, \
                       loginName_, ME + string(methodName), "en", \
                       global_.getVersion() + " " + global_.getBuildTimestamp(), "", "", \
                       string("type='char*', msg='") + ex + "'");     \
   }                                                                  \
   catch(int ex) {                                                    \
      freeResources(deleteConnection);                                \
      throw org::xmlBlaster::util::XmlBlasterException(INTERNAL_UNKNOWN, \
                       loginName_, ME + string(methodName), "en", \
                       global_.getVersion() + " " + global_.getBuildTimestamp(), "", "", \
       string("type='int', msg='") + lexical_cast<std::string>(ex) + "'"); \
   }                                                                  \
   catch (...) {                                                      \
      freeResources(deleteConnection);                                \
      throw org::xmlBlaster::util::XmlBlasterException(INTERNAL_UNKNOWN, \
                       loginName_, ME + string(methodName), "en", \
                       global_.getVersion() + " " + global_.getBuildTimestamp());\
   }

SocketDriver::SocketDriver(const SocketDriver& socketDriver)
   : mutex_(socketDriver.mutex_),
     ME("SocketDriver"), 
     argsStructP_(0),
     global_(socketDriver.global_), 
     log_(socketDriver.log_),
     statusQosFactory_(socketDriver.global_), 
     msgKeyFactory_(socketDriver.global_), 
     msgQosFactory_(socketDriver.global_),
     callbackClient_(0),
     progressListener_(0)
{
   // no instantiation of these since this should never be invoked (just to make it private)
   connection_      = NULL;
   argsStructP_ = new ArgsStruct_T;
   //memset(argsStructP_, '\0', sizeof(ArgsStruct_T));
   global_.fillArgs(*argsStructP_);
   if (log_.call()) log_.call(ME, string("Copy constructor"));
}

SocketDriver& SocketDriver::operator =(const SocketDriver& /*socketDriver*/)
{
   if (log_.call()) log_.call(ME, "operator=()");
   return *this;
}


SocketDriver::SocketDriver(Global& global, const string instanceName)
   : mutex_(),
     instanceName_(instanceName),
     connection_(NULL),
     ME(string("SocketDriver-") + instanceName), 
     argsStructP_(0),
     global_(global), 
     log_(global.getLog("org.xmlBlaster.client.protocol.socket")),
     statusQosFactory_(global),
     msgKeyFactory_(global),
     msgQosFactory_(global),
     callbackClient_(0),
     progressListener_(0)
{
   if (log_.call()) log_.call("SocketDriver", string("getInstance for ") + instanceName);

   argsStructP_ = new ArgsStruct_T;
   if (!global_.getProperty().propertyExists("logLevel")) {
      if (log_.trace() || log_.call())
         global_.getProperty().setProperty("logLevel", "TRACE");
      else if (log_.dump())
         global_.getProperty().setProperty("logLevel", "DUMP");
   }

   global_.fillArgs(*argsStructP_);
   try {
      connection_ = getXmlBlasterAccessUnparsed((int)argsStructP_->argc, argsStructP_->argv);
      if (connection_) {
         connection_->userObject = this; // Transports us to the myUpdate() method
         connection_->log = myLogger;    // Register our own logging function
         connection_->logUserP = this;   // Pass ourself to myLogger()
         if (log_.dump()) {
            log_.dump(ME, "C properties:");
            ::dumpProperties(connection_->props);
         }
      }
      else {
         log_.error(ME, "Allocation of C SOCKET library failed");
      }
   } catch_MACRO("::Constructor", true)
}

/**
 * Called on polling, must be synchronized from outside,
 * throws an exception on failure
 */
void SocketDriver::reconnectOnIpLevel(void)
{
   log_.trace(ME, "Trying to reconnect to server");

   freeResources(true); // Cleanup if old connection exists

   // Give a chance to new configuration settings
   if (argsStructP_ != 0) {
      global_.freeArgs(*argsStructP_);
      delete argsStructP_;
      argsStructP_ = 0;
   }
   argsStructP_ = new ArgsStruct_T;
   global_.fillArgs(*argsStructP_);

   ::ExceptionStruct socketException;

   try {
      connection_ = getXmlBlasterAccessUnparsed((int)argsStructP_->argc, argsStructP_->argv);
      connection_->userObject = this; // Transports us to the myUpdate() method
      connection_->log = myLogger;    // Register our own logging function
      connection_->logUserP = this;   // Pass ourself to myLogger()
   } catch_MACRO("::Constructor", true)
   
   try {
      if (log_.trace()) log_.trace(ME, "Before createCallbackServer");
      if (connection_->initialize(connection_, myUpdate, &socketException) == false) {
         if (log_.trace()) log_.trace(ME, string("Reconnection to xmlBlaster failed, please start the server or check your network: ") + socketException.message);
         throw socketException;
      }
      registerProgressListener(this->progressListener_); // Re-register
      if (log_.trace()) log_.trace(ME, "After createCallbackServer");
   } catch_MACRO("::initialize", true)
}

SocketDriver::~SocketDriver()
{
   if (log_.call()) log_.call(ME, "~SocketDriver()");
   try {
      freeResources(true);
   }
   catch (...) {
      log_.error(ME, "Unexpected catch in ~SocketDriver()");
   }
}

XMLBLASTER_C_bool myUpdate(::MsgUnitArr *msgUnitArr, void *userData,
                     ::ExceptionStruct *exception)
{
   XmlBlasterAccessUnparsed *xa = (XmlBlasterAccessUnparsed *)userData;
   SocketDriver* socketDriver = static_cast<SocketDriver*>(xa->userObject);
   Global& global = socketDriver->getGlobal();
   I_Log& log = socketDriver->getLog();
   const string &ME = socketDriver->me();

   try {
      for (size_t i=0; i<msgUnitArr->len; i++) {
         //char *xml = messageUnitToXml(&msgUnitArr->msgUnitArr[i]);
         //printf("[client] CALLBACK update(): Asynchronous message update arrived:%s\n",xml);
                        //xmlBlasterFree(xml);
         if (log.trace()) log.trace(ME, "Received callback message");
         ::MsgUnit& msgUnit = msgUnitArr->msgUnitArr[i];
         I_Callback* cb = socketDriver->getCallbackClient();
         if (cb != 0) {
            UpdateKey updateKey(global, socketDriver->getMsgKeyFactory().readObject(string(msgUnit.key)));
            UpdateQos updateQos(global, socketDriver->getMsgQosFactory().readObject(string(msgUnit.qos)));
            std::string retQos = cb->update(msgUnitArr->secretSessionId,
                          updateKey, (const unsigned char*)msgUnit.content,
                          msgUnit.contentLen, updateQos);
            msgUnitArr->msgUnitArr[i].responseQos = strcpyAlloc(retQos.c_str());
         }
         else { /* Return QoS: Everything is OK */
            log.error(ME, string("Ignoring unexpected update message as client has not registered a callback: ") + msgUnit.key);
            msgUnitArr->msgUnitArr[i].responseQos = strcpyAlloc(Constants::RET_OK); // "<qos><state id='OK'/></qos>");
         }
      }
      //throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "TEST THROWING EXCEPTION");
   } 
   catch (XmlBlasterException &e) {
      string tmp = "Exception caught in C++ update(), " +
                   lexical_cast<std::string>(msgUnitArr->len) +
                   " messages are handled as not delivered: " +
                   e.getMessage();
      log.error(ME, tmp);
      for (size_t i=0; i<msgUnitArr->len; i++) {
         char* xml = messageUnitToXmlLimited(&msgUnitArr->msgUnitArr[i], 100);
         log.error(ME, xml);
         xmlBlasterFree(xml);
      }
      strncpy0(exception->errorCode, e.getErrorCodeStr().c_str(), XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      strncpy0(exception->message, tmp.c_str(), XMLBLASTEREXCEPTION_MESSAGE_LEN);
      return (XMLBLASTER_C_bool)0;
   }
   catch(...) {
      string tmp = "Unidentified exception caught in C++ update(), " + lexical_cast<std::string>(msgUnitArr->len) + " messages are handled as not delivered";
      log.error(ME, tmp);
      for (size_t i=0; i<msgUnitArr->len; i++) {
         char* xml = messageUnitToXmlLimited(&msgUnitArr->msgUnitArr[i], 100);
         log.error(ME, xml);
         xmlBlasterFree(xml);
      }
      strncpy0(exception->errorCode, "user.update.error", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      strncpy0(exception->message, tmp.c_str(), XMLBLASTEREXCEPTION_MESSAGE_LEN);
      return (XMLBLASTER_C_bool)0;
   }
   return (XMLBLASTER_C_bool)1;
}

I_Callback* SocketDriver::getCallbackClient()
{
   return callbackClient_;
}

/** Enforced by I_CallbackServer */
void SocketDriver::initialize(const string& name, I_Callback &client)
{
   ::ExceptionStruct socketException;
   ME = string("SocketDriver-") + instanceName_ + "-" + name;
   if (log_.call()) log_.call(ME, "initialize() callback server");
   callbackClient_ = &client;
   Lock lock(mutex_);
   if (connection_ == 0) {
      if (log_.trace()) log_.trace(ME, "ERROR: connection_ is null");
      throw org::xmlBlaster::util::XmlBlasterException(INTERNAL_UNKNOWN, name, ME + ".initialize", "en",
                       global_.getVersion() + " " + global_.getBuildTimestamp() + " The connection_ handle is NULL");
   }
   try {
      if (log_.trace()) log_.trace(ME, "Before createCallbackServer");
      if (connection_->initialize(connection_, myUpdate, &socketException) == false) {
         log_.warn(ME, "Connection to xmlBlaster failed,"
                " please start the server or check your configuration\n");
         freeResources(true);
      }
      if (log_.trace()) log_.trace(ME, "After createCallbackServer");
   } catch_MACRO("::initialize", true)
}

string SocketDriver::getCbProtocol()
{
    return Constants::SOCKET; // "SOCKET";
}                             

string SocketDriver::getCbAddress()
{
   Lock lock(mutex_);
   if (connection_ == 0 || connection_->callbackP == 0) {
      return string("socket://:");
   }
   try {
      return string("socket://") + string(connection_->callbackP->hostCB) + ":" +
             lexical_cast<std::string>(connection_->callbackP->portCB);
   } catch_MACRO("::getCbAddress", false)
}

bool SocketDriver::shutdownCb()
{
   Lock lock(mutex_);
   if (connection_ == 0 || connection_->callbackP == 0) return false;
   connection_->callbackP->shutdown(connection_->callbackP);
   return true;
}

ConnectReturnQosRef SocketDriver::connect(const ConnectQosRef& qos) //throw (XmlBlasterException) // Visual C++ emits a warning with this throw clause
{
   if (log_.call()) log_.call(ME, string("connect() ") + string((connection_==0)?"connection_==0":"connection_!=0") +
                              ", secretSessionId_="+secretSessionId_);
                              //+" isConnected=" + ((connection_==0)?XMLBLASTER_FALSE:lexical_cast<string>(connection_->isConnected(connection_))));
   ::ExceptionStruct socketException;
   Lock lock(mutex_);
   try {
      loginName_ = qos->getUserId();
      if (connection_ == 0) {
         reconnectOnIpLevel(); // Connects on IP level only, throws an exception on failure
         if (secretSessionId_ != "") {
            qos->getSessionQos().setSecretSessionId(secretSessionId_);
         }
         if (connection_ != 0 && connection_->callbackP != 0) {
            ConnectQos *qq = const_cast<ConnectQos*>(&(*qos));
            if (qq->getSessionCbQueueProperty().getCurrentCallbackAddress()->getType() == Constants::SOCKET) {
               // Force callback address, it could have changed on reconnect (checked to cb not be a delegate)
               string addr = string("socket://") + string(connection_->callbackP->hostCB) + ":" +
                      lexical_cast<std::string>(connection_->callbackP->portCB);
               qq->getSessionCbQueueProperty().getCurrentCallbackAddress()->setAddress(addr);
               log_.trace(ME, "Setting callback address to " + addr);
            }
         }
      }

      char *retQos = connection_->connect(connection_, qos->toXml().c_str(),
                                          myUpdate, &socketException);
      if (*socketException.errorCode != 0) {
         throw socketException; // Is converted to util::XmlBlasterException in catch_MACRO
      }
      ConnectQosFactory factory(global_);
      ConnectReturnQosRef connectReturnQos = factory.readObject(retQos);
      xmlBlasterFree(retQos);
      secretSessionId_ = connectReturnQos->getSecretSessionId();
      return connectReturnQos;
   } catch_MACRO("::connect", false)
}

bool SocketDriver::disconnect(const DisconnectQos& qos)
{
   if (log_.call()) log_.call(ME, "disconnect()");
   if (connection_ == 0) return false;
   ::ExceptionStruct socketException;
   Lock lock(mutex_);
   try {
      bool ret = connection_->disconnect(connection_, qos.toXml().c_str(), &socketException);
      if (*socketException.errorCode != 0) {
         throw socketException; // Is converted to util::XmlBlasterException in catch_MACRO
      }
      return ret;
   } catch_MACRO("::disconnect", false)
   return true;
}

string SocketDriver::getProtocol()
{
   return Constants::SOCKET; // "SOCKET";
}

/** Called when going to POLLING mode */
bool SocketDriver::shutdown()
{
   if (log_.call()) log_.call(ME, "shutdown()");
   Lock lock(mutex_);
   if (connection_ == 0) return false;
   freeResources(true);
   return true;
}

string SocketDriver::getLoginName()
{
   return loginName_;
}

bool SocketDriver::isLoggedIn()
{
   Lock lock(mutex_);
   return connection_ != 0 && connection_->isConnected(connection_);
}

string SocketDriver::ping(const string& qos)
{
   ::ExceptionStruct socketException;
   Lock lock(mutex_);
   if (connection_ == 0) {
      throw org::xmlBlaster::util::XmlBlasterException(INTERNAL_UNKNOWN, "", ME + ".ping", "en",
                       global_.getVersion() + " " + global_.getBuildTimestamp() + " The connection_ handle is NULL");
   }
   try {
      char *retQosP = connection_->ping(connection_, qos.c_str(), &socketException);
      if (retQosP == 0 || *socketException.errorCode != 0) {
         throw socketException; // Is converted to util::XmlBlasterException in catch_MACRO
      }
      string retQos(retQosP);
      xmlBlasterFree(retQosP);
      return retQos;
   } catch_MACRO("::ping", false)
}

SubscribeReturnQos SocketDriver::subscribe(const SubscribeKey& key, const SubscribeQos& qos)
{
   ::ExceptionStruct socketException;
   Lock lock(mutex_);
   if (connection_ == 0) {
      throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "Sorry, you are not connected to the server");
   }
   try {
      char *response = connection_->subscribe(connection_, key.toXml().c_str(), qos.toXml().c_str(), &socketException);
      if (*socketException.errorCode != 0) {
         throw socketException; // Is converted to util::XmlBlasterException in catch_MACRO
      }
      SubscribeReturnQos subscribeReturnQos(global_, statusQosFactory_.readObject(response));
      xmlBlasterFree(response);
      return subscribeReturnQos;
   } catch_MACRO("::subscribe", false)
}

vector<MessageUnit> SocketDriver::get(const GetKey& getKey, const GetQos& getQos)
{
   ::ExceptionStruct socketException;
   Lock lock(mutex_);
   if (connection_ == 0) {
      throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "Sorry, you are not connected to the server");
   }
   try {
      MsgUnitArr *msgUnitArr;  // The returned C struct array
      string key = getKey.toXml();
      string qos = getQos.toXml();
      msgUnitArr = connection_->get(connection_, key.c_str(), qos.c_str(), &socketException);
      if (*socketException.errorCode != 0) {
         throw socketException; // Is converted to util::XmlBlasterException in catch_MACRO
      }
      if (msgUnitArr != (MsgUnitArr *)0) {
         vector<MessageUnit> ret;
         for (size_t i=0; i<msgUnitArr->len; i++) {
            MsgKeyData msgKeyData = msgKeyFactory_.readObject(string(msgUnitArr->msgUnitArr[i].key));
            MsgQosData msgQosData = msgQosFactory_.readObject(string(msgUnitArr->msgUnitArr[i].qos));
            MessageUnit messageUnit(msgKeyData,
                         msgUnitArr->msgUnitArr[i].contentLen,
                         (const unsigned char*)msgUnitArr->msgUnitArr[i].content,
                         msgQosData);
            ret.insert(ret.end(),  messageUnit);
         }
         freeMsgUnitArr(msgUnitArr);
         return ret;
      }
   } catch_MACRO("::get", false)
   return vector<MessageUnit>();
}

vector<UnSubscribeReturnQos>
SocketDriver::unSubscribe(const UnSubscribeKey& key, const UnSubscribeQos& qos)
{
   ::ExceptionStruct socketException;
   Lock lock(mutex_);
   if (connection_ == 0) {
      throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "Sorry, you are not connected to the server");
   }
   try {
      QosArr* retC = connection_->unSubscribe(connection_, key.toXml().c_str(), qos.toXml().c_str(), &socketException);
      if (*socketException.errorCode != 0) {
         throw socketException; // Is converted to util::XmlBlasterException in catch_MACRO
      }
      vector<UnSubscribeReturnQos> ret;
      for (size_t ii=0; ii<retC->len; ii++) {
         ret.insert(ret.end(),  UnSubscribeReturnQos(global_, statusQosFactory_.readObject(retC->qosArr[ii])));
      }
      freeQosArr(retC);
      return ret;
   } catch_MACRO("::unSubscribe", false)
   return vector<UnSubscribeReturnQos>();
}

PublishReturnQos SocketDriver::publish(const MessageUnit& msgUnit)
{
   ::ExceptionStruct socketException;
   Lock lock(mutex_);
   if (connection_ == 0) {
      throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "Sorry, you are not connected to the server");
   }
   try {
      if (log_.call()) log_.call(ME, "publish");
      ::MsgUnit msgUnitC;
      const string key = msgUnit.getKey().toXml();
      msgUnitC.key = key.c_str();
      msgUnitC.content = reinterpret_cast<const char *>(msgUnit.getContent());
      msgUnitC.contentLen = msgUnit.getContentLen();
      const string qos = msgUnit.getQos().toXml();
      msgUnitC.qos = qos.c_str();

      char* response = connection_->publish(connection_, &msgUnitC, &socketException);

      if (*socketException.errorCode != 0) {
         throw socketException; // Is converted to util::XmlBlasterException in catch_MACRO
      }

      //freeMsgUnitData(&msgUnitC); -> not needed as it contains pointers only
      if (log_.trace()) log_.trace(ME, "successfully published");
      PublishReturnQos publishReturnQos(global_, statusQosFactory_.readObject(response));
      xmlBlasterFree(response);
      return publishReturnQos;
   } catch_MACRO("::publish", false)
}

void SocketDriver::publishOneway(const vector<MessageUnit> &msgUnitArr)
{
   ::ExceptionStruct socketException;
   Lock lock(mutex_);
   if (connection_ == 0) {
      throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "Sorry, you are not connected to the server");
   }
   try {

      // Copy C++ MessageUnit to C MsgUnit
      ::MsgUnitArr msgUnitArrC;
      vector<MessageUnit>::const_iterator iter;
      memset(&msgUnitArrC, 0, sizeof(::MsgUnitArr));
      msgUnitArrC.len = msgUnitArr.size();
      msgUnitArrC.msgUnitArr = (::MsgUnit *)calloc(msgUnitArrC.len, sizeof(::MsgUnit));
      size_t ii=0;
      vector<string> keyArr;  // We need to hold key/qos on the stack because toXml() returns a temporary string
      vector<string> qosArr;
      for (iter = msgUnitArr.begin(); iter != msgUnitArr.end(); ++iter) {
         //log_.trace(ME, "ii=" + lexical_cast<string>(ii) + ", len=" + lexical_cast<string>(msgUnitArrC.len));
         const MessageUnit& msgUnitCpp = *iter;
         ::MsgUnit& msgUnitC = msgUnitArrC.msgUnitArr[ii];
         keyArr.push_back(msgUnitCpp.getKey().toXml());
         msgUnitC.key = keyArr[ii].c_str();
         qosArr.push_back(msgUnitCpp.getQos().toXml());
         msgUnitC.qos = qosArr[ii].c_str();
         msgUnitC.contentLen = (size_t)msgUnitCpp.getContentLen();
         msgUnitC.content = reinterpret_cast<const char *>(msgUnitCpp.getContent());
         ii++;
      }

      connection_->publishOneway(connection_, &msgUnitArrC, &socketException);

      ::free(msgUnitArrC.msgUnitArr);

      if (*socketException.errorCode != 0) {
         throw socketException; // Is converted to util::XmlBlasterException in catch_MACRO
      }
   } catch_MACRO("::publishOneway", false)
}

vector<PublishReturnQos> SocketDriver::publishArr(const vector<MessageUnit> &msgUnitArr)
{
   ::ExceptionStruct socketException;
   Lock lock(mutex_);
   if (connection_ == 0) {
      throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "Sorry, you are not connected to the server");
   }
   try {

      // Copy C++ MessageUnit to C MsgUnit
      ::MsgUnitArr msgUnitArrC;
      vector<MessageUnit>::const_iterator iter;
      memset(&msgUnitArrC, 0, sizeof(::MsgUnitArr));
      msgUnitArrC.len = msgUnitArr.size();
      msgUnitArrC.msgUnitArr = (::MsgUnit *)calloc(msgUnitArrC.len, sizeof(::MsgUnit));
      size_t ii=0;
      vector<string> keyArr;  // We need to hold key/qos on the stack because toXml() returns a temporary string
      vector<string> qosArr;
      for (iter = msgUnitArr.begin(); iter != msgUnitArr.end(); ++iter) {
         //log_.trace(ME, "ii=" + lexical_cast<string>(ii) + ", len=" + lexical_cast<string>(msgUnitArrC.len));
         const MessageUnit& msgUnitCpp = *iter;
         ::MsgUnit& msgUnitC = msgUnitArrC.msgUnitArr[ii];
         keyArr.push_back(msgUnitCpp.getKey().toXml());
         msgUnitC.key = keyArr[ii].c_str();
         qosArr.push_back(msgUnitCpp.getQos().toXml());
         msgUnitC.qos = qosArr[ii].c_str();
         msgUnitC.contentLen = (size_t)msgUnitCpp.getContentLen();
         msgUnitC.content = reinterpret_cast<const char *>(msgUnitCpp.getContent());
         ii++;
      }

      QosArr* retC = connection_->publishArr(connection_, &msgUnitArrC, &socketException);

      ::free(msgUnitArrC.msgUnitArr);

      if (*socketException.errorCode != 0) {
         throw socketException; // Is converted to util::XmlBlasterException in catch_MACRO
      }
      vector<PublishReturnQos> ret;
      for (size_t jj=0; jj<retC->len; jj++) {
         ret.insert(ret.end(),  PublishReturnQos(global_, statusQosFactory_.readObject(retC->qosArr[jj])) );
      }
      freeQosArr(retC);
      return ret;
   } catch_MACRO("::publishArr", false)
   return vector<PublishReturnQos>();
}

vector<EraseReturnQos> SocketDriver::erase(const EraseKey& key, const EraseQos& qos)
{
   ::ExceptionStruct socketException;
   Lock lock(mutex_);
   if (connection_ == 0) {
      throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "Sorry, you are not connected to the server");
   }
   try {
      QosArr* retC = connection_->erase(connection_, key.toXml().c_str(), qos.toXml().c_str(), &socketException);
      if (*socketException.errorCode != 0) {
         throw socketException; // Is converted to util::XmlBlasterException in catch_MACRO
      }
      vector<EraseReturnQos> ret;
      for (size_t ii=0; ii<retC->len; ii++) {
         ret.insert(ret.end(),  EraseReturnQos(global_, statusQosFactory_.readObject(retC->qosArr[ii])) );
      }
      freeQosArr(retC);
      return ret;
   } catch_MACRO("::erase", false)
   return vector<EraseReturnQos>();
}

I_ProgressListener* SocketDriver::registerProgressListener(I_ProgressListener *listener) {
   I_ProgressListener *old = this->progressListener_;
   this->progressListener_ = listener;
   if (connection_ && connection_->callbackP != 0) {
      connection_->callbackP->readFromSocket.numReadUserP = this;
      if (this->progressListener_ && connection_->callbackP != 0) {
         connection_->callbackP->readFromSocket.numReadFuncP = callbackProgressListener;
      }
      else {
         connection_->callbackP->readFromSocket.numReadFuncP = 0; // Dangerous: not thread safe, TODO: Add a mutex
      }
   }
   return old;
}

string SocketDriver::usage()
{
   char usage[XMLBLASTER_MAX_USAGE_LEN];
   ::xmlBlasterAccessUnparsedUsage(usage);
   return  "\nThe SOCKET plugin configuration:" +
           string(usage);
}

// Exception conversion ....
org::xmlBlaster::util::XmlBlasterException SocketDriver::convertFromSocketException(const ::ExceptionStruct& ex) const
{
   return org::xmlBlaster::util::XmlBlasterException(
            (*ex.errorCode=='\0')?string("internal.unknown"):string(ex.errorCode),
            string(""),
            ME,
            "en",
            string(ex.message),
            global_.getVersion() + " " + global_.getBuildTimestamp());
            // TODO: isServerSide!!!
}


::ExceptionStruct SocketDriver::convertToSocketException(org::xmlBlaster::util::XmlBlasterException& ex)
{
   ::ExceptionStruct exSocket;
   ::initializeXmlBlasterException(&exSocket);
   strncpy0(exSocket.errorCode, ex.getErrorCodeStr().c_str(), XMLBLASTEREXCEPTION_ERRORCODE_LEN);
   strncpy0(exSocket.message, ex.getMessage().c_str(), XMLBLASTEREXCEPTION_MESSAGE_LEN);
   //exSocket.remote = ??
   return exSocket;
}

}}}}} // namespaces

