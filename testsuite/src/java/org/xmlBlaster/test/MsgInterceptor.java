/*------------------------------------------------------------------------------
Name:      MsgInterceptor.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test;

import java.util.logging.Logger;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.UpdateReturnQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;

import java.lang.InterruptedException;
import java.util.Vector;

import junit.framework.Assert;
import java.lang.ref.WeakReference;

/**
 * Intercepts incoming message in update() and collects them in a Vector for nice handling. 
 */
public class MsgInterceptor extends Assert implements I_Callback 
{
   private final WeakReference weakglob;
   private final WeakReference weaklog;
   private I_Callback testsuite = null;
   //private Msgs msgs = null;
   private int verbosity = 2;
   private boolean countErased = false;

   /**
    * @param testsuite If != null your update() variant will be called as well
    */
   public MsgInterceptor(Global glob, Logger log, I_Callback testsuite) {
      this.weakglob = new WeakReference(glob);
      this.weaklog = new WeakReference(log);
      this.testsuite = testsuite;
      //this.msgs = new Msgs();
   }

   public final Global getGlobal() {
      return (Global)this.weakglob.get();
   }

   public final Logger getLog() {
      return (Logger)this.weaklog.get();
   }

   public void setLogPrefix(String prefix) {
   }

   /**
    * 0: no logging
    * 1: simple logging
    * 2: dump messages on arrival
    */
   public void setVerbosity(int val) {
      this.verbosity = val;
   }

   /*
    * Contains all update() messages in a Vector, but not erase events.
   public Msgs getMsgs() {
      return this.msgs;
   }
    */

   /**
    * @param countErased Set to true to count the erased notifications as well
    */
   public void countErased(boolean countErased) {
      this.countErased = countErased;
   }

   /**
    * This is the callback method (I_Callback) invoked from xmlBlaster
    * It directly calls the update method from the testsuite (delegation)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      String contentStr = new String(content);
      
      if (this.verbosity == 1) {
         String cont = (contentStr.length() > 10) ? (contentStr.substring(0,10)+"...") : contentStr;
         getLog().info("Receiving update of a message oid=" + updateKey.getOid() +
                   " priority=" + updateQos.getPriority() +
                   " state=" + updateQos.getState() +
                   " content=" + cont);
      }
      else if (this.verbosity == 2) {
         getLog().info("Receiving update #" + (count()+1) + " of a message cbSessionId=" + cbSessionId +
                      updateKey.toXml() + "\n" + new String(content) + updateQos.toXml());
      }

      if (this.countErased || !updateQos.isErased()) {
         add(new Msg(cbSessionId, updateKey, content, updateQos));
      }
      if (testsuite != null)
         return testsuite.update(cbSessionId, updateKey, content, updateQos);
      else {
         UpdateReturnQos qos = new UpdateReturnQos(getGlobal());
         return qos.toXml();
      }
   }

   /**
    * @see #waitOnUpdate(long, String, String, int)
    */
   public int waitOnUpdate(final long timeout, int countExpected) {
      return waitOnUpdate(timeout, null, null, countExpected);
   }

   /**
    * Waits until the given number of messages arrived,
    * the messages must match the given oid and state. 
    * It is not checked if more messages would arrive as we return after
    * countExpected are here.
    * <p>
    * ERASE notifies are not returned
    * </p>
    * <p>
    * This method does not assert() it return the number of messages arrived
    * which you can use to assert yourself.
    * </p>
    * @param timeout in milliseconds
    * @param oid The expected message oid, if null the oid is not checked (all oids are OK)
    * @param state The expected state, if null the state is not checked (all states are OK)
    *
    * @return Number of messages arrived
    */
   public int waitOnUpdate(final long timeout, String oid, String state, int countExpected) {
      long pollingInterval = 50L;  // check every 0.05 seconds
      if (timeout < 50)  pollingInterval = timeout / 10L;
      long sum = 0L;
      int countArrived = 0;
      while (true) {
         countArrived = getMsgs(oid, state).length;
         if (countArrived >= countExpected)
            return countArrived; // OK, no timeout
         try {
            Thread.sleep(pollingInterval);
         }
         catch( InterruptedException i)
         {}

         sum += pollingInterval;
         if (sum > timeout) {
            getLog().severe("timeout=" + timeout + " occurred for " + oid + " state=" + state + " countExpected=" + countExpected + " countArrived=" + countArrived);
            return countArrived; // Timeout occurred
         }
      }
   }

