/*------------------------------------------------------------------------------
Name:      XmlBlasterNativeClient.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch.plugins.prio;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.client.I_ConnectionProblems;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.UpdateReturnQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;


/**
 * Helper class encapsulates xmlBlaster access for PriorizedDeliveryPlugin. 
 * <p>
 * We subscribe to a status message which describes the current connection to the remote side.
 * </p>
 * <p>
 * Exactly one instance of this class exists in the Global scope
 * </p>
 * @author xmlBlaster@marcelruff.info
 */
public final class XmlBlasterNativeClient implements I_Callback
{
   private String ME = "dispatch.plugins.prio.XmlBlasterNativeClient";
   private final Global glob;
   private final LogChannel log;
   private final PriorizedDeliveryPlugin plugin;
   /* // Native xmlBlaster access currently not implemented, using remote client xmlBlasterConnection access instead
   private final I_Authenticate authenticate;
   private final I_XmlBlaster xmlBlasterImpl;
   private final String sessionId;
   */
   private XmlBlasterConnection xmlBlasterCon;
   private final ConnectQos connectQos;
   private ConnectReturnQos conRetQos;
   private boolean connected;
   private String loginName;

   /** The key is a I_Notify instance, the value is a set with SubscriptionReturnQos objects */
   private Map subscriptionsByNotifierMap = new HashMap();
   /** The key is the message oid, the value is a set with listeners */
   private Map oidListenerMap = new HashMap();
   private final String cbSessionId;

   /**
    * Creates a remote client to xmlBlaster. 
    */
   public XmlBlasterNativeClient(final Global glob, PriorizedDeliveryPlugin plugin, String sessionId) throws XmlBlasterException {
      this.log = glob.getLog("dispatch");
      this.glob = glob;
      this.plugin = plugin;
      /*
      this.authenticate = (I_Authenticate)glob.getObjectEntry(Constants.I_AUTHENTICATE_PROPERTY_KEY);
      if (this.authenticate == null) {
         throw new IllegalArgumentException(ME + ": The I_Authenticate handle is not registered in the properties, lookup of '" + Constants.I_AUTHENTICATE_PROPERTY_KEY + "' failed");
      }
      this.xmlBlasterImpl = ((org.xmlBlaster.authentication.Authenticate)this.authenticate).getXmlBlaster();
      this.sessionId = sessionId;
      */
      log.info(ME, "Connecting to xmlBlaster to subscribe to status messages");

      // Connect as a remote client ...
      xmlBlasterCon = new XmlBlasterConnection(this.glob);
      this.loginName = this.glob.getProperty().get("PriorizedDeliveryPlugin.user", "_PriorizedDeliveryPlugin");
      String passwd = this.glob.getProperty().get("PriorizedDeliveryPlugin.password", "secret");
      this.cbSessionId = passwd;
      this.connectQos = new ConnectQos(this.glob, loginName, passwd);
      this.connectQos.setSessionTimeout(0L);
      this.connectQos.setMaxSessions(this.glob.getProperty().get("PriorizedDeliveryPlugin.session.maxSessions", 10));

      Address address = new Address(this.glob, "IOR"); // Force Corba until we have native access implemented (as we know CORBA is loaded before e.g. JDBC)
      address.setDelay(2000L);      // retry connecting every 2 sec
      address.setRetries(-1);       // -1 == forever
      address.setPingInterval(0L);  // switched off
      this.connectQos.setAddress(address);

      CallbackAddress cbAddress = new CallbackAddress(this.glob);
      cbAddress.setSessionId(this.cbSessionId); // to protect our callback server - see method update()
      cbAddress.setDispatchPlugin("undef");
      this.connectQos.addCallbackAddress(cbAddress);

      this.xmlBlasterCon.initFailSave(new I_ConnectionProblems() {
            
            public void reConnected() {
               connected = true;
               conRetQos = xmlBlasterCon.getConnectReturnQos();
               log.info(ME, "I_ConnectionProblems: We were lucky, connected to " + glob.getId() + " as " + conRetQos.getSessionName());
               try {
                  xmlBlasterCon.flushQueue();    // send all tailback messages
                  // xmlBlasterCon.resetQueue(); // or discard them (it is our choice)
               } catch (XmlBlasterException e) {
                  log.error(ME, "Exception during reconnection recovery: " + e.getMessage());
               }
            }

            public void lostConnection() {
               log.warn(ME, "I_ConnectionProblems: No connection to " + glob.getId());
               connected = false;
            }
         });

      try {      
         if (log.TRACE) log.trace(ME, "Connecting to xmlBlaster as user '" + loginName + "' to subscribe to status messages");
         this.conRetQos = this.xmlBlasterCon.connect(this.connectQos, this);
         this.connected = true;
         this.loginName = conRetQos.getUserId(); // this.connectQos.getUserId();

         log.info(ME, "Succefully initialized");
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Can't subscribe to status messages: " + e.toString());
      }
   }

