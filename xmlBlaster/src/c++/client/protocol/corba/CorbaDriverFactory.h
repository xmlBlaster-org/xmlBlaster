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
#include <util/objman.h> // for managed objects






namespace org {
 namespace xmlBlaster {
  namespace client {
   namespace protocol {
    namespace corba {

typedef std::pair<CorbaDriver*, int>  DriverEntry;
typedef std::map<org::xmlBlaster::util::Global*, DriverEntry> DriversMap;

/**
 * Factory used to create instances of CorbaDriver objects. It currently is a singleton class and has for
 * that reason private constructors, destructor and assignment operator. 
 * To get a reference to the singleton instance you must invoke getFactory(...).
 * 
 * This factory has a running thread in which the orb performs its work. For threadsafe orbs, this is a
 * blocking invocation to orb.run() (which exits when shutting down the orb). For singlethreaded orbs, it is
 * a loop invoking orb.perform_work() with a sleep interval of 20 ms between each invocation to that method.
 *
 * You can either pass to this factory a previously instantiated orb by passing it explicitly, or you can 
 * let it instantiate the default orb (with the arguments you passed at application startup) by not passing
 * any orb or by passing NULL.
 * If you pass an external orb, then this factory will not start the mentionned working thread nor it will 
 * free the orb resources. You must handle that on your own.
 * If you let the factory create an orb instance, then this factory will start the thread and will cleanup
 * all resources used by the orb.
 *
 */
class Dll_Export CorbaDriverFactory : public org::xmlBlaster::util::thread::Thread
{
friend CorbaDriverFactory& getFactory(org::xmlBlaster::util::Global& global, CORBA::ORB_ptr orb=NULL);

// required for the managed objects
friend class Object_Lifetime_Manager;
friend class ManagedObject;

private:
   const std::string   ME;
   //org::xmlBlaster::util::Global&        global_;
   //org::xmlBlaster::util::I_Log&           log_;
   DriversMap     drivers_;         // the std::map containing all drivers created by this factory
   bool           doRun_;           // the command: if set to 'false' the thread will stop.
   bool           isRunning_;       // the status: if the thread is running it is 'true'
   org::xmlBlaster::util::thread::Mutex          mutex_,           // the mutex passed to all CorbaDriver instances (for singlethreaded)
                  getterMutex_;     // the mutex used for creating/deleting CorbaDriver instances
   bool           orbIsThreadSafe_; // flag telling if the orb is a singletheraded or multithreaded orb
   CORBA::ORB_ptr orb_;             // the orb used (either created here or passed in constructor
   bool           isOwnOrb_;        // 'true' if the orb has been created by this factory.

   static CorbaDriverFactory* factory_;

   /**
    * Only used by getInstance()
    * @param global
    * @param mutex   org::xmlBlaster::util::Global thread synchronization (to avoid static variable)
    * @param doRun   Only for internal main loop for single threaded orbs. false stops the loop
    *                
    * @param isRunning    Feedback is doRun has stopped
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

   CorbaDriverFactory(org::xmlBlaster::util::Global& global, CORBA::ORB_ptr orb=NULL);

   CorbaDriverFactory(const CorbaDriverFactory& factory);
   CorbaDriverFactory& operator =(const CorbaDriverFactory& factory);

public:
   ~CorbaDriverFactory(); // Should be private, VC7 is ok with private, g++ 3.3 does not like it

   /**
    * You can assign only one orb per CorbaDriverFactory object. Since this class is currently a singleton,
    * you can only assign one single orb to handle communication to xmlBlaster. So mixed orb implementation 
    * usage is still allowed, but to communicate to xmlBlaster you must use one single orb implementation per
    * client application.
    *
    * @param global the global parameter to pass
    * @param orb the orb to pass. Note that if you pass NULL (the default) then an own orb is initialized in
    *        the CorbaConnection class encapsulated by CorbaDriver. If you pass an orb different from NULL,
    *        then you are responsible of making the orb perform work and you must clean up its resources
    *        i.e. you must call expicitly shutdown and destroy on work completition.
    */
   static CorbaDriverFactory& getFactory(org::xmlBlaster::util::Global& global, CORBA::ORB_ptr orb=NULL);

   /**
    * gets an instance of a corba driver with the specified name.
    */
   CorbaDriver& getDriverInstance(org::xmlBlaster::util::Global* global);

   /**
    * Kills the driver instance owned by the specified Global. Note that if you invoked getDriverInstance several 
    * times with the same instanceName, you just decrement the internal reference counter. When the reference
    * counter reaches zero, the driver is really destroyed.
    */
   int killDriverInstance(org::xmlBlaster::util::Global* global);

};

}}}}} // namespaces

#endif
