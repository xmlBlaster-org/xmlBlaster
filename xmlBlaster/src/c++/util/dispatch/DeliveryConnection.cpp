/*------------------------------------------------------------------------------
Name:      DeliveryConnection.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Holding all necessary infos to establish a remote connection
 * and check this connection. 
 * <pre>
 *    State chart of a single connection:
 *
 *          +<--toAlive()----+                                  
 *          |                |                          
 *    #########            ##########         ##########
 *   #         #          #          #       #          #
 *   #  ALIVE  #          # POLLING  #       #  DEAD    #
 *   #         #          #          #       #          #
 *    #########            ##########         ##########
 *      |   |                |    |             |    |
 *      |   +--toPolling()-->+    +--toDead()-->+    |
 *      |                                            |
 *      +-------------toDead()---------------------->+
 *
 * </pre>
 * <p>
 * Not that DeliveryConnection can't recover from DEAD state
 * you need to create a new instance if desired
 * </p>
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */

#include <util/I_Timeout.h>
#include <util/Timeout.h>
#include <util/qos/address/AddressBase.h>
#include <util/XmlBlasterException.h>
#include <client/protocol/I_XmlBlasterConnection.h>
#include <util/Log.h>
#include <util/Global.h>
#include <util/queue/MsgQueueEntry.h>
#include <string>
#include <vector>

#include <client/protocol/corba/CorbaDriver.h>

#include <boost/lexical_cast.hpp>
using boost::lexical_cast;

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::client;
using org::xmlBlaster::util::qos::address::AddressBase;
using org::xmlBlaster::client::protocol;
using org::xmlBlaster::util::queue::MsgQueueEntry;

namespace org { namespace xmlBlaster { namespace util { namespace dispatch {

class DeliveryConnection : public I_Timeout
{
private:
   Timeout&                pingTimer_;
   Timestamp               timerKey_; // = null;
   I_XmlBlasterConnection* driver_;

protected:
   Global& global_;
   Log& log_;
//   DeliveryConnectionsHandler connectionsHandler_; //  = null;
   /** For logging only */
   string myId_;
   AddressBase address_; // = null;
   enum {UNDEF, ALIVE, POLLING, DEAD} state_;
   int retryCounter_; // = 0;
   char* pollStr_;

public:
   string ME;

   /**
    * Our initialize() needs to be called next. 
    * @param connectionsHandler The DevliveryConnectionsHandler witch i belong to
    * @param address The address i shall connect to
    */
   DeliveryConnection(Global& global,
                    /*DeliveryConnectionsHandler connectionsHandler,*/
                      AddressBase address)
      : pingTimer_(global.getPingTimer()),
        global_(global),
        log_(global.getLog("dispatch")),
        address_(address),
        pollStr_("poll")
   {
      state_ = UNDEF;
//      ME = "DeliveryConnection-"; // + connectionsHandler.getDeliveryManager().getQueue().getQueueId();
//      this.connectionsHandler = connectionsHandler;
//      myId_      = connectionsHandler.getDeliveryManager().getQueue().getQueueId();
      myId_ = "unknown queue"; // temporarly until connectionsHandler done
//      pingTimer_ = global_.getPingTimer();
      driver_    = NULL;
   }

   virtual ~DeliveryConnection()
   {
      if (timerKey_ != 0) {
         pingTimer_.removeTimeoutListener(timerKey_);
         timerKey_ = 0;
      }

      delete driver_;
      driver_ = NULL;
      // is it allowed to use the logging here ?
      if (log_.TRACE) log_.trace(ME, "finalize - garbage collected");
   }

   /**
    * @return A nice name for logging
    */
   virtual string getName() const
   {
      return "delivery connection handler";
   }

