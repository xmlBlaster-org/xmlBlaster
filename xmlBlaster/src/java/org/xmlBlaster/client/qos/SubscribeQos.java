/*------------------------------------------------------------------------------
Name:      SubscribeQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.engine.mime.Query;
import org.xmlBlaster.engine.helper.AccessFilterQos;
import org.xmlBlaster.util.qos.HistoryQos;

/**
 * This class encapsulates the QoS (quality of service) of a subscribe() request. 
 * <p />
 * A full specified <b>subscribe</b> qos could look like this:<br />
 * <pre>
 *&lt;qos>
 *   &lt;id>__subId:/node/heron/client/joe/3/34&lt;/id> &lt; Force a subscription ID from client side -->
 *   &lt;meta>false&lt;/meta>       &lt;!-- Don't send me the xmlKey meta data on updates -->
 *   &lt;content>false&lt;/content> &lt;!-- Don't send me the content data on updates (notify only) -->
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
      this.glob = (glob==null) ? Global.instance() : glob;
      this.queryQosData = new QueryQosData(glob, glob.getQueryQosFactory()); 
   }

   /**
    * Force the identifier (unique handle) for this subscription. 
    * Usually you let the identifier be generated by xmlBlaster.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.subscribe.id.html">The engine.qos.subscribe.id requirement</a>
    */
   public void setSubscriptionId(String subId) {
      this.queryQosData.setSubscriptionId(subId);
   }

   /**
    * Do we want to have an initial update on subscribe if the message
    * exists already?
    *
    * @return true if initial update wanted
    *         false if only updates on new publishes are sent
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.subscribe.initialUpdate.html">The engine.qos.subscribe.initialUpdate requirement</a>
    */
   public void setWantInitialUpdate(boolean initialUpdate) {
      this.queryQosData.setWantInitialUpdate(initialUpdate);
   }

   /**
    * false Inhibit the delivery of messages to myself if i have published it.
    */
   public void setWantLocal(boolean local) {
      this.queryQosData.setWantLocal(local);
   }

   /**
    * If false, the update contains not the content (it is a notify of change only)
    * <p />
    * This may be useful if you have huge contents, and you only want to be informed about a change
    * TODO: Implement in server!!!
    */
   public void setWantContent(boolean content) {
      this.queryQosData.setWantContent(content);
   }

   /**
    * Adds your supplied subscribe filter. 
    * <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/mime.plugin.accessfilter.html">The access filter plugin requirement</a>
    */
   public void addAccessFilter(AccessFilterQos filter) {
      this.queryQosData.addAccessFilter(filter);
   }

   /**
    * Query historical messages. 
    */
   public void setHistoryQos(HistoryQos historyQos) {
      this.queryQosData.setHistoryQos(historyQos);
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
