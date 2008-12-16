/*------------------------------------------------------------------------------
Name:      DispatchConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.StatusQosData;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.xbformat.I_ProgressListener;

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
 *   #  ALIVE  #          # POLLING  #       #   DEAD   #
 *   #         #          #          #       #          #
 *    #########            ##########         ##########
 *      |   |                |    |             |    |
 *      |   +--toPolling()-->+    +--toDead()-->+    |
 *      |                                            |
 *      +-------------toDead()---------------------->+
 *
 * </pre>
 * <p>
 * Note that DispatchConnection can't recover from DEAD state
 * you need to create a new instance if desired
 * </p>
 * @author xmlBlaster@marcelruff.info
 * @author michele@laghi.eu
 */
abstract public class DispatchConnection implements I_Timeout
{
   public final String ME;
   protected final Global glob;
   private static Logger log = Logger.getLogger(DispatchConnection.class.getName());

   public static final String PING_THREAD_NAME = "_xmlBlaster.timerThread";
   
   protected DispatchConnectionsHandler connectionsHandler = null;

   /** For logging only */
   protected final String myId;
   protected AddressBase address;

   private Timestamp timerKey;
   /** Protects timerKey refresh */
   private final Object PING_TIMER_MONITOR = new Object();

   protected ConnectionStateEnum state = ConnectionStateEnum.UNDEF;

   protected int retryCounter = 0;
   private final long logEveryMillis; // Every hour (60000: every minute a log)
   private int logInterval = 10;

   /**
    * Flag if the remote server is reachable but is not willing to process our requests (standby mode).
    * This flag is only evaluated in POLLING state
    */
   protected boolean serverAcceptsRequests = false;
   protected boolean physicalConnectionOk = false;
   protected long previousBytesWritten;
   protected long previousBytesRead;
   private boolean bypassPingOnActivity = true;

   /**
    * Our loadPlugin() and initialize() needs to be called next.
    * @param connectionsHandler The DevliveryConnectionsHandler witch i belong to
    * @param address The address i shall connect to
    */
   public DispatchConnection(Global glob, DispatchConnectionsHandler connectionsHandler, AddressBase address) {
      if (address == null)
         throw new IllegalArgumentException("DispatchConnection expects an address!=null");
      this.ME = "DispatchConnection-" + connectionsHandler.getDispatchManager().getQueue().getStorageId() + " ";
      this.glob = glob;

      this.logEveryMillis = glob.getProperty().get("dispatch/logRetryEveryMillis", 3600000L); // every hour a log
      if (log.isLoggable(Level.FINE))
         log.fine("dispatch/logRetryEveryMillis=" + this.logEveryMillis);
      this.connectionsHandler = connectionsHandler;
      this.myId = connectionsHandler.getDispatchManager().getQueue().getStorageId().getId() + " ";
      this.address = address;
   }

   public void setAddress(AddressBase address)  throws XmlBlasterException {
      if (log.isLoggable(Level.FINE))
         log.fine(ME +  "setAddress: configuration has changed (with same url) set to new address object");
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
    * Called by ClientDispatchConnectionsHandler or CbDispatchConnectionsHandler
    */
   public final void initialize() throws XmlBlasterException {
      this.retryCounter = 0;

      if (this.logEveryMillis <= 0) {
         logInterval = -1; // no logging
      }
      else if (address.getDelay() < 1 || address.getDelay() > this.logEveryMillis)  // millisec
         logInterval = 1;
      else
         logInterval = (int)(this.logEveryMillis / address.getDelay());

      try {
         connectLowlevel();
         handleTransition(true, null);
      }
      catch (XmlBlasterException e) {
         if (ErrorCode.COMMUNICATION_FORCEASYNC.equals(e.getErrorCode())) {
            if (log.isLoggable(Level.FINE)) log.fine(ME + "initialize:" + e.getMessage());
         }
         else {
            if (address.isFromPersistenceRecovery()) {
               if (log.isLoggable(Level.FINE))
                  log.fine(ME + "initialize:" + e.getMessage());
            }
            else {
               log.warning(ME + "initialize:" + e.getMessage());
            }
         }
         if (retry(e)) {    // all types of ErrorCode.COMMUNICATION*
            handleTransition(true, e); // never returns (only if DEAD) - throws exception
         }
         else {
            connectionsHandler.toDead(this, e);
            throw e;
         }
      }
      catch (Throwable throwable) {
         throwable.printStackTrace();
         XmlBlasterException e = (throwable instanceof XmlBlasterException) ? (XmlBlasterException)throwable :
                                  new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "", throwable);
         if (log.isLoggable(Level.FINE)) log.fine(ME + e.toString());
         connectionsHandler.toDead(this, e);
         throw e;
      }

      if (log.isLoggable(Level.FINE)) log.fine(ME + "Created driver for protocol '" + this.address.getType() + "'");
   }

