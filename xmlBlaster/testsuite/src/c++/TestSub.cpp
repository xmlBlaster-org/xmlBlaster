/*-----------------------------------------------------------------------------
Name:      TestSub.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: TestSub.cpp,v 1.21 2003/05/04 21:33:20 laghi Exp $
-----------------------------------------------------------------------------*/

#include "TestSuite.h"

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


namespace org { namespace xmlBlaster { namespace test {

class TestSub: public TestSuite, public virtual I_Callback 
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
   TestSub(int args, char *argc[], const string &loginName)
      : TestSuite(args, argc, "TestSub"), returnQos_(global_)
   {
      senderName_          = loginName;
      receiverName_        = loginName;
      numReceived_         = 0;
      publishOid_          = "dummy";
      contentMime_         = "text/xml";
      contentMimeExtended_ = "1.0";
      senderContent_       = "Yeahh, i'm the new content";
   }

   virtual ~TestSub() 
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
         SecurityQos secQos(global_, senderName_, passwd);
         ConnectQos connQos(global_);
         returnQos_ = connection_.connect(connQos, this);
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
      connection_.disconnect(DisconnectQos(global_));
      TestSuite::tearDown();
   }


   /**
    * TEST: Subscribe to messages with XPATH.<p />
    * The returned subscribeOid is checked
    */
   void testSubscribeXPath() 
   {
      if (log_.trace()) log_.trace(ME, "Subscribing using XPath syntax ...");
      SubscribeKey subKey(global_);
      subKey.setQueryString("//TestSub-AGENT");
      SubscribeQos subQos(global_);
      numReceived_ = 0;
      subscribeOid_ = "";
      try {
         subscribeOid_ = connection_.subscribe(subKey, subQos).getSubscriptionId();
         log_.info(ME, string("Success: Subscribe subscription-id=") +
                   subscribeOid_ + " done");
      }
      catch(XmlBlasterException &e) {
         log_.warn(ME, string("XmlBlasterException: ")
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
   void testPublishCorbaMethods(TestType testType) 
   {
      if (log_.trace()) log_.trace(ME, "Publishing a message (old style) ...");
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
            connection_.publishOneway(msgUnitArr);
            log_.info(ME, string("Success: Publishing oneway done (old style)"));
         }
         else if (testType == TEST_PUBLISH) {
            string tmp = connection_.publish(msgUnit).getKeyOid();
            if (tmp.find(publishOid_) == string::npos) {
               log_.error(ME, "Wrong publishOid: " + tmp);
               assert(0);
            }
            log_.info(ME, string("Success: Publishing with ACK done (old style), returned oid=") +
                      publishOid_);
         }
         else {
            vector<MessageUnit> msgUnitArr;
            msgUnitArr.insert(msgUnitArr.begin(), msgUnit);
            connection_.publishArr(msgUnitArr);
            log_.info(ME, string("Success: Publishing array done (old style)"));
         }
      }
      catch(XmlBlasterException &e) {
         log_.warn(ME, string("XmlBlasterException: ")+e.toXml());
         assert(0);
      }
   }


   /**
    * TEST: Construct a message and publish it. <p />
    * The returned publishOid is checked
    */
   void testPublishSTLMethods(TestType testType) 
   {
      if (log_.trace()) log_.trace(ME, "Publishing a message (the STL way) ...");
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
            connection_.publishOneway(msgVec);
            log_.info(ME, string("Success: Publishing oneway done (the STL way)"));
         }
         else if (testType == TEST_PUBLISH) {
            string tmp = connection_.publish(msgUnit).getKeyOid();
            log_.info(ME, string("the publish oid ='") + tmp + "'");
         }
         else {
            vector<MessageUnit> msgVec;
            msgVec.push_back(msgUnit);
            vector<PublishReturnQos> retArr = connection_.publishArr(msgVec);
            log_.info(ME, string("Success: Publishing array of size " + lexical_cast<string>(retArr.size())
                                   + " done (the STL way)"));
         }
      }
      catch(XmlBlasterException &e) {
         log_.warn(ME, string("XmlBlasterException: ")+e.toXml());
         assert(0);
      }
   }


