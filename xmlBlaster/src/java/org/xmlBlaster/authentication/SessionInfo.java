/*------------------------------------------------------------------------------
Name:      SessionInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.jutils.log.LogChannel;

import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.engine.SubscriptionInfo;
import org.xmlBlaster.authentication.SubjectInfo;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.DisconnectQosServer;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
//import org.xmlBlaster.engine.queuemsg.MsgQueueUpdateEntry;
import org.xmlBlaster.util.dispatch.DeliveryManager;
import org.xmlBlaster.util.dispatch.I_ConnectionStatusListener;
import org.xmlBlaster.util.error.I_MsgErrorHandler;
import org.xmlBlaster.util.error.MsgErrorInfo;
import org.xmlBlaster.engine.MsgErrorHandler;



/**
 * SessionInfo stores all known session data about a client.
 * <p />
 * The driver supporting the desired Callback protocol (CORBA/EMAIL/HTTP)
 * is instantiated here.<br />
 * Note that only CORBA is supported in this version.<br />
 * To add a new driver protocol, you only need to implement the empty
 * CallbackEmailDriver.java or any other protocol.
 * <p />
 * It also contains a message queue, where messages are stored
 * until they are delivered at the next login of this client.
 * <p />
 * We distinguish two different unique ID for each login session:
 * <ol>
 *   <li>sessionId: This is the unique, secret session Id which is passed
 *                  by the client on every method invocation to allow authentication</li>
 *   <li>instanceId: This is a unique counter (with respect to one virtual machine JVM).
 *                   It allows 'public' addressing of a session</li>
 * </ol>
 * @author Marcel Ruff
 */
public class SessionInfo implements I_Timeout
{
   public static long sentMessages = 0L;
   private String ME = "SessionInfo";
   /** The cluster wide unique identifier of the session e.g. "/node/heron/client/joe/2" */
   private final SessionName sessionName;
   private SubjectInfo subjectInfo; // all client informations
   private I_Session securityCtx;
   private static long instanceCounter = 0L;
   private long instanceId = 0L;
   private ConnectQosServer connectQos;
   private Timeout expiryTimer;
   private Timestamp timerKey;
   private final Global glob;
   private final LogChannel log;
   /** Do error recovery if message can't be delivered and we give it up */
   private final MsgErrorHandler msgErrorHandler;
   /** manager for sending callback messages */
   private final DeliveryManager deliveryManager;
   private boolean isShutdown = false;
   /** Protects timerKey refresh */
   private final Object EXPIRY_TIMER_MONITOR = new Object();
   private final SessionInfoProtector sessionInfoProtector;

   /**
    * All MsgUnit which shall be delivered to the current session of the client
    * are queued here to be ready to deliver.
    * <p />
    * Node objects = MsgQueueEntry
    */
   private I_Queue sessionQueue;

   // Enforced by I_AdminSubject
   /** Incarnation time of this object instance in millis */
   private long uptime;

   /**
    * Create this instance when a client did a login.
    * <p />
    * @param subjectInfo the SubjectInfo with the login informations for this client
    */
   public SessionInfo(SubjectInfo subjectInfo, I_Session securityCtx, ConnectQosServer connectQos, Global glob)
          throws XmlBlasterException {
      this.glob = glob;
      this.log = this.glob.getLog("auth");
      if (securityCtx==null) {
         String tmp = "SessionInfo(securityCtx==null); A correct security manager must be set.";
         log.error(ME+".illegalArgument", tmp);
         throw new XmlBlasterException(ME+".illegalArgument", tmp);
      }
      this.sessionInfoProtector = new SessionInfoProtector(this);

      //String prefix = glob.getLogPrefix();
      subjectInfo.checkNumberOfSessions(connectQos.getData());

      synchronized (SessionInfo.class) {
         instanceId = instanceCounter;
         instanceCounter--;
      }
      //this.id = ((prefix.length() < 1) ? "client/" : (prefix+"/client/")) + subjectInfo.getLoginName() + "/" + getPublicSessionId();

      if (connectQos.getSessionName().isPubSessionIdUser()) { // client has specified its own publicSessionId (> 0)
         this.sessionName = connectQos.getSessionName();
      }
      else {
         this.sessionName = new SessionName(glob, subjectInfo.getSubjectName(), getPublicSessionId());
      }
      this.ME = "SessionInfo-" + this.sessionName.getAbsoluteName();

      if (log.CALL) log.call(ME, "Creating new SessionInfo " + instanceId + ": " + subjectInfo.toString());
      this.uptime = System.currentTimeMillis();
      this.subjectInfo = subjectInfo;
      this.securityCtx = securityCtx;
      this.connectQos = connectQos;

      if (this.connectQos.getSessionCbQueueProperty().getCallbackAddresses().length > 0) {
         this.msgErrorHandler = new MsgErrorHandler(glob, this);
         String type = connectQos.getSessionCbQueueProperty().getType();
         String version = connectQos.getSessionCbQueueProperty().getVersion();
         if (log.TRACE) log.trace(ME, "Creating callback queue type=" + type + " version=" + version);
         this.sessionQueue = glob.getQueuePluginManager().getPlugin(type, version, new StorageId(Constants.RELATING_CALLBACK, this.sessionName.getAbsoluteName()), connectQos.getSessionCbQueueProperty());
         this.sessionQueue.setNotifiedAboutAddOrRemove(true); // Entries are notified to support reference counting

         this.deliveryManager = new DeliveryManager(glob, this.msgErrorHandler,
                                this.securityCtx, this.sessionQueue, (I_ConnectionStatusListener)null,
                                this.connectQos.getSessionCbQueueProperty().getCallbackAddresses());
      }
      else { // No callback configured
         this.msgErrorHandler = null;
         this.sessionQueue = null;
         this.deliveryManager = null;
      }

      this.expiryTimer = glob.getSessionTimer();
      if (connectQos.getSessionTimeout() > 0L) {
         if (log.TRACE) log.trace(ME, "Setting expiry timer for " + getLoginName() + " to " + connectQos.getSessionTimeout() + " msec");
         timerKey = this.expiryTimer.addTimeoutListener(this, connectQos.getSessionTimeout(), null);
      }
      else
         log.info(ME, "Session lasts forever, requested expiry timer was 0");
   }

