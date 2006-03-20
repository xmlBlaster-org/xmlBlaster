/*------------------------------------------------------------------------------
Name:      xmlBlaster/demo/c++/TestEmptyContent.cpp
Project:   xmlBlaster.org
Comment:   C++ client example
Author:    Michele Laghi
------------------------------------------------------------------------------*/
#include <client/XmlBlasterAccess.h>
#include <util/XmlBlasterException.h>
#include <util/ErrorCode.h>
#include <util/Global.h>
#include <util/I_Log.h>
#include <util/Timestamp.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::dispatch;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

/**
 * This client connects to xmlBlaster and subscribes to a message.
 * <p>
 * We then publish the message and receive it asynchronous in the update() method.
 * </p>
 * <p>
 * Note that the CORBA layer is transparently hidden,
 * and all code conforms to STD C++ (with STL).
 * </p>
 * <pre>
 * Invoke: TestEmptyContent
 * </pre>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html"
 *              target="others">xmlBlaster interface</a>
 */
class TestEmptyContent : public I_Callback,     // for the asynchroneous updates
                    public I_ConnectionProblems // notification of connection problems when failsafe
{
private:
   string  ME;                        // the string identifying this class when logging
   Global& global_;
   I_Log&  log_;                      // the reference to the log object for this instance

public:
   TestEmptyContent(Global& glob) 
   : ME("TestEmptyContent"),
     global_(glob), 
     log_(glob.getLog("demo"))        // all logs written in this class are written to the
   {                                  // log channel called 'demo'. To see the traces of this
                                      // channel invoke -trace[demo] true on the command line,
                                      // then it will only switch on the traces for the demo channel
      log_.info(ME, "Trying to connect to xmlBlaster with C++ client lib " + Global::getVersion() +
                    " from " + Global::getBuildTimestamp());
   }

   virtual ~TestEmptyContent()             // the constructor does nothing for the moment
   {
   }


   bool reachedAlive(StatesEnum /*oldState*/, I_ConnectionsHandler* /*connectionsHandler*/)
   {
      log_.info(ME, "reconnected");
      return true;
   }

   void reachedDead(StatesEnum /*oldState*/, I_ConnectionsHandler* /*connectionsHandler*/)
   {
      log_.info(ME, "lost connection");
   }

   void reachedPolling(StatesEnum /*oldState*/, I_ConnectionsHandler* /*connectionsHandler*/)
   {
      log_.info(ME, "going to poll modus");
   }

   void execute()
   {
      try {
         XmlBlasterAccess con(global_);
         con.initFailsafe(this);

         // Creates a connect qos with the user 'joe' and the password 'secret'
         ConnectQos qos(global_, "joe", "secret");

         /* To test SOCKET plugin
         ServerRef ref("SOCKET", "socket://localhost:7604");
         qos.addServerRef(ref);
         */
         log_.info(ME, string("connecting to xmlBlaster. Connect qos: ") + qos.toXml());

         // connects to xmlBlaster and gives a pointer to this class to tell
         // which update method to invoke when callbacks come from the server.
         ConnectReturnQos retQos = con.connect(qos, this);  // Login and register for updates
         log_.info(ME, "successfully connected to xmlBlaster. Return qos: " + retQos.toXml());

         // subscribe key. By invoking setOid you implicitly choose the 'EXACT' mode.
         // If you want to subscribe with XPATH use setQueryString instead.
         SubscribeKey subKey(global_);
         subKey.setOid("TestEmptyContent");
         SubscribeQos subQos(global_);
         log_.info(ME, string("subscribing to xmlBlaster with key: ") + subKey.toXml() +
                       " and qos: " + subQos.toXml());

         SubscribeReturnQos subRetQos = con.subscribe(subKey, subQos);
         log_.info(ME, string("successfully subscribed to xmlBlaster. Return qos: ") +
                       subRetQos.toXml());

         // publish a message with the oid 'TestEmptyContent'
         PublishQos publishQos(global_);
         PublishKey publishKey(global_);
         publishKey.setOid("TestEmptyContent");
         publishKey.setContentMime("text/plain");
         MessageUnit msgUnit(publishKey, string(""), publishQos);
         log_.info(ME, string("publishing to xmlBlaster with message: ") + msgUnit.toXml());
         PublishReturnQos pubRetQos = con.publish(msgUnit);
         log_.info(ME, "successfully published to xmlBlaster. Return qos: " + pubRetQos.toXml());
         try {
            org::xmlBlaster::util::thread::Thread::sleepSecs(1);
         }
         catch(XmlBlasterException e) {
            log_.error(ME, e.toXml());
         }

         // now an update should have come. Its time to erase the message,
         // otherwise you would get directly an update the next time you connect
         // to the same xmlBlaster server.
         // Specify which messages you want to erase. Note that you will get an
         // update with the status of the UpdateQos set to 'ERASED'.
         EraseKey eraseKey(global_);
         eraseKey.setOid("TestEmptyContent");
         EraseQos eraseQos(global_);
         log_.info(ME, string("erasing the published message. Key: ") + eraseKey.toXml() +
                       " qos: " + eraseQos.toXml());
         vector<EraseReturnQos> eraseRetQos = con.erase(eraseKey, eraseQos);
         for (size_t i=0; i < eraseRetQos.size(); i++ ) {
            log_.info(ME, string("successfully erased the message. return qos: ") +
                          eraseRetQos[i].toXml());
         }

         log_.info(ME, "going to sleep for 2 sec and disconnect");
         org::xmlBlaster::util::thread::Thread::sleep(2000);

         DisconnectQos disconnectQos(global_);
         con.disconnect(disconnectQos);
      }
      catch (XmlBlasterException e) {
         log_.error(ME, e.toXml());
      }
   }

   /**
    * Callbacks from xmlBlaster arrive here. 
    */
   string update(const string& /*sessionId*/, UpdateKey& updateKey, const unsigned char* /*content*/,
                 long /*contentSize*/, UpdateQos& updateQos)
   {
      log_.info(ME, "update: key: " + updateKey.toXml());
      log_.info(ME, "update: qos: " + updateQos.toXml());
      log_.info(ME, "update: mime: " + updateKey.getContentMime());
      // if (true) throw XmlBlasterException(USER_UPDATE_ERROR, "TestEmptyContent", "");
      return "";
   }

};

/**
 * Try
 * <pre>
 *   TestEmptyContent -help
 * </pre>
 * for usage help
 */
int main(int args, char ** argv)
{
   org::xmlBlaster::util::Object_Lifetime_Manager::init();
   Global& glob = Global::getInstance();
   glob.initialize(args, argv);
// XmlBlasterAccess::usage();
// glob.getLog().info("TestEmptyContent", "Example: TestEmptyContent\n");

   TestEmptyContent hello(glob);
   hello.execute();
   org::xmlBlaster::util::Object_Lifetime_Manager::fini();
   return 0;
}
