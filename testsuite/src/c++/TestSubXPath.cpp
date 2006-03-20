/*-----------------------------------------------------------------------------
Name:      TestSubXPath.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the Timeout Features
-----------------------------------------------------------------------------*/
#include "TestSuite.h"
#include <iostream>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;
using namespace org::xmlBlaster;

namespace org { namespace xmlBlaster { namespace test {

class TestSubXPath : public TestSuite, public virtual I_Callback
{
private:
   Mutex             updateMutex_;
   int               numOfUpdates_;

   void subscribeXPath(const string& query) 
   {
      if (log_.trace()) log_.trace(ME, "Subscribing using XPath syntax ...");
      SubscribeKey subKey(global_);
      subKey.setQueryString(query);
      SubscribeQos qos(global_);
      string subscribeOid = "";
      try {
         subscribeOid = connection_.subscribe(subKey, qos).getSubscriptionId();
         log_.info(ME, string("Success: Subscribe on ") + subscribeOid + " done:\n" + subKey.toXml());
      } catch(XmlBlasterException& e) {
         log_.warn(ME, string("XmlBlasterException: ") + e.toXml());
         assertEquals(log_, ME, true, false, string("subscribe - XmlBlasterException: ") + e.toXml());
      }
      assertEquals(log_, ME, false, subscribeOid.empty(), "returned emty subscribeOid");
   }


public:
   TestSubXPath(int args, char *argc[]) 
      :  TestSuite(args, argc, "TestSubXPath"), updateMutex_()
   {
      numOfUpdates_ = 0;
   }

   void tearDown()
   {
      TestSuite::tearDown();
   }

   virtual ~TestSubXPath()
   {
   }

   void setUp()
   {
      TestSuite::setUp();
      try {   
//         ConnectQos connQos(global_, "Tim", "secret");
         ConnectQos connQos(global_);
         log_.info(ME, string("connecting to xmlBlaster. Connect qos: ") + connQos.toXml());

         ConnectReturnQos retQos = connection_.connect(connQos, this);
         log_.info(ME, "successfully connected to xmlBlaster. Return qos: " + retQos.toXml());

      }
      catch (XmlBlasterException& ex) {
         log_.error(ME, string("exception occurred in setUp. ") + ex.toXml());
         assert(0);
      }

   }



   /**
    * TEST: Construct 5 messages and publish them,<br />
    * the previous XPath subscription should match message #3 and send an update.
    */
   void testInitial()  
   {
      ME = "TestSubXPath:testInitial()";
      string oid = "INITIAL";
      subscribeXPath("//demo");
      Thread::sleep(1000);

      try {
         PublishKey pk(global_, oid, "text/xml", "1.0");
         pk.setClientTags("<org.xmlBlaster><demo/></org.xmlBlaster>");
         PublishQos pq(global_);
         MessageUnit msgUnit(pk, "Hi", pq);
         PublishReturnQos tmp = connection_.publish(msgUnit);
         if (oid != tmp.getKeyOid()) {
            log_.error(ME, string("wrong oid. It should be '") + oid + "' but is '" + tmp.getKeyOid());
            assert(0);
         }
         Thread::sleep(3000);                                   
         assertEquals(log_, ME, 1, numOfUpdates_, "checking number of updates");
      }
      catch (XmlBlasterException& e) {
         log_.error(ME, e.getMessage());
         assert(0);
      }

      try {
         EraseKey key(global_);
         key.setOid(oid);
         EraseQos qos(global_);
         vector<EraseReturnQos> arr = connection_.erase(key, qos);
         assertEquals(log_, ME, (size_t)1, arr.size(), "Erase");
      } 
      catch(XmlBlasterException& e) { 
                        log_.error(ME, "Erase problem: " + e.getMessage());
         assert(0);
      }
   }

   string update(const string&, UpdateKey&, const unsigned char*, long, UpdateQos&)
   {
      Lock lock(updateMutex_);
      log_.info(ME, "update invoked");
      numOfUpdates_++;
      return "";
   }

};

}}} // namespaces

using namespace org::xmlBlaster::test;

/**
 * Try
 * <pre>
 *   java TestSubXPath -help
 * </pre>
 * for usage help
 */
int main(int args, char ** argv)
{
   try {
      org::xmlBlaster::util::Object_Lifetime_Manager::init();
      TestSubXPath *testSubXpath = new TestSubXPath(args, argv);
      testSubXpath->setUp();
      testSubXpath->testInitial();
      testSubXpath->tearDown();
      Thread::sleepSecs(1);
      delete testSubXpath;
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
