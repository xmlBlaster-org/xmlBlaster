package org.xmlBlaster.authentication.plugins.ldap;

import org.xmlBlaster.authentication.plugins.CryptDataHolder;
import org.xmlBlaster.authentication.plugins.DataHolder;
import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.plugins.I_Subject;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.authentication.plugins.SessionHolder;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.MsgUnitRaw;

import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;


/**
 * This implements the session AND the subject interface in the same class. 
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @see org.xmlBlaster.authentication.plugins.ldap.LdapGateway
 */
public class Session implements I_Session, I_Subject {
   private static final String ME = "Session";

   protected final Manager secMgr;
   private static Logger log = Logger.getLogger(Session.class.getName());
   protected String sessionId;
   protected boolean authenticated = false;

   protected final LdapGateway ldap;

   protected String loginName;

   public Session(Manager sm, String sessionId) throws XmlBlasterException {
      this.secMgr = sm;

      this.sessionId = sessionId;
      final String serverUrl = sm.getGlobal().getProperty().get("ldap.serverUrl", "ldap://localhost:389/o=xmlBlaster,c=ORG");
      final String rootDN = sm.getGlobal().getProperty().get("ldap.rootDN", "cn=Manager,o=xmlBlaster,c=ORG");
      final String rootPwd =  sm.getGlobal().getProperty().get("ldap.rootPwd", "secret");
      final String loginFieldName = sm.getGlobal().getProperty().get("ldap.loginFieldName", "cn");

      log.info("Initializing LDAP access on ldap.serverUrl='" + serverUrl + "' with rootdn='" + rootDN  + "'. The unique uid field name in ldap should be '" + loginFieldName + "'.");
      ldap = new LdapGateway(this.secMgr.getGlobal(), serverUrl, rootDN, rootPwd, loginFieldName);
   }

   /**
	* @see I_Session#init(ConnectQosServer, Map)
	*/
   public ConnectQosServer init(ConnectQosServer connectQos, Map map) throws XmlBlasterException {
      //this.connectQos = connectQos;
      return connectQos;
   }

   public String init(I_SecurityQos securityQos) throws XmlBlasterException {
      authenticated = false;

      this.loginName = securityQos.getUserId();
      String passwd = securityQos.getCredential();

      if (log.isLoggable(Level.FINE)) log.fine("Checking password ...");
      authenticated = ldap.checkPassword(this.loginName, passwd);
      if (log.isLoggable(Level.FINE)) log.fine("The password" /*+ passwd */+ " for cn=" + this.loginName + " is " + ((authenticated)?"":" NOT ") + " valid.");

      if (authenticated == false)
         throw new XmlBlasterException(this.secMgr.getGlobal(), ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED, ME,
                    "Authentication of user " + getName() + " failed, no authentication from LDAP server.");

      return null; // no extra information
   }

   /**
    * @see I_Session#verify(I_SecurityQos)
    */
   public boolean verify(I_SecurityQos securityQos) {
      if (!this.authenticated)
         return false;

      try {
         return ldap.checkPassword(securityQos.getUserId(), securityQos.getCredential());
      }
      catch (XmlBlasterException e) {
         return false;
      }
   }

   public String getName() {
      return this.loginName;
   }

   public boolean isAuthorized(SessionHolder sessionHolder, DataHolder dataHolder) {
      if (authenticated == false) {
         log.warning("Authentication of user " + getName() + " failed");
         return false;
      }
      
      MethodName action = dataHolder.getAction();
      String key = dataHolder.getKeyOid();

      log.warning("No authorization check for action='" + action + "' on key='" +key + "' is implemented, access generously granted.");
      return true;
   }

   public void changeSecretSessionId(String sessionId) throws XmlBlasterException {
      if(this.sessionId.endsWith(sessionId)) return;
      synchronized(sessionId) {
         this.secMgr.changeSecretSessionId(this.sessionId, sessionId);
         this.sessionId = sessionId;
      }
   }

   public String getSecretSessionId() {
      return sessionId;
   }

   public I_Subject getSubject() {
      return this;
   }

   public I_Manager getManager() {
      return this.secMgr;
   }

   public MsgUnitRaw importMessage(CryptDataHolder dataHolder) throws XmlBlasterException {
      return dataHolder.getMsgUnitRaw();
   }

   public MsgUnitRaw exportMessage(CryptDataHolder dataHolder) throws XmlBlasterException {
      return dataHolder.getMsgUnitRaw();
   }

	//@Override
	public String interceptExeptionByAuthorizer(Throwable throwable,
			SessionHolder sessionHolder, DataHolder dataHolder) {
		// TODO Auto-generated method stub
		return null;
	}
}
