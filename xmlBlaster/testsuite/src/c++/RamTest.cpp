/*--------------------------------------------------------------------------
Name:      RamTest.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Load test for xmlBlaster
Version:   $Id: RamTest.cpp,v 1.2 2002/11/26 12:40:14 ruff Exp $
---------------------------------------------------------------------------*/

#include <string>
#include <boost/lexical_cast.hpp>
#include <client/CorbaConnection.h>
#include <util/StopWatch.h>

using namespace std;
using boost::lexical_cast;

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

namespace org { namespace xmlBlaster {

/**
 * Constructs the RamTest object.
 * <p />
 * @param testName  The name used in the test suite
 * @param loginName The name to login to the xmlBlaster
 */

class RamTest {

private:

   string me() {
      return "Tim";
   }

   static const string::size_type NUM_PUBLISH = 1000;
   util::StopWatch  stopWatch_;
   CorbaConnection* senderConnection_;
   string           publishOid_;
   string           senderName_;
   string           senderContent_;
   string           contentMime_;
   string           contentMimeExtended_;
   util::Log log_;

public:
   RamTest(const string &loginName) : 
                 stopWatch_(), log_() {
      senderName_   = loginName;
      publishOid_   = "";
      contentMime_  = "text/plain";
      contentMimeExtended_ = "1.0";
      senderConnection_ = 0;
   }

   ~RamTest() {
      delete senderConnection_;
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   void setUp(int args=0, char *argc[]=0) {
      for (int ii=0; ii<args; ii++) {
         if (strcmp(argc[ii], "-?")==0 || strcmp(argc[ii], "-h")==0 || strcmp(argc[ii], "-help")==0) {
            usage();
            log_.exit(me(), "Good bye");
         }
      }
      try {
         senderConnection_ = new CorbaConnection(args, argc); // Find orb
         string passwd = "secret";
         senderConnection_->login(senderName_, passwd, 0);
          // Login to xmlBlaster without Callback
      }
      catch (CORBA::Exception &e) {
          log_.error(me(), to_string(e));
          usage();
      }
   }


   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   void tearDown() {
      log_.info(me(), "tearDown() ...");
//      stopWatch = new StopWatch();

      for (string::size_type i=0; i < NUM_PUBLISH; i++) {
         string xmlKey = "<key oid='RamTest-" + lexical_cast<string>(i+1) + "'/>";
         string qos = "<qos/>";
         vector<string> strArr;
         try {
            strArr = senderConnection_->erase(xmlKey, qos);
            if (strArr.size() != 1) {
               log_.error(me(), "num erased messages is wrong");
               assert(0);
            }
         }
         catch(serverIdl::XmlBlasterException &e) {
            log_.error(me(), string("XmlBlasterException: ")
                    + string(e.errorCodeStr) + ": " + string(e.message));
         }
      }
      log_.info(me(), "Erased " + lexical_cast<string>(NUM_PUBLISH) + " messages");

//        long avg = NUM_PUBLISH / (stopWatch.elapsed()/1000L);
//        Log.info(ME, "Success: Erasing done, " + NUM_PUBLISH + " messages erased, average messages/second = " + avg);

      senderConnection_->logout();
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid is checked
    */
   void testPublish() {
      if (log_.TRACE) log_.trace(me(), "Publishing messages ...");

      vector<util::MessageUnit> msgVec;
      msgVec.reserve(NUM_PUBLISH);

      for (string::size_type i=0; i < NUM_PUBLISH; i++) {
         string xmlKey = string("<key oid='RamTest-") + lexical_cast<string>(i+1) + "'></key>";
         senderContent_ = lexical_cast<string>(i+1);
         util::MessageUnit msgUnit(xmlKey, senderContent_, "<qos/>");
         msgVec.push_back(msgUnit);
      }

      try {
         // 1. Query the current memory allocated in xmlBlaster
         string xmlKey = "<key oid='__cmd:?usedMem' queryType='EXACT'/>";
         string qos    = "<qos></qos>";
         vector<util::MessageUnit> msgRetVec = senderConnection_->get(xmlKey, qos);
         if (msgRetVec.size() != 1) {
            log_.error(me(), "msgRetVec.length!=1");
            assert(0);
         }
         if (msgRetVec[0].getContentLen() == 0) {
            log_.error(me(), "returned msgRetVec[0].msgUnit.content.length == 0");
            assert(0);
         }
         string usedMemBefore = msgRetVec[0].getContentStr();
         long usedBefore = lexical_cast<long>(usedMemBefore);
         log_.info(me(), string("xmlBlaster used allocated memory before ") +
                   "publishing = " + usedMemBefore);

         log_.info(me(), "Publishing " + lexical_cast<string>(NUM_PUBLISH) + " messages ...");
         stopWatch_.restart();
         // 2. publish all the messages
         vector<string> publishOidArr = senderConnection_->publishArr(msgVec);
         double elapsed = 0.001 * stopWatch_.elapsed();

         for (unsigned int i=0; i < NUM_PUBLISH; i++) {
            cout << msgVec[i].getKey() << endl;
            //cout << msgVec[i].getContentStr() << endl;
         }

         long avg = (long)((double)NUM_PUBLISH / elapsed);
         log_.info(me(), "Success: Publishing done, " + lexical_cast<string>(NUM_PUBLISH) + " messages sent, average messages/second = " + lexical_cast<string>(avg));

         if (publishOidArr.size() != NUM_PUBLISH) {
            log_.error(me(), "numPublished is wrong");
            assert(0);
         }

         // 3. Query the memory allocated in xmlBlaster after publishing all
         // the messages
         msgRetVec = senderConnection_->get(xmlKey, qos);
         string usedMemAfter = msgRetVec[0].getContentStr();
         long usedAfter = lexical_cast<long>(usedMemAfter);
         log_.info(me(), string("xmlBlaster used allocated memory after ") +
                   "publishing = " + usedMemAfter);
         log_.info(me(), lexical_cast<string>((usedAfter-usedBefore)/NUM_PUBLISH) + " bytes/message");
      }

      catch(serverIdl::XmlBlasterException &e) {
         log_.warn(me(), string("XmlBlasterException: ") + string(e.errorCodeStr) + ": "+string(e.message));
         assert(0);
      }
      catch(CORBA::Exception &e) {
         log_.warn(me(), string("Exception: ") + to_string(e));
         assert(0);
      }
   }


   /**
    * TEST: Construct 1000 messages and publish it.
    */
   void testManyPublish() {
      testPublish();
   }

   void usage()
   {
      log_.plain(me(), "----------------------------------------------------------");
      log_.plain(me(), "Testing C++/CORBA access to xmlBlaster");
      log_.plain(me(), "Usage:");
      CorbaConnection::usage();
      log_.usage();
      log_.plain(me(), "Example:");
      log_.plain(me(), "   RamTest -hostname myHostName");
      log_.plain(me(), "----------------------------------------------------------");
   }
};

}} // namespace


int main(int args, char *argc[]) {
   org::xmlBlaster::RamTest *testSub = new org::xmlBlaster::RamTest("Tim");
   testSub->setUp(args, argc);
   testSub->testManyPublish();
   testSub->tearDown();
   return 0;
}


