/*------------------------------------------------------------------------------
Name:      QueryQosData.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import java.util.ArrayList;
import java.util.Properties;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.property.PropBoolean;

/**
 * Data container handling of query / access QoS.
 * <p>
 * This data holder is accessible through decorators, each of them allowing a specialized view on the data:
 * </p>
 * <ul>
 * <li>SubscribeQos QoS of a subscribe() invocation (Client side)</i>
 * <li>UnSubscribeQos QoS of a unSubscribe() invocation (Client side)</i>
 * <li>EraseQos QoS of an erase() invocation (Client side)</i>
 * <li>GetQos QoS of an get() invocation (Client side)</i>
 * </ul>
 * <p>
 * For the xml representation see QueryQosSaxFactory.
 * </p>
 * @see org.xmlBlaster.util.qos.QueryQosSaxFactory
 * @see org.xmlBlaster.test.classtest.qos.QueryQosFactoryTest
 * @author xmlBlaster@marcelruff.info
 */
public final class QueryQosData extends QosData implements java.io.Serializable, Cloneable
{
   private static final long serialVersionUID = 1L;
   private transient I_QueryQosFactory factory;

   /** A client can force a specific subscription id */
   private String subscriptionId;

   /** On erase forceDestroy */
   private PropBoolean forceDestroy = new PropBoolean(false);

   /** not yet supported */
   private PropBoolean meta = new PropBoolean(true);

   /** not yet supported */
   private PropBoolean content = new PropBoolean(true);

   /** allow duplicate identical subscriptions */
   private PropBoolean multiSubscribe = new PropBoolean(true);

   /** update messages i have sent myself to myself? */
   private PropBoolean local = new PropBoolean(true);

   /** send on subscribe an initial update with the current message */
   private PropBoolean initialUpdate = new PropBoolean(true);

   /** for erase(): Notify the subscribers on erase? */
   private PropBoolean notify = new PropBoolean(true);

   /** Set for each subscription if you want updateOneway() instead of update()
       currently this is a flag of the connectQos as well */
   private PropBoolean updateOneway = new PropBoolean(false);
   
   /** Mime based filter rules */
   private ArrayList filters = null;
   private transient AccessFilterQos[] filterArr = null; // To cache the filters in an array

   /** Query based filter rules */
   private ArrayList queries = null;
   private transient QuerySpecQos[] querySpecArr = null; // To cache the querySpecs in an array

   /** Query history messages */
   private HistoryQos historyQos;
   private boolean containsHistoryQos;

   /** true if query has to be

   /**
    * Constructs the specialized quality of service object for query informations.
    * E.g. for a subscribe() call
    * @param The factory which knows how to serialize and parse me
    */
   public QueryQosData(Global glob, MethodName methodName) {
      this(glob, null, null, methodName);
   }

   /**
    * Constructs the specialized quality of service object for query informations.
    * E.g. for a subscribe() call
    * @param The factory which knows how to serialize and parse me
    */
   public QueryQosData(Global glob, I_QueryQosFactory factory, MethodName methodName) {
      this(glob, factory, null, methodName);
   }

   /**
    * Constructs the specialized quality of service object for a publish() call.
    * For internal use only, this message is sent from the persistence layer
    * @param the XML based ASCII string
    * @param true
    */
   public QueryQosData(Global glob, I_QueryQosFactory factory, String serialData, MethodName methodName) {
      super(glob, serialData, methodName);
      this.factory = (factory==null) ? glob.getQueryQosFactory() : factory;
      this.containsHistoryQos = (this.historyQos != null);
   }

   /**
    * Allow to subscribe multiple times to the same query/oid
    * @return defaults to true
    */
   public boolean getMultiSubscribe() {
      return this.multiSubscribe.getValue();
   }

   public PropBoolean getMultiSubscribeProp() {
      return this.multiSubscribe;
   }

   public void setMultiSubscribe(boolean multiSubscribe) {
      this.multiSubscribe.setValue(multiSubscribe);
   }

   /**
    * Do we want to have an initial update on subscribe if the message
    * exists already?
    * Defaults to true.
    * @return true if initial update wanted
    *         false if only updates on new publishes are sent
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.subscribe.initialUpdate.html">The engine.qos.subscribe.initialUpdate requirement</a>
    */
   public void setWantInitialUpdate(boolean initialUpdate) {
      this.initialUpdate.setValue(initialUpdate);
   }

   /**
    * Defaults to true.
    */
   public boolean getWantInitialUpdate() {
      return this.initialUpdate.getValue();
   }

   public PropBoolean getInitialUpdateProp() {
      return this.initialUpdate;
   }

