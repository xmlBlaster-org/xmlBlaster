/*----------------------------------------------------------------------------
Name:      CorbaConnection.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster: for now a simplified version
           without caching and without failsave mode.
Author:    <Michele Laghi> michele.laghi@attglobal.net
-----------------------------------------------------------------------------*/

#include <client/CorbaConnection.h>
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

namespace org { namespace xmlBlaster {

   CorbaConnection::CorbaConnection(int args, const char * const argc[], bool orbOwner)
      : loginQos_(), log_(args, argc) {
      log_.getProperties().loadPropertyFile();
      log_.info(me(), "Trying to establish a CORBA connection to xmlBlaster");
      if (log_.CALL) log_.call(me(), "CorbaConnection constructor ...");
      if (numOfSessions_ == 0) orb_ = CORBA::ORB_init(args, const_cast<char **>(argc));
      numOfSessions_++;
      nameServerControl_ = 0;
      numLogins_         = 0;
      xmlBlaster_        = 0;
      authServer_        = 0; // getAuthenticationService();
      callback_          = 0;
      defaultCallback_   = 0;
      orbOwner_          = orbOwner;
   }


   CorbaConnection::~CorbaConnection() {
      if (log_.CALL) log_.call(me(), "CorbaConnection destructor ...");
      numOfSessions_--;
      if (isLoggedIn()) logout();
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


   serverIdl::Server_ptr CorbaConnection::getXmlBlaster() {
      if ( !CORBA::is_nil(xmlBlaster_ /*.in()*/ )) {
         string msg = "Sorry, no xmlBlaster handle ";
         msg       += "available, please login first.";
         string txt = me();
         txt += ".NotLoggedIn";
         throw serverIdl::XmlBlasterException(txt.c_str(), msg.c_str());
      }
      return serverIdl::Server::_duplicate(xmlBlaster_);
   }


   CosNaming::NamingContext_ptr CorbaConnection::getNamingService() {
      if (log_.CALL) log_.call(me(), "getNamingService() ...");
      if (orb_ == 0) log_.panic(me(), "orb==null, internal problem");
      if (nameServerControl_ == 0)
         nameServerControl_ = new util::NameServerControl(orb_);
      return nameServerControl_->getNamingService();
   }


   authenticateIdl::AuthServer_ptr CorbaConnection::getAuthenticationService() {
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
         log_.getProperties().getStringProperty("iorFile","");
      // -iorFile IOR string is given through a file
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
      if (log_.TRACE) log_.trace(me(), "No -iorFile ...");

      // 3) Using builtin http IOR download ...
      {
         char myHostName[126];
         strcpy(myHostName, "localhost");
         gethostname(myHostName, 125);
         string iorHost = log_.getProperties().getStringProperty("iorHost",myHostName);
         // Port may be a name from /etc/services: "xmlBlaster 7609/tcp"
         string iorPortStr = log_.getProperties().getStringProperty("iorPort","xmlBlaster"); // default port=7609
         if (log_.TRACE) log_.trace(me(), "Trying -iorHost=" + iorHost + " and -iorPort=" + iorPortStr + " ...");
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
               xmlBlasterAddr.sin_port = htons(log_.getProperties().getIntProperty("iorPort",7609));
            int s = socket(AF_INET, SOCK_STREAM, 0);
            if (s != -1) {
               int ret=0;
               if ((ret=connect(s, (struct sockaddr *)&xmlBlasterAddr, sizeof(xmlBlasterAddr))) != -1) {
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
                  log_.warn(me(), "Connecting to -iorHost=" + iorHost + " failed"); // errno
               }
               shutdown(s, 2);
            }
         }
         if (!authServerIOR.empty()) {
            CORBA::Object_var obj = orb_->string_to_object(authServerIOR.c_str());
            authServer_ = authenticateIdl::AuthServer::_narrow(obj);
            string msg  = "Accessing xmlBlaster using -iorHost "+iorHost;
            log_.info(me(), msg);
            return authenticateIdl::AuthServer::_duplicate(authServer_);
         }
      }
      if (log_.TRACE) log_.trace(me(), "No -iorHost and -iorPort ...");


      // 4) asking Name Service CORBA compliant
      bool useNameService=log_.getProperties().getBoolProperty("ns",true);
      // -ns default is to ask the naming service

      string text = "Can't access xmlBlaster Authentication Service";
      text += ", is the server running and ready?\n - try to specify ";
      text += "'-iorFile <fileName>' if server is running on same host\n";
      text += " - try to specify '-iorHost <hostName>  -iorPort 7609' to ";
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
         catch(serverIdl::XmlBlasterException &e) {
            log_.error(me() + ".NoAuthService", text);
            throw serverIdl::XmlBlasterException(msg.c_str(),text.c_str());
         }
      }
      if (log_.TRACE) log_.trace(me(), "No -ns ...");
      throw serverIdl::XmlBlasterException(msg.c_str(), text.c_str());
   }


