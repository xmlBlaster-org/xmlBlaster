/*-----------------------------------------------------------------------------
Name:      TestCorbaDriver.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the Timeout Features
-----------------------------------------------------------------------------*/
#ifdef COMPILE_CORBA_PLUGIN

#include <client/protocol/corba/CorbaDriverFactory.h>
#include "TestSuite.h"
#include <iostream>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::client::protocol::corba;


namespace org { namespace xmlBlaster { namespace test {

class TestCorbaDriver
{
private:
   string              ME;
   Global&             global_;
   I_Log&              log_;
   Mutex               updateMutex_;
   int                 numOfUpdates_;
   CorbaDriverFactory& factory_;

public:

   TestCorbaDriver(Global& glob) 
      : ME("TestCorbaDriver"), 
        global_(glob), 
        log_(glob.getLog()),
        updateMutex_(),
        factory_(CorbaDriverFactory::getFactory(glob))
   {
   }


   void tearDown()
   {
   }

   virtual ~TestCorbaDriver()
   {
   }

   void setUp()
   {
   }

   void testSingleDriver()
   {
      log_.info(ME, "testing single driver: start");
      CorbaDriver& driver1 = factory_.getDriverInstance(&global_);
      CorbaDriver& driver2 = factory_.getDriverInstance(&global_);
      CorbaDriver& driver3 = factory_.getDriverInstance(&global_);
      // should be three instances with the name 'one' now ...

      assertEquals(log_, ME, &driver1, &driver2, "Both 'one' drivers should share the same address");
      assertEquals(log_, ME, &driver2, &driver3, "Both 'one' drivers should share the same address");
      assertEquals(log_, ME, 2, factory_.killDriverInstance(&global_), "number of 'one' instances should be 2 (after deletion)");
      Thread::sleepSecs(1);
      assertEquals(log_, ME, 1, factory_.killDriverInstance(&global_), "number of 'one' instances should be 1 (after deletion)");
      Thread::sleepSecs(1);
      assertEquals(log_, ME, 0, factory_.killDriverInstance(&global_), "number of 'one' instances should be 0 (after deletion)");
      Thread::sleepSecs(1);
      assertEquals(log_, ME, -1, factory_.killDriverInstance(&global_), "number of 'one' instances should be -1 (after deletion)");
      Thread::sleepSecs(1);
      assertEquals(log_, ME, -1, factory_.killDriverInstance(&global_), "number of 'one' instances should be -1 (after deletion)");
      log_.info(ME, "testing single driver: end");
   }

   void testMultipleDrivers()
   {
      log_.info(ME, "testing multiple drivers: start");
      Global glob2;
      Global glob3;
      CorbaDriver& driver1 = factory_.getDriverInstance(&global_);
      CorbaDriver& driver2 = factory_.getDriverInstance(&glob2);
      CorbaDriver& driver3 = factory_.getDriverInstance(&glob3);
      factory_.getDriverInstance(&glob2);
      factory_.getDriverInstance(&glob3);
      // should be three instances with the name 'one' now ...

      assertDifferes(log_, ME, &driver1, &driver2, "'one' and 'two' should NOT share the same address");
      assertDifferes(log_, ME, &driver2, &driver3, "Both 'one' drivers should share the same address");
      
      Thread::sleepSecs(1);
      assertEquals(log_, ME, 0, factory_.killDriverInstance(&global_), "number of 'one' instances should be 0 (after deletion)");
      Thread::sleepSecs(1);
      assertEquals(log_, ME, -1, factory_.killDriverInstance(&global_), "number of 'one' instances should be -1 (after deletion)");
      Thread::sleepSecs(1);
      assertEquals(log_, ME, 1, factory_.killDriverInstance(&glob2), "number of 'two' instances should be 1 (after deletion)");
      Thread::sleepSecs(1);
      assertEquals(log_, ME, 1, factory_.killDriverInstance(&glob3), "number of 'three' instances should be 1 (after deletion)");
      Thread::sleepSecs(1);
      assertEquals(log_, ME, 0, factory_.killDriverInstance(&glob3), "number of 'three' instances should be 0 (after deletion)");
      // here the thread still should be running ...
      Thread::sleepSecs(2);
      assertEquals(log_, ME, 0, factory_.killDriverInstance(&glob2), "number of 'two' instances should be 0 (after deletion)");
      log_.info(ME, "testing multiple drivers: end");
   }

};                                

}}}

/**
 * Try
 * <pre>
 *   java TestCorbaDriver -help
 * </pre>
 * for usage help
 */
using namespace org::xmlBlaster::test;

int main(int args, char ** argv)
{
   try {
      org::xmlBlaster::util::Object_Lifetime_Manager::init();
      Global& glob = Global::getInstance();
      glob.initialize(args, argv);

      TestCorbaDriver test(glob);
      test.setUp();
      test.testSingleDriver();
      test.tearDown();

      test.setUp();
      test.testMultipleDrivers();
      test.tearDown();
      org::xmlBlaster::util::Object_Lifetime_Manager::fini();
   }
   catch (XmlBlasterException& ex) {
      std::cout << ex.toXml() << std::endl;
   }
   catch (bad_exception& ex) {
      cout << "bad_exception: " << ex.what() << endl;
   }
   catch (exception& ex) {
      cout << " exception: " << ex.what() << endl;
   }
   catch (string& ex) {
      cout << "string: " << ex << endl;
   }
   catch (char* ex) {
      cout << "char* :  " << ex << endl;
   }

   catch (...)
   {
      cout << "unknown exception occured" << endl;
      XmlBlasterException e(INTERNAL_UNKNOWN, "main", "main thread");
      cout << e.toXml() << endl;
   }

   return 0;
}
 
#else // COMPILE_CORBA_PLUGIN
#include <iostream>
int main(int args, char ** argv)
{
   ::std::cout << "TestCorbaDriver: COMPILE_CORBA_PLUGIN is not defined, nothing to do" << ::std::endl;
   return 0;
}
#endif // COMPILE_CORBA_PLUGIN

