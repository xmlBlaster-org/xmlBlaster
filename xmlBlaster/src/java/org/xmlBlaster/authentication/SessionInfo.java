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
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.engine.SubscriptionInfo;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.DisconnectQosServer;
import org.xmlBlaster.engine.query.plugins.QueueQueryPlugin;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.I_QueueSizeListener;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
//import org.xmlBlaster.engine.queuemsg.MsgQueueUpdateEntry;
import org.xmlBlaster.util.dispatch.DispatchManager;
import org.xmlBlaster.util.dispatch.I_ConnectionStatusListener;
import org.xmlBlaster.util.error.I_MsgErrorHandler;
import org.xmlBlaster.engine.MsgErrorHandler;

//import EDU.oswego.cs.dl.util.concurrent.ReentrantLock;
import org.xmlBlaster.util.ReentrantLock;


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
public final class SessionInfo implements I_Timeout, I_QueueSizeListener
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
   private DispatchManager dispatchManager;
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
   private long lastNumEntries = -1L;

   // Enforced by I_AdminSubject
   /** Incarnation time of this object instance in millis */
   private long uptime;

   /** uniqueId used to store this in queue */
   private long persistenceId = -1L; 

   private ReentrantLock lock = new ReentrantLock();
   
   /** this is used for administrative gets (queries on callback queue) */
   private QueueQueryPlugin queueQueryPlugin;

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
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, tmp);
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
         this.sessionName = new SessionName(glob, subjectInfo.getSubjectName(), getInstanceId());
      }
      this.ME = "SessionInfo-" + this.sessionName.getAbsoluteName();

      if (log.CALL) log.call(ME, "Creating new SessionInfo " + instanceId + ": " + subjectInfo.toString());
      this.uptime = System.currentTimeMillis();
      this.subjectInfo = subjectInfo;
      this.securityCtx = securityCtx;
      this.connectQos = connectQos;

      this.msgErrorHandler = new MsgErrorHandler(glob, this);
      String type = connectQos.getSessionCbQueueProperty().getType();
      String version = connectQos.getSessionCbQueueProperty().getVersion();
      if (log.TRACE) log.trace(ME, "Creating callback queue type=" + type + " version=" + version);
      this.sessionQueue = glob.getQueuePluginManager().getPlugin(type, version, new StorageId(Constants.RELATING_CALLBACK, this.sessionName.getAbsoluteName()), connectQos.getSessionCbQueueProperty());
      this.sessionQueue.setNotifiedAboutAddOrRemove(true); // Entries are notified to support reference counting
      this.sessionQueue.addQueueSizeListener(this);

      if (this.connectQos.getSessionCbQueueProperty().getCallbackAddresses().length > 0) {
         if (log.TRACE) log.trace(ME, "Creating dispatch manager as ConnectQos contains callback addresses");
         this.dispatchManager = new DispatchManager(glob, this.msgErrorHandler,
                                this.securityCtx, this.sessionQueue, (I_ConnectionStatusListener)null,
                                this.connectQos.getSessionCbQueueProperty().getCallbackAddresses());
      }
      else { // No callback configured
         if (log.TRACE) log.trace(ME, "Don't create dispatch manager as ConnectQos contains no callback addresses");
         this.dispatchManager = null;
      }
      this.expiryTimer = glob.getSessionTimer();
      if (connectQos.getSessionTimeout() > 0L) {
         if (log.TRACE) log.trace(ME, "Setting expiry timer for " + getLoginName() + " to " + connectQos.getSessionTimeout() + " msec");
         timerKey = this.expiryTimer.addTimeoutListener(this, connectQos.getSessionTimeout(), null);
      }
      else {
         if (log.TRACE) log.trace(ME, "Session lasts forever, requested expiry timer was 0");
      }
   }

   public final boolean isAlive() {
      return !this.isShutdown;
   }

   /**
    * Configure server with '-xmlBlaster/acceptWrongSenderAddress true' or "-xmlBlaster/acceptWrongSenderAddress/joe true".
    * @return true: We accept wrong sender address in PublishQos.getSender() (not myself)
    */
   public boolean acceptWrongSenderAddress() {
      boolean may = glob.getProperty().get("xmlBlaster/acceptWrongSenderAddress", false); // TODO: Decide by authorizer
      return glob.getProperty().get("xmlBlaster/acceptWrongSenderAddress/"+getSessionName().getLoginName(), may);
   }

   /**
    * if state==UNDEF we block until we are ALIVE (or DEAD)
   public void waitUntilAlive() {
      //!!!
      log.error(ME, "Implemenation of waitUntilAlive() is missing");
      return;
   }
   */

   /**
    * The protector prevents direct access to this sessionInfo instance. 
    */
   public final SessionInfoProtector getSessionInfoProtector() {
      return this.sessionInfoProtector;
   }

   /**
    * This is a unique instance id per JVM (it is the pubSessionId if the client hasn't specified its own). 
    * <p>
    * It is NOT the secret sessionId and may be published with PtP messages
    * without security danger
    * </p>
    */
   public final long getInstanceId() {
      return this.instanceId;
   }

   /**
    * Access the synchronization object of this SessionInfo instance. 
    */
   public ReentrantLock getLock() {
      return this.lock;
   }

   /**
    * Check if a callback was configured (if client has passed a callback address on connect).
    */
   public final boolean hasCallback() {
      return this.dispatchManager != null && this.isShutdown == false;
   }

   public final I_MsgErrorHandler getMsgErrorHandler() {
      return this.msgErrorHandler;
   }

   /**
    * This is the publicSessionId which is unique in the subject scope. 
    * <p />
    * It is NOT the secret sessionId and may be published with PtP messages
    * without security danger
    * <p />
    * @return The same as getInstanceId()
    * @see #getInstanceId
    */
   public final long getPublicSessionId() {
      return this.sessionName.getPublicSessionId();
   }

   public void finalize() {
      if (timerKey != null) {
         this.expiryTimer.removeTimeoutListener(timerKey);
         timerKey = null;
      }

      if (log.TRACE) log.trace(ME, "finalize - garbage collected " + getSecretSessionId());
   }

   public boolean isShutdown() {
      this.lock.lock();
      try {
         return this.isShutdown; // sync'd because of TimeoutListener?
      }
      finally {
         this.lock.release();
      }
   }

   public void shutdown() {
      if (log.CALL) log.call(ME, "shutdown() of session");
      this.lock.lock();
      try {
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
         if (this.dispatchManager != null)
            this.dispatchManager.shutdown();
         this.subjectInfo = null;
         // this.securityCtx = null; We need it in finalize() getSecretSessionId()
         // this.connectQos = null;
         this.expiryTimer = null;
      }
      finally {
         this.lock.release();
      }
   }

   /**
    * @return null if no callback is configured
    */
   public final DispatchManager getDispatchManager() {
      return this.dispatchManager;
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
      this.lock.lock();
      try {
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
      finally {
         this.lock.release();
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
      if (!hasCallback()) {
         if (log.TRACE) log.trace(ME, "Queing PtP message without having configured a callback to the client, the client needs to reconnect with a valid callback address later");
         //if (!connectQos.getSessionName().isPubSessionIdUser()) { // client has specified its own publicSessionId (> 0)
         //   throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME, "No callback server is configured, can't callback client to send message " + entry.getKeyOid());
         //}
      }
      this.sessionQueue.put(entry, I_Queue.USE_PUT_INTERCEPTOR);
   }

   public final ConnectQosServer getConnectQos() {
      return this.connectQos;
   }

   public final void updateConnectQos(ConnectQosServer newConnectQos) throws XmlBlasterException {
      boolean wantsCallbacks = (newConnectQos.getSessionCbQueueProperty().getCallbackAddresses().length > 0);

      CbQueueProperty cbQueueProperty = newConnectQos.getSessionCbQueueProperty();
      this.sessionQueue.setProperties(cbQueueProperty);
      if (wantsCallbacks && hasCallback()) {
         this.dispatchManager.updateProperty(cbQueueProperty.getCallbackAddresses());
         log.info(ME, "Successfully reconfigured callback address with new settings, other reconfigurations are not yet implemented");
         this.dispatchManager.notifyAboutNewEntry();
      }
      else if (wantsCallbacks && !hasCallback()) {
         log.info(ME, "Successfully reconfigured and created dispatch manager with given callback address");
         this.dispatchManager = new DispatchManager(glob, this.msgErrorHandler,
                              this.securityCtx, this.sessionQueue, (I_ConnectionStatusListener)null,
                              newConnectQos.getSessionCbQueueProperty().getCallbackAddresses());
      }
      else if (!wantsCallbacks && hasCallback()) {
         this.dispatchManager.shutdown();
         this.dispatchManager = null;
         log.info(ME, "Successfully shutdown dispatch manager as no callback address is configured");
      }
      else if (!wantsCallbacks && !hasCallback()) {
         if (log.TRACE) log.trace(ME, "No callback exists and no callback is desired");
         // nothing to do
      }

      // Remember persistent values:
      newConnectQos.isFromPersistenceRecovery(this.connectQos.isFromPersistenceRecovery());
      newConnectQos.setPersistenceUniqueId(this.connectQos.getPersistenceUniqueId());

      this.connectQos = newConnectQos; // Replaces ConnectQosServer settings like bypassCredentialCheck
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
    * We register for queue size changes and notify the subject queue if 
    * we are willing to accept messages again. 
    * Enforced by I_QueueSizeListener
    */
   public void changed(I_Queue queue, long numEntries, long numBytes) {
      boolean hasSubjectEntries = getSubjectInfo().getSubjectQueue().getNumOfEntries() > 0;
      if (lastNumEntries != numEntries) {
         long max = getSessionQueue().getMaxNumOfEntries();
         if (hasSubjectEntries && numEntries < max && lastNumEntries > numEntries) {
            if (log.TRACE) log.trace(ME, "SessionQueue has emptied from " + lastNumEntries +
                           " to " + numEntries + " entries, calling SubjectInfoShuffler.shuffle()");
            this.glob.getSubjectInfoShuffler().shuffle(subjectInfo);
         }
         lastNumEntries = numEntries; // to avoid recursion
      }
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

      sb.append(offset).append("<SessionInfo id='").append(getId());
      long timeToLife = this.expiryTimer.spanToTimeout(timerKey);
      sb.append("' timeout='").append(timeToLife).append("'>");
      // Avoid dump of password: ?
      //sb.append(this.connectQos.toXml(extraOffset+Constants.INDENT));
      if (hasCallback()) {
         sb.append(this.dispatchManager.toXml(extraOffset+Constants.INDENT));
      }
      else {
         sb.append(offset).append(Constants.INDENT).append("<DispatchManager id='NULL'/>");
      }
      if (this.sessionQueue != null) {
         sb.append(this.sessionQueue.toXml(extraOffset+Constants.INDENT));
      }
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
      if (this.dispatchManager == null) return 0L;
      return this.dispatchManager.getDispatchStatistic().getNumUpdate();
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

   public final String getSubscriptionDump() throws XmlBlasterException {
      SubscriptionInfo[] subs = glob.getRequestBroker().getClientSubscriptions().getSubscriptions(this);
      if (subs.length < 1)
         return "";
      StringBuffer sb = new StringBuffer(subs.length * 300);
      sb.append("<SessionInfo id='").append(getId()).append("'>");
      for (int i=0; i<subs.length; i++) {
         /*
         if (subs[i].isCreatedByQuerySubscription()) {
            continue;
         }
         if (sb.length() > 0)
            sb.append(",");
         */
         sb.append(subs[i].toXml(" "));
      }
      sb.append("</SessionInfo>");
      return sb.toString();
   }

   public final String getKillSession() throws XmlBlasterException {
      glob.getAuthenticate().disconnect(securityCtx.getSecretSessionId(), "<qos/>");
      return getId() + " killed";
   }

   /**
    * Gets the uniqueId for the persistence of this session.
    * @return the uniqueId used to identify this session as an  entry
    * in the queue where it is stored  (for persistent subscriptions).
    * If the session is not persistent it returns -1L.
    * 
    */
   public final long getPersistenceUniqueId() {
      return this.connectQos.getPersistenceUniqueId();
   }
   
   /**
    * Sets the uniqueId used to retrieve this session from the persistence
    * @param persistenceId
    */
   public final void setPersistenceUniqueId(long persistenceId) {
      this.connectQos.setPersistenceUniqueId(persistenceId);
   }

   /**
    * Sets the DispachManager belonging to this session to active or inactive.
    * It is initially active. Setting it to false temporarly inhibits dispatch of
    * messages which are in the callback queue. Setting it to true starts the 
    * dispatch again.
    * @param dispatchActive
    */
   public void setDispatcherActive(boolean dispatcherActive) {
      this.dispatchManager.setDispatcherActive(dispatcherActive);
   }
   
   public boolean getDispatcherActive() {
      return this.dispatchManager.isDispatcherActive();
   }

   /**
    * keyData is currently unused but it is needed to be consistent with the 
    * admin get convention (i.e. either take no parameters or always take a key
    * and a qos).
    */
   public MsgUnit[] getCbQueueEntries(QueryKeyData keyData, QueryQosData qosData) throws XmlBlasterException {
      if (this.queueQueryPlugin == null) {
         synchronized (this) {
            if (this.queueQueryPlugin == null) {
               this.queueQueryPlugin = new QueueQueryPlugin(this.glob);
            } 
         }
      }
      return this.queueQueryPlugin.query(this.sessionQueue, keyData, qosData);
   }
   
}
