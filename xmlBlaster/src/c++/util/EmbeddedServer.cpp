/*-----------------------------------------------------------------------------
Name:      EmbeddedServer.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the Timeout Features
-----------------------------------------------------------------------------*/

#include <util/EmbeddedServer.h>
#include <util/Global.h>

namespace org { namespace xmlBlaster { namespace util {

EmbeddedServer::EmbeddedServer(Global& glob, const string& jvmArguments, const string& applArguments) 
   : Thread(),
     ME("EmbeddedServer"), 
     global_(glob), 
     log_(glob.getLog())
{
   isRunning_     = false;
   applArguments_ = applArguments;
   jvmArguments_  = jvmArguments;
}

EmbeddedServer::~EmbeddedServer()
{
 stop();
}

void EmbeddedServer::run()
{  
   if (isRunning_) {
      log_.warn(ME, "the current server is already running. ignoring the start command.");
      return;
   }
   if (isSomeServerResponding()) {
      log_.error(ME, "an external server is already running. Please shut it down");
      return;
   }
   string cmdLine = string("java ") + jvmArguments_ + " org.xmlBlaster.Main " + applArguments_;
   log_.info(ME, "starting the embedded server with command line: '" + cmdLine + "'");
   if ( system(NULL) ) {
      try {
         isRunning_ = true;
         system(cmdLine.c_str());
         isRunning_ = false;
         log_.info(ME, "the embedded server with command line: '" + cmdLine + "' has been stopped");
      }
      catch (exception& ex) {
         isRunning_ = false;
         log_.error(ME,string("could not start the server: ") + ex.what());
      }
      catch (...) {
         isRunning_ = false;
         log_.error(ME,"could not start the server: an unknown exception occured");
      }
   }
   else {
      isRunning_ = false;
      log_.error(ME, "could not start the embedded server: your OS does not have a command processor, plase start your server manually");
   }
}

bool EmbeddedServer::stop(bool shutdownExternal)
{
   if (!isRunning_ && !shutdownExternal) {
      log_.warn(ME, "the current embedded server is not running. Ignoring this 'stop' command");
      return false;
   }

   XmlBlasterAccess conn(global_, "embedded");
   try {
      SessionQos sessionQos(global_);
      sessionQos.setAbsoluteName("embeddedKiller");
      ConnectQos connQos(global_, "embeddedKiller", "secret");
      connQos.setSessionQos(sessionQos);
      // to be sure not to store the kill msg in a client queue ...
      Address address(global_);
      address.setDelay(0);
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

   PublishKey key(global_);
   key.setOid("__cmd:?exit=-1");
   PublishQos qos(global_);
   MessageUnit msgUnit(key, "", qos);
   conn.publish(msgUnit);
   return true;
}


bool EmbeddedServer::isSomeServerResponding() const
{
   try {
      XmlBlasterAccess conn(global_);
      SessionQos sessionQos(global_);
      sessionQos.setAbsoluteName("embeddedTester");
      ConnectQos connQos(global_, "embeddedTester", "secret");
      connQos.setSessionQos(sessionQos);
      conn.connect(connQos, NULL);
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

#include <util/PlatformUtils.hpp>

using namespace std;
using namespace org::xmlBlaster::util;


int main(int args, char* argv[])
{
   XMLPlatformUtils::Initialize();
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

