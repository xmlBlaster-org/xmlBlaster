/*------------------------------------------------------------------------------
Name:      SubjectInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;


import org.jutils.log.LogChannel;

import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.queue.MsgQueue;
import org.xmlBlaster.engine.queue.SubjectMsgQueue;
import org.xmlBlaster.engine.queue.MsgQueueEntry;
import org.xmlBlaster.authentication.plugins.I_Subject;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.CbQueueProperty;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.cluster.NodeId;
import org.xmlBlaster.engine.cluster.ClusterNode;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
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
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>
 */
public class SubjectInfo implements I_AdminSubject
{
   private String ME = "SubjectInfo";
   private final Global glob;
   private final LogChannel log;
   /** The unique client identifier */
   private String loginName = null; 
   /** The partner class from the security framework */
   private I_Subject securityCtx = null;
   /** All sessions of this subject are stored in this map.
       The sessionId is the key, the SessionInfo object the value
   */
   private Map sessionMap = Collections.synchronizedMap(new HashMap());
   /** Check if instance is still valid */
   private boolean isShutdown = false;
   /** Cache for callback addresses for this subject queue */
   public CallbackAddress[] callbackAddressCache = null;

   private NodeId nodeId = null;
   private boolean determineNodeId = true;

   // Enforced by I_AdminSubject
   /** Incarnation time of this object instance in millis */
   private long uptime;
   private int maxSessions;
   

   /**
    * All MessageUnit which can't be delivered to the client (if he is not logged in)
    * are queued here and are delivered when the client comes on line.
    * <p>
    * Node objects = MsgQueueEntry
    */
   private SubjectMsgQueue subjectQueue;

   
   /** Statistics */
   private static long instanceCounter = 0L;
   private long instanceId = 0L;


   /**
    * Create this instance when a client did a login.
    * <p />
    * @param securityCtx  The security context of this subject
    * @param prop         The property from the subject queue, usually from connectQos.getSubjectCbQueueProperty()
    */
   public SubjectInfo(I_Subject securityCtx, CbQueueProperty prop, Global glob)
          throws XmlBlasterException
   {
      this.glob = glob;
      this.log = this.glob.getLog("auth");
      if (securityCtx==null) {
         String tmp="SubjectInfo(securityCtx==null); // a correct security manager must be set.";
         log.error(ME+".illegalArgument", tmp);
         throw new XmlBlasterException(ME+".illegalArgument", tmp);
      }
      initialize(securityCtx.getName(), securityCtx, prop, glob);
      if (log.CALL) log.call(ME, "Created new SubjectInfo " + instanceId + ": " + toString());
   }

   /**
    * Create this instance when a message is sent to this client, but he is not logged in
    * <p />
    * @param loginName The unique login name
    */
   public SubjectInfo(String loginName, Global glob) throws XmlBlasterException
   {
      this.glob = glob;
      this.log = this.glob.getLog("auth");
      initialize(loginName, null, null, glob);
      if (log.CALL) log.trace(ME, "Creating new empty SubjectInfo for " + loginName);
   }

   /**
    * Initialize. 
    * <p />
    * @param loginName    The unique loginName
    * @param securityCtx  The security context of this subject
    * @param prop         The property from the subject queue, usually from connectQos.getSubjectCbQueueProperty()
    */
   private void initialize(String loginName, I_Subject securityCtx, CbQueueProperty prop, Global glob)
          throws XmlBlasterException
   {
      synchronized (SubjectInfo.class) {
         instanceId = instanceCounter;
         instanceCounter++;
      }
      this.loginName = loginName;
      this.ME = "SubjectInfo-"+instanceCounter+":"+loginName;
      this.uptime = System.currentTimeMillis();
      this.securityCtx = securityCtx;

      this.maxSessions = glob.getProperty().get("session.maxSessions", ConnectQos.DEFAULT_maxSessions);
      if (glob.getId() != null)
         this.maxSessions = glob.getProperty().get("session.maxSessions["+glob.getId()+"]", this.maxSessions);

      if (prop == null) prop = new CbQueueProperty(glob, Constants.RELATING_SUBJECT, glob.getId());
      this.subjectQueue = new SubjectMsgQueue(this, "subject:"+loginName, prop, glob);
      if (log.TRACE) log.trace(ME, "Created new SubjectInfo " + loginName);
   }

   public void finalize()
   {
      if (log.TRACE) log.trace(ME, "finalize - garbage collected " + getLoginName());
   }

   public boolean isShutdown()
   {
      return this.isShutdown();
   }

