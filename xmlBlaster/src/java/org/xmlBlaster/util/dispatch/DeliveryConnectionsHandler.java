/*------------------------------------------------------------------------------
Name:      DeliveryConnectionsHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding messages waiting on client callback.
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.qos.address.AddressBase;

import java.util.ArrayList;


/**
 * Holding all necessary infos to establish a remote
 * connection and invoke update()/updateOneway()/ping(). 
 * <p>
 * This instance is a 'logical connection' hiding multiple
 * 'physical' connections (called DeliveryConnection).
 * </p>
 * <p>
 * One instance of this is used for each DeliveryManager (one logical connection).
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
 *       by a single DeliveryConnection only, telling its state change.
 * </p>
 * @author xmlBlaster@marcelruff.info
 */
abstract public class DeliveryConnectionsHandler
{
   public final String ME;
   protected final Global glob;
   protected final LogChannel log;
   protected final DeliveryManager deliveryManager;
   protected final DeliveryStatistic statistic;

   /** holds all DeliveryConnection instances */
   private ArrayList conList = new ArrayList();
   private final DeliveryConnection[] DUMMY_ARR = new DeliveryConnection[0];

   private ConnectionStateEnum state = ConnectionStateEnum.UNDEF;
   
   /**
    * You need to call initialize() after construction. 
    * @param deliveryManager The message queue witch i belong to
    * @param cbAddr The addresses i shall connect to
    */
   public DeliveryConnectionsHandler(Global glob, DeliveryManager deliveryManager) throws XmlBlasterException {
      this.ME = "DeliveryConnectionsHandler-" + deliveryManager.getQueue().getStorageId();
      this.glob = glob;
      this.log = glob.getLog("dispatch");
      this.deliveryManager = deliveryManager;
      this.statistic = new DeliveryStatistic();
   }

   public final DeliveryManager getDeliveryManager() {
      return this.deliveryManager;
   }