   public String getLoginName() {
      return this.loginName;
   }

   /**
    * Send a PtP message to the publisher notifying him on problems about
    * dispatching his just published message. 
    * <p>
    * The message oid remains the same as that one published
    * </p>
    * <p>
    * The sender of this PtP message is the loginName of the plugin itself -
    * to avoid looping we check the sender name in our plugin
    * </p>
    */
   public final void sendPtPMessage(MsgQueueEntry entry, String pluginName, String action, String currStatus) throws XmlBlasterException {
      SessionName receiver = entry.getSender();  // Send back (receiver==sender)
      if (log.TRACE) log.trace(ME, "Sending PtP notification about special message treatment in plugin, dispatcher state=" + currStatus + " receiver '" + receiver + "' ...");
      PublishQos pq = new PublishQos(glob);
      pq.addDestination(new Destination(receiver)); 
      pq.setSender(new SessionName(glob, getLoginName())); // Set ourself as sender
      pq.setIsSubscribeable(false); // For the time being we don't allow others to subscribe on the PtP notification
      pq.setState(action);
      pq.setStateInfo("Notification about special message treatment in plugin " + pluginName + ", dispatcher state=" + currStatus);
      MsgUnit msgUnit = new MsgUnit(glob, "<key oid='" + entry.getKeyOid() + "'/>", "", pq.toXml());
      //xmlBlasterImpl.publish(sessionId, msgUnit);
      xmlBlasterCon.publish(msgUnit);
   }

   /**
    * We subscribe to the status message (e.g. "_bandwidth.status') which switches our operational mode. 
    * Take care not to invoke it twice for the same message oid,
    * on configuration change call unsSubscribeStatusMessage() first.
    */
   public void subscribeToStatusMessage(String msgOid, I_Notify callback) throws XmlBlasterException {
      if (msgOid == null) {
         return;
      }

      synchronized (oidListenerMap) {
         Set listeners = (Set)oidListenerMap.get(msgOid);
         if (listeners != null && listeners.contains(callback))
            return;  //has subscribed already
      }

      SubscribeKey sk = new SubscribeKey(glob, msgOid);
      SubscribeQos sq = new SubscribeQos(glob);
      //String ret = xmlBlasterImpl.subscribe(sessionId, sk.toXml(), sq.toXml());
      SubscribeReturnQos subscribeReturnQos = xmlBlasterCon.subscribe(sk.toXml(), sq.toXml());

      // Remember subscriptions of this I_Notify instance ...
      synchronized (subscriptionsByNotifierMap) {
         Set subscriptions = (Set)subscriptionsByNotifierMap.get(callback);
         if (subscriptions == null) {
            subscriptions = new HashSet();
            subscriptionsByNotifierMap.put(callback, subscriptions);
         }
         subscriptions.add(subscribeReturnQos);
      }

      // Remember listeners of this msgOid ...
      synchronized (oidListenerMap) {
         Set listeners = (Set)oidListenerMap.get(msgOid);
         if (listeners == null) {
            listeners = new HashSet();
            oidListenerMap.put(msgOid, listeners);
         }
         listeners.add(callback);
      }
   }

