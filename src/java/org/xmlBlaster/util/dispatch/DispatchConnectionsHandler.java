/*------------------------------------------------------------------------------
Name:      DispatchConnectionsHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding messages waiting on client callback.
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBuffer;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.address.AddressBase;

import java.util.ArrayList;


/**
 * Holding all necessary infos to establish a remote
 * connection and invoke update()/updateOneway()/ping(). 
 * <p>
 * This instance is a 'logical connection' hiding multiple
 * 'physical' connections (called DispatchConnection).
 * </p>
 * <p>
 * One instance of this is used for each DispatchManager (one logical connection).
 * </p>
 * <pre>
 *    State chart of the 'logical connection', initially UNDEF:
 *
 *      +<-----------------initialize()--------------+
 *      |                                            |
 *      |   +<--toAlive()----+    +<-initialize()+   |          
 *      |   |   initialize() |    |              |   |  
 *      |   |                |    |              |   |  
 *    #########            ##########         ##########
 *   #         #          #          #       #          #
 *   #  ALIVE  #          # POLLING  #       #  DEAD    #
 *   #         #          #          #       #          #
 *    #########            ##########         ##########
 *      |   |                |    |             |    |
 *      |   +--toPolling()-->+    +--toDead()-->+    |
 *      |      initialize()          initialize()    |
 *      |                                            |
 *      +------------------toDead()----------------->+
 *                         initialize()
 * </pre>
 * <p>
 * Note: Recovery from dead state is only possible if
 *       new callback addresses are passed with initialize()
 * </p>
 * <p>
 * Note: toAlive(), toPolling() and toDead() are called
 *       by a single DispatchConnection only, telling its state change.
 * </p>
 * @author xmlBlaster@marcelruff.info
 */
abstract public class DispatchConnectionsHandler
{
   public final String ME;
   protected final Global glob;
   private static Logger log = Logger.getLogger(DispatchConnectionsHandler.class.getName());
   protected final I_DispatchManager dispatchManager;
   protected final DispatchStatistic statistic;
   protected I_PostSendListener postSendListener;

   /** holds all DispatchConnection instances */
   private ArrayList conList = new ArrayList();

   private ConnectionStateEnum state = ConnectionStateEnum.UNDEF;
   
   /**
    * You need to call initialize() after construction. 
    * @param dispatchManager The message queue witch i belong to
    * @param cbAddr The addresses i shall connect to
    */
   public DispatchConnectionsHandler(Global glob, I_DispatchManager dispatchManager) throws XmlBlasterException {
      this.ME = dispatchManager.getQueue().getStorageId().toString();
      this.glob = glob;

      this.dispatchManager = dispatchManager;
      this.statistic = new DispatchStatistic();
   }

   public final I_DispatchManager getDispatchManager() {
      return this.dispatchManager;
   }

   /**
    * Access the listener for send messages. 
    * @return Returns the postSendListener or null if none is registered
    */
   public final I_PostSendListener getPostSendListener() {
      return this.postSendListener;
   }

   /**
    * Register a listener to get notifications when a messages is successfully send. 
    * Max one can be registered, any old one will be overwritten 
    * @param postSendListener The postSendListener to set.
    */
   public final void registerPostSendListener(I_PostSendListener postSendListener) {
      this.postSendListener = postSendListener;
   }