   /**
    * Sleeps until timeout and returns the arrived messages. 
    * <p>
    * ERASE notifies are not returned
    * </p>
    * @see #waitOnUpdate(long, String, String)
    */
   public int waitOnUpdate(final long timeout) {
      return waitOnUpdate(timeout, null, null);
   }

   /**
    * Sleeps until timeout and returns the number of arrived messages filtered by oid and state. 
    * <p>
    * ERASE notifies are not returned
    * </p>
    * @param timeout in milliseconds
    * @param oid The expected message oid, if null the oid is not checked (all oids are OK)
    * @param state The expected state, if null the state is not checked (all states are OK)
    *
    * @return Number of messages arrived
    */
   public int waitOnUpdate(final long timeout, String oid, String state) {
      try {
         Thread.sleep(timeout);
      }
      catch( InterruptedException i)
      {}
      return getMsgs(oid, state).length;
   }


   // Holding all messages
   private Vector updateVec = new Vector();
   
   public void add(Msg msg) {
      this.updateVec.addElement(msg);
  }
   
   public void remove(Msg msg) {
      this.updateVec.removeElement(msg);
   }
   
   public void clear() { 
      this.updateVec.clear();
      this.countErased = false;
   }

   /**
    * Access the updated message filtered by the given oid and state. 
    * @param oid if null the oid is not checked
    * @param state if null the state is not checked
    */
   public Msg[] getMsgs(String oid, String state) {
      Vector ret = new Vector();
      for (int i=0; i<this.updateVec.size(); i++) {
         Msg msg = (Msg)this.updateVec.elementAt(i);
         //System.out.println("MsgInterceptor: Checking msg oid='" + msg.getOid() + "' with state='" + msg.getState() + "' against '" + oid + "' '" + state + "'");
         if (
             (oid == null || oid.equals(msg.getOid())) &&
             (state == null || state.equals(msg.getState()))
            ) {
            ret.addElement(msg);
            //System.out.println("MsgInterceptor: FOUND: Checking msg oid='" + msg.getOid() + "' with state='" + msg.getState() + "' against '" + oid + "' '" + state + "'");
         }
      }
      return  (Msg[])ret.toArray(new Msg[ret.size()]);
   }

   public Msg[] getMsgs() {
      return getMsgs(null, null);
   }

   /**
    * Access the updated message filtered by the given oid and state. 
    * @return null or the message
    * @exception If more than one message is available
    */
   public Msg getMsg(String oid, String state) throws XmlBlasterException {
      Msg[] msgs = getMsgs(oid, state);
      //System.out.println("MsgInterceptor: FOUND " + msgs.length + " entries for msg oid='" + oid + "' with state='" + state);
      if (msgs.length > 1)
         throw new XmlBlasterException("Msgs", "update(oid=" + oid + ", state=" + state + ") " + msgs.length + " arrived instead of zero or one");
      if (msgs.length == 0)
         return null;
      return msgs[0];
   }

   public int count() {
      return this.updateVec.size();
   }

   /**
    * Compares all messages given by parameter 'expectedArr' and compare
    * them with the received ones. On failure a junit - assert() is thrown.
    * <p>
    * The correct sequence and the message data is checked.
    * </p>
    * @param expectedArr The published messages which we expect here as updates
    * @param secretCbSessionId If not null it is checked as well
    */
   public void compareToReceived(MsgUnit[] expectedArr, String secretCbSessionId) {
      assertEquals("We have received " + count() + " messages only", expectedArr.length, count());
      
      for(int i=0; i<expectedArr.length; i++) {
         MsgUnit expected = expectedArr[i];
         Msg msg = (Msg)this.updateVec.elementAt(i);
         if (secretCbSessionId != null) {
            assertEquals("The secretCbSessionId is wrong", secretCbSessionId, msg.getCbSessionId());
         }
         msg.compareMsg(expected);
      }
   }

   /**
    * Compares all messages given by parameter 'expectedArr' and compare
    * them with the received ones. On failure a junit - assert() is thrown.
    * <p>
    * Especially the sequence and the rcvTimestamp is checked.
    * </p>
    * @param expectedArr The published messages which we expect here as updates
    */
   public void compareToReceived(PublishReturnQos[] expectedArr) {
      assertEquals("We have received " + count() + " messages only", expectedArr.length, count());

      for(int i=0; i<expectedArr.length; i++) {
         Msg msg = (Msg)this.updateVec.elementAt(i);
         msg.compareMsg(expectedArr[i]);
      }
   }
} // MsgInterceptor
