/*-----------------------------------------------------------------------------
Name:      SubscribeDemo.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Little demo to show how a subscribe is done
-----------------------------------------------------------------------------*/

#include <client/XmlBlasterAccess.h>
#include <util/Global.h>
#include <util/PlatformUtils.hpp>
#include <util/lexical_cast.h>


/**
 *
 */

using namespace std;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::util;
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
   bool             doContinue_;

   SubscribeDemo(Global& glob) 
      : ME("SubscribeDemo"), 
        global_(glob), 
        log_(glob.getLog("demo")),
        connection_(global_)
   {
      doContinue_ = true;
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

   string update(const string& /*sessionId*/, UpdateKey& updateKey, void *content, long contentSize, UpdateQos& updateQos)
   {
      log_.info(ME, "update invoked");
      if (log_.trace()) log_.trace(ME, "update: key    : " + updateKey.toXml());
      if (log_.trace()) log_.trace(ME, "update: qos    : " + updateQos.toXml());
      string help((char*)content, (char*)(content)+contentSize);
      if (log_.trace()) log_.trace(ME, "update: content: " + help);
      if (updateQos.getState() == "ERASED" ) {
         doContinue_ = false;
         return "";
      }

      return "";
   }
};

void usage(Log& log) 
{
   log.info("SubscribeDemo", "usage: all typical xmlBlaster command line arguments");
   log.info("SubscribeDemo", "plus the following additional command line arguments:");
   exit(0);
}

/**
 * Try
 * <pre>
 *   java TestSubXPath -help
 * </pre>
 * for usage help
 */
int main(int args, char ** argv)
{
   org::xmlBlaster::util::Object_Lifetime_Manager::init();
   TimestampFactory& tsFactory = TimestampFactory::getInstance();
   Timestamp startStamp = tsFactory.getTimestamp();
   std::cout << " end time: " << tsFactory.toXml(startStamp, "", true) << std::endl;

   try {
      XMLPlatformUtils::Initialize();
      Global& glob = Global::getInstance();
      glob.initialize(args, argv);

      SubscribeDemo demo(glob);
      demo.connect();

      demo.subscribe();
      while (demo.doContinue_) {
         org::xmlBlaster::util::thread::Thread::sleepSecs(1);
      }

      Timestamp stopStamp = tsFactory.getTimestamp();
      std::cout << " end time: " << tsFactory.toXml(stopStamp, "", true) << std::endl;
      Timestamp diff = stopStamp - startStamp;
      std::cout << " time used for demo: " << tsFactory.toXml(diff, "", true) << std::endl;

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
