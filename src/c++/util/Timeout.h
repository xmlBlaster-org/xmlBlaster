/*-----------------------------------------------------------------------------
Name:      Timeout.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Allows you be called back after a given delay.
-----------------------------------------------------------------------------*/

#ifndef _UTIL_TIMEOUT_H
#define _UTIL_TIMEOUT_H

#include <util/xmlBlasterDef.h>
#include <util/I_Timeout.h>
#include <util/Timestamp.h>

#include <string>
#include <map>
#include <util/thread/ThreadImpl.h>

namespace org { namespace xmlBlaster { namespace util {

   typedef std::pair<I_Timeout*, void*> Container;
   typedef std::map<org::xmlBlaster::util::Timestamp, Container> TimeoutMap;


/**
 * Allows you be called back after a given delay.
 * <p />
 * Note that this class should be called Timer, but with JDK 1.3 there
 * will be a java.util.Timer.
 * <p />
 * There is a single background thread that is used to execute the I_Timeout.timeout() callback.
 * Timer callbacks should complete quickly. If a timeout() takes excessive time to complete, it "hogs" the timer's
 * task execution thread. This can, in turn, delay the execution of subsequent tasks, which may "bunch up" and execute in
 * rapid succession when (and if) the offending task finally completes.
 * <p />
 * This singleton is thread-safe.
 * <p />
 * This class does not offer real-time guarantees, but usually notifies you within ~ 20 milliseconds
 * of the scheduled time.
 * <p />
 * Adding or removing a timer is good performing, also when huge amounts of timers (> 1000) are used.<br />
 * Feeding of 10000: 10362 adds/sec and all updates came in 942 millis (600MHz Linux PC with Sun JDK 1.3.1)
 * * <p />
 * TODO: Use a thread pool to dispatch the timeout callbacks.
 * <p />
 * Example:<br />
 * <pre>
 * public class MyClass implements I_Timeout {
 *   ...
 *   Timeout timeout = new Timeout("TestTimer");
 *   org::xmlBlaster::util::Timestamp timeoutHandle = timeout.addTimeoutListener(this, 4000L, "myTimeout");
 *   ...
 *   public void timeout(Object userData) {
 *      // userData contains String "myTimeout"
 *      System.out.println("Timeout happened");
 *      ...
 *      // If you want to activate the timer again:
 *      timeoutHandle = timeout.addTimeoutListener(this, 4000L, "myTimeout");
 *   }
 *   ...
 *   // if you want to refresh the timer:
 *   timeoutHandle = timeout.refreshTimeoutListener(timeoutHandle, 1500L);
 *   ...
 * }
 * </pre>
 * Or a short form:
 * <pre>
 *  Timeout timeout = new Timeout("TestTimer");
 *  org::xmlBlaster::util::Timestamp timeoutHandle = timeout.addTimeoutListener(new I_Timeout() {
 *        public void timeout(Object userData) {
 *           System.out.println("Timeout happened");
 *           System.exit(0);
 *        }
 *     },
 *     2000L, null);
 * </pre>
 *
 *
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */
class Dll_Export Timeout : public org::xmlBlaster::util::thread::Thread
{

 private: 
   /** Name for logging output */
   std::string ME; //  = "Timeout";
   std::string threadName_;
//   boost::thread* runningThread_;
   /** Sorted std::map */
   TimeoutMap timeoutMap_;
   // private TreeMap std::map = null;
   /** Start/Stop the Timeout manager thread */
   bool isRunning_; //  = true;
   /** On creation wait until thread started */
   bool isReady_; //  = false;

   /** To protect thread gap **/
   bool mapHasNewEntry_;

   /** is set to false once the thread is finished (for cleanup) */
   bool isActive_;
   /** Switch on debugging output */
   const bool isDebug_; //  = false;

   const bool detached_;

   org::xmlBlaster::util::TimestampFactory& timestampFactory_;

   org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::I_Log&    log_;

   /** The synchronization object */
   org::xmlBlaster::util::thread::Mutex invocationMutex_;   
   org::xmlBlaster::util::thread::Mutex waitForTimeoutMutex_;
   org::xmlBlaster::util::thread::Condition waitForTimeoutCondition_;

   size_t getTimeoutMapSize();

