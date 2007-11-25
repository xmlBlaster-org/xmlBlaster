/*-----------------------------------------------------------------------------
Name:      Thread.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Encapsulates (and hides) threads for the omnithread
-----------------------------------------------------------------------------*/

#include <omnithread.h>
#include <util/thread/ThreadBase.h>
#include <util/Constants.h>
#include <util/Global.h>
#include <iostream>

// namespace org { namespace xmlBlaster { namespace util { namespace thread {
using namespace std;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::util;

/*
typedef boost::thread             ThreadImpl; 
typedef boost::mutex              MutexImpl;
typedef boost::mutex::scoped_lock LockImpl;
typedef boost::condition          ConditionImpl;
*/

namespace org { namespace xmlBlaster { namespace util { namespace thread {

class ThreadImpl : public omni_thread 
{
   Thread *owner_;


   /**
    * Little helper function to pass to the ThreadImpl constructor 
    */
   void* make_arg(int /*i*/) 
   { 
      // std::cerr << "make_arg invoked" << std::endl;
      return NULL; // return (void*)new int(i); 
   }
/*
   void make_argDetached(int) 
   { 
      return NULL; // return (void*)new int(i); 
   }
*/
public:
   ThreadImpl(Thread* owner, void (*fn)(void*)) : omni_thread(fn)
   {
      // std::cerr << "ThreadImpl: constructor invoked" << std::endl;
      owner_ = owner;
   }
   
   /**
    * The thread will execute the run() or run_undetached() member functions depending on
    * whether start() or start_undetached() is called respectively.
    */
   ThreadImpl(Thread* owner, int i /*void* (*fn)(void*)*/) : omni_thread(make_arg(i))
   {
      // std::cerr << "ThreadImpl: constructor invoked" << std::endl;
      owner_ = owner;
   }

   void run(void*)
   {
      try {
         // std::cerr << "ThreadImpl::run invoked ..." << std::endl;
         if (owner_) owner_->run();
      }
      catch (exception & /*ex*/) {
         // std::cerr << "exception in run method: " << ex.what() << std::endl;
      }
      catch (...) {
         // std::cerr << "unknown exception in run method" << std::endl;
      }
   }

   /**
    * Start depending on constructor used a detached or a joinable thread
    */
   void start()
   {
      omni_thread::start();
   }

   void start_undetached()
   {
      omni_thread::start_undetached();
   }

   /**
    * Called by new thread when thread starts running, we delegate the call to our owner. 
    * You need to call join() to clean up resources. 
    */
   void* run_undetached(void*)
   { 
      try {
         // std::cerr << "ThreadImpl::run_undetached invoked ..." << std::endl;
         if (owner_) owner_->run();
      }
      catch (exception & /*ex*/) {
         // std::cerr << "exception in run_undetached method: " << ex.what() << std::endl;
      }
      catch (...) {
         // std::cerr << "unknown exception in run_undetached method" << std::endl;
      }
      return NULL;
   }

};


class MutexImpl : public omni_mutex 
{
public:
  MutexImpl(bool recursive=false) : omni_mutex(recursive) {}
};

class LockImpl 
{
   friend class Condition;
    omni_mutex& mutex;
public:
    LockImpl(omni_mutex& m) : mutex(m) 
    {  try {
          // std::cerr << "LockImpl constructor before locking mutex " << &mutex << std::endl;
          mutex.lock(); 
          // std::cerr << "LockImpl constructor after locking mutex" << std::endl;
       }
       catch (...) {
          // std::cerr << "LockImpl constructor unknown exception" << std::endl;
       }
    }

    ~LockImpl(void) 
    {  
       try {
          // std::cerr << "LockImpl destructor before unlocking mutex "  << &mutex << std::endl;
          mutex.unlock(); 
          // std::cerr << "LockImpl destructor after unlocking mutex" << std::endl;
       }
       catch (...) {
          // std::cerr << "LockImpl destructor unknown exception" << std::endl;
       }
    }
private:
    // dummy copy constructor and operator= to prevent copying
//    omni_mutex_lock(const omni_mutex_lock&);
//    omni_mutex_lock& operator=(const omni_mutex_lock&);
};


class ConditionImpl : public omni_condition
{
public:
   ConditionImpl(omni_mutex* m) : omni_condition(m)
   {
   }
};

}}}}


// ----------------------------- ThreadRunner ----------------------------

/**
 * This helper class is used by boost only to map their operator() syntax to our java-like run() syntax.
 * <p>Other threading implementations than boost could call run() directly without this helper class.</p>
 * This way we ensure a usage of our thread library as it would be a java thread.
 */
ThreadRunner::ThreadRunner(Thread& owner) : owner_(owner) 
{
}

void ThreadRunner::operator()()
{
}


// ----------------------------- Thread ----------------------------------

Thread::Thread()
{
    thread_    = NULL;
    runner_    = NULL;
    isStarted_ = false;
}

Thread::~Thread() 
{
   delete thread_;
   thread_ = NULL;
// delete runner_;
// runner_ = NULL;
}

bool Thread::start(bool detached)
{
   if (isStarted_) return false;
   isStarted_ = true;
//   if (!runner_) runner_ = new ThreadRunner(*this);
   if (!thread_) thread_ = new ThreadImpl(this, 0);
   if (detached)
      thread_->start();
   else
      thread_->start_undetached();
   return true;
}

