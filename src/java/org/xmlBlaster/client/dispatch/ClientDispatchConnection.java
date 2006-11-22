/*------------------------------------------------------------------------------
Name:      ClientDispatchConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.dispatch;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueConnectEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueDisconnectEntry;
import org.xmlBlaster.client.queuemsg.MsgQueuePublishEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueSubscribeEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueUnSubscribeEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueEraseEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueGetEntry;
import org.xmlBlaster.util.dispatch.DispatchConnection;
import org.xmlBlaster.util.dispatch.I_PostSendListener;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.protocol.I_XmlBlasterConnection;
import org.xmlBlaster.client.protocol.ProtocolPluginManager;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.xbformat.I_ProgressListener;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.authentication.plugins.CryptDataHolder;
import org.xmlBlaster.authentication.plugins.I_MsgSecurityInterceptor;


/**
 * Holding all necessary infos to establish callback
 * connections and invoke their update().
 * @see DispatchConnection
 * @author xmlBlaster@marcelruff.info
 */
public final class ClientDispatchConnection extends DispatchConnection
{
   private static Logger log = Logger.getLogger(ClientDispatchConnection.class.getName());
   private final String ME;
   private I_XmlBlasterConnection driver;
   private final I_MsgSecurityInterceptor securityInterceptor;
   private String encryptedConnectQos;
   private ConnectReturnQos connectReturnQos;
   private MsgQueueEntry connectEntry;

   /**
    * @param connectionsHandler The DevliveryConnectionsHandler witch i belong to
    * @param aAddress The address i shall connect to
    */
   public ClientDispatchConnection(Global glob, ClientDispatchConnectionsHandler connectionsHandler, AddressBase address) throws XmlBlasterException {
      super(glob, connectionsHandler, address);
      this.ME = "ClientDispatchConnection-" + connectionsHandler.getDispatchManager().getQueue().getStorageId();
      this.securityInterceptor = connectionsHandler.getDispatchManager().getMsgSecurityInterceptor();
   }

   public final String getDriverName() {
      return (this.driver != null) ? this.driver.getProtocol() : "unknown";
   }

   /**
    * @return A nice name for logging
    */
   public final String getName() {
      return ME;
   }

