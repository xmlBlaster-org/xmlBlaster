/*-----------------------------------------------------------------------------
Name:      TestCorbaDriver.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the Timeout Features
-----------------------------------------------------------------------------*/

// #include <client/XmlBlasterAccess.h>
// #include <util/EmbeddedServer.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include <util/Log.h>
#include <util/PlatformUtils.hpp>
// #include <util/thread/Thread.h>
// #include <util/Timestamp.h>
#include <client/protocol/corba/CorbaDriver.h>
#include <boost/lexical_cast.hpp>
#include "testSuite.h"


/**
 *
 */

using boost::lexical_cast;
using namespace std;
using namespace org::xmlBlaster::util;
// using namespace org::xmlBlaster::util::thread;
// using namespace org::xmlBlaster::client;
// using namespace org::xmlBlaster::client::qos;
// using namespace org::xmlBlaster::client::key;
using namespace org::xmlBlaster::client::protocol::corba;
// using namespace org::xmlBlaster;

namespace org { namespace xmlBlaster {

class TestCorbaDriver // : public I_Callback
{
private:
   string  ME;
   Global& global_;
   Log&    log_;

//   EmbeddedServer     embeddedServer_;
   Mutex              updateMutex_;
   int                numOfUpdates_;

public:

   TestCorbaDriver(Global& glob) 
      : ME("TestCorbaDriver"), 
        global_(glob), 
        log_(glob.getLog()),
//        embeddedServer_(glob, "", "-info false -warn false -error false"),
        updateMutex_()
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
      CorbaDriver& driver1 = CorbaDriver::getInstance(global_, "one");
      CorbaDriver& driver2 = CorbaDriver::getInstance(global_, "one");
      CorbaDriver& driver3 = CorbaDriver::getInstance(global_, "one");
      // should be three instances with the name 'one' now ...

      assertEquals(log_, ME, &driver1, &driver2, "Both 'one' drivers should share the same address");
      assertEquals(log_, ME, &driver2, &driver3, "Both 'one' drivers should share the same address");
      assertEquals(log_, ME, 2, CorbaDriver::killInstance("one"), "number of 'one' instances should be 2 (after deletion)");
      Thread::sleepSecs(1);
      assertEquals(log_, ME, 1, CorbaDriver::killInstance("one"), "number of 'one' instances should be 1 (after deletion)");
      Thread::sleepSecs(1);
      assertEquals(log_, ME, 0, CorbaDriver::killInstance("one"), "number of 'one' instances should be 0 (after deletion)");
      Thread::sleepSecs(1);
      assertEquals(log_, ME, -1, CorbaDriver::killInstance("one"), "number of 'one' instances should be -1 (after deletion)");
      Thread::sleepSecs(1);
      assertEquals(log_, ME, -1, CorbaDriver::killInstance("one"), "number of 'one' instances should be -1 (after deletion)");
      log_.info(ME, "testing single driver: end");
   }

   void testMultipleDrivers()
   {
      log_.info(ME, "testing multiple drivers: start");
      CorbaDriver& driver1 = CorbaDriver::getInstance(global_, "one");
      CorbaDriver& driver2 = CorbaDriver::getInstance(global_, "two");
      CorbaDriver& driver3 = CorbaDriver::getInstance(global_, "three");
      CorbaDriver::getInstance(global_, "two");
      CorbaDriver::getInstance(global_, "three");
      // should be three instances with the name 'one' now ...

      assertDifferes(log_, ME, &driver1, &driver2, "'one' and 'two' should NOT share the same address");
      assertDifferes(log_, ME, &driver2, &driver3, "Both 'one' drivers should share the same address");
      
      Thread::sleepSecs(1);
      assertEquals(log_, ME, 0, CorbaDriver::killInstance("one"), "number of 'one' instances should be 0 (after deletion)");
      Thread::sleepSecs(1);
      assertEquals(log_, ME, -1, CorbaDriver::killInstance("one"), "number of 'one' instances should be -1 (after deletion)");
      Thread::sleepSecs(1);
      assertEquals(log_, ME, 1, CorbaDriver::killInstance("two"), "number of 'two' instances should be 1 (after deletion)");
      Thread::sleepSecs(1);
      assertEquals(log_, ME, 1, CorbaDriver::killInstance("three"), "number of 'three' instances should be 1 (after deletion)");
      Thread::sleepSecs(1);
      assertEquals(log_, ME, 0, CorbaDriver::killInstance("three"), "number of 'three' instances should be 0 (after deletion)");
      // here the thread still should be running ...
      Thread::sleepSecs(2);
      assertEquals(log_, ME, 0, CorbaDriver::killInstance("two"), "number of 'two' instances should be 0 (after deletion)");
      log_.info(ME, "testing multiple drivers: end");
   }

/*
   string update(const string& sessionId, UpdateKey& updateKey, void *content, long contentSize, UpdateQos& updateQos)
   {
      Lock lock(updateMutex_);
      log_.info(ME, "update invoked");
//      numOfUpdates_++;
      return "";
   }
*/

};

}}

/**
 * Try
 * <pre>
 *   java TestCorbaDriver -help
 * </pre>
 * for usage help
 */
using namespace org::xmlBlaster;

int main(int args, char ** argv)
{
   try {
      XMLPlatformUtils::Initialize();
      Global& glob = Global::getInstance();
      glob.initialize(args, argv);

      TestCorbaDriver test(glob);
      test.setUp();
      test.testSingleDriver();
      test.tearDown();

      test.setUp();
      test.testMultipleDrivers();
      test.tearDown();
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
 
