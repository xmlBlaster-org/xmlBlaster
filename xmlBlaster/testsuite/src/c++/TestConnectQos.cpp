/*-----------------------------------------------------------------------------
Name:      TestConnectQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the Timeout Features
-----------------------------------------------------------------------------*/

#include <util/qos/ConnectQos.h>
#include <util/qos/ConnectQosFactory.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include <util/Log.h>
#include <util/PlatformUtils.hpp>
#include <boost/lexical_cast.hpp>
#include "testSuite.h"


/**
 *
 */

using boost::lexical_cast;
using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster;
using namespace org::xmlBlaster::util::qos::storage;

class TestConnectQos
{
private:
   string  ME;
   Global& global_;
   Log&    log_;

public:
   TestConnectQos(Global& glob) 
      : ME("TestConnectQos"), 
        global_(glob), 
        log_(glob.getLog("test"))
   {
   }


   void tearDown()
   {
   }

   virtual ~TestConnectQos()
   {
   }

   void setUp()
   {
   }


   void testSessionQos()
   {

      string me = ME + ".testSessionQos";
      log_.info(me, "testing creation, parsing and output of SessionQos: start");

       string qos = string("<session name='/node/http:/client/ticheta/-3' timeout='86400000' maxSessions='10' \n") +
                    "         clearSessions='false' sessionId='IIOP:01110728321B0222011028'/>\n";

      SessionQosFactory factory(global_);

      for (int i=0; i<2; i++) {
         SessionQosData data = factory.readObject(qos);
         assertEquals(log_, me, string("/node/http:/client/ticheta/-3"), data.getAbsoluteName(), "absolute name check");
         assertEquals(log_, me, (long)86400000l, data.getTimeout(), "timeout check");
         assertEquals(log_, me, 10, data.getMaxSessions(), "maxSessions check");
         assertEquals(log_, me, false, data.getClearSessions(), "clearSessions check");
         assertEquals(log_, me, string("IIOP:01110728321B0222011028"), data.getSecretSessionId(), "sessionId check");
         SessionQosData ref(global_);
         ref.setAbsoluteName("/node/http:/client/ticheta/-3");
         ref.setTimeout(86400000l);
         ref.setMaxSessions(10);
         ref.setClearSessions(false);
         ref.setSecretSessionId("IIOP:01110728321B0222011028");
         string lit1 = data.toXml();
         string lit2 = ref.toXml();
         if (log_.TRACE) {
            log_.trace(me, string("xml is: ") + lit1);
            log_.trace(me, string("xml should be: ") + lit2);
         }
         assertEquals(log_, me, lit2, lit1, "sessionId check");
      }

      // make sure the property 'session.name' has not been set on the command line or on the prop. file
      string name = global_.getProperty().getStringProperty("session.name", "");
      assertEquals(log_, me, string(""), name, "non setting of property 'session.name'");
      SessionQosData data1(global_, "Fritz", 0);
      assertEquals(log_, me, string("client/Fritz"), data1.getAbsoluteName(), "checking constructor with 'user' and 'pubSessionId=0'");
      data1 = SessionQosData(global_, "Franz", -3);
      assertEquals(log_, me, string("client/Franz/-3"), data1.getAbsoluteName(), "checking constructor with relative name");

      global_.getProperty().setProperty("user", "PincoPallino");
      name = global_.getProperty().getStringProperty("user", "");
      assertEquals(log_, me, string("PincoPallino"), name, "checking if property 'user' has been set correctly");

      data1 = SessionQosData(global_, "", 6);
      assertEquals(log_, me, string("client/PincoPallino/6"), data1.getAbsoluteName(), "checking constructor with empty defaultUserName when 'user' set");

      data1 = SessionQosData(global_, "Nisse", 0);
      assertEquals(log_, me, string("client/Nisse"), data1.getAbsoluteName(), "checking constructor with set defaultUserName when 'user' set");

      
      // set the property now
      global_.getProperty().setProperty("session.name", "/node/australia/client/Martin/4");
      name = global_.getProperty().getStringProperty("session.name", "");
      assertEquals(log_, me, string("/node/australia/client/Martin/4"), name, "checking if property 'session.name' has been set correctly");
      data1 = SessionQosData(global_, "Nisse/3", 0);
      assertEquals(log_, me, string("/node/australia/client/Martin/4"), name, "checking when 'session.name' is strongest");

      data1 = SessionQosData(global_);
      data1.setAbsoluteName("/node/frodo/client/whoMore/3");
      assertEquals(log_, me, "/node/frodo/client/whoMore/3", data1.getAbsoluteName(), "checking when 'session.name' is weaker");
      log_.info(me, "testing creation, parsing and output of SessionQos: end");
   }


