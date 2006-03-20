package org.xmlBlaster.authentication.plugins.demo;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.util.def.MethodName;

/**
 * Class for java clients, decrypting messages which
 * came from the corresponding security plugin. 
 * <p />
 * If for example the server security plugin crypts
 * messages with rot13, we need to decrypt it on the
 * client side with the same algorithm. This is done here.
 */
public class ClientPlugin implements I_ClientPlugin {
   public static final String ME = "ClientPlugin";
   private Global glob;

   public ClientPlugin() {
   }

   public void init(Global glob, PluginInfo pluginInfo) {
      this.glob = glob;
   }

   public String getType()
   {
      return "gui";
   }

   public String getVersion()
   {
      return "1.0";
   }

   /**
    * The client application can use this method to get a new I_SecurityQos instance,
    * and use it to set userId/password etc.
    */
   public I_SecurityQos createSecurityQos()
   {
      return new SecurityQos(this.glob); // "demo" "1.0"
   }

   public void setSessionData(String sessionData)
   {

   }

   // --- message handling ----------------------------------------------------

   /**
    * decrypt, check, unseal an incomming message. 
    * <p/>
    * @param MsgUnitRaw The the received message
    * @param MethodName The name of the method which is intercepted
    * @return MsgUnitRaw The original message
    * @exception XmlBlasterException Thrown i.e. if the message has been modified
    */
   public MsgUnitRaw importMessage(MsgUnitRaw msg, MethodName action) throws XmlBlasterException {
      // dummy implementation
      msg = new MsgUnitRaw(msg.getMsgUnit(),
                           importMessage(msg.getKey()),
                           importMessage(msg.getContent()),
                           importMessage(msg.getQos()));

      return msg;
   }

   /**
    * decrypt, check, unseal etc an incomming message. 
    * <p/>
    * @param String The the received message
    * @return String The original message
    * @exception XmlBlasterException Thrown i.e. if the message has been modified
    */
   public String importMessage(String xmlMsg) throws XmlBlasterException
   {
      if (xmlMsg==null) return null;
      return new String(crypt(xmlMsg.getBytes()));
   }

   public byte[] importMessage(byte[] byteArr) throws XmlBlasterException
   {
      return crypt(byteArr);
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

   public String exportMessage(String xmlMsg) throws XmlBlasterException
   {
      if (xmlMsg==null) return null;
//      return new String(crypt(xmlMsg.toCharArray()));
      return new String(crypt(xmlMsg.getBytes()));
    }

   public byte[] exportMessage(byte[] byteArr) throws XmlBlasterException
   {
      return crypt(byteArr);
   }

   /**
    * Well, rot13 is no encryption, but it illustrates what importMessage and
    * ExportMessage could do.
    */
   private byte[] crypt(byte[] byteArr) {
      if (byteArr==null) return null;
      byte[] newByteArr = new byte[byteArr.length];
      int cap;
      int tmp;
      for (int i=0; i<byteArr.length; i++) {
         tmp = byteArr[i];
         tmp = ((tmp >= 'A') && (tmp <= 'Z')) ? ((tmp - 'A' + 13) % 26 + 'A') : tmp;
         tmp = ((tmp >= 'a') && (tmp <= 'z')) ? ((tmp - 'a' + 13) % 26 + 'a') : tmp;

         newByteArr[i]=(byte)tmp;
      }
      return newByteArr;
   }
/*
   private char[] crypt(char[] charArr) {
      if (charArr==null) return null;
      char[] newCharArr = new char[charArr.length];
      int cap;
      int tmp;
      for (int i=0; i<charArr.length; i++) {
         tmp = charArr[i];
         tmp = ((tmp >= 'A') && (tmp <= 'Z')) ? ((tmp - 'A' + 13) % 26 + 'A') : tmp;
         tmp = ((tmp >= 'a') && (tmp <= 'z')) ? ((tmp - 'a' + 13) % 26 + 'a') : tmp;

         newCharArr[i]=(char)tmp;
      }
      return newCharArr;
   }
*/
}
