/*------------------------------------------------------------------------------
Name:      InvocationRecorder.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   InvocationRecorder for client messages
Version:   $Id: InvocationRecorder.java,v 1.13 2002/05/19 12:55:56 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.jutils.collection.Queue;
import org.jutils.JUtilsException;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.client.I_CallbackRaw;

import java.util.*;


/**
 * You can use this recorder to remember all invocations of the xmlBlaster CORBA methods.
 * <p />
 * It is based on a queue (FIFO).
 * Every method invocation is timestamped and wrapped into an InvocationContainer object,
 * and pushed into the queue.
 *
 * @version $Revision: 1.13 $
 * @author $Author: ruff $
 */
public class InvocationRecorder implements I_InvocationRecorder
{
   private String ME = "InvocationRecorder";

   /**
    * The queue to hold the method invocations
    * TODO: Allow persistence store e.g. via JDBC bridge into Orcale.
    */
   private Queue queue;

   /**
    * Callback which the client must implement.
    * The recorder calls these methods when doing a playback
    */
   private I_InvocationRecorder serverCallback = null;
   private I_CallbackRaw clientCallback = null;

   private MessageUnit[] dummyMArr = new MessageUnit[0];
   private String[] dummySArr = new String[0];
   private String dummyS = "";


   /**
    * @param maxEntries The maximum number of invocations to store
    * @param serverCallback You need to implement I_InvocationRecorder to receive the invocations on playback
    *                       null if you are not interested in those
    * @param clientCallback You need to implement I_CallbackRaw to receive the invocations on playback
    *                       null if you are not interested in those
    */
   public InvocationRecorder(int maxEntries, I_InvocationRecorder serverCallback,
                             I_CallbackRaw clientCallback)
   {
      init(maxEntries, serverCallback, clientCallback);
   }


   /**
    * @param maxEntries The maximum number of invocations to store
    */
   private void init(int maxEntries, I_InvocationRecorder serverCallback,
                              I_CallbackRaw clientCallback)
   {
      if (Log.CALL) Log.call(ME, "Creating new InvocationRecorder(" + maxEntries + ") ...");
      this.queue = new Queue(ME, maxEntries);
      this.serverCallback = serverCallback;
      this.clientCallback = clientCallback;
   }


   /**
    * Check if the recorder is filled up.
    * <p />
    * @return true space for at least on more entry
    *         false quota exceeded
    */
   public final boolean isFull() throws XmlBlasterException
   {
      try {
         return queue.isFull();
      }
      catch (JUtilsException e) {
         throw new XmlBlasterException(e);
      }
   }


   /**
    * How many objects are in the recorder.
    * <p />
    * @return number of objects
    */
   public final int size()
   {
      return queue.size();
   }


   /**
    * Playback the stored messages, the are removed from the recorder after the callback.
    * <p />
    * Every message is chronologically sent through the interface to the client.
    * @param startDate Start date for playback, 0 means from the very start
    * @param endDate End date to stop playback, pass 0 to go to the very end
    * @param motionFactor for fast motion choose for example 4.0
    *        so four reals seconds are elapsing in one second.<br />
    *        For slow motion choose for example 0.5
    *        0. does everything instantly.
    */
   public void pullback(long startDate, long endDate, double motionFactor) throws XmlBlasterException
   {
      Log.info(ME, "Invoking pullback(startDate=" + startDate + ", endDate=" + endDate + ", motionFactor=" + motionFactor + ")");

      InvocationContainer cont = null;
      while(queue.size() > 0) { // find the start node ...
         cont = (InvocationContainer)queue.pull();
         if (startDate == 0L || cont.timestamp >= startDate)
            break;
      }
      if (cont == null) {
         Log.warn(ME + ".NoInvoc", "Sorry, no invocations found, queue is empty or your start date is to late");
         throw new XmlBlasterException(ME + ".NoInvoc", "Sorry, no invocations found, queue is empty or your start date is to late");
      }

      long startTime = cont.timestamp;
      long playbackStart = System.currentTimeMillis();

      while(cont != null) {
         if (endDate != 0 && cont.timestamp > endDate) // break if the end date is reached
            break;

         if (motionFactor == 0.)
            callback(cont);
         else {
            long actualElapsed = (long)((System.currentTimeMillis() - playbackStart) * motionFactor);
            long originalElapsed = cont.timestamp - startTime;
            if (originalElapsed > actualElapsed) {
               try {
                  Thread.currentThread().sleep(originalElapsed - actualElapsed);
               } catch(InterruptedException e) {
                  Log.warn(ME, "Thread sleep got interrupted, this invocation is not in sync");
               }
            }
            callback(cont);
         }

         cont = (InvocationContainer)queue.pull();
      }
   }


   /**
    * Reset the queue, throw all entries to garbage
    */
   public void reset()
   {
      while(queue.size() > 0)
         queue.pull();
   }


   /**
    * Playback the stored messages, without removing them form the recorder.
    * This you can use multiple times again.
    * <p />
    * NOT YET IMPLEMENTED
    * @param startDate Start date for playback, 0 means from the very start
    * @param endDate End date to stop playback, pass 0 to go to the very end
    * @param motionFactor for fast motion choose for example 4.0
    *        so four reals seconds are elapsing in one second.<br />
    *        For slow motion choose for example 0.5
    */
   public void playback(long startDate, long endDate, double motionFactor) throws XmlBlasterException
   {
      // !!! implement similar to pullback() but using the iterator to process the queue
      Log.error(ME + ".NoImpl", "Sorry, only pullback is implemented");
      throw new XmlBlasterException(ME + ".NoImpl", "Sorry, only pullback is implemented");
   }


