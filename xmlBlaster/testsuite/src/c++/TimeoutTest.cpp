/*-----------------------------------------------------------------------------
Name:      TimeoutTest.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the Timeout Features
-----------------------------------------------------------------------------*/

#include <util/Timeout.h>
#include <iostream>
#include <string>
#include <util/Global.h>
#include <util/thread/Thread.h>

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

namespace org { namespace xmlBlaster {

class TimeoutTest : public I_Timeout {
   
private:
   string ME;
   Timeout *timeoutObject;
   Global& global_;
public:
   TimeoutTest(Global& global, string name) : ME(name), global_(global) {
   }

   virtual ~TimeoutTest()
   {
      delete timeoutObject;
   }

   void timeout(void *userData) {
      cout << "this is the timeout for the test" << endl;
      if (userData == NULL) return;
      Timeout *to = static_cast<Timeout*>(userData);
      to->addTimeoutListener(this, 1000, to);
      cout << "next timeout will occur in about 1 s" << endl;
   }

   void testTimeout() 
   {
      cout << ME << " testTimeout(): the timeout will now be started" << endl;
      timeoutObject->start();
      cout << ME << " testTimeout(): the timeout will now be triggered" << endl;
      timeoutObject->addTimeoutListener(this, 2000, timeoutObject);
      cout << ME << " testTimeout: timeout triggered. Waiting to be fired (should happen in 2 seconds" << endl;

      // waiting some time ... (you can't use join because the timeout thread
      // never stops ...
      Timestamp delay = 10000;
      delay *= 1000000;
      std::cout << ME << " main thread is sleeping now" << std::endl;
      Thread::sleep(delay);
      std::cout << ME << " after waiting to complete" << std::endl;
   }

   void setUp(int args=0, char *argc[]=0) {
      cout << ME << " setUp(): creating the timeout object" << endl;
      timeoutObject = new Timeout(global_);
      cout << ME << " setUp(): timeout object created" << endl;
   }

   void tearDown() {
      cout << ME << " tearDown(): will delete now" << endl;
      timeoutObject->shutdown();
      // delete TimestampFactory::getInstance();
      cout << ME << " tearDown(): has deleted now" << endl;
   }
};
   
}} // namespace



int main(int args, char *argc[]) {

   Global& glob = Global::getInstance();
   glob.initialize(args, argc);

   org::xmlBlaster::TimeoutTest *test = new org::xmlBlaster::TimeoutTest(glob, "TimeoutTest");

   test->setUp(args, argc);
   test->testTimeout();
   test->tearDown();
   delete test;
   return 0;
}


