/*-----------------------------------------------------------------------------
Name:      TestSub.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: TestSub.cpp,v 1.12 2003/01/22 13:11:30 laghi Exp $
-----------------------------------------------------------------------------*/

#include <client/XmlBlasterAccess.h>
#include <boost/lexical_cast.hpp>
#include <util/XmlBlasterException.h>
#include <util/Timestamp.h>
#include <util/PlatformUtils.hpp>

#include <util/StopWatch.h>
#include <util/Global.h>
#include <util/thread/ThreadImpl.h>

/**
 * This client tests the method subscribe() with a later publish() with XPath
 * query.<br />
 * The subscribe() should be recognized for this later arriving publish()<p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 */

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

using boost::lexical_cast;

namespace org { namespace xmlBlaster {

class TestSub: public client::I_Callback {
private:
   string me() {
      return "Tim";
         log_.info(me(), string("Success: Publishing done, returned oid=") +
                   publishOid_);
         log_.info(me(), string("Success: Publishing done, returned oid=") +
                   publishOid_);
         log_.info(me(), string("Success: Publishing done, returned oid=") +
                   publishOid_);
   }

   bool             messageArrived_; // = false;
   int              numReceived_;    //  = 0;         // error checking
   XmlBlasterAccess *senderConnection_;

   string subscribeOid_;
   string publishOid_; // = "dummy";
   string senderName_;
   string senderContent_;
   string receiverName_;         // sender/receiver is here the same client

   string contentMime_; // = "text/xml";
   string contentMimeExtended_; //  = "1.0";
   Global&          global_;
   util::Log&       log_;
   ConnectReturnQos returnQos_;

   /** Publish tests */
   enum TestType {
      TEST_ONEWAY, TEST_PUBLISH, TEST_ARRAY
   };

   /**
    * Constructs the TestSub object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
 public:
   TestSub(Global& global, const string &loginName)
      : global_(global), log_(global.getLog("test")), returnQos_(global)
   {
      senderName_          = loginName;
      receiverName_        = loginName;
      numReceived_         = 0;
      publishOid_          = "dummy";
      contentMime_         = "text/xml";
      contentMimeExtended_ = "1.0";
      senderContent_       = "Yeahh, i'm the new content";
   }

   virtual ~TestSub() {
      if (senderConnection_ != NULL)
         delete senderConnection_;
   }

   /**
    * Sets up the fixture. <p />
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
         senderConnection_ = new XmlBlasterAccess(global_); // Find orb
         string passwd = "secret";
         SecurityQos secQos(global_, senderName_, passwd);
         ConnectQos connQos(global_);
//         connQos.setSecurityQos(secQos);
         returnQos_ = senderConnection_->connect(connQos, this);
         // Login to xmlBlaster
      }
      catch (XmlBlasterException &e) {
         log_.error(me(), string("Login failed: ") + e.toXml());
         usage();
         assert(0);
      }
   }


   /**
    * Tears down the fixture. <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   void tearDown() {
      //senderConnection_->run();
      log_.info(me(), "Cleaning up test - erasing message.");

      EraseKey eraseKey(global_);
      eraseKey.setOid(publishOid_);
      EraseQos eraseQos(global_);

      vector<EraseReturnQos> retArr;
      try {
         retArr = senderConnection_->erase(eraseKey, eraseQos);
      }
      catch(XmlBlasterException &e) {
         log_.error(me(), string("XmlBlasterException: ") + e.toXml());
      }
      if (retArr.size() != 1) {
         log_.error(me(), "Erased " + lexical_cast<string>(retArr.size()) + " messages");
      }
      // still old fashioned
      senderConnection_->disconnect(DisconnectQos(global_));
   }


   /**
    * TEST: Subscribe to messages with XPATH.<p />
    * The returned subscribeOid is checked
    */
   void testSubscribeXPath() {
      if (log_.TRACE) log_.trace(me(), "Subscribing using XPath syntax ...");
      SubscribeKey subKey(global_);
      subKey.setQueryString("//TestSub-AGENT");
      SubscribeQos subQos(global_);
      numReceived_ = 0;
      subscribeOid_ = "";
      try {
         subscribeOid_ = senderConnection_->subscribe(subKey, subQos).getSubscriptionId();
         log_.info(me(), string("Success: Subscribe subscription-id=") +
                   subscribeOid_ + " done");
      }
      catch(XmlBlasterException &e) {
         log_.warn(me(), string("XmlBlasterException: ")
                      + e.toXml());
         cerr << "subscribe - XmlBlasterException: " << e.toXml() << endl;
         assert(0);
      }
      if (subscribeOid_ == "") {
         cerr << "returned null subscribeOid" << endl;
         assert(0);
      }
      if (subscribeOid_.length() == 0) {
         cerr << "returned subscribeOid is empty" << endl;
         assert(0);
      }
   }


