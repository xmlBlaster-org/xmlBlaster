package org.xmlBlaster.authentication.plugins.demo;

import org.xmlBlaster.authentication.plugins.CryptDataHolder;
import org.xmlBlaster.authentication.plugins.DataHolder;
import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.plugins.I_Subject;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.authentication.plugins.SessionHolder;
import org.xmlBlaster.authentication.plugins.simple.SecurityQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnitRaw;
//import org.xmlBlaster.authentication.plugins.ReversibleCrypt;

/**
 * @author Wolfgang Kleinertz
 */

public class Session implements I_Session {
   private              Subject       subject = null;
   private              Manager        secMgr = null;
   private              String          sessionId = null;
   private              boolean     authenticated = false;

   private              Subject      dummyUsr = null;

   private ReversibleCrypt crypter = new ReversibleCrypt();

   public Session(Manager sm, String sessionId) {
      secMgr = sm;
      this.sessionId = sessionId;
      // Up to now, we've a session, but no subject where it belongs to.
      // Thus, it gets a dummy, a subjet with nearly no rights.
      if (dummyUsr == null) dummyUsr = new Subject(secMgr.getGUI());
   }

   /**
    * Initialize the SessionSecurityContext. (In this case, it's a login.)<br/>
    * [I_Session]
    * <p/>
    * @param String A xml-String containing the loginname, password, etc.
    * @exception XmlBlasterException Thrown (in this case) if the user doesn't
    *                                exist or the passwd is incorrect.
    */
   public String init(String securityQos_literal) throws XmlBlasterException {
      return init(new SecurityQos(secMgr.getGlobal(), securityQos_literal));
   }


   /**
    * Initialize the Session for a login or connect call. 
    * <p/>
    * @param String The SecurityQos object containing the credentials, e.g. loginName/passwd
    * @exception XmlBlasterException Thrown (in this case) if the user doesn't
    *                                exist or the passwd is incorrect.
    */
   public String init(I_SecurityQos securityQos) throws XmlBlasterException {
      this.authenticated = false;
      this.subject = determineSubject(securityQos.getUserId(), ((org.xmlBlaster.authentication.plugins.demo.SecurityQos)securityQos).getCredential()); // throws XmlBlasterException if authentication fails
      this.authenticated = true;

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
         determineSubject(securityQos.getUserId(), ((org.xmlBlaster.authentication.plugins.demo.SecurityQos)securityQos).getCredential());
         return true;
      }
      catch (XmlBlasterException e) {
         return false;
      }
   }

   public void changeSecretSessionId(String sessionId) throws XmlBlasterException {
      if(this.sessionId.equals(sessionId)) return;
      synchronized(sessionId) {
         secMgr.changeSecretSessionId(this.sessionId, sessionId);
         this.sessionId = sessionId;
      }
   }

   public String getSecretSessionId() {
      return sessionId;
   }

   /**
    *
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
      subject.getGui().printAction(dataHolder.getAction());
      subject.getGui().printKey(dataHolder.getKeyOid());
      subject.getGui().printName(subject.getName());
      return subject.getGui().getAccessDecision(); // dummy implementation;
//      return true;
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
    * decrypt, check, unseal an incoming message. 
    * <p/>
    * @param MsgUnitRaw The the received message
    * @return MsgUnitRaw The original message
    * @exception XmlBlasterException Thrown i.e. if the message has been modified
    */
   public MsgUnitRaw importMessage(CryptDataHolder dataHolder) throws XmlBlasterException {
      MsgUnitRaw msg = dataHolder.getMsgUnitRaw();

      if (dataHolder.getAction() == null)
         return msg;

      if (dataHolder.getAction().wantsMsgArrArg()) { // PUBLISH
         secMgr.getGUI().printQoS(msg.getQos());
         secMgr.getGUI().printContent(msg.getContentStr());
      }   
      
      msg = new MsgUnitRaw(msg.getMsgUnit(),
                           importMessage(msg.getKey()),
                           importMessage(msg.getContent()),
                           importMessage(msg.getQos()));
   
      if (dataHolder.getAction().wantsMsgArrArg()) {
         secMgr.getGUI().printQoS(msg.getQos());
         secMgr.getGUI().printContent(msg.getContentStr());
      }   
      return msg;
   }

   /**
    * @see #importMessage(CryptDataHolder)
    */
   private String importMessage(String xmlMsg) throws XmlBlasterException {
      if (xmlMsg==null) return null;
      return crypter.decrypt(xmlMsg);
   }

   /**
    * @see #importMessage(CryptDataHolder)
    */
   private byte[] importMessage(byte[] byteArr) throws XmlBlasterException {
      if (byteArr==null || byteArr.length == 0) return new byte[0];
      return crypter.decrypt(byteArr);
   }

   /**
    * encrypt, sign, seal an outgoing message. 
    * <p/>
    * @param MsgUnitRaw The source message
    * @return MsgUnitRaw
    * @exception XmlBlasterException Thrown if the message cannot be processed
    */
   public MsgUnitRaw exportMessage(CryptDataHolder dataHolder) throws XmlBlasterException {
      MsgUnitRaw msg = dataHolder.getMsgUnitRaw();

      if (dataHolder.getAction() == null)
         return msg;

      if (dataHolder.getAction().wantsMsgArrArg()) { // PUBLISH
         secMgr.getGUI().printQoS(msg.getQos());
         secMgr.getGUI().printContent(msg.getContentStr());
      }

      msg = new MsgUnitRaw(msg.getMsgUnit(),
                           exportMessage(msg.getKey()),
                           exportMessage(msg.getContent()),
                           exportMessage(msg.getQos()));

      if (dataHolder.getAction().wantsMsgArrArg()) {
         secMgr.getGUI().printQoS(msg.getQos());
         secMgr.getGUI().printContent(msg.getContentStr());
      }   
      return msg;
   }

   /**
    * @see #exportMessage(CryptDataHolder)
    */
   private String exportMessage(String xmlMsg) throws XmlBlasterException {
      if (xmlMsg==null) return null;
      return new String(crypter.crypt(xmlMsg.getBytes()));
   }

   /**
    * @see #exportMessage(CryptDataHolder)
    */
   private byte[] exportMessage(byte[] byteArr) throws XmlBlasterException {
      if (byteArr==null || byteArr.length == 0) return new byte[0];
      return crypter.crypt(byteArr);
   }
}

/**
 * A totally unsecure reversible crypt algorythm
 */
class ReversibleCrypt {

   /**
    * Rotates the chars 23 forward, the next time called
    * it rotates again for 23 chars, so we have the
    * decrypted string again
    */
   private byte[] rot13crypt(byte[] byteArr) {
      if (byteArr==null) return null;
      byte[] newByteArr = new byte[byteArr.length];
      int cap;
      int tmp;
      for (int i=0; i<byteArr.length; i++) {
         tmp = byteArr[i];
         cap = tmp & 32;
         tmp &= ~cap;
         tmp = ((tmp >= 'A') &&
                (tmp <= 'Z') ?
                ((tmp - 'A' + 13) % 26 + 'A') : tmp) | cap;

         newByteArr[i]=(byte)tmp;
      }
      return newByteArr;
   }

   byte[] crypt(byte[] dc2Value)
   {
     return rot13crypt(dc2Value);
   }

   byte[] decrypt(byte[] dc2Value)
   {
     return rot13crypt(dc2Value);
   }
   String crypt(String dc2Value)
   {
     return new String(rot13crypt(dc2Value.getBytes()));
   }
   String decrypt(String dc2Value)
   {
     return new String(rot13crypt(dc2Value.getBytes()));
   }
}
