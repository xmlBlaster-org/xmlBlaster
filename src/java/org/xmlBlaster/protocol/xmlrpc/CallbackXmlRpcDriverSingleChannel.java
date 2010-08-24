/*------------------------------------------------------------------------------
Name:      CallbackXmlRpcDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

import java.lang.ref.WeakReference;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.util.property.PropLong;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.xbformat.I_ProgressListener;
import org.xmlBlaster.util.MsgUnitRaw;

//import EDU.oswego.cs.dl.util.concurrent.LinkedQueue;

/**
 * This object sends a MsgUnitRaw back to a client using XMLRPC interface, in
 * the same JVM.
 * <p>
 * The I_CallbackDriver.update() method of the client will be invoked
 *
 * @author Michele Laghi (michele@laghi.eu)
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @see org.xmlBlaster.protocol.xmlrpc.XmlRpcDriver
 */
public class CallbackXmlRpcDriverSingleChannel implements I_CallbackDriver, I_Timeout {
   
   private String ME = "CallbackXmlRpcDriverSingleChannel";
   private Global glob = null;
   private static Logger log = Logger.getLogger(CallbackXmlRpcDriverSingleChannel.class.getName());
   private CallbackAddress callbackAddress = null;
   private String sessionId;
   private LinkedBlockingQueue<UpdateEvent> updateQueue;
   private LinkedBlockingQueue<UpdateEvent> ackQueue;
   
   private long updateAckTimeout = 30000L;
   private long updateTimeout = 30000L;
   private long counter = 0L;
   private WeakReference<XmlBlasterImpl> xblImpl;
   private Timestamp timestamp;
   private Thread currentThread;
   private String lastRespanLocation = "";
   
   /** Get a human readable name of this driver */
   public String getName() {
      return ME;
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return "XMLRPC"
    */
   public String getProtocolId() {
      return "XMLRPC";
   }

   /** Enforced by I_Plugin */
   public String getType() {
      return getProtocolId();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return "1.0";
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
      if (log.isLoggable(Level.FINE))
         log.fine("init invoked");
   }

   /**
    * Get the address how to access this driver. 
    * @return "http://server.mars.universe:8080/"
    */
   public String getRawAddress() {
      return callbackAddress.getRawAddress();
   }

   public void register(String sessionId_, XmlBlasterImpl xblImpl_) throws XmlBlasterException {
      // we need to pass this since the callbackAddress does not contain it only 'unknown'
      sessionId = sessionId_;
      xblImpl = new WeakReference<XmlBlasterImpl>(xblImpl_);
      respan("register");
   }
   
   /**
    * Get callback reference here.
    * <p />
    * This method is enforced by interface I_CallbackDriver and is called by
    * xmlBlaster after instantiation of this class, telling us
    * the address to callback.
    * @param  callbackAddress Contains the stringified XMLRPC callback handle of
    *                      the client
    */
   public void init(Global global, CallbackAddress cbAddress) throws XmlBlasterException {
      this.glob = global;
      org.xmlBlaster.engine.ServerScope 
         engineGlob = (org.xmlBlaster.engine.ServerScope)glob.getObjectEntry(Constants.OBJECT_ENTRY_ServerScope);
      if (engineGlob == null)
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "could not retreive the ServerNodeScope. Am I really on the server side ?");

      this.callbackAddress = cbAddress;
      updateQueue = new LinkedBlockingQueue<UpdateEvent>();
      ackQueue = new LinkedBlockingQueue<UpdateEvent>();
      
      PropLong tmp = callbackAddress.getEnv("updateTimeout", 30000L);
      updateTimeout = tmp.getValue();
      
      tmp = callbackAddress.getEnv("updateAckTimeout", 30000L);
      updateAckTimeout = tmp.getValue();
      
      if (log.isLoggable(Level.FINE)) 
         log.fine("Accessing client callback web server using given url=" + callbackAddress.getRawAddress());
   }

