/*-----------------------------------------------------------------------------
Name:      TestThread.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the Thread cleanup
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

class TestThread : public Thread {
   
private:
   string ME;
   Global& global_;
   I_Log&  log_;
   bool    blocking_;
   bool    doRun_;
public:
   TestThread(Global& global, string name, bool blocking) 
      : ME(name), 
        global_(global),
        log_(global.getLog("test")) 
   {
      blocking_ = blocking;
      doRun_    = true;
   }

   ~TestThread()
   {
      if (log_.call()) log_.call(ME, "destructor");
      if (!blocking_) {
         doRun_ = false;
         join();
      }
   }

   void run() 
   {
      log_.info(ME, "start run");
      if (blocking_) {
         sleepSecs(30);
      }
      else {
         while (doRun_) {
            if (log_.trace()) log_.trace(ME, "run: going to sleep");
            sleep(20);
         }
         if (log_.trace()) log_.trace(ME, "run: coming out of non-blocking run loop");
      }
      log_.info(ME, "stopped run");
   }

   void testThread()
   {
      log_.info(ME, "testThread() start");
      start();
      sleepSecs(2);
      log_.info(ME, "testThread() end");
   }

   void setUp(int args=0, char *argc[]=0) {
      if (log_.trace()) {
         for (int i=0; i < args; i++) {
            log_.trace(ME, string(" setUp invoked with argument ") + string(argc[i]));
         }
      }
   }

   void tearDown() {
   }
};
   
}}} // namespace

using namespace org::xmlBlaster::test;

int main(int args, char *argc[]) 
{
   Global& glob = Global::getInstance();
   glob.initialize(args, argc);

   TestThread *testObj = new TestThread(glob, "TestThread", false);
   testObj->setUp(args, argc);
   testObj->testThread();
   testObj->tearDown();
   delete testObj;

   testObj = new TestThread(glob, "TestThread", true);
   testObj->setUp(args, argc);
   testObj->testThread();
   testObj->tearDown();
   delete testObj;
   testObj = NULL;
   return 0;
}


