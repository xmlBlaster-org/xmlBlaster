package org.xmlBlaster.authentication.plugins.demo;

import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.plugins.I_Subject;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.authentication.plugins.simple.SecurityQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.MsgUnitRaw;
//import org.xmlBlaster.authentication.plugins.ReversibleCrypt;
import org.xmlBlaster.util.def.MethodName;

/**
 * @author Wolfgang Kleinertz
 */

public class Session implements I_Session {
   private static final String ME = "Session";

   private              Subject       subject = null;
   private              Manager        secMgr = null;
   private              String          sessionId = null;
   private              boolean     authenticated = false;

   private              Subject      dummyUsr = null;

   private              byte       aDemoCryptoKey = 10;
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
    * decrypt, check, unseal an incoming message. 
    * <p/>
    * @param MsgUnitRaw The the received message
    * @return MsgUnitRaw The original message
    * @exception XmlBlasterException Thrown i.e. if the message has been modified
    */
   public MsgUnitRaw importMessage(MsgUnitRaw msg, MethodName method) throws XmlBlasterException {
      // dummy implementation
      msg = new MsgUnitRaw(msg.getMsgUnit(),
                           importMessage(msg.getKey()),
                           importMessage(msg.getContent()),
                           importMessage(msg.getQos()));

      //secMgr.getGUI().printKey(msg.getKey());
      secMgr.getGUI().printQoS(msg.getQos());
      secMgr.getGUI().printContent(msg.getContentStr());

      return msg;
   }

   /**
    * @see #importMessage(MsgUnitRaw, MethodName)
    */
   public String importMessage(String xmlMsg) throws XmlBlasterException
   {
      if (xmlMsg==null) return null;
      String ret=null;

      //ret = new String(crypter.decrypt(xmlMsg.getBytes()));
      ret = crypter.decrypt(xmlMsg);

      return ret;
   }

   /**
    * @see #importMessage(MsgUnitRaw, MethodName)
    */
   public byte[] importMessage(byte[] byteArr) throws XmlBlasterException
   {
      secMgr.getGUI().printInputStream(new String(byteArr));
      byte[] newByteArr = crypter.decrypt(byteArr);
      secMgr.getGUI().printOutputStream(new String(newByteArr));

      return newByteArr;
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
      msg = new MsgUnitRaw(msg.getMsgUnit(),
                           exportMessage(msg.getKey()),
                           exportMessage(msg.getContent()),
                           exportMessage(msg.getQos()));

      return msg;

   }

   /**
    * @see #exportMessage(MsgUnitRaw, MethodName)
    */
   public String exportMessage(String xmlMsg) throws XmlBlasterException
   {
      if (xmlMsg==null) return null;
      String ret=null;

      ret = new String(crypter.crypt(xmlMsg.getBytes()));

      return ret;
   }

   /**
    * @see #exportMessage(MsgUnitRaw, MethodName)
    */
   public byte[] exportMessage(byte[] byteArr) throws XmlBlasterException
   {
      secMgr.getGUI().printOutputStream(new String(byteArr));
      byte[] newByteArr = crypter.crypt(byteArr);
      secMgr.getGUI().printInputStream(new String(newByteArr));

      return newByteArr;
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
