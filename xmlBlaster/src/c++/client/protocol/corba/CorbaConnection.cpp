/*----------------------------------------------------------------------------
Name:      CorbaConnection.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster: for now a simplified version
           without caching and without failsafe mode.
Author:    <Michele Laghi> michele.laghi@attglobal.net
-----------------------------------------------------------------------------*/
/*
#ifdef _WINDOWS
#pragma warning(disable:4786)
#endif
*/
#include <client/protocol/corba/CorbaConnection.h>
#include <util/Constants.h>
#include <sys/types.h>
#ifdef _WINDOWS
#  include <winsock2.h>
#else
#  include <sys/socket.h>
#  include <netinet/in.h>
#  include <netdb.h>
#  include <arpa/inet.h>   // inet_addr()
#  include <unistd.h>      // gethostname()
#endif

#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include <client/protocol/corba/CorbaDriver.h>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;

namespace org {
 namespace xmlBlaster {
  namespace client {
   namespace protocol {
    namespace corba {


CorbaConnection::CorbaConnection(Global& global, CORBA::ORB_ptr orb)
  : orb_(0),
    poa_(0),
    /* loginQos_(), */
    connectReturnQos_(global), 
    global_(global), 
    log_(global.getLog("corba")),
    msgKeyFactory_(global),
    msgQosFactory_(global)
{
  log_.getProperties().loadPropertyFile();
  log_.info(me(), "Initializing CORBA ORB");
  if (log_.call()) log_.call(me(), "CorbaConnection constructor ...");
//  if (numOfSessions_ == 0) {
     if (orb) orb_ = orb;
     else {
        int args                 = global_.getArgs();
        const char * const* argc = global_.getArgc();
        orb_ = CORBA::ORB_init(args, const_cast<char **>(argc)); //, "XmlBlaster-C++-Client");
     }

//  numOfSessions_++;
  nameServerControl_ = 0;
  numLogins_         = 0;
  xmlBlaster_        = 0;
  authServer_        = 0; // getAuthenticationService();
  callback_          = 0;
  defaultCallback_   = 0;
  sessionId_         = "";
  xmlBlasterIOR_     = "";
}


CorbaConnection::~CorbaConnection() 
{
   if (log_.call()) log_.call(me(), "destructor");

   delete nameServerControl_;
   
   if (log_.trace()) log_.trace(me(), "destructor: invoking shutdown");
   shutdown();
   
   if (log_.trace()) log_.trace(me(), "destructor: invoking shutdownCb");
   shutdownCb();
   if (log_.trace()) log_.trace(me(), "destructor: deleting the defaultCallback");
   delete defaultCallback_;

   if (log_.trace()) log_.trace(me(), "destructor: releasing the orb");
   if (!CORBA::is_nil(orb_)) CORBA::release(orb_);
   if (log_.trace()) log_.trace(me(), "destructor: releasing the poa");
   if (!CORBA::is_nil(poa_)) CORBA::release(poa_);
   orb_ = 0;
   poa_ = 0;
}

string CorbaConnection::getAddress() const
{
   return xmlBlasterIOR_;
}

string CorbaConnection::getCbAddress() const
{
   return callbackIOR_;
}

CORBA::ORB_ptr CorbaConnection::getOrb() 
{
   if ( CORBA::is_nil(orb_)) {
      string msg = "Sorry, no ORB handle ";
      msg       += "available, please check CORBA plugin.";
      string txt = me();
      txt += ".NotLoggedIn";
      throw XmlBlasterException("communication.noConnection", "client", me(), "en", txt);
   }
   return CORBA::ORB::_duplicate(orb_);
}


CosNaming::NamingContext_ptr CorbaConnection::getNamingService() 
{
  if (log_.call()) log_.call(me(), "getNamingService() ...");
  if (orb_ == 0) log_.panic(me(), "orb==null, internal problem");
  if (nameServerControl_ == 0)
     nameServerControl_ = new NameServerControl(orb_);
  return nameServerControl_->getNamingService();
}

   
authenticateIdl::AuthServer_ptr CorbaConnection::getAuthenticationService() 
{
  if (log_.call()) log_.call(me(), "getAuthenticationService() ...");
  if (!CORBA::is_nil(authServer_))
     return authenticateIdl::AuthServer::_duplicate(authServer_);

  // 1) check if argument -IOR at program startup is given
  string authServerIOR = /* -ior IOR string is directly given */
     log_.getProperties().getStringProperty("ior","");
  if (authServerIOR != "") {
     CORBA::Object_var
        obj = orb_->string_to_object(authServerIOR.c_str());
     authServer_ = authenticateIdl::AuthServer::_narrow(obj.in());
     log_.info(me(),"Accessing xmlBlaster using your given IOR string");
     return authenticateIdl::AuthServer::_duplicate(authServer_);
  }
  if (log_.trace()) log_.trace(me(), "No -ior ...");

  string authServerIORFile =
     log_.getProperties().getStringProperty("ior.file","");
  // -ior.file IOR string is given through a file
  if (authServerIORFile != "") {
     ifstream in(authServerIORFile.c_str());
     if ((!in) /* && (log_.PANIC) */ )
        log_.panic(me(), "Could not open the file");
     in >> authServerIOR;
     in.close();
     CORBA::Object_var
        obj = orb_->string_to_object(authServerIOR.c_str());
     authServer_ = authenticateIdl::AuthServer::_narrow(obj.in());
     string msg  = "Accessing xmlBlaster using your given IOR file ";
     msg += authServerIORFile;
     log_.info(me(), msg);
     return authenticateIdl::AuthServer::_duplicate(authServer_);
  }
  if (log_.trace()) log_.trace(me(), "No -ior.file ...");

  // 3) Using builtin http IOR download ...
  {
     char myHostName[126];
     strcpy(myHostName, "localhost");
     gethostname(myHostName, 125);
     string iorHost = log_.getProperties().getStringProperty("hostname",myHostName);
     // Port may be a name from /etc/services: "xmlBlaster 3412/tcp"
     string iorPortStr = log_.getProperties().getStringProperty("port","3412"); // default port=3412 (xmlblaster)
     if (log_.trace()) log_.trace(me(), "Trying -hostname=" + iorHost + " and -port=" + iorPortStr + " ...");
     struct sockaddr_in xmlBlasterAddr;
     memset((char *)&xmlBlasterAddr, 0, sizeof(xmlBlasterAddr));
     xmlBlasterAddr.sin_family=AF_INET;
     struct hostent *hostP = gethostbyname(iorHost.c_str());
     struct servent *portP = getservbyname(iorPortStr.c_str(), "tcp");
     string authServerIOR;
     authServerIOR.reserve(520);
     if (hostP != NULL) {
        xmlBlasterAddr.sin_addr.s_addr = ((struct in_addr *)(hostP->h_addr))->s_addr; //inet_addr("192.168.1.2");
        if (portP != NULL)
           xmlBlasterAddr.sin_port = portP->s_port;
        else
           xmlBlasterAddr.sin_port = htons(log_.getProperties().getIntProperty("port",3412));
        int s = socket(AF_INET, SOCK_STREAM, 0);
        if (s != -1) {
           int ret=0;
           if ((ret= ::connect(s, (struct sockaddr *)&xmlBlasterAddr, sizeof(xmlBlasterAddr))) != -1) {
              string req="GET /AuthenticationService.ior HTTP/1.0\r\n \n";
              int numSent = send(s, req.c_str(), req.size(), 0);
              if (numSent < (int)req.size()) {
                 log_.error(me(), "Problems sending request '" + req + "'");
              }
              else {
                 log_.trace(me(), "Sent IOR request '" + req + "'");
              }
              int numRead;
              char buf[10];
              while ((numRead = recv(s, buf, 10, 0)) > 0) {
                authServerIOR.append(buf, numRead); 
              }
              if (log_.dump()) log_.dump(me(), "Received IOR data: '" + authServerIOR + "'");
              size_t pos = authServerIOR.find("IOR:");
//              if (pos > 0)
              if (pos != authServerIOR.npos) authServerIOR = authServerIOR.substr(pos);
              else {
                 throw serverIdl::XmlBlasterException("communication.noConnection", 
                                                      "client", me().c_str(), "en",
                                                      "can't access authentication Service", "", "", "", "", 
                                                      "", "");
              }
              if (log_.trace()) log_.trace(me(), "Received IOR data: '" + authServerIOR + "'");
           }
           else {
              log_.warn(me(), "Connecting to -hostname=" + iorHost + " failed"); // errno
           }
           ::shutdown(s, 2);
        }
     }
     if (!authServerIOR.empty()) {
        CORBA::Object_var obj = orb_->string_to_object(authServerIOR.c_str());
        authServer_ = authenticateIdl::AuthServer::_narrow(obj.in());
        string msg  = "Accessing xmlBlaster using -hostname "+iorHost;
        log_.info(me(), msg);
        return authenticateIdl::AuthServer::_duplicate(authServer_);
     }
  }
  if (log_.trace()) log_.trace(me(), "No -hostname and -port ...");


  // 4) asking Name Service CORBA compliant
  bool useNameService=log_.getProperties().getBoolProperty("ns",true);
  // -ns default is to ask the naming service

  string text = "Can't access xmlBlaster Authentication Service";
  text += ", is the server running and ready?\n - try to specify ";
  text += "'-ior.file <fileName>' if server is running on same host\n";
  text += " - try to specify '-hostname <hostName>  -port 3412' to ";
  text += "locate xmlBlaster\n  - or contact your ";
  text += "system administrator to start a naming service";

  string msg = me() + ".NoAuthService";
  if (useNameService) {
     try {
        if (!nameServerControl_) getNamingService();

        string contextId = global_.getProperty().getStringProperty("NameService.context.id", "xmlBlaster");
        string contextKind = global_.getProperty().getStringProperty("NameService.context.kind", "MOM");
        string clusterId = global_.getProperty().getStringProperty("NameService.node.id", global_.getStrippedId());
        string clusterKind = global_.getProperty().getStringProperty("NameService.node.kind", "MOM");

        CORBA::Object_var obj = nameServerControl_->resolve(contextId, contextKind);
        CosNaming::NamingContext_var relativeContext_obj = CosNaming::NamingContext::_narrow(obj.in());
        NameServerControl relativeContext(relativeContext_obj);
        log_.info(me(), "Retrieved NameService context " + contextId + "." + contextKind);

         authenticateIdl::AuthServer_var authServerFirst;
         string tmpId = "";           // for logging only
         string tmpServerName = "";   // for logging only
         string firstServerName = ""; // for logging only
         int countServerFound = 0;    // for logging only
         string serverNameList = "";  // for logging only
         try {
            authServer_ = authenticateIdl::AuthServer::_narrow(relativeContext.resolve(clusterId, clusterKind));
         }
         catch (XmlBlasterException ex) {
         }

         /*============================
          TestGet -ORBInitRef NameService=`cat /tmp/ns.ior` -trace true -call true
         =============================*/

         if ( CORBA::is_nil(authServer_) ) {
            if (log_.trace()) log_.trace(me(), "Query NameServer to find a suitable xmlBlaster server for '" +
                     NameServerControl::getString(contextId, contextKind)+"/"+NameServerControl::getString(clusterId, clusterKind) +
                     "' failed, is nil");
            CosNaming::BindingList_var bl;
            CosNaming::BindingIterator_var bi;
            relativeContext.getNamingService()->list(0, bl, bi);

            // process the remaining bindings if an iterator exists:
            if (CORBA::is_nil(authServer_) &&
               !CORBA::is_nil(static_cast<CosNaming::BindingIterator*&>(bi)) ) {
               int i = 0;
               CORBA::Boolean more;
               do {
                  more = bi->next_n(1, bl);
                  if (bl->length() != 1) {
                     if (log_.trace()) log_.trace(me(), "NameService entry id is nil");
                     break;
                  }
                  CORBA::ULong index = 0;
                  string id = lexical_cast<string>(bl[index].binding_name[0].id);
                  string kind = lexical_cast<string>(bl[index].binding_name[0].kind);
                  if (log_.trace()) log_.trace(me(), "id=" + id + " kind=" + kind);

                  tmpId = id;
                  countServerFound++;
                  tmpServerName = NameServerControl::getString(contextId, contextKind)+"/"+NameServerControl::getString(id, kind);
                  if (i>0) serverNameList += ", ";
                  i++;
                  serverNameList += tmpServerName;

                  if (clusterId == id && clusterKind == kind) {
                     try {
                        if (log_.trace()) log_.trace(me(), "Trying to resolve NameService entry '"+NameServerControl::getString(id, kind)+"'");
                        authServer_ = authenticateIdl::AuthServer::_narrow(relativeContext.resolve(id, kind));
                        if (! CORBA::is_nil(authServer_))
                           break; // found a matching server
                        else
                           log_.warn(me(), "Connecting to NameService entry '"+tmpServerName+"' failed, is_nil");
                     }
                     catch (const CORBA::Exception &exc) {
                        log_.warn(me(), "Connecting to NameService entry '"+tmpServerName+"' failed: " + to_string(exc));
                     }
                  }

                  if (CORBA::is_nil( static_cast<authenticateIdl::AuthServer*&>(authServerFirst))) {
                     if (log_.trace()) log_.trace(me(), "Remember the first server");
                     try {
                        firstServerName = tmpServerName;
                        if (log_.trace()) log_.trace(me(), "Remember the first reachable xmlBlaster server from NameService entry '"+firstServerName+"'");
                        authServerFirst = authenticateIdl::AuthServer::_narrow(relativeContext.resolve(id, kind));
                     }
                     catch (const CORBA::Exception &exc) {
                        log_.warn(me(), "Connecting to NameService entry '"+tmpServerName+"' failed: " + to_string(exc));
                     }
                  }
               } while ( more );
            }
            bi->destroy();  // Clean up server side iteration resources
         }

         if (CORBA::is_nil(authServer_)) {
            if (! CORBA::is_nil(static_cast<authenticateIdl::AuthServer*&>(authServerFirst))) {
               if (countServerFound > 1) {
                  string str = string("Can't choose one of ") + lexical_cast<string>(countServerFound) +
                                 " avalailable server in CORBA NameService: " + serverNameList +
                                 ". Please choose one with e.g. -NameService.node.id " + tmpId;
                  log_.warn(me(), str);
                  throw XmlBlasterException("communication.noConnection", "client", me(), "en", str);
               }
               log_.info(me(), "Choosing only available server '" + firstServerName + "' in CORBA NameService");
               this->authServer_ = authenticateIdl::AuthServer::_duplicate( static_cast<authenticateIdl::AuthServer*&>(authServerFirst));
               return authenticateIdl::AuthServer::_duplicate( static_cast<authenticateIdl::AuthServer*&>(authServerFirst));
            }
            else {
               log_.trace(me(), "No usable xmlBlaster server found in NameService: " + serverNameList);
               throw XmlBlasterException("communication.noConnection", "client", me(), "en", text);
            }
         }

         log_.info(me(), "Accessing xmlBlaster using CORBA naming service entry '" +
                           NameServerControl::getString(contextId, contextKind) +
                           "/" + NameServerControl::getString(clusterId, clusterKind));

         return authenticateIdl::AuthServer::_duplicate(authServer_);
      }
      catch(serverIdl::XmlBlasterException &e ) {
         log_.trace(me() + ".NoAuthService", text);
         throw CorbaDriver::convertFromCorbaException(e);
      }
   } // if (useNameService)

   if (log_.trace()) log_.trace(me(), "No -ns ...");
   throw XmlBlasterException("communication.noConnection", "client", me(), "en", text);
} // getAuthenticationService() 

clientIdl::BlasterCallback_ptr CorbaConnection::createCallbackServer(POA_clientIdl::BlasterCallback *implObj) 
{
  if (implObj) {
     if (log_.trace()) log_.trace(me(), "Trying resolve_initial_references ...");
     CORBA::Object_var obj = orb_->resolve_initial_references("RootPOA");
     if (log_.trace()) log_.trace(me(), "Trying narrowing POA ...");
     poa_ = PortableServer::POA::_narrow(obj.in());
     PortableServer::POAManager_var poa_mgr = poa_->the_POAManager();
     callback_ = implObj->_this();
     callbackIOR_ = orb_->object_to_string(callback_);
     poa_mgr->activate();
     while (orb_->work_pending()) orb_->perform_work();
     return clientIdl::BlasterCallback::_duplicate(callback_);
     // add exception handling here !!!!!
  }
  return (clientIdl::BlasterCallback_ptr)0;
}

ConnectReturnQos CorbaConnection::connect(const ConnectQos& connectQos)
{
   if ( !CORBA::is_nil(xmlBlaster_)) {
      string msg = "You are already logged in, returning cached handle";
      msg += " on xmlBlaster";
      log_.warn(me(), msg);
      return connectReturnQos_;
   }

   loginName_ = connectQos.getUserId();
   if (log_.call()) log_.call(me(),"connect(" + loginName_ + ") ...");
   try {
      if (CORBA::is_nil(authServer_)) getAuthenticationService();
      ConnectQos help = connectQos; // since it is a const
      string reqQos = help.toXml();
      if (log_.trace()) log_.trace(me(), string("connect req: ") + reqQos);
      string retQos = authServer_->connect(reqQos.c_str());
      if (log_.trace()) log_.trace(me(), string("connect ret: ") + retQos);
      ConnectQosFactory factory(global_);
      if (log_.dump()) log_.dump(me(), "connect: the connect return qos before parsing: " + retQos);
      connectReturnQos_ = factory.readObject(retQos);
      sessionId_ = connectReturnQos_.getSecretSessionId();
      xmlBlasterIOR_ = connectReturnQos_.getServerRef().getAddress();

      CORBA::Object_var obj = orb_->string_to_object(xmlBlasterIOR_.c_str());
      xmlBlaster_ = serverIdl::Server::_narrow(obj.in());

      numLogins_++;
      if (log_.trace()) log_.trace(me(),"Success, connect for "+loginName_);
      return connectReturnQos_;
   }
   catch(XmlBlasterException &e) {
      string msg = "Connect failed for ";
      msg +=  loginName_; //  + ", numLogins=" + numLogins_;
      if (log_.trace()) log_.trace(me(), msg);
      throw e;
   }
}

bool CorbaConnection::shutdown()
{
   bool ret = false;
   if (!CORBA::is_nil(xmlBlaster_)) {
      CORBA::release(xmlBlaster_);
      xmlBlaster_ = NULL;
      ret = true;
   }
   if (!CORBA::is_nil(authServer_)) {
      CORBA::release(authServer_);
      authServer_ = NULL;
      ret = true;
   }
   return ret;
}

bool CorbaConnection::shutdownCb()
{
   if (!CORBA::is_nil(callback_)) {
      CORBA::release(callback_);
      callback_ = NULL;
      return true;
   }
   return false;
}

bool CorbaConnection::disconnect(const string& qos)
{
   if (log_.call()) log_.call(me(), "disconnect() ...");
   if (log_.dump()) log_.dump(me(), string("disconnect: the qos: ") + qos);

   try {
      if (!CORBA::is_nil(authServer_)) {
         if (sessionId_=="") authServer_->logout(xmlBlaster_);
         else authServer_->disconnect(sessionId_.c_str(), qos.c_str());
      }
      shutdown();
      return true;
   }
   catch (...) {
   }
   shutdown();
   return false;
}

/**
* Subscribe a message. 
* <br />
* Note: You don't need to free anything
* @return The xml based QoS
*/
string 
CorbaConnection::subscribe(const string &xmlKey, const string &qos) 
{
   if (log_.call()) log_.call(me(), "subscribe() ...");
   if (log_.dump()) {
      log_.dump(me(), string("subscribe: the key: ") + xmlKey);
      log_.dump(me(), string("subscribe: the qos: ") + qos);
   }
   if (CORBA::is_nil(xmlBlaster_)) {
      string txt = "no auth.Server, you must login first";
      throw serverIdl::XmlBlasterException("communication.noConnection", 
                                          "client", me().c_str(), "en",
                                          txt.c_str(), "", "", "", "", "", "");
  }
  try {
     CORBA::String_var ret = xmlBlaster_->subscribe(xmlKey.c_str(), qos.c_str());
     return static_cast<const char *>(ret);
  } catch(serverIdl::XmlBlasterException &e) {
     throw e;
  }
  //return "";
}


vector<string> CorbaConnection::unSubscribe(const string &xmlKey,
                                  const string &qos) 
{
  if (log_.call()) log_.call(me(), "unSubscribe() ...");
  if (log_.dump()) {
     log_.dump(me(), string("unSubscribe: the key: ") + xmlKey);
     log_.dump(me(), string("unSubscribe: the qos: ") + qos);
  }

  if (CORBA::is_nil(xmlBlaster_)) {
     string txt = "no auth.Server, you must login first";
     throw serverIdl::XmlBlasterException("communication.noConnection", 
                                          "client", me().c_str(), "en",
                                          txt.c_str(), "", "", "", "", "", "");
  }

  try {
     serverIdl::XmlTypeArr_var 
        retArr = xmlBlaster_->unSubscribe(xmlKey.c_str(), qos.c_str());
     
     vector<string> vecArr;
     for (CORBA::ULong ii=0; ii<retArr->length(); ii++) {
        vecArr.push_back(static_cast<char *>(retArr[ii].inout()));
     }
     return vecArr;
  }
  catch(serverIdl::XmlBlasterException e) {
     throw e;
  }
}

/**
* publish a message. 
* <br />
* This method has a common interface which is not CORBA depending. 
* <br />
* Note: You don't need to free anything
* @return The xml based QoS
*/
string CorbaConnection::publish(const util::MessageUnit &msgUnitUtil) {
  if (log_.trace()) log_.trace(me(), "Publishing the STL way ...");
  if (log_.dump()) {
     log_.dump(me(), string("publish: the msgUnit: ") + msgUnitUtil.toXml());
  }

  if (CORBA::is_nil(xmlBlaster_)) {
     string txt = "no auth.Server, you must login first";
     throw serverIdl::XmlBlasterException("communication.noConnection", 
                                          "client", me().c_str(), "en",
                                          txt.c_str(), "", "", "", "", "", "");
  }

  try {
     serverIdl::MessageUnit msgUnit;
     // serverIdl::MessageUnit_var msgUnit;
     copyToCorba(msgUnit, msgUnitUtil);
     CORBA::String_var ret = xmlBlaster_->publish(msgUnit);
     return static_cast<char *>(ret);
  }
  catch(serverIdl::XmlBlasterException &e) {
     string msg = "XmlBlasterException: ";
     msg += e.message;
     if (log_.trace()) log_.trace(me(), msg);
     throw e;
  }
//        catch(CORBA::Exception &ex1) {
//       throw serverIdl::XmlBlasterException(me().c_str(),to_string(ex1));
//        }
}

