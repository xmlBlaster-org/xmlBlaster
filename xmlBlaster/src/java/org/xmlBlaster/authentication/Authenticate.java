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
import org.jutils.time.StopWatch;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.engine.callback.CbWorkerPool;
import org.xmlBlaster.engine.helper.CbQueueProperty;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.Global;
import java.util.*;



/**
 * Authenticate a client via login.
 * <p>
 * The login method serves as a factory for a xmlBlaster.Server Reference
 */
final public class Authenticate implements I_Authenticate
{
   final private String ME;

   public static final String DEFAULT_SECURITYPLUGIN_TYPE = "simple";
   public static final String DEFAULT_SECURITYPLUGIN_VERSION = "1.0";

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


   /**
    */
   public Authenticate(Global global)
   {
      this.glob = global;
      this.log = this.glob.getLog("auth");
      this.ME = "Authenticate-" + glob.getId();

      if (log.CALL) log.call(ME, "Entering constructor");
      this.glob.setAuthenticate(this);
      plgnLdr = new PluginManager(global);
      plgnLdr.init(this);
   }

   public Global getGlobal()
   {
      return this.glob;
   }

   public String login(String loginName, String passwd,
                       String xmlQoS_literal, String sessionId)
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
   public SessionInfo unsecureCreateSession(String loginName) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering unsecureCreateSession(" + loginName + ")");
      String sessionId = createSessionId(loginName);
      org.xmlBlaster.authentication.plugins.simple.Manager manager = new org.xmlBlaster.authentication.plugins.simple.Manager();
      I_Session session = new org.xmlBlaster.authentication.plugins.simple.Session(manager, sessionId);
      org.xmlBlaster.authentication.plugins.I_SecurityQos securityQos = new org.xmlBlaster.authentication.plugins.simple.SecurityQos(loginName, "");
      session.init(securityQos);
      I_Subject subject = session.getSubject();
      SubjectInfo subjectInfo = new SubjectInfo(subject, new CbQueueProperty(getGlobal(), Constants.RELATING_SUBJECT, null), getGlobal());
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
    * If no sessionId==null, the sessionId from xmlQoS_literal is used,
    * if this is null as well, we generate one.
    * <p />
    * The given sessionId (in the qos) from the client could be from e.g. a2Blaster,
    * and will be used here as is, the a2Blaster plugin verifies it.
    * The extra parameter sessionId is the CORBA internal POA session id.
    * <p />
    * TODO: Totally rewrite this connect() method to allow multiple sessions
    *
    * @param connectQos  The login/connect QoS, see ConnectQos.java
    * @param sessionId   The caller (here CORBA-POA protocol driver) may insist to you its own sessionId
    */
   public final ConnectReturnQos connect(ConnectQos connectQos, String sessionId) throws XmlBlasterException
   {
      try {
         if (log.CALL) log.call(ME, "Entering connect(sessionId=" + sessionId + ")");
         if (log.DUMP) log.dump(ME, toXml());

         I_Session sessionCtx = null;
         I_Manager securityMgr = null;
         SessionInfo sessionInfo = null;

         // Get or create the sessionId (we respect a user supplied sessionId) ...
         if (sessionId == null) sessionId = connectQos.getSessionId();
         if (sessionId == null || sessionId.length() < 2) {
            sessionId = createSessionId("null" /*subjectCtx.getName()*/);
            connectQos.setSessionId(sessionId); // assure consistency
            if (log.TRACE) log.trace(ME+".connect()", "Empty sessionId - generated sessionId=" + sessionId);
         }
         else {
            SessionInfo info = (SessionInfo)sessionInfoMap.get(sessionId);
            if (info != null) {
               ConnectReturnQos returnQos = new ConnectReturnQos(glob, connectQos);
               returnQos.setSessionId(sessionId);
               log.info(ME, "Reconnecting with given sessionId, using QoS from first login");
               return returnQos;
            }
         }

         // Get suitable SecurityManager and context ...
         securityMgr = plgnLdr.getManager(connectQos.getSecurityPluginType(), connectQos.getSecurityPluginVersion());
         sessionCtx = securityMgr.reserveSession(sessionId);  // allways creates a new I_Session instance
         String securityInfo = sessionCtx.init(connectQos.getSecurityQos()); // throws XmlBlasterExceptions if authentication fails
         if (securityInfo != null && securityInfo.length() > 1) log.warn(ME, "Ignoring security info: " + securityInfo);
         // Now the client is authenticated


         // Check if user is known, otherwise create an entry ...
         I_Subject subjectCtx = sessionCtx.getSubject();
         SubjectInfo subjectInfo = getSubjectInfoByName(subjectCtx.getName());
         if (subjectInfo == null) {
            subjectInfo = new SubjectInfo(subjectCtx, connectQos.getSubjectCbQueueProperty(), getGlobal());
            loginNameSubjectInfoMap.put(subjectCtx.getName(), subjectInfo);
         }
         else
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
            while (/*!subjectInfo.isShutdown()*/true) {
               Iterator it = subjectInfo.getSessions().iterator();
               if (it.hasNext()) {
                  SessionInfo si = (SessionInfo)it.next();
                  log.warn(ME, "Destroying session '" + si.getSessionId() + "' of user '" + subjectInfo.getLoginName() + "' as requested by client");
                  disconnect(si.getSessionId(), null);
               }
               else
                  break;
            }
            return connect(connectQos, sessionId);
         }

         sessionInfo = new SessionInfo(subjectInfo, sessionCtx, connectQos, getGlobal());
         sessionInfoMap.put(sessionId, sessionInfo);
         subjectInfo.notifyAboutLogin(sessionInfo);

         fireClientEvent(sessionInfo, true);

         // --- compose an answer -----------------------------------------------
         ConnectReturnQos returnQos = new ConnectReturnQos(glob, connectQos);
         returnQos.setSessionId(sessionId); // securityInfo is not coded yet !
         returnQos.setPublicSessionId(""+sessionInfo.getInstanceId());

         // Now some nice logging ...
         StringBuffer sb = new StringBuffer(256);
         sb.append("Successful login for client ").append(subjectCtx.getName());
         sb.append(", session:").append(sessionInfo.getInstanceId());
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
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw new XmlBlasterException("Authenticate.connect.InternalError", e.toString());
      }
   }


   public final void disconnect(String sessionId, String qos_literal) throws XmlBlasterException
   {
      try {
         if (log.CALL) log.call(ME, "Entering disconnect()");
         if (log.DUMP) log.dump(ME, toXml().toString());

         I_Manager securityMgr = plgnLdr.getManager(sessionId);
         I_Session sessionSecCtx = securityMgr.getSessionById(sessionId);
         securityMgr.releaseSession(sessionId, sessionSecCtx.importMessage(qos_literal));

         DisconnectQos disconnectQos = new DisconnectQos(qos_literal);

         resetSessionInfo(sessionId, disconnectQos.deleteSubjectQueue());

         if (log.DUMP) log.dump(ME, toXml().toString());
         if (log.CALL) log.call(ME, "Leaving disconnect()");
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw new XmlBlasterException("Authenticate.disconnect.InternalError", e.toString());
      }
   }

   /**
    * Access a subjectInfo with the unique login name.
    * <p />
    * If the client is yet unknown, there will be instantiated a dummy SubjectInfo object
    * @return the SubjectInfo object
    */
   public final SubjectInfo getOrCreateSubjectInfoByName(String loginName) throws XmlBlasterException
   {
      if (loginName == null || loginName.length() < 2) {
         log.warn(ME + ".InvalidClientName", "Given loginName='" + loginName + "' is invalid");
         throw new XmlBlasterException(ME + ".InvalidClientName", "Your given loginName is null or shorter 2 chars, loginName rejected");
      }

      SubjectInfo subjectInfo = getSubjectInfoByName(loginName);
      if (subjectInfo == null) {
         subjectInfo = new SubjectInfo(loginName, getGlobal());
         loginNameSubjectInfoMap.put(loginName, subjectInfo);
      }

      return subjectInfo;
   }


   /**
    * Access a sessionInfo with the unique sessionId. 
    * <p />
    * @return the SessionInfo object or null if not known
    */
   public final SessionInfo getSessionInfo(String sessionId) throws XmlBlasterException
   {
      return (SessionInfo)sessionInfoMap.get(sessionId);
   }


   /**
    * Access a subjectInfo with the unique login name
    * @return the SubjectInfo object<br />
    *         null if not found
    */
   public final SubjectInfo getSubjectInfoByName(String loginName)
   {
      return (SubjectInfo)loginNameSubjectInfoMap.get(loginName);
   }


   /**
    * Logout of a client.
    * <p>
    * @exception XmlBlasterException If client is unknown
    */
   public final void logout(String sessionId) throws XmlBlasterException
   {
      log.error(ME, "logout not implemented");
      throw new XmlBlasterException(ME, "logout not implemented");
   }


   /**
    * @param xmlServer xmlBlaster CORBA handle
    * @param clearQueue Shall the message queue of the client be destroyed as well?
    */
   private void resetSessionInfo(String sessionId, boolean clearQueue) throws XmlBlasterException
   {
      Object obj;
      synchronized(sessionInfoMap) {
         obj = sessionInfoMap.remove(sessionId);
      }

      if (obj == null) {
         log.error(ME+".Unknown", "Sorry, you are not known, no logout");
         throw new XmlBlasterException(ME+".Unknown", "Sorry, you are not known, no logout");
      }

      SessionInfo sessionInfo = (SessionInfo)obj;

      log.info(ME, "Disconnecting client " + sessionInfo.getLoginName() + ", instanceId=" + sessionInfo.getInstanceId() + ", sessionId=" + sessionId);

      I_Session oldSessionCtx = sessionInfo.getSecuritySession();
      oldSessionCtx.getManager().releaseSession(sessionId, null);

      fireClientEvent(sessionInfo, false); // informs all I_ClientListener

      SubjectInfo subjectInfo = sessionInfo.getSubjectInfo();
      subjectInfo.notifyAboutLogout(sessionId, true);

      if (!subjectInfo.isLoggedIn()) {
         if (clearQueue || subjectInfo.getSubjectQueue().size() < 1) {
            if (subjectInfo.getSubjectQueue().size() < 1)
               log.info(ME, "Destroying SubjectInfo " + subjectInfo.getLoginName() + ". Nobody is logged in and no queue entries available");
            else
               log.warn(ME, "Destroying SubjectInfo " + subjectInfo.getLoginName() + " as clearQueue is set to true. Lost " + subjectInfo.getSubjectQueue().size() + " messages");
            loginNameSubjectInfoMap.remove(subjectInfo.getLoginName());
            subjectInfo.shutdown();
            subjectInfo = null;
         }
      }

      sessionInfo.shutdown();
      sessionInfo = null;
      Runtime.getRuntime().gc(); // !!! 
      log.info(ME, "loginNameSubjectInfoMap has " + loginNameSubjectInfoMap.size() + " entries and sessionInfoMap has " + sessionInfoMap.size() + " entries");
   }


   /**
    *  Generate a unique resource ID <br>
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

         // sessionId:<IP-Address>-<LoginName>-<TimestampMilliSec>-<RandomNumber>-<LocalCounter>
         StringBuffer buf = new StringBuffer(512);

         buf.append(Constants.SESSIONID_PRAEFIX).append(ip).append("-").append(loginName).append("-");
         buf.append(System.currentTimeMillis()).append("-").append(ran.nextInt()).append("-").append((counter++));

         String sessionId = buf.toString();
         if (log.TRACE) log.trace(ME, "Created sessionId='" + sessionId + "'");
         return sessionId;
      }
      catch (Exception e) {
         String text = "Can't generate a unique sessionId: " + e.toString();
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
    * method (like subscribe()), using the delievered sessionId
    *
    * @return SessionInfo - if the client is OK otherwise an exception is thrown (returns never null)
    * @exception XmlBlasterException Access denied
    */
   public SessionInfo check(String sessionId) throws XmlBlasterException
   {
      StopWatch stop=null; if (log.TIME) stop = new StopWatch();

      Object obj = null;
      synchronized(sessionInfoMap) {
         obj = sessionInfoMap.get(sessionId);
      }

      if (obj == null) {
         log.warn(ME+".AccessDenied", "SessionId '" + sessionId + "' is invalid, no access to xmlBlaster.");
         throw new XmlBlasterException("AccessDenied", "Your sessionId is invalid, no access to " + glob.getId() + ".");
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


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of Authenticate as a XML ASCII string
    */
   public final String toXml() throws XmlBlasterException
   {
      return toXml((String)null);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of Authenticate as a XML ASCII string
    */
   public final String toXml(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer(1000);
      String offset = "\n";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      log.info(ME, "Client maps, sessionInfoMap.size()=" + sessionInfoMap.size() + " and loginNameSubjectInfoMap.size()=" + loginNameSubjectInfoMap.size());
      Iterator iterator = loginNameSubjectInfoMap.values().iterator();

      sb.append(offset).append("<Authenticate>");
      while (iterator.hasNext()) {
         SubjectInfo subjectInfo = (SubjectInfo)iterator.next();
         sb.append(subjectInfo.toXml(extraOffset));
      }
      sb.append(offset).append("</Authenticate>\n");

      return sb.toString();
   }

}
