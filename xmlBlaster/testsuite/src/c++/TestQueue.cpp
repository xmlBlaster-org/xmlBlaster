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

namespace org { namespace xmlBlaster { namespace test {

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::qos::storage;
using namespace org::xmlBlaster::util::queue;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

/**
 * Tests the queue entry and queue functionality. 
 * The following is tested here:
 * - PublishQueueEntry comparison operators
 * - ConnectQueueEntry comparison operators
 * - Intermixed comparisons (between PublishQueueEntry and ConnectQueueEntry).
 * - Queue access and overflow
 */
class TestQueue
{
   
private:
   string    ME;
   Global&   global_;
   I_Log&    log_;
   I_Queue* queue_;

public:
   /** The values for "-queue/connection/type"; */
   std::vector<string> types;

public:
   TestQueue(Global& global, string name) : ME(name), global_(global), log_(global.getLog("test"))
   {
      queue_ = NULL;
      types.push_back("RAM");
      types.push_back("SQLite");
      types.push_back("CACHE");
   }

   virtual ~TestQueue() { }

   void destroyQueue() {
      ClientQueueProperty prop(global_, "");
      I_Queue *queue = &QueueFactory::getFactory().getPlugin(global_, prop);
      queue->destroy();
      QueueFactory::getFactory().releasePlugin(queue);
   }

   void testPublishCompare() 
   {
      string me = ME + "::testPublishCompare";
      log_.info(me, "");
      log_.info(me, "comparison test between PublishQueueEntry objects.");

      PublishKey pubKey(global_);
      PublishQos pubQos(global_);
      MessageUnit msgUnit(pubKey, string("comparison test"), pubQos);
      PublishQueueEntry entry1(global_, msgUnit);
      PublishQueueEntry entry2(global_, msgUnit);
      PublishQueueEntry entry3(global_, msgUnit, 2);
      PublishQueueEntry entry4(global_, msgUnit, 3);
      PublishQueueEntry entry5(global_, msgUnit, 1);

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

      ConnectQos *connectQos = new ConnectQos(global_);
      ConnectQueueEntry entry1(global_, connectQos);
      ConnectQueueEntry entry2(global_, connectQos);
      ConnectQueueEntry entry3(global_, connectQos, 2);
      ConnectQueueEntry entry4(global_, connectQos, 3);
      ConnectQueueEntry entry5(global_, connectQos, 1);

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
      ConnectQos *connectQos = new ConnectQos(global_);

      PublishQueueEntry entry1(global_, msgUnit, 2);
      ConnectQueueEntry entry2(global_, connectQos, 3);
      PublishQueueEntry entry3(global_, msgUnit, 1);

      ConnectQueueEntry entry4(global_, connectQos, 2);
      PublishQueueEntry entry5(global_, msgUnit, 3);
      ConnectQueueEntry entry6(global_, connectQos, 1);

      assertEquals(log_, me, true, entry1 < entry2, "1. Mixed compare 1 with 2");
      assertEquals(log_, me, true, entry3 < entry2, "2. Mixed compare 3 with 2");

      assertEquals(log_, me, true, entry4 < entry5, "3. Mixed compare 4 with 5");
      assertEquals(log_, me, true, entry6 < entry5, "4. Mixed compare 6 with 5");

      log_.info(me, "test completed successfully");
   }


