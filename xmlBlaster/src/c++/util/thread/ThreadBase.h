/*-----------------------------------------------------------------------------
Name:      ThreadBase.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Encapsulates (and hides) threads
------------------------------------------------------------------------------*/

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

class Dll_Export Thread 
{
   friend class ThreadRunner;
private:
   ThreadImpl*   thread_;
   ThreadRunner* runner_;

public:
   Thread();

   virtual ~Thread();

   /** When this method is invoked, the thread starts to run */
   void start();

   /** This is the method which has to be implemented by the user */
   virtual void run() = 0;

   /** Sleeps the specified amount of time in nanoseconds */
   static void sleepNanos(Timestamp nanoSecondDelay);

   /** Sleeps the specified amount of time in nanoseconds */
   static void sleep(long millis);

   /** Sleeps the specified amount of time in nanoseconds */
   static void sleepSecs(long secs);

   /** returns the current timestamp */
   static Timestamp getCurrentTimestamp();


   /**
    * Joins the thread to the current thread, in other words, the it will wait until the 
    * thread on which this method is invoked, will terminate, before it continues.
    */
   void join();


   bool isRunning() const
   {
      return thread_;
   }

};


/* -------------------------- MutexClass -----------------------------*/

class Dll_Export MutexClass
{
   friend class Lock;
private:
   MutexImpl* mutex_;

public:
   MutexClass();
   
   ~MutexClass();
};


/* -------------------------- Lock ------------------------------*/

class Dll_Export Lock 
{
   friend class Condition;
private:
   LockImpl* lock_;

public:
   Lock(const MutexClass& mutex);
   
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

