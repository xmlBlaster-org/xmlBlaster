/*-----------------------------------------------------------------------------
Name:      TestGet.cc
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing publish()
Version:   $Id: TestGet.cc,v 1.7 2001/11/26 09:21:33 ruff Exp $
-----------------------------------------------------------------------------*/

#include <strstream.h>
#include <string>
#include <util/StopWatch.h>
#include <client/CorbaConnection.h>
#include <client/LoginQosWrapper.h>
#include <client/PublishQosWrapper.h>

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

   util::Log       log_;
   CorbaConnection *corbaConnection_;
   string publishOid_;
   string loginName_;
   string senderContent_;
   string contentMime_;
   string contentMimeExtended_;
   int    numReceived_;  // error checking

   /**
    * Constructs the TestGet object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
public:
   TestGet(const string &testName, const string &loginName) {
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
      try {
         cerr << "BEFORE CONSTRUCTOR" << endl;
         corbaConnection_ = new CorbaConnection(args, argc); // Find orb
         string passwd = "secret";
         LoginQosWrapper qos = new LoginQosWrapper(); // == "<qos></qos>";
         cerr << "BEFORE LOGIN" << endl;
         corbaConnection_->login(loginName_, passwd, qos);
         cerr << "AFTER LOGIN" << endl;
      }
      catch (serverIdl::XmlBlasterException &ex) {
         log_.error(me(), string(ex.reason));
      }
      catch (CORBA::Exception &e) {
          log_.error(me(), to_string(e));
//          e.printStackTrace();
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
      serverIdl::StringArr_var strArr; // = (serverIdl::StringArr_var)0;
      try {
         strArr = corbaConnection_->erase(xmlKey, qos);
         log_.info(me(), "Success, erased a message");
      }
      catch(serverIdl::XmlBlasterException &e) {
         log_.error(me(), "XmlBlasterException: " + string(e.reason));
      }
      if (strArr->length() != 1) {
         char buffer[256];
         ostrstream out(buffer, 255);
         out << "erased" << strArr->length() << "messages" << (char)0;
         log_.error(me(), buffer);
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
      if (log_.TRACE) log_.trace(me(), "1. Get a not existing message ...");
      try {
         string xmlKey = string("<key oid='") + publishOid_
            + "' queryType='EXACT'></key>";
         string qos = "<qos></qos>";
         serverIdl::MessageUnitArr*
            msgArr = corbaConnection_->get(xmlKey, qos);
         cerr << "get of not existing message is not possible" << endl;
         delete msgArr;
         assert(0);
      }
      catch(serverIdl::XmlBlasterException &e) {
         log_.info(me(), string("Success, got XmlBlasterException for ") +
                   "trying to get unknown message: " + string(e.reason));
      }

      if (log_.TRACE) log_.trace(me(), "2. Publish a message ...");

      try {
         string xmlKey = string("<key oid='") + publishOid_
            + "' contentMime='text/plain'>\n</key>";
         serverIdl::MessageUnit msgUnit;
         msgUnit.xmlKey  = xmlKey.c_str();
         serverIdl::ContentType content(senderContent_.length()+1,
                                        senderContent_.length()+1,
                                        (CORBA::Octet*)senderContent_.c_str());
         msgUnit.content = content;

         PublishQosWrapper qosWrapper = new PublishQosWrapper();
         // the same as "<qos></qos>"
         msgUnit.qos = qosWrapper.toXml().c_str();
         corbaConnection_->publish(msgUnit);
         log_.info(me(), "Success, published a message");
      }
      catch(serverIdl::XmlBlasterException &e) {
         cerr << "publish - XmlBlasterException: " + string(e.reason) << endl;
         assert(0);
      }

      if (log_.TRACE) log_.trace(me(), "3. Get an existing message ...");
      try {
         string xmlKey = string("<key oid='") + publishOid_
            + "' queryType='EXACT'></key>";
         string qos = "<qos></qos>";
         serverIdl::MessageUnitArr* msgArr =
            corbaConnection_->get(xmlKey, qos);
         log_.info(me(), "Success, got the message");
         string str = (char*)&(*msgArr)[0].content[0];
         if (senderContent_ != str) {
            cerr << "Corrupted content" << endl;
            delete msgArr;
            assert(0);
         }
         delete msgArr;
      }
      catch(serverIdl::XmlBlasterException &e) {
         log_.error(me(), string("XmlBlasterException for trying to get ")
                    + "a message: " + string(e.reason));
         cerr << "Couldn't get() an existing message" << endl;
         assert(0);
      }
   }


   /**
    * LOAD TEST: get 200 times a non-existing message
    */
   void testGetMany() {
      int num = 200;
      char buffer[256];
      ostrstream out(buffer, 255);
      out <<  "Get " << num << (char)0;
      string msg = string(buffer) + " not existing messages ...";
      log_.info(me(), msg);
      string xmlKey = "<key oid='NotExistingMessage' queryType='EXACT'></key>";
      string qos    = "<qos></qos>";
      for (int i=0; i < num; i++) {
         try {
            serverIdl::MessageUnitArr* msgArr =
               corbaConnection_->get(xmlKey, qos);
            cerr << "get of not existing message is not possible" << endl;
            delete msgArr;
            assert(0);
         }
         catch(serverIdl::XmlBlasterException &e) {
            // Log.info(ME, "Success, got XmlBlasterException for trying to get unknown message: " + e.reason);
         }
      }
      string txt = string(buffer) + " not existing messages done";
      log_.info(me(), txt);
   }
};

}} // namespace



int main(int args, char *argc[]) {

   org::xmlBlaster::TestGet *testSub = new org::xmlBlaster::TestGet("TestGet", "Tim");
   testSub->setUp(args, argc);
   testSub->testGet();
   testSub->testGetMany();
   testSub->tearDown();
//   Log.exit(TestGet.ME, "Good bye");
   return 0;
}

