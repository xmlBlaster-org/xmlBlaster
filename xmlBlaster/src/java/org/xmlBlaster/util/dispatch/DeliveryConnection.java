/*------------------------------------------------------------------------------
Name:      DeliveryConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch;

import org.jutils.log.LogChannel;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;

import java.io.IOException;


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
 * Note that DeliveryConnection can't recover from DEAD state
 * you need to create a new instance if desired
 * </p>
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */
abstract public class DeliveryConnection implements I_Timeout
{
   public final String ME;
   protected final Global glob;
   protected final LogChannel log;

   protected DeliveryConnectionsHandler connectionsHandler = null;

   /** For logging only */
   protected final String myId;
   protected AddressBase address;

   private final Timeout pingTimer;
   private Timestamp timerKey;
   /** Protects timerKey refresh */
   private final Object PING_TIMER_MONITOR = new Object();

   protected ConnectionStateEnum state = ConnectionStateEnum.UNDEF;

   protected int retryCounter = 0;
   private final long logEveryMillis; // 60000: every minute a log
   private int logInterval = 10;

   /**
    * Our loadPlugin() and initialize() needs to be called next. 
    * @param connectionsHandler The DevliveryConnectionsHandler witch i belong to
    * @param address The address i shall connect to
    */
   public DeliveryConnection(Global glob, DeliveryConnectionsHandler connectionsHandler, AddressBase address) {
      if (address == null)
         throw new IllegalArgumentException("DeliveryConnection expects an address!=null");
      this.ME = "DeliveryConnection-" + connectionsHandler.getDeliveryManager().getQueue().getStorageId();
      this.glob = glob;
      this.log = glob.getLog("dispatch");
      this.logEveryMillis = glob.getProperty().get("dispatch/logRetryEveryMillis", 60000L); // every minute a log
      this.connectionsHandler = connectionsHandler;
      this.myId = connectionsHandler.getDeliveryManager().getQueue().getStorageId().getId();
      this.pingTimer = glob.getCbPingTimer();
      this.address = address;
   }

   /**
    * @return A nice name for logging
    */
   abstract public String getName();

   /** 
    * Connects on protocol level to the server and tries a ping. 
    * Needs to be called after construction
    * <p>
    * Calls connectLowLevel() which needs to be implemented by derived classes
    * loadPlugin() needs to be called before.
    * </p>
    * Called by ClientDeliveryConnectionsHandler or CbDeliveryConnectionsHandler
    */
   public final void initialize() throws XmlBlasterException {
      this.retryCounter = 0;

      try {
         connectLowlevel();
         handleTransition(true, true, null);
      }
      catch (XmlBlasterException e) {
         if (log.TRACE) log.trace(ME, e.getMessage());
         if (e.isCommunication()) {
            handleTransition(false, true, e); // never returns - throws exception
         }
         else {
            connectionsHandler.toDead(this, e);
            throw e;
         }
      }
      catch (Throwable throwable) {
         XmlBlasterException e = (throwable instanceof XmlBlasterException) ? (XmlBlasterException)throwable :
                                  new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "", throwable);
         if (log.TRACE) log.trace(ME, e.toString());
         connectionsHandler.toDead(this, e);
         throw e;
      }