   /**
    * Overwrite existing connections with new configuration
    */
   public final void initialize(AddressBase[] cbAddr) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Initialize old connections=" + conList.size() +
                                 " new connections=" + ((cbAddr==null)?0:cbAddr.length));
      synchronized (conList) {
         
         if (cbAddr == null || cbAddr.length==0) {
            for (int ii=0; ii<conList.size(); ii++)
               ((DeliveryConnection)conList.get(ii)).shutdown();
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
               if (((DeliveryConnection)tmpList.get(ii)).getAddress().equals(cbAddr[jj])) {
                  found = true;
                  break;
               }
            }
            if (!found) {
               DeliveryConnection con = (DeliveryConnection)tmpList.get(ii);
               log.info(ME, "Shutting down callback connection '" + con.getName() + "' because of new configuration.");
               con.shutdown();
            }
         }

         // keep existing addresses, add the new ones ...
         for (int ii=0; ii<cbAddr.length; ii++) {
            boolean found = false;
            for (int jj=0; jj<tmpList.size(); jj++) {
               if (cbAddr[ii].equals(((DeliveryConnection)tmpList.get(jj)).getAddress())) {
                  found = true;
                  conList.add(tmpList.get(jj)); // reuse
                  break;
               }
            }
            if (!found) {
               try {  // This creates a client or cb instance with its plugin
                  DeliveryConnection con = createDeliveryConnection(cbAddr[ii]);
                  try {
                     conList.add(con);
                     con.initialize();
                  }
                  catch (XmlBlasterException e) {
                     if (e.isCommunication()) { // Initial POLLING ?
                        if (log.TRACE) log.trace(ME, "Load " + cbAddr[ii].toString() + ": " + e.getMessage());
                     }
                     else {
                        log.error(ME, "Can't load " + cbAddr[ii].toString() + ": " + e.getMessage());
                        con.shutdown();
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
               // TODO: cleanup if exception is thrown by createDeliveryConnection()
            }
         }
         
         tmpList.clear();

      } // synchronized

      updateState(null);  // Redundant??
      if (log.TRACE) log.trace(ME, "Reached state = " + state.toString());
   }

   /**
    * Create a DeliveryConnection instance and load the protocol plugin. 
    * You should call initialie() later.
    */
   abstract public DeliveryConnection createDeliveryConnection(AddressBase address) throws XmlBlasterException;


   /** @return a currently alive callback connection or null */
   public final DeliveryConnection getAliveDeliveryConnection() {
      synchronized (conList) {
         for (int ii=0; ii<conList.size(); ii++) {
            if (((DeliveryConnection)conList.get(ii)).isAlive())
               return ((DeliveryConnection)conList.get(ii));
         }
      }
      return null;
   }

   /** @return a currently alive callback connection or null */
   public final AddressBase getAliveAddress() {
      DeliveryConnection con = getAliveDeliveryConnection();
      return (con == null) ? null : con.getAddress();
   }

   /** @return a currently dead callback connection or null */
   public final AddressBase getDeadAddress() {
      DeliveryConnection con = getDeadDeliveryConnection();
      return (con == null) ? null : con.getAddress();
   }

   /** @return a dead callback connection or null */
   public final DeliveryConnection getDeadDeliveryConnection() {
      synchronized (conList) {
         for (int ii=0; ii<conList.size(); ii++) {
            if (((DeliveryConnection)conList.get(ii)).isDead())
               return ((DeliveryConnection)conList.get(ii));
         }
      }
      return null;
   }

   /** @return a copy snapshot of the current connections */
   public final DeliveryConnection[] getConnectionsArrCopy() {
      DeliveryConnection[] dest = null;
      synchronized (conList) {
         dest = (DeliveryConnection[])conList.toArray(DUMMY_ARR);
      }
      return dest;
   }

   /** Call by DeliveryConnection on state transition */
   final void toAlive(DeliveryConnection con) {
      updateState(null);
   }

   /** Call by DeliveryConnection on state transition */
   final void toPolling(DeliveryConnection con) {
      updateState(null);
   }

   /** Call by DeliveryConnection on state transition */
   final void toDead(DeliveryConnection con, XmlBlasterException ex) {
      removeDeliveryConnection(con); // does updateState()
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
            if (((DeliveryConnection)conList.get(ii)).isAlive()) {
               this.state = ConnectionStateEnum.ALIVE;
               if (oldState != this.state)
                  deliveryManager.toAlive(oldState);
               return;
            }
            else if (((DeliveryConnection)conList.get(ii)).isPolling()) {
               tmp = ConnectionStateEnum.POLLING;
            }
         }
      }
      if (tmp == ConnectionStateEnum.POLLING) {
         this.state = ConnectionStateEnum.POLLING;
         if (oldState != this.state)
            deliveryManager.toPolling(oldState);
      }
      else if (tmp == ConnectionStateEnum.DEAD) {
         this.state = ConnectionStateEnum.DEAD;
         if (oldState != this.state)
            deliveryManager.toDead(oldState, ex);
      }
      else {
         this.state = tmp;
         log.error(ME, "Internal error in updateState(oldState="+oldState+","+this.state+") " + toXml(""));
         Thread.currentThread().dumpStack();
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

   private final void removeDeliveryConnection(DeliveryConnection con) {
      if (log.CALL) log.call(ME, "removeDeliveryConnection(" + con.getName() + ") ...");
      synchronized (conList) {
         try {
            con.shutdown();
         }
         catch (XmlBlasterException ex) {
            this.log.error(ME, "removeDeliveryConnection() could not shutdown properly. " + ex.getMessage());
         }
         conList.remove(con);
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
    * @return The returned String[] from the client, for oneway invocations it is null
    */
   public void send(MsgQueueEntry[] msgArr) throws Throwable, XmlBlasterException
   {
      if (isDead()) // if (conList.size() < 1)
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_DEAD, ME, "Callback of " + msgArr.length + " messages '" + msgArr[0].getKeyOid() +
            "' from [" + msgArr[0].getSender() + "] failed, no callback connection is available");

      Throwable ex = null; // to remember exception

         // Try to find a connection which delivers the message ...
         // PtP messages from the subject Queue are delivered to all reachable sessions of this user ...

      DeliveryConnection[] cons = getConnectionsArrCopy(); // take a snapshot
      for (int ii=0; ii<cons.length; ii++) {
         DeliveryConnection con = cons[ii];
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
    * @return A container holding some statistical delivery information
    */
   public final DeliveryStatistic getDeliveryStatistic() {
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
               ((DeliveryConnection)conList.get(ii)).shutdown();
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

      sb.append(offset).append("<DeliveryConnectionsHandler state='").append(this.state.toString()).append("'>");
      if (this.conList.size() < 1)
         sb.append(offset).append(" <noDeliveryConnection/>");
      else {
         DeliveryConnection[] arr = getConnectionsArrCopy();
         for (int ii=0; ii<arr.length; ii++) {
            sb.append(offset).append(" <" + arr[ii].getDriverName() + " />");
         }
      }
      sb.append(offset).append("</DeliveryConnectionsHandler>");

      return sb.toString();
   }
}

