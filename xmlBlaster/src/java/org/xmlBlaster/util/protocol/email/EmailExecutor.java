/*------------------------------------------------------------------------------
 Name:      EmailExecutor.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.util.protocol.email;

import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_ResponseListener;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.plugin.I_PluginConfig;
import org.xmlBlaster.util.plugin.PluginInfo;
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
import java.sql.Timestamp;
import java.util.Map;
import java.util.TreeMap;
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
public abstract class EmailExecutor extends  RequestReplyExecutor implements I_ResponseListener, EmailExecutorMBean {
   private String ME = "EmailExecutor";

   private static Logger log = Logger.getLogger(EmailExecutor.class.getName());
   
   private boolean isShutdown;

   private AddressBase addressBase;

   private I_PluginConfig pluginConfig;

   protected InternetAddress fromAddress;

   protected InternetAddress toAddress;
   
   protected String cc;

   protected String bcc;

   protected SmtpClient smtpClient;

   private String secretSessionId = "";
   
   private String emailSessionId = "";

   protected Pop3Driver pop3Driver;
   
   private Deflater compressor;
   
   private Inflater decompressor;
   
   /** Which message format parser to use */
   protected String msgInfoParserClassName;
   
   protected Map senderLoopProtectionMap = new TreeMap();
   /**
    * Use to protect against looping messages
    */
   protected class LoopProtection {
      LoopProtection(String key, long lastRequestId, long lastPingRequestId) {
         this.key = key;
         this.lastRequestId = lastRequestId;
         this.lastPingRequestId = lastPingRequestId;
      }
      public String key; // email.from address
      //protected String lastSessionId;
      /** Use to protect against looping messages, is a monotonous ascending timestamp */
      public long lastRequestId=-1;
      /** Ping runs in another thread, we need to protect seperately (an update can legally overtake a ping and vice versa) */
      public long lastPingRequestId=-1;
   }
   
   protected final String BOUNCE_MESSAGEID_KEY = "bounce:messageId";
   protected final String BOUNCE_MAILTO_KEY = "mail.to";
   protected final String BOUNCE_MAILFROM_KEY = "mail.from";

   /** 'messageId.mid' */
   protected String messageIdFileName = "messageId" + EmailData.MESSAGEID_EXTENSION;
   
   /** The extension is added later to for example "xmlBlasterMessage.xfb" */
   protected String payloadFileNamePrefix = "xmlBlasterMessage";
   
   protected String subjectTemplate;
   
   protected final String SUBJECT_MESSAGEID_TOKEN = "$_{xmlBlaster/email/messageId}";

   /**
    * This init() is called after the init(Global, PluginInfo)
    * 
    * @param addressBase
    *           Contains the email TO: address
    */
   public void init(Global glob, AddressBase addressBase, PluginInfo pluginConfig)
         throws XmlBlasterException {
      this.addressBase = addressBase;
      this.pluginConfig = pluginConfig;
      this.compressor = new Deflater(Deflater.BEST_COMPRESSION);
      this.decompressor = new Inflater();

      // Add
      //   CbProtocolPlugin[email][1.0]=org.xmlBlaster.protocol.email.CallbackEmailDriver,mail.user=xmlBlaster,mail.password=xmlBlaster,compress/type=zlib:stream
      //   ClientCbServerProtocolPlugin[email][1.0]=org.xmlBlaster.client.protocol.email.EmailCallbackImpl,mail.user=demo,mail.password=demo,mail.pop3.url=pop3://demo:demo@localhost/INBOX,compress/type=zlib:stream
      //   ClientProtocolPlugin[email][1.0]=org.xmlBlaster.client.protocol.email.EmailConnection,mail.user=demo,mail.password=demo,mail.pop3.url=pop3://demo:demo@localhost/INBOX,pop3PollingInterval=1000,compress/type=zlib:stream
      // settings to the clients Address configuration
      if (this.pluginConfig != null)
         this.addressBase.setPluginInfoParameters(this.pluginConfig.getParameters());

      super.initialize(glob, addressBase);
      
      this.secretSessionId = addressBase.getSecretSessionId();

      String to = addressBase.getRawAddress();
      if (to != null && to.length() > 0) {
         // The xmlBlaster address is given on client side
         // but not on server side
         setTo(to);
      }

      // from="xmlBlaster@localhost"
      setFrom(this.glob.get("mail.smtp.from",
            "unknown@localhost", null, this.pluginConfig));

      setCc(this.glob.get("mail.smtp.cc",
            "", null, this.pluginConfig));

      setBcc(this.glob.get("mail.smtp.bcc",
            "", null, this.pluginConfig));
      
      // if template contains SUBJECT_MESSAGEID_TOKEN = "${xmlBlaster/email/messageId}"
      // this will be replaced by the current messageId
      this.subjectTemplate = this.glob.get("mail.subject",
            "XmlBlaster Generated Email "+SUBJECT_MESSAGEID_TOKEN, null, this.pluginConfig);


      this.isShutdown = false;
      if (log.isLoggable(Level.FINE)) log.fine("Initialized email connector from=" + this.fromAddress.toString() + " to=" + to);
   }

   /**
    * Access the Pop3Driver. 
    * @return never null
    */
   public Pop3Driver getPop3Driver() throws XmlBlasterException {
      if (this.pop3Driver == null) {
         this.pop3Driver = (Pop3Driver) glob.getObjectEntry(Pop3Driver.OBJECTENTRY_KEY);
         
         if (this.pop3Driver == null) {
            if (this.glob.isServerSide()) {
               // On server side the Pop3Driver is created by the runlevel manager as configured in xmlBlasterPlugins.xml
               String text = "Please register a Pop3Driver in xmlBlasterPlugins.xml to have 'email' support";
               // If the session was loaded on startup from persistent store we shouldn't to a log.warn
               // but how to detect this? We don't have connectQosServer.isFromPersistenceRecovery(true);
               log.warning(text);
               // Throw a communication exception to go to polling until Pop3Driver is available
               throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, text);
            }
            // On client side we create it dynamically as configured in xmlBlaster.properties
            this.pop3Driver = Pop3Driver.getPop3Driver(glob, this.pluginConfig);
         }
      }
      return this.pop3Driver;
   }
   
   /**
    * TODO: Put into engine.Global and util.Global (see EventPlugin.java)
    * @return
    * @throws XmlBlasterException
    */
   public SmtpClient getSmtpClient() throws XmlBlasterException {
      if (this.smtpClient == null) {
         this.smtpClient = (SmtpClient) glob.getObjectEntry(SmtpClient.OBJECTENTRY_KEY);

         if (this.smtpClient == null) {
            if (this.glob.isServerSide()) {
               // On server side the SmtpClient is created by the runlevel manager as configured in xmlBlasterPlugins.xml
               String text = "Please register a SmtpClient in xmlBlasterPlugins.xml to have 'email' support";
               // If the session was loaded on startup from persistent store we shouldn't to a log.warn
               // but how to detect this?
               log.warning(text);
               throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, text);
            }
            // On client side we create it dynamically as configured in xmlBlaster.properties
            this.smtpClient = SmtpClient.getSmtpClient(this.glob, this.pluginConfig);
         }
      }
      return this.smtpClient;
   }
   
  // public String getType() {
  //    return "email";
  // }
   
   /**
    * Defaults to one day. 
    */
   public long getDefaultResponseTimeout() {
      return Constants.DAY_IN_MILLIS;
   }
   
   /**
    * Defaults to one day. 
    */
   public long getDefaultUpdateResponseTimeout() {
      return Constants.DAY_IN_MILLIS;
   }
   
   /**
    * Which parser to use. 
    * The 'email' protocol uses as a default setting the XbfParser
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
            getPop3Driver().registerForEmail(getEmailSessionId(), requestId, this);
         }

         // super calls our sendMessage() which effectively sends the message
         Object response = super.requestAndBlockForReply(msgInfo,
               expectingResponse, false);
         return response;
      } catch (XmlBlasterException e) {
         // ErrorCode.USER* errors can't arrive here
         throw e;
      } catch (Throwable e) {
         e.printStackTrace();
         throw new XmlBlasterException(glob,
               ErrorCode.COMMUNICATION_NOCONNECTION, ME,
               "Sorry, email sending " + methodName.toString()
                     + " failed, no mail sent to "
                     + addressBase.getRawAddress(), e);
      } finally {
         if (expectingResponse)
            getPop3Driver().deregisterForEmail(getEmailSessionId(), requestId);
      }
   }

   /**
    * Notification by Pop3Driver when a (response) email message arrives. 
    * Enforced by I_ResponseListener
    */
   public void incomingMessage(String requestId, Object response) {
      EmailData emailData = (EmailData) response;

      AttachmentHolder msgUnitAttachmentHolder = null;
      String pop3Url = null;
      try {
         pop3Url = getPop3Driver().getPop3Url(); // for logging only
         msgUnitAttachmentHolder =
            emailData.getMsgUnitAttachment(); // "*.xbf", "*.xbfz", "*.xmlz", ...
      } catch (Throwable e) {
         log.warning("Error parsing email data from "
               + pop3Url
               + ", please check the format: "
               + e.toString());
         return;
      }

      if (msgUnitAttachmentHolder == null) {
         log.warning("Got email from POP3 but there was no MsgUnit attachment, we ignore it: " + emailData.toXml(true));
         return;
      }

      byte[] encodedMsgUnit = msgUnitAttachmentHolder.getContent();
      MsgInfo[] msgInfos = null;
      try {
         if (MsgInfo.isCompressed(msgUnitAttachmentHolder.getFileName(), msgUnitAttachmentHolder.getContentType())) {
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
         String parserClassName = MsgInfoParserFactory.instance().guessParserName(msgUnitAttachmentHolder.getFileName(), msgUnitAttachmentHolder.getContentType());
         msgInfos = MsgInfo.parse(glob, this.progressListener, encodedMsgUnit, parserClassName);
         if (msgInfos.length < 1) {
            log.severe("Unexpected msgInfo with length==0");
            Thread.dumpStack();
            return;
         }
         for (int j=0; j<msgInfos.length; j++) {
            MsgInfo msgInfo = msgInfos[j];
            msgInfo.setRequestIdGuessed(emailData.isRequestIdFromSentDate());
            msgInfo.setBounceObject(BOUNCE_MAILFROM_KEY, emailData.getFrom());
            // The messageId could be in the subject and not in the attachment
            msgInfo.setBounceObject(BOUNCE_MESSAGEID_KEY, emailData.getMessageId());
            AttachmentHolder[] attachments = emailData.getAttachments();
            for (int i=0; i<attachments.length; i++) {
               AttachmentHolder a = attachments[i];
               if (a == msgUnitAttachmentHolder)
                  continue;
               // TODO: Determine which attachments to bounce
               msgInfo.setBounceObject(a.getFileName(), a);
            }
         }

      } catch (Throwable e) {
         log.warning("Error parsing email data from "
                           + pop3Url
                           + ", check if client and server have identical compression settings: "
                           + e.toString() + ": " + emailData.toXml(true));
         return;
      }

      // Response and Exception messages should NEVER expire
      if (emailData.isExpired() && msgInfos[0].isInvoke()) {
         log.warning("Message is expired, we discard it: " + emailData.toString());
         return;
      }

      // For XmlScript && INVOKE we could have multiple message bundled
      // else length is always 1!
      for (int i=0; i<msgInfos.length; i++) {
         MsgInfo msgInfo = msgInfos[i];
         // If counterside has stripped information we add it again from the messageId attachment
         if (msgInfo.getRequestId().length() == 0)
            msgInfo.setRequestId(emailData.getRequestId());
         if (msgInfo.getSecretSessionId().length() == 0)
            msgInfo.setSecretSessionId(emailData.getSessionId());
   
         try {
            if (i==0 && msgInfo.isInvoke()) {
               if (isLoopingMail(msgInfo, emailData))
                  return;
            }
   
            /*
              Memory leak cleanup is handled by EmailDriver.java on LogoutEvent
              if (MethodName.DISCONNECT.equals(msgInfo.getMethodName())) {
                removeFromLoopProtection(emailData.getFrom());
              }
            */
            
            // This wakes up the blocking thread of sendEmail() and returns the
            // returnQos or the received invocation
            // On server side it typically invokes the core connect() or publish() etc.
            if (receiveReply(msgInfo, false) == false) {
               log.warning("Error parsing email data from "
                     + getPop3Driver().getPop3Url()
                     + ", CONNECT etc is not yet implemented");
            }
            
            if (i==0 && msgInfos.length > 1 && MethodName.CONNECT.equals(msgInfo.getMethodName())) {
               // If multiple requests where bundled pass the others the secret session id
               for (int k=1; k<msgInfos.length; k++)
                  msgInfos[k].setSecretSessionId(msgInfo.getSecretSessionId());
            }
         } catch (Throwable e) {
            log.warning("Can't process email data from "
                  + pop3Url + ": " + e.toString());
            return;
         }
      }
   }
   
   /**
    * Some weak looping protection. 
    * Assume requestId to be strictly increasing
    * to detect email duplicates (which can be produced by MTAs)
    */
   protected boolean isLoopingMail(MsgInfo msgInfo, EmailData emailData) {
      try {
         LoopProtection loopProtection = null;
         synchronized (this.senderLoopProtectionMap) {
            loopProtection = (LoopProtection)this.senderLoopProtectionMap.get(emailData.getFrom());
            if (loopProtection == null) {
               loopProtection = new LoopProtection(emailData.getFrom(), -1, -1);
               this.senderLoopProtectionMap.put(loopProtection.key, loopProtection);
            }
         }
         long currRequestId = new Long(msgInfo.getRequestId()).longValue();
         if (MethodName.PING.equals(msgInfo.getMethodName())) {
            if (loopProtection.lastPingRequestId >= 0 && currRequestId <= loopProtection.lastPingRequestId) {
               log.warning("Can't process email data from "
                     + getPop3Driver().getPop3Url()
                     + ", it seems to be looping as requestId="+currRequestId+" (last="+loopProtection.lastPingRequestId+") has been processed already"
                     + ": " + emailData.toXml(true));
               return true;
            }
            loopProtection.lastPingRequestId = currRequestId;
         }
         else {
            if (loopProtection.lastRequestId >= 0 && currRequestId <= loopProtection.lastRequestId) {
               log.warning("Can't process email data from "
                     + getPop3Driver().getPop3Url()
                     + ", it seems to be looping as requestId="+currRequestId+" (last="+loopProtection.lastRequestId+") has been processed already"
                     + ": " + emailData.toXml(true));
               return true;
            }
            loopProtection.lastRequestId = currRequestId;
         }
      }
      catch (Throwable e) {
         log.warning("Cant handle requestId '"+msgInfo.getRequestId()+"' to be of type long");
      }
      return false;
   }

   /**
    * Cleanup
    * @param key this is the sender email address, for example "xmlBlaster@localhost" for a client
    *         or "demo@localhost" on server side (emailData.getFrom())
    * @return The removed entry or null if not found
    */
   protected LoopProtection removeFromLoopProtection(String key) {
      LoopProtection loopProtection = null;
      synchronized (this.senderLoopProtectionMap) {
         loopProtection = (LoopProtection)this.senderLoopProtectionMap.remove(key);
         if (loopProtection == null) {
            // Is OK for email plugins which only invokes
            log.fine("No loopProtection entry found for sender " + key 
                  + " which is OK in most cases, current known are: "
                  + getLoopProtectionList());
         }
      }
      return loopProtection;
   }

   /**
    * Returns a comma seperated list of all 'from email addresses'.
    * Used for JMX. 
    * @return For example "joe@localhost,blue@localhost", never null
    */
   public String getLoopProtectionList() {
      LoopProtection[] arr = getLoopProtections();
      StringBuffer sb = new StringBuffer(512);
      for (int i=0; i<arr.length; i++) {
         if (i>0) sb.append(",");
         sb.append(arr[i].key);
      }
      return sb.toString();
   }
   
   protected LoopProtection[] getLoopProtections() {
      synchronized (this.senderLoopProtectionMap) {
         return (LoopProtection[])this.senderLoopProtectionMap.values().toArray(new LoopProtection[this.senderLoopProtectionMap.size()]);
      }
   }

   /**
    * @return messageId="<messageId><sessionId>sessionId:4423c443</sessionId><requestId>3</requestId><methodName>subscribe</methodName></messageId>"
    */
   protected String createMessageId(MsgInfo msgInfo, String requestId, MethodName methodName, Timestamp expiryTimestamp) {
      String messageId = (String)msgInfo.getBounceObject(BOUNCE_MESSAGEID_KEY);
      if (messageId == null) {
         String sessionId = getEmailSessionId(msgInfo);
         {
            // Hardcoded simplification of email subject for messages send manually from a normal email client
            // like this the GUI user can simply push the reply button to send further publish() etc.
            // RE: <messageId><sessionId>sessionId:127.0.0.2-null-1134497522115--102159664-3</sessionId></messageId>
            //if (methodName.equals(MethodName.CONNECT)) {
               // We send the secretSessionId in the SUBJECT of a ConnectReturnQos
               //sessionId = msgInfo.getSecretSessionId();
            //}
   
            if (!msgInfo.isInvoke()) // Responses and exceptions should never expire
               expiryTimestamp = null;
            
            if (msgInfo.isRequestIdGuessed()) {
               requestId = null;
               methodName = null;
            }
         }
         
         messageId = EmailData.createMessageId(sessionId,
            requestId, methodName, expiryTimestamp);
      }
      return messageId;
   }
   
   /**
    * Extends RequestReplyExecutor.sendMessage
    */
   protected void sendMessage(MsgInfo msgInfo, String requestId,
         MethodName methodName, boolean udp) throws XmlBlasterException,
         IOException {

      String subject = this.subjectTemplate;
      
      Timestamp expiryTimestamp = getExpiryTimestamp(methodName);
      String messageId = createMessageId(msgInfo, requestId, methodName, expiryTimestamp);

      if (subject != null && subject.length() > 0) {
         // Transport messageId in subject if token "${xmlBlaster/email/messageId}" is present:
         // The responseQos gets it already replaced and the messageId remains same (which we must assure)
         subject = ReplaceVariable.replaceFirst(subject, SUBJECT_MESSAGEID_TOKEN, messageId); 
         // and for testing as attachment, for example this.messageIdFileName="messageId.mid"
      }

      // Serialize the pay load
      byte[] origAttachment = msgInfo.getMsgInfoParser(getMsgInfoParserClassName(), pluginConfig).createRawMsg(msgInfo);
      boolean isCompressed = false;
      byte[] payload = origAttachment;
      if (isCompressZlib()) {
         if (origAttachment.length > getMinSizeForCompression()) { // > 0
            this.compressor.reset();
            this.compressor.setInput(origAttachment, 0, origAttachment.length);
            this.compressor.finish();
            payload = new byte[origAttachment.length+64];
            int compSize = compressor.deflate(payload);
            if (compSize <= 0) {
               throw new IOException("Compression exception, got 0 bytes output: " + MsgInfo.toLiteral(msgInfo.createRawMsg()));
            }
            if (compSize >= origAttachment.length) { // Compression didn't help
               payload = origAttachment;  
               if (log.isLoggable(Level.FINE)) log.fine("No compression of attachment as size increased from " + origAttachment.length + " to " + compSize + ", we leave it uncompressed");
            }
            else {
               isCompressed = true;
               byte[] tmp = new byte[compSize];
               System.arraycopy(payload, 0, tmp, 0, compSize);
               payload = tmp;
               if (log.isLoggable(Level.FINE)) log.fine("Compressing attachment of size " + origAttachment.length + " to length " + payload.length);
            }
         }
         else {
            if (log.isLoggable(Level.FINE)) log.fine("Compressing of attachment skipped as size of " + origAttachment.length + " is smaller minSize " + getMinSizeForCompression());
         }
      }
      
      // The real message blob, for example "xmlBlasterMessage.xbf"
      String payloadFileName = this.payloadFileNamePrefix + 
         msgInfo.getMsgInfoParser(getMsgInfoParserClassName(), pluginConfig).getExtension(isCompressed);

      InternetAddress toAddr = this.toAddress;
      String to = (String)msgInfo.getBounceObject(BOUNCE_MAILTO_KEY);
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
      emailData.setCc(this.cc);
      emailData.setBcc(this.bcc);
      emailData.setExpiryTime(expiryTimestamp);
      String payloadMimetype = msgInfo.getMsgInfoParser(getMsgInfoParserClassName(), pluginConfig).getMimetype(isCompressed);
      emailData.addAttachment(new AttachmentHolder(payloadFileName, payloadMimetype, payload));
      emailData.addAttachment(new AttachmentHolder(this.messageIdFileName, messageId));
      // Bounce all other attachments back to sender
      AttachmentHolder[] attachments = msgInfo.getBounceAttachments();
      for (int i=0; i<attachments.length; i++) {
         AttachmentHolder a = attachments[i];
         if (this.messageIdFileName.equals(a.getFileName()))
            continue; // added alread, see above
         emailData.addAttachment(a);
      }
      
      getSmtpClient().sendEmail(emailData);
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

   /**
    * This method shuts down the driver.
    * <p />
    */
   public void shutdown() {
      this.isShutdown = true;
      synchronized (this.senderLoopProtectionMap) {
         this.senderLoopProtectionMap.clear();
      }
      try {
         getPop3Driver().deregisterForEmail(this);
      } catch (XmlBlasterException e) {
         e.printStackTrace();
      }
   }
   
   /** I_AdminPlugin#isShutodwn */
   public boolean isShutdown() {
      return this.isShutdown;
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

   /**
    * Is overwritten for example by EmailDriver.java singleton. 
    * @param msgInfo
    * @return
    */
   public String getEmailSessionId(MsgInfo msgInfo) {
      return getEmailSessionId();
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
         log.warning("The current email callback implementation can handle max one connection per email account (like 'joe' on the POP3 server) if you don't supply a positive sessionId");
         setEmailSessionId(sessionName.getLoginName());
      }
   }

   /**
    * @return Returns the cc.
    */
   public String getCc() {
      return this.cc;
   }

   /**
    * Send all emails additionally to the given CC addresses. 
    * You can pass a comma separated list of addresses.
    * @param cc The copy addresses, for example "joe@localhost,jack@localhost"
    */
   public void setCc(String cc) {
      this.cc = cc;
   }

   /** JMX */
   public void setTo(String to) throws IllegalArgumentException {
      try {
         this.toAddress = new InternetAddress(to);
      } catch (AddressException e) {
         throw new IllegalArgumentException(
               ME + " Illegal 'to' address '" + to + "'");
      }
   }

   /**
    * @return Returns the 'to' email address.
    */
   public String getTo() {
      return (this.toAddress == null) ? "" : this.toAddress.toString();
   }

   /** JMX */
   public void setFrom(String from) throws IllegalArgumentException {
      try {
         this.fromAddress = new InternetAddress(from);
      } catch (AddressException e) {
         throw new IllegalArgumentException(
               ME + " Illegal 'from' address '" + from + "'");
      }
   }

   /**
    * @return Returns the 'from' email address.
    */
   public String getFrom() {
      return (this.fromAddress == null) ? "" : this.fromAddress.toString();
   }

   /**
    * @return Returns the blind copy email addresses or null
    */
   public String getBcc() {
      return this.bcc;
   }

   /**
    * Send all emails additionally to the given bcc. 
    * You can pass a comma separated list of addresses.
    * @param bcc The blind copy addresses, for example "joe@localhost,jack@localhost"
    */
   public void setBcc(String bcc) {
      this.bcc = bcc;
   }
   
   /**
    * @return a human readable usage help string
    */
   public java.lang.String usage() {
      return super.usage() + "\n" + Global.getJmxUsageLinkInfo(this.getClass().getName(), null);
   }

   /**
    * @return A link for JMX usage
    */
   public java.lang.String getUsageUrl() {
      return Global.getJavadocUrl(this.getClass().getName(), null);
   }

   /* dummy to have a copy/paste functionality in jconsole */
   public void setUsageUrl(java.lang.String url) {
   }
}
