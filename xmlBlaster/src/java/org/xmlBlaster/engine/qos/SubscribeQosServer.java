/*------------------------------------------------------------------------------
Name:      SubscribeQosServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.util.qos.HistoryQos;

/**
 * Handling of subscribe() quality of services in the server core.
 * <p>
 * This decorator hides the real qos data object and gives us a server specific view on it. 
 * </p>
 * <p>
 * QoS Informations sent from the client to the server via the subscribe() method<br />
 * They are needed to control xmlBlaster
 * </p>
 * <p>
 * For the xml representation see QueryQosSaxFactory.
 * </p>
 * @author xmlBlaster@marcelruff.info
 * @see org.xmlBlaster.util.qos.QueryQosData
 * @see org.xmlBlaster.util.qos.QueryQosSaxFactory
 */
public final class SubscribeQosServer
{
   private String ME = "SubscribeQosServer";
   private final Global glob;
   private final QueryQosData queryQosData;

   /**
    * Constructor which accepts a raw data struct. 
    */
   public SubscribeQosServer(Global glob, QueryQosData queryQosData) {
      this.glob = glob;
      this.queryQosData = queryQosData;
   }

   /**
    * Constructs the specialized quality of service object for a subscribe() call.
    * @param the XML based ASCII string
    */
   public SubscribeQosServer(Global glob, String xmlQos) throws XmlBlasterException {
      this.glob = glob;
      this.queryQosData = glob.getQueryQosFactory().readObject(xmlQos);
   }

   /**
    * Access the internal data struct
    */
   public QueryQosData getData() {
      return this.queryQosData;
   }

   /**
    * Return the subscribe filters or null if none is specified. 
    */
   public final AccessFilterQos[] getAccessFilterArr() {
      return this.queryQosData.getAccessFilterArr();
   }

   /**
    * Query the message history
    */
   public HistoryQos getHistoryQos() {
      return this.queryQosData.getHistoryQos();
   }

   public boolean getWantInitialUpdate() {
      return this.queryQosData.getWantInitialUpdate();
   }

   public boolean getWantNotify() {
      return this.queryQosData.getWantNotify();
   }

   /**
    * false Inhibit the delivery of messages to myself if i have published it.
    */
   public boolean getWantLocal() {
      return this.queryQosData.getWantLocal();
   }

   /**
    * @return false: Don't send me the meta information of a message key
    */
   public boolean getWantMeta() {
      return this.queryQosData.getWantMeta();
   }

   /**
    * If false, the update contains not the content (it is a notify of change only)
    * TODO: Implement in server!!!
    */
   public boolean getWantContent() {
      return this.queryQosData.getWantContent();
   }

   public final void setSubscriptionId(String subscriptionId) {
      this.queryQosData.setSubscriptionId(subscriptionId);
   }

   /**
    * Get the identifier (unique handle) for this subscription. 
    * @return the identifier force by the client, or null if xmlBlaster generates it
    */
   public final String getSubscriptionId() {
      return this.queryQosData.getSubscriptionId();
   }

   public String toXml() {
      return toXml((String)null);
   }

   public String toXml(String extraOffset) {
      return this.queryQosData.toXml(extraOffset);
   }
}
