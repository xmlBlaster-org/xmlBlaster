package org.xmlBlaster.authentication.plugins.simple;

import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.plugins.I_Subject;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.util.Log;

/**
 * @author  $Author: ruff $ ($Name:  $)
 *
 * @author Wolfgang Kleinertz
 */

public class Session implements I_Session {
   private static final String ME = "Session";

   private              Subject  subject = null;
   private              Manager          secMgr = null;
   private              String                       sessionId = null;
   private              boolean                  authenticated = false;

   private static       Subject dummyUsr = null;


   public Session(Manager sm, String sessionId) {
      secMgr = sm;
      this.sessionId = sessionId;
      // Up to now, we've a session, but no subject where it belongs to.
      // Thus, it gets a dummy, a subjet with nearly no rights.
      if (dummyUsr == null) dummyUsr = new Subject();
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
      InitQos xmlQoS = new InitQos(xmlQoS_literal);
      subject = determineSubject(xmlQoS.getUserId(), xmlQoS.getCredential()); // throws XmlBlasterException if authentication fails
      authenticated = true;

      return null; // no extra information
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
    *
    * [I_Session]
    */
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