void Thread::sleepNanos(Timestamp nanoSecondDelay)
{

/*
   boost::xtime xt;
   boost::xtime_get(&xt, boost::TIME_UTC);
*/
   unsigned long secDelay  = static_cast<unsigned long>(nanoSecondDelay / Constants::BILLION);
   unsigned long nanoDelay = static_cast<unsigned long>(nanoSecondDelay % Constants::BILLION);
   omni_thread::sleep(secDelay, nanoDelay);
}

void Thread::sleep(long millis)
{
   Timestamp nanos = Constants::MILLION * millis;
   Thread::sleepNanos(nanos);
}

void Thread::sleepSecs(long secs)
{
   Thread::sleep(secs * 1000l);
}

void Thread::join() 
{  
   // std::cerr << "Thread::join invoked" << std::endl;
   try {
      if (thread_) thread_->join(NULL);
      thread_ = NULL;
   }
   catch (...) {
      // std::cerr << "exception in join" << std::endl;
   }
}


Timestamp Thread::getCurrentTimestamp()
{
   unsigned long secDelay  = 0;
   unsigned long nanoDelay = 0;
   omni_thread::get_time(&secDelay, &nanoDelay);
   return Constants::BILLION * secDelay + nanoDelay;
}



// ----------------------------- MutexClass ----------------------------------

MutexClass::MutexClass(bool recursive) 
{
   mutex_ = new MutexImpl(recursive);
}

MutexClass::~MutexClass() 
{
   delete mutex_;
}


// ----------------------------- Lock ------------------------------------

Lock::Lock(const MutexClass& mutex, bool ignore)
{
   if (ignore) lock_ = NULL;
   else lock_ = new LockImpl(*mutex.mutex_);
}

Lock::~Lock() 
{
   delete lock_;
}


// ----------------------------- Condition--------------------------------

Condition::Condition()
{
   // std::cerr << "Condition::Condition ..." << std::endl;
   condition_ = NULL;
}

Condition::~Condition() 
{
   // std::cerr << "Condition::destructor ..." << std::endl;
   delete condition_;
}


void Condition::wait(const Lock& lock, long delay)
{
   // Lazy initialization here. We don't do it in the constructor for boost (since boost needs a Lock on
   // wait and omniorb needs a Mutex in the constructor.
   // It is threadsafe to do it here since it is in a lock until it reaches timedwait (and then we are sure
   // that the creation of condition_ is completed and everybody is happy.
   if (!condition_) condition_ = new ConditionImpl(&(lock.lock_->mutex));
   unsigned long int sec = (unsigned long int)(delay / Constants::THOUSAND);
   unsigned long int nano = (unsigned long int)((delay - sec*Constants::THOUSAND)*Constants::MILLION);
   unsigned long int absSec = 0;
   unsigned long int absNano = 0;
   omni_thread::get_time(&absSec, &absNano, sec, nano);
   if (delay > -1)
      condition_->timedwait(absSec, absNano);
   else
      condition_->wait();
}


void Condition::notify()
{
   try {
      if (condition_) condition_->signal();
      else {
      }
   }
   catch (exception &ex) {
      std::cerr << "Condition::notify: exception: " << ex.what() << std::endl;
   }
   catch (string &ex) {
      std::cerr << "Condition::notify: string exception: " << ex << std::endl;
   }
   catch (char* ex) {
      std::cerr << "Condition::notify: char* exception: " << ex << std::endl;
   }
   catch (int ex) {
      std::cerr << "Condition::notify: int exception: " << ex << std::endl;
   }
   catch (...) {
      std::cerr << "Condition::notify: unknown exception ..." << std::endl;
   }
}


// }}}} // namespaces


#ifdef _XMLBLASTER_CLASSTEST

#include <boost/lexical_cast.hpp>
using namespace boost;

using namespace std;
using namespace org::xmlBlaster::util::thread;

class SimpleThread : public Thread
{
private:
   int ref_;
public:
   static int staticRef;
   static int staticRef2;
   static MutexClass mutex;
   static Condition condition;

   SimpleThread()
   {
      ref_ =staticRef++;
   }
      

   void run()
   {
      for (int i=0; i < 5; i++) {
         cout << "thread nr. '" + lexical_cast<std::string>(ref_) << " sweep number " << lexical_cast<std::string>(i) << " static ref is: " << lexical_cast<std::string>(staticRef2) << endl;
         sleep(10);
         staticRef2++;
         sleep(990);
      }
      for (int i=0; i < 5; i++) {
         { 
            Lock lock(mutex);
            cout << "thread nr. '" + lexical_cast<std::string>(ref_) << " sweep number " << lexical_cast<std::string>(i) << " static ref is: " << lexical_cast<std::string>(staticRef2) << endl;
            sleep(10);
            staticRef2++;
         }
         sleep(990);
      }
   }

};

int SimpleThread::staticRef  = 0;
int SimpleThread::staticRef2 = 0;
MutexClass SimpleThread::mutex;
Condition SimpleThread::condition;

int main()
{
   int imax = 10;
   SimpleThread* threads = new SimpleThread[imax];
/*
   for (int i=0;i<imax; i++) {
      threads[i] = new SimpleThread();
   }
*/
   for (int i=0; i<imax; i++) threads[i].start(false);
   cout << "The simple thread has been started" << endl;
   for (int i = 0; i < imax; i++) threads[i].join();
   cout << "The simple thread is now finished" << endl;

   cout << "and now testing the condition" << endl;
   {
      Lock lock(SimpleThread::mutex);
      cout << "waiting 3 seconds to be notified (nobody will notify)" << endl;
      SimpleThread::condition.wait(lock, 3000);
      cout << "the 3 seconds elapsed now" << endl;
   }
   return 0;
}

#endif