   private boolean retry(XmlBlasterException e) {
      if (e.isCommunication())  // all types of ErrorCode.COMMUNICATION*
         return true;
      //if (e.isErrorCode(ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED))
      //   return true; // If the client was killed in the server and tries to reconnect with old sessionId
      return false;
   }

   public void finalize()
   {
      if (this.timerKey != null) {
         this.glob.getPingTimer().removeTimeoutListener(this.timerKey);
         this.timerKey = null;
      }
      try {
         super.finalize();
         //if (log.isLoggable(Level.FINE)) log.fine(ME + "finalize - garbage collected");
      }
      catch (Throwable e) {
         e.printStackTrace();
      }
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
    * @param isAsyncMode true if coming from queue
    */
   abstract public void doSend(MsgQueueEntry[] msgArr_, boolean isAsyncMode) throws XmlBlasterException;

   /**
    * Should be overwritten by extending classes.
    */
   abstract public I_ProgressListener registerProgressListener(I_ProgressListener listener);

   /**
    * Send the messages back to the client.
    * @param msgArr Should be a copy of the original, since we export it which changes/encrypts the content
    * @param isAsyncMode true if coming from queue
    * @return The returned string from the client which is decrypted if necessary, for oneway updates it is null
    */
   public void send(MsgQueueEntry[] msgArr, boolean isAsyncMode) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer(ME + "send(msgArr.length=" + msgArr.length + ")");
      if (msgArr == null || msgArr.length == 0) return; // assert

      if (isDead()) { // assert
         log.severe(ME + "Connection to " + this.address.toString() + " is in state DEAD, msgArr.length=" + msgArr.length + " messages are lost");
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_DEAD, ME, "Internal problem: Connection to " + this.address.toString() + " is in state DEAD, msgArr.length=" + msgArr.length + " messages are lost");
      }

      // Send the message ...
      try {
         long now = System.currentTimeMillis();
         doSend(msgArr, isAsyncMode);
         this.connectionsHandler.getDispatchStatistic().setRoundTripDelay(System.currentTimeMillis() - now);
         handleTransition(true, null);
         return;
      }
      catch (XmlBlasterException e) {
         if (isPolling() && log.isLoggable(Level.FINE)) log.fine(ME + "Exception from update(), retryCounter=" + retryCounter + ", state=" + this.state.toString());
         for (int i=0; i<msgArr.length; i++)
            msgArr[i].incrRedeliverCounter();
         if (isDead()) throw e;
         handleTransition(true, e);
         if (isDead()) throw e;
         if (retry(e)) {
            log.severe(ME + "Exception from update(), retryCounter=" + retryCounter + ", state=" + this.state.toString() + ": " + e.getMessage());
         }
         else {
            throw e; // forward server side exception to the client
         }
      }

