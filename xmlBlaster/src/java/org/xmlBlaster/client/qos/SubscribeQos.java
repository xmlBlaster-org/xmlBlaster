/*------------------------------------------------------------------------------
Name:      SubscribeQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.QuerySpecQos;
import org.xmlBlaster.engine.mime.Query;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.util.qos.HistoryQos;
import org.xmlBlaster.util.def.MethodName;

/**
 * This class encapsulates the QoS (quality of service) of a subscribe() request. 
 * <p />
 * A full specified <b>subscribe</b> qos could look like this:<br />
 * <pre>
 *&lt;qos>
 *   &lt;id>__subId:/node/heron/client/joe/3/34&lt;/id> &lt; Force a subscription ID from client side -->
 *   &lt;meta>false&lt;/meta>       &lt;!-- Don't send me the xmlKey meta data on updates -->
 *   &lt;content>false&lt;/content> &lt;!-- Don't send me the content data on updates (notify only) -->
 *   &lt;multiSubscribe>false&lt;/multiSubscribe> &lt;!-- Ignore a second subscribe on same oid or XPATH -->
 *   &lt;local>false&lt;/local>     &lt;!-- Inhibit the delivery of messages to myself if i have published it -->
 *   &lt;initialUpdate>false&lt;/initialUpdate>;
 *   &lt;filter type='myPlugin' version='1.0'>a!=100&lt;/filter>
 *                                  &lt;!-- Filters messages i have subscribed as implemented in your plugin -->
 *   &lt;history numEntries='20'/>  &lt;!-- Default is to deliver the current entry (numEntries='1'), '-1' deliver all -->
 *&lt;/qos>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 * @see org.xmlBlaster.util.qos.QueryQosData
 * @see org.xmlBlaster.util.qos.QueryQosSaxFactory
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.subscribe.html">subscribe interface</a>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/mime.plugin.accessfilter.html">MIME access filter requirement</a>
 */
public final class SubscribeQos
{
   private String ME = "SubscribeQos";
   private final Global glob;
   private final QueryQosData queryQosData;

   /**
    * Constructor for default qos (quality of service).
    */
   public SubscribeQos(Global glob) {
      this(glob, null);
   }

   /**
    * Constructor for internal use. 
    * @param queryQosData The struct holding the data
    */
   public SubscribeQos(Global glob, QueryQosData queryQosData) {
      this.glob = (glob==null) ? Global.instance() : glob;
      this.queryQosData = (queryQosData==null) ? new QueryQosData(this.glob, this.glob.getQueryQosFactory(), MethodName.SUBSCRIBE) : queryQosData;
      this.queryQosData.setMethod(MethodName.SUBSCRIBE);
   }

   /**
    * Access the wrapped data holder
    */
   public QueryQosData getData() {
      return this.queryQosData;
   }

   /**
    * Force the identifier (unique handle) for this subscription. 
    * Usually you let the identifier be generated by xmlBlaster.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.subscribe.id.html">The engine.qos.subscribe.id requirement</a>
    * @see QueryQosData#setSubscriptionId(String)
    */
   public void setSubscriptionId(String subId) {
      this.queryQosData.setSubscriptionId(subId);
   }

   /**
    * Are multiple subscribes allowed?
    * Defaults to true. 
    * @return true Multiple subscribes deliver multiple updates
    *         false Ignore more than one subscribes on same oid
    */
   public void setMultiSubscribe(boolean multiSubscribe) {
      this.queryQosData.setMultiSubscribe(multiSubscribe);
   }

   /**
    * Do we want to have an initial update on subscribe if the message
    * exists already?
    * Defaults to true. 
    * @return true if initial update wanted
    *         false if only updates on new publishes are sent
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.subscribe.initialUpdate.html">The engine.qos.subscribe.initialUpdate requirement</a>
    * @see QueryQosData#setWantInitialUpdate(boolean)
    */
   public void setWantInitialUpdate(boolean initialUpdate) {
      this.queryQosData.setWantInitialUpdate(initialUpdate);
   }

   /**
    * false Inhibit the delivery of messages to myself if i have published it.
    * Defaults to true. 
    * @see QueryQosData#setWantLocal(boolean)
    */
   public void setWantLocal(boolean local) {
      this.queryQosData.setWantLocal(local);
   }

   /**
    * If false, the update contains not the content (it is a notify of change only)
    * <p />
    * This may be useful if you have huge contents, and you only want to be informed about a change
    * TODO: Implement in server!!!
    * @see QueryQosData#setWantContent(boolean)
    */
   public void setWantContent(boolean content) {
      this.queryQosData.setWantContent(content);
   }

   /**
    * If set to true (which is default) an erase notification message is sent
    * to the subscriber when the topic is erased.
    * <br />
    * The <i>state</i> in the message QoS is set to Constants.STATE_ERASED="ERASED"
    * @param notify true - notify subscriber when the topic is erased (default is true)
    */
   public void setWantNotify(boolean notify) {
      this.queryQosData.setWantNotify(notify);
   }

   /**
    * Adds your supplied subscribe filter. 
    * <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/mime.plugin.accessfilter.html">The access filter plugin requirement</a>
    */
   public void addAccessFilter(AccessFilterQos filter) {
      this.queryQosData.addAccessFilter(filter);
   }

   /**
    * Adds your supplied get querySpec. 
    * <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/query.plugin.html">The query plugin requirement</a>
    */
   public void addQuerySpec(QuerySpecQos querySpec) {
      this.queryQosData.addQuerySpec(querySpec);
   }

   /**
    * Query historical messages. 
    */
   public void setHistoryQos(HistoryQos historyQos) {
      this.queryQosData.setHistoryQos(historyQos);
   }

   /**
    * Sets a client property (an application specific property) to the
    * given value
    * @param key
    * @param value
    */
   public void addClientProperty(String key, Object value) {
      this.queryQosData.addClientProperty(key, value);
   }

   /**
    * Read back a property. 
    * @return The client property or null if not found
    */
   public ClientProperty getClientProperty(String key) {
      return this.queryQosData.getClientProperty(key);
   }

   /**
    * Mark the subscription request to be persistent. 
    * <p>
    * Sets the persistent flag for this subscription. If this flag is
    * set, the subscription will persist a server crash.
    * </p>
    * @param persistent
    */
   public void setPersistent(boolean persistent) {
      this.queryQosData.setPersistent(persistent);
   }

   /**
    * Gets the persistent flag for this subscription. If this flag is
    * set, the subscription will persist a server crash.
    * @return true if persistent false otherwise.
    */
   public boolean getPersistent() {
      return this.queryQosData.getPersistentProp().getValue();
   }
   
   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   public String toString() {
      return this.queryQosData.toXml();
   }

   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   public String toXml() {
      return this.queryQosData.toXml();
   }

   /** For testing: java org.xmlBlaster.client.qos.SubscribeQos */
   public static void main(String[] args) {
      Global glob = new Global(args);
      try {
         SubscribeQos qos = new SubscribeQos(glob);
         qos.setWantContent(false);
         qos.addAccessFilter(new AccessFilterQos(glob, "ContentLenFilter", "1.0", new Query(glob, "800")));
         qos.addAccessFilter(new AccessFilterQos(glob, "ContentLenFilter", "3.2", new Query(glob, "a<10")));
         System.out.println(qos.toXml());
      }
      catch (Throwable e) {
         System.out.println("Test failed: " + e.toString());
      }
   }
}
