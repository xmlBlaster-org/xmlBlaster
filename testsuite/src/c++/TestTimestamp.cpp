/*-----------------------------------------------------------------------------
Name:      TimeoutTest.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the Timeout Features
-----------------------------------------------------------------------------*/
#include "TestSuite.h"
#include <iostream>

using namespace std;
using namespace org::xmlBlaster::util;

/**
 * This client tests the synchronous method get() with its different qos
 * variants.<p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 */

namespace org { namespace xmlBlaster { namespace test {

class TestTimestamp 
{
   
private:
   string ME;
   Timeout *timestampObject;
public:
   TestTimestamp(string name) : ME(name) {
   }

   void setUp(int args=0, char *argc[]=0) {
      for (int i=0; i < args; i++) {
         cout << ME << " setUp invoked with argument " << argc[i] << endl;
      }
   }

   /**
    * The following should be tested:
    * - all timestamps are different from eachother
    * - timestamps are created in increasing order
    *
    */
   void testTimestamp() {

      TimestampFactory& factory = TimestampFactory::getInstance();
      Timestamp sum = 0;
      Timestamp previous = 0;
      int nmax = 1000;
      for (int i=0; i < nmax; i++) {
         Timestamp timestamp = factory.getTimestamp();
         if (timestamp <= previous) {
            cout << "error: the timestamp is lower or like a previous one" << endl;
            assert(0);
         }
         if (i != 0) sum += timestamp - previous;
         previous = timestamp;
      }
      double average = 1.0 * sum / (nmax-1);
      cout << ME << " testTimestamp: " + lexical_cast<string>(average) + " nanoseconds per request" << endl;
   }

   void tearDown() {
   }
};

   
}}} // namespace


using namespace org::xmlBlaster::test;

int main(int args, char *argc[]) {

   TestTimestamp *testObj = new TestTimestamp("TestTimestamp");

   testObj->setUp(args, argc);
   testObj->testTimestamp();
   testObj->tearDown();
   delete testObj;
   testObj = NULL;
   return 0;
}

