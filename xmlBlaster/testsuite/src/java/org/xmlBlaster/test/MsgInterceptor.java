/*------------------------------------------------------------------------------
Name:      MsgInterceptor.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test;

import org.jutils.log.LogChannel;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.UpdateReturnQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;

import java.lang.InterruptedException;

/**
 * Intercepts incoming message in update() and collects them in a Vector for nice handling. 
 */
public class MsgInterceptor implements I_Callback 
{
   private static String ME = "Testsuite.MsgInterceptor";
   private final Global glob;
   private final LogChannel log;
   private I_Callback testsuite = null;
   private Msgs msgs = null;

   /**
    * @param testsuite If != null your update() variant will be called as well
    */
   public MsgInterceptor(Global glob, LogChannel log, I_Callback testsuite) throws XmlBlasterException {
      this.glob = glob;
      this.log = log;
      this.testsuite = testsuite;
      this.msgs = new Msgs();
   }

   /**
    * Contains all update() messages in a Vector, but not erase events.
    */
   public Msgs getMsgs() {
      return this.msgs;
   }

   /**
    * This is the callback method (I_Callback) invoked from xmlBlaster
    * It directly calls the update method from the testsuite (delegation)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      String contentStr = new String(content);
      String cont = (contentStr.length() > 10) ? (contentStr.substring(0,10)+"...") : contentStr;
      log.info(ME, "Receiving update of a message oid=" + updateKey.getOid() +
                   " priority=" + updateQos.getPriority() +
                   " state=" + updateQos.getState() +
                   " content=" + cont);
      log.info(ME, "Receiving update of a message " + updateQos.toXml());
      if (!updateQos.isErased()) {
         msgs.add(new Msg(cbSessionId, updateKey, content, updateQos));
      }
      if (testsuite != null)
         return testsuite.update(cbSessionId, updateKey, content, updateQos);
      else {
         UpdateReturnQos qos = new UpdateReturnQos(glob);
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
         countArrived = msgs.getMsgs(oid, state).length;
         if (countArrived >= countExpected)
            return countArrived; // OK, no timeout
         try {
            Thread.currentThread().sleep(pollingInterval);
         }
         catch( InterruptedException i)
         {}

         sum += pollingInterval;
         if (sum > timeout) {
            log.error(ME, "timeout=" + timeout + " occurred for " + oid + " state=" + state + " countExpected=" + countExpected + " countArrived=" + countArrived);
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
         Thread.currentThread().sleep(timeout);
      }
      catch( InterruptedException i)
      {}
      return msgs.getMsgs(oid, state).length;
   }
} // MsgInterceptor
