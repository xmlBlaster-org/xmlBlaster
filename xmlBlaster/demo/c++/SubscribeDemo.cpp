/*-----------------------------------------------------------------------------
Name:      SubscribeDemo.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Little demo to show how a subscribe is done
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

class SubscribeDemo : public I_Callback
{
private:
   string           ME;
   Global&          global_;
   Log&             log_;
   XmlBlasterAccess connection_;

public:
   SubscribeDemo(Global& glob) 
      : ME("SubscribeDemo"), 
        global_(glob), 
        log_(glob.getLog("demo")),
        connection_(global_)
   {
   }

   void connect()
   {
      ConnectQos connQos(global_);
      log_.trace(ME, string("connecting to xmlBlaster. Connect qos: ") + connQos.toXml());
      ConnectReturnQos retQos = connection_.connect(connQos, this);
      log_.trace(ME, "successfully connected to xmlBlaster. Return qos: " + retQos.toXml());
   }

   void subscribe(const string& oid="c++-demo")
   {
      SubscribeKey key(global_);
      key.setOid(oid);
      SubscribeQos qos(global_);
      log_.trace(ME, string("subscribing to xmlBlaster with key: ") + key.toXml() + " and qos: " + qos.toXml());
      SubscribeReturnQos subRetQos = connection_.subscribe(key, qos);
      log_.trace(ME, string("successfully subscribed to xmlBlaster. Return qos: ") + subRetQos.toXml());
   }

   string update(const string& sessionId, UpdateKey& updateKey, void *content, long contentSize, UpdateQos& updateQos)
   {
      log_.info(ME, "update invoked");
      if (log_.trace()) log_.trace(ME, "update: key    : " + updateKey.toXml());
      if (log_.trace()) log_.trace(ME, "update: qos    : " + updateQos.toXml());
      string help((char*)content, (char*)(content)+contentSize);
      if (log_.trace()) log_.trace(ME, "update: content: " + help);
      if (updateQos.getState() == "ERASED" ) return "";

      return "";
   }


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

      SubscribeDemo demo(glob);
      demo.connect();
      demo.subscribe();
      org::xmlBlaster::util::thread::Thread::sleepSecs(12);
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