   /**
    * This sends the update to the client.
    * <p />
    * This method is enforced by interface I_CallbackDriver and is called by
    * @return Clients should return a qos as follows.
    *         An empty qos string "" is valid as well and
    *         interpreted as OK
    * <pre>
    *  &lt;qos>
    *     &lt;state id='OK'/>
    *  &lt;/qos>
    * </pre>
    * @exception e.id="CallbackFailed", should be caught and handled appropriate
    */
   public final String[] sendUpdate(MsgUnitRaw[] msgArr) throws XmlBlasterException {
      if (msgArr == null || msgArr.length < 1)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal update argument");
      
      if (updateQueue == null || ackQueue == null) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "Callback Failed since the callback is shutdown");
      }
      try {
         UpdateEvent event = new UpdateEvent("update", msgArr, null, null, counter++);
         boolean worked = updateQueue.offer(event, updateTimeout, TimeUnit.MILLISECONDS);
         if (!worked)
            throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "CallbackFailed since nobody picks up updates");

         // wait for acknolwedge from server
         UpdateEvent ret = (UpdateEvent)ackQueue.poll(updateAckTimeout, TimeUnit.MILLISECONDS);
         if (ret == null)
            throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "CallbackFailed since no response from client");
         
         if (ret.getUniqueId() != event.getUniqueId())
            throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "CallbackFailed since expected ack is not consistent with associated update");

         respan("CallbackXmlRpcDriverSingleChannel.sendUpdate");
         if ("ack".equals(ret.getMethod()))
            return ret.getRet();
         // then it is an exception
         XmlBlasterException exToThrow = new XmlBlasterException(glob, ErrorCode.USER_UPDATE_ERROR, ME, "USER UPDATE EXCEPTION " + ret.getQos());
         exToThrow.isServerSide(false); // otherwise error handling is broken
         throw exToThrow;
      }
      catch (Throwable e) { // e.g. IOException
         if (e instanceof XmlBlasterException) {
            throw (XmlBlasterException)e;
         }
         String str = "Sending message to " + ((callbackAddress!=null)?callbackAddress.getRawAddress():"?") + " failed: " + e.toString();
         if (log.isLoggable(Level.FINE)) log.fine(str);
         e.printStackTrace();
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "CallbackFailed", e);
      }
   }

   /**
    * The oneway variant, without return value. 
    * @exception XmlBlasterException Is never from the client (oneway).
    */
   public void sendUpdateOneway(MsgUnitRaw[] msgArr) throws XmlBlasterException {
      if (msgArr == null || msgArr.length < 1)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal update argument");
      
      if (updateQueue == null || ackQueue == null) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALSTATE, ME, "Illegal state: the callback is shutdown");
      }

      try {
         UpdateEvent event = new UpdateEvent("updateOneway", msgArr, null, null, counter++);
         boolean worked = updateQueue.offer(event, updateTimeout, TimeUnit.MILLISECONDS);
         if (!worked)
            throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "CallbackFailed since nobody picks up updates");
         respan("CallbackXmlRpcDriverSingleChannel.sendUpdateOneway");
      }
      catch (Throwable e) { // e.g. IOException
         String str = "Sending message to " + ((callbackAddress!=null)?callbackAddress.getRawAddress():"?") + " failed: " + e.toString();
         if (log.isLoggable(Level.FINE)) log.fine(str);
         e.printStackTrace();
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "CallbackFailed", e);
      }
   }

   public final void respan(String location) throws XmlBlasterException {
      lastRespanLocation = location;
      if (callbackAddress == null)
         return; // silently return since we are shut down already
      long pingInterval = getPingInterval();
      if (pingInterval > 0) {
         timestamp = glob.getPingTimer().addOrRefreshTimeoutListener(this, pingInterval, null, timestamp);
      }
      else
         log.info("The callback for session '" + sessionId + "' will not ping");
   }
   
   public final long getPingInterval() {
      if (callbackAddress == null)
         return 0;
      return callbackAddress.getPingInterval();
   }
   
   /**
    * Ping to check if callback server is alive. 
    * This ping checks the availability on the application level.
    * @param qos Currently an empty string ""
    * @return    Currently an empty string ""
    * @exception XmlBlasterException If client not reachable
    */
   public final String ping(String qos) throws XmlBlasterException {
      if (updateQueue == null || ackQueue == null) {
         // throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALSTATE, ME, "Illegal state: the callback is shutdown");
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_CALLBACKSERVER_NOTAVAILABLE, ME, "the updateQueue and/or ackQueue are null");
      }

      XmlBlasterImpl impl = this.xblImpl.get();
      if (impl != null) {
         if (impl.getCb(sessionId) == null) {
            throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_CALLBACKSERVER_NOTAVAILABLE, ME, "the callback is shutdown");
         }
         return "OK";
      }
      else {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_CALLBACKSERVER_NOTAVAILABLE, ME, "the callback weak ref is shutdown");
      }
   }

   public I_ProgressListener registerProgressListener(I_ProgressListener listener) {
      if (log.isLoggable(Level.FINE)) log.fine("Registering I_ProgressListener is not supported with this protocol plugin");
      return null;
   }

   /**
    * This method shuts down the driver.
    * <p />
    */
   public void shutdown() {
      //if (xmlRpcClient != null) xmlRpcClient.shutdown(); method is missing in XmlRpc package !!!
      if (timestamp != null)
         glob.getPingTimer().removeTimeoutListener(timestamp);
      callbackAddress = null;
      if (updateQueue != null) {
         updateQueue = null;
      }
      ackQueue = null;
      if (log.isLoggable(Level.FINE)) 
         log.fine("Shutdown implementation is missing");
   }

   /**
    * @return true if the plugin is still alive, false otherwise
    */
   public boolean isAlive() {
      return true;
   }

   public LinkedBlockingQueue<UpdateEvent> getUpdateQueue() {
      return updateQueue;
   }

   public LinkedBlockingQueue<UpdateEvent> getAckQueue() {
      return ackQueue;
   }

   public void timeout(Object userData) {
      XmlBlasterImpl impl = this.xblImpl.get();
      if (impl != null)
         impl.removeCallback(sessionId, "removed from timeout: " + lastRespanLocation);
      else
         log.warning("The callback for '" + sessionId + "' was already removed");
   }

   public synchronized void setCurrentThread(Thread currThread) {
      currentThread = currThread;
   }
   
   public synchronized void interrupt() {
      if (currentThread == null)
         return;
      currentThread.interrupt();
      shutdown();
   }
   
}
