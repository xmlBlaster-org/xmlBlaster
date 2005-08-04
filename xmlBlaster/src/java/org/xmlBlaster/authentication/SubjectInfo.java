/*------------------------------------------------------------------------------
Name:      SubjectInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;


import org.jutils.log.LogChannel;

import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.TopicHandler;
import org.xmlBlaster.authentication.plugins.I_Subject;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.qos.SessionQos;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.cluster.NodeId;
import org.xmlBlaster.engine.cluster.ClusterNode;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.dispatch.DispatchStatistic;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.admin.extern.JmxMBeanHandle;
import org.xmlBlaster.engine.msgstore.I_Map;
import org.xmlBlaster.engine.queuemsg.MsgQueueUpdateEntry;
import org.xmlBlaster.engine.queuemsg.ReferenceEntry;
import org.xmlBlaster.engine.admin.I_AdminSession;

import org.xmlBlaster.util.error.I_MsgErrorHandler;
import org.xmlBlaster.util.error.MsgErrorInfo;
import org.xmlBlaster.engine.MsgErrorHandler;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;

//import EDU.oswego.cs.dl.util.concurrent.ReentrantLock;
import org.xmlBlaster.util.ReentrantLock;

import javax.management.NotificationBroadcasterSupport;
import javax.management.AttributeChangeNotification;
import javax.management.MBeanNotificationInfo;


/**
 * SubjectInfo stores all known data about a client.
 * <p>
 * It also contains a message queue, where messages are stored
 * until they are delivered at the next login of this client.
 * </p>
 * <p>
 * There are three states for SubjectInfo namely UNDEF, ALIVE, DEAD.
 * A transition from UNDEF directly to DEAD is not supported.
 * Transitions from ALIVE or DEAD to UNDEF are not possible.
 * </p>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public final class SubjectInfo extends NotificationBroadcasterSupport /* implements I_AdminSubject, SubjectInfoMBean -> is delegated to SubjectInfoProtector */
{
   private String ME = "SubjectInfo";
   private final Global glob;
   private final LogChannel log;
   private final ContextNode contextNode;

   private final Authenticate authenticate;

   /** The cluster wide unique identifier of the subject e.g. "/node/heron/client/joe" */
   private SessionName subjectName;
   /** The partner class from the security framework */
   private I_Subject securityCtx = null;
   /**
    * All sessions of this subject are stored in this map.
    * The absoluteSessionName == sessionInfo.getId() is the key,
    * the SessionInfo object the value
    */
   private Map sessionMap = new HashMap();
   private SessionInfo[] sessionArrCache;
   public CallbackAddress[] callbackAddressCache = null;

   private MsgErrorHandler msgErrorHandler;

   private final DispatchStatistic dispatchStatistic;

   private final SubjectInfoProtector subjectInfoProtector;

   private NodeId nodeId = null;
   private boolean determineNodeId = true;

   // Enforced by I_AdminSubject
   /** Incarnation time of this object instance in millis */
   private long startupTime;
   private int maxSessions;
   
   /** State during and after construction */
   public final int UNDEF = -1;
   /** State after calling toAlive() */
   public final int ALIVE = 0;
   /** State after calling shutdown() */
   public final int DEAD = 1;
   private int state = UNDEF;

   /** JMX calls */
   private long sequenceNumber = 1; 

   private ReentrantLock lock = new ReentrantLock();


   /**
    * All MsgUnit which can't be delivered to the client (if he is not logged in)
    * are queued here and are delivered when the client comes on line.
    * <p>
    * Node objects = MsgQueueEntry
    */
   private I_Queue subjectQueue;


   /** Statistics */
   private static long instanceCounter = 0L;
   private long instanceId = 0L;

   /** My JMX registration */
   private JmxMBeanHandle mbeanHandle;

   /**
    * <p />
    * @param subjectName  The unique loginName
    * @param securityCtx  The security context of this subject
    * @param prop         The property from the subject queue, usually from connectQos.getSubjectQueueProperty()
    */
   public SubjectInfo(Global glob, Authenticate authenticate, SessionName subjectName) //, I_Subject securityCtx, CbQueueProperty prop)
          throws XmlBlasterException {
      synchronized (SubjectInfo.class) {
         instanceId = instanceCounter;
         instanceCounter++;
      }
      this.glob = glob;
      this.log = this.glob.getLog("auth");
      this.authenticate = authenticate;
      this.subjectInfoProtector = new SubjectInfoProtector(this);

      String prae = glob.getLogPrefix();
      this.subjectName = subjectName; //new SessionName(glob, glob.getNodeId(), loginName);
      if (this.subjectName.isSession()) {
         log.error(ME, "Didn't expect a session name for a subject: " + this.subjectName.toXml());
         Thread.dumpStack();
      }
      this.contextNode = new ContextNode(this.glob, ContextNode.SUBJECT_MARKER_TAG, 
                                       this.subjectName.getLoginName(), this.glob.getContextNode());

      this.ME = "SubjectInfo-" + instanceCounter + "-" + this.subjectName.getAbsoluteName();
      this.dispatchStatistic = new DispatchStatistic();

      // JMX register "client/joe"
      this.mbeanHandle = this.glob.registerMBean(this.contextNode, this.subjectInfoProtector);

      if (log.TRACE) log.trace(ME, "Created new SubjectInfo");
   }

   /**
    * The unique name of this subject instance. 
    * @return Never null, for example "/xmlBlaster/node/heron/client/joe"
    */
   public final ContextNode getContextNode() {
      return this.contextNode;
   }

   /**
    * if state==UNDEF we block until we are ALIVE (or DEAD)
    * @exception If we are DEAD or on one minute timeout, subjectInfo is never locked in such a case
    */
   public void waitUntilAlive(boolean returnLocked) throws XmlBlasterException {
      if (this.state == ALIVE) {
         if (returnLocked) this.lock.lock();
         return;
      }
      if (this.state == DEAD)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME+".waitUntilAlive()", "Did not expect state DEAD, please try again.");

      if (log.TRACE) log.trace(ME, "waitUntilAlive() is going to wait max. one minute");
      //log.error(ME, "DEBUG ONLY: waitUntilAlive() is going to wait max. one minute");
      long msecs = 1000 * 60;
      while (true) {
         synchronized (this) {
            try {
               //log.error(ME, "DEBUG ONLY: Entering wait()");
               this.wait(msecs);
               break;
            }
            catch (InterruptedException e) {
               log.error(ME, "waitUntilAlive() Ignoring unexpected exception: " + e.toString());
            }
         }
      }

      if (returnLocked) this.lock.lock();
      if (this.state != ALIVE) {
         if (returnLocked) this.lock.release();
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME+".waitUntilAlive()", "ALIVE not reached, state=" + this.state);
      }

      //log.error(ME, "DEBUG ONLY: Leaving waitUntilAlive()");
      return;
   }

   /**
    * Access the synchronization object of this SubjectInfo instance. 
    */
   public ReentrantLock getLock() {
      return this.lock;
   }

   SubjectInfoProtector getSubjectInfoProtector() {
      return this.subjectInfoProtector;
   }

   /**
    * Initialize SubjectInfo
    * @param securityCtx Can be null for PtP message with implicit SubjectInfo creation
    * @param prop The property to configure the PtP message queue
    */
   public void toAlive(I_Subject securityCtx, CbQueueProperty prop) throws XmlBlasterException {
      if (isAlive()) {
         return;
      }

      this.lock.lock();
      try {

         //if (isDead()) {
         //   log.error(ME, "State transition from DEAD -> ALIVE is not implemented");
         //   throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "State transition from DEAD -> ALIVE is not implemented");
         //}
         if (securityCtx != null) {
            this.securityCtx = securityCtx;
         }

         this.startupTime = System.currentTimeMillis();

         this.maxSessions = glob.getProperty().get("session.maxSessions", SessionQos.DEFAULT_maxSessions);
         if (glob.getId() != null)
            this.maxSessions = glob.getProperty().get("session.maxSessions["+glob.getId()+"]", this.maxSessions);

         this.subjectQueue = createSubjectQueue(prop);

         this.state = ALIVE;

         synchronized (this) {
            this.notifyAll();    // notify waitUntilAlive()
         }

         // done externally to avoid dead locks
         //this.authenticate.addLoginName(this); // register myself -> enters synchronized(this.loginNameSubjectInfoMap)
         try { // Keep this code in the sync block, in case a disconnect() arrives immediately
            glob.getRequestBroker().updateInternalUserList();
         }
         catch (XmlBlasterException e) {
            log.error(ME, "Publishing internal user list failed: " + e.getMessage());
         }

         if (log.TRACE) log.trace(ME, "Transition from UNDEF to ALIVE done");
      }
      finally {
         this.lock.release();
      }
   }

   private I_Queue createSubjectQueue(CbQueueProperty prop) throws XmlBlasterException {
      if (prop == null) prop = new CbQueueProperty(glob, Constants.RELATING_SUBJECT, glob.getId());
      String type = prop.getType();
      String version = prop.getVersion();
      I_Queue queue = glob.getQueuePluginManager().getPlugin(type, version,
                           new StorageId(Constants.RELATING_SUBJECT, this.subjectName.getAbsoluteName()), prop);
      queue.setNotifiedAboutAddOrRemove(true); // Entries are notified to support reference counting
      return queue;
   }

   /**
    * The shutdown is synchronized and checks if there is no need for this subject anymore. 
    * <p>
    * clearQueue==false&&forceIfEntries==true: We shutdown and preserve existing PtP messages
    * </p>
    * @param clearQueue Shall the message queue of the client be destroyed as well on last session logout?
    * @param forceIfEntries Shutdown even if there are messages in the queue
    */
   public void shutdown(boolean clearQueue, boolean forceIfEntries) {
      if (log.CALL) log.call(ME, "shutdown(clearQueue=" + clearQueue + ", forceIfEntries=" + forceIfEntries + ") of subject " + getLoginName());

      this.lock.lock();
      try {

         if (!this.isAlive()) {
            if (log.TRACE) log.trace(ME, "Ignoring shutdown request as we are in state " + getStateStr());
            return;
         }

         if (isLoggedIn()) {
            if (log.TRACE) log.trace(ME, "Ignoring shutdown request as there are still login sessions");
            return;
         }

         if (!forceIfEntries && !clearQueue && getSubjectQueue().getNumOfEntries() > 0) {
            if (log.TRACE) log.trace(ME, "Ignoring shutdown request as there are still messages in the subject queue");
            return;
         }

         this.glob.unregisterMBean(this.mbeanHandle);

         if (getSubjectQueue().getNumOfEntries() < 1)
            log.info(ME, "Destroying SubjectInfo. Nobody is logged in and no queue entries available");
         else {
            if (clearQueue)
               log.warn(ME, "Destroying SubjectInfo. Lost " + getSubjectQueue().getNumOfEntries() + " messages in the subject queue as clearQueue is set to true");
            else
               log.warn(ME, "Destroying SubjectInfo. The subject queue still contains " + getSubjectQueue().getNumOfEntries() + " messages, " +
                            getSubjectQueue().getNumOfPersistentEntries() + " persistent messages remain on disk, the transients are lost");
         }

         this.authenticate.removeLoginName(this);  // deregister

         this.state = DEAD;

         if (clearQueue)
            this.subjectQueue.clear();

         if (this.subjectQueue != null) {
            this.subjectQueue.shutdown();
         }

         if (getSessions().length > 0) {
            log.warn(ME, "shutdown() of subject " + getLoginName() + " has still " + getSessions().length + " sessions - memory leak?");
         }
         synchronized (this.sessionMap) {
            this.sessionArrCache = null;
            this.sessionMap.clear();
            this.callbackAddressCache = null;
         }

         if (this.msgErrorHandler != null)
            this.msgErrorHandler.shutdown();

         synchronized (this) {
            this.notifyAll();    // notify waitUntilAlive()
         }
         // Not possible to allow toAlive()
         //this.securityCtx = null;
      }
      finally {
         this.lock.release();
      }
   }

   /**
    * Shutdown my queue
    */
   public void finalize() {
      //log.error(ME, "DEBUG ONLY: finalize - garbage collected " + getLoginName());
      if (log.TRACE) log.trace(ME, "finalize - garbage collected " + getLoginName());
      //boolean force = true;
      //this.subjectQueue.shutdown();
   }

   /**
    * Find a session by its pubSessionId or return null if not found
    */
   public SessionInfo getSessionInfo(SessionName sessionName) {
      SessionInfo[] sessions = getSessions();
      for (int ii=0; ii<sessions.length; ii++) {
         if (sessions[ii].getSessionName().equalsRelative(sessionName)) {
            return sessions[ii];
         }
      }
      return null;
   }

   /**
    * @return not null if client is a cluster node, else null
    */
   public final NodeId getNodeId() throws XmlBlasterException {
      if (determineNodeId) {

         determineNodeId = false;

         if (this.subjectName.getLoginName().startsWith(org.xmlBlaster.engine.RequestBroker.internalLoginNamePrefix))
            return null; // don't check for internal logins

         if (glob.useCluster()) {
            // Is the client a well known, configured cluster node?
            ClusterNode clusterNode = glob.getClusterManager().getClusterNode(this.subjectName.getLoginName()); // is null if not found
            if (clusterNode != null) {
               nodeId = clusterNode.getNodeId();
            }
            else {
               // Does the client send a tag which marks it as a cluster node?

               SessionInfo ses = getFirstSession();
               if (ses != null) {
                  if (ses.getConnectQos().isClusterNode())
                     nodeId = new NodeId(this.subjectName.getLoginName());
               }
            }
         }
         /*
         if (!glob.useCluster() && connectQos.isCluster()) {
            log.error(ME, "Internal mismatch: Clustering is switched off, but client '" + loginName + "' claims to be a cluster node");
         }
         */
      }
      return nodeId;
   }

   /**
    * @return true if this client is an xmlBlaster cluster node
    */
   public boolean isCluster() throws XmlBlasterException {
      return getNodeId() != null;
   }

   /**
    * Allows to overwrite queue property. 
    * <p>
    * It will be only written if prop!= null. 
    * </p>
    * @param prop CbQueueProperty transports subject queue property as well
    *        TODO: we should have a clear named SubjectQueueProperty
    */
   public final void setSubjectQueueProperty(CbQueueProperty prop) throws XmlBlasterException {
      CbQueueProperty origProp = (CbQueueProperty)this.subjectQueue.getProperties();
      if (origProp == null) {
         log.error(ME+".setSubjectQueueProperty()", "Existing subject queue properties are null");
         return;
      }

      if (prop == null) prop = new CbQueueProperty(glob, Constants.RELATING_SUBJECT, glob.getId());

      this.lock.lock();
      try {
         if (prop.getTypeVersion().equals(origProp.getTypeVersion())) {
            this.subjectQueue.setProperties(prop);
            return;
         }

         // TODO: Extend CACHE queue to handle reconfigurations hidden so we don't need to do anything here

         if (!this.subjectQueue.isTransient()) {
            I_Queue newQueue = createSubjectQueue(prop);
            if (newQueue.isTransient()) {
               log.info(ME, "Reconfiguring subject queue: Copying " + this.subjectQueue.getNumOfEntries() + " entries from old " + origProp.getType() + " queue to " + prop.getTypeVersion() + " queue");
               java.util.ArrayList list = null;
               int lastSize = -99;
               while (this.subjectQueue.getNumOfEntries() > 0) {

                  try {
                     list = this.subjectQueue.peek(-1, -1);
                     if (this.subjectQueue.getNumOfEntries() == lastSize) {
                        log.error(ME, "PANIC: " + this.subjectQueue.getNumOfEntries() + " entries from old queue " + this.subjectQueue.getStorageId() + " can't be copied, giving up!");
                        break;
                     }
                     lastSize = (int)this.subjectQueue.getNumOfEntries();
                  }
                  catch (XmlBlasterException e) {
                     log.error(ME, "PANIC: Can't copy from subject queue '" + this.subjectQueue.getStorageId() + "' with " + this.subjectQueue.getNumOfEntries() + " entries: " + e.getMessage());
                     e.printStackTrace();
                     continue;
                  }

                  MsgQueueEntry[] queueEntries = (MsgQueueEntry[])list.toArray(new MsgQueueEntry[list.size()]);
                  // On error we send them as dead letters, as we don't know what to do with them in our holdback queue
                  try {
                     newQueue.put(queueEntries, false);
                  }
                  catch (XmlBlasterException e) {
                     log.warn(ME, "flushHoldbackQueue() failed: " + e.getMessage());
                     // errorCode == "ONOVERFLOW"
                     getMsgErrorHandler().handleError(new MsgErrorInfo(glob, queueEntries, null, e));
                  }

                  try {
                     long num = this.subjectQueue.remove(list.size(), -1);
                     if (num != list.size()) {
                        log.error(ME, "PANIC: Expected to remove from subject queue '" + this.subjectQueue.getStorageId() + "' with " + this.subjectQueue.getNumOfEntries() + " entries " + list.size() + " entries, but only " + num + " where removed");
                     }
                  }
                  catch (XmlBlasterException e) {
                     log.error(ME, "PANIC: Expected to remove from subject queue '" + this.subjectQueue.getStorageId() + "' with " + this.subjectQueue.getNumOfEntries() + " entries " + list.size() + " entries: " + e.getMessage());
                  }
               }

               this.subjectQueue.clear();
               this.subjectQueue.shutdown();
               this.subjectQueue = newQueue;
               return;
            }
         }
      } // synchronized
      finally {
         this.lock.release();
      }

      log.error(ME+".setSubjectQueueProperty()", "Can't reconfigure subject queue type '" + origProp.getTypeVersion() + "' to '" + prop.getTypeVersion() + "'");
      return;
      //throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME+".setSubjectQueueProperty()", "Can't reconfigure subject queue type '" + origProps.getTypeVersion() + "' to '" + props.getTypeVersion() + "'");
   }

   /**
    * This queue holds all messages which where addressed to destination loginName
    * @return never null
    */
   public I_Queue getSubjectQueue() {
      return this.subjectQueue;
   }

   /**
    * Subject specific informations from the security framework
    * @return null if created without login (for example with a PtP message)
    */
   public I_Subject getSecurityCtx() {
      return this.securityCtx;
   }

   public void setSecurityCtx(I_Subject securityCtx) {
      this.securityCtx = securityCtx;
   }

   /**
    * Check if this subject is permitted to do something
    * <p/>
    * @param String The action the user tries to perfrom
    * @param String whereon the user tries to perform the action
    *
    * EXAMPLE:
    *    isAuthorized("PUBLISH", "thisIsAMessageKey");
    *
    * The above line checks if this subject is permitted to >>publish<<
    * a message under the key >>thisIsAMessageKey<<
    *
    * Known action keys:
    *    PUBLISH, SUBSCRIBE, GET, ERASE,
    */
   public boolean isAuthorized(MethodName actionKey, String key) {
      if (this.securityCtx == null) {
         log.warn(ME, "No authorization for '" + actionKey + "' and msg=" + key);
         return false;
      }
      return this.securityCtx.isAuthorized(actionKey, key);
   }

   /**
    * PtP mode: If the qos is set to forceQueuing the message is queued.
    * @param msgUnit The message. Only called in sync mode on publish (TopicHandler)
    * @param destination The Destination object of the receiver
    */
   public final void queueMessage(MsgQueueEntry entry) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Queuing message for destination " + entry.getReceiver());
      if (log.DUMP) log.dump(ME, "Putting PtP message to queue: " + entry.toXml(""));
      this.subjectQueue.put(entry, I_Queue.USE_PUT_INTERCEPTOR);
      //forwardToSessionQueue();
      // returns here with no waiting ...
      this.glob.getSubjectInfoShuffler().shuffle(this);
   }

   /**
    * Forward entries in subject queue to all session queues,
    * if no entries are available
    * we return 0 without doing anything.
    * @return number of messages taken from queue and forwarded
    */
   public final long forwardToSessionQueue() {
      if (getSessions().length < 1 || this.subjectQueue.getNumOfEntries() < 1) return 0;

      long numMsgs = 0;
      MsgQueueUpdateEntry entry = null;
      if (log.TRACE) log.trace(ME, "Trying to forward " + this.subjectQueue.getNumOfEntries() + " messages in subject queue to session queue ...");
      while (true) {
         try {
            try {
               entry = (MsgQueueUpdateEntry)this.subjectQueue.peek(); // non-blocking
            }
            catch (Throwable ex) {
               this.log.error(ME, "forwardToDestinationQueue: can't get entry from subject queue when trying to forward it to session queue " + ex.getMessage());
               // TODO toDead from the subject may be necessary to avoid looping
               break;
            }
            if (entry == null)
               break;
            int countForwarded = forwardToSessionQueue(entry);
            if (countForwarded > 0) {
               this.subjectQueue.removeRandom(entry); // Remove the forwarded entry (blocking)
               numMsgs++;
            }
            else if (countForwarded == -1) { // There are sessions but they don't want PtP
               break;
            }
         }
         catch(Throwable e) {
            MsgQueueEntry[] msgQueueEntries = new MsgQueueEntry[] { entry };
            MsgErrorInfo msgErrorInfo = new MsgErrorInfo(glob, msgQueueEntries, null, e);  // this.subjectQueue
            getMsgErrorHandler().handleError(msgErrorInfo);
 
            try {
               this.subjectQueue.removeRandom(entry); // Remove the entry
            }
            catch (XmlBlasterException ex) {
               this.log.error(ME, "forwardToDestinationQueue: can't empty queue when removing '" + entry.getLogId() + "' " + ex.getMessage());
               // TODO toDead from the subject may be necessary to avoid looping
               break;
            }
         }
      }

      if (log.TRACE) log.trace(ME, "Forwarded " + numMsgs + " messages from subject queue to session queue");
      
      if (!isLoggedIn()) { // Check if we can shutdown now
         shutdown(false, false);
      }

      return numMsgs;
   }

   /**
    * Forward the given message to session queue.
    * @return Number of session queues this message is forwarded to.
    *         -1 if not delivered because the available sessions don't want PtP
    * @throws XmlBlasterException if not delivered at all.
    */
   private final int forwardToSessionQueue(MsgQueueEntry entry) throws XmlBlasterException {

      if (getSessions().length < 1) return -1;

      int countForwarded = 0;

      SessionName destination = entry.getReceiver();

      if (destination.isSession()) {
         // send to a specific session, it should never happen to have such messages in the subject queue ...
         String tmp = "Can't forward msg " + entry.getLogId() + " from " +
                      this.subjectQueue.getStorageId() + " size=" + 
                      this.subjectQueue.getNumOfEntries() + " to unknown session '" + 
                      entry.getReceiver().getAbsoluteName() + "'"; 
         log.warn(ME, tmp);
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, tmp);
      }

      // ... or send to ALL sessions
      SessionInfo[] sessions = getSessions();
      for (int i=0; i<sessions.length; i++) {
         SessionInfo sessionInfo = sessions[i];
         if (sessionInfo.getConnectQos().isPtpAllowed() && sessionInfo.hasCallback()) {
            if (log.TRACE) log.trace(ME, "Forwarding msg " + entry.getLogId() + " from " +
                          this.subjectQueue.getStorageId() + " size=" + this.subjectQueue.getNumOfEntries() +
                          " to session queue " + sessionInfo.getSessionQueue().getStorageId() +
                          " size=" + sessionInfo.getSessionQueue().getNumOfEntries() + " ...");
            try {
               TopicHandler topicHandler = ((ReferenceEntry)entry).getTopicHandler();
               if (topicHandler == null) {
                  if (log.TRACE) log.trace(ME, "forwardToSessionQueue: TopicHandler is null for '" + entry.getLogId() + "'");
                  break;
               }
               I_Map cache = topicHandler.getMsgUnitCache();
               if (cache == null) {
                  if (log.TRACE) log.trace(ME, "forwardToSessionQueue: MsgUnitStore is null for '" + entry.getLogId() + "'");
                  break;
               }
               MsgQueueUpdateEntry entryCb = null;
               
               synchronized(topicHandler) {
                  synchronized(cache) {
                     entryCb = new MsgQueueUpdateEntry((MsgQueueUpdateEntry)entry, sessionInfo.getSessionQueue().getStorageId());
                  }
               }
               sessionInfo.queueMessage(entryCb);
               countForwarded++;
            }
            catch (XmlBlasterException e) {
               if (log.TRACE) log.trace(ME, "Can't forward message from subject queue '" + this.subjectQueue.getStorageId() + "' to session '" + sessionInfo.getId() + "', we keep it in the subject queue: " + e.getMessage());
            }
            catch (Throwable e) {
               e.printStackTrace();
               log.warn(ME, "Can't forward message from subject queue '" + this.subjectQueue.getStorageId() + "' to session '" + sessionInfo.getId() + "', we keep it in the subject queue: " + e.toString());
            }
         }
      }

      if (countForwarded > 0) {
         return countForwarded;
      }
      return -1;
   }

   public final I_MsgErrorHandler getMsgErrorHandler() {
      if (this.msgErrorHandler == null) {
         this.lock.lock();
         try {
            if (this.msgErrorHandler == null) {
               log.error(ME, "INTERNAL: Support for MsgErrorHandler is not implemented");
               this.msgErrorHandler = new MsgErrorHandler(glob, null);
            }
         }
         finally {
            this.lock.release();
         }
      }
      return this.msgErrorHandler;
   }

   /**
    * Is the client currently logged in?
    * @return true yes
    *         false client is not on line
    */
   public final boolean isLoggedIn() {
      synchronized (this.sessionMap) {
         return this.sessionMap.size() > 0;
      }
      //return getSessions().length > 0;
   }

   /**
    * Access the collection containing all SessionInfo objects of this user.
    */
   public final SessionInfo[] getSessions() {
      if (this.sessionArrCache == null) {
         synchronized (this.sessionMap) {
            if (this.sessionArrCache == null) {
               this.sessionArrCache = (SessionInfo[])this.sessionMap.values().toArray(new SessionInfo[this.sessionMap.size()]);
            }
         }
      }
      return this.sessionArrCache;
      
   }

   /**
    * Find a session by its absolute name. 
    * @param absoluteName e.g. "/node/heron/client/joe/2"
    * @return SessionInfo or null if not found
    */
   public final SessionInfo getSessionByAbsoluteName(String absoluteName) {
      synchronized (this.sessionMap) {
         return (SessionInfo)this.sessionMap.get(absoluteName);
      }
   }

   /**
    * Find a session by its public session ID. 
    * @param sessionName
    * @return SessionInfo or null if not found
    */
   public final SessionInfo getSession(SessionName sessionName) {
      synchronized (this.sessionMap) {
         return (SessionInfo)this.sessionMap.get(sessionName.getAbsoluteName());
      }
   }

   public final SessionInfo getFirstSession() {
      SessionInfo[] sessions = getSessions();
      return (sessions.length > 0) ? sessions[0] : null;
   }

   /**
    * Get the callback addresses for this subjectQueue, every session
    * callback may have decided to receive subject messages
    */
   public final CallbackAddress[] getCallbackAddresses() {
      if (this.callbackAddressCache == null) {
         SessionInfo[] sessions = getSessions();
         Set set = new HashSet();
         for (int i=0; i<sessions.length; i++) {
            SessionInfo ses = sessions[i];
            if (ses.hasCallback()) {
               CallbackAddress[] arr = ((CbQueueProperty)ses.getSessionQueue().getProperties()).getCallbackAddresses();
               for (int ii=0; arr!=null && ii<arr.length; ii++) {
                  if (arr[ii].useForSubjectQueue() == true)
                     set.add(arr[ii]);
               }
            }
         }
         this.callbackAddressCache = (CallbackAddress[])set.toArray(new CallbackAddress[set.size()]);
      }
      if (log.TRACE) log.trace(ME, "Accessing " + this.callbackAddressCache.length + " callback addresses from " + getSessions().length + " sessions for '" + getLoginName() + "' queue");
      return this.callbackAddressCache;
   }

   /**
    * If you have a callback address and want to know to which session it belongs.
    * @param addr The address object
    * @return the sessionInfo or null
    */
   public final SessionInfo findSessionInfo(AddressBase addr) {
      SessionInfo[] sessions = getSessions();
      for (int i=0; i<sessions.length; i++) {
         SessionInfo ses = sessions[i];
         if (ses.hasAddress(addr))
            return ses;
      }
      return null;
   }

   /**
    * @exception Throws XmlBlasterException if max. sessions is exhausted
    */
   public final void checkNumberOfSessions(ConnectQosData qos) throws XmlBlasterException {
      if (SessionQos.DEFAULT_maxSessions != qos.getSessionQos().getMaxSessions())
         this.maxSessions = qos.getSessionQos().getMaxSessions();

      if (getSessions().length >= this.maxSessions) {
         log.warn(ME, "Max sessions = " + this.maxSessions + " for user " + getLoginName() + " exhausted, login denied.");
         throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION_MAXSESSION, ME, "Max sessions = " + this.maxSessions + " exhausted, login denied.");
      }
   }

   /**
    * Get notification that the client did a login.
    * <p />
    * This instance may exist before a login was done, for example
    * when some messages where directly addressed to this client.<br />
    * This notifies about a client login.
    */
   public final void notifyAboutLogin(SessionInfo sessionInfo) throws XmlBlasterException {
      if (!isAlive()) { // disconnect() and connect() are not synchronized, so this can happen
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "SubjectInfo is shutdown, try to login again");
      }
      
      if (log.CALL) log.call(ME, "notifyAboutLogin(" + sessionInfo.getSecretSessionId() + ")");
      synchronized (this.sessionMap) {
         this.sessionMap.put(sessionInfo.getId(), sessionInfo);
         this.sessionArrCache = null;
         this.callbackAddressCache = null;
      }
      if (log.DUMP) log.dump(ME, this.subjectQueue.toXml(""));

      if (this.subjectQueue.getNumOfEntries() > 0) {
         if (log.TRACE) log.trace(ME, "Flushing " + this.subjectQueue.getNumOfEntries() + " messages");
         this.glob.getSubjectInfoShuffler().shuffle(this); // Background thread
      }
   }

   /**
    * Get notification that the client did a logout.
    * <br />
    * Note that the loginName is not reset.
    * @param absoluteSessionName == sessionInfo.getId()
    * @param clearQueue Shall the message queue of the client be cleared&destroyed as well (e.g. disconnectQos.deleteSubjectQueue())?
    * @param forceShutdownEvenIfEntriesExist on last session
    */
   public final void notifyAboutLogout(String absoluteSessionName, boolean clearQueue, boolean forceShutdownEvenIfEntriesExist) throws XmlBlasterException {
      if (!isAlive()) { // disconnect() and connect() are not synchronized, so this can happen
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "SubjectInfo is shutdown, no logout");
      }
      if (log.CALL) log.call(ME, "Entering notifyAboutLogout(" + absoluteSessionName + ", " + clearQueue + ")");
      SessionInfo sessionInfo = null;
      synchronized (this.sessionMap) {
         sessionInfo = (SessionInfo)sessionMap.remove(absoluteSessionName);
         this.sessionArrCache = null;
         this.callbackAddressCache = null;
      }
      if (sessionInfo != null) {
         this.dispatchStatistic.incrNumUpdate(sessionInfo.getNumUpdate());
      }
      else {
         log.warn(ME, "Lookup of session with absoluteSessionName=" + absoluteSessionName + " failed");
      }

      if (log.DUMP) log.dump(ME, this.subjectQueue.toXml(null));

      //if (!isLoggedIn()) {
      //   if (clearQueue || getSubjectQueue().getNumOfEntries() < 1) {
            shutdown(clearQueue, forceShutdownEvenIfEntriesExist); // Does shutdown only on last session
      //   }
      //}
   }

   /**
    * Access the unique login name of a client.
    * <br />
    * If not known, its unique key (subjectId) is delivered
    * @return The SessionName object specific for a subject (pubSessionId is null)
    */
   public final SessionName getSubjectName() {
      return this.subjectName;
   }

   /**
    * Cluster wide unique identifier "/node/heron/client/<loginName>" e.g. for logging
    * <p />
    * @return e.g. "client/joe
    */
   public final String getId() {
      return this.subjectName.getAbsoluteName();
   }

   /**
    * @see #getId
    */
   public final String toString() {
      return this.subjectName.getAbsoluteName();
   }

   /**
    * Access the unique login name of a client.
    * <br />
    * If not known, its unique key (subjectId) is delivered
    * @return loginName
    */
   public final String getLoginName() {
      return this.subjectName.getLoginName();
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of SubjectInfo as a XML ASCII string
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of SubjectInfo as a XML ASCII string
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<SubjectInfo id='").append(this.subjectName.getAbsoluteName()).append("'>");
      sb.append(offset).append(" <state>").append(getStateStr()).append("</state>");
      if (isAlive()) {
         sb.append(offset).append(" <subjectId>").append(getLoginName()).append("</subjectId>");
         sb.append(subjectQueue.toXml(extraOffset+Constants.INDENT));
         SessionInfo[] sessions = getSessions();
         for (int i=0; i<sessions.length; i++) {
            SessionInfo sessionInfo = sessions[i];
            sb.append(sessionInfo.toXml(extraOffset+Constants.INDENT));
         }
      }
      sb.append(offset).append("</SubjectInfo>");

      return sb.toString();
   }

   /**
    * Get the SessionInfo with its public session identifier e.g. "5"
    * @return null if not found
    */
   public final SessionInfo getSessionByPublicId(long publicSessionId) {
      if (publicSessionId == 0L) {
         return null;
      }
      SessionName sessionName = new SessionName(glob, subjectName, publicSessionId);
      synchronized (this.sessionMap) {
         return (SessionInfo)this.sessionMap.get(sessionName.getAbsoluteName());
      }
   }

   public final boolean isUndef() {
      return this.state == UNDEF;
   }

   public final boolean isAlive() {
      return this.state == ALIVE;
   }

   public final boolean isDead() {
      return this.state == DEAD;
   }

   public final String getStateStr() {
      if (isAlive()) {
         return "ALIVE";
      }
      else if (isDead()) {
         return "DEAD";
      }
      else if (isUndef()) {
         return "UNDEF";
      }
      else {
         return "INTERNAL_ERROR";
      }
   }

   //=========== Enforced by I_AdminSubject and SubjectInfoProtector.java ================
   /**
    * @return startupTime in seconds
    */
   long getUptime() {
      return (System.currentTimeMillis() - this.startupTime)/1000L;
   }

   public final String getCreationDate() {
      long ll = this.startupTime;
      java.sql.Timestamp tt = new java.sql.Timestamp(ll);
      return tt.toString();
   }

   /**
    * How many update where sent for this client, the sum of all session and
    * subject queues of this clients.
    */
   long getNumUpdate() {
      long numUpdates = this.dispatchStatistic.getNumUpdate(); // The sessions which disappeared already are remembered here
      SessionInfo[] sessions = getSessions();
      for (int i=0; i<sessions.length; i++) {
         SessionInfo sessionInfo = sessions[i];
         numUpdates += sessionInfo.getNumUpdate();
      }
      return numUpdates;
   }

   long getSubjectQueueNumMsgs() {
      return subjectQueue.getNumOfEntries();
   }

   long getSubjectQueueMaxMsgs() {
      return subjectQueue.getMaxNumOfEntries();
   }

   /**
    * Access the number of sessions of this user.
    * @return The number of sessions of this user
    */
   int getNumSessions() {
      return getSessions().length;
   }

   /**
    * @return The max allowed simultaneous logins of this user
    */
   int getMaxSessions() {
      return this.maxSessions;
   }

   /**
    * JMX access. 
    * @param Change the max allowed simultaneous logins of this user
    */
   void setMaxSessions(int max) {
      this.maxSessions = max;
   }

   /**
    * Access a list of public session identifier e.g. "1,5,7,12"
    * @return An empty string if no sessions available
    */
   String getSessionList() {
      int numSessions = getNumSessions();
      if (numSessions < 1)
         return "";
      StringBuffer sb = new StringBuffer(numSessions * 30);
      SessionInfo[] sessions = getSessions();
      for (int i=0; i<sessions.length; i++) {
         if (sb.length() > 0)
            sb.append(",");
         sb.append(sessions[i].getPublicSessionId());
      }
      return sb.toString();
   }

   /**
    * Find a session by its public session ID. 
    * @param pubSessionId e.g. "-2"
    * @return I_AdminSession or null if not found
    */
   I_AdminSession getSessionByPubSessionId(long pubSessionId) {
      SessionInfo sessionInfo = getSessionByPublicId(pubSessionId);
      return (sessionInfo == null) ? null : sessionInfo.getSessionInfoProtector();
   }

   /**
    * Kills all sessions of this client
    * @return The list of killed sessions (public session IDs)
    */
   String killClient() throws XmlBlasterException {
      int numSessions = getNumSessions();
      if (numSessions < 1)
         return "";
      String sessionList = getSessionList();
      while (true) {
         SessionInfo sessionInfo = null;
         synchronized (sessionMap) {
            Iterator iterator = sessionMap.values().iterator();
            if (!iterator.hasNext())
               break;
            sessionInfo = (SessionInfo)iterator.next();
         }
         sessionInfo.killSession();
      }
      /* The upper form is probably better
      SessionInfo[] sessions = getSessions();
      for (int ii=0; ii<sessions.length; ii++) {
         sessions[ii].killSession();
      }
      */
     return getId() + " Sessions " + sessionList + " killed";
   }

   public String[] peekSubjectMessages(int numOfEntries) throws XmlBlasterException {
      return this.glob.peekMessages(this.subjectQueue, numOfEntries, "subject");
   } 

   public String[] peekSubjectMessagesToFile(int numOfEntries, String path) throws Exception {
      try {
         return this.glob.peekQueueMessagesToFile(this.subjectQueue, numOfEntries, path, "subject");
      }
      catch (XmlBlasterException e) {
         throw new Exception(e.toString());
      }
   }

   /**
    * JMX: Enforced by interface NotificationBroadcasterSupport
    */
   public MBeanNotificationInfo[] getNotificationInfo() { 
      String[] types = new String[] { 
         AttributeChangeNotification.ATTRIBUTE_CHANGE 
      }; 
      String name = AttributeChangeNotification.class.getName(); 
      String description = "TODO: An attribute of this MBean has changed"; 
      MBeanNotificationInfo info = 
         new MBeanNotificationInfo(types, name, description); 
      return new MBeanNotificationInfo[] {info}; 
   } 
}
