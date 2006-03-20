package org.xmlBlaster.authentication.plugins.ldap;

import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.plugins.I_Subject;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.MsgUnitRaw;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.def.MethodName;


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
    * Initialize the Session. (In this case, it's a login.)<br/>
    * [I_Session]
    * <p/>
    * @param String A xml-String containing the loginname, password, etc.
    * @exception XmlBlasterException Thrown (in this case) if the user doesn't
    *                                exist or the passwd is incorrect.
    */
   public String init(String xmlQoS_literal) throws XmlBlasterException {
      return init(new SecurityQos(this.secMgr.getGlobal(), xmlQoS_literal));
   }


   /**
    * Initialize the Session for a login or connect call. 
    * <p/>
    * @param String The SecurityQos object containing the credentials, e.g. loginName/passwd
    * @exception XmlBlasterException Thrown (in this case) if the user doesn't
    *                                exist or the passwd is incorrect.
    */
   public String init(I_SecurityQos securityQos) throws XmlBlasterException {
      authenticated = false;

      this.loginName = securityQos.getUserId();
      String passwd = ((SecurityQos)securityQos).getCredential();

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
         return ldap.checkPassword(securityQos.getUserId(), ((SecurityQos)securityQos).getCredential());
      }
      catch (XmlBlasterException e) {
         return false;
      }
   }

   /**
    * Get the subjects login-name.
    * <p/>
    * @return String name
    */
   public String getName() {
      return this.loginName;
   }

   /**
    * Check if this subject is permitted to do something
    * <p/>
    * @param String The action the user tries to perfrom
    * @param String whereon the user tries to perform the action
    *
    * EXAMPLE:
    *    isAuthorized("publish", "thisIsAMessageKey");
    *
    * The above line checks if this subject is permitted to >>publish<<
    * a message under the key >>thisIsAMessageKey<<
    *
    * Known action keys:
    *    publish, subscribe, get, erase, ... see Constants.PUBLISH etc.
    */
   public boolean isAuthorized(MethodName actionKey, String key) {
      if (authenticated == false) {
         log.warning("Authentication of user " + getName() + " failed");
         return false;
      }

      log.warning("No authorization check for action='" + actionKey + "' on key='" +key + "' is implemented, access generously granted.");
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

   /**
    * Enforced by interface I_Session
    */
   public I_Subject getSubject() {
      return this;
   }

   public I_Manager getManager() {
      return this.secMgr;
   }

   /**
    * decrypt, check, unseal an incomming message. 
    * <p/>
    * @param MsgUnitRaw The the received message
    * @param MethodName The name of the method which is intercepted
    * @return MsgUnitRaw The original message
    * @exception XmlBlasterException Thrown i.e. if the message has been modified
    */
   public MsgUnitRaw importMessage(MsgUnitRaw msg, MethodName action) throws XmlBlasterException {
      // dummy implementation
      return msg;
   }

   public String importMessage(String xmlMsg) throws XmlBlasterException {
      return xmlMsg;
   }

   public byte[] importMessage(byte[] xmlMsg) throws XmlBlasterException {
      return xmlMsg;
   }

   /**
    * encrypt, sign, seal an outgoing message. 
    * <p/>
    * @param MsgUnitRaw The source message
    * @return MsgUnitRaw
    * @exception XmlBlasterException Thrown if the message cannot be processed
    */
   public MsgUnitRaw exportMessage(MsgUnitRaw msg, MethodName action) throws XmlBlasterException {
      // dummy implementation
      return msg;

   }

   public String exportMessage(String xmlMsg) throws XmlBlasterException {
      return xmlMsg;
   }

   public byte[] exportMessage(byte[] xmlMsg) throws XmlBlasterException {
      return xmlMsg;
   }
}
