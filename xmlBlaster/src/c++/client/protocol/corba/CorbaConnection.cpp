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

#include <util/Global.h>

using namespace org::xmlBlaster::util::qos;

namespace org {
 namespace xmlBlaster {
  namespace client {
   namespace protocol {
    namespace corba {


CorbaConnection::CorbaConnection(Global& global, bool orbOwner)
  : /* loginQos_(), */
    connectReturnQos_(global), 
    global_(global), 
    log_(global.getLog("corba")),
    msgKeyFactory_(global),
    msgQosFactory_(global)
{
  log_.getProperties().loadPropertyFile();
  log_.info(me(), "Trying to establish a CORBA connection to xmlBlaster");
  if (log_.CALL) log_.call(me(), "CorbaConnection constructor ...");
  if (numOfSessions_ == 0) {
     int args                 = global_.getArgs();
     const char * const* argc = global_.getArgc();
     orb_ = CORBA::ORB_init(args, const_cast<char **>(argc));
  }
  numOfSessions_++;
  nameServerControl_ = 0;
  numLogins_         = 0;
  xmlBlaster_        = 0;
  authServer_        = 0; // getAuthenticationService();
  callback_          = 0;
  defaultCallback_   = 0;
  sessionId_         = "";
  orbOwner_          = orbOwner;
  xmlBlasterIOR_     = "";
}


CorbaConnection::~CorbaConnection() 
{
  if (log_.CALL) log_.call(me(), "CorbaConnection destructor ...");
  numOfSessions_--;
//  if (isLoggedIn()) logout();
  disconnect();

  if (nameServerControl_) delete nameServerControl_;
  if (orbOwner_) orb_->destroy();
  CORBA::release(authServer_);
  CORBA::release(xmlBlaster_);
  CORBA::release(callback_  );
  if (numOfSessions_ == 0) {
     CORBA::release(orb_);
     CORBA::release(poa_);
     orb_ = 0;
     poa_ = 0;
  }
  if (defaultCallback_) delete defaultCallback_;
}

string
CorbaConnection::getAddress() const
{
   return xmlBlasterIOR_;
}

string
CorbaConnection::getCbAddress() const
{
   return callbackIOR_;
}

CORBA::ORB_ptr
CorbaConnection::getOrb() 
{
   return CORBA::ORB::_duplicate(orb_);
}



serverIdl::Server_ptr 
CorbaConnection::getXmlBlaster() 
{
  if ( !CORBA::is_nil(xmlBlaster_ /*.in()*/ )) {
     string msg = "Sorry, no xmlBlaster handle ";
     msg       += "available, please login first.";
     string txt = me();
     txt += ".NotLoggedIn";
     throw serverIdl::XmlBlasterException("communication.noConnection", 
                                          "client", me().c_str(), "en",
                                          txt.c_str(), "", "", "", "", "", "");
  }
  return serverIdl::Server::_duplicate(xmlBlaster_);
}


CosNaming::NamingContext_ptr 
CorbaConnection::getNamingService() 
{
  if (log_.CALL) log_.call(me(), "getNamingService() ...");
  if (orb_ == 0) log_.panic(me(), "orb==null, internal problem");
  if (nameServerControl_ == 0)
     nameServerControl_ = new NameServerControl(orb_);
  return nameServerControl_->getNamingService();
}

   
authenticateIdl::AuthServer_ptr 
CorbaConnection::getAuthenticationService() 
{
  if (log_.CALL) log_.call(me(), "getAuthenticationService() ...");
  if (!CORBA::is_nil(authServer_ /*.in()*/ ))
     return authenticateIdl::AuthServer::_duplicate(authServer_);

  // 1) check if argument -IOR at program startup is given
  string authServerIOR = /* -ior IOR string is directly given */
     log_.getProperties().getStringProperty("ior","");
  if (authServerIOR != "") {
     CORBA::Object_var
        obj = orb_->string_to_object(authServerIOR.c_str());
     authServer_ = authenticateIdl::AuthServer::_narrow(obj);
     log_.info(me(),"Accessing xmlBlaster using your given IOR string");
     return authenticateIdl::AuthServer::_duplicate(authServer_);
  }
  if (log_.TRACE) log_.trace(me(), "No -ior ...");

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
     authServer_ = authenticateIdl::AuthServer::_narrow(obj);
     string msg  = "Accessing xmlBlaster using your given IOR file ";
     msg += authServerIORFile;
     log_.info(me(), msg);
     return authenticateIdl::AuthServer::_duplicate(authServer_);
  }
  if (log_.TRACE) log_.trace(me(), "No -ior.file ...");

