/*----------------------------------------------------------------------------
Name:      CorbaConnection.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster: for now a simplified version 
           without caching and without failsave mode.
Author:    <Michele Laghi> michele.laghi@attglobal.net
-----------------------------------------------------------------------------*/

#ifndef _CLIENT_CORBACONNECTION_H
#define _CLIENT_CORBACONNECTION_H

#include <string>
#include <fstream>
#include <client/LoginQosWrapper.h>
#include <client/DefaultCallback.h>
#include <util/Log.h>
#include <util/NameServerControl.h>
#include <util/CallbackAddress.h>

#define  SERVER_HEADER generated/xmlBlaster
#include <util/CompatibleCorba.h>
#include COSNAMING
using namespace std;

namespace org { namespace xmlBlaster {

   class CorbaConnection {
      
   private:
      
      string me() {
         return "CorbaConnection";
      }
      
      static CORBA::ORB_ptr           orb_;
      static PortableServer::POA_ptr  poa_;
      static unsigned short           numOfSessions_;
      util::NameServerControl*        nameServerControl_;
      authenticateIdl::AuthServer_ptr authServer_;
      serverIdl::Server_ptr           xmlBlaster_;
      clientIdl::BlasterCallback_ptr  callback_;
      string                          loginName_;
      string                          passwd_;
      LoginQosWrapper                 loginQos_;
      util::Log                       log_;
      int                             numLogins_;
      bool                            orbOwner_;

      DefaultCallback*                defaultCallback_;

   public:
      /**
       * CORBA client access to xmlBlaster (default behavior).
       */
/*        CorbaConnection(bool orbOwner = false) : log_(),  */
/*      loginQos_() , implObj_() { */
/*      int  args = 0; */
/*      char *argc[0]; */
/*      orb_ = CORBA::ORB_init(args, argc); */
/*      nameServerControl_ = 0; */
/*      numLogins_         = 0; */
/*      authServer_        = getAuthenticationService(); */
/*      callback_          = createCallbackServer(); */
/*      orbOwner_          = orbOwner; */
/*        } */

      /**
       * CORBA client access to xmlBlaster for <strong>normal client 
       * applications
       * </strong>.
       * <p />
       * @param arg  parameters given on command line
       * <ul>
       *    <li>-ior  IOR string is directly given</li>
       *    <li>-iorFile IOR string is given through a file</li>
       *    <li>-iorHost hostName or IP where xmlBlaster is running</li>
       *    <li>-iorPort where the internal xmlBlaster-http server publishes 
       *         its IOR (defaults to 7609)</li>
       *    <li>-ns true/false, if a naming service shall be used</li>
       * </ul>
       */
      CorbaConnection(int args=0, const char * const argc[]=0, bool orbOwner = false);

      ~CorbaConnection();

      /**
       * Accessing the orb handle.
       * @return org.omg.CORBA.ORB
       */
      CORBA::ORB_ptr getOrb() {
         return CORBA::ORB::_duplicate(orb_);
      }

      
      /**
       * Is used to perform work on the orb (if necessary).
       */
      bool orbPerformWork() {
         if (orb_ != NULL) {
            bool ret = (orb_->work_pending() != 0);
            if (ret) orb_->perform_work();
            return ret;
         }
         else
            return false;
      }


      /**
       * Run forever
       */
      void run() {
         if (orb_ != NULL)
            orb_->run();
      }
    

      /**
       * Accessing the xmlBlaster handle.
       * @return Server
       * @exception if not logged in
       */
      serverIdl::Server_ptr getXmlBlaster();
      

      /**
       * Locate the CORBA Name Service.
       * <p />
       * The found name service is cached, for better performance in 
       * subsequent calls
       * @return NamingContext, reference on name service
       * @exception XmlBlasterException
       *                    CORBA error handling if no naming service is found
       */
      CosNaming::NamingContext_ptr getNamingService();


