/*------------------------------------------------------------------------------
Name:      QueryQosData.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.util.property.PropBoolean;
import org.xmlBlaster.util.enum.MethodName;

import java.util.ArrayList;

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
   private String ME = "QueryQosData";
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

   /** Mime based filter rules */
   private ArrayList filters = null;
   private transient AccessFilterQos[] filterArr = null; // To cache the filters in an array

   /** Query history messages */
   private HistoryQos historyQos;

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
    * Defaults to true. 
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
    * @param setWantLocal false Inhibit the delivery of messages to myself if i have published it.
    */
   public void setWantLocal(boolean local) {
      this.local.setValue(local);
   }

   /**
    * Defaults to true. 
    * @return false Inhibit the delivery of messages to myself if i have published it.
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

   /**
    * Adds your subplied subscribe filter
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
    * Set the QoS which describes the history query settings. 
    */
   public void setHistoryQos(HistoryQos historyQos) {
      this.historyQos = historyQos;
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
    * Get the identifier (unique handle) for this subscription. 
    * @return The id or null if not specified by client.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.subscribe.id.html">The engine.qos.subscribe.id requirement</a>
    */
   public String getSubscriptionId() {
      return this.subscriptionId;
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
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of the query as a XML ASCII string
    */
   public String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the query as a XML ASCII string
    */
   public String toXml(String extraOffset) {
      return factory.writeObject(this, extraOffset);
   }

   /**
    * Returns a shallow clone, you can change savely all basic or immutable types
    * like boolean, String, int.
    * Currently RouteInfo is not cloned (so don't change it)
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
}

