/*-----------------------------------------------------------------------------
Name:      TestConnect.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login/logout test for xmlBlaster
Version:   $Id: TestConnect.cpp,v 1.1 2002/12/02 22:24:13 laghi Exp $
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

#include <string>
#include <util/Log.h>
#include <client/protocol/corba/CorbaConnection.h>
#include <util/qos/ConnectQos.h>
#include <client/LoginQosWrapper.h>
#include <util/PlatformUtils.hpp>
//#include <unistd.h>
#include <util/StopWatch.h>

using namespace std;
using org::xmlBlaster::client::protocol::corba::CorbaConnection;
using namespace org::xmlBlaster::util::qos;

namespace org { namespace xmlBlaster {

class TestConnect : public virtual I_Callback {

private:
   string me() {
      return "connect";
   }

   string                 publishReturnQos, secondOid_;
   string                 oid_;
   string                 qos1_, qos2_;
   string                 senderContent_;
   CorbaConnection        *conn1_, *conn2_;
   serverIdl::MessageUnit *msgUnit_;     // a message to play with

   int       numReceived_; // error checking
   string    contentMime_;
   string    contentMimeExtended_;
   util::Log *log_;
   util::StopWatch stopWatch_;

public:
   /**
    * Constructs the TestLogin object.
    * <p />
    * @param testName   The name used in the test suite
    * @param loginName  The name to login to the xmlBlaster
    * @param secondName The name to login to the xmlBlaster again
    */
   TestConnect(const string &qos1,
               const string &qos2) : stopWatch_() {
      qos1_                = qos1;
      qos2_                = qos2;
      publishReturnQos     = "";
      secondOid_           = "SecondOid";
      oid_                 = "TestLogin";
      numReceived_         = 0;
      contentMime_         = "text/plain";
      log_                 = NULL;
      contentMimeExtended_ = "1.0";
      msgUnit_             = NULL;
      conn1_               = conn2_ = NULL;
   }

   ~TestConnect() {
      delete conn1_;
      delete conn2_;
      delete log_;
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
               void * /*content*/, long /*contentSize*/,
               UpdateQos &/*updateQos*/) {
      if (log_->CALL) log_->call(me(), "Receiving update of a message ...");
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
         if (!log_) log_   = new util::Log(args, argc);
         if (conn1_) delete conn1_;
         conn1_ = new CorbaConnection(args, argc); // Find orb
         ConnectQosFactory factory(args, argc);
         ConnectQos connectQos1 = factory.readObject(qos1_);
         conn1_->connect(connectQos1);

         // Login to xmlBlaster
         if (conn2_) delete conn2_;
         ConnectQos connectQos2 = factory.readObject(qos2_);
         conn2_ = new CorbaConnection(args, argc); // Find orb
         conn2_->connect(connectQos2);

      }
      catch (CORBA::Exception &e) {
         log_->error(me(), to_string(e));
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
      conn1_->logout();
      conn2_->logout();
   }

private:
   void usage()
   {
      util::Log log_;
      log_.plain(me(), "----------------------------------------------------------");
      log_.plain(me(), "Testing C++/CORBA access to xmlBlaster");
      log_.plain(me(), "Usage:");
      CorbaConnection::usage();
      log_.usage();
      log_.plain(me(), "Example:");
      log_.plain(me(), "   TestLogin -ior.file /tmp/ior.dat -trace true");
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

   org::xmlBlaster::TestConnect
    *testConnect = new org::xmlBlaster::TestConnect(qos1, qos2);
   testConnect->setUp(args, argc);
   testConnect->tearDown();
   delete testConnect;
   return 0;
}

