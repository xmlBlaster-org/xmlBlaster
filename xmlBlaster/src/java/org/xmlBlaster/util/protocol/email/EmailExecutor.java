/*------------------------------------------------------------------------------
 Name:      EmailExecutor.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.util.protocol.email;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_ResponseListener;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.plugin.I_PluginConfig;
import org.xmlBlaster.util.protocol.Executor;
import org.xmlBlaster.util.protocol.email.Pop3Driver;
import org.xmlBlaster.util.protocol.email.SmtpClient;
import org.xmlBlaster.util.protocol.email.MessageData;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.xbformat.MsgInfo;
import org.xmlBlaster.util.xbformat.XbfParser;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.def.Constants;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
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
public class EmailExecutor extends Executor implements I_ResponseListener {
   private String ME = "EmailExecutor";

   private static Logger log = Logger.getLogger(EmailExecutor.class.getName());

   private AddressBase addressBase;

   private I_PluginConfig pluginConfig;

   protected InternetAddress fromAddress;

   protected InternetAddress toAddress;

   protected SmtpClient smtpClient;

   private String secretSessionId = "";

   protected Pop3Driver pop3Driver;

   protected PipedOutputStream oStreamForResponse;

   protected PipedInputStream iStreamSend;

   /**
    * This init() is called after the init(Global, PluginInfo)
    * 
    * @param addressBase
    *           Contains the email TO: address
    */
   public void init(Global glob, AddressBase addressBase, I_PluginConfig pluginConfig)
         throws XmlBlasterException {
      this.glob = glob;
      this.addressBase = addressBase;
      this.pluginConfig = pluginConfig;

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
      MsgUnitRaw[] msgArr = { new MsgUnitRaw(null, null, qos) };
      return sendEmail(msgArr, methodName,
            expectingResponse);
   }

   public Object sendEmail(String key, String qos, MethodName methodName,
         boolean expectingResponse) throws XmlBlasterException {
      MsgUnitRaw[] msgArr = { new MsgUnitRaw(key, null, qos) };
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
    *           one of Executor.WAIT_ON_RESPONSE or Executor.ONEWAY
    */
   public Object sendEmail(MsgUnitRaw[] msgArr, MethodName methodName,
         boolean expectingResponse) throws XmlBlasterException {
      if (msgArr == null || msgArr.length < 1)
         throw new XmlBlasterException(glob,
               ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal sendEmail("
                     + methodName.toString() + ") argument");
      if (log.isLoggable(Level.FINE)) log.fine(methodName.toString() + "(" + msgArr.length + ") to "
               + getSecretSessionId());

      String sessionId = getSecretSessionId();
      String requestId = null;
      try {
         // We use the Executor.java for the request/reply and compression
         // support
         // As the Executor works with Input/OutputStream we need to use pipes
         // to satisfy this need
         PipedInputStream iStreamResponse = new PipedInputStream();
         this.oStreamForResponse = new PipedOutputStream(iStreamResponse);

         PipedOutputStream oStreamSend = new PipedOutputStream();
         this.iStreamSend = new PipedInputStream(oStreamSend);

         super.initialize(this.glob, addressBase, iStreamResponse, oStreamSend);

         MsgInfo parser = new MsgInfo(this.glob, MsgInfo.INVOKE_BYTE, methodName,
               sessionId, super.getProgressListener());
         parser.addMessage(msgArr);
         requestId = parser.createRequestId(null);

         if (expectingResponse) { // register at the POP3 poller
            this.pop3Driver.registerForEmail(sessionId, requestId, this);
         }

         // super calls our sendMessage() which effectively sends the message
         Object response = super.execute(parser,
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
            this.pop3Driver.deregisterForEmail(sessionId, requestId);
      }
   }

   /**
    * Find the MsgUnit in the attachments or the body of the email. We look for
    * extension "*.xbf" and if not found we try the first "*.xml" found
    * 
    * @param messageData
    * @return true if message is processed
    */
   private boolean extractMsgUnit(MessageData messageData) throws XmlBlasterException {
      byte[] encodedMsgUnit = messageData.getEncodedMsgUnitByExtension(
               XbfParser.XBFORMAT_EXTENSION, ".xml"); // "*.xbf"
      if (encodedMsgUnit != null) { // Process the messageUnit
         if (encodedMsgUnit.length < XbfParser.NUM_FIELD_LEN) // min 10 bytes, otherwise we block forever when reading the stream
            throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "The messageUnit in the email is too short: " + MsgInfo.toLiteral(encodedMsgUnit));
         try {
            if (this.oStreamForResponse == null) {
               // Happens if it is not a response but an initial request
               // We need to go over super in case the streams to
               // compress/inflate
               PipedInputStream iStreamResponse = new PipedInputStream();
               this.oStreamForResponse = new PipedOutputStream(iStreamResponse);

               // Not needed here, but to avoid NPE
               PipedOutputStream oStreamSend = new PipedOutputStream();
               this.iStreamSend = new PipedInputStream(oStreamSend);

               super.initialize(this.glob, addressBase, iStreamResponse,
                     oStreamSend);
            }

            if (log.isLoggable(Level.FINER)) log.finer("Parsing now: " + MsgInfo.toLiteral(encodedMsgUnit));
            this.oStreamForResponse.write(encodedMsgUnit);
            this.oStreamForResponse.flush();
            return true;
         } catch (Exception e) {
            log.severe("Handling POP3 response failed: " + e.toString());
         }
      } else {
         log.severe("Handling POP3 response failed, no MsgUnit found in attachtments '" + messageData.getFileNameList() + "': "
               + messageData.toXml());
      }
      return false;
   }

   /**
    * Notification by Pop3Driver when a (response) email message arrives. Enforced by
    * I_ResponseListener
    */
   public void responseEvent(String requestId, Object response) {
      MessageData messageData = (MessageData) response;

      boolean responseArrived;
      try {
         // Fills message into this.oStreamForResponse
         responseArrived = extractMsgUnit(messageData);
      } catch (Throwable e) {
         log.warning("Error parsing email data from "
               + this.pop3Driver.getPop3Url()
               + ", please check the format: "
               + e.toString());
         return;
      }

      if (responseArrived) {
         MsgInfo receiver = null;
         try {
            // Reads message again from this.oStreamForResponse
            // This uncompresses the message if needed
            receiver = MsgInfo.parse(glob, progressListener, iStream);
            this.oStreamForResponse = null;
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
            if (receive(receiver, false) == false) {
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
    * Extends Executor.sendMessage
    */
   protected void sendMessage(MsgInfo msgInfo, String requestId,
         MethodName methodName, boolean udp) throws XmlBlasterException,
         IOException {
      super.sendMessage(msgInfo, requestId, methodName, udp); // compresses it to
      // our oStreamSend
      super.oStream.close();

      String subject = this.glob.get("mail.subject",
            "XmlBlaster Generated Email ", null, this.pluginConfig);
      String messageId = (String)msgInfo.getBounceObject("messageId");
      if (messageId == null)
         messageId = MessageData.createMessageId(getSecretSessionId(),
            requestId, methodName);

      // Transport messageId in subject:
      subject += messageId;
      // and for testing as attachment, for example "messageId.mid"
      String attachmentName2 = "messageId" + MessageData.MESSAGEID_EXTENSION;

      // The real message blob, for example "xmlBlasterMessage.xbf"
      String attachmentName = "xmlBlasterMessage" + msgInfo.getMsgInfoParser().getExtension();

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
            attachmentName, this.iStreamSend, attachmentName2, messageId,
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

}
