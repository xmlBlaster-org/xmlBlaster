/*-----------------------------------------------------------------------------
Name:      TestGet.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing publish()
Version:   $Id: TestGet.cpp,v 1.4 2002/01/31 20:57:19 ruff Exp $
-----------------------------------------------------------------------------*/

#include <sstream>
#include <boost/lexical_cast.hpp>
#include <string>
#include <util/StopWatch.h>
#include <client/CorbaConnection.h>
#include <client/LoginQosWrapper.h>
#include <client/PublishQosWrapper.h>

using namespace std;
using boost::lexical_cast;
//using namespace boost;

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
      for (int ii=0; ii<args; ii++) {
         if (strcmp(argc[ii], "-?")==0 || strcmp(argc[ii], "-h")==0 || strcmp(argc[ii], "-help")==0) {
            usage();
            log_.exit(me(), "Good bye");
         }
      }

      try {
         corbaConnection_ = new CorbaConnection(args, argc); // Find orb
         string passwd = "secret";
         LoginQosWrapper qos = new LoginQosWrapper(); // == "<qos></qos>";
         corbaConnection_->login(loginName_, passwd, qos);
         log_.info(me(), "Successful login");
      }
      catch (serverIdl::XmlBlasterException &ex) {
         log_.error(me(), string(ex.reason));
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
         ostringstream out(buffer, 255);
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
      if (log_.TRACE) log_.trace(me(), "1. Get a not existing message " + publishOid_ + " ...");
      try {
         string xmlKey = string("<key oid='") + publishOid_
            + "' queryType='EXACT'></key>";
         string qos = "<qos></qos>";
         serverIdl::MessageUnitArr* msgArr = corbaConnection_->get(xmlKey, qos);
         log_.error(me(), "get of not existing message " + publishOid_);
         delete msgArr;
         usage();
         assert(0);
      }
      catch(serverIdl::XmlBlasterException &e) {
         log_.info(me(), string("Success, got XmlBlasterException for trying to get unknown message: ") + string(e.reason));
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
         log_.error(me(), "publish - XmlBlasterException: " + string(e.reason));
         usage();
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
            log_.error(me(), "Corrupted content");
            delete msgArr;
            usage();
            assert(0);
         }
         delete msgArr;
      }
      catch(serverIdl::XmlBlasterException &e) {
         log_.error(me(), string("XmlBlasterException for trying to get ")
                    + "a message: " + string(e.reason));
         usage();
         assert(0);
      }
   }


   /**
    * LOAD TEST: get 200 times a non-existing message
    */
   void testGetMany() {
      int num = 200;
      log_.info(me(), "Get " + lexical_cast<string>(num) + " not existing messages ...");
      string xmlKey = "<key oid='NotExistingMessage' queryType='EXACT'></key>";
      string qos    = "<qos></qos>";
      for (int i=0; i < num; i++) {
         try {
            serverIdl::MessageUnitArr* msgArr =
               corbaConnection_->get(xmlKey, qos);
            log_.error(me(), "Got a not existing message message");
            delete msgArr;
            assert(0);
         }
         catch(serverIdl::XmlBlasterException &e) {
            log_.info(me(), string("Success, got XmlBlasterException for trying to get unknown message: ") + string(e.reason));
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
      log_.plain(me(), "   TestGet -iorHost serverHost.myCompany.com");
      log_.plain(me(), "----------------------------------------------------------");
   }
};

}} // namespace



int main(int args, char *argc[]) {

   org::xmlBlaster::TestGet *testSub = new org::xmlBlaster::TestGet("TestGet", "Tim");
   testSub->setUp(args, argc);
   testSub->testGet();
   testSub->testGetMany();
   testSub->tearDown();
//   log_.exit(TestGet.me(), "Good bye");
   return 0;
}