   /**
    * Call back the client through the interfaces
    */
   private void callback(InvocationContainer cont) throws XmlBlasterException
   {
      if (serverCallback != null) {
         // This should be faster then reflection
         if (cont.method.equals("publish")) {
            serverCallback.publish(cont.msgUnit);
            return;
         }
         else if (cont.method.equals("get")) {
            serverCallback.get(cont.xmlKey, cont.xmlQos);
            return;
         }
         else if (cont.method.equals("subscribe")) {
            serverCallback.subscribe(cont.xmlKey, cont.xmlQos);
            return;
         }
         else if (cont.method.equals("unSubscribe")) {
            serverCallback.unSubscribe(cont.xmlKey, cont.xmlQos);
            return;
         }
         else if (cont.method.equals("publishArr")) {
            serverCallback.publishArr(cont.msgUnitArr);
            return;
         }
         else if (cont.method.equals("erase")) {
            serverCallback.erase(cont.xmlKey, cont.xmlQos);
            return;
         }
      }

      if (clientCallback != null) {
         // This should be faster then reflection
         if (cont.method.equals("update")) {
            clientCallback.update(cont.cbSessionId, cont.msgUnitArr);
            return;
         }
      }

      Log.error(ME, "Internal error: Method '" + cont.method + "' is unknown");
      throw new XmlBlasterException(ME, "Internal error: Method '" + cont.method + "' is unknown");
   }


   /**
    * @return dummy to match I_InvocationRecorder interface
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>" target="others">CORBA xmlBlaster.idl</a>
    */
   public String subscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      InvocationContainer cont = new InvocationContainer();
      cont.method = "subscribe";
      cont.xmlKey = xmlKey_literal;
      cont.xmlQos = qos_literal;
      try {
         queue.push(cont);
      }
      catch (JUtilsException e) {
         throw new XmlBlasterException(e);
      }
      return dummyS;
   }


   /**
    * For I_InvocationRecorder interface
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>" target="others">CORBA xmlBlaster.idl</a>
    */
   public void unSubscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      InvocationContainer cont = new InvocationContainer();
      cont.method = "unSubscribe";
      cont.xmlKey = xmlKey_literal;
      cont.xmlQos = qos_literal;
      try {
         queue.push(cont);
      }
      catch (JUtilsException e) {
         throw new XmlBlasterException(e);
      }
   }


   /**
    * @return dummy to match I_InvocationRecorder interface
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>" target="others">CORBA xmlBlaster.idl</a>
    */
   public String publish(MessageUnit msgUnit) throws XmlBlasterException
   {
      InvocationContainer cont = new InvocationContainer();
      cont.method = "publish";
      cont.msgUnit = msgUnit;
      cont.xmlQos = msgUnit.qos;
      try {
         queue.push(cont);
      }
      catch (JUtilsException e) {
         throw new XmlBlasterException(e);
      }
      return dummyS;
   }


   /**
    * @return dummy to match I_InvocationRecorder interface
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>" target="others">CORBA xmlBlaster.idl</a>
    */
   public String[] publishArr(MessageUnit [] msgUnitArr) throws XmlBlasterException
   {
      InvocationContainer cont = new InvocationContainer();
      cont.method = "publishArr";
      cont.msgUnitArr = msgUnitArr;
      try {
         queue.push(cont);
      }
      catch (JUtilsException e) {
         throw new XmlBlasterException(e);
      }
      return dummySArr;
   }


   /**
    * @return dummy to match I_InvocationRecorder interface
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>" target="others">CORBA xmlBlaster.idl</a>
    */
   public String[] erase(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      InvocationContainer cont = new InvocationContainer();
      cont.method = "erase";
      cont.xmlKey = xmlKey_literal;
      cont.xmlQos = qos_literal;
      try {
         queue.push(cont);
      }
      catch (JUtilsException e) {
         throw new XmlBlasterException(e);
      }
      return dummySArr;
   }


   /**
    * @return dummy to match I_InvocationRecorder interface
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>" target="others">CORBA xmlBlaster.idl</a>
    */
   public MessageUnit[] get(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      InvocationContainer cont = new InvocationContainer();
      cont.method = "get";
      cont.xmlKey = xmlKey_literal;
      cont.xmlQos = qos_literal;
      try {
         queue.push(cont);
      }
      catch (JUtilsException e) {
         throw new XmlBlasterException(e);
      }
      return dummyMArr;
   }


   /**
    * For I_InvocationRecorder interface
    * @return false No connection to server, off line recording messages.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>" target="others">CORBA xmlBlaster.idl</a>
    */
   public boolean ping()
   {
      return false;
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, MessageUnit [] msgUnitArr)
   {
      InvocationContainer cont = new InvocationContainer();
      cont.method = "update";
      cont.cbSessionId = cbSessionId;
      cont.msgUnitArr = msgUnitArr;
      try {
         queue.push(cont);
      } catch (JUtilsException e) {
         Log.error(ME, "Can't push update(): " + e.reason);
      }
      return "";
   }

   /**
    * This holds all the necessary info about one method invocation.
    */
   private class InvocationContainer
   {
      // Only parts of the attributes are needed, depending on the method (Not nice but practical :-)

      long timestamp;
      /** publish/subscribe/get etc. */
      String method;
      String cbSessionId;
      String xmlKey;
      String xmlQos;
      MessageUnit msgUnit;
      MessageUnit[] msgUnitArr;

      InvocationContainer() {
         timestamp = System.currentTimeMillis();
      }
   }


   /**
    * Only for testing
    *    java org.xmlBlaster.util.InvocationRecorder
    */
   public static void main(String args[]) throws Exception
   {
      String me = "InvocationRecorder-Tester";
      int size = 3;
      // InvocationRecorder queue = new InvocationRecorder(size);
   }
}
