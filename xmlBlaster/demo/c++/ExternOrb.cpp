#ifdef COMPILE_CORBA_PLUGIN

#include <client/XmlBlasterAccess.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include <util/Timestamp.h>
#include <client/protocol/corba/CorbaDriverFactory.h>
#include <iostream>

/**
 * This demo shows how to use XmlBlasterAccess with an orb which is already initialized. Since
 * XmlBlasterAccess does not know anything about CORBA, you must instantiate a CorbaDriver outside.
 * To make sure that the XmlBlasterAccess instance you use will internally use the CorbaDriver you
 * instantiated, you have to pass the same instanceName to both.
 * When passing an orb to CorbaDriver::getInstance(...) the CorbaDriver does not start a thread to
 * perform the orb work. You have to provide it yourself (See here the run() method provided).
 *
 *
 * This client connects to xmlBlaster and subscribes to a message.
 * <p />
 * We then publish the message and receive it asynchronous in the update() method.
 * <p />
 * Invoke: ExternOrb
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 */

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::util::dispatch;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::client::protocol::corba;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

namespace org { namespace xmlBlaster { namespace demo {

class ExternOrb : public I_Callback,           // for the asynchroneous updates
                  public I_ConnectionProblems, // for notification of connection problems when failsafe
                  public Thread                // the thread to perform the orb work
{
private:
   string              ME;                     // the string identifying this class when logging
   Global&             global_;
   I_Log&              log_;                                                                                                                                // the reference to the log object for this instance
   bool                doRun_;
   CORBA::ORB_ptr      orb_;
   CorbaDriverFactory& factory_;
public:
   ExternOrb(Global& glob, CORBA::ORB_ptr orb)
   : Thread(),
     ME("ExternOrb"),
     global_(glob), 
     log_(glob.getLog("demo")),
     factory_(CorbaDriverFactory::getFactory(glob, orb))
   {                  
      doRun_ = true;
      orb_   = orb;
   } 


   virtual ~ExternOrb()                                                                                               // the constructor does nothing for the moment
   {
      doRun_ = false;
      this->join();
   }


   void run()
   {
      log_.info(ME, "the corba loop starts now");
      while (doRun_) {
         while (orb_->work_pending()) orb_->perform_work();
         sleep(20); // sleep 20 milliseconds
      }
      log_.info(ME, "the corba loop has ended now");
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
      start(false); // to start the orb worker ...
      try {
      // CorbaDriver driver = 
         XmlBlasterAccess con(global_);
         con.initFailsafe(this);

         // Creates a connect qos with the user 'joe' and the password 'secret'
         ConnectQos qos(global_, "joe", "secret");
         log_.info(ME, string("connecting to xmlBlaster. Connect qos: ") + qos.toXml());

         // connects to xmlBlaster and gives a pointer to this class to tell which update method to invoke
         // when callbacks come from the server.
         ConnectReturnQos retQos = con.connect(qos, this);  // Login to xmlBlaster, register for updates
         log_.info(ME, "successfully connected to xmlBlaster. Return qos: " + retQos.toXml());

         // subscribe key. By invoking setOid you implicitly choose the 'EXACT' mode. If you want to 
         // subscribe with XPATH use setQueryString instead.
         SubscribeKey subKey(global_);
         subKey.setOid("ExternOrb");
         SubscribeQos subQos(global_);
         log_.info(ME, string("subscribing to xmlBlaster with key: ") + subKey.toXml() + " and qos: " + subQos.toXml());

         SubscribeReturnQos subRetQos = con.subscribe(subKey, subQos);
         log_.info(ME, string("successfully subscribed to xmlBlaster. Return qos: ") + subRetQos.toXml());

         // publish a message with the oid 'ExternOrb'
         PublishQos publishQos(global_);
         PublishKey publishKey(global_);
         publishKey.setOid("ExternOrb");
         MessageUnit msgUnit(publishKey, string("Hi"), publishQos);
         log_.info(ME, string("publishing to xmlBlaster with message: ") + msgUnit.toXml());
         PublishReturnQos pubRetQos = con.publish(msgUnit);
         log_.info(ME, "successfully published to xmlBlaster. Return qos: " + pubRetQos.toXml());
         try {
            Thread::sleepSecs(1);
         }
         catch(XmlBlasterException e) {
            cout << e.toXml() << endl;
         }

         // now an update should have come. Its time to erase the message (otherwise you would get directly
         // an update the next time you connect to the same xmlBlaster server. Specify which messages you
         // want to erase. Note that you will get an update with the status of the UpdateQos set to 'ERASED'.
         EraseKey eraseKey(global_);
         eraseKey.setOid("ExternOrb");
         EraseQos eraseQos(global_);
         log_.info(ME, string("erasing the published message. Key: ") + eraseKey.toXml() + " qos: " + eraseQos.toXml());
         vector<EraseReturnQos> eraseRetQos = con.erase(eraseKey, eraseQos);
         for (size_t i=0; i < eraseRetQos.size(); i++ ) {
            log_.info(ME, string("successfully erased the message. return qos: ") + eraseRetQos[i].toXml());
         }

         log_.info(ME, "going to sleep for one minute");
         org::xmlBlaster::util::thread::Thread::sleep(60000);

         DisconnectQos disconnectQos(global_);
         con.disconnect(disconnectQos);
      }
      catch (XmlBlasterException e) {
         cout << e.toXml() << endl;
      }
   }

   string update(const string& /*sessionId*/, 
                 UpdateKey& updateKey, 
                 const unsigned char* /*content*/, 
                 long /*contentSize*/, 
                 UpdateQos& updateQos)
   {
      log_.info(ME, "update: key: " + updateKey.toXml());
      log_.info(ME, "update: qos: " + updateQos.toXml());
      return "";
   }

};

}}} // namespace
/**
 * Try
 * <pre>
 *   java ExternOrb -help
 * </pre>
 * for usage help
 */
int main(int args, char ** argv)
{
   org::xmlBlaster::util::Object_Lifetime_Manager::init();
   // suppose you have already initialized an orb
   CORBA::ORB_ptr orb = CORBA::ORB_init(args, argv);

   Global& glob = Global::getInstance();
   glob.initialize(args, argv);

   org::xmlBlaster::demo::ExternOrb hello(glob, orb);
   hello.execute();
   org::xmlBlaster::util::Object_Lifetime_Manager::fini();
   return 0;
}

#else // COMPILE_CORBA_PLUGIN
#include <iostream>
int main(int args, char ** argv)
{
   ::std::cout << "ExternOrb: COMPILE_CORBA_PLUGIN is not defined, nothing to do" << ::std::endl;
   return 0;
}
#endif // COMPILE_CORBA_PLUGIN

