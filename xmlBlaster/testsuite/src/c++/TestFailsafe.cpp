/*-----------------------------------------------------------------------------
Name:      TestFailsafe.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the Timeout Features
-----------------------------------------------------------------------------*/

#include <client/XmlBlasterAccess.h>
#include <util/EmbeddedServer.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include <util/Log.h>
#include <util/PlatformUtils.hpp>
#include <util/thread/Thread.h>
#include <util/Timestamp.h>
#include <boost/lexical_cast.hpp>
#include "testSuite.h"


/**
 *
 */

using boost::lexical_cast;
using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::thread;
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

   EmbeddedServer*    embeddedServer_;
   XmlBlasterAccess   *connection_;
   ConnectQos         *connQos_;
   ConnectReturnQos   *connRetQos_;
   SubscribeQos       *subQos_;
   SubscribeKey       *subKey_;
   PublishQos         *pubQos_;
   PublishKey         *pubKey_;
   Mutex              updateMutex_;
   int                numOfUpdates_;
   bool               useEmbeddedServer_;

public:
   TestFailsafe(Global& glob) 
      : ME("TestFailsafe"), 
        global_(glob), 
        log_(glob.getLog("test")),
        updateMutex_()
   {
      embeddedServer_ = NULL;
      connection_     = NULL;
      connQos_        = NULL;
      connRetQos_     = NULL;
      subQos_         = NULL;
      subKey_         = NULL;
      pubQos_         = NULL;
      pubKey_         = NULL;
      numOfUpdates_   = 0;
      useEmbeddedServer_ = global_.getProperty().getBoolProperty("embeddedServer", true);
      if (useEmbeddedServer_) log_.info(ME, "the embedded server is switched ON (you could switch it off with '-embeddedServer false' on the command line)");
      else {
         log_.warn(ME, "the embedded server is switched OFF (you will need an external xmlBlaster running)");
         Thread::sleep(2000);
     }
      
   }

   virtual ~TestFailsafe()
   {
      delete connection_;
      delete connQos_;
      delete connRetQos_;
      delete subQos_;
      delete subKey_;
      delete pubQos_;
      delete pubKey_;
      delete embeddedServer_;
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
         if (useEmbeddedServer_) {
            embeddedServer_ = new EmbeddedServer(global_, "", "-call true -trace true > failsafe.dump 2>&1", connection_);
/* currently commented out (problems with multithreading) 
         if (embeddedServer_->isSomeServerResponding()) {
            log_.error(ME, "this test uses an embedded Server. There is already an external xmlBlaster running on this system, please shut it down first");
            assert(0);
         }
*/
            embeddedServer_->start();
            Thread::sleepSecs(10);
         }
         Address address(global_);
         address.setDelay(10000);
         address.setPingInterval(10000);
         connQos_ = new ConnectQos(global_, "guy", "secret");
         connQos_->setAddress(address);
         log_.info(ME, string("connecting to xmlBlaster. Connect qos: ") + connQos_->toXml());
         connRetQos_ = new ConnectReturnQos(connection_->connect(*connQos_, this));  // Login to xmlBlaster, register for updates
         log_.info(ME, "successfully connected to xmlBlaster. Return qos: " + connRetQos_->toXml());

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

   void testReconnect()
   {
      DisconnectQos disconnectQos(global_);
      connection_->disconnect(disconnectQos);
      Thread::sleep(500);
      string origSessionId = connRetQos_->getSessionQos().getSessionId();
      log_.info(ME, string("original session Id: '") + origSessionId + "'");
      ConnectReturnQos tmp = connection_->connect(*connQos_, this);
      Thread::sleep(500);
      string currentSessionId = tmp.getSessionQos().getSessionId();
      log_.info(ME, string("session Id after reconnection: '") + currentSessionId + "'");
      connection_->disconnect(disconnectQos);

      Thread::sleep(500);
      SessionQos sessionQos = connQos_->getSessionQos();
      sessionQos.setSessionId(connRetQos_->getSessionQos().getSessionId());
      connQos_->setSessionQos(sessionQos);
      tmp = connection_->connect(*connQos_, this);
      log_.info(ME, string("connect qos for second reconnection: ") + connQos_->toXml());
      currentSessionId = tmp.getSessionQos().getSessionId();
      log_.info(ME, string("session Id after second reconnection: '") + currentSessionId + "'");
   }


   void testFailsafe() 
   {
   	  int imax = 30;
      try {
         pubQos_ = new PublishQos(global_);
         pubKey_ = new PublishKey(global_);
         pubKey_->setOid("TestFailsafe");

         for (int i=0; i < imax; i++) {
            string msg = lexical_cast<string>(i);
            MessageUnit msgUnit(*pubKey_, msg, *pubQos_);
            log_.info(ME, string("publishing msg '") + msg + "'");
            PublishReturnQos pubRetQos = connection_->publish(msgUnit);
            if (i == 2 && useEmbeddedServer_) {
               embeddedServer_->stop();
            }
            if (i == 12 && useEmbeddedServer_) {
               embeddedServer_->start();
            }
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

      int i = 0;
      while (numOfUpdates_ < (imax-1) && i < 100) {
         i++;
         Thread::sleep(100);
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
      Lock lock(updateMutex_);
      if (log_.TRACE) log_.trace(ME, "update: key    : " + updateKey.toXml());
      if (log_.TRACE) log_.trace(ME, "update: qos    : " + updateQos.toXml());
      string help((char*)content, (char*)(content)+contentSize);
      if (log_.TRACE) log_.trace(ME, "update: content: " + help);
      if (updateQos.getState() == "ERASED" ) return "";

      int count = atoi(help.c_str());
      assertEquals(log_, ME, numOfUpdates_, count, string("update check ") + help);
      numOfUpdates_++;
      return "";
   }

};

/**
 * Try
 * <pre>
 *   java TestFailsafe -help
 * </pre>
 * for usage help
 *
 * To disable the embedded server add -embeddedServer false
 */
int main(int args, char ** argv)
{
   try {
      XMLPlatformUtils::Initialize();
      Global& glob = Global::getInstance();
      glob.initialize(args, argv);
   // XmlBlasterConnection::usage();
   //   glob.getLog().info("TestFailsafe", "Example: java TestFailsafe\n");

      TestFailsafe testFailsafe(glob);
      testFailsafe.setUp();
      // testFailsafe.testReconnect();
      testFailsafe.testFailsafe();
      testFailsafe.tearDown();
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
