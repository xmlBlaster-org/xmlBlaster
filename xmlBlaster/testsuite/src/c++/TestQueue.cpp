/*-----------------------------------------------------------------------------
Name:      TestQueue.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the Timeout Features
-----------------------------------------------------------------------------*/
#include "TestSuite.h"
#include <vector>
#include <iostream>
/*#include "tut.h"*/
#include <util/queue/QueueFactory.h>
#include <util/queue/I_Queue.h>
#include <util/queue/PublishQueueEntry.h>
#include <util/queue/ConnectQueueEntry.h>
#include <util/queue/SubscribeQueueEntry.h>
#include <util/queue/UnSubscribeQueueEntry.h>

/**
 * Tests the queue entry and queue functionality. The following is tested here:
 * - PublishQueueEntry comparison operators
 * - ConnectQueueEntry comparison operators
 * - Intermixed comparisons (between PublishQueueEntry and ConnectQueueEntry).
 */
namespace org { namespace xmlBlaster { namespace test {

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::qos::storage;
using namespace org::xmlBlaster::util::queue;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

class TestQueue
{
   
private:
   string    ME;
   Global&   global_;
   I_Log&    log_;
   I_Queue* queue_; 

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
      string me = ME + "::testPublishCompare";
      log_.info(me, "");
      log_.info(me, "comparison test between PublishQueueEntry objects.");

      PublishKey pubKey(global_);
      PublishQos pubQos(global_);
      MessageUnit msgUnit(pubKey, string("comparison test"), pubQos);
      PublishQueueEntry entry1(global_, msgUnit, "publish1");
      PublishQueueEntry entry2(global_, msgUnit, "publish2");
      PublishQueueEntry entry3(global_, msgUnit, "publish3", 2);
      PublishQueueEntry entry4(global_, msgUnit, "publish4", 3);
      PublishQueueEntry entry5(global_, msgUnit, "publish5", 1);

      assertEquals(log_, me, true, entry2 < entry1, "1. PublishQos compare 2 with 1");
      assertEquals(log_, me, true, entry3 < entry4, "2. PublishQos compare 3 with 4");
      assertEquals(log_, me, true, entry5 < entry4, "3. PublishQos compare 5 with 4");

      log_.info(me, "test ended successfully");
   }


   void testConnectCompare() 
   {
      string me = ME + "::testConnectCompare";
      log_.info(me, "");
      log_.info(me, "comparison test between ConnectQueueEntry objects.");

      ConnectQos connectQos(global_);
      ConnectQueueEntry entry1(global_, connectQos, "connect1");
      ConnectQueueEntry entry2(global_, connectQos, "connect2");
      ConnectQueueEntry entry3(global_, connectQos, "connect3", 2);
      ConnectQueueEntry entry4(global_, connectQos, "connect4", 3);
      ConnectQueueEntry entry5(global_, connectQos, "connect5", 1);

      assertEquals(log_, me, true, entry2 < entry1, "1. PublishQos compare 2 with 1");
      assertEquals(log_, me, true, entry3 < entry4, "2. PublishQos compare 3 with 4");
      assertEquals(log_, me, true, entry5 < entry4, "3. PublishQos compare 5 with 4");

      log_.info(me, "test ended successfully");
   }

   void testMixedCompare() 
   {
      string me = ME + "::testMixedCompare";
      log_.info(me, "");
      log_.info(me, "comparison test between PublishQueueEntry and ConnectQueueEntry objects.");

      PublishKey pubKey(global_);
      PublishQos pubQos(global_);
      MessageUnit msgUnit(pubKey, string("comparison test"), pubQos);
      ConnectQos connectQos(global_);

      PublishQueueEntry entry1(global_, msgUnit, "publish3", 2);
      ConnectQueueEntry entry2(global_, connectQos, "connect4", 3);
      PublishQueueEntry entry3(global_, msgUnit, "publish5", 1);

      ConnectQueueEntry entry4(global_, connectQos, "connect3", 2);
      PublishQueueEntry entry5(global_, msgUnit, "publish4", 3);
      ConnectQueueEntry entry6(global_, connectQos, "connect5", 1);

      assertEquals(log_, me, true, entry1 < entry2, "1. Mixed compare 1 with 2");
      assertEquals(log_, me, true, entry3 < entry2, "2. Mixed compare 3 with 2");

      assertEquals(log_, me, true, entry4 < entry5, "3. Mixed compare 4 with 5");
      assertEquals(log_, me, true, entry6 < entry5, "4. Mixed compare 6 with 5");

      log_.info(me, "test completed successfully");
   }


