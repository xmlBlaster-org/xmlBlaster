/*-----------------------------------------------------------------------------
Name:      PublishDemo.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Little demo to show how a publish is done
-----------------------------------------------------------------------------*/

#include <client/XmlBlasterAccess.h>
#include <util/Global.h>
#include <util/PlatformUtils.hpp>
#include <boost/lexical_cast.hpp>


/**
 *
 */

using boost::lexical_cast;
using namespace std;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

class PublishDemo
{
private:
   string           ME;
   Global&          global_;
   Log&             log_;
   XmlBlasterAccess connection_;

public:
   PublishDemo(Global& glob) 
      : ME("PublishDemo"), 
        global_(glob), 
        log_(glob.getLog("demo")),
        connection_(global_)
   {
   }

   void connect()
   {
      ConnectQos connQos(global_);
      log_.trace(ME, string("connecting to xmlBlaster. Connect qos: ") + connQos.toXml());
      ConnectReturnQos retQos = connection_.connect(connQos, NULL); // no callback
      log_.trace(ME, "successfully connected to xmlBlaster. Return qos: " + retQos.toXml());
   }

   void publish(const string& oid="c++-demo", const string& clientTags="<demo/>", const string& content="simple content")
   {
      PublishKey key(global_, oid, "text/xml", "1.0");
      key.setClientTags("<org.xmlBlaster><demo/></org.xmlBlaster>");
      PublishQos qos(global_);
      MessageUnit msgUnit(key, content, qos);
      log_.trace(ME, string("published message unit: ") + msgUnit.toXml());
      PublishReturnQos tmp = connection_.publish(msgUnit);
      log_.trace(ME, string("publish return qos: ") + tmp.toXml());
   }

/*
      try {
         EraseKey key(global_);
         key.setOid(oid);
         EraseQos qos(global_);
         vector<EraseReturnQos> arr = connection_->erase(key, qos);
         assertEquals(log_, ME, (size_t)1, arr.size(), "Erase");
      } 
      catch(XmlBlasterException& e) { 
         assert(0);
      }
   }
*/

};

/**
 * Try
 * <pre>
 *   java TestSubXPath -help
 * </pre>
 * for usage help
 */
int main(int args, char ** argv)
{
   try {
      XMLPlatformUtils::Initialize();
      Global& glob = Global::getInstance();
      glob.initialize(args, argv);

      PublishDemo demo(glob);
      demo.connect();
      for (int i=0; i < 10; i++) {
         demo.publish();
         org::xmlBlaster::util::thread::Thread::sleepSecs(1);
      }
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
