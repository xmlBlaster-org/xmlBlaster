/*------------------------------------------------------------------------------
Name:      CbConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding messages waiting on client callback.
Version:   $Id: CbConnection.java,v 1.9 2002/09/09 13:35:44 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.callback;

import org.jutils.log.LogChannel;

import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.queue.MsgQueue;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.engine.queue.MsgQueueEntry;


/**
 * Holding all necessary infos to establish callback
 * connections and invoke their update().
 */
public class CbConnection implements I_Timeout
{
   public final String ME;
   private final Global glob;
   private final LogChannel log;
   private final MsgQueue msgQueue;
   private I_CallbackDriver cbDriver = null;
   private CallbackAddress cbAddress = null;

   private final Timeout cbPingTimer;
   private Timestamp timerKey = null;

   private final int IS_ALIVE = 0;
   private final int IS_POLLING = 1;
   private final int IS_DEAD = 2;
   private int state = IS_ALIVE;

   private int retryCounter = 0;

   /**
    * @param msgQueue The message queue witch i belong to
    * @param cbAddress The address i shall connect to
    */
   public CbConnection(Global glob, MsgQueue msgQueue, CallbackAddress cbAddress) throws XmlBlasterException {
      this.ME = "CbConnection-" + msgQueue.getName();
      this.glob = glob;
      this.log = glob.getLog("cb");
      this.msgQueue = msgQueue;
      this.cbPingTimer = glob.getCbPingTimer();
      initialize(cbAddress);
   }

   /**
    * @return A nice name for logging
    */
   public final String getName() {
      return ME;
   }

   /** Load the appropriate protocol driver */
   public final void initialize(CallbackAddress cbAddress_) throws XmlBlasterException {
      this.cbAddress = cbAddress_;
      if (cbAddress == null)
         throw new IllegalArgumentException(ME + " with null address");

      state = IS_ALIVE;
      retryCounter = 0;
      
      // Check if a native callback driver is passed in the glob Hashtable (e.g. for "SOCKET" or "native"), take this instance
      //if (cbAddress.getId().equalsIgnoreCase("NATIVE")) {
      cbDriver = glob.getNativeCallbackDriver(cbAddress.getType() + cbAddress.getAddress());
      if (cbDriver != null)  { // && obj.toString().indexOf("org.xmlBlaster.protocol.socket.CallbackSocketDriver") >= 0) {
         cbDriver.init(glob, cbAddress);
         if (log.TRACE) log.trace(ME, "Created native callback driver for protocol '" + cbAddress.getType() + "'");
         return;
      }

      cbDriver = glob.getProtocolManager().getCbProtocolManager().getNewCbProtocolDriverInstance(cbAddress.getType());

      if (cbDriver == null) {
         log.error(ME+".UnknownCallbackProtocol", "Sorry, callback type='" + cbAddress.getType() + "' is not supported, try setting 'CbProtocolPlugin["+cbAddress.getType()+"][1.0]=' in xmlBlaster.properties");
         throw new XmlBlasterException("UnknownCallbackProtocol", "Sorry, callback type='" + cbAddress.getType() + "' is not supported");
      }

      try {
         cbDriver.init(glob, cbAddress);
      }
      catch (XmlBlasterException e) {
         handleTransition(false, true);
      }

      if (cbAddress.getPingInterval() > 0L) // respan ping timer
         timerKey = cbPingTimer.addTimeoutListener(this, cbAddress.getPingInterval(), null);
      /* This gives deadlock on second login session if first is stale
         The login and shutdown don't like each other
         So we span a ping timer to check with the ping thread.
      try {
         ping("");
      }
      catch (XmlBlasterException e) {
      }
      */

      if (log.TRACE) log.trace(ME, "Created callback driver for protocol '" + cbAddress.getType() + "'");
   }

   public void finalize()
   {
      if (timerKey != null) {
         this.cbPingTimer.removeTimeoutListener(timerKey);
         timerKey = null;
      }

      if (log.TRACE) log.trace(ME, "finalize - garbage collected");
   }

   public final CallbackAddress getCbAddress() {
      return this.cbAddress;
   }

   public final I_CallbackDriver getCbDriver() {
      return this.cbDriver;
   }

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

      boolean isPing = (userData == null);