   serverIdl::Server_ptr
      CorbaConnection::login(const string &loginName, const string &passwd,
                             const LoginQosWrapper &qos, I_Callback *client) {

      if (log_.CALL) log_.call(me(), "login(" + loginName + ") ...");
      if ( !CORBA::is_nil(xmlBlaster_ /*.in()*/ )) {
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
         defaultCallback_ =  new DefaultCallback(loginName_, client, 0);
         callback_ = createCallbackServer(defaultCallback_);
         util::CallbackAddress addr("IOR");
         addr.setAddress(orb_->object_to_string(callback_));
         loginQos_.addCallbackAddress(addr);
         if (log_.TRACE) log_.trace(me(), string("Success, exported ") +
                                    "BlasterCallback Server interface for "+
                                    loginName);
      }
      loginRaw();
      return serverIdl::Server::_duplicate(xmlBlaster_);
   }


   clientIdl::BlasterCallback_ptr
      CorbaConnection::
      createCallbackServer(POA_clientIdl::BlasterCallback *implObj) {
      if (implObj) {
         CORBA::Object_var obj =
            orb_->resolve_initial_references("RootPOA");
         poa_ = PortableServer::POA::_narrow(obj);
         PortableServer::POAManager_var poa_mgr = poa_->the_POAManager();
         callback_ = implObj->_this();
         poa_mgr->activate();
         while (orb_->work_pending()) orb_->perform_work();
         return clientIdl::BlasterCallback::_duplicate(callback_);
         // add exception handling here !!!!!
      }
      return (clientIdl::BlasterCallback_ptr)0;
   }