   /** 
    * Load the appropriate protocol driver. 
    * Needs to be called after construction
    * <p>
    * Calls initDriver() which needs to be implemented by derived classes
    * </p>
    */
   void initialize()
   {
      state_        = ALIVE;
      retryCounter_ = 0;

      try {
         initDriver();
         //connectionsHandler.toAlive(this);
         if (address_.getPingInterval() > 0) { // respan ping timer (even for native plugins we do a dummy ping)
            timerKey_ = pingTimer_.addOrRefreshTimeoutListener(this,
                        address_.getPingInterval(), NULL, timerKey_);
         }
      }
      catch (XmlBlasterException &ex) {
         throw ex;
      }
      catch (...) {
         XmlBlasterException e(INTERNAL_UNKNOWN, ME, "initialize");
         if (log_.TRACE) log_.trace(ME, e.toXml());
//         connectionsHandler.toDead(this, e);
         handleTransition(false, true, &e);
         throw e;
      }

      if (log_.TRACE)
         log_.trace(ME, string("Created driver for protocol '") + address_.getType() + "'");
   }

   AddressBase getAddress() const
   {
      return address_;
   }

   /** Called on COMMUNICATION errors, reset protocol driver for reconnect polling */
   virtual void resetConnection() {
   }

   /** The derived class should create an instance of the protocol driver */
   virtual void initDriver()
   {
      driver_ = new corba::CorbaDriver(global_);
   }

   /** A human readable name of the protocol plugin */
   virtual string getDriverName()
   {
      return address_.getType();
   }

   /**
    * Send the messages. 
    * @param msgArr Should be a copy of the original, since we export it which changes/encrypts the content
    * @return The returned string array from the client which is decrypted if necessary, for oneway updates it is null
    */
   virtual bool doSend(vector<MsgQueueEntry>& msgArr)
   {
      log_.warn(ME, "::doSend is not implemented yet");
      return false;
   }

   string getStateStr(int ref=-1) const
   {
      if (ref < 0) ref = state_;
      switch (ref) {
         case UNDEF: return "UNDEF";
         case ALIVE: return "ALIVE";
         case POLLING: return "POLLING";
      }
      return "DEAD";
   }


   /**
    * Send the messages back to the client. 
    * @param msgArr Should be a copy of the original, since we export it which changes/encrypts the content
    * @return The returned string from the client which is decrypted if necessary, for oneway updates it is null
    */
   bool send(vector<MsgQueueEntry>& msgArr)
   {
      if (log_.CALL) log_.call(ME, string("send(msgArr.length=") + lexical_cast<string>(msgArr.size()) + ")");
      if (msgArr.empty()) return false;

      if (isDead()) { // assert
         log_.error(ME, string("Connection to ") + address_.toXml() + " is in state DEAD, msgArr.length=" + lexical_cast<string>(msgArr.size()) + " messages are lost");
         throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "Internal problem: Connection to " + address_.toXml() + string(" is in state DEAD, msgArr.length=") + lexical_cast<string>(msgArr.size()) + " messages are lost");
      }

      // Send the message ...
      try {
         bool ret = doSend(msgArr);
         handleTransition(true, true, NULL);
         return ret;
      }
      catch (XmlBlasterException &ex) {
         if (isPolling() && log_.TRACE)
            log_.trace(ME, string("Exception from update(), retryCounter=") + lexical_cast<string>(retryCounter_) + ", state=" + getStateStr());
         log_.warn(ME, "sending failed and redeliver counter is not implemented");
         /*
         for (int i=0; i<msgArr.length; i++)
            UpdateQosServer.incrRedeliver(msgArr[i].getMsgQosData());
         */
         if (ex.isCommunication()) {
            handleTransition(false, true, &ex); // never returns - throws exception
         }
         else {
            handleTransition(true, true, &ex);
            throw ex;
         }
      }

      throw XmlBlasterException(INTERNAL_UNKNOWN, ME, "This exception is never reached");
   }

   /**
    * Does the real ping
    */
   virtual string doPing(const string& data)
   {
      log_.warn(ME, "doPing not implemented yet");
      return "";
   }

