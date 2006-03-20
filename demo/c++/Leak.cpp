/*------------------------------------------------------------------------------
Name:      xmlBlaster/demo/c++/Leak.cpp
Project:   xmlBlaster.org
Comment:   Manually check for memory leaks (use for example valgrind to check)
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

class SpecificCallback : public I_Callback
{
 private:
   const string ME;
   I_Log& log_;
 public:
   SpecificCallback(const GlobalRef global) : ME(global->getInstanceName()),
                                              log_(global->getLog("Leak"))
   {}

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
};


class Leak
{
private:
   string  ME;      /**< the string identifying this class when logging */
   Global& global_; /**< The singleton Global instance, handled by Object_Lifetime_Manager */
   I_Log& log_;     /**< Logging output */
   int count;
   bool holdReferenceCount;

public:
   Leak(Global& glob) : ME("Leak"), global_(glob), 
                                    log_(glob.getLog("Leak")),
                                    count(5),
                                    holdReferenceCount(false) {
      count = global_.getProperty().get("count", count);
      holdReferenceCount = global_.getProperty().get("holdReferenceCount", holdReferenceCount);
   }

   virtual ~Leak() {}

   void checkGlobal()
   {
      log_.info(ME, "checkGlobal()");
      try {
         for (int i=0; i<count; i++) {
            string instanceName = string("connection-") + lexical_cast<std::string>(i);
            Property::MapType propMap;
            GlobalRef globalRef = global_.createInstance(instanceName, &propMap, holdReferenceCount);
            log_.info(ME, "Global created " + globalRef->getId());
            if (holdReferenceCount)
               global_.destroyInstance(instanceName);
         }
      }
      catch (const XmlBlasterException &e) {
         log_.error(ME, e.toXml());
      }
   }

   void checkGlobal2()
   {
      log_.info(ME, "checkGlobal2()");
      vector<GlobalRef> globVec; // Holding all connections to xmlBlaster
      try {
         for (int i=0; i<count; i++) {
            string instanceName = string("connection-") + lexical_cast<std::string>(i);
            Property::MapType propMap;
            GlobalRef globalRef = global_.createInstance(instanceName, &propMap, holdReferenceCount);
            globVec.push_back(globalRef);
            log_.info(ME, "Global created " + globalRef->getId());
            if (holdReferenceCount)
               global_.destroyInstance(instanceName);
         }
         for (int i=0; i<count; i++) {
            GlobalRef globalRef = globVec[i];
            log_.info(ME, "Global destroy " + globalRef->getId());
            if (holdReferenceCount)
               global_.destroyInstance(globalRef->getInstanceName());
         }
         globVec.clear();
      }
      catch (const XmlBlasterException &e) {
         log_.error(ME, e.toXml());
      }
   }

   void checkConnection()
   {
      log_.info(ME, "checkConnection()");
      try {
         for (int i=0; i<count; i++) {
            string instanceName = string("connection-") + lexical_cast<std::string>(i);
            Property::MapType propMap;
            GlobalRef globalRef = global_.createInstance(instanceName, &propMap);
            XmlBlasterAccessRef con = XmlBlasterAccessRef(new XmlBlasterAccess(globalRef));
            SpecificCallback* cbP = new SpecificCallback(globalRef);
            ConnectQos qos(*globalRef);
            ConnectReturnQos retQos = con->connect(qos, cbP);
            log_.info(ME, "Successfully connected to xmlBlaster as " +
                      retQos.getSessionQos().getSessionName()->getAbsoluteName());
            con->disconnect(DisconnectQos(con->getGlobal()));
            delete con->getCallback();  // same as *cbP
         }
      }
      catch (const XmlBlasterException &e) {
         log_.error(ME, e.toXml());
      }
   }

   // Using the pointers seems to leak memory -> this issue is not yet resolved!
   void checkConnection2()
   {
      log_.info(ME, "checkConnection2()");
      try {
         XmlBlasterAccess** refs;
         refs = new XmlBlasterAccess*[count];
         for (int i=0; i<count; i++) {
            string instanceName = string("connection2-") + lexical_cast<std::string>(i);
            Property::MapType propMap;
            GlobalRef globalRef = global_.createInstance(instanceName, &propMap);
            XmlBlasterAccess* con = new XmlBlasterAccess(globalRef);
            refs[i] = con;
            SpecificCallback* cbP = new SpecificCallback(globalRef);
            ConnectQos qos(*globalRef);
            ConnectReturnQos retQos = con->connect(qos, cbP);
            log_.info(ME, "Successfully connected to xmlBlaster as " +
                      retQos.getSessionQos().getSessionName()->getAbsoluteName());
         }
         for (int i=0; i<count; i++) {
            XmlBlasterAccess* con = refs[i];
            con->disconnect(DisconnectQos(con->getGlobal()));
            delete con->getCallback();  // same as *cbP
            delete con;
         }
         delete [] refs;
      }
      catch (const XmlBlasterException &e) {
         log_.error(ME, e.toXml());
      }
   }

   // Using the vector seems to leak memory -> this issue is not yet resolved!
   void checkConnection3()
   {
      log_.info(ME, "checkConnection3()");
      try {
         vector<XmlBlasterAccessRef> connVec; // Holding all connections to xmlBlaster
         for (int i=0; i<count; i++) {
            string instanceName = string("connection2-") + lexical_cast<std::string>(i);
            Property::MapType propMap;
            GlobalRef globalRef = global_.createInstance(instanceName, &propMap);
            XmlBlasterAccessRef con = XmlBlasterAccessRef(new XmlBlasterAccess(globalRef));
            connVec.push_back(con);
            SpecificCallback* cbP = new SpecificCallback(globalRef);
            ConnectQos qos(*globalRef);
            ConnectReturnQos retQos = con->connect(qos, cbP);
            log_.info(ME, "Successfully connected to xmlBlaster as " +
                      retQos.getSessionQos().getSessionName()->getAbsoluteName());
         }
         for (int i=0; i<count; i++) {
            XmlBlasterAccessRef con = connVec[i];
            con->disconnect(DisconnectQos(con->getGlobal()));
            delete con->getCallback();  // same as *cbP
         }
         connVec.erase(connVec.begin(), connVec.end());
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
 *   Leak -help
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
      glob.getLog().info("Leak", intro);

      if (glob.wantsHelp()) {
         cout << Global::usage() << endl;
         cout << endl << "Leak";
         cout << endl << "   -sleep              Sleep after publishing [1000 millisec]" << endl;
         cout << endl << "Example:" << endl;
         cout << endl << "Leak -trace true -sleep 2000";
         cout << endl << "Leak -dispatch/connection/delay 10000 -sleep 2000000" << endl << endl;
         org::xmlBlaster::util::Object_Lifetime_Manager::fini();
         return 1;
      }

      Leak hello(glob);
      if (glob.getProperty().get("global", false))
         hello.checkGlobal();
      if (glob.getProperty().get("global2", false))
         hello.checkGlobal2();
      if (glob.getProperty().get("con", false))
         hello.checkConnection();
      if (glob.getProperty().get("con2", false))
         hello.checkConnection2();
      if (glob.getProperty().get("con3", false))
         hello.checkConnection3();
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
