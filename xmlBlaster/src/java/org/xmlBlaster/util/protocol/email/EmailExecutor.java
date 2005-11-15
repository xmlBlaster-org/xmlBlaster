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
import org.xmlBlaster.util.protocol.email.EmailData;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.xbformat.MsgInfo;
import org.xmlBlaster.util.xbformat.MsgInfoParserFactory;
import org.xmlBlaster.util.xbformat.XbfParser;
import org.xmlBlaster.util.MsgUnitRaw;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
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
   
   private Deflater compressor;
   
   private Inflater decompressor;
   
   /** Which message format parser to use */
   protected String msgInfoParserClassName;
   
   /** Use to protect against looping messages */
   protected String lastMessageId;

   /**
    * This init() is called after the init(Global, PluginInfo)
    * 
    * @param addressBase
    *           Contains the email TO: address
    */
   public void init(Global glob, AddressBase addressBase, I_PluginConfig pluginConfig)
         throws XmlBlasterException {
      this.addressBase = addressBase;
      this.pluginConfig = pluginConfig;
      this.compressor = new Deflater(Deflater.BEST_COMPRESSION);
      this.decompressor = new Inflater();

      // Add
      //   CbProtocolPlugin[EMAIL][1.0]=org.xmlBlaster.protocol.email.CallbackEmailDriver,mail.user=xmlBlaster,mail.password=xmlBlaster,compress/type=zlib:stream
      //   ClientCbServerProtocolPlugin[EMAIL][1.0]=org.xmlBlaster.client.protocol.email.EmailCallbackImpl,mail.user=demo,mail.password=demo,mail.pop3.url=pop3://demo:demo@localhost/INBOX,compress/type=zlib:stream
      //   ClientProtocolPlugin[EMAIL][1.0]=org.xmlBlaster.client.protocol.email.EmailConnection,mail.user=demo,mail.password=demo,mail.pop3.url=pop3://demo:demo@localhost/INBOX,pop3PollingInterval=1000,compress/type=zlib:stream
      // settings to the clients Address configuration
      if (pluginConfig != null)
         this.addressBase.setPluginInfoParameters(pluginConfig.getParameters());

      super.initialize(glob, addressBase);
      
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
   
   public String getType() {
      return "EMAIL";
   }
   
   /**
    * Which parser to use. 
    * The EMAIL protocol uses as a default setting the XbfParser
    * but usig the XmlScriptParser may be convenient as well. 
    * <p />
    * The environment setting 'parserClass=' is checked.
    * @return The class name of the parser, "org.xmlBlaster.util.xbformat.XbfParser"
    */
   public String getMsgInfoParserClassName() {
      if (this.msgInfoParserClassName == null) {
         this.msgInfoParserClassName = this.addressConfig.getEnv("parserClass", XbfParser.class.getName()).getValue();
      }
      return this.msgInfoParserClassName; //XbfParser.class.getName();
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
   public void incomingMessage(String requestId, Object response) {
      EmailData emailData = (EmailData) response;

      AttachmentHolder holder = null;
      try {
         holder =
            emailData.getMsgUnitAttachment(); // "*.xbf", "*.xbfz", "*.xmlz", ...
      } catch (Throwable e) {
         log.warning("Error parsing email data from "
               + this.pop3Driver.getPop3Url()
               + ", please check the format: "
               + e.toString());
         return;
      }

      if (holder == null) {
         log.warning("No mails via POP3 found");
         //log.fine("DUMP:" + emailData.toXml());
         return;
      }

      byte[] encodedMsgUnit = holder.getContent();
      MsgInfo receiver = null;
      try {
         if (MsgInfo.isCompressed(holder.getFileName(), holder.getContentType())) {
            // Decompress the bytes
            int length = encodedMsgUnit.length;
            this.decompressor.reset();
            this.decompressor.setInput(encodedMsgUnit, 0, length);
            byte[] buf = new byte[2048+length];
            ByteArrayOutputStream out = new ByteArrayOutputStream(2048+length);
            while (!this.decompressor.finished()) {
               int resultLength = this.decompressor.inflate(buf);
               if (resultLength > 0)
                  out.write(buf, 0, resultLength);
            }
            encodedMsgUnit = out.toByteArray();
            if (log.isLoggable(Level.FINE)) log.fine("Decompressed message from " + length + " to " + encodedMsgUnit.length + " bytes");
         }
         String className = MsgInfoParserFactory.instance().guessParserName(holder.getFileName(), holder.getContentType());
         receiver = MsgInfo.parse(glob, progressListener, encodedMsgUnit, className);
         receiver.setBounceObject("mail.from", emailData.getFrom());
         receiver.setBounceObject("messageId", emailData.getMessageId());
      } catch (Throwable e) {
         log.warning("Error parsing email data from "
                           + this.pop3Driver.getPop3Url()
                           + ", check if client and server have identical compression settings: "
                           + e.toString() + ": " + emailData.toXml(true));
         //shutdown();
         return;
      }

      try {
         {
            // Some weak looping protection
            // TODO: Enforce requestId to be strong-monotonous accending
            // to also detect email duplicates (which can be produced by MTAs)
            String messageId = receiver.getSecretSessionId()+receiver.getRequestId();
            if (receiver.isInvoke() && messageId.equals(this.lastMessageId)) {
               log.warning("Can't process email data from "
                     + this.pop3Driver.getPop3Url()
                     + ", it seems to be looping as requestId has been processed already"
                     + ": " + emailData.toXml(true));
               return;
            }
            this.lastMessageId = messageId;
         }
         
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
         messageId = EmailData.createMessageId(getEmailSessionId(),
            requestId, methodName);

      // Transport messageId in subject:
      subject += messageId;
      // and for testing as attachment, for example "messageId.mid"
      String attachmentName2 = "messageId" + EmailData.MESSAGEID_EXTENSION;

      // The pay load
      byte[] origAttachment = msgInfo.getMsgInfoParser(getMsgInfoParserClassName()).createRawMsg(msgInfo);
      boolean isCompressed = false;
      byte[] attachment = origAttachment;
      if (isCompressZlib()) {
         if (origAttachment.length > getMinSizeForCompression()) { // > 0
            this.compressor.reset();
            this.compressor.setInput(origAttachment, 0, origAttachment.length);
            this.compressor.finish();
            attachment = new byte[origAttachment.length+64];
            int compSize = compressor.deflate(attachment);
            if (compSize <= 0) {
               throw new IOException("Compression exception, got 0 bytes output: " + MsgInfo.toLiteral(msgInfo.createRawMsg()));
            }
            if (compSize >= origAttachment.length) { // Compression didn't help
               attachment = origAttachment;  
               if (log.isLoggable(Level.FINE)) log.fine("No compression of attachment as size increased from " + origAttachment.length + " to " + compSize + ", we leave it uncompressed");
            }
            else {
               isCompressed = true;
               byte[] tmp = new byte[compSize];
               System.arraycopy(attachment, 0, tmp, 0, compSize);
               attachment = tmp;
               if (log.isLoggable(Level.FINE)) log.fine("Compressing attachment of size " + origAttachment.length + " to length " + attachment.length);
            }
         }
         else {
            if (log.isLoggable(Level.FINE)) log.fine("Compressing of attachment skipped as size of " + origAttachment.length + " is smaller minSize " + getMinSizeForCompression());
         }
      }
      
      // The real message blob, for example "xmlBlasterMessage.xbf"
      String attachmentName = "xmlBlasterMessage" + 
         msgInfo.getMsgInfoParser(getMsgInfoParserClassName()).getExtension(isCompressed);

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

      EmailData emailData = new EmailData(toAddr, this.fromAddress, subject);
      emailData.addAttachment(new AttachmentHolder(attachmentName, msgInfo.getMsgInfoParser(getMsgInfoParserClassName()).getMimetype(isCompressed), attachment));
      emailData.addAttachment(new AttachmentHolder(attachmentName2, messageId));
      
      this.smtpClient.sendEmail(emailData);
      //this.smtpClient.sendEmail(this.fromAddress, toAddr, subject,
      //      attachmentName, attachment, attachmentName2, messageId,
      //      Constants.UTF8_ENCODING);

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