   void testWithOnePublishEntry()
   {
      string me = ME + "::testWithOnePublishEntry";
      log_.info(me, "");
      log_.info(me, "this test creates a queue. The following checks are done:");
      ClientQueueProperty prop(global_, "");
      queue_ = &QueueFactory::getFactory().getPlugin(global_, prop);
      assertEquals(log_, me, true, queue_->empty(), "The queue must be empty after creation");
      assertEquals(log_, me, 0, queue_->getNumOfEntries(), "The queue must be empty after creation");
      PublishQos qos(global_);
      PublishKey key(global_);
      const string contentStr = "BlaBla";
      MessageUnit messageUnit(key, contentStr, qos);
      PublishQueueEntry entry(global_, messageUnit, messageUnit.getQos().getPriority());
      std::cout << "Putting " << entry.getUniqueId() << std::endl;

      queue_->put(entry);
      assertEquals(log_, me, false, queue_->empty(), " 2. the queue must contain entries after invoking put one time");
      assertEquals(log_, me, 1, queue_->getNumOfEntries(), " 2b. the queue must contain one entry after invoking put one time");
      
      vector<EntryType> ret = queue_->peekWithSamePriority();
      assertEquals(log_, me, (size_t)1, ret.size(), " 3. the number of entries peeked after one put must be 1");
      {
         const MsgQueueEntry &e = *ret[0];
         std::cout << "Peeking " << e.getUniqueId() << std::endl;
         assertEquals(log_, me, entry.getUniqueId(),  e.getUniqueId(), " 3. the uniqueId must be same");
         assertEquals(log_, me, entry.getPriority(),  e.getPriority(), " 3. the priority must be same");
      }
      long numDel = queue_->randomRemove(ret.begin(), ret.end());
      assertEquals(log_, me, (long)1, numDel, " 4. randomRemove must return 1 entry deleted");
      assertEquals(log_, me, true, queue_->empty(), " 5. after removing all entries (it was only 1 entry) the queue  must be empty");
      log_.info(me, "ends here. Test was successful.");
   }