  // 3) Using builtin http IOR download ...
  {
     char myHostName[126];
     strcpy(myHostName, "localhost");
     gethostname(myHostName, 125);
     string iorHost = log_.getProperties().getStringProperty("hostname",myHostName);
     // Port may be a name from /etc/services: "xmlBlaster 3412/tcp"
     string iorPortStr = log_.getProperties().getStringProperty("port","3412"); // default port=3412 (xmlblaster)
     if (log_.TRACE) log_.trace(me(), "Trying -hostname=" + iorHost + " and -port=" + iorPortStr + " ...");
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
              if (log_.DUMP) log_.dump(me(), "Received IOR data: '" + authServerIOR + "'");
              size_t pos = authServerIOR.find("IOR:");
              if (pos > 0)
                 authServerIOR = authServerIOR.substr(pos);
              if (log_.TRACE) log_.trace(me(), "Received IOR data: '" + authServerIOR + "'");
           }
           else {
              log_.warn(me(), "Connecting to -hostname=" + iorHost + " failed"); // errno
           }
           ::shutdown(s, 2);
        }
     }
     if (!authServerIOR.empty()) {
        CORBA::Object_var obj = orb_->string_to_object(authServerIOR.c_str());
        authServer_ = authenticateIdl::AuthServer::_narrow(obj);
        string msg  = "Accessing xmlBlaster using -hostname "+iorHost;
        log_.info(me(), msg);
        return authenticateIdl::AuthServer::_duplicate(authServer_);
     }
  }
  if (log_.TRACE) log_.trace(me(), "No -hostname and -port ...");


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
        CORBA::Object_var obj =
           nameServerControl_->resolve("xmlBlaster-Authenticate.MOM");
        authServer_ = authenticateIdl::AuthServer::_narrow(obj);
        log_.info(me(),"Accessing xmlBlaster using a naming service.");
        return authenticateIdl::AuthServer::_duplicate(authServer_);
     }
     catch(serverIdl::XmlBlasterException & /*e*/ ) {
        log_.error(me() + ".NoAuthService", text);
        throw serverIdl::XmlBlasterException("communication.noConnection", 
                                             "client", me().c_str(), "en",
                                             text.c_str(), "", "", "", "", 
                                             "", "");
     }
  }
  if (log_.TRACE) log_.trace(me(), "No -ns ...");
  throw serverIdl::XmlBlasterException("communication.noConnection", "client",
                                       me().c_str(), "en", text.c_str(), "", 
                                       "", "", "", "", "");
}

/*
serverIdl::Server_ptr
CorbaConnection::login(const string &loginName, const string &passwd,
                       const LoginQosWrapper &qos, I_Callback *client) 
{

  if (log_.CALL) log_.call(me(), "login(" + loginName + ") ...");
  if ( !CORBA::is_nil(xmlBlaster_)) {
     string msg = "You are already logged in, returning cached handle";
     msg += " on xmlBlaster";
     log_.warn(me(), msg);
     return serverIdl::Server::_duplicate(xmlBlaster_);
  }
  loginName_ = loginName;
  passwd_    = passwd;
  loginQos_  = qos;

  if (client) {
     if (defaultCallback_) delete defaultCallback_;
     defaultCallback_ =  new DefaultCallback(global_, loginName_, client, 0);
     callback_ = createCallbackServer(defaultCallback_);
     callbackIOR_ = orb_->object_to_string(callback_);
     util::qos::address::CallbackAddress addr(global_, "IOR");
     addr.setAddress(callbackIOR_);
     loginQos_.addCallbackAddress(addr);
     if (log_.TRACE) log_.trace(me(), string("Success, exported ") +
                                "BlasterCallback Server interface for "+
                                loginName);
  }
  loginRaw();
  return serverIdl::Server::_duplicate(xmlBlaster_);
}
*/

