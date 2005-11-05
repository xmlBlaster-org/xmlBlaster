/*------------------------------------------------------------------------------
Name:      Executor.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Send/receive messages over outStream and inStream.
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.protocol;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_ResponseListener;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.xbformat.I_ProgressListener;
import org.xmlBlaster.util.xbformat.MsgInfo;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.engine.qos.AddressServer;

import EDU.oswego.cs.dl.util.concurrent.Latch;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/**
 * Send/receive messages over outStream and inStream.
 * <p />
 * A common base class for socket based messaging.
 * Allows to block during a request and deliver the return message
 * to the waiting thread.
 *
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.socket.html" target="others">xmlBlaster SOCKET access protocol</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public abstract class Executor
{
   private String ME = Executor.class.getName();
   protected Global glob;
   private LogChannel log;
   /** Reading from socket */
   protected InputStream iStream;
   /** Writing to socket */
   protected OutputStream oStream;
   /** The prefix to create a unique requestId namspace (is set to the loginName) */
   protected String prefix = null;
   /** The client login name */
   protected String loginName = "";
   /** How long to block on remote call waiting on response */
   protected long responseWaitTime = 0;
   /** How long to block the socket on input stream read */
   protected long soTimeout = 0;
   /** How long to block the socket on close with remaining data */
   protected long soLingerTimeout = 0; // Constants.MINUTE_IN_MILLIS -> this can lead to blocking close(), so we choose '0'
   /** This is the client side */
   protected I_CallbackExtended cbClient;
   /** The singleton handle for this xmlBlaster server (the server side) */
   protected I_XmlBlaster xmlBlasterImpl;
   private final Set latchSet = new HashSet();
   private AddressBase addressConfig;
   /** A listener may register to receive send/receive progress informations */
   protected I_ProgressListener progressListener;

   /**
    * For listeners who want to be informed about return messages or exceptions,
    * the invocation is blocking during this period.
    * <p />
    * The key is the String requestId, the value the listener thread I_ResponseListener
    */
   protected final Map responseListenerMap = Collections.synchronizedMap(new HashMap());
   /** Used for execute() */
   public static final boolean ONEWAY = false;
   /** Used for execute() */
   public static final boolean WAIT_ON_RESPONSE = true;

   public Executor() {
   }

   /**
    * Used by SocketCallbackImpl on client side, uses I_CallbackExtended to invoke client classes
    * <p />
    * Used by HandleClient on server side, uses I_XmlBlaster to invoke xmlBlaster core
    * <p />
    * This executor has mixed client and server specific code for two reasons:<br />
    * - Possibly we can use the same socket between two xmlBlaster server (load balance)<br />
    * - Everything is together<br />
    * @param iStream The reading stream (for example a socket InputStream)
    * @param oStream The writing stream (for example a socket OutputStream)
    */
   protected void initialize(Global glob, AddressBase addressConfig, InputStream iStream, OutputStream oStream) throws IOException {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.log = this.glob.getLog("socket");
      this.addressConfig = addressConfig;

      if (Constants.COMPRESS_ZLIB_STREAM.equals(this.addressConfig.getCompressType())) { // Statically configured for server side protocol plugin
         log.info(ME, "Full stream compression enabled with '" + Constants.COMPRESS_ZLIB_STREAM + "'");
         this.iStream = new ZFlushInputStream(iStream);
         this.oStream =  new ZFlushOutputStream(oStream);
      }
      else if (Constants.COMPRESS_ZLIB.equals(this.addressConfig.getCompressType())) { // Compress each message indiviually
         log.info(ME, "Message compression enabled with  '" + Constants.COMPRESS_ZLIB + "', minimum size for compression is " + this.addressConfig.getMinSize() + " bytes");
         this.iStream = new ZBlockInputStream(iStream);
         this.oStream = new ZBlockOutputStream(oStream, (int)this.addressConfig.getMinSize());
      }
      else {
         this.iStream = iStream;
         this.oStream = oStream;
      }

      setResponseWaitTime(addressConfig.getEnv("responseTimeout", Constants.MINUTE_IN_MILLIS).getValue());
      if (log.TRACE) log.trace(ME, this.addressConfig.getEnvLookupKey("responseTimeout") + "=" + this.responseWaitTime);
      // the responseWaitTime is used later to wait on a return value
      // additionally we protect against blocking on socket level during invocation
      // JacORB CORBA allows similar setting with "jacorb.connection.client_idle_timeout"
      //        and with "jacorb.client.pending_reply_timeout"
   }

   public I_ProgressListener registerProgressListener(I_ProgressListener listener) {
      I_ProgressListener oldOne = this.progressListener;
      this.progressListener = listener;
      return oldOne;
   }

   public final I_ProgressListener getProgressListener() {
      return this.progressListener;
   }

   /**
    * Set the given millis to protect against blocking client.
    * @param millis If <= 0 it is set to one minute
    */
   public final void setResponseWaitTime(long millis) {
      if (millis <= 0L) {
         log.warn(ME, this.addressConfig.getEnvLookupKey("responseTimeout") + "=" + millis +
                      " is invalid, setting it to " + Constants.MINUTE_IN_MILLIS + " millis");
         this.responseWaitTime = Constants.MINUTE_IN_MILLIS;
      }
      this.responseWaitTime = millis;
   }

   /**
    * Set the given millis to protect against blocking socket on input stream read() operations
    * @param millis If <= 0 it is disabled
    */
   public final void setSoTimeout(long millis) {
      if (millis < 0L) {
         log.warn(ME, this.addressConfig.getEnvLookupKey("SoTimeout") + "=" + millis +
                      " is invalid, is invalid, deactivating timeout");
         this.soTimeout = 0L;
      }
      this.soTimeout = millis;
   }

   public final long getSoTimeout() {
      return this.soTimeout;
   }

   /**
    * Set the given millis to timeout socket close if data are lingering
    * @param millis If < 0 it is set to one minute, 0 disable timeout
    */
   public final void setSoLingerTimeout(long millis) {
      if (millis < 0L) {
         log.warn(ME, this.addressConfig.getEnvLookupKey("SoLingerTimeout") + "=" + millis +
                      " is invalid, setting it to " + Constants.MINUTE_IN_MILLIS + " millis");
         this.soLingerTimeout = Constants.MINUTE_IN_MILLIS;
      }
      this.soLingerTimeout = millis;
   }

   public final long getSoLingerTimeout() {
      return this.soLingerTimeout;
   }

   public final void setCbClient(I_CallbackExtended cbClient) {
      this.cbClient = cbClient;
   }

   public final void setXmlBlasterCore(I_XmlBlaster xmlBlaster) {
      this.xmlBlasterImpl = xmlBlaster;
   }

   public final I_CallbackExtended getCbClient() {
      return this.cbClient;
   }

   public final OutputStream getOutputStream() {
      return this.oStream;
   }

   public final InputStream getInputStream() {
      return this.iStream;
   }

   public void finalize() {
      if (log.TRACE) log.trace(ME, "Garbage Collected");
   }

   /**
    * Sets the loginName and automatically the requestId as well
    */
   protected final void setLoginName(String loginName) {
      this.loginName = loginName;
      if (loginName != null && loginName.length() > 0)
         this.prefix = this.loginName + ":";
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
      Object o = responseListenerMap.put(requestId, l);
      if (o == null) {
         if (log.TRACE) log.trace(ME, "Added addResponseListener requestId=" + requestId);
      }
      else {
         log.warn(ME, "Added addResponseListener requestId=" + requestId + " but there was already one");
      }
   }


   /**
    * Removes the specified listener.
    */
   public final void removeResponseListener(String requestId) {
      if (requestId == null) {
         throw new IllegalArgumentException("removeResponseListener() with requestId=null");
      }
      synchronized (responseListenerMap) {
         Object o = responseListenerMap.remove(requestId);
         if (o == null) {
            log.error(ME, "removeResponseListener(" + requestId + ") entry not found");
         }
         else {
            if (log.TRACE) log.trace(ME, "removeResponseListener(" + requestId + ") done");
         }
      }
   }


   /**
    * Get the response listener object
    */
   public final I_ResponseListener getResponseListener(String requestId) {
      if (requestId == null) {
         throw new IllegalArgumentException("getResponseListener() with requestId=null");
      }
      return (I_ResponseListener)responseListenerMap.get(requestId);
   }


   /**
    * Handle common messages
    * @return false: for connect() and disconnect() which must be handled by the base class
    */
   public boolean receive(MsgInfo receiver, boolean udp) throws XmlBlasterException, IOException {
      if (log.TRACE) log.trace(ME, "Receiving '" + receiver.getTypeStr() + "' message " + receiver.getMethodName() + "(" + receiver.getRequestId() + ")");

      if (receiver.isInvoke()) {
         // handling invocations ...

         if (MethodName.PUBLISH_ONEWAY == receiver.getMethodName()) {
            MsgUnitRaw[] arr = receiver.getMessageArr();
            if (arr == null || arr.length < 1) {
               log.error(ME, "Invocation of " + receiver.getMethodName() + "() failed, missing arguments");
               return true;
            }
            xmlBlasterImpl.publishOneway((AddressServer)this.addressConfig, receiver.getSecretSessionId(), arr);
         }
         else if (MethodName.PUBLISH == receiver.getMethodName()) {
            MsgUnitRaw[] arr = receiver.getMessageArr();
            if (arr == null || arr.length < 1)
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Invocation of " + receiver.getMethodName() + "() failed, missing arguments");
            String[] response = xmlBlasterImpl.publishArr((AddressServer)this.addressConfig, receiver.getSecretSessionId(), arr);
            executeResponse(receiver, response, udp);
         }
         else if (MethodName.UPDATE_ONEWAY == receiver.getMethodName()) {
            try {
               I_CallbackExtended cbClientTmp = this.cbClient;
               if (cbClientTmp == null) {
                  throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_CALLBACKSERVER_NOTAVAILABLE, ME, "The SOCKET callback driver is not created, can't process the remote invocation. Try configuration ' -protocol SOCKET'");
               }
               MsgUnitRaw[] arr = receiver.getMessageArr();
               if (arr == null || arr.length < 1) {
                  log.error(ME, "Invocation of " + receiver.getMethodName() + "() failed, missing arguments");
                  return true;
               }
               cbClientTmp.updateOneway(receiver.getSecretSessionId(), arr);
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
         else if (MethodName.UPDATE == receiver.getMethodName()) {
            try {
               I_CallbackExtended cbClientTmp = this.cbClient; // Remember to avoid synchronized block
               if (cbClientTmp == null) {
                  throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_CALLBACKSERVER_NOTAVAILABLE, ME, "No SOCKET callback driver is available, can't process the remote invocation.");
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
            MsgUnitRaw[] arr = receiver.getMessageArr();
            if (arr == null || arr.length != 1)
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Invocation of " + receiver.getMethodName() + "() failed, wrong arguments");
            MsgUnitRaw[] response = xmlBlasterImpl.get((AddressServer)this.addressConfig, receiver.getSecretSessionId(), arr[0].getKey(), arr[0].getQos());
            executeResponse(receiver, response, udp);
         }
         else if (MethodName.PING == receiver.getMethodName()) {
            MsgUnitRaw[] arr = receiver.getMessageArr();
            if (this.cbClient == null && !glob.isServerSide()) {
               XmlBlasterException xmlBlasterException = new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_CALLBACKSERVER_NOTAVAILABLE, ME, "No SOCKET callback driver is available, can't process the remote invocation.");
               executeException(receiver, xmlBlasterException, udp);
               return true;
            }
            if (xmlBlasterImpl != null) { // Server side: Forward ping to xmlBlaster core
               String response = xmlBlasterImpl.ping((AddressServer)this.addressConfig, /*receiver.getSecretSessionId(),*/ (arr.length>0) ? arr[0].getQos() : "<qos/>");
               executeResponse(receiver, response, udp); // Constants.RET_OK="<qos><state id='OK'/></qos>" or current run level
            }
            else { // Client side: answer directly, not forwarded to client code
               executeResponse(receiver, Constants.RET_OK, udp); // Constants.RET_OK="<qos><state id='OK'/></qos>" or current run level
            }
         }
         else if (MethodName.SUBSCRIBE == receiver.getMethodName()) {
            MsgUnitRaw[] arr = receiver.getMessageArr();
            if (arr == null || arr.length != 1)
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Invocation of " + receiver.getMethodName() + "() failed, wrong arguments");
            String response = xmlBlasterImpl.subscribe((AddressServer)this.addressConfig, receiver.getSecretSessionId(), arr[0].getKey(), arr[0].getQos());
            executeResponse(receiver, response, udp);
         }
         else if (MethodName.UNSUBSCRIBE == receiver.getMethodName()) {
            MsgUnitRaw[] arr = receiver.getMessageArr();
            if (arr == null || arr.length != 1)
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Invocation of " + receiver.getMethodName() + "() failed, wrong arguments");
            String[] response = xmlBlasterImpl.unSubscribe((AddressServer)this.addressConfig, receiver.getSecretSessionId(), arr[0].getKey(), arr[0].getQos());
            executeResponse(receiver, response, udp);
         }
         else if (MethodName.ERASE == receiver.getMethodName()) {
            MsgUnitRaw[] arr = receiver.getMessageArr();
            if (arr == null || arr.length != 1)
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Invocation of " + receiver.getMethodName() + "() failed, wrong arguments");
            String[] response = xmlBlasterImpl.erase((AddressServer)this.addressConfig, receiver.getSecretSessionId(), arr[0].getKey(), arr[0].getQos());
            executeResponse(receiver, response, udp);
         }
         else if (MethodName.CONNECT == receiver.getMethodName()) {
            return false;
         }
         else if (MethodName.DISCONNECT == receiver.getMethodName()) {
            return false;
         }
         else {
            log.warn(ME, "Ignoring received invocation message '" + receiver.getMethodName() + "' with requestId=" + receiver.getRequestId() + ", nobody is interested in it: " + receiver.toLiteral());
            if (log.DUMP) log.dump(ME, "Ignoring received message, nobody is interested in it:\n>" + receiver.toLiteral() + "<");
         }

         return true;
      }

      // Handling response or exception ...
      I_ResponseListener listener = getResponseListener(receiver.getRequestId());
      if (listener == null) {
         log.warn(ME, "Ignoring received '" + receiver.getMethodName() + "' response message, requestId=" + receiver.getRequestId() + ", nobody is interested in it");
         if (log.DUMP) log.dump(ME, "Ignoring received message, nobody is interested in it: >" + receiver.toLiteral() + "<");
         return true;
      }
      removeResponseListener(receiver.getRequestId());

      if (receiver.isResponse()) {
         if (receiver.getMethodName().returnsMsgArr()) { // GET returns MsgUnitRaw[]
            listener.responseEvent(receiver.getRequestId(), receiver.getMessageArr());
         }
         else if (receiver.getMethodName().returnsStringArr()) {  // PUBLISH etc. return String[]
            listener.responseEvent(receiver.getRequestId(), receiver.getQosArr());
         }
         else if (receiver.getMethodName().returnsString()) { // SUBSCRIBE, CONNECT etc. return a String
            listener.responseEvent(receiver.getRequestId(), receiver.getQos());
         }
         else {  // SUBSCRIBE, CONNECT etc. return a String
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "The method " + receiver.getMethodName() + " is not expected in this context");
         }
      }
      else if (receiver.isException()) { // XmlBlasterException
         listener.responseEvent(receiver.getRequestId(), receiver.getException());
      }
      else {
         log.error(ME, "PANIC: Invalid response message for " + receiver.getMethodName());
         listener.responseEvent(receiver.getRequestId(), new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Invalid response message '" + receiver.getMethodName()));
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
    * @return the response object of the request, of type String(QoS), MsgUnitRaw[] or XmlBlasterException
    */
   public Object execute(MsgInfo parser, boolean expectingResponse, boolean udp) throws XmlBlasterException, IOException {

      String requestId = parser.createRequestId(prefix);
      if (log.TRACE) log.trace(ME, "Invoking  parser type='" + parser.getTypeStr() + "' message " + parser.getMethodName() + "(requestId=" + requestId + ") oneway=" + !expectingResponse + " udp=" + udp);

      final Object[] response = new Object[1];  // As only final variables are accessable from the inner class, we put the response in this array
      response[0] = null;
      final Latch startSignal;

      // Register the return value / Exception listener ...
      if (expectingResponse) {
         startSignal = new Latch(); // defaults to false
         synchronized (latchSet) { latchSet.add(startSignal); } // remember all blocking threads for release on shutdown
         if (!hasConnection()) return null;
         addResponseListener(requestId, new I_ResponseListener() {
            public void responseEvent(String reqId, Object responseObj) {
               if (log.TRACE) log.trace(ME+".responseEvent()", "RequestId=" + reqId + ": return value arrived ...");
               response[0] = responseObj;
               startSignal.release(); // wake up
            }
         });
      }
      else
         startSignal = null;

      // Send the message / method invocation ...
      if (log.DUMP) log.dump(ME, "Sending now : >" + parser.toLiteral() + "<");
      try {
         sendMessage(parser, parser.getRequestId(), parser.getMethodName(), udp);
         // if (log.TRACE) log.trace(ME, "Successfully sent " + parser.getNumMessages() + " messages");
      }
      catch (Throwable e) {
         if (startSignal != null) {
            synchronized (latchSet) { latchSet.remove(startSignal); }
         }
         String str = "Socket blocked for " + getSoTimeout() + " millis, giving up now waiting on " + parser.getMethodName() + "(" + requestId + ") response. You can change it with -plugin/socket/SoTimeout <millis>";
         if (e instanceof XmlBlasterException) {
            if (log.TRACE) log.trace(ME, str + ": " + e.toString());
            throw (XmlBlasterException)e;
         }
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_TIMEOUT, ME, str, e);
      }

      if (log.DUMP) log.dump(ME, "Successful sent message: >" + parser.toLiteral() + "<");

      if (!expectingResponse) {
         return null;
      }

      // Waiting for the response to arrive ...
      try {
         boolean awakened = false;
         while (true) {
            try {
               awakened = startSignal.attempt(responseWaitTime); // block max. milliseconds
               break;
            }
            catch (InterruptedException e) {
               log.warn(ME, "Waking up (waited on " + parser.getMethodName() + "(" + requestId + ") response): " + e.toString());
               // try again
            }
         }
         if (awakened) {
            if (log.TRACE) log.trace(ME, "Waking up, got response for " + parser.getMethodName() + "(requestId=" + requestId + ")");
            if (response[0]==null) // Caused by freePendingThreads()
               throw new IOException(ME + ": Lost socket connection for " + parser.getMethodName() + "(requestId=" + requestId + ")");

            if (log.DUMP) log.dump(ME, "Response for " + parser.getMethodName() + "(" + requestId + ") is: " + response[0].toString());
            if (response[0] instanceof XmlBlasterException)
               throw (XmlBlasterException)response[0];
            return response[0];
         }
         else {
            String str = "Timeout of " + responseWaitTime + " milliseconds occured when waiting on " + parser.getMethodName() + "(" + requestId + ") response. You can change it with -plugin/socket/responseTimeout <millis>";
            removeResponseListener(requestId);
            throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_RESPONSETIMEOUT, ME, str);
         }
      }
      finally {
         synchronized (latchSet) { latchSet.remove(startSignal); }
      }
   }

   /**
    * If we detect somewhere that the socket is down
    * use this method to free blocking threads which wait on responses
    */
   public final void freePendingThreads() {
      if (log != null && log.TRACE && latchSet!=null && latchSet.size()>0) log.trace(ME, "Freeing " + latchSet.size() + " pending threads (waiting on responses) from their ugly blocking situation");
      if (latchSet != null) {
         while (true) {
            Latch l = null;
            synchronized (latchSet) {
               Iterator it = latchSet.iterator();
               if (it.hasNext()) {
                  l = (Latch)it.next();
                  it.remove();
               }
               else
                  break;
            }
            if (l == null)
               break;
            l.release();
         }
         synchronized(latchSet) {
            latchSet.clear();
         }
      }
   }

   /**
    * Send a one way response message back to the other side
    */
   protected final void executeResponse(MsgInfo receiver, Object response, boolean udp) throws XmlBlasterException, IOException {
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
      if (log.TRACE) log.trace(ME, "Successfully sent response for " + receiver.getMethodName() + "(" + receiver.getRequestId() + ")");
      if (log.DUMP) log.dump(ME, "Successful sent response for " + receiver.getMethodName() + "() >" + returner.toLiteral() + "<");
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
      if (log.TRACE) log.trace(ME, "Successfully sent exception for " + receiver.getMethodName() + "(" + receiver.getRequestId() + ")");
      if (log.DUMP) log.dump(ME, "Successful sent exception for " + receiver.getMethodName() + "() >" + returner.toLiteral() + "<");
   }

   /**
    * Flush the data to the socket. 
    * Overwrite this in your derived class to send UDP 
    */
   protected void sendMessage(MsgInfo msgInfo, String requestId, MethodName methodName, boolean udp) throws XmlBlasterException, IOException {
      byte[] msg = msgInfo.createRawMsg();
      I_ProgressListener listener = this.progressListener;
      if (listener != null) {
         listener.progressWrite("", 0, msg.length);
      }
      synchronized (oStream) {
         oStream.write(msg);
         oStream.flush();
         if (log.TRACE) log.trace(ME, "TCP data is send");
      }
      if (listener != null) {
         listener.progressWrite("", msg.length, msg.length);
      }
   }

   //abstract boolean shutdown();
   //abstract void shutdown();
}

