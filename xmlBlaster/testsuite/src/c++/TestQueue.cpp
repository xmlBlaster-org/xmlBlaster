/*-----------------------------------------------------------------------------
Name:      TestQueue.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the Timeout Features
-----------------------------------------------------------------------------*/

#include <util/ReferenceHolder.h>
#include <util/queue/PublishQueueEntry.h>
#include <util/queue/ConnectQueueEntry.h>
#include <util/queue/MsgQueue.h>
 
#include <iostream>
#include <string>
#include <util/Log.h>
#include <util/Global.h>

#include <boost/lexical_cast.hpp>
#include <vector>

using boost::lexical_cast;

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::queue;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;


/**
 * Tests the queue entry and queue functionality. The following is tested here:
 * - PublishQueueEntry comparison operators
 * - ConnectQueueEntry comparison operators
 * - Intermixed comparisons (between PublishQueueEntry and ConnectQueueEntry).
 */

namespace org { namespace xmlBlaster {

template <class T> void assertEquals(Log& log, const T& should, const T& is, const string& txt)
{
   if (should != is) {
      log.error(txt, string(" value is ") + lexical_cast<string>(is) + " but should be '" + lexical_cast<string>(should) + "'");
      assert(0);
   }
}


class TestQueue
{
   
private:
   string    ME;
   Global&   global_;
   Log&      log_;
   MsgQueue* queue_; 

public:
   TestQueue(Global& global, string name) : ME(name), global_(global), log_(global.getLog("test"))
   {
      queue_ = NULL;
   }

   virtual ~TestQueue()
   {
      delete queue_;
      queue_ = NULL;
   }

   void testPublishCompare() 
   {
      log_.info(ME, "comparison test between PublishQueueEntry objects: starting ...");

      PublishKey pubKey(global_);
      PublishQos pubQos(global_);
      MessageUnit msgUnit(pubKey, string("comparison test"), pubQos);
      PublishQueueEntry entry1(msgUnit, "publish1");
      PublishQueueEntry entry2(msgUnit, "publish2");
      PublishQueueEntry entry3(msgUnit, "publish3", 2);
      PublishQueueEntry entry4(msgUnit, "publish4", 3);
      PublishQueueEntry entry5(msgUnit, "publish5", 1);

      assertEquals(log_, true, entry2 < entry1, "PublishQos compare 2 with 1");
      assertEquals(log_, true, entry3 < entry4, "PublishQos compare 3 with 4");
      assertEquals(log_, true, entry5 < entry4, "PublishQos compare 5 with 4");

      log_.info(ME, "comparison test between PublishQueueEntry objects: successfully completed!");
   }


   void testConnectCompare() 
   {
      log_.info(ME, "comparison test between ConnectQueueEntry objects: starting ...");

      ConnectQos connectQos(global_);
      ConnectQueueEntry entry1(connectQos, "connect1");
      ConnectQueueEntry entry2(connectQos, "connect2");
      ConnectQueueEntry entry3(connectQos, "connect3", 2);
      ConnectQueueEntry entry4(connectQos, "connect4", 3);
      ConnectQueueEntry entry5(connectQos, "connect5", 1);

      assertEquals(log_, true, entry2 < entry1, "PublishQos compare 2 with 1");
      assertEquals(log_, true, entry3 < entry4, "PublishQos compare 3 with 4");
      assertEquals(log_, true, entry5 < entry4, "PublishQos compare 5 with 4");

      log_.info(ME, "comparison test between ConnectQueueEntry objects: successfully completed!");
   }

   void testMixedCompare() 
   {
      log_.info(ME, "comparison test between PublishQueueEntry and ConnectQueueEntry objects: starting ...");

      PublishKey pubKey(global_);
      PublishQos pubQos(global_);
      MessageUnit msgUnit(pubKey, string("comparison test"), pubQos);
      ConnectQos connectQos(global_);

      PublishQueueEntry entry1(msgUnit, "publish3", 2);
      ConnectQueueEntry entry2(connectQos, "connect4", 3);
      PublishQueueEntry entry3(msgUnit, "publish5", 1);

      ConnectQueueEntry entry4(connectQos, "connect3", 2);
      PublishQueueEntry entry5(msgUnit, "publish4", 3);
      ConnectQueueEntry entry6(connectQos, "connect5", 1);

      assertEquals(log_, true, entry1 < entry2, "Mixed compare 1 with 2");
      assertEquals(log_, true, entry3 < entry2, "Mixed compare 3 with 2");

      assertEquals(log_, true, entry4 < entry5, "Mixed compare 4 with 5");
      assertEquals(log_, true, entry6 < entry5, "Mixed compare 6 with 5");

      log_.info(ME, "comparison test between PublishQueueEntry and ConnectQueueEntry objects: successfully completed!");
   }


   void testQueue()
   {
      QueueProperty prop(global_, "");
      queue_ = new MsgQueue(global_, prop);
      assertEquals(log_, true, queue_->empty(),  "check for emptiness of queue after creation");
      ConnectQos connQos(global_);
      ConnectQueueEntry entry(connQos);
      queue_->put(entry);
      assertEquals(log_, false, queue_->empty(), "check for emptiness of queue after creation");
      
      vector<EntryType> ret = queue_->peekWithSamePriority();
      assertEquals(log_, (size_t)1, ret.size(), "check for number of entries peeked");

      assertEquals(log_, (long)1, queue_->randomRemove(ret.begin(), ret.end()), "check for number of entries deleted");
      assertEquals(log_, true, queue_->empty(), "check for emptiness after deletion");
   }


   void setUp() 
   {
      if (queue_) {
         delete queue_;
	 queue_ = NULL;
      }
   }

   void tearDown() {
      if (queue_) {
         delete queue_;
	 queue_ = NULL;
      }
   }


};
   
}} // namespace



int main(int args, char *argc[]) {

   Global& glob = Global::getInstance();
   glob.initialize(args, argc);

   org::xmlBlaster::TestQueue *test = new org::xmlBlaster::TestQueue(glob, "TestQueue");

   test->setUp();
   test->testPublishCompare();
   test->tearDown();

   test->setUp();
   test->testConnectCompare();
   test->tearDown();

   test->setUp();
   test->testMixedCompare();
   test->tearDown();

   test->setUp();
   test->testQueue();
   test->tearDown();

   delete test;
   return 0;
}


