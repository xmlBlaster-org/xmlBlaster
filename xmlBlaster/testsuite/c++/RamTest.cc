/*--------------------------------------------------------------------------
Name:      RamTest.cc
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Load test for xmlBlaster
Version:   $Id: RamTest.cc,v 1.7 2001/11/26 09:21:33 ruff Exp $
---------------------------------------------------------------------------*/

#include <string>
#include <strstream.h>
#include <client/CorbaConnection.h>
#include <util/StopWatch.h>

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

   const static string::size_type NUM_PUBLISH = 1000;
   util::StopWatch  stopWatch_;
   CorbaConnection* senderConnection_;
   string           publishOid_;
   string           senderName_;
   string           senderContent_;
   string           contentMime_;
   string           contentMimeExtended_;
   util::Log log_;

public:
   RamTest(const string &testName, const string &loginName) : 
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
      try {
         senderConnection_ = new CorbaConnection(args, argc); // Find orb
         string passwd = "secret";
         senderConnection_->login(senderName_, passwd, 0);
          // Login to xmlBlaster without Callback
      }
      catch (CORBA::Exception &e) {
          log_.error(me(), to_string(e));
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
         char buffer[256];
         ostrstream out(buffer, 255);
         out << "<key oid='RamTest-" << (i+1) << "'>\n" << "</key>" << (char)0;
         string xmlKey = buffer;
         string qos = "<qos></qos>";
         serverIdl::StringArr* strArr = 0;
         try {
            strArr = senderConnection_->erase(xmlKey, qos);
            if (!strArr) {
               cerr << "returned erased oid array == null" << endl;
               assert(0);
            }
            if (strArr->length() != 1) {
               cerr << "num erased messages is wrong" << endl;
               assert(0);
            }
         }
         catch(serverIdl::XmlBlasterException &e) {
            log_.error(me(), string("XmlBlasterException: ")
                       + string(e.reason));
         }
      }

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
      if (log_.TRACE) log_.trace(me(), "Publishing a message ...");

      serverIdl::MessageUnitArr msgUnitArr(NUM_PUBLISH);
      msgUnitArr.length(NUM_PUBLISH);

      char buffer[128];

      for (string::size_type i=0; i < NUM_PUBLISH; i++) {
         ostrstream out(buffer, 127);
         out << i+1 << (char)0;
//           string xmlKey = string("<key oid='RamTest-") + buffer
//          + "' contentMime='"
//          + contentMime_ + "' contentMimeExtended='" + contentMimeExtended_
//          + "'>\n   <RamTest-AGENT id='192.168.124.10' subId='1' "
//          + "type='generic'>      <RamTest-DRIVER id='FileProof' "
//          + "pollingFreq='10'>      </RamTest-DRIVER>"
//          + "   </RamTest-AGENT></key>";

         string xmlKey = string("<key oid='RamTest-") + buffer + "'></key>";
         senderContent_ = buffer;
         serverIdl::MessageUnit msgUnit;
         CORBA::String_var help = CORBA::string_dup(xmlKey.c_str());
         msgUnit.xmlKey  = help;
         msgUnit.xmlKey[xmlKey.length()] = (char)0;
         msgUnit.content = serverIdl::
            ContentType(senderContent_.length()+1,
                        senderContent_.length()+1,
                        (CORBA::Octet*)senderContent_.c_str());
         msgUnit.qos   = "<qos></qos>";
         msgUnitArr[i] = msgUnit;
      }

      try {
         // 1. Query the current memory allocated in xmlBlaster
         string xmlKey = "<key oid='__sys__UsedMem' queryType='EXACT'></key>";
         string qos    = "<qos></qos>";
         serverIdl::MessageUnitArr_var
            msgArr = senderConnection_->get(xmlKey, qos);
         if (!msgArr) {
            cerr << "returned msgArr == null" << endl;
            assert(0);
         }
         if (msgArr->length() != 1) {
            cerr << "msgArr.length!=1" << endl;
            assert(0);
         }
         serverIdl::MessageUnit msgUnit = (*msgArr)[0];


//       if (msgUnitCont.size() == 0) {
//          cerr << "returned msgArr[0].msgUnit == null" << endl;
//          assert(0);
//       }
         if (msgUnit.content.length() == 0) {
            cerr << "returned msgArr[0].msgUnit.content.length == 0" << endl;
            assert(0);
         }

         char *mem = (char*)&msgUnit.content[0];
         mem[msgUnit.content.length()] = (char)0; // null terminated string !!!
         log_.info(me(), string("xmlBlaster used allocated memory before ") +
                   "publishing = " + mem);

         stopWatch_.restart();
         // 2. publish all the messages
         serverIdl::StringArr_var publishOidArr =
            senderConnection_->publishArr(msgUnitArr);

         for (int i=0; i < 1000; i++) {
            cout << msgUnitArr[i].xmlKey << endl;
            cout << (char*)&(msgUnitArr[i].content)[0] << endl;
         }


         double elapsed = 0.001 * stopWatch_.elapsed();
         long avg = (long)((double)NUM_PUBLISH / elapsed);
         char buffer[1024];
         ostrstream out(buffer, 1023);
         out << "Success: Publishing done, " << NUM_PUBLISH;
         out << " messages sent, average messages/second = " << avg << (char)0;
         log_.info(me(), buffer);
         if (!publishOidArr) {
            cerr << "returned publishOidArr == null" << endl;
            assert(0);
         }

         if (publishOidArr->length() != NUM_PUBLISH) {
            cerr << "numPublished is wrong" << endl;
            assert(0);
         }

         // 3. Query the memory allocated in xmlBlaster after publishing all
         // the messages
         msgArr = senderConnection_->get(xmlKey, qos);
         char *usedMemAfter = (char*)&(*msgArr)[0].content[0];
         usedMemAfter[(*msgArr)[0].content.length()] = (char)0;
         log_.info(me(), string("xmlBlaster used allocated memory after ") +
                   "publishing = " + usedMemAfter);

      }

      catch(serverIdl::XmlBlasterException &e) {
         log_.warn(me(), string("XmlBlasterException: ")+string(e.reason));
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
};

}} // namespace


int main(int args, char *argc[]) {
   org::xmlBlaster::RamTest *testSub = new org::xmlBlaster::RamTest("RamTest", "Tim");
   testSub->setUp(args, argc);
   testSub->testManyPublish();
   testSub->tearDown();
   return 0;
}