   /**
    * Load the appropriate protocol driver, e.g the CORBA protocol plugin. 
    * <p>
    * This method is called by our base class during initialization.
    * </p>
    */
   public final void loadPlugin() throws XmlBlasterException {
      ProtocolPluginManager loader = glob.getProtocolPluginManager();
      this.driver = loader.getPlugin(super.address.getType(), super.address.getVersion()); // e.g. CorbaConnection(glob);
      if (this.driver == null)
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME, "Sorry, protocol type='" + super.address.getType() + "' is not supported");
   }

   /**
    * @see DispatchConnection#connectLowlevel()
    */
   public final void connectLowlevel() throws XmlBlasterException {
      if (this.driver == null)
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME, "Sorry, protocol type='" + super.address.getType() + "' is not supported");
      this.driver.connectLowlevel((Address)super.address);
      if (super.address.getPingInterval() > 0) {
         //spanPingTimer(1, true); // Could deadlock as it uses complete dispatch framework with its synchronized?
         this.driver.ping("<qos><state info='"+Constants.INFO_INITIAL+"'/></qos>");  // Try a low level ping
      }
      if (log.isLoggable(Level.FINE)) log.fine("Connected low level to " + super.address.toString());
   }

   /**
    * Send the messages to xmlBlaster. 
    * @param msgArr The messages to send.
    *  msgArr[i].getReturnVal() will contain the returned QoS object or null for oneway operations
    */
   public void doSend(MsgQueueEntry[] msgArr_) throws XmlBlasterException {
      if (msgArr_.length < 1) {
         return;
      }

      boolean onlyPublish = true;
      boolean onlyPublishOneway = true;
      for (int ii=0; ii<msgArr_.length; ii++) {
         if (MethodName.PUBLISH_ONEWAY != msgArr_[ii].getMethodName())
            onlyPublishOneway = false;
         if (MethodName.PUBLISH != msgArr_[ii].getMethodName())
            onlyPublish = false;
      }
      if (onlyPublishOneway || onlyPublish) {
         publish(msgArr_);
         return;
      }
      
      for (int ii=0; ii<msgArr_.length; ii++) {
         try {
            if (MethodName.PUBLISH_ONEWAY == msgArr_[ii].getMethodName()) {
               MsgQueueEntry[] tmp = new MsgQueueEntry[] { msgArr_[ii] };
               publish(tmp);
               //Not for oneway
               //if (postSendListener != null) postSendListener.postSend(msgArr_[ii]);
            }
            else if (MethodName.PUBLISH == msgArr_[ii].getMethodName()) {
               MsgQueueEntry[] tmp = new MsgQueueEntry[] { msgArr_[ii] };
               publish(tmp);
               I_PostSendListener postSendListener = this.connectionsHandler.getPostSendListener();
               if (postSendListener != null) postSendListener.postSend(msgArr_[ii]);
            }
            else if (MethodName.GET == msgArr_[ii].getMethodName()) {
               get(msgArr_[ii]);
               I_PostSendListener postSendListener = this.connectionsHandler.getPostSendListener();
               if (postSendListener != null) postSendListener.postSend(msgArr_[ii]);
            }
            else if (MethodName.SUBSCRIBE == msgArr_[ii].getMethodName()) {
               subscribe(msgArr_[ii]);
               I_PostSendListener postSendListener = this.connectionsHandler.getPostSendListener();
               if (postSendListener != null) postSendListener.postSend(msgArr_[ii]);
            }
            else if (MethodName.UNSUBSCRIBE == msgArr_[ii].getMethodName()) {
               unSubscribe(msgArr_[ii]);
               I_PostSendListener postSendListener = this.connectionsHandler.getPostSendListener();
               if (postSendListener != null) postSendListener.postSend(msgArr_[ii]);
            }
            else if (MethodName.ERASE == msgArr_[ii].getMethodName()) {
               erase(msgArr_[ii]);
               I_PostSendListener postSendListener = this.connectionsHandler.getPostSendListener();
               if (postSendListener != null) postSendListener.postSend(msgArr_[ii]);
            }
            else if (MethodName.CONNECT == msgArr_[ii].getMethodName()) {
               connect(msgArr_[ii]);
               this.connectEntry = msgArr_[ii]; // remember it
               I_PostSendListener postSendListener = this.connectionsHandler.getPostSendListener();
               if (postSendListener != null) postSendListener.postSend(msgArr_[ii]);
            }
            else if (MethodName.DISCONNECT == msgArr_[ii].getMethodName()) {
               this.connectEntry = null;
               disconnect(msgArr_[ii]);
               I_PostSendListener postSendListener = this.connectionsHandler.getPostSendListener();
               if (postSendListener != null) postSendListener.postSend(msgArr_[ii]);
            }
            else {
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "Message type '" + msgArr_[ii].getEmbeddedType() + "' is not implemented");
            }
         }
         catch (XmlBlasterException e) {
            if (this.connectEntry != null && e.isErrorCode(ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED)) {
               // Happens if the client was killed in the server by an admin task
               // and has tried to reconnect with the old sessionId
               log.warning("Server changed sessionId, trying reconnect now: " + e.toString());
               //reconnect();   // loops?!
               connect(this.connectEntry);
               I_PostSendListener postSendListener = this.connectionsHandler.getPostSendListener();
               if (postSendListener != null) postSendListener.postSend(this.connectEntry);
               if (log.isLoggable(Level.FINE)) log.fine("Server changed sessionId to " + this.connectReturnQos.getServerInstanceId());
               ii--;
            }
            else {
               throw e;
            }
         }
      }
   }

   private void publish(MsgQueueEntry[] msgArr_) throws XmlBlasterException {

      // Convert to PublishEntry
      MsgUnit[] msgArr = new MsgUnit[msgArr_.length];
      for (int i=0; i<msgArr.length; i++) {
         MsgQueuePublishEntry publishEntry = (MsgQueuePublishEntry)msgArr_[i];
         msgArr[i] = publishEntry.getMsgUnit();
      }

      MsgUnitRaw[] msgUnitRawArr = new MsgUnitRaw[msgArr.length];
      // We export/encrypt the message (call the interceptor)
      if (securityInterceptor != null) {
         for (int i=0; i<msgArr.length; i++) {
            CryptDataHolder dataHolder = new CryptDataHolder(MethodName.PUBLISH, msgArr[i].getMsgUnitRaw());
            msgUnitRawArr[i] = securityInterceptor.exportMessage(dataHolder);
         }
         if (log.isLoggable(Level.FINE)) log.fine("Exported/encrypted " + msgArr.length + " publish messages.");
      }
      else {
         log.warning("No session security context, sending " + msgArr.length + " publish messages without encryption");
         for (int i=0; i<msgArr.length; i++) {
            msgUnitRawArr[i] = msgArr[i].getMsgUnitRaw();
         }
      }

      if (MethodName.PUBLISH_ONEWAY == msgArr_[0].getMethodName()) {
         this.driver.publishOneway(msgUnitRawArr);
         connectionsHandler.getDispatchStatistic().incrNumPublish(msgUnitRawArr.length);
         if (log.isLoggable(Level.FINE)) log.fine("Success, sent " + msgArr.length + " oneway publish messages.");
         return;
      }

      if (log.isLoggable(Level.FINE)) log.fine("Before publish " + msgArr.length + " acknowledged messages ...");

      String[] rawReturnVal = this.driver.publishArr(msgUnitRawArr);
      connectionsHandler.getDispatchStatistic().incrNumPublish(rawReturnVal.length);

      if (log.isLoggable(Level.FINE)) log.fine("Success, sent " + msgArr.length + " acknowledged publish messages, return value #1 is '" + rawReturnVal[0] + "'");

      if (rawReturnVal != null) {
         for (int i=0; i<rawReturnVal.length; i++) {
            if (!msgArr_[i].wantReturnObj())
               continue;

            if (securityInterceptor != null) {
               CryptDataHolder dataHolder = new CryptDataHolder(MethodName.PUBLISH, new MsgUnitRaw(null, (byte[])null, rawReturnVal[i]));
               dataHolder.setReturnValue(true);
               rawReturnVal[i] = securityInterceptor.importMessage(dataHolder).getQos();
            }

            // create return object
            try {
               msgArr_[i].setReturnObj(new PublishReturnQos(glob, rawReturnVal[i]));
            }
            catch (Throwable e) {
               log.warning("Can't parse publish returned value '" + rawReturnVal[i] + "', setting to default: " + e.toString());
               //e.printStackTrace();
               msgArr_[i].setReturnObj(new PublishReturnQos(glob, "<qos/>"));
            }
         }
         if (log.isLoggable(Level.FINE)) log.fine("Imported/decrypted " + rawReturnVal.length + " publish message return values.");
      }
   }

   /**
    * Encrypt and send a subscribe request, decrypt the returned data
    */
   private void subscribe(MsgQueueEntry entry) throws XmlBlasterException {
      MsgQueueSubscribeEntry subscribeEntry = (MsgQueueSubscribeEntry)entry;

      String key = subscribeEntry.getSubscribeKeyData().toXml();
      String qos = subscribeEntry.getSubscribeQosData().toXml();
      if (securityInterceptor != null) {  // We export/encrypt the message (call the interceptor)
         CryptDataHolder dataHolder = new CryptDataHolder(MethodName.SUBSCRIBE, new MsgUnitRaw(key, (byte[])null, qos));
         MsgUnitRaw msgUnitRaw = securityInterceptor.exportMessage(dataHolder);
         key = msgUnitRaw.getKey();
         qos = msgUnitRaw.getQos();
         if (log.isLoggable(Level.FINE)) log.fine("Exported/encrypted subscribe request.");
      }
      else {
         log.warning("No session security context, subscribe request is not encrypted");
      }

      String rawReturnVal = this.driver.subscribe(key, qos); // Invoke remote server

      connectionsHandler.getDispatchStatistic().incrNumSubscribe(1);
      
      if (subscribeEntry.wantReturnObj()) {
         if (securityInterceptor != null) { // decrypt return value ...
            CryptDataHolder dataHolder = new CryptDataHolder(MethodName.SUBSCRIBE, new MsgUnitRaw(null, (byte[])null, rawReturnVal));
            dataHolder.setReturnValue(true);
            rawReturnVal = securityInterceptor.importMessage(dataHolder).getQos();
         }
         try {
            subscribeEntry.setReturnObj(new SubscribeReturnQos(glob, rawReturnVal));
         }
         catch (Throwable e) {
            log.warning("Can't parse returned subscribe value '" + rawReturnVal + "', setting to default: " + e.toString());
            subscribeEntry.setReturnObj(new SubscribeReturnQos(glob, "<qos/>"));
         }
      }
   }

   /**
    * Encrypt and send a unSubscribe request, decrypt the returned data
    */
   private void unSubscribe(MsgQueueEntry entry) throws XmlBlasterException {
      MsgQueueUnSubscribeEntry unSubscribeEntry = (MsgQueueUnSubscribeEntry)entry;

      String key = unSubscribeEntry.getUnSubscribeKey().toXml();
      String qos = unSubscribeEntry.getUnSubscribeQos().toXml();
      if (securityInterceptor != null) {  // We export/encrypt the message (call the interceptor)
         CryptDataHolder dataHolder = new CryptDataHolder(MethodName.UNSUBSCRIBE, new MsgUnitRaw(key, (byte[])null, qos));
         MsgUnitRaw msgUnitRaw = securityInterceptor.exportMessage(dataHolder);
         key = msgUnitRaw.getKey();
         qos = msgUnitRaw.getQos();
         if (log.isLoggable(Level.FINE)) log.fine("Exported/encrypted unSubscribe request.");
      }
      else {
         log.warning("No session security context, unSubscribe request is not encrypted");
      }

      String[] rawReturnValArr = this.driver.unSubscribe(key, qos); // Invoke remote server

      connectionsHandler.getDispatchStatistic().incrNumUnSubscribe(1);
      
      if (unSubscribeEntry.wantReturnObj()) {
         UnSubscribeReturnQos[] retQosArr = new UnSubscribeReturnQos[rawReturnValArr.length];
         for (int ii=0; ii<rawReturnValArr.length; ii++) {
            if (securityInterceptor != null) { // decrypt return value ...
               CryptDataHolder dataHolder = new CryptDataHolder(MethodName.UNSUBSCRIBE, new MsgUnitRaw(null, (byte[])null, rawReturnValArr[ii]));
               dataHolder.setReturnValue(true);
               String xmlQos = securityInterceptor.importMessage(dataHolder).getQos();
               retQosArr[ii] = new UnSubscribeReturnQos(glob, xmlQos);
            }
         }

         try {
            unSubscribeEntry.setReturnObj(retQosArr);
         }
         catch (Throwable e) {
            log.warning("Can't parse returned unSubscribe value setting to default: " + e.toString());
            for (int ii=0; ii<rawReturnValArr.length; ii++) {
               retQosArr[ii] = new UnSubscribeReturnQos(glob, "<qos/>");
            }
            unSubscribeEntry.setReturnObj(retQosArr);
         }
      }
   }

   /**
    * Encrypt and send a synchronous get request, decrypt the returned data
    */
   private void get(MsgQueueEntry entry) throws XmlBlasterException {
      MsgQueueGetEntry getEntry = (MsgQueueGetEntry)entry;

      String key = getEntry.getGetKey().toXml();
      String qos = getEntry.getGetQos().toXml();
      if (this.securityInterceptor != null) {  // We export/encrypt the message (call the interceptor)
         CryptDataHolder dataHolder = new CryptDataHolder(MethodName.GET, new MsgUnitRaw(key, (byte[])null, qos));
         MsgUnitRaw msgUnitRaw = securityInterceptor.exportMessage(dataHolder);
         key = msgUnitRaw.getKey();
         qos = msgUnitRaw.getQos();
         if (log.isLoggable(Level.FINE)) log.fine("Exported/encrypted get request.");
      }
      else {
         log.warning("No session security context, get request is not encrypted");
      }

      MsgUnitRaw[] rawReturnValArr = this.driver.get(key, qos); // Invoke remote server

      connectionsHandler.getDispatchStatistic().incrNumGet(1);
      
      MsgUnit[] msgUnitArr = new MsgUnit[rawReturnValArr.length];
      if (getEntry.wantReturnObj()) {
         for (int ii=0; ii<rawReturnValArr.length; ii++) {
            if (this.securityInterceptor != null) { // decrypt return value ...
               CryptDataHolder dataHolder = new CryptDataHolder(MethodName.GET, rawReturnValArr[ii]);
               dataHolder.setReturnValue(true);
               rawReturnValArr[ii] = securityInterceptor.importMessage(dataHolder);
            }
            // NOTE: We use PUBLISH here instead of GET_RETURN to have the whole MsgUnit stored
            msgUnitArr[ii] = new MsgUnit(glob, rawReturnValArr[ii], MethodName.PUBLISH);
         }

         getEntry.setReturnObj(msgUnitArr);
      }
   }

   /**
    * Encrypt and send a erase request, decrypt the returned data
    */
   private void erase(MsgQueueEntry entry) throws XmlBlasterException {
      MsgQueueEraseEntry eraseEntry = (MsgQueueEraseEntry)entry;

      String key = eraseEntry.getEraseKey().toXml();
      String qos = eraseEntry.getEraseQos().toXml();
      if (securityInterceptor != null) {  // We export/encrypt the message (call the interceptor)
         CryptDataHolder dataHolder = new CryptDataHolder(MethodName.ERASE, new MsgUnitRaw(key, (byte[])null, qos));
         MsgUnitRaw msgUnitRaw = securityInterceptor.exportMessage(dataHolder);
         key = msgUnitRaw.getKey();
         qos = msgUnitRaw.getQos();
         if (log.isLoggable(Level.FINE)) log.fine("Exported/encrypted erase request.");
      }
      else {
         log.warning("No session security context, erase request is not encrypted");
      }

      String[] rawReturnValArr = this.driver.erase(key, qos); // Invoke remote server

      connectionsHandler.getDispatchStatistic().incrNumErase(1);
      
      if (eraseEntry.wantReturnObj()) {
         EraseReturnQos[] retQosArr = new EraseReturnQos[rawReturnValArr.length];
         for (int ii=0; ii<rawReturnValArr.length; ii++) {
            if (securityInterceptor != null) { // decrypt return value ...
               CryptDataHolder dataHolder = new CryptDataHolder(MethodName.ERASE, new MsgUnitRaw(null, (byte[])null, rawReturnValArr[ii]));
               dataHolder.setReturnValue(true);
               String xmlQos = securityInterceptor.importMessage(dataHolder).getQos();
               retQosArr[ii] = new EraseReturnQos(glob, xmlQos);
            }
         }

         try {
            eraseEntry.setReturnObj(retQosArr);
         }
         catch (Throwable e) {
            log.warning("Can't parse returned erase value setting to default: " + e.toString());
            for (int ii=0; ii<rawReturnValArr.length; ii++) {
               retQosArr[ii] = new EraseReturnQos(glob, "<qos/>");
            }
            eraseEntry.setReturnObj(retQosArr);
         }
      }
   }

   /**
    * Encrypt and send a connect request, decrypt the returned data
    */
   private void connect(MsgQueueEntry entry) throws XmlBlasterException {
      MsgQueueConnectEntry connectEntry = (MsgQueueConnectEntry)entry;
      if (securityInterceptor != null) {  // We export/encrypt the message (call the interceptor)
         log.fine("TODO: Crypting msg with exportMessage() is not supported for connect() as the server currently can't handle encrypted ConnectQos (for SOCKET see HandleClient.java:234)");
         CryptDataHolder dataHolder = new CryptDataHolder(MethodName.CONNECT, new MsgUnitRaw(null, (byte[])null, connectEntry.getConnectQosData().toXml()));
         this.encryptedConnectQos = securityInterceptor.exportMessage(dataHolder).getQos();
         if (log.isLoggable(Level.FINE)) log.fine("Exported/encrypted connect request.");
      }
      else {
         log.warning("No session security context, connect request is not encrypted");
         this.encryptedConnectQos = connectEntry.getConnectQosData().toXml();
      }

      String rawReturnVal = this.driver.connect(this.encryptedConnectQos); // Invoke remote server

      connectionsHandler.getDispatchStatistic().incrNumConnect(1);
      
      if (securityInterceptor != null) { // decrypt return value ...
         CryptDataHolder dataHolder = new CryptDataHolder(MethodName.CONNECT, new MsgUnitRaw(null, (byte[])null, rawReturnVal));
         dataHolder.setReturnValue(true);
         rawReturnVal = securityInterceptor.importMessage(dataHolder).getQos();
      }

      try {
         this.connectReturnQos = new ConnectReturnQos(glob, rawReturnVal);
      }
      catch (XmlBlasterException e) {
         log.severe("Can't parse returned connect QoS value '" + rawReturnVal + "': " + e.getMessage());
         throw e;
      }

      if (!connectEntry.getConnectQosData().getSessionName().isSession()) {
         // We need to remember the server side assigned public session id for reconnect polling
         // If do we should probably take a clone:
         //ConnectQos connectQos = new ConnectQos(this.glob, this.connectReturnQos.getData());
         ConnectQosData connectQos = connectEntry.getConnectQosData();
         connectQos.setSessionName(this.connectReturnQos.getSessionName());
         connectQos.getSessionQos().setSecretSessionId(this.connectReturnQos.getSecretSessionId());
         if (securityInterceptor != null) {  // We export/encrypt the message (call the interceptor)
            CryptDataHolder dataHolder = new CryptDataHolder(MethodName.CONNECT, new MsgUnitRaw(null, (byte[])null, connectQos.toXml()));
            this.encryptedConnectQos = securityInterceptor.exportMessage(dataHolder).getQos();
            if (log.isLoggable(Level.FINE)) log.fine("Exported/encrypted connect request.");
         }
         else {
            log.warning("No session security context, connect request is not encrypted");
            this.encryptedConnectQos = connectQos.toXml();
         }
      }

      if (connectEntry.wantReturnObj()) {
         connectEntry.setReturnObj(this.connectReturnQos);
      }
      this.driver.setConnectReturnQos(this.connectReturnQos);
   }

   /**
    * Encrypt and send a disconnect request, decrypt the returned data
    */
   private void disconnect(MsgQueueEntry entry) throws XmlBlasterException {
      MsgQueueDisconnectEntry disconnectEntry = (MsgQueueDisconnectEntry)entry;
      String qos = disconnectEntry.getDisconnectQos().toXml();
      if (securityInterceptor != null) {  // We export/encrypt the message (call the interceptor)
         CryptDataHolder dataHolder = new CryptDataHolder(MethodName.DISCONNECT, new MsgUnitRaw(null, (byte[])null, qos));
         qos = securityInterceptor.exportMessage(dataHolder).getQos();
         if (log.isLoggable(Level.FINE)) log.fine("Exported/encrypted disconnect request.");
      }
      else {
         log.warning("No session security context, disconnect request is not encrypted");
      }

      //returns void
      this.driver.disconnect(qos); // Invoke remote server
   }

   /**
    * @see org.xmlBlaster.util.dispatch.DispatchConnection#doPing(String)
    */
   public final String doPing(String data) throws XmlBlasterException {
      String ret = driver.ping(data);
      return (ret==null) ? "" : ret;
   }

   /**
    * Nothing to do here
    */
   public final void resetConnection() {
      if (log.isLoggable(Level.FINE)) log.fine("resetConnection(): Initializing driver for polling");
      this.connectReturnQos = null;
      this.driver.resetConnection();
   }

   /**
    * On reconnect polling try to establish the connection. 
    */
   protected final void reconnect() throws XmlBlasterException {
      if (this.driver == null) return;
      if (log.isLoggable(Level.FINER)) log.finer("Entering reconnect(" + this.driver.getProtocol() + ")");

      if (this.connectReturnQos != null) {
         super.ping("", false);
         return;
      }

      if (this.encryptedConnectQos == null) {
         // We never had connected on application layer, so try low level layer only
         this.driver.connectLowlevel((Address)super.address);
         return;
      }

      // low level connect (e.g. on TCP/IP layer) and remote invoke method connect()
      String rawReturnVal = this.driver.connect(this.encryptedConnectQos); // Invoke remote server

      connectionsHandler.getDispatchStatistic().incrNumConnect(1);
      
      if (securityInterceptor != null) { // decrypt return value ...
         CryptDataHolder dataHolder = new CryptDataHolder(MethodName.CONNECT, new MsgUnitRaw(null, (byte[])null, rawReturnVal));
         dataHolder.setReturnValue(true);
         rawReturnVal = securityInterceptor.importMessage(dataHolder).getQos();
      }

      this.connectReturnQos = null;
      try {
         this.connectReturnQos = new ConnectReturnQos(glob, rawReturnVal);
         if (this.connectEntry != null) {
            if (this.connectEntry.wantReturnObj()) {
               this.connectEntry.setReturnObj(this.connectReturnQos);
            }
            I_PostSendListener postSendListener = this.connectionsHandler.getPostSendListener();
            if (postSendListener != null) postSendListener.postSend(this.connectEntry);
         }
      }
      catch (XmlBlasterException e) {
         log.severe("reconnect(): Can't parse returned connect QoS value '" + rawReturnVal + "': " + e.getMessage());
         throw e;
      }
      this.driver.setConnectReturnQos(this.connectReturnQos);
   }

   /**
    * Stop all callback drivers of this client.
    */
   public final void shutdown() throws XmlBlasterException {
      super.shutdown();
      if (driver != null) {
         driver.shutdown();
      }
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state as an XML ASCII string
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset + "<ClientDispatchConnection>");
      super.address.toXml(" " + offset);
      if (driver == null)
         sb.append(offset).append(" <noProtocolDriver />");
      else
         sb.append(offset).append(" <address type='" + driver.getProtocol() + "' state='" + getState() + "'/>");
      sb.append(offset).append("</ClientDispatchConnection>");

      return sb.toString();
   }
   
   public I_ProgressListener registerProgressListener(I_ProgressListener listener) {
      if (this.driver == null) return null;
      return this.driver.registerProgressListener(listener);
   }

   protected boolean forcePingFailure() {
      return true;
   }
   
}

