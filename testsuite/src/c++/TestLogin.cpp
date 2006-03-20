/*-----------------------------------------------------------------------------
Name:      TestLogin.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login/logout test for xmlBlaster
Version:   $Id$
-----------------------------------------------------------------------------*/

/**
 * This client does test login and logout.<br />
 * login/logout combinations are checked with subscribe()/publish() calls
 * <p />
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java -jar lib/xmlBlaster.jar    (Server)
 *
 *    TestLogin                       (Client)
 * </pre>
 */

#include <string>
#include <client/protocol/corba/CorbaConnection.h>
#include <client/LoginQosWrapper.h>
#include <util/StopWatch.h>
#include <util/Global.h>
#include <client/I_Callback.h>
#include <iostream>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::client::protocol::corba;

namespace org { namespace xmlBlaster {

class TestLogin : public virtual client::I_Callback {

private:
   string me() {
      return "Tim";
   }

   string publishReturnQos, secondOid_;
   string oid_;
   string senderName_, secondName_;
   string senderContent_;
   CorbaConnection         *senderConnection_, *secondConnection_;
   serverIdl::MessageUnit* msgUnit_;     // a message to play with

   int       numReceived_; // error checking
   string    contentMime_;
   string    contentMimeExtended_;
   util::StopWatch stopWatch_;
   Global&    global_;
   util::I_Log& log_;

public:
   /**
    * Constructs the TestLogin object.
    * <p />
    * @param testName   The name used in the test suite
    * @param loginName  The name to login to the xmlBlaster
    * @param secondName The name to login to the xmlBlaster again
    */
   TestLogin(Global& global, const string &senderName,
             const string &secondName) : stopWatch_(), global_(global),
             log_(global.getLog("test"))
   {
      senderName_  = senderName;
      secondName_  = secondName;
      publishReturnQos  = "";
      secondOid_   = "SecondOid";
      oid_         = "TestLogin";
      numReceived_ = 0;
      contentMime_ = "text/plain";
      contentMimeExtended_ = "1.0";
      msgUnit_     = 0;
      senderConnection_ = secondConnection_ = 0;
   }

   ~TestLogin() {
      delete senderConnection_;
      delete secondConnection_;
      delete msgUnit_;
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
               const unsigned char * /*content*/, long /*contentSize*/,
               UpdateQos &/*updateQos*/) {
      if (log_.call()) log_.call(me(), "Receiving update of a message ...");
      numReceived_++;
      return "<qos><state id='OK'/></qos>";
   }

   /**
    * Sets up the fixture. <p />
    * Connect to xmlBlaster and login
    */

