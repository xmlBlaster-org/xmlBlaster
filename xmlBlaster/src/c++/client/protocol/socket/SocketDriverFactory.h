/*------------------------------------------------------------------------------
Name:      SocketDriverFactory.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The factory for the client driver for the SOCKET protocol
------------------------------------------------------------------------------*/
#ifndef _CLIENT_PROTOCOL_SOCKET_SOCKETDRIVERFACTORY
#define _CLIENT_PROTOCOL_SOCKET_SOCKETDRIVERFACTORY

#include <util/xmlBlasterDef.h>
#include <client/protocol/socket/SocketDriver.h>
#include <util/objman.h> // for managed objects

namespace org {
 namespace xmlBlaster {
  namespace client {
   namespace protocol {
    namespace socket {

typedef std::pair<SocketDriver*, int> DriverEntry;
typedef std::map<org::xmlBlaster::util::Global*, DriverEntry> DriversMap;

/**
 * Factory used to create instances of SocketDriver objects. It currently is a singleton class and has for
 * that reason private constructors, destructor and assignment operator. 
 * To get a reference to the singleton instance you must invoke getFactory(...).
 */
class Dll_Export SocketDriverFactory
{
friend SocketDriverFactory& getFactory(org::xmlBlaster::util::Global& global);

// required for the managed objects
friend class Object_Lifetime_Manager;
friend class ManagedObject;

private:
   const std::string   ME;
   DriversMap     drivers_;         //< the std::map containing all drivers created by this factory
   org::xmlBlaster::util::thread::Mutex getterMutex_;  //< the mutex used for creating/deleting SocketDriver instances

   static SocketDriverFactory* factory_;

   /** Not used here */
   void run();

   SocketDriverFactory(org::xmlBlaster::util::Global& global);

   SocketDriverFactory(const SocketDriverFactory& factory);
   SocketDriverFactory& operator =(const SocketDriverFactory& factory);

public:
   ~SocketDriverFactory(); // Should be private, VC7 is ok with private, g++ 3.3 does not like it

   /**
    */
   static SocketDriverFactory& getFactory(org::xmlBlaster::util::Global& global);

   /**
    * gets an instance of a socket driver with the specified name.
    */
   SocketDriver& getDriverInstance(org::xmlBlaster::util::Global* global);

   /**
    * Kills the driver instance with the given name. Note that if you invoked getDriverInstance several 
    * times with the same global object, you just decrement the internal reference counter. When the reference
    * counter reaches zero, the driver is really destroyed.
    */
   int killDriverInstance(org::xmlBlaster::util::Global* global);

};

}}}}} // namespaces

#endif
