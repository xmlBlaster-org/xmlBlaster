
#include <client/XmlBlasterAccess.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include <util/Log.h>
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
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;
using namespace org::xmlBlaster;

class HelloWorld2 : public I_Callback, public I_ConnectionProblems
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

   void reConnected()
   {
      log_.info(ME, "reconnected");
   }

   void lostConnection()
   {
      log_.info(ME, "lost connection");
   }
   void execute()
   {
      try {
         XmlBlasterAccess con(global_);
	 con.initFailsafe(this);

         ConnectQos qos(global_, "joe", "secret");
         log_.info(ME, string("connecting to xmlBlaster. Connect qos: ") + qos.toXml());
         ConnectReturnQos retQos = con.connect(qos, this);  // Login to xmlBlaster, register for updates
         log_.info(ME, "successfully connected to xmlBlaster. Return qos: " + retQos.toXml());

	 SubscribeKey subKey(global_);
	 subKey.setOid("HelloWorld2");
	 SubscribeQos subQos(global_);
         log_.info(ME, string("subscribing to xmlBlaster with key: ") + subKey.toXml() + " and qos: " + subQos.toXml());

         SubscribeReturnQos subRetQos = con.subscribe(subKey, subQos);
         log_.info(ME, string("successfully subscribed to xmlBlaster. Return qos: ") + subRetQos.toXml());

         PublishQos publishQos(global_);
	 PublishKey publishKey(global_);
	 publishKey.setOid("HelloWorld2");
         MessageUnit msgUnit(publishKey, string("Hi"), publishQos);
         log_.info(ME, string("publishing to xmlBlaster with message: ") + msgUnit.toXml());
         PublishReturnQos pubRetQos = con.publish(msgUnit);
         log_.info(ME, "successfully published to xmlBlaster. Return qos: " + pubRetQos.toXml());
         try {
            TimestampFactory::sleepSecs(1);
         }
         catch(XmlBlasterException e) {
            cout << e.toXml() << endl;
         }

         EraseKey eraseKey(global_);
	 eraseKey.setOid("HelloWorld2");
	 EraseQos eraseQos(global_);
         log_.info(ME, string("erasing the published message. Key: ") + eraseKey.toXml() + " qos: " + eraseQos.toXml());
	 vector<EraseReturnQos> eraseRetQos = con.erase(eraseKey, eraseQos);
	 for (size_t i=0; i < eraseRetQos.size(); i++ ) {
            log_.info(ME, string("successfully erased the message. return qos: ") + eraseRetQos[i].toXml());
	 }

	 DisconnectQos disconnectQos(global_);
         con.disconnect(disconnectQos);
      }
      catch (XmlBlasterException e) {
         cout << e.toXml() << endl;
      }
   }

   string update(const string& sessionId, UpdateKey& updateKey, void *content, long contentSize, UpdateQos& updateQos)
   {
      log_.info(ME, "update: key: " + updateKey.toXml());
      log_.info(ME, "update: qos: " + updateQos.toXml());
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
