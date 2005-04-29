
/**
*
* Author: Martin Johnson
*
* Based upon http://www.cs.wustl.edu/~schmidt/PDF/ObjMan.pdf
* with some modification.
*
* Synopsis:
* Support for Preallocated Managed Objects and Managed Objects.
* Managed Objects are required to grant friendship to the following
*
*  template <class TYPE> friend class ManagedObject;
*  friend class Object_Lifetime_Manager;
*
* Applications utilising Object_Lifetime_Manager are required to call
*
**/

#ifndef _Object_Lifetime_Manager_
#define _Object_Lifetime_Manager_

#include <util/XmlBCfg.h>
#include <string>
#include <map>
#include <list>

extern "C"
{
   void object_manager_cleanup();
}

namespace org { namespace xmlBlaster { namespace util {

class Cleanup_Adaptor
{
public:
   Cleanup_Adaptor(){;}
   virtual ~Cleanup_Adaptor(){;}

   virtual void cleanup(){;}
};

template <class TYPE>
class ManagedObject : public Cleanup_Adaptor
{
public:
   ManagedObject(TYPE* p):obj_(p) {;}
   virtual ~ManagedObject(){if(obj_ != 0) delete obj_;}

   virtual void cleanup(){ delete obj_; obj_ = 0;}

protected:
   TYPE* obj_;

private:
   ManagedObject();
   ManagedObject(const ManagedObject& in){ obj_ = in.obj_; }
};

typedef std::map<std::string, Cleanup_Adaptor*> ManagedMap;
typedef std::list<Cleanup_Adaptor*> ManagedList;

class Dll_Export Object_Lifetime_Manager_Base
{
public:
   // Explicitly initialize. Returns 0 on success,
   // -1 on failure due to dynamic allocation
   // failure (in which case errno is set to
   // ENOMEM), or 1 if it had already been called.
   virtual int startup (void) = 0;

   // Explicitly destroy. Returns 0 on success,
   // -1 on failure because the number of <fini>
   // calls hasn’t reached the number of <init>
   // calls, or 1 if it had already been called.
   virtual int shutdown (void) = 0;

   enum Object_Lifetime_Manager_State 
   {
      OBJ_MAN_UNINITIALIZED,
      OBJ_MAN_INITIALIZING,
      OBJ_MAN_INITIALIZED,
      OBJ_MAN_SHUTTING_DOWN,
      OBJ_MAN_SHUT_DOWN
   };

protected:

   Object_Lifetime_Manager_Base (void) :
      object_manager_state_ (OBJ_MAN_UNINITIALIZED),
      dynamically_allocated_ (0) {}

   virtual ~Object_Lifetime_Manager_Base (void) 
   {
      // Clear the flag so that fini
      // does not delete again.
      dynamically_allocated_ = 0;
   }

   /**
    * This flag can be used to determine if the program is constructing
    * static objects. 
    * If no static object spawns any threads, the program will be
    * single-threaded when this flag returns 1.
    * @return 1 before Object_Lifetime_Manager_Base
    *         has been constructed.
    */
   int starting_up_i (void) 
   {
      return object_manager_state_ < OBJ_MAN_INITIALIZED;
   }

   /**
    * @return 1 after Object_Lifetime_Manager_Base
    * has been destroyed.  
    */
   int shutting_down_i (void) 
   {
      return object_manager_state_ > OBJ_MAN_INITIALIZED;
   }

   /** State of the Object_Lifetime_Manager; */
   Object_Lifetime_Manager_State object_manager_state_;

   /**
    * Flag indicating whether the
    * Object_Lifetime_Manager instance was
    * dynamically allocated by the library.
    * (If it was dynamically allocated by the
    * application, then the application is
    * responsible for deleting it.)
    */
   int dynamically_allocated_;  

};

class Dll_Export Object_Lifetime_Manager : public Object_Lifetime_Manager_Base
{
public:

   static void init (void);
   static void fini (void);

   virtual int startup (void);
   virtual int shutdown (void);

   static int starting_up (void) 
   {
      return instance_ ? instance_->starting_up_i () : 1;
   }

   static int shutting_down (void) 
   {
      return instance_ ? instance_->shutting_down_i () : 1;
   }

   /**
    * Unique identifiers for Preallocated Objects. 
    */
   enum Preallocated_Object
   {
      XMLBLASTER_GLOBAL,
      PREALLOCATED_OBJECTS
   };

   /**
    * Accessor to singleton instance. 
    *
    * This function should be called during
    * construction of static instances, or
    * before any other threads have been created
    * in the process. So, it’s not thread safe.
    */
   static Object_Lifetime_Manager *instance (void);
public:
 
   Object_Lifetime_Manager (void) 
   {
      // Make sure that no further instances are
      // created via instance.
      if (instance_ == 0) {
         instance_ = this;
         
         // shown to be useless though if some one else has a better
         // opinion. Doesnt work in win32 land.
         //atexit(object_manager_cleanup);
      }
      init ();
   }

   ~Object_Lifetime_Manager (void) 
   {
      // Don’t delete this again in fini.
      dynamically_allocated_ = 0;
      fini ();
   }

   /**
    * Add a new object to be managed on startup/shutdown. 
    * <p />
    * If you want to lookup the managed object at any time you
    * should use the
    * <code>manage_object(const std::string& key, T* obj)</code>
    * variant.
    * @param obj The instance to lifecycle-manage
    */
   template <class T> void manage_object(T* obj)
   {
      ManagedObject<T>* mobj = new ManagedObject<T>(obj);
      managedObjectList_.push_back(mobj);
   }

   /**
    * Add a new object to be managed on startup/shutdown
    *
    * @param key The unique key of this object, you can lookup this instance later if desired
    * @param obj The instance to lifecycle-manage
    */
   template <class T> void manage_object(const std::string& key, T* obj)
   {
      ManagedObject<T>* mobj = new ManagedObject<T>(obj);
      managedObjectList_.push_back(mobj);
      managedObjectMap_[key] = mobj;
   }

   /**
    * Lookup a managed object by its registration key
    * @return Pointer on managed object or NULL if not found
    */
   template <class T> T* getManagedObject(const std::string& key)
   {
      ManagedMap::iterator mi = managedObjectMap_.find(key);
      if (mi != managedObjectMap_.end()) {
         return (*mi).second;
      }
      return (T *)0;
   }

private:

   /** Singleton instance pointer. */
   static Object_Lifetime_Manager *instance_;

   /** Array of Preallocated Objects. */
   static void * preallocated_object[PREALLOCATED_OBJECTS];

   static ManagedMap managedObjectMap_;
   static ManagedList managedObjectList_;
};

}}} //namespace

#endif