   /**
    * Overwrite existing connections with new configuration
    */
   public final void initialize(AddressBase[] cbAddr) throws XmlBlasterException {
      int oldConSize = getCountDispatchConnection();//conList.size();
      DispatchConnection reconfiguredCon = null;
      if (log.isLoggable(Level.FINER)) log.finer(ME+": Initialize old connections=" + oldConSize +
                                 " new connections=" + ((cbAddr==null)?0:cbAddr.length));
      ArrayList toShutdown = new ArrayList();
      try {
         synchronized (this.dispatchManager) {
            
            DispatchConnection[] tmpList = getDispatchConnectionArr();
            clearDispatchConnectionList(); // conList.clear();
            
            if (cbAddr == null || cbAddr.length==0) {
               for (int ii=0; ii<tmpList.length; ii++) {
                  if (tmpList[ii] != null)
                     tmpList[ii].shutdown(true);
               }
               updateState(null);
               return;
            }

            // shutdown callbacks not in use any more ...
            for (int ii=0; ii<tmpList.length; ii++) {
               boolean found = false;
               DispatchConnection  tmpConn = tmpList[ii];
               if (tmpConn == null) continue;
               for (int jj=0; jj<cbAddr.length; jj++) {
                  Object obj = cbAddr[jj].getCallbackDriver();
                  if (obj != null && obj != tmpConn.getAddress().getCallbackDriver()) {
                     continue;
                  }
                  if (tmpConn.getAddress().isSameAddress(cbAddr[jj])) {
                     found = true;
                     break;
                  }
               }
               if (!found) {
                  log.info(ME+": Shutting down callback connection '" + tmpConn.getName() + "' because of new configuration.");
                  toShutdown.add(tmpConn);
                  tmpList[ii] = null;
                  //con.shutdown();
               }
            }

            // keep existing addresses, add the new ones ...
            for (int ii=0; ii<cbAddr.length; ii++) {
               boolean found = false;
               for (int jj=0; jj<tmpList.length; jj++) {
                  DispatchConnection tmpCon = tmpList[jj];
                  if (tmpCon == null) continue;
                  if (cbAddr[ii].isSameAddress((tmpCon).getAddress())) {
                     found = true;
                     tmpCon.setAddress(cbAddr[ii]);
                     addDispatchConnection(tmpCon); // reuse
                     reconfiguredCon = tmpCon;
                     tmpCon.registerProgressListener(this.statistic); // presistent SOCKET cb after restart
                     break;
                  }
               }
               if (!found) {
                  try {  // This creates a client or cb instance with its plugin
                     DispatchConnection con = createDispatchConnection(cbAddr[ii]);
                     if (log.isLoggable(Level.FINE)) log.fine(ME+": Create new DispatchConnection, retries=" + cbAddr[ii].getRetries() + " :" + cbAddr[ii].toXml());
                     try {
                        addDispatchConnection(con);
                        con.initialize();
                        con.registerProgressListener(this.statistic);
                     }
                     catch (XmlBlasterException e) {
                        if (e.isCommunication()) { // Initial POLLING ?
                           this.dispatchManager.toPolling(this.state);
                           if (log.isLoggable(Level.FINE)) log.fine(ME+": Load " + cbAddr[ii].toString() + ": " + e.getMessage());
                        }
                        else {
                           log.severe(ME+": Can't load " + cbAddr[ii].toString() + ": " + e.getMessage());
                           toShutdown.add(con);
                           //con.shutdown();
                           synchronized (conList) {
                              conList.remove(con);
                           }
                        }
                     }
                  }
                  catch (XmlBlasterException e) {
                     log.warning(ME+": Can't load " + cbAddr[ii].toString() + ": " + e.getMessage());
                     throw e;
                  }
                  catch (Throwable e) {
                     log.severe(ME+": Can't load " + cbAddr[ii].toXml() + ": " + e.toString());
                     throw XmlBlasterException.convert(glob, ME, "", e);
                  }
                  // TODO: cleanup if exception is thrown by createDispatchConnection()
               }
            }
            
            tmpList = null;

         } // synchronized
      }
      finally {
         // We had the case where
         //   java.net.PlainSocketImpl.socketClose0(Native Method)
         // blocked for 20 min in LAST_ACK, so we shutdown outside of the synchronized now: 
         for (int i=0;  i<toShutdown.size(); i++) {
            DispatchConnection con = (DispatchConnection)toShutdown.get(i);
            try {
                boolean delayed = false;
                con.shutdown(delayed); // Immediate shutdown (and send POLLING events etc
            }
            catch (XmlBlasterException ex) {
               log.severe(ME+"initialize(): Could not shutdown properly. " + ex.getMessage());
            }
         }

         updateState(null);  // Redundant??
         if (log.isLoggable(Level.FINE)) log.fine(ME+": Reached state = " + state.toString());

         if (reconfiguredCon != null && /*reconfiguredCon.*/isPolling() && oldConSize > 0) {
            this.glob.getPingTimer().addTimeoutListener(reconfiguredCon, 0L, "poll");  // force a reconnect try
         }
      }
   }

   /**
    * Create a DispatchConnection instance and load the protocol plugin. 
    * You should call initialie() later.
    */
   abstract public DispatchConnection createDispatchConnection(AddressBase address) throws XmlBlasterException;

   /** @return a currently available callback connection (with any state) or null */
   public final DispatchConnection getCurrentDispatchConnection() {
      DispatchConnection d = getAliveDispatchConnection();
      if (d == null)
         d = getPollingDispatchConnection();
      if (d == null)
         d = getDeadDispatchConnection();
      return d;
   }

   /** @return a currently alive callback connection or null */
   public final DispatchConnection getAliveDispatchConnection() {
      DispatchConnection[] arr = getDispatchConnectionArr();
      for (int ii=0; ii<arr.length; ii++) {
         if (arr[ii].isAlive())
            return arr[ii];
      }
      return null;
   }

