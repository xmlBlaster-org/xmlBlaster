/*-----------------------------------------------------------------------------
Name:      TestQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the ClientProperty Features
-----------------------------------------------------------------------------*/
#include "TestSuite.h"
#include <iostream>
#include <util/qos/SessionQos.h>

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

   return 0;
}


