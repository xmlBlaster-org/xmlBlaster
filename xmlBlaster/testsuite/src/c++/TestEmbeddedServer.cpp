/*-----------------------------------------------------------------------------
Name:      TestEmbeddedServer.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the Timeout Features
-----------------------------------------------------------------------------*/
#include "TestSuite.h"
#include <iostream>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;
using namespace org::xmlBlaster;

namespace org { namespace xmlBlaster { namespace test {

class TestEmbeddedServer
{
private:
   string  ME;
   Global& global_;
   I_Log&  log_;
   long    sleepDelay_;


   inline void usage() 
   {
      log_.info(ME, "usage: all typical xmlBlaster command line arguments");
      log_.info(ME, "plus the following additional command line arguments:");
      log_.info(ME, " -h (for help: this command)");
      log_.info(ME, " -additional.sleep.delay (long) : the number of seconds to wait between shutting down and starting the embedded server and viceversa");
      log_.info(ME, " note that the embedded server will already wait until a connection is up until it continues to work, so normally you don't need additional waiting time");
      exit(0);
   }


public:
   TestEmbeddedServer(Global& glob) 
      : ME("TestEmbeddedServer"), 
        global_(glob), 
        log_(glob.getLog())
   {

      int args = glob.getArgs();
      const char * const* argc = glob.getArgc();
      for (int i=0; i < args; i++) {
         string help = argc[i];
         if ( help == string("-h") || help == string("-help") || help == string("--help") || help == string("-?") ) {
            usage();
         }
      }

      sleepDelay_ = glob.getProperty().getLongProperty("additional.sleep.delay", 0L);
      if (sleepDelay_ < 0L) {
         sleepDelay_ = 0L;
         log_.warn(ME, "the additional.sleep.delay property was negative. Setting it to 0 s");
      }
   }


   virtual ~TestEmbeddedServer()
   {
   }

   void setUp()
   {
   }

   void tearDown()
   {
   }

   void testEmbeddedServer()
   {
      string delay = lexical_cast<string>(sleepDelay_);
      log_.info(ME, "testing the embedded server");
      log_.info(ME, "note that you should shut down running xmlBlaster servers before you run this test");
      log_.info(ME, string("an xmlBlaster server instance will be started now and after ") + delay + " seconds it will be");
      log_.info(ME, string("shut down. It then waits ") + delay + " seconds and starts again another server.");
      log_.info(ME, "it then will live for 15 seconds and die. The test ends there.");
      Thread::sleepSecs(3);
      EmbeddedServer server(global_, "", "");
      log_.info(ME, "starting the embedded server now");
      server.start();
      Thread::sleepSecs(sleepDelay_);
      log_.info(ME, "stopping the embedded server now");
      server.stop();
      Thread::sleepSecs(sleepDelay_);
      log_.info(ME, "starting the embedded server now");
      server.start();
      Thread::sleepSecs(15);
      log_.info(ME, "stopping the embedded server now");
      server.stop();
      log_.info(ME, "testEmbeddedServer ended successfully");
   }

};

}}} // namespace


using namespace org::xmlBlaster::test;

int main(int args, char *argv[])
{
   org::xmlBlaster::util::Object_Lifetime_Manager::init();
   Global& glob = Global::getInstance();
   glob.initialize(args, argv);
   TestEmbeddedServer test(glob);
   
   test.setUp();
   test.testEmbeddedServer();
   test.tearDown();

   org::xmlBlaster::util::Object_Lifetime_Manager::fini();
   return 0;
}
