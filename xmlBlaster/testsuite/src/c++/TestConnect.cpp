/*-----------------------------------------------------------------------------
Name:      TestConnect.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login/logout test for xmlBlaster
Version:   $Id: TestConnect.cpp,v 1.14 2003/02/13 14:00:32 ruff Exp $
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

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::client;

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
                 void * /*content*/, 
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
         ConnectQosFactory factory(global_);
         ConnectQos connectQos1 = factory.readObject(qos1_);
         connection_.connect(connectQos1, NULL);

         // Login to xmlBlaster
         if (conn2_) delete conn2_;
         ConnectQos connectQos2 = factory.readObject(qos2_);
         conn2_ = new XmlBlasterAccess(global_);
         conn2_->connect(connectQos2, NULL);
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
      conn2_->disconnect(DisconnectQos(global_));
   }

};

}}} // namespace

using namespace org::xmlBlaster::test;

int main(int args, char *argc[]) {

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
   testConnect->tearDown();
   delete testConnect;
   return 0;
}

