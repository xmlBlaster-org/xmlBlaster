
#include <util/xmlBlasterDef.h>
#include <client/XmlBlasterAccess.h>
#include <util/qos/ConnectQos.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include <util/Log.h>
#include <client/I_Callback.h>
#include <client/UpdateKey.h>
#include <client/UpdateQos.h>
#include <util/MessageUnit.h>
#include <util/PlatformUtils.hpp>
#include <util/Timestamp.h>

#include <util/qos/PublishQos.h>

/**
 * This client connects to xmlBlaster and subscribes to a message.
 * <p />
 * We then publish the message and receive it asynchronous in the update() method.
 * <p />
 * Invoke: HelloWorld2
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 */

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster;

class HelloWorld2 :public I_Callback
{
private:
   string  ME;
   Global& global_;
   Log&    log_;

public:
   HelloWorld2(Global& glob) : ME("HelloWorld2"), global_(glob), log_(glob.getLog())
   {
   }

   virtual ~HelloWorld2()
   {
   }

   void execute()
   {
      try {
         XmlBlasterAccess con(global_);
         log_.info(ME, "connecting to xmlBlaster");

         ConnectQos qos(global_, "joe", "secret");
         ConnectReturnQos retQos = con.connect(qos, this);  // Login to xmlBlaster, register for updates
         log_.info(ME, "successfully connected to xmlBlaster. Return qos: " + retQos.toXml());
         log_.info(ME, "subscribing to xmlBlaster");
         con.subscribe("<key oid='HelloWorld2'/>", "<qos/>");
         log_.info(ME, "successfully subscribed to xmlBlaster");

         PublishQos publishQos(global_);
         MessageUnit msgUnit(string("<key oid='HelloWorld2'/>"),
                                     string("Hi"),
                                     publishQos);

         log_.info(ME, "publishing to xmlBlaster");
         con.publish(msgUnit);
         log_.info(ME, "successfully published to xmlBlaster");
         try {
            TimestampFactory::sleepSecs(1);
         }
         catch(XmlBlasterException e) {
            cout << e.toXml() << endl;
         }

         con.erase("<key oid='HelloWorld2'/>", "<qos/>");
         con.disconnect("<qos/>");
      }
      catch (XmlBlasterException e) {
         cout << e.toXml() << endl;
      }
   }

   string update(const string& sessionId, UpdateKey& updateKey, void *content, long contentSize, UpdateQos& updateQos)
   {
      log_.info(ME, "update: unique Key: " + updateKey.getUniqueKey());
      log_.info(ME, "update: state     : " + updateQos.getState());
      log_.info(ME, "update: updateQos : " + updateQos.toXml());
      return "";
   }

};

/**
 * Try
 * <pre>
 *   java HelloWorld2 -help
 * </pre>
 * for usage help
 */
int main(int args, char ** argv)
{
   XMLPlatformUtils::Initialize();
   Global& glob = Global::getInstance();
   glob.initialize(args, argv);
// XmlBlasterConnection::usage();
//   glob.getLog().info("HelloWorld2", "Example: java HelloWorld2\n");

   HelloWorld2 hello(glob);
   hello.execute();
   return 0;
}
