package org.xmlBlaster.authentication.plugins.demo;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.authentication.plugins.I_ClientHelper;
import org.xmlBlaster.authentication.plugins.I_InitQos;
//import org.jutils.JUtilsException;

/**
 * Title:
 * Description:
 * Copyright:    Copyright (c) 2001
 * Company:
 * @author
 * @version 1.0
 */

public class ClientHelper implements I_ClientHelper {
   public static final String ME = "ClientHelper";
   private             byte aDemoCryptoKey = 10;

   private InitQos  initQoSWrapper = new InitQos("gui","1.0");

   public ClientHelper() {
   }

   /**
    * Called by the PluginLoader.
    * <p/>
    * @param String[] Arguments from xmlBlaster.properties:
    *                 There must be either 0 arguments or 2 arguments.
    *                 The first arguments has to be a valid user name (userId),
    *                 the second must contain the password.
    */
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

   /**
    * The client application can use this method to get I_InitQos,
    * and use it to set userId/password etc.
    */
   public I_InitQos getInitQoSWrapper()
   {
      return initQoSWrapper;
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

   private byte[] importMessage(byte[] byteArr) throws XmlBlasterException
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

   private byte[] exportMessage(byte[] byteArr) throws XmlBlasterException
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
