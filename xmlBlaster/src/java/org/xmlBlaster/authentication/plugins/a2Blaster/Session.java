package org.xmlBlaster.authentication.plugins.a2Blaster;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.authentication.plugins.I_Subject;
import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.a2Blaster.engine.A2BlasterException;
import org.a2Blaster.client.api.CorbaConnection;

/**
 *
 * @author  $Author: ruff $ ($Name:  $)
 * @version $Revision: 1.7 $ (State: $State) (Date: $Date: 2002/09/13 23:17:51 $)
 */

public class Session implements I_Session, I_Subject {
   private static final String                    ME = "Session";
   private static final String               baseKey = "/org/xmlBlaster/";
   private        final Manager               secMgr;
   private        final LogChannel               log;
   private              boolean        authenticated = false;
   private              String             sessionId = null;
   private              String                  name = null;   // login name
   private              String    a2BlasterSessionId = null;
   private              boolean  a2BlasterSessionCtl = false;  // who has initiated the a2Blaster login, we?


   public Session(Manager sm, String sessionId) {
      secMgr = sm;
      log = secMgr.getGlobal().getLog("a2Blaster");
      this.sessionId = sessionId;
      if (log.CALL) log.call(ME+"."+ME+"(...)=...", "-------END-------\n");
   }

   /**
    * Initialize the Session.
    * In this case, the plugin supports two kinds of authentications:<br>
    * 1. The xmlBlaster handles the whole process and hides the a2Blaster. Example:
    *    <pre>
    *       <qos>
    *          <!-- .... -->
    *          <securityService type="a2Blaster" version="1.0">
    *             <![CDATA[
    *                <user>root</user>
    *                <passwd>secrete</passwd>
    *             ]]>
    *          </securityService>
    *          <!-- .... -->
    *       </qos>
    *   </pre>
    *   <p>
    *   Result:<Br>
    *   <pre>
    *      <qos>
    *          <!-- .... -->
    *          <securityPlugin type="a2Blaster" version="a2Blaster">
    *             <sessionId>a2Blaster-Session-989340224637-0</sessionId>
    *          </securityPlugin>
    *          <!-- .... -->
    *       </qos>
    *   </pre>
    *   <p>
    * 2. On the other hand, it possible, that the client logs on the a2Blaster
    *    and uses the return a2Blaster-sessionId as proof of his/her identity
    *    for the xmlBlaster. Example:<br>
    *    <pre>
    *       <qos>
    *          <!-- .... -->
    *          <securityService type="a2Blaster" version="1.0">
    *             <sessionId>a2Blaster-Session-989340224637-0</sessionId>
    *          </securityService>
    *          <!-- .... -->
    *       </qos>
    *   </pre>
    *   <p>
    *   Result:<Br>
    *   <pre>
    *      <qos>
    *          <!-- .... -->
    *          <securityPlugin type="a2Blaster" version="a2Blaster">
    *          </securityPlugin>
    *          <!-- .... -->
    *       </qos>
    *   </pre>
    *   <p>
    * <p/>
    * @param String A xml-String containing the loginname, password, credentials etc.
    * @exception XmlBlasterException Thrown (in this case) if the user doesn't
    *                                exist, the passwd or the sessionId is incorrect.
    * implements: I_Session.init();<br>
    */
   public String init(String securityQos_literal) throws XmlBlasterException {
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
      if (log.CALL) log.call(ME+".init(String qos=...)=...", "-------START-----\n");
      String result = null;
      authenticated = false;
      name = securityQos.getUserId();

      // Ok, we have to decide, if we have to log on the a2Blaster, or if the
      // Client has done it, and we only use this session.
      a2BlasterSessionId = ((SecurityQos)securityQos).getA2BlasterSessionId();
      if (a2BlasterSessionId == null) { // we've to do the job
         a2BlasterSessionId = authenticate(((SecurityQos)securityQos).getCredential()); // throws XmlBlasterException if authentication fails
      }

      result ="   <securityPlugin type=\""+Manager.TYPE+"\" version=\""+Manager.VERSION+"\">\n"+
              "      <sessionId type=\""+Manager.TYPE+"\">"+a2BlasterSessionId+"</sessionId>\n"+
              "   </securityPlugin>\n";

      if (log.CALL) log.call(ME+".init(...)=...", "-------END-------\n");

      return result;
   }

