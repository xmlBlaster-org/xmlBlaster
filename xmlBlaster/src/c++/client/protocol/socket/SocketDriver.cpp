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
#include <string>

namespace org {
 namespace xmlBlaster {
  namespace client {
   namespace protocol {
    namespace socket {

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::key;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::client::key;
using namespace org::xmlBlaster::client::qos;

static bool myUpdate(::MsgUnitArr *msgUnitArr, void *userData,
                     ::XmlBlasterException *exception);

void SocketDriver::freeResources(bool deleteConnection)
{
   if (deleteConnection && connection_ != 0) {
      freeXmlBlasterAccessUnparsed(connection_);
      connection_ = 0;
   }
}

#define catch_MACRO(methodName, deleteConnection)                     \
   catch(XmlBlasterException &ex) {                                   \
      freeResources(deleteConnection);                                \
      ex.setLocation(ME + string(methodName));                        \
      throw ex;                                                       \
   }                                                                  \
   catch(const XmlBlasterException *ex) {                             \
      freeResources(deleteConnection);                                \
      throw ex;                                                       \
   }                                                                  \
   catch(const ::XmlBlasterException &ex) {                           \
      freeResources(deleteConnection);                                \
      throw convertFromSocketException(ex);                           \
   }                                                                  \
   catch(const ::XmlBlasterException *ex) {                           \
      freeResources(deleteConnection);                                \
      org::xmlBlaster::util::XmlBlasterException xx = convertFromSocketException(*ex); \
      delete ex;                                                      \
      throw xx;                                                       \
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
     global_(socketDriver.global_), 
     log_(socketDriver.log_),
     statusQosFactory_(socketDriver.global_), 
     msgKeyFactory_(socketDriver.global_), 
     msgQosFactory_(socketDriver.global_),
     callbackClient_(0)
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
     statusQosFactory_(global),
     msgKeyFactory_(global),
     msgQosFactory_(global),
     callbackClient_(0)
{
   if (log_.call()) log_.call("SocketDriver", string("getInstance for ") + instanceName);
   int argc = global_.getArgs();
   const char * const*argv = global.getArgc();
   try {
      connection_ = getXmlBlasterAccessUnparsed(argc, argv);
      connection_->userObject = this; // Transports us to the myUpdate() method
   } catch_MACRO("::Constructor", true)
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

bool myUpdate(::MsgUnitArr *msgUnitArr, void *userData,
                     ::XmlBlasterException *exception)
{
   XmlBlasterAccessUnparsed *xa = (XmlBlasterAccessUnparsed *)userData;
   SocketDriver* socketDriver = static_cast<SocketDriver*>(xa->userObject);
   Global& global = socketDriver->getGlobal();
   Log& log = socketDriver->getLog();
   const string &ME = socketDriver->me();

   try {
      for (size_t i=0; i<msgUnitArr->len; i++) {
         char *xml = messageUnitToXml(&msgUnitArr->msgUnitArr[i]);
         printf("[client] CALLBACK update(): Asynchronous message update arrived:%s\n",xml);
         free(xml);
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
            msgUnitArr->msgUnitArr[i].responseQos = strcpyAlloc("<qos><state id='OK'/></qos>");
         }
      }
      //throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "TEST THROWING XCEPT");
   } 
   catch (XmlBlasterException &e) {
      string tmp = "Exception caught in update(), " +
                   lexical_cast<std::string>(msgUnitArr->len) +
                   " messages are handled as not delivered: " +
                   e.getMessage();
      log.error(ME, tmp);
      strncpy0(exception->errorCode, e.getErrorCodeStr().c_str(), XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      strncpy0(exception->message, tmp.c_str(), XMLBLASTEREXCEPTION_MESSAGE_LEN);
      return false;
   }
   catch(...) {
      string tmp = "Unidentified exception caught in update(), " + lexical_cast<std::string>(msgUnitArr->len) + " messages are handled as not delivered";
      log.error(ME, tmp);
      strncpy0(exception->errorCode, "user.update.error", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      strncpy0(exception->message, tmp.c_str(), XMLBLASTEREXCEPTION_MESSAGE_LEN);
      return false;
   }
   return true;
}

I_Callback* SocketDriver::getCallbackClient()
{
   return callbackClient_;
}

void SocketDriver::initialize(const string& name, I_Callback &client)
{
   ::XmlBlasterException socketException;
   ME = string("SocketDriver-") + instanceName_ + "-" + name;
   callbackClient_ = &client;
   Lock lock(mutex_);
   try {
      if (log_.trace()) log_.trace(ME, "Before createCallbackServer");
      if (connection_->initialize(connection_, myUpdate, &socketException) == false) {
         log_.error(ME, "Connection to xmlBlaster failed,"
                " please start the server or check your configuration\n");
         freeXmlBlasterAccessUnparsed(connection_);
         connection_ = NULL;
      }
      if (log_.trace()) log_.trace(ME, "After createCallbackServer");
   } catch_MACRO("::initialize", true)
}

string SocketDriver::getCbProtocol()
{
    return "SOCKET";
}                             

string SocketDriver::getCbAddress()
{
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
   try {
      connection_->callbackP->shutdown(connection_->callbackP);
      return true;
   } catch_MACRO("::shutdownCb", false)
}

ConnectReturnQos SocketDriver::connect(const ConnectQos& qos) throw (XmlBlasterException)
{
   ::XmlBlasterException socketException;
   Lock lock(mutex_);
   try {
      loginName_ = qos.getUserId();
      if (connection_ == 0) {
         throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "Please check your configuration to find the server");
      }
      char *retQos = connection_->connect(connection_, qos.toXml().c_str(),
                                          myUpdate, &socketException);
      if (*socketException.errorCode != 0) {
         throw socketException; // Is converted to util::XmlBlasterException in catch_MACRO
      }
      ConnectQosFactory factory(global_);
      ConnectReturnQos connectReturnQos = factory.readObject(retQos);
      xmlBlasterFree(retQos);
      secretSessionId_ = connectReturnQos.getSecretSessionId();
      return connectReturnQos;
   } catch_MACRO("::connect", false)
}

bool SocketDriver::disconnect(const DisconnectQos& qos)
{
   if (connection_ == 0) return false;
   ::XmlBlasterException socketException;
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
   return "SOCKET";
}

bool SocketDriver::shutdown()
{
  if (connection_ == 0) return false;
  log_.error(ME, "Shutdown is not implemented");
# ifdef TODO
   Lock lock(mutex_);
   try {
      return connection_->shutdown();
   } catch_MACRO("::shutdown", false)
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
   try {
      return connection_->ping(connection_, qos.c_str());
   } catch_MACRO("::ping", false)
}

SubscribeReturnQos SocketDriver::subscribe(const SubscribeKey& key, const SubscribeQos& qos)
{
   if (connection_ == 0) {
      throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "Sorry, you are not connected to the server");
   }
   ::XmlBlasterException socketException;
   Lock lock(mutex_);
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
   if (connection_ == 0) {
      throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "Sorry, you are not connected to the server");
   }
   ::XmlBlasterException socketException;
   Lock lock(mutex_);
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
   if (connection_ == 0) {
      throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "Sorry, you are not connected to the server");
   }
   ::XmlBlasterException socketException;
   Lock lock(mutex_);
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
   if (connection_ == 0) {
      throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "Sorry, you are not connected to the server");
   }
   ::XmlBlasterException socketException;
   Lock lock(mutex_);
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
   if (connection_ == 0) {
      throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "Sorry, you are not connected to the server");
   }
   ::XmlBlasterException socketException;
   Lock lock(mutex_);
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
   if (connection_ == 0) {
      throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "Sorry, you are not connected to the server");
   }
   ::XmlBlasterException socketException;
   Lock lock(mutex_);
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
      for (size_t ii=0; ii<retC->len; ii++) {
         ret.insert(ret.end(),  PublishReturnQos(global_, statusQosFactory_.readObject(retC->qosArr[ii])) );
      }
      freeQosArr(retC);
      return ret;
   } catch_MACRO("::publishArr", false)
   return vector<PublishReturnQos>();
}

vector<EraseReturnQos> SocketDriver::erase(const EraseKey& key, const EraseQos& qos)
{
   if (connection_ == 0) {
      throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, "Sorry, you are not connected to the server");
   }
   ::XmlBlasterException socketException;
   try {
      Lock lock(mutex_);
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

string SocketDriver::usage()
{
   char usage[XMLBLASTER_MAX_USAGE_LEN];
   ::xmlBlasterAccessUnparsedUsage(usage);
   return  "\nThe SOCKET plugin configuration:" +
           string(usage) + "\n";
}

// Exception conversion ....
org::xmlBlaster::util::XmlBlasterException SocketDriver::convertFromSocketException(const ::XmlBlasterException& ex)
{
   string tmp = "";     // Missing: serverSide?
   return org::xmlBlaster::util::XmlBlasterException(
            ex.errorCode==0?tmp:string(ex.errorCode),
            tmp,
            ME,
            "en",
            (*ex.message=='\0')?tmp:string(ex.message),
            global_.getVersion() + " " + global_.getBuildTimestamp());
            // TODO: isServerSide!!!
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

