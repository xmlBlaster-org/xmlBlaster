/*------------------------------------------------------------------------------
Name:      Authenticate.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login for clients
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.jutils.log.LogChannel;

import org.xmlBlaster.authentication.plugins.PluginManager;
import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.plugins.I_Subject;
import org.jutils.time.StopWatch;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.DisconnectQosServer;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.engine.qos.ConnectReturnQosServer;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.engine.XmlBlasterImpl;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.runlevel.RunlevelManager;
import org.xmlBlaster.engine.runlevel.I_RunlevelListener;
import org.xmlBlaster.protocol.I_XmlBlaster;
import java.util.*;



/**
 * Authenticate a client via login.
 * <p>
 * The login method serves as a factory for a xmlBlaster.Server Reference
 */
final public class Authenticate implements I_RunlevelListener
{
   final private String ME;

   private final PluginManager plgnLdr;

//   private Hashtable sessions = new Hashtable();

   /** Unique counter to generate IDs */
   private long counter = 1;

   private final Global glob;
   private final LogChannel log;

   /**
    * With this map you can find a client using a sessionId.
    *
    * key   = sessionId A unique identifier
    * value = SessionInfo object, containing all data about a client
    */
   final private Map sessionInfoMap = new HashMap();

   /**
    * With this map you can find a client using his login name.
    *
    * key   = loginName, the unique login name of a client
    * value = SessionInfo object, containing all data about a client
    */
   final private Map loginNameSubjectInfoMap = new HashMap();

   /**
    * For listeners who want to be informed about login/logout
    */
   final private Set clientListenerSet = Collections.synchronizedSet(new HashSet());

   /** The singleton handle for this xmlBlaster server */
   private final I_XmlBlaster xmlBlasterImpl;

   /** My security delegate layer which is exposed to the protocol plugins */
   private final AuthenticateProtector encapsulator;


   /**
    */
   public Authenticate(Global global) throws XmlBlasterException
   {
      this.glob = global;
      this.log = this.glob.getLog("auth");
      this.ME = "Authenticate" + glob.getLogPrefixDashed();

      if (log.CALL) log.call(ME, "Entering constructor");
      this.encapsulator = new AuthenticateProtector(glob, this); // my security layer (delegate)
      
      glob.getRunlevelManager().addRunlevelListener(this);

      plgnLdr = new PluginManager(global);
      plgnLdr.init(this);
      xmlBlasterImpl = new XmlBlasterImpl(this);
   }

   public Global getGlobal()
   {
      return this.glob;
   }

   /**
    * Access the xmlBlaster singleton.
    */
   public I_XmlBlaster getXmlBlaster()
   {
      return xmlBlasterImpl;
   }