   /**
    * Get owner of this session
    * <p/>
    * @return I_Subject The owner of this session.
    * implements: I_Session.getSubject();<br>
    */
   public I_Subject getSubject() {
      return (I_Subject)this;
   }

   /**
    *
    * implements: I_Session.getSecurityManager();<br>
    */
   public I_Manager getManager() {
      return secMgr;
   }

   /**
    * The current implementation of the user session handling (especially org.xmlBlaster.authenticate.init(...))
    * cannot provide a real sessionId when this object is created. Thus, it
    * uses a temporary id first and changes it to the real in a later step.<p>
    * The purpose of this method is to enable this functionality.<p>
    *
    * @param String The new sessionId.
    * @exception XmlBlasterException Thrown if the new sessionId is already in use.
    * @deprecated
    */
   public void changeSessionId(String sessionId) throws XmlBlasterException {
      if (log.CALL) log.call(ME+".importMessage(String sessionId="+sessionId+") [DEPRECATED]",
                             "-------START-----\n");

      if (log.CALL) log.call(ME+".importMessage() [DEPRECATED]",
                             "try to change id="+this.sessionId+" to id="+sessionId);

      if(this.sessionId.endsWith(sessionId)) return;
      synchronized(sessionId) {
         secMgr.changeSessionId(this.sessionId, sessionId);
         this.sessionId = sessionId;
      }

      if (log.CALL) log.call(ME+".importMessage(...) [DEPRECATED]",
                             "-------END-------\n");
   }

   /**
    * Get the id of this session.
    * <p>
    * @return String sessionId
    */
   public String getSessionId() {
      return sessionId;
   }

