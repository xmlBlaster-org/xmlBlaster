/*------------------------------------------------------------------------------
Name:      RamRecorder.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   RamRecorder for client messages
Version:   $Id$
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.recorder.ram;

import org.xmlBlaster.util.SimpleXbQueue;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.recorder.I_InvocationRecorder;
import org.xmlBlaster.util.qos.StatusQosData;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.protocol.I_XmlBlaster;
import org.xmlBlaster.util.def.MethodName;


/**
 * You can use this recorder to remember all invocations of the xmlBlaster CORBA methods.
 * <p />
 * It is based on a queue (FIFO).
 * Every method invocation is timestamped and wrapped into an InvocationContainer object,
 * and pushed into the queue.
 * <p />
 * Configuration:
 * <pre>
 * RecorderPlugin[RamRecorder][1.0]=org.xmlBlaster.util.recorder.ram.RamRecorder
 * </pre>
 * @author xmlBlaster@marcelruff.info
 * @see org.xmlBlaster.test.qos.TestFailSave
 * @see org.xmlBlaster.test.classtest.InvocationRecorderTest
 */
public class RamRecorder implements I_Plugin, I_InvocationRecorder//, I_CallbackRaw
//                    I_InvocationRecorder contains client.protocol.I_XmlBlaster and  I_CallbackRaw
{
   private String ME = "RamRecorder";
   private Global glob;
   private static Logger log = Logger.getLogger(RamRecorder.class.getName());

   /**
    * The queue to hold the method invocations
    * TODO: Allow persistence store e.g. via JDBC bridge into Orcale.
    */
   private SimpleXbQueue queue;

   /**
    * Callback which the client must implement.
    * The recorder calls these methods when doing a playback
    */
   private I_XmlBlaster serverCallback = null;
   //private I_CallbackRaw clientCallback = null;

   private final MsgUnit[] dummyMArr = new MsgUnit[0];
   private final PublishReturnQos[] dummyPubRetQosArr = new PublishReturnQos[0];
   private PublishReturnQos dummyPubRet;
   private SubscribeReturnQos dummySubRet;
   private final UnSubscribeReturnQos[] dummyUbSubRetQosArr = new UnSubscribeReturnQos[0];
   private final EraseReturnQos[] dummyEraseReturnQosArr = new EraseReturnQos[0];


   /** Empty constructor for plugin manager */
   public RamRecorder() {}
   
   /**
    * @param maxEntries The maximum number of invocations to store
    * @see org.xmlBlaster.util.recorder.I_InvocationRecorder#initialize
    */
   public void initialize(Global glob, String fn, long maxEntries, I_XmlBlaster serverCallback)
                             //I_CallbackRaw clientCallback)
   {
      this.glob = glob;

      StatusQosData statRetQos = new StatusQosData(glob, MethodName.UNKNOWN);
      statRetQos.setStateInfo(Constants.INFO_QUEUED);
      this.dummyPubRet = new PublishReturnQos(glob, statRetQos);
      StatusQosData subQos = new StatusQosData(glob, MethodName.SUBSCRIBE);
      subQos.setStateInfo(Constants.INFO_QUEUED);
      this.dummySubRet = new SubscribeReturnQos(glob, subQos);

      if (log.isLoggable(Level.FINER)) log.finer("Initializing new RamRecorder(" + maxEntries + ") ...");
      if (maxEntries >= Integer.MAX_VALUE) {
         log.warning("Stripping queue size to Integer.MAX_VALUE");
         maxEntries = Integer.MAX_VALUE;
      }
      this.queue = new SimpleXbQueue(ME, (int)maxEntries);
      this.serverCallback = serverCallback;
      //this.clientCallback = clientCallback;
      log.info("Invocation recorder is initialized to queue max=" + maxEntries + " tail back messages on failure");
   }

   /** Returns the name of the database file or null if RAM based */
   public String getFullFileName() {
      return null;
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
   }

   /**
    * Return plugin type for Plugin loader
    * @return "RamRecorder"
    */
   public String getType() {
      return "RamRecorder";
   }

   /**
    * Return plugin version for Plugin loader
    * @return "1.0"
    */
   public String getVersion() {
      return "1.0";
   }

   /**
    * @param ONOVERFLOW_DEADMESSAGE = "deadMessage",
    *        ONOVERFLOW_DISCARD = "discard", ONOVERFLOW_DISCARDOLDEST = "discardOldest",
    *        ONOVERFLOW_EXCEPTION = "exception"
    */
   public void setMode(String mode)
   {
      if (mode == null) return;

      if (mode.equals(Constants.ONOVERFLOW_DISCARDOLDEST))
         this.queue.setModeToDiscardOldest();
      else if (mode.equals(Constants.ONOVERFLOW_DISCARD))
         this.queue.setModeToDiscard();
      else if (mode.equals(Constants.ONOVERFLOW_EXCEPTION))
         log.fine("Setting onOverflow mode to exception"); // default
      else
         log.warning("Ignoring unknown onOverflow mode '" + mode + "', using default mode 'exception'."); // default
   }

   /**
    * Check if the recorder is filled up.
    * <p />
    * @return true space for at least on more entry
    *         false quota exceeded
    */
   public final boolean isFull() throws XmlBlasterException
   {
      return queue.isFull();
   }


   /**
    * How many objects are in the recorder.
    * <p />
    * @return number of objects
    */
   public final long getNumUnread()
   {
      return queue.size();
   }


   /**
    * Playback the stored messages, the are removed from the recorder after the callback.
    * @see I_InvocationRecorder#playback(long, long, double)
    */
   public void pullback(long startDate, long endDate, double motionFactor) throws XmlBlasterException
   {
      log.info("Invoking pullback(startDate=" + startDate + ", endDate=" + endDate + ", motionFactor=" + motionFactor + ") queue.size=" + queue.size());

      InvocationContainer cont = null;
      while(queue.size() > 0) { // find the start node ...
         cont = (InvocationContainer)queue.pull();
         if (startDate == 0L || cont.timestamp >= startDate)
            break;
      }
      if (cont == null) {
         log.warning("Sorry, no invocations found, queue is empty or your start date is to late");
         throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE, "RamRecorder.NoInvoc", "Sorry, no invocations found, queue is empty or your start date is to late");
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
                  Thread.sleep(originalElapsed - actualElapsed);
               } catch(InterruptedException e) {
                  log.warning("Thread sleep got interrupted, this invocation is not in sync");
               }
            }
            callback(cont);
         }

         cont = (InvocationContainer)queue.pull();
      }
   }

   /**
    * Playback the stored messages, the are removed from the recorder after the callback. 
    * <p />
    * The messages are retrieved with the given rate per second
    * <p />
    * NOTE: This is not implemented for this plugin!
    * @param msgPerSec 20. is 20 msg/sec, 0.1 is one message every 10 seconds
    */
   public void pullback(float msgPerSec) throws XmlBlasterException {
      log.warning("Sorry, pullback(msgPerSec) is not implemented, we switch to full speed mode");
      pullback(0L, 0L, 0.);
   }

   /**
    * Reset the queue, throw all entries to garbage
    */
   public void destroy()
   {
      while(queue.size() > 0)
         queue.pull();
   }

   public void shutdown() {
   }

   /**
    * How many messages are silently lost in 'discard' or 'discardOldest' mode?
    */
   public long getNumLost()
   {
      return queue.getNumLost();
   }


   /**
    * Playback the stored messages, without removing them form the recorder.
    * @see I_InvocationRecorder#playback(long, long, double)
    */
   public void playback(long startDate, long endDate, double motionFactor) throws XmlBlasterException
   {
      // !!! implement similar to pullback() but using the iterator to process the queue
      log.severe("Sorry, playback() is not implemented, use pullback() or implement it");
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "Sorry, only pullback is implemented");
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
         else if (cont.method.equals("publishOneway")) {
            serverCallback.publishOneway(cont.msgUnitArr);
            return;
         }
         else if (cont.method.equals("erase")) {
            serverCallback.erase(cont.xmlKey, cont.xmlQos);
            return;
         }
      }

      /*
      if (clientCallback != null) {
         // This should be faster then reflection
         if (cont.method.equals("update")) {
            clientCallback.update(cont.cbSessionId, cont.msgUnitArr);
            return;
         }
         else if (cont.method.equals("updateOneway")) {
            clientCallback.updateOneway(cont.cbSessionId, cont.msgUnitArr);
            return;
         }
      }
      */

      log.severe("Internal error: Method '" + cont.method + "' is unknown");
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "Internal error: Method '" + cont.method + "' is unknown");
   }


   /**
    * @return dummy to match I_XmlBlaster interface
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    * @exception XmlBlasterException if queue is full with id="<driverName>.MaxSize"
    */
   public SubscribeReturnQos subscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      InvocationContainer cont = new InvocationContainer();
      cont.method = "subscribe";
      cont.xmlKey = xmlKey_literal;
      cont.xmlQos = qos_literal;
      queue.push(cont);
      return dummySubRet;
   }

   /**
    * For I_XmlBlaster interface
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    * @exception XmlBlasterException if queue is full
    */
   public UnSubscribeReturnQos[] unSubscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      InvocationContainer cont = new InvocationContainer();
      cont.method = "unSubscribe";
      cont.xmlKey = xmlKey_literal;
      cont.xmlQos = qos_literal;
      queue.push(cont);
      return dummyUbSubRetQosArr;
   }


   /**
    * @return dummy to match I_XmlBlaster interface
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    * @exception XmlBlasterException if queue is full
    */
   public PublishReturnQos publish(MsgUnit msgUnit) throws XmlBlasterException
   {
      InvocationContainer cont = new InvocationContainer();
      cont.method = "publish";
      cont.msgUnit = msgUnit;
      cont.xmlQos = msgUnit.getQos();
      queue.push(cont);
      return dummyPubRet;
   }


   public void publishOneway(MsgUnit [] msgUnitArr) {
      InvocationContainer cont = new InvocationContainer();
      cont.method = "publishOneway";
      cont.msgUnitArr = msgUnitArr;
      try {
         queue.push(cont);
      }
      catch (XmlBlasterException e) {
         log.severe(e.getMessage());
      }
   }


   /**
    * @return dummy to match I_XmlBlaster interface
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    * @exception XmlBlasterException if queue is full
    */
   public PublishReturnQos[] publishArr(MsgUnit [] msgUnitArr) throws XmlBlasterException
   {
      InvocationContainer cont = new InvocationContainer();
      cont.method = "publishArr";
      cont.msgUnitArr = msgUnitArr;
      queue.push(cont);
      return dummyPubRetQosArr;
   }


   /**
    * @return dummy to match I_XmlBlaster interface
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    * @exception XmlBlasterException if queue is full
    */
   public EraseReturnQos[] erase(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      InvocationContainer cont = new InvocationContainer();
      cont.method = "erase";
      cont.xmlKey = xmlKey_literal;
      cont.xmlQos = qos_literal;
      queue.push(cont);
      return dummyEraseReturnQosArr;
   }


   /**
    * @return dummy to match I_XmlBlaster interface
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    * @exception XmlBlasterException if queue is full
    */
   public MsgUnit[] get(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      InvocationContainer cont = new InvocationContainer();
      cont.method = "get";
      cont.xmlKey = xmlKey_literal;
      cont.xmlQos = qos_literal;
      queue.push(cont);
      return dummyMArr;
   }


   /**
    * For I_XmlBlaster interface
    * @return false No connection to server, off line recording messages.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
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
   public String[] update(String cbSessionId, MsgUnit [] msgUnitArr) throws XmlBlasterException
   {
      InvocationContainer cont = new InvocationContainer();
      cont.method = "update";
      cont.cbSessionId = cbSessionId;
      cont.msgUnitArr = msgUnitArr;
      queue.push(cont);
      String[] ret=new String[msgUnitArr.length];
      for (int i=0; i<ret.length; i++) ret[i] = "";
      return ret;
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public void updateOneway(String cbSessionId, MsgUnit [] msgUnitArr)
   {
      InvocationContainer cont = new InvocationContainer();
      cont.method = "updateOneway";
      cont.cbSessionId = cbSessionId;
      cont.msgUnitArr = msgUnitArr;
      try {
         queue.push(cont);
      } catch (XmlBlasterException e) {
         log.severe("Can't push updateOneway(): " + e.getMessage());
      }
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
      MsgUnit msgUnit;
      MsgUnit[] msgUnitArr;

      InvocationContainer() {
         timestamp = System.currentTimeMillis();
      }
   }
}
