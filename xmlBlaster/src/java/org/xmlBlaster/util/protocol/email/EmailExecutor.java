/*------------------------------------------------------------------------------
 Name:      EmailExecutor.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.util.protocol.email;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_ResponseListener;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.plugin.I_PluginConfig;
import org.xmlBlaster.util.protocol.Executor;
import org.xmlBlaster.util.protocol.email.AttachmentHolder;
import org.xmlBlaster.util.protocol.email.Pop3Driver;
import org.xmlBlaster.util.protocol.email.SmtpClient;
import org.xmlBlaster.util.protocol.email.MessageData;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.xbformat.Parser;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.def.Constants;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

/**
 * Base class to handle request/reply for emails.
 * <p>
 * 
 * @author xmlBlaster@marcelruff.info
 */
public class EmailExecutor extends Executor implements I_ResponseListener {
   private String ME = "EmailExecutor";

   private LogChannel log;

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
      this.log = glob.getLog("email");
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
         log.warn(ME, "Please register a Pop3Driver in xmlBlasterPlugins.xml to have EMAIL support");
         throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME,
               "Please register a Pop3Driver in xmlBlasterPlugins.xml to have EMAIL support");
      }
   }
   
   public Object sendEmail(String qos, MethodName methodName,
         boolean expectingResponse) throws XmlBlasterException {
      MsgUnitRaw[] msgArr = { new MsgUnitRaw(null, null, qos) };
      return sendEmail(msgArr, methodName,
            expectingResponse);
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
      if (log.TRACE)
         log.trace(ME, methodName.toString() + "(" + msgArr.length + ") to "
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

         Parser parser = new Parser(this.glob, Parser.INVOKE_BYTE, methodName,
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
   private boolean extractMsgUnit(MessageData messageData) {
      AttachmentHolder[] atts = messageData.getAttachments();
      byte[] content = null;
      for (int j = 0; j < atts.length; j++) {
         if (atts[j].getFileName().endsWith(Parser.XBFORMAT_EXTENSION)) { // "*.xbf"
            content = atts[j].getContent();
            break;
         }
      }
      if (content == null) {
         for (int j = 0; j < atts.length; j++) {
            log.info(ME, "Processing response mail file name '"
                  + atts[j].getFileName() + "'");
            if (atts[j].getFileName().endsWith(".xml")) {
               content = atts[j].getContent();
               break;
            } else {
               log.warn(ME, "Ignoring unknown attachment file name '"
                     + atts[j].getFileName() + "'");
            }
         }
      }

      if (content != null) { // Process the messageUnit
         try {
            if (this.oStreamForResponse == null) {
               // Happens if it is not a response but an initial request
               // We need to go over super in case the streams to
               // compress/inflate
               PipedInputStream iStreamResponse = new PipedInputStream();
               this.oStreamForResponse = new PipedOutputStream(iStreamResponse);

               PipedOutputStream oStreamSend = new PipedOutputStream(); // not
               // need
               // here,
               // but
               // to
               // avoid
               // NPE
               this.iStreamSend = new PipedInputStream(oStreamSend);

               super.initialize(this.glob, addressBase, iStreamResponse,
                     oStreamSend);
            }

            log.info(ME, "Parsing now: " + Parser.toLiteral(content));
            this.oStreamForResponse.write(content);
            this.oStreamForResponse.flush();
            return true;
         } catch (Exception e) {
            log.error(ME, "Handling POP3 response failed: " + e.toString());
         }
      } else {
         log.error(ME, "Handling POP3 response failed, no MsgUnit found: "
               + messageData.toXml());
      }
      return false;
   }

   /**
    * Notification by Pop3Driver when a (response) message arrives. Enforced by
    * I_ResponseListener
    */
   public void responseEvent(String requestId, Object response) {
      MessageData messageData = (MessageData) response;

      // Fills message into this.oStreamForResponse
      boolean responseArrived = extractMsgUnit(messageData);

      if (responseArrived) {
         Parser receiver = new Parser(glob, progressListener);
         try {
            // Reads message again from this.oStreamForResponse
            receiver.parse(iStream);
            this.oStreamForResponse = null;
         } catch (Throwable e) {
            log
                  .warn(
                        ME,
                        "Error parsing email data from "
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
               log.warn(ME, "Error parsing email data from "
                     + this.pop3Driver.getPop3Url()
                     + ", CONNECT etc is not yet implemented");
            }
            return;
         } catch (Throwable e) {
            log.warn(ME, "Can't process email data from "
                  + this.pop3Driver.getPop3Url() + ": " + e.toString());
            //shutdown();
         }
      }

      log.warn(ME, "No mails via POP3 found");
   }

   /**
    * Extends Executor.sendMessage
    */
   protected void sendMessage(byte[] msg, String requestId,
         MethodName methodName, boolean udp) throws XmlBlasterException,
         IOException {
      super.sendMessage(msg, requestId, methodName, udp); // compresses it to
      // our oStreamSend
      super.oStream.close();

      String subject = this.glob.get("mail.subject",
            "XmlBlaster Generated Email ", null, this.pluginConfig);
      String messageId = MessageData.createMessageId(getSecretSessionId(),
            requestId, methodName);

      // Transport messageId in subject:
      subject += messageId;
      // and for testing as attachment
      String attachmentName2 = "messageId" + MessageData.MESSAGEID_EXTENSION; // ".mid"

      // The real message blob
      String attachmentName = "xmlBlasterMessage" + Parser.XBFORMAT_EXTENSION; // ".xbf";

      if (this.toAddress == null)
         throw new IllegalArgumentException("No 'toAddress' email address is given, can't send mail");
      this.smtpClient.sendEmail(this.fromAddress, this.toAddress, subject,
            attachmentName, this.iStreamSend, attachmentName2, messageId,
            Constants.UTF8_ENCODING);

      log.info(ME + ".sendUpdate", "Sending email from "
            + this.fromAddress.toString() + " to " + this.toAddress.toString()
            + " done");
      log.info(ME, "Parser dump: " + Parser.toLiteral(msg));
   }

   /**
    * The oneway variant, without return value.
    * 
    * @exception XmlBlasterException
    *               Is never from the client (oneway).
    */
   public void sendUpdateOneway(MsgUnitRaw[] msgArr) throws XmlBlasterException {
      log.warn(ME, "Email sendUpdateOneway is not supported, request ignored");
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
      log.info(ME, "Email ping is not supported, request ignored");
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
      log.warn(ME, "shutdown implementation is missing");
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
