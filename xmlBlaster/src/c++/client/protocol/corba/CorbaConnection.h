/*----------------------------------------------------------------------------
Name:      CorbaConnection.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster: for now a simplified version 
           without caching and without failsafe mode.
Author:    <Michele Laghi> laghi@swissinfo.org
-----------------------------------------------------------------------------*/

#ifndef _CLIENT_PROTOCOL_CORBA_CORBACONNECTION_H
#define _CLIENT_PROTOCOL_CORBA_CORBACONNECTION_H

// #include <util/XmlBCfg.h>
#include <util/xmlBlasterDef.h>

#include <string>
#include <vector>
#include <fstream>
// #include <client/LoginQosWrapper.h>
#include <client/protocol/corba/DefaultCallback.h>
#include <util/Log.h>
#include <client/protocol/corba/NameServerControl.h>
#include <util/qos/address/CallbackAddress.h>
#include <util/MessageUnit.h>
#include <util/qos/ConnectQosFactory.h>
#include <util/qos/MsgQosFactory.h>
#include <util/key/MsgKeyFactory.h>

#define  SERVER_HEADER generated/xmlBlaster
#include <client/protocol/corba/CompatibleCorba.h>
#include COSNAMING

using namespace std;
using namespace org::xmlBlaster::util::qos; // ConnectQos + ConnectReturnQos
using namespace org::xmlBlaster::util::key;
using namespace org::xmlBlaster::util;

namespace org {
 namespace xmlBlaster {
  namespace client {
   namespace protocol {
    namespace corba {

   class Dll_Export CorbaConnection {
      
   private:
      
      string me() const {
         return string("CorbaConnection");
      }
      static CORBA::ORB_ptr           orb_;
      static PortableServer::POA_ptr  poa_;
      static unsigned short           numOfSessions_;
      NameServerControl*              nameServerControl_;
      authenticateIdl::AuthServer_ptr authServer_;
      serverIdl::Server_ptr           xmlBlaster_;
      clientIdl::BlasterCallback_ptr  callback_;
      string                          loginName_;
      string                          passwd_;
//      LoginQosWrapper                 loginQos_;
      int                             numLogins_;
      bool                            orbOwner_;
      DefaultCallback*                defaultCallback_;
      ConnectReturnQos                connectReturnQos_;
      string                          sessionId_;
      string                          xmlBlasterIOR_;
      string                          callbackIOR_;
      Global&                         global_;
      //mutable util::Log               log_;
      util::Log&                      log_;
      MsgKeyFactory                   msgKeyFactory_;
      MsgQosFactory                   msgQosFactory_;

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
       *    <li>-ior.file IOR string is given through a file</li>
       *    <li>-hostname host name or IP where xmlBlaster is running</li>
       *    <li>-port where the internal xmlBlaster-http server publishes 
       *         its IOR (defaults to 3412)</li>
       *    <li>-ns true/false, if a naming service shall be used</li>
       * </ul>
       */
      CorbaConnection(Global& global, bool orbOwner=false, CORBA::ORB_ptr orb=NULL);

      ~CorbaConnection();

      string getAddress() const;

      string getCbAddress() const;

      /**
       * Accessing the orb handle.
       * @return org.omg.CORBA.ORB
       */
      CORBA::ORB_ptr getOrb();

      
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
       *        <code>   -ior.file yourIorFile</code></li>
       *    <li>Give the xmlBlaster host and port where 
       * xmlBlaster-Authenticate serves the IOR via http, give at command line 
       * e.g.
       *        <code>   -hostname server.xmlBlaster.org  -port 3412</code>
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
//      serverIdl::Server_ptr login(const string &loginName, const string &passwd, 
//                   const LoginQosWrapper &qos, I_Callback *client=0);


      /**
       * The new way to connect (i.e. login to xmlBlaster)
       */
       ConnectReturnQos connect(const ConnectQos& connectQos);

