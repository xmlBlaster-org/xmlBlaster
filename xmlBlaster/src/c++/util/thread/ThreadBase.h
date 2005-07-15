/*-----------------------------------------------------------------------------
Name:      ThreadBase.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Encapsulates (and hides) threads
------------------------------------------------------------------------------*/

#ifndef _UTIL_THREAD_THREADBASE_H
#define _UTIL_THREAD_THREADBASE_H

#include <util/xmlBlasterDef.h>

class thread;
class mutex;
class lock;
class condition;

namespace org { namespace xmlBlaster { namespace util { namespace thread {

class ThreadImpl;
class MutexImpl;
class LockImpl;
class ConditionImpl;

class Thread;
class Condition;
class Lock;

/* -------------------------- ThreadRunner --------------------------*/

class ThreadRunner 
{
public:
   Thread& owner_;
   ThreadRunner(Thread& owner);
   void operator()();
};


/* -------------------------- Thread --------------------------------*/

/**
 * Little framework for the abstraction of threads and thread related stuff. Currently the implementation of
 * threads and their synchronization is provided by boost::threads. Since some platforms have problems building
 * this library we provided here an abstraction which makes it easy to switch to another implementation if
 * needed.
 * 
 * This framework provides the following classes:
 * ThreadRunner: only used internally by the class Thread.
 * Thread: This class has a pure virtual method 'void run()' which must be implemented by the user.
 * Mutex: The mutual exclusion class
 * Condition: The class for wait and notify.
 * Lock: used to lock a mutex. The constructor locks and the destructor unlocks.
 *
 * @author <a href='mailto:laghi@swissinfo.org'>Michele Laghi</a>
 * 
 */
class Dll_Export Thread 
{
   friend class ThreadRunner;
private:
   ThreadImpl*   thread_;
   ThreadRunner* runner_;
   bool          isStarted_;

public:
   Thread();

   virtual ~Thread();

   /** 
    * When this method is invoked, the thread starts to run. Note that you can not
    * start a thread twice.
    * @return true if the thread could be started, false otherwise.
    */
   virtual bool start(bool detached);

   /** This is the method which has to be implemented by the user */
   virtual void run() = 0;

   /** Sleeps the specified amount of time in nanoseconds */
   static void sleepNanos(org::xmlBlaster::util::Timestamp nanoSecondDelay);

   /** Sleeps the specified amount of time in nanoseconds */
   static void sleep(long millis);

   /** Sleeps the specified amount of time in nanoseconds */
   static void sleepSecs(long secs);

   /** returns the current timestamp */
   static org::xmlBlaster::util::Timestamp getCurrentTimestamp();


   /**
    * Joins the thread to the current thread, in other words, the it will wait until the 
    * thread on which this method is invoked, will terminate, before it continues.
    */
   virtual void join();


   bool isRunning() const
   {
      return (thread_ != NULL);
   }

};


/* -------------------------- MutexClass -----------------------------*/

class Dll_Export MutexClass
{
   friend class Lock;
private:
   MutexImpl* mutex_;

public:
   /**
    * The locks may not be called recursive. 
    * Posix supports recursive calls as an extension.
    * @param mutex The mutex implementation
    * @param ignore If true no lock is created
    * @param recursive If true the same thread may call the lock recursive
    *                  Note that the thread needs to free the lock as many times again.
    * @since xmlBlaster 1.0.5
    */
   MutexClass(bool recursive=false);
   
   ~MutexClass();
};


/* -------------------------- Lock ------------------------------*/

class Dll_Export Lock 
{
   friend class Condition;
private:
   LockImpl* lock_;

public:
   Lock(const MutexClass& mutex, bool ignore=false);
   
   ~Lock();
};


/* -------------------------- Lock ------------------------------*/

class Dll_Export Condition
{
private:
   ConditionImpl* condition_;
public:
   Condition();
   
   ~Condition();

   void wait(const Lock& lock, long delay);

   void notify();
};


}}}} // namespaces

#endif

