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
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.jutils.log.LogChannel;


/**
 * This implements the session AND the subject interface in the same class.
 *
 * @author <a href="mailto:cyrille@ktaland.com">Cyrille Giquello</a>.
 * @see org.xmlBlaster.authentication.plugins.htpasswd.HtPasswd
 */
public class Session implements I_Session, I_Subject {

   private static final String ME = "Session";
   private final Global glob;
   private final LogChannel log;

   protected Manager secMgr = null;
   protected String sessionId = null;
   protected boolean authenticated = false;

   // no final in order to enable inheritance for new features
   // like LdapGateway, etc.
   protected HtPasswd htpasswd;

   // this is unique for the session
   protected String loginName = null;
   protected String passwd = null;

   public Session( Manager sm, String sessionId ) throws XmlBlasterException {
      this.glob = sm.getGlobal();
      this.log = this.glob.getLog("auth");
      log.trace(ME, "Initializing HTACCESS Session sm="+sm+", sessionId="+sessionId+".");

      this.secMgr = sm;
      this.sessionId = sessionId;

      this.htpasswd = new HtPasswd(sm.getGlobal());
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
      passwd = ((SecurityQos)securityQos).getCredential();

      if (log.TRACE) log.trace( ME, "Checking password ...");
      authenticated = htpasswd.checkPassword(loginName, passwd);
      if (log.TRACE) log.trace( ME, "The password" /*+ passwd */+ " for " + loginName + " is " + ((authenticated)?"":" NOT ") + " valid.");

      if (authenticated == false)
         throw new XmlBlasterException("AccessDenied", "Authentication of user " + getName() + " failed");

      return null; // no extra information
   }

   /**
    * Get the subjects login-name.
    * <p/>
    * @return String name
    */
   public String getName() {
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
    *    publish, subscribe, get, erase, ... see Constants.PUBLISH etc.
    */
   public boolean isAuthorized(MethodName actionKey, String key) {
      if (authenticated == false) {
         log.warn(ME+".AccessDenied", "Authentication of user " + getName() + " failed");
         return false;
      }

      //log.warn(ME, "No authorization check for action='" + actionKey + "' on key='" +key + "' is implemented, access generously granted.");
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

   public String importMessage(String xmlMsg) throws XmlBlasterException {
      return xmlMsg;
   }

   public byte[] importMessage(byte[] xmlMsg) throws XmlBlasterException {
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

   public String exportMessage(String xmlMsg) throws XmlBlasterException {
      return xmlMsg;
   }

   public byte[] exportMessage(byte[] xmlMsg) throws XmlBlasterException {
      return xmlMsg;
   }
}
