/*------------------------------------------------------------------------------
Name:      CbDispatchConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.dispatch;

import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.engine.MsgUnitWrapper;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.engine.SubscriptionInfo;
import org.xmlBlaster.engine.qos.UpdateReturnQosServer;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.xbformat.I_ProgressListener;
import org.xmlBlaster.engine.queuemsg.MsgQueueUpdateEntry;
import org.xmlBlaster.util.dispatch.DispatchConnection;
import org.xmlBlaster.util.dispatch.I_PostSendListener;
import org.xmlBlaster.authentication.plugins.CryptDataHolder;
import org.xmlBlaster.authentication.plugins.I_MsgSecurityInterceptor;


/**
 * Holding all necessary infos to establish callback
 * connections and invoke their update().
 * @see DispatchConnection
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */
public final class CbDispatchConnection extends DispatchConnection
{
   private static Logger log = Logger.getLogger(CbDispatchConnection.class.getName());
   
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
      // How to access ConnectQos? refactor and pass SessionInfo to DispatchManager?
      //ServerScope serverScope = (ServerScope)glob;
      //SessionInfo sessionInfo = serverScope.getAuthenticate().getSubjectInfoByName(connectionsHandler.getDispatchManager().getSessionName());
      // -- in doSend
      //if (msg.isPtp() && !sessionInfo.getConnectQos().isPtpAllowed())
      //   throaway msg 
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
         this.cbDriver = ((org.xmlBlaster.engine.ServerScope)glob).getCbProtocolManager().getNewCbProtocolDriverInstance(address.getType());
         if (this.cbDriver == null)
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME, "Sorry, callback protocol type='" + address.getType() + "' is not supported");
            
         // glob.addNativeCallbackDriver(this.cbKey, this.cbDriver);
         if (log.isLoggable(Level.FINE)) log.fine("Created callback plugin '" + this.address.getType() + "'");
      }
      else {
         if (log.isLoggable(Level.FINE)) log.fine("Created native callback driver for protocol '" + address.getType() + "'");
      }
   }

   public I_ProgressListener registerProgressListener(I_ProgressListener listener) {
      if (this.cbDriver == null) return null;
      return this.cbDriver.registerProgressListener(listener);
   }

   /**
    * @see DispatchConnection#connectLowlevel()
    */
   public final void connectLowlevel() throws XmlBlasterException {
      // Initialize the driver (connect on lowlevel layer) ...
      this.cbDriver.init(glob, (CallbackAddress)address);

      // Check if it is available
      if (super.address.getPingInterval() > 0) {
         doPing("<qos><state info='"+Constants.INFO_INITIAL+"'/></qos>");
      }

      if (log.isLoggable(Level.FINE)) log.fine("Connected low level to callback '" + this.address.getType() + "'");
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
      ArrayList oneways = null;
      ArrayList responders = null;
      
      class Holder {
         public MsgQueueUpdateEntry msgQueueUpdateEntry;
         public MsgUnitRaw msgUnitRaw;
         public String subscriptionId;
         
         public Holder(MsgQueueUpdateEntry msgQueueUpdateEntry, MsgUnitRaw msgUnitRaw, String subscriptionId) {
            this.msgQueueUpdateEntry = msgQueueUpdateEntry;
            this.msgUnitRaw = msgUnitRaw;
            this.subscriptionId = subscriptionId;
         }
      }

      {
         for (int i=0; i<msgArr_.length; i++) {
            MsgQueueUpdateEntry entry = (MsgQueueUpdateEntry)msgArr_[i];

            MsgUnitWrapper msgUnitWrapper = entry.getMsgUnitWrapper();
            if (msgUnitWrapper == null) {
               if (log.isLoggable(Level.FINE)) log.fine("doSend("+entry.getLogId()+") ignoring callback message as no meat is available (assume expired)");
               entry.setReturnObj(new UpdateReturnQosServer(this.glob, Constants.RET_EXPIRED)); //"<qos><state id='EXPIRED'/></qos>";
               continue;
            }
            MsgUnit mu = msgUnitWrapper.getMsgUnit();
            //MsgUnit mu = entry.getMsgUnit(); throws unwanted exception if meat==null (forceDestroy)
            MsgQosData msgQosData = (MsgQosData)mu.getQosData().clone();
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
               mu = new MsgUnit(mu, (MsgKeyData)mu.getKeyData().clone(), null, msgQosData);
               String oid = mu.getQosData().getClientProperty("__oid", (String)null);
               if (oid != null) {
                  mu.getKeyData().setOid(oid);
                  try {
                     ((org.xmlBlaster.util.qos.MsgQosData)mu.getQosData()).setSubscriptionId(mu.getQosData().getClientProperty("__subscriptionId", (String)null));
                  }
                  catch (Throwable e) {
                     log.severe("Failed to set subscriptionId: " + e.toString());
                  }
                  String domain = mu.getQosData().getClientProperty("__domain", (String)null);
                  if (domain != null) {
                     mu.getKeyData().setDomain(domain);
                     mu.getQosData().getClientProperties().remove("__domain");
                  }
                  mu.getQosData().getClientProperties().remove("__oid");
                  mu.getQosData().getClientProperties().remove("__subscriptionId");
               }
            }
            else {
               mu = new MsgUnit(mu, null, null, msgQosData);
            }
            
            MsgUnitRaw raw = new MsgUnitRaw(mu, mu.getKeyData().toXml(), mu.getContent(), mu.getQosData().toXml());
            if (address.oneway() || entry.updateOneway()) {
               if (oneways == null) oneways = new ArrayList();
               oneways.add(raw);
            }
            else {
               if (responders == null) responders = new ArrayList();
               responders.add(new Holder(entry, raw, entry.getSubscriptionId()));
            }
         }
      }
      
      ServerScope scope = (ServerScope)this.glob;

      // We export/encrypt the message (call the interceptor)
      I_MsgSecurityInterceptor securityInterceptor = connectionsHandler.getDispatchManager().getMsgSecurityInterceptor();
      if (securityInterceptor != null) {
         if (responders != null) {
            for (int i=0; i<responders.size(); i++) {
               Holder holder = (Holder)responders.get(i);
               
               // Pass subscribeQos or connectQos - clientProperties to exportMessage() in case there are
               // some interesting settings provided, for example a desired XSL transformation
               SubscriptionInfo subscriptionInfo = null;
               Map map = null;
               if (holder.subscriptionId != null) {
                  subscriptionInfo = scope.getRequestBroker().getClientSubscriptions().getSubscription(holder.subscriptionId);
                  if (subscriptionInfo != null)
                     map = subscriptionInfo.getQueryQosDataClientProperties();
                  //String xslFileName = subscriptionInfo.getQueryQosData().getClientProperty("__xslTransformerFileName", (String)null);
               }
               else {
                  // todo: use map=ConnectQos.getClientProperties() as a map to pass to dataHolder
               }
               
               CryptDataHolder dataHolder = new CryptDataHolder(MethodName.UPDATE, holder.msgUnitRaw, map);
               holder.msgUnitRaw = securityInterceptor.exportMessage(dataHolder);
            }
            if (log.isLoggable(Level.FINE)) log.fine("Exported/encrypted " + responders.size() + " messages.");
         }
         if (oneways != null) {
            for (int i=0; i<oneways.size(); i++) {
               Holder holder = (Holder)responders.get(i);
               
               SubscriptionInfo subscriptionInfo = null;
               Map map = null;
               if (holder.subscriptionId != null) {
                  subscriptionInfo = scope.getRequestBroker().getClientSubscriptions().getSubscription(holder.subscriptionId);
                  if (subscriptionInfo != null)
                     map = subscriptionInfo.getQueryQosDataClientProperties();
               }
               else {
                  // todo: use map=ConnectQos.getClientProperties() as a map to pass to dataHolder
               }

               CryptDataHolder dataHolder = new CryptDataHolder(MethodName.UPDATE_ONEWAY,
                     holder.msgUnitRaw, map);
               oneways.set(i, securityInterceptor.exportMessage(dataHolder));
            }
            if (log.isLoggable(Level.FINE)) log.fine("Exported/encrypted " + oneways.size() + " oneway messages.");
         }
      }
      else {
         log.warning("No session security context, sending " + msgArr_.length + " messages without encryption");
      }

      if (oneways != null) {
         cbDriver.sendUpdateOneway((MsgUnitRaw[])oneways.toArray(new MsgUnitRaw[oneways.size()]));
         connectionsHandler.getDispatchStatistic().incrNumUpdate(oneways.size());
         if (log.isLoggable(Level.FINE)) log.fine("Success, sent " + oneways.size() + " oneway messages.");
      }

      if (responders != null) {
         if (log.isLoggable(Level.FINE)) log.fine("Before update " + responders.size() + " acknowledged messages ...");
         MsgUnitRaw[] raws = new MsgUnitRaw[responders.size()];
         for (int i=0; i<responders.size(); i++) {
            raws[i] = ((Holder)responders.get(i)).msgUnitRaw;
         }
         String[] rawReturnVal = cbDriver.sendUpdate(raws);
         connectionsHandler.getDispatchStatistic().incrNumUpdate(raws.length);
         if (log.isLoggable(Level.FINE)) log.fine("Success, sent " + raws.length + " acknowledged messages, return value #1 is '" + rawReturnVal[0] + "'");

         if (rawReturnVal != null && rawReturnVal.length == raws.length) {
            for (int i=0; i<rawReturnVal.length; i++) {
               MsgQueueUpdateEntry entry = ((Holder)responders.get(i)).msgQueueUpdateEntry;
               if (!entry.wantReturnObj())
                  continue;

               if (securityInterceptor != null) {
                  // decrypt ...
                  CryptDataHolder dataHolder = new CryptDataHolder(MethodName.UPDATE,
                        new MsgUnitRaw(null, (byte[])null, rawReturnVal[i]));
                  dataHolder.setReturnValue(true);
                  rawReturnVal[i] = securityInterceptor.importMessage(dataHolder).getQos();
               }

               // create object
               try {
                  entry.setReturnObj(new UpdateReturnQosServer(glob, rawReturnVal[i]));
                  I_PostSendListener postSendListener = this.connectionsHandler.getPostSendListener();
                  if (postSendListener != null) postSendListener.postSend(entry);
               }
               catch (Throwable e) {
                  log.warning("Can't parse returned value '" + rawReturnVal[i] + "', setting to default: " + e.toString());
                  //e.printStackTrace();
                  UpdateReturnQosServer updateRetQos = new UpdateReturnQosServer(glob, "<qos/>");
                  updateRetQos.setException(e);
                  entry.setReturnObj(updateRetQos);
               }
            }
            if (log.isLoggable(Level.FINE)) log.fine("Imported/decrypted " + rawReturnVal.length + " message return values.");
         }
         else 
            log.severe("Unexpected UpdateReturnQos '" + (rawReturnVal==null?"null":""+rawReturnVal.length)+ "', expected " + raws.length);
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

