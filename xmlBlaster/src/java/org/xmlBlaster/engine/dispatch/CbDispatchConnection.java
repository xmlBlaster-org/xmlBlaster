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
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.engine.qos.UpdateReturnQosServer;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.engine.queuemsg.MsgQueueUpdateEntry;
import org.xmlBlaster.util.dispatch.DispatchConnection;
import org.xmlBlaster.util.dispatch.I_PostSendListener;
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
      MsgUnitRaw[] oneways = null;
      MsgUnitRaw[] responders = null;

      {
         // Convert to UpdateEntry
         MsgUnitRaw[] msgUnitRawArr = new MsgUnitRaw[msgArr_.length];

         // The update QoS is completed ...
         int onewayCount = 0;
         for (int i=0; i<msgArr_.length; i++) {
            MsgQueueUpdateEntry entry = (MsgQueueUpdateEntry)msgArr_[i];

            if (address.oneway() || entry.updateOneway())
               onewayCount++;

            MsgUnit mu = entry.getMsgUnit();
            MsgQosData msgQosData = (MsgQosData)entry.getMsgQosData().clone();
            msgQosData.setTopicProperty(null);
            msgQosData.setState(entry.getState());
            msgQosData.setSubscriptionId(entry.getSubscriptionId());
            msgQosData.setQueueIndex(i);
            msgQosData.setQueueSize(connectionsHandler.getDispatchManager().getQueue().getNumOfEntries());
            if (msgQosData.getNumRouteNodes() == 1) {
               msgQosData.clearRoutes();
            }

            // Convert oid to original again for erased events fired by TopicHandler.java notifySubscribersAboutErase()
            if (mu.getKeyOid().equals(Constants.EVENT_OID_ERASEDTOPIC)) {
               mu = new MsgUnit(mu, (MsgKeyData)entry.getMsgKeyData().clone(), null, msgQosData);
               String oid = mu.getQosData().getClientProperty("__oid", (String)null);
               if (oid != null) {
                  mu.getKeyData().setOid(oid);
                  try {
                     ((org.xmlBlaster.util.qos.MsgQosData)mu.getQosData()).setSubscriptionId(mu.getQosData().getClientProperty("__subscriptionId", (String)null));
                  }
                  catch (Throwable e) {
                     log.error(ME, "Failed to set subscriptionId: " + e.toString());
                  }
                  mu.getQosData().getClientProperties().clear();
               }
            }
            else {
               mu = new MsgUnit(mu, null, null, msgQosData);
            }
            
            msgUnitRawArr[i] = new MsgUnitRaw(mu, mu.getKeyData().toXml(), mu.getContent(), mu.getQosData().toXml());
         }

         if (onewayCount == 0) // normal case
            responders = msgUnitRawArr;
         else if (onewayCount == msgArr_.length) // more seldom case
            oneways = msgUnitRawArr;
         else { // mix: very seldom case (worst performing)
            responders = new MsgUnitRaw[msgArr_.length-onewayCount];
            int iResponders = 0;
            oneways = new MsgUnitRaw[onewayCount];
            int iOneways = 0;
            for (int i=0; i<msgArr_.length; i++) {
               MsgQueueUpdateEntry entry = (MsgQueueUpdateEntry)msgArr_[i];
               if (address.oneway() || entry.updateOneway()) {
                  oneways[iOneways++] = msgUnitRawArr[i];
               }
               else {
                  responders[iResponders] = msgUnitRawArr[i];
               }
            }
         }
      }

      // We export/encrypt the message (call the interceptor)
      I_MsgSecurityInterceptor securityInterceptor = connectionsHandler.getDispatchManager().getMsgSecurityInterceptor();
      if (securityInterceptor != null) {
         if (responders != null) {
            for (int i=0; i<responders.length; i++) {
               responders[i] = securityInterceptor.exportMessage(responders[i], MethodName.UPDATE);
            }
         }
         if (oneways != null) {
            for (int i=0; i<oneways.length; i++) {
               oneways[i] = securityInterceptor.exportMessage(oneways[i], MethodName.UPDATE_ONEWAY);
            }
         }
         if (log.TRACE) log.trace(ME, "Exported/encrypted " + msgArr_.length + " messages.");
      }
      else {
         log.warn(ME+".accessDenied", "No session security context, sending " + msgArr_.length + " messages without encryption");
      }

      if (oneways != null) {
         cbDriver.sendUpdateOneway(oneways);
         connectionsHandler.getDispatchStatistic().incrNumUpdate(oneways.length);
         if (log.TRACE) log.trace(ME, "Success, sent " + oneways.length + " oneway messages.");
      }

      if (responders != null) {
         if (log.TRACE) log.trace(ME, "Before update " + responders.length + " acknowledged messages ...");
         String[] rawReturnVal = cbDriver.sendUpdate(responders);
         connectionsHandler.getDispatchStatistic().incrNumUpdate(responders.length);
         if (log.TRACE) log.trace(ME, "Success, sent " + responders.length + " acknowledged messages, return value #1 is '" + rawReturnVal[0] + "'");

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
                  I_PostSendListener postSendListener = this.connectionsHandler.getPostSendListener();
                  if (postSendListener != null) postSendListener.postSend(msgArr_[i]);
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
   }

   /**
    * @see org.xmlBlaster.util.dispatch.DispatchConnection#doPing(String)
    */
   public final String doPing(String data) throws XmlBlasterException {
      String ret = this.cbDriver.ping(data);
      return (ret==null) ? "" : ret;
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

