package org.xmlBlaster.authentication.plugins.demo;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
//import org.jutils.JUtilsException;

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

   public ClientPlugin() {
   }

   public String getType()
   {
      return "gui";
   }

   public String getVersion()
   {
      return "1.0";
   }

   /*
    * Called by the PluginLoader.
    * <p/>
    * @param String[] Arguments from xmlBlaster.properties:
    *                 There must be either 0 arguments or 2 arguments.
    *                 The first arguments has to be a valid user name (userId),
    *                 the second must contain the password.
   public void init(String[] param) throws XmlBlasterException
   {
      Log.trace(ME+".init()", "-------START--------\n");
      for(int i=0;i<param.length;i++) {
         switch (i) {
         case 0:  {
                     initQoSWrapper.setUserId(param[i]);
                     break;
                  }
         case 1:  {
                     initQoSWrapper.setCredential(param[i]);
                     break;
                  }
         default: {
                     Log.warn(ME+".init()", "Unexpected option! option="+param[i]);
                     break;
                  }
         }
      }
      Log.trace(ME+".init()", "-------END--------\n");
   }
    */

   /**
    * The client application can use this method to get a new I_SecurityQos instance,
    * and use it to set userId/password etc.
    */
   public I_SecurityQos getSecurityQos()
   {
      return new SecurityQos(); // "demo" "1.0"
   }

   public void setSessionData(String sessionData)
   {

   }

   // --- message handling ----------------------------------------------------

   /**
    * decrypt, check, unseal ... an incomming message
    * <p/>
    * @param MessageUnit The the received message
    * @return MessageUnit The original message
    * @exception XmlBlasterException Thrown i.e. if the message has been modified
    * @see #importMessage(String)
    */
   public MessageUnit importMessage(MessageUnit msg) throws XmlBlasterException {
      // dummy implementation
      msg.xmlKey = importMessage(msg.xmlKey);
      msg.qos = importMessage(msg.qos);
      msg.content = importMessage(msg.content);

      return msg;
   }

   /**
    * decrypt, check, unseal ... an incomming message
    * <p/>
    * @param String The the received message
    * @return String The original message
    * @exception XmlBlasterException Thrown i.e. if the message has been modified
    * @see #importMessage(MessageUnit)
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
