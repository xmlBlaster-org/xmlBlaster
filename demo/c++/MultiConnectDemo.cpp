/*------------------------------------------------------------------------------
Name:      xmlBlaster/demo/c++/MultiConnectDemo.cpp
Project:   xmlBlaster.org
Comment:   C++ client example
Author:    Marcel Ruff
------------------------------------------------------------------------------*/
#include <client/XmlBlasterAccess.h>
#include <util/Global.h>
#include <vector>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::dispatch;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

/**
 * Helper class to receive the connection specific callback messages. 
 * One instance of this will be used for each connection.
 * <p>
 * Additionally we listen on connection change events (for example if
 * the xmlBlaster server disappears).
 * </p>
 */
class SpecificCallback : public I_Callback, public I_ConnectionProblems
{
 private:
   const string ME;
   I_Log& log_;
 public:
   SpecificCallback(const GlobalRef global) : ME(global->getInstanceName()),
                                              log_(global->getLog("MultiConnectDemo"))
   {}

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
      return "";
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
};


/**
 * This client connects 5 times to xmlBlaster. 
 * <p>
 * All five connections subscribe to a message, we then publish the message
 * and receive it 5 times asynchronous in the connection specific update() method.
 * </p>
 * <pre>
 * Invoke: MultiConnectDemo
 * </pre>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html"
 *              target="others">xmlBlaster interface</a>
 */
class MultiConnectDemo
{
private:
   string  ME;      /**< the string identifying this class when logging */
   Global& global_; /**< The singleton Global instance, handled by Object_Lifetime_Manager */
   I_Log& log_;     /**< Logging output */

public:
   MultiConnectDemo(Global& glob) : ME("MultiConnectDemo"), global_(glob), 
                                    log_(glob.getLog("MultiConnectDemo")) {}

   virtual ~MultiConnectDemo() {}

   void execute()
   {
      const int NUM_CONN = global_.getProperty().get("numConn", 10);
      long sleepMillis = global_.getProperty().get("sleep", 1000L);
      try {
         vector<XmlBlasterAccessRef> connVec; // Holding all connections to xmlBlaster
         // Connect 5 times to xmlBlaster
         for (int i=0; i<NUM_CONN; i++) {
            string instanceName = string("connection-") + lexical_cast<std::string>(i);
            Property::MapType propMap;
            propMap["session.name"] = instanceName; // Set a unique login name
            GlobalRef globalRef = Global::getInstance().createInstance(instanceName, &propMap);
            connVec.push_back(XmlBlasterAccessRef(new XmlBlasterAccess(globalRef)));

            SpecificCallback* cbP = new SpecificCallback(globalRef);
            //connVec[i]->initFailsafe(cbP);
            ConnectQos qos(*globalRef);
            ConnectReturnQos retQos = connVec[i]->connect(qos, cbP);
            log_.info(ME, "Successfully connected to xmlBlaster as " +
                      retQos.getSessionQos().getSessionName()->getAbsoluteName());
         }

         // Subscribe 5 times
         for (int i=0; i<NUM_CONN; i++) {
            SubscribeKey subKey(connVec[i]->getGlobal());
            subKey.setOid("MultiConnectDemo");
            SubscribeQos subQos(connVec[i]->getGlobal());
            log_.info(ME, "Subscribing to xmlBlaster"); // + subKey.toXml() +
            SubscribeReturnQos subRetQos = connVec[i]->subscribe(subKey, subQos);
            log_.info(ME, "Successfully subscribed to xmlBlaster: " + subRetQos.getSubscriptionId());
         }

         // Publish a message with the oid 'MultiConnectDemo'
         // all subscribers should receive it
         PublishQos publishQos(connVec[0]->getGlobal());
         PublishKey publishKey(connVec[0]->getGlobal());
         publishKey.setOid("MultiConnectDemo");
         MessageUnit msgUnit(publishKey, string("Hi"), publishQos);
         log_.info(ME, "Publishing to xmlBlaster");
         PublishReturnQos pubRetQos = connVec[0]->publish(msgUnit);
         log_.info(ME, "Successfully published to xmlBlaster: " + pubRetQos.getKeyOid());
         try {
            log_.info(ME, "Sleeping now for " + lexical_cast<string>(sleepMillis) + " msec ...");
            org::xmlBlaster::util::thread::Thread::sleep(sleepMillis);
         }
         catch(const XmlBlasterException &e) {
            log_.error(ME, e.toXml());
         }

         // Erase the topic
         EraseKey eraseKey(connVec[0]->getGlobal());
         eraseKey.setOid("MultiConnectDemo");
         EraseQos eraseQos(connVec[0]->getGlobal());
         log_.info(ME, "Erasing the published message");
         connVec[0]->erase(eraseKey, eraseQos);

         // Disconnect all clients
         for (int i=0; i<NUM_CONN; i++) {
            connVec[i]->disconnect(DisconnectQos(connVec[i]->getGlobal()));
            delete connVec[i]->getCallback();
         }

         connVec.clear();
         log_.info(ME, "Done, resources are released");
      }
      catch (const XmlBlasterException &e) {
         log_.error(ME, e.toXml());
      }
   }
};

#include <iostream>

/**
 * Try
 * <pre>
 *   MultiConnectDemo -help
 * </pre>
 * for usage help
 */
int main(int args, char ** argv)
{
   try {
      org::xmlBlaster::util::Object_Lifetime_Manager::init();
      Global& glob = Global::getInstance();
      glob.initialize(args, argv);
      
      string intro = "XmlBlaster C++ client " + glob.getReleaseId() +
                     ", try option '-help' if you need usage informations.";
      glob.getLog().info("MultiConnectDemo", intro);

      if (glob.wantsHelp()) {
         cout << Global::usage() << endl;
         cout << endl << "MultiConnectDemo";
         cout << endl << "   -sleep              Sleep after publishing [1000 millisec]" << endl;
         cout << endl << "Example:" << endl;
         cout << endl << "MultiConnectDemo -trace true -sleep 2000";
         cout << endl << "MultiConnectDemo -dispatch/connection/delay 10000 -sleep 2000000" << endl << endl;
         org::xmlBlaster::util::Object_Lifetime_Manager::fini();
         return 1;
      }

      MultiConnectDemo hello(glob);
      hello.execute();
   }
   catch (XmlBlasterException &e) {
      std::cerr << "Caught exception: " << e.getMessage() << std::endl;
   }
   catch (...) {
      std::cerr << "Caught exception, exit" << std::endl;
   }
   org::xmlBlaster::util::Object_Lifetime_Manager::fini();
   return 0;
}
