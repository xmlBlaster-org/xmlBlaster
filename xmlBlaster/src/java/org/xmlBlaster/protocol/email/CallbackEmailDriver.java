/*------------------------------------------------------------------------------
Name:      CallbackEmailDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using email
Version:   $Id: CallbackEmailDriver.java,v 1.17 2002/04/30 16:41:39 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.email;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.queue.MsgQueueEntry;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Constants;

import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;


/**
 * Sends a MessageUnit back to a client using Email.
 * <p>
 * Activate the email callback driver in xmlBlaster.properies first,
 * for example:
 * <pre>
 *    Protocol.CallbackDrivers=IOR:org.xmlBlaster.protocol.corba.CallbackCorbaDriver,\
 *                             EMAIL:org.xmlBlaster.protocol.email.CallbackEmailDriver
 * 
 *    EmailDriver.smtpHost=192.1.1.1
 *    EmailDriver.from=xmlblast@localhost
 * </pre>
 * @author $Author: ruff $
 * @see javaclients.ClientSubEmail
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
   public final String[] sendUpdate(MsgQueueEntry[] msg) throws XmlBlasterException
   {
      if (msg == null || msg.length < 1) throw new XmlBlasterException(ME, "Illegal update argument");
      if (Log.TRACE) Log.trace(ME, "xmlBlaster.update(" + msg.length + ") to " + callbackAddress.getSessionId());
      try {
         String smtpHost = XmlBlasterProperty.get("EmailDriver.smtpHost", "localhost");
         String from = XmlBlasterProperty.get("EmailDriver.from", "xmlblast@localhost"); //sessionInfo.getLoginName();
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
         String text = getMailBody(msg);
         message.setText(text);
         Log.info(ME + ".sendUpdate", "Sending email from " + from + " to " + to + ", smtpHost=" + smtpHost);
         if (Log.DUMP) Log.dump(ME + ".sendUpdate", "\n"+text);
         // Send message
         Transport.send(message);
         String[] ret = new String[msg.length];
         for (int ii=0; ii<ret.length; ii++)
            ret[ii] = Constants.RET_OK; // "<qos><state id='OK'/></qos>";
         return ret;
      } catch (Throwable e) {
         String str = "Sorry, email callback failed, no mail sent to " + callbackAddress.getAddress() + ": " + e.toString();
         Log.warn(ME + ".EmailSendError", str);
         throw new XmlBlasterException(ME + ".EmailSendError", str);
      }
   }

   /**
    * The oneway variant, without return value. 
    * @exception XmlBlasterException Is never from the client (oneway).
    */
   public void sendUpdateOneway(MsgQueueEntry[] msg) throws XmlBlasterException
   {
      sendUpdate(msg);
   }

   /**
    * Ping to check if callback server is alive. 
    * This ping checks the availability on the application level.
    * @param qos Currently an empty string ""
    * @return    Currently an empty string ""
    * @exception XmlBlasterException If client not reachable
    */
   public final String ping(String qos) throws XmlBlasterException
   {
      Log.info(ME, "Email ping is not supported, request ignored");
      return "";
   }

   private String getMailBody(MsgQueueEntry[] msg) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n";

      sb.append(offset).append("<xmlBlaster>");
      for (int ii=0; ii<msg.length; ii++) {
         MessageUnit msgUnit = msg[ii].getMessageUnit();
         sb.append(offset).append(msgUnit.getXmlKey()).append("\n");
         sb.append(offset).append("   <content><![CDATA[").append(new String(msgUnit.getContent())).append("]]></content>");
         sb.append(offset).append(msgUnit.getQos());
      }
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