   void testWithOneEntry()
   {
      string me = ME + "::testWithOneEntry";
      log_.info(me, "");
      log_.info(me, "this test creates a queue. The following checks are done:");
      ClientQueueProperty prop(global_, "");
      queue_ = QueueFactory::getFactory(global_).createQueue(prop);
      assertEquals(log_, me, true, queue_->empty(), " 1. the queue must be empty after creation");
      ConnectQos connQos(global_);
      ConnectQueueEntry entry(global_, connQos);
      queue_->put(entry);
      assertEquals(log_, me, false, queue_->empty(), " 2. the queue must contain entries after invoking put one time");
      vector<EntryType> ret = queue_->peekWithSamePriority();
      assertEquals(log_, me, (size_t)1, ret.size(), " 3. the number of entries peeked after one put must be 1");
      assertEquals(log_, me, (long)1, queue_->randomRemove(ret.begin(), ret.end()), " 4. randomRemove must return 1 entry deleted");
      assertEquals(log_, me, true, queue_->empty(), " 5. after removing all entries (it was only 1 entry) the queue  must be empty");
      log_.info(me, "ends here. Test was successful.");
   }


   void testOrder()
   {
      string me = ME + "::testOrder";
      log_.info(me, "");
      log_.info(me, "this test checks the order in which entries are returned from the queue");
      ClientQueueProperty prop(global_, "");
      queue_ = QueueFactory::getFactory(global_).createQueue(prop);
      ConnectQos connQos(global_);
      ConnectQueueEntry   entry(global_, connQos, "7", 1);
      queue_->put(entry);
      entry = ConnectQueueEntry(global_, connQos, "4", 5);
      queue_->put(entry);
      entry = ConnectQueueEntry(global_, connQos, "1", 7);
      queue_->put(entry);
      entry = ConnectQueueEntry(global_, connQos, "2", 7);
      queue_->put(entry);
      entry = ConnectQueueEntry(global_, connQos, "8", 1);
      queue_->put(entry);
      entry = ConnectQueueEntry(global_, connQos, "5", 5);
      queue_->put(entry);
      entry = ConnectQueueEntry(global_, connQos, "6", 5);
      queue_->put(entry);
      entry = ConnectQueueEntry(global_, connQos, "3", 7);
      queue_->put(entry);
      entry = ConnectQueueEntry(global_, connQos, "9", 1);
      queue_->put(entry);

      vector<EntryType> ret = queue_->peekWithSamePriority();
      // should be 3 entries with priority 7 
      assertEquals(log_, me, (size_t)3, ret.size(), "1. number of priority 7 msg peeked must be correct.");
      assertEquals(log_, me, 1, atoi(ret[0]->getEmbeddedType().c_str()), "2. checking the first entry.");
      assertEquals(log_, me, 2, atoi(ret[1]->getEmbeddedType().c_str()), "3. checking the second entry.");
      assertEquals(log_, me, 3, atoi(ret[2]->getEmbeddedType().c_str()), "4. checking the third entry.");

      assertEquals(log_, me, false, queue_->empty(), "5. there should still be entries in the queue.");
      queue_->randomRemove(ret.begin(), ret.end());
      ret = queue_->peekWithSamePriority();
      assertEquals(log_, me, (size_t)3, ret.size(), "6. number of priority 7 msg peeked must be correct.");
      assertEquals(log_, me, 4, atoi(ret[0]->getEmbeddedType().c_str()), "7. checking the first entry.");
      assertEquals(log_, me, 5, atoi(ret[1]->getEmbeddedType().c_str()), "8. checking the second entry.");
      assertEquals(log_, me, 6, atoi(ret[2]->getEmbeddedType().c_str()), "9. checking the third entry.");
            
      queue_->randomRemove(ret.begin(), ret.end());
      ret = queue_->peekWithSamePriority();
      assertEquals(log_, me, (size_t)3, ret.size(), "10. number of priority 7 msg peeked must be correct.");
      assertEquals(log_, me, 7, atoi(ret[0]->getEmbeddedType().c_str()), "11. checking the first entry.");
      assertEquals(log_, me, 8, atoi(ret[1]->getEmbeddedType().c_str()), "12. checking the second entry.");
      assertEquals(log_, me, 9, atoi(ret[2]->getEmbeddedType().c_str()), "13. checking the third entry.");
      queue_->randomRemove(ret.begin(), ret.end());
      assertEquals(log_, me, true, queue_->empty(), "14. the queue should be empty now.");
      log_.info(me, "test ended successfully");
   }