   /**
    * @deprecated Please use the util::MessageUnit variant
    */
string 
CorbaConnection::publish(const serverIdl::MessageUnit &msgUnit) 
{
  if (log_.trace()) log_.trace(me(), "Publishing ...");

  if (CORBA::is_nil(xmlBlaster_)) {
     string txt = "no auth.Server, you must login first";
     throw serverIdl::XmlBlasterException("communication.noConnection", 
                                          "client", me().c_str(), "en",
                                          txt.c_str(), "", "", "", "", "", "");
 }

  try {
     CORBA::String_var ret = xmlBlaster_->publish(msgUnit);
     return static_cast<char *>(ret);
  }
  catch(serverIdl::XmlBlasterException &e) {
     string msg = "XmlBlasterException: ";
     msg += e.message;
     if (log_.trace()) log_.trace(me(), msg);
     throw e;
  }
//        catch(CORBA::Exception &ex1) {
//       throw serverIdl::XmlBlasterException(me().c_str(),to_string(ex1));
//        }
}

/**
* Publish a bulk of messages. 
* <br />
* This method has a common interface which is not CORBA depending. 
* <br />
* Note: You don't need to free anything
* @param A vector with MessageUnit
* @return A vector of strings each is a publish return QoS. 
*/
vector<string> 
CorbaConnection::publishArr(const vector<util::MessageUnit> &msgVec) 
{
  if (log_.call()) log_.call(me(), "publishArr() ...");

  if (CORBA::is_nil(xmlBlaster_)) {
     string txt = "no auth.Server, you must login first";
     throw serverIdl::XmlBlasterException("communication.noConnection", 
                                          "client", me().c_str(), "en",
                                          txt.c_str(), "", "", "", "", "", "");
  }

  try {
     serverIdl::MessageUnitArr_var msgUnitArr = new serverIdl::MessageUnitArr;
     copyToCorba(msgUnitArr, msgVec);
     serverIdl::XmlTypeArr_var retArr = xmlBlaster_->publishArr(msgUnitArr);
     vector<string> vecArr;
     for (CORBA::ULong ii=0; ii<retArr->length(); ii++) {
        vecArr.push_back(static_cast<char *>(retArr[ii].inout()));
     }
     return vecArr;
  }
  catch(serverIdl::XmlBlasterException &e) {
     if (log_.trace()) log_.trace(me(), "XmlBlasterException: "
                                + string(e.message) );
     throw e;
  }
}

/**
* @deprecated Please use the STL vector variant
*/
serverIdl::XmlTypeArr* 
CorbaConnection::publishArr(const serverIdl::MessageUnitArr& msgUnitArr)
{
  if (log_.call()) log_.call(me(), "publishArr() ...");

  if (CORBA::is_nil(xmlBlaster_)) {
     string txt = "no auth.Server, you must login first";
     throw serverIdl::XmlBlasterException("communication.noConnection", 
                                          "client", me().c_str(), "en",
                                          txt.c_str(), "", "", "", "", "", "");
  }

  try {
     return xmlBlaster_->publishArr(msgUnitArr);
  }
  catch(serverIdl::XmlBlasterException &e) {
     if (log_.trace()) log_.trace(me(), "XmlBlasterException: "
                                + string(e.message) );
     throw e;
  }
  return 0;
}

/**
* Publish a bulk of messages without ACK. 
* <br />
* This method has a common interface which is not CORBA depending. 
* <br />
* Note: You don't need to free anything
* @param The MessageUnit array as a STL vector
*/
void 
CorbaConnection::publishOneway(const vector<util::MessageUnit>& msgVec)
{
  if (log_.call()) log_.call(me(), "publishOneway() ...");

  if (CORBA::is_nil(xmlBlaster_)) {
     string txt = "no auth.Server, you must login first";
     throw serverIdl::XmlBlasterException("communication.noConnection", 
                                          "client", me().c_str(), "en",
                                          txt.c_str(), "", "", "", "", "", "");
  }

  try {
     serverIdl::MessageUnitArr_var msgUnitArr = new serverIdl::MessageUnitArr;
     copyToCorba(msgUnitArr, msgVec);
     xmlBlaster_->publishOneway(msgUnitArr);
  }
  catch (const exception& e) {
     log_.error(me(), string("Exception caught in publishOneway, it is not transferred to client: ") + e.what());
  }
  catch(...) {
     log_.error(me(), "Exception caught in publishOneway, it is not transferred to client");
  }
}

/*
* Please use the STL based variant
* @param The MessageUnit array as a CORBA datatype
* @deprecated Use the vector<util::MessageUnit> variant
*/
void 
CorbaConnection::publishOneway(const serverIdl::MessageUnitArr& msgUnitArr)
{
  if (log_.call()) log_.call(me(), "publishOneway() ...");

  if (CORBA::is_nil(xmlBlaster_)) {
     string txt = "no auth.Server, you must login first";
     throw serverIdl::XmlBlasterException("communication.noConnection", 
                                          "client", me().c_str(), "en",
                                          txt.c_str(), "", "", "", "", "", "");
  }

  try {
     xmlBlaster_->publishOneway(msgUnitArr);
  }
  catch (const exception& e) {
     log_.error(me(), string("Exception caught in publishOneway, it is not transferred to client: ") + e.what());
  }
  catch(...) {
     log_.error(me(), "Exception caught in publishOneway, it is not transferred to client");
  }
}

/**
* This method has a common interface which is not CORBA depending. 
* <br />
* Note: You don't need to free anything
*/
vector<string> 
CorbaConnection::erase(const string &xmlKey, const string &qos) 
{
  if (log_.call()) log_.call(me(), "erase() ...");
  if (log_.dump()) {
     log_.dump(me(), string("erase: the key: ") + xmlKey);
     log_.dump(me(), string("erase: the qos: ") + qos);
  }

  if (CORBA::is_nil(xmlBlaster_)) {
     string txt = "no auth.Server, you must login first";
     throw serverIdl::XmlBlasterException("communication.noConnection", 
                                          "client", me().c_str(), "en",
                                          txt.c_str(), "", "", "", "", "", "");
  }

  try {
     serverIdl::XmlTypeArr_var retArr = xmlBlaster_->erase(xmlKey.c_str(), qos.c_str());
     vector<string> vecArr;
     for (CORBA::ULong ii=0; ii<retArr->length(); ii++) {
        vecArr.push_back(static_cast<const char *>(retArr[ii]));
     }
     return vecArr;
  }
  catch(serverIdl::XmlBlasterException e) {
     throw e;
  }
}


/**
* Access messages the synchronous way. 
* <br />
* Note: You don't need to free anything
* @return The STL MessageUnit vector, its a copy so if you have the variable on the
*         stack it will free itself
*/
vector<util::MessageUnit>
CorbaConnection::get(const string &xmlKey, const string &qos) 
{

  serverIdl::MessageUnitArr_var units;
  if (log_.call()) log_.call(me(), "get() ...");
  if (log_.dump()) {
     log_.dump(me(), string("get: the key: ") + xmlKey);
     log_.dump(me(), string("get: the qos: ") + qos);
  }

  if (CORBA::is_nil(xmlBlaster_)) {
     string txt = "no auth.Server, you must login first";
     throw serverIdl::XmlBlasterException("communication.noConnection", 
                                          "client", me().c_str(), "en",
                                          txt.c_str(), "", "", "", "", "", "");
  }

  try {
     units = xmlBlaster_->get(xmlKey.c_str(), qos.c_str());
     /*
     string subId = xmlBlaster_->subscribe(xmlKey.c_str(),
                                           qos.c_str());
     log_.info(me(),"New Entry in Cache created (subId="+subId+")");
     */
     vector<util::MessageUnit> msgVec;
     copyFromCorba(msgVec, units);
     return msgVec;
  }
  catch(serverIdl::XmlBlasterException &e) {
     throw e;
  }
}

/*
* Please use the other STL based get() variant
* @return The MessageUnit array as a CORBA datatype
* @deprecated Use the vector<util::MessageUnit> variant of get()
serverIdl::MessageUnitArr* CorbaConnection::get(const string &xmlKey, const string &qos) {

  serverIdl::MessageUnitArr* units;
  if (log_.call()) log_.call(me(), "get() ...");

  if (CORBA::is_nil(xmlBlaster_)) {
     string txt = "no auth.Server, you must login first";
     throw serverIdl::XmlBlasterException(me().c_str(), txt.c_str());
  }

  try {
     units = xmlBlaster_->get(xmlKey.c_str(), qos.c_str());
     return units; // check if this is OK or if it must be duplicated.
  }
  catch(serverIdl::XmlBlasterException &e) {
     throw e;
  }

  return (serverIdl::MessageUnitArr*)0;
}
*/

string 
CorbaConnection::ping(const string &qos) 
{
  if (log_.call()) log_.call(me(), "ping(" + qos + ") ...");

  if (CORBA::is_nil(xmlBlaster_)) {
     string txt = "no auth.Server, you must login first";
     throw serverIdl::XmlBlasterException("communication.noConnection", 
                                          "client", me().c_str(), "en",
                                          txt.c_str(), "", "", "", "", "", "");
  }

  try {
     CORBA::String_var ret = xmlBlaster_->ping("");
     return static_cast<char *>(ret);
  }
  catch(serverIdl::XmlBlasterException &e) {
     throw e;
  }
}

/**
* Transform a util::MessageUnit to the corba variant
*/
void 
CorbaConnection::copyToCorba(serverIdl::MessageUnit &dest,
                             const util::MessageUnit &src) const 
{
  dest.xmlKey = src.getKey().toXml().c_str();
  serverIdl::ContentType content(src.getContentLen(),
                                 src.getContentLen(),
                                 (CORBA::Octet*)src.getContent(),
                                 false); // our src does memory management itself
  dest.content = content;  // dest.content and content point to same memory? memory leak?
  dest.qos = src.getQos().toXml().c_str();
}

   
/**
* Transform STL vector to corba messageUnit array variant. 
*/
void 
CorbaConnection::copyToCorba(serverIdl::MessageUnitArr_var &units,
                             const vector<util::MessageUnit> &msgVec) const 
{
  unsigned int len = msgVec.size();
  units->length(len);
  for (CORBA::ULong ii=0; ii<len; ii++) {
     util::MessageUnit src = msgVec[ii];
     serverIdl::MessageUnit dest;
     copyToCorba(dest, src);
     units[ii] = dest;
  }
}

/**
* Transform corba messageUnit array to vector variant. 
* @param units Is not const as [] operator does not like it
*/
void 
CorbaConnection::copyFromCorba(vector<util::MessageUnit> &msgVec,
                               serverIdl::MessageUnitArr_var &units)
{
  unsigned int len = units->length();
  msgVec.reserve(len);
  for (CORBA::ULong ii=0; ii<len; ii++) {
     const serverIdl::MessageUnit &msgUnit = static_cast<const serverIdl::MessageUnit>(units[ii]);
     unsigned long len = static_cast<unsigned long>(msgUnit.content.length());
     const unsigned char * blob = static_cast<const unsigned char *>(&msgUnit.content[0]);
     if (log_.trace()) log_.trace(me(), "copyFromCorba() '" + string((const char *)blob) + "' len=" + lexical_cast<string>(len));
     MsgKeyData key = msgKeyFactory_.readObject(string(msgUnit.xmlKey));
     MsgQosData qos = msgQosFactory_.readObject(string(msgUnit.qos)); 
     const util::MessageUnit msg(key, len, blob, qos);
     msgVec.push_back(msg);
  }
}

/**
* Transform corba messageUnit array to vector variant. 
void CorbaConnection::copyFromCorba(util::MessageUnit &msgUnitUtil, serverIdl::MessageUnitArr_var &msgUnit) {
  string key(units[ii].xmlKey);
  unsigned long len = static_cast<unsigned long>(units[ii].content.length());
  const unsigned char * blob = static_cast<const unsigned char *>(&units[ii].content[0]);
  //unsigned char *blob = (unsigned char *)&units[ii].content[0];
  string qos(units[ii].qos);
  msgUnitUtil.setKey(key);
  msgUnitUtil.setContentLen(len);
  msgUnitUtil.setContent(blob);
  msgUnitUtil.setQos(qos);
}
*/

void CorbaConnection::usage() 
{
  Global& glob = Global::getInstance();
  glob.initialize();
  Log& log = glob.getLog("client");
  string me="";
  log.plain(me, "");
  log.plain(me, "Client connection options:");
  log.plain(me, "  -ior <IOR:00...>    The IOR string of the xmlBlaster-authentication server.");
  log.plain(me, "  -hostname <host>    The host where to find xmlBlaster [localhost]");
  log.plain(me, "  -port <port>        The port where xmlBlaster publishes its IOR [3412]");
  log.plain(me, "  -ior.file <file>    A file with the xmlBlaster-authentication server IOR.");
  log.plain(me, "  -ns <true/false>    Try to access xmlBlaster through a naming service [true]");
  log.plain(me, "");
}

// CORBA::ORB_ptr CorbaConnection::orb_           = 0;
// unsigned short CorbaConnection::numOfSessions_ = 0;
// PortableServer::POA_ptr CorbaConnection::poa_  = 0;

}}}}} // end of namespace


