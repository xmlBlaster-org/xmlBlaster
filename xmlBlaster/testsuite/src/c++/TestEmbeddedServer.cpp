/*-----------------------------------------------------------------------------
Name:      TestEmbeddedServer.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the Timeout Features
-----------------------------------------------------------------------------*/

#include <client/XmlBlasterAccess.h>
#include <util/XmlBlasterException.h>
#include <util/EmbeddedServer.h>
#include <util/Global.h>
#include <util/Log.h>
#include <util/PlatformUtils.hpp>
#include <util/Timestamp.h>
#include <boost/lexical_cast.hpp>
#include "testSuite.h"

/**
 *
 */

using boost::lexical_cast;
using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;
using namespace org::xmlBlaster;

class TestEmbeddedServer
{
private:
   string            ME;
   Global&           global_;
   Log&              log_;

public:
   TestEmbeddedServer(Global& glob) 
      : ME("TestEmbeddedServer"), 
        global_(glob), 
        log_(glob.getLog())
   {
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
      log_.info(ME, "testing the embedded server");
      log_.info(ME, "note that you should shut down running xmlBlaster servers before you run this test");
      log_.info(ME, "an xmlBlaster server instance will be started now and after 15 seconds it will be");
      log_.info(ME, "shut down. It then waits 10 seconds and starts again another server.");
      log_.info(ME, "it then will live for 15 seconds and die. The test ends there.");
      Thread::sleepSecs(3);
      EmbeddedServer server(global_);
      log_.info(ME, "starting the embedded server now");
      server.start();
      Thread::sleepSecs(15);
      log_.info(ME, "stopping the embedded server now");
      server.stop();
      server.sleepSecs(10);
      log_.info(ME, "starting the embedded server now");
      server.start();
      Thread::sleepSecs(15);
      log_.info(ME, "stopping the embedded server now");
      server.stop();
      server.join();
      log_.info(ME, "testEmbeddedServer ended successfully");
   }

};


int main(int args, char *argv[])
{
//   ServerThread* server = new ServerThread("java org.xmlBlaster.Main");
//   server->start();
   XMLPlatformUtils::Initialize();
   Global& glob = Global::getInstance();
   glob.initialize(args, argv);
   TestEmbeddedServer test(glob);
   
   test.setUp();
   test.testEmbeddedServer();
   test.tearDown();

   return 0;
}
