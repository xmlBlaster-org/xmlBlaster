/*------------------------------------------------------------------------------
Name:      DispatchConnectionsHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding messages waiting on client callback.
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
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
 *    State chart of the 'logical connection':
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
   protected final LogChannel log;
   protected final DispatchManager dispatchManager;
   protected final DispatchStatistic statistic;
   protected I_PostSendListener postSendListener;

   /** holds all DispatchConnection instances */
   private ArrayList conList = new ArrayList();
   private final DispatchConnection[] DUMMY_ARR = new DispatchConnection[0];

   private ConnectionStateEnum state = ConnectionStateEnum.UNDEF;
   
   /**
    * You need to call initialize() after construction. 
    * @param dispatchManager The message queue witch i belong to
    * @param cbAddr The addresses i shall connect to
    */
   public DispatchConnectionsHandler(Global glob, DispatchManager dispatchManager) throws XmlBlasterException {
      this.ME = "DispatchConnectionsHandler-" + dispatchManager.getQueue().getStorageId();
      this.glob = glob;
      this.log = glob.getLog("dispatch");
      this.dispatchManager = dispatchManager;
      this.statistic = new DispatchStatistic();
   }

   public final DispatchManager getDispatchManager() {
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
      int oldConSize = conList.size();
      DispatchConnection reconfiguredCon = null;
      if (log.CALL) log.call(ME, "Initialize old connections=" + oldConSize +
                                 " new connections=" + ((cbAddr==null)?0:cbAddr.length));
      ArrayList toShutdown = new ArrayList();
      try {
         synchronized (conList) {
            
            if (cbAddr == null || cbAddr.length==0) {
               for (int ii=0; ii<conList.size(); ii++)
                  ((DispatchConnection)conList.get(ii)).shutdown();
               conList.clear();
               updateState(null);
               return;
            }

            ArrayList tmpList = (ArrayList)conList.clone(); // shallow
            conList.clear();

            // shutdown callbacks not in use any more ...
            for (int ii=0; ii<tmpList.size(); ii++) {
               boolean found = false;
               for (int jj=0; jj<cbAddr.length; jj++) {
                  if (((DispatchConnection)tmpList.get(ii)).getAddress().isSameAddress(cbAddr[jj])) {
                     found = true;
                     break;
                  }
               }
               if (!found) {
                  DispatchConnection con = (DispatchConnection)tmpList.get(ii);
                  log.info(ME, "Shutting down callback connection '" + con.getName() + "' because of new configuration.");
                  toShutdown.add(con);
                  tmpList.remove(ii);
                  ii--;
                  //con.shutdown();
               }
            }

            // keep existing addresses, add the new ones ...
            for (int ii=0; ii<cbAddr.length; ii++) {
               boolean found = false;
               for (int jj=0; jj<tmpList.size(); jj++) {
                  DispatchConnection tmpCon = (DispatchConnection)tmpList.get(jj);
                  if (cbAddr[ii].isSameAddress((tmpCon).getAddress())) {
                     found = true;
                     tmpCon.setAddress(cbAddr[ii]);
                     conList.add(tmpCon); // reuse
                     reconfiguredCon = tmpCon;
                     break;
                  }
               }
               if (!found) {
                  try {  // This creates a client or cb instance with its plugin
                     DispatchConnection con = createDispatchConnection(cbAddr[ii]);
                     if (log.TRACE) log.trace(ME, "Create new DispatchConnection, retries=" + cbAddr[ii].getRetries() + " :" + cbAddr[ii].toXml());
                     try {
                        conList.add(con);
                        con.initialize();
                     }
                     catch (XmlBlasterException e) {
                        if (e.isCommunication()) { // Initial POLLING ?
                           this.dispatchManager.toPolling(this.state);
                           if (log.TRACE) log.trace(ME, "Load " + cbAddr[ii].toString() + ": " + e.getMessage());
                        }
                        else {
                           log.error(ME, "Can't load " + cbAddr[ii].toString() + ": " + e.getMessage());
                           toShutdown.add(con);
                           //con.shutdown();
                           conList.remove(con);
                        }
                     }
                  }
                  catch (XmlBlasterException e) {
                     //log.error(ME, "Can't load " + cbAddr[ii].toString() + ": " + e.getMessage());
                     throw e;
                  }
                  catch (Throwable e) {
                     log.error(ME, "Can't load " + cbAddr[ii].toXml() + ": " + e.toString());
                     throw XmlBlasterException.convert(glob, ME, "", e);
                  }
                  // TODO: cleanup if exception is thrown by createDispatchConnection()
               }
            }
            
            tmpList.clear();

         } // synchronized
      }
      finally {
         // We had the case where
         //   java.net.PlainSocketImpl.socketClose0(Native Method)
         // blocked for 20 min in LAST_ACK, so we shutdown outside of the synchronized now: 
         for (int i=0;  i<toShutdown.size(); i++) {
            DispatchConnection con = (DispatchConnection)toShutdown.get(i);
            con.shutdown();
         }

         updateState(null);  // Redundant??
         if (log.TRACE) log.trace(ME, "Reached state = " + state.toString());

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


   /** @return a currently alive callback connection or null */
   public final DispatchConnection getAliveDispatchConnection() {
      synchronized (conList) {
         for (int ii=0; ii<conList.size(); ii++) {
            if (((DispatchConnection)conList.get(ii)).isAlive())
               return ((DispatchConnection)conList.get(ii));
         }
      }
      return null;
   }

   /** @return a currently polling callback connection or null */
   public final DispatchConnection getPollingDispatchConnection() {
      synchronized (conList) {
         for (int ii=0; ii<conList.size(); ii++) {
            if (((DispatchConnection)conList.get(ii)).isPolling())
               return ((DispatchConnection)conList.get(ii));
         }
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
      synchronized (conList) {
         for (int ii=0; ii<conList.size(); ii++) {
            if (((DispatchConnection)conList.get(ii)).isDead())
               return ((DispatchConnection)conList.get(ii));
         }
      }
      return null;
   }

   /** @return a copy snapshot of the current connections */
   public final DispatchConnection[] getConnectionsArrCopy() {
      DispatchConnection[] dest = null;
      synchronized (conList) {
         dest = (DispatchConnection[])conList.toArray(DUMMY_ARR);
      }
      return dest;
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
      if (log.TRACE) log.trace(ME, "updateState() oldState="+oldState+" conList.size="+conList.size());
      //Thread.currentThread().dumpStack();
      synchronized (conList) {
         for (int ii=0; ii<conList.size(); ii++) {
            if (((DispatchConnection)conList.get(ii)).isAlive()) {
               this.state = ConnectionStateEnum.ALIVE;
               if (oldState != this.state)
                  dispatchManager.toAlive(oldState);
               return;
            }
            else if (((DispatchConnection)conList.get(ii)).isPolling()) {
               tmp = ConnectionStateEnum.POLLING;
            }
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
            dispatchManager.toDead(oldState, ex);
      }
      else {
         this.state = tmp;
         log.error(ME, "Internal error in updateState(oldState="+oldState+","+this.state+") " + toXml(""));
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
      if (log.CALL) log.call(ME, "removeDispatchConnection(" + con.getName() + ") ...");
      synchronized (conList) {
         conList.remove(con);
      }
      try {
         con.shutdown();
      }
      catch (XmlBlasterException ex) {
         this.log.error(ME, "removeDispatchConnection() could not shutdown properly. " + ex.getMessage());
      }
      if (log.TRACE) log.trace(ME, "Destroyed one callback connection, " + conList.size() + " remain.");
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
    */
   public void send(MsgQueueEntry[] msgArr) throws Throwable, XmlBlasterException
   {
      if (isDead()) // if (conList.size() < 1)
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_DEAD, ME, "Callback of " + msgArr.length + " messages '" + msgArr[0].getKeyOid() +
            "' from [" + msgArr[0].getSender() + "] failed, no callback connection is available");

      Throwable ex = null; // to remember exception

         // Try to find a connection which delivers the message ...
         // PtP messages from the subject Queue are delivered to all reachable sessions of this user ...

      DispatchConnection[] cons = getConnectionsArrCopy(); // take a snapshot
      for (int ii=0; ii<cons.length; ii++) {
         DispatchConnection con = cons[ii];
         if (log.TRACE) log.trace(ME, "Trying cb# " + ii + " state=" + con.getState().toString() + " ...");
         if (con.isAlive()) {
            try {
               con.send(msgArr);
               return;
            } catch(Throwable e) {
               ex = e;
               if (ii<(cons.length-1)) log.warn(ME, "Callback failed, trying other addresses");
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

   /** @return Number of established callback connections */
   public final int getSize() {
      return conList.size();
   }

   /**
    * @return A container holding some statistical delivery information, is never null
    */
   public final DispatchStatistic getDispatchStatistic() {
      return this.statistic; 
   }

   /**
    * Stop all callback drivers of this client.
    */
   public final void shutdown() {
      if (log.CALL) log.call(ME, "Entering shutdown ...");
      synchronized (conList) {
         for (int ii=0; ii<conList.size(); ii++) {
            try {
               ((DispatchConnection)conList.get(ii)).shutdown();
            }
            catch (XmlBlasterException ex) {
               this.log.error(ME, "shutdown() could not shutdown properly. " + ex.getMessage());
            }
         }
         conList.clear();
      }
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of SessionInfo as a XML ASCII string
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(1000);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<DispatchConnectionsHandler state='").append(this.state.toString()).append("'>");
      if (this.conList.size() < 1)
         sb.append(offset).append(" <noDispatchConnection/>");
      else {
         DispatchConnection[] arr = getConnectionsArrCopy();
         for (int ii=0; ii<arr.length; ii++) {
            sb.append(offset).append(" <" + arr[ii].getDriverName() + " />");
         }
      }
      sb.append(offset).append("</DispatchConnectionsHandler>");

      return sb.toString();
   }
}