   public String login(String loginName, String passwd,
                       String xmlQoS_literal, String secretSessionId)
                          throws XmlBlasterException
   {
      Thread.dumpStack();
      log.error(ME, "login() not implemented");
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "login() not implemented and deprecated");
   }

   /**
    * Use this to create a user and session for internal users only.
    * This method is a security risk never allow external code to call it (there is no
    * passwd needed).
    * Note that only the security instances are created, they are not registered
    * with the Authentication server.
    */
   public SessionInfo unsecureCreateSession(SessionName loginName) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering unsecureCreateSession(" + loginName + ")");
      String secretSessionId = createSessionId(loginName.getLoginName());
      org.xmlBlaster.authentication.plugins.simple.Manager manager = new org.xmlBlaster.authentication.plugins.simple.Manager();
      manager.init(glob, null);
      I_Session session = new org.xmlBlaster.authentication.plugins.simple.Session(manager, secretSessionId);
      org.xmlBlaster.authentication.plugins.I_SecurityQos securityQos = new org.xmlBlaster.authentication.plugins.simple.SecurityQos(this.glob, loginName.getLoginName(), "");
      session.init(securityQos);
      I_Subject subject = session.getSubject();
      SubjectInfo subjectInfo = new SubjectInfo(getGlobal(), this, loginName);
      subjectInfo.toAlive(subject, new CbQueueProperty(getGlobal(), Constants.RELATING_SUBJECT, null));
      org.xmlBlaster.client.qos.ConnectQos connectQos = new org.xmlBlaster.client.qos.ConnectQos(glob);
      connectQos.getSessionQos().setSessionTimeout(0L);  // Lasts forever
      return new SessionInfo(subjectInfo, session, new ConnectQosServer(glob, connectQos.getData()), getGlobal());
   }

   /**
    * Login to xmlBlaster.
    */
   public final ConnectReturnQosServer connect(ConnectQosServer xmlQos) throws XmlBlasterException
   {
      return connect(xmlQos, null);
   }

   /**
    * Login to xmlBlaster.
    *
    * If no secretSessionId==null, the secretSessionId from xmlQoS_literal is used,
    * if this is null as well, we generate one.
    * <p />
    * The given secretSessionId (in the qos) from the client could be from e.g. a2Blaster,
    * and will be used here as is, the a2Blaster plugin verifies it.
    * The extra parameter secretSessionId is the CORBA internal POA session id.
    * <p />
    *
    * @param connectQos  The login/connect QoS, see ConnectQosServer.java
    * @param secretSessionId   The caller (here CORBA-POA protocol driver) may insist to you its own secretSessionId
    */
   public final ConnectReturnQosServer connect(ConnectQosServer connectQos, String secretSessionId) throws XmlBlasterException
   {
      // [1] Try reconnecting with secret sessionId
      try {
         if (log.CALL) log.call(ME, "Entering connect(sessionName=" + connectQos.getSessionName() + ")"); // " secretSessionId=" + secretSessionId + ")");
         if (log.DUMP) log.dump(ME, "ConnectQos=" + connectQos.toXml());

         // Get or create the secretSessionId (we respect a user supplied secretSessionId) ...
         if (secretSessionId == null || secretSessionId.length() < 2) {
            secretSessionId = connectQos.getSessionQos().getSecretSessionId();
            if (secretSessionId != null && secretSessionId.length() >= 2)
               log.info(ME, "Using secretSessionId '" + secretSessionId + "' from ConnectQos");
         }
         if (secretSessionId != null && secretSessionId.length() >= 2) {
            SessionInfo info = getSessionInfo(secretSessionId);
            if (info != null) {  // authentication succeeded
               
               info.updateConnectQos(connectQos);

               ConnectReturnQosServer returnQos = new ConnectReturnQosServer(glob, info.getConnectQos().getData());
               returnQos.getSessionQos().setSecretSessionId(secretSessionId);
               returnQos.getSessionQos().setSessionName(info.getSessionName());
               returnQos.setReconnected(true);
               log.info(ME, "Reconnected with given secretSessionId.");
               return returnQos;
            }
         }
      }
      catch (Throwable e) {
         log.error(ME, "Internal error when trying to reconnect to session " + connectQos.getSessionName() + " with secret session ID: " + e.toString());
         e.printStackTrace();
         throw XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_CONNECTIONFAILURE.toString(), e);
      }

      // [2] Try reconnecting with publicSessionId
      if (connectQos.hasPublicSessionId()) {
         SessionInfo info = getSessionInfo(connectQos.getSessionName());
         if (info != null && !info.isShutdown()) {
            if (connectQos.getSessionQos().reconnectSameClientOnly()) {
               String text = "Only the creator of session " + connectQos.getSessionName().toString() + " may reconnect, access denied.";
               log.warn(ME+".connect()", text);
               throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION_IDENTICALCLIENT,
                         ME+".connect()", text);
            }
            try {
               // Check password as we can't trust the public session ID
               // throws XmlBlasterExceptions if authentication fails
               info.getSecuritySession().verify(connectQos.getSecurityQos());

               String oldSecretSessionId = info.getSecretSessionId();

               if (secretSessionId == null || secretSessionId.length() < 2) {
                  // Keep the old secretSessionId
                  connectQos.getSessionQos().setSecretSessionId(oldSecretSessionId);
               }
               else {
                  // The CORBA driver insists in a new secretSessionId
                  changeSecretSessionId(oldSecretSessionId, secretSessionId);
                  connectQos.getSessionQos().setSecretSessionId(secretSessionId);
               }

               info.updateConnectQos(connectQos);

               ConnectReturnQosServer returnQos = new ConnectReturnQosServer(glob, info.getConnectQos().getData());
               returnQos.getSessionQos().setSessionName(info.getSessionName());
               returnQos.setReconnected(true);
               log.info(ME, "Reconnected with given publicSessionId to '" + info.getSessionName() + "'.");
               return returnQos;
            }
            catch (XmlBlasterException e) {
               log.warn(ME, "Access is denied when trying to reconnect to session " + info.getSessionName() + ": " + e.getMessage());
               throw e; // Thrown if authentication failed
            }
            catch (Throwable e) {
               log.error(ME, "Internal error when trying to reconnect to session " + info.getSessionName() + " with public session ID: " + e.toString());
               e.printStackTrace();
               throw XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_CONNECTIONFAILURE.toString(), e);
            }
         }
      }

      // [3] Generate a secret session ID
      if (secretSessionId == null || secretSessionId.length() < 2) {
         secretSessionId = createSessionId("null" /*subjectCtx.getName()*/);
         connectQos.getSessionQos().setSecretSessionId(secretSessionId); // assure consistency
         if (log.TRACE) log.trace(ME+".connect()", "Empty secretSessionId - generated secretSessionId=" + secretSessionId);
      }

      I_Session sessionCtx = null;
      I_Manager securityMgr = null;
      SessionInfo sessionInfo = null;

      try {
         // Get suitable SecurityManager and context ...
         securityMgr = plgnLdr.getManager(connectQos.getClientPluginType(), connectQos.getClientPluginVersion());
         if (securityMgr == null) {
            log.warn(ME, "Access is denied, there is no security manager configured for this connect QoS: " + connectQos.toXml());
            throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED, ME, "There is no security manager configured with the given connect QoS");
         }
         sessionCtx = securityMgr.reserveSession(secretSessionId);  // allways creates a new I_Session instance
         String securityInfo = sessionCtx.init(connectQos.getSecurityQos()); // throws XmlBlasterExceptions if authentication fails
         if (securityInfo != null && securityInfo.length() > 1) log.warn(ME, "Ignoring security info: " + securityInfo);
         // Now the client is authenticated
      }
      catch (XmlBlasterException e) {
         // If access is denied: cleanup resources
         log.warn(ME, "Access is denied: " + e.getMessage());
         if (securityMgr != null) securityMgr.releaseSession(secretSessionId, null);  // allways creates a new I_Session instance
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         log.error(ME, "PANIC: Access is denied: " + e.getMessage());
         // On error: cleanup resources
         securityMgr.releaseSession(secretSessionId, null);  // allways creates a new I_Session instance
         throw XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_CONNECTIONFAILURE.toString(), e);
      }

      if (log.TRACE) log.trace(ME, "Checking if user is known ...");
      SubjectInfo subjectInfo = null;
      try {
         // Check if user is known, otherwise create an entry ...
         I_Subject subjectCtx = sessionCtx.getSubject();
         SessionName subjectName = new SessionName(glob, connectQos.getSessionName(), 0L); // Force to be of type subject (no public session ID)

         synchronized(this.loginNameSubjectInfoMap) { // Protect against two simultaneous logins
            subjectInfo = (SubjectInfo)this.loginNameSubjectInfoMap.get(subjectName.getLoginName());
            //log.error(ME, "DEBUG ONLY, subjectName=" + subjectName.toString() + " loginName=" + subjectName.getLoginName() + " state=" + toXml());
            if (subjectInfo == null) {
               subjectInfo = new SubjectInfo(getGlobal(), this, subjectName); // registers itself in loginNameSubjectInfoMap
            }
         } // synchronized(this.loginNameSubjectInfoMap)

         // This sync gap is no problem for SubjectInfo if somebody else does a subjectInfo.shutdown()
         // since the subjectInfo does a transition from DEAD to ALIVE below

         synchronized(subjectInfo) {
            if (subjectInfo.isAlive()) {
               // TODO: Reconfigure subject queue only when queue relating='subject' was used
               subjectInfo.setCbQueueProperty(connectQos.getSubjectQueueProperty()); // overwrites only if not null
            }
            else {
               subjectInfo.toAlive(subjectCtx, connectQos.getSubjectQueueProperty());
            }

            // Check if client does a relogin and wants to destroy old sessions
            if (connectQos.getSessionQos().clearSessions() == true && subjectInfo.getNumSessions() > 0) {
               SessionInfo[] sessions = subjectInfo.getSessions();
               for (int i=0; i<sessions.length; i++ ) {
                  SessionInfo si = sessions[i];
                  log.warn(ME, "Destroying session '" + si.getSecretSessionId() + "' of user '" + subjectInfo.getSubjectName() + "' as requested by client");
                  disconnect(si.getSecretSessionId(), (String)null);
               }
               // will create a new SubjectInfo instance (which should be OK)
               return connect(connectQos, secretSessionId);
            }

            if (log.TRACE) log.trace(ME, "Creating sessionInfo for " + subjectInfo.getId());

            // Create the new sessionInfo instance
            sessionInfo = new SessionInfo(subjectInfo, sessionCtx, connectQos, getGlobal());
            synchronized(this.sessionInfoMap) {
               this.sessionInfoMap.put(secretSessionId, sessionInfo);
            }
            subjectInfo.notifyAboutLogin(sessionInfo);

            fireClientEvent(sessionInfo, true);
         } // synchronized(subjectInfo)

         // --- compose an answer -----------------------------------------------
         ConnectReturnQosServer returnQos = new ConnectReturnQosServer(glob, connectQos.getData());
         returnQos.getSessionQos().setSecretSessionId(secretSessionId); // securityInfo is not coded yet !
         returnQos.getSessionQos().setSessionName(sessionInfo.getSessionName());

         // Now some nice logging ...
         StringBuffer sb = new StringBuffer(256);
         sb.append("Successful login for client ").append(sessionInfo.getSessionName().getAbsoluteName());
         sb.append(", session");
         sb.append(((connectQos.getSessionTimeout() > 0L) ?
                         " expires after"+org.jutils.time.TimeHelper.millisToNice(connectQos.getSessionTimeout()) :
                         " lasts forever"));
         sb.append(", ").append(subjectInfo.getNumSessions()).append(" of ");
         sb.append(connectQos.getMaxSessions()).append(" sessions are in use.");
         log.info(ME, sb.toString());
         if (log.DUMP) log.dump(ME, toXml());
         if (log.DUMP) log.dump(ME, "Returned QoS:\n" + returnQos.toXml());
         if (log.CALL) log.call(ME, "Leaving connect()");

         return returnQos;
      }
      catch (XmlBlasterException e) {
         String id = (sessionInfo != null) ? sessionInfo.getId() : ((subjectInfo != null) ? subjectInfo.getId() : "");
         log.warn(ME, "Connection for " + id + " failed: " + e.getMessage());
         //e.printStackTrace(); Sometimes nice, often not - what to do?
         try {
            disconnect(secretSessionId, (String)null); // cleanup
         }
         catch (Throwable th) {
            log.warn(ME, "Ignoring problems during cleanup of exception '" + e.getMessage() + "':" + th.getMessage());
         }
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         log.error(ME, "Internal error: Connect failed: " + e.getMessage());
         try {
            disconnect(secretSessionId, (String)null); // cleanup
         }
         catch (Throwable th) {
            log.warn(ME, "Ignoring problems during cleanup of exception '" + e.getMessage() + "':" + th.getMessage());
         }
         throw XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_CONNECTIONFAILURE.toString(), e);
      }
   }


   public final void disconnect(String secretSessionId, String qos_literal) throws XmlBlasterException
   {
      try {
         if (log.CALL) log.call(ME, "Entering disconnect()");
         //Thread.currentThread().dumpStack();
         if (log.DUMP) log.dump(ME, toXml().toString());
         if (secretSessionId == null) {
            throw new IllegalArgumentException("disconnect() failed, the given secretSessionId is null");
         }

         I_Manager securityMgr = plgnLdr.getManager(secretSessionId);
         I_Session sessionSecCtx = securityMgr.getSessionById(secretSessionId);
         if (sessionSecCtx == null) {
            throw new XmlBlasterException(this.glob, ErrorCode.USER_NOT_CONNECTED, ME + " Authenticate.disconnect", "You are not connected, your secretSessionId is invalid.");
         }
         try {
            securityMgr.releaseSession(secretSessionId, sessionSecCtx.importMessage(qos_literal));
         }
         catch(Throwable e) {
            log.warn(ME, "Ignoring importMessage() problems, we continue to cleanup resources: " + e.getMessage());
         }

         SessionInfo sessionInfo = getSessionInfo(secretSessionId);
         if (sessionInfo.getCbQueueNumMsgs() > 0) {
            long sleep = glob.getProperty().get("cb.disconnect.pending.sleep", 1000L); // TODO: allow configuration over DisconnectQos
            log.info(ME, "Sleeping cb.disconnect.pending.sleep=" + sleep + " millis in disconnect(" + sessionInfo.getId() + ") to deliver " + sessionInfo.getCbQueueNumMsgs() + " pending messages ...");
            try { Thread.sleep(sleep); } catch( InterruptedException i) {}
         }

         SubjectInfo subjectInfo = sessionInfo.getSubjectInfo();

         DisconnectQosServer disconnectQos = new DisconnectQosServer(glob, qos_literal);

         boolean forceShutdownEvenIfEntriesExist = false;
         
         resetSessionInfo(sessionInfo, disconnectQos.deleteSubjectQueue(), forceShutdownEvenIfEntriesExist);

         if (disconnectQos.clearSessions() == true && subjectInfo.getNumSessions() > 0) {
            SessionInfo[] sessions = subjectInfo.getSessions();
            for (int i=0; i<sessions.length; i++ ) {
               SessionInfo si = sessions[i];
               log.warn(ME, "Destroying session '" + si.getSecretSessionId() + "' of user '" + subjectInfo.getSubjectName() + "' as requested by client");
               disconnect(si.getSecretSessionId(), null);
            }
         }

         if (log.DUMP) log.dump(ME, toXml().toString());
         if (log.CALL) log.call(ME, "Leaving disconnect()");
      }
      catch (XmlBlasterException e) {
         if (log.TRACE) log.trace(ME, "disconnect failed: " + e.getMessage());
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         log.error(ME, "Internal error: Disconnect failed: " + e.getMessage());
         throw XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_DISCONNECT.toString(), e);
      }
   }

   /**
    * Access a subjectInfo with the unique login name.
    * <p />
    * If the client is yet unknown, there will be instantiated a dummy SubjectInfo object
    * @return the SubjectInfo object
    */
   public final SubjectInfo getOrCreateSubjectInfoByName(SessionName subjectName) throws XmlBlasterException
   {
      if (subjectName == null || subjectName.getLoginName().length() < 2) {
         log.warn(ME + ".InvalidClientName", "Given loginName is null");
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".InvalidClientName", "Your given loginName is null or shorter 2 chars, loginName rejected");
      }

      SubjectInfo subjectInfo = getSubjectInfoByName(subjectName);
      if (subjectInfo == null) {
         // strip nodeId, strip pubSessionId
         SessionName name = new SessionName(glob, glob.getNodeId(), subjectName.getLoginName());
         subjectInfo = new SubjectInfo(getGlobal(), this, name);
         subjectInfo.toAlive(null, new CbQueueProperty(getGlobal(), Constants.RELATING_SUBJECT, null));
      }

      return subjectInfo;
   }

   /**
    * Add a new SubjectInfo instance. 
    */
   void addLoginName(SubjectInfo subjectInfo) {
      Object oldInstance = null;
      synchronized(this.loginNameSubjectInfoMap) {
         oldInstance = this.loginNameSubjectInfoMap.put(subjectInfo.getLoginName(), subjectInfo);
      }

      if (oldInstance != null) {
         SubjectInfo old = (SubjectInfo)oldInstance;
         if (old != subjectInfo) {
            log.error(ME, "Internal problem: didn't expect two different SubjectInfo old=" + old.toXml() + " new=" + subjectInfo.toXml());
         }
         return;
      }

      try {
         glob.getRequestBroker().updateInternalUserList();
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Publishing internal user list failed: " + e.getMessage());
      }
   }

   /**
    * Remove a SubjectInfo instance. 
    */
   void removeLoginName(SubjectInfo subjectInfo) {
      Object entry = null;
      synchronized(this.loginNameSubjectInfoMap) {
         entry = this.loginNameSubjectInfoMap.remove(subjectInfo.getLoginName());
      }
      if (entry == null) {
         return;
      }
      try {
         glob.getRequestBroker().updateInternalUserList();
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Publishing internal user list failed: " + e.getMessage());
      }
   }

   public int getNumSubjects() {
      return this.loginNameSubjectInfoMap.size();
   }

   /**
    * Access a subjectInfo with the unique login name
    * @return the SubjectInfo object<br />
    *         null if not found
    */
   public final SubjectInfo getSubjectInfoByName(SessionName subjectName) {
      synchronized(this.loginNameSubjectInfoMap) {
         return (SubjectInfo)this.loginNameSubjectInfoMap.get(subjectName.getLoginName());
      }
   }

   /**
    * Replace the old by the new session id
    */
   public final void changeSecretSessionId(String oldSessionId, String newSessionId) throws XmlBlasterException {
      synchronized(this.sessionInfoMap) {
         SessionInfo sessionInfo = (SessionInfo)this.sessionInfoMap.get(oldSessionId);
         if (sessionInfo == null) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME+".changeSecretSessionId()", "Couldn't lookup secretSessionId.");
         }
         if (this.sessionInfoMap.get(newSessionId) != null) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME+".changeSecretSessionId()", "The new secretSessionId is already in use.");
         }
         this.sessionInfoMap.put(newSessionId, sessionInfo);
         this.sessionInfoMap.remove(oldSessionId);

         sessionInfo.getSecuritySession().changeSecretSessionId(newSessionId);
         sessionInfo.getConnectQos().getSessionQos().setSecretSessionId(newSessionId);
      }
   }

   /**
    * Access a sessionInfo with the unique secretSessionId. 
    * <p />
    * @return the SessionInfo object or null if not known
    */
   private final SessionInfo getSessionInfo(String secretSessionId) {
      synchronized(this.sessionInfoMap) {
         return (SessionInfo)this.sessionInfoMap.get(secretSessionId);
      }
   }

   /**
    * Returns a current snapshot of all sessions
    */
   private final SessionInfo[] getSessionInfoArr() {
      synchronized(this.sessionInfoMap) {
         return (SessionInfo[])this.sessionInfoMap.values().toArray((new SessionInfo[this.sessionInfoMap.size()]));
      }
   }

   /**
    * Find a session by its login name and pubSessionId or return null if not found
    */
   public final SessionInfo getSessionInfo(SessionName sessionName) {
      SubjectInfo subjectInfo = getSubjectInfoByName(sessionName);
      if (subjectInfo == null) {
         return null;
      }
      return subjectInfo.getSessionInfo(sessionName);
   }

   public boolean sessionExists(String secretSessionId) {
      synchronized(this.sessionInfoMap) {
         return this.sessionInfoMap.containsKey(secretSessionId);
      }
   }

   /**
    * Logout of a client.
    * <p>
    * @exception XmlBlasterException If client is unknown
    */
   public final void logout(String secretSessionId) throws XmlBlasterException
   {
      log.error(ME, "logout not implemented");
      throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME + ".logout not implemented");
   }


   /**
    * @param sessionInfo 
    * @param clearQueue Shall the message queue of the client be destroyed as well on last session logout?
    * @param forceShutdownEvenIfEntriesExist on last session
    */
   private void resetSessionInfo(SessionInfo sessionInfo, boolean clearQueue, boolean forceShutdownEvenIfEntriesExist) throws XmlBlasterException
   {
      String secretSessionId = sessionInfo.getSecretSessionId();
      Object obj;
      synchronized(this.sessionInfoMap) {
         obj = this.sessionInfoMap.remove(secretSessionId);
      }

      if (obj == null) {
         log.warn(ME, "Sorry, '" + sessionInfo.getId() + "' is not known, no logout.");
         throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED, ME,
                   "Client '" + sessionInfo.getId() + "' is not known, disconnect is not possible.");
      }

      log.info(ME, "Disconnecting client " + sessionInfo.getSessionName() + ", instanceId=" + sessionInfo.getInstanceId() + ", secretSessionId=" + secretSessionId);

      I_Session oldSessionCtx = sessionInfo.getSecuritySession();
      oldSessionCtx.getManager().releaseSession(secretSessionId, null);

      fireClientEvent(sessionInfo, false); // informs all I_ClientListener

      SubjectInfo subjectInfo = sessionInfo.getSubjectInfo();
      subjectInfo.notifyAboutLogout(sessionInfo.getId(), clearQueue, forceShutdownEvenIfEntriesExist);
      //if (subjectInfo.isShutdown()) {
      //   subjectInfo = null; // Give GC a hint
      //}

      sessionInfo.shutdown();
      sessionInfo = null;
      log.info(ME, "loginNameSubjectInfoMap has " + getNumSubjects() +
                   " entries and sessionInfoMap has " + this.sessionInfoMap.size() + " entries");
   }


   /**
    *  Generate a unique (and secret) resource ID <br>
    *
    *  @param loginName
    *  @return unique ID
    *  @exception XmlBlasterException random generator
    */
   private String createSessionId(String loginName) throws XmlBlasterException
   {
      try {
         String ip = glob.getLocalIP();

         // This is a real random, but probably not necessary here:
         // Random random = new java.security.SecureRandom();
         java.util.Random ran = new java.util.Random();  // is more or less currentTimeMillis

         // Note: We should include the process ID from this JVM on this host to be granted unique

         // secretSessionId:<IP-Address>-<LoginName>-<TimestampMilliSec>-<RandomNumber>-<LocalCounter>
         StringBuffer buf = new StringBuffer(512);

         buf.append(Constants.SESSIONID_PREFIX).append(ip).append("-").append(loginName).append("-");
         buf.append(System.currentTimeMillis()).append("-").append(ran.nextInt()).append("-").append((counter++));

         String secretSessionId = buf.toString();
         if (log.TRACE) log.trace(ME, "Created secretSessionId='" + secretSessionId + "'");
         return secretSessionId;
      }
      catch (Exception e) {
         String text = "Can't generate a unique secretSessionId: " + e.getMessage();
         log.error(ME, text);
         throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED, ME, text);
      }
   }


   /**
    * Used to fire an event if a client does a login / logout
    */
   private void fireClientEvent(SessionInfo sessionInfo, boolean login) throws XmlBlasterException
   {
      synchronized (clientListenerSet) {
         if (clientListenerSet.size() == 0)
            return;

         ClientEvent event = new ClientEvent(sessionInfo);
         Iterator iterator = clientListenerSet.iterator();

         while (iterator.hasNext()) {
            I_ClientListener cli = (I_ClientListener)iterator.next();
            if (login)
               cli.sessionAdded(event);
            else
               cli.sessionRemoved(event);
         }

         event = null;
      }
   }


   /**
    * Use this method to check a clients authentication.
    * <p>
    * This method is called from an invoked xmlBlaster-server
    * method (like subscribe()), using the delivered secretSessionId
    *
    * @return SessionInfo - if the client is OK otherwise an exception is thrown (returns never null)
    * @exception XmlBlasterException Access denied
    */
   public SessionInfo check(String secretSessionId) throws XmlBlasterException
   {
      // even the corba client should get a communication exception when the server is shutting down
      // (before this change he was getting "access denided" since the sessions were already killed).
      /* Removed check, Marcel 2003-03-26: This should be handled by loading specific plugins
         in xmlBlasterPlugin.xml
      if (glob.getRunlevelManager().getCurrentRunlevel() < RunlevelManager.RUNLEVEL_STANDBY) {
         String text = "The run level " + RunlevelManager.toRunlevelStr(glob.getRunlevelManager().getCurrentRunlevel()) +
                       " of xmlBlaster is not handling any communication anymore. " + glob.getId() + ".";
         log.warn(ME+".communication.noconnection", text);
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, text);
      }
      */

      StopWatch stop=null; if (log.TIME) stop = new StopWatch();

      Object obj = null;
      synchronized(this.sessionInfoMap) {
         obj = this.sessionInfoMap.get(secretSessionId);
      }

      if (obj == null) {
         log.warn(ME+".AccessDenied", "SessionId '" + secretSessionId + "' is invalid, no access to xmlBlaster.");
         throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED, ME, "Your secretSessionId is invalid, no access to " + glob.getId() + ".");
      }
      SessionInfo sessionInfo = (SessionInfo)obj;

      sessionInfo.refreshSession(); // touch the session, expiry timer is spaned

      if (log.TIME) log.time(ME, "Elapsed time in check()" + stop.nice());
      if (log.TRACE) log.trace(ME, "Succesfully granted access for " + sessionInfo.toString());

      return sessionInfo;
   }


   /**
    * Adds the specified client listener to receive login/logout events.
    * <p />
    * This listener needs to implement the I_ClientListener interface.
    */
   public void addClientListener(I_ClientListener l) {
      if (l == null) {
         return;
      }
      synchronized (clientListenerSet) {
         clientListenerSet.add(l);
      }
   }


   /**
    * Removes the specified listener
    */
   public synchronized void removeClientListener(I_ClientListener l) {
      if (l == null) {
         return;
      }
      synchronized (clientListenerSet) {
         clientListenerSet.remove(l);
      }
   }

   public int getMaxSubjects() {
      return Integer.MAX_VALUE; // TODO: allow to limit max number of different clients (or login sessions?)
   }

   /**
    * Access a list of login names e.g. "joe,jack,averell,william"
    * @return An empty string if no clients available
    */
   public String getSubjectList() {
      int numSubjects = getNumSubjects();
      if (numSubjects < 1)
         return "";
      StringBuffer sb = new StringBuffer(numSubjects * 30);
      synchronized(this.loginNameSubjectInfoMap) {
         Iterator iterator = this.loginNameSubjectInfoMap.values().iterator();
         while (iterator.hasNext()) {
            if (sb.length() > 0)
               sb.append(",");
            SubjectInfo subjectInfo = (SubjectInfo)iterator.next();
            sb.append(subjectInfo.getLoginName());
         }
      }
      return sb.toString();
   }

   /**
    * Enforced by I_RunlevelListener
    */
   public String getName() {
      return this.ME;
   }

   /**
    * Invoked on run level change, see RunlevelManager.RUNLEVEL_HALTED and RunlevelManager.RUNLEVEL_RUNNING
    * <p />
    * Enforced by I_RunlevelListener
    */
   public void runlevelChange(int from, int to, boolean force) throws org.xmlBlaster.util.XmlBlasterException {
      //if (log.CALL) log.call(ME, "Changing from run level=" + from + " to level=" + to + " with force=" + force);
      if (to == from)
         return;

      if (to > from) { // startup
         if (to == RunlevelManager.RUNLEVEL_CLEANUP_PRE) {
         }
      }

      if (to < from) { // shutdown
         if (to == RunlevelManager.RUNLEVEL_HALTED) {
            if (log.TRACE) log.trace(ME, "Killing " + this.sessionInfoMap.size() + " login sessions");
            SessionInfo[] sessionInfoArr = getSessionInfoArr();
            for (int ii=0; ii<sessionInfoArr.length; ii++) {
               try {
                  boolean clearQueue = false;
                  boolean forceShutdownEvenIfEntriesExist = true;
                  resetSessionInfo(sessionInfoArr[ii], clearQueue, forceShutdownEvenIfEntriesExist);
               }
               catch (Throwable e) {
                  log.error(ME, "Problem on session shutdown, we ignore it: " + e.getMessage());
                  if (!(e instanceof XmlBlasterException)) e.printStackTrace();
               }
            } // for
         }
      }
   }



