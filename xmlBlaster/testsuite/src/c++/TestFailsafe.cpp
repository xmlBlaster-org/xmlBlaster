/*-----------------------------------------------------------------------------
Name:      TestFailsafe.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the Timeout Features
-----------------------------------------------------------------------------*/

#include <client/XmlBlasterAccess.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include <util/Log.h>
#include <util/PlatformUtils.hpp>
#include <util/Timestamp.h>
#include <boost/lexical_cast.hpp>

/**
 *
 */

using boost::lexical_cast;
using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;
using namespace org::xmlBlaster;

class TestFailsafe : public I_Callback, public I_ConnectionProblems
{
private:
   string  ME;
   Global& global_;
   Log&    log_;

   XmlBlasterAccess   *connection_;
   ConnectQos         *connQos_;
   SubscribeQos       *subQos_;
   SubscribeKey       *subKey_;
   PublishQos         *pubQos_;
   PublishKey         *pubKey_;


public:
   TestFailsafe(Global& glob) : ME("TestFailsafe"), global_(glob), log_(glob.getLog())
   {
      connection_ = NULL;
      connQos_    = NULL;
      subQos_     = NULL;
      subKey_     = NULL;
      pubQos_     = NULL;
      pubKey_     = NULL;
   }

   virtual ~TestFailsafe()
   {
   }

   bool reConnected()
   {
      log_.info(ME, "reconnected");
      return true;
   }

   void lostConnection()
   {
      log_.info(ME, "lost connection");
   }

   void toPolling()
   {
      log_.info(ME, "going to poll modus");
   }


   void setUp()
   {
      try {   
	 connection_ = new XmlBlasterAccess(global_);
	 connection_->initFailsafe(this);

	 connQos_ = new ConnectQos(global_, "guy", "secret");

	 log_.info(ME, string("connecting to xmlBlaster. Connect qos: ") + connQos_->toXml());
	 ConnectReturnQos connRetQos = connection_->connect(*connQos_, this);  // Login to xmlBlaster, register for updates
	 log_.info(ME, "successfully connected to xmlBlaster. Return qos: " + connRetQos.toXml());

	 subKey_ = new SubscribeKey(global_);
	 subKey_->setOid("TestFailsafe");
	 subQos_ = new SubscribeQos(global_);
	 log_.info(ME, string("subscribing to xmlBlaster with key: ") + subKey_->toXml() + " and qos: " + subQos_->toXml());

	 SubscribeReturnQos subRetQos = connection_->subscribe(*subKey_, *subQos_);
	 log_.info(ME, string("successfully subscribed to xmlBlaster. Return qos: ") + subRetQos.toXml());
      }
      catch (XmlBlasterException& ex) {
         log_.error(ME, string("exception occurred in setUp. ") + ex.toXml());
	 assert(0);
      }

   }


   void testFailsafe() 
   {
      try {
         pubQos_ = new PublishQos(global_);
	 pubKey_ = new PublishKey(global_);
	 pubKey_->setOid("TestFailsafe");

	 for (int i=0; i < 120; i++) {
	    string msg = string("msg") + lexical_cast<string>(i);
	    MessageUnit msgUnit(*pubKey_, msg, *pubQos_);
            log_.info(ME, string("publishing msg '") + msg + "'");
	    PublishReturnQos pubRetQos = connection_->publish(msgUnit);
	    try {
	       Thread::sleepSecs(1);
	    }
	    catch(XmlBlasterException e) {
	       cout << e.toXml() << endl;
	    }

	 }
      }
      catch (XmlBlasterException& ex) {
         log_.error(ME, string("exception occurred in setFailSafe. ") + ex.toXml());
	 assert(0);
      }
   }


   void tearDown()
   {
      try {
         EraseKey eraseKey(global_);
	 eraseKey.setOid("TestFailsafe");
	 EraseQos eraseQos(global_);
         log_.info(ME, string("erasing the published message. Key: ") + eraseKey.toXml() + " qos: " + eraseQos.toXml());
	 vector<EraseReturnQos> eraseRetQos = connection_->erase(eraseKey, eraseQos);
	 for (size_t i=0; i < eraseRetQos.size(); i++ ) {
            log_.info(ME, string("successfully erased the message. return qos: ") + eraseRetQos[i].toXml());
	 }

	 // log_.info(ME, "going to sleep for one minute");
	 // org::xmlBlaster::util::thread::Thread::sleep(60000);

	 DisconnectQos disconnectQos(global_);
         connection_->disconnect(disconnectQos);
      }
      catch (XmlBlasterException& ex) {
         log_.error(ME, string("exception occurred in tearDown. ") + ex.toXml());
	 assert(0);
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
 *   java TestFailsafe -help
 * </pre>
 * for usage help
 */
int main(int args, char ** argv)
{
   XMLPlatformUtils::Initialize();
   Global& glob = Global::getInstance();
   glob.initialize(args, argv);
// XmlBlasterConnection::usage();
//   glob.getLog().info("TestFailsafe", "Example: java TestFailsafe\n");

   TestFailsafe testFailsafe(glob);
   testFailsafe.setUp();
   testFailsafe.testFailsafe();
   testFailsafe.tearDown();
   return 0;
}