   /**
    * TEST: Construct a message and publish it. <p />
    * The returned publishOid is checked
    */
   void testPublishCorbaMethods(TestType testType) {
      if (log_.TRACE) log_.trace(me(), "Publishing a message (old style) ...");
      numReceived_ = 0;
      PublishKey pubKey(global_);
      pubKey.setOid(publishOid_);
      pubKey.setContentMime(contentMime_);
      pubKey.setContentMimeExtended(contentMimeExtended_);
      string xmlKey = string("") +
         "   <TestSub-AGENT id='192.168.124.10' subId='1' type='generic'>" +
         "      <TestSub-DRIVER id='FileProof' pollingFreq='10'>" +
         "      </TestSub-DRIVER>"+
         "   </TestSub-AGENT>";
      pubKey.setClientTags(xmlKey);

      PublishQos pubQos(global_);
      MessageUnit msgUnit(pubKey, senderContent_, pubQos);
      try {

         if (testType == TEST_ONEWAY) {
            vector<MessageUnit> msgUnitArr;
            msgUnitArr.insert(msgUnitArr.begin(), msgUnit);
            senderConnection_->publishOneway(msgUnitArr);
            //delete msgUnitArr;
            log_.info(me(), string("Success: Publishing oneway done (old style)"));
         }
         else if (testType == TEST_PUBLISH) {
            string tmp = senderConnection_->publish(msgUnit).getKeyOid();
            if (tmp.find(publishOid_) == string::npos) {
               log_.error(me(), "Wrong publishOid: " + tmp);
               assert(0);
            }
            log_.info(me(), string("Success: Publishing with ACK done (old style), returned oid=") +
                      publishOid_);
         }
         else {
            vector<MessageUnit> msgUnitArr;
            msgUnitArr.insert(msgUnitArr.begin(), msgUnit);
            senderConnection_->publishArr(msgUnitArr);
            //delete msgUnitArr;
            log_.info(me(), string("Success: Publishing array done (old style)"));
         }
      }
      catch(XmlBlasterException &e) {
         log_.warn(me(), string("XmlBlasterException: ")+e.toXml());
         assert(0);
      }
   }


   /**
    * TEST: Construct a message and publish it. <p />
    * The returned publishOid is checked
    */
   void testPublishSTLMethods(TestType testType) {
      if (log_.TRACE) log_.trace(me(), "Publishing a message (the STL way) ...");
      numReceived_ = 0;
      string clientTags = string("") +
         "   <TestSub-AGENT id='192.168.124.10' subId='1' type='generic'>" +
         "      <TestSub-DRIVER id='FileProof' pollingFreq='10'>" +
         "      </TestSub-DRIVER>"+
         "   </TestSub-AGENT>";

      PublishKey key(global_, publishOid_, contentMime_, contentMimeExtended_);
      key.setClientTags(clientTags);
      PublishQos pubQos(global_);
      MessageUnit msgUnit(key, senderContent_, pubQos);
      try {
         if (testType == TEST_ONEWAY) {
            vector<MessageUnit> msgVec;
            msgVec.push_back(msgUnit);
            senderConnection_->publishOneway(msgVec);
            log_.info(me(), string("Success: Publishing oneway done (the STL way)"));
         }
         else if (testType == TEST_PUBLISH) {
            string tmp = senderConnection_->publish(msgUnit).getKeyOid();
            log_.info(me(), string("the publish oid ='") + tmp + "'");
         }
         else {
            vector<MessageUnit> msgVec;
            msgVec.push_back(msgUnit);
            vector<PublishReturnQos> retArr = senderConnection_->publishArr(msgVec);
            log_.info(me(), string("Success: Publishing array of size " + lexical_cast<string>(retArr.size())
                                   + " done (the STL way)"));
         }
      }
      catch(XmlBlasterException &e) {
         log_.warn(me(), string("XmlBlasterException: ")+e.toXml());
         assert(0);
      }
   }