       /**
        * Disconnects from the xmlBlaster server (the callback server is not
        * disconnected).
        */
       bool disconnect(const string& qos="");

       bool shutdown();

       bool shutdownCb();

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
//      void loginRaw();


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
      
      
      /**
       * Subscribe a message. 
       * <br />
       * Note: You don't need to free anything
       * @return The xml based QoS
       */
      string subscribe(const string &xmlKey, const string &qos=string("<qos/>"));
      
 
      vector<string> unSubscribe(const string &xmlKey, const string &qos=string("<qos/>"));
      
      
      /**
       * Publish the given message.
       * <p />
       * This is a wrapper around the raw CORBA publish() method
       * If the server disappears you get an exception.
       * This call will not block.
       * <p />
       * @see xmlBlaster.idl
       * @deprecated Please use the util::MessageUnit variant
       */
      string publish(const serverIdl::MessageUnit &msgUnit);

      /**
       * Publish with util MessageUnit (not CORBA specific client code). 
       * <br />
       * This method has a common interface which is not CORBA depending. 
       * Prefer this to publish(const serverIdl::MessageUnit &msgUnit)
       * <br />
       * Note: You don't need to free anything
       * @return The xml based QoS
       */
      string publish(const util::MessageUnit &msgUnitUtil);
      
      /**
       * Publish a bulk of messages. 
       * <br />
       * This method has a common interface which is not CORBA depending. 
       * <br />
       * Note: You don't need to free anything
       * @param A vector with MessageUnit
       * @return A vector of strings each is a publish return QoS. 
       * @see xmlBlaster.idl
       */
      vector<string> publishArr(const vector<util::MessageUnit> &msgVec);
     
      /**
       * @deprecated Please use the STL vector variant
       */
      serverIdl::XmlTypeArr* CorbaConnection::publishArr(const serverIdl::MessageUnitArr& msgUnitArr);

      /**
       * Publish a bulk of messages without ACK. 
       * <br />
       * This method has a common interface which is not CORBA depending. 
       * <br />
       * Note: You don't need to free anything
       * @param The MessageUnit array as a STL vector
       * @see xmlBlaster.idl
       */
      void CorbaConnection::publishOneway(const vector<util::MessageUnit>& msgVec);

      /**
       * Enforced by ServerOperations interface.
       * @see xmlBlaster.idl
       * @deprecated Use the vector<util::MessageUnit> variant
       */
      void publishOneway(const serverIdl::MessageUnitArr& msgUnitArr);

      /**
       * @see xmlBlaster.idl
       */
      vector<string> erase(const string &xmlKey, const string &qos=string("<qos/>"));

      
      /**
       * Access messages the synchronous way. 
       * <br />
       * Note: You don't need to free anything
       * @return The STL MessageUnit vector, its a copy so if you have the variable on the
       *         stack it will free itself
       * @see xmlBlaster.idl
       */
      vector<util::MessageUnit> get(const string &xmlKey, const string &qos=string("<qos/>"));

      //serverIdl::MessageUnitArr* get(const string &xmlKey, const string &qos);
      
      
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
      string ping(const string &qos=string("<qos/>"));

      /**
       * Transform a util::MessageUnit to the corba variant
       */
      void copyToCorba(serverIdl::MessageUnit &dest, const util::MessageUnit &src) const;

      /**
       * Transform STL vector to corba messageUnit array variant. 
       */
      void copyToCorba(serverIdl::MessageUnitArr_var &units, const vector<util::MessageUnit> &msgVec) const;

      void copyFromCorba(vector<util::MessageUnit> &vecArr, serverIdl::MessageUnitArr_var &units);

      /**
       * Command line usage.
       * <p />
       * These variables may be set in xmlBlaster.properties as well.
       * Don't use the "-" prefix there.
       */
      static void usage();
   
   }; // class CorbaConnection
}}}}} // namespace


#endif