   public void shutdown()
   {
      if (log.CALL) log.call(ME, "shutdown() of subject " + getLoginName());
      this.isShutdown = true;
      this.subjectQueue.shutdown();
      this.subjectQueue = null;
      if (this.sessionMap.size() > 0) {
         log.warn(ME, "shutdown() of subject " + getLoginName() + " has still " + this.sessionMap.size() + " sessions - memory leak?");
      }
      this.sessionMap.clear();
      this.callbackAddressCache = null;
      //this.loginName = null;
      this.securityCtx = null;
   }

   /**
    * @return not null if client is a cluster node, else null
    */
   public final NodeId getNodeId() throws XmlBlasterException {
      if (determineNodeId) {

         determineNodeId = false;
      
         if (loginName.startsWith(org.xmlBlaster.engine.RequestBroker.internalLoginNamePraefix))
            return null; // don't check for internal logins

         if (glob.useCluster()) {
            // Is the client a well known, configured cluster node?
            ClusterNode clusterNode = glob.getClusterManager().getClusterNode(loginName); // is null if not found
            if (clusterNode != null) {
               nodeId = clusterNode.getNodeId();
            }
            else {
               // Does the client send a tag which marks it as a cluster node?

               SessionInfo ses = getFirstSession();
               if (ses != null) {
                  if (ses.getConnectQos().isClusterNode())
                     nodeId = new NodeId(loginName);
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
      this.subjectQueue.setProperty(prop);
   }

   /**
    * This queue holds all messages which where addressed to destination loginName
    * @return never null
    */
   public MsgQueue getSubjectQueue() {
      return subjectQueue;
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
   public boolean isAuthorized(String actionKey, String key) {
      if (securityCtx == null) {
         log.warn(ME, "No authorization for '" + actionKey + "' and msg=" + key);
         return false;
      }
      return securityCtx.isAuthorized(actionKey, key);
   }

   /**
    * PtP mode: If the qos is set to forceQueuing the message is queued.
    * @param msgUnitWrapper Wraps the msgUnit with some more infos
    * @param destination The Destination object of the receiver
    */
   public final void queueMessage(MsgQueueEntry entry) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Client [" + loginName + "] queing message");
      if (entry == null) {
         log.error(ME+".Internal", "Can't queue null message");
         throw new XmlBlasterException(ME+".Internal", "Can't queue null message");
      }
      //log.info(ME, toXml());
      subjectQueue.putMsg(entry);
   }

   /**
    * Is the client currently logged in?
    * @return true yes
    *         false client is not on line
    */
   public final boolean isLoggedIn()
   {
      return (sessionMap.size() > 0);
   }

   /**
    * Access the collection containing all SessionInfo objects of this user. 
    */
   public final Collection getSessions()
   {
      return sessionMap.values();
   }

   public final SessionInfo getFirstSession()
   {
      return (SessionInfo)getSessions().iterator().next();
   }

   /**
    * Get the callback addresses for this subjectQueue, every session
    * callback may have decided to receive subject messages
    */
   public final CallbackAddress[] getCallbackAddresses()
   {
      if (this.callbackAddressCache == null) {
         Iterator it = getSessions().iterator();
         Set set = new HashSet();
         while (it.hasNext()) {
            SessionInfo ses = (SessionInfo)it.next();
            CallbackAddress[] arr = ses.getSessionQueue().getProperty().getCallbackAddresses();
            for (int ii=0; arr!=null && ii<arr.length; ii++) {
               if (arr[ii].useForSubjectQueue() == true)
                  set.add(arr[ii]);
            }
         }
         this.callbackAddressCache = (CallbackAddress[])set.toArray(new CallbackAddress[set.size()]);
      }
      if (log.TRACE) log.trace(ME, "Accessing " + this.callbackAddressCache.length + " callback addresses for '" + getLoginName() + "' queue");
      return this.callbackAddressCache;
   }

   /**
    * If you have a callback address and want to know to which session it belongs. 
    * @param addr The address object
    * @return the sessionInfo or null
    */
   public final SessionInfo findSessionInfo(CallbackAddress addr)
   {
      Iterator it = getSessions().iterator();
      while (it.hasNext()) {
         SessionInfo ses = (SessionInfo)it.next();
         if (ses.hasAddress(addr))
            return ses;
      }
      return null;
   }

   /**
    * @exception Throws XmlBlasterException if max. sessions is exhausted
    */
   public final void checkNumberOfSessions(ConnectQos qos) throws XmlBlasterException
   {
      if (ConnectQos.DEFAULT_maxSessions != qos.getMaxSessions())
         this.maxSessions = qos.getMaxSessions();

      if (sessionMap.size() >= this.maxSessions) {
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
   public final void notifyAboutLogin(SessionInfo sessionInfo) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "notifyAboutLogin(" + sessionInfo.getSessionId() + ")");
      sessionMap.put(sessionInfo.getSessionId(), sessionInfo);

      this.callbackAddressCache = null;
      this.subjectQueue.setCbAddresses(getCallbackAddresses());

      if (log.DUMP) log.dump(ME, this.subjectQueue.toXml());

      synchronized (this.subjectQueue) {
         if (this.subjectQueue.size() > 0) {
            if (log.TRACE) log.trace(ME, "Flushing " + this.subjectQueue.size() + " messages");
            this.subjectQueue.activateCallbackWorker();
         }
      }
   }


   /**
    * Get notification that the client did a logout.
    * <br />
    * Note that the loginName is not reset.
    * @param clearQueue Shall the message queue of the client be destroyed as well?
    */
   public final void notifyAboutLogout(String sessionId, boolean clear) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering notifyAboutLogout(" + sessionId + ", " + clear + ")");
      sessionMap.remove(sessionId);

      this.callbackAddressCache = null;
      this.subjectQueue.setCbAddresses(getCallbackAddresses());

      if (log.DUMP) log.dump(ME, this.subjectQueue.toXml());
      
      if (sessionMap.size() == 0 && clear) {
         // "Clearing of subject queue see Authenticate.java:351
         // authenticate.loginNameSubjectInfoMap.remove(loginName);
      }
   }

   /**
    * This is the unique identifier of the client,
    * <p />
    * @return getLoginName()
    */
   public final String getUniqueKey()
   {
      return loginName;
   }

   /**
    * Access the unique login name of a client.
    * <br />
    * If not known, its unique key (subjectId) is delivered
    * @return loginName
    * @todo The subjectId is a security risk, what else
    *        can we use? !!!
    */
   public final String getLoginName()
   {
      return loginName;
   }

   public I_Subject getSecuritySubject() {
      return securityCtx;
   }

   public void setSecuritySubject(I_Subject ctx) {
      this.securityCtx = ctx;
   }

   /**
    * The unique login name.
    * <p />
    * @return the loginName
    */
   public final String toString()
   {
      return loginName;
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of SubjectInfo as a XML ASCII string
    */
   public final String toXml() throws XmlBlasterException
   {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of SubjectInfo as a XML ASCII string
    */
   public final String toXml(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer(256);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<SubjectInfo id='").append(instanceId).append("' subjectId='").append(getUniqueKey()).append("'>");
      if (isShutdown) {
         sb.append(offset).append("   <isShutdown/>");
      }
      else {
         sb.append(offset).append("   <loginName>").append(loginName).append("</loginName>");
         sb.append(subjectQueue.toXml(extraOffset+"   "));
         Iterator iterator = sessionMap.values().iterator();
         while (iterator.hasNext()) {
            SessionInfo sessionInfo = (SessionInfo)iterator.next();
            sb.append(sessionInfo.toXml(extraOffset+"   "));
         }
      }
      sb.append(offset).append("</SubjectInfo>");

      return sb.toString();
   }

   /**
    * Get the SessionInfo with its public session identifier e.g. "5"
    * @return null if not found
    */
   public final SessionInfo getSessionByPublicId(String publicSessionId) {
      Iterator iterator = sessionMap.values().iterator();
      while (iterator.hasNext()) {
         SessionInfo sessionInfo = (SessionInfo)iterator.next();
         if (sessionInfo.getPublicSessionId().equals(publicSessionId))
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
      return this.subjectQueue.getNumUpdates();
      // for (iSession) num += iSessionQueue.getNumUpdates(); ?
   }

   public final int getCbQueueNumMsgs() {
      return subjectQueue.size();
   }

   public final int getCbQueueMaxMsgs() {
      return subjectQueue.capacity();
   }
 
   /**
    * Access the number of sessions of this user. 
    * @return The number of sessions of this user
    */
   public final int getNumSessions()
   {
      return this.sessionMap.size();
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
      Iterator iterator = sessionMap.values().iterator();
      while (iterator.hasNext()) {
         if (sb.length() > 0)
            sb.append(",");
         SessionInfo sessionInfo = (SessionInfo)iterator.next();
         sb.append(sessionInfo.getPublicSessionId());
      }
      return sb.toString();
   }
}
