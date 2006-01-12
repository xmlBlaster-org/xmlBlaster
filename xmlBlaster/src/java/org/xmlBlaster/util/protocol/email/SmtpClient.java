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
import java.util.Date;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XbUri;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.I_PluginConfig;
import org.xmlBlaster.util.plugin.PluginInfo;

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
   
   /**
    * Comma separated list of fileName extensions to send attachment as inline
    */
   private String inlineExtension;

   /** My JMX registration */
   private Object mbeanHandle;

   public static final String OBJECTENTRY_KEY = SmtpClient.class.getName();
   
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
      SmtpClient smtpClient = (SmtpClient)glob.getObjectEntry(OBJECTENTRY_KEY);
      if (smtpClient != null)
         return smtpClient;
      
      synchronized(glob.objectMapMonitor) {
         smtpClient = (SmtpClient)glob.getObjectEntry(OBJECTENTRY_KEY);
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
      this.glob.addObjectEntry(OBJECTENTRY_KEY, this);
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

      if (props.getProperty("inlineExtension") == null)
         props.put("inlineExtension", ""+glob.get("inlineExtension", "", null,
               pluginConfig));
      this.inlineExtension = props.getProperty("inlineExtension"); // like ".txt,.xml"
      
      // Pass "this" for SMTP authentication with Authenticator
      this.authentication = new PasswordAuthentication(getUser(), this.xbUri.getPassword());
      this.session = Session.getDefaultInstance(props, this);
      this.isInitialized = true;
      
      if (this.mbeanHandle == null) {
        // For JMX instanceName may not contain ","
        this.contextNode = new ContextNode(ContextNode.SERVICE_MARKER_TAG,
                            "SmtpClient[" + getType() + "]", 
                            glob.getScopeContextNode());
        this.mbeanHandle = glob.registerMBean(this.contextNode, this);
      }

      log.info("SMTP client to '" + this.xbUri.getUrlWithoutPassword() + "' is ready");
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

   /**
    * Send a mail.
    * @param emailData
    *        Container holding the message to send
    */
   public void sendEmail(EmailData emailData) throws XmlBlasterException {
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
            send(message);
            return;
         }
         
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
               mbp.setHeader(Constants.EMAIL_TRANSFER_ENCODING, Constants.ENCODING_BASE64);  // "Content-Transfer-Encoding", "base64");
            }
            else {
               if (holder[i].hasExtensionFromList(this.inlineExtension))
                  mbp.setDisposition(MimeBodyPart.INLINE);
            }
            
            // Encoding violates RFC 2231 but is very common to do so for non-ASCII character sets:
            //mbp.setFileName(MimeUtility.encodeText(holder[i].getFileName()));
            if (holder[i].getContentType().startsWith("text/")) {
               mbp.setText(new String(content, Constants.UTF8_ENCODING), Constants.UTF8_ENCODING);
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

         // set the Date: header
         Date date = new Date();
         message.setSentDate(date);

         // Set the xmlBlaster specific expiry header field "X-xmlBlaster-ExpiryDate"
         // This could be evaluated by MTA plugins
         if (emailData.getExpiryTime() != null) 
            message.setHeader(EmailData.EXPIRES_HEADER, emailData.getExpiryTime().toString());

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
    * java -Dmail.debug=true -Dmail.smtp.url=smtp://xmlBlaster:xmlBlaster@localhost:25 org.xmlBlaster.util.protocol.email.SmtpClient -from xmlBlaster@localhost -to xmlBlaster@localhost
    * </code>
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

         EmailData msg = new EmailData(to, from, subject, content);
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
}
