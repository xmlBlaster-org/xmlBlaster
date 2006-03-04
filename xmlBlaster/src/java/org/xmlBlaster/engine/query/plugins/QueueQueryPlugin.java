/*------------------------------------------------------------------------------
Name:      QueueQueryPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.engine.query.plugins;

import java.util.ArrayList;
import java.util.Map;

import org.jutils.log.LogChannel;
import org.xmlBlaster.engine.query.I_Query;
import org.xmlBlaster.engine.queuemsg.MsgQueueUpdateEntry;
import org.xmlBlaster.engine.queuemsg.ReferenceEntry;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.StringPairTokenizer;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.dispatch.DispatchManager;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.I_QueueSizeListener;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;

/**
 * QueueQueryPlugin
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.queryspec.html">The engine.qos.queryspec requirement</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.queryspec.QueueQuery.html">The engine.qos.queryspec.QueueQuery requirement</a>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class QueueQueryPlugin implements I_Query, I_QueueSizeListener {

   private final static String ME = "QueueQueryPlugin";
   private Global global;
   private LogChannel log;
   private int maxEntries;
   private long maxSize;
   
   public QueueQueryPlugin(Global global) {
      this.global = global;
      this.log = global.getLog("query");
   }


   /**
    * If no restriction is given, i.e. if both maxEntries and maxBytes is negative,
    * then it will wait.
    * 
    * @param maxEntries the maximum number of entries for which to wait. If negative
    *        no restriction is given, so the the other limitations count.
    * @param maxBytes the maximum number of bytes which need to be in the queue before
    *        it will continue. If negative no restriction is given.
    * @return true if it has to wait, false if there are already sufficently entries 
    *         in the queue.
    */
   private final boolean checkIfNeedsWaiting(int entriesInQueue, long bytesInQueue, int maxEntries, long maxBytes) {
      if (maxEntries > 0) {
         if (entriesInQueue >= maxEntries) return false;
      }
      if (maxBytes > 0) {
         if (bytesInQueue >= maxBytes) return false;
      }
      return true;
   }
   
   /**
    * The query to the queue. The parameters specifying which kind of query it is
    * are specified in the qos, and more precisely in the QuerySpecQos.
    * @param source must be an I_Queue implementation (can not be null).
    * @param query must not be null, e.g.
    * "maxEntries=3&maxSize=1000&consumable=true&waitingDelay=1000"      
    */
   public MsgUnit[] query(Object source, String query) throws XmlBlasterException {
      //if (this.log.CALL) this.log.call(ME, "query for '" + keyData.getOid() + "'");
      if (source == null)
         throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME + ".query", "the source on which do the query is null");
      if (! (source instanceof I_Queue) )
         throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME + ".query", "wrong type of source for query. Expected an 'I_Queue' implementation but was '" + source.getClass().getName() + "'");
      if (query == null)
         throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME + ".query", "the query string is null");
         
      I_Queue queue = (I_Queue)source;         

      this.maxEntries = 1;
      this.maxSize = -1L;
      boolean consumable = false;
      long waitingDelay = 0L; // no wait is default
      
      /*
      QuerySpecQos[] querySpecs = qosData.getQuerySpecArr();
      QuerySpecQos querySpec = null;
      if (querySpecs != null) {
         for (int i=0; i < querySpecs.length; i++) {
            if (querySpecs[i].getType().equals("QueueQuery")) {
               querySpec = querySpecs[i];
               break;
            }
         }
      }
      */
      
      // get the query properties
      //if (querySpec.getQuery() != null) query = querySpec.getQuery().getQuery();
      // "maxEntries=3&maxSize=1000&consumable=true&waitingDelay=1000"      
      Map props = StringPairTokenizer.parseToStringClientPropertyPairs(query, "&", "=");
      ClientProperty prop = (ClientProperty)props.get("maxEntries");
      if (prop != null) this.maxEntries = prop.getIntValue();
      prop = (ClientProperty)props.get("maxSize");
      if (prop != null) this.maxSize = prop.getLongValue();
      if (this.maxSize > -1L)
         throw new XmlBlasterException(this.global, ErrorCode.USER_ILLEGALARGUMENT, ME + ".query: specification of maxSize is not implemented, please use the default value -1 or leave it untouched"); 
      prop = (ClientProperty)props.get("consumable");
      if (prop != null) consumable = prop.getBooleanValue();
      prop = (ClientProperty)props.get("waitingDelay");
      if (prop != null) waitingDelay = prop.getLongValue();

      if (this.log.TRACE) 
      this.log.trace(ME, "query: waitingDelay='" + waitingDelay + "' consumable='" + consumable + "' maxEntries='" + this.maxEntries + "' maxSize='" + this.maxSize + "'");
      
      if (waitingDelay != 0L) {
         if (this.log.TRACE) this.log.trace(ME, "query: waiting delay is " + waitingDelay);
         if (maxEntries < 1 && maxSize < 1L && waitingDelay < 0L)
            throw new XmlBlasterException(this.global, ErrorCode.USER_ILLEGALARGUMENT, ME + ".query: if you specify a blocking get you must also specify a maximum size or maximum number of entries to retreive, otherwise specify non-blocking by setting 'waitingDelay' to zero");
         if (checkIfNeedsWaiting((int)queue.getNumOfEntries(), queue.getNumOfBytes(), maxEntries, maxSize)) {
            if (this.log.TRACE) this.log.trace(ME, "query: going to wait due to first check");
            synchronized(this) {
               try {
                  queue.addQueueSizeListener(this);
                  if (checkIfNeedsWaiting((int)queue.getNumOfEntries(), queue.getNumOfBytes(), maxEntries, maxSize)) {
                     if (this.log.TRACE) this.log.trace(ME, "query: going to wait due to second check (inside sync now)");
                     try {
                        if (waitingDelay < 0L) this.wait();
                        else this.wait(waitingDelay);
                        if (this.log.TRACE) this.log.trace(ME, "did not wake up after waiting");
                     }
                     catch (InterruptedException ex) {
                        if (this.log.TRACE) this.log.trace(ME, "just waked up after waiting for incoming entries");
                     }
                  }
               }
               finally {
                  try {
                     queue.removeQueueSizeListener(this);
                     if (this.log.TRACE) this.log.trace(ME, "query: removed myself as a QueueSizeListener");
                  }
                  catch (Throwable ex) {
                     if (this.log.TRACE) this.log.trace(ME, "query: exception occurred when removing the QueueSizeListener from the queue");
                  }
               }
            }
         }
      }
      
      ArrayList list = queue.peek(maxEntries, maxSize);
      ArrayList entryListChecked = DispatchManager.prepareMsgsFromQueue(ME, this.log, queue, list);
      
      MsgQueueEntry[] entries = (MsgQueueEntry[])entryListChecked.toArray(new MsgQueueEntry[entryListChecked.size()]);
      
      ArrayList ret = new ArrayList(entries.length);
      for (int i=0; i < entries.length; i++) {
         // TODO: REQ engine.qos.update.queue states that the queue size is passed and not the curr msgArr.length
         ReferenceEntry entry = (ReferenceEntry)entries[i];
         MsgUnit mu = entry.getMsgUnitOrNull();
         if (mu == null)
            continue;
         MsgQosData msgQosData = (MsgQosData)mu.getQosData().clone();
         msgQosData.setTopicProperty(null);
         if (entry instanceof MsgQueueUpdateEntry) {
            MsgQueueUpdateEntry updateEntry = (MsgQueueUpdateEntry)entry;
            msgQosData.setState(updateEntry.getState());
            msgQosData.setSubscriptionId(updateEntry.getSubscriptionId());
         }
         msgQosData.setQueueIndex(i);
         msgQosData.setQueueSize(entries.length);
         if (msgQosData.getNumRouteNodes() == 1) {
            msgQosData.clearRoutes();
         }
         
         ret.add(new MsgUnit(mu, null, null, msgQosData));
         // ret[i] = new MsgUnitRaw(mu, mu.getKeyData().toXml(), mu.getContent(), mu.getQosData().toXml());
      }
      if (consumable) queue.removeRandom(entries);
      return (MsgUnit[])ret.toArray(new MsgUnit[ret.size()]);
   }

   public void changed(I_Queue queue, long numEntries, long numBytes) {
      if (this.log.CALL) this.log.call(ME, "changed numEntries='" + numEntries + "' numBytes='" + numBytes + "'");
      if (!checkIfNeedsWaiting((int)numEntries, numBytes, this.maxEntries, this.maxSize)) {
         if (this.log.TRACE) this.log.trace(ME, "changed going to notify");
         synchronized(this) {
            this.notify();
         }
      }
   }
   
}
