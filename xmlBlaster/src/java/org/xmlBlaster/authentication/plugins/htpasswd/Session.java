package org.xmlBlaster.authentication.plugins.htpasswd;

import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.plugins.I_Subject;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.Log;


/**
 * This implements the session AND the subject interface in the same class.
 *
 * @author <a href="mailto:cyrille@ktaland.com">Cyrille Giquello</a>.
 * @see org.xmlBlaster.authentication.plugins.htpasswd.HtPasswd
 */
public class Session
        implements      I_Session,
                                I_Subject {

   private static final String ME = "Session";

   private Subject  subject = null;
   private static Subject dummyUsr = null;

   protected Manager secMgr = null;
   protected String sessionId = null;
   protected boolean authenticated = false;

   //protected final LdapGateway htpasswd;
   protected final HtPasswd htpasswd;

   protected String loginName = null;

   public Session( Manager sm, String sessionId )
        throws XmlBlasterException {

      Log.trace(ME, "Initializing HTACCESS Session sm="+sm+", sessionId="+sessionId+".");

      secMgr = sm;
      this.sessionId = sessionId;

      // Up to now, we've a session, but no subject where it belongs to.
      // Thus, it gets a dummy, a subjet with nearly no rights.
      if (dummyUsr == null) dummyUsr = new Subject();

                htpasswd = new HtPasswd();

      /*
      final String serverUrl = XmlBlasterProperty.get("htpasswd.serverUrl", "htpasswd://localhost:389/o=xmlBlaster,c=ORG");
      final String rootDN = XmlBlasterProperty.get("htpasswd.rootDN", "cn=Manager,o=xmlBlaster,c=ORG");
      final String rootPwd =  XmlBlasterProperty.get("htpasswd.rootPwd", "secret");
      final String loginFieldName = XmlBlasterProperty.get("htpasswd.loginFieldName", "cn");

      Log.info(ME, "Initializing LDAP access on htpasswd.serverUrl='" + serverUrl + "' with rootdn='" + rootDN  + "'. The unique uid field name in htpasswd should be '" + loginFieldName + "'.");
      htpasswd = new LdapGateway(serverUrl, rootDN, rootPwd, loginFieldName);
      */

   }//Session

   /**
    * Initialize the Session. (In this case, it's a login or connect.)<br/>
    * [I_Session]
    * <p/>
    * @param String A xml-String containing the loginname, password, etc.
    * @exception XmlBlasterException Thrown (in this case) if the user doesn't
    *                                exist or the passwd is incorrect.
    */
   public String init( String securityQos_literal ) throws XmlBlasterException {
      return init(new SecurityQos(securityQos_literal));
   }

   /**
    * Initialize the Session for a login or connect call.<br/>
    * [I_Session]
    * <p/>
    * @param String The SecurityQos object containing the credentials, e.g. loginName/passwd
    * @exception XmlBlasterException Thrown (in this case) if the user doesn't
    *                                exist or the passwd is incorrect.
    */
   public String init(I_SecurityQos securityQos) throws XmlBlasterException {
      authenticated = false;
      loginName = securityQos.getUserId();
      String passwd = ((SecurityQos)securityQos).getCredential();

      if (Log.TRACE) Log.trace( ME, "Checking password ...");
      authenticated = htpasswd.checkPassword(loginName, passwd);

      if (Log.TRACE) Log.trace( ME, "Checking subject ...");
      subject = determineSubject(securityQos.getUserId(), passwd); // throws XmlBlasterException if authentication fails

      if (Log.TRACE) Log.trace( ME, "The password" /*+ passwd */+ " for " + loginName + " is " + ((authenticated)?"":" NOT ") + " valid.");

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
    *    isAuthorized("publish", "thisIsAMessageKey");
    *
    * The above line checks if this subject is permitted to >>publish<<
    * a message under the key >>thisIsAMessageKey<<
    *
    * Known action keys:
    *    publish, subscribe, get, erase, ... see XmlBlasterImpl.PUBLISH etc.
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
   /*public I_Subject getSubject() {
      return this;
   }*/
   public I_Subject getSubject() {
      return (I_Subject)subject;
   }

   public I_Manager getManager() {
      return secMgr;
   }

   /**
    * Determine which subject is specified by user/passwd
    * <p/>
    * @param String username
    * @param String password
    * @exception XmlBlasterException Thrown (in this case) if the user doesn't
    *                                exist or the passwd is incorrect.
    */
   private Subject determineSubject(String user, String passwd) throws XmlBlasterException
   {
      Subject subj;

      subj = secMgr.getSubject(user); // throws a XmlBlasterException if user is unknown
      subj.authenticate(passwd); // throws a XmlBlasterException, if the autentication fails

      return subj;
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