   /**
    * Do we want the callback message delivered with update() or with updateOneway()?
    * Defaults to false.
    * @return true if oneway callback wanted
    *         false to use update() with ACK return
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html">The interface.subscribe requirement</a>
    */
   public void setWantUpdateOneway(boolean updateOneway) {
      this.updateOneway.setValue(updateOneway);
   }

   /**
    * Defaults to true.
    */
   public boolean getWantUpdateOneway() {
      return this.updateOneway.getValue();
   }

   public PropBoolean getUpdateOnewayProp() {
      return this.updateOneway;
   }

   /** if __newestOnly client property is true remove older instances from callback queue of client */
   public boolean newestOnly() {
	   return getClientProperty("__newestOnly", false);
   }

   public void setNewestOnly(boolean newestOnly) {
	   addClientProperty("__newestOnly", newestOnly);
   }

   /**
    * Set to true if you want an erase notification if the topic is explicitly erased.
    * @param notify Defaults to true.
    */
   public void setWantNotify(boolean notify) {
      this.notify.setValue(notify);
   }

   /**
    * For erase(): Notify the subscribers on erase?
    * Defaults to true.
    */
   public boolean getWantNotify() {
      return this.notify.getValue();
   }

   public PropBoolean getNotifyProp() {
      return this.notify;
   }

   /**
    * Defaults to true.
    * @param setWantLocal false Inhibit the dispatch of messages to myself if i have published it.
    */
   public void setWantLocal(boolean local) {
      this.local.setValue(local);
   }

   /**
    * Defaults to true.
    * @return false Inhibit the dispatch of messages to myself if i have published it.
    */
   public boolean getWantLocal() {
      return this.local.getValue();
   }

   public PropBoolean getLocalProp() {
      return this.local;
   }

   /**
    * Defaults to true.
    * @param meta false: Don't send me the meta information of a message key
    */
   public void setWantMeta(boolean meta) {
      this.meta.setValue(meta);
   }

   /**
    * Defaults to true.
    * @return false: Don't send me the meta information of a message key
    */
   public boolean getWantMeta() {
      return this.meta.getValue();
   }

   public PropBoolean getMetaProp() {
      return this.meta;
   }

   /**
    * Defaults to true.
    * If false, the update contains not the content (it is a notify of change only)
    * TODO: Implement in server!!!
    */
   public void setWantContent(boolean content) {
      this.content.setValue(content);
   }

   /**
    * Defaults to true.
    */
   public boolean getWantContent() {
      return this.content.getValue();
   }

   public PropBoolean getContentProp() {
      return this.content;
   }

   /**
    * Topic erase behavior with pending messages, defaults to false.
    * @param forceDestroy
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.message.lifecycle.html">engine.message.lifecycle requirement</a>
    */
   public void setForceDestroy(boolean forceDestroy) {
      this.forceDestroy.setValue(forceDestroy);
   }

   /**
    * Defaults to false.
    * @return Topic erase behavior with pending messages
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.message.lifecycle.html">engine.message.lifecycle requirement</a>
    */
   public boolean getForceDestroy() {
      return this.forceDestroy.getValue();
   }

   public PropBoolean getForceDestroyProp() {
      return this.forceDestroy;
   }

   public synchronized void setFilters(AccessFilterQos[] newFilters) {
      if (newFilters == null) {
         this.filters = null;
      }
      else {
         this.filters = new ArrayList(newFilters.length);
         for (int i=0; i<newFilters.length; i++)
            this.filters.add(newFilters[i]);
      }
      this.filterArr = null; // Reset cache
   }

   /**
    * Adds your supplied subscribe filter
    */
   public void addAccessFilter(AccessFilterQos filter) {
      if (filters == null) filters = new ArrayList();
      filterArr = null; // clear cache
      this.filters.add(filter);
   }

   public ArrayList getAccessFilters() {
      return this.filters;
   }

   /**
    * Return the subscribe filters or null if none is specified.
    */
   public AccessFilterQos[] getAccessFilterArr() {
      if (filterArr != null || filters == null || filters.size() < 1)
         return filterArr;

      filterArr = (AccessFilterQos[])filters.toArray(new AccessFilterQos[filters.size()]);
      return filterArr;
   }

   /**
    * Adds the querySpec you supplied.
    */
   public void addQuerySpec(QuerySpecQos querySpec) {
      if (this.queries == null) this.queries = new ArrayList();
      this.querySpecArr = null; // clear cache
      this.queries.add(querySpec);
   }

   public ArrayList getQuerySpecs() {
      return this.queries;
   }