clientIdl::BlasterCallback_ptr
CorbaConnection::createCallbackServer(POA_clientIdl::BlasterCallback *implObj) 
{
  if (implObj) {
     CORBA::Object_var obj = orb_->resolve_initial_references("RootPOA");
     poa_ = PortableServer::POA::_narrow(obj);
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

/*
void 
CorbaConnection::loginRaw() 
{
  if (log_.CALL) log_.call(me(),"loginRaw(" + loginName_ + ") ...");
  try {
     if (CORBA::is_nil(authServer_)) getAuthenticationService();
     xmlBlaster_ = authServer_->login(loginName_.c_str(),
                                      passwd_.c_str(),
                                      loginQos_.toXml().c_str());
     numLogins_++;
     if (log_.TRACE) log_.trace(me(),"Success, login for "+loginName_);
     if (log_.DUMP ) log_.dump(me() ,loginQos_.toXml());
  }
  catch(serverIdl::XmlBlasterException &e) {
     string msg = "Login failed for ";
     msg +=  loginName_; //  + ", numLogins=" + numLogins_;
     if (log_.TRACE) log_.trace(me(), msg);
     throw e;
  }
}
*/

ConnectReturnQos CorbaConnection::connect(const ConnectQos& connectQos)
{
   if ( !CORBA::is_nil(xmlBlaster_)) {
      string msg = "You are already logged in, returning cached handle";
      msg += " on xmlBlaster";
      log_.warn(me(), msg);
      return connectReturnQos_;
   }

   loginName_ = connectQos.getUserId();
   if (log_.CALL) log_.call(me(),"connect(" + loginName_ + ") ...");
   try {
      if (CORBA::is_nil(authServer_)) getAuthenticationService();
      ConnectQos help = connectQos; // since it is a const
      string reqQos = help.toXml();
      if (log_.TRACE) log_.trace(me(), string("connect req: ") + reqQos);
      string retQos = authServer_->connect(reqQos.c_str());
      if (log_.TRACE) log_.trace(me(), string("connect ret: ") + retQos);
      ConnectQosFactory factory(global_);
      if (log_.DUMP) log_.dump(me(), "connect: the connect qos before parsing: " + retQos);
      connectReturnQos_ = factory.readObject(retQos);
      sessionId_ = connectReturnQos_.getSessionId();
      xmlBlasterIOR_ = connectReturnQos_.getServerRef().getAddress();

      CORBA::Object_var obj = orb_->string_to_object(xmlBlasterIOR_.c_str());
      xmlBlaster_ = serverIdl::Server::_narrow(obj);

      numLogins_++;
      if (log_.TRACE) log_.trace(me(),"Success, connect for "+loginName_);
      return connectReturnQos_;
   }
   catch(serverIdl::XmlBlasterException &e) {
      string msg = "Connect failed for ";
      msg +=  loginName_; //  + ", numLogins=" + numLogins_;
      if (log_.TRACE) log_.trace(me(), msg);
      throw e;
   }
}


bool
CorbaConnection::logout() 
{
  if (log_.CALL) log_.call(me(), "logout() ...");

  if ( CORBA::is_nil(xmlBlaster_)) {
     log_.warn(me(), "No logout, you are not logged in");
     return false;
  }
  else log_.info(me(), "Logout from xmlBlaster ...");

  try {
     if (!CORBA::is_nil(xmlBlaster_)) authServer_->logout(xmlBlaster_);
     xmlBlaster_ = 0;
     log_.info(me(), "Disconnected from xmlBlaster.");
     return true;
  }
  catch(serverIdl::XmlBlasterException &e) {
     string msg = "XmlBlasterException: ";
     msg += e.message;
     log_.warn(me(), msg);
  }
  xmlBlaster_ = 0;
  return false;
}

bool
CorbaConnection::shutdown()
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

bool
CorbaConnection::shutdownCb()
{
   if (!CORBA::is_nil(callback_)) {
      CORBA::release(callback_);
      callback_ = NULL;
      return true;
   }
   return false;
}



bool
CorbaConnection::disconnect(const string& qos)
{
   if (log_.CALL) log_.call(me(), "disconnect() ...");

   if (CORBA::is_nil(xmlBlaster_)) {
      shutdown();
      return false;
   }

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
  if (log_.CALL) log_.call(me(), "subscribe() ...");
  if (CORBA::is_nil(xmlBlaster_)) {
     string txt = "no auth.Server, you must login first";
     throw serverIdl::XmlBlasterException("communication.noConnection", 
                                          "client", me().c_str(), "en",
                                          txt.c_str(), "", "", "", "", "", "");
  }
  try {
     CORBA::String_var ret = xmlBlaster_->subscribe(xmlKey.c_str(), qos.c_str());
     return static_cast<char *>(ret);
  } catch(serverIdl::XmlBlasterException &e) {
     throw e;
  }
  //return "";
}


vector<string> CorbaConnection::unSubscribe(const string &xmlKey,
                                  const string &qos) 
{
  if (log_.CALL) log_.call(me(), "unSubscribe() ...");

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
     for (unsigned int ii=0; ii<retArr->length(); ii++) {
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
  if (log_.TRACE) log_.trace(me(), "Publishing the STL way ...");

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
     if (log_.TRACE) log_.trace(me(), msg);
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
  if (log_.TRACE) log_.trace(me(), "Publishing ...");

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
     if (log_.TRACE) log_.trace(me(), msg);
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
  if (log_.CALL) log_.call(me(), "publishArr() ...");

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
     for (unsigned int ii=0; ii<retArr->length(); ii++) {
        vecArr.push_back(static_cast<char *>(retArr[ii].inout()));
     }
     return vecArr;
  }
  catch(serverIdl::XmlBlasterException &e) {
     if (log_.TRACE) log_.trace(me(), "XmlBlasterException: "
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
  if (log_.CALL) log_.call(me(), "publishArr() ...");

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
     if (log_.TRACE) log_.trace(me(), "XmlBlasterException: "
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
  if (log_.CALL) log_.call(me(), "publishOneway() ...");

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
  if (log_.CALL) log_.call(me(), "publishOneway() ...");

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
  if (log_.CALL) log_.call(me(), "erase() ...");

  if (CORBA::is_nil(xmlBlaster_)) {
     string txt = "no auth.Server, you must login first";
     throw serverIdl::XmlBlasterException("communication.noConnection", 
                                          "client", me().c_str(), "en",
                                          txt.c_str(), "", "", "", "", "", "");
  }

  try {
     serverIdl::XmlTypeArr_var retArr = xmlBlaster_->erase(xmlKey.c_str(), qos.c_str());
     vector<string> vecArr;
     for (unsigned int ii=0; ii<retArr->length(); ii++) {
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
  if (log_.CALL) log_.call(me(), "get() ...");

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
  if (log_.CALL) log_.call(me(), "get() ...");

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
  if (log_.CALL) log_.call(me(), "ping(" + qos + ") ...");

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
  for (unsigned int ii=0; ii<len; ii++) {
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
  for (unsigned int ii=0; ii<len; ii++) {
     const serverIdl::MessageUnit &msgUnit = static_cast<const serverIdl::MessageUnit>(units[ii]);
     unsigned long len = static_cast<unsigned long>(msgUnit.content.length());
     const unsigned char * blob = static_cast<const unsigned char *>(&msgUnit.content[0]);
     if (log_.TRACE) log_.trace(me(), "copyFromCorba() '" + string((const char *)blob) + "' len=" + lexical_cast<string>(len));
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

CORBA::ORB_ptr CorbaConnection::orb_           = 0;
unsigned short CorbaConnection::numOfSessions_ = 0;
PortableServer::POA_ptr CorbaConnection::poa_  = 0;

}}}}} // end of namespace