      Thread.dumpStack();
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "This exception is never reached" + toXml(""));
   }

   /**
    * Does the real ping to the remote server instance.
    * @param data QoS, never null
    * @return ping return QoS, never null
    * @see org.xmlBlaster.protocol.I_XmlBlaster#ping(String)
    */
   abstract public String doPing(String data) throws XmlBlasterException;

   /**
    * Ping the remote server instance (callback of the client or xmlBlaster itself)
    */
   public final String ping(String data) throws XmlBlasterException {
      return ping(data, true);
   }

   /**
    * Ping the xmlBlaster server or callback server of the client.
    * Sets serverAcceptsRequests to false if the protocol reaches the remote server but this
    * is in standby mode.
    * @param byDispatchConnectionsHandler true if invoked by DispatchConnectionsHandler
    *        we can throw exceptions back.
    *        false: If invoked by our timer/ping thread, we need to notify the situation
    */
   protected final String ping(String data, boolean byDispatchConnectionsHandler) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer(ME + "ping(" + data + ")");
      if (isDead()) { // assert
         log.severe(ME + "Protocol driver is in state DEAD, ping failed");
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_DEAD, ME, "Protocol driver is in state DEAD, ping failed");
      }
      
      data = (data==null)?"":data;
      boolean stalled = false;
      DispatchStatistic stats = this.connectionsHandler.getDispatchStatistic();
      try {
         if (log.isLoggable(Level.FINE))
            log.fine(ME + stats.toXml(""));

         // check if an ongoing write operation on the same client
         if ((stats.ongoingWrite() || stats.ongoingRead()) && this.bypassPingOnActivity) {
            if (log.isLoggable(Level.FINE))
               log.fine(ME + "AN ONGOING WRITE- OR READ OPERATION WHILE PINGING");
            long currentBytesWritten = stats.getOverallBytesWritten() + stats.getCurrBytesWritten();
            long currentBytesRead = stats.getOverallBytesRead() + stats.getCurrBytesRead();
            if (this.previousBytesWritten != currentBytesWritten || this.previousBytesRead != currentBytesRead) {
               if (log.isLoggable(Level.FINE))
                  log.fine(ME + "there was an activity since last ping, previousWritten='" + this.previousBytesWritten + "' and currentWritten='" + currentBytesWritten + "' previousRead='" + this.previousBytesRead + "' currentRead='" + currentBytesRead + "'");
               this.previousBytesWritten = currentBytesWritten;
               this.previousBytesRead = currentBytesRead;
               handleTransition(false, null);
               stalled = false;
               return Constants.RET_OK;
            }
            else {
               if (forcePingFailure()) {
                  if (log.isLoggable(Level.FINE)) {
                     String debug = " currentBytesWritten='" + currentBytesWritten + "' currentBytesRead='" + currentBytesRead + "'";
                     log.fine(ME + "there was NO activity since last ping, throwing an exception" + debug);
                  }
                  throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_RESPONSETIMEOUT,
                        glob.getId(), ME, (String)null,
                        "Ping result: The dispatcher was busy sending data but there was no activity since last ping",
                        (String)null, (Timestamp)null,
                        (String)null, (String)null, (String)null,
                        true);  /* We need to set serverSide==true ! */
               }
               else {
                  String debug = " currentBytesWritten='" + currentBytesWritten + "' currentBytesRead='" + currentBytesRead + "'";
                  log.fine(ME + "there was NO activity since last ping, will set the connection to stalled. " + debug);
                  stalled = true;
                  return Constants.RET_OK;
               }
            }
         }
         stalled = false;
         this.previousBytesWritten = stats.getOverallBytesWritten() + stats.getCurrBytesWritten();
         this.previousBytesRead = stats.getOverallBytesRead() + stats.getCurrBytesRead();

         long now = System.currentTimeMillis();
         String returnVal = doPing(data);
         this.connectionsHandler.getDispatchStatistic().setPingRoundTripDelay(System.currentTimeMillis() - now);
         // Ignore "" returns as this was specified to always return in older xmlBlaster versions
         if (returnVal.length() > 0 && returnVal.indexOf("OK") == -1) {
            // Fake a server standby exception: ping() is not specified to transport a remote XmlBlasterException but carries standby information in the state id.
            StatusQosData qos = glob.getStatusQosFactory().readObject(returnVal);
            if (!Constants.STATE_OK.equals(qos.getState())) {
               throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_SERVERDENY,
                           glob.getId(), ME, (String)null,
                           "Ping result: The server is in run level " + qos.getState() + " and not ready for requests",
                           (String)null, (Timestamp)null,
                           (String)null, (String)null, (String)null,
                           true);  /* We need to set serverSide==true ! */
            }
         }
         if (log.isLoggable(Level.FINE) && isAlive())
            log.fine(ME + "Success for ping('" + data + "'), return='" + returnVal + "'");
         handleTransition(byDispatchConnectionsHandler, null);
         return returnVal;
      }
      catch (Throwable e) { // the remote ping does not throw any XmlBlasterException, see xmlBlaster.idl
         if (isAlive() && log.isLoggable(Level.FINE))
            log.fine(ME + "Exception from remote ping(), retryCounter=" + retryCounter + ", state=" + this.state.toString() + ": " + e.toString());
         e = processResponseTimeoutException(stats, e);
         if (e == null)
            stalled = true;
         handleTransition(byDispatchConnectionsHandler, e);
         return ""; // Only reached if from timeout
      }
      finally {
         if (stats != null)
            stats.setStalled(stalled);
      }
   }

   /**
    * Returns true if it is an XmlBlasterException with error code COMMUNICATION_RESPONSETIMEOUT, false otherwise
    * @param e
    * @return
    */
   private final static boolean isResponseTimeout(Throwable e) {
      if (e == null)
         return false;
      if (e instanceof XmlBlasterException) {
         XmlBlasterException ex = (XmlBlasterException)e;
         if (ex.getErrorCode().equals(ErrorCode.COMMUNICATION_RESPONSETIMEOUT))
            return true;
         return isResponseTimeout(ex.getEmbeddedException());
      }
      return false;
   }

   /**
    *
    * @param e the original Communication Exception
    * @return the original communication exception or null if it wants to force a 'stalled' in case a ping timeout occured
    */
   private final Throwable processResponseTimeoutException(DispatchStatistic stats, Throwable e) {
      if (this.address == null || !this.address.isStallOnPingTimeout())
         return e;
      if (isResponseTimeout(e)) {
         return null;
      }
      return e;
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
    *                 here it is "poll" or null
    */
   public final void timeout(Object userData) {
      
      String origThreadName = Thread.currentThread().getName();
      Thread.currentThread().setName(PING_THREAD_NAME);
      
      try {
         //this.timerKey = null; Fix marcel 2008-08-18: seems to increase reconnect polling if server is in RL4
   
         if (isDead())
            return;
   
         boolean isPing = (userData == null);
   
         if (isPing) {
            if (log.isLoggable(Level.FINE))
               log.fine(ME + " timeout -> Going to ping remote server, physicalConnectionOk=" + this.physicalConnectionOk + ", serverAcceptsRequests=" + this.serverAcceptsRequests + " ...");
            try {
               /*String result = */ping("", false);
            }
            catch (XmlBlasterException e) {
               if (isDead()) {
                  if (log.isLoggable(Level.FINE)) log.fine(ME + "We are shutdown already: " + e.toString());
               }
               else {
                  e.printStackTrace();
                  log.severe(ME + "PANIC: " + e.toString());
               }
            } // is handled in ping() itself
         }
         else { // reconnect polling
            try {
               if (log.isLoggable(Level.FINE)) log.fine(ME + "timeout -> Going to check if remote server is available again, physicalConnectionOk=" + this.physicalConnectionOk + ", serverAcceptsRequests=" + this.serverAcceptsRequests + " ...");
               reconnect(); // The ClientDispatchConnection may choose to ping only
               try {
                  /*String result = */ping("", false);
               }
               catch (XmlBlasterException e) {
                  if (isDead()) {
                     if (log.isLoggable(Level.FINE)) log.fine(ME + "We are shutdown already: " + e.toString());
                  }
                  else {
                     e.printStackTrace();
                     log.severe(ME + "PANIC: " + e.toString()); // is handled in ping() itself
                  }
               }
            }
            catch (Throwable e) {
               if (log.isLoggable(Level.FINE)) log.fine("Polling for remote connection" + this.myId + " failed:" + e.getMessage());
               if (e instanceof NullPointerException)
                   e.printStackTrace();
               if (logInterval > 0 && (retryCounter % logInterval) == 0)
                  log.warning("No connection established, " + this.myId + address.getLogId() + " still seems to be down after " + (retryCounter+1) + " connection retries.");
               try { handleTransition(false, e); } catch(XmlBlasterException e2) { e.printStackTrace(); log.severe("PANIC: " + e.toString()); }
            }
         }
      }
      finally {
         if (origThreadName != null) {
            Thread.currentThread().setName(origThreadName);
         }
      }
   }

   protected void spanPingTimer(long timeout, boolean isPing) throws XmlBlasterException {
      String userData = (isPing) ? null : "poll";
      synchronized (this.PING_TIMER_MONITOR) {
         this.timerKey = this.glob.getPingTimer().addOrRefreshTimeoutListener(this,
               timeout, userData, this.timerKey);
      }
   }

   private boolean isAuthenticationException(XmlBlasterException ex) {
           ErrorCode code = ex.getErrorCode();
           if (code == null)
                   return false;
           return code.isOfType(ErrorCode.USER_SECURITY_AUTHENTICATION);
   }
   
   /**
    * @param toReconnected If true if the connection is OK (it is a transition to reconnected)
    * @param byDispatchConnectionsHandler true if invoked by DispatchConnectionsHandler,
    *        false if invoked by our timer/ping
    * @param The problem, is expected to be not null for toReconnected==false
    * @exception XmlBlasterException If delivery failed
    */
   protected final void handleTransition(boolean byDispatchConnectionsHandler,
                                       Throwable throwable) throws XmlBlasterException {

      boolean toReconnected = (throwable == null) ? true : false;

      XmlBlasterException ex = (throwable == null) ? null : ((throwable instanceof XmlBlasterException) ? (XmlBlasterException)throwable : null);
      ConnectionStateEnum oldState = this.state;
      this.serverAcceptsRequests = (ex == null) ? true : !ex.isErrorCode(ErrorCode.COMMUNICATION_NOCONNECTION_SERVERDENY);
      this.physicalConnectionOk = (ex == null ||
                                   glob.isServerSide() && !ex.isServerSide() ||
                                   !glob.isServerSide() && ex.isServerSide()) ? true : false;
      if (ex != null && ex.getErrorCode().isOfType(ErrorCode.COMMUNICATION_NOCONNECTION))
         this.physicalConnectionOk = false;

      if (ex != null) {
          if (isAuthenticationException(ex)) {
              connectionsHandler.toDead(this, ex);
              if (byDispatchConnectionsHandler) {
                 throw ex;
              }
              else {
                 // ping timer thread, no sense to throw an exception:
              }
          }
         if (retry(ex)) {
            toReconnected = false;
         }
         else {
            toReconnected = true;
         }
      }

      if (ex != null) {
         if (ErrorCode.COMMUNICATION_USER_HOLDBACK.equals(ex.getErrorCode())) {
            this.connectionsHandler.getDispatchManager().setDispatcherActive(false);
            log.warning("We have set dispatchActive=false, please activate dispatcher manually when the client side problem is resolved: " + ex.getMessage());
         }
      }

      if (log.isLoggable(Level.FINE)) log.fine(ME + "Connection transition " + oldState.toString() + " -> toReconnected=" + toReconnected + " byDispatchConnectionsHandler=" + byDispatchConnectionsHandler + ": " + ((ex == null) ? "" : ex.toXml()));

      synchronized (this.connectionsHandler.dispatchManager) {
         if (isDead()) {   // ignore, not possible
            if (log.isLoggable(Level.FINE)) log.fine(ME + "Connection transition " + oldState.toString() + " -> " + this.state.toString() +
                      " for " + ME + ": We ignore it: " + ((throwable == null) ? "No throwable" : throwable.toString()));
            if (connectionsHandler.getDispatchManager().isShutdown()) {
               return; // Can happen if DispatchWorker is currently delivering and the client has disconnected in the same time (thus DEAD)
            }
            if (throwable == null) {
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALSTATE, ME, "Connection transition " + oldState.toString() + " -> " + this.state.toString());
            }
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
               // timerKey==null (byDispatchConnectionsHandler==false) -> call from a ping timeout: respan
               //
               // timerKey!=null (byDispatchConnectionsHandler==true)  -> call between pings from successful update() invocation:
               // We don't need to ping directly after a successful invocation
               // so we respan the timer.
               // Probably this slows down on many updates and seldom pings,
               // should we remove the following two lines?
               spanPingTimer(this.address.getPingInterval(), true);
            }
            return;
         }

         if (timerKey != null) {
            this.glob.getPingTimer().removeTimeoutListener(timerKey);
            timerKey = null;
         }

         if (toReconnected /*&& this.serverAcceptsRequests*/ && (isPolling() || isUndef()) && !this.address.isFromPersistenceRecovery()) {
            this.state = ConnectionStateEnum.ALIVE;
            retryCounter = 0; // success
            log.info("Connection '" + getAddress().getType() + "' transition " + oldState.toString() + " -> " + this.state.toString() + ": Success, " + ME + " connected.");
            if (this.address.getPingInterval() > 0L) // respan ping timer
               timerKey = this.glob.getPingTimer().addTimeoutListener(this, this.address.getPingInterval(), null);
            connectionsHandler.toAlive(this);
            return;
         }
         
         this.address.setFromPersistenceRecovery(false); // reset
            
         if (this.address.getRetries() == -1 || retryCounter < this.address.getRetries()) {
            // poll for connection ...
            this.state = ConnectionStateEnum.POLLING;
            retryCounter++;
            if (this.address.getDelay() > 0L) { // respan reconnect poller
               if (log.isLoggable(Level.FINE)) log.fine("Polling for server with delay=" + this.address.getDelay() + " oldState=" + oldState + " retryCounter=" + retryCounter);
               if (!this.physicalConnectionOk) resetConnection();
               // next line would have spanned timeout twice and outside the synchronize (2007-00-05 Michele)
               long realSpanTime = address.getDelay();
               //  timerKey = this.glob.getPingTimer().addTimeoutListener(this, this.address.getDelay(), "poll");
               if (oldState == ConnectionStateEnum.ALIVE || oldState == ConnectionStateEnum.UNDEF) {
                  String str = (throwable != null) ? ": " + throwable.toString() : "";
                  //if (throwable != null) throwable.printStackTrace();
                  log.warning(ME + "Connection transition " + oldState.toString() + " -> " + this.state.toString() + ": " +
                               this.address.getLogId() +
                               " is unaccessible, we poll for it every " + this.address.getDelay() + " msec" + str);
                  if (log.isLoggable(Level.FINE)) log.fine(ME + "Connection transition " + oldState.toString() + " -> " + this.state.toString() + " for " + ME +
                               ": retryCounter=" + retryCounter + ", delay=" + this.address.getDelay() + ", maxRetries=" + this.address.getRetries() + str);
                  connectionsHandler.toPolling(this);
                  // spanPingTimer(400, false); // do one instant try
                  realSpanTime = 400;
               }
               if (log.isLoggable(Level.FINE))
                  log.fine(ME + " Respanning timeout for polling to '" + realSpanTime + "' ms");
               spanPingTimer(realSpanTime, false); 
               if (byDispatchConnectionsHandler)
                  throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_POLLING, ME, "We are in polling mode, can't handle request. oldState=" + oldState, throwable);
               return;
            }
            else {
               if (oldState == ConnectionStateEnum.ALIVE) {
                  resetConnection();
                  String str = (throwable != null) ? ": " + throwable.toString() : "";
                  log.warning("Connection transition " + oldState.toString() + " -> " + this.state.toString() + ": " + this.address.toString() + " is unaccessible" + str);
                  if (log.isLoggable(Level.FINE)) log.fine(ME + "Connection transition " + oldState.toString() + " -> " + this.state.toString() + " for " + ME +
                                 ": retryCounter=" + retryCounter + ", delay=" + this.address.getDelay() + ", maxRetries=" + this.address.getRetries() + str);
               }
            }
         }

         // error giving up ...
         this.state = ConnectionStateEnum.DEAD;
      } // synchronized because of timerKey and status transition

      // error giving up ...
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
      log.warning(ME + "Connection transition " + oldState.toString() + " -> " + this.state.toString() + ": retryCounter=" + retryCounter +
                   ", maxRetries=" + this.address.getRetries());

      connectionsHandler.toDead(this, ex);

      if (byDispatchConnectionsHandler) {
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
      if (log.isLoggable(Level.FINER)) log.finer(ME + "Entering shutdown ...");
      if (this.timerKey != null) {
         this.glob.getPingTimer().removeTimeoutListener(this.timerKey);
         this.timerKey = null;
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

   /**
    *
    * @return true if the implementation has to throw an exception in case the ping is invoked when
    * there is an ongoing write or read operation on the same connection.
    * Normally it shall give false on the callback and true on the client side. The client side shall
    * give true because otherwise if such a situation arizes it will never go in polling and no
    * reconnect will be triggered.
    */
   abstract protected boolean forcePingFailure();
}

