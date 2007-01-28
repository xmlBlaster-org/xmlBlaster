/*------------------------------------------------------------------------------
Name:      SubscribeQosServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.util.qos.HistoryQos;
import org.xmlBlaster.util.qos.QuerySpecQos;

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
   private final QueryQosData queryQosData;
   private boolean doInhibitInitialUpdates = false;
   private final Global glob;

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
   
   Global getGlobal() { return this.glob; }

   public static void verifySubscriptionId(SessionName sessionName, QueryKeyData xmlKey, SubscribeQosServer subscribeQos)
      throws XmlBlasterException {
      String subscriptionId = subscribeQos.getSubscriptionId();
      if (subscriptionId != null) {
         boolean isOk = true;
         
         //"__subId:client/joe/session/1-[your-unqiue-postfix]"
         if (!subscriptionId.startsWith(Constants.SUBSCRIPTIONID_PREFIX)
               || subscriptionId.length() < (Constants.SUBSCRIPTIONID_PREFIX.length()+5))
            isOk = false;

         String tail = subscriptionId.substring(Constants.SUBSCRIPTIONID_PREFIX.length());
         if (!tail.startsWith(sessionName.getRelativeName(true)))
            isOk = false;
         
         if (!isOk)
            throw new XmlBlasterException(subscribeQos.getGlobal(), ErrorCode.USER_SUBSCRIBE_ID,
               "Your subscriptionId '" + subscriptionId +
               "' is invalid, we expect something like '" +
               subscribeQos.getData().generateSubscriptionId(sessionName, xmlKey));
      }
   }


   /**
    * Return the subscribe filters or null if none is specified. 
    */
   public final AccessFilterQos[] getAccessFilterArr() {
      return this.queryQosData.getAccessFilterArr();
   }

   /**
    * Return the get querySpecs or null if none is specified. 
    */
   public final QuerySpecQos[] getQuerySpecArr() {
      return this.queryQosData.getQuerySpecArr();
   }

   /**
    * Query the message history
    */
   public HistoryQos getHistoryQos() {
      return this.queryQosData.getHistoryQos();
   }

   public boolean getWantInitialUpdate() {
      return this.queryQosData.getWantInitialUpdate() && !this.doInhibitInitialUpdates;
   }

   public boolean getWantUpdateOneway() {
      return this.queryQosData.getWantUpdateOneway();
   }

   public boolean getWantNotify() {
      return this.queryQosData.getWantNotify();
   }

   /**
    * Are multiple subscribes allowed?
    * Defaults to true. 
    * @return true Multiple subscribes deliver multiple updates
    *         false Ignore more than one subscribes on same oid
    */
   public boolean getMultiSubscribe() {
      return this.queryQosData.getMultiSubscribe();
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

   /**
    * Inhibits the initial updates even if the qosData.wantInitialUpdates has been
    * set.
    * @param doInhibit
    */
   public void inhibitInitalUpdates(boolean doInhibit) {
      this.doInhibitInitialUpdates = doInhibit; 
   }
   
   public String toXml() {
      return toXml((String)null);
   }

   public String toXml(String extraOffset) {
      return this.queryQosData.toXml(extraOffset);
   }
}
