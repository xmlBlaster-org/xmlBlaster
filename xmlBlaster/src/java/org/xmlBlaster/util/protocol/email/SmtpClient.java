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

import java.util.Date;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.I_PluginConfig;
import org.xmlBlaster.util.plugin.PluginInfo;

/**
 * This class is capable to send emails.
 * 
 * @see <a
 *      href="http://www-106.ibm.com/developerworks/java/library/j-james1.html">James
 *      MTA</a>
 * @see <a href="http://java.sun.com/products/javamail/javadocs/index.html">Java
 *      Mail API</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.email.html">The protocol.email requirement</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class SmtpClient extends Authenticator implements I_Plugin, SmtpClientMBean {
   private static SmtpClient theMailClient;

   private static Logger log = Logger.getLogger(SmtpClient.class.getName());

   private Global glob;
   
   private ContextNode contextNode;
   
   private PluginInfo pluginInfo;

   private Session session;

   private PasswordAuthentication authentication;

   private String user;
   private String password;
   private String host;
   private int port;
   
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

   /** My JMX registration */
   private Object mbeanHandle;

   /**
    * @return Returns the user.
    */
   public String getUser() {
      return this.user;
   }

   public static SmtpClient instance() {
      if (theMailClient == null) {
         synchronized (SmtpClient.class) {
            if (theMailClient == null) {
               theMailClient = new SmtpClient();
            }
         }
      }
      return theMailClient;
   }

   /**
    * Usually a singleton, but you can create your own instances as the
    * constructor is public
    */
   public SmtpClient() {
      if (theMailClient == null) theMailClient = this;
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
      this.glob.addObjectEntry(SmtpClient.class.getName(), this);
   }

   /**
    * For receiving only, used by Authenticator.
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
    * props.put(&quot;mail.user&quot;, &quot;joe&quot;);
    * props.put(&quot;mail.password&quot;, &quot;joe&quot;);
    * props.put(&quot;mail.debug&quot;, &quot;true&quot;);
    * props.put(&quot;mail.transport.protocol&quot;, &quot;smtp&quot;);
    * props.put(&quot;mail.smtp.host&quot;, &quot;localhost&quot;);
    * props.put(&quot;mail.smtp.port&quot;, &quot;25&quot;);
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

      if (props.getProperty("mail.user") == null)
         props.put("mail.user", glob.get("mail.user", System
               .getProperty("user.name"), null, pluginConfig));
      this.user = props.getProperty("mail.user").trim();

      if (props.getProperty("mail.password") == null)
         props.put("mail.password", glob.get("mail.password", user, null,
               pluginConfig));
      this.password = props.getProperty("mail.password").trim();

      if (props.getProperty("mail.transport.protocol") == null)
         props.put("mail.transport.protocol", glob.get(
               "mail.transport.protocol", "smtp", null, pluginConfig));
      String shema = props.getProperty("mail.transport.protocol");

      if (props.getProperty("mail.smtp.host") == null)
         props.put("mail.smtp.host", glob.get("mail.smtp.host", "127.0.0.1",
               null, pluginConfig));
      this.host = props.getProperty("mail.smtp.host");

      if (props.getProperty("mail.smtp.port") == null)
         props.put("mail.smtp.port", glob.get("mail.smtp.port", "25", null,
               pluginConfig));
      String p = props.getProperty("mail.smtp.port");
      this.port = new Integer(p).intValue();
      
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
      
      // Pass "this" for SMTP authentication with Authenticator
      // For only sending mails we can pass null
      this.session = Session.getDefaultInstance(props, this);
      this.authentication = new PasswordAuthentication(getUser(), password);
      this.isInitialized = true;
      
      if (this.mbeanHandle == null) {
	      // For JMX instanceName may not contain ","
	      this.contextNode = new ContextNode(glob, ContextNode.SERVICE_MARKER_TAG, 
	                          "SmtpClient", glob.getContextNode());
	      this.mbeanHandle = glob.registerMBean(this.contextNode, this);
      }

      if (log.isLoggable(Level.FINER))
         log.finer("Setting user='" + user + "' password='" + password + "'");
      log.info("SMTP client to '" + shema + "://" + getUser() + "@" + host + ":"
            + port + "' is ready");
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
    * Send a mail.
    * @param emailData
    *        Container holding the message to send
    */
   public void sendEmail(EmailData emailData) throws XmlBlasterException {
      try {
         MimeMessage message = new MimeMessage(getSession());
         message.setFrom(emailData.getFromAddress());
         message.setRecipients(Message.RecipientType.TO, emailData.getToAddresses());
         message.setSubject(emailData.getSubject(), Constants.UTF8_ENCODING);

         // create the Multipart and add its parts to it
         Multipart multi = new MimeMultipart();
         
         if (emailData.getContent() != null && emailData.getContent().length() > 0) {
            MimeBodyPart mbp = new MimeBodyPart();
            mbp.setFileName("content.txt");
            mbp.setText(emailData.getContent(), Constants.UTF8_ENCODING);
            multi.addBodyPart(mbp);
         }

         AttachmentHolder[] holder = emailData.getAttachments();
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
            
            // Encoding violates RFC 2231 but is very common to do so for non-ASCII character sets:
            //mbp.setFileName(MimeUtility.encodeText(holder[i].getFileName()));
            if (holder[i].getContentType().startsWith("text/")) {
               mbp.setText(new String(content, Constants.UTF8_ENCODING), Constants.UTF8_ENCODING);
               mbp.setHeader("Content-Type", "application/octet-stream");
               mbp.setHeader(Constants.EMAIL_TRANSFER_ENCODING, Constants.ENCODING_BASE64);
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
         message.setSentDate(new Date());

         //log.severe("DEBUG ONLY: Trying to send email" + emailData.toXml(true));
         send(message);
         //log.severe("DEBUG ONLY: Successful send email" + emailData.toXml(true));
         if (log.isLoggable(Level.FINE))
            log.fine("Successful send email from=" + emailData.getFrom() + " to="
                  + emailData.getRecipientsList());
         if (log.isLoggable(Level.FINER))
            log.finer("Successful send email" + emailData.toXml(true));
      } catch (Exception e) {
         log.warning("Can't send mail: " + e.toString() + ": " + emailData.toXml(true));
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

   /**
    * java -Dmail.user=marcel -Dmail.password=marcel
    * org.xmlBlaster.util.protocol.email.SmtpClient -from joe@localhost -to
    * jack@localhost java -Dmail.smtp.port=6025 -Dmail.debug=true ...
    * <p>
    * 
    * @see #setSessionProperties(Properties) for other properties
    */
   public static void main(String[] args) {
      Global glob = new Global(args);

      SmtpClient mail = null;
      try {
         mail = SmtpClient.instance();

         final boolean debug = false;

         // Here we create the mail Session manually without a JNDI lookup
         Properties props = System.getProperties();
         props.put("mail.debug", "" + debug);
         mail.setSessionProperties(props, glob, null);
         String from = glob.getProperty().get("from", "blue@localhost");
         String to = glob.getProperty().get("to", "blue@localhost");

         EmailData msg = new EmailData(to, from, "Hi from java",
               "Some body text");
         mail.sendEmail(msg);
         System.out.println("Sent a message from '" + from + "' to '" + to
               + "'");
      } catch (Exception e) {
         System.out.println("Mail failed: " + e.toString());
      } finally {
         if (mail != null)
            mail.shutdown();
      }
   }

   public String getHost() {
   	return host;
   }
   
   public void setHost(String host) {
   	this.host = host;
   }
   
   public String getPassword() {
   	return password;
   }
   
   public void setPassword(String password) {
   	this.password = password;
   }
   
   public int getPort() {
   	return port;
   }
   
   public void setPort(int port) {
   	this.port = port;
   }
   
   public void setUser(String user) {
   	this.user = user;
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
}
