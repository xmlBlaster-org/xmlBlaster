/*------------------------------------------------------------------------------
Name:      CallbackEmailDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using email
Version:   $Id: CallbackEmailDriver.java,v 1.11 2001/11/23 17:57:14 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.email;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.CallbackAddress;

import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;


/**
 * Sends a MessageUnit back to a client using Email.
 * <p>
 * One instance of this for each client callback is used
 *
 * @author $Author: ruff $
 */
public class CallbackEmailDriver implements I_CallbackDriver
{
   private String ME = "CallbackEmailDriver";
   private CallbackAddress callbackAddress = null;

   /** Get a human readable name of this driver */
   public String getName()
   {
      return ME;
   }


   /**
    * @param  callbackAddress Contains the email TO: address
    */
   public void init(CallbackAddress callbackAddress)
   {
      this.callbackAddress = callbackAddress;
   }


   /**
    * This sends the update to the client.
    */
   public final void sendUpdate(ClientInfo clientInfo, MessageUnitWrapper msgUnitWrapper, MessageUnit[] messageUnitArr) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "xmlBlaster.update(" + msgUnitWrapper.getUniqueKey() + ") to " + clientInfo.toString());
      try {
         String smtpHost = XmlBlasterProperty.get("EmailDriver.smtpHost", "localhost");
         String from = XmlBlasterProperty.get("EmailDriver.from", "xmlblast@localhost"); //clientInfo.getLoginName();
         String to = callbackAddress.getAddress();

         // Get system properties
         Properties props = System.getProperties();

         // Setup mail server
         props.put("mail.smtp.host", smtpHost);

         // Get session
         Session session = Session.getDefaultInstance(props, null);

         // Define message
         MimeMessage message = new MimeMessage(session);
         message.setFrom(new InternetAddress(from));
         message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
         message.setSubject("XmlBlaster Generated Email");
         String text = getMailBody(msgUnitWrapper, messageUnitArr);
         message.setText(text);
         Log.info(ME + ".sendUpdate", "Sending email from " + from + " to " + to + ", smtpHost=" + smtpHost);
         Log.info(ME + ".sendUpdate", "\n"+text);
         // Send message
         Transport.send(message);
      } catch (Exception e) {
         String str = "Sorry, email callback failed, no mail sent to " + callbackAddress.getAddress() + ": " + e.toString();
         Log.warn(ME + ".EmailSendError", str);
         throw new XmlBlasterException(ME + ".EmailSendError", str);
      }
   }


   private String getMailBody(MessageUnitWrapper msgUnitWrapper, MessageUnit[] arr) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n";

      sb.append(offset).append("<xmlBlaster>");
      sb.append(offset).append(msgUnitWrapper.getXmlKey().toXml()).append("\n");
      sb.append(offset).append("   <content>").append(new String(msgUnitWrapper.getMessageUnit().getContent())).append("</content>");
      sb.append(offset).append(msgUnitWrapper.getPublishQoS().toXml());
      if (arr.length > 1)
         Log.error(ME, "Sendung more than one callback email (burst mode) is not supported, mails are lost!");
      /*
      for (int ii=0; ii<arr.length; ii++) {
         sb.append(offset).append(arr[ii].xmlKey);
         sb.append(offset).append("<content>").append(arr[ii].content).append("</content>");
         sb.append(offset).append(arr[ii].qos);
      }
      */
      sb.append(offset).append("</xmlBlaster>\n");
      return sb.toString();
   }


   /**
    * This method shuts down the driver.
    * <p />
    */
   public void shutdown()
   {
      Log.warn(ME, "shutdown implementation is missing");
   }
}
