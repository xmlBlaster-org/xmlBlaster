/*-----------------------------------------------------------------------------
Name:      TestCommand.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: TestCommand.cpp 12915 2004-11-18 14:55:44Z ruff $
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
 * This client tests the method sendAdministrativeCommand(). 
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 */
class SpecificCallback : public I_Callback {
private:
   int numReceived_;
   string name_;
   I_Log& log_;

public:
   SpecificCallback(I_Log& log, const string& name) : log_(log) {
      name_ = name;
      numReceived_ = 0;
   }

   int getCount() {
      return numReceived_;
   }


   string update(const string &sessionId,
               UpdateKey &updateKey,
               const unsigned char * /*content*/, long /*contentSize*/,
               UpdateQos &updateQos) 
   {
      log_.info("update", string("Receiving update on callback '") + name_ + "' of message oid=" +
                updateKey.getOid() + " state=" + updateQos.getState() +
                " authentication sessionId=" + sessionId + " ...");
      numReceived_++;
      return "<qos><state id='OK'/></qos>";
   }


};


class TestCommand: public TestSuite, public virtual I_Callback 
{
private:
   bool   messageArrived_;      // = false;
   int    numReceived_;         //  = 0;         // error checking
   string subscribeOid_;
   string publishOid_;          // = "dummy";
   string senderName_;
   string senderContent_;
   string receiverName_;        // sender/receiver is here the same client
   string contentMime_;         // = "text/xml";
   string contentMimeExtended_; //  = "1.0";
   ConnectReturnQos returnQos_;
   SpecificCallback *cb1_;
   SpecificCallback *cb2_;
   SpecificCallback *cb3_;

   /** Publish tests */
   enum TestType {
      TEST_ONEWAY, TEST_PUBLISH, TEST_ARRAY
   };

   /**
    * Constructs the TestCommand object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
 public:
   TestCommand(int args, char *argc[], const string &loginName)
      : TestSuite(args, argc, "TestCommand"), returnQos_(global_)
   {
      senderName_          = loginName;
      receiverName_        = loginName;
      numReceived_         = 0;
      publishOid_          = "dummy";
      contentMime_         = "text/xml";
      contentMimeExtended_ = "1.0";
      senderContent_       = "Yeahh, i'm the new content";
      cb1_ = new SpecificCallback(log_, "callback1");
   }

   virtual ~TestCommand() 
   {
      delete cb1_;
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
         SecurityQos secQos(global_, senderName_, passwd);
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
         usage();
         assert(0);
      }
   }


   /**
    * Tears down the fixture. <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   void tearDown() 
   {
      log_.info(ME, "Cleaning up test - erasing message.");
      /*
      EraseKey eraseKey(global_);
      eraseKey.setOid(publishOid_);
      EraseQos eraseQos(global_);

      vector<EraseReturnQos> retArr;
      try {
         retArr = connection_.erase(eraseKey, eraseQos);
      }
      catch(XmlBlasterException &e) {
         log_.error(ME, string("XmlBlasterException: ") + e.toXml());
      }
      if (retArr.size() != 1) {
         log_.error(ME, "Erased " + lexical_cast<string>(retArr.size()) + " messages");
      }
      */
      connection_.disconnect(DisconnectQos(global_));
      TestSuite::tearDown();
   }


   void testSetCallbackDispatcherActive() 
   {
      if (log_.trace()) log_.trace(ME, "setCallbackDispatcherActive() ...");
      try {
         connection_.setCallbackDispatcherActive(false);
         log_.info(ME, string("Success: setCallbackDispatcherActive(false)"));

         //string ret = org::xmlBlaster::util::waitOnKeyboardHit("Hit a key to activate again ...");

         connection_.setCallbackDispatcherActive(true);
         log_.info(ME, string("Success: setCallbackDispatcherActive(true)"));

         //org::xmlBlaster::util::waitOnKeyboardHit("Hit a key to finish test ...");
      }
      catch(XmlBlasterException &e) {
         log_.warn(ME, string("XmlBlasterException: ") + e.toXml());
         assert(0);
      }
   }


   void testSendAdministrativeCommand() 
   {
      if (log_.trace()) log_.trace(ME, "sendAdministrativeCommand() ...");
      //string command = "?clientList";
      {
         string command = global_.getId()+"/?dispatcherActive=false";
         log_.info(ME, string("Trying command '" + command + "'"));
         try {
            string ret = connection_.sendAdministrativeCommand(command);
            log_.info(ME, string("Success: " + command + " returned '" + ret + "'"));
         }
         catch(XmlBlasterException &e) {
            log_.warn(ME, string("XmlBlasterException: ") + e.toXml());
            assert(0);
         }
      }
      {
         string command = global_.getId()+"/?dispatcherActive";
         log_.info(ME, string("Trying command '" + command + "'"));
         try {
            string ret = connection_.sendAdministrativeCommand(command);
            log_.info(ME, string("Success: " + command + " returned '" + ret + "'"));
         }
         catch(XmlBlasterException &e) {
            log_.warn(ME, string("XmlBlasterException: ") + e.toXml());
            assert(0);
         }
      }
   }

   string update(const string &sessionId,
               UpdateKey &updateKey,
               const unsigned char *content, long contentSize,
               UpdateQos &updateQos) 
   {
      log_.info(ME, string("Receiving update of message oid=") +
                updateKey.getOid() + " state=" + updateQos.getState() +
                " authentication sessionId=" + sessionId + " ...");
      numReceived_ ++;
      return "<qos><state id='OK'/></qos>";
   }
};

}}} // namespace

using namespace org::xmlBlaster::test;

int main(int args, char *argc[]) 
{
   try {
      org::xmlBlaster::util::Object_Lifetime_Manager::init();
      TestCommand testSub(args, argc, "Tim");
 
      testSub.setUp();
      testSub.testSetCallbackDispatcherActive();
      testSub.testSendAdministrativeCommand();
      testSub.tearDown();

      Thread::sleepSecs(1);
   }
   catch (XmlBlasterException& ex) {
      std::cout << ex.toXml() << std::endl;
   }
   catch (...) {
      cout << "unknown exception occured" << endl;
      XmlBlasterException e(INTERNAL_UNKNOWN, "main", "main thread");
      cout << e.toXml() << endl;
   }
   org::xmlBlaster::util::Object_Lifetime_Manager::fini();
   return 0;
}


