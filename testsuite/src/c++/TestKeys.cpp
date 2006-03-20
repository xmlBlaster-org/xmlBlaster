/*-----------------------------------------------------------------------------
Name:      TestKeys.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id$
-----------------------------------------------------------------------------*/
#include "TestSuite.h"
#include <util/key/MsgKeyFactory.h>
#include <iostream>

/**
 * This client tests the Key objects.<br />
 * <p>
 */

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::util::key;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::client::key;

namespace org { namespace xmlBlaster { namespace test {

class TestKeys: public TestSuite
{
private:

   /**
    * Constructs the TestKeys object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
 public:
   TestKeys(int args, char *argc[]) : TestSuite(args, argc, "TestKeys")
   {
   }

   virtual ~TestKeys() 
   {
   }

   /**
    * Sets up the fixture. <p />
    * Connect to xmlBlaster and login
    */
   void setUp() 
   {
      TestSuite::setUp();
   }


   /**
    * Tears down the fixture. <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   void tearDown() 
   {
      log_.info(ME, "Going to tear down.");
      TestSuite::tearDown();
   }


   /**
    * TEST: Construct a message and publish it. <p />
    * The returned publishOid is checked
    */
   void testPublishKey() 
   {
      if (log_.trace()) log_.trace(ME, "TestPublishKey");
      PublishKey pubKey(global_);
      pubKey.setOid("someOid");
      pubKey.setContentMime("text/plain");
      pubKey.setContentMimeExtended("3.124");
      string xmlKey = string("") +
         "  <TestKeys-AGENT id='192.168.124.10' subId='1' type='generic' >\n" +
         "    <TestKeys-DRIVER id='FileProof' pollingFreq='10' >\n" +
         "      <![CDATA[ this is a simple cdata text ]]>\n" + 
         "    </TestKeys-DRIVER>\n"+
         "  </TestKeys-AGENT>\n";
      pubKey.setClientTags(xmlKey);

     string mime = pubKey.getContentMime();
     assertEquals(log_, ME, "text/plain", mime, "checking mime type");
     string mimeExtended = pubKey.getContentMimeExtended();
     assertEquals(log_, ME, "3.124", mimeExtended, "checking mime extended type");
     string xmlKeyRet =  pubKey.getData().getClientTags();
     assertEquals(log_, ME, xmlKey, xmlKeyRet, "checking client tags");

     string literal = pubKey.toXml();
     if (log_.trace()) log_.trace(ME, "testPublishKey the literal is: " + literal);
     
     MsgKeyFactory factory(global_);
     MsgKeyData keyData = factory.readObject(literal);

     mime = keyData.getContentMime();
     assertEquals(log_, ME, "text/plain", mime, "msgKeyData: checking mime type");
     mimeExtended = keyData.getContentMimeExtended();
     assertEquals(log_, ME, "3.124", mimeExtended, "msgKeyData: checking mime extended type");
     xmlKeyRet =  keyData.getClientTags();
     assertEquals(log_, ME, xmlKey, xmlKeyRet, "msgKeyData: checking client tags");

   }


   void usage() const
   {
      TestSuite::usage();
      log_.plain(ME, "----------------------------------------------------------");
      XmlBlasterAccess::usage();
      log_.usage();
      log_.plain(ME, "Example:");
      log_.plain(ME, "   TestKeys -bootstrapHostname myHost.myCompany.com -bootstrapPort 3412 -trace true");
      log_.plain(ME, "----------------------------------------------------------");
   }
};

}}} // namespace

using namespace org::xmlBlaster::test;

int main(int args, char *argc[]) 
{
   try {
      org::xmlBlaster::util::Object_Lifetime_Manager::init();
      TestKeys testSub(args, argc);
      testSub.setUp();
      testSub.testPublishKey();
      testSub.tearDown();
      Thread::sleepSecs(1);
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

