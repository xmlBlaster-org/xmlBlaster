/*------------------------------------------------------------------------------
 Name:      SmtpClient.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 Comment:   javac EmailData.java SmtpClient.java
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.util.protocol.email;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Message;
import javax.mail.Transport;
import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.AddressException;
import javax.mail.internet.MimeMultipart;

import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.XbUri;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.I_PluginConfig;
import org.xmlBlaster.util.plugin.PluginInfo;

import edu.emory.mathcs.backport.java.util.concurrent.ArrayBlockingQueue;
import edu.emory.mathcs.backport.java.util.concurrent.BlockingQueue;

/**
 * This class sends outgoing emails.
 * <p>
 * Developer note: Please don't use log.severe() or log.warning() to avoid
 * recursion for logging-notification mails.
 * 
 * @see <a
 *      href="http://www-106.ibm.com/developerworks/java/library/j-james1.html">James
 *      MTA</a>
 * @see <a href="http://java.sun.com/products/javamail/javadocs/index.html">Java
 *      Mail API</a>
 * @see <a href="http://java.sun.com/developer/onlineTraining/JavaMail/contents.html">Javamail tutorial</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.email.html">The protocol.email requirement</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class SmtpClient extends Authenticator implements I_Plugin, SmtpClientMBean {

   private static Logger log = Logger.getLogger(SmtpClient.class.getName());

   private Global glob;
   
   private ContextNode contextNode;
   
   private PluginInfo pluginInfo;

   // If the mail.smtp.from changes we would need a new session instance with its own properties
   private Session session;

   private PasswordAuthentication authentication;

   private XbUri xbUri;
   
   private boolean isInitialized;
   
   /** 
    * Setting this to true we can force the messageId attachment to
    * always be base64 encoded.
    * <br />
    * Javamail does base64 encoding automatically if need so
    * the default of this variable is false.
    */
   private boolean messageIdForceBase64;

   /** 
    * Setting this to true we can force the MsgUnit attachment to
    * always be base64 encoded.
    * <br />
    * Javamail does base64 encoding automatically if need so
    * the default of this variable is false.
    */
   private boolean contentForceBase64;
   
   private boolean breakLongMessageIdLine;
   
   /**
    * mail.smtp.timeout
    * Socket I/O timeout value in milliseconds. Default is infinite timeout.
    */
   private int smtpIoTimeout;

   /**
    * mail.smtp.connectiontimeout
    * Socket connection timeout value in milliseconds. Default is infinite timeout.
    */
   private int smtpConnectionTimeout;

   /**
    * Add 'Expires:' email header. 
    * If the message to send has an expiry date and this
    * addExpiresHeader=true we send an 'Expires:' header in the email
    * (Expiry Date Indication). 
    * <br />
    * Supported as new RFC 822 header (Expires:).  In general, no
    * automatic action can be expected by MTAs.
    * <br />
    * Defaults to true.
    * @see http://www.faqs.org/rfcs/rfc2156.html
    */
   private boolean addExpiresHeader;
   
   /**
    * Comma separated list of fileName extensions to send attachment as inline
    */
   private String inlineExtension;

   /** My JMX registration */
   private Object mbeanHandle;

   public static final String OBJECTENTRY_KEY = SmtpClient.class.getName();
   
   private AsyncSender asyncSender;
   private int asyncSendQueueSizeMax = 100;
   private boolean asyncSendQueueBlockOnOverflow = false;
   private BlockingQueue asyncSendQueue;
   
   /**
    * Consumer pattern.
    * The mail can be send asynchronously to decouple
    * the sending thread from a blocking smtp server
    * @author marcel
    */
   class AsyncSender implements Runnable {
      private final SmtpClient smtpClient;
      private final BlockingQueue queue;

      AsyncSender(SmtpClient smtpClient, BlockingQueue q) {
         this.smtpClient = smtpClient;
         this.queue = q;
      }

      public void run() {
         while (true) {
            EmailData emailData = null;
            try {
               emailData = (EmailData)queue.take();
               this.smtpClient.sendEmailSync(emailData);
            } catch (XmlBlasterException ex) {
               String dump = (emailData == null) ? "" : emailData.toXml(true);
               log.severe("Sending asynchronously of mail failed:" + ex.toString() + "\n" + dump);
            } catch (Throwable ex) {
               ex.printStackTrace();
            }
         }
      }
   } // Consumer
   
   /**
    * @return Returns the user.
    */
   public String getUser() {
      return this.xbUri.getUser();
   }

   /**
    * The SmtpClient is a singleton in the Global scope. 
    * Access this singleton for the given global, and if it
    * doesn't exist create one instance.
    * @param glob
    * @param pluginInfo
    * @return never null
    * @throws XmlBlasterException 
    */
   public static SmtpClient getSmtpClient(Global glob, I_PluginConfig pluginConfig)
                              throws XmlBlasterException {
      Global serverNode = (org.xmlBlaster.util.Global)glob.getObjectEntry(Constants.OBJECT_ENTRY_ServerScope);
      if (serverNode == null) serverNode = glob;
      
      SmtpClient smtpClient = (SmtpClient)serverNode.getObjectEntry(OBJECTENTRY_KEY);
      if (smtpClient != null)
         return smtpClient;
      
      synchronized(glob.objectMapMonitor) {
         smtpClient = (SmtpClient)serverNode.getObjectEntry(OBJECTENTRY_KEY);
         if (smtpClient == null) {
            smtpClient = new SmtpClient();
            smtpClient.setSessionProperties(null, glob, pluginConfig); // adds itself as ObjectEntry
         }
         return smtpClient;
      }
   }

   /**
    * Called from runlevel manager on server side. 
    */
   public SmtpClient() {
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver.
    * @return The configured type in xmlBlasterPlugins.xml, defaults to "smtp"
    */
   public String getProtocolId() {
      return (this.pluginInfo == null) ? "smtp" : this.pluginInfo.getType();
   }

   /**
    * Enforced by I_Plugin
    * 
    * @return The configured type in xmlBlaster.properties, defaults to "smtp"
    */
   public String getType() {
      return getProtocolId();
   }

   /**
    * The command line key prefix
    * @return Defaults to "plugin/smtp"
    */
   public String getEnvPrefix() {
      return "plugin/" + getType().toLowerCase();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return (this.pluginInfo == null) ? "1.0" : this.pluginInfo.getVersion();
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). The
    * SmtpClient singleton is registered in the Global object store.
    * 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo)
         throws XmlBlasterException {
      this.glob = glob;
      this.pluginInfo = pluginInfo;

      setSessionProperties(null, glob, pluginInfo);

      // Make this singleton available for others
      // key="org.xmlBlaster.util.protocol.email.SmtpClient"
      Global serverNode = (org.xmlBlaster.util.Global)this.glob.getObjectEntry(Constants.OBJECT_ENTRY_ServerScope);
      if (serverNode == null) serverNode = this.glob;
      serverNode.addObjectEntry(OBJECTENTRY_KEY, this);
   }

   /**
    * Used by Authenticator to access user name and password
    */
   public PasswordAuthentication getPasswordAuthentication() {
      if (log.isLoggable(Level.FINE))
         log.fine("Entering getPasswordAuthentication: "
               + this.authentication.toString());
      return this.authentication;
   }

   /**
    * Set session properties and create a session.
    * <p>
    * Example settings:
    * </p>
    * 
    * <pre>
    * Properties props = System.getProperties();
    * props.put(&quot;mail.debug&quot;, &quot;true&quot;);
    * props.put(&quot;mail.smtp.url&quot;, &quot;smtp://demo:secret@localhost:2525&quot;);
    * </pre>
    * 
    * <p>
    * If a property is not found <tt>System.getProperty()</tt> is consulted.
    * </p>
    * 
    * @see <a
    *      href="http://java.sun.com/products/javamail/javadocs/com/sun/mail/smtp/package-summary.html">SMTP
    *      API</a>
    * @see <a
    *      href="http://java.sun.com/products/javamail/javadocs/com/sun/mail/pop3/package-summary.html">POP3
    *      API</a>
    */
   public synchronized void setSessionProperties(Properties props, Global glob,
         I_PluginConfig pluginConfig) throws XmlBlasterException {
      
      if (this.isInitialized) {
         if (log.isLoggable(Level.FINE))
            log.fine("Ignoring multiple setSessionProperties() call");
         return;
      }
      this.glob = glob;

      if (props == null)
         props = new Properties();

      if (props.getProperty("mail.debug") == null)
         props.put("mail.debug", glob.get("mail.debug", System.getProperty(
               "mail.debug", "false"), null, pluginConfig));

      String uri = glob.get("mail.smtp.url", System
            .getProperty("mail.smtp.url"), null, pluginConfig);
      if (uri == null) {
         throw new XmlBlasterException(glob,
               ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, "SmtpClient",
               "Please configure a mail.smtp.url to access the SMTP MTA, for example 'mail.smtp.url=smtp://joe:password@smtp.xmlBlaster.org:25'");
      }
      try {
         this.xbUri = new XbUri(uri.trim());
      } catch (URISyntaxException e) {
         throw new XmlBlasterException(glob,
               ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, "SmtpClient",
               "Your URI '" + uri +
               "' is illegal", e);
      }
      if (this.xbUri.getHost() == null) {
         throw new XmlBlasterException(glob,
               ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, "SmtpClient",
               "Your URI '" + this.xbUri.toString() +
               "' is illegal, expecting something like 'smtp://user:password@host:port'");
      }
      
      props.put("mail.user", this.xbUri.getUser());
      if (this.xbUri.getPassword() != null) {
         //props.put("mail.password", this.xbUri.getPassword()); // I don't think "mail.password" is ever used, remove again?
         props.setProperty("mail.smtp.auth", "true"); //Indicate that authentication is required at smtp server
      }
      props.put("mail.transport.protocol", this.xbUri.getScheme());
      props.put("mail.smtp.host", this.xbUri.getHost());
      if (this.xbUri.getPort() > 0)
         props.put("mail.smtp.port", ""+this.xbUri.getPort());
      
      String p;
      if (props.getProperty("messageIdForceBase64") == null)
         props.put("messageIdForceBase64", ""+glob.get("messageIdForceBase64", false, null,
               pluginConfig));
      p = props.getProperty("messageIdForceBase64");
      this.messageIdForceBase64 = new Boolean(p).booleanValue();
      
      if (props.getProperty("contentForceBase64") == null)
         props.put("contentForceBase64", ""+glob.get("contentForceBase64", false, null,
               pluginConfig));
      p = props.getProperty("contentForceBase64");
      this.contentForceBase64 = new Boolean(p).booleanValue();

      if (props.getProperty("addExpiresHeader") == null)
         props.put("addExpiresHeader", ""+glob.get("addExpiresHeader", true, null,
               pluginConfig));
      p = props.getProperty("addExpiresHeader");
      this.addExpiresHeader = new Boolean(p).booleanValue();
      
      if (props.getProperty("breakLongMessageIdLine") == null)
         props.put("breakLongMessageIdLine", ""+glob.get("breakLongMessageIdLine", false, null,
               pluginConfig));
      p = props.getProperty("breakLongMessageIdLine");
      this.breakLongMessageIdLine = new Boolean(p).booleanValue();

      if (props.getProperty("inlineExtension") == null)
         props.put("inlineExtension", ""+glob.get("inlineExtension", "", null,
               pluginConfig));
      this.inlineExtension = props.getProperty("inlineExtension"); // like ".txt,.xml"
      
      if (props.getProperty("mail.smtp.timeout") == null)
          props.put("mail.smtp.timeout", ""+glob.get("mail.smtp.timeout",
        		  Integer.MAX_VALUE, null,
                pluginConfig));
       p = props.getProperty("mail.smtp.timeout");
       this.smtpIoTimeout = new Integer(p).intValue();

       if (props.getProperty("mail.smtp.connectiontimeout") == null)
           props.put("mail.smtp.connectiontimeout", ""+glob.get("mail.smtp.connectiontimeout",
         		  Integer.MAX_VALUE, null,
                 pluginConfig));
        p = props.getProperty("mail.smtp.connectiontimeout");
        this.smtpConnectionTimeout = new Integer(p).intValue();

      // Pass "this" for SMTP authentication with Authenticator
      this.authentication = new PasswordAuthentication(getUser(), this.xbUri.getPassword());
      this.session = Session.getDefaultInstance(props, this);
      this.isInitialized = true;
      
      // Setup asynchronous sending thread for outgoing emails
      this.asyncSendQueueSizeMax = glob.get("asyncSendQueueSizeMax",
            this.asyncSendQueueSizeMax, null,
            pluginConfig);
      if (this.asyncSendQueueSizeMax > 0) {
         this.asyncSendQueueBlockOnOverflow = glob.get("asyncSendQueueBlockOnOverflow",
               this.asyncSendQueueBlockOnOverflow, null,
               pluginConfig);
         this.asyncSendQueue = new ArrayBlockingQueue(this.asyncSendQueueSizeMax);
         this.asyncSender = new AsyncSender(this, this.asyncSendQueue);
         Thread t = new Thread(this.asyncSender, getType() + "-AsyncSender");
         t.start();
      }
      
      if (this.mbeanHandle == null) {
        // For JMX instanceName may not contain ","
        this.contextNode = new ContextNode(ContextNode.SERVICE_MARKER_TAG,
                            "SmtpClient[" + getType() + "]", 
                            glob.getScopeContextNode());
        this.mbeanHandle = glob.registerMBean(this.contextNode, this);
      }

      log.info("SMTP client to '" + this.xbUri.getUrlWithoutPassword() + "' is ready");
   }
   
   public void sendEmailAsync(EmailData emailData) throws XmlBlasterException {
      if (emailData == null) throw new IllegalArgumentException("SmtpClient.sendEmailAsync(): Missing argument emailData");
      AsyncSender as = this.asyncSender;
      BlockingQueue queue = this.asyncSendQueue;
      if (as == null || queue == null) {
         throw new XmlBlasterException(glob,
               ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, "SmtpClient",
               "Please configure asyncSendQueueSizeMax > 0 for sending emails asynchronously, the mail is lost: " + emailData.toXml(true));
      }
      try {
         if (this.asyncSendQueueBlockOnOverflow)
            queue.put(emailData);
         else {
            boolean added = queue.offer(emailData);
            if (!added) {
               throw new XmlBlasterException(glob,
                     ErrorCode.RESOURCE_CONFIGURATION_CONNECT, "SmtpClient",
                     "Can't send email, queueu overflow of asyncSendQueueSizeMax="+this.asyncSendQueueSizeMax+", there may be a problem with your Smtp server, the mail is lost: " + emailData.toXml(true));
            }
         }
      } catch (XmlBlasterException ex) {
         throw ex;
      } catch (Throwable ex) {
         ex.printStackTrace();
         throw new XmlBlasterException(glob,
               ErrorCode.INTERNAL_CONNECTIONFAILURE, "SmtpClient",
               "Can't send email asynchronously, there may be a problem with your Smtp server, the mail is lost: " + emailData.toXml(true), ex);
      }
   }

   /**
    * Access the mailing session.
    */
   public Session getSession() {
      return this.session;
   }

   public Message getMessage() {
      return new MimeMessage(getSession());
   }

   /**
    * Send a ready prepared message.
    * <p>
    * Usually you choose the convenience method sendEmail()
    * </p>
    */
   public void send(Message message) throws MessagingException {
/*
 * Reuse transport for better performance:
 * See http://java.sun.com/products/javamail/FAQ.html
  MimeMessage msg = ...;
   construct message
   msg.saveChanges();
   Transport t = session.getTransport("smtp");
   t.connect();
   for (int i = 0; .....) {
     t.sendMessage(msg, new Address[] { recipients[i] });
   }
   t.close();
*/

      try {
         Transport.send(message);
      } catch (MessagingException e) {
         throw e;
      }
   }

   public void sendEmail(String from, String to, String subject, String body)
         throws AddressException, MessagingException {
      Message message = getMessage();
      try {
         message.setFrom(new InternetAddress(from));
         InternetAddress tos[] = new InternetAddress[1];
         tos[0] = new InternetAddress(to);
         message.setRecipients(Message.RecipientType.TO, tos);
         message.setSubject(subject);
         message.setContent(body, "text/plain");
      } catch (MessagingException e) {
         throw e;
      }
      send(message);
   }

   public void sendEmail(String from, String to, String subject, String body,
         String encoding) throws AddressException, MessagingException {
      sendEmail(new InternetAddress(from), new InternetAddress(to), subject,
            body, encoding);
   }

   /**
    * @param body
    *           Is assumed to be of mime type "text/plain"
    * @param encoding
    *           (charset) for example "UTF-8", will set the mail header:
    * 
    * <pre>
    *          Content-Type: text/plain; charset=UTF-8
    * </pre>
    */
   public void sendEmail(InternetAddress from, InternetAddress to,
         String subject, String body, String encoding) throws AddressException,
         MessagingException {
      MimeMessage message = new MimeMessage(getSession());
      try {
         message.setFrom(from);
         InternetAddress tos[] = new InternetAddress[1];
         tos[0] = to;
         message.setRecipients(Message.RecipientType.TO, tos);
         message.setSubject(subject, encoding);
         message.setText(body, encoding); // is automatically "text/plain"
      } catch (MessagingException e) {
         throw e;
      }
      send(message);
   }

   /**
    * @param attachmentName2
    *           If not null this second attachment is added as "text/plain"
    * @param encoding
    *           For example "UTF-8"
    */
   public void sendEmail(InternetAddress from, InternetAddress to,
         String subject, String attachmentName, byte[] attachment,
         String attachmentName2, String attachment2, String encoding)
         throws XmlBlasterException {
      try {
         MimeMessage message = new MimeMessage(getSession());
         message.setFrom(from);
         InternetAddress tos[] = new InternetAddress[1];
         tos[0] = to;
         message.setRecipients(Message.RecipientType.TO, tos);
         message.setSubject(subject, encoding);

         // MimeBodyPart mbp1 = new MimeBodyPart(attachment);
         MimeBodyPart mbp1 = new MimeBodyPart(); // "application/x-any"
         // "application/xmlBlaster-xbformat"
         DataSource ds = new ByteArrayDataSource(attachment,
               "application/xmlBlaster-xbformat");
         mbp1.setDataHandler(new DataHandler(ds));
         mbp1.setFileName(attachmentName);
         // mbp1.getContentType(); "application/octet-stream"

         // create the Multipart and add its parts to it
         Multipart mp = new MimeMultipart();
         mp.addBodyPart(mbp1);

         if (attachmentName2 != null) {
            MimeBodyPart mbp2 = new MimeBodyPart(); // "text/plain"
            mbp2.setText(attachment2, encoding);
            mbp2.setFileName(attachmentName2);
            mp.addBodyPart(mbp2);
         }

         // add the Multipart to the message
         message.setContent(mp);

         // set the Date: header
         message.setSentDate(new Date());

         // message.setContent("A test mail from xmlBlaster with attachment in
         // xmlBlaster-SOCKET format", encoding); // is automatically
         // "text/plain"

         send(message);
         if (log.isLoggable(Level.FINE))
            log.fine("Successful send email from=" + from.toString() + " to="
                  + to.toString());
      } catch (Exception e) {
         throw new XmlBlasterException(Global.instance(),
               ErrorCode.COMMUNICATION_NOCONNECTION, "SmtpClient",
               "Email sending failed, no mail sent to " + to.toString(), e);
      }
   }
   
   /**
    * JMX
    */
   public String sendTestEmail(String to, String from) {
      if (to==null || to.trim().length() < 1 || "String".equalsIgnoreCase(to)) to = "demo@localhost";
      if (from==null || from.trim().length() < 1 || "String".equalsIgnoreCase(from)) from = "xmlBlaster@localhost";
      EmailData emailData = new EmailData(to, from, "[xmlBlaster SmtpClient] Test email", "Hello world!");
      try {
         sendEmail(emailData);
      } catch (XmlBlasterException e) {
         e.printStackTrace();
         throw new IllegalArgumentException(e.toString());
      }
      return "Send email from '" + from + "' to '" + to + "'";
   }

   public void sendEmail(EmailData emailData) throws XmlBlasterException {
      if (emailData == null) throw new IllegalArgumentException("SmtpClient.sendEmail(): Missing argument emailData");
      if (emailData.isSendAsync()) {
         sendEmailAsync(emailData);
      }
      else {
         sendEmailSync(emailData);
      }
   }

   /**
    * Send a mail.
    * @param emailData
    *        Container holding the message to send
    */
   public void sendEmailSync(EmailData emailData) throws XmlBlasterException {
      if (emailData == null) throw new IllegalArgumentException("SmtpClient.sendEmail(): Missing argument emailData");
      try {
         MimeMessage message = new MimeMessage(getSession());
         message.setFrom(emailData.getFromAddress());
         message.setRecipients(Message.RecipientType.TO, emailData.getToAddresses());
         if (emailData.getCc().length > 0)
            message.setRecipients(Message.RecipientType.CC, emailData.getCc());
         if (emailData.getBcc().length > 0)
            message.setRecipients(Message.RecipientType.BCC, emailData.getBcc());
         message.setSubject(emailData.getSubject(), Constants.UTF8_ENCODING);
         AttachmentHolder[] holder = emailData.getAttachments();

         if (holder.length == 0 && emailData.getContent() != null && emailData.getContent().length() > 0) {
            message.setText(emailData.getContent(), Constants.UTF8_ENCODING);
         }
         else {
            // create the Multipart and add its parts to it
            Multipart multi = new MimeMultipart();
   
            if (emailData.getContent() != null && emailData.getContent().length() > 0) {
               MimeBodyPart mbp = new MimeBodyPart();
               mbp.setFileName("content.txt");
               mbp.setText(emailData.getContent(), Constants.UTF8_ENCODING);
               mbp.setDisposition(MimeBodyPart.INLINE);
               multi.addBodyPart(mbp);
            }
   
            for (int i=0; i<holder.length; i++) {
               MimeBodyPart mbp = new MimeBodyPart();
               // 'AA xmlBlasterMessage.xbf' will be automatically quoted to '"AA xmlBlasterMessage.xbf"' by javamail implementation
               // 'xx.xbf' names will be send unquoted
               mbp.setFileName(holder[i].getFileName());
               byte[] content = holder[i].getContent();
               if (this.messageIdForceBase64 && emailData.isMessageIdAttachment(holder[i])
                    || this.contentForceBase64 && emailData.isMsgUnitAttachment(holder[i])) {
                  //We don't need to do it, javamail does it for us
                  //content = Base64.encode(holder[i].getContent()).getBytes(Constants.UTF8_ENCODING);
                  //Buggy: is not accepted by javamail: (Why? and How?)
                  mbp.setHeader(Constants.EMAIL_TRANSFER_ENCODING, Constants.ENCODING_BASE64);  // "Content-Transfer-Encoding", "base64");
               }
               else {
                  // http://www.ietf.org/rfc/rfc2045.txt
                  // The Quoted-Printable encoding REQUIRES that encoded lines be no more than 76
                  // characters long. (78 with CRLF), the line uses a trailing '=' as a soft line brake
                  mbp.setHeader(Constants.EMAIL_TRANSFER_ENCODING, Constants.ENCODING_QUOTED_PRINTABLE);  // "Content-Transfer-Encoding", "quoted-printable");
                  if (holder[i].hasExtensionFromList(this.inlineExtension))
                     mbp.setDisposition(MimeBodyPart.INLINE);
               }
               
               // Encoding violates RFC 2231 but is very common to do so for non-ASCII character sets:
               //mbp.setFileName(MimeUtility.encodeText(holder[i].getFileName()));
               if (holder[i].getContentType().startsWith("text/")) {
                  //String tmp = MimeUtility.encodeText(new String(content, Constants.UTF8_ENCODING), Constants.UTF8_ENCODING, Constants.ENCODING_QUOTED_PRINTABLE);
                  //mbp.setText(tmp, Constants.UTF8_ENCODING);
                  String contentStr = new String(content, Constants.UTF8_ENCODING);
                  if (this.breakLongMessageIdLine && emailData.isMessageIdAttachment(holder[i])) {
                     // <messageId><sessionId>unknown</sessionId><requestId>1140597982821000000</requestId><methodName>update</methodName><expires>2006-02-23T08:46:22.821Z</expires></messageId>
                     contentStr = ReplaceVariable.replaceAll(contentStr, "<requestId>", "\r\n<requestId>");
                     contentStr = ReplaceVariable.replaceAll(contentStr, "<methodName>", "\r\n<methodName>");
                     contentStr = ReplaceVariable.replaceAll(contentStr, "<expires>", "\r\n<expires>");
                  }
                  mbp.setText(contentStr, Constants.UTF8_ENCODING);
               }
               else {
                  // "application/xmlBlaster-xbformat"
                  DataSource ds = new ByteArrayDataSource(
                        content,
                        holder[i].getContentType());
                  mbp.setDataHandler(new DataHandler(ds));
               }
               multi.addBodyPart(mbp);
            }
   
            // add the Multipart to the message
            message.setContent(multi);
         } // else multipart
         
         // set the Date: header
         Date date = new Date();
         message.setSentDate(date);

         // Set the xmlBlaster specific expiry header field
         // Expires: Thu, 15 Dec 2005 21:45:01 +0100 (CET)
         // This could be evaluated by MTA plugins
         if (this.addExpiresHeader && emailData.getExpiryTime() != null) {
            //message.setHeader(EmailData.EXPIRES_HEADER, emailData.getExpiryTime().toString());
            message.setHeader(EmailData.EXPIRES_HEADER_RFC2156, MailUtil.dateTime(emailData.getExpiryTime()));
         }

         //log.severe("DEBUG ONLY: Trying to send email" + emailData.toXml(true));
         send(message);
         //log.severe("DEBUG ONLY: Successful send email" + emailData.toXml(true));
         if (log.isLoggable(Level.FINE))
            log.fine("Successful send email from=" + emailData.getFrom() + " to="
                  + emailData.getRecipientsList());
         if (log.isLoggable(Level.FINER))
            log.finer("Successful send email" + emailData.toXml(true));
      } catch (Exception e) {
         log.fine("Can't send mail: " + e.toString() + ": " + emailData.toXml(true));
         throw new XmlBlasterException(Global.instance(),
               ErrorCode.COMMUNICATION_NOCONNECTION, "SmtpClient",
               "Email sending failed, no mail sent from=" + emailData.getFrom() + " to="
                  + emailData.getRecipientsList(), e);
      }
   }

   public synchronized void shutdown() {
      if (this.session != null) {
         log.info("Shutting down SMTP mail client");
         this.glob.unregisterMBean(this.mbeanHandle);
         this.mbeanHandle = null;
         this.session = null;
      }
   }

   public String getSmtpUrl() {
        return this.xbUri.toString();
   }
   
   public void setSmtpUrl(String uri) {
      try {
         this.xbUri = new XbUri(uri);
      } catch (URISyntaxException e) {
         throw new IllegalArgumentException(
               "Your URI '" + uri +
               "' is illegal: " + e.toString());
      }
   }
   
   /**
    * @return a human readable usage help string
    */
   public java.lang.String usage() {
      return "Provides access to a remote SMTP mail transfer agent (MTA)"
      +Global.getJmxUsageLinkInfo(this.getClass().getName(), null);
   }
   
   /**
    * @return A link for JMX usage
    */
   public java.lang.String getUsageUrl() {
      return Global.getJavadocUrl(this.getClass().getName(), null);
   }
   
   /* dummy to have a copy/paste functionality in jconsole */
   public void setUsageUrl(java.lang.String url) {}
   
   /**
    * @return Returns the contentForceBase64.
    */
   public boolean isContentForceBase64() {
      return this.contentForceBase64;
   }
   
   /**
    * @param contentForceBase64 The contentForceBase64 to set.
    */
   public void setContentForceBase64(boolean contentForceBase64) {
      this.contentForceBase64 = contentForceBase64;
   }
   
   /**
    * @return Returns the messageIdForceBase64.
    */
   public boolean isMessageIdForceBase64() {
      return this.messageIdForceBase64;
   }
   
   /**
    * @param messageIdForceBase64 The messageIdForceBase64 to set.
    */
   public void setMessageIdForceBase64(boolean messageIdForceBase64) {
      this.messageIdForceBase64 = messageIdForceBase64;
   }

   /**
    * Standalone usage example:
    * <code>
    * java -Dmail.debug=true -Dmail.smtp.url=smtp://xmlBlaster:xmlBlaster@localhost:25 org.xmlBlaster.util.protocol.email.SmtpClient -from xmlBlaster@localhost -to xmlBlaster@localhost -expires +5000
    * </code>
    * The output is like
<pre>
Return-Path: <blue@localhost>
Received: from localhost ([127.0.0.1])
          by noty (JAMES SMTP Server 2.2.0) with SMTP ID 501
          for <blue@localhost>;
          Tue, 21 Feb 2006 10:54:58 +0100 (CET)
Message-ID: <13748088.01140515698827.JavaMail.xmlBlaster@noty>
Date: Tue, 21 Feb 2006 10:54:58 +0100 (CET)
From: blue@localhost
To: blue@localhost
Subject: Hi from java
MIME-Version: 1.0
Content-Type: text/plain; charset=UTF-8
Content-Transfer-Encoding: 7bit
Expires: Tue, 21 Feb 2006 10:55:00 +0100 (CET)

Some body text
</pre>
    * @see #setSessionProperties(Properties) for other properties
    */
   public static void main(String[] args) {
      Global glob = new Global(args);

      SmtpClient mail = null;
      try {
         mail = SmtpClient.getSmtpClient(glob, null);

         final boolean debug = false;

         // Here we create the mail Session manually without a JNDI lookup
         Properties props = System.getProperties();
         props.put("mail.debug", "" + debug);
         mail.setSessionProperties(props, glob, null);
         String from = glob.getProperty().get("from", "blue@localhost");
         String to = glob.getProperty().get("to", "blue@localhost");
         String subject = glob.getProperty().get("subject", "Hi from java");
         String content = glob.getProperty().get("content", "Some body text");
         String expires = glob.getProperty().get("expires", ""); // "+5000" means lives 5 sec from now on
         
         Timestamp ts = null;
         if (expires.length() > 0) {
            Date now = new Date();
            if (expires.indexOf("+") == 0) {
               ts = new Timestamp(Long.valueOf(expires.substring(1)).longValue() + now.getTime());
            }
            else
               ts = Timestamp.valueOf(expires);
         }

         EmailData msg = new EmailData(to, from, subject, content);
         if (ts != null) msg.setExpiryTime(ts);
         System.out.println("Sending message " + msg.toXml(true));
         mail.sendEmail(msg);
         System.out.println("Sent a message from '" + from + "' to '" + to
               + "'");
      } catch (Exception e) {
         e.printStackTrace();
         System.out.println("Mail failed: " + e.toString());
      } finally {
         if (mail != null)
            mail.shutdown();
      }
   }
   /* James 2.2.0 extension in
    *   org.apache.james.transport.mailets.RemoteDelivery.java
    * method
    *   boolean deliver(MailImpl mail, Session session)
    * to throw away expired mails:
    
 try {
    // Expiry Date Indication
    // Supported as new RFC 822 header (Expires:).
    // @see http://www.faqs.org/rfcs/rfc2156.html
    final String EXPIRES_HEADER_RFC2156 = "Expires";
    String[] expires = mail.getMessage().getHeader(EXPIRES_HEADER_RFC2156);
    if (expires != null && expires.length > 0) {
       // Date: Thu, 17 Nov 2005 16:45:12 +0100 (CET)
       String value = expires[0].trim();
       java.text.DateFormat df = new javax.mail.internet.MailDateFormat();
       java.util.Date expire = df.parse(value);
       java.util.Date now = new java.util.Date();
       if (now.getTime() > expire.getTime()) {
           StringBuffer logMessageBuffer =
             new StringBuffer(256)
             .append("Mail ")
             .append(mail.getName())
             .append(" to host ")
             .append(outgoingMailServer.getHostName())
             .append(" at ")
             .append(outgoingMailServer.getHost())
             .append(" to addresses ")
             .append(Arrays.asList(addr))
             .append(" is expired since ")
             .append(value)
             .append(" and silently discarded");
          log(logMessageBuffer.toString());
          return true;
       }
    }
 }
 catch (Throwable e) {
    e.printStackTrace(); // Ignore Expires: problems 
 }
    */

   /**
    * @return Returns the addExpiresHeader.
    */
   public boolean isAddExpiresHeader() {
      return this.addExpiresHeader;
   }

   /**
    * @param addExpiresHeader The addExpiresHeader to set.
    */
   public void setAddExpiresHeader(boolean addExpiresHeader) {
      this.addExpiresHeader = addExpiresHeader;
   }

   /**
    * @return Returns the breakLongMessageIdLine.
    */
   public boolean isBreakLongMessageIdLine() {
      return this.breakLongMessageIdLine;
   }

   /**
    * @param breakLongMessageIdLine The breakLongMessageIdLine to set.
    */
   public void setBreakLongMessageIdLine(boolean breakLongMessageIdLine) {
      this.breakLongMessageIdLine = breakLongMessageIdLine;
   }

   public int getSmtpIoTimeout() {
	  return smtpIoTimeout;
   }

   //I don't think i can change this on an established connection
   //public void setSmtpIoTimeout(int smtpIoTimeout) {
   //   this.smtpIoTimeout = smtpIoTimeout;
   //}
	
   public int getSmtpConnectionTimeout() {
      return smtpConnectionTimeout;
   }

   /**
    * @return the asyncSendQueueSizeMax
    */
   public int getAsyncSendQueueSizeMax() {
      return this.asyncSendQueueSizeMax;
   }

   // Not yet supported to change dynamically
   //public void setAsyncSendQueueSizeMax(int asyncSendQueueSizeMax) {
   //   this.asyncSendQueueSizeMax = asyncSendQueueSizeMax;
   //}

   /**
    * @return the asyncSendQueueBlockOnOverflow
    */
   public boolean isAsyncSendQueueBlockOnOverflow() {
      return this.asyncSendQueueBlockOnOverflow;
   }

   /**
    * @param asyncSendQueueBlockOnOverflow the asyncSendQueueBlockOnOverflow to set
    */
   public void setAsyncSendQueueBlockOnOverflow(
         boolean asyncSendQueueBlockOnOverflow) {
      this.asyncSendQueueBlockOnOverflow = asyncSendQueueBlockOnOverflow;
   }
	
   //I don't think i can change this on an established connection
   //public void setSmtpConnectionTimeout(int smtpConnectionTimeout) {
   //   this.smtpConnectionTimeout = smtpConnectionTimeout;
   //}
}