   /**
    * Starts the thread
    */
    bool start(bool detached);

//   friend class TimeoutRunner;
 public:

   /**
    * Create a timer thread. 
    */
   Timeout(org::xmlBlaster::util::Global& global);

   /**
    * Create a timer thread. 
    */
   Timeout(org::xmlBlaster::util::Global& global, const std::string &name);

    ~Timeout();

   /**
    * Used to join the thread used by this instance. 
    * Don't call this method for detached running threads.
    */
   void join();

   /** the run method (what has to be done inside a thread */
//   void operator()();

   /**
    * Get number of current used timers. 
    * @return The number of active timers
    */
   int getSize() const {
      return timeoutMap_.size();
   }

   /**
    * Add a listener which gets informed after 'delay' milliseconds.<p />
    * 
    * After the timeout happened, you are not registered any more. If you 
    * want to cycle timeouts, you need to register again.<p />
    *
    * @param      listener 
    *             Your callback handle (you need to implement this interface).
    * @param      delay 
    *             The timeout in milliseconds.
    * @param      userData 
    *             Some arbitrary data you supply, it will be routed back 
    *             to you when the timeout occurs through method 
    *             I_Timeout.timeout().
    * @return     A handle which you can use to unregister with 
    *             removeTimeoutListener().
    * @throws     XmlBlasterException if timer is not started
    */
    org::xmlBlaster::util::Timestamp addTimeoutListener(I_Timeout *listener, long delay, void *userData);

   /**
    * Refresh a listener before the timeout happened.<p />
    * 
    * NOTE: The returned timeout handle is different from the original one.<p />
    *
    * NOTE: If you are not sure if the key has elapsed already try this:
    * <pre>
    *  timeout.removeTimeoutListener(timeoutHandle);
    *  timeoutHandle = timeout.addTimeoutListener(this, "1000L", "UserData");
    * </pre>
    * @param      key 
    *             The timeout handle you received by a previous 
    *             addTimeoutListener() call.<br />
    *             It is invalid after this call.
    * @param      delay 
    *             The timeout in milliseconds measured from now.
    * @return     A new handle which you can use to unregister with 
    *             removeTimeoutListener().
    *             -1: if key is null or unknown or invalid because timer elapsed already
    * @throws     XmlBlasterException if key<0 or timer is not started
    */
   org::xmlBlaster::util::Timestamp refreshTimeoutListener(org::xmlBlaster::util::Timestamp key, long delay); 

   /**
    * Checks if key is 0 -> addTimeoutListener else refreshTimeoutListener() 
    * in a thread save way. 
    * @param key If <= 0 we add a new timer, else lookup given key and refresh
    * @throws     XmlBlasterException if timer is not started
    */
    org::xmlBlaster::util::Timestamp addOrRefreshTimeoutListener(I_Timeout *listener, 
                                                  long delay, void *userData, org::xmlBlaster::util::Timestamp key);
   // throws org::xmlBlaster::util::XmlBlasterException

   /**
    * Remove a listener before the timeout happened.<p />
    * 
    * @param      key
    *             The timeout handle you received by a previous 
    *             addTimeoutListener() call.
    */
   void removeTimeoutListener(org::xmlBlaster::util::Timestamp key);

   /**
    * Is this handle expired?<p />
    * 
    * @param      key
    *             The timeout handle you received by a previous 
    *             addTimeoutListener() call<br />
    * @return     true/false
    */
   bool isExpired(org::xmlBlaster::util::Timestamp key);

   /**
    * How long to my timeout.<p />
    *
    * @param      key 
    *             The timeout handle you received by a previous 
    *             addTimeoutListener() call.
    * @return     Milliseconds to timeout, or -1 if not known.
    */
   long spanToTimeout(org::xmlBlaster::util::Timestamp key);

   /**
    * Access the end of life span.<p />
    * 
    * @param      key 
    *             The timeout handle you received by a previous 
    *             addTimeoutListener() call.
    * @return     Time in milliseconds since midnight, January 1, 1970 UTC 
    *             or -1 if not known.
    */
   long getTimeout(org::xmlBlaster::util::Timestamp key);

   /**
    * Reset all pending timeouts.<p />
    */
   void removeAll();

   /**
    * Reset and stop the Timeout manager thread. 
    */
   void shutdown();

   void run();

};

}}} // namespaces

#endif


