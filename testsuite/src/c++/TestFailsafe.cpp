/*-----------------------------------------------------------------------------
Name:      TestFailsafe.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the Timeout Features
-----------------------------------------------------------------------------*/
#include "TestSuite.h"
#include <iostream>

namespace org { namespace xmlBlaster { namespace test {

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::dispatch;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::util::qos::address;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

class TestFailsafe : public virtual I_Callback, public virtual I_ConnectionProblems, public TestSuite
{
private:
   ConnectQos       *connQos_;
   ConnectReturnQos *connRetQos_;
   SubscribeQos     *subQos_;
   SubscribeKey     *subKey_;
   PublishQos       *pubQos_;
   PublishKey       *pubKey_;
   Mutex            updateMutex_;
   bool             isConnected_;
   int              numOfUpdates_;
   bool useSessionMarker_;  // Remove again at version 2.0

public:
   TestFailsafe(int args, char ** argv) 
      : TestSuite(args, argv, "TestFailsafe"),
        updateMutex_()
   {
      connQos_        = 0;
      connRetQos_     = 0;
      subQos_         = 0;
      subKey_         = 0;
      pubQos_         = 0;
      pubKey_         = 0;
      isConnected_    = false;
      numOfUpdates_   = 0;

      SessionName sn(global_, "client/dummy");
      useSessionMarker_ = sn.useSessionMarker();
   }


   virtual ~TestFailsafe()
   {
      if (log_.call()) log_.call(ME, "destructor");
      delete connQos_;
      delete connRetQos_;
      delete subQos_;
      delete subKey_;
      delete pubQos_;
      delete pubKey_;
      if (log_.trace()) log_.trace(ME, "destructor ended");
   }

   bool reachedAlive(StatesEnum /*oldState*/, I_ConnectionsHandler* /*connectionsHandler*/)
   {
      log_.info(ME, "reconnected");
      isConnected_ = true;
      return true;
   }

   void reachedDead(StatesEnum /*oldState*/, I_ConnectionsHandler* /*connectionsHandler*/)
   {
      log_.info(ME, "lost connection");
      isConnected_ = false;
   }

   void reachedPolling(StatesEnum /*oldState*/, I_ConnectionsHandler* /*connectionsHandler*/)
   {
      log_.info(ME, "going to poll modus");
      isConnected_ = false;
   }

   AddressBaseRef getAddress() {
      AddressBaseRef address = new Address(global_);
      address->setDelay(1000);
      address->setPingInterval(1000);
      return address;
   }

   void setUp()
   {
      TestSuite::setUp();
      try {   
         connection_.initFailsafe(this);

         connQos_ = new ConnectQos(global_, "guy", "secret");
         connQos_->setAddress(getAddress());
         log_.info(ME, string("connecting to xmlBlaster. Connect qos: ") + connQos_->toXml());
         // Login to xmlBlaster, register for updates
         connRetQos_ = new ConnectReturnQos(connection_.connect(*connQos_, this));  
         log_.info(ME, "successfully connected to xmlBlaster. Return qos: " + connRetQos_->toXml());

         subKey_ = new SubscribeKey(global_);
         subKey_->setOid("TestFailsafe");
         subQos_ = new SubscribeQos(global_);
         log_.info(ME, string("subscribing to xmlBlaster with key: ") + subKey_->toXml() + " and qos: " + subQos_->toXml());

         SubscribeReturnQos subRetQos = connection_.subscribe(*subKey_, *subQos_);
         log_.info(ME, string("successfully subscribed to xmlBlaster. Return qos: ") + subRetQos.toXml());
      }
      catch (XmlBlasterException& ex) {
         log_.error(ME, string("exception occurred in setUp. ") + ex.toXml());
         assert(0);
      }
   }


