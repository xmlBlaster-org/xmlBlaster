/*------------------------------------------------------------------------------
Name:      RequestReplyExecutor.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Send/receive messages over outStream and inStream.
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.protocol;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_ResponseListener;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.xbformat.I_ProgressListener;
import org.xmlBlaster.util.xbformat.MsgInfo;

import EDU.oswego.cs.dl.util.concurrent.Latch;

/**
 * Request/reply simulates a local method invocation.
 * <p />
 * A common base class for socket or email based messaging.
 * Allows to block during a request and deliver the return message
 * to the waiting thread.
 *
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.socket.html" target="others">xmlBlaster SOCKET access protocol</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.email.html" target="others">xmlBlaster EMAIL access protocol</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public abstract class RequestReplyExecutor implements RequestReplyExecutorMBean
{
   private String ME = RequestReplyExecutor.class.getName();
   protected Global glob;
   private static Logger log = Logger.getLogger(RequestReplyExecutor.class.getName());
   /** The prefix to create a unique requestId namspace (is set to the loginName) */
   protected String prefix = null;
   /** How long to block on remote call waiting on response */
   protected long responseTimeout;
   /** How long to block on remote call waiting on ping responses */
   protected long pingResponseTimeout;
   /** How long to block on remote call waiting on update responses */
   protected long updateResponseTimeout;
   /** This is the client side */
   protected I_CallbackExtended cbClient;
   /** The singleton handle for this xmlBlaster server (the server side) */
   private I_XmlBlaster xmlBlasterImpl;
   /** A set containing LatchHolder instances */
   private final Set latchSet = new HashSet();
   protected AddressBase addressConfig;
   protected AddressServer addressServer;
   /** A listener may register to receive send/receive progress informations */
   protected I_ProgressListener progressListener;
   protected int minSizeForCompression;
   protected boolean compressZlib;
   protected boolean compressZlibStream;
   protected boolean useEmailExpiryTimestamp;

   /**
    * For listeners who want to be informed about return messages or exceptions,
    * the invocation is blocking during this period.
    * <p />
    * The key is the String requestId, the value the listener thread I_ResponseListener
    */
   protected final Map responseListenerMap = Collections.synchronizedMap(new HashMap());
   private boolean responseListenerMapWasCleared;
   /** Used for execute() */
   public static final boolean ONEWAY = false;
   /** Used for execute() */
   public static final boolean WAIT_ON_RESPONSE = true;

   /** My JMX registration, can be done optionally by implementing classes */
   protected Object mbeanHandle;
   protected ContextNode contextNode;

   public RequestReplyExecutor() {
      if (log.isLoggable(Level.FINER)) log.finer("ctor");
   }

   /**
    * Used by SocketCallbackImpl on client side, uses I_CallbackExtended to invoke client classes
    * <p />
    * Used by HandleClient on server side, uses I_XmlBlaster to invoke xmlBlaster core
    * <p />
    * This executor has mixed client and server specific code for two reasons:<br />
    * - Possibly we can use the same socket between two xmlBlaster server (load balance)<br />
    * - Everything is together<br />
    * @param addressConfig The configuration to use
    */
   protected void initialize(Global glob, AddressBase addressConfig) {
      this.glob = (glob == null) ? Global.instance() : glob;

      this.addressConfig = addressConfig.getClone();
      this.ME = addressConfig.getRawAddress() + "-" + addressConfig.getSessionName();
      
      if (this.addressConfig instanceof AddressServer) {
         this.addressServer = (AddressServer)this.addressConfig; // downcast the clone
      }
      else {
         boolean acceptRemoteLoginAsTunnel = this.addressConfig.getEnv("acceptRemoteLoginAsTunnel", false).getValue();
         if (acceptRemoteLoginAsTunnel) { // The cluster slave accepts publish(), subscribe() etc callbacks
            // TODO: Use a clone from addressServer from the SocketDriver
            this.addressServer = new AddressServer(glob, getType(), glob.getId(), new Properties());
            /*
            I_Driver[] drivers = serverScope.getPluginRegistry().getPluginsOfInterfaceI_Driver();//register(pluginInfo.getId(), plugin);//getProtocolPluginManager().getPlugin(type, version)
            for (int i=0; i<drivers.length; i++) {
               if (drivers[i] instanceof SocketDriver) {
                  SocketDriver sd = (SocketDriver)drivers[i];
                  rawAddress = sd.getRawAddress();
                  type = sd.getType();
                  version = sd.getVersion();
                  found = true;
               }
            }
            if (!found)
               log.severe("No socket protocol driver found");
            */
         }
      }

      setMinSizeForCompression((int)this.addressConfig.getMinSize());

      if (Constants.COMPRESS_ZLIB_STREAM.equals(this.addressConfig.getCompressType())) { // Statically configured for server side protocol plugin
         this.compressZlibStream = true;
         if (log.isLoggable(Level.FINE)) log.fine("Full stream compression enabled with '" + Constants.COMPRESS_ZLIB_STREAM + "'");
      }
      else if (Constants.COMPRESS_ZLIB.equals(this.addressConfig.getCompressType())) { // Compress each message indiviually
         this.compressZlib = true;
         log.info("Message compression enabled with  '" + Constants.COMPRESS_ZLIB + "', minimum size for compression is " + getMinSizeForCompression() + " bytes");
      }
      else {
         this.compressZlibStream = false;
         this.compressZlib = false;
      }

      // 1. Response should never expire
      // 2. Otherwise the Pop3Driver must be changed to nevertheless return it to wake up the blocking latch
      setUseEmailExpiryTimestamp(addressConfig.getEnv("useEmailExpiryTimestamp", true).getValue());

      initializeCb(addressConfig); // default settings
   }

   protected void initializeCb(AddressBase addressConfig) {
	      setResponseTimeout(addressConfig.getEnv("responseTimeout", getDefaultResponseTimeout()).getValue());
	      if (log.isLoggable(Level.FINE)) log.fine(this.addressConfig.getEnvLookupKey("responseTimeout") + "=" + this.responseTimeout);
	      // the responseTimeout is used later to wait on a return value
	      // additionally we protect against blocking on socket level during invocation
	      // JacORB CORBA allows similar setting with "jacorb.connection.client_idle_timeout"
	      //        and with "jacorb.client.pending_reply_timeout"

	      setPingResponseTimeout(addressConfig.getEnv("pingResponseTimeout", getDefaultPingResponseTimeout()).getValue());
	      if (log.isLoggable(Level.FINE)) log.fine(this.addressConfig.getEnvLookupKey("pingResponseTimeout") + "=" + this.pingResponseTimeout);

	      setUpdateResponseTimeout(addressConfig.getEnv("updateResponseTimeout", getDefaultUpdateResponseTimeout()).getValue());
	      if (log.isLoggable(Level.FINE)) log.fine(this.addressConfig.getEnvLookupKey("updateResponseTimeout") + "=" + this.updateResponseTimeout);
   }
   
   public AddressServer getAddressServer() {
      return this.addressServer;
   }

   /**
    * The protocol type, used for logging
    * @return "SOCKET" or "EMAIL", never null
    */
   abstract public String getType();

   public I_ProgressListener registerProgressListener(I_ProgressListener listener) {
      I_ProgressListener oldOne = this.progressListener;
      this.progressListener = listener;
      return oldOne;
   }

   public final I_ProgressListener getProgressListener() {
      return this.progressListener;
   }

   /**
    * How long to block on remote call waiting on response.
    * The default is to block forever (Integer.MAX_VALUE)
    * Changed after xmlBlaster release 1.0.7 (before it was one minute: Constants.MINUTE_IN_MILLIS)
    * Can be overwritten by implementations like EMAIL
    */
   public long getDefaultResponseTimeout() {
      return Integer.MAX_VALUE;
   }

   /**
    * How long to block on remote call waiting on a ping response.
    * The default is to block for one minute
    * This method can be overwritten by implementations like EMAIL
    */
   public long getDefaultPingResponseTimeout() {
      return Constants.MINUTE_IN_MILLIS;
   }

   /**
    * How long to block on remote call waiting on a update() response.
    * The default is to block forever
    * This method can be overwritten by implementations like EMAIL
    */
   public long getDefaultUpdateResponseTimeout() {
      return Integer.MAX_VALUE;
   }

   /**
    * Set the given millis to protect against blocking client.
    * @param millis If <= 0 it is set to the default (forever).
    * An argument less than or equal to zero means not to wait at all
    * and is not supported
    */
   public final void setResponseTimeout(long millis) {
      if (millis <= 0L) {
         log.warning(this.addressConfig.getEnvLookupKey("responseTimeout") + "=" + millis +
                      " is invalid, setting it to " + getDefaultResponseTimeout() + " millis");
         this.responseTimeout = getDefaultResponseTimeout();
      }
      else
         this.responseTimeout = millis;
   }

   /**
    * Set the given millis to protect against blocking client for ping invocations.
    * @param millis If <= 0 it is set to the default (one minute).
    * An argument less than or equal to zero means not to wait at all
    * and is not supported
    */
   public final void setPingResponseTimeout(long millis) {
      if (millis <= 0L) {
         log.warning(this.addressConfig.getEnvLookupKey("pingResponseTimeout") + "=" + millis +
                      " is invalid, setting it to " + getDefaultPingResponseTimeout() + " millis");
         this.pingResponseTimeout = getDefaultPingResponseTimeout();
      }
      else
         this.pingResponseTimeout = millis;
   }

   /**
    * Set the given millis to protect against blocking client for update() invocations.
    * @param millis If <= 0 it is set to the default (one minute).
    * An argument less than or equal to zero means not to wait at all
    * and is not supported
    */
   public final void setUpdateResponseTimeout(long millis) {
      if (millis <= 0L) {
         log.warning(this.addressConfig.getEnvLookupKey("updateResponseTimeout") + "=" + millis +
                      " is invalid, setting it to " + getDefaultUpdateResponseTimeout() + " millis");
         this.updateResponseTimeout = getDefaultUpdateResponseTimeout();
      }
      else
         this.updateResponseTimeout = millis;
   }

   /**
    * @return Returns the responseTimeout.
    */
   public long getResponseTimeout(MethodName methodName) {
      if (MethodName.PING.equals(methodName)) {
         return this.pingResponseTimeout;
      }
      else if (MethodName.UPDATE.equals(methodName)) {
         return this.updateResponseTimeout;
      }
      return this.responseTimeout;
   }

   /**
    * Access the timeout for method invocation.
    * @param methodName e.g. "PING", "UPDATE", "SUBSCRIBE", "PUBLISH", ...
    * @return Returns the responseTimeout for JMX in milli seconds
    */
   public long getResponseTimeout(String methodName) {
      return getResponseTimeout(MethodName.toMethodName(methodName));
   }

   /**
    * Return the time in future when the email can be deleted.
    * @return Returns the expiry timestamp, is null if message never expires
    */
   public Timestamp getExpiryTimestamp(MethodName methodName) {
      if (!isUseEmailExpiryTimestamp()) return null;
      long diff = getResponseTimeout(methodName);
      if (diff <= 0) return null;
      long current = new Date().getTime();
      return new Timestamp(current+diff);
   }

   /**
    * For logging.
    * @param methodName
    * @return
    */
   public String getResponseTimeoutPropertyName(MethodName methodName) {
      if (MethodName.PING.equals(methodName)) {
         return "pingResponseTimeout";
      }
      else if (MethodName.UPDATE.equals(methodName)) {
         return "updateResponseTimeout";
      }
      return "responseTimeout";
   }

   public final void setCbClient(I_CallbackExtended cbClient) {
      this.cbClient = cbClient;
   }

   public final void setXmlBlasterCore(I_XmlBlaster xmlBlaster) {
      this.xmlBlasterImpl = xmlBlaster;
   }
   
   public final I_XmlBlaster getXmlBlasterCore() {
      return this.xmlBlasterImpl;
   }

   public final I_CallbackExtended getCbClient() {
      return this.cbClient;
   }

   /**
    * Sets the loginName and automatically the requestId as well
    */
   protected void setLoginName(String loginName) {
      if (loginName != null && loginName.length() > 0)
         this.prefix = loginName + ":";
      else
         this.prefix = null;
   }

   /**
    * Adds the listener to receive response/exception events.
    */
   public final void addResponseListener(String requestId, I_ResponseListener l) {
      if (requestId == null || l == null) {
         throw new IllegalArgumentException("addResponseListener() with requestId=null");
      }
      Object o = this.responseListenerMap.put(requestId, l);
      if (o == null) {
         if (log.isLoggable(Level.FINE)) log.fine("Added addResponseListener requestId=" + requestId);
      }
      else {
         log.warning("Added addResponseListener requestId=" + requestId + " but there was already one");
      }
   }

   /**
    * Removes the specified listener.
    */
   public final void removeResponseListener(String requestId) {
      if (requestId == null) {
         throw new IllegalArgumentException("removeResponseListener() with requestId=null");
      }
      Object o = this.responseListenerMap.remove(requestId);
      if (o == null) {
         if (this.responseListenerMapWasCleared) {
            if (log.isLoggable(Level.FINE)) log.fine("removeResponseListener(" + requestId + ") entry not found, size is " + this.responseListenerMap.size());
         }
         else {
            log.severe("removeResponseListener(" + requestId + ") entry not found, size is " + this.responseListenerMap.size());
         }
      }
      else {
         if (log.isLoggable(Level.FINE)) log.fine("removeResponseListener(" + requestId + ") done");
      }
   }

   /**
    * Get the response listener object
    */
   public final I_ResponseListener getResponseListener(String requestId) {
      if (requestId == null) {
         throw new IllegalArgumentException("getResponseListener() with requestId=null");
      }
      return (I_ResponseListener)this.responseListenerMap.get(requestId);
   }
   
   /**
    * For logging only
    * @return null if none found
    */
   public final String getPendingRequestList() {
      synchronized (this.responseListenerMap) { // for iteration we need to sync
         if (this.responseListenerMap.size() > 0) {
            StringBuffer buf = new StringBuffer(256);
            java .util.Iterator iterator = this.responseListenerMap.keySet().iterator();
            while (iterator.hasNext()) {
               if (buf.length() > 0) buf.append(", ");
               String key = (String)iterator.next();
               buf.append(key);
            }
            return buf.toString();
         }
      }
      return null;
   }

   public void clearResponseListenerMap() {
      try {
         String str = getPendingRequestList();
         if (str != null) {
            // Seems to happen during SSL SOCKET reconnect polling with connect()
            log.info(ME+" There are " + this.responseListenerMap.size() + " messages pending without a response, request IDs are '" + str + "', we remove them now.");
            this.responseListenerMap.clear();
            this.responseListenerMapWasCleared = true;
         }
      }
      catch (Throwable e) {
         e.printStackTrace();
      }
   }

   /**
    * Handle common messages
    * @return false: for connect() and disconnect() which must be handled by the base class
    */
   public boolean receiveReply(MsgInfo receiver, boolean udp) throws XmlBlasterException, IOException {
      if (log.isLoggable(Level.FINE)) log.fine("Receiving '" + receiver.getTypeStr() + "' message " + receiver.getMethodName() + "(" + receiver.getRequestId() + ")");

      top: // used for the break in case we are on the client side when an invocation is done
      
      if (receiver.isInvoke()) {
         // handling invocations ...

         if (MethodName.PUBLISH_ONEWAY == receiver.getMethodName()) {
            MsgUnitRaw[] arr = receiver.getMessageArr();
            if (arr == null || arr.length < 1) {
               log.severe("Invocation of " + receiver.getMethodName() + "() failed, missing arguments");
               return true;
            }
            xmlBlasterImpl.publishOneway(getAddressServer(), receiver.getSecretSessionId(), arr);
         }
         else if (MethodName.PUBLISH == receiver.getMethodName()) {
            if (!glob.isServerSide()) {
               log.severe("We are on client side and no "+getType()+" callback driver is available, can't process the remote invocation " + receiver.getMethodName());
               break top;
            }
            MsgUnitRaw[] arr = receiver.getMessageArr();
            if (arr == null || arr.length < 1)
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Invocation of " + receiver.getMethodName() + "() failed, missing arguments");
            String[] response = xmlBlasterImpl.publishArr(getAddressServer(), receiver.getSecretSessionId(), arr);
            executeResponse(receiver, response, udp);
         }
         else if (MethodName.UPDATE_ONEWAY == receiver.getMethodName()) {
            try {
               I_CallbackExtended cbClientTmp = this.cbClient;
               if (cbClientTmp == null) {
                  throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_CALLBACKSERVER_NOTAVAILABLE, ME, "The " + getType() + " callback driver is not created, can't process the remote invocation. Try configuration ' -protocol "+getType()+"'");
               }
               MsgUnitRaw[] arr = receiver.getMessageArr();
               if (arr == null || arr.length < 1) {
                  log.severe("Invocation of " + receiver.getMethodName() + "() failed, missing arguments");
                  return true;
               }
               cbClientTmp.updateOneway(receiver.getSecretSessionId(), arr);
            }
            catch (XmlBlasterException e) {
               log.warning("Invocation of " + receiver.getMethodName() + "() failed, server is not informed as we are in oneway operation: " + e.getMessage());
               //executeException(receiver, e, udp); // Removed 2010-02-11 oneway should not send exception back to server
               return true;
            }
            catch (Throwable e) {
               e.printStackTrace();
               log.severe("Invocation of " + receiver.getMethodName() + "() failed, server is not informed as we are in oneway operation: " + e.toString());
               // Removed 2010-02-11 oneway should not send exception back to server
               //XmlBlasterException xmlBlasterException = new XmlBlasterException(glob, ErrorCode.USER_UPDATE_INTERNALERROR, ME, "Invocation of " + receiver.getMethodName() + "() failed, missing arguments", e);
               //executeException(receiver, xmlBlasterException, udp); // Remove?? oneway should not send exception back to server
               return true;
            }
         }
         else if (MethodName.UPDATE == receiver.getMethodName()) {
            try {
               I_CallbackExtended cbClientTmp = this.cbClient; // Remember to avoid synchronized block
               if (cbClientTmp == null) {
                  throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_CALLBACKSERVER_NOTAVAILABLE, ME, "No "+getType()+" callback driver is available, can't process the remote invocation for UPDATE");
               }
               MsgUnitRaw[] arr = receiver.getMessageArr();
               if (arr == null || arr.length < 1) {
                  throw new XmlBlasterException(glob, ErrorCode.USER_UPDATE_INTERNALERROR, ME, "Invocation of " + receiver.getMethodName() + "() failed, missing arguments");
               }
               String[] response = cbClientTmp.update(receiver.getSecretSessionId(), arr);
               executeResponse(receiver, response, udp);
            }
            catch (XmlBlasterException e) {
               executeException(receiver, e, udp);
               return true;
            }
            catch (Throwable e) {
               XmlBlasterException xmlBlasterException = new XmlBlasterException(glob, ErrorCode.USER_UPDATE_INTERNALERROR, ME, "Invocation of " + receiver.getMethodName() + "() failed, missing arguments", e);
               executeException(receiver, xmlBlasterException, udp);
               return true;
            }
         }
         else if (MethodName.GET == receiver.getMethodName()) {
            if (!glob.isServerSide()) {
               log.severe("We are on client side and no "+getType()+" callback driver is available, can't process the remote invocation " + receiver.getMethodName());
               break top;
            }
            MsgUnitRaw[] arr = receiver.getMessageArr();
            if (arr == null || arr.length != 1)
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Invocation of " + receiver.getMethodName() + "() failed, wrong arguments");
            MsgUnitRaw[] response = xmlBlasterImpl.get(getAddressServer(), receiver.getSecretSessionId(), arr[0].getKey(), arr[0].getQos());
            executeResponse(receiver, response, udp);
         }
         else if (MethodName.PING == receiver.getMethodName()) {
            MsgUnitRaw[] arr = receiver.getMessageArr();
            if (this.cbClient == null && !glob.isServerSide()) {
               log.severe("We are on client side and no "+getType()+" callback driver is available, can't process the remote invocation " + receiver.getMethodName());
               Thread.dumpStack();
               XmlBlasterException xmlBlasterException = new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_CALLBACKSERVER_NOTAVAILABLE, ME, "We are on client side and no "+getType()+" callback driver is available, can't process the remote invocation " + receiver.getMethodName());
               executeException(receiver, xmlBlasterException, udp);
               return true;
            }
            if (xmlBlasterImpl != null) { // Server side: Forward ping to xmlBlaster core
               String response = xmlBlasterImpl.ping(getAddressServer(), /*receiver.getSecretSessionId(),*/ (arr.length>0) ? arr[0].getQos() : "<qos/>");
               executeResponse(receiver, response, udp); // Constants.RET_OK="<qos><state id='OK'/></qos>" or current run level
            }
            else { // Client side: answer directly, not forwarded to client code
               executeResponse(receiver, Constants.RET_OK, udp); // Constants.RET_OK="<qos><state id='OK'/></qos>" or current run level
            }
         }
         else if (MethodName.SUBSCRIBE == receiver.getMethodName()) {
            if (!glob.isServerSide()) {
               log.severe("We are on client side and no "+getType()+" callback driver is available, can't process the remote invocation " + receiver.getMethodName());
               break top;
            }
            MsgUnitRaw[] arr = receiver.getMessageArr();
            if (arr == null || arr.length != 1)
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Invocation of " + receiver.getMethodName() + "() failed, wrong arguments");
            String response = xmlBlasterImpl.subscribe(getAddressServer(), receiver.getSecretSessionId(), arr[0].getKey(), arr[0].getQos());
            executeResponse(receiver, response, udp);
         }
         else if (MethodName.UNSUBSCRIBE == receiver.getMethodName()) {
            if (!glob.isServerSide()) {
               log.severe("We are on client side and no "+getType()+" callback driver is available, can't process the remote invocation " + receiver.getMethodName());
               break top;
            }
            MsgUnitRaw[] arr = receiver.getMessageArr();
            if (arr == null || arr.length != 1)
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Invocation of " + receiver.getMethodName() + "() failed, wrong arguments");
            String[] response = xmlBlasterImpl.unSubscribe(getAddressServer(), receiver.getSecretSessionId(), arr[0].getKey(), arr[0].getQos());
            executeResponse(receiver, response, udp);
         }
         else if (MethodName.ERASE == receiver.getMethodName()) {
            if (!glob.isServerSide()) {
               log.severe("We are on client side and no "+getType()+" callback driver is available, can't process the remote invocation " + receiver.getMethodName());
               break top;
            }
            MsgUnitRaw[] arr = receiver.getMessageArr();
            if (arr == null || arr.length != 1)
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Invocation of " + receiver.getMethodName() + "() failed, wrong arguments");
            String[] response = xmlBlasterImpl.erase(getAddressServer(), receiver.getSecretSessionId(), arr[0].getKey(), arr[0].getQos());
            executeResponse(receiver, response, udp);
         }
         else if (MethodName.CONNECT == receiver.getMethodName()) {
            if (!glob.isServerSide()) {
               log.severe("We are on client side and no "+getType()+" callback driver is available, can't process the remote invocation " + receiver.getMethodName());
               break top;
            }
            return false;
         }
         else if (MethodName.DISCONNECT == receiver.getMethodName()) {
            if (!glob.isServerSide()) {
               log.severe("We are on client side and no "+getType()+" callback driver is available, can't process the remote invocation " + receiver.getMethodName());
               break top;
            }
            return false;
         }
         else {
            log.warning("Ignoring received invocation message '" + receiver.getMethodName() + "' with requestId=" + receiver.getRequestId() + ", nobody is interested in it: " + receiver.toLiteral());
            if (log.isLoggable(Level.FINEST)) log.finest("Ignoring received message, nobody is interested in it:\n>" + receiver.toLiteral() + "<");
         }

         return true;
      }

      // Handling response or exception ...
      I_ResponseListener listener = getResponseListener(receiver.getRequestId());
      if (listener == null) {
         log.warning("Ignoring received '" + receiver.getMethodName() + "' response message, requestId=" + receiver.getRequestId() + ", nobody is interested in it");
         if (log.isLoggable(Level.FINEST)) log.finest("Ignoring received message, nobody is interested in it: >" + receiver.toLiteral() + "<");
         return true;
      }
      removeResponseListener(receiver.getRequestId());

      if (receiver.isResponse()) {
         if (receiver.getMethodName().returnsMsgArr()) { // GET returns MsgUnitRaw[]
            listener.incomingMessage(receiver.getRequestId(), receiver.getMessageArr());
         }
         else if (receiver.getMethodName().returnsStringArr()) {  // PUBLISH etc. return String[]
            listener.incomingMessage(receiver.getRequestId(), receiver.getQosArr());
         }
         else if (receiver.getMethodName().returnsString()) { // SUBSCRIBE, CONNECT etc. return a String
            listener.incomingMessage(receiver.getRequestId(), receiver.getQos());
         }
         else {  // SUBSCRIBE, CONNECT etc. return a String
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "The method " + receiver.getMethodName() + " is not expected in this context");
         }
      }
      else if (receiver.isException()) { // XmlBlasterException
         listener.incomingMessage(receiver.getRequestId(), receiver.getException());
      }
      else {
         log.severe("PANIC: Invalid response message for " + receiver.getMethodName());
         listener.incomingMessage(receiver.getRequestId(), new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Invalid response message '" + receiver.getMethodName()));
      }

      return true;
   }

   /*
    * Overwrite on demand.
    * TODO: Is this needed anymore?
    * @return
    */
   protected boolean hasConnection() {
      return true;
   }

   /**
    * Send a message and block until the response arrives.
    * <p/>
    * We simulate RPC (remote procedure call) here.
    * This should be thread save and may be invoked by many
    * client threads in parallel (though i have not tested it).
    * @param expectingResponse WAIT_ON_RESPONSE=true or ONEWAY=false
    * @param udp Some user info which is passed through
    * @return the response object of the request, of type String(QoS), MsgUnitRaw[] or XmlBlasterException
    */
   public Object requestAndBlockForReply(MsgInfo msgInfo, boolean expectingResponse, boolean udp) throws XmlBlasterException, IOException {

      String requestId = msgInfo.createRequestId(prefix);
      if (log.isLoggable(Level.FINE)) log.fine("Invoking  msgInfo type='" + msgInfo.getTypeStr() + "' message " + msgInfo.getMethodName() + "(requestId=" + requestId + ") oneway=" + !expectingResponse + " udp=" + udp);

      final Object[] response = new Object[1];  // As only final variables are accessable from the inner class, we put the response in this array
      response[0] = null;
      final LatchHolder startSignal;

      // Register the return value / Exception listener ...
      if (expectingResponse) {
         //startSignal = new Latch(); // defaults to false
         startSignal = addLatch(new Latch()); //synchronized (this.latchSet) { this.latchSet.add(startSignal); } // remember all blocking threads for release on shutdown
         if (!hasConnection()) return null;
         addResponseListener(requestId, new I_ResponseListener() {
            public void incomingMessage(String reqId, Object responseObj) {
               if (log.isLoggable(Level.FINE)) log.fine("RequestId=" + reqId + ": return value arrived ...");
               response[0] = responseObj;
               startSignal.latch.release(); // wake up
            }
         });
      }
      else
         startSignal = null;

      // Send the message / method invocation ...
      if (log.isLoggable(Level.FINEST)) log.finest("Sending now : >" + msgInfo.toLiteral() + "<");
      try {
         sendMessage(msgInfo, msgInfo.getRequestId(), msgInfo.getMethodName(), udp);
         // if (log.isLoggable(Level.FINE)) log.trace(ME, "Successfully sent " + msgInfo.getNumMessages() + " messages");
      }
      catch (Throwable e) {
         if (startSignal != null) {
            removeLatch(startSignal); // synchronized (this.latchSet) { this.latchSet.remove(startSignal); }
         }
         String tmp = (msgInfo==null) ? "" : msgInfo.getMethodNameStr();
         String str = "Request blocked and timed out, giving up now waiting on " +
                      tmp + "(" + requestId + ") response. Please check settings of " +
                      "responseTimeout="+this.responseTimeout+
                      " pingResponseTimeout="+this.pingResponseTimeout+
                      " updateResponseTimeout="+this.updateResponseTimeout;
         if (e instanceof XmlBlasterException) {
            if (log.isLoggable(Level.FINE)) log.fine(str + ": " + e.toString());
            throw (XmlBlasterException)e;
         }
         if (e instanceof NullPointerException)
            e.printStackTrace();
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_TIMEOUT, ME, str, e);
      }

      if (log.isLoggable(Level.FINEST)) log.finest("Successful sent message: >" + msgInfo.toLiteral() + "<");

      if (!expectingResponse) {
         return null;
      }

      // Waiting for the response to arrive ...
      try {
         boolean awakened = false;
         while (true) {
            try {
               //  An argument less than or equal to zero means not to wait at all
               awakened = startSignal.latch.attempt(getResponseTimeout(msgInfo.getMethodName())); // block max. milliseconds
               if (startSignal.latchIsInterrupted) {
                  awakened = false; // Simulates a responseTimeout
                  startSignal.latchIsInterrupted = false;
               }
               break;
            }
            catch (InterruptedException e) {
               log.warning("Waking up (waited on " + msgInfo.getMethodName() + "(" + requestId + ") response): " + e.toString());
               // try again
            }
         }
         if (awakened) {
            if (log.isLoggable(Level.FINE)) log.fine("Waking up, got response for " + msgInfo.getMethodName() + "(requestId=" + requestId + ")");
            if (response[0]==null) // Caused by freePendingThreads()
               throw new IOException(ME + ": Lost " + getType() + " connection for " + msgInfo.getMethodName() + "(requestId=" + requestId + ")");

            if (log.isLoggable(Level.FINEST)) log.finest("Response for " + msgInfo.getMethodName() + "(" + requestId + ") is: " + response[0].toString());
            if (response[0] instanceof XmlBlasterException)
               throw (XmlBlasterException)response[0];
            return response[0];
         }
         else {
            String str = "Timeout of " + getResponseTimeout(msgInfo.getMethodName())
                       + " milliseconds occured when waiting on " + msgInfo.getMethodName() + "(" + requestId
                       + ") response. You can change it with -plugin/"
                       + getType().toLowerCase()+"/"+getResponseTimeoutPropertyName(msgInfo.getMethodName())+" <millis>";
            removeResponseListener(requestId);
            throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_RESPONSETIMEOUT, ME, str);
         }
      }
      finally {
         removeLatch(startSignal); //synchronized (this.latchSet) { this.latchSet.remove(startSignal); }
      }
   }

   private class LatchHolder {
      public LatchHolder(Latch latch) {
         this.latch = latch;
      }
      Latch latch;
      boolean latchIsInterrupted;
   }
   
   public void shutdown() {
      //this.addressConfig.shutdown();
      //this.addressServer.shutdown();
   }

   /**
    * Interrupts a blocking request with a not returning reply.
    * The pending message is handled as not delivered and will be queued
    * @return Number of interrupted invocations, typically 0 or 1
    */
   public int interruptInvocation() {
      LatchHolder[] latches = getLatches();
      for (int i=0; i<latches.length; i++) {
         latches[i].latchIsInterrupted = true;
         latches[i].latch.release(); // wake up
         //log.warning("DEBUG ONLY: Forced release of latch");
      }
      return latches.length;
   }

   private LatchHolder addLatch(Latch latch) {
      LatchHolder latchHolder = new LatchHolder(latch);
      synchronized (this.latchSet) {
         boolean added = this.latchSet.add(latchHolder);
         if (!added)
            throw new IllegalArgumentException("Didn't expect the latch already");
      }
      return latchHolder;
   }

   private void removeLatch(LatchHolder latchHolder) {
      synchronized (this.latchSet) {
         this.latchSet.remove(latchHolder);
      }
   }

   private LatchHolder[] getLatches() {
      synchronized (this.latchSet) {
         return (LatchHolder[])this.latchSet.toArray(new LatchHolder[this.latchSet.size()]);
      }
   }

   /**
    * If we detect somewhere that the socket is down
    * use this method to free blocking threads which wait on responses
    */
   public final void freePendingThreads() {
      if (log != null && log.isLoggable(Level.FINE) && this.latchSet.size()>0) log.fine("Freeing " + this.latchSet.size() + " pending threads (waiting on responses) from their ugly blocking situation");
      LatchHolder[] latches = getLatches();
      for (int i=0; i<latches.length; i++) {
         latches[i].latchIsInterrupted = true;
         latches[i].latch.release(); // wake up
      }
      synchronized (this.latchSet) { latchSet.clear(); }
   }

   /**
    * Send a one way response message back to the other side
    */
   protected final void executeResponse(MsgInfo receiver, Object response, boolean udp) throws XmlBlasterException, IOException {

      // Take a clone:
      MsgInfo returner = receiver.createReturner(MsgInfo.RESPONSE_BYTE);

      if (response instanceof String)
         returner.addMessage((String)response);
      else if (response instanceof String[])
         returner.addMessage((String[])response);
      else if (response instanceof MsgUnitRaw[])
         returner.addMessage((MsgUnitRaw[])response);
      else if (response instanceof MsgUnitRaw)
         returner.addMessage((MsgUnitRaw)response);
      else
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Invalid response data type " + response.toString());
      sendMessage(returner, receiver.getRequestId(), receiver.getMethodName(), udp);
      if (log.isLoggable(Level.FINE)) log.fine("Successfully sent response for " + receiver.getMethodName() + "(" + receiver.getRequestId() + ")");
      if (log.isLoggable(Level.FINEST)) log.finest("Successful sent response for " + receiver.getMethodName() + "() >" + returner.toLiteral() + "<");
   }

   /**
    * Send a one way exception back to the other side
    */
   protected final void executeException(MsgInfo receiver, XmlBlasterException e, boolean udp) throws XmlBlasterException, IOException {
      e.isServerSide(glob.isServerSide());
      MsgInfo returner = receiver.createReturner(MsgInfo.EXCEPTION_BYTE);
      returner.setChecksum(false);
      returner.setCompressed(false);
      returner.addException(e);
      sendMessage(returner, receiver.getRequestId(), receiver.getMethodName(), udp);
      if (log.isLoggable(Level.FINE)) log.fine("Successfully sent exception for " + receiver.getMethodName() + "(" + receiver.getRequestId() + ")");
      if (log.isLoggable(Level.FINEST)) log.finest("Successful sent exception for " + receiver.getMethodName() + "() >" + returner.toLiteral() + "<");
   }

   /**
    * Flush the data to the protocol layer (socket, email, ...).
    * Overwrite this in your derived class to send using your specific protocol
    */
   abstract protected void sendMessage(MsgInfo msgInfo, String requestId, MethodName methodName, boolean udp) throws XmlBlasterException, IOException;

   public boolean isCompressZlib() {
      return this.compressZlib;
   }

   public void setCompressZlib(boolean compress) {
      this.compressZlib = compress;
   }

   /**
    * Compressing too small messages won't reduce the size
    * @return The number of bytes, only compress if bigger
    */
   public int getMinSizeForCompression() {
      return this.minSizeForCompression;
   }

   public boolean isCompressZlibStream() {
      return this.compressZlibStream;
   }

   public void setCompressZlibStream(boolean compress) {
      this.compressZlibStream = compress;
   }

   /**
    * @return Returns the updateResponseTimeout.
    */
   public final long getUpdateResponseTimeout() {
      return this.updateResponseTimeout;
   }

   /**
    * @return Returns the useEmailExpiryTimestamp.
    */
   public boolean isUseEmailExpiryTimestamp() {
      return this.useEmailExpiryTimestamp;
   }

   /**
    * @param useEmailExpiryTimestamp The useEmailExpiryTimestamp to set.
    */
   public void setUseEmailExpiryTimestamp(boolean useEmailExpiryTimestamp) {
      this.useEmailExpiryTimestamp = useEmailExpiryTimestamp;
   }

   /**
    * @return a human readable usage help string
    */
   public java.lang.String usage() {
      return
        "interruptInvocation(): Interrupts a blocking request" +
        "\n  The pending message is handled as not delivered and will be kept in queue";
   }

   public boolean isShutdown() {
      return true;
   }

   /**
    * The invocation timeout for "ping" method calls.
    * @return Returns the pingResponseTimeout.
    */
   public final long getPingResponseTimeout() {
      return this.pingResponseTimeout;
   }

   /**
    * The invocation timeout for all remaining method calls like "publish", "connect", "subscribe"
    * but NOT for "ping" and "update"
    * @return Returns the responseTimeout.
    */
   public final long getResponseTimeout() {
      return this.responseTimeout;
   }

   /**
    * @param minSizeForCompression The minSizeForCompression to set.
    */
   public void setMinSizeForCompression(int minSizeForCompression) {
      this.minSizeForCompression = minSizeForCompression;
   }
}

