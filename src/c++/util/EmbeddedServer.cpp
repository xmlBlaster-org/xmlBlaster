/*-----------------------------------------------------------------------------
Name:      EmbeddedServer.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the Timeout Features
-----------------------------------------------------------------------------*/

#include <util/EmbeddedServer.h>
#include <util/Global.h>
#include <util/lexical_cast.h>

namespace org { namespace xmlBlaster { namespace util {

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::qos::address;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

EmbeddedServerRunner::EmbeddedServerRunner(EmbeddedServer& owner) : ME("EmbeddedServerRunner"), owner_(owner)
{
}

void EmbeddedServerRunner::run()
{  
#if !defined(WINCE)
   if (owner_.log_.call()) owner_.log_.call(ME, "::run");

   if (owner_.isRunning_) {
      owner_.log_.warn(ME, "the current server is already running. ignoring the start command.");
      return;
   }
/* currently commented out (could give problems if multithreading not supported)
   if (isSomeServerResponding()) {
      log_.error(ME, "an external server is already running. Please shut it down");
      return;
   }
*/
   string cmdLine = string("java ") + owner_.jvmArguments_ + " org.xmlBlaster.Main " + owner_.applArguments_;
   owner_.log_.info(ME, "starting the embedded server with command line: '" + cmdLine + "'");
   if (system(NULL)) {
      try {
         owner_.isRunning_ = true;
         int ret = system(cmdLine.c_str());
         owner_.log_.info(ME, "the embedded server with command line: '" + cmdLine + "' has been stopped, return code is: " + lexical_cast<std::string>(ret));
         owner_.isRunning_ = false;
      }
      catch (exception& ex) {
         owner_.log_.error(ME,string("could not start the server: ") + ex.what());
         owner_.isRunning_ = false;
      }
      catch (...) {
         owner_.log_.error(ME,"could not start the server: an unknown exception occured");
         owner_.isRunning_ = false;
      }
   }
   else {
      owner_.log_.error(ME, "could not start the embedded server: your OS does not have a command processor, plase start your server manually");
      owner_.isRunning_ = false;
   }
#endif //!defined(WINCE)
}


EmbeddedServer::EmbeddedServer(Global& glob, const string& jvmArguments, const string& applArguments, XmlBlasterAccess* externalAccess) 
   : ME("EmbeddedServer"), 
     global_(glob), 
     log_(glob.getLog("org.xmlBlaster.util"))
{
   isRunning_      = false;
   applArguments_  = applArguments;
   jvmArguments_   = jvmArguments;
   externalAccess_ = externalAccess; 
   runner_         = false;
}

EmbeddedServer::~EmbeddedServer()
{
   log_.call(ME, "destructor");
   // don't try to stop it with the borrowed external connection here since it could be a 
   // failsafe connection (which would queue this publish in case it is already disconnected)
   externalAccess_ = NULL;
   stop(false, false);
   log_.trace(ME, "destructor: stopped the server");
}

bool EmbeddedServer::start(bool blockUntilUp)
{
   if (log_.call()) log_.call(ME, "start");

   if (runner_) return false;
   runner_ = new EmbeddedServerRunner(*this);
        const bool detached = false;
   bool ret  = runner_->start(detached);
   if (ret && blockUntilUp) {
      if (log_.trace()) log_.trace(ME, "start: setting up for a client connection");
      bool isConnected = false;
      int count = 0;
      while (!isConnected && count < 60) {
         if (log_.trace()) log_.trace(ME, "start: establishing a connection: trial nr. '" + lexical_cast<std::string>(count) + "'");
         try {
            count++;
            XmlBlasterAccess conn(global_);
            ConnectQos connQos(global_, "embeddedKiller", "secret");
            Address *address = new Address(global_);
            address->setDelay(0);
            connQos.setAddress(address);
            // to be sure not to store the kill msg in a client queue ...
            conn.connect(connQos, NULL);
            log_.trace(ME, "successfully connected to the embedded server");
            conn.disconnect(DisconnectQos(global_));
            log_.trace(ME, "successfully disconnected from the embedded server");
            isConnected = true;
         }
         catch (XmlBlasterException& ex) {
            if (log_.trace()) log_.trace(ME, "exception occurred when connecting: " + ex.toXml());
            if ( !ex.isCommunication() ) throw ex;
            if (log_.trace()) log_.trace(ME, "the exception occurred was a communication exception (connection not established yet). Will retry (sleep for 2 sec)");
            Thread::sleepSecs(2);
         }
         count++;
      }
      if (!isConnected) {
         log_.error(ME, "maximum number of retrials to establish a connection failed ");
         throw XmlBlasterException(COMMUNICATION_NOCONNECTION, ME, " start: could not establish a connection to the embedded server");
      }

   }
   return ret;
}

bool EmbeddedServer::stop(bool shutdownExternal, bool warnIfNotRunning)
{
   if (log_.call()) log_.call(ME, "stop");
   if (!isRunning_ && !shutdownExternal) {
      if (warnIfNotRunning)
         log_.warn(ME, "the current embedded server is not running. Ignoring this 'stop' command");
      return false;
   }

   PublishKey key(global_);
   key.setOid("__cmd:?exit=0");
   PublishQos qos(global_);
   MessageUnit msgUnit(key, "0", qos);

   XmlBlasterAccess conn(global_);
   try {
      ConnectQos connQos(global_, "embeddedKiller", "secret");
      // to be sure not to store the kill msg in a client queue ...
      Address *address = new Address(global_);
      address->setDelay(0);
      connQos.setAddress(address);
      conn.connect(connQos, NULL);
   }
   catch (XmlBlasterException& ex) {
      if ( ex.isCommunication() ) {
         log_.warn(ME, "there is no server responding, ignoring this 'stop' command");
         return false;
      }
      throw ex;
   }
   conn.publish(msgUnit);

   if (log_.trace()) log_.trace(ME, "stop: going to join the threads");
   if (runner_) runner_->join();

   delete runner_;
   runner_ = NULL;
   if (log_.trace()) log_.trace(ME, "stop completed");
   return true;
}


bool EmbeddedServer::isSomeServerResponding() const
{
   try {
      SessionQos sessionQos(global_);
      sessionQos.setAbsoluteName("embeddedTester");
      ConnectQos connQos(global_, "embeddedTester", "secret");
      connQos.setSessionQos(sessionQos);
      if (externalAccess_) {
         externalAccess_->connect(connQos, NULL);
      }
      else {
         XmlBlasterAccess conn(global_);
         conn.connect(connQos, NULL);
      }
      return true;
   }
   catch (XmlBlasterException& ex) {
      if (ex.isCommunication()) {
         return false;
      }
      throw ex; // then it is another exception
   }
}


}}}


#ifdef _XMLBLASTER_CLASSTEST

using namespace std;
using namespace org::xmlBlaster::util;


int main(int args, char* argv[])
{
   Global& glob = Global::getInstance();
   glob.initialize(args, argv);
  
   EmbeddedServer server(glob, ""); // , "-info false -error false -warn false");
   server.start();
   Thread::sleepSecs(10);
   server.stop();
   Thread::sleepSecs(10);
   server.start();
   Thread::sleepSecs(10);

   return 0;
}

#endif

