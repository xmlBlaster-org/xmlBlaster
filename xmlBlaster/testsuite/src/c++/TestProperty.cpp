/*-----------------------------------------------------------------------------
Name:      TestProperty.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the property class
-----------------------------------------------------------------------------*/
#include "TestSuite.h"
#include <util/Property.h>
#include <iostream>
#include <stdlib.h> // setenv()

using namespace std;
using namespace org::xmlBlaster::util;

namespace org { namespace xmlBlaster { namespace test {

static int dosetenv(const char *key, const char *value);

/**
 * @return 0 on success
 */
static int dosetenv(const char *key, const char *value)
{
#  ifdef _WINDOWS
      string str = string(key) + "=" + value;
      return _putenv(str.c_str());
#  else

# ifdef __sun__
      string str = string(key) + "=" + value;
      return putenv((char*)str.c_str()); // for SOLARIS
# else
      return setenv(key, value, 1); // for UNIX
#endif

#  endif
}


/**
 * Tests the property class. 
 */
class TestProperty: public TestSuite
{
private:
   //Global& glob;

public:

   /**
    * Constructs the TestProperty object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   TestProperty(int args, char *argc[]) : TestSuite(args, argc, "TestProperty", false)
               //, glob(Global::getInstance())
   {
   }

   virtual ~TestProperty() 
   {
   }

   /**
    * TEST: Construct a message and publish it. <p />
    * The returned publishOid is checked
    */
   void testDefault() 
   {
      if (log_.trace()) log_.trace(ME, "testDefault");
      string me = ME+".testDefault()";
      {
         Property prop;
         std::cout << "Property.testDefault()" << prop.toXml() << std::endl;
         assertEquals(log_, me, true, prop.propertyExists("user.home"), "user.home check");
         assertEquals(log_, me, true, prop.propertyExists("user.name"), "user.name check");
         assertEquals(log_, me, true, prop.propertyExists("file.separator"), "file.separator check");
         assertEquals(log_, me, true, prop.propertyExists("path.separator"), "path.separator check");
      }
      {  // Manipulate env ...

         const char *HOME = getenv("HOME");  // remember
         const char *HOMEDRIVE = getenv("HOMEDRIVE");
         const char *HOMEPATH = getenv("HOMEPATH");
         const char *USER = getenv("USER");

         assertEquals(log_, me, 0, dosetenv("HOME", "D:/BLA"), "setenv"); // for UNIX
         assertEquals(log_, me, 0, dosetenv("HOMEDRIVE", "D:"), "setenv"); // for Windows
         assertEquals(log_, me, 0, dosetenv("HOMEPATH", "/BLA"), "setenv"); // for Windows

         assertEquals(log_, me, 0, dosetenv("USER", "OIOI"), "setenv");
         
         Property prop;
         std::cout << "Property.testDefault()" << prop.toXml() << std::endl;
         assertEquals(log_, me, "D:/BLA", prop.get("user.home", ""), "user.home check");
         assertEquals(log_, me, "OIOI", prop.get("user.name", ""), "user.name check");

         // restore env
         if (HOME) dosetenv("HOME", HOME);
         if (HOMEDRIVE) dosetenv("HOMEDRIVE", HOMEDRIVE);
         if (HOMEPATH) dosetenv("HOMEPATH", HOMEPATH);
         if (USER) dosetenv("USER", USER);
      }
   }

   /**
    * TEST: Construct a message and publish it. <p />
    * The returned publishOid is checked
    */
   void testReplace() 
   {
      string me = ME+".testReplace()";
      if (log_.trace()) log_.trace(me, "");
      Property prop;
      prop.setProperty("A", "aaa");
      prop.setProperty("B", "bValue-${A}-bValue");
      std::cout << "Property.testReplace()" << prop.toXml() << std::endl;
      assertEquals(log_, me, "bValue-aaa-bValue", prop.get("B", ""), "${} check");
   }

   /**
    * TEST: Construct a message and publish it. <p />
    * The returned publishOid is checked
    */
   void testLoadPropertyFile() 
   {
      string me = ME+".testLoadPropertyFile()";
      if (log_.trace()) log_.trace(me, "");
      Property prop;
      prop.loadPropertyFile();
      std::cout << "Property.testLoadPropertyFile()" << prop.toXml() << std::endl;
      // TODO: How to test?
   }

   /**
    * Sets up the fixture. 
    */
   void setUp() 
   {
      TestSuite::setUp();
   }

   /**
    * Tears down the fixture. 
    */
   void tearDown() 
   {
      log_.info(ME, "Going to tear down.");
      TestSuite::tearDown();
   }

   void usage() const
   {
      TestSuite::usage();
      log_.plain(ME, "Example:");
      log_.plain(ME, "   TestProperty -A aValue -B bValue-${A}-bValue"); // TODO: support command line manual checks
      log_.plain(ME, "----------------------------------------------------------");
   }
};

}}} // namespace

using namespace org::xmlBlaster::test;

int main(int args, char *argc[]) 
{
   try {
      org::xmlBlaster::util::Object_Lifetime_Manager::init();
      TestProperty test(args, argc);

      test.setUp();
      test.testDefault();
      test.tearDown();

      test.setUp();
      test.testReplace();
      test.tearDown();

      test.setUp();
      test.testLoadPropertyFile();
      test.tearDown();
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

