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
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.queuemsg.MsgQueueConnectEntry;
import org.xmlBlaster.util.queuemsg.MsgQueuePublishEntry;
import org.xmlBlaster.util.qos.StatusQosData;
import org.xmlBlaster.util.dispatch.DeliveryConnection;
import org.xmlBlaster.util.dispatch.DeliveryManager;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
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
   private ConnectReturnQos connectReturnQos;

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
      return (this.driver != null) ? this.driver.getLoginName() : "unknown";
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
   public final void initDriver() throws XmlBlasterException {
      ProtocolPluginManager loader = glob.getProtocolPluginManager();
      this.driver = loader.getPlugin(super.address.getType(), super.address.getVersion()); // e.g. CorbaConnection(glob);
   }

   /**
    * Send the messages to xmlBlaster. 
    * @param msgArr The messages to send. 
    * @return The returned data [] from xmlBlaster with same length as msgArr_,
    *         for oneway invocations it is null
    */
   public Object doSend(MsgQueueEntry[] msgArr_) throws XmlBlasterException {
      if (msgArr_.length < 1) {
         return null;
      }

      MsgQueueEntry first = msgArr_[0];
      if (MethodName.CONNECT == first.getMethodName()) {
         MsgQueueConnectEntry entry = (MsgQueueConnectEntry)first;
         this.connectReturnQos = driver.connect(entry.getConnectQos());
         return new ConnectReturnQos[] { this.connectReturnQos };
      }
      else if (MethodName.PUBLISH_ONEWAY == first.getMethodName()) {
         return publish(msgArr_);
      }
      else if (MethodName.PUBLISH == first.getMethodName()) {
         return publish(msgArr_);
      }
      else {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "Message type '" + first.getEmbeddedType() + "' is not implemented");
      }
   }

   private Object publish(MsgQueueEntry[] msgArr_) throws XmlBlasterException {

      // Convert to PublishEntry
      MsgUnit[] msgArr = new MsgUnit[msgArr_.length];

      // The update QoS is completed ...
      for (int i=0; i<msgArr.length; i++) {
         MsgQueuePublishEntry publishEntry = (MsgQueuePublishEntry)msgArr_[i];
         msgArr[i] = publishEntry.getMsgUnit();
      }

      MsgUnitRaw[] msgUnitRawArr = new MsgUnitRaw[msgArr.length];
      // We export/encrypt the message (call the interceptor)
      if (securityInterceptor != null) {
         for (int i=0; i<msgArr.length; i++) {
            msgUnitRawArr[i] = securityInterceptor.exportMessage(msgArr[i].getMsgUnitRaw());
         }
         if (log.TRACE) log.trace(ME, "Exported/encrypted " + msgArr.length + " messages.");
      }
      else {
         log.warn(ME+".accessDenied", "No session security context, sending " + msgArr.length + " messages without encryption");
         for (int i=0; i<msgArr.length; i++) {
            msgUnitRawArr[i] = msgArr[i].getMsgUnitRaw();
         }
      }

      String[] rawReturnVal = null;
      //if (address.oneway()) {
      //   cbDriver.publishOneway(msgArr);
      //   if (log.TRACE) log.trace(ME, "Success, sent " + msgArr.length + " oneway messages.");
      //}
      //else {
         if (log.TRACE) log.trace(ME, "Before update " + msgArr.length + " acknowledged messages ...");
         rawReturnVal = this.driver.publishArr(msgUnitRawArr);
         if (log.TRACE) log.trace(ME, "Success, sent " + msgArr.length + " acknowledged messages, return value #1 is '" + rawReturnVal[0] + "'");
      //}

      connectionsHandler.getDeliveryStatistic().incrNumPublish(rawReturnVal.length);

      PublishReturnQos[] returnObjects = null;
      if (rawReturnVal != null) {
         returnObjects = new PublishReturnQos[rawReturnVal.length];
         for (int i=0; i<rawReturnVal.length; i++) {
            if (securityInterceptor != null) {
               // decrypt ...
               rawReturnVal[i] = securityInterceptor.importMessage(rawReturnVal[i]);
            }

            // create object
            try {
               returnObjects[i] = new PublishReturnQos(glob, rawReturnVal[i]);
            }
            catch (Throwable e) {
               log.warn(ME, "Can't parse returned value '" + rawReturnVal[i] + "', setting to default: " + e.toString());
               //e.printStackTrace();
               returnObjects[i] = new PublishReturnQos(glob, "<qos/>");
            }
         }
         if (log.TRACE) log.trace(ME, "Imported/decrypted " + rawReturnVal.length + " message return values.");
      }

      return returnObjects;
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
      log.info(ME, "Entering reconnect(" + this.driver + ")");
      if (this.driver != null)
         this.connectReturnQos = this.driver.loginRaw();
   }

   /**
    * Stop all callback drivers of this client.
    */
   public final void shutdown() {
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
   public final String toXml(String extraOffset) throws XmlBlasterException {
      StringBuffer sb = new StringBuffer(256);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<ClientDeliveryConnection>");
      address.toXml("   " + offset);
      if (driver == null)
         sb.append(offset).append("   <noProtocolDriver />");
      else
         sb.append(offset).append("   <address type='" + driver.getLoginName() + "' state='" + getState() + "'/>");
      sb.append(offset).append("</ClientDeliveryConnection>");

      return sb.toString();
   }
}