   void setUp(int args, char *argc[]) {
      for (int ii=0; ii<args; ii++) {
         if (strcmp(argc[ii], "-?")==0 || strcmp(argc[ii], "-h")==0 || strcmp(argc[ii], "-help")==0) {
            usage();
            exit(0);
         }
      }
      try {
         if (senderConnection_) delete senderConnection_;
         senderConnection_ = new CorbaConnection(global_); // Find orb
         string passwd     = "secret";

         LoginQosWrapper qosWrapper;
         senderConnection_->login(senderName_, passwd, qosWrapper, this);

         // Login to xmlBlaster
         if (secondConnection_) delete secondConnection_;
         secondConnection_ = new CorbaConnection(global_); // Find orb
         secondConnection_->login(secondName_, passwd, qosWrapper, this);

         // a sample message unit
         string xmlKey = "<key oid='" + oid_ + "' contentMime='" +
            contentMime_ + "' contentMimeExtended='" +
            contentMimeExtended_ + "'>\n" +
            "   <TestLogin-AGENT>   </TestLogin-AGENT> </key>";
         senderContent_ = "Some content";
         if (msgUnit_) delete msgUnit_;
         msgUnit_ = new serverIdl::MessageUnit();
         msgUnit_->xmlKey  = xmlKey.c_str();
         msgUnit_->content =
            serverIdl::ContentType(senderContent_.length(),
                                   senderContent_.length(),
                                   (CORBA::Octet*)senderContent_.c_str());
      }
      catch (CORBA::Exception &e) {
         log_.error(me(), to_string(e));
         cerr << to_string(e);
         usage();
      }
   }


   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   void tearDown() {
      string xmlKey = "<key oid='" + oid_ + "' queryType='EXACT'>\n</key>";
      string qos    = "<qos></qos>";
      vector<string> strArr;
      try {
         strArr = senderConnection_->erase(xmlKey.c_str(), qos.c_str());
      }
      catch(serverIdl::XmlBlasterException &e) {
         string msg = me() + "-tearDown()";
         string txt = string("XmlBlasterException in erase(): ") + string(e.errorCodeStr) + string(": ") + string(e.message);
         log_.error(msg, txt);
      }
      if (strArr.size() != 1) {
         string txt = "Erased ";
         txt += strArr.size() + " messages:";
         log_.error(me(), txt);
      }

      xmlKey = "<key oid='" + secondOid_ + "' queryType='EXACT'>\n</key>";
      qos    = "<qos></qos>";
      try {
         strArr = senderConnection_->erase(xmlKey.c_str(), qos.c_str());
      }
      catch(serverIdl::XmlBlasterException &e) {

         string msg = me() + "-tearDown()";
         string txt = string("XmlBlasterException in erase(): ") + string(e.errorCodeStr) + string(": ") + string(e.message);
         log_.error(msg, txt);
      }
      if (strArr.size() != 1) {
         string txt = "Erased ";
         txt       += "many messages"; // change many with the number!!!!
         log_.error(me(), txt);
      }
      senderConnection_->logout();
      secondConnection_->logout();
   }


public:
   /**
    * TEST: Subscribe to messages with XPATH.
    * <p />
    * The returned subscribeOid is checked
    */
   void testSubscribeXPath() {
      if (log_.trace()) log_.trace(me(),"Subscribing using XPath syntax ...");

      string xmlKey = "<key oid='' queryType='XPATH'>\n";
      xmlKey       += "   //TestLogin-AGENT </key>";
      string qos    = "<qos></qos>";
      numReceived_  = 0;
      string subscribeOid = "";
      try {
         subscribeOid =
            senderConnection_->subscribe(xmlKey.c_str(), qos.c_str());
         string txt   = "Success: Subscribe on ";
         txt         += subscribeOid + " done";
         log_.info(me(), txt);
      }
      catch(serverIdl::XmlBlasterException &e) {
         log_.warn(me() + "-testSubscribeXPath", string("XmlBlasterException: ") + string(e.errorCodeStr) + ": " + string(e.message));
         string txt = string("subscribe - XmlBlasterException: ") + string(e.errorCodeStr) + ": " + string(e.message);
         cerr << txt << endl;
         assert(0);
      }
      if (subscribeOid == "") {
         cerr << "returned null subscribeOid" << endl;
         assert(0);
      }
      if (subscribeOid.length() < 1) {
         cerr << "returned subscribeOid is empty" << endl;
         assert(0);
      }
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid is checked
    * @param ptp Use the Point to Point style
    */
   void testPublish(bool ptp) {
      if (log_.trace()) log_.trace(me(), "Publishing a message ...");

      numReceived_ = 0;
      string qos = "<qos></qos>";
      if (ptp)
         qos = "<qos>\n<destination>\n" + secondName_ +
            "\n</destination>\n</qos>";
      try {
         msgUnit_->qos = qos.c_str();
         publishReturnQos = senderConnection_->publish(*msgUnit_);
         if (publishReturnQos.find(oid_) == string::npos) {
            cerr << "oid is different" << endl;
            assert(0);
         }

      }
      catch(serverIdl::XmlBlasterException &e) {
         log_.warn(me()+"-testPublish",
               string("XmlBlasterException: ") + string(e.errorCodeStr) + ": " + string(e.message));
         string msg = string("publish - XmlBlasterException: ") + string(e.errorCodeStr) + ": " + string(e.message);
         cerr << msg << endl;
         assert(0);
      }

      if (publishReturnQos == "") {
         cerr << "returned publishOid == null" << endl;
         assert(0);
      }
      if (publishReturnQos.length() < 1) {
         cerr << "returned publishOid is zero in length";
         assert(0);
      }
   }


   /**
    * TEST: Construct a message and publish it,<br />
    * the previous XPath subscription should match and send an update.
    */
   void testLoginLogout(int args=0, char *argc[]=0) {
      // test ordinary login
      numReceived_ = 0;
      testSubscribeXPath();
      testPublish(false);
      waitOnUpdate(1000L, 1);              // message arrived?

      // login again, without logout
      setUp(args, argc);
      testPublish(true);                   // sending directly PtP to 'receiver'
      waitOnUpdate(1000L, 1);              // message arrived?

      // login again, without logout
      setUp(args, argc);
      testPublish(false);
      stopWatch_.wait(1000L);
      numReceived_ = 0;
      testSubscribeXPath();
      waitOnUpdate(1000L, 1);
      // test publish from other user
      numReceived_ = 0;
      try {
         // a sample message unit
         string xmlKey = "<key oid='" + secondOid_ + "' contentMime='" +
            contentMime_ + "' contentMimeExtended='" + contentMimeExtended_
            + "'>\n" + "   <TestLogin-AGENT>" + "   </TestLogin-AGENT>"
            + "</key>";
         string content = "Some content";

         serverIdl::MessageUnit secondMsg;
         secondMsg.xmlKey  = xmlKey.c_str();
         secondMsg.content =
            serverIdl::ContentType(content.length(),
                                   content.length(),
                                   (CORBA::Octet*)content.c_str());

         secondMsg.qos = "<qos></qos>";
         publishReturnQos = /*secondBlaster_*/
            /*second*/ senderConnection_->publish(secondMsg);
      }
      catch(serverIdl::XmlBlasterException &e) {
         log_.warn(me()+"-secondPublish", string("XmlBlasterException: ") + string(e.errorCodeStr) + ": " + string(e.message));
         string msg = string("second - publish - XmlBlasterException: ") + string(e.errorCodeStr) + ": " + string(e.message);
         cerr << msg << endl;
         assert(0);
      }
      waitOnUpdate(1000L, 1);              // message arrived?

      if (publishReturnQos == "") {
         cerr <<  "returned publishOid == null" << endl;
         assert(0);
      }
      if (publishReturnQos.length() == 0) {
         cerr << "returned publishOid" << endl;
         assert(0);
      }
      // test logout with following subscribe()
      senderConnection_->logout();
      try {
         msgUnit_->qos = "<qos></qos>";
         publishReturnQos = /*xmlBlaster_*/
            senderConnection_->publish(*msgUnit_);
         cerr << "Didn't expect successful subscribe after logout";
         assert(0);
      }
      catch(serverIdl::XmlBlasterException &e) {
         log_.info(me(), string("Success: ") + string(e.errorCodeStr) + ": " + string(e.message));
      }

      stopWatch_.wait(1000L); // wait a second
      if (numReceived_ != 0) {
         cerr << "Didn't expect an update" << endl;
         assert(0);
      }

      // login again
      setUp(args, argc);

   }


   /**
    * Little helper, waits until the wanted number of messages are arrived
    * or returns when the given timeout occurs.
    * <p />
    * @param timeout in milliseconds
    * @param numWait how many messages to wait
    */
private:
   void waitOnUpdate(long timeout, int numWait) {
      long pollingInterval = 50L;  // check every 0.05 seconds
      if (timeout < 50L)  pollingInterval = timeout / 10L;
      long sum = 0L;
      // check if too few are arriving
      while (numReceived_ < numWait) {
         stopWatch_.wait(pollingInterval);
         sum += pollingInterval;
         senderConnection_->orbPerformWork();
         secondConnection_->orbPerformWork();
         if (sum >= timeout) {
            cerr << "Timeout of " << timeout << " occured without updatetimeout: " << timeout << " " << numWait << endl;
            assert(0);
         }
      }

      // check if too many are arriving
      stopWatch_.wait(timeout);
      if (numWait != numReceived_) {
         cerr << "Wrong number of messages arrived ";
         cerr << "expected: " << numWait << " received: ";
         cerr << numReceived_ << endl;
         assert(0);
      }
      numReceived_ = 0;
   }
   void usage()
   {
      Global& glob = Global::getInstance();
      glob.initialize();
      util::I_Log& log_ = glob.getLog("test");
      log_.plain(me(), "----------------------------------------------------------");
      log_.plain(me(), "Testing C++/CORBA access to xmlBlaster");
      log_.plain(me(), "Usage:");
      CorbaConnection::usage();
      log_.usage();
      log_.plain(me(), "Example:");
      log_.plain(me(), "   TestLogin -dispatch/connection/plugin/ior/iorFile /tmp/ior.dat -trace true");
      log_.plain(me(), "----------------------------------------------------------");
   }
};

}} // namespace





int main(int args, char *argc[]) {
   // Init the XML platform
   org::xmlBlaster::util::Object_Lifetime_Manager::init();
   Global& glob = Global::getInstance();
   glob.initialize(args, argc);
   org::xmlBlaster::TestLogin *testSub = new org::xmlBlaster::TestLogin(glob, "Tim", "Joe");
   testSub->setUp(args, argc);
   testSub->testLoginLogout();
   testSub->tearDown();
   delete testSub;
   // .exit(TestLogin.ME, "Good bye");
   org::xmlBlaster::util::Object_Lifetime_Manager::fini();
   return 0;
}