      if (this.logEveryMillis <= 0) {
         logInterval = -1; // no logging
      }
      else if (address.getDelay() < 1 || address.getDelay() > this.logEveryMillis)  // millisec
         logInterval = 1;
      else
         logInterval = (int)(this.logEveryMillis / address.getDelay());
      if (log.TRACE) log.trace(ME, "Created driver for protocol '" + this.address.getType() + "'");
   }

   public void finalize()
   {
      if (this.timerKey != null) {
         this.pingTimer.removeTimeoutListener(this.timerKey);
         this.timerKey = null;
      }

      if (log.TRACE) log.trace(ME, "finalize - garbage collected");
   }

   public final AddressBase getAddress() {
      return this.address;
   }

   /** Called on COMMUNICATION errors, reset protocol driver for reconnect polling */
   abstract public void resetConnection();

   /**
    * The derived class should create an instance of the protocol driver. 
    */
   abstract public void loadPlugin() throws XmlBlasterException;

   /**
    * Connect on protocol layer and try an initial low level ping. 
    * @exception XmlBlasterException with ErrorCode.COMMUNICATION* if server is not reachable
    *            or other exceptions on other errors 
    */
   abstract public void connectLowlevel() throws XmlBlasterException;

   /** A human readable name of the protocol plugin */
   abstract public String getDriverName();

   /**
    * Send the messages. 
    * @param msgArr Should be a copy of the original, since we export it which changes/encrypts the content. 
    * msgArr[i].getReturnObj() transports the returned string array from the client which is decrypted
    * if necessary, for oneway updates it is null
    */
   abstract public void doSend(MsgQueueEntry[] msgArr_) throws XmlBlasterException;

   /**
    * Send the messages back to the client. 
    * @param msgArr Should be a copy of the original, since we export it which changes/encrypts the content
    * @return The returned string from the client which is decrypted if necessary, for oneway updates it is null
    */
   public void send(MsgQueueEntry[] msgArr) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "send(msgArr.length=" + msgArr.length + ")"); 
      if (msgArr == null || msgArr.length == 0) return; // assert

      if (isDead()) { // assert
         log.error(ME, "Connection to " + this.address.toString() + " is in state DEAD, msgArr.length=" + msgArr.length + " messages are lost");
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_DEAD, ME, "Internal problem: Connection to " + this.address.toString() + " is in state DEAD, msgArr.length=" + msgArr.length + " messages are lost");
      }

      // Send the message ...
      try {
         doSend(msgArr);
         handleTransition(true, true, null);
         return;
      }
      catch (XmlBlasterException e) {
         if (isPolling() && log.TRACE) log.trace(ME, "Exception from update(), retryCounter=" + retryCounter + ", state=" + this.state.toString());
         for (int i=0; i<msgArr.length; i++)
            msgArr[i].incrRedeliverCounter();
         if (e.isCommunication()) {
            handleTransition(false, true, e); // never returns - throws exception
         }
         else {
            handleTransition(true, true, e);
            throw e;
         }
      }

      Thread.currentThread().dumpStack();
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "This exception is never reached" + toXml(""));
   }

   /**
    * Does the real ping
    */
   abstract public String doPing(String data) throws XmlBlasterException;

   /** Ping the callback server of the client */
   public final String ping(String data) throws XmlBlasterException {
      return ping(data, true);
   }

   /**
    * Ping the callback server of the client
    * @param byDeliveryConnectionsHandler true if invoked by DeliveryConnectionsHandler
    *        we can throw exceptions back.
    *        false: If invoked by our timer/ping thread, we need to callback the situation
    */
   private final String ping(String data, boolean byDeliveryConnectionsHandler) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "ping()");
      if (isDead()) { // assert
         log.error(ME, "Callback driver is in state DEAD, ping failed");
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_DEAD, ME, "Callback driver is in state DEAD, ping failed");
      }
      
      data = (data==null)?"":data;

      try {
         String returnVal = doPing(data);
         if (log.TRACE && isAlive()) log.trace(ME, "Success for ping('" + data + "'), return='" + returnVal + "'");
         handleTransition(true, byDeliveryConnectionsHandler, null);
         return returnVal;
      }
      catch (Throwable e) {
         if (isAlive() && log.TRACE) log.trace(ME, "Exception from callback ping(), retryCounter=" + retryCounter + ", state=" + this.state.toString());
         handleTransition(false, byDeliveryConnectionsHandler, e);
         return ""; // Only reached if from timeout
      }
   }

   /** On reconnect polling try to establish the connection */
   abstract protected void reconnect() throws XmlBlasterException;

   /**
    * We are notified to do the next ping or reconnect polling. 
    * <p />
    * When connected, we ping<br />
    * When connection is lost, we do reconnect polling
    * @param userData You get bounced back your userData which you passed
    *                 with Timeout.addTimeoutListener()
    */
   public final void timeout(Object userData) {
      if (isDead())
         return;

      this.timerKey = null;

      boolean isPing = (userData == null);

      if (isPing) {
         if (log.TRACE) log.trace(ME, "timeout -> Going to ping remote server ...");
         try { String result = ping("", false); } catch (XmlBlasterException e) { e.printStackTrace(); log.error(ME, "PANIC: " + e.toString()); } // is handled in ping() itself
      }
      else { // reconnect polling
         try {
            retryCounter++;
            if (log.TRACE) log.trace(ME, "timeout -> Going to check #" + retryCounter + " if remote server is available again ...");
            reconnect();
            try { String result = ping("", false); } catch (XmlBlasterException e) { e.printStackTrace(); log.error(ME, "PANIC: " + e.toString()); } // is handled in ping() itself
         }
         catch (Throwable e) {
            if (log.TRACE) log.trace(ME, "Polling for remote connection failed:" + e.getMessage());
            if (logInterval > 0 && (retryCounter % logInterval) == 0)
               log.warn(ME, "No connection established, " + address.getLogId() + " still seems to be down after " + (retryCounter+1) + " connection retries.");
            try { handleTransition(false, false, e); } catch(XmlBlasterException e2) { e.printStackTrace(); log.error(ME, "PANIC: " + e.toString()); }
         }
      }
   }

   /**
    * @param toReconnected If true if the connection is OK (it is a transition to reconnected)
    * @param byDeliveryConnectionsHandler true if invoked by DeliveryConnectionsHandler,
    *        false if invoked by our timer/ping
    * @param The problem, is expected to be not null for toReconnected==false
    * @exception XmlBlasterException If delivery failed
    */
   protected final void handleTransition(boolean toReconnected, boolean byDeliveryConnectionsHandler,
                                       Throwable throwable) throws XmlBlasterException {

      ConnectionStateEnum oldState = this.state;
      if (log.TRACE) log.trace(ME, "Connection transition " + oldState.toString() + " -> toReconnected=" + toReconnected + " byDeliveryConnectionsHandler=" + byDeliveryConnectionsHandler);

      synchronized (this) {
         if (isDead()) {   // ignore, not possible
            log.warn(ME, "Connection transition " + oldState.toString() + " -> " + this.state.toString() + " for " + myId + ": We ignore it.");
            //Thread.currentThread().dumpStack();
            throw XmlBlasterException.convert(glob, ME, "Connection transition " + oldState.toString() + " -> " + this.state.toString(), throwable);
         }

         /*
         if (oldState == ConnectionStateEnum.UNDEF) { //startup
            if (toReconnected) {
               this.state = ConnectionStateEnum.ALIVE;
               connectionsHandler.toAlive(this);
            }
            else {
               this.state = ConnectionStateEnum.POLLING;
               connectionsHandler.toPolling(this);
            }
         }
         */

         if (toReconnected && isAlive()) { //everything is ok
            if (this.address.getPingInterval() > 0L) { // respan ping timer (even for native plugins we do a dummy ping)
               // timerKey==null (byDeliveryConnectionsHandler==false) -> call from a ping timeout: respan
               //
               // timerKey!=null (byDeliveryConnectionsHandler==true)  -> call between pings from successful update() invocation:
               // We don't need to ping directly after a successful invocation
               // so we respan the timer.
               // Probably this slows down on many updates and seldom pings,
               // should we remove the following two lines?
               synchronized (this.PING_TIMER_MONITOR) {
                  this.timerKey = this.pingTimer.addOrRefreshTimeoutListener(this,
                              this.address.getPingInterval(), null, this.timerKey);
               }
            }
            return;
         }
      
         if (timerKey != null) {
            this.pingTimer.removeTimeoutListener(timerKey);
            timerKey = null;
         }

         if (toReconnected && (isPolling() || isUndef())) {
            this.state = ConnectionStateEnum.ALIVE;
            retryCounter = 0; // success
            log.info(ME, "Connection transition " + oldState.toString() + " -> " + this.state.toString() + ": Success, " + myId + " connected.");
            if (this.address.getPingInterval() > 0L) // respan ping timer
               timerKey = pingTimer.addTimeoutListener(this, this.address.getPingInterval(), null);
            connectionsHandler.toAlive(this);
            return;
         }

         if (this.address.getRetries() == -1 || retryCounter < this.address.getRetries()) {
            // poll for connection ...
            this.state = ConnectionStateEnum.POLLING;
            retryCounter++;
            if (this.address.getDelay() > 0L) { // respan reconnect poller
               if (log.TRACE) log.trace(ME, "Polling for server with delay=" + this.address.getDelay() + " oldState=" + oldState);
               timerKey = pingTimer.addTimeoutListener(this, this.address.getDelay(), "poll");
               if (oldState == ConnectionStateEnum.ALIVE || oldState == ConnectionStateEnum.UNDEF) {
                  resetConnection();
                  String str = (throwable != null) ? ": " + throwable.toString() : "";
                  //if (throwable != null) throwable.printStackTrace();
                  log.warn(ME, "Connection transition " + oldState.toString() + " -> " + this.state.toString() + ": " + this.address.toString() +
                               " is unaccessible, we poll for it every " + this.address.getDelay() + " msec" + str);
                  if (log.TRACE) log.trace(ME, "Connection transition " + oldState.toString() + " -> " + this.state.toString() + " for " + myId +
                               ": retryCounter=" + retryCounter + ", delay=" + this.address.getDelay() + ", maxRetries=" + this.address.getRetries() + str);
                  connectionsHandler.toPolling(this);
               }
               if (byDeliveryConnectionsHandler)
                  throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_POLLING, ME, "We are in polling mode, can't handle request. oldState=" + oldState);
               return;
            }
            else {
               if (oldState == ConnectionStateEnum.ALIVE) {
                  resetConnection();
                  String str = (throwable != null) ? ": " + throwable.toString() : "";
                  log.warn(ME, "Connection transition " + oldState.toString() + " -> " + this.state.toString() + ": " + this.address.toString() + " is unaccessible" + str);
                  if (log.TRACE) log.trace(ME, "Connection transition " + oldState.toString() + " -> " + this.state.toString() + " for " + myId +
                                 ": retryCounter=" + retryCounter + ", delay=" + this.address.getDelay() + ", maxRetries=" + this.address.getRetries() + str);
               }
            }
         }

         // error giving up ...
         this.state = ConnectionStateEnum.DEAD;
      } // synchronized because of timerKey and status transition

      // error giving up ...
      XmlBlasterException ex = null;
      if (throwable == null) {
         ex = new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_DEAD, ME,
              "Connection transition " + oldState.toString() + " -> " + this.state.toString() + ": " +
              this.address.toString() + " is unaccessible.", throwable);
      }
      else if (throwable instanceof XmlBlasterException) {
         ex = (XmlBlasterException)throwable;
      }
      else {
         ex = new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "Sending of messge failed", throwable);
      }
      log.warn(ME, "Connection transition " + oldState.toString() + " -> " + this.state.toString() + ": retryCounter=" + retryCounter +
                   ", maxRetries=" + this.address.getRetries());

      connectionsHandler.toDead(this, ex);
      
      if (byDeliveryConnectionsHandler) {
         throw ex;
      }
      else { 
         // ping timer thread, no sense to throw an exception:
      }
   }

   /**
    * Stop all remote connections. 
    */
   public void shutdown() throws XmlBlasterException {
      this.state = ConnectionStateEnum.DEAD;
      if (log.CALL) log.call(ME, "Entering shutdown ...");
      if (timerKey != null) {
         this.pingTimer.removeTimeoutListener(timerKey);
         timerKey = null;
      }
      retryCounter = 0;
   }

   public final ConnectionStateEnum getState() {
      return this.state;
   }

   public final boolean isUndef() {
      return this.state == ConnectionStateEnum.UNDEF;
   }

   public final boolean isAlive() {
      return this.state == ConnectionStateEnum.ALIVE;
   }

   public final boolean isPolling() {
      return this.state == ConnectionStateEnum.POLLING;
   }

   public final boolean isDead() {
      return this.state == ConnectionStateEnum.DEAD;
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state as an XML ASCII string
    */
   abstract public String toXml(String extraOffset);
}