   /** @return a currently polling callback connection or null */
   public final DispatchConnection getPollingDispatchConnection() {
      DispatchConnection[] arr = getDispatchConnectionArr();
      for (int ii=0; ii<arr.length; ii++) {
         if (arr[ii].isPolling())
            return arr[ii];
      }
      return null;
   }

   /** @return a currently alive callback connection or null */
   public final AddressBase getAliveAddress() {
      DispatchConnection con = getAliveDispatchConnection();
      return (con == null) ? null : con.getAddress();
   }

   /** @return a currently dead callback connection or null */
   public final AddressBase getDeadAddress() {
      DispatchConnection con = getDeadDispatchConnection();
      return (con == null) ? null : con.getAddress();
   }

   /** @return a dead callback connection or null */
   public final DispatchConnection getDeadDispatchConnection() {
      DispatchConnection[] arr = getDispatchConnectionArr();
      for (int ii=0; ii<arr.length; ii++) {
         if (arr[ii].isDead())
            return arr[ii];
      }
      return null;
   }

   /** @return a copy snapshot of the current connections */
   public DispatchConnection[] getDispatchConnectionArr() {
      synchronized (conList) {
         return (DispatchConnection[])conList.toArray(new DispatchConnection[conList.size()]);
      }
   }

   /** @return Number of established callback connections */
   public int getCountDispatchConnection() {
      synchronized (conList) {
         return conList.size();
      }
   }

   public void addDispatchConnection(DispatchConnection con) {
      synchronized (conList) {
         conList.add(con);
      }
   }

   public void clearDispatchConnectionList() {
      synchronized (conList) {
         conList.clear();
      }
   }

   /** Call by DispatchConnection on state transition */
   final void toAlive(DispatchConnection con) {
      con.registerProgressListener(this.statistic);
      updateState(null);
   }

   /** Call by DispatchConnection on state transition */
   final void toPolling(DispatchConnection con) {
      con.registerProgressListener(null);
      updateState(null);
   }

   /** Call by DispatchConnection on state transition */
   final void toDead(DispatchConnection con, XmlBlasterException ex) {
      con.registerProgressListener(null);
      removeDispatchConnection(con); // does updateState()
      updateState(ex);
   }

   /**
    * Handles the state transition
    * @param XmlBlasterException can be null
    */
   private final void updateState(XmlBlasterException ex) {
      ConnectionStateEnum oldState = this.state;
      ConnectionStateEnum tmp = ConnectionStateEnum.DEAD;
      
      if (oldState == ConnectionStateEnum.DEAD) {
         log.warning("Ignoring state change as we are in DEAD: " + ((ex == null) ? "" : ex.getMessage()));
         return;
      }
      
      if (log.isLoggable(Level.FINE)) log.fine(ME+": updateState() oldState="+oldState+" conList.size="+
            getCountDispatchConnection());
      DispatchConnection[] arr = getDispatchConnectionArr();
      for (int ii=0; ii<arr.length; ii++) {
         if (arr[ii].isAlive()) {
            this.state = ConnectionStateEnum.ALIVE;
            if (oldState != this.state)
               dispatchManager.toAlive(oldState);
            return;
         }
         else if (arr[ii].isPolling()) {
            tmp = ConnectionStateEnum.POLLING;
         }
      }
      if (tmp == ConnectionStateEnum.POLLING) {
         this.state = ConnectionStateEnum.POLLING;
         if (oldState != this.state)
            dispatchManager.toPolling(oldState);
      }
      else if (tmp == ConnectionStateEnum.DEAD) {
         this.state = ConnectionStateEnum.DEAD;
         if (oldState != this.state)
            dispatchManager.shutdownFomAnyState(oldState, ex);
      }
      else {
         this.state = tmp;
         log.severe(ME+": Internal error in updateState(oldState="+oldState+","+this.state+") " + toXml(""));
         Thread.dumpStack();
      }
   }

   /**
    * @return true if not initialized yet. 
    */
   public final boolean isUndef() {
      return this.state == ConnectionStateEnum.UNDEF;
   }

   /**
    * @return true if at least one connection is alive
    */
   public final boolean isAlive() {
      return this.state == ConnectionStateEnum.ALIVE;
   }

   /**
    * @return true if no connection alive but at least one is still polling
    */
   public final boolean isPolling() {
      return this.state == ConnectionStateEnum.POLLING;
   }

   /**
    * @return true if all connections are lost (polling is given up)
    */
   public final boolean isDead() {
      return this.state == ConnectionStateEnum.DEAD;
   }

   public ConnectionStateEnum getState() {
      return this.state;
   }

