
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
   Global& global_;

public:
   HelloWorld2(Global& glob) : global_(glob)
   {
   }

   void execute()
   {
      try {
         XmlBlasterAccess con(global_);
         ConnectQos qos(global_, "joe", "secret");
         ConnectReturnQos retQos = con.connect(qos, this);  // Login to xmlBlaster, register for updates
         cout << "connect return qos: " << endl << retQos.toXml() << endl;

         con.subscribe("<key oid='HelloWorld2'/>", "<qos/>");

         MessageUnit msgUnit(string("<key oid='HelloWorld2'/>"),
                                     string("Hi"),
                                     string("<qos/>"));

         con.publish(msgUnit);
         try {
            Timestamp delay = 1000000000ll; // 1 seconds
            TimestampFactory::getInstance().sleep(delay);
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
      cout << "\nHelloWorld: Received asynchronous message '";
      cout << updateKey.getUniqueKey() << "' state=" << updateQos.getState();
      cout << updateQos.toXml() << endl;
      cout << " from xmlBlaster" << endl;
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