   void testWithOneConnectEntry()
   {
      string me = ME + "::testWithOneEntry";
      log_.info(me, "");
      log_.info(me, "this test creates a queue. The following checks are done:");
      ClientQueueProperty prop(global_, "");
      queue_ = &QueueFactory::getFactory().getPlugin(global_, prop);
      assertEquals(log_, me, true, queue_->empty(), " 1. the queue must be empty after creation");
      ConnectQos *connQos = new ConnectQos(global_);
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
      queue_ = &QueueFactory::getFactory().getPlugin(global_, prop);
      ConnectQos *connQos = new ConnectQos(global_);

      ConnectQueueEntry e1(global_, ConnectQosRef(new ConnectQos(global_)), 1);
      e1.getConnectQos()->addClientProperty("X", 7);
      queue_->put(e1);

      ConnectQueueEntry e2(global_, ConnectQosRef(new ConnectQos(global_)), 5);  // NORM_PRIORITY
      e2.getConnectQos()->addClientProperty("X", 4);
      queue_->put(e2);

      ConnectQueueEntry e3(global_, ConnectQosRef(new ConnectQos(global_)), 7);
      e3.getConnectQos()->addClientProperty("X", 1);
      queue_->put(e3);

      ConnectQueueEntry e4(global_, ConnectQosRef(new ConnectQos(global_)), 7);
      e4.getConnectQos()->addClientProperty("X", 2);
      queue_->put(e4);

      ConnectQueueEntry e5(global_, ConnectQosRef(new ConnectQos(global_)), 1);  // MIN1_PRIORITY
      e5.getConnectQos()->addClientProperty("X", 8);
      queue_->put(e5);

      ConnectQueueEntry e6(global_, ConnectQosRef(new ConnectQos(global_)), 5);
      e6.getConnectQos()->addClientProperty("X", 5);
      queue_->put(e6);

      ConnectQueueEntry e7(global_, ConnectQosRef(new ConnectQos(global_)), 5);
      e7.getConnectQos()->addClientProperty("X", 6);
      queue_->put(e7);

      ConnectQueueEntry e8(global_, ConnectQosRef(new ConnectQos(global_)), 7);
      e8.getConnectQos()->addClientProperty("X", 3);
      queue_->put(e8);

      ConnectQueueEntry e9(global_, connQos, 1);
      e9.getConnectQos()->addClientProperty("X", 9);    // MAX_PRIORITY
      queue_->put(e9);

      vector<EntryType> ret = queue_->peekWithSamePriority();
      // should be 3 entries with priority 7 
      assertEquals(log_, me, (size_t)3, ret.size(), "1. number of priority 7 msg peeked must be correct.");

      const MsgQueueEntry &entry = *ret[0];
      // TODO:
      // [cc] \xmlBlaster\testsuite\src\c++\TestQueue.cpp(245) : warning C4541:
      // 'dynamic_cast' used on polymorphic type 'org::xmlBlaster::util::queue::MsgQueueEntry' with /GR-;
      // unpredictable behavior may result
      //cout << "Trying dynamic cast" << endl;   // On _WINDOWS: /GR  to enable C++ RTTI didn't help (see build.xml)
      const ConnectQueueEntry *connectQueueEntry = dynamic_cast<const ConnectQueueEntry*>(&entry);
      assertEquals(log_, me, 1, connectQueueEntry->getConnectQos()->getClientProperty("X", -1), "2. checking the first entry.");
      assertEquals(log_, me, 2, dynamic_cast<const ConnectQueueEntry*>(&(*ret[1]))->getConnectQos()->getClientProperty("X", -1), "3. checking the second entry.");
      assertEquals(log_, me, 3, dynamic_cast<const ConnectQueueEntry*>(&(*ret[2]))->getConnectQos()->getClientProperty("X", -1), "4. checking the third entry.");

      assertEquals(log_, me, false, queue_->empty(), "5. there should still be entries in the queue.");
      queue_->randomRemove(ret.begin(), ret.end());
      ret = queue_->peekWithSamePriority();
      assertEquals(log_, me, (size_t)3, ret.size(), "6. number of priority 7 msg peeked must be correct.");
      assertEquals(log_, me, 4, dynamic_cast<const ConnectQueueEntry*>(&(*ret[0]))->getConnectQos()->getClientProperty("X", -1), "7. checking the first entry.");
      assertEquals(log_, me, 5, dynamic_cast<const ConnectQueueEntry*>(&(*ret[1]))->getConnectQos()->getClientProperty("X", -1), "8. checking the second entry.");
      assertEquals(log_, me, 6, dynamic_cast<const ConnectQueueEntry*>(&(*ret[2]))->getConnectQos()->getClientProperty("X", -1), "9. checking the third entry.");
            
      queue_->randomRemove(ret.begin(), ret.end());
      ret = queue_->peekWithSamePriority();
      assertEquals(log_, me, (size_t)3, ret.size(), "10. number of priority 7 msg peeked must be correct.");
      assertEquals(log_, me, 7, dynamic_cast<const ConnectQueueEntry*>(&(*ret[0]))->getConnectQos()->getClientProperty("X", -1), "11. checking the first entry.");
      assertEquals(log_, me, 8, dynamic_cast<const ConnectQueueEntry*>(&(*ret[1]))->getConnectQos()->getClientProperty("X", -1), "12. checking the second entry.");
      assertEquals(log_, me, 9, dynamic_cast<const ConnectQueueEntry*>(&(*ret[2]))->getConnectQos()->getClientProperty("X", -1), "13. checking the third entry.");
      queue_->randomRemove(ret.begin(), ret.end());
      assertEquals(log_, me, true, queue_->empty(), "14. the queue should be empty now.");
      log_.info(me, "test ended successfully");
   }