   /**
    * The protector prevents direct access to this sessionInfo instance. 
    */
   public final SessionInfoProtector getSessionInfoProtector() {
      return this.sessionInfoProtector;
   }

   /**
    * This is a unique instance id per JVM (it is the pubSessionId).
    * <p />
    * It is NOT the secret sessionId and may be published with PtP messages
    * without security danger
    */
   public final long getInstanceId() {
      return this.instanceId;
   }

   /**
    * Check if a callback was configured (if client has passed a callback address on connect).
    */
   public final boolean hasCallback() {
      return this.deliveryManager != null && this.isShutdown == false;
   }

   public final I_MsgErrorHandler getMsgErrorHandler() {
      return this.msgErrorHandler;
   }

   /**
    * This is a unique instance id per JVM.
    * <p />
    * It is NOT the secret sessionId and may be published with PtP messages
    * without security danger
    * <p />
    * @return The same as getInstanceId()
    * @see #getInstanceId
    */
   public final long getPublicSessionId() {
      return getInstanceId();
   }

   public void finalize() {
      if (timerKey != null) {
         this.expiryTimer.removeTimeoutListener(timerKey);
         timerKey = null;
      }

      if (log.TRACE) log.trace(ME, "finalize - garbage collected " + getSecretSessionId());
   }

   public synchronized boolean isShutdown() {
      return this.isShutdown;
   }

   public synchronized void shutdown() {
      if (log.CALL) log.call(ME, "shutdown() of session");
      this.isShutdown = true;
      if (timerKey != null) {
         this.expiryTimer.removeTimeoutListener(timerKey);
         timerKey = null;
      }
      if (this.sessionQueue != null) {
         this.sessionQueue.shutdown();
         //this.sessionQueue = null; Not set to null to support avoid synchronize(this.sessionQueue)
      }
      if (this.msgErrorHandler != null)
         this.msgErrorHandler.shutdown();
      if (this.deliveryManager != null)
         this.deliveryManager.shutdown();
      this.subjectInfo = null;
      // this.securityCtx = null; We need it in finalize() getSecretSessionId()
      this.connectQos = null;
      this.expiryTimer = null;
   }

   /**
    * @return null if no callback is configured
    */
   public final DeliveryManager getDeliveryManager() {
      return this.deliveryManager;
   }

   /**
    * Call this to reactivate the session expiry to full value
    */
   public final void refreshSession() throws XmlBlasterException {
      if (connectQos.getSessionTimeout() > 0L) {
         synchronized (this.EXPIRY_TIMER_MONITOR) {
            this.timerKey = this.expiryTimer.addOrRefreshTimeoutListener(this, connectQos.getSessionTimeout(), null, this.timerKey);
         }
      }
   }

   /**
    * We are notified when this session expires.
    * @param userData You get bounced back your userData which you passed
    *                 with Timeout.addTimeoutListener()
    */
   public final void timeout(Object userData) {
      synchronized (this) {
         timerKey = null;
         log.warn(ME, "Session timeout for " + getLoginName() + " occurred, session '" + getSecretSessionId() + "' is expired, autologout");
         DisconnectQosServer qos = new DisconnectQosServer(glob);
         qos.deleteSubjectQueue(true);
         try {
            glob.getAuthenticate().disconnect(getSecretSessionId(), qos.toXml());
         } catch (XmlBlasterException e) {
            log.error(ME, "Internal problem with disconnect: " + e.toString());
         }
      }
   }

   /**
    * Is the given address the same as our?
    */
   public final boolean hasAddress(AddressBase addr) {
      if (addr == null) return false;
      CallbackAddress[] arr = ((CbQueueProperty)getSessionQueue().getProperties()).getCallbackAddresses();
      for (int ii=0; arr!=null && ii<arr.length; ii++) {
         // if (arr[ii].isSameAddress(addr))
         if (arr[ii].equals(addr))
            return true;
      }
      return false;
   }

