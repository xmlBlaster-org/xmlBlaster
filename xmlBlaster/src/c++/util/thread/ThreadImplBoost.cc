/*-----------------------------------------------------------------------------
Name:      ThreadImplBoost.cc
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Encapsulates (and hides) threads
-----------------------------------------------------------------------------*/

#include <boost/thread/thread.hpp>
#include <boost/thread/mutex.hpp>
#include <boost/thread/condition.hpp>
#include <boost/thread/xtime.hpp>

#include <util/thread/ThreadBase.h>
#include <util/Constants.h>
#include <util/Global.h>
#include <iostream>

// namespace org { namespace xmlBlaster { namespace util { namespace thread {
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::util;

/*
typedef boost::thread             ThreadImpl; 
typedef boost::mutex              MutexImpl;
typedef boost::mutex::scoped_lock LockImpl;
typedef boost::condition          ConditionImpl;
*/

namespace org { namespace xmlBlaster { namespace util { namespace thread {

class ThreadImpl : public boost::thread {
        public:
   explicit ThreadImpl(const boost::function0<void>& threadfunc) : boost::thread(threadfunc) {}
};
class MutexImpl : public boost::mutex {
        public:
};
class LockImpl : public boost::mutex::scoped_lock {
   public:
   LockImpl(boost::mutex& mutex) : boost::mutex::scoped_lock(mutex) {}
};
class ConditionImpl : public boost::condition {
        public:
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
   try {
      owner_.run();
   }
   catch (...) {
      std::cerr << "ThreadRunner: uncatched exception occurred in the run thread" << std::endl;
   }
//   owner_.thread_ = NULL;
/*
   Lock lock(shutdownMutex_);
   if (owner_) {
      delete owner_.thread_;
      owner_.thread_ = NULL;
   }
*/
}


// ----------------------------- Thread ----------------------------------

Thread::Thread()
{
    thread_ = NULL;
    runner_ = NULL;
    isStarted_ = false;
}

Thread::~Thread() 
{
delete thread_;
delete runner_;
runner_ = NULL;

   // runner_.shutdown(); // set the owner to NULL
/*
   delete thread_;
   thread_ = NULL;
   delete runner_;
   runner_ = NULL;
*/
}

bool Thread::start(bool detached)
{
   if (isStarted_) return false;
   isStarted_ = true;
   if (!runner_) runner_ = new ThreadRunner(*this);
   if (!thread_) thread_ = new ThreadImpl(*runner_);
   /* OmniOrd has this too... do we care???
      but, start(void) and start_undetatched(void)
      aren't defined in the ThreadImpl class.  so, i'm not sure what to do.
   if (detached)
      thread_->start();
   else
      thread_->start_undetached();
   */
   return true;
}

void Thread::sleepNanos(Timestamp nanoSecondDelay)
{
   boost::xtime xt;
   boost::xtime_get(&xt, boost::TIME_UTC);

   long secDelay  = static_cast<long>(nanoSecondDelay / Constants::BILLION);
   long nanoDelay = static_cast<long>(nanoSecondDelay % Constants::BILLION);
   xt.sec        += secDelay;
   xt.nsec       += nanoDelay;
   boost::thread::sleep(xt);
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
   if (thread_) thread_->join();
}


Timestamp Thread::getCurrentTimestamp()
{
   boost::xtime xt;
   boost::xtime_get(&xt, boost::TIME_UTC);
   return Constants::BILLION * xt.sec + xt.nsec;
}



// ----------------------------- MutexClass ----------------------------------

MutexClass::MutexClass() 
{
   mutex_ = new MutexImpl();
}

MutexClass::~MutexClass() 
{
   delete mutex_;
}


// ----------------------------- Lock ------------------------------------

Lock::Lock(const MutexClass& mutex, bool ignore)
{
   if (ignore) lock_ = NULL;
   else lock_ = new LockImpl(*(mutex.mutex_));
}

Lock::~Lock() 
{
   delete lock_;
}


// ----------------------------- Condition--------------------------------

Condition::Condition()
{
   condition_ = new ConditionImpl();
}

Condition::~Condition()
{
   delete condition_;
}

void Condition::wait(const Lock& lock, long delay)
{
   boost::xtime timeToWait;
   boost::xtime_get(&timeToWait, boost::TIME_UTC);

   long int sec = (long int)(delay / Constants::THOUSAND);
   long int nano = (long int)((delay - sec*Constants::THOUSAND)*Constants::MILLION);
   timeToWait.sec  +=  sec;
   timeToWait.nsec += nano;
   condition_->timed_wait(*(lock.lock_), timeToWait);
}

void Condition::notify()
{
   condition_->notify_one();
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
   for (int i=0; i<imax; i++) threads[i].start();
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

