/*------------------------------------------------------------------------------
Name:      CbDispatchConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.dispatch;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.engine.qos.UpdateReturnQosServer;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.engine.queuemsg.MsgQueueUpdateEntry;
import org.xmlBlaster.util.dispatch.DispatchConnection;
import org.xmlBlaster.authentication.plugins.I_MsgSecurityInterceptor;
import org.xmlBlaster.util.def.MethodName;


/**
 * Holding all necessary infos to establish callback
 * connections and invoke their update().
 * @see DispatchConnection
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */
public final class CbDispatchConnection extends DispatchConnection
{
   public final String ME;
   private I_CallbackDriver cbDriver = null;
   private String cbKey = null;

   /**
    * @param connectionsHandler The DevliveryConnectionsHandler witch i belong to
    * @param address The address i shall connect to
    */
   public CbDispatchConnection(Global glob, CbDispatchConnectionsHandler connectionsHandler, AddressBase address) throws XmlBlasterException {
      super(glob, connectionsHandler, address);
      this.ME = "CbDispatchConnection-" + connectionsHandler.getDispatchManager().getQueue().getStorageId();
   }

   /**
    * @return A nice name for logging
    */
   public final String getName() {
      return ME;
   }
   
   public void setAddress(AddressBase address) throws XmlBlasterException {
      super.setAddress(address);
      if (this.cbDriver == null || !this.cbDriver.isAlive())
         loadPlugin();
      this.cbDriver.init(this.glob, (CallbackAddress)address);   
   }
   
   
   /**
    * The name of the protocol driver
    */
   public final String getDriverName() {
      return (this.cbDriver != null) ? this.cbDriver.getName() : "unknown";
   }

