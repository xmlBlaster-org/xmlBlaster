/*-----------------------------------------------------------------------------
Name:      TestSub.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: TestSub.cpp,v 1.11 2002/06/27 19:42:41 ruff Exp $
-----------------------------------------------------------------------------*/

#include <boost/lexical_cast.hpp>
#include <client/CorbaConnection.h>
#include <util/StopWatch.h>
#include <util/PlatformUtils.hpp>

/**
 * This client tests the method subscribe() with a later publish() with XPath
 * query.<br />
 * The subscribe() should be recognized for this later arriving publish()<p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 */

using namespace std;
using boost::lexical_cast;
namespace org { namespace xmlBlaster {

class TestSub: public I_Callback {
private:
   string me() {
      return "Tim";
   }

   bool            messageArrived_; // = false;
   int             numReceived_;    //  = 0;         // error checking
   CorbaConnection *senderConnection_;
   util::Log       log_;

   string subscribeOid_;
   string publishOid_; // = "dummy";
   string senderName_;
   string senderContent_;
   string receiverName_;         // sender/receiver is here the same client

   string contentMime_; // = "text/xml";
   string contentMimeExtended_; //  = "1.0";

   /**
    * Constructs the TestSub object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
 public:
   TestSub(const string &testName, const string &loginName) : log_() {
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
         senderConnection_ = new CorbaConnection(args, argc); // Find orb
         string passwd = "secret";
         senderConnection_->login(senderName_, passwd, 0, this);
         // Login to xmlBlaster
      }
      catch (CORBA::Exception &e) {
         log_.error(me(), string("Login failed: ") + to_string(e));
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

      string xmlKey = string("<?xml version='1.0' encoding='ISO-8859-1' ?>\n")
         + "<key oid='" + publishOid_ + "' queryType='EXACT'>\n</key>";
      string qos = "<qos></qos>";
      serverIdl::StringArr_var strArr; //  = null;
      try {
         strArr = senderConnection_->erase(xmlKey, qos);
      }
      catch(serverIdl::XmlBlasterException &e) {
         log_.error(me(), string("XmlBlasterException: ") + string(e.reason));
      }
      if (strArr->length() != 1) {
         log_.error(me(), "Erased " + lexical_cast<string>(strArr->length()) + " messages");
      }
      senderConnection_->logout();
   }


   /**
    * TEST: Subscribe to messages with XPATH.<p />
    * The returned subscribeOid is checked
    */
   void testSubscribeXPath() {
      if (log_.TRACE) log_.trace(me(), "Subscribing using XPath syntax ...");
      string xmlKey = string("<?xml version='1.0' encoding='ISO-8859-1' ?>\n")
         + "<key oid='' queryType='XPATH'>\n   //TestSub-AGENT\n</key>";
      string qos = "<qos></qos>";
      numReceived_ = 0;
      subscribeOid_ = "";
      try {
         subscribeOid_ = senderConnection_->subscribe(xmlKey, qos);
         log_.info(me(), string("Success: Subscribe subscription-id=") +
                   subscribeOid_ + " done");
      }
      catch(serverIdl::XmlBlasterException &e) {
         log_.warn(me(), string("XmlBlasterException: ")
                      + string(e.reason));
         cerr << "subscribe - XmlBlasterException: " << string(e.reason)
              << endl;
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
   void testPublish() {
      if (log_.TRACE) log_.trace(me(), "Publishing a message ...");
      numReceived_ = 0;
      string xmlKey = string("<?xml version='1.0' encoding='ISO-8859-1' ?>\n")+
         "<key oid='" + publishOid_ + "' contentMime='" + contentMime_ +
         "' contentMimeExtended='" + contentMimeExtended_ + "'>\n" +
         "   <TestSub-AGENT id='192.168.124.10' subId='1' type='generic'>" +
         "      <TestSub-DRIVER id='FileProof' pollingFreq='10'>" +
         "      </TestSub-DRIVER>"+
         "   </TestSub-AGENT>" +
         "</key>";
      serverIdl::MessageUnit msgUnit;
      msgUnit.xmlKey  = xmlKey.c_str();
      serverIdl::ContentType content(senderContent_.length()+1,
                                     senderContent_.length()+1,
                                     (CORBA::Octet*)senderContent_.c_str());
      msgUnit.content = content;
      try {
         msgUnit.qos = "<qos></qos>";
         string tmp = senderConnection_->publish(msgUnit);
         if (tmp.find(publishOid_) == string::npos) {
            log_.error(me(), "Wrong publishOid: " + tmp);
            assert(0);
         }
         log_.info(me(), string("Success: Publishing done, returned oid=") +
                   publishOid_);
      }
      catch(serverIdl::XmlBlasterException &e) {
         log_.warn(me(), string("XmlBlasterException: ")+string(e.reason));
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
         log_.error(me(), "numReceived after subscribe");
         assert(0);
      }
      testPublish();
      waitOnUpdate(2000L);
      if (numReceived_ != 1) {
         log_.error(me(),"numReceived after publishing");
         assert(0);
      }
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
    */
   string update(const string &sessionId,
               UpdateKey &updateKey,
               void *content, long contentSize,
               UpdateQos &updateQos) {
      log_.info(me(), string("Receiving update of message oid=") +
                updateKey.getUniqueKey() + " state=" + updateQos.getState() + " ...");
      numReceived_ ++;

      if (senderName_ != updateQos.getSender()) {
         log_.error(me(), "Wrong Sender");
         assert(0);
      }
      if (subscribeOid_.find(updateQos.getSubscriptionId()) == string::npos) {
         log_.error(me(), string("engine.qos.update.subscriptionId: ")
                    + "Wrong subscriptionId, expected=" + subscribeOid_ + " received=" + updateQos.getSubscriptionId());
         //assert(0);
      }
      if (publishOid_ != updateKey.getUniqueKey()) {
         log_.error(me(), "Wrong oid of message returned");
         assert(0);
      }
      if (senderContent_ != string((char*)content)) {
         log_.error(me(), "Message content is corrupted: Sent '" + senderContent_ + "' received '" + string((char*)content) + "'");
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
      util::StopWatch stopWatch(timeout);
      while (stopWatch.isRunning()) {
         senderConnection_->orbPerformWork();
         if (messageArrived_) {
            messageArrived_ = false;
            return;
         }
      }
      log_.warn(me(), "Timeout of " + lexical_cast<string>(timeout) + " milliseconds occured");
   }
   void usage()
   {
      log_.plain(me(), "----------------------------------------------------------");
      log_.plain(me(), "Testing C++/CORBA access to xmlBlaster with subscribe()");
      log_.plain(me(), "Usage:");
      CorbaConnection::usage();
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
   org::xmlBlaster::TestSub *testSub = new org::xmlBlaster::TestSub("TestSub", "Tim");
   testSub->setUp(args, argc);
   testSub->testPublishAfterSubscribeXPath();
   testSub->tearDown();
   delete testSub;
   // Log.exit(TestSub.ME, "Good bye");
   return 0;
}