   /**
    * This test does the following:
    * - tears down , i.e. it erases the message 'TestFailsafe' and disconnects.
    * - shuts down the server if embedded, otherwise waits you to shutdown for 20 s.
    * - tries to reconnect (and should fail since the server is not connected and the session id is negative)
    * - 
    */
   void testReconnect()
   {
      log_.info(ME, "testReconnect START");
      tearDown();
      // DisconnectQos disconnectQos(global_);
      // connection_.disconnect(disconnectQos);
      Thread::sleep(500);
      if (useEmbeddedServer_) {
         stopEmbeddedServer();
         Thread::sleepSecs(2);
      }
      else {
         waitOnKeyboardHit("Please stop the server now and hit 'c' to continue >> ");
         //log_.info(ME, "please stop the server now (I will wait 20 s)");
         //Thread::sleepSecs(20);
      }
      log_.info(ME, "the communication is now down: ready to start the tests");
      ConnectQos connQos(global_);
      connQos.setAddress(getAddress());
      SessionQos sessionQos(global_,"client/Fritz/-2");
      connQos.setSessionQos(sessionQos);
      bool wentInException = false;

      try {
         connection_.connect(connQos, this);
      }
      catch (XmlBlasterException &ex) {
         log_.info(ME, "Exception is wanted: " + ex.toString());
         wentInException = true;
      }   
      assertEquals(log_, ME, true, wentInException, "reconnecting when communication down and not giving positive publicSessionId: exception must be thrown");

      sessionQos = SessionQos(global_,"client/Fritz/-1");
      connQos.setSessionQos(sessionQos);
      wentInException = false;
      try {
         connection_.connect(connQos, this);
      }
      catch (XmlBlasterException &ex) {
         log_.info(ME, "Exception is wanted: " + ex.toString());
         wentInException = true;
      }   
      assertEquals(log_, ME, true, wentInException, "reconnecting for the second time when communication down and not giving positive publicSessionId: exception must be thrown (again)");

      log_.info(ME, "TESTING FAIL SAFE ...");
      sessionQos = SessionQos(global_,"client/Fritz/7");
      connQos.setSessionQos(sessionQos);
      wentInException = false;
      try {
         ConnectReturnQos retQos = connection_.connect(connQos, this);
         string name = retQos.getSessionQos().getRelativeName();
         if (useSessionMarker_)
            assertEquals(log_, ME, string("client/Fritz/session/7"), name, "checking that return qos has the correct sessionId");
         else
            assertEquals(log_, ME, string("client/Fritz/7"), name, "checking that return qos has the correct sessionId");
      }
      catch (XmlBlasterException &ex) {
         log_.error(ME, ex.toXml());
         wentInException = true;
      }   
      assertEquals(log_, ME, false, wentInException, "reconnecting when communication down and giving positive publicSessionId: no exception expected");

      sessionQos = SessionQos(global_,"client/Fritz/2");
      connQos.setSessionQos(sessionQos);
      wentInException = false;
      try {
         connection_.connect(connQos, this);
      }
      catch (XmlBlasterException &/*ex*/) {
         wentInException = true;
      }   
      assertEquals(log_, ME, false, wentInException, "reconnecting second time when communication down and giving positive publicSessionId: no exception expected but a warning should have come");


      DisconnectQos discQos(global_);
      wentInException = false;
      try {
         connection_.disconnect(discQos);
      }
      catch (XmlBlasterException &/*ex*/) {
         wentInException = true;
      }   
      assertEquals(log_, ME, true, wentInException, "disconnecting when no communication should give an exception");

      // and now we are reconnecting ...
      if (useEmbeddedServer_) {
         startEmbeddedServer();
         Thread::sleepSecs(1);
      }
      else {
         for (int i=0; i < 30; i++) {
            if (isConnected_) break;
            log_.info(ME, "please restart the server now");
            Thread::sleepSecs(2);
            if (connection_.isAlive()) {
               break;
            }
         }
      }

      // making  a subscription now should work ...
      SubscribeKey subKey(global_);
      subKey.setOid("TestReconnect");
      SubscribeQos subQos(global_);
      wentInException = false;
      try {
         connection_.subscribe(subKey, subQos);
      }
      catch (XmlBlasterException &ex) {
         wentInException = true;
         log_.info(ME, string("exception when subscribing: ") + ex.toXml());
      }   
      assertEquals(log_, ME, false, wentInException, "subscribing when communication should not give an exception");

      log_.info(ME, "disconnecting now the newly established connection");
      connection_.disconnect(DisconnectQos(global_));
      log_.info(ME, "going to call setUp to reestablish the initial setup");

      setUp();

      // publishing something to make it happy
      PublishQos pubQos(global_);
      PublishKey pubKey(global_);
      pubKey.setOid("TestFailsafe");

      string msg = "dummy";
      MessageUnit msgUnit(pubKey, msg, pubQos);
      connection_.publish(msgUnit);

      log_.info(ME, "testReconnect END");
   }


