/*-----------------------------------------------------------------------------
Name:      TestRequestResponse.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
           build -DexeName=TestRequestResponse cpp-test-single
-----------------------------------------------------------------------------*/
#include "TestSuite.h"
#include <iostream>

/**
 * This client tests the request/reply pattern
 */

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
 * Callback implementation of the receiver client. 
 */
class ReceiverCallback : public I_Callback {
private:
   XmlBlasterAccess &receiver_;
   string name_;
   I_Log& log_;

public:
   ReceiverCallback(XmlBlasterAccess &receiver, I_Log& log, const string& name) : receiver_(receiver), log_(log) {
      name_ = name;
   }

   string update(const string &sessionId,
               UpdateKey &updateKey,
               const unsigned char * /*content*/, long /*contentSize*/,
               UpdateQos &updateQos) 
   {
      log_.info("update", string("Receiving update on callback '") + name_ + "' of message oid=" +
                updateKey.getOid() + " state=" + updateQos.getState() +
                " authentication sessionId=" + sessionId + " ...");

      //if (updateKey.isInternal()) return "";
      if (updateQos.isErased()) return "";
      
      try {
         string tempTopicOid = updateQos.getClientProperty(Constants::JMS_REPLY_TO, string("")); // __jms:JMSReplyTo
         log_.info("update", name_+": Got request, using topic '" + tempTopicOid + "' for response");

         // Send reply back ...
         PublishKey pk(receiver_.getGlobal(), tempTopicOid, "text/plain", "1.0");
         PublishQos pq(receiver_.getGlobal());
         MessageUnit msgUnit(pk, "On doubt no ultimate truth, my dear.", pq);
         PublishReturnQos retQos = receiver_.publish(msgUnit);
         log_.info("update", name_+": Published reply message using temporary topic " + retQos.getKeyOid());
      }
      catch (XmlBlasterException &e) {
         log_.error("update", string(name_)+": Sending reply to " + updateQos.getSender()->getAbsoluteName() + " failed: " + e.getMessage());
      }
      return "<qos><state id='OK'/></qos>";
   }
};


class TestRequestResponse: public TestSuite, public virtual I_Callback 
{
private:
   bool   messageArrived_;      // = false;
   string subscribeOid_;
   string publishOid_;          // = "dummy";
   string senderName_;
   string receiverName_;        // sender/receiver is here the same client
   ConnectReturnQos returnQos_;
   ReceiverCallback *cbReceiver;

   GlobalRef globalReceiver_;
   XmlBlasterAccess receiver_;

   /**
    * Constructs the TestRequestResponse object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
 public:
   TestRequestResponse(int args, char *argc[], const string &senderName, const string &receiverName)
      : TestSuite(args, argc, "TestRequestResponse"),
        returnQos_(global_),
        globalReceiver_(global_.createInstance("receiverGlobal")),
	receiver_(globalReceiver_)
   {
      globalReceiver_->initialize(args, argc);
      senderName_          = senderName;
      receiverName_        = receiverName;
      publishOid_          = "dummy";
      cbReceiver = new ReceiverCallback(receiver_, log_, "callbackReceiver");
   }

   virtual ~TestRequestResponse() 
   {
      delete cbReceiver;
   }

   /**
    * Sets up the fixture. <p />
    * Connect to xmlBlaster and login
    */
   void setUp() 
   {
      log_.info(ME, "Trying to connect to xmlBlaster with C++ client lib " + Global::getVersion() + " from " + Global::getBuildTimestamp());
      TestSuite::setUp();
      const string passwd = "secret";
      try {
         ConnectQos connQos(global_, senderName_);
         returnQos_ = connection_.connect(connQos, this);
         log_.info(ME, string("connected '") + senderName_ + "'");
      }
      catch (XmlBlasterException &e) {
         log_.error(ME, string("Login failed: ") + e.toXml());
         usage();
         assert(0);
      }

      try {
         ConnectQos connQos(receiver_.getGlobal(), receiverName_);
         returnQos_ = receiver_.connect(connQos, cbReceiver);
         log_.info(ME, string("connected '") + receiverName_ + "'");
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
      log_.info(ME, "Cleaning up test");
      connection_.disconnect(DisconnectQos(global_));
      receiver_.disconnect(DisconnectQos(receiver_.getGlobal()));
      TestSuite::tearDown();
   }

   /**
    * TEST
    */
   void testRequest() 
   {
      if (log_.trace()) log_.trace(ME, "Publishing a message (old style) ...");
      PublishKey pubKey(global_, "requestForEnlightenment");
      PublishQos pubQos(global_);
      Destination destination(global_, receiverName_);
      pubQos.addDestination(destination);
      MessageUnit msgUnit(pubKey, "Tell me the truth!", pubQos);
      try {
         std::vector<org::xmlBlaster::util::MessageUnit> replies = 
	    connection_.request(msgUnit, 6000, 1);
	 assertEquals(log_, ME, 1, replies.size(), "missing response");
         log_.info(ME, senderName_+": Got " + lexical_cast<string>(replies.size()) + " reply :\n" + replies[0].toXml());
         log_.info(ME, replies[0].getContentStr() + " size=" + lexical_cast<string>(replies[0].getContentLen()));
	 assertEquals(log_, ME, "On doubt no ultimate truth, my dear.", replies[0].getContentStr(), "response from a wise man");
      }
      catch(XmlBlasterException &e) {
         log_.warn(ME, string("XmlBlasterException: ")+e.toXml());
         assert(0);
      }
   }

   string update(const string &sessionId,
               UpdateKey &updateKey,
               const unsigned char *content, long contentSize,
               UpdateQos &updateQos) 
   {
      log_.error(ME, string("Unexpected receiving update of message oid=") +
                updateKey.getOid() + " state=" + updateQos.getState() +
                " authentication sessionId=" + sessionId + " ...");
      return "<qos><state id='OK'/></qos>";
   }


   /**
    * Little helper, waits until the variable 'messageArrive' is set
    * to true, or returns when the given timeout occurs.
    * @param timeout in milliseconds
    */
private:
   void usage() const
   {
      TestSuite::usage();
      log_.plain(ME, "----------------------------------------------------------");
      log_.plain(ME, "Testing C++ request/reply pattern");
      log_.plain(ME, "Usage:");
      XmlBlasterAccess::usage();
      log_.usage();
      log_.plain(ME, "Example:");
      log_.plain(ME, "   TestRequestResponse -trace true");
      log_.plain(ME, "----------------------------------------------------------");
   }
};

}}} // namespace

using namespace org::xmlBlaster::test;

int main(int args, char *argc[]) 
{
   try {
      org::xmlBlaster::util::Object_Lifetime_Manager::init();
      TestRequestResponse testReq(args, argc, "TheDesperate", "TheKnowing");
 
      testReq.setUp();
      testReq.testRequest();
      testReq.tearDown();

      Thread::sleepSecs(1);
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


