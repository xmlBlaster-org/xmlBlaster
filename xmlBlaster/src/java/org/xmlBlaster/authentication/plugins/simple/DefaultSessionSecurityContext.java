package org.xmlBlaster.authentication.plugins.simple;

import org.xmlBlaster.authentication.plugins.I_SecurityManager;
import org.xmlBlaster.authentication.plugins.I_SessionSecurityContext;
import org.xmlBlaster.authentication.plugins.I_SubjectSecurityContext;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.util.Log;

/**
 *
 *
 * @author  $Author: ruff $ ($Name:  $)
 * @version $Revision: 1.2 $ (State: $State) (Date: $Date: 2001/08/19 23:07:53 $)
 * Last Changes:
 *    ($Log: DefaultSessionSecurityContext.java,v $
 *    (Revision 1.2  2001/08/19 23:07:53  ruff
 *    (Merged the new security-plugin framework
 *    (
 *    (Revision 1.1.2.1  2001/08/19 09:13:48  ruff
 *    (Changed locations for security stuff, added RMI support
 *    (
 *    (Revision 1.1.2.3  2001/08/13 12:19:50  kleinertz
 *    (wkl: minor fixes
 *    (
 *    (Revision 1.1.2.2  2001/05/21 07:37:28  kleinertz
 *    (wkl: some javadoc tags removed
 *    (
 *    (Revision 1.1.2.1  2001/05/17 13:54:30  kleinertz
 *    (wkl: the first version with security framework
 *    ()
 */

public class DefaultSessionSecurityContext implements I_SessionSecurityContext {
   private static final String ME = "DefaultSessionSecurityContext";

   private              DefaultSubjectSecurityContext  subject = null;
   private              DefaultSecurityManager          secMgr = null;
   private              String                       sessionId = null;
   private              boolean                  authenticated = false;

   private static       DefaultSubjectSecurityContext dummyUsr = null;


   public DefaultSessionSecurityContext(DefaultSecurityManager sm, String sessionId) {
      secMgr = sm;
      this.sessionId = sessionId;
      // Up to now, we've a session, but no subject where it belongs to.
      // Thus, it gets a dummy, a subjet with nearly no rights.
      if (dummyUsr == null) dummyUsr = new DefaultSubjectSecurityContext();
   }


   /**
    * Initialize the SessionSecurityContext. (In this case, it's a login.)<br/>
    * [I_SessionSecurityContext]
    * <p/>
    * @param String A xml-String containing the loginname, password, etc.
    * @exception XmlBlasterException Thrown (in this case) if the user doesn't
    *                                exist or the passwd is incorrect.
    */
   public String init(String xmlQoS_literal) throws XmlBlasterException {
      authenticated = false;
      DefaultSecurityQoS xmlQoS = new DefaultSecurityQoS(xmlQoS_literal);
      subject = determineSubject(xmlQoS.getName(), xmlQoS.getPasswd()); // throws XmlBlasterException if authentication fails
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
    * [I_SessionSecurityContext]
    */
   public I_SubjectSecurityContext getSubject() {
      return (I_SubjectSecurityContext)subject;
   }


   public I_SecurityManager getSecurityManager() {
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
   private DefaultSubjectSecurityContext determineSubject(String user, String passwd) throws XmlBlasterException
   {
      DefaultSubjectSecurityContext subj;

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