   void testFailsafe() 
   {
      int imax = 30;
      try {
         pubQos_ = new PublishQos(global_);
         pubKey_ = new PublishKey(global_);
         pubKey_->setOid("TestFailsafe");

         for (int i=0; i < imax; i++) {
            string msg = lexical_cast<string>(i);
            MessageUnit msgUnit(*pubKey_, msg, *pubQos_);
            log_.info(ME, string("publishing msg '") + msg + "'");
            /*PublishReturnQos pubRetQos =*/ connection_.publish(msgUnit);

            if (i == 2) stopEmbeddedServer();
            if (i == 12) startEmbeddedServer();
            try {
               Thread::sleepSecs(1);
            }
            catch(const XmlBlasterException &e) {
               cout << e.toXml() << endl;
            }

         }
      }
      catch (const XmlBlasterException& ex) {
         log_.error(ME, string("exception occurred in testFailSafe. ") + ex.toXml());
         assert(0);
      }

      int i = 0;
      while (numOfUpdates_ < (imax-1) && i < 100) {
         i++;
         Thread::sleep(100);
      }


   }


   void tearDown()
   {
      try {
         EraseKey eraseKey(global_);
         eraseKey.setOid("TestFailsafe");
         EraseQos eraseQos(global_);
         log_.info(ME, string("erasing the published message. Key: ") + eraseKey.toXml() + " qos: " + eraseQos.toXml());
         vector<EraseReturnQos> eraseRetQos = connection_.erase(eraseKey, eraseQos);
         for (size_t i=0; i < eraseRetQos.size(); i++ ) {
            log_.info(ME, string("successfully erased the message. return qos: ") + eraseRetQos[i].toXml());
         }

         // log_.info(ME, "going to sleep for one minute");
         // org::xmlBlaster::util::thread::Thread::sleep(60000);

         DisconnectQos disconnectQos(global_);
         connection_.disconnect(disconnectQos);
      }
      catch (const XmlBlasterException& ex) {
         log_.error(ME, string("exception occurred in tearDown. ") + ex.toXml());
         assert(0);
      }

      delete connQos_; connQos_ = 0;
      delete subQos_; subQos_ = 0;
      delete subKey_; subKey_ = 0;
      delete connRetQos_; connRetQos_ = 0;
      delete pubQos_; pubQos_ = 0;
      delete pubKey_; pubKey_ = 0;

      TestSuite::tearDown();
   }

   string update(const string& sessionId, UpdateKey& updateKey, const unsigned char *content, long contentSize, UpdateQos& updateQos)
   {
      Lock lock(updateMutex_);
      if (log_.trace()) log_.trace(ME, "update: session: " + sessionId);
      if (log_.trace()) log_.trace(ME, "update: key    : " + updateKey.toXml());
      if (log_.trace()) log_.trace(ME, "update: qos    : " + updateQos.toXml());
      string help((char*)content, (char*)(content)+contentSize);
      if (log_.trace()) log_.trace(ME, "update: content: " + help);
      if (updateQos.getState() == "ERASED" ) return "";

      int count = atoi(help.c_str());
      assertEquals(log_, ME, numOfUpdates_, count, string("update check ") + help);
      numOfUpdates_++;
      return "";
   }

};

}}}


using namespace org::xmlBlaster::test;

/**
 * Try
 * <pre>
 *   java TestFailsafe -help
 * </pre>
 * for usage help
 *
 * To disable the embedded server add -embeddedServer false
 */
int main(int args, char ** argv)
{
   TestFailsafe *testFailsafe = 0;
   try {
      org::xmlBlaster::util::Object_Lifetime_Manager::init();
      testFailsafe = new TestFailsafe(args, argv);
      testFailsafe->setUp();
      testFailsafe->testReconnect();
      
      // testFailsafe.testFailsafe();
      testFailsafe->tearDown();
      delete testFailsafe;
      testFailsafe = 0; 
      org::xmlBlaster::util::Object_Lifetime_Manager::fini();
   }
   catch (const XmlBlasterException& ex) {
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

   return 0;
}

