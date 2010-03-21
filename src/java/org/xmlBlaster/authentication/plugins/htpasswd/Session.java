/*------------------------------------------------------------------------------
Name:           Session.java
Project:        xmlBlaster
Comment:
Author:         @author <a href="mailto:cyrille@ktaland.com">Cyrille Giquello</a>
changed:        gnovak@avitech.de 2002 06 14
-----------------------------------------------------------------------------*/


package org.xmlBlaster.authentication.plugins.htpasswd;

import org.xmlBlaster.authentication.plugins.CryptDataHolder;
import org.xmlBlaster.authentication.plugins.DataHolder;
import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.plugins.I_Subject;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.authentication.plugins.SessionHolder;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.MsgUnitRaw;

import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;


/**
 * This implements the session AND the subject interface in the same class
 * and supports simple authorization.
 * <p>Example password configuration:</p> 
 * <pre>
 * guest:yZ24stvIel1j6:connect,disconnect,publish(tennis;sailing)
 * admin:yZ24stvIel1j6:!erase
 * other:yZ24stvIel1j6:! subscribe,unSubscribe
 * all:yZ24stvIel1j6::
 * </pre>
 * [userName] : [cryptedPassword] : [optional authorization]
 *
 * @author <a href="mailto:cyrille@ktaland.com">Cyrille Giquello</a>.
 * @author <a href="mailto:mr@marcelruff.info">Marcel Ruff</a>.
 * @see org.xmlBlaster.authentication.plugins.htpasswd.HtPasswd
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/security.htpasswd.html">The security.htpasswd requirement</a>
 */
public class Session implements I_Session, I_Subject {

   private String ME = "Session";
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
      
      this.ME = "Session-" + getManager().getType() + "," + getManager().getVersion();

      this.htpasswd = new HtPasswd(this.glob);
   }

   public ConnectQosServer init(ConnectQosServer connectQos, Map map) throws XmlBlasterException {
      return connectQos;
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
      this.passwd = securityQos.getCredential();

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
          this.passwd.equals(securityQos.getCredential()) )
         return true;
      
      return false;
   }

   public String getName() {
      return this.loginName;
   }

   public boolean isAuthorized(SessionHolder sessionHolder, DataHolder dataHolder) {
      if (this.authenticated == false) {
         log.warning("Authentication of user " + getName() + " failed");
         return false;
      }
      
      if (!this.htpasswd.isAuthorized(sessionHolder, dataHolder)) return false;
      
      /*
      if (sessionHolder.getSessionInfo().getSessionName().getLoginName().equals("jackTheSubscriber") &&
          (dataHolder.getAction().equals(MethodName.PUBLISH) ||
          dataHolder.getAction().equals(MethodName.PUBLISH_ARR) ||
          dataHolder.getAction().equals(MethodName.PUBLISH_ONEWAY))) {
         log.warning("Reject publish attempt by " + sessionHolder.getSessionInfo().getSessionName().getAbsoluteName());
         return false;
      }
      */
      
      /*
      // Is buggy: Currently this is the SocketDrivers AddressServer singleton
      // and has no client specific address informations: 
      if (sessionHolder.getAddressServer().getRemoteAddress() != null &&
            sessionHolder.getAddressServer().getRemoteAddress() instanceof org.xmlBlaster.util.protocol.socket.SocketUrl) {
         org.xmlBlaster.util.protocol.socket.SocketUrl url =
            (org.xmlBlaster.util.protocol.socket.SocketUrl)sessionHolder.getAddressServer().getRemoteAddress();
         // "socket://127.0.0.2:35335"
         String hostname = url.getHostname();
         int port = url.getPort();
         log.severe("DEBUG ONLY: Client for action='" + dataHolder.getAction() + "' coming from " + url.toString());
      }
      */
      
      if (log.isLoggable(Level.FINER))
         log.finer("Action='" + dataHolder.getAction() + "' on key='" +dataHolder.getKeyOid() + "' access granted.");
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

   public I_Subject getSubject() {
      return this;
   }

   public I_Manager getManager() {
      return secMgr;
   }

   public MsgUnitRaw importMessage(CryptDataHolder dataHolder) throws XmlBlasterException {
      //MethodName methodName = dataHolder.getAction();
      //Map map = dataHolder.getClientProperties();
      //log.fine("Entering " + methodName + " isReturn=" + dataHolder.isReturnValue());
      return dataHolder.getMsgUnitRaw();
   }

   public MsgUnitRaw exportMessage(CryptDataHolder dataHolder) throws XmlBlasterException {
      /*
      // TODO Verify if the subscription clientProperties are proper handled for
      // - persistent subscribe with server restart
      // - swapped callback messages
      if (MethodName.UPDATE.equals(dataHolder.getAction())) {
         // Callback update() call
         Map clientProperties = dataHolder.getClientProperties(); // subscribeQos.getClientProperties()
         if (clientProperties != null) {
            ClientProperty clp = (ClientProperty)clientProperties.get("_xslTransformationFileName");
            String xslFile = clp.getStringValue();
            log.info("Implementation of XSL transformation with '" + xslFile + "' is missing");
         }
      }
      */
      return dataHolder.getMsgUnitRaw();
   }

   @Override
   public String interceptExeptionByAuthorizer(Throwable throwable,
		SessionHolder sessionHolder, DataHolder dataHolder) {
	  return null;
   }
}
