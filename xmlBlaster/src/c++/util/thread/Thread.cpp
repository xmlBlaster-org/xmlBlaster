/*-----------------------------------------------------------------------------
Name:      Thread.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Encapsulates (and hides) threads
-----------------------------------------------------------------------------*/

#include <boost/thread/thread.hpp>
#include <boost/thread/mutex.hpp>
#include <boost/thread/condition.hpp>
#include <boost/thread/xtime.hpp>

#include <util/thread/Thread.h>
#include <util/Constants.h>
#include <util/Global.h>

namespace org { namespace xmlBlaster { namespace util { namespace thread {

/*
typedef boost::thread             ThreadImpl; 
typedef boost::mutex              MutexImpl;
typedef boost::mutex::scoped_lock LockImpl;
typedef boost::condition          ConditionImpl;
*/

struct ThreadImpl : public boost::thread {
   explicit ThreadImpl(const boost::function0<void>& threadfunc) : boost::thread(threadfunc) {}
};
struct MutexImpl : public boost::mutex {
};
struct LockImpl : public boost::mutex::scoped_lock {
   LockImpl(boost::mutex& mutex) : boost::mutex::scoped_lock(mutex) {}
};
struct ConditionImpl : public boost::condition {
};

// ----------------------------- ThreadRunner ----------------------------

ThreadRunner::ThreadRunner(Thread& owner) : owner_(owner) 
{
}

void ThreadRunner::operator()()
{
   owner_.run();
   delete owner_.thread_;
   owner_.thread_ = NULL;
}


// ----------------------------- Thread ----------------------------------

Thread::Thread()
{
    thread_ = NULL;
    runner_ = NULL;
}

Thread::~Thread() 
{
   delete thread_;
   delete runner_;
}

void Thread::start()
{
   if (thread_) return;
   if (!runner_) runner_ = new ThreadRunner(*this);
   thread_ = new ThreadImpl(*runner_);

}

void Thread::sleepNanos(Timestamp nanoSecondDelay)
{
   boost::xtime xt;
   boost::xtime_get(&xt, boost::TIME_UTC);

   long secDelay  = nanoSecondDelay / Constants::BILLION;
   long nanoDelay = nanoSecondDelay % Constants::BILLION;
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
   thread_->join();
}


// ----------------------------- Mutex ----------------------------------

Mutex::Mutex() 
{
   mutex_ = new MutexImpl();
}

Mutex::~Mutex() 
{
   delete mutex_;
}


// ----------------------------- Lock ------------------------------------

Lock::Lock(const Mutex& mutex)
{
   lock_ = new LockImpl(*(mutex.mutex_));
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


}}}} // namespaces


#ifdef _XMLBLASTER_CLASSTEST

#include <boost/lexical_cast.hpp>
using boost::lexical_cast;

using namespace std;
using namespace org::xmlBlaster::util::thread;

class SimpleThread : public Thread
{
private:
   int ref_;
public:
   static int staticRef;
   static int staticRef2;
   static Mutex mutex;
   static Condition condition;

   SimpleThread()
   {
      ref_ =staticRef++;
   }
      

   void run()
   {
      for (int i=0; i < 5; i++) {
         cout << "thread nr. '" + lexical_cast<string>(ref_) << " sweep number " << lexical_cast<string>(i) << " static ref is: " << lexical_cast<string>(staticRef2) << endl;
	 sleep(10);
	 staticRef2++;
	 sleep(990);
      }
      for (int i=0; i < 5; i++) {
         { 
	    Lock lock(mutex);
            cout << "thread nr. '" + lexical_cast<string>(ref_) << " sweep number " << lexical_cast<string>(i) << " static ref is: " << lexical_cast<string>(staticRef2) << endl;
   	    sleep(10);
	    staticRef2++;
         }
	 sleep(990);
      }
   }

};

int SimpleThread::staticRef  = 0;
int SimpleThread::staticRef2 = 0;
Mutex SimpleThread::mutex;
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

