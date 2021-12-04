/*-----------------------------------------------------------------------------
Name:      TestClientProperty.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the ClientProperty Features
-----------------------------------------------------------------------------*/
#include "TestSuite.h"
#include <iostream>
#include <util/qos/ClientProperty.h>
#include <util/qos/QosData.h>
#include <util/qos/MsgQosData.h>

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

   void testQosData()
   {
      log_.info(ME, "testQosData(): Starting tests ...");
      {
         MsgQosData msgQosData(global_);
         msgQosData.addClientProperty("key1", "value1", Constants::TYPE_STRING, Constants::ENCODING_BASE64);

         string res = msgQosData.getClientProperty("key1", string("dummy"));
         assertEquals(log_, ME, "value1", res, "bla");
      
         const QosData::ClientPropertyMap& map = msgQosData.getClientProperties();
         const QosData::ClientPropertyMap::const_iterator it = map.find("key1");
         if (it==map.end())
            log_.error(ME, "NO key1 found");
         const ClientProperty cp = (*it).second;
         log_.info(ME, cp.toXml(""));
         assertEquals(log_, ME, "key1", cp.getName(), "key1");
         assertEquals(log_, ME, "value1", cp.getStringValue(), "");
         assertEquals(log_, ME, Constants::TYPE_STRING, cp.getType(), "type");
         assertEquals(log_, ME, true, cp.isBase64(), "isBase64");
         log_.info(ME, cp.toXml(""));
      }
      {
         MsgQosData msgQosData(global_);
         msgQosData.addClientProperty("key2", string("value2"));

         string res = msgQosData.getClientProperty("key2", string("dummy"));
         assertEquals(log_, ME, "value2", res, "bla");
      
         const QosData::ClientPropertyMap& map = msgQosData.getClientProperties();
         const QosData::ClientPropertyMap::const_iterator it = map.find("key2");
         if (it==map.end())
            log_.error(ME, "NO key2 found");
         const ClientProperty cp = (*it).second;
         log_.info(ME, cp.toXml(""));
         assertEquals(log_, ME, "key2", cp.getName(), "key2");
         assertEquals(log_, ME, "value2", cp.getStringValue(), "");
         assertEquals(log_, ME, ""/*Constants::TYPE_STRING*/, cp.getType(), "type");
         assertEquals(log_, ME, false, cp.isBase64(), "isBase64");
         log_.info(ME, cp.toXml(""));
      }
   }

   void testClientProperty() 
   {
      log_.info(ME, "testClientProperty(): Starting tests ...");
      try {
         {
            ClientProperty cp("key", string("s tring"));
            cout << "name=" << cp.getName() 
                 << ", valueB64=" << cp.getValueRaw()
                 << ", value=" << cp.getStringValue()
                 << ", type=" << cp.getType()
                 << ", charset=" << cp.getCharset()
                 << ", isBase64=" << cp.isBase64()
                 << cp.toXml("")
                 << endl << endl;
            assertEquals(log_, ME, "key", cp.getName(), "name");
            assertEquals(log_, ME, "s tring", cp.getStringValue(), "value");
            assertEquals(log_, ME, "s tring", cp.getValueRaw(), "encoded value");
            assertEquals(log_, ME, "", cp.getType(), "type");
            assertEquals(log_, ME, "", cp.getEncoding(), "encoding");
            assertEquals(log_, ME, "", cp.getCharset(), "charset");
            assertEquals(log_, ME, false, cp.isBase64(), "isBase64");
         }
         {
            ClientProperty cp("key", string("102 304 506 "));
            cp.setCharset("windows-1252");
            cout << "name=" << cp.getName() 
                 << ", valueB64=" << cp.getValueRaw()
                 << ", value=" << cp.getStringValue()
                 << ", type=" << cp.getType()
                 << ", charset=" << cp.getCharset()
                 << ", isBase64=" << cp.isBase64()
                 << cp.toXml("")
                 << endl << endl;
            assertEquals(log_, ME, "key", cp.getName(), "name");
            assertEquals(log_, ME, "102 304 506 ", cp.getStringValue(), "value");
            assertEquals(log_, ME, "102 304 506 ", cp.getValueRaw(), "encoded value");
            assertEquals(log_, ME, "", cp.getType(), "type");
            assertEquals(log_, ME, "", cp.getEncoding(), "encoding");
            assertEquals(log_, ME, "windows-1252", cp.getCharset(), "charset");
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
            ClientProperty cp("key", string("str&ing"));
            cout << "name=" << cp.getName() 
                 << ", valueB64=" << cp.getValueRaw()
                 << ", value=" << cp.getStringValue()
                 << ", type=" << cp.getType()
                 << ", isBase64=" << cp.isBase64()
                 << cp.toXml("")
                 << endl << endl;
            assertEquals(log_, ME, "key", cp.getName(), "name");
            assertEquals(log_, ME, "str&ing", cp.getStringValue(), "value");
            assertEquals(log_, ME, "c3RyJmluZw==", cp.getValueRaw(), "encoded value");
            assertEquals(log_, ME, "", cp.getType(), "type");
            assertEquals(log_, ME, "base64", cp.getEncoding(), "encoding");
            assertEquals(log_, ME, true, cp.isBase64(), "isBase64");
         }
         {
            ClientProperty cp("transactionID", string("x2004062008 4423489478000"));
            cout << "name=" << cp.getName() 
                 << ", valueB64=" << cp.getValueRaw()
                 << ", value=" << cp.getStringValue()
                 << ", type=" << cp.getType()
                 << ", isBase64=" << cp.isBase64()
                 << cp.toXml("")
                 << endl << endl;
            assertEquals(log_, ME, "transactionID", cp.getName(), "name");
            assertEquals(log_, ME, "x2004062008 4423489478000", cp.getStringValue(), "value");
            assertEquals(log_, ME, "", cp.getType(), "type");
            assertEquals(log_, ME, "", cp.getEncoding(), "encoding");
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
#if defined(__sun)
               // No assign
               cout << "NEW=";
               for (int i=0; i<ret.size(); i++) {
                  cout << ret.at(i);
               }
               cout << endl;
#else
               str.assign(ret.begin(),ret.end());
               cout << "NEW=" << str << endl;
#endif
            }
            assertEquals(log_, ME, "key", cp.getName(), "name");
            assertEquals(log_, ME, "Hallo", cp.getStringValue(), "value");
            assertEquals(log_, ME, "byte[]", cp.getType(), "type");
            assertEquals(log_, ME, "base64", cp.getEncoding(), "encoding");
            assertEquals(log_, ME, true, cp.isBase64(), "isBase64");
            assertEquals(log_, ME, "SGFsbG8=", cp.getValueRaw(), "encoded value");
         }
         {
            bool b=true;
            ClientProperty cp("key", b);
            cout << "name=" << cp.getName() 
                 << ", valueB64=" << cp.getValueRaw()
                 << ", value=" << cp.getStringValue()
                 << ", type=" << cp.getType()
                 << ", encoding=" << cp.getEncoding()
                 << ", isBase64=" << cp.isBase64()
                 << cp.toXml("")
                 << endl;
            bool ret;
            cp.getValue(ret);
            assertEquals(log_, ME, "key", cp.getName(), "name");
            //assertEquals(log_, ME, f, ret, "value");
            assertEquals(log_, ME, "true", cp.getValueRaw(), "encoded value");
            assertEquals(log_, ME, "boolean", cp.getType(), "type");
            assertEquals(log_, ME, "", cp.getEncoding(), "encoding");
            assertEquals(log_, ME, false, cp.isBase64(), "isBase64");
         }
      }
      catch(const bad_cast &b) {
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
   testObj.testQosData();
   testObj.testClientProperty();
   testObj.tearDown();
   return 0;
}