   /*
    * Put the given message into the queue
   public final void queueMessage(MsgUnit msgUnit) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Queing message");
      if (msgUnit == null) {
         log.error(ME+".Internal", "Can't queue null message");
         throw new XmlBlasterException(ME+".Internal", "Can't queue null message");
      }

      MsgQueueUpdateEntry entry = new MsgQueueUpdateEntry(glob, msgUnit, this.sessionQueue, getSessionName());

      queueMessage(entry);
   }
    */

   /**
    * Put the given message entry into the queue
    */
   public final void queueMessage(MsgQueueEntry entry) throws XmlBlasterException {
      if (!hasCallback())
         throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME, "No callback server is configured, can't callback client to send message " + entry.getKeyOid());

      try {
         this.sessionQueue.put(entry, I_Queue.USE_PUT_INTERCEPTOR);
      }
      catch (Throwable e) {
         log.warn(ME, e.toString());
         this.msgErrorHandler.handleError(new MsgErrorInfo(glob, entry, null, e));
      }
   }

   public final ConnectQosServer getConnectQos() {
      return this.connectQos;
   }

   public final void updateConnectQos(ConnectQosServer newConnectQos) throws XmlBlasterException {
      CbQueueProperty cbQueueProperty = newConnectQos.getSessionCbQueueProperty();
      if (hasCallback()) {
         this.deliveryManager.updateProperty(cbQueueProperty);
         log.info(ME, "Successfully reconfigured callback address with new settings, other reconfigurations are not yet implemented");
         this.deliveryManager.notifyAboutNewEntry();
      }
   }

   /**
    * Access the unique login name of a client.
    * <br />
    * @return loginName
    */
   public final String getLoginName() {
      return (subjectInfo==null)?"--":subjectInfo.getLoginName();
   }

   /**
    * Accessing the SubjectInfo object
    * <p />
    * @return SubjectInfo
    */
   public final SubjectInfo getSubjectInfo() {
      return subjectInfo;
   }

   /**
    * @return The secret sessionId of this login session
    */
   public String getSecretSessionId() {
      return this.securityCtx.getSecretSessionId();
   }

   public I_Session getSecuritySession() {
      return this.securityCtx;
   }

   public void setSecuritySession(I_Session ctx) {
      this.securityCtx = ctx;
   }

   /**
    * This queue holds all messages which where addressed to this session
    * @return null if no callback was configured
    */
   public I_Queue getSessionQueue() {
      return this.sessionQueue;
   }

   /**
    * Cluster wide unique identifier: /node/heron/client/<loginName>/<publicSessionId>,
    * e.g. for logging only
    * <p />
    * @return e.g. "/node/heron/client/joe/2
    */
   public final String getId() {
      return this.sessionName.getAbsoluteName();
   }

   public final SessionName getSessionName() {
      return this.sessionName;
   }

   /**
    * Check cluster wide if the sessions are identical
    */
   public boolean isSameSession(SessionInfo sessionInfo) {
      return getId().equals(sessionInfo.getId());
   }

   /**
    * @see #getId
    */
   public final String toString() {
      return getId();
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of SessionInfo as a XML ASCII string
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of SessionInfo as a XML ASCII string
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<SessionInfo id='").append(getId()).append("' timeout='").append("'>");
      if (hasCallback())
         sb.append(this.sessionQueue.toXml(extraOffset+Constants.INDENT));
      sb.append(offset).append("</SessionInfo>");

      return sb.toString();
   }

   //=========== Enforced by I_AdminSession ================
   /**
    * @return uptime in seconds
    */
   public final long getUptime() {
      return (System.currentTimeMillis() - this.uptime)/1000L;
   }
   /**
    * How many update where sent for this client, the sum of all session and
    * subject queues of this clients.
    */
   public final long getNumUpdates() {
      if (this.deliveryManager == null) return 0L;
      return this.deliveryManager.getDeliveryStatistic().getNumUpdate();
   }

   public final long getCbQueueNumMsgs() {
      if (this.sessionQueue == null) return 0L;
      return this.sessionQueue.getNumOfEntries();
   }

   public final long getCbQueueMaxMsgs() {
      if (this.sessionQueue == null) return 0L;
      return this.sessionQueue.getMaxNumOfEntries();
   }

   public final String getSubscriptionList() throws XmlBlasterException {
      SubscriptionInfo[] subs = glob.getRequestBroker().getClientSubscriptions().getSubscriptions(this);
      if (subs.length < 1)
         return "";
      StringBuffer sb = new StringBuffer(subs.length * 30);
      for (int i=0; i<subs.length; i++) {
         if (subs[i].isCreatedByQuerySubscription()) {
            continue;
         }
         if (sb.length() > 0)
            sb.append(",");
         sb.append(subs[i].getSubscriptionId());
      }
      return sb.toString();
   }

   public final String getKillSession() throws XmlBlasterException {
      glob.getAuthenticate().disconnect(securityCtx.getSecretSessionId(), "<qos/>");
      return getId() + " killed";
   }
}
