/*-----------------------------------------------------------------------------
Name:      TestTimeout.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the Timeout Features
-----------------------------------------------------------------------------*/
#include "TestSuite.h"
#include <iostream>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::thread;

/**
 * This client tests the synchronous method get() with its different qos
 * variants.<p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 */

namespace org { namespace xmlBlaster { namespace test {

class TestTimeout : public I_Timeout {
   
private:
   string ME;
   Timeout *timeoutObject;
   Global& global_;
   I_Log&  log_;
   int     count_;
	int     max_;
public:
   TestTimeout(Global& global, string name) 
      : ME(name), 
        global_(global),
        log_(global.getLog("test")) 
   {
      count_ = 0;
		max_ = 10;
   }

   virtual ~TestTimeout()
   {
      delete timeoutObject;
   }

   void timeout(void *userData) {
      log_.info(ME, "this is the timeout for the test count_=" + lexical_cast<string>(count_));
      if (userData == NULL) return;
      Timeout *to = static_cast<Timeout*>(userData);
      if (count_ < max_) {
         to->addTimeoutListener(this, 1000, to);
         log_.info(ME, "next timeout will occur in about 1 s");
         count_++;
      }
   }

   void testTimeout() 
   {
      log_.info(ME, "testTimeout(): the timeout will now be triggered");
      timeoutObject->addTimeoutListener(this, 2000, timeoutObject);
      log_.info(ME, "testTimeout: timeout triggered. Waiting to be fired (should happen in 2 seconds");

      // waiting some time ... (you can't use join because the timeout thread
      // never stops ...
      log_.info(ME, "main thread is sleeping now");
      Thread::sleepSecs(max_+6);
      log_.info(ME, "after waiting to complete");
   }

   void testLifecycle() 
   {
      log_.info(ME, "testLifecycle(): the timeout will now be triggered");
      Timeout* timeout = new Timeout(global_);
      timeout->addTimeoutListener(this, 10000, timeout);
      log_.info(ME, "testLifecycle: timeout triggered. Now destroying it again");
      timeout->shutdown();
      delete timeout;
   }

   void setUp(int args=0, char *argc[]=0) {
      if (log_.trace()) {
         for (int i=0; i < args; i++) {
            log_.trace(ME, string(" setUp invoked with argument ") + string(argc[i]));
         }
      }
      log_.info(ME, "setUp(): creating the timeout object");
      timeoutObject = new Timeout(global_);
      log_.info(ME, "setUp(): timeout object created");
   }

   void tearDown() {
      log_.info(ME, "tearDown(): will delete now");
      timeoutObject->shutdown();
      // delete TimestampFactory::getInstance();
      log_.info(ME, "tearDown(): has deleted now");
   }
};
   
}}} // namespace

using namespace org::xmlBlaster::test;

int main(int args, char *argc[]) 
{
   Global& glob = Global::getInstance();
   glob.initialize(args, argc);
   try {
      TestTimeout testObj(glob, "TestTimeout");
      testObj.setUp(args, argc);
      testObj.testLifecycle();
      testObj.testTimeout();
      testObj.tearDown();
   } catch(...) {
      std::cout << "UNEXPECTED EXCEPTION" << std::endl;
   }
   org::xmlBlaster::util::Object_Lifetime_Manager::fini();
   return 0;
}