   /**
    * TEST: Construct a message and publish it,<br />
    * the previous XPath subscription should match and send an update.
    */
   void testPublishAfterSubscribeXPath() 
   {
      testSubscribeXPath();
      waitOnUpdate(1000L);
      // Wait some time for callback to arrive ...
      if (numReceived_ != 0) {
         log_.error(ME, "numReceived after subscribe = " + lexical_cast<string>(numReceived_));
         assert(0);
      }
/*
      testPublishCorbaMethods(TEST_ONEWAY);
      waitOnUpdate(2000L);
      if (numReceived_ != 1) {
         log_.error(ME,"numReceived after publishing oneway = " + lexical_cast<string>(numReceived_));
         assert(0);
      }

      testPublishCorbaMethods(TEST_PUBLISH);
      waitOnUpdate(2000L);
      if (numReceived_ != 1) {
         log_.error(ME,"numReceived after publishing with ACK = " + lexical_cast<string>(numReceived_));
         assert(0);
      }

      testPublishCorbaMethods(TEST_ARRAY);
      waitOnUpdate(2000L);
      if (numReceived_ != 1) {
         log_.error(ME,"numReceived after publishing with ACK = " + lexical_cast<string>(numReceived_));
         assert(0);
      }
*/
/*
      testPublishSTLMethods(TEST_ONEWAY);
      waitOnUpdate(2000L);
      if (numReceived_ != 1) {
         log_.error(ME,"numReceived after publishing STL oneway = " + lexical_cast<string>(numReceived_));
         assert(0);
      }
*/
      testPublishSTLMethods(TEST_PUBLISH);
      waitOnUpdate(2000L);
      if (numReceived_ != 1) {
         log_.error(ME,"numReceived after publishing STL with ACK = " + lexical_cast<string>(numReceived_));
         assert(0);
      }
/*
      testPublishSTLMethods(TEST_ARRAY);
      waitOnUpdate(2000L);
      if (numReceived_ != 1) {
         log_.error(ME,"numReceived after publishing STL with ACK = " + lexical_cast<string>(numReceived_));
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
               UpdateQos &updateQos) 
   {
      log_.info(ME, string("Receiving update of message oid=") +
                updateKey.getOid() + " state=" + updateQos.getState() +
                " authentication sessionId=" + sessionId + " ...");
      numReceived_ ++;

      string contentStr(static_cast<char *>(content), contentSize);

      if (updateQos.getState() != Constants::STATE_OK &&
          updateQos.getState() != org::xmlBlaster::util::Constants::STATE_ERASED) {
         log_.error(ME, "Unexpected message state=" + updateQos.getState());
         assert(0);
      }

      string name = returnQos_.getSessionQos().getAbsoluteName();
      if (/*senderName_*/ name != updateQos.getSender().getAbsoluteName()) {
         log_.error(ME, string("Wrong Sender, should be: '") + name + "' but is: '" + updateQos.getSender().getAbsoluteName());
         assert(0);
      }
      if (subscribeOid_.find(updateQos.getSubscriptionId()) == string::npos) {
         log_.error(ME, string("engine.qos.update.subscriptionId: ")
                    + "Wrong subscriptionId, expected=" + subscribeOid_ + " received=" + updateQos.getSubscriptionId());
         //assert(0);
      }
      if (publishOid_ != updateKey.getOid()) {
         log_.error(ME, "Wrong oid of message returned");
         assert(0);
      }

      if (updateQos.getState() == Constants::STATE_OK && senderContent_ != contentStr) {
         log_.error(ME, "Corrupted content expected '" + senderContent_ + "' size=" +
                           lexical_cast<string>(senderContent_.size()) + " but was '" + contentStr +
                           "' size=" + lexical_cast<string>(contentStr.size()) + " and contentSize=" +
                           lexical_cast<string>(contentSize));
         assert(0);
      }
      if (contentMime_ != updateKey.getContentMime()) {
         log_.error(ME, "Message contentMime is corrupted");
         assert(0);
      }
      if (contentMimeExtended_ != updateKey.getContentMimeExtended()) {
         log_.error(ME, "Message contentMimeExtended is corrupted");
         assert(0);
      }
      messageArrived_ = true;

      log_.info(ME, "Success, message oid=" + updateKey.getOid() + " state=" + updateQos.getState() + " arrived as expected.");
      return "<qos><state id='OK'/></qos>";
   }


   /**
    * Little helper, waits until the variable 'messageArrive' is set
    * to true, or returns when the given timeout occurs.
    * @param timeout in milliseconds
    */
private:
   void waitOnUpdate(long timeout) {
      long delay = timeout;
      Thread::sleep(delay);
/*
      util::StopWatch stopWatch(timeout);
      while (stopWatch.isRunning()) {
         connection_.orbPerformWork();
         if (messageArrived_) {
            messageArrived_ = false;
            return;
         }
      }
*/
      log_.warn(ME, "Timeout of " + lexical_cast<string>(timeout) + " milliseconds occured");
   }

   void usage() const
   {
      TestSuite::usage();
      log_.plain(ME, "----------------------------------------------------------");
      log_.plain(ME, "Testing C++/CORBA access to xmlBlaster with subscribe()");
      log_.plain(ME, "Usage:");
      XmlBlasterAccess::usage();
      log_.usage();
      log_.plain(ME, "Example:");
      log_.plain(ME, "   TestSub -hostname myHost.myCompany.com -port 3412 -trace true");
      log_.plain(ME, "----------------------------------------------------------");
   }
};

}}} // namespace

using namespace org::xmlBlaster::test;

int main(int args, char *argc[]) 
{
   try {
      org::xmlBlaster::util::Object_Lifetime_Manager::init();
      TestSub testSub(args, argc, "Tim");
      testSub.setUp();
      testSub.testPublishAfterSubscribeXPath();
      testSub.tearDown();
      Thread::sleepSecs(1);
      org::xmlBlaster::util::Object_Lifetime_Manager::fini();
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

   return 0;
}

