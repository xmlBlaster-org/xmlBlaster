/*------------------------------------------------------------------------------
Name:      ClientDeliveryConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.dispatch;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueConnectEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueDisconnectEntry;
import org.xmlBlaster.client.queuemsg.MsgQueuePublishEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueSubscribeEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueUnSubscribeEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueEraseEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueGetEntry;
import org.xmlBlaster.util.qos.StatusQosData;
import org.xmlBlaster.util.dispatch.DeliveryConnection;
import org.xmlBlaster.util.dispatch.DeliveryManager;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.GetReturnQos;
import org.xmlBlaster.client.protocol.I_XmlBlasterConnection;
import org.xmlBlaster.client.protocol.ProtocolPluginManager;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.authentication.plugins.I_MsgSecurityInterceptor;

import java.io.IOException;


/**
 * Holding all necessary infos to establish callback
 * connections and invoke their update().
 * @see DeliveryConnection
 * @author xmlBlaster@marcelruff.info
 */
public final class ClientDeliveryConnection extends DeliveryConnection
{
   private final String ME;
   private I_XmlBlasterConnection driver;
   private final I_MsgSecurityInterceptor securityInterceptor;
   private String encryptedConnectQos;

   /**
    * @param connectionsHandler The DevliveryConnectionsHandler witch i belong to
    * @param aAddress The address i shall connect to
    */
   public ClientDeliveryConnection(Global glob, ClientDeliveryConnectionsHandler connectionsHandler, AddressBase address) throws XmlBlasterException {
      super(glob, connectionsHandler, address);
      this.ME = "ClientDeliveryConnection-" + connectionsHandler.getDeliveryManager().getQueue().getStorageId();
      this.securityInterceptor = connectionsHandler.getDeliveryManager().getMsgSecurityInterceptor();
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
    * Load the appropriate protocol driver, e.g. the CORBA protocol plugin. 
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
    * @see DeliveryConnection#connectLowlevel()
    */
   public final void connectLowlevel() throws XmlBlasterException {
      if (this.driver == null)
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME, "Sorry, protocol type='" + super.address.getType() + "' is not supported");
      this.driver.connectLowlevel((Address)super.address);
      this.driver.ping("");  // Try a low level ping
      if (log.TRACE) log.trace(ME, "Connected low level to " + super.address.toString());
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
         if (MethodName.PUBLISH_ONEWAY == msgArr_[ii].getMethodName()) {
            MsgQueueEntry[] tmp = new MsgQueueEntry[] { msgArr_[ii] };
            publish(tmp);
         }
         else if (MethodName.PUBLISH == msgArr_[ii].getMethodName()) {
            MsgQueueEntry[] tmp = new MsgQueueEntry[] { msgArr_[ii] };
            publish(tmp);
         }
         else if (MethodName.GET == msgArr_[ii].getMethodName()) {
            get(msgArr_[ii]);
         }
         else if (MethodName.SUBSCRIBE == msgArr_[ii].getMethodName()) {
            subscribe(msgArr_[ii]);
         }
         else if (MethodName.UNSUBSCRIBE == msgArr_[ii].getMethodName()) {
            unSubscribe(msgArr_[ii]);
         }
         else if (MethodName.ERASE == msgArr_[ii].getMethodName()) {
            erase(msgArr_[ii]);
         }
         else if (MethodName.CONNECT == msgArr_[ii].getMethodName()) {
            connect(msgArr_[ii]);
         }
         else if (MethodName.DISCONNECT == msgArr_[ii].getMethodName()) {
            disconnect(msgArr_[ii]);
         }
         else {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "Message type '" + msgArr_[ii].getEmbeddedType() + "' is not implemented");
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
            msgUnitRawArr[i] = securityInterceptor.exportMessage(msgArr[i].getMsgUnitRaw(), MethodName.PUBLISH);
         }
         if (log.TRACE) log.trace(ME, "Exported/encrypted " + msgArr.length + " publish messages.");
      }
      else {
         log.warn(ME+".accessDenied", "No session security context, sending " + msgArr.length + " publish messages without encryption");
         for (int i=0; i<msgArr.length; i++) {
            msgUnitRawArr[i] = msgArr[i].getMsgUnitRaw();
         }
      }

      if (MethodName.PUBLISH_ONEWAY == msgArr_[0].getMethodName()) {
         this.driver.publishOneway(msgUnitRawArr);
         connectionsHandler.getDeliveryStatistic().incrNumPublish(msgUnitRawArr.length);
         if (log.TRACE) log.trace(ME, "Success, sent " + msgArr.length + " oneway publish messages.");
         return;
      }

      if (log.TRACE) log.trace(ME, "Before publish " + msgArr.length + " acknowledged messages ...");

      String[] rawReturnVal = this.driver.publishArr(msgUnitRawArr);
      connectionsHandler.getDeliveryStatistic().incrNumPublish(rawReturnVal.length);

      if (log.TRACE) log.trace(ME, "Success, sent " + msgArr.length + " acknowledged publish messages, return value #1 is '" + rawReturnVal[0] + "'");

      if (rawReturnVal != null) {
         for (int i=0; i<rawReturnVal.length; i++) {
            if (!msgArr_[i].wantReturnObj())
               continue;

            if (securityInterceptor != null) {
               // decrypt ...
               rawReturnVal[i] = securityInterceptor.importMessage(rawReturnVal[i]);
            }

            // create return object
            try {
               msgArr_[i].setReturnObj(new PublishReturnQos(glob, rawReturnVal[i]));
            }
            catch (Throwable e) {
               log.warn(ME, "Can't parse publish returned value '" + rawReturnVal[i] + "', setting to default: " + e.toString());
               //e.printStackTrace();
               msgArr_[i].setReturnObj(new PublishReturnQos(glob, "<qos/>"));
            }
         }
         if (log.TRACE) log.trace(ME, "Imported/decrypted " + rawReturnVal.length + " publish message return values.");
      }
   }

   /**
    * Encrypt and send a subscribe request, decrypt the returned data
    */
   private void subscribe(MsgQueueEntry entry) throws XmlBlasterException {
      MsgQueueSubscribeEntry subscribeEntry = (MsgQueueSubscribeEntry)entry;

      String key = subscribeEntry.getSubscribeKey().toXml();
      String qos = subscribeEntry.getSubscribeQos().toXml();
      if (securityInterceptor != null) {  // We export/encrypt the message (call the interceptor)
         key = securityInterceptor.exportMessage(key);
         qos = securityInterceptor.exportMessage(qos);
         if (log.TRACE) log.trace(ME, "Exported/encrypted subscribe request.");
      }
      else {
         log.warn(ME, "No session security context, subscribe request is not encrypted");
      }

      String rawReturnVal = this.driver.subscribe(key, qos); // Invoke remote server

      connectionsHandler.getDeliveryStatistic().incrNumSubscribe(1);
      
      if (subscribeEntry.wantReturnObj()) {
         if (securityInterceptor != null) { // decrypt return value ...
            rawReturnVal = securityInterceptor.importMessage(rawReturnVal);
         }
         try {
            subscribeEntry.setReturnObj(new SubscribeReturnQos(glob, rawReturnVal));
         }
         catch (Throwable e) {
            log.warn(ME, "Can't parse returned subscribe value '" + rawReturnVal + "', setting to default: " + e.toString());
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
         key = securityInterceptor.exportMessage(key);
         qos = securityInterceptor.exportMessage(qos);
         if (log.TRACE) log.trace(ME, "Exported/encrypted unSubscribe request.");
      }
      else {
         log.warn(ME, "No session security context, unSubscribe request is not encrypted");
      }

      String[] rawReturnValArr = this.driver.unSubscribe(key, qos); // Invoke remote server

      connectionsHandler.getDeliveryStatistic().incrNumUnSubscribe(1);
      
      if (unSubscribeEntry.wantReturnObj()) {
         UnSubscribeReturnQos[] retQosArr = new UnSubscribeReturnQos[rawReturnValArr.length];
         for (int ii=0; ii<rawReturnValArr.length; ii++) {
            if (securityInterceptor != null) { // decrypt return value ...
               String xmlQos = securityInterceptor.importMessage(rawReturnValArr[ii]);
               retQosArr[ii] = new UnSubscribeReturnQos(glob, xmlQos);
            }
         }

         try {
            unSubscribeEntry.setReturnObj(retQosArr);
         }
         catch (Throwable e) {
            log.warn(ME, "Can't parse returned unSubscribe value setting to default: " + e.toString());
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
         key = this.securityInterceptor.exportMessage(key);
         qos = this.securityInterceptor.exportMessage(qos);
         if (log.TRACE) log.trace(ME, "Exported/encrypted get request.");
      }
      else {
         log.warn(ME, "No session security context, get request is not encrypted");
      }

      MsgUnitRaw[] rawReturnValArr = this.driver.get(key, qos); // Invoke remote server

      connectionsHandler.getDeliveryStatistic().incrNumGet(1);
      
      MsgUnit[] msgUnitArr = new MsgUnit[rawReturnValArr.length];
      if (getEntry.wantReturnObj()) {
         for (int ii=0; ii<rawReturnValArr.length; ii++) {
            if (this.securityInterceptor != null) { // decrypt return value ...
               rawReturnValArr[ii] = this.securityInterceptor.importMessage(rawReturnValArr[ii], MethodName.PUBLISH);
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
         key = securityInterceptor.exportMessage(key);
         qos = securityInterceptor.exportMessage(qos);
         if (log.TRACE) log.trace(ME, "Exported/encrypted erase request.");
      }
      else {
         log.warn(ME, "No session security context, erase request is not encrypted");
      }

      String[] rawReturnValArr = this.driver.erase(key, qos); // Invoke remote server

      connectionsHandler.getDeliveryStatistic().incrNumErase(1);
      
      if (eraseEntry.wantReturnObj()) {
         EraseReturnQos[] retQosArr = new EraseReturnQos[rawReturnValArr.length];
         for (int ii=0; ii<rawReturnValArr.length; ii++) {
            if (securityInterceptor != null) { // decrypt return value ...
               String xmlQos = securityInterceptor.importMessage(rawReturnValArr[ii]);
               retQosArr[ii] = new EraseReturnQos(glob, xmlQos);
            }
         }

         try {
            eraseEntry.setReturnObj(retQosArr);
         }
         catch (Throwable e) {
            log.warn(ME, "Can't parse returned erase value setting to default: " + e.toString());
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
      String qosOrig = connectEntry.getConnectQos().toXml();
      String qos;
      if (securityInterceptor != null) {  // We export/encrypt the message (call the interceptor)
         qos = securityInterceptor.exportMessage(qosOrig);
         if (log.TRACE) log.trace(ME, "Exported/encrypted connect request.");
      }
      else {
         log.warn(ME, "No session security context, connect request is not encrypted");
         qos = qosOrig;
      }

      this.encryptedConnectQos = qos;
      String rawReturnVal = this.driver.connect(qos); // Invoke remote server

      connectionsHandler.getDeliveryStatistic().incrNumConnect(1);
      
      if (securityInterceptor != null) { // decrypt return value ...
         rawReturnVal = securityInterceptor.importMessage(rawReturnVal);
      }

      ConnectReturnQos connectReturnQos = null;
      try {
         connectReturnQos = new ConnectReturnQos(glob, rawReturnVal);
      }
      catch (Throwable e) {
         log.warn(ME, "Can't parse returned connect QoS value '" + rawReturnVal + "', setting to default: " + e.toString());
         connectReturnQos = new ConnectReturnQos(glob, "<qos/>");
      }

      if (connectEntry.wantReturnObj()) {
         connectEntry.setReturnObj(connectReturnQos);
      }
      this.driver.setConnectReturnQos(connectReturnQos);
   }

   /**
    * Encrypt and send a disconnect request, decrypt the returned data
    */
   private void disconnect(MsgQueueEntry entry) throws XmlBlasterException {
      MsgQueueDisconnectEntry disconnectEntry = (MsgQueueDisconnectEntry)entry;
      String qos = disconnectEntry.getDisconnectQos().toXml();
      if (securityInterceptor != null) {  // We export/encrypt the message (call the interceptor)
         qos = securityInterceptor.exportMessage(qos);
         if (log.TRACE) log.trace(ME, "Exported/encrypted disconnect request.");
      }
      else {
         log.warn(ME, "No session security context, disconnect request is not encrypted");
      }

      //returns void
      this.driver.disconnect(qos); // Invoke remote server
   }

   /**
    * Ping the callback server of the client. 
    * @param data never null
    */
   public final String doPing(String data) throws XmlBlasterException {
      return driver.ping(data);
   }

   /**
    * Nothing to do here
    */
   public final void resetConnection() {
      if (log.TRACE) log.trace(ME, "Initializing driver for polling");
      this.driver.resetConnection();
   }

   /**
    * On reconnect polling try to establish the connection. 
    */
   protected final void reconnect() throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering reconnect(" + this.driver.getProtocol() + ")");
      if (this.driver == null) return;

      if (this.encryptedConnectQos == null) {
         // We never had connected on application layer, so try low level layer only
         this.driver.connectLowlevel((Address)super.address);
         return;
      }

      // low level connect (e.g. on TCP/IP layer) and remote invoke method connect()
      String rawReturnVal = this.driver.connect(this.encryptedConnectQos); // Invoke remote server

      connectionsHandler.getDeliveryStatistic().incrNumConnect(1);
      
      if (securityInterceptor != null) { // decrypt return value ...
         rawReturnVal = securityInterceptor.importMessage(rawReturnVal);
      }

      ConnectReturnQos connectReturnQos = null;
      try {
         connectReturnQos = new ConnectReturnQos(glob, rawReturnVal);
      }
      catch (Throwable e) {
         log.warn(ME, "Can't parse returned connect QoS value '" + rawReturnVal + "', setting to default: " + e.toString());
         connectReturnQos = new ConnectReturnQos(glob, "<qos/>");
      }
      this.driver.setConnectReturnQos(connectReturnQos);
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

      sb.append(offset + "<ClientDeliveryConnection>");
      super.address.toXml(" " + offset);
      if (driver == null)
         sb.append(offset).append(" <noProtocolDriver />");
      else
         sb.append(offset).append(" <address type='" + driver.getProtocol() + "' state='" + getState() + "'/>");
      sb.append(offset).append("</ClientDeliveryConnection>");

      return sb.toString();
   }
}

