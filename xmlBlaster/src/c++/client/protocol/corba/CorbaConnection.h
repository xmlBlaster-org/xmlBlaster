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

#include <util/xmlBlasterDef.h>

#include <string>
#include <vector>
#include <fstream>
#include <client/protocol/corba/DefaultCallback.h>
#include <util/I_Log.h>
#include <client/protocol/corba/NameServerControl.h>
#include <util/qos/address/CallbackAddress.h>
#include <util/MessageUnit.h>
#include <util/qos/ConnectQosFactory.h>
#include <util/qos/MsgQosFactory.h>
#include <util/key/MsgKeyFactory.h>

#include <client/protocol/corba/CompatibleCorba.h>  // client side headers
#include COSNAMING

 // org::xmlBlaster::util::qos::ConnectQos + org::xmlBlaster::util::qos::ConnectReturnQos

namespace org {
 namespace xmlBlaster {
  namespace client {
   namespace protocol {
    namespace corba {

   class Dll_Export CorbaConnection {
      
   private:
      
      std::string me() const {
         return std::string("CorbaConnection");
      }
      /* static*/ CORBA::ORB_ptr           orb_;
      /*static*/ PortableServer::POA_ptr  poa_;
      NameServerControl*              nameServerControl_;
      authenticateIdl::AuthServer_ptr authServer_;
      serverIdl::Server_ptr           xmlBlaster_;
      clientIdl::BlasterCallback_ptr  callback_;
      std::string                          loginName_;
      std::string                          passwd_;
      int                             numLogins_;
      DefaultCallback*                defaultCallback_;
      org::xmlBlaster::util::qos::ConnectReturnQosRef connectReturnQos_;
      std::string                          sessionId_;
      std::string                          xmlBlasterIOR_;
      std::string                          callbackIOR_;
      org::xmlBlaster::util::Global&       global_;
      /* mutable */ org::xmlBlaster::util::I_Log&     log_;
      org::xmlBlaster::util::key::MsgKeyFactory msgKeyFactory_;
      org::xmlBlaster::util::qos::MsgQosFactory msgQosFactory_;

   public:
      /**
       * CORBA client access to xmlBlaster for <strong>normal client 
       * applications
       * </strong>.
       * <p />
       * @param arg  parameters given on command line
       * <ul>
       *    <li>-dispatch/connection/plugin/ior/iorString  IOR std::string is directly given</li>
       *    <li>-dispatch/connection/plugin/ior/iorFile IOR std::string is given through a file</li>
       *    <li>-bootstrapHostname host name or IP where xmlBlaster is running</li>
       *    <li>-bootstrapPort where the internal xmlBlaster-http server publishes 
       *         its IOR (defaults to 3412)</li>
       *    <li>-dispatch/connection/plugin/ior/useNameService true/false, if a naming service shall be used</li>
       * </ul>
       */
      CorbaConnection(org::xmlBlaster::util::Global& global, CORBA::ORB_ptr orb=NULL);

      ~CorbaConnection();

      std::string getAddress() const;

      std::string getCbAddress() const;

      /**
       * Is used to perform work on the orb (if necessary).
       * @return true if some work was done
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
    

      /*
       * Accessing the xmlBlaster handle.
       * @return Server
       * @exception if not logged in
      serverIdl::Server_ptr getXmlBlaster();
       */
      
private:
      /**
       * Locate the CORBA Name Service.
       * <p />
       * The found name service is cached, for better performance in 
       * subsequent calls
       * @exception org::xmlBlaster::util::XmlBlasterException
       *                    CORBA error handling if no naming service is found
       */
      void initNamingService();

public:
      /**
       * Access the authentication service.
       * <p />
       * There are several ways to bootstrap the authentication service:
       * <br />
       * <ul>
       *    <li>Give the authentication service std::string-IOR at command line,
       * e.g.<br />
       *        <code>   -dispatch/connection/plugin/ior/iorString "IOR:0000..."</code><br />
       *        or giving a file name<br />
       *        <code>   -dispatch/connection/plugin/ior/iorFile yourIorFile</code></li>
       *    <li>Give the xmlBlaster host and bootstrap port where 
       * xmlBlaster-Authenticate serves the IOR via http, give at command line 
       * e.g.
       *        <code>   -bootstrapHostname server.xmlBlaster.org  -bootstrapPort 3412</code>
       * </li>
       *    <li>Try to find a naming service which knows about 
       * 'xmlBlaster-Authenticate'</li>
       * </ul>
       * <p />
       * @return a handle on the AuthServer IDL interface
       *
       */
      void initAuthenticationService();

      /**
       * The new way to connect (i.e. login to xmlBlaster)
       */
       org::xmlBlaster::util::qos::ConnectReturnQosRef connect(const org::xmlBlaster::util::qos::ConnectQosRef& connectQos);

