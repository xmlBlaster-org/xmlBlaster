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
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.authentication.plugins.I_Subject;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.qos.SessionQos;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.cluster.NodeId;
import org.xmlBlaster.engine.cluster.ClusterNode;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.dispatch.DeliveryManager;
import org.xmlBlaster.util.dispatch.DeliveryStatistic;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.engine.queuemsg.MsgQueueUpdateEntry;
import org.xmlBlaster.authentication.plugins.I_MsgSecurityInterceptor;
import org.xmlBlaster.engine.admin.I_AdminSubject;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Collection;
import java.util.Collections;


/**
 * SubjectInfo stores all known data about a client.
 * <p />
 * It also contains a message queue, where messages are stored
 * until they are delivered at the next login of this client.
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class SubjectInfo implements I_AdminSubject
{
   private String ME = "SubjectInfo";
   private final Global glob;
   private final LogChannel log;
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
   /** Check if instance is still valid */
   private boolean isShutdown = false;
   public CallbackAddress[] callbackAddressCache = null;

   private final DeliveryStatistic deliveryStatistic;

   private NodeId nodeId = null;
   private boolean determineNodeId = true;

   // Enforced by I_AdminSubject
   /** Incarnation time of this object instance in millis */
   private long uptime;
   private int maxSessions;


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


   /**
    * Create this instance when a client did a login.
    * <p />
    * @param securityCtx  The security context of this subject
    * @param prop         The property from the subject queue, usually from connectQos.getSubjectQueueProperty()
    */
   public SubjectInfo(Global glob, I_Subject securityCtx, CbQueueProperty prop) throws XmlBlasterException {
      this(glob, securityCtx.getName(), securityCtx, prop);
      if (securityCtx==null) {
         String tmp="SubjectInfo(securityCtx==null); // a correct security manager must be set.";
         log.error(ME+".illegalArgument", tmp);
         throw new XmlBlasterException(ME+".illegalArgument", tmp);
      }
      if (log.CALL) log.call(ME, "Created new SubjectInfo " + instanceId + ": " + toString());
   }

   /**
    * Create this instance when a message is sent to this client, but he is not logged in
    * <p />
    * @param loginName The unique login name
    */
   public SubjectInfo(Global glob, String loginName) throws XmlBlasterException {
      this(glob, loginName, null, null);
      if (log.CALL) log.trace(ME, "Creating new empty SubjectInfo");
   }

   /**
    * Initialize.
    * <p />
    * @param loginName    The unique loginName
    * @param securityCtx  The security context of this subject
    * @param prop         The property from the subject queue, usually from connectQos.getSubjectQueueProperty()
    */
   private SubjectInfo(Global glob, String loginName, I_Subject securityCtx, CbQueueProperty prop)
          throws XmlBlasterException {
      synchronized (SubjectInfo.class) {
         instanceId = instanceCounter;
         instanceCounter++;
      }
      this.glob = glob;
      this.log = this.glob.getLog("auth");
      String prae = glob.getLogPrefix();
      this.subjectName = new SessionName(glob, glob.getNodeId(), loginName);
      this.ME = "SubjectInfo-" + instanceCounter + "-" + this.subjectName.getAbsoluteName();
      this.uptime = System.currentTimeMillis();
      this.securityCtx = securityCtx;
      this.deliveryStatistic = new DeliveryStatistic();

      this.maxSessions = glob.getProperty().get("session.maxSessions", SessionQos.DEFAULT_maxSessions);
      if (glob.getId() != null)
         this.maxSessions = glob.getProperty().get("session.maxSessions["+glob.getId()+"]", this.maxSessions);

      if (prop == null) prop = new CbQueueProperty(glob, Constants.RELATING_SUBJECT, glob.getId());
      String type = prop.getType();
      String version = prop.getVersion();
      this.subjectQueue = glob.getQueuePluginManager().getPlugin(type, version,
                          new StorageId(Constants.RELATING_SUBJECT, this.subjectName.getAbsoluteName()), prop);
      this.subjectQueue.setNotifiedAboutAddOrRemove(true); // Entries are notified to support reference counting

      if (log.TRACE) log.trace(ME, "Created new SubjectInfo");
   }

   public void finalize() {
      if (log.TRACE) log.trace(ME, "finalize - garbage collected " + getLoginName());
   }

   public boolean isShutdown() {
      return this.isShutdown;
   }

   public void shutdown() {
      if (log.CALL) log.call(ME, "shutdown() of subject " + getLoginName());
      this.isShutdown = true;
      boolean force = false;
      this.subjectQueue.shutdown(force);
      this.subjectQueue = null;
      if (getSessions().length > 0) {
         log.warn(ME, "shutdown() of subject " + getLoginName() + " has still " + getSessions().length + " sessions - memory leak?");
      }
      synchronized (this.sessionMap) {
         this.sessionMap.clear();
         this.sessionArrCache = null;
         this.callbackAddressCache = null;
      }
      this.securityCtx = null;
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
    * Allows to overwrite queue property, will be only written if prop!= null
    */
   public final void setCbQueueProperty(CbQueueProperty prop) throws XmlBlasterException {
      this.subjectQueue.setProperties(prop);
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
    */
   public I_Subject getSecurityCtx() {
      return this.securityCtx;
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
      if (securityCtx == null) {
         log.warn(ME, "No authorization for '" + actionKey + "' and msg=" + key);
         return false;
      }
      return securityCtx.isAuthorized(actionKey, key);
   }

   /**
    * PtP mode: If the qos is set to forceQueuing the message is queued.
    * @param msgUnit The message
    * @param destination The Destination object of the receiver
    */
   public final void queueMessage(MsgQueueEntry entry) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "queuing message");
      /*
      if (msgUnit == null) {
         log.error(ME+".Internal", "Can't queue null message");
         throw new XmlBlasterException(ME+".Internal", "Can't queue null message");
      }

      MsgQueueUpdateEntry entry = new MsgQueueUpdateEntry(glob, msgUnit, this.subjectQueue, getSubjectName());
      */
      if (log.DUMP) log.dump(ME, "Putting PtP message to queue: " + entry.toXml(""));

      int countForwarded = forwardToSessionQueue(entry);

      if (countForwarded == 0) {
         log.warn(ME, "No login session available, queueing message '" + entry.getLogId() + "'");
         try {
            this.subjectQueue.put(entry, false);
            forwardToSessionQueue();
         }
         catch (Throwable e) {
            log.warn(ME, e.toString());
            throw new XmlBlasterException(ME, "Can't process PtP message: " + e.toString());
         }
      }
   }

   /**
    * Forward entries in subject queue to all session queues,
    * if no entries are available
    * we return 0 without doing anything.
    * @return number of messages taken from queue and forwarded
    */
   private final long forwardToSessionQueue() {

      if (getSessions().length < 1 || this.subjectQueue.getNumOfEntries() < 1) return 0;

      long numMsgs = 0;
      MsgQueueUpdateEntry entry = null;
      if (log.TRACE) log.trace(ME, "Forwarding " + this.subjectQueue.getNumOfEntries() + " messages in subcject queue to session queue");
      while (true) {
         try {
            entry = (MsgQueueUpdateEntry)this.subjectQueue.peek(); // none blocking
            if (entry == null)
               break;
            int countForwarded = forwardToSessionQueue(entry);
            if (countForwarded > 0) {
               this.subjectQueue.remove(); // Remove the forwarded entry (blocking)
               numMsgs++;
            }
         }
         catch(Throwable e) {
            String id = (entry == null) ? "" : entry.getKeyOid();
            log.warn(ME, "Can't forward message " + id + " to session queue, keeping it in subject queue");
         }
      }
      return numMsgs;
   }

   /**
    * Forward the given message to session queue.
    * @return Number of session queues this message is forwarded to
    *         or 0 if not delivered at all
    */
   private final int forwardToSessionQueue(MsgQueueEntry entry) {

      if (getSessions().length < 1) return 0;

      int countForwarded = 0;
      SessionInfo[] sessions = getSessions();
      for (int i=0; i<sessions.length; i++) {
         SessionInfo sessionInfo = sessions[i];
         if (log.TRACE) log.trace(ME, "Forwarding msg " + entry.getLogId() + " from " +
                          this.subjectQueue.getStorageId() + " size=" + this.subjectQueue.getNumOfEntries() +
                          " to session queue " + sessionInfo.getSessionQueue().getStorageId() +
                          " size=" + sessionInfo.getSessionQueue().getNumOfEntries() + " ...");
         if (sessionInfo.hasCallback()) {
            try {
               sessionInfo.queueMessage(entry);
               countForwarded++;
            }
            catch (Throwable e) {
               log.warn(ME, "Can't deliver message with session '" + sessionInfo.getId() + "': " + e.toString());
            }
         }
      }
      return countForwarded;
   }

   /**
    * Is the client currently logged in?
    * @return true yes
    *         false client is not on line
    */
   public final boolean isLoggedIn() {
      return getSessions().length > 0;
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
         throw new XmlBlasterException(ME, "Max sessions = " + this.maxSessions + " exhausted, login denied.");
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
      if (isShutdown()) { // disconnect() and connect() are not synchronized, so this can happen
         String text = "SubjectInfo is shutdown, try to login again";
         Thread.currentThread().dumpStack();
         log.error(ME, text);
         throw new XmlBlasterException(ME, text);
      }
      if (log.CALL) log.call(ME, "notifyAboutLogin(" + sessionInfo.getSecretSessionId() + ")");
      synchronized (this.sessionMap) {
         this.sessionMap.put(sessionInfo.getId(), sessionInfo);
         this.sessionArrCache = null;
         this.callbackAddressCache = null;
      }
      if (log.DUMP) log.dump(ME, this.subjectQueue.toXml(""));

      if (log.TRACE) log.trace(ME, "Flushing " + this.subjectQueue.getNumOfEntries() + " messages");
      forwardToSessionQueue();
   }

   /**
    * Get notification that the client did a logout.
    * <br />
    * Note that the loginName is not reset.
    * @param absoluteSessionName == sessionInfo.getId()
    * @param clearQueue Shall the message queue of the client be destroyed as well?
    */
   public final void notifyAboutLogout(String absoluteSessionName, boolean clear) throws XmlBlasterException {
      if (isShutdown()) { // disconnect() and connect() are not synchronized, so this can happen
         String text = "SubjectInfo is shutdown, no logout";
         Thread.currentThread().dumpStack();
         log.error(ME, text);
         throw new XmlBlasterException(ME, text);
      }
      if (log.CALL) log.call(ME, "Entering notifyAboutLogout(" + absoluteSessionName + ", " + clear + ")");
      SessionInfo sessionInfo = null;
      synchronized (this.sessionMap) {
         sessionInfo = (SessionInfo)sessionMap.remove(absoluteSessionName);
         this.sessionArrCache = null;
         this.callbackAddressCache = null;
      }
      if (sessionInfo != null) {
         this.deliveryStatistic.incrNumUpdate(sessionInfo.getNumUpdates());
      }
      else {
         log.warn(ME, "Lookup of session with absoluteSessionName=" + absoluteSessionName + " failed");
      }

      if (log.DUMP) log.dump(ME, this.subjectQueue.toXml(null));

      if (getSessions().length == 0 && clear) {
         // "Clearing of subject queue see Authenticate.java:351
         // authenticate.loginNameSubjectInfoMap.remove(loginName);
      }
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

   public I_Subject getSecuritySubject() {
      return securityCtx;
   }

   public void setSecuritySubject(I_Subject ctx) {
      this.securityCtx = ctx;
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of SubjectInfo as a XML ASCII string
    */
   public final String toXml() throws XmlBlasterException {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of SubjectInfo as a XML ASCII string
    */
   public final String toXml(String extraOffset) throws XmlBlasterException {
      StringBuffer sb = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<SubjectInfo id='").append(this.subjectName.getAbsoluteName()).append("'>");
      if (isShutdown) {
         sb.append(offset).append(" <isShutdown/>");
      }
      else {
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
      SessionInfo[] sessions = getSessions();
      for (int i=0; i<sessions.length; i++) {
         SessionInfo sessionInfo = sessions[i];
         if (sessionInfo.getPublicSessionId() == publicSessionId)
            return sessionInfo;
      }
      return null;
   }


   //=========== Enforced by I_AdminSubject ================
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
      long numUpdates = this.deliveryStatistic.getNumUpdate(); // The sessions which disappeared already are remembered here
      SessionInfo[] sessions = getSessions();
      for (int i=0; i<sessions.length; i++) {
         SessionInfo sessionInfo = sessions[i];
         numUpdates += sessionInfo.getNumUpdates();
      }
      return numUpdates;
   }

   public final long getCbQueueNumMsgs() {
      return subjectQueue.getNumOfEntries();
   }

   public final long getCbQueueMaxMsgs() {
      return subjectQueue.getMaxNumOfEntries();
   }

   /**
    * Access the number of sessions of this user.
    * @return The number of sessions of this user
    */
   public final int getNumSessions() {
      return getSessions().length;
   }

   /**
    * @return The max allowed simultaneous logins of this user
    */
   public final int getMaxSessions() {
      return this.maxSessions;
   }

   /**
    * Access a list of public session identifier e.g. "1,5,7,12"
    * @return An empty string if no sessions available
    */
   public final String getSessionList() {
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
    * Kills all sessions of this client
    * @return The list of killed sessions (public session IDs)
    */
   public final String getKillClient() throws XmlBlasterException {
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
         sessionInfo.getKillSession();
      }
      /* The upper form is probably better
      SessionInfo[] sessions = getSessions();
      for (int ii=0; ii<sessions.length; ii++) {
         sessions[ii].getKillSession();
      }
      */
     return getId() + " Sessions " + sessionList + " killed";
   }
}