   /**
    * TEST: Construct a message and publish it,<br />
    * the previous XPath subscription should match and send an update.
    */
   void testPublishAfterSubscribeXPath() {
      testSubscribeXPath();
      waitOnUpdate(1000L);
      // Wait some time for callback to arrive ...
      if (numReceived_ != 0) {
         log_.error(me(), "numReceived after subscribe = " + lexical_cast<string>(numReceived_));
         assert(0);
      }
/*
      testPublishCorbaMethods(TEST_ONEWAY);
      waitOnUpdate(2000L);
      if (numReceived_ != 1) {
         log_.error(me(),"numReceived after publishing oneway = " + lexical_cast<string>(numReceived_));
         assert(0);
      }

      testPublishCorbaMethods(TEST_PUBLISH);
      waitOnUpdate(2000L);
      if (numReceived_ != 1) {
         log_.error(me(),"numReceived after publishing with ACK = " + lexical_cast<string>(numReceived_));
         assert(0);
      }

      testPublishCorbaMethods(TEST_ARRAY);
      waitOnUpdate(2000L);
      if (numReceived_ != 1) {
         log_.error(me(),"numReceived after publishing with ACK = " + lexical_cast<string>(numReceived_));
         assert(0);
      }
*/
/*
      testPublishSTLMethods(TEST_ONEWAY);
      waitOnUpdate(2000L);
      if (numReceived_ != 1) {
         log_.error(me(),"numReceived after publishing STL oneway = " + lexical_cast<string>(numReceived_));
         assert(0);
      }
*/
      testPublishSTLMethods(TEST_PUBLISH);
      waitOnUpdate(2000L);
      if (numReceived_ != 1) {
         log_.error(me(),"numReceived after publishing STL with ACK = " + lexical_cast<string>(numReceived_));
         assert(0);
      }
/*
      testPublishSTLMethods(TEST_ARRAY);
      waitOnUpdate(2000L);
      if (numReceived_ != 1) {
         log_.error(me(),"numReceived after publishing STL with ACK = " + lexical_cast<string>(numReceived_));
         assert(0);
      }
*/
   }


   /**
    * This is the callback method (I_Callback) invoked from XmlBlasterAccess
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
    */
   string update(const string &sessionId,
               UpdateKey &updateKey,
               void *content, long contentSize,
               UpdateQos &updateQos) {
      log_.info(me(), string("Receiving update of message oid=") +
                updateKey.getOid() + " state=" + updateQos.getState() +
                " authentication sessionId=" + sessionId + " ...");
      numReceived_ ++;

      string contentStr(static_cast<char *>(content), contentSize);

      if (updateQos.getState() != util::Constants::STATE_OK &&
          updateQos.getState() != util::Constants::STATE_ERASED) {
         log_.error(me(), "Unexpected message state=" + updateQos.getState());
         assert(0);
      }

      string name = returnQos_.getSessionQos().getAbsoluteName();
      if (/*senderName_*/ name != updateQos.getSender().getAbsoluteName()) {
         log_.error(me(), string("Wrong Sender, should be: '") + name + "' but is: '" + updateQos.getSender().getAbsoluteName());
         assert(0);
      }
      if (subscribeOid_.find(updateQos.getSubscriptionId()) == string::npos) {
         log_.error(me(), string("engine.qos.update.subscriptionId: ")
                    + "Wrong subscriptionId, expected=" + subscribeOid_ + " received=" + updateQos.getSubscriptionId());
         //assert(0);
      }
      if (publishOid_ != updateKey.getOid()) {
         log_.error(me(), "Wrong oid of message returned");
         assert(0);
      }
      if (senderContent_ != contentStr) {
         log_.error(me(), "Corrupted content expected '" + senderContent_ + "' size=" +
                           lexical_cast<string>(senderContent_.size()) + " but was '" + contentStr +
                           "' size=" + lexical_cast<string>(contentStr.size()) + " and contentSize=" +
                           lexical_cast<string>(contentSize));
         assert(0);
      }
      if (contentMime_ != updateKey.getContentMime()) {
         log_.error(me(), "Message contentMime is corrupted");
         assert(0);
      }
      if (contentMimeExtended_ != updateKey.getContentMimeExtended()) {
         log_.error(me(), "Message contentMimeExtended is corrupted");
         assert(0);
      }
      messageArrived_ = true;

      log_.info(me(), "Success, message arrived as expected.");
      return "<qos><state id='OK'/></qos>";
   }


   /**
    * Little helper, waits until the variable 'messageArrive' is set
    * to true, or returns when the given timeout occurs.
    * @param timeout in milliseconds
    */
private:
   void waitOnUpdate(long timeout) {
      Timestamp delay = timeout;
      Thread::sleep(delay);
/*
      util::StopWatch stopWatch(timeout);
      while (stopWatch.isRunning()) {
         senderConnection_->orbPerformWork();
         if (messageArrived_) {
            messageArrived_ = false;
            return;
         }
      }
*/
      log_.warn(me(), "Timeout of " + lexical_cast<string>(timeout) + " milliseconds occured");
   }

   void usage()
   {
      log_.plain(me(), "----------------------------------------------------------");
      log_.plain(me(), "Testing C++/CORBA access to xmlBlaster with subscribe()");
      log_.plain(me(), "Usage:");
      XmlBlasterAccess::usage();
      log_.usage();
      log_.plain(me(), "Example:");
      log_.plain(me(), "   TestSub -hostname myHost.myCompany.com -port 3412 -trace true");
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
   org::xmlBlaster::TestSub *testSub = new org::xmlBlaster::TestSub(glob, "Tim");
   testSub->setUp(args, argc);
   testSub->testPublishAfterSubscribeXPath();
   testSub->tearDown();
   delete testSub;
   // Log.exit(TestSub.ME, "Good bye");
   return 0;
}