   /**
    * Return the subscribe filters or null if none is specified.
    */
   public QuerySpecQos[] getQuerySpecArr() {
      if (this.querySpecArr != null || this.queries == null || this.queries.size() < 1)
         return this.querySpecArr;

      this.querySpecArr = (QuerySpecQos[])this.queries.toArray(new QuerySpecQos[this.queries.size()]);
      return this.querySpecArr;
   }

   /**
    * Set the QoS which describes the history query settings.
    */
   public void setHistoryQos(HistoryQos historyQos) {
      this.historyQos = historyQos;
      this.containsHistoryQos = (historyQos == null) ? false : true;
   }

   /**
    * Get the QoS which describes the history query settings.
    * @return never null
    */
   public HistoryQos getHistoryQos() {
      if (this.historyQos == null) {
         this.historyQos = new HistoryQos(glob);
      }
      return this.historyQos;
   }

   /**
    * Was a history qos specified?
    */
   public boolean containsHistoryQos() {
      return this.containsHistoryQos;
   }

   /**
    * Get the identifier (unique handle) for this subscription.
    * @return The id or null if not specified by client.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.subscribe.id.html">The engine.qos.subscribe.id requirement</a>
    */
   public String getSubscriptionId() {
      return this.subscriptionId;
   }

   /**
    *
    * @return true if the client has forced a subscriptionId
    */
   public boolean hasSubscriptionId() {
	   return (this.subscriptionId!=null && this.subscriptionId.length()!=0);
   }

   /**
    * Force the identifier (unique handle) for this subscription.
    * Usually you let the identifier be generated by xmlBlaster.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.subscribe.id.html">The engine.qos.subscribe.id requirement</a>
    */
   public void setSubscriptionId(String subscriptionId) {
      this.subscriptionId = subscriptionId;
   }

   /**
    * A client side subscriptionId must start with "__subId:" followed by the relative session name.
    * <p>This us only useful for positive session Ids in fail save environments: if the
    * subscription is queued the faked subscriptionId will be used later by the server</p>
    * @param sessionName
    * @param subscribeKey
    * @return e.g. "__subId:client/joe/session/1-XPATH://key" for pubSessionId>0 and multiSubscribe=false
    * or e.g. "__subId:client/joe-135692304540000" in other cases
    */
   public String generateSubscriptionId(SessionName sessionName, QueryKeyData subscribeKey) {
      if (sessionName == null) {
         this.subscriptionId = Constants.SUBSCRIPTIONID_PREFIX +
                     "UnknownUser-" +
                     new Timestamp().getTimestamp();
         return this.subscriptionId;
      }
      if (sessionName.isPubSessionIdUser() || !getMultiSubscribe()) {
         // This key is assured to be the same on client restart
         // a previous subscription in the server will have the same subscriptionId
         // Benefit: If on client restart we are queueing the returned faked subscriptionId will
         // match the later used one of the xmlBlaster server. We can easily use the subscriptionId
         // as a key in client code hashtable to dispatch update() messages
         // Note: multiSubscribe==false allows max one subscription on a topic, even it has
         // different mime query plugins (the latest wins)
         String url = subscribeKey.getUrl();
         // url = ReplaceVariable.replaceAll(url, "'", "&apos;"); // to have valid xml (<subscribe id='bla'/>
         this.subscriptionId = Constants.SUBSCRIPTIONID_PREFIX +
                               sessionName.getRelativeName(true) + "-" +
                               url;
      }
      else {
         this.subscriptionId = Constants.SUBSCRIPTIONID_PREFIX +
                               sessionName.getRelativeName(true) + "-" +
                               new Timestamp().getTimestamp();
      }
	   return this.subscriptionId;

   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of the query as a XML ASCII string
    */
   public String toXml() {
      return toXml((String)null, (Properties)null);
   }

   // deprecated
   public String toXml(String extraOffset) {
      Properties props = null;
      if (extraOffset != null && extraOffset.length() > 0) {
         props = new Properties();
         props.put(Constants.TOXML_EXTRAOFFSET, extraOffset);
      }
      return toXml(extraOffset, props);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the query as a XML ASCII string
    */
   public String toXml(String extraOffset, Properties props) {
      return factory.writeObject(this, extraOffset, props);
   }

   /**
    * Returns a deep clone, you can change savely all basic or immutable types
    * like boolean, String, int and also the ClientProperties and RouteInfo.
    */
   public Object clone() {
      return super.clone();
   }

   /**
    * Sets the global object (used when deserializing the object)
    */
   public void setGlobal(Global glob) {
      super.setGlobal(glob);
      this.factory = glob.getQueryQosFactory();
   }
   
   public String toString() {
	   return toXmlReadable();
   }
}

