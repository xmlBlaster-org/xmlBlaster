/*------------------------------------------------------------------------------
Name:      Authenticate.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login for clients
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.jutils.log.LogChannel;

import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.plugins.I_Subject;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.authentication.plugins.PluginManager;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.jutils.time.StopWatch;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.util.dispatch.DeliveryWorkerPool;
import org.xmlBlaster.engine.helper.CbQueueProperty;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.XmlBlasterImpl;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.RunlevelManager;
import org.xmlBlaster.engine.I_RunlevelListener;
import org.xmlBlaster.protocol.I_XmlBlaster;
import java.util.*;



/**
 * Authenticate a client via login.
 * <p>
 * The login method serves as a factory for a xmlBlaster.Server Reference
 */
final public class Authenticate implements I_Authenticate, I_RunlevelListener
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
   final private Map sessionInfoMap = Collections.synchronizedMap(new HashMap());

   /**
    * With this map you can find a client using his login name.
    *
    * key   = loginName, the unique login name of a client
    * value = SessionInfo object, containing all data about a client
    */
   final private Map loginNameSubjectInfoMap = Collections.synchronizedMap(new HashMap());

   /**
    * For listeners who want to be informed about login/logout
    */
   final private Set clientListenerSet = Collections.synchronizedSet(new HashSet());

   /** The singleton handle for this xmlBlaster server */
   private final I_XmlBlaster xmlBlasterImpl;

   /**
    */
   public Authenticate(Global global) throws XmlBlasterException
   {
      this.glob = global;
      this.log = this.glob.getLog("auth");
      this.ME = "Authenticate" + glob.getLogPrefixDashed();

      if (log.CALL) log.call(ME, "Entering constructor");
      this.glob.setAuthenticate(this);
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
      Thread.currentThread().dumpStack();
      log.error(ME, "login() not implemented");
      throw new XmlBlasterException(ME, "login() not implemented");
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
      SubjectInfo subjectInfo = new SubjectInfo(getGlobal(), subject, new CbQueueProperty(getGlobal(), Constants.RELATING_SUBJECT, null));
      ConnectQos connectQos = new ConnectQos(glob);
      connectQos.setSessionTimeout(0L);  // Lasts forever
      return new SessionInfo(subjectInfo, session, connectQos, getGlobal());
   }

   /**
    * Login to xmlBlaster.
    */
   public final ConnectReturnQos connect(ConnectQos xmlQos) throws XmlBlasterException
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
    * @param connectQos  The login/connect QoS, see ConnectQos.java
    * @param secretSessionId   The caller (here CORBA-POA protocol driver) may insist to you its own secretSessionId
    */
   public final ConnectReturnQos connect(ConnectQos connectQos, String secretSessionId) throws XmlBlasterException
   {
      try {
         if (log.CALL) log.call(ME, "Entering connect(secretSessionId=" + secretSessionId + ")");
         if (log.DUMP) log.dump(ME, "ConnectQos=" + connectQos.toXml());

         // Get or create the secretSessionId (we respect a user supplied secretSessionId) ...
         if (secretSessionId == null) {
            secretSessionId = connectQos.getSessionId();
            if (secretSessionId != null && secretSessionId.length() >= 2)
               log.info(ME, "Using secretSessionId '" + secretSessionId + "' from ConnectQos");
         }
         if (secretSessionId == null || secretSessionId.length() < 2) {
            secretSessionId = createSessionId("null" /*subjectCtx.getName()*/);
            connectQos.setSessionId(secretSessionId); // assure consistency
            if (log.TRACE) log.trace(ME+".connect()", "Empty secretSessionId - generated secretSessionId=" + secretSessionId);
         }
         else {
            SessionInfo info = getSessionInfo(secretSessionId);
            if (info != null) {
               ConnectReturnQos returnQos = new ConnectReturnQos(glob, connectQos);
               returnQos.setSessionId(secretSessionId);
               log.info(ME, "Reconnecting with given secretSessionId, using QoS from first login");
               return returnQos;
            }
         }
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_CONNECTIONFAILURE.toString(), e);
      }

      I_Session sessionCtx = null;
      I_Manager securityMgr = null;
      SessionInfo sessionInfo = null;

      try {
         // Get suitable SecurityManager and context ...
         securityMgr = plgnLdr.getManager(connectQos.getSecurityPluginType(), connectQos.getSecurityPluginVersion());
         sessionCtx = securityMgr.reserveSession(secretSessionId);  // allways creates a new I_Session instance
         String securityInfo = sessionCtx.init(connectQos.getSecurityQos()); // throws XmlBlasterExceptions if authentication fails
         if (securityInfo != null && securityInfo.length() > 1) log.warn(ME, "Ignoring security info: " + securityInfo);
         // Now the client is authenticated
      }
      catch (XmlBlasterException e) {
         // If access is denied: cleanup resources
         if (log.TRACE) log.trace(ME, "Access is denied: " + e.getMessage());
         securityMgr.releaseSession(secretSessionId, null);  // allways creates a new I_Session instance
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         if (log.TRACE) log.trace(ME, "PANIC: Access is denied: " + e.getMessage());
         // On error: cleanup resources
         securityMgr.releaseSession(secretSessionId, null);  // allways creates a new I_Session instance
         throw XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_CONNECTIONFAILURE.toString(), e);
      }

      if (log.TRACE) log.trace(ME, "Checking if user is known ...");
      SubjectInfo subjectInfo = null;
      try {
         // Check if user is known, otherwise create an entry ...
         I_Subject subjectCtx = sessionCtx.getSubject();
         SessionName subjectName = new SessionName(glob, subjectCtx.getName());
         subjectInfo = getSubjectInfoByName(subjectName);
         if (subjectInfo == null) {
            subjectInfo = new SubjectInfo(getGlobal(), subjectCtx, connectQos.getSubjectCbQueueProperty());
            loginNameSubjectInfoMap.put(subjectInfo.getLoginName(), subjectInfo);
         }
         else  // TODO: Reconfigure subject queue only when queue relating='subject' was used
            subjectInfo.setCbQueueProperty(connectQos.getSubjectCbQueueProperty()); // overwrites only if not null

         /*
         // Check if client does a relogin and may only login once ...
         if (connectQos.getMaxSessions() == 1 && subjectInfo.isLoggedIn()) {
            log.warn(ME+".AlreadyLoggedIn", "Client " + subjectCtx.getName() + " is already logged in. Your login session will be re-initialized.");
            sessionInfo = subjectInfo.getFirstSession();
            if (sessionInfo!=null) {
               resetSessionInfo(sessionInfo.getSessionId(), false);
            }
            // allowing re-login: if the client crashed without proper logout, she should
            // be allowed to login again, so - first logout the last session (but keep messages in client queue)
            // We need to clean up sessionInfo, usually the callback reference is another one, etc.
         }
         */
         if (connectQos.clearSessions() == true && subjectInfo.getNumSessions() > 0) {
            SessionInfo[] sessions = subjectInfo.getSessions();
            for (int i=0; i<sessions.length; i++ ) {
               SessionInfo si = sessions[i];
               log.warn(ME, "Destroying session '" + si.getSessionId() + "' of user '" + subjectInfo.getSubjectName() + "' as requested by client");
               disconnect(si.getSessionId(), (String)null);
            }
            return connect(connectQos, secretSessionId);
         }

         if (log.TRACE) log.trace(ME, "Creating sessionInfo for " + subjectInfo.getId());

         sessionInfo = new SessionInfo(subjectInfo, sessionCtx, connectQos, getGlobal());
         synchronized(sessionInfoMap) {
            sessionInfoMap.put(secretSessionId, sessionInfo);
         }
         subjectInfo.notifyAboutLogin(sessionInfo);

         fireClientEvent(sessionInfo, true);

         // --- compose an answer -----------------------------------------------
         ConnectReturnQos returnQos = new ConnectReturnQos(glob, connectQos);
         returnQos.setSessionId(secretSessionId); // securityInfo is not coded yet !
         returnQos.setSessionName(sessionInfo.getSessionName());

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
         disconnect(secretSessionId, (String)null); // cleanup
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         log.error(ME, "Internal error: Connect failed: " + e.getMessage());
         disconnect(secretSessionId, (String)null); // cleanup
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
            throw new XmlBlasterException("Authenticate.disconnect", "You are not connected, your secretSessionId is invalid.");
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
            try { Thread.currentThread().sleep(sleep); } catch( InterruptedException i) {}
         }

         SubjectInfo subjectInfo = sessionInfo.getSubjectInfo();

         DisconnectQos disconnectQos = new DisconnectQos(qos_literal);

         resetSessionInfo(secretSessionId, disconnectQos.deleteSubjectQueue());

         if (disconnectQos.clearSessions() == true && subjectInfo.getNumSessions() > 0) {
            SessionInfo[] sessions = subjectInfo.getSessions();
            for (int i=0; i<sessions.length; i++ ) {
               SessionInfo si = sessions[i];
               log.warn(ME, "Destroying session '" + si.getSessionId() + "' of user '" + subjectInfo.getSubjectName() + "' as requested by client");
               disconnect(si.getSessionId(), null);
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
         throw new XmlBlasterException(ME + ".InvalidClientName", "Your given loginName is null or shorter 2 chars, loginName rejected");
      }

      SubjectInfo subjectInfo = getSubjectInfoByName(subjectName);
      if (subjectInfo == null) {
         subjectInfo = new SubjectInfo(getGlobal(), subjectName.getRelativeName());
         loginNameSubjectInfoMap.put(subjectName.getLoginName(), subjectInfo);
      }

      return subjectInfo;
   }


   /**
    * Access a sessionInfo with the unique secretSessionId. 
    * <p />
    * @return the SessionInfo object or null if not known
    */
   public final SessionInfo getSessionInfo(String secretSessionId) {
      synchronized(sessionInfoMap) {
         return (SessionInfo)sessionInfoMap.get(secretSessionId);
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
      synchronized(sessionInfoMap) {
         return sessionInfoMap.get(secretSessionId) != null;
      }
   }

   /**
    * Access a subjectInfo with the unique login name
    * @return the SubjectInfo object<br />
    *         null if not found
    */
   public final SubjectInfo getSubjectInfoByName(SessionName subjectName) {
      return (SubjectInfo)loginNameSubjectInfoMap.get(subjectName.getLoginName());
   }


   /**
    * Logout of a client.
    * <p>
    * @exception XmlBlasterException If client is unknown
    */
   public final void logout(String secretSessionId) throws XmlBlasterException
   {
      log.error(ME, "logout not implemented");
      throw new XmlBlasterException(ME, "logout not implemented");
   }


   /**
    * @param xmlServer xmlBlaster CORBA handle
    * @param clearQueue Shall the message queue of the client be destroyed as well?
    */
   private void resetSessionInfo(String secretSessionId, boolean clearQueue) throws XmlBlasterException
   {
      Object obj;
      synchronized(sessionInfoMap) {
         obj = sessionInfoMap.remove(secretSessionId);
      }

      if (obj == null) {
         log.error(ME+".Unknown", "Sorry, you are not known, no logout");
         throw new XmlBlasterException(ME+".Unknown", "Sorry, you are not known, no logout");
      }

      SessionInfo sessionInfo = (SessionInfo)obj;

      log.info(ME, "Disconnecting client " + sessionInfo.getSessionName() + ", instanceId=" + sessionInfo.getInstanceId() + ", secretSessionId=" + secretSessionId);

      I_Session oldSessionCtx = sessionInfo.getSecuritySession();
      oldSessionCtx.getManager().releaseSession(secretSessionId, null);

      fireClientEvent(sessionInfo, false); // informs all I_ClientListener

      SubjectInfo subjectInfo = sessionInfo.getSubjectInfo();
      subjectInfo.notifyAboutLogout(secretSessionId, true);

      if (!subjectInfo.isLoggedIn()) {
         if (clearQueue || subjectInfo.getSubjectQueue().getNumOfEntries() < 1) {
            if (subjectInfo.getSubjectQueue().getNumOfEntries() < 1)
               log.info(ME, "Destroying SubjectInfo " + subjectInfo.getSubjectName() + ". Nobody is logged in and no queue entries available");
            else
               log.warn(ME, "Destroying SubjectInfo " + subjectInfo.getSubjectName() + " as clearQueue is set to true. Lost " + subjectInfo.getSubjectQueue().getNumOfEntries() + " messages");
            loginNameSubjectInfoMap.remove(subjectInfo.getLoginName());
            subjectInfo.shutdown();
            subjectInfo = null;
         }
      }

      sessionInfo.shutdown();
      sessionInfo = null;
      log.info(ME, "loginNameSubjectInfoMap has " + loginNameSubjectInfoMap.size() + " entries and sessionInfoMap has " + sessionInfoMap.size() + " entries");
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
         throw new XmlBlasterException("NoSessionId", text);
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
      StopWatch stop=null; if (log.TIME) stop = new StopWatch();

      Object obj = null;
      synchronized(sessionInfoMap) {
         obj = sessionInfoMap.get(secretSessionId);
      }

      if (obj == null) {
         log.warn(ME+".AccessDenied", "SessionId '" + secretSessionId + "' is invalid, no access to xmlBlaster.");
         throw new XmlBlasterException("AccessDenied", "Your secretSessionId is invalid, no access to " + glob.getId() + ".");
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

   public int getNumSubjects() {
      return loginNameSubjectInfoMap.size();
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
      Iterator iterator = loginNameSubjectInfoMap.values().iterator();
      while (iterator.hasNext()) {
         if (sb.length() > 0)
            sb.append(",");
         SubjectInfo subjectInfo = (SubjectInfo)iterator.next();
         sb.append(subjectInfo.getLoginName());
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
            if (log.TRACE) log.trace(ME, "Killing " + sessionInfoMap.size() + " login sessions");
            Object[] objs = null;
            synchronized(sessionInfoMap) {
               objs = sessionInfoMap.values().toArray();
            }
            for (int ii=0; objs!=null && ii<objs.length; ii++) {
               try {
                  boolean clearQueue = true;
                  SessionInfo sessionInfo = (SessionInfo)objs[ii];
                  resetSessionInfo(sessionInfo.getSessionId(), clearQueue);
                  objs[ii] = null;
               }
               catch (Throwable e) {
                  log.error(ME, "Problem on session shutdown, we ignore it: " + e.getMessage());
                  if (!(e instanceof XmlBlasterException)) e.printStackTrace();
               }
            }
         }
      }
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of Authenticate as a XML ASCII string
    */
   public final String toXml() throws XmlBlasterException {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of Authenticate as a XML ASCII string
    */
   public final String toXml(String extraOffset) throws XmlBlasterException {
      StringBuffer sb = new StringBuffer(1000);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      log.info(ME, "Client maps, sessionInfoMap.size()=" + sessionInfoMap.size() + " and loginNameSubjectInfoMap.size()=" + loginNameSubjectInfoMap.size());
      Iterator iterator = loginNameSubjectInfoMap.values().iterator();

      sb.append(offset).append("<Authenticate>");
      while (iterator.hasNext()) {
         SubjectInfo subjectInfo = (SubjectInfo)iterator.next();
         sb.append(subjectInfo.toXml(extraOffset+Constants.INDENT));
      }
      sb.append(offset).append("</Authenticate>\n");

      return sb.toString();
   }

}