/*
   private I_SessionPersistencePlugin getSessionPersistencePlugin() {
      return ((I_SessionPersistencePlugin)this.glob.getPluginRegistry().getPlugin(I_SessionPersistencePlugin.ID));
   }

   private void persistenceConnect(SessionInfo info) throws XmlBlasterException {
      I_SessionPersistencePlugin plugin = getSessionPersistencePlugin();
      if (plugin == null) {
         this.log.error(ME, "persistenceConnect: the session persistence plugin is not registered (yet): can't make connection persitent");
         Thread.dumpStack();
         return;
      }
      ClientEvent event = new ClientEvent(info);
      plugin.sessionAdded(event);
   }

   private void persistenceDisConnect(SessionInfo info) throws XmlBlasterException {
      I_SessionPersistencePlugin plugin = getSessionPersistencePlugin();
      if (plugin == null) {
         this.log.error(ME, "persistenceConnect: the session persistence plugin is not registered (yet): can't make connection persitent");
         Thread.dumpStack();
         return;
      }
      ClientEvent event = new ClientEvent(info);
      plugin.sessionRemoved(event);
   }
*/
   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of Authenticate as a XML ASCII string
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of Authenticate as a XML ASCII string
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(1000);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      log.info(ME, "Client maps, sessionInfoMap.size()=" + this.sessionInfoMap.size() +
                   " and loginNameSubjectInfoMap.size()=" + getNumSubjects());
      synchronized(this.loginNameSubjectInfoMap) {
         Iterator iterator = this.loginNameSubjectInfoMap.values().iterator();

         sb.append(offset).append("<Authenticate>");
         while (iterator.hasNext()) {
            SubjectInfo subjectInfo = (SubjectInfo)iterator.next();
            sb.append(subjectInfo.toXml(extraOffset+Constants.INDENT));
         }
         sb.append(offset).append("</Authenticate>\n");
      }

      return sb.toString();
   }

}