   void testMaxMsg()
   {
      string me = ME + "::testMaxMsg";
      log_.info(me, "");
      log_.info(me, "this test checks that an excess of entries really throws an exception");
      ClientQueueProperty prop(global_, "");
      prop.setMaxEntries(10);
      queue_ = QueueFactory::getFactory(global_).createQueue(prop);
      ConnectQos connQos(global_);
      int i=0;
      try {
         for (i=0; i < 10; i++) {
            ConnectQueueEntry entry(global_, connQos);
            queue_->put(entry);
         }
         log_.info(me, "1. putting entries inside the queue: OK");      
      }
      catch (const XmlBlasterException &/*ex*/) {
         log_.error(me, "1. putting entries inside the queue: FAILED could not put inside the queue the entry nr. " + lexical_cast<string>(i));      
         assert(0);
      }
      try {
         ConnectQueueEntry entry(global_, connQos);
         queue_->put(entry);
         log_.error(me, "2. putting entries inside the queue: FAILED should have thrown an exception");      
         assert(0);
      }
      catch (const XmlBlasterException &ex) {
         assertEquals(log_, me, ex.getErrorCodeStr(), string("resource.overflow.queue.entries"), "3. checking that exceeding number of entries throws the correct exception.");
         queue_->clear();
      }
      log_.info(me, "test ended successfully");
   }


   void testMaxEntries()
   {
      string me = ME + "::testMaxEntries";
      log_.info(me, "");
      log_.info(me, "this test checks that an excess of size in bytes really throws an exception");
      ClientQueueProperty prop(global_, "");
      ConnectQos connQos(global_);
      ConnectQueueEntry entry(global_, connQos);
      prop.setMaxBytes(10 * entry.getSizeInBytes());
      queue_ = QueueFactory::getFactory(global_).createQueue(prop);

      int i=0;
      try {
         for (i=0; i < 10; i++) {
            ConnectQueueEntry entry(global_, connQos);
            queue_->put(entry);
         }
         log_.info(me, "1. putting entries inside the queue: OK");      
      }
      catch (const XmlBlasterException &/*ex*/) {
         log_.error(me, "1. putting entries inside the queue: FAILED could not put inside the queue the entry nr. " + lexical_cast<string>(i));      
         assert(0);
      }
      try {
         ConnectQueueEntry entry(global_, connQos);
         queue_->put(entry);
         log_.error(me, "2. putting entries inside the queue: FAILED should have thrown an exception");      
         assert(0);
      }
      catch (const XmlBlasterException &ex) {
         assertEquals(log_, me, ex.getErrorCodeStr(), string("resource.overflow.queue.bytes"), "3. checking that exceeding number of entries throws the correct exception.");
      }
      log_.info(me, "test ended successfully");
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
   
}}} // namespace


using namespace org::xmlBlaster::test;

int main(int args, char *argc[]) 
{
   org::xmlBlaster::util::Object_Lifetime_Manager::init();
   Global& glob = Global::getInstance();
   glob.initialize(args, argc);

   TestQueue testObj = TestQueue(glob, "TestQueue");

   testObj.setUp();
   testObj.testPublishCompare();
   testObj.tearDown();

   testObj.setUp();
   testObj.testConnectCompare();
   testObj.setUp();
   testObj.tearDown();

   testObj.setUp();
   testObj.testMixedCompare();
   testObj.tearDown();

   testObj.setUp();
   testObj.testWithOneEntry();
   testObj.tearDown();

   testObj.setUp();
   testObj.testOrder();
   testObj.tearDown();

   testObj.setUp();
   testObj.testMaxMsg();
   testObj.tearDown();

   testObj.setUp();
   testObj.testMaxEntries();
   testObj.tearDown();

   org::xmlBlaster::util::Object_Lifetime_Manager::fini();
   return 0;
}


