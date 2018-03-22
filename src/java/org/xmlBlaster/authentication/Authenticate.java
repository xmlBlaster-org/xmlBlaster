/*------------------------------------------------------------------------------
Name:      Authenticate.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login for clients
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.authentication.plugins.CryptDataHolder;
import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.plugins.I_Subject;
import org.xmlBlaster.authentication.plugins.PluginManager;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.engine.XmlBlasterImpl;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.ConnectReturnQosServer;
import org.xmlBlaster.engine.qos.DisconnectQosServer;
import org.xmlBlaster.engine.runlevel.I_RunlevelListener;
import org.xmlBlaster.engine.runlevel.RunlevelManager;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.util.IsoDateParser;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;



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

   private final ServerScope glob;
   private static Logger log = Logger.getLogger(Authenticate.class.getName());

   /**
    * With this map you can find a client using a sessionId.
    *
    * key   = sessionId A unique identifier
    * value = SessionInfo object, containing all data about a client
    */
   final private Map<String, SessionInfo> sessionInfoMap = new HashMap<String, SessionInfo>();

   /**
    * With this map you can find a client using his login name.
    *
    * key   = loginName, the unique login name of a client
    * value = SessionInfo object, containing all data about a client
    */
   final private Map<String, SubjectInfo> loginNameSubjectInfoMap = new HashMap<String, SubjectInfo>();

   /**
    * For listeners who want to be informed about login/logout
    */
   final private Set<I_ClientListener> clientListenerSet = new HashSet<I_ClientListener>();

   /** The singleton handle for this xmlBlaster server */
   private final I_XmlBlaster xmlBlasterImpl;
   
   private boolean acceptWrongSenderAddress;

   // My security delegate layer which is exposed to the protocol plugins
   //private final AuthenticateProtector encapsulator;

   /**
    */
   public Authenticate(ServerScope global) throws XmlBlasterException
   {
      this.glob = global;

      this.ME = "Authenticate" + glob.getLogPrefixDashed();

      if (log.isLoggable(Level.FINER)) log.finer("Entering constructor");
      /*this.encapsulator = */new AuthenticateProtector(glob, this); // my security layer (delegate)

      glob.getRunlevelManager().addRunlevelListener(this);

      plgnLdr = new PluginManager(global);
      plgnLdr.init(this);
      xmlBlasterImpl = new XmlBlasterImpl(this);
      
      // TODO: Decide by authorizer, see SessionInfo.java with specific setting
      this.acceptWrongSenderAddress = glob.getProperty().get("xmlBlaster/acceptWrongSenderAddress", false);
   }

   /**
    * Just to testing sync
    * @return
    */
   public Map getSessionInfoMap() {
      return this.sessionInfoMap;
   }



   public ServerScope getGlobal()
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
      log.severe("login() not implemented");
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "login() not implemented and deprecated");
   }

   /**
    * Use this to create a user and session for internal users only.
    * This method is a security risk never allow external code to call it (there is no
    * passwd needed).
    * Note that the security instances are created rawish,
    * they are not registered with the Authentication server.
    */
   public SessionInfo unsecureCreateSession(org.xmlBlaster.client.qos.ConnectQos connectQos) throws XmlBlasterException
   {
      SessionName sessionName = connectQos.getSessionName();
      if (log.isLoggable(Level.FINER)) log.finer("Entering unsecureCreateSession(" + sessionName + ")");
      String secretSessionId = createSessionId(sessionName.getLoginName());
      org.xmlBlaster.authentication.plugins.simple.Manager manager = new org.xmlBlaster.authentication.plugins.simple.Manager();
      manager.init(glob, null);
      I_Session session = new org.xmlBlaster.authentication.plugins.simple.Session(manager, secretSessionId);
      org.xmlBlaster.authentication.plugins.I_SecurityQos securityQos = new org.xmlBlaster.authentication.plugins.simple.SecurityQos(this.glob, sessionName.getLoginName(), "");
      session.init(securityQos);
      I_Subject subject = session.getSubject();

      SubjectInfo subjectInfo = null;
      if (sessionName.getLoginName().startsWith("__")) { // __RequestBroker_internal
         // strip the pubSessionId and create a subjectInfo ...
         SessionName subjectName = new SessionName(glob, sessionName.getNodeId(), sessionName.getLoginName());
         subjectInfo = new SubjectInfo(getGlobal(), this, subjectName);
         synchronized(this.loginNameSubjectInfoMap) {
            this.loginNameSubjectInfoMap.put(subjectInfo.getLoginName(), subjectInfo);
         }
         subjectInfo.toAlive(subject, new CbQueueProperty(getGlobal(), Constants.RELATING_SUBJECT, null));
      }
      else {
         subjectInfo = getOrCreateSubjectInfoByName(sessionName, false, subject, new CbQueueProperty(getGlobal(), Constants.RELATING_SUBJECT, null));
      }

      ConnectQosServer connectQosServer = new ConnectQosServer(glob, connectQos.getData());
      SessionInfo sessionInfo = subjectInfo.getOrCreateSessionInfo(sessionName, connectQosServer);
      if (!sessionInfo.isInitialized()) {
         sessionInfo.init(subjectInfo, session, connectQosServer);
      }

      return sessionInfo;
   }

   /**
    * Login to xmlBlaster.
    */
   public final ConnectReturnQosServer connect(ConnectQosServer xmlQos) throws XmlBlasterException
   {
      return connect(xmlQos, null);
   }

   private boolean isKnownInSessionInfoMap(String previousSecretSessionId) {
      if (previousSecretSessionId == null)
         return false;
      synchronized(sessionInfoMap) {
         SessionInfo ret = sessionInfoMap.get(previousSecretSessionId);
         return ret != null;
      }
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
    * @param secretSessionId   The caller (here CORBA-POA protocol driver or SessionPersistencePlugin) may insist to you its own secretSessionId
    */
   public /*synchronized*/ final ConnectReturnQosServer connect(ConnectQosServer connectQos, String forcedSecretSessionId) throws XmlBlasterException
   {
      if (connectQos.getSessionQos().getSessionName().getLoginName().equals(this.glob.getId())) {
         String text = "You are not allowed to login with the cluster node name " + connectQos.getSessionName().toString() + ", access denied.";
         log.warning(text);
         throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION_IDENTICALCLIENT,
                   ME+".connect()", text);
      }

      { // Administrative block clients
         SubjectInfo si = getSubjectInfoByName(connectQos.getSessionQos().getSessionName());
         if (si != null && si.isBlockClientLogin()) {
            // Future todo: Throw out existing session, not only block new
            // logins
            log.warning("Access for " + connectQos.getSessionQos().getSessionName().toString() + " is blocked and denied (jconsole->blockClientLogin)");
            throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_SERVERDENY, ME + ".connect()",
                  "Access for "
                  + connectQos.getSessionQos().getSessionName().toString()
                  + " is currently not possible, please contact the server administrator");
         }
         {
            SessionInfo sesi = getSessionInfoByName(connectQos.getSessionQos().getSessionName());
            if (sesi != null && sesi.isBlockClientSessionLogin()) {
               // Future todo: Throw out existing session, not only block new
               // logins
               log.warning("Access for " + connectQos.getSessionQos().getSessionName().toString()
                     + " is blocked and denied (jconsole->blockClientSessionLogin)");
               throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_SERVERDENY, ME + ".connect()",
                     "Access for " + connectQos.getSessionQos().getSessionName().toString()
                           + " session is currently not possible, please contact the server administrator");
            }
         }
      }

      String secretSessionId = forcedSecretSessionId;
      String previousSecretSessionId = secretSessionId;
      // [1] Try reconnecting with secret sessionId
      try {
         if (log.isLoggable(Level.FINE)) log.fine("Entering connect(sessionName=" + connectQos.getSessionName().getAbsoluteName() + ")"); // " secretSessionId=" + secretSessionId + ")");
         if (log.isLoggable(Level.FINEST)) log.finest("ConnectQos=" + connectQos.toXml());

         // Get or create the secretSessionId (we respect a user supplied secretSessionId) ...
         if (secretSessionId == null || secretSessionId.length() < 2) {
            secretSessionId = connectQos.getSessionQos().getSecretSessionId();
            if (secretSessionId != null && secretSessionId.length() >= 2)
               log.info(connectQos.getSessionName().getAbsoluteName() + " is using secretSessionId '" + secretSessionId + "' from ConnectQos");
         }
         if (secretSessionId != null && secretSessionId.length() >= 2) {
            SessionInfo info = getSessionInfo(secretSessionId);
            if (info != null && !info.isShutdown()) {  // authentication succeeded

               updateConnectQos(info, connectQos);

               ConnectReturnQosServer returnQos = new ConnectReturnQosServer(glob, info.getConnectQos().getData());
               returnQos.getSessionQos().setSecretSessionId(secretSessionId);
               returnQos.getSessionQos().setSessionName(info.getSessionName());
               returnQos.setReconnected(true);
               returnQos.getData().addClientProperty(Constants.CLIENTPROPERTY_RCVTIMESTAMPSTR, IsoDateParser.getCurrentUTCTimestampNanos());
               log.info("Reconnected with given secretSessionId.");
               return returnQos;
            }
         }
      }
      catch (Throwable e) {
         log.severe("Internal error when trying to reconnect to session " + connectQos.getSessionName() + " with secret session ID: " + e.toString());
         e.printStackTrace();
         throw XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_CONNECTIONFAILURE.toString(), e);
      }

      // [2] Try reconnecting with publicSessionId
      if (connectQos.hasPublicSessionId()) {
         SessionInfo info = getSessionInfo(connectQos.getSessionName());
         if (info != null && !isKnownInSessionInfoMap(previousSecretSessionId) && connectQos.getSessionName().getPublicSessionId() <= 0) {
            if (connectQos.getData().getCurrentCallbackAddress().getRetries() == -1)
               log.warning(connectQos.getSessionName().getAbsoluteName()
                     + " -dispatch/callback/retries=-1 causes a memory leak on re-connect with negative sessionId.");
            // set pubSessionId=0 because race condition between disconnect and re-connect 
            SessionName sessionName = new SessionName(this.glob, connectQos.getSessionName().getNodeId(), connectQos.getSessionName().getLoginName(), 0);
            connectQos.setSessionName(sessionName);
         }
         if (info != null && !info.isShutdown() && !isKnownInSessionInfoMap(previousSecretSessionId) && !info.getConnectQos().bypassCredentialCheck() && connectQos.hasPublicSessionId()) {
            if (connectQos.getSessionQos().reconnectSameClientOnly()) {
               String text = "Only the creator of session " + connectQos.getSessionName().toString() + " may reconnect, access denied.";
               log.warning(text);
               throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION_IDENTICALCLIENT,
                         ME+".connect()", text);
            }
            try {
               // Check password as we can't trust the public session ID
               connectQos = info.getSecuritySession().init(connectQos, null);
               boolean ok = info.getSecuritySession().verify(connectQos.getSecurityQos());
               if (!ok)
                   throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED,
                		   ME, "Access denied for " + connectQos.getSecurityQos().getUserId() + " " + connectQos.getClientPluginType());

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

               updateConnectQos(info, connectQos); // fires event

               ConnectReturnQosServer returnQos = new ConnectReturnQosServer(glob, info.getConnectQos().getData());
               returnQos.getSessionQos().setSessionName(info.getSessionName());
               returnQos.setReconnected(true);
               returnQos.getData().addClientProperty(Constants.CLIENTPROPERTY_RCVTIMESTAMPSTR, IsoDateParser.getCurrentUTCTimestampNanos());
               info.getDispatchStatistic().incrNumConnect(1);
               info.getDispatchStatistic().touchLastLoginMillis();
               log.info("Reconnected with given publicSessionId to '" + info.getSessionName() + "'.");
               return returnQos;
            }
            catch (XmlBlasterException e) {
               String secName = connectQos.getSecurityQos().getUserId();
               log.warning("Access is denied when trying to reconnect to session " + info.getSessionName() + " with security Qos " + connectQos.getSecurityQos() + " userId=" + secName + ": " + e.getMessage());// + ": " + connectQos.toXml());
               throw e; // Thrown if authentication failed
            }
            catch (Throwable e) {
               log.severe("Internal error when trying to reconnect to session " + info.getSessionName() + " with public session ID: " + e.toString());
               e.printStackTrace();
               throw XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_CONNECTIONFAILURE.toString(), e);
            }
         }
      }

      // [3] Generate a secret session ID
      if (forcedSecretSessionId == null || forcedSecretSessionId.length() < 2) {
         if (connectQos.getSessionName().getPublicSessionId() <= 0 && connectQos.getData().getCurrentCallbackAddress().getRetries() == -1)
            log.severe(connectQos.getSessionName().getAbsoluteName() + " -dispatch/callback/retries=-1 causes a memory leak on re-connect with negative sessionId!");
         secretSessionId = createSessionId("null" /*subjectCtx.getName()*/);
         connectQos.getSessionQos().setSecretSessionId(secretSessionId); // assure consistency
         if (log.isLoggable(Level.FINE)) log.fine("Empty secretSessionId - generated secretSessionId=" + secretSessionId);
      }

      I_Session sessionCtx = null;
      I_Manager securityMgr = null;
      SessionInfo sessionInfo = null;

      // [4] Authenticate new client with password
      try {
         // Get suitable SecurityManager and context ...
         securityMgr = plgnLdr.getManager(connectQos.getClientPluginType(), connectQos.getClientPluginVersion());
         if (securityMgr == null) {
            log.warning("Access is denied, there is no security manager configured for this connect QoS: " + connectQos.toXml());
            throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED, ME, "There is no security manager configured with the given connect QoS");
         }
         sessionCtx = securityMgr.reserveSession(secretSessionId);  // always creates a new I_Session instance
         connectQos = sessionCtx.init(connectQos, null);
         if (connectQos.bypassCredentialCheck()) {
            // This happens when a session is auto created by a PtP message
            // Only ConnectQosServer (which is under control of the core) can set this flag
            if (log.isLoggable(Level.FINE)) log.fine("SECURITY SWITCH OFF: Granted access to xmlBlaster without password, bypassCredentialCheck=true");
         }
         else {
            String securityInfo = sessionCtx.init(connectQos.getSecurityQos()); // throws XmlBlasterExceptions if authentication fails
            if (securityInfo != null && securityInfo.length() > 1) log.warning("Ignoring security info: " + securityInfo);
         }
         log.info("Access for " + connectQos.getSessionName().getAbsoluteName() + " " + securityMgr + " authenticated");
         // Now the client is authenticated
      }
      catch (XmlBlasterException e) {
         // If access is denied: cleanup resources
         log.warning("Access is denied: " + e.getMessage() + ": " + connectQos.toString());
         if (securityMgr != null) securityMgr.releaseSession(secretSessionId, null);  // allways creates a new I_Session instance
         throw e;
      }
      catch (Throwable e) {
         log.severe("PANIC: Access is denied: " + e.getMessage() + "\n" + ServerScope.getStackTraceAsString(e));
         e.printStackTrace();
         // On error: cleanup resources
         securityMgr.releaseSession(secretSessionId, null);  // allways creates a new I_Session instance
         throw XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_CONNECTIONFAILURE.toString(), e);
      }

      if (log.isLoggable(Level.FINE)) log.fine("Checking if user is known ...");
      SubjectInfo subjectInfo = null;
      try {
      /*
         // Check if user is known, otherwise create an entry ...
         I_Subject subjectCtx = sessionCtx.getSubject();
         SessionName subjectName = new SessionName(glob, connectQos.getSessionName(), 0L); // Force to be of type subject (no public session ID)

         boolean subjectIsAlive = false;
         synchronized(this.loginNameSubjectInfoMap) { // Protect against two simultaneous logins
            subjectInfo = (SubjectInfo)this.loginNameSubjectInfoMap.get(subjectName.getLoginName());
            //log.error(ME, "DEBUG ONLY, subjectName=" + subjectName.toString() + " loginName=" + subjectName.getLoginName() + " state=" + toXml());
            if (subjectInfo == null) {
               subjectInfo = new SubjectInfo(getGlobal(), this, subjectName);
               this.loginNameSubjectInfoMap.put(subjectInfo.getLoginName(), subjectInfo); // Protect against two simultaneous logins
            }

            subjectIsAlive = subjectInfo.isAlive();
         } // synchronized(this.loginNameSubjectInfoMap)

         if (!subjectInfo.isAlive()) {
            try {
               subjectInfo.toAlive(subjectCtx, connectQos.getSubjectQueueProperty());
            }
            catch(Throwable e) {
               synchronized(this.loginNameSubjectInfoMap) {
                  this.loginNameSubjectInfoMap.remove(subjectInfo.getLoginName());
               }
               throw e;
            }
         }
         */

         // [5] New client is authenticated, create the SessioInfo
         boolean returnLocked = true;
         subjectInfo = getOrCreateSubjectInfoByName(connectQos.getSessionName(),
                                   returnLocked, sessionCtx.getSubject(), connectQos.getSubjectQueueProperty());
         try { // subjectInfo.getLock().release())
            if (subjectInfo.isAlive()) {
               if (connectQos.getData().hasSubjectQueueProperty())
                  subjectInfo.setSubjectQueueProperty(connectQos.getSubjectQueueProperty()); // overwrites only if not null
            }
            // Check if client does a relogin and wants to destroy old sessions
            if (connectQos.clearSessions() == true) {
               SessionInfo[] sessions = subjectInfo.getSessionsToClear(connectQos);
               if (sessions.length > 0) {
	               for (int i=0; i<sessions.length; i++ ) {
	                  SessionInfo si = sessions[i];
	                  log.warning("Destroying session '" + si.getSecretSessionId() + "' of user '" + subjectInfo.getSubjectName() + "' as requested by client");
	                  disconnect(si.getSecretSessionId(), (String)null);
	               }
	               // will create a new SubjectInfo instance (which should be OK)
	               return connect(connectQos, secretSessionId);
               }
            }

            if (log.isLoggable(Level.FINE)) log.fine("Creating sessionInfo for " + subjectInfo.getId());

            // A PtP with forceQueuing=true and a simultaneous connect of the same
            // client: This code is thread safe with new SessionInfo() below
            // to avoid duplicate SessionInfo
            sessionInfo = getOrCreateSessionInfo(connectQos.getSessionName(), connectQos);
            if (sessionInfo.isInitialized() &&
                !sessionInfo.isShutdown() && sessionInfo.getConnectQos().bypassCredentialCheck()) {
               if (log.isLoggable(Level.FINE)) log.fine("connect: Reused session with had bypassCredentialCheck=true");
               String oldSecretSessionId = sessionInfo.getSecretSessionId();
               sessionInfo.setSecuritySession(sessionCtx);
               if (secretSessionId == null || secretSessionId.length() < 2) {
                  // Keep the old secretSessionId
                  connectQos.getSessionQos().setSecretSessionId(oldSecretSessionId);
               }
               else {
                  // The CORBA driver insists in a new secretSessionId
                  changeSecretSessionId(oldSecretSessionId, secretSessionId);
                  connectQos.getSessionQos().setSecretSessionId(secretSessionId);
               }
               updateConnectQos(sessionInfo, connectQos);
            }
            else {
               // Create the new sessionInfo instance
               if (log.isLoggable(Level.FINE)) log.fine("connect: sessionId='" + secretSessionId + "' connectQos='"  + connectQos.toXml() + "'");
               sessionInfo.init(subjectInfo, sessionCtx, connectQos);
               synchronized(this.sessionInfoMap) {
                  this.sessionInfoMap.put(secretSessionId, sessionInfo);
               }
            }

            connectQos.getSessionQos().setSecretSessionId(secretSessionId);
            connectQos.getSessionQos().setSessionName(sessionInfo.getSessionName());
            subjectInfo.notifyAboutLogin(sessionInfo);
            fireClientEvent(sessionInfo, true);
         }
         finally {
            if (subjectInfo != null) subjectInfo.getLock().unlock();
         }

         // --- compose an answer -----------------------------------------------
         ConnectReturnQosServer returnQos = new ConnectReturnQosServer(glob, connectQos.getData());
         returnQos.getSessionQos().setSecretSessionId(secretSessionId); // securityInfo is not coded yet !
         returnQos.getSessionQos().setSessionName(sessionInfo.getSessionName());
         returnQos.getData().addClientProperty(Constants.CLIENTPROPERTY_RCVTIMESTAMPSTR, IsoDateParser.getCurrentUTCTimestampNanos());


         // Now some nice logging ...
         StringBuffer sb = new StringBuffer(256);
         if (connectQos.bypassCredentialCheck())
            sb.append("Created tempory session for client ");
         else
            sb.append("Successful login for client ");
         sb.append(sessionInfo.getSessionName().getAbsoluteName());
         sb.append(", session");
         sb.append(((connectQos.getSessionTimeout() > 0L) ?
                         " expires after"+Timestamp.millisToNice(connectQos.getSessionTimeout()) :
                         " lasts forever"));
         sb.append(", ").append(subjectInfo.getNumSessions()).append(" of ");
         sb.append(connectQos.getMaxSessions()).append(" sessions are in use.");
         if (connectQos.isFromPersistenceRecovery()) {
        	 sb.append(" isFromPersistenceRecovery=" + true);
         }
         log.info(sb.toString());
         if (log.isLoggable(Level.FINEST)) log.finest(toXml());
         if (log.isLoggable(Level.FINEST)) log.finest("Returned QoS:\n" + returnQos.toXml());
         if (log.isLoggable(Level.FINER)) log.finer("Leaving connect()");

         return returnQos;
      }
      catch (XmlBlasterException e) {
         String id = (sessionInfo != null) ? sessionInfo.getId() : ((subjectInfo != null) ? subjectInfo.getId() : "");
         log.warning("Connection for " + id + " failed: " + e.getMessage());
         // e.g. by TestPersistentSession.java 
         // persistence/session/maxEntriesCache=1
         // persistence/session/maxEntries=2
         if (!e.getErrorCode().isOfType(ErrorCode.USER_SECURITY_AUTHENTICATION)) {
            // E.g. if sessionStore overflow: we don't want the client polling
            //e.changeErrorCode(ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED);
            e = new XmlBlasterException(e.getGlobal(), ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED,
                  ME, "Access to xmlBlaster denied", e);
         }
         e.setCleanupSession(true);
         // cleanup delayed to give our throw return a chance to reach client before the socket is closed
         // Too dangerous: The stale SessionInfo could be reaccessed during the sleep
         // There for we do the delay in CallbackSocketDriver ...
         //disconnectDelayed(secretSessionId, (String)null, 5000, e); // cleanup
         disconnect(secretSessionId, (String)null);
         throw e;
      }
      catch (Throwable t) {
         t.printStackTrace();
         log.severe("Internal error: Connect failed: " + t.getMessage());
         //disconnectDelayed(secretSessionId, (String)null, 10000, t); // cleanup
         disconnect(secretSessionId, (String)null);
         // E.g. if NPE: we don't want the client polling: Should we change to USER_SECURITY_AUTHENTICATION_ACCESSDENIED?
         XmlBlasterException e = XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_CONNECTIONFAILURE.toString(), t);
         e.setCleanupSession(true);
         throw e;
      }
   }
   
   /*
    * Probably dangerous as the sessionInfo is visible and could be found
    * by a reconnecting client and the it is suddenly destroyed after the delay
   private void disconnectDelayed(final String secretSessionId, final String qos_literal, long delay, final Throwable reason) {
      Timeout timeout = new Timeout("DisconnectTimer", true);
      timeout.addTimeoutListener(new I_Timeout() {
         public void timeout(Object userData) {
            try {
               disconnect(secretSessionId, qos_literal); // cleanup delayed to give our throw return a chance to reach client
            }
            catch (XmlBlasterException e) {
               e.printStackTrace();
               log.warning("Ignoring problems during cleanup of exception: " + e.getMessage() + ((reason==null) ? "" : (": " + reason.getMessage())));
            }
         }
      }, delay, secretSessionId);
   }
    */

   public final /*synchronized*/ void disconnect(String secretSessionId, String qos_literal) throws XmlBlasterException {
      try {
         if (log.isLoggable(Level.FINER)) log.finer("Entering disconnect()");
         //Thread.currentThread().dumpStack();
         if (log.isLoggable(Level.FINEST)) log.finest(toXml().toString());
         if (secretSessionId == null) {
            throw new IllegalArgumentException("disconnect() failed, the given secretSessionId is null");
         }

         I_Manager securityMgr = plgnLdr.getManager(secretSessionId);
         I_Session sessionSecCtx = securityMgr.getSessionById(secretSessionId);
         if (sessionSecCtx == null) {
            throw new XmlBlasterException(this.glob, ErrorCode.USER_NOT_CONNECTED, ME + " Authenticate.disconnect", "You are not connected, your secretSessionId is invalid.");
         }
         try {
            CryptDataHolder dataHolder = new CryptDataHolder(MethodName.DISCONNECT, new MsgUnitRaw(null, (byte[])null, qos_literal), null);
            securityMgr.releaseSession(secretSessionId, sessionSecCtx.importMessage(dataHolder).getQos());
         }
         catch(Throwable e) {
            log.warning("Ignoring importMessage() problems, we continue to cleanup resources: " + e.getMessage());
         }

         SessionInfo sessionInfo = getSessionInfo(secretSessionId);
         if (sessionInfo == null) {
        	 log.warning("Given secretSessionId=" + secretSessionId + " is not known, no disconnect possible: " + qos_literal);
        	 return;
         }
         if (sessionInfo.getCbQueueNumMsgs() > 0) {
            long sleep = glob.getProperty().get("cb.disconnect.pending.sleep", 1000L); // TODO: allow configuration over DisconnectQos
            log.info("Sleeping cb.disconnect.pending.sleep=" + sleep + " millis in disconnect(" + sessionInfo.getId() + ") to deliver " + sessionInfo.getCbQueueNumMsgs() + " pending messages ...");
            try { Thread.sleep(sleep); } catch( InterruptedException i) {}
         }
         sessionInfo.getDispatchStatistic().incrNumDisconnect(1);

         SubjectInfo subjectInfo = sessionInfo.getSubjectInfo();

         DisconnectQosServer disconnectQos = new DisconnectQosServer(glob, qos_literal);

         boolean forceShutdownEvenIfEntriesExist = false;

         resetSessionInfo(sessionInfo, disconnectQos.deleteSubjectQueue(), forceShutdownEvenIfEntriesExist, true);

         if (disconnectQos.clearSessions() == true && subjectInfo.getNumSessions() > 0) {
            //Specific deleting for pubSessionId< or >0 not yet implemented
            //SessionInfo[] sessions = subjectInfo.getSessionsToClear(connectQos);
            SessionInfo[] sessions = subjectInfo.getSessions();
            for (int i=0; i<sessions.length; i++ ) {
               SessionInfo si = sessions[i];
               log.warning("Destroying session '" + si.getSecretSessionId() + "' of user '" + subjectInfo.getSubjectName() + "' as requested by client");
               disconnect(si.getSecretSessionId(), null);
            }
         }

         if (log.isLoggable(Level.FINEST)) log.finest(toXml().toString());
         if (log.isLoggable(Level.FINER)) log.finer("Leaving disconnect()");
      }
      catch (XmlBlasterException e) {
         if (log.isLoggable(Level.FINE)) log.fine("disconnect failed: " + e.getMessage());
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         log.severe("Internal error: Disconnect failed: " + e.getMessage());
         throw XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_DISCONNECT.toString(), e);
      }
   }

   /**
    * Access a subjectInfo with the unique login name.
    * <p />
    * If the client is yet unknown, there will be instantiated a dummy SubjectInfo object
    * @param returnLocked true: The SubjectInfo is locked
    * @param prop Can be null
    * @return the SubjectInfo object, is never null
    * @exception the SubjectInfo object is never locked in such a case
    */
   public final SubjectInfo getOrCreateSubjectInfoByName(SessionName subjectName, boolean returnLocked, I_Subject subjectCtx, CbQueueProperty prop) throws XmlBlasterException
   {
      if (subjectName == null || subjectName.getLoginName().length() < 2) {
         log.warning("Given loginName is null");
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".InvalidClientName", "Your given loginName is null or shorter 2 chars, loginName rejected");
      }

      SubjectInfo subjectInfo = null;
      boolean isNew = false;
      synchronized(this.loginNameSubjectInfoMap) {
         subjectInfo = (SubjectInfo)this.loginNameSubjectInfoMap.get(subjectName.getLoginName());
         if (subjectInfo == null) {
            SessionName name = new SessionName(glob, glob.getNodeId(), subjectName.getLoginName()); // strip nodeId, strip pubSessionId
            //log.error(ME, "DEBUG ONLY: Stripped name=" + name.toString());
            subjectInfo = new SubjectInfo(getGlobal(), this, name);
            this.loginNameSubjectInfoMap.put(subjectName.getLoginName(), subjectInfo);
            isNew = true;
         }
      }

      if (isNew) {
         if (returnLocked) subjectInfo.getLock().lock();
         try {
            //log.error(ME, "DEBUG ONLY: REMOVE AGAIN");
            //if (subjectName.getLoginName().equals("subscriber")) {
            //   log.error(ME, "DEBUG ONLY: sleepig 20 sec for toAlive(): " + subjectName.toString());
            //   try { Thread.currentThread().sleep(20*1000L); } catch( InterruptedException i) {}
            //}
            subjectInfo.toAlive(subjectCtx, (prop != null) ? prop : new CbQueueProperty(getGlobal(), Constants.RELATING_SUBJECT, null));
         }
         catch(Throwable e) {
            synchronized(this.loginNameSubjectInfoMap) {
               this.loginNameSubjectInfoMap.remove(subjectInfo.getLoginName());
            }
            if (returnLocked) subjectInfo.getLock().unlock();
            throw XmlBlasterException.convert(getGlobal(), ErrorCode.INTERNAL_UNKNOWN, ME, e.toString(), e);
         }
      }
      else {
         subjectInfo.waitUntilAlive(returnLocked);
         if (subjectCtx != null && subjectInfo.getSecurityCtx() == null)
            subjectInfo.setSecurityCtx(subjectCtx); // If SubjectInfo was created by a PtP message the securityCtx is missing, add it here
      }

      return subjectInfo;
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
         log.severe("Publishing internal user list failed: " + e.getMessage());
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

   public final SessionInfo getSessionInfoByName(SessionName sessionName) {
      SubjectInfo subjectInfo = getSubjectInfoByName(sessionName);
      if (subjectInfo == null) return null;
      return subjectInfo.getSession(sessionName);
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
            // Happens in race condition of disconnect vs re-connect
            throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_RESOURCE_TEMPORARY_UNAVAILABLE, ME+".changeSecretSessionId()", "The new secretSessionId '" + newSessionId + "' is already in use.");
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
         SessionInfo sessionInfo = (SessionInfo)this.sessionInfoMap.get(secretSessionId);
         if (sessionInfo != null && sessionInfo.isInitialized())
            return sessionInfo;
      }
      return null;
   }

   /**
    * Returns a current snapshot of all sessions, never returns null.
    */
   public final SessionInfo[] getSessionInfoArr() {
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
      SessionInfo sessionInfo = subjectInfo.getSessionInfo(sessionName);
      if (sessionInfo != null && sessionInfo.isInitialized())
         return sessionInfo;
      return null;
   }

   /**
    * Blocks for existing SessionInfo until it is initialized.
    * For new created SessionInfo you need to call sessionInfo.init() 
    * @param sessionName
    * @param connectQos
    * @return
    * @throws XmlBlasterException
    */
   private final SessionInfo getOrCreateSessionInfo(SessionName sessionName, ConnectQosServer connectQos) throws XmlBlasterException {
      SubjectInfo subjectInfo = getSubjectInfoByName(sessionName);
      if (subjectInfo == null)
         throw new IllegalArgumentException("subjectInfo is null for " + sessionName.getAbsoluteName());
      return subjectInfo.getOrCreateSessionInfo(sessionName, connectQos);
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
      log.severe("logout not implemented");
      throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME + ".logout not implemented");
   }


   /**
    * @param sessionInfo
    * @param clearQueue Shall the message queue of the client be destroyed as well on last session logout?
    * @param forceShutdownEvenIfEntriesExist on last session
    * @param isDisconnecting true if this method is invoked while explicitly disconnecting a session, false
    *        otherwise. It is used to determine if the session queue (callback queue) has to be cleared or not.
    *    */
   private void resetSessionInfo(SessionInfo sessionInfo, boolean clearQueue, boolean forceShutdownEvenIfEntriesExist, boolean isDisconnecting) throws XmlBlasterException
   {
      firePreRemovedClientEvent(sessionInfo);
      String secretSessionId = sessionInfo.getSecretSessionId();
      Object obj;
      synchronized(this.sessionInfoMap) {
         obj = this.sessionInfoMap.remove(secretSessionId);
      }

      if (obj == null) {
         log.warning("Sorry, '" + sessionInfo.getId() + "' is not known, no logout.");
         throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED, ME,
                   "Client '" + sessionInfo.getId() + "' is not known, disconnect is not possible.");
      }

      log.info("Disconnecting client " + sessionInfo.getSessionName() + ", instanceId=" + sessionInfo.getInstanceId() + ", secretSessionId=" + secretSessionId);

      I_Session oldSessionCtx = sessionInfo.getSecuritySession();
      oldSessionCtx.getManager().releaseSession(secretSessionId, null);

      fireClientEvent(sessionInfo, false); // informs all I_ClientListener

      SubjectInfo subjectInfo = sessionInfo.getSubjectInfo();
      subjectInfo.notifyAboutLogout(sessionInfo.getId(), clearQueue, forceShutdownEvenIfEntriesExist);
      //if (subjectInfo.isShutdown()) {
      //   subjectInfo = null; // Give GC a hint
      //}

      // with positive sessionId avoid to clear session queue: Such a DisconnectQos flag is currently not existing
      if (isDisconnecting) sessionInfo.getSessionQueue().clear();
      sessionInfo.shutdown();

      sessionInfo = null;
      log.info("loginNameSubjectInfoMap has " + getNumSubjects() +
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
         if (log.isLoggable(Level.FINE)) log.fine("Created secretSessionId='" + secretSessionId + "'");
         return secretSessionId;
      }
      catch (Exception e) {
         String text = "Can't generate a unique secretSessionId: " + e.getMessage();
         log.severe(text);
         throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED, ME, text);
      }
   }

   /**
    * Returns a current snapshot of all ClientListeners
    */
   private final I_ClientListener[] getClientListenerArr() {
      synchronized(this.clientListenerSet) {
         return (I_ClientListener[])this.clientListenerSet.toArray((new I_ClientListener[this.clientListenerSet.size()]));
      }
   }

   private void firePreRemovedClientEvent(SessionInfo sessionInfo) throws XmlBlasterException {
      I_ClientListener[] clientListenerArr = getClientListenerArr();
      if (clientListenerArr.length == 0) return;
      ClientEvent event = new ClientEvent(sessionInfo);
      for (int ii=0; ii<clientListenerArr.length; ii++)
         clientListenerArr[ii].sessionPreRemoved(event);
      event = null;
   }

   /**
    * Used to fire an event if a client does a login / logout
    */
   private void fireClientEvent(SessionInfo sessionInfo, boolean login) throws XmlBlasterException {
      I_ClientListener[] clientListenerArr = getClientListenerArr();
      if (clientListenerArr.length == 0) return;
      ClientEvent event = new ClientEvent(sessionInfo);
      for (int ii=0; ii<clientListenerArr.length; ii++) {
         if (login)
            clientListenerArr[ii].sessionAdded(event);
         else
            clientListenerArr[ii].sessionRemoved(event);
      }
      event = null;
   }
   
   private void updateConnectQos(SessionInfo sessionInfo, ConnectQosServer newConnectQos) throws XmlBlasterException {
      ConnectQosServer previousConnectQosServer = sessionInfo.getConnectQos();
      
      sessionInfo.updateConnectQos(newConnectQos);
      
      try {
         I_ClientListener[] clientListenerArr = getClientListenerArr();
         if (clientListenerArr.length == 0) return;
         ClientEvent event = new ClientEvent(previousConnectQosServer, sessionInfo);
         for (int ii=0; ii<clientListenerArr.length; ii++) {
            clientListenerArr[ii].sessionUpdated(event);
         }
         event = null;
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         log.severe("updateConnectQos for " + sessionInfo.getId() + " failed: " + e.toString());
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
         in xmlBlasterPlugins.xml
      if (glob.getRunlevelManager().getCurrentRunlevel() < RunlevelManager.RUNLEVEL_STANDBY) {
         String text = "The run level " + RunlevelManager.toRunlevelStr(glob.getRunlevelManager().getCurrentRunlevel()) +
                       " of xmlBlaster is not handling any communication anymore. " + glob.getId() + ".";
         log.warn(ME+".communication.noconnection", text);
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, text);
      }
      */

      Object obj = null;
      synchronized(this.sessionInfoMap) {
         obj = this.sessionInfoMap.get(secretSessionId);
      }

      if (obj == null) {
         log.warning("SessionId '" + secretSessionId + "' is invalid, no access to xmlBlaster.");
         throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED, ME, "Your secretSessionId is invalid, no access to " + glob.getId() + ".");
      }
      SessionInfo sessionInfo = (SessionInfo)obj;

      sessionInfo.refreshSession(); // touch the session, expiry timer is spaned

      if (log.isLoggable(Level.FINE)) log.fine("Succesfully granted access for " + sessionInfo.toString());

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
    * Get a current snapshot of all known subjects.
    * @return The subjects known
    */
   public SubjectInfo[] getSubjectInfoArr() {
      synchronized(this.loginNameSubjectInfoMap) {
         return (SubjectInfo[])this.loginNameSubjectInfoMap.values().toArray(new SubjectInfo[this.loginNameSubjectInfoMap.size()]);
      }
   }

   /**
    * Access a list of login names e.g. "joe","jack","averell","william"
    * @return An array of length 0 if no clients available
    */
   public String[] getSubjects() {
      SubjectInfo[] arr = getSubjectInfoArr();
      if (arr == null || arr.length == 0)
         return new String[0];
      String[] ret = new String[arr.length];
      for (int i=0; i<arr.length; i++) {
         ret[i] = arr[i].getLoginName();
      }
      return ret;
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
    * Helper method where protocol layers may report a lost connection (e.g. socket EOF).
    * <p>
    * The SessionInfo can than do an immediate ping() to trigger POLLING mode
    * @see I_Authenticate#connectionState(String, ConnectionStateEnum)
    */
   public void connectionState(String secretSessionId, ConnectionStateEnum state) {
      if (state == ConnectionStateEnum.DEAD) {
         SessionInfo sessionInfo = getSessionInfo(secretSessionId);
         if (sessionInfo != null)
            sessionInfo.lostClientConnection();
      }
      else {
         log.warning("Ignoring unexpected connectionState notification + " + state.toString() + ", handling is not implemented");
      }
   }

   /**
    * Invoked on run level change, see RunlevelManager.RUNLEVEL_HALTED and RunlevelManager.RUNLEVEL_RUNNING
    * <p />
    * Enforced by I_RunlevelListener
    */
   public void runlevelChange(int from, int to, boolean force) throws org.xmlBlaster.util.XmlBlasterException {
      //if (log.isLoggable(Level.FINER)) log.call(ME, "Changing from run level=" + from + " to level=" + to + " with force=" + force);
      if (to == from)
         return;

      if (to > from) { // startup
         if (to == RunlevelManager.RUNLEVEL_CLEANUP_PRE) {
         }
      }

      if (to < from) { // shutdown
         if (to == RunlevelManager.RUNLEVEL_HALTED) {
            if (log.isLoggable(Level.FINE)) log.fine("Killing " + this.sessionInfoMap.size() + " login sessions");
            SessionInfo[] sessionInfoArr = getSessionInfoArr();
            for (int ii=0; ii<sessionInfoArr.length; ii++) {
               try {
                  boolean clearQueue = false;
                  boolean forceShutdownEvenIfEntriesExist = true;
                  resetSessionInfo(sessionInfoArr[ii], clearQueue, forceShutdownEvenIfEntriesExist, false);
               }
               catch (Throwable e) {
                  log.severe("Problem on session shutdown, we ignore it: " + e.getMessage());
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
         log.error(ME, "persistenceConnect: the session persistence plugin is not registered (yet): can't make connection persistent");
         Thread.dumpStack();
         return;
      }
      ClientEvent event = new ClientEvent(info);
      plugin.sessionAdded(event);
   }

   private void persistenceDisConnect(SessionInfo info) throws XmlBlasterException {
      I_SessionPersistencePlugin plugin = getSessionPersistencePlugin();
      if (plugin == null) {
         log.error(ME, "persistenceConnect: the session persistence plugin is not registered (yet): can't make connection persistent");
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

      log.info("Client maps, sessionInfoMap.size()=" + this.sessionInfoMap.size() +
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

   /** For JMX MBean: The number of different users, the sessions may be higher */
   public int getNumClients() {
      return getNumSubjects();
   }
   /** For JMX MBean: The maximum number of different users, the sessions may be higher */
   public int getMaxClients() {
      return getMaxSubjects();
   }
   /** For JMX MBean: These are the login names returned, every client may be logged in multiple times
       which you can't see here */
   public String getClientList() {
      return getSubjectList();
   }

   /**
    * Authorization check (TODO: generic approach)
    * @param sessionInfo can be null to get the general setting
    * @return true: We accept wrong sender address in PublishQos.getSender() (not myself)
    */
   public boolean isAcceptWrongSenderAddress(SessionInfo sessionInfo) {
      if (this.acceptWrongSenderAddress)
         return this.acceptWrongSenderAddress;
      if (sessionInfo != null)
         return sessionInfo.isAcceptWrongSenderAddress();
      return this.acceptWrongSenderAddress;
   }

   /**
    * @param acceptWrongSenderAddress the acceptWrongSenderAddress to set
    */
   public void setAcceptWrongSenderAddress(boolean acceptWrongSenderAddress) {
      boolean old = this.acceptWrongSenderAddress;
      this.acceptWrongSenderAddress = acceptWrongSenderAddress;
      String tmp = "Changed acceptWrongSenderAddress from " + old + " to " + this.acceptWrongSenderAddress + ".";
      if (this.acceptWrongSenderAddress == true)
         log.warning(tmp + " Caution: All clients can now publish messages using anothers login name as sender");
      else
         log.info(tmp + " Faking anothers publisher address is not possible, but specific clients may allow it");
   }
}
