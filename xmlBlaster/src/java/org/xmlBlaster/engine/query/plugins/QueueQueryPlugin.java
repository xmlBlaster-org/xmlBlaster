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
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.qos.QuerySpecQos;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;

/**
 * QueueQueryPlugin
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class QueueQueryPlugin implements I_Query {

   private final static String ME = "QueueQueryPlugin";
   private Global global;
   private LogChannel log;
   
   public QueueQueryPlugin(Global global) {
      this.global = global;
      this.log = global.getLog("query");
   }

   /**
    * The query to the queue. The parameters specifying which kind of query it is
    * are specified in the qos, and more precisely in the QuerySpecQos.
    * @param source must be an I_Queue implementation (can not be null).
    * @param qosData must be non null.
    */
   public MsgUnit[] query(Object source, QueryKeyData keyData, QueryQosData qosData) throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "query for '" + keyData.getOid() + "'");
      if (source == null)
         throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME + ".query", "the source on which do the query is null");
      if (! (source instanceof I_Queue) )
         throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME + ".query", "wrong type of source for query. Expected an 'I_Queue' implementation but was '" + source.getClass().getName() + "'");
      if (qosData == null)
         throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME + ".query", "the qos of the query was null");
         
      I_Queue queue = (I_Queue)source;         

      int maxEntries = 1;
      long maxSize = -1L;
      boolean consumable = false;
      long waitingDelay = 0L; // no wait is default
      
      // TODO this should go into a QueryPlugin called QueueQuery   
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

      // get the query properties
      if (querySpec != null) {
         String query = querySpec.getQuery().getQuery();
         // "maxEntries=3;maxSize=1000;consumable=true;waitingDelay=1000"      
         Map props = StringPairTokenizer.parseToStringClientPropertyPairs(this.global, query, ";", "=");
         ClientProperty prop = (ClientProperty)props.get("maxEntries");
         if (prop != null) maxEntries = prop.getIntValue();
         prop = qosData.getClientProperty("maxSize");
         if (prop != null) maxSize = prop.getLongValue();
         prop = qosData.getClientProperty("consumable");
         if (prop != null) consumable = prop.getBooleanValue();
         prop = qosData.getClientProperty("waitingDelay");
         if (prop != null) waitingDelay = prop.getLongValue();
      }
      
      // TODO implement blocking invocation and deletion ..
      ArrayList list = queue.peek(maxEntries, maxSize);
      ArrayList entryListChecked = DispatchManager.prepareMsgsFromQueue(ME, this.log, queue, list);
      
      MsgQueueEntry[] entries = (MsgQueueEntry[])entryListChecked.toArray(new MsgQueueEntry[entryListChecked.size()]);
      
      MsgUnit[] ret = new MsgUnit[entries.length];
      for (int i=0; i < entries.length; i++) {
         // TODO: REQ engine.qos.update.queue states that the queue size is passed and not the curr msgArr.length
         ReferenceEntry entry = (MsgQueueUpdateEntry)entries[i];
         MsgUnit mu = entry.getMsgUnit();
         MsgQosData msgQosData = (MsgQosData)entry.getMsgQosData().clone();
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
         
         ret[i] = new MsgUnit(mu, null, null, msgQosData);
         // ret[i] = new MsgUnitRaw(mu, mu.getKeyData().toXml(), mu.getContent(), mu.getQosData().toXml());
      }
      return ret;
   }

}
