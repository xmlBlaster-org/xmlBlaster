/*------------------------------------------------------------------------------
 Name:      EmailExecutor.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.util.protocol.email;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_ResponseListener;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.plugin.I_PluginConfig;
import org.xmlBlaster.util.protocol.RequestReplyExecutor;
import org.xmlBlaster.util.protocol.email.Pop3Driver;
import org.xmlBlaster.util.protocol.email.SmtpClient;
import org.xmlBlaster.util.protocol.email.MessageData;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.xbformat.MsgInfo;
import org.xmlBlaster.util.xbformat.XbfParser;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.def.Constants;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

/**
 * Base class to handle request/reply for emails.
 * <p>
 * 
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class EmailExecutor extends  RequestReplyExecutor implements I_ResponseListener {
   private String ME = "EmailExecutor";

   private static Logger log = Logger.getLogger(EmailExecutor.class.getName());

   private AddressBase addressBase;

   private I_PluginConfig pluginConfig;

   protected InternetAddress fromAddress;

   protected InternetAddress toAddress;

   protected SmtpClient smtpClient;

   private String secretSessionId = "";
   
   private String emailSessionId = "";

   protected Pop3Driver pop3Driver;

   /**
    * This init() is called after the init(Global, PluginInfo)
    * 
    * @param addressBase
    *           Contains the email TO: address
    */
   public void init(Global glob, AddressBase addressBase, I_PluginConfig pluginConfig)
         throws XmlBlasterException {
      super.initialize(glob, addressBase);
      this.addressBase = addressBase;
      this.pluginConfig = pluginConfig;
      
      // Add
      //   CbProtocolPlugin[EMAIL][1.0]=org.xmlBlaster.protocol.email.CallbackEmailDriver,mail.user=xmlBlaster,mail.password=xmlBlaster,compress/type=zlib:stream
      //   ClientCbServerProtocolPlugin[EMAIL][1.0]=org.xmlBlaster.client.protocol.email.EmailCallbackImpl,mail.user=demo,mail.password=demo,mail.pop3.url=pop3://demo:demo@localhost/INBOX,compress/type=zlib:stream
      //   ClientProtocolPlugin[EMAIL][1.0]=org.xmlBlaster.client.protocol.email.EmailConnection,mail.user=demo,mail.password=demo,mail.pop3.url=pop3://demo:demo@localhost/INBOX,pop3PollingInterval=1000,compress/type=zlib:stream
      // settings to the clients Address configuration
      if (pluginConfig != null)
         this.addressBase.setPluginInfoParameters(pluginConfig.getParameters());

      this.secretSessionId = addressBase.getSecretSessionId();
      /*
       * TODO: Support adding ConnectQos: <callback type='EMAIL'> a@b.com
       * <clientProperty name='responseTimeout'>120000</clientProperty>
       * </callback>
       */

      String to = addressBase.getRawAddress();
      if (to != null && to.length() > 0) {
         // The xmlBlaster address is given on client side
         // but not on server side
         try {
            this.toAddress = new InternetAddress(to);
         } catch (AddressException e) {
            throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT,
                  ME, "Illegal 'to' address '" + to + "'");
         }
      }

      this.smtpClient = SmtpClient.instance();
      this.smtpClient.setSessionProperties(null, this.glob, this.pluginConfig);

      // from="xmlBlaster@localhost"
      String from = this.glob.get("mail.smtp.from", this.smtpClient.getUser()
            + "@localhost", null, this.pluginConfig);
      try {
         this.fromAddress = new InternetAddress(from);
      } catch (AddressException e) {
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT,
               ME, "Illegal 'from' address '" + from + "'");
      }

      this.pop3Driver = (Pop3Driver) glob.getObjectEntry(Pop3Driver.class
            .getName());
      if (this.pop3Driver == null) {
         log.warning("Please register a Pop3Driver in xmlBlasterPlugins.xml to have EMAIL support");
         throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME,
               "Please register a Pop3Driver in xmlBlasterPlugins.xml to have EMAIL support");
      }
      
      if (log.isLoggable(Level.FINE)) log.fine("Initialized email connector from=" + from + " to=" + to);
   }
   
   public Object sendEmail(String qos, MethodName methodName,
         boolean expectingResponse) throws XmlBlasterException {
      MsgUnitRaw[] msgArr = { new MsgUnitRaw(null, (byte[])null, qos) };
      return sendEmail(msgArr, methodName,
            expectingResponse);
   }

   public Object sendEmail(String key, String qos, MethodName methodName,
         boolean expectingResponse) throws XmlBlasterException {
      MsgUnitRaw[] msgArr = { new MsgUnitRaw(key, (byte[])null, qos) };
      return sendEmail(msgArr, methodName,
            expectingResponse);
   }

   public Object sendEmail(MsgUnitRaw msgUnit, MethodName methodName,
         boolean expectingResponse) throws XmlBlasterException {
      MsgUnitRaw[] msgArr = { msgUnit };
      return sendEmail(msgArr, methodName, expectingResponse);
   }

   /**
    * This sends the update to the client.
    * 
    * @param methodName
    *           MethodName.UPDATE and others
    * @param withResponse
    *           one of SocketExecutor.WAIT_ON_RESPONSE or SocketExecutor.ONEWAY
    */
   public Object sendEmail(MsgUnitRaw[] msgArr, MethodName methodName,
         boolean expectingResponse) throws XmlBlasterException {
      if (msgArr == null || msgArr.length < 1)
         throw new XmlBlasterException(glob,
               ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal sendEmail("
                     + methodName.toString() + ") argument");
      if (log.isLoggable(Level.FINE))
         log.fine(methodName.toString() + "(" + msgArr.length + ") with emailSessionId="
               + getEmailSessionId());

      String requestId = null;
      try {
         // We use the RequestReplyExecutor.java for the request/reply pattern

         MsgInfo msgInfo = new MsgInfo(this.glob, MsgInfo.INVOKE_BYTE, methodName,
               getSecretSessionId(), super.getProgressListener());
         msgInfo.addMessage(msgArr);
         requestId = msgInfo.createRequestId(null);

         if (expectingResponse) { // register at the POP3 poller
            this.pop3Driver.registerForEmail(getEmailSessionId(), requestId, this);
         }

         // super calls our sendMessage() which effectively sends the message
         Object response = super.requestAndBlockForReply(msgInfo,
               expectingResponse, false);
         return response;
      } catch (Throwable e) {
         // ErrorCode.USER* errors can't arrive here
         throw new XmlBlasterException(glob,
               ErrorCode.COMMUNICATION_NOCONNECTION, ME,
               "Sorry, email sending " + methodName.toString()
                     + " failed, no mail sent to "
                     + addressBase.getRawAddress(), e);
      } finally {
         if (expectingResponse)
            this.pop3Driver.deregisterForEmail(getEmailSessionId(), requestId);
      }
   }

   /**
    * Notification by Pop3Driver when a (response) email message arrives. 
    * Enforced by I_ResponseListener
    */
   public void responseEvent(String requestId, Object response) {
      MessageData messageData = (MessageData) response;

      byte[] encodedMsgUnit = null;
      try {
         // TODO: msgInfo.getMsgInfoParser().getExtension(super.isCompressZlib())
         encodedMsgUnit = messageData.getEncodedMsgUnitByExtension(
               XbfParser.XBFORMAT_EXTENSION, XbfParser.XBFORMAT_ZLIB_EXTENSION, ".xml"); // "*.xbf", "*.xbfz"
      } catch (Throwable e) {
         log.warning("Error parsing email data from "
               + this.pop3Driver.getPop3Url()
               + ", please check the format: "
               + e.toString());
         return;
      }

      if (encodedMsgUnit != null) {
         MsgInfo receiver = null;
         try {
            // TODO: uncompress the message if needed
            receiver = MsgInfo.parse(glob, progressListener, encodedMsgUnit);
            receiver.setBounceObject("mail.from", messageData.getFrom());
            receiver.setBounceObject("messageId", messageData.getMessageId());
         } catch (Throwable e) {
            log.warning("Error parsing email data from "
                              + this.pop3Driver.getPop3Url()
                              + ", check if client and server have identical compression settings: "
                              + e.toString() + ": " + messageData.toXml());
            //shutdown();
            return;
         }

         try {
            // This wakes up the blocking thread of sendEmail() and returns the
            // returnQos or the received invocation
            if (receiveReply(receiver, false) == false) {
               log.warning("Error parsing email data from "
                     + this.pop3Driver.getPop3Url()
                     + ", CONNECT etc is not yet implemented");
            }
            return;
         } catch (Throwable e) {
            log.warning("Can't process email data from "
                  + this.pop3Driver.getPop3Url() + ": " + e.toString());
            return;
         }
      }

      log.warning("No mails via POP3 found");
   }

   /**
    * Extends SocketExecutor.sendMessage
    */
   protected void sendMessage(MsgInfo msgInfo, String requestId,
         MethodName methodName, boolean udp) throws XmlBlasterException,
         IOException {

      String subject = this.glob.get("mail.subject",
            "XmlBlaster Generated Email ", null, this.pluginConfig);
      String messageId = (String)msgInfo.getBounceObject("messageId");
      if (messageId == null)
         messageId = MessageData.createMessageId(getEmailSessionId(),
            requestId, methodName);

      // Transport messageId in subject:
      subject += messageId;
      // and for testing as attachment, for example "messageId.mid"
      String attachmentName2 = "messageId" + MessageData.MESSAGEID_EXTENSION;

      // The real message blob, for example "xmlBlasterMessage.xbf"
      String attachmentName = "xmlBlasterMessage" + 
         msgInfo.getMsgInfoParser().getExtension(super.isCompressZlib());
     
      byte[] attachment = msgInfo.getMsgInfoParser().createRawMsg(msgInfo);
      if (super.isCompressZlib()) {
         log.severe("Compression is not implemented");
      }

      InternetAddress toAddr = this.toAddress;
      String to = (String)msgInfo.getBounceObject("mail.to");
      if (to != null) {
         try { // The EmailDriver has different destinations for each client
            toAddr = new InternetAddress(to);
         } catch (AddressException e) {
            log.warning("Illegal 'to' address '" + to + "'");
         }
      }
      if (toAddr == null)
         throw new IllegalArgumentException("No 'toAddress' email address is given, can't send mail");
      
      this.smtpClient.sendEmail(this.fromAddress, toAddr, subject,
            attachmentName, attachment, attachmentName2, messageId,
            Constants.UTF8_ENCODING);

      if (log.isLoggable(Level.FINE)) log.fine("Sending email from="
            + this.fromAddress.toString() + " to=" + toAddr.toString()
            + " messageId=" + messageId
            + " done");
      if (log.isLoggable(Level.FINEST)) log.finest("MsgInfo dump: " + MsgInfo.toLiteral(msgInfo.createRawMsg()));
   }

   /**
    * The oneway variant, without return value.
    * 
    * @exception XmlBlasterException
    *               Is never from the client (oneway).
    */
   public void sendUpdateOneway(MsgUnitRaw[] msgArr) throws XmlBlasterException {
      log.warning("Email sendUpdateOneway is not supported, request ignored");
   }

   /**
    * Ping to check if callback server is alive. This ping checks the
    * availability on the application level.
    * 
    * @param qos
    *           Currently an empty string ""
    * @return Currently an empty string ""
    * @exception XmlBlasterException
    *               If client not reachable
    */
   public String ping(String qos) throws XmlBlasterException {
      log.warning("Email ping is not supported, request ignored");
      return "";
   }

   /*
    * private String getMailBody(MsgUnitRaw[] msgArr) throws XmlBlasterException {
    * StringBuffer sb = new StringBuffer(); String offset = "\n";
    * 
    * sb.append(offset).append("<xmlBlaster>"); for (int ii = 0; ii <
    * msgArr.length; ii++) { MsgUnitRaw msgUnit = msgArr[ii];
    * sb.append(offset).append(msgUnit.getKey()).append("\n");
    * sb.append(offset).append(" <content><![CDATA[").append( new
    * String(msgUnit.getContent())).append("]]></content>");
    * sb.append(offset).append(msgUnit.getQos()); } sb.append(offset).append("</xmlBlaster>\n");
    * return sb.toString(); }
    */

   /**
    * This method shuts down the driver.
    * <p />
    */
   public void shutdown() {
      log.warning("shutdown implementation is missing");
   }

   /**
    * @return true if the plugin is still alive, false otherwise
    */
   public boolean isAlive() {
      return true;
   }

   /**
    * @return Returns the secretSessionId.
    */
   public String getSecretSessionId() {
      return this.secretSessionId;
   }

   /**
    * @param secretSessionId
    *           The secretSessionId to set.
    */
   public void setSecretSessionId(String secretSessionId) {
      this.secretSessionId = secretSessionId;
   }

   /**
    * The sessionId used to register at Pop3Driver and send in subject: of email
    * @return Usually the clients relative login sessionName "client/joe/3", never null
    */
   public String getEmailSessionId() {
      return (this.emailSessionId == null) ? "" : this.emailSessionId;
   }

   public void setEmailSessionId(String emailSessionId) {
      this.emailSessionId = emailSessionId;
   }

   /**
    * Email protocol contract with server side CallbackEmailDriver.java and client side EmailCallbackImpl.java
    * We use <messageId><sessionId>joe/2</sessionId>...
    * to find our response again. If no positive sessionId
    * is given, the server generates e.g. -7 for us which we don't know
    * In this case we use <messageId><sessionId>joe</sessionId>... only
    */
   protected void setEmailSessionId(SessionName sessionName) {
      if (sessionName == null) {
         log.severe("setEmailSessionId() is called with null sessionName");
         return;
      }
      if (sessionName.isPubSessionIdUser()) 
         setEmailSessionId(sessionName.getRelativeName());
      else {
         log.warning("The current email callback implementation can handle max one connection per client name (like 'joe') if you don't supply a positive sessionId");
         setEmailSessionId(sessionName.getLoginName());
      }
   }
}