   /**
    * Unsubscribe from all status messages, usually if configuration has changed. 
    */
   public void unSubscribeStatusMessages(I_Notify callback) {

      // Remove msg oid listeners ...
      synchronized(oidListenerMap) {
         // Slow linear search, but there are not expected to be too many status messages around
         Iterator it = oidListenerMap.values().iterator();

         while (it.hasNext()) {
            Set listeners = (Set)it.next();
            listeners.remove(callback);
         }

         // Cleanup oids with no listeners 
         Set emptyOidSet = new HashSet();
         it = oidListenerMap.keySet().iterator();
         while (it.hasNext()) {
            String oid = (String)it.next();
            Set listeners = (Set)oidListenerMap.get(oid);
            if (listeners.size() == 0)
               emptyOidSet.add(oid);
         }
         it = emptyOidSet.iterator();
         while (it.hasNext()) {
            String oid = (String)it.next();
            oidListenerMap.remove(oid);
         }
      }

      // unSubscribe from xmlBlaster ...
      Set subscriptions = null;
      synchronized (subscriptionsByNotifierMap) {
         subscriptions = (Set)subscriptionsByNotifierMap.get(callback);
      }

      if (subscriptions != null) {
         Iterator it = subscriptions.iterator();

         while (it.hasNext()) {
            SubscribeReturnQos subscribeRetQos = (SubscribeReturnQos)it.next(); 
            try {
               UnSubscribeKey uk = new UnSubscribeKey(glob, subscribeRetQos.getSubscriptionId());
               UnSubscribeQos uq = new UnSubscribeQos(glob);
               //xmlBlasterImpl.unSubscribe(sessionId, uk.toXml(), uq.toXml());
               xmlBlasterCon.unSubscribe(uk.toXml(), uq.toXml());
            }
            catch (XmlBlasterException e) {
               log.warn(ME, "Unsubscribe failed: " + e.toString());
            }
         }

         subscriptions.clear();
      }
   }

   /**
    * Callback from xmlBlaster core
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      if (!this.cbSessionId.equals(cbSessionId)) {
         log.warn(ME, "The given cbSessionId=" + cbSessionId + " is unknown, we don't trust this callback of a status message with oid=" + updateKey.getOid());
         UpdateReturnQos q = new UpdateReturnQos(glob);
         q.setState("ERROR");
         q.setStateInfo("Callback access denied");
         return q.toXml();
      }

      if (updateKey.isInternal()) return "";
      if (updateQos.isErased()) return "";

      String contentStr = new String(content);

      if (!updateQos.isOk()) {
         log.warn(ME, "Receiving unexpected asynchronous status message '" + updateKey.getOid() +
                      "' state=" + updateQos.getState() + " with content='" + contentStr + "'");
         return "";
      }

      log.info(ME, "Receiving asynchronous status message '" + updateKey.getOid() +
                     "' state=" + updateQos.getState() + " with content='" + contentStr + "'");

      // notify listeners ...
      synchronized (oidListenerMap) {
         Set listeners = (Set)oidListenerMap.get(updateKey.getOid());
         if (listeners != null) {
            Iterator it = listeners.iterator();
            while (it.hasNext()) {
               I_Notify callback = (I_Notify)it.next(); 
               callback.statusChanged(contentStr);
            }
         }
         else {
            log.warn(ME, "Receiving asynchronous status message '" + updateKey.getOid() +
              "' state=" + updateQos.getState() + " with content='" + contentStr + "' but nobody is interested in it");
         }
      }
      return "";
   }

   void shutdown(I_Notify callback) {
      unSubscribeStatusMessages(callback);
      synchronized (subscriptionsByNotifierMap) {
         subscriptionsByNotifierMap.remove(callback);
      }
   }
   
   /**
    * @see I_MsgDeliveryInterceptor#shutdown()
    */ 
   synchronized void shutdown() {
      //unSubscribeStatusMessages(); -> disconnect() takes care

      synchronized (subscriptionsByNotifierMap) {
         Iterator it = subscriptionsByNotifierMap.values().iterator();
         while (it.hasNext()) {
            ((Set)it.next()).clear();
         }
         subscriptionsByNotifierMap.clear();
      }
      
      synchronized (oidListenerMap) {
         Iterator it = oidListenerMap.values().iterator();
         while (it.hasNext()) {
            ((Set)it.next()).clear();
         }
         oidListenerMap.clear();
      }

      /*
      if (this.sessionId != null) {
         try { authenticate.disconnect(sessionId, (new DisconnectQos(glob)).toXml()); } catch(XmlBlasterException e) { }
      }
      */
      if (xmlBlasterCon != null) {
         xmlBlasterCon.disconnect(null); // does unsubscribe automatically
         xmlBlasterCon = null;
      }
      log.info(ME, "Native xmlBlaster access stopped, resources released.");
   }
}

