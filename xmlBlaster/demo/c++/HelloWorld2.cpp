/*------------------------------------------------------------------------------
Name:      xmlBlaster/demo/c++/HelloWorld2.cpp
Project:   xmlBlaster.org
Comment:   C++ client example
Author:    Michele Laghi
------------------------------------------------------------------------------*/
#include <client/XmlBlasterAccess.h>
#include <util/XmlBlasterException.h>
#include <util/ErrorCode.h>
#include <util/Global.h>
#include <util/Log.h>
#include <util/Timestamp.h>

using namespace std;
using namespace org::xmlBlaster::util;
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
 * Invoke: HelloWorld2
 * </pre>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html"
 *              target="others">xmlBlaster interface</a>
 */
class HelloWorld2 : public I_Callback,          // for the asynchroneous updates
                    public I_ConnectionProblems // notification of connection problems when failsafe
{
private:
   string  ME;                        // the string identifying this class when logging
   Global& global_;
   Log&    log_;                      // the reference to the log object for this instance

public:
   HelloWorld2(Global& glob) 
   : ME("HelloWorld2"),
     global_(glob), 
     log_(glob.getLog("demo"))        // all logs written in this class are written to the
   {                                  // log channel called 'demo'. To see the traces of this
                                      // channel invoke -trace[demo] true on the command line,
                                      // then it will only switch on the traces for the demo channel
      log_.info(ME, "Trying to connect to xmlBlaster with C++ client lib " + Global::getVersion() +
                    " from " + Global::getBuildTimestamp());
   }

   virtual ~HelloWorld2()             // the constructor does nothing for the moment
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
         subKey.setOid("HelloWorld2");
         SubscribeQos subQos(global_);
         log_.info(ME, string("subscribing to xmlBlaster with key: ") + subKey.toXml() +
                       " and qos: " + subQos.toXml());

         SubscribeReturnQos subRetQos = con.subscribe(subKey, subQos);
         log_.info(ME, string("successfully subscribed to xmlBlaster. Return qos: ") +
                       subRetQos.toXml());

         // publish a message with the oid 'HelloWorld2'
         PublishQos publishQos(global_);
         PublishKey publishKey(global_);
         publishKey.setOid("HelloWorld2");
         MessageUnit msgUnit(publishKey, string("Hi"), publishQos);
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
         eraseKey.setOid("HelloWorld2");
         EraseQos eraseQos(global_);
         log_.info(ME, string("erasing the published message. Key: ") + eraseKey.toXml() +
                       " qos: " + eraseQos.toXml());
         vector<EraseReturnQos> eraseRetQos = con.erase(eraseKey, eraseQos);
         for (size_t i=0; i < eraseRetQos.size(); i++ ) {
            log_.info(ME, string("successfully erased the message. return qos: ") +
                          eraseRetQos[i].toXml());
         }

         log_.info(ME, "Disconnect, bye.");
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
   string update(const string& sessionId, UpdateKey& updateKey,
                 const unsigned char* content,
                 long contentSize, UpdateQos& updateQos)
   {
      string contentStr(reinterpret_cast<char *>(const_cast<unsigned char *>(content)), contentSize);
      log_.info(ME, "Received update message with secret sessionId '" + sessionId + "':" +
                    updateKey.toXml() +
                    "\n content=" + contentStr +
                    updateQos.toXml());
      // if (true) throw XmlBlasterException(USER_UPDATE_ERROR, "HelloWorld2", "TEST");
      return "";
   }

};

/**
 * Try
 * <pre>
 *   HelloWorld2 -help
 * </pre>
 * for usage help
 */
int main(int args, char ** argv)
{
   org::xmlBlaster::util::Object_Lifetime_Manager::init();
   Global& glob = Global::getInstance();
   glob.initialize(args, argv);
   
   string intro = "XmlBlaster C++ client " + glob.getVersion() +
                  ", try option '-help' if you need usage informations.";
   glob.getLog().info("HelloWorld2", intro);

   if (glob.wantsHelp()) {
      glob.getLog().info("HelloWorld2", Global::usage());
      glob.getLog().info("HelloWorld2", "Example: HelloWorld2 -trace true\n");
      org::xmlBlaster::util::Object_Lifetime_Manager::fini();
      return 1;
   }

   HelloWorld2 hello(glob);
   hello.execute();
   org::xmlBlaster::util::Object_Lifetime_Manager::fini();
   return 0;
}
