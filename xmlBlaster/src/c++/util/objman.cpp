
#include <util/xmlBlasterDef.h>
#include <util/objman.h>
#include <util/Global.h>
#include <cstdlib> //<stdlib.h>

using namespace std;
using namespace org::xmlBlaster::client::protocol;
using namespace org::xmlBlaster::util::dispatch;
using namespace org::xmlBlaster::util;

static int gObjectManagerState = 0;
// Cleanup routine for atexit
extern "C" void object_manager_cleanup()
   { 
     if(gObjectManagerState != Object_Lifetime_Manager_Base::OBJ_MAN_SHUT_DOWN &&
        gObjectManagerState != Object_Lifetime_Manager_Base::OBJ_MAN_SHUTTING_DOWN)
       ;//Object_Lifetime_Manager::instance()->fini();
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

// Static declaratons for Object_Lifetime_Manager.
Object_Lifetime_Manager * Object_Lifetime_Manager::instance_ = 0;
managed_list              Object_Lifetime_Manager::managed_objects_list_;
void *                    Object_Lifetime_Manager::preallocated_object[PREALLOCATED_OBJECTS];


void Object_Lifetime_Manager::init (void)
{
  Object_Lifetime_Manager::instance()->startup();
}

void Object_Lifetime_Manager::fini (void)
{
  Object_Lifetime_Manager::instance()->shutdown();
}

Object_Lifetime_Manager * Object_Lifetime_Manager::instance (void)
{
// This function should be called during
// construction of static instances, or
// before any other threads have been created
// in the process. So, it’s not thread safe.
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
  if (shutting_down_i ())
    // Too late. Or, maybe too early. Either
    // <fini> has already been called, or
    // <init> was never called.
    return object_manager_state_ == OBJ_MAN_SHUT_DOWN ? 1 : -1;

  // Indicate that the Object_Lifetime_Manager
  // instance is being shut down.
  // This object manager should be the last one
  // to be shut down.
  gObjectManagerState = object_manager_state_ = OBJ_MAN_SHUTTING_DOWN;
  
  // Only clean up Preallocated Objects when
  // the singleton Instance is being destroyed.
  if (this == instance_) 
  {
   
    managed_list::iterator i;

    for(i = managed_objects_list_.begin(); i != managed_objects_list_.end(); i++)
    {
      (dynamic_cast<Cleanup_Adaptor*>(*i))->cleanup();
      delete *i;
    }

    // Delete the Global post of list method as a dependancy may exist
    // between objects existing in both constructs.
    // Array is last as all objects placed here are intentional and
    // it is assumed user is aware of such semantics.
    delete (Cleanup_Adaptor*)preallocated_object[XMLBLASTER_GLOBAL];
    // continue to remove any other array based objects

  }

  // Indicate that this Object_Lifetime_Manager
  // instance has been shut down.
  gObjectManagerState = object_manager_state_ = OBJ_MAN_SHUT_DOWN;
  if (dynamically_allocated_)
    delete this;
  if (this == instance_)
    instance_ = 0;
  return 0;
}

}}} // namespace