   /** Ping the callback server of the client */
   string ping(const string& data)
   {
      return ping(data, true);
   }

private:
   /**
    * Ping the callback server of the client
    * @param byDeliveryConnectionsHandler true if invoked by DeliveryConnectionsHandler
    *        we can throw exceptions back.
    *        false: If invoked by our timer/ping thread, we need to callback the situation
    */
   string ping(const string& data, bool byDeliveryConnectionsHandler)
   {
      if (log_.CALL) log_.call(ME, "ping()");
      if (isDead()) { // assert
         log_.error(ME, "Callback driver is in state DEAD, ping failed");
         throw XmlBlasterException(COMMUNICATION_NOCONNECTION_DEAD, ME, "Callback driver is in state DEAD, ping failed");
      }

      try {
         string returnVal = doPing(data);
         if (log_.TRACE && isAlive()) log_.trace(ME, string("Success for ping('") + data + "'), return='" + returnVal + "'");
         handleTransition(true, byDeliveryConnectionsHandler, NULL);
         return returnVal;
      }
      catch (XmlBlasterException &ex) {
         if (isAlive() && log_.TRACE) log_.trace(ME, string("Exception from callback ping(), retryCounter=") + lexical_cast<string>(retryCounter_) + ", state=" + getStateStr());
         handleTransition(false, byDeliveryConnectionsHandler, &ex);
         return ""; // Only reached if from timeout
      }
      catch (...) {
         throw XmlBlasterException(INTERNAL_UNKNOWN, ME, "::ping()");
      }
   }

protected:
   /** On reconnect polling try to establish the connection */
   virtual void reconnect()
   {
   }

public:
   /**
    * We are notified to do the next ping or reconnect polling. 
    * <p />
    * When connected, we ping<br />
    * When connection is lost, we do reconnect polling
    * @param userData You get bounced back your userData which you passed
    *                 with Timeout.addTimeoutListener()
    */
   void timeout(void *userData)
   {
      if (isDead()) return;
      timerKey_ = 0;
      bool isPing = (userData == NULL);

      if (isPing) {
         if (log_.TRACE)
            log_.trace(ME, "timeout -> Going to ping client callback server ...");
         try {
            string result = ping("", false);
         }
         catch (XmlBlasterException &ex) {
            log_.error(ME, "PANIC: " + ex.toXml());
         } // is handled in ping() itself
      }
      else { // reconnect polling
         try {
            if (log_.TRACE)
               log_.trace(ME, "timeout -> Going to check if remote server is available again ...");
            reconnect();
            try {
               string result = ping("", false);
            }
            catch (XmlBlasterException &ex) {
               log_.error(ME, "PANIC: " + ex.toXml());
            } // is handled in ping() itself
         }
         catch (...) {
            if (log_.TRACE)
               log_.trace(ME, "Polling for remote connection failed for an unknown reason");
            try {
               XmlBlasterException ex(INTERNAL_UNKNOWN, ME, "::timeout()");
               handleTransition(false, false, &ex);
            }
            catch(XmlBlasterException& e2) {
                log_.error(ME, "PANIC: " + e2.toXml());
            }
         }
      }
   }

protected:
   /**
    * @param toReconnected If true if the connection is OK (it is a transition to reconnected)
    * @param byDeliveryConnectionsHandler true if invoked by DeliveryConnectionsHandler,
    *        false if invoked by our timer/ping
    * @param The problem, is expected to be not null for toReconnected==false
    * @exception XmlBlasterException If delivery failed
    */
   void handleTransition(bool toReconnected,
                         bool byDeliveryConnectionsHandler,
                         XmlBlasterException* ex)
   {
      int oldState = state_;
      if (log_.TRACE) {
         string help1 = "false";
         if (toReconnected) help1 = "true";
         string help2 = "false";
         if (byDeliveryConnectionsHandler) help2 = "true";
         log_.trace(ME, string("Connection transition ") + lexical_cast<string>(oldState) + " -> toReconnected=" + help1 + " byDeliveryConnectionsHandler=" + help2);
      }
//      synchronized (this) {
         if (isDead()) {   // ignore, not possible
            log_.warn(ME, string("Connection transition ") + getStateStr(oldState) + " -> " + getStateStr() + " for " + myId_ + ": We ignore it.");
            return;
         }

         if (toReconnected && (oldState==UNDEF)) { //startup
            state_ = ALIVE;
//            connectionsHandler_.toAlive(this);
         }

         if (toReconnected && isAlive()) { //everything is ok
            if (address_.getPingInterval() > 0) { // respan ping timer (even for native plugins we do a dummy ping)
               // timerKey==null (byDeliveryConnectionsHandler==false) -> call from a ping timeout: respan
               //
               // timerKey!=null (byDeliveryConnectionsHandler==true)  -> call between pings from successful update() invocation:
               // We don't need to ping directly after a successful invocation
               // so we respan the timer.
               // Probably this slows down on many updates and seldom pings,
               // should we remove the following two lines?
               timerKey_ = pingTimer_.addOrRefreshTimeoutListener(this,
                           address_.getPingInterval(), NULL, timerKey_);
            }
            return;
         }
      
         if (timerKey_ != 0) {
            pingTimer_.removeTimeoutListener(timerKey_);
            timerKey_ = 0;
         }

         if (toReconnected && isPolling()) {
            state_ = ALIVE;
            retryCounter_ = 0; // success
            log_.info(ME, string("Connection transition ") + getStateStr(oldState) + " -> " + getStateStr(state_) + ": Success, " + myId_ + " reconnected.");
            if (address_.getPingInterval() > 0L) // respan ping timer
               timerKey_ = pingTimer_.addTimeoutListener(this, address_.getPingInterval(), NULL);
//            connectionsHandler_.toAlive(this);
            return;
         }

         if (address_.getRetries() == -1 || retryCounter_ < address_.getRetries()) {
            // poll for connection ...
            state_ = POLLING;
            retryCounter_++;
            if (address_.getDelay() > 0) // respan reconnect poller
               timerKey_ = pingTimer_.addTimeoutListener(this, address_.getDelay(), pollStr_);
            if (oldState == ALIVE) {
               resetConnection();
               log_.warn(ME, string("Connection transition ") + getStateStr(oldState) + " -> " + getStateStr() + ": " + address_.toXml() + " is unaccessible, we poll for it ...");
               if (log_.TRACE) log_.trace(ME, string("Connection transition ") + getStateStr(oldState) + " -> " + getStateStr() + " for " + myId_ + ": retryCounter=" + lexical_cast<string>(retryCounter_) + ", delay=" + lexical_cast<string>(address_.getDelay()) + ", maxRetries=" + lexical_cast<string>(address_.getRetries()));
//               connectionsHandler_.toPolling(this);
            }

            if (byDeliveryConnectionsHandler)
               throw XmlBlasterException(COMMUNICATION_NOCONNECTION_POLLING, ME, "We are in polling mode, can't handle request");
            return;
         }

         // error giving up ...
         state_ = DEAD;
//      } // synchronized because of timerKey and status transition

      // error giving up ...
//      log_.warn(ME, string("Connection transition ") + getStateStr(oldState) + " -> " + getStateStr() + ": retryCounter=" + lexical_cast<string>(retryCounter_) + ", maxRetries=" + lexical_cast<string>(address_.getRetries()));
      string help = lexical_cast<string>(address_.getRetries());
      log_.warn(ME, string("Connection transition ") + getStateStr(oldState) + " -> " + getStateStr() + ": retryCounter=" + lexical_cast<string>(retryCounter_) + ", maxRetries=" + help);

//      _.toDead(this, ex);
      
      if (byDeliveryConnectionsHandler) {
         throw ex;
      }
      else { 
         // ping timer thread, no sense to throw an exception:
      }
   }

public:
   /**
    * Stop all remote connections. 
    */
   void shutdown()
   {
      state_ = DEAD;
      if (log_.CALL) log_.call(ME, "Entering shutdown ...");
      if (timerKey_ != 0) {
         pingTimer_.removeTimeoutListener(timerKey_);
         timerKey_ = 0;
      }
      retryCounter_ = 0;
   }

   int getState() const
   {
      return state_;
   }

   bool isAlive() const
   {
      return (state_ == ALIVE);
   }

   bool isPolling() const
   {
      return (state_ == POLLING);
   }

   bool isDead() const
   {
      return (state_ == DEAD);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state as an XML ASCII string
    */
   virtual string toXml(const string& extraOffset="")
   {
      log_.warn(ME,"toXml not implemented yet");
      return "";
   }
};


}}}} // namespace

