/*-----------------------------------------------------------------------------
Name:      TestDoubleGlobal.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the Timeout Features
-----------------------------------------------------------------------------*/
#include <util/qos/ConnectQos.h>
#include <util/qos/ConnectQosFactory.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include "TestSuite.h"
#include <iostream>

namespace org { namespace xmlBlaster { namespace test {

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::qos::storage;
using namespace org::xmlBlaster::util::qos::address;

class TestDoubleGlobal
{
private:
   string  ME;
   Global& global_;
   I_Log&  log_;
   Global  *global2_;

public:
   TestDoubleGlobal(Global& glob) 
      : ME("TestDoubleGlobal"), 
        global_(glob), 
        log_(glob.getLog("test"))
   {
   }


   void tearDown()
   {
      delete global2_;
      global2_ = NULL; 
   }

   virtual ~TestDoubleGlobal()
   {
   }

   void setUp()
   {
      global2_ = new Global();
      global2_->initialize();                                
      bool overwrite = true;
      global2_->getProperty().setProperty("session.name", "pinco/2", overwrite);
   }


   void sessionQos(Global& glob, const string& shouldSessionName)
   {

      string me = ME + ".testSessionQos";
      log_.info(me, "testing creation, parsing and output of SessionQos: start");

       string qos = string("<session name='/node/http:/client/ticheta/-3' timeout='86400000' maxSessions='10' \n") +
                    "         clearSessions='false' sessionId='IIOP:01110728321B0222011028'/>\n";

      SessionQosFactory factory(glob);

      for (int i=0; i<2; i++) {
         SessionQosData data = factory.readObject(qos);
         assertEquals(log_, me, string("/node/http:/client/ticheta/-3"), data.getAbsoluteName(), "absolute name check");
         assertEquals(log_, me, (long)86400000l, data.getTimeout(), "timeout check");
         assertEquals(log_, me, 10, data.getMaxSessions(), "maxSessions check");
         assertEquals(log_, me, false, data.getClearSessions(), "clearSessions check");
         assertEquals(log_, me, string("IIOP:01110728321B0222011028"), data.getSecretSessionId(), "sessionId check");
         SessionQosData ref(glob);
         ref.setAbsoluteName("/node/http:/client/ticheta/-3");
         ref.setTimeout(86400000l);
         ref.setMaxSessions(10);
         ref.setClearSessions(false);
         ref.setSecretSessionId("IIOP:01110728321B0222011028");
         string lit1 = data.toXml();
         string lit2 = ref.toXml();
         if (log_.trace()) {
            log_.trace(me, string("xml is: ") + lit1);
            log_.trace(me, string("xml should be: ") + lit2);
         }
         assertEquals(log_, me, lit2, lit1, "sessionId check");
      }

      // make sure the property 'session.name' has not been set on the command line or on the prop. file
      string name = glob.getProperty().getStringProperty("session.name", "");
      assertEquals(log_, me, shouldSessionName, name, "non setting of property 'session.name'");
      SessionQosData data1(glob, "Fritz");
      assertEquals(log_, me, string("client/Fritz"), data1.getAbsoluteName(), "checking constructor with 'user' and 'pubSessionId=0'");

      glob.getProperty().setProperty("user", "PincoPallino");
      name = glob.getProperty().getStringProperty("user", "");
      assertEquals(log_, me, string("PincoPallino"), name, "checking if property 'user' has been set correctly");

      // set the property now
      glob.getProperty().setProperty("session.name", "/node/australia/client/Martin/4");
      name = glob.getProperty().getStringProperty("session.name", "");
      assertEquals(log_, me, string("/node/australia/client/Martin/4"), name, "checking if property 'session.name' has been set correctly");
      data1 = SessionQosData(glob, "Nisse/3", 0);
      assertEquals(log_, me, string("/node/australia/client/Martin/4"), name, "checking when 'session.name' is strongest");

      data1 = SessionQosData(glob);
      data1.setAbsoluteName("/node/frodo/client/whoMore/3");
      assertEquals(log_, me, string("/node/frodo/client/whoMore/3"), data1.getAbsoluteName(), "checking when 'session.name' is weaker");
      log_.info(me, "testing creation, parsing and output of SessionQos: end");
   }

   void testSessionQos() {
      sessionQos(global_, string(""));
      sessionQos(*global2_, string("pinco/2"));
   }


};

}}} // namespace 


using namespace org::xmlBlaster::test;

/**
 * Try
 * <pre>
 *   java TestDoubleGlobal -help
 * </pre>
 * for usage help
 */
int main(int args, char ** argv)
{
   try {
      org::xmlBlaster::util::Object_Lifetime_Manager::init();
      Global& glob = Global::getInstance();
      glob.initialize(args, argv);

      TestDoubleGlobal testConnectQos(glob);

      testConnectQos.setUp();
      testConnectQos.testSessionQos();
      testConnectQos.tearDown();

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
