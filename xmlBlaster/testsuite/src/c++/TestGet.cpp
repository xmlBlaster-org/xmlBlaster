/*-----------------------------------------------------------------------------
Name:      TestGet.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing get()
-----------------------------------------------------------------------------*/

#include <string>
#include <boost/lexical_cast.hpp>
#include <util/Constants.h>
#include <util/StopWatch.h>
#include <client/protocol/corba/CorbaConnection.h>
#include <client/LoginQosWrapper.h>
#include <client/PublishQosWrapper.h>
#include <util/Global.h>

using namespace std;
using org::xmlBlaster::util::Global;
using boost::lexical_cast;
using org::xmlBlaster::client::protocol::corba::CorbaConnection;

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

   CorbaConnection* corbaConnection_;
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
      corbaConnection_     = 0;
   }


   ~TestGet() {
      delete corbaConnection_;
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
         corbaConnection_ = new CorbaConnection(global_); // Find orb
         string passwd = "secret";
         LoginQosWrapper qos = new LoginQosWrapper(); // == "<qos></qos>";
         corbaConnection_->login(loginName_, passwd, qos);
         log_.info(me(), "Successful login");
      }
      catch (serverIdl::XmlBlasterException &ex) {
         log_.error(me(), string(ex.errorCodeStr) + ": " + string(ex.message));
         usage();
         assert(0);
      }
      catch (CORBA::Exception &e) {
         log_.error(me(), to_string(e));
         usage();
         // e.printStackTrace();
         assert(0);
      }
   }


   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   void tearDown() {
      string xmlKey = "<key oid='" + publishOid_ + "' queryType='EXACT'>\n" +
         "</key>";
      string qos = "<qos></qos>";
      vector<string> strArr;
      try {
         strArr = corbaConnection_->erase(xmlKey, qos);
         log_.info(me(), "Success, erased a message");
      }
      catch(serverIdl::XmlBlasterException &e) {
         log_.error(me(), "XmlBlasterException: " + string(e.errorCodeStr) + ": " + string(e.message));
      }
      if (strArr.size() != 1) {
         log_.error(me(), "Erased " + lexical_cast<string>(strArr.size()) + " messages");
      }
      corbaConnection_->logout();
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
         string xmlKey = string("<key oid='") + publishOid_
            + "' queryType='EXACT'></key>";
         string qos = "<qos></qos>";
         vector<util::MessageUnit> msgVec = corbaConnection_->get(xmlKey, qos);
         log_.info(me(), "Success, got array of size " + lexical_cast<string>(msgVec.size()) +
                         " for trying to get unknown message");
         assert(msgVec.size() == 0);
      }
      catch(serverIdl::XmlBlasterException &e) {
         log_.error(me(), "get of not existing message " + publishOid_);
         usage();
         assert(0);
      }

      if (log_.TRACE) log_.trace(me(), "2. Publish a message ...");

      try {
         string xmlKey = string("<key oid='") + publishOid_
            + "' contentMime='text/plain'>\n</key>";
         serverIdl::MessageUnit msgUnit;
         msgUnit.xmlKey  = xmlKey.c_str();
         serverIdl::ContentType content(senderContent_.length(),
                                        senderContent_.length(),
                                        (CORBA::Octet*)senderContent_.c_str());
         msgUnit.content = content;

         PublishQosWrapper qosWrapper = new PublishQosWrapper();
         // the same as "<qos></qos>"
         msgUnit.qos = qosWrapper.toXml().c_str();
         corbaConnection_->publish(msgUnit);
         log_.info(me(), "Success, published a message");
      }
      catch(serverIdl::XmlBlasterException &e) {
         log_.error(me(), "publish - XmlBlasterException: " + string(e.errorCodeStr) + ": " + string(e.message));
         usage();
         assert(0);
      }

      if (log_.TRACE) log_.trace(me(), "3. Get an existing message ...");
      try {
         string xmlKey = string("<key oid='") + publishOid_
            + "' queryType='EXACT'></key>";
         string qos = "<qos></qos>";
         vector<util::MessageUnit> msgVec = corbaConnection_->get(xmlKey, qos);
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
      catch(serverIdl::XmlBlasterException &e) {
         log_.error(me(), string("XmlBlasterException for trying to get ")
                    + "a message: " + string(e.errorCodeStr) + ": "+ string(e.message));
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
      string xmlKey = "<key oid='NotExistingMessage' queryType='EXACT'></key>";
      string qos    = "<qos></qos>";
      for (int i=0; i < num; i++) {
         try {
            vector<util::MessageUnit> msgVec = corbaConnection_->get(xmlKey, qos);
            assert(msgVec.size() == 0);
            log_.info(me(), string("Success"));
         }
         catch(serverIdl::XmlBlasterException &e) {
            log_.error(me(), "Exception for a not existing message: " + string(e.errorCodeStr) + ": "+ string(e.message));
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
      CorbaConnection::usage();
      log_.usage();
      log_.plain(me(), "Example:");
      log_.plain(me(), "   TestGet -hostname serverHost.myCompany.com");
      log_.plain(me(), "----------------------------------------------------------");
   }
};

}} // namespace



int main(int args, char *argc[]) {

   Global& glob = Global::getInstance();
   org::xmlBlaster::TestGet *testSub = new org::xmlBlaster::TestGet(glob, "Tim");
   testSub->setUp(args, argc);
   testSub->testGetMany();
   testSub->testGet();
   testSub->tearDown();
//   log_.exit(TestGet.me(), "Good bye");
   return 0;
}

