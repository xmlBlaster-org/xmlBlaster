/*-----------------------------------------------------------------------------
Name:      TestLeaveServer.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
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
using namespace org::xmlBlaster::authentication;

namespace org { namespace xmlBlaster { namespace test {

/**
 * This client tests the method leaveServer(). 
 */
class TestLeaveServer: public TestSuite, public virtual I_Callback 
{
private:
   string loginName_;
   ConnectReturnQos returnQos_;

   /** Publish tests */
   enum TestType {
      TEST_ONEWAY, TEST_PUBLISH, TEST_ARRAY
   };

   /**
    * Constructs the TestLeaveServer object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
 public:
   TestLeaveServer(int args, char *argc[], const string &loginName)
      : TestSuite(args, argc, "TestLeaveServer"), returnQos_(global_)
   {
      loginName_ = loginName;
   }

   virtual ~TestLeaveServer() 
   {
   }

   /**
    * Sets up the fixture. <p />
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
         connQos.getSessionQosRef()->setPubSessionId(3L);
         returnQos_ = connection_.connect(connQos, this);
         string name = returnQos_.getSessionQos().getAbsoluteName();
         string name1 = returnQos_.getSessionQosRef()->getAbsoluteName();
         assertEquals(log_, ME, name, name1, string("name comparison for reference"));

         log_.info(ME, string("connection setup: the session name is '") + name + "'");
         // Login to xmlBlaster
      }
      catch (XmlBlasterException &e) {
         log_.error(ME, string("Login failed: ") + e.toXml());
         assert(0);
      }
   }


   /**
    * Tears down the fixture. <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   void tearDown() 
   {
      TestSuite::tearDown();
   }


   /**
    * TEST: 
    */
   void testLeaveServer()
   {
      log_.info(ME, "testLeaveServer() ...");

      try {
         GetKey getKey(global_);
         getKey.setOid("__cmd:?freeMem");
         GetQos getQos(global_);
         vector<util::MessageUnit> msgVec = connection_.get(getKey, getQos);
         log_.info(ME, "Success, got array of size " + lexical_cast<string>(msgVec.size()) +
                         " for trying to get __cmd:?freeMem");
         assert(msgVec.size() == 1);
      }
      catch(XmlBlasterException &e) {
         log_.error(ME, "get of '__cmd:?freeMem' failed: " + e.toString());
         assert(0);
      }

      try {
         StringMap map;
         connection_.leaveServer(map);
         log_.info(ME, string("Success: leaveServer()"));
      }
      catch(XmlBlasterException &e) {
         log_.warn(ME, string("XmlBlasterException: ")+e.toXml());
         assert(0);
      }

      try {
         StringMap map;
         connection_.leaveServer(map);
         log_.error(ME, string("leaveServer(): Leaving server twice should fail"));
                        assert(0);
      }
      catch(XmlBlasterException &e) {
         log_.info(ME, string("SUCCESS: Expected XmlBlasterException: ")+e.toString());
      }


      try {
         GetKey getKey(global_);
         getKey.setOid("__cmd:?freeMem");
         GetQos getQos(global_);
         vector<util::MessageUnit> msgVec = connection_.get(getKey, getQos);
         log_.error(ME, string("leaveServer(): Calling get() after leaving server should fail, msgs=") + lexical_cast<string>(msgVec.size()));
         assert(0);
      }
      catch(XmlBlasterException &e) {
         log_.info(ME, string("SUCCESS: Expected XmlBlasterException: ")+e.toString());
      }

      log_.info(ME, "SUCCESS: testLeaveServer() DONE");
   }


   string update(const string &sessionId,
               UpdateKey &updateKey,
               const unsigned char * /*content*/, long /*contentSize*/,
               UpdateQos &updateQos) 
   {
      log_.error(ME, string("Receiving update of message oid=") +
                updateKey.getOid() + " state=" + updateQos.getState() +
                " authentication sessionId=" + sessionId + " ...");
      return "<qos><state id='OK'/></qos>";
   }
};

}}} // namespace

using namespace org::xmlBlaster::test;

int main(int args, char *argc[]) 
{
   try {
      org::xmlBlaster::util::Object_Lifetime_Manager::init();
      TestLeaveServer testSub(args, argc, "TestLeaveServer");
      testSub.setUp();
      testSub.testLeaveServer();
      testSub.tearDown();
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

   org::xmlBlaster::util::Object_Lifetime_Manager::fini();
   return 0;
}


