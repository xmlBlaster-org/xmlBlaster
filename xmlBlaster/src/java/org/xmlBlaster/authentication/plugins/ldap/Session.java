package org.xmlBlaster.authentication.plugins.ldap;

import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.plugins.I_Subject;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.Log;


/**
 * This implements the session AND the subject interface in the same class. 
 *
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 * @see org.xmlBlaster.authentication.plugins.ldap.LdapGateway
 */
public class Session implements I_Session, I_Subject {
   private static final String ME = "Session";

   protected Manager secMgr = null;
   protected String sessionId = null;
   protected boolean authenticated = false;

   protected final LdapGateway ldap;

   protected String loginName = null;

   public Session(Manager sm, String sessionId) throws XmlBlasterException {
      secMgr = sm;
      this.sessionId = sessionId;
      final String serverUrl = XmlBlasterProperty.get("ldap.serverUrl", "ldap://localhost:389/o=xmlBlaster,c=ORG");
      final String rootDN = XmlBlasterProperty.get("ldap.rootDN", "cn=Manager,o=xmlBlaster,c=ORG");
      final String rootPwd =  XmlBlasterProperty.get("ldap.rootPwd", "secret");
      final String loginFieldName = XmlBlasterProperty.get("ldap.loginFieldName", "cn");

      Log.info(ME, "Initializing LDAP access on ldap.serverUrl='" + serverUrl + "' with rootdn='" + rootDN  + "'. The unique uid field name in ldap should be '" + loginFieldName + "'.");
      ldap = new LdapGateway(serverUrl, rootDN, rootPwd, loginFieldName);
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
      authenticated = false;
      SecurityQos xmlQoS = new SecurityQos(xmlQoS_literal);

      loginName = xmlQoS.getUserId();
      String passwd = xmlQoS.getCredential();

      if (Log.TRACE) Log.trace(ME, "Checking password ...");
      authenticated = ldap.checkPassword(loginName, passwd);
      if (Log.TRACE) Log.trace(ME, "The password" /*+ passwd */+ " for cn=" + loginName + " is " + ((authenticated)?"":" NOT ") + " valid.");

      if (authenticated == false)
         throw new XmlBlasterException("AccessDenied", "Authentication of user " + getName() + " failed");

      return null; // no extra information
   }

   /**
    * Get the subjects login-name.
    * <p/>
    * @return String name
    */
   public String getName()
   {
      return loginName;
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
   public boolean isAuthorized(String actionKey, String key)
   {
      if (authenticated == false) {
         Log.warn(ME+".AccessDenied", "Authentication of user " + getName() + " failed");
         return false;
      }

      Log.warn(ME, "No authorization check for action='" + actionKey + "' on key='" +key + "' is implemented, access generously granted.");
      return true;
   }

   public void changeSessionId(String sessionId) throws XmlBlasterException {
      if(this.sessionId.endsWith(sessionId)) return;
      synchronized(sessionId) {
         secMgr.changeSessionId(this.sessionId, sessionId);
         this.sessionId = sessionId;
      }
   }

   public String getSessionId() {
      return sessionId;
   }

   /**
    * Enforced by interface I_Session
    */
   public I_Subject getSubject() {
      return this;
   }


   public I_Manager getManager() {
      return secMgr;
   }


   /**
    * decrypt, check, unseal ... an incomming message
    * <p/>
    * @param MessageUnit The the received message
    * @return MessageUnit The original message
    * @exception XmlBlasterException Thrown i.e. if the message has been modified
    * @see #importMessage(MessageUnit)
    */
   public MessageUnit importMessage(MessageUnit msg) throws XmlBlasterException {
      // dummy implementation
      return msg;
   }

   public String importMessage(String xmlMsg) throws XmlBlasterException
   {
      return xmlMsg;
   }

   /**
    * encrypt, sign, seal ... an outgoing message
    * <p/>
    * @param MessageUnit The source message
    * @return MessageUnit
    * @exception XmlBlasterException Thrown if the message cannot be processed
    * @see #importMessage(MessageUnit)
    */
   public MessageUnit exportMessage(MessageUnit msg) throws XmlBlasterException {
      // dummy implementation
      return msg;

   }

   public String exportMessage(String xmlMsg) throws XmlBlasterException
   {
      return xmlMsg;
   }

}