   /**
    * decrypt, check, unseal ... an incomming message
    * <p/>
    * @author wolfgang.kleinertz@epost.de
    * @param MessageUnit The the received message
    * @return MessageUnit The original message
    * @exception XmlBlasterException Thrown i.e. if the message has been modified
    * @see #importMessage(MessageUnit)
    * implements: I_Session.importMessage(MessageUnit);<br>
    */
   public MessageUnit importMessage(MessageUnit msg) throws XmlBlasterException {
      // dummy implementation
      if (log.CALL) log.call(ME+".importMessage(...)", "-------START-----\n");
      if (log.DUMP) log.dump(ME+".importMessage(...)", "in: key="+msg.xmlKey+
                             " content="+msg.content+" qos="+msg.qos);

      if (log.DUMP) log.dump(ME+".importMessage(...)", "out: key="+msg.xmlKey+
                             " content="+msg.content+" qos="+msg.qos);
      if (log.CALL) log.call(ME+".importMessage(...)=...", "-------END-------\n");

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
    * implements: I_Session.exportMessage(MessageUnit);<br>
    */
   public MessageUnit exportMessage(MessageUnit msg) throws XmlBlasterException {
      // dummy implementation
      if (log.CALL) log.call(ME+".exportMessage(...)", "-------START-----\n");
      if (log.DUMP) log.dump(ME+".exportMessage(...)", "in: key="+msg.xmlKey+
                             " content="+msg.content+" qos="+msg.qos);

      if (log.DUMP) log.dump(ME+".exportMessage(...)", "out: key="+msg.xmlKey+
                             " content="+msg.content+" qos="+msg.qos);
      if (log.CALL) log.call(ME+".exportMessage(...)=...", "-------END-------\n");

      return msg;
   }

   public String exportMessage(String xmlMsg) throws XmlBlasterException
   {
      return xmlMsg;
   }

   /**
    * Check if the user is permited (authorized) to do something
    * implements: I_Subject.isAuthorized(String, String)<br>
    */
   public boolean isAuthorized(String actionKey, String key) {
      CorbaConnection con = null;
      boolean   permitted = false;

      if (log.CALL) log.call(ME+".isAuthorized(String actionKey="+actionKey+
                             ", String key="+key+")", "-------START-----\n");

      try {
         con = secMgr.getA2Blaster();

         // NOTE (W. Kleinertz): This is not much more than a dummy implementation,
         //      beacuse access is only granted if the message key and the key
         //      stored in the a2Blaster match exactly!!!
         //
         // Solution: The a2Blaster stores only query-keys (like XPath).
         //      The results of a query, initiated by a client must be used as
         //      input for a query using the a2Blaster query key. The result
         //      is the intersection of requested set and the permitted set of messages.
         permitted = con.isAuthorized(a2BlasterSessionId, baseKey+actionKey+"/"+key);
      }
      catch (XmlBlasterException xe) {
         log.error(ME + ".error", "Couldn't check if " + getName() + " is permitted to " +
                                  actionKey + " " + key + "! Reason: No connection to the a2Blaster! Assertion: " + xe);
         if (log.CALL) log.call(ME+".isAuthorized(...)=false", "-------END-------\n");

         return false;
      }
      catch (A2BlasterException ae) {
         log.error(ME + ".error", "Couldn't check if " + getName() + " is permitted to " +
                                  actionKey + " " + key + "! Assertion: " + ae);
         if (log.CALL) log.call(ME+".isAuthorized(...)=false", "-------END-------\n");

         return false;
      }

      if (log.CALL) log.call(ME+".isAuthorized(...)=true", "-------END-------\n");
      return true; // dummy implementation;
   }


   /**
    * Return the subjects name.
    * (Implementation of: I_Subject.getName)<br>
    * <p>
    * @return String login name
    */
   public String getName() {
      if (log.CALL) log.call(ME+".getName()", "-------START-----\n");

      if (name == null) {
         // ask the a2Blaster
         try {
            CorbaConnection con = null;
            ClientInfo userInfo = null;

            con = secMgr.getA2Blaster();
            userInfo = new ClientInfo(con.getUserInfoAsXml(a2BlasterSessionId));
            name = userInfo.getName();
         }
         catch (XmlBlasterException xe) {
            log.error(ME + ".brokenConnection", "a2Blaster connection doesn't respond! Reason: "+xe);
            return null;
         }
         catch (A2BlasterException ae) {
            log.error(ME + ".accessDenied", "Unknown a2Blaster session! Can't fetch user info! Reason: "+ae);
            return null;
         }
      }

      if (log.CALL) log.call(ME+".getName()="+name, "-------END-------\n");

      return name;
   }

   /**
    * Check the subjects identity using the a2Blaster
    * <p/>
    * @param String The password.
    * @exception XmlBlasterException Thrown if the check fails. (internal a2Blaster exceptions, connection problems, wrong user/passwd)
    */
   private String authenticate(String passwd) throws XmlBlasterException{
      a2BlasterSessionCtl = true;
      CorbaConnection con = null;
      String    sessionId = null;

      if (log.CALL) log.call(ME+".authenticate(String passwd=[passwd])", "-------START-----\n");
      try {
         con = secMgr.getA2Blaster();
         sessionId = con.login(name, passwd);
      }
      catch (XmlBlasterException xe) {
         log.error(ME + ".brokenConnection", "a2Blaster connection doesn't respond! Reason: "+xe);
         throw xe;
      }
      catch (A2BlasterException ae) {
         log.error(ME + ".accessDenied", "Login incorrect! Reason: "+ae);
         throw new XmlBlasterException(ME + ".accessDenied", "Login incorrect! Reason: "+ae);
      }
      authenticated = true;

      if (log.CALL) log.call(ME+".authenticate(...)=sessionId", "-------END-------\n");

      return sessionId;
   }

   /**
    * Who initiated the a2Blaster login? The user or this xmlBlaster plugin
    * on behalf of the user?
    * <p\>
    * @retun boolean This plugin did the job.
    */
   boolean a2BlasterSessionCtlEnabled() {
      return a2BlasterSessionCtl;
   }

   /**
    * Cleanup
    * <p/>
    * @param String qos_literals; useful to check if it's the real user and not
    *               anyone else who triggered the disconnect. (not implemented in this version)
    */
   void destroy(String qos_literal) {
      if (log.CALL) log.call(ME+".destroy(String qos_literal="+qos_literal+")",
                             "-------START-----\n");
      if(a2BlasterSessionCtl) { // do the logout, only if we (not the client) did the login
         try {
            CorbaConnection con = secMgr.getA2Blaster();
            con.logout(a2BlasterSessionId);
         }
         catch (XmlBlasterException xe) {
            log.error(ME + ".error", "Unable to logout user " + getName() +
                                     " (a2BlasterSessionId: "+ a2BlasterSessionId +
                                     "); Assertion: " + xe);
            return;
         }
         catch (A2BlasterException ae) {
            log.error(ME + ".accessDenied", "Logout impossible! Reason: "+ae);
            return;
         }
      }
      if (log.CALL) log.call(ME+".destroy(...)", "-------END-------\n");
   }
}
