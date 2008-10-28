/*------------------------------------------------------------------------------
Name:      SessionInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.contrib.ClientPropertiesInfo;
import org.xmlBlaster.engine.MsgErrorHandler;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.engine.SubscriptionInfo;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.DisconnectQosServer;
import org.xmlBlaster.engine.qos.SubscribeQosServer;
import org.xmlBlaster.engine.qos.UnSubscribeQosServer;
import org.xmlBlaster.engine.query.plugins.QueueQueryPlugin;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.IsoDateParser;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.ReentrantLock;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.admin.extern.JmxMBeanHandle;
import org.xmlBlaster.util.checkpoint.I_Checkpoint;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.dispatch.DispatchConnection;
import org.xmlBlaster.util.dispatch.DispatchManager;
import org.xmlBlaster.util.dispatch.DispatchStatistic;
import org.xmlBlaster.util.dispatch.I_ConnectionStatusListener;
import org.xmlBlaster.util.error.I_MsgErrorHandler;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.I_Storage;
import org.xmlBlaster.util.queue.I_StorageSizeListener;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.cache.CacheQueueInterceptorPlugin;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;


/**
 * SessionInfo stores all known session data about a client.
 * <p />
 * One client (SubjectInfo) may have multiple login sessions.
 * Each session has its callback queue to deliver subscribed
 * messages to the client.
 * <p />
 * We distinguish two different unique ID for each login session:
 * <ol>
 *   <li>sessionId: This is the unique, secret session Id which is passed
 *                  by the client on every method invocation to allow authentication</li>
 *   <li>instanceId: This is a unique counter (with respect to one virtual machine JVM).
 *                   It allows 'public' addressing of a session</li>
 * </ol>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.qos.login.session.html">The engine.qos.login requirement</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public final class SessionInfo implements I_Timeout, I_StorageSizeListener
{
   private String ME = "SessionInfo";
   private ContextNode contextNode;
   /** The cluster wide unique identifier of the session e.g. "/node/heron/client/joe/2" */
   private final SessionName sessionName;
   private SubjectInfo subjectInfo; // all client informations
   private I_Session securityCtx;
   private static long instanceCounter = 0L;
   private long instanceId = 0L;
   /** The current connection address from the protocol plugin */
   private ConnectQosServer connectQos;
   private Timeout expiryTimer;
   private Timestamp timerKey;
   private ServerScope glob;
   private static Logger log = Logger.getLogger(SessionInfo.class.getName());
   /** Do error recovery if message can't be delivered and we give it up */
   private MsgErrorHandler msgErrorHandler;
   /** manager for sending callback messages */
   private DispatchManager dispatchManager;
   /** Statistic about send/received messages, can be null if there is a DispatchManager around */
   private volatile DispatchStatistic statistic;
   private boolean isShutdown = false;
   /** Protects timerKey refresh */
   private final Object EXPIRY_TIMER_MONITOR = new Object();
   private SessionInfoProtector sessionInfoProtector;
   /** My JMX registration */
   private JmxMBeanHandle mbeanHandle;
   /** To prevent noisy warnings */
   private boolean transientWarn;
   /** Can be optionally used by authorization frameworks */
   private Object authorizationCache;
   private boolean blockClientSessionLogin;

   private XmlBlasterException transportConnectFail;

   /** Holding properties send by our remote client via the topic __sys__sessionProperties */
   private ClientPropertiesInfo remoteProperties;

   private boolean acceptWrongSenderAddress;

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
   private long startupTime;

   private ReentrantLock lock = new ReentrantLock();

   /** this is used for administrative gets (queries on callback queue) */
   private volatile QueueQueryPlugin queueQueryPlugin;

   private boolean initialized;

   /**
    * Create this instance when a client did a login.
    * <p />
    * You need to call init()!
    */
   SessionInfo(ServerScope glob, SessionName sessionName) {
      this.glob = glob;
      synchronized (SessionInfo.class) {
         instanceId = instanceCounter;
         instanceCounter--;
      }
      // client has specified its own publicSessionId (> 0)
      this.sessionName = (sessionName.isPubSessionIdUser()) ? sessionName :
         new SessionName(glob, sessionName, getInstanceId());
   }
   
   /**
    * @param subjectInfo the SubjectInfo with the login informations for this client
    */
   void init(SubjectInfo subjectInfo, I_Session securityCtx, ConnectQosServer connectQos)
          throws XmlBlasterException {

      if (securityCtx==null) {
         String tmp = "SessionInfo(securityCtx==null); A correct security manager must be set.";
         log.severe(tmp);
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, tmp);
      }
      this.sessionInfoProtector = new SessionInfoProtector(this);

      //this.id = ((prefix.length() < 1) ? "client/" : (prefix+"/client/")) + subjectInfo.getLoginName() + "/" + getPublicSessionId();

      this.contextNode = new ContextNode(ContextNode.SESSION_MARKER_TAG, ""+this.sessionName.getPublicSessionId(),
                                       subjectInfo.getContextNode());
      this.ME = this.instanceId + "-" + this.sessionName.getRelativeName();


      if (log.isLoggable(Level.FINER)) log.finer(ME+": Creating new SessionInfo " + instanceId + ": " + subjectInfo.toString());
      this.startupTime = System.currentTimeMillis();
      this.subjectInfo = subjectInfo;
      this.securityCtx = securityCtx;
      this.connectQos = connectQos;

      this.msgErrorHandler = new MsgErrorHandler(glob, this);
      String type = connectQos.getSessionCbQueueProperty().getType();
      String version = connectQos.getSessionCbQueueProperty().getVersion();
      if (log.isLoggable(Level.FINE)) log.fine(ME+": Creating callback queue type=" + type + " version=" + version);
      this.sessionQueue = glob.getQueuePluginManager().getPlugin(type, version, new StorageId(Constants.RELATING_CALLBACK, this.sessionName.getAbsoluteName()), connectQos.getSessionCbQueueProperty());
      this.sessionQueue.setNotifiedAboutAddOrRemove(true); // Entries are notified to support reference counting
      this.sessionQueue.addStorageSizeListener(this);

      CallbackAddress[] cba = this.connectQos.getSessionCbQueueProperty().getCallbackAddresses();
      if (cba.length > 0) {
         if (log.isLoggable(Level.FINE)) log.fine(ME+": Creating dispatch manager as ConnectQos contains callback addresses");
         for (int i=0; i<cba.length; i++) {
            cba[i].setSessionName(this.sessionName);
            cba[i].addClientProperty(new ClientProperty("__ContextNode", "String", null, this.contextNode.getAbsoluteName()));
            cba[i].setFromPersistenceRecovery(connectQos.isFromPersistenceRecovery());
         }
         this.dispatchManager = new DispatchManager(glob, this.msgErrorHandler,
                                this.securityCtx, this.sessionQueue, (I_ConnectionStatusListener)null,
                                cba, this.sessionName);
      }
      else { // No callback configured
         if (log.isLoggable(Level.FINE)) log.fine(ME+": Don't create dispatch manager as ConnectQos contains no callback addresses");
         this.dispatchManager = null;
      }
      this.expiryTimer = glob.getSessionTimer();
      if (connectQos.getSessionTimeout() > 0L) {
         if (log.isLoggable(Level.FINE)) log.fine(ME+": Setting expiry timer for " + getLoginName() + " to " + connectQos.getSessionTimeout() + " msec");
         this.timerKey = this.expiryTimer.addTimeoutListener(this, connectQos.getSessionTimeout(), null);
      }
      else {
         if (log.isLoggable(Level.FINE)) log.fine(ME+": Session lasts forever, requested expiry timer was 0");
      }

      // "__remoteProperties"
      if (this.connectQos.getData().getClientProperty(Constants.CLIENTPROPERTY_REMOTEPROPERTIES, false)) {
          mergeRemoteProperties(this.connectQos.getData().getClientProperties());
      }

      // TODO: Decide by authorizer
      // see Authenticate.java boolean may = glob.getProperty().get("xmlBlaster/acceptWrongSenderAddress", false);
      this.acceptWrongSenderAddress = glob.getProperty().get("xmlBlaster/acceptWrongSenderAddress/"+getSessionName().getLoginName(), false);

      // JMX register "client/joe/1"
      this.mbeanHandle = this.glob.registerMBean(this.contextNode, this.sessionInfoProtector);
      
      this.initialized = true;
   }
   
   final boolean isInitialized() {
      return this.initialized;
   }

   public final boolean isAlive() {
      return !isShutdown();
   }

   /**
    * The unique name of this session instance.
    * @return Never null, for example "/xmlBlaster/node/heron/client/joe/session/-2"
    */
   public final ContextNode getContextNode() {
      return this.contextNode;
   }

   /**
    * Configure server with '-xmlBlaster/acceptWrongSenderAddress true'
    * or "-xmlBlaster/acceptWrongSenderAddress/joe true".
    * Is available using JMX.
    * @return true: We accept wrong sender address in PublishQos.getSender() (not myself)
    */
   public boolean isAcceptWrongSenderAddress() {
      return this.acceptWrongSenderAddress;
   }

   /**
    * @param acceptWrongSenderAddress the acceptWrongSenderAddress to set
    */
   public void setAcceptWrongSenderAddress(boolean acceptWrongSenderAddress) {
      boolean old = this.acceptWrongSenderAddress;
      this.acceptWrongSenderAddress = acceptWrongSenderAddress;
      String tmp = ME + "Changed acceptWrongSenderAddress from " + old + " to " + this.acceptWrongSenderAddress + ".";
      //if (glob.getAuthenticate().iscceptWrongSenderAddress()
      if (this.acceptWrongSenderAddress == true)
         log.warning(tmp + " Caution: This client can now publish messages using anothers login name as sender");
      else
         log.info(tmp + " Faking anothers publisher address is not possible");
   }

   /**
    * The address information got from the protocol plugin.
    * @return Can be null
    */
   public AddressServer getAddressServer() {
      return (this.connectQos == null) ? null : this.connectQos.getAddressServer();
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
    * Freeing sessionInfo lock with test/assert code. 
    * @param errorInfo
    * @return number of holds released
    */
   public long releaseLockAssertOne(String errorInfo) {
      long holds = this.lock.holds();
      if (holds != 1) {
         log.severe("Topic=" + getId() + " receiverSession=" + getId() +". Not expected lock holds=" + holds + "\n" + Global.getStackTraceAsString(null));
      }
      if (holds > 0) {
         try {
            this.lock.release(holds);
         }
         catch (Throwable e) {
            log.severe("Free lock failed: " + e.toString() + " " + errorInfo + " receiverSession=" + getId() +". Not expected lock holds=" + holds + "\n" + Global.getStackTraceAsString(null));
         }
      }
      return holds;
   }

   /**
    * Check if a callback was configured (if client has passed a callback address on connect).
    */
   public final boolean hasCallback() {
      return this.dispatchManager != null && isShutdown() == false;
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
      try {
         removeExpiryTimer();
         if (log.isLoggable(Level.FINE)) log.fine(ME+": finalize - garbage collected " + getSecretSessionId());
      }
      catch (Throwable e) {
         e.printStackTrace();
      }
      try {
         super.finalize();
      }
      catch (Throwable e) {
         e.printStackTrace();
      }
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

   public void removeExpiryTimer() {
      synchronized (this.EXPIRY_TIMER_MONITOR) {
         if (this.timerKey != null) {
            this.expiryTimer.removeTimeoutListener(this.timerKey);
            this.timerKey = null;
         }
      }
   }

   public void shutdown() {
      if (log.isLoggable(Level.FINER)) log.finer(ME+": shutdown() of session");
      this.lock.lock();
      try {
         if (this.isShutdown)
            return;
         this.isShutdown = true;
      }
      finally {
         this.lock.release();
      }
      this.glob.unregisterMBean(this.mbeanHandle);
      removeExpiryTimer();

      I_Queue sessionQueue = this.sessionQueue;
      if (sessionQueue != null) {
         sessionQueue.shutdown();
         //this.sessionQueue = null; Not set to null to support avoid synchronize(this.sessionQueue)
      }

      if (this.msgErrorHandler != null)
         this.msgErrorHandler.shutdown();

      DispatchManager dispatchManager = this.dispatchManager;
      if (dispatchManager != null)
         dispatchManager.shutdown();

      this.subjectInfo = null;
      // this.securityCtx = null; We need it in finalize() getSecretSessionId()
      // this.connectQos = null;
      this.expiryTimer = null;
   }

   /**
    * @return null if no callback is configured, can change to null on reconfiguration
    */
   public final DispatchManager getDispatchManager() {
      return this.dispatchManager;
   }

   /**
    * @return never null but empty if no callback is configured
    */
   public final DispatchStatistic getDispatchStatistic() {
      if (this.statistic == null) {
         synchronized (this) {
            if (this.statistic == null) {
               DispatchManager dispatchManager = this.dispatchManager;
               if (dispatchManager != null)
                  this.statistic = dispatchManager.getDispatchStatistic();
               else
                  this.statistic = new DispatchStatistic();
            }
         }
      }
      return this.statistic;
   }

   /**
    * Call this to reactivate the session expiry to full value
    */
   public final void refreshSession() throws XmlBlasterException {
      if (connectQos.getSessionTimeout() > 0L) {
         synchronized (this.EXPIRY_TIMER_MONITOR) {
            Timeout expiryTimer = this.expiryTimer;
            if (expiryTimer != null) {
               this.timerKey = expiryTimer.addOrRefreshTimeoutListener(this, connectQos.getSessionTimeout(), null, this.timerKey);
            }
         }
      }
      else {
         removeExpiryTimer();
      }
   }

   /**
    * We are notified when this session expires.
    * @param userData You get bounced back your userData which you passed
    *                 with Timeout.addTimeoutListener()
    */
   public final void timeout(Object userData) {
      // lock could cause deadlock with topicHandler.lock()
      // it is not needed here as the disconnect from remote clients
      // also can come at any time and the core must be capable to handle this.
      //this.lock.lock();
      //try {
      synchronized (this.EXPIRY_TIMER_MONITOR) {
         this.timerKey = null;
      }
      log.warning(ME+": Session timeout for " + getLoginName() + " occurred, session '" + getSecretSessionId() + "' is expired, autologout");
      DisconnectQosServer qos = new DisconnectQosServer(glob);
      qos.deleteSubjectQueue(true);
      try {
         glob.getAuthenticate().disconnect(getAddressServer(), getSecretSessionId(), qos.toXml());
      } catch (XmlBlasterException e) {
         e.printStackTrace();
         log.severe(ME+": Internal problem with disconnect: " + e.toString());
      }
      //}
      //finally {
      //   this.lock.release();
      //}
   }

   /**
    * Is the given address the same as our?
    */
   public final boolean hasAddress(AddressBase addr) {
      if (addr == null) return false;
      I_Queue sessionQueue = getSessionQueue();
      if (sessionQueue == null) return false;
      CallbackAddress[] arr = ((CbQueueProperty)sessionQueue.getProperties()).getCallbackAddresses();
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
      if (log.isLoggable(Level.FINER)) log.call(ME, "Queing message");
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
      I_Queue sessionQueue = this.sessionQueue;
      if (!hasCallback() || sessionQueue == null) {
         if (log.isLoggable(Level.FINE)) log.fine(ME+": Queing PtP message without having configured a callback to the client, the client needs to reconnect with a valid callback address later");
         //if (!connectQos.getSessionName().isPubSessionIdUser()) { // client has specified its own publicSessionId (> 0)
         //   throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME, "No callback server is configured, can't callback client to send message " + entry.getKeyOid());
         //}
      }
      if (getPublicSessionId() < 0 && entry.isPersistent()) {
         entry.setPersistent(false);
         if (!this.transientWarn) {
            log.warning(ME+": Handling persistent messages in callback queue as transient as we have a login session with a negative public session id (we can't reconnect to same queue after restart)");
            this.transientWarn = true;
         }
      }
      sessionQueue.put(entry, I_Queue.USE_PUT_INTERCEPTOR);

      I_Checkpoint cp = glob.getCheckpointPlugin();
      if (cp != null) {
         cp.passingBy(I_Checkpoint.CP_UPDATE_QUEUE_ADD, entry.getMsgUnit(),
                  this.getSessionName(), null);
      }
   }

   public final ConnectQosServer getConnectQos() {
      return this.connectQos;
   }

   public final void updateConnectQos(ConnectQosServer newConnectQos) throws XmlBlasterException {
      boolean wantsCallbacks = (newConnectQos.getSessionCbQueueProperty().getCallbackAddresses().length > 0);

      // Remember persistent values:
      //newConnectQos.isFromPersistenceRecovery(this.connectQos.isFromPersistenceRecovery());
      newConnectQos.setPersistenceUniqueId(this.connectQos.getPersistenceUniqueId());
      if (this.connectQos.getData().isPersistent()) // otherwise persistent sessions could be made transient
         newConnectQos.getData().setPersistent(true); // and would never be deleted from persistence.
      this.connectQos = newConnectQos; // Replaces ConnectQosServer settings like bypassCredentialCheck

      // "__remoteProperties"
      if (newConnectQos.getData().getClientProperty(Constants.CLIENTPROPERTY_REMOTEPROPERTIES, false)) {
          mergeRemoteProperties(newConnectQos.getData().getClientProperties());
      }

      CbQueueProperty cbQueueProperty = newConnectQos.getSessionCbQueueProperty();
      I_Queue sessionQueue = this.sessionQueue;
      if (sessionQueue != null) sessionQueue.setProperties(cbQueueProperty);
      if (wantsCallbacks && hasCallback()) {
         DispatchManager dispatchManager = this.dispatchManager;
         if (dispatchManager != null) {
            dispatchManager.updateProperty(cbQueueProperty.getCallbackAddresses());
            log.info(ME+": Successfully reconfigured callback address with new settings, other reconfigurations are not yet implemented");
            dispatchManager.notifyAboutNewEntry();
         }
      }
      else if (wantsCallbacks && !hasCallback()) {
         log.info(ME+": Successfully reconfigured and created dispatch manager with given callback address");
         DispatchManager tmpDispatchManager = new DispatchManager(glob, this.msgErrorHandler,
                              this.securityCtx, this.sessionQueue, (I_ConnectionStatusListener)null,
                              newConnectQos.getSessionCbQueueProperty().getCallbackAddresses(), this.sessionName);
         DispatchManager dispatchManager = this.dispatchManager;
         if (dispatchManager != null)
            tmpDispatchManager.setDispatcherActive(dispatchManager.isDispatcherActive());
         this.dispatchManager = tmpDispatchManager;
      }
      else if (!wantsCallbacks && hasCallback()) {
         DispatchManager dispatchManager = this.dispatchManager;
         if (dispatchManager != null) {
            dispatchManager.shutdown();
            log.info(ME+": Successfully shutdown dispatch manager as no callback address is configured");
         }
         this.dispatchManager = null;
      }
      else if (!wantsCallbacks && !hasCallback()) {
         if (log.isLoggable(Level.FINE)) log.fine(ME+": No callback exists and no callback is desired");
         // nothing to do
      }
   }

   /**
    * Access the unique login name of a client.
    * <br />
    * @return loginName
    */
   public final String getLoginName() {
      SubjectInfo subjectInfo = this.subjectInfo;
      return (subjectInfo==null)?"--":subjectInfo.getLoginName();
   }

   /**
    * Accessing the SubjectInfo object
    * <p />
    * @return SubjectInfo
    */
   public final SubjectInfo getSubjectInfo() {
      return this.subjectInfo;
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
    * Enforced by I_StorageSizeListener
    */
   public void changed(I_Storage storage, long numEntries, long numBytes, boolean isShutdown) {
      if (isShutdown) return;
      SubjectInfo subjectInfo = getSubjectInfo();
      boolean hasSubjectEntries = (subjectInfo == null) ? false : subjectInfo.getSubjectQueue().getNumOfEntries() > 0;
      if (lastNumEntries != numEntries) {
         I_Queue sessionQueue = this.sessionQueue;
         long max = (sessionQueue == null) ? 0 : sessionQueue.getMaxNumOfEntries();
         if (hasSubjectEntries && numEntries < max && lastNumEntries > numEntries) {
            if (log.isLoggable(Level.FINE)) log.fine(ME+": SessionQueue has emptied from " + lastNumEntries +
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
      return toXml((String)null, (Properties)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of SessionInfo as a XML ASCII string
    */
   public final String toXml(String extraOffset, Properties props) {
      StringBuffer sb = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<SessionInfo id='").append(getId());

      Timeout expiryTimer = this.expiryTimer;
      long timeToLife = (expiryTimer != null) ? expiryTimer.spanToTimeout(timerKey) : 0;
      sb.append("' timeout='").append(timeToLife).append("'>");

      // Avoid dump of password
      if (props == null) props = new Properties();
      props.put(Constants.TOXML_NOSECURITY, ""+true);
      sb.append(this.connectQos.toXml(extraOffset+Constants.INDENT, props));

      DispatchManager dispatchManager = this.dispatchManager;
      if (dispatchManager != null) {
         sb.append(dispatchManager.toXml(extraOffset+Constants.INDENT));
      }
      else {
         sb.append(offset).append(Constants.INDENT).append("<DispatchManager id='NULL'/>");
      }

      I_Queue sessionQueue = this.sessionQueue;
      if (sessionQueue != null) {
         sb.append(sessionQueue.toXml(extraOffset+Constants.INDENT));
      }
      sb.append(offset).append("</SessionInfo>");

      return sb.toString();
   }

   //=========== Enforced by I_AdminSession ================
   public String getQos() {
      return (this.connectQos == null) ? "" : this.connectQos.toXml();
   }

   public final boolean isCallbackConfigured() {
      return hasCallback();
   }

   public final long getUptime() {
      return (System.currentTimeMillis() - this.startupTime)/1000L;
   }

   public final String getConnectionState() {
      if (this.dispatchManager != null) {
         return this.dispatchManager.getDispatchConnectionsHandler().getState().toString();
      }
      else {
         return "UNDEF";
      }

   }

   public final String getLoginDate() {
      long ll = this.startupTime;
      java.sql.Timestamp tt = new java.sql.Timestamp(ll);
      return tt.toString();
   }

   public final String getSessionTimeoutExpireDate() {
      long timeToLife = this.expiryTimer.spanToTimeout(timerKey);
      if (timeToLife == -1) {
         return "unlimited";
      }
      long ll = System.currentTimeMillis() + timeToLife;
      java.sql.Timestamp tt = new java.sql.Timestamp(ll);
      return tt.toString();
   }

   // JMX
   public final String getAliveSinceDate() {
      if (this.dispatchManager == null) return "";
      long ll = this.dispatchManager.getAliveSinceTime();
      if (ll == 0) return "";
      java.sql.Timestamp tt = new java.sql.Timestamp(ll);
      return tt.toString();
   }

   // JMX
   public final String getPollingSinceDate() {
      if (this.dispatchManager == null) return "";
      long ll = this.dispatchManager.getPollingSinceTime();
      if (ll == 0) return "";
      java.sql.Timestamp tt = new java.sql.Timestamp(ll);
      return tt.toString();
   }

   public final String getLastCallbackException() {
      return getDispatchStatistic().getLastDeliveryException();
   }

   public final void clearLastCallbackException() {
      getDispatchStatistic().setLastDeliveryException("");
   }

   public final int getNumCallbackExceptions() {
      return getDispatchStatistic().getNumDeliveryExceptions();
   }

   public final long getNumPublish() {
      return getDispatchStatistic().getNumPublish();
   }

   public final long getNumSubscribe() {
      return getDispatchStatistic().getNumSubscribe();
   }

   public final long getNumUnSubscribe() {
      return getDispatchStatistic().getNumUnSubscribe();
   }

   public final long getNumGet() {
      return getDispatchStatistic().getNumGet();
   }

   public final long getNumErase() {
      return getDispatchStatistic().getNumErase();
   }

   public final long getNumUpdateOneway() {
      return getDispatchStatistic().getNumUpdateOneway();
   }

   public final long getNumUpdate() {
      return getDispatchStatistic().getNumUpdate();
   }

   public final long getCbQueueNumMsgs() {
      if (this.sessionQueue == null) return 0L;
      return this.sessionQueue.getNumOfEntries();
   }

   public final long getCbQueueBytes() {
      if (this.sessionQueue == null) return 0L;
      return this.sessionQueue.getNumOfBytes();
   }

   public final long getCbQueueBytesCache() {
      I_Queue sq = this.sessionQueue;
      if (sq == null) return 0L;
      if (sq instanceof CacheQueueInterceptorPlugin) {
         CacheQueueInterceptorPlugin cq = (CacheQueueInterceptorPlugin)sq;
         I_Queue tq = cq.getTransientQueue();
         if (tq != null) return tq.getNumOfBytes();
         return 0L;
      }
      return -1L;
   }

   public final long getCbQueueNumMsgsCache() {
      I_Queue sq = this.sessionQueue;
      if (sq == null) return 0L;
      if (sq instanceof CacheQueueInterceptorPlugin) {
         CacheQueueInterceptorPlugin cq = (CacheQueueInterceptorPlugin)sq;
         I_Queue tq = cq.getTransientQueue();
         if (tq != null) return tq.getNumOfEntries();
         return 0L;
      }
      return -1L;
   }

   public final long getCbQueueMaxMsgs() {
      if (this.sessionQueue == null) return 0L;
      return this.sessionQueue.getMaxNumOfEntries();
   }

   public final long getCbQueueMaxMsgsCache() {
      I_Queue sq = this.sessionQueue;
      if (sq == null) return 0L;
      if (sq instanceof CacheQueueInterceptorPlugin) {
          CacheQueueInterceptorPlugin cq = (CacheQueueInterceptorPlugin)sq;
          I_Queue tq = cq.getTransientQueue();
          if (tq != null) return tq.getMaxNumOfEntries();
          return 0L;
      }
      return -1L;
   }

   public String pingClientCallbackServer() {
      DispatchManager dispatchManager = this.dispatchManager;
      if (dispatchManager != null) {
         boolean isSend = dispatchManager.pingCallbackServer(true);
         if (isSend)
            return "Ping done in " + getPingRoundTripDelay() + " millis, current state is "
                  + dispatchManager.getDispatchConnectionsHandler().getState().toString();
         else
            return "Ping is not possible, no callback available";
      }
      return "No ping because of no callback";
   }

   public long getPingRoundTripDelay() {
      return getDispatchStatistic().getPingRoundTripDelay();
   }

   public long getRoundTripDelay() {
      return getDispatchStatistic().getRoundTripDelay();
   }

   public final String[] getSubscribedTopics() {
      SubscriptionInfo[] subs = glob.getRequestBroker().getClientSubscriptions().getSubscriptions(this);
      String[] arr = new String[subs.length];
      for (int i=0; i<arr.length; i++) {
         arr[i] = subs[i].getKeyOid();
      }
      return arr;
   }

   public final String subscribe(String url, String qos) throws XmlBlasterException {
      if (url == null) {
         return "Please pass a valid topic oid";
      }

      log.info(ME+": Administrative subscribe() of '" + url + "' for client '" + getId() + "' qos='" + qos + "'");
      SubscribeKey uk = new SubscribeKey(glob, url);
      SubscribeQos uq;
      if (qos == null || qos.length() == 0 || qos.equalsIgnoreCase("String")) {
         uq = new SubscribeQos(glob);
      }
      else {
         uq = new SubscribeQos(glob, glob.getQueryQosFactory().readObject(qos));
      }
      SubscribeQosServer uqs = new SubscribeQosServer(glob, uq.getData());

      String ret = glob.getRequestBroker().subscribe(this, uk.getData(), uqs);

      SubscribeReturnQos tmp = new SubscribeReturnQos(glob, ret);
      ret = "Subscribe '" + tmp.getSubscriptionId() + "' state is " + tmp.getState();
      if (tmp.getStateInfo() != null)
         ret += " " + tmp.getStateInfo();

      if (ret.length() == 0) {
         ret = "Unsubscribe of '" + url + "' for client '" + getId() + "' did NOT match any subscription";
      }

      return ret;
   }

   public String[] unSubscribeByIndex(int index, String qos) throws XmlBlasterException {
      SubscriptionInfo[] subs = glob.getRequestBroker().getClientSubscriptions().getSubscriptions(this);
      if (subs.length < 1)
         return new String[] { "Currently no topics are subscribed" };

      if (index < 0 || index >= subs.length) {
         return new String[] { "Please choose an index between 0 and " + (subs.length-1) + " (inclusiv)" };
      }

      return unSubscribe(subs[index].getSubscriptionId(), qos);
   }

   public final String[] unSubscribe(String url, String qos) throws XmlBlasterException {
      if (url == null)
         return new String[] { "Please pass a valid topic oid" };

      log.info(ME+": Administrative unSubscribe() of '" + url + "' for client '" + getId() + "'");
      UnSubscribeKey uk = new UnSubscribeKey(glob, url);

      UnSubscribeQos uq;
      if (qos == null || qos.length() == 0 || qos.equalsIgnoreCase("String"))
         uq = new UnSubscribeQos(glob);
      else
         uq = new UnSubscribeQos(glob, glob.getQueryQosFactory().readObject(qos));
      UnSubscribeQosServer uqs = new UnSubscribeQosServer(glob, uq.getData());

      String[] ret = glob.getRequestBroker().unSubscribe(this, uk.getData(), uqs);

      if (ret.length == 0)
         return new String[] { "Unsubscribe of '" + url + "' for client '" + getId() + "' did NOT match any subscription" };

      for (int i=0; i<ret.length; i++) {
         UnSubscribeReturnQos tmp = new UnSubscribeReturnQos(glob, ret[i]);
         ret[i] = "Unsubscribe '" + tmp.getSubscriptionId() + "' state is " + tmp.getState();
         if (tmp.getStateInfo() != null)
            ret[i] += " " + tmp.getStateInfo();
      }

      return ret;
   }

   public final String[] getSubscriptions() throws XmlBlasterException {
      SubscriptionInfo[] subs = glob.getRequestBroker().getClientSubscriptions().getSubscriptions(this);
      String[] arr = new String[subs.length];
      for (int i=0; i<arr.length; i++) {
         arr[i] = subs[i].getSubscriptionId();
      }
      return arr;
   }

   public final String getSubscriptionDump() throws XmlBlasterException {
      SubscriptionInfo[] subs = glob.getRequestBroker().getClientSubscriptions().getSubscriptions(this);
      if (subs.length < 1)
         return "";
      StringBuffer sb = new StringBuffer(subs.length * 300);
      sb.append("<SessionInfo id='").append(getId()).append("'>");
      for (int i=0; i<subs.length; i++) {
         sb.append(subs[i].toXml(" "));
      }
      sb.append("</SessionInfo>");
      return sb.toString();
   }

   public final String killSession() throws XmlBlasterException {
      glob.getAuthenticate().disconnect(getAddressServer(), securityCtx.getSecretSessionId(), "<qos/>");
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
      if (this.dispatchManager != null) {
         this.dispatchManager.setDispatcherActive(dispatcherActive);
      }
   }

   public boolean getDispatcherActive() {
      if (this.dispatchManager != null) {
         return this.dispatchManager.isDispatcherActive();
      }
      return false;
   }

   public String[] peekCallbackMessages(int numOfEntries) throws XmlBlasterException {
      return this.glob.peekMessages(this.sessionQueue, numOfEntries, "callback");
   }

   /**
    * Peek messages from callback queue and dump them to a file, they are not removed.
    * @param numOfEntries The number of messages to peek, taken from the front
    * @param path The path to dump the messages to, it is automatically created if missing.
    * @return The file names of the dumped messages
    */
   public String[] peekCallbackMessagesToFile(int numOfEntries, String path) throws Exception {
      try {
         return this.glob.peekQueueMessagesToFile(this.sessionQueue, numOfEntries, path, "callback");
      }
      catch (XmlBlasterException e) {
         throw new Exception(e.toString());
      }
   }

   public long clearCallbackQueue() {
      I_Queue sessionQueue = this.sessionQueue;
      return (sessionQueue==null) ? 0L : sessionQueue.clear();
   }

   public long removeFromCallbackQueue(long numOfEntries) throws XmlBlasterException {
      I_Queue sessionQueue = this.sessionQueue;
      return (sessionQueue==null) ? 0L : sessionQueue.remove(numOfEntries, -1);
   }

   public MsgUnit[] getCallbackQueueEntries(String query) throws XmlBlasterException {
      if (this.queueQueryPlugin == null) {
         synchronized (this) {
            if (this.queueQueryPlugin == null) {
               this.queueQueryPlugin = new QueueQueryPlugin(this.glob);
            }
         }
      }
      return this.queueQueryPlugin.query(this.sessionQueue, query);
   }

   /** JMX Enforced by ConnectQosDataMBean interface. */
   public final void setSessionTimeout(long timeout) {
      getConnectQos().setSessionTimeout(timeout);
      try {
         refreshSession();
      } catch (XmlBlasterException e) {
         e.printStackTrace();
      }
   }


   /** JMX */
   public java.lang.String usage() {
      return ServerScope.getJmxUsageLinkInfo(this.getClass().getName(), null);
   }
   /** JMX */
   public java.lang.String getUsageUrl() {
      return ServerScope.getJavadocUrl(this.getClass().getName(), null);
   }
   /* JMX dummy to have a copy/paste functionality in jconsole */
   public void setUsageUrl(java.lang.String url) {}

   /**
    * @return Returns the remoteProperties or null
    */
   public ClientPropertiesInfo getRemoteProperties() {
      return this.remoteProperties;
   }

   /**
    * @return never null
    */
   public ClientProperty[] getRemotePropertyArr() {
      ClientPropertiesInfo tmp = this.remoteProperties;
      if (tmp == null) return new ClientProperty[0];
      return tmp.getClientPropertyArr();
   }

   /**
    * Set properties send by our client.
    * @param remoteProperties The remoteProperties to set, pass null to reset.
    * The key is of type String and the value of type ClientProperty
    */
   public synchronized void setRemoteProperties(Map map) {
      if (map == null)
         this.remoteProperties = null;
      else
         this.remoteProperties = new ClientPropertiesInfo(map);
   }

   /**
    * Clear remote properties.
    * @param prefix if not null only keys starting with are removed
    * @return number of removed entries
    */
   public synchronized int clearRemoteProperties(String prefix) {
      if (prefix == null) {
         int size = 0;
         if (this.remoteProperties != null)
            size = this.remoteProperties.getClientPropertyMap().size();
         this.remoteProperties = null;
         return size;
      }
      
      ClientPropertiesInfo info = this.remoteProperties;
      if (info == null || prefix == null) return 0;
      ClientProperty[] arr = info.getClientPropertyArr();
      int count = 0;
      for (int i=0; i<arr.length; i++) {
         if (arr[i].getName().startsWith(prefix)) {
            info.getClientPropertyMap().remove(arr[i].getName());
            count++;
         }
      }
      return count;
   }

   /**
    * Update properties send by our client.
    * @param remoteProperties The remoteProperties to set,
    * if a property exists its value is overwritten, passing null does nothing
    * The key is of type String and the value of type ClientProperty
    */
   public synchronized void mergeRemoteProperties(Map map) {
      if (map == null || map.size() == 0) return;
      if (this.remoteProperties == null) {
          this.remoteProperties = new ClientPropertiesInfo(new HashMap());
          /*// Changed 2007-06-29 marcel: we now take a clone
         this.remoteProperties = new ClientPropertiesInfo(map);
         // remove, is only a hint:
         this.remoteProperties.put(Constants.CLIENTPROPERTY_REMOTEPROPERTIES, (ClientProperty)null);
         return;
         */
      }
      Iterator it = map.keySet().iterator();
      while (it.hasNext()) {
         String key = (String)it.next();
         if (Constants.CLIENTPROPERTY_REMOTEPROPERTIES.equals(key))
             continue; // Remove, is only a flag
         if (Constants.CLIENTPROPERTY_UTC.equals(key)) {
            try {
                ClientProperty cpClientUtc = (ClientProperty)map.get(key);
                if (cpClientUtc != null) {
                   String timeOffset = IsoDateParser.getDifferenceToNow(cpClientUtc.getStringValue());
                   this.remoteProperties.put("__timeOffset", timeOffset);
                }
             }
             catch (Throwable e) {
                e.printStackTrace();
             }
             continue; // Remove, we only want the offset time between client and server
         }
         Object value = map.get(key);
         this.remoteProperties.put(key, (ClientProperty)value);
      }
   }

   /**
    * Add a remote property.
    * Usually this is done by a publish of a client, but for
    * testing reasons we can to it here manually.
    * If the key exists, its value is overwritten
    * @param key The unique key (no multimap)
    * @param value The value, it is assumed to be of type "String"
    * @return The old ClientProperty if existed, else null
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.events.html">The admin.events requirement</a>
    */
   public synchronized ClientProperty addRemoteProperty(String key, String value) {
      if (this.remoteProperties == null)
         this.remoteProperties = new ClientPropertiesInfo(null);
      ClientProperty old = (ClientProperty)this.remoteProperties.getClientPropertyMap().get(key);
      this.remoteProperties.put(key, value);
      return old;
   }

   public boolean isStalled() {
      return getDispatchStatistic().isStalled();
   }

   /**
    * Can be called when client connection is lost (NOT the callback connection).
    * Currently only detected by the SOCKET protocol plugin.
    * Others can only detect lost clients with their callback protocol pings
    */
   public void lostClientConnection() {
      if (log.isLoggable(Level.FINE)) log.fine(ME+": Protocol layer is notifying me about a lost connection");
      DispatchManager dispatchManager = this.dispatchManager;
      if (dispatchManager != null)
         dispatchManager.lostClientConnection();
   }

   /**
    * If the connection failed the reason is stored here, like this
    * cleanup code knows what happened.
    * @return the transportConnectFail
    */
   public XmlBlasterException getTransportConnectFail() {
      return this.transportConnectFail;
   }

   /**
    * @param transportConnectFail the transportConnectFail to set
    */
   public void setTransportConnectFail(XmlBlasterException transportConnectFail) {
      this.transportConnectFail = transportConnectFail;
   }

   /**
    * Can be optionally used by the current authorization plugin.
    */
   public Object getAuthorizationCache() {
      return authorizationCache;
   }

   public void setAuthorizationCache(Object authorizationCache) {
      this.authorizationCache = authorizationCache;
   }
   
   public boolean isBlockClientSessionLogin() {
      return blockClientSessionLogin;
   }

   public String setBlockClientSessionLogin(boolean blockClient) {
      if (this.blockClientSessionLogin == blockClient)
         return "Session " + getId() + " is alread in state blocking=" + blockClient;
      this.blockClientSessionLogin = blockClient;
      String text = blockClient ? "The ALIVE client remains logged in, reconnects are blocked" : "Blocking of "
            + getId() + " is switched off";
      log.info(text);
      return text;
   }
   
   public String disconnectClientKeepSession() {
      if (this.dispatchManager.isPolling()) {
         String text = "Client " + getId() + " is in POLLING state already";
         log.info(text);
         return text;
      }
      
      // try {
         DispatchConnection dc = this.dispatchManager.getDispatchConnectionsHandler().getCurrentDispatchConnection();
         dc.resetConnection();
         // dc.shutdown();
      // } catch (XmlBlasterException e) {
      // log.warning("disconnectClientKeepSession for " + getId() + " failed: "
      // + e.getMessage());
      // return e.toString();
      // }

      // this.dispatchManager.shutdown();
      String text = "Client " + getId() + " is disconnected";
      log.info(text);
      return text;
   }
}
