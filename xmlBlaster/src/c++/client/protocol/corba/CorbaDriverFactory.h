/*------------------------------------------------------------------------------
Name:      CorbaDriverFactory.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The Factory for the client driver for the corba protocol
------------------------------------------------------------------------------*/

#ifndef _CLIENT_PROTOCOL_CORBA_CORBADRIVERFACTORY
#define _CLIENT_PROTOCOL_CORBA_CORBADRIVERFACTORY

#include <util/xmlBlasterDef.h>
#include <client/protocol/corba/CorbaDriver.h>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::thread;
using namespace std;

namespace org {
 namespace xmlBlaster {
  namespace client {
   namespace protocol {
    namespace corba {

typedef pair<CorbaDriver*, int>  DriverEntry;
typedef map<string, DriverEntry> DriversMap;

class Dll_Export CorbaDriverFactory : public Thread
{
friend CorbaDriverFactory& getFactory(Global& global, CORBA::ORB_ptr orb=NULL);

private:
   const string   ME;
   Global&        global_;
   Log&           log_;
   DriversMap     drivers_;
   bool           doRun_;
   bool           isRunning_; 
   Mutex          mutex_, getterMutex_;
   bool           orbIsThreadSafe_;
   CORBA::ORB_ptr orb_;
   bool           isOwnOrb_;

   /**
    * Only used by getInstance()
    * @param global
    * @param mutex   Global thread synchronization (to avoid static variable)
    * @param doRun   Only for internal main loop for single threaded orbs. false stops the loop
    *                
    * @param isRunning    Feedback is doRun has stopped
    * @param instanceName
    * @param orb
    */

   /**
    * For single threaded CORBA implementations only (like MICO).
    * One instance (the first) starts a main loop and checks if the
    * orb has some work to perform (every 20 millis).
    * In your real application this should be done by your main loop (e.g. from X-Window)
    * E.g. mico has a helper implementation to register its file descriptors with another main loop.
    */
   void run();

   /** This is specific for threadsafe orbs like TAO */
   bool orbRun();

   CorbaDriverFactory(Global& global, CORBA::ORB_ptr orb=NULL);

   ~CorbaDriverFactory();

   CorbaDriverFactory(const CorbaDriverFactory& factory);
   CorbaDriverFactory& operator =(const CorbaDriverFactory& factory);

public:

   /**
    * You can assign only one orb per CorbaDriverFactory object. Since this class is currently a singleton,
    * you can only assign one single orb to handle communication to xmlBlaster. So mixed orb implementation 
    * usage is still allowed, but to communicate to xmlBlaster you must use one single orb implementation per
    * client application.
    *
    * @param global the global parameter to pass
    * @param orb the orb to pass. Note that if you pass NULL (the default) then an own orb is initialized in
    *        the CorbaConnection class encapsulated by CorbaDriver.
    */
   static CorbaDriverFactory& getFactory(Global& global, CORBA::ORB_ptr orb=NULL);
   
   CorbaDriver& getDriverInstance(const string& instanceName);

   int killDriverInstance(const string& instanceName);

};

}}}}} // namespaces

#endif