   void testMaxNumOfEntries()
   {
      string me = ME + "::testMaxNumOfEntries";
      log_.info(me, "");
      log_.info(me, "this test checks that an excess of entries really throws an exception");
      ClientQueueProperty prop(global_, "");
      prop.setMaxEntries(10);
      queue_ = &QueueFactory::getFactory().getPlugin(global_, prop);
      ConnectQosRef connQos = new ConnectQos(global_);
      connQos->setPersistent(false);
      int i=0;
      try {
         for (i=0; i < 10; i++) {
            if (i == 5) connQos->setPersistent(true);
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


   void testMaxNumOfBytes()
   {
      string me = ME + "::testMaxNumOfBytes";
      log_.info(me, "");
      log_.info(me, "this test checks that an excess of size in bytes really throws an exception");
      ClientQueueProperty prop(global_, "");
      ConnectQos *connQos = new ConnectQos(global_);
      ConnectQueueEntry entry(global_, connQos);
      size_t maxBytes = 10 * entry.getSizeInBytes();
      prop.setMaxBytes(maxBytes);
      queue_ = &QueueFactory::getFactory().getPlugin(global_, prop);

      assertEquals(log_, me, maxBytes, (int)queue_->getMaxNumOfBytes(), "Setting maxNumOfBytes");

      int i=0;
      try {
         for (i=0; i < 10; i++) {
            ConnectQueueEntry ent(global_, connQos);
            log_.trace(me, "Putting entry " + lexical_cast<string>(i) + " to queue, size=" + lexical_cast<string>(ent.getSizeInBytes()));
            queue_->put(ent);
         }
         log_.info(me, "1. putting entries inside the queue: OK");      
      }
      catch (const XmlBlasterException &/*ex*/) {
         log_.error(me, "1. putting entries inside the queue: FAILED could not put inside the queue the entry no. " + lexical_cast<string>(i) +
                        /*", entryBytes=" + lexical_cast<string>(entry->getNumOfBytes()) +*/
                        ", numOfEntries=" + lexical_cast<string>(queue_->getNumOfEntries()) +
                        ", numOfBytes=" + lexical_cast<string>(queue_->getNumOfBytes()) +
                      " maxNumOfBytes=" + lexical_cast<string>(queue_->getMaxNumOfBytes()));
         assert(0);
      }
      try {
         ConnectQueueEntry ent(global_, connQos);
         queue_->put(ent);
         log_.error(me, string("2. putting entries inside the queue: FAILED should have thrown an exception currQueueByte=") + 
                      lexical_cast<string>(queue_->getNumOfBytes()) +
                      " maxNumOfBytes=" + lexical_cast<string>(queue_->getMaxNumOfBytes()));
         assert(0);
      }
      catch (const XmlBlasterException &ex) {
         assertEquals(log_, me, ex.getErrorCodeStr(), string("resource.overflow.queue.bytes"),
                      string("3. checking that exceeding number of entries throws the correct exception. numOfBytes=") + 
                      lexical_cast<string>(queue_->getNumOfBytes()) +
                      " maxNumOfBytes=" + lexical_cast<string>(queue_->getMaxNumOfBytes()));
      }
      log_.info(me, "test ended successfully");
   }

   void setUp() 
   {
      destroyQueue(); // Destroy old queue
   }

   void tearDown() {
      if (queue_) {
         QueueFactory::getFactory().releasePlugin(queue_);
         queue_ = NULL;
      }
   }
};
   
}}} // namespace


using namespace org::xmlBlaster::test;

/** Compile:  build -DexeName=TestQueue cpp-test-single */
int main(int args, char *argc[]) 
{
   org::xmlBlaster::util::Object_Lifetime_Manager::init();

   try {
      Global& glob = Global::getInstance();
      glob.initialize(args, argc);

      TestQueue testObj = TestQueue(glob, "TestQueue");

      for (std::vector<string>::size_type i=0; i < testObj.types.size(); i++) {
         glob.getProperty().setProperty("queue/connection/type", testObj.types[i], true);
         std::cout << "Testing queue type '" << glob.getProperty().get("queue/connection/type", string("eRRoR")) << "'" << std::endl;

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
         testObj.testWithOnePublishEntry();
         testObj.tearDown();

         testObj.setUp();
         testObj.testWithOneConnectEntry();
         testObj.tearDown();

         testObj.setUp();
         testObj.testOrder();
         testObj.tearDown();

         testObj.setUp();
         testObj.testMaxNumOfEntries();
         testObj.tearDown();

         testObj.setUp();
         testObj.testMaxNumOfBytes();
         testObj.tearDown();
      }
   }
   catch (const XmlBlasterException &e) {
      std::cerr << "TestQueue FAILED: " << e.getMessage() << std::endl;
      assert(0);

   }

   org::xmlBlaster::util::Object_Lifetime_Manager::fini();
   return 0;
}