       /**
        * Disconnects from the xmlBlaster server (the callback server is not
        * disconnected).
        */
       bool disconnect(const std::string& qos="");

       /**
        * Shutdown the connection without disconnecting (xmlBlaster does not know that we have disappeared)
        */
       bool shutdown();

       bool shutdownCb();

      /**
       * Building a Callback server.
       * @return the BlasterCallback server
       */
      void createCallbackServer(POA_clientIdl::BlasterCallback *implObj);


      /**
       * Access the login name.
       * @return your login name or null if you are not logged in
       */
      const std::string &getLoginName() const {
         return loginName_;
      }
      
      
      /**
       * @return true if you are logged in
       */
      bool isLoggedIn() {
         if (CORBA::is_nil(authServer_)) initAuthenticationService();
         return (!CORBA::is_nil(xmlBlaster_ /*.in()*/ ));
      }
      
      
      /**
       * Subscribe a message. 
       * <br />
       * Note: You don't need to free anything
       * @return The xml based QoS
       */
      std::string subscribe(const std::string &xmlKey, const std::string &qos=std::string("<qos/>"));
      
 
      std::vector<std::string> unSubscribe(const std::string &xmlKey, const std::string &qos=std::string("<qos/>"));
      
      
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
      std::string publish(const serverIdl::MessageUnit &msgUnit);

      /**
       * Publish with util org::xmlBlaster::util::MessageUnit (not CORBA specific client code). 
       * <br />
       * This method has a common interface which is not CORBA depending. 
       * Prefer this to publish(const serverIdl::MessageUnit &msgUnit)
       * <br />
       * Note: You don't need to free anything
       * @return The xml based QoS
       */
      std::string publish(const util::MessageUnit &msgUnitUtil);
      
      /**
       * Publish a bulk of messages. 
       * <br />
       * This method has a common interface which is not CORBA depending. 
       * <br />
       * Note: You don't need to free anything
       * @param A std::vector with org::xmlBlaster::util::MessageUnit
       * @return A std::vector of std::strings each is a publish return QoS. 
       * @see xmlBlaster.idl
       */
      std::vector<std::string> publishArr(const std::vector<util::MessageUnit> &msgVec);
     
      /**
       * @deprecated Please use the STL std::vector variant
       */
      serverIdl::XmlTypeArr* publishArr(const serverIdl::MessageUnitArr& msgUnitArr);

      /**
       * Publish a bulk of messages without ACK. 
       * <br />
       * This method has a common interface which is not CORBA depending. 
       * <br />
       * Note: You don't need to free anything
       * @param The org::xmlBlaster::util::MessageUnit array as a STL std::vector
       * @see xmlBlaster.idl
       */
      void publishOneway(const std::vector<util::MessageUnit>& msgVec);

      /**
       * Enforced by ServerOperations interface.
       * @see xmlBlaster.idl
       * @deprecated Use the std::vector<util::MessageUnit> variant
       */
      void publishOneway(const serverIdl::MessageUnitArr& msgUnitArr);

      /**
       * @see xmlBlaster.idl
       */
      std::vector<std::string> erase(const std::string &xmlKey, const std::string &qos=std::string("<qos/>"));

      
      /**
       * Access messages the synchronous way. 
       * <br />
       * Note: You don't need to free anything
       * @return The STL org::xmlBlaster::util::MessageUnit std::vector, its a copy so if you have the variable on the
       *         stack it will free itself
       * @see xmlBlaster.idl
       */
      std::vector<util::MessageUnit> get(const std::string &xmlKey, const std::string &qos=std::string("<qos/>"));

      //serverIdl::MessageUnitArr* get(const std::string &xmlKey, const std::string &qos);
      
      
      /**
       * Publish fault-tolerant the given message.
       * <p />
       * This is a wrapper around the raw CORBA publish() method
       * If the server disappears you get an exception.
       * This call will not block.
       * <p />
       * Enforced by ServerOperations interface (failsafe mode)
       * @see xmlBlaster.idl
       */
      std::string ping(const std::string &qos=std::string("<qos/>"));

      /**
       * Transform a util::MessageUnit to the corba variant
       */
      void copyToCorba(serverIdl::MessageUnit &dest, const util::MessageUnit &src) const;

      /**
       * Transform STL std::vector to corba messageUnit array variant. 
       */
      void copyToCorba(serverIdl::MessageUnitArr_var &units, const std::vector<util::MessageUnit> &msgVec) const;

      void copyFromCorba(std::vector<util::MessageUnit> &vecArr, serverIdl::MessageUnitArr_var &units);

      /**
       * Command line usage.
       * <p />
       * These variables may be set in xmlBlaster.properties as well.
       * Don't use the "-" prefix there.
       */
      static std::string usage();
   
   }; // class CorbaConnection
}}}}} // namespace


#endif


