package org.xmlBlaster.authentication.plugins.demo;

import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.plugins.I_Subject;
import org.xmlBlaster.authentication.plugins.simple.InitQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.util.Log;
//import org.xmlBlaster.authentication.plugins.ReversibleCrypt;

/**
 * @author Wolfgang Kleinertz
 */

public class Session implements I_Session {
   private static final String ME = "Session";

   private              Subject       subject = null;
   private              Manager  secMgr = null;
   private              String          sessionId = null;
   private              boolean     authenticated = false;

   private static       Subject      dummyUsr = null;

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
   public String init(String xmlQoS_literal) throws XmlBlasterException {
      authenticated = false;
      InitQos xmlQoS = new InitQos(xmlQoS_literal);
      subject = determineSubject(xmlQoS.getName(), xmlQoS.getPasswd()); // throws XmlBlasterException if authentication fails
      authenticated = true;

      return null; // no extra information
   }

   public void changeSessionId(String sessionId) throws XmlBlasterException {
      if(this.sessionId.equals(sessionId)) return;
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
      msg.xmlKey = importMessage(msg.xmlKey);
      //secMgr.getGUI().printKey(msg.xmlKey);
      msg.qos = importMessage(msg.qos);
      secMgr.getGUI().printQoS(msg.qos);
      msg.content = importMessage(msg.content);
      secMgr.getGUI().printContent(new String(msg.content));
      return msg;
   }

   public String importMessage(String xmlMsg) throws XmlBlasterException
   {
      if (xmlMsg==null) return null;
      String ret=null;

      //ret = new String(crypter.decrypt(xmlMsg.getBytes()));
      ret = crypter.decrypt(xmlMsg);

      return ret;
   }

   private byte[] importMessage(byte[] byteArr) throws XmlBlasterException
   {
      secMgr.getGUI().printInputStream(new String(byteArr));
      byte[] newByteArr = crypter.decrypt(byteArr);
      secMgr.getGUI().printOutputStream(new String(newByteArr));

      return newByteArr;
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
      msg.xmlKey = exportMessage(msg.xmlKey);
      msg.qos = exportMessage(msg.qos);
      msg.content = exportMessage(msg.content);

      return msg;

   }

   public String exportMessage(String xmlMsg) throws XmlBlasterException
   {
      if (xmlMsg==null) return null;
      String ret=null;

      ret = new String(crypter.crypt(xmlMsg.getBytes()));

      return ret;
   }

   private byte[] exportMessage(byte[] byteArr) throws XmlBlasterException
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