      if (isPing) {
         timerKey = null;
         try {
            if (log.TRACE) log.trace(ME, "Going to ping client callback server ...");
            String result = ping("");
         } catch (XmlBlasterException e) {
            // is handled in ping() itself
         }
      }
      else { // reconnect polling
         try {
            if (log.TRACE) log.trace(ME, "Going to check if client callback server is available again ...");
            cbDriver.init(glob, cbAddress);
         }
         catch (XmlBlasterException e) {
            handleTransition(false, false);
         }
         catch (Throwable e) {
            log.error(ME, "Callback reconnect polling error: " + e.toString());
            e.printStackTrace();
            handleTransition(false, false);
         }
         try {
            ping("");
         }
         catch (XmlBlasterException e) {
         }
      }
   }

   /**
    * Send the messages back to the client. 
    * @return The returned string from the client, for oneway updates it is null
    */
   public String[] sendUpdate(MsgQueueEntry[] msg, int redeliver) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "sendUpdate(msg.length=" + msg.length + ", redeliver=" + redeliver + ")"); 
      if (msg.length == 0) { // assert
         return null;
      }
      if (cbDriver == null) { // assert
         state = IS_DEAD;
         log.error(ME, "callback driver is null, msg.length=" + msg.length + " messages are lost");
         throw new XmlBlasterException(ME, "Internal problem: callback driver is null, msg.length=" + msg.length + " messages are lost");
      }
      if (isDead()) { // assert
         log.error(ME, "callback driver is in state IS_DEAD, msg.length=" + msg.length + " messages are lost");
         throw new XmlBlasterException(ME, "Internal problem: callback driver is in stater IS_DEAD, msg.length=" + msg.length + " messages are lost");
      }

      // First we export the message (call the interceptor) ...
      for (int i=0; i<msg.length; i++) {
         I_Session sessionSecCtx = msg[i].getSessionInfo().getSecuritySession();
         if (sessionSecCtx==null) {
            log.error(ME+".accessDenied", "No session security context!");
            throw new XmlBlasterException(ME+".accessDenied", "No session security context!");
         }
         //cbAddress[0] REDUCE UPDATE QOS!!! TODO
         msg[i].setMessageUnit(sessionSecCtx.exportMessage(msg[i].getMessageUnit(i, msg.length, redeliver)));
         if (log.DUMP) log.dump(ME, "CallbackQos=" + msg[i].getMessageUnit().getQos());
      }

      try {
         if (cbAddress.oneway()) {
            cbDriver.sendUpdateOneway(msg);
            if (log.TRACE) log.trace(ME, "Success, sent " + msg.length + " oneway messages.");
            return null;
         }
         
         if (log.TRACE) log.trace(ME, "Before update " + msg.length + " acknowledged messages ...");
         String[] returnVal = cbDriver.sendUpdate(msg);
         if (log.TRACE) log.trace(ME, "Success, sent " + msg.length + " acknowledged messages, return value #1 is '" + returnVal[0] + "'");
         return returnVal;
      }
      catch (XmlBlasterException e) {
         if (isPolling() && log.TRACE) log.trace(ME, "Exception from update(), retryCounter=" + retryCounter + ", state=" + getStateStr());
         handleTransition(false, true);
         throw e;
      }
   }

   /** Ping the callback server of the client */
   public final String ping(String data) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "ping()");
      if (cbDriver == null) { // assert
         state = IS_DEAD;
         log.error(ME, "callback driver is null, ping not possible");
         throw new XmlBlasterException(ME, "Internal problem: callback driver is null, ping is not possible");
      }
      
      data = (data==null)?"":data;

      try {
         String returnVal = cbDriver.ping(data);
         if (log.TRACE && isAlive()) log.trace(ME, "Success for ping('" + data + "'), return='" + returnVal + "'");
         handleTransition(true, false);
         return returnVal;
      }
      catch (XmlBlasterException e) {
         if (isAlive() && log.TRACE) log.trace(ME, "Exception from callback ping(), retryCounter=" + retryCounter + ", state=" + getStateStr());
         handleTransition(false, false);
         throw e;
      }
   }

   /**
    * @param ok If true it is a transition to reconnected
    * @param byCbManager true if invoked by CbManager, false if invoked by our timer/ping
    */
   private void handleTransition(boolean ok, boolean byCbManager) {

      int oldState = state;

      synchronized (this) {
         
         if (timerKey != null) {
            this.cbPingTimer.removeTimeoutListener(timerKey);
            timerKey = null;
         }

         if (ok) {
            if (isAlive()) {
               if (cbAddress.getPingInterval() > 0L) // respan ping timer
                  timerKey = cbPingTimer.addTimeoutListener(this, cbAddress.getPingInterval(), null);
            }
            else if (isPolling()) {
               state = IS_ALIVE;
               retryCounter = 0; // success
               if (cbAddress.getPingInterval() > 0L) // respan ping timer
                  timerKey = cbPingTimer.addTimeoutListener(this, cbAddress.getPingInterval(), null);
               log.info(ME, "Callback transition " + getStateStr(oldState) + " -> " + getStateStr() + ": Success, " + msgQueue.getLoginName() + " reconnected.");
            }
            else if (isDead()) {   // ignore, not possible
               log.warn(ME, "Callback transition " + getStateStr(oldState) + " -> " + getStateStr() + " for " + msgQueue.getLoginName() + ": We ignore it.");
            }
         }
         else { // error
            if (isDead()) {   // ignore, not possible
               log.warn(ME, "Callback transition " + getStateStr(oldState) + " -> " + getStateStr() + " for " + msgQueue.getLoginName() + ": We ignore it ...");
            }
            else if (cbAddress.getRetries() != -1 && retryCounter >= cbAddress.getRetries()) {
               state = IS_DEAD;
               log.warn(ME, "Callback transition " + getStateStr(oldState) + " -> " + getStateStr() + ": Callback server of client " + msgQueue.getLoginName() + " is unaccessible.");
               if (log.TRACE) log.trace(ME, "Callback transition " + getStateStr(oldState) + " -> " + getStateStr() + " for " + msgQueue.getLoginName() + ": retryCounter=" + retryCounter + ", maxRetries=" + cbAddress.getRetries());
               // CbManager does clean up -> auto logout of session
               if (byCbManager == false) {
                  msgQueue.getCbManager().removeCbConnection(this);
               }
            }
            else {
               state = IS_POLLING;
               retryCounter++;
               if (cbAddress.getDelay() > 0L) // respan reconnect poller
                  timerKey = cbPingTimer.addTimeoutListener(this, cbAddress.getDelay(), "poll");
               if (oldState == IS_ALIVE) {
                  log.warn(ME, "Callback transition " + getStateStr(oldState) + " -> " + getStateStr() + ": Callback server of client " + msgQueue.getLoginName() + " is unaccessible, we poll for it ...");
                  if (log.TRACE) log.trace(ME, "Callback transition " + getStateStr(oldState) + " -> " + getStateStr() + " for " + msgQueue.getLoginName() + ": retryCounter=" + retryCounter + ", delay=" + cbAddress.getDelay() + ", maxRetries=" + cbAddress.getRetries());
               }
            }
         }
      } // synchronized
   }


   /**
    * Stop all callback drivers of this client.
    */
   public final void shutdown() {
      if (log.CALL) log.call(ME, "Entering shutdown ...");
      if (timerKey != null) {
         this.cbPingTimer.removeTimeoutListener(timerKey);
         timerKey = null;
      }
      retryCounter = 0;
      state = IS_DEAD;
      if (cbDriver != null) {
         cbDriver.shutdown();
      }
   }

   public final boolean isAlive() {
      return state == IS_ALIVE;
   }

   public final boolean isPolling() {
      return state == IS_POLLING;
   }

   public final boolean isDead() {
      return state == IS_DEAD;
   }

   public final String getStateStr() {
      return getStateStr(state);
   }

   public final String getStateStr(int stat) {
      if (stat == IS_ALIVE) return "IS_ALIVE";
      if (stat == IS_POLLING) return "IS_POLLING";
      if (stat == IS_DEAD) return "IS_DEAD";
      return "ERROR";
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of SessionInfo as a XML ASCII string
    */
   public final String toXml(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer(256);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<CbConnection>");
      cbAddress.toXml("   " + offset);
      if (cbDriver == null)
         sb.append(offset).append("   <noCallbackDriver />");
      else
         sb.append(offset).append("   <callback type='" + cbDriver.getName() + "' state='" + state + "'/>");
      sb.append(offset).append("</CbConnection>");

      return sb.toString();
   }
}

