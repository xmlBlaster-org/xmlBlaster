/*-----------------------------------------------------------------------------
Name:      TestGet.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing get()
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
using org::xmlBlaster::authentication::SecurityQos;

/**
 * This client tests the synchronous method get() with its different qos
 * variants.<p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 */

namespace org { namespace xmlBlaster { namespace test {

class TestGet : public TestSuite
{

private:
   string publishOid_;
   string loginName_;
   string senderContent_;
   string contentMime_;
   string contentMimeExtended_;
   int    numReceived_;  // error checking

   /**
    * Constructs the TestGet object.
    * <p />
    * @param loginName The name to login to the xmlBlaster
    */
public:
   TestGet(int args, char *argc[], const string &loginName)
      : TestSuite(args, argc, "TestGet")
   {
      loginName_           = loginName;
      publishOid_          = "TestGet";
      senderContent_       = "A test message";
      contentMime_         = "text/xml";
      contentMimeExtended_ = "1.0";
      numReceived_         = 0;
   }

   ~TestGet() 
   {
   }

   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   void setUp() 
   {
      log_.info(ME, "Trying to connect to xmlBlaster with C++ client lib " + Global::getVersion() + " from " + Global::getBuildTimestamp());
      TestSuite::setUp();
      try {
         string passwd = "secret";
         SecurityQos secQos(global_, loginName_, passwd);
         ConnectQos connQos(global_);
         connQos.setSecurityQos(secQos);
         connection_.connect(connQos, NULL);
         log_.info(ME, "Successful connection");
      }
      catch (XmlBlasterException &ex) {
         log_.error(ME, ex.toXml());
         usage();
         assert(0);
      }
   }


   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   void tearDown() {
      EraseKey eraseKey(global_);
      eraseKey.setOid(publishOid_);

      EraseQos eraseQos(global_);

      vector<EraseReturnQos> returnQosArr;
      try {
         returnQosArr = connection_.erase(eraseKey, eraseQos);
         log_.info(ME, "Success, erased a message");
      }
      catch(XmlBlasterException &e) {
         log_.error(ME, "XmlBlasterException: " + e.toXml());
      }
      if (returnQosArr.size() != 1) {
         log_.error(ME, "Erased " + lexical_cast<string>(returnQosArr.size()) + " messages");
      }
      // this is still old fashion ...
      connection_.disconnect(DisconnectQos(global_));
      // Give the server some millis to finish the iiop handshake ...
      Thread::sleep(200);
      log_.info(ME, "Success, logged out");

      TestSuite::tearDown();
   }


   /**
    * TEST: Get an not existing and an existing message
    * <p />
    * The returned content is checked
    */
   void testGet() 
   {
      if (log_.trace()) log_.trace(ME, "1. Get a not existing message " + publishOid_ + " ...");
      try {
         GetKey getKey(global_);
         getKey.setOid(publishOid_);
         GetQos getQos(global_);
         vector<util::MessageUnit> msgVec = connection_.get(getKey, getQos);
         log_.info(ME, "Success, got array of size " + lexical_cast<string>(msgVec.size()) +
                         " for trying to get unknown message");
         assert(msgVec.size() == 0);
      }
      catch(XmlBlasterException &e) {
         log_.error(ME, "get of not existing message " + publishOid_ + ": " + e.getMessage());
         usage();
         assert(0);
      }

      if (log_.trace()) log_.trace(ME, "2. Publish a message ...");

      try {
         PublishKey publishKey(global_);
         publishKey.setOid(publishOid_);
         publishKey.setContentMime("text/plain");

         PublishQos publishQos(global_);
         MessageUnit msgUnit(publishKey, senderContent_, publishQos);
         connection_.publish(msgUnit);
         log_.info(ME, "Success, published a message");
      }
      catch(XmlBlasterException &e) {
         log_.error(ME, "publish - XmlBlasterException: " + e.toXml());
         usage();
         assert(0);
      }

      if (log_.trace()) log_.trace(ME, "3. Get an existing message ...");
      try {
         GetKey getKey(global_);
         getKey.setOid(publishOid_);
         GetQos getQos(global_);
         vector<MessageUnit> msgVec = connection_.get(getKey, getQos);
         log_.info(ME, "Success, got " + lexical_cast<string>(msgVec.size()) + " message");
         assert(msgVec.size() == 1);
         string str = msgVec[0].getContentStr();
         if (senderContent_ != str) {
            log_.error(ME, "Corrupted content expected '" + senderContent_ + "' size=" +
                             lexical_cast<string>(senderContent_.size()) + " but was '" + str +
                             "' size=" + lexical_cast<string>(str.size()) + " and contentLen=" +
                             lexical_cast<string>(msgVec[0].getContentLen()));
            usage();
            assert(0);
         }
      }
      catch(XmlBlasterException &e) {
         log_.error(ME, string("XmlBlasterException for trying to get ")
                    + "a message: " + e.toXml());
         usage();
         assert(0);
      }
   }


   /**
    * LOAD TEST: get 50 times a non-existing message
    */
   void testMany() 
   {
      int num = 50;
      log_.info(ME, "Get " + lexical_cast<string>(num) + " not existing messages ...");
      GetKey getKey(global_);
      getKey.setOid("NotExistingMessage");
      GetQos getQos(global_);
      for (int i=0; i < num; i++) {
         try {
            vector<MessageUnit> msgVec = connection_.get(getKey, getQos);
            assert(msgVec.size() == 0);
            log_.info(ME, string("Success"));
         }
         catch(XmlBlasterException &e) {
            log_.error(ME, "Exception for a not existing message: " + e.toXml());
            assert(0);
         }
      }
      log_.info(ME, "Get " + lexical_cast<string>(num) + " not existing messages done");
   }

   void usage() const
   {
                TestSuite::usage();
      log_.plain(ME, "----------------------------------------------------------");
      log_.plain(ME, "Testing C++/CORBA access to xmlBlaster with a synchronous get()");
      log_.plain(ME, "Usage:");
      XmlBlasterAccess::usage();
      log_.usage();
      log_.plain(ME, "Example:");
      log_.plain(ME, "   TestGet -bootstrapHostname serverHost.myCompany.com");
      log_.plain(ME, "----------------------------------------------------------");
   }
};

}}} // namespace

using namespace org::xmlBlaster::test;

  
int main(int args, char *argc[]) 
{
   int ret = -1;
   try {
      org::xmlBlaster::util::Object_Lifetime_Manager::init();
      TestGet *testObj = new TestGet(args, argc, "Tim");
      testObj->setUp();
      testObj->testMany();
      testObj->testGet();
      testObj->tearDown();
      delete testObj;
      testObj = NULL;
      ret = 0;
   }
   catch (XmlBlasterException& err) {
      cout << "exception occurred in main string = " << err.getMessage() << endl;
   }
   catch (const exception& err) {
      cout << "exception occurred in main string = " << err.what() << endl;
   }
   catch (const string& err) {
      cout << "exception occurred in main string = " << err << endl;
   }
   catch (const char* err) {
      cout << "exception occurred in main char* = " << err << endl;
   }
   catch (...) {
      cout << "exception occurred in main"<< endl;
   }
   org::xmlBlaster::util::Object_Lifetime_Manager::fini();
   return ret;
}