   void CorbaConnection::loginRaw() {
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


   bool CorbaConnection::logout() {
      if (log_.CALL) log_.call(me(), "logout() ...");

      if ( CORBA::is_nil(xmlBlaster_ /*.in()*/ )) {
         log_.warn(me(), "No logout, you are not logged in");
         return false;
      }
      else log_.warn(me(), "Logout!");

      try {
         if (!CORBA::is_nil(xmlBlaster_)) authServer_->logout(xmlBlaster_);
         xmlBlaster_ = 0;
         return true;
      }
      catch(serverIdl::XmlBlasterException &e) {
         string msg = "XmlBlasterException: ";
         msg += e.reason;
         log_.warn(me(), msg);
      }
      xmlBlaster_ = 0;
      return false;
   }


   string CorbaConnection::subscribe(const string &xmlKey, const string &qos) {
      if (log_.CALL) log_.call(me(), "subscribe() ...");
      if (CORBA::is_nil(xmlBlaster_)) {
         string txt = "no auth.Server, you must login first";
         throw serverIdl::XmlBlasterException(me().c_str(), txt.c_str());
      }
      try {
         return xmlBlaster_->subscribe(xmlKey.c_str(), qos.c_str());
      } catch(serverIdl::XmlBlasterException &e) {
         throw e;
      }
      return "";
   }


   void CorbaConnection::unSubscribe(const string &xmlKey,
                                     const string &qos) {
      if (log_.CALL) log_.call(me(), "unSubscribe() ...");

      if (CORBA::is_nil(xmlBlaster_)) {
         string txt = "no auth.Server, you must login first";
         throw serverIdl::XmlBlasterException(me().c_str(), txt.c_str());
      }

      try {
         xmlBlaster_->unSubscribe(xmlKey.c_str(), qos.c_str());
         }
      catch(serverIdl::XmlBlasterException e) {
         throw e;
      }
   }


   string CorbaConnection::publish(const serverIdl::MessageUnit &msgUnit) {
      if (log_.TRACE) log_.trace(me(), "Publishing ...");

      if (CORBA::is_nil(xmlBlaster_)) {
         string txt = "no auth.Server, you must login first";
         throw serverIdl::XmlBlasterException(me().c_str(), txt.c_str());
      }

      try {
         return xmlBlaster_->publish(msgUnit);
      }
      catch(serverIdl::XmlBlasterException &e) {
         string msg = "XmlBlasterException: ";
         msg += e.reason;
         if (log_.TRACE) log_.trace(me(), msg);
         throw e;
      }
//        catch(CORBA::Exception &ex1) {
//       throw serverIdl::XmlBlasterException(me().c_str(),to_string(ex1));
//        }

      return "";
   }


   serverIdl::StringArr*
      CorbaConnection::publishArr(const serverIdl::MessageUnitArr& msgUnitArr){
      if (log_.CALL) log_.call(me(), "publishArr() ...");

      if (CORBA::is_nil(xmlBlaster_)) {
         string txt = "no auth.Server, you must login first";
         throw serverIdl::XmlBlasterException(me().c_str(), txt.c_str());
      }

      try {
         return xmlBlaster_->publishArr(msgUnitArr);
      }
      catch(serverIdl::XmlBlasterException &e) {
         if (log_.TRACE) log_.trace(me(), "XmlBlasterException: "
                                    + string(e.reason) );
         throw e;
      }
      return 0;
   }


   serverIdl::StringArr*
      CorbaConnection::erase(const string &xmlKey, const string &qos) {
      if (log_.CALL) log_.call(me(), "erase() ...");

      if (CORBA::is_nil(xmlBlaster_)) {
         string txt = "no auth.Server, you must login first";
         throw serverIdl::XmlBlasterException(me().c_str(), txt.c_str());
      }

      try {
         return xmlBlaster_->erase(xmlKey.c_str(), qos.c_str());
      }
      catch(serverIdl::XmlBlasterException e) {
         throw e;
      }
      return 0;
   }


   serverIdl::MessageUnitArr*
      CorbaConnection::get(const string &xmlKey, const string &qos) {
      serverIdl::MessageUnitArr* units;
      if (log_.CALL) log_.call(me(), "get() ...");

      if (CORBA::is_nil(xmlBlaster_)) {
         string txt = "no auth.Server, you must login first";
         throw serverIdl::XmlBlasterException(me().c_str(), txt.c_str());
      }

      try {
         units = xmlBlaster_->get(xmlKey.c_str(), qos.c_str());
         /*
         string subId = xmlBlaster_->subscribe(xmlKey.c_str(),
                                               qos.c_str());
         log_.info(me(),"New Entry in Cache created (subId="+subId+")");
         */
         return units; // check if this is OK or if it must be duplicated.
      }
      catch(serverIdl::XmlBlasterException &e) {
         throw e;
      }

      return (serverIdl::MessageUnitArr*)0;
   }


   void CorbaConnection::usage() {
      util::Log log;
      string me="";
      log.plain(me, "");
      log.plain(me, "Client connection options:");
      log.plain(me, "  -ior <IOR:00...>    The IOR string of the xmlBlaster-authentication server.");
      log.plain(me, "  -iorHost <host>     The host where to find xmlBlaster [localhost]");
      log.plain(me, "  -iorPort <port>     The port where xmlBlaster publishes its IOR [7609]");
      log.plain(me, "  -iorFile <fileName> A file with the xmlBlaster-authentication server IOR.");
      log.plain(me, "  -ns <true/false>    Try to access xmlBlaster through a naming service [true]");
      log.plain(me, "");
   }


   CORBA::ORB_ptr CorbaConnection::orb_           = 0;
   unsigned short CorbaConnection::numOfSessions_ = 0;
   PortableServer::POA_ptr CorbaConnection::poa_  = 0;
}} // end of namespace



