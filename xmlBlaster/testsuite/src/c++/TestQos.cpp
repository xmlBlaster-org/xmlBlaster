/*-----------------------------------------------------------------------------
Name:      TestQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the ClientProperty Features
-----------------------------------------------------------------------------*/
#include "TestSuite.h"
#include <iostream>
#include <util/qos/SessionQos.h>
#include <util/qos/StatusQosFactory.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;

/**
 * Class test of ClientProperty.cpp
 * @see org::xmlBlaster::util::qos::ClientProperty 
 */

namespace org { namespace xmlBlaster { namespace test {

class TestQos : public TestSuite {
   
private:
   string ME;
   Global& global_;
   I_Log&  log_;
   int     count_;
public:
   TestQos(Global& global, const string& name) 
      : TestSuite(global.getArgs(), global.getArgc(), name, false),
        ME(name), 
        global_(global),
        log_(global.getLog("test")) 
   {
      count_ = 0;
   }

   virtual ~TestQos()
   {
   }

   void testSessionQos() 
   {
      log_.info(ME, "testSessionQos(): Starting tests ...");
      try {
         {
            string qos = "<session name='/node/http:/client/ticheta/3' timeout='86400000' maxSessions='10' \n" +
                 string("         clearSessions='false' reconnectSameClientOnly='false' sessionId='IIOP:01110728321B0222011028'/>\n");
     
            SessionQosFactory factory(global_);
            SessionQosData data = factory.readObject(qos);

            assertEquals(log_, ME, 3L, data.getPubSessionId(), "public sessionId");
            assertEquals(log_, ME, "ticheta", data.getSubjectId(), "subjectId");
            assertEquals(log_, ME, "http:", data.getClusterNodeId(), "http:");
            assertEquals(log_, ME, 86400000L, data.getTimeout(), "timeout");
            assertEquals(log_, ME, 10, data.getMaxSessions(), "maxSessions");
            assertEquals(log_, ME, false, data.getClearSessions(), "clearSessions");
            assertEquals(log_, ME, false, data.getReconnectSameClientOnly(), "reconnectSameClientOnly");
            assertEquals(log_, ME, "IIOP:01110728321B0222011028", data.getSecretSessionId(), "secret sessionId");
         }
      }
      catch(bad_cast b) {
         cout << "EXCEPTION: " << b.what() << endl;
         assert(0);
      }
   }

   void testStatusQos() 
   {
      log_.info(ME, "testSessionQos(): Starting tests ...");
      try {
         {
            StatusQosData data(global_);
            data.setState("OK");
            data.setStateInfo("OK-INFO");
            data.setKeyOid("dummyKey");
            data.setPersistent(true);
            data.setSubscriptionId("subId3");

            assertEquals(log_, ME, "OK", data.getState(), "state");
            assertEquals(log_, ME, "OK-INFO", data.getStateInfo(), "stateInfo");
            assertEquals(log_, ME, "dummyKey", data.getKeyOid(), "key");
            assertEquals(log_, ME, true, data.isPersistent(), "persistent");
            assertEquals(log_, ME, "subId3", data.getSubscriptionId(), "subId");

            StatusQosFactory factory(global_);
            StatusQosData data2 = factory.readObject(data.toXml());

            assertEquals(log_, ME, "OK", data2.getState(), "state");
            assertEquals(log_, ME, "OK-INFO", data2.getStateInfo(), "stateInfo");
            assertEquals(log_, ME, "dummyKey", data2.getKeyOid(), "key");
            assertEquals(log_, ME, true, data2.isPersistent(), "persistent");
            assertEquals(log_, ME, "subId3", data2.getSubscriptionId(), "subId");

         }
      }
      catch(bad_cast b) {
         cout << "EXCEPTION: " << b.what() << endl;
         assert(0);
      }
   }

   void setUp() {
   }

   void tearDown() {
   }
};
   
}}} // namespace



using namespace org::xmlBlaster::test;

int main(int args, char *argv[]) 
{
   Global& glob = Global::getInstance();
   glob.initialize(args, argv);

   TestQos testObj(glob, "TestQos");

   testObj.setUp();
   testObj.testSessionQos();
   testObj.tearDown();

   testObj.setUp();
   testObj.testStatusQos();
   testObj.tearDown();

   return 0;
}