   void testQueueProperty()
   {
      string me = ME + "::testQueueProperty";
      log_.info(me, "testing queue properties parsing: start");

      string qos = string("<queue relating='client' storeSwapLevel='1468006' storeSwapBytes='524288' ") +
                   string("reloadSwapLevel='629145' reloadSwapBytes='524288'>\n") + 
                   string("  <address type='IOR' hostname='127.0.0.2' dispatchPlugin='undef'>") + 
                   string("http://127.0.0.2:3412</address>\n") +
                   string("</queue>\n");
      
      QueuePropertyFactory factory(global_);
      for (int i=0; i < 2; i++) {
         QueuePropertyBase propBase = factory.readObject(qos);
         QueueProperty prop(propBase);
         assertEquals(log_, me, string("client"), prop.getRelating(), "relating check");
         assertEquals(log_, me, 1468006L, prop.getStoreSwapLevel(), "storeSwapLevel check");
         assertEquals(log_, me, 524288L, prop.getStoreSwapBytes(), "storeSwapBytes check");
         assertEquals(log_, me, 629145L, prop.getReloadSwapLevel(), "reloadSwapLevel check");
         assertEquals(log_, me, 524288L, prop.getReloadSwapBytes(), "reloadSwapBytes check");

         AddressBase address = prop.getCurrentAddress();
         assertEquals(log_, me, string("IOR"), address.getType(), "address type check");
         assertEquals(log_, me, string("127.0.0.2"), address.getHostname(), "address hostname check");
         assertEquals(log_, me, string("undef"), address.getDispatchPlugin(), "address dispatch Plugin check");

         if (log_.TRACE) log_.trace(me, string("the queue property literal: ") + prop.toXml());
      }
      log_.info(me, "testing queue properties parsing: end");
   }


   void testConnectQos()
   {
      string me = ME + "::testConnectQos";
      log_.info(me, "testing parsing of a return connect qos: start");

      string qos = 
          string("<qos>\n") +
          string("  <securityService type='htpasswd' version='1.0'><![CDATA[\n") +
          string("   <user>Tim</user>\n") +
          string("   <passwd>secret</passwd>\n") +
          string("  ]]></securityService>\n") +
          string("  <ptp>false</ptp>\n") +
          string("  <duplicateUpdates>false</duplicateUpdates>\n") +
          string("  <session name='/node/http_127_0_0_2_3412/client/Tim/-3' timeout='86400000' maxSessions='10' clearSessions='false' sessionId='IIOP:01110C332A141532012A0F'/>\n") +
          string("  <queue relating='client' storeSwapLevel='1468006' storeSwapBytes='524288' reloadSwapLevel='629145' reloadSwapBytes='524288'>\n") +
          string("   <address type='IOR' hostname='127.0.0.2' dispatchPlugin='undef'>\n") +
          string("      http://127.0.0.2:3412\n") +
          string("   </address>\n") +
          string("  </queue>\n") +
          string("  <queue relating='callback' type='CACHE' version='1.0' maxMsg='10000000' storeSwapLevel='1468006' storeSwapBytes='524288' reloadSwapLevel='629145' reloadSwapBytes='524288'>\n") +
          string("   <callback type='IOR' hostname='127.0.0.1' dispatchPlugin='undef'>\n") +
          string("      IOR:010000004000000049444c3a6f72672e786d6c426c61737465722e70726f746f636f6c2e636f7262612f636c69656e7449646c2f426c617374657243616c6c6261636b3a312e300002000000000000002f000000010100000c0000006c696e75782e6c6f63616c00a6820000130000002f353936372f313034323232363530392f5f30000100000024000000010000000100000001000000140000000100000001000100000000000901010000000000\n") +
          string("   </callback>\n") +
          string("  </queue>\n") +
          string("  <serverRef type='IOR'>\n") +
          string("  IOR:000000000000003749444c3a6f72672e786d6c426c61737465722e70726f746f636f6c2e636f7262612f73657276657249646c2f5365727665723a312e300000000000030000000000000043000100000000000a3132372e302e302e320082980000002b5374616e64617264496d706c4e616d652f786d6c426c61737465722d504f412f01110c332a141532012a0f000000000000000048000101000000000a3132372e302e302e320082980000002b5374616e64617264496d706c4e616d652f786d6c426c61737465722d504f412f01110c332a141532012a0f0000000000000000010000002c0000000000000001000000010000001c00000000000100010000000105010001000101090000000105010001\n") +
          string("  </serverRef>\n") +
          string(" </qos>\n");

      ConnectQosFactory factory(global_);
      for (int i=0; i < 2; i++) {
                         ConnectQos connQos = factory.readObject(qos);
         log_.info(me, string("connect qos: ") + connQos.toXml());
      }
   }

};

/**
 * Try
 * <pre>
 *   java TestConnectQos -help
 * </pre>
 * for usage help
 */
int main(int args, char ** argv)
{
   try {
      XMLPlatformUtils::Initialize();
      Global& glob = Global::getInstance();
      glob.initialize(args, argv);

      TestConnectQos testConnectQos(glob);

      testConnectQos.setUp();
      testConnectQos.testSessionQos();
      testConnectQos.tearDown();
      testConnectQos.setUp();
      testConnectQos.testQueueProperty();
      testConnectQos.tearDown();
      testConnectQos.setUp();
      testConnectQos.testConnectQos();
      testConnectQos.tearDown();
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