   /** Load the appropriate protocol driver */
   public final void loadPlugin() throws XmlBlasterException {
      // Check if a native callback driver is passed in the glob Hashtable (e.g. for "SOCKET" or "native"), take this instance
      //if (address.getId().equalsIgnoreCase("NATIVE")) {
      this.cbKey = address.getType() + address.getRawAddress();
      this.cbDriver = glob.getNativeCallbackDriver(this.cbKey);

      if (this.cbDriver == null) { // instantiate the callback plugin ...
         this.cbDriver = ((org.xmlBlaster.engine.Global)glob).getCbProtocolManager().getNewCbProtocolDriverInstance(address.getType());
         if (this.cbDriver == null)
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME, "Sorry, callback protocol type='" + address.getType() + "' is not supported");
            
         // glob.addNativeCallbackDriver(this.cbKey, this.cbDriver);
         if (log.TRACE) log.trace(ME, "Created callback plugin '" + this.address.getType() + "'");
      }
      else {
         if (log.TRACE) log.trace(ME, "Created native callback driver for protocol '" + address.getType() + "'");
      }
   }

   /**
    * @see DispatchConnection#connectLowlevel()
    */
   public final void connectLowlevel() throws XmlBlasterException {
      // Initialize the driver (connect on lowlevel layer) ...
      this.cbDriver.init(glob, (CallbackAddress)address);

      // Check if it is available
      this.cbDriver.ping("");

      if (log.TRACE) log.trace(ME, "Connected low level to callback '" + this.address.getType() + "'");
   }

   /**
    * Send the messages back to the client. 
    * @param msgArr Should be a copy of the original, since we export it which changes/encrypts the content
    * <p>
    * The RETURN value is transferred in the msgArr[i].getReturnObj(), for oneway updates it is null
    * </p>
    */
   public void doSend(MsgQueueEntry[] msgArr_) throws XmlBlasterException
   {
      // Convert to UpdateEntry
      MsgUnitRaw[] msgUnitRawArr = new MsgUnitRaw[msgArr_.length];

      // The update QoS is completed ...
      for (int i=0; i<msgUnitRawArr.length; i++) {
         MsgQueueUpdateEntry entry = (MsgQueueUpdateEntry)msgArr_[i];

         // TODO: REQ engine.qos.update.queue states that the queue size is passed and not the curr msgArr.length
         MsgUnit mu = entry.getMsgUnit();
         MsgQosData msgQosData = (MsgQosData)entry.getMsgQosData().clone();
         msgQosData.setTopicProperty(null);
         msgQosData.setState(entry.getState());
         msgQosData.setSubscriptionId(entry.getSubscriptionId());
         msgQosData.setQueueIndex(i);
         msgQosData.setQueueSize(msgUnitRawArr.length);
         if (msgQosData.getNumRouteNodes() == 1) {
            msgQosData.clearRoutes();
         }
         mu = new MsgUnit(mu, null, null, msgQosData);
         msgUnitRawArr[i] = new MsgUnitRaw(mu, mu.getKeyData().toXml(), mu.getContent(), mu.getQosData().toXml());
      }

      // We export/encrypt the message (call the interceptor)
      I_MsgSecurityInterceptor securityInterceptor = connectionsHandler.getDispatchManager().getMsgSecurityInterceptor();
      if (securityInterceptor != null) {
         for (int i=0; i<msgUnitRawArr.length; i++) {
            msgUnitRawArr[i] = securityInterceptor.exportMessage(msgUnitRawArr[i], MethodName.UNKNOWN);
         }
         if (log.TRACE) log.trace(ME, "Exported/encrypted " + msgUnitRawArr.length + " messages.");
      }
      else {
         log.warn(ME+".accessDenied", "No session security context, sending " + msgUnitRawArr.length + " messages without encryption");
      }

      String[] rawReturnVal = null;
      if (address.oneway()) {
         cbDriver.sendUpdateOneway(msgUnitRawArr);
         if (log.TRACE) log.trace(ME, "Success, sent " + msgUnitRawArr.length + " oneway messages.");
      }
      else {
         if (log.TRACE) log.trace(ME, "Before update " + msgUnitRawArr.length + " acknowledged messages ...");
         rawReturnVal = cbDriver.sendUpdate(msgUnitRawArr);
         if (log.TRACE) log.trace(ME, "Success, sent " + msgUnitRawArr.length + " acknowledged messages, return value #1 is '" + rawReturnVal[0] + "'");
      }

      connectionsHandler.getDispatchStatistic().incrNumUpdate(rawReturnVal.length);

      if (rawReturnVal != null) {
         for (int i=0; i<rawReturnVal.length; i++) {
            if (!msgArr_[i].wantReturnObj())
               continue;

            if (securityInterceptor != null) {
               // decrypt ...
               rawReturnVal[i] = securityInterceptor.importMessage(rawReturnVal[i]);
            }

            // create object
            try {
               msgArr_[i].setReturnObj(new UpdateReturnQosServer(glob, rawReturnVal[i]));
            }
            catch (Throwable e) {
               log.warn(ME, "Can't parse returned value '" + rawReturnVal[i] + "', setting to default: " + e.toString());
               //e.printStackTrace();
               UpdateReturnQosServer updateRetQos = new UpdateReturnQosServer(glob, "<qos/>");
               updateRetQos.setException(e);
               msgArr_[i].setReturnObj(updateRetQos);
            }
         }
         if (log.TRACE) log.trace(ME, "Imported/decrypted " + rawReturnVal.length + " message return values.");
      }
   }

   /**
    * Ping the callback server of the client. 
    * @param data never null
    */
   public final String doPing(String data) throws XmlBlasterException {
      return this.cbDriver.ping(data);
   }

   /**
    * Nothing to do here
    */
   public final void resetConnection() {
   }

   /**
    * On reconnect polling try to establish the connection. 
    */
   protected final void reconnect() throws XmlBlasterException {
      // this.connectionsHandler.createDispatchConnection(address);
      this.cbDriver.init(glob, (CallbackAddress)address);
   }

   /**
    * Stop all callback drivers of this client.
    */
   public final void shutdown() throws XmlBlasterException {
      super.shutdown();
      glob.removeNativeCallbackDriver(cbKey);
      if (this.cbDriver != null) {
         this.cbDriver.shutdown();
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
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<CbDispatchConnection>");
      address.toXml("   " + offset);
      if (this.cbDriver == null)
         sb.append(offset).append("   <noCallbackDriver />");
      else
         sb.append(offset).append("   <callback type='" + getDriverName() + "' state='" + getState() + "'/>");
      sb.append(offset).append("</CbDispatchConnection>");

      return sb.toString();
   }
}

