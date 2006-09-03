/*------------------------------------------------------------------------------
Name:      QueueQueryPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.engine.query.plugins;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import java.util.logging.Logger;
import java.util.logging.Level;
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

import edu.emory.mathcs.backport.java.util.concurrent.CountDownLatch;
import edu.emory.mathcs.backport.java.util.concurrent.TimeUnit;

/**
 * Each TopicHandler/SessionInfo or SubjectInfo instance creates its own instance of this plugin. 
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.queryspec.html">The engine.qos.queryspec requirement</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.queryspec.QueueQuery.html">The engine.qos.queryspec.QueueQuery requirement</a>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class QueueQueryPlugin implements I_Query, I_QueueSizeListener {
   
   /** Helper container */
   class WaitingQuery {
      /** 
       * The maximum number of entries for which to wait. If negative
       * no restriction is given, so the the other limitations count.
       */
      int maxEntries;
      /**
       * The maximum number of bytes which need to be in the queue before
       * it will continue. If negative no restriction is given.
       */
      long maxSize;
      CountDownLatch startSignal = new CountDownLatch(1);
      
      public WaitingQuery(int maxEntries, long maxSize) {
         super();
         this.maxEntries = maxEntries;
         this.maxSize = maxSize;
      }
   }

   private final static String ME = "QueueQueryPlugin";
   private Global global;
   private static Logger log = Logger.getLogger(QueueQueryPlugin.class.getName());
   private Set waitingThreads = new HashSet();
   //private int maxEntries;
   //private long maxSize;
   
   public QueueQueryPlugin(Global global) {
      this.global = global;

   }

   private WaitingQuery[] getWaitingQueries() {
      synchronized (this.waitingThreads) {
         return (WaitingQuery[])this.waitingThreads.toArray(new WaitingQuery[this.waitingThreads.size()]);
      }
   }

   /**
    * If no restriction is given, i.e. if both maxEntries and maxBytes is negative,
    * then it will wait.
    * 
    * @return true if it has to wait, false if there are already sufficently entries 
    *         in the queue.
    */
   private final boolean checkIfNeedsWaiting(int entriesInQueue, long bytesInQueue, WaitingQuery wq) {
      if (wq.maxEntries > 0) {
         if (entriesInQueue >= wq.maxEntries) return false;
      }
      if (wq.maxSize > 0) {
         if (bytesInQueue >= wq.maxSize) return false;
      }
      return true;
   }
   
   /**
    * The query to the queue. The parameters specifying which kind of query it is
    * are specified in the qos, and more precisely in the QuerySpecQos.
    * @param source must be an I_Queue implementation (can not be null).
    * @param query must not be null, e.g.
    * "maxEntries=3&maxSize=1000&consumable=true&waitingDelay=1000"
    * for example from qosData.getQuerySpecArr()[0].getQuery().getQuery()     
    */
   public MsgUnit[] query(Object source, String query) throws XmlBlasterException {
      //if (log.isLoggable(Level.FINER)) log.call(ME, "query for '" + keyData.getOid() + "'");
      if (source == null)
         throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "The source on which do the query is null");
      if (! (source instanceof I_Queue) )
         throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Wrong type of source for query. Expected an 'I_Queue' implementation but was '" + source.getClass().getName() + "'");
      if (query == null)
         throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "The query string is null");
         
      I_Queue queue = (I_Queue)source;         


      int maxEntries = 1;
      long maxSize = -1L;
      boolean consumable = false;
      long waitingDelay = 0L; // no wait is default
      
      // get the query properties
      //if (querySpec.getQuery() != null) query = querySpec.getQuery().getQuery();
      // "maxEntries=3&maxSize=1000&consumable=true&waitingDelay=1000"      
      Map props = StringPairTokenizer.parseToStringClientPropertyPairs(query, "&", "=");
      ClientProperty prop = (ClientProperty)props.get("maxEntries");
      if (prop != null) maxEntries = prop.getIntValue();
      prop = (ClientProperty)props.get("maxSize");
      if (prop != null) maxSize = prop.getLongValue();
      if (maxSize > -1L) {
         log.warning(" Query specification of maxSize is not implemented, please use the default value -1 or leave it untouched: '" + query + "'");
         throw new XmlBlasterException(this.global, ErrorCode.USER_ILLEGALARGUMENT, ME, "Query specification of maxSize is not implemented, please use the default value -1 or leave it untouched");
      }
      prop = (ClientProperty)props.get("consumable");
      if (prop != null) consumable = prop.getBooleanValue();
      prop = (ClientProperty)props.get("waitingDelay");
      if (prop != null) waitingDelay = prop.getLongValue();

      if (log.isLoggable(Level.FINE)) 
      log.fine("query: waitingDelay='" + waitingDelay + "' consumable='" + consumable + "' maxEntries='" + maxEntries + "' maxSize='" + maxSize + "'");
      
      if (waitingDelay != 0L) {
         if (log.isLoggable(Level.FINE)) log.fine("query: waiting delay is " + waitingDelay);
         if (maxEntries < 1 && maxSize < 1L && waitingDelay < 0L) {
            log.warning("If you specify a blocking get you must also specify a maximum size or maximum number of entries to retreive, otherwise specify non-blocking by setting 'waitingDelay' to zero, query is illegal: '" + query + "'");
            throw new XmlBlasterException(this.global, ErrorCode.USER_ILLEGALARGUMENT, ME, "If you specify a blocking get you must also specify a maximum size or maximum number of entries to retreive, otherwise specify non-blocking by setting 'waitingDelay' to zero");
         }
         
         WaitingQuery wq = new WaitingQuery(maxEntries, maxSize);
         
         if (checkIfNeedsWaiting((int)queue.getNumOfEntries(), queue.getNumOfBytes(), wq)) {
            if (log.isLoggable(Level.FINE)) log.fine("query: going to wait due to first check");
            try {
               synchronized (this.waitingThreads) {
                  this.waitingThreads.add(wq);
               }
               queue.addQueueSizeListener(this);
               boolean timedOut = false;
               try {
                  if (waitingDelay < 0L)
                     wq.startSignal.await();
                  else 
                     timedOut = !wq.startSignal.await(waitingDelay, TimeUnit.MILLISECONDS);
                  if (log.isLoggable(Level.FINE)) log.fine("just waked up after waiting for incoming entries, timedOut=" + timedOut);
               }
               catch (InterruptedException ex) {
                  if (log.isLoggable(Level.FINE)) log.fine("just waked up because of interrupt: " + ex.toString());
               }
            }
            catch (Throwable e) {
               log.severe("Can't handle query: " + e.toString());
            }
            finally {
               try {
                  synchronized (this.waitingThreads) {
                     this.waitingThreads.remove(wq);
                  }
                  queue.removeQueueSizeListener(this);
                  if (log.isLoggable(Level.FINE)) log.fine("query: removed myself as a QueueSizeListener");
               }
               catch (Throwable ex) {
                  log.severe("query: exception occurred when removing the QueueSizeListener from the queue");
               }
            }
         }
      }
      
      ArrayList list = queue.peek(maxEntries, maxSize);
      ArrayList entryListChecked = DispatchManager.prepareMsgsFromQueue(ME, log, queue, list);
      
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

   /**
    * We register for queue size changes and our blocking thread returns if we are done.  
    * Enforced by I_QueueSizeListener
    */
   public void changed(I_Queue queue, long numEntries, long numBytes, boolean isShutdown) {
      if (log.isLoggable(Level.FINER)) log.finer("changed numEntries='" + numEntries + "' numBytes='" + numBytes + "'");
      WaitingQuery[] queries = getWaitingQueries();
      for (int i=0; i<queries.length; i++) {
         if (isShutdown || !checkIfNeedsWaiting((int)numEntries, numBytes, queries[i])) {
            if (log.isLoggable(Level.FINE)) log.fine("changed going to notify");
            queries[i].startSignal.countDown();
         }
      }
   }
   
}
