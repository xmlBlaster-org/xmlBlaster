
#include <util/xmlBlasterDef.h>
#include <util/objman.h>
#include <util/Global.h>
#include <cstdlib> //<stdlib.h>

using namespace std;
using namespace org::xmlBlaster::client::protocol;
using namespace org::xmlBlaster::util::dispatch;
using namespace org::xmlBlaster::util;

//static int gObjectManagerState = 0;
// Cleanup routine for atexit
extern "C" void object_manager_cleanup()
   { 
        /*
     if(gObjectManagerState != Object_Lifetime_Manager_Base::OBJ_MAN_SHUT_DOWN &&
        gObjectManagerState != Object_Lifetime_Manager_Base::OBJ_MAN_SHUTTING_DOWN)
       ;//Object_Lifetime_Manager::instance()->fini();
        */
   }

namespace org { namespace xmlBlaster { namespace util {

#define PREALLOCATE_OBJECT(TYPE, ID)\
{\
  TYPE* t = new TYPE;\
  ManagedObject<TYPE> *obj_p;\
  obj_p = new  ManagedObject<TYPE>(t);\
  preallocated_object[ID] = obj_p;\
}

//namespace org { namespace xmlBlaster { namespace util {

// Static declarations for Object_Lifetime_Manager.
Object_Lifetime_Manager * Object_Lifetime_Manager::instance_ = 0;
ManagedMap              Object_Lifetime_Manager::managedObjectMap_;
ManagedList             Object_Lifetime_Manager::managedObjectList_;
void *                  Object_Lifetime_Manager::preallocated_object[PREALLOCATED_OBJECTS];


void Object_Lifetime_Manager::init (void)
{
   Object_Lifetime_Manager::instance()->startup();
}

void Object_Lifetime_Manager::fini (void)
{
   Object_Lifetime_Manager::instance()->shutdown();
}

/**
 * This function should be called during
 * construction of static instances, or
 * before any other threads have been created
 * in the process. So, it's not thread safe.
 */
Object_Lifetime_Manager * Object_Lifetime_Manager::instance (void)
{
  if (instance_ == 0) 
  {
    Object_Lifetime_Manager *instance_pointer =
    new Object_Lifetime_Manager;
    
    instance_pointer->dynamically_allocated_ = 1;
  }
  return instance_;
}

int Object_Lifetime_Manager::startup ()
{
  if (starting_up_i ()) 
  {
    // First, indicate that this
    // Object_Lifetime_Manager instance
    // is being initialized.
    object_manager_state_ = OBJ_MAN_INITIALIZING;
    if (this == instance_) 
    {      
      // Create Global as part of ObjectManager startup.
      PREALLOCATE_OBJECT(Global, XMLBLASTER_GLOBAL);
      object_manager_state_ = OBJ_MAN_INITIALIZED;    
    }
    return 0;
  } 
  else 
  {
    // Hads already initialized.
    return 1;
  }
}

int Object_Lifetime_Manager::shutdown ()
{
   if (shutting_down_i())
      // Too late. Or, maybe too early. Either
      // <fini> has already been called, or
      // <init> was never called.
      return object_manager_state_ == OBJ_MAN_SHUT_DOWN ? 1 : -1;

   // Indicate that the Object_Lifetime_Manager
   // instance is being shut down.
   // This object manager should be the last one
   // to be shut down.
   /*gObjectManagerState = */object_manager_state_ = OBJ_MAN_SHUTTING_DOWN;

   // Only clean up Pre-allocated Objects when
   // the singleton Instance is being destroyed.
   // The sequence is reversed, the last created object is destroyed first.
   if (this == instance_) {
      ManagedList::reverse_iterator i;
      for(i = managedObjectList_.rbegin(); i != managedObjectList_.rend(); ++i) {
         Cleanup_Adaptor* adap = *i;
         adap->cleanup();
         delete adap;
      }
      managedObjectList_.clear();

      managedObjectMap_.clear();

      // Delete the Global post of list method as a dependency may exist
      // between objects existing in both constructs.
      // Array is last as all objects placed here are intentional and
      // it is assumed user is aware of such semantics.
      delete (Cleanup_Adaptor*)preallocated_object[XMLBLASTER_GLOBAL];
   }

   // Indicate that this Object_Lifetime_Manager
   // instance has been shut down.
   /*gObjectManagerState = */object_manager_state_ = OBJ_MAN_SHUT_DOWN;
   if (dynamically_allocated_)
      delete this;
   if (this == instance_)
      instance_ = 0;
   return 0;
}

}}} // namespace
