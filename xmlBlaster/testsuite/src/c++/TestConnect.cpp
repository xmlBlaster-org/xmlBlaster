/*-----------------------------------------------------------------------------
Name:      TestConnect.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login/logout test for xmlBlaster
-----------------------------------------------------------------------------*/

/**
 * This client does test connect and disconnect.<br />
 * login/logout combinations are checked with subscribe()/publish() calls
 * <p />
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java -jar lib/xmlBlaster.jar    (Server)
 *
 *    TestConnect                     (Client)
 * </pre>
 */
#include "TestSuite.h"
#include <util/qos/ConnectQosFactory.h>
#include <iostream>
#ifdef XMLBLASTER_MICO
#  include <mico/version.h>
#endif

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::client::key;
using namespace org::xmlBlaster::client::qos;

namespace org { namespace xmlBlaster { namespace test {

class TestConnect : public virtual client::I_Callback, public TestSuite
{

private:

   string            publishReturnQos, secondOid_;
   string            oid_;
   string            qos1_, qos2_;
   string            senderContent_;
   XmlBlasterAccess* conn2_;
   MessageUnit*      msgUnit_;
   int               numReceived_; // error checking
   string            contentMime_;
   string            contentMimeExtended_;

public:
   /**
    * Constructs the TestLogin object.
    * <p />
    * @param testName   The name used in the test suite
    * @param loginName  The name to login to the xmlBlaster
    * @param secondName The name to login to the xmlBlaster again
    */
   TestConnect(int args, char * argv[], const string &qos1, const string &qos2)
      : TestSuite(args, argv, "TestConnect")
   {
      qos1_                = qos1;
      qos2_                = qos2;
      publishReturnQos     = "";
      secondOid_           = "SecondOid";
      oid_                 = "TestLogin";
      numReceived_         = 0;
      contentMime_         = "text/plain";
      contentMimeExtended_ = "1.0";
      msgUnit_             = NULL;
      conn2_               = NULL;
   }

   ~TestConnect() {
      cout << "Destructor for TestConnect invoked" << endl;
      delete conn2_;
      conn2_ = 0;
      delete msgUnit_;
      msgUnit_ = 0;
   }


   /**
    * This is the callback method (I_Callback) invoked from CorbaConnection
    * informing the client in an asynchronous mode about a new message.
    * <p />
    * The raw CORBA-BlasterCallback.update() is unpacked and for each arrived
    * message this update is called.
    *
    * @param sessionId The sessionId to authenticate the callback
    *                  This sessionId was passed on subscription
    *                  we can use it to decide if we trust this update()
    * @param updateKey The arrived key
    * @param content   The arrived message content
    * @param qos       Quality of Service of the MessageUnit
    * @return The status string
    */
   string update(const string &/*sessionId*/,
                 UpdateKey &/*updateKey*/,
                 const unsigned char * /*content*/, 
                 long /*contentSize*/,
                 UpdateQos &/*updateQos*/) 
   {
      if (log_.call()) log_.call(ME, "Receiving update of a message ...");
      numReceived_++;
      return "<qos><state id='OK'/></qos>";
   }

   /**
    * Sets up the fixture. <p />
    * Connect to xmlBlaster and login
    */

   void setUp() 
   {
      TestSuite::setUp();
      try {

         // Login to xmlBlaster
         ConnectQosFactory factory(global_);
         if (conn2_) delete conn2_;
         ConnectQosRef connectQos2 = factory.readObject(qos2_);
         conn2_ = new XmlBlasterAccess(global_);
         conn2_->connect(*connectQos2, NULL);

         conn2_->disconnect(DisconnectQos(global_));
         delete conn2_;
         conn2_ = NULL;

         ConnectQosRef connectQos1 = factory.readObject(qos1_);
         connection_.connect(*connectQos1, this);

      }
      catch (XmlBlasterException &e) {
         log_.error(ME, e.toXml());
         usage();
      }
   }


   void testPubSub()
   {
      try {
         log_.info(ME, "testPubSub");


         PublishKey pubKey(global_);
         pubKey.setOid("testConnect");
         PublishQos pubQos(global_);
         MessageUnit msgUnit(pubKey, "This is a happy day!", pubQos);
         connection_.publish(msgUnit);
         Thread::sleepSecs(1);

         SubscribeKey subKey(global_);
         subKey.setOid("testConnect");

         SubscribeQos subQos(global_);
         string subscribeOid_ = connection_.subscribe(subKey, subQos).getSubscriptionId();
         log_.info(ME, string("Success: Subscribe subscription-id=") + subscribeOid_ + " done");

         Thread::sleepSecs(2);
         assertEquals(log_, ME, 1, numReceived_, "reconnecting when communication down and giving positive publicSessionId: no exception expected");

         EraseKey key(global_);
         key.setOid("testConnect");
         EraseQos qos(global_);
         connection_.erase(key, qos);
         Thread::sleepSecs(1);
         log_.info(ME, "testPubSub successfully completed");
      }
      catch (XmlBlasterException &e) {
         log_.error(ME, e.toXml());
         usage();
      }
   }


   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   void tearDown() 
   {
      TestSuite::tearDown();
      connection_.disconnect(DisconnectQos(global_));
//      conn2_->disconnect(DisconnectQos(global_));
   }

};

}}} // namespace

using namespace org::xmlBlaster::test;

int main(int args, char *argc[]) {
   org::xmlBlaster::util::Object_Lifetime_Manager::init();

# ifdef XMLBLASTER_MICO
   if (MICO_BIN_VERSION < 0x02030b) {
        std::cout << " !!!!! THIS TEST CAN NOT BE RUN WITH MICO SINCE AN ORB WHICH IS SHUTDOWN CAN NOT BE REUSED !!!!" << std::endl;
        std::cout << " !!!!! IT HAS BEEN TESTED AND IS PROVEN TO FAIL WITH MICO 2.3.7 AND 2.3.8                  !!!!" << std::endl;
        std::cout << " !!!!! IT IS PROVEN TO FAIL WITH MICO 2.3.7 AND 2.3.8                                      !!!!" << std::endl;
        std::cout << " !!!!! TRY IT WITH ANOTHER CORBA IMPLEMENTATION (for example TAO)                          !!!!" << std::endl;
        exit(-1);
   }
   else {
      std::cout << "MICO Version " << MICO_VERSION << " should run fine" << std::endl;
   }
# endif

   string qos1 =
      string("<qos>\n") +
      string("   <securityService type='htpasswd' version='1.0'>\n") +
      string("     <![CDATA[\n") +
      string("     <user>ticheta</user>\n") +
      string("     <passwd>secret</passwd>\n") +
      string("     ]]>\n") +
      string("   </securityService>\n") +
      string("   <session name='ticheta'/>\n") +
      string("   <ptp>false</ptp>\n") +
      string("</qos>\n");

   string qos2 =
      string("<qos>\n") +
      string("   <securityService type='htpasswd' version='1.0'>\n") +
      string("     <![CDATA[\n") +
      string("     <user>tacatitac</user>\n") +
      string("     <passwd>secret</passwd>\n") +
      string("     ]]>\n") +
      string("   </securityService>\n") +
      string("   <session name='tacatitac'/>\n") +
      string("   <ptp>false</ptp>\n") +
      string("</qos>\n");

   Global& glob = Global::getInstance();
   glob.initialize(args, argc);
   TestConnect *testConnect = new TestConnect(args, argc, qos1, qos2);
   testConnect->setUp();
   testConnect->testPubSub();
   testConnect->tearDown();
   delete testConnect;
   org::xmlBlaster::util::Object_Lifetime_Manager::fini();
   return 0;
}

