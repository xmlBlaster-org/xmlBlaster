/*--------------------------------------------------------------------------
Name:      RamTest.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Load test for xmlBlaster
Version:   $Id: RamTest.cpp,v 1.9 2003/01/08 16:03:39 laghi Exp $
---------------------------------------------------------------------------*/

#include <string>
#include <boost/lexical_cast.hpp>
#include <client/XmlBlasterAccess.h>
#include <util/qos/ConnectQos.h>
#include <authentication/SecurityQos.h>
#include <util/XmlBlasterException.h>
#include <util/PlatformUtils.hpp>

#include <util/StopWatch.h>
#include <util/Global.h>

using namespace std;
using org::xmlBlaster::client::XmlBlasterAccess;
using org::xmlBlaster::util::qos::ConnectQos;
using org::xmlBlaster::util::XmlBlasterException;
using org::xmlBlaster::authentication::SecurityQos;

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
   util::StopWatch   stopWatch_;
   XmlBlasterAccess* senderConnection_;
   string            publishOid_;
   string            senderName_;
   string            senderContent_;
   string            contentMime_;
   string            contentMimeExtended_;
   util::Global&     global_;
   util::Log&        log_;

public:
   RamTest(Global& global, const string &loginName) :
                 stopWatch_(), global_(global), log_(global.getLog("test")) {
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
         senderConnection_ = new XmlBlasterAccess(global_); // Find server
         string passwd = "secret";
         SecurityQos secQos(global_, senderName_, passwd);
         ConnectQos connQos(global_);
         connQos.setSecurityQos(secQos);
         senderConnection_->connect(connQos, 0);
          // Connect to xmlBlaster without Callback
      }
      catch (XmlBlasterException &e) {
          log_.error(me(), e.toXml());
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

      for (string::size_type i=0; i < NUM_PUBLISH; i++) {
         EraseKey key(global_);
         key.setOid(string("RamTest-") + lexical_cast<string>(i+1));
         EraseQos qos(global_);
         vector<EraseReturnQos> strArr;
         try {
            strArr = senderConnection_->erase(key, qos);
            if (strArr.size() != 1) {
               log_.error(me(), "num erased messages is wrong");
               assert(0);
            }
         }
         catch(XmlBlasterException &e) {
            log_.error(me(), string("XmlBlasterException: ") + e.toXml());
         }
      }
      log_.info(me(), "Erased " + lexical_cast<string>(NUM_PUBLISH) + " messages");

      senderConnection_->disconnect(DisconnectQos(global_));
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
         PublishKey key(global_);
         key.setOid(string("RamTest-") + lexical_cast<string>(i+1));
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
         vector<util::MessageUnit> msgRetVec = senderConnection_->get(key, qos);
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
         vector<PublishReturnQos> publishOidArr = senderConnection_->publishArr(msgVec);
         double elapsed = 0.001 * stopWatch_.elapsed();

         for (unsigned int i=0; i < NUM_PUBLISH; i++) {
            cout << msgVec[i].getKey().toXml() << endl;
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
         msgRetVec = senderConnection_->get(key, qos);
         string usedMemAfter = msgRetVec[0].getContentStr();
         long usedAfter = lexical_cast<long>(usedMemAfter);
         log_.info(me(), string("xmlBlaster used allocated memory after ") +
                   "publishing = " + usedMemAfter);
         log_.info(me(), lexical_cast<string>((usedAfter-usedBefore)/NUM_PUBLISH) + " bytes/message");
      }
      catch(XmlBlasterException &e) {
         log_.warn(me(), string("Exception: ") + e.toXml());
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
      XmlBlasterAccess::usage();
      log_.usage();
      log_.plain(me(), "Example:");
      log_.plain(me(), "   RamTest -hostname myHostName");
      log_.plain(me(), "----------------------------------------------------------");
   }
};

}} // namespace

using org::xmlBlaster::util::Global;

int main(int args, char *argc[]) {
   // Init the XML platform
   try {
      XMLPlatformUtils::Initialize();
   }

   catch(const XMLException& toCatch) {
      cout << "Error during platform init! Message:\n"
           << endl;
      return 1;
   }

   Global& glob = Global::getInstance();
   glob.initialize(args, argc);
   org::xmlBlaster::RamTest *testSub = new org::xmlBlaster::RamTest(glob, "Tim");
   testSub->setUp(args, argc);
   testSub->testManyPublish();
   testSub->tearDown();
   return 0;
}


