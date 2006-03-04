/*------------------------------------------------------------------------------
Name:           Session.java
Project:        xmlBlaster
Comment:
Author:         @author <a href="mailto:cyrille@ktaland.com">Cyrille Giquello</a>
changed:        gnovak@avitech.de 2002 06 14
-----------------------------------------------------------------------------*/


package org.xmlBlaster.authentication.plugins.htpasswd;

import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.plugins.I_Subject;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.MsgUnitRaw;
import java.util.logging.Logger;
import java.util.logging.Level;


/**
 * This implements the session AND the subject interface in the same class.
 *
 * @author <a href="mailto:cyrille@ktaland.com">Cyrille Giquello</a>.
 * @see org.xmlBlaster.authentication.plugins.htpasswd.HtPasswd
 */
public class Session implements I_Session, I_Subject {

   private static final String ME = "Session";
   private final Global glob;
   private static Logger log = Logger.getLogger(Session.class.getName());

   protected Manager secMgr = null;
   protected String secretSessionId = null;
   protected boolean authenticated = false;

   // no final in order to enable inheritance for new features
   // like LdapGateway, etc.
   protected HtPasswd htpasswd;

   // this is unique for the session
   protected String loginName;
   protected String passwd;

   public Session( Manager sm, String sessionId ) throws XmlBlasterException {
      this.glob = (sm.getGlobal() == null) ? Global.instance() : sm.getGlobal();

      log.fine("Initializing HTACCESS Session sm="+sm+", sessionId="+sessionId+".");

      this.secMgr = sm;
      this.secretSessionId = sessionId;

      this.htpasswd = new HtPasswd(this.glob);
   }

   /**
    * Initialize the Session. (In this case, it's a login or connect.)<br/>
    * [I_Session]
    * <p/>
    * @param String A xml-String containing the loginname, password, etc.
    * @exception XmlBlasterException Thrown (in this case) if the user doesn't
    *                                exist or the passwd is incorrect.
    */
   public String init( String securityQos_literal ) throws XmlBlasterException {
      return init(new SecurityQos(this.glob, securityQos_literal));
   }

   /**
    * Initialize the Session for a login or connect call. 
    * <br/>
    * [I_Session]
    * <p/>
    * @param String The SecurityQos object containing the credentials, e.g. loginName/passwd
    * @exception XmlBlasterException Thrown (in this case) if the user doesn't
    *                                exist or the passwd is incorrect.
    */
   public String init(I_SecurityQos securityQos) throws XmlBlasterException {
      if (securityQos == null) {
         throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED, ME, "Authentication failed, due to missing security QoS");
      }
      this.authenticated = false;
      this.loginName = securityQos.getUserId();
      this.passwd = ((SecurityQos)securityQos).getCredential();

      if (this.loginName == null || this.passwd == null) {
         throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED, ME, "Authentication of user " + getName() + " failed, you've passed an illegal login name or password");
      }

      if (log.isLoggable(Level.FINE)) log.fine( "Checking password ...");
      this.authenticated = this.htpasswd.checkPassword(this.loginName, this.passwd);
      if (log.isLoggable(Level.FINE)) log.fine( "The password" /*+ this.passwd */+ " for " + this.loginName + " is " + ((this.authenticated)?"":" NOT ") + " valid.");

      if (!this.authenticated)
         throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED, ME, "Authentication of user " + getName() + " failed");

      return null; // no extra information
   }

   /**
    * @see I_Session#verify(I_SecurityQos)
    */
   public boolean verify(I_SecurityQos securityQos) {
      if (!this.authenticated)
         return false;

      if (this.loginName.equals(securityQos.getUserId()) &&
          this.passwd.equals(((SecurityQos)securityQos).getCredential()) )
         return true;
      
      return false;
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
      if (this.authenticated == false) {
         log.warning("Authentication of user " + getName() + " failed");
         return false;
      }

      //log.warn(ME, "No authorization check for action='" + actionKey + "' on key='" +key + "' is implemented, access generously granted.");
      return true;
   }

   public void changeSecretSessionId(String sessionId) throws XmlBlasterException {
      if(this.secretSessionId.endsWith(sessionId)) return;
      synchronized(this) {
         secMgr.changeSecretSessionId(this.secretSessionId, sessionId);
         this.secretSessionId = sessionId;
      }
   }

   public String getSecretSessionId() {
      return secretSessionId;
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
    * decrypt, check, unseal an incomming message. 
    * <p/>
    * @param MsgUnitRaw The the received message
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
