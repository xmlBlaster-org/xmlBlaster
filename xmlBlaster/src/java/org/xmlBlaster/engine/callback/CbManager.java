/*------------------------------------------------------------------------------
Name:      CbManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding messages waiting on client callback.
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.callback;

import org.jutils.log.LogChannel;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.queue.MsgQueue;
import org.xmlBlaster.engine.queue.MsgQueueEntry;


/**
 * Holding all necessary infos to establish a callback
 * connection and invoke update()/updateOneway()/ping(). 
 * <p />
 * One instance of this is used for each MsgQueue.
 */
public final class CbManager
{
   public final String ME;
   private final Global glob;
   private final LogChannel log;
   private final MsgQueue msgQueue;

   private CbConnection[] cbConnectionArr;
   
   /**
    * @param msgQueue The message queue witch i belong to
    * @param cbAddr The addresses i shall connect to
    */
   public CbManager(Global glob, MsgQueue msgQueue, CallbackAddress[] cbAddr) throws XmlBlasterException {
      this.ME = "CbManager-" + msgQueue.getName();
      this.glob = glob;
      this.log = glob.getLog("cb");
      this.msgQueue = msgQueue;
      initialize(cbAddr);
   }

   /**
    * Overwrite existing connections with new configuration
    */
   public final void initialize(CallbackAddress[] cbAddr) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Initialize old connections=" + ((cbConnectionArr==null)?0:cbConnectionArr.length) +
                             " new connections=" + ((cbAddr==null)?0:cbAddr.length));
      synchronized(this) {
         
         if (cbAddr == null || cbAddr.length==0) {
            for (int ii=0; cbConnectionArr!=null && ii<cbConnectionArr.length; ii++) {
               cbConnectionArr[ii].shutdown();
            }
            cbConnectionArr = new CbConnection[0];
            return;
         }

         // shutdown callbacks not in use any more ...
         for (int ii=0; cbConnectionArr!=null && ii<cbConnectionArr.length; ii++) {
            boolean found = false;
            for (int jj=0; jj<cbAddr.length; jj++) {
               if (cbConnectionArr[ii].getCbAddress().equals(cbAddr[jj])) {
                  found = true;
                  break;
               }
            }
            if (!found) {
               cbConnectionArr[ii].shutdown();
            }
         }

         // keep existing addresses, add the new ones ...
         CbConnection[] tmp = new CbConnection[cbAddr.length];
         for (int ii=0; ii<cbAddr.length; ii++) {
            boolean found = false;
            for (int jj=0; cbConnectionArr!=null && jj<cbConnectionArr.length; jj++) {
               if (cbAddr[ii].equals(cbConnectionArr[jj].getCbAddress())) {
                  found = true;
                  tmp[ii] = cbConnectionArr[jj]; // reuse
                  break;
               }
            }
            if (!found)
               tmp[ii] = new CbConnection(glob, msgQueue, cbAddr[ii]);
         }
         cbConnectionArr = tmp;
      }
   }

   /** @return a currently alive callback connection or null */
   public final CbConnection getAliveCbConnection() {
      for (int ii=0; ii<cbConnectionArr.length; ii++) {
         if (cbConnectionArr[ii].isAlive())
            return cbConnectionArr[ii];
      }
      return null;
   }

   /** @return a currently alive callback connection or null */
   public final CallbackAddress getAliveCbAddress() {
      CbConnection con = getAliveCbConnection();
      return (con == null) ? null : con.getCbAddress();
   }

   /** @return a currently dead callback connection or null */
   public final CallbackAddress getDeadCbAddress() {
      CbConnection con = getDeadCbConnection();
      return (con == null) ? null : con.getCbAddress();
   }

   /** @return a dead callback connection or null */
   public final CbConnection getDeadCbConnection() {
      for (int ii=0; ii<cbConnectionArr.length; ii++) {
         if (cbConnectionArr[ii].isDead())
            return cbConnectionArr[ii];
      }
      return null;
   }

   public final void removeCbConnection(CbConnection cbCon) {
      synchronized (this) {
         boolean found = false;
         for (int ii=0; ii<cbConnectionArr.length; ii++) {
            if (cbConnectionArr[ii].equals(cbCon))
               found = true;
         }
         if (found) {
            CbConnection[] tmp = new CbConnection[cbConnectionArr.length-1];
            for (int ii=0, jj=0; ii<cbConnectionArr.length; ii++) {
               if (cbConnectionArr[ii].equals(cbCon))
                  continue;
               tmp[jj++] = cbConnectionArr[ii];
            }
            cbConnectionArr = tmp;
            if (log.TRACE) log.trace(ME, "Destroyed one callback connection, " + cbConnectionArr.length + " remain.");
         }
      }
      cbCon.shutdown();
      msgQueue.onExhaust(cbCon);
   }

   /**
    * Send the messages back to the client. 
    * If there are more fallback addresses, these will be used if the
    * first fails.
    * @return The returned string from the client, for oneway updates it is null
    */
   public String[] sendUpdate(MsgQueueEntry[] msg, int redeliver) throws XmlBlasterException
   {
      if (cbConnectionArr.length < 1)
         throw new XmlBlasterException("CallbackFailed", "Callback of " + msg.length + " messages '" + msg[0].getUniqueKey() +
            "' from [" + msg[0].getPublisherName() + "] failed, no callback connection is available");

      XmlBlasterException ex = null; // to remember exception
      String[] retArr = null;

         // Try to find a connection which delivers the message ...
         // PtP messages from the subject Queue are delivered to all reachable sessions of this user ...

      for (int ii=0; ii<cbConnectionArr.length; ii++) {
         if (log.TRACE) log.trace(ME, "Trying cb# " + ii + " state=" + cbConnectionArr[ii].getStateStr() + " ...");
         if (cbConnectionArr[ii].isAlive()) {
            try {
               retArr = cbConnectionArr[ii].sendUpdate(msg, redeliver);
               if (msgQueue.isSessionQueue()) {
                  if (ex != null) killDeadCons();
                  return retArr;
               }
            } catch(XmlBlasterException e) {
               ex = e;
               if (log.TRACE && ii<(cbConnectionArr.length-1)) log.trace(ME, "Callback failed, trying other addresses");
            }
         }
      }

      if (msgQueue.isSubjectQueue() && retArr != null) {
         if (ex != null) killDeadCons();
         return retArr; // not very nice coded, for subject queue we deliver to all available sessions of this subject, loosing returns from some sessions
      }

      if (ex == null)
         ex = new XmlBlasterException("CallbackFailed", "Callback of " + msg.length + " messages '" + msg[0].getUniqueKey() +
            "' to client [" + cbConnectionArr[0].getCbAddress().getSessionId() + "] from [" + msg[0].getPublisherName() + "] failed, no callback connection is alive");

      killDeadCons();

      throw ex;
   }

   private final void killDeadCons() {
      while (true) {
         CbConnection dead = null;
         for (int ii=0; ii<cbConnectionArr.length; ii++) {
            if (cbConnectionArr[ii].isDead()) {
               dead = cbConnectionArr[ii];
            }
         }
         if (dead == null)
            break;
         removeCbConnection(dead);
      }
   }

   /** @return Number of established callback connections */
   public final int getSize() {
      return cbConnectionArr.length;
   }

   /**
    * Stop all callback drivers of this client.
    */
   public final void shutdown() {
      if (log.CALL) log.call(ME, "Entering shutdown ...");
      for (int ii=0; ii<cbConnectionArr.length; ii++) {
         cbConnectionArr[ii].shutdown();
         cbConnectionArr[ii] = null;
      }
      cbConnectionArr = new CbConnection[0];
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of SessionInfo as a XML ASCII string
    */
   public final String toXml(String extraOffset) throws XmlBlasterException {
      StringBuffer sb = new StringBuffer(256);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<CbManager>");
      if (cbConnectionArr.length < 1)
         sb.append(offset).append("   <noCbConnection/>");
      else {
         for (int ii=0; ii<cbConnectionArr.length; ii++) {
            sb.append(offset).append("   <" + cbConnectionArr[ii].getCbDriver().getName() + " />");
         }
      }
      sb.append(offset).append("</CbManager>");

      return sb.toString();
   }
}