   private final void removeDispatchConnection(DispatchConnection con) {
      if (log.isLoggable(Level.FINER)) log.finer(ME+": removeDispatchConnection(" + con.getName() + ") ...");
      synchronized (conList) {
         conList.remove(con);
      }
      try {
         con.shutdown(true);
      }
      catch (XmlBlasterException ex) {
         log.severe(ME+": Could not shutdown properly. " + ex.getMessage());
      }
      if (log.isLoggable(Level.FINE)) log.fine(ME+": Destroyed one callback connection, " + getCountDispatchConnection() + " remain.");
   }

   /**
    * If no connection is available but the message is for example save queued,
    * we can generate here valid return objects
    * @param state e.g. Constants.STATE_OK
    */
   abstract public void createFakedReturnObjects(I_QueueEntry[] entries, String state, String stateInfo) throws XmlBlasterException;

   /**
    * Send the messages back to the client. 
    * If there are more fallback addresses, these will be used if the
    * first fails.
    * <p>
    * The RETURN value is transferred in the msgArr[i].getReturnObj(), for oneway updates it is null
    * </p>
    * @param isAsyncMode true if coming from queue
    */
   public void send(MsgQueueEntry[] msgArr, boolean isAsyncMode) throws Throwable, XmlBlasterException
   {
      ///////////////if (isDead())
      /////////////   throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_DEAD, ME, "Sending of " + msgArr.length + " messages '" + msgArr[0].getKeyOid() +
      ////////////      "' from [" + msgArr[0].getSender() + "] failed, no connection is available");

      Throwable ex = null; // to remember exception

         // Try to find a connection which delivers the message ...
         // PtP messages from the subject Queue are delivered to all reachable sessions of this user ...

      DispatchConnection[] cons = getDispatchConnectionArr(); // take a snapshot
      for (int ii=0; ii<cons.length; ii++) {
         DispatchConnection con = cons[ii];
         if (log.isLoggable(Level.FINE)) log.fine(ME+": Trying cb# " + ii + " state=" + con.getState().toString() + " ...");
         if (con.isAlive()) {
            try {
               con.send(msgArr, isAsyncMode);
               return;
            } catch(Throwable e) {
               ex = e;
               if (ii<(cons.length-1)) log.warning(ME+": Callback failed, trying other addresses: " + e.toString());
            }
         }
      }

      // error - no success sending message:

      if (ex == null) {
         if (cons.length == 0) {
            ex = new XmlBlasterException(glob, 
               isDead() ? ErrorCode.COMMUNICATION_NOCONNECTION_DEAD : ErrorCode.COMMUNICATION_NOCONNECTION,
               ME, 
               "Callback of " + msgArr.length + " messages '" + msgArr[0].getKeyOid() +
               "' from sender [" + msgArr[0].getSender() +
               "] failed, no callback connection is alive");
         }
         else {
            ex = new XmlBlasterException(glob, 
               isDead() ? ErrorCode.COMMUNICATION_NOCONNECTION_DEAD : ErrorCode.COMMUNICATION_NOCONNECTION,
               ME, 
               "Callback of " + msgArr.length + " messages '" + msgArr[0].getKeyOid() +
               "' to client [" + cons[0].getAddress().getSecretSessionId() + "] from [" + msgArr[0].getSender() +
               "] failed, no callback connection is alive");
         }
      }

      throw ex;
   }

   /**
    * @return A container holding some statistical delivery information, is never null
    */
   public final DispatchStatistic getDispatchStatistic() {
      return this.statistic; 
   }
   
   abstract public boolean isUserThread();

   /**
    * Stop all callback drivers of this client.
    */
   public final void shutdown() {
      if (log.isLoggable(Level.FINER)) log.finer(ME+": Entering shutdown ...");
      DispatchConnection[] arr=getDispatchConnectionArr();
      clearDispatchConnectionList();
      for (int ii=0; ii<arr.length; ii++) {
         try {
            arr[ii].shutdown(true);
         }
         catch (XmlBlasterException ex) {
            log.severe(ME+": Could not shutdown properly. " + ex.getMessage());
         }
      }
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of SessionInfo as a XML ASCII string
    */
   public final String toXml(String extraOffset) {
      XmlBuffer sb = new XmlBuffer(1000);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<DispatchConnectionsHandler state='").append(this.state.toString()).append("'>");
      DispatchConnection[] arr = getDispatchConnectionArr();
      if (arr.length < 1)
         sb.append(offset).append(" <noDispatchConnection/>");
      else {
         for (int ii=0; ii<arr.length; ii++) {
            sb.append(offset).append(" <connection type='").appendEscaped(arr[ii].getDriverName()).append("' state='").append(""+arr[ii].getState()).append("'/>");
         }
      }
      sb.append(offset).append("</DispatchConnectionsHandler>");

      return sb.toString();
   }

   public abstract ArrayList filterDistributorEntries(ArrayList entries, Throwable ex);
   
}

