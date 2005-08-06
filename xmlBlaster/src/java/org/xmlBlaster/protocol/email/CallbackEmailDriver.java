/*------------------------------------------------------------------------------
Name:      CallbackEmailDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.email;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.protocol.I_ProgressListener;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.def.Constants;

import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;


/**
 * Sends a MsgUnitRaw back to a client using Email.
 * <p>
 * Activate the email callback driver in xmlBlaster.properies first,
 * for example:
 * <pre>
 *    CbProtocolPlugin[EMAIL][1.0]=org.xmlBlaster.protocol.email.CallbackEmailDriver
 * 
 *    EmailDriver.smtpHost=192.1.1.1
 *    EmailDriver.from=xmlblast@localhost
 * </pre>
 * see javaclients.ClientSubEmail
 * @author xmlBlaster@marcelruff.info
 */
public class CallbackEmailDriver implements I_CallbackDriver
{
   private String ME = "CallbackEmailDriver";
   private Global glob = null;
   private LogChannel log = null;
   private CallbackAddress callbackAddress = null;

   /** Get a human readable name of this driver */
   public String getName() {
      return ME;
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return "NativeDemo"
    */
   public String getProtocolId() {
      return "EMAIL";
   }

   /** Enforced by I_Plugin */
   public String getType() {
      return getProtocolId();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return "1.0";
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
   }

   /**
    * Get the address how to access this driver. 
    * @return null
    */
   public String getRawAddress() {
      return this.callbackAddress.getRawAddress();
   }

   /**
    * @param  callbackAddress Contains the email TO: address
    */
   public void init(Global glob, CallbackAddress callbackAddress)
   {
      this.glob = glob;
      this.log = glob.getLog("email");
      this.callbackAddress = callbackAddress;
   }


   /**
    * This sends the update to the client.
    */
   public String[] sendUpdate(MsgUnitRaw[] msg) throws XmlBlasterException
   {
      if (msg == null || msg.length < 1) 
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal sendUpdate() argument");
      if (log.TRACE) log.trace(ME, "xmlBlaster.update(" + msg.length + ") to " + callbackAddress.getSecretSessionId());
      try {
         String smtpHost = glob.getProperty().get("EmailDriver.smtpHost", "localhost");
         String from = glob.getProperty().get("EmailDriver.from", "xmlblast@localhost"); //sessionInfo.getLoginName();
         String to = callbackAddress.getRawAddress();
         String subject =  glob.getProperty().get("EmailDriver.subject", "XmlBlaster Generated Email");
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
         message.setSubject(subject);
         String text = getMailBody(msg);
         message.setText(text);
         log.info(ME + ".sendUpdate", "Sending email from " + from + " to " + to + ", smtpHost=" + smtpHost);
         if (log.DUMP) log.dump(ME + ".sendUpdate", "\n"+text);
         // Send message
         Transport.send(message);
         String[] ret = new String[msg.length];
         for (int ii=0; ii<ret.length; ii++)
            ret[ii] = Constants.RET_OK; // "<qos><state id='OK'/></qos>";
         return ret;
      } catch (Throwable e) {
         // ErrorCode.USER* errors can't arrive here
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                     "Sorry, email callback failed, no mail sent to " + callbackAddress.getRawAddress(), e);

      }
   }

   /**
    * The oneway variant, without return value. 
    * @exception XmlBlasterException Is never from the client (oneway).
    */
   public void sendUpdateOneway(MsgUnitRaw[] msg) throws XmlBlasterException
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
   public String ping(String qos) throws XmlBlasterException
   {
      log.info(ME, "Email ping is not supported, request ignored");
      return "";
   }

   private String getMailBody(MsgUnitRaw[] msg) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n";

      sb.append(offset).append("<xmlBlaster>");
      for (int ii=0; ii<msg.length; ii++) {
         MsgUnitRaw msgUnit = msg[ii];
         sb.append(offset).append(msgUnit.getKey()).append("\n");
         sb.append(offset).append("   <content><![CDATA[").append(new String(msgUnit.getContent())).append("]]></content>");
         sb.append(offset).append(msgUnit.getQos());
      }
      sb.append(offset).append("</xmlBlaster>\n");
      return sb.toString();
   }

   public I_ProgressListener registerProgressListener(I_ProgressListener listener) {
      if (log.TRACE) log.trace(ME, "Registering I_ProgressListener is not supported with this protocol plugin");
      return null;
   }

   /**
    * This method shuts down the driver.
    * <p />
    */
   public void shutdown()
   {
      log.warn(ME, "shutdown implementation is missing");
   }

   /**
    * @return true if the plugin is still alive, false otherwise
    */
   public boolean isAlive() {
      return true;
   }

}
