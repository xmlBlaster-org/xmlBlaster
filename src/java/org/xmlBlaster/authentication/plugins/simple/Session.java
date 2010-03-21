package org.xmlBlaster.authentication.plugins.simple;

import java.util.Map;

import org.xmlBlaster.authentication.plugins.CryptDataHolder;
import org.xmlBlaster.authentication.plugins.DataHolder;
import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.plugins.I_Subject;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.authentication.plugins.SessionHolder;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnitRaw;

/**
 * @author  $Author: laghi $ ($Name:  $)
 *
 * @author Wolfgang Kleinertz
 */

public class Session implements I_Session {

   private Subject  subject = null;
   private Manager secMgr = null;
   private String sessionId = null;
   private boolean authenticated = false;

   private Subject dummyUsr = null;


   public Session(Manager sm, String sessionId) {
      secMgr = sm;

      this.sessionId = sessionId;
      // Up to now, we've a session, but no subject where it belongs to.
      // Thus, it gets a dummy, a subjet with nearly no rights.
      if (dummyUsr == null) dummyUsr = new Subject(secMgr.getGlobal());
   }

   /**
	* @see I_Session#init(ConnectQosServer, Map)
	*/
   public ConnectQosServer init(ConnectQosServer connectQos, Map map) throws XmlBlasterException {
      //this.connectQos = connectQos;
      return connectQos;
   }
   
   /**
    * Initialize the Session for a login or connect call. 
    * <p/>
    * @param securityQos Containing the credentials, e.g. loginName/passwd
    * @exception XmlBlasterException Thrown (in this case) if the user doesn't
    *                                exist or the passwd is incorrect.
    */
   public String init(I_SecurityQos securityQos) throws XmlBlasterException {
      authenticated = false;
      subject = determineSubject(securityQos.getUserId(), securityQos.getCredential()); // throws XmlBlasterException if authentication fails
      authenticated = true;

      return null; // no extra information
   }

   /**
    * @see I_Session#verify(I_SecurityQos)
    */
   public boolean verify(I_SecurityQos securityQos) {
      if (!this.authenticated)
         return false;

      try {
         // throws XmlBlasterException if authentication fails
         determineSubject(securityQos.getUserId(), securityQos.getCredential());
         return true;
      }
      catch (XmlBlasterException e) {
         return false;
      }
   }

   public void changeSecretSessionId(String sessionId) throws XmlBlasterException {
      if(this.sessionId.endsWith(sessionId)) return;
      synchronized(sessionId) {
         secMgr.changeSecretSessionId(this.sessionId, sessionId);
         this.sessionId = sessionId;
      }
   }

   public String getSecretSessionId() {
      return sessionId;
   }

   /**
    * [I_Session]
    */
   public I_Subject getSubject() {
      return subject;
   }


   public I_Manager getManager() {
      return secMgr;
   }

   /**
    * Check if the user is permited (authorized) to do something
    */
   public boolean isAuthorized(SessionHolder sessionHolder, DataHolder dataHolder) {
      //System.out.println("### User: "+getName()+" is permitted to "+actionKey+" "+key+" ###");
      return true; // dummy implementation;
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

   public MsgUnitRaw importMessage(CryptDataHolder dataHolder) throws XmlBlasterException {
      return dataHolder.getMsgUnitRaw();
   }

   public MsgUnitRaw exportMessage(CryptDataHolder dataHolder) throws XmlBlasterException {
      return dataHolder.getMsgUnitRaw();
   }

   @Override
   public String interceptExeptionByAuthorizer(Throwable throwable,
		SessionHolder sessionHolder, DataHolder dataHolder) {
      return null;
   }
}
