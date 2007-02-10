/*-----------------------------------------------------------------------------
Name:      EmbeddedServer.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the Timeout Features
-----------------------------------------------------------------------------*/

#ifndef _UTIL_EMBEDDEDSERVER_H
#define _UTIL_EMBEDDEDSERVER_H

#include <util/xmlBlasterDef.h>
#include <client/XmlBlasterAccess.h>
#include <util/XmlBlasterException.h>
#include <util/thread/ThreadImpl.h>

/**
 * Embedds an xmlBlaster server so that you can control its start and stop from within a c++ program.
 * Is not supported on Windows CE
 * @author <a href='mailto:laghi@swissinfo.org'>Michele Laghi</a>
 */

namespace org { namespace xmlBlaster { namespace util {

class EmbeddedServer;

class EmbeddedServerRunner : public org::xmlBlaster::util::thread::Thread 
{
private:
   const std::string    ME;
   EmbeddedServer& owner_;
public:

   EmbeddedServerRunner(EmbeddedServer& owner);

   /**
    * This method is invoked by the start method. Note that it is invoked only if the current thread is not
    * already running.
    */
   void run();
};

class Dll_Export EmbeddedServer
{
friend class EmbeddedServerRunner;
private:
   std::string                ME;
   org::xmlBlaster::util::Global&               global_;
   org::xmlBlaster::util::I_Log&                  log_;
   bool                  isRunning_;
   std::string                applArguments_;
   std::string                jvmArguments_;
   org::xmlBlaster::client::XmlBlasterAccess*     externalAccess_;
   EmbeddedServerRunner* runner_;
public:
   /**
    * To start the server, you need a java virtual machine, the xmlBlaster server installed and in the 
    * CLASSPATH. Alternatively you can pass these parameters to the jvm with the 'applArguments'
    * programmatically. You also can pass application parameters to the xmlBlaster. For example you could
    * set traces to true (-trace true).
    *
    * Note that it uses the user 'embeddedTester' to check if a connection exists (if a server is
    * responding) and a user 'embeddedKiller' to kill the server. If you have configured an authentication
    * service for xmlBlaster make sure that these users have credentials and that 'embeddedKiller' has
    * authorization to kill the server.
    * 
    * @param glob the global variable
    * @param jvmArguments the arguments to pass to the java virtual machine.
    * @param applArguments the arguments to pass to the application.
    * @param externalAccess the pointer to the external org::xmlBlaster::client::XmlBlasterAccess object. If this is NULL, then an
    *        own instance is created each time, otherwise the external is used. This parameter is needed
    *        where the communication protocol does not support multithreading.
    */
   EmbeddedServer(org::xmlBlaster::util::Global& glob, const std::string& jvmArguments = "", const std::string& applArguments="", org::xmlBlaster::client::XmlBlasterAccess* externalAccess=NULL);

   virtual ~EmbeddedServer();

   /**
    * This method shuts down the xmlBlaster server which is responding to the request. If the current 
    * embedded server is running it shuts it down. If the current embedded server is not running, this 
    * method will make a try to kill it only if the 'shutdownExternal' flag has been set to 'true'. This
    * flag defaults to 'false'.
    * The method returns 'true' if a server was shutdown, 'false' otherwise.
    * It blocks until the thread really has stopped (it joins the thread)
    */
   bool stop(bool shutdownExternal=false, bool warnIfNotRunning=true);

   /**
    * @param blockUntilUp if set to 'true' the method blocks until the server really is reacheable
    *        (i.e. a client has successfully been able to connect)
    */
   bool start(bool blockUntilUp=true);

   /**
    * This method can be used to check if a server is already running and responding to requests.
    */
   bool isSomeServerResponding() const;
};

}}}


#endif

