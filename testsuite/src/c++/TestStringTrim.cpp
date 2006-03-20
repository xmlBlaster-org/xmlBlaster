/*-----------------------------------------------------------------------------
Name:      TestStringTrim.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the Timeout Features
-----------------------------------------------------------------------------*/
#include <util/StringTrim.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include "TestSuite.h"
#include <iostream>

using namespace std;
using namespace org::xmlBlaster::util;


namespace org { namespace xmlBlaster { namespace test {

class TestStringTrim
{
private:
   string  ME;
   Global& global_;
   I_Log&  log_;

public:
   TestStringTrim(Global& glob) 
      : ME("TestStringTrim"), 
        global_(glob), 
        log_(glob.getLog("test"))
   {
   }


   void tearDown()
   {
   }

   virtual ~TestStringTrim()
   {
   }

   void setUp()
   {
   }

   void testTrim()
   {
      string me = ME + "::testConnectQos";
      log_.info(me, "testing parsing of a return connect qos: start");
      StringTrim trimmer;

      // Test const char * variant
      assertEquals(log_, me, string("ab"), StringTrim::trimStart(" \t\n ab"), string("Start test"));
      assertEquals(log_, me, string("OK"), trimmer.trimStart("OK"), string("Start test"));
      assertEquals(log_, me, string(""), trimmer.trimStart(0), string("Start test"));
      assertEquals(log_, me, string(""), trimmer.trimStart(""), string("Start test"));
      assertEquals(log_, me, string("A  A  "), trimmer.trimStart("A  A  "), string("Start test"));

      assertEquals(log_, me, string("ab"), trimmer.trimEnd("ab \t\n  "), string("End test"));
      assertEquals(log_, me, string("OK"), trimmer.trimEnd("OK"), string("End test"));
      assertEquals(log_, me, string(""), trimmer.trimEnd(0), string("End test"));
      assertEquals(log_, me, string(""), trimmer.trimEnd(""), string("End test"));
      assertEquals(log_, me, string("  A  A"), trimmer.trimEnd("  A  A"), string("End test"));

      assertEquals(log_, me, string("ab"), trimmer.trim(" \t\n ab \t\n  "), string("Trim test"));
      assertEquals(log_, me, string("OK"), trimmer.trim("OK"), string("Trim test"));
      assertEquals(log_, me, string(""), trimmer.trim(0), string("Trim test"));
      assertEquals(log_, me, string(""), trimmer.trim(""), string("Trim test"));
      assertEquals(log_, me, string("A  A"), trimmer.trim("  A  A"), string("Trim test"));

      // Test string variant
      string result;
      result = " \t\n ab";
      trimmer.trimStart(result);
      assertEquals(log_, me, string("ab"), result, string("string - Start test"));
      result = "OK";
      trimmer.trimStart(result);
      assertEquals(log_, me, string("OK"), result, string("string - Start test"));
      result = "";
      trimmer.trimStart(result);
      assertEquals(log_, me, string(""), result, string("string - Start test"));
      result = "A  A  ";
      trimmer.trimStart(result);
      assertEquals(log_, me, string("A  A  "), result, string("string - Start test"));

      result = "ab \t\n  ";
      trimmer.trimEnd(result);
      assertEquals(log_, me, string("ab"), result, string("string - End test"));
      result = "OK";
      trimmer.trimEnd(result);
      assertEquals(log_, me, string("OK"), result, string("string - End test"));
      result = "";
      trimmer.trimEnd(result);
      assertEquals(log_, me, string(""), result, string("string - End test"));
      result = "  A  A";
      trimmer.trimEnd(result);
      assertEquals(log_, me, string("  A  A"), result, string("string - End test"));

      result = " \t\n ab \t\n  ";
      trimmer.trim(result);
      assertEquals(log_, me, string("ab"), result, string("string - Trim test"));
      result = "OK";
      trimmer.trim(result);
      assertEquals(log_, me, string("OK"), result, string("string - Trim test"));
      result = "";
      trimmer.trim(result);
      assertEquals(log_, me, string(""), result, string("string - Trim test"));
      result = "  A  A";
      trimmer.trim(result);
      assertEquals(log_, me, string("A  A"), result, string("string - Trim test"));
      cout << "DONE" << endl;
      //   assertEquals(log_, log_, me, string("127.0.0.2"), address.getHostname(), "address hostname check");
   }
};

}}} // namespace 


using namespace org::xmlBlaster::test;

/**
 * Try
 * <pre>
 *   java TestStringTrim -help
 * </pre>
 * for usage help
 */
int main(int args, char ** argv)
{
   try {
      org::xmlBlaster::util::Object_Lifetime_Manager::init();
      Global& glob = Global::getInstance();
      glob.initialize(args, argv);

      TestStringTrim test(glob);

      test.setUp();
      test.testTrim();
      test.tearDown();
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
