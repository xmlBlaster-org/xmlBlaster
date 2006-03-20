/*--------------------------------------------------------------------------
Name:      TestRam.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Load test for xmlBlaster
Version:   $Id$
---------------------------------------------------------------------------*/
#include <util/XmlBCfg.h>
#include "TestSuite.h"
#include <util/StopWatch.h>
#include <iostream>

using namespace std;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::authentication;
using namespace org::xmlBlaster::client::key;
using namespace org::xmlBlaster::client::qos;


/**
 * This client publishes 1000 different messages to measure RAM
 * consumption/message. <br />
 * The RAM consumption in kByte/Message is logged to the console.  <br />
 * Note that this is the net RAM consumption, without any content and a very
 * small XmlKey. You may see this as the internal memory overhead in
 * xmlBlaster for each published message. <br />
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done. <p>
 */

namespace org { namespace xmlBlaster { namespace test {

/**
 * Constructs the TestRam object.
 * <p />
 * @param testName  The name used in the test suite
 * @param loginName The name to login to the xmlBlaster
 */

class TestRam : public TestSuite
{

private:

   static const string::size_type NUM_PUBLISH = 1000;
   StopWatch stopWatch_;
   string    publishOid_;
   string    senderName_;
   string    senderContent_;
   string    contentMime_;
   string    contentMimeExtended_;

public:
   TestRam(int args, char *argc[], const string &loginName) 
      :  TestSuite(args, argc, "TestRam"), stopWatch_()
   {
      senderName_   = loginName;
      publishOid_   = "";
      contentMime_  = "text/plain";
      contentMimeExtended_ = "1.0";
   }

   ~TestRam() {
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   void setUp() 
   {
      TestSuite::setUp();
      try {
         string passwd = "secret";
         SecurityQos secQos(global_, senderName_, passwd);
         ConnectQos connQos(global_);
         connQos.setSecurityQos(secQos);
         connection_.connect(connQos, 0);
          // Connect to xmlBlaster without Callback
      }
      catch (XmlBlasterException &e) {
          log_.error(ME, e.toXml());
          usage();
      }
   }


   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   void tearDown() 
   {
      log_.info(ME, "tearDown() ...");

      for (string::size_type i=0; i < NUM_PUBLISH; i++) {
         EraseKey key(global_);
         key.setOid(string("TestRam-") + lexical_cast<string>(i+1));
         EraseQos qos(global_);
         vector<EraseReturnQos> strArr;
         try {
            strArr = connection_.erase(key, qos);
            if (strArr.size() != 1) {
               log_.error(ME, "num erased messages is wrong");
               assert(0);
            }
         }
         catch(XmlBlasterException &e) {
            log_.error(ME, string("XmlBlasterException: ") + e.toXml());
         }
      }
      log_.info(ME, "Erased " + lexical_cast<string>(NUM_PUBLISH) + " topics");

      connection_.disconnect(DisconnectQos(global_));
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid is checked
    */
   void testPublish() 
   {
      if (log_.trace()) log_.trace(ME, "Publishing new topics ...");

      vector<util::MessageUnit> msgVec;
      msgVec.reserve(NUM_PUBLISH);

      for (string::size_type i=0; i < NUM_PUBLISH; i++) {
         PublishKey key(global_);
         key.setOid(string("TestRam-") + lexical_cast<string>(i+1));
         senderContent_ = lexical_cast<string>(i+1);
         PublishQos qos(global_);
         util::MessageUnit msgUnit(key, senderContent_, qos);
         msgVec.push_back(msgUnit);
      }

      try {
         // 1. Query the current memory allocated in xmlBlaster
         GetKey key(global_);
         key.setOid("__cmd:?usedMem");
         GetQos qos(global_);
         vector<util::MessageUnit> msgRetVec = connection_.get(key, qos);
         if (msgRetVec.size() != 1) {
            log_.error(ME, "msgRetVec.length!=1");
            assert(0);
         }
         if (msgRetVec[0].getContentLen() == 0) {
            log_.error(ME, "returned msgRetVec[0].msgUnit.content.length == 0");
            assert(0);
         }
         string usedMemBefore = msgRetVec[0].getContentStr();
         long usedBefore = lexical_cast<long>(usedMemBefore);
         log_.info(ME, string("xmlBlaster used allocated memory before ") +
                   "publishing = " + usedMemBefore);

         log_.info(ME, "Publishing " + lexical_cast<string>(NUM_PUBLISH) + " new topics ...");
         stopWatch_.restart();
         // 2. publish all the messages
         vector<PublishReturnQos> publishOidArr = connection_.publishArr(msgVec);
         double elapsed = 0.001 * stopWatch_.elapsed();

         for (unsigned int i=0; i < NUM_PUBLISH; i++) {
            cout << msgVec[i].getKey().toXml() << endl;
            //cout << msgVec[i].getContentStr() << endl;
         }

         long avg = (long)((double)NUM_PUBLISH / elapsed);
         log_.info(ME, "Success: Publishing done, " + lexical_cast<string>(NUM_PUBLISH) + " messages sent, average new topics/second = " + lexical_cast<string>(avg));

         if (publishOidArr.size() != NUM_PUBLISH) {
            log_.error(ME, "numPublished=" + lexical_cast<string>(publishOidArr.size()) + " is wrong");
            assert(0);
         }

         // 3. Query the memory allocated in xmlBlaster after publishing all
         // the messages
         msgRetVec = connection_.get(key, qos);
         string usedMemAfter = msgRetVec[0].getContentStr();
         long usedAfter = lexical_cast<long>(usedMemAfter);
         log_.info(ME, string("xmlBlaster used allocated memory after ") +
                   "publishing = " + usedMemAfter);
         log_.info(ME, lexical_cast<string>((usedAfter-usedBefore)/NUM_PUBLISH) + " bytes/topic");
      }
      catch(XmlBlasterException &e) {
         log_.warn(ME, string("Exception: ") + e.toXml());
         assert(0);
      }
   }


   /**
    * TEST: Construct 1000 messages and publish it.
    */
   void testManyPublish() 
   {
      testPublish();
   }

   void usage() const
   {
                TestSuite::usage();
      log_.plain(ME, "----------------------------------------------------------");
      log_.plain(ME, "Testing C++ access to xmlBlaster");
      log_.plain(ME, "Usage:");
      XmlBlasterAccess::usage();
      log_.usage();
      log_.plain(ME, "Example:");
      log_.plain(ME, "   TestRam -bootstrapHostname myHostName");
      log_.plain(ME, "----------------------------------------------------------");
   }
};

}}} // namespace

using namespace org::xmlBlaster::test;

int main(int args, char *argc[]) {
   org::xmlBlaster::util::Object_Lifetime_Manager::init();
   try {
      TestRam testObj(args, argc, "Tim");
      testObj.setUp();
      testObj.testManyPublish();
      testObj.tearDown();
   }
   catch (...) {
      std::cout << "ERROR: Caught exception!!!!" << endl;
   }
   org::xmlBlaster::util::Object_Lifetime_Manager::fini();
   return 0;
}


