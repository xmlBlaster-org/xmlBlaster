/*-----------------------------------------------------------------------------
Name:      TestGet.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing get()
-----------------------------------------------------------------------------*/

#include <client/XmlBlasterAccess.h>
#include <boost/lexical_cast.hpp>
#include <util/StopWatch.h>
#include <util/XmlBlasterException.h>
#include <util/PlatformUtils.hpp>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;
using boost::lexical_cast;
using org::xmlBlaster::authentication::SecurityQos;

/**
 * This client tests the synchronous method get() with its different qos
 * variants.<p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 */

namespace org { namespace xmlBlaster {

class TestGet {
private:
   string me() {
      return "Tim";
   }

//   CorbaConnection* corbaConnection_;
   XmlBlasterAccess* connection_;
   string           publishOid_;
   string           loginName_;
   string           senderContent_;
   string           contentMime_;
   string           contentMimeExtended_;
   int              numReceived_;  // error checking
   Global&          global_;
   util::Log&       log_;

   /**
    * Constructs the TestGet object.
    * <p />
    * @param loginName The name to login to the xmlBlaster
    */
public:
   TestGet(Global& global, const string &loginName)
      : global_(global), log_(global.getLog("test"))
   {
      loginName_           = loginName;
      publishOid_          = "TestGet";
      senderContent_       = "A test message";
      contentMime_         = "text/xml";
      contentMimeExtended_ = "1.0";
      numReceived_         = 0;
      connection_          = 0;
   }


   ~TestGet() {
      delete connection_;
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
         connection_ = new XmlBlasterAccess(global_); // Find orb
         string passwd = "secret";
         SecurityQos secQos(global_, loginName_, passwd);
         ConnectQos connQos(global_);
         connQos.setSecurityQos(secQos);
         connection_->connect(connQos, NULL);
         log_.info(me(), "Successful connection");
      }
      catch (XmlBlasterException &ex) {
         log_.error(me(), ex.toXml());
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
      eraseKey.setQueryType("EXACT");

      EraseQos eraseQos(global_);

      vector<EraseReturnQos> returnQosArr;
      try {
         returnQosArr = connection_->erase(eraseKey, eraseQos);
         log_.info(me(), "Success, erased a message");
      }
      catch(XmlBlasterException &e) {
         log_.error(me(), "XmlBlasterException: " + e.toXml());
      }
      if (returnQosArr.size() != 1) {
         log_.error(me(), "Erased " + lexical_cast<string>(returnQosArr.size()) + " messages");
      }
      // this is still old fashion ...
      connection_->disconnect(DisconnectQos(global_));
      // Give the server some millis to finish the iiop handshake ...
      util::StopWatch stopWatch;
      stopWatch.wait(200);
      log_.info(me(), "Success, logged out");
   }


   /**
    * TEST: Get an not existing and an existing message
    * <p />
    * The returned content is checked
    */
   void testGet() {
      if (log_.TRACE) log_.trace(me(), "1. Get a not existing message " + publishOid_ + " ...");
      try {
         GetKey getKey(global_);
	 getKey.setOid(publishOid_);
	 getKey.setQueryType("EXACT");
	 GetQos getQos(global_);
         vector<util::MessageUnit> msgVec = connection_->get(getKey, getQos);
         log_.info(me(), "Success, got array of size " + lexical_cast<string>(msgVec.size()) +
                         " for trying to get unknown message");
         assert(msgVec.size() == 0);
      }
      catch(XmlBlasterException &e) {
         log_.error(me(), "get of not existing message " + publishOid_);
         usage();
         assert(0);
      }

      if (log_.TRACE) log_.trace(me(), "2. Publish a message ...");

      try {
         PublishKey publishKey(global_);
	 publishKey.setOid(publishOid_);
	 publishKey.setContentMime("text/plain");

         PublishQos publishQos(global_);
         MessageUnit msgUnit(publishKey, senderContent_, publishQos);
         connection_->publish(msgUnit);
         log_.info(me(), "Success, published a message");
      }
      catch(XmlBlasterException &e) {
         log_.error(me(), "publish - XmlBlasterException: " + e.toXml());
         usage();
         assert(0);
      }

      if (log_.TRACE) log_.trace(me(), "3. Get an existing message ...");
      try {
         GetKey getKey(global_);
	 getKey.setOid(publishOid_);
	 getKey.setQueryType("EXACT");
	 GetQos getQos(global_);
         vector<MessageUnit> msgVec = connection_->get(getKey, getQos);
         log_.info(me(), "Success, got " + lexical_cast<string>(msgVec.size()) + " message");
         assert(msgVec.size() == 1);
         string str = msgVec[0].getContentStr();
         if (senderContent_ != str) {
            log_.error(me(), "Corrupted content expected '" + senderContent_ + "' size=" +
                             lexical_cast<string>(senderContent_.size()) + " but was '" + str +
                             "' size=" + lexical_cast<string>(str.size()) + " and contentLen=" +
                             lexical_cast<string>(msgVec[0].getContentLen()));
            usage();
            assert(0);
         }
      }
      catch(XmlBlasterException &e) {
         log_.error(me(), string("XmlBlasterException for trying to get ")
                    + "a message: " + e.toXml());
         usage();
         assert(0);
      }
   }


   /**
    * LOAD TEST: get 50 times a non-existing message
    */
   void testGetMany() {
      int num = 50;
      log_.info(me(), "Get " + lexical_cast<string>(num) + " not existing messages ...");
      GetKey getKey(global_);
      getKey.setOid("NotExistingMessage");
      getKey.setQueryType("EXACT");
      GetQos getQos(global_);
      for (int i=0; i < num; i++) {
         try {
            vector<MessageUnit> msgVec = connection_->get(getKey, getQos);
            assert(msgVec.size() == 0);
            log_.info(me(), string("Success"));
         }
         catch(XmlBlasterException &e) {
            log_.error(me(), "Exception for a not existing message: " + e.toXml());
            assert(0);
         }
      }
      log_.info(me(), "Get " + lexical_cast<string>(num) + " not existing messages done");
   }

   void usage()
   {
      log_.plain(me(), "----------------------------------------------------------");
      log_.plain(me(), "Testing C++/CORBA access to xmlBlaster with a synchronous get()");
      log_.plain(me(), "Usage:");
      XmlBlasterAccess::usage();
      log_.usage();
      log_.plain(me(), "Example:");
      log_.plain(me(), "   TestGet -hostname serverHost.myCompany.com");
      log_.plain(me(), "----------------------------------------------------------");
   }
};

}} // namespace



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
   org::xmlBlaster::TestGet *testSub = new org::xmlBlaster::TestGet(glob, "Tim");
   testSub->setUp(args, argc);
   testSub->testGetMany();
   testSub->testGet();
   testSub->tearDown();
//   log_.exit(TestGet.me(), "Good bye");
   return 0;
}