      /**
       * Access the authentication service.
       * <p />
       * There are several ways to bootstrap the authentication service:
       * <br />
       * <ul>
       *    <li>Give the authentication service string-IOR at command line,
       * e.g.<br />
       *        <code>   -ior "IOR:0000..."</code><br />
       *        or giving a file name<br />
       *        <code>   -iorFile yourIorFile</code></li>
       *    <li>Give the xmlBlaster host and iorPort where 
       * xmlBlaster-Authenticate serves the IOR via http, give at command line 
       * e.g.
       *        <code>   -iorHost server.xmlBlaster.org  -iorPort 7609</code>
       * </li>
       *    <li>Try to find a naming service which knows about 
       * 'xmlBlaster-Authenticate'</li>
       * </ul>
       * <p />
       * @return a handle on the AuthServer IDL interface
       *
       */
      authenticateIdl::AuthServer_ptr getAuthenticationService();


      /**
       * Login to the server, using the default BlasterCallback implementation.
       * <p />
       * You need to implement the I_Callback interface, which informs you 
       * about arrived messages with its update() method
       * <p />
       * If you do multiple logins with the same I_Callback implementation, 
       * the loginName which is delivered with the update() method may be used
       * to dispatch the message to the correct client.
       * <p />
       * WARNING: <strong>The qos gets added a <pre>&lt;callback type='IOR'>
       * </pre> tag, so don't use it for a second login, otherwise a second 
       * callback is inserted !</strong>
       *
       * @param loginName The login name for xmlBlaster
       * @param passwd    The login password for xmlBlaster
       * @param qos       The Quality of Service for this client (the callback
       *                  tag will be added automatically if client!=null)
       * @param client    Your implementation of I_Callback, or null if you 
       *                  don't want any.
       * @exception       XmlBlasterException if login fails
       */
      serverIdl::Server_ptr login(const string &loginName, const string &passwd, 
                   const LoginQosWrapper &qos, I_Callback *client=0);


      /**
       * Building a Callback server.
       * @return the BlasterCallback server
       */
      clientIdl::BlasterCallback_ptr 
      createCallbackServer(POA_clientIdl::BlasterCallback *implObj);


      /**
       * Login to the server.
       * <p />
       * For internal use only.
       * The qos needs to be set up correctly if you wish a callback
       * @exception       XmlBlasterException if login fails
       */
      void loginRaw();


      /**
       * Access the login name.
       * @return your login name or null if you are not logged in
       */
      const string &getLoginName() const {
         return loginName_;
      }
      
      
      /**
       * Logout from the server.
       * @return true successfully logged out
       *         false failure on logout
       */
      bool logout();
      
      
      /**
       * @return true if you are logged in
       */
      bool isLoggedIn() {
         if (CORBA::is_nil(authServer_)) getAuthenticationService();
         return (!CORBA::is_nil(xmlBlaster_ /*.in()*/ ));
      }
      
      
      string subscribe(const string &xmlKey, const string &qos);
      
 
      void unSubscribe(const string &xmlKey, const string &qos);
      
      
      /**
       * Publish fault-tolerant the given message.
       * <p />
       * This is a wrapper around the raw CORBA publish() method
       * If the server disappears you get an exception.
       * This call will not block.
       * <p />
       * Enforced by ServerOperations interface (fail save mode)
       * @see xmlBlaster.idl
       */
      string publish(const serverIdl::MessageUnit &msgUnit);

      
      /**
       * Enforced by ServerOperations interface.
       * @see xmlBlaster.idl
       */
      serverIdl::StringArr*
      publishArr(const serverIdl::MessageUnitArr& msgUnitArr);
     
      
      /**
       * Enforced by ServerOperations interface (fail save mode)
       * @see xmlBlaster.idl
       */
      serverIdl::StringArr* erase(const string &xmlKey, const string &qos);

      
      /**
       * Enforced by ServerOperations interface (fail save mode)
       * @see xmlBlaster.idl
       */
      serverIdl::MessageUnitArr* get(const string &xmlKey, const string &qos);
      
      
      /**
       * Command line usage.
       * <p />
       * These variables may be set in xmlBlaster.properties as well.
       * Don't use the "-" prefix there.
       */
      static void usage();
   
   }; // class CorbaConnection
}} // namespace

#endif


