/*-----------------------------------------------------------------------------
Name:      TestClientProperty.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the ClientProperty Features
-----------------------------------------------------------------------------*/
#include "TestSuite.h"
#include <iostream>
#include <util/qos/ClientProperty.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;

/**
 * Class test of ClientProperty.cpp
 * @see org::xmlBlaster::util::qos::ClientProperty 
 */

namespace org { namespace xmlBlaster { namespace test {

class TestClientProperty : public TestSuite {
   
private:
   string ME;
   Global& global_;
   I_Log&  log_;
   int     count_;
public:
   TestClientProperty(Global& global, const string& name) 
      : TestSuite(global.getArgs(), global.getArgc(), name, false),
        ME(name), 
        global_(global),
        log_(global.getLog("test")) 
   {
      count_ = 0;
   }

   virtual ~TestClientProperty()
   {
   }

   void testClientProperty() 
   {
      log_.info(ME, "testClientProperty(): Starting tests ...");
      try {
         {
            ClientProperty cp("key", string("string"));
            cout << "name=" << cp.getName() 
                 << ", valueB64=" << cp.getValueRaw()
                 << ", value=" << cp.getStringValue()
                 << ", type=" << cp.getType()
                 << ", isBase64=" << cp.isBase64()
                 << cp.toXml("")
                 << endl << endl;
            assertEquals(log_, ME, "key", cp.getName(), "name");
            assertEquals(log_, ME, "string", cp.getStringValue(), "value");
            assertEquals(log_, ME, "string", cp.getValueRaw(), "encoded value");
            assertEquals(log_, ME, "", cp.getType(), "type");
            assertEquals(log_, ME, "", cp.getEncoding(), "encoding");
            assertEquals(log_, ME, false, cp.isBase64(), "isBase64");
         }
         {
            ClientProperty cp("key", string("str]]>ing"));
            cout << "name=" << cp.getName() 
                 << ", valueB64=" << cp.getValueRaw()
                 << ", value=" << cp.getStringValue()
                 << ", type=" << cp.getType()
                 << ", isBase64=" << cp.isBase64()
                 << cp.toXml("")
                 << endl << endl;
            assertEquals(log_, ME, "key", cp.getName(), "name");
            assertEquals(log_, ME, "str]]>ing", cp.getStringValue(), "value");
            assertEquals(log_, ME, "c3RyXV0+aW5n", cp.getValueRaw(), "encoded value");
            assertEquals(log_, ME, "", cp.getType(), "type");
            assertEquals(log_, ME, "base64", cp.getEncoding(), "encoding");
            assertEquals(log_, ME, true, cp.isBase64(), "isBase64");
         }
         {
            ClientProperty cp("key", string("str<<<ing"));
            cout << "name=" << cp.getName() 
                 << ", valueB64=" << cp.getValueRaw()
                 << ", value=" << cp.getStringValue()
                 << ", type=" << cp.getType()
                 << ", isBase64=" << cp.isBase64()
                 << cp.toXml("")
                 << endl << endl;
            assertEquals(log_, ME, "key", cp.getName(), "name");
            assertEquals(log_, ME, "str<<<ing", cp.getStringValue(), "value");
            assertEquals(log_, ME, "", cp.getType(), "type");
            assertEquals(log_, ME, "base64", cp.getEncoding(), "encoding");
            assertEquals(log_, ME, "c3RyPDw8aW5n", cp.getValueRaw(), "encoded value");
            assertEquals(log_, ME, true, cp.isBase64(), "isBase64");
         }
         {
            ClientProperty cp("key", "const char *");
            cout << "name=" << cp.getName() 
                 << ", valueB64=" << cp.getValueRaw()
                 << ", value=" << cp.getStringValue()
                 << ", type=" << cp.getType()
                 << ", isBase64=" << cp.isBase64()
                 << cp.toXml("")
                 << endl << endl;
            assertEquals(log_, ME, "key", cp.getName(), "name");
            assertEquals(log_, ME, "const char *", cp.getStringValue(), "value");
            assertEquals(log_, ME, "const char *", cp.getValueRaw(), "encoded value");
            assertEquals(log_, ME, "", cp.getType(), "type");
            assertEquals(log_, ME, "", cp.getEncoding(), "encoding");
            assertEquals(log_, ME, false, cp.isBase64(), "isBase64");
         }
         {
            long f=10L;
            ClientProperty cp("key", f);
            cout << "name=" << cp.getName() 
                 << ", valueB64=" << cp.getValueRaw()
                 << ", value=" << cp.getStringValue()
                 << ", type=" << cp.getType()
                 << ", isBase64=" << cp.isBase64()
                 << cp.toXml("")
                 << endl << endl;
            long ret;
            cp.getValue(ret);
            assertEquals(log_, ME, "key", cp.getName(), "name");
            assertEquals(log_, ME, f, ret, "value");
            assertEquals(log_, ME, "10", cp.getValueRaw(), "encoded value");
            assertEquals(log_, ME, "long", cp.getType(), "type");
            assertEquals(log_, ME, "", cp.getEncoding(), "encoding");
            assertEquals(log_, ME, false, cp.isBase64(), "isBase64");
         }
         {
            float f=10.5;
            ClientProperty cp("key", f);
            cout << "name=" << cp.getName() 
                 << ", valueB64=" << cp.getValueRaw()
                 << ", value=" << cp.getStringValue()
                 << ", type=" << cp.getType()
                 << ", isBase64=" << cp.isBase64()
                 << cp.toXml("")
                 << endl << endl;
            float ret;
            cp.getValue(ret);
            assertEquals(log_, ME, "key", cp.getName(), "name");
            assertEquals(log_, ME, f, ret, "value");
            assertEquals(log_, ME, "10.5", cp.getValueRaw(), "encoded value");
            assertEquals(log_, ME, "float", cp.getType(), "type");
            assertEquals(log_, ME, "", cp.getEncoding(), "encoding");
            assertEquals(log_, ME, false, cp.isBase64(), "isBase64");
         }
         {
            double f=20.63452879L;
            ClientProperty cp("key", f);
            cout << "name=" << cp.getName() 
                 << ", valueB64=" << cp.getValueRaw()
                 << ", value=" << cp.getStringValue()
                 << ", type=" << cp.getType()
                 << ", encoding=" << cp.getEncoding()
                 << ", isBase64=" << cp.isBase64()
                 << cp.toXml("")
                 << endl;
            double ret;
            cp.getValue(ret);
            assertEquals(log_, ME, "key", cp.getName(), "name");
            //assertEquals(log_, ME, f, ret, "value");
            assertEquals(log_, ME, "20.63452879", cp.getValueRaw(), "encoded value");
            assertEquals(log_, ME, "double", cp.getType(), "type");
            assertEquals(log_, ME, "", cp.getEncoding(), "encoding");
            assertEquals(log_, ME, false, cp.isBase64(), "isBase64");
         }
         {
            vector<unsigned char> v;
            v.push_back('H');
            v.push_back('a');
            v.push_back('l');
            v.push_back('l');
            v.push_back('o');
            ClientProperty cp("key", v);
            cout << "name=" << cp.getName() 
                 << ", valueB64=" << cp.getValueRaw()
                 << ", value=" << cp.getStringValue()
                 << ", type=" << cp.getType()
                 << ", encoding=" << cp.getEncoding()
                 << ", isBase64=" << cp.isBase64()
                 << cp.toXml("")
                 << endl;
            {
               std::vector<unsigned char> ret = cp.getValue();
               std::string str;
               str.assign(ret.begin(),ret.end());
               cout << "NEW=" << str << endl;
            }
            assertEquals(log_, ME, "key", cp.getName(), "name");
            assertEquals(log_, ME, "Hallo", cp.getStringValue(), "value");
            assertEquals(log_, ME, "byte[]", cp.getType(), "type");
            assertEquals(log_, ME, "base64", cp.getEncoding(), "encoding");
            assertEquals(log_, ME, true, cp.isBase64(), "isBase64");
            assertEquals(log_, ME, "SGFsbG8=", cp.getValueRaw(), "encoded value");
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

   TestClientProperty testObj(glob, "TestClientProperty");

   testObj.setUp();
   testObj.testClientProperty();
   testObj.tearDown();
   return 0;
}


