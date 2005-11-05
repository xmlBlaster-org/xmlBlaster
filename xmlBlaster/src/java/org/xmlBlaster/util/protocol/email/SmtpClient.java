/*------------------------------------------------------------------------------
 Name:      SmtpClient.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 Comment:   javac MessageData.java SmtpClient.java
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
import java.io.InputStream;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.I_PluginConfig;

/**
 * This class is capable to send emails.
 * @see <a
 *      href="http://www-106.ibm.com/developerworks/java/library/j-james1.html">James
 *      MTA</a>
 * @see <a href="http://java.sun.com/products/javamail/javadocs/index.html">Java
 *      Mail API</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class SmtpClient extends Authenticator {
   private static SmtpClient theMailClient;

   private static Logger log = Logger.getLogger(SmtpClient.class.getName());

   private Session session;

   private PasswordAuthentication authentication;

   public static final String UTF8 = "UTF-8";
   
   private String user;

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
    * Usually a singleton, but you can create your own instances
    * as the constructor is public
    */
   public SmtpClient() {
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
    * props.put(&quot;mail.store.protocol&quot;, &quot;pop3&quot;);
    * props.put(&quot;mail.transport.protocol&quot;, &quot;smtp&quot;);
    * props.put(&quot;mail.host&quot;, &quot;localhost&quot;);
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
      if (props == null)
         props = new Properties();
      
      if (props.getProperty("mail.debug") == null)
         props.put("mail.debug", glob.get("mail.debug", System
               .getProperty("mail.debug", "false"), null, pluginConfig));


      if (props.getProperty("mail.user") == null)
         props.put("mail.user", glob.get("mail.user", System
               .getProperty("user.name"), null, pluginConfig));
      this.user = props.getProperty("mail.user").trim();

      if (props.getProperty("mail.password") == null)
         props.put("mail.password", glob.get("mail.password", user, null,
               pluginConfig));
      String password = props.getProperty("mail.password").trim();

      if (props.getProperty("mail.transport.protocol") == null)
         props.put("mail.transport.protocol", glob.get(
               "mail.transport.protocol", "smtp", null, pluginConfig));
      if (props.getProperty("mail.smtp.host") == null)
         props.put("mail.smtp.host", glob.get("mail.smtp.host", "127.0.0.1",
               null, pluginConfig));
      if (props.getProperty("mail.smtp.port") == null)
         props.put("mail.smtp.port", glob.get("mail.smtp.port", "25", null,
               pluginConfig));

      if (props.getProperty("mail.store.protocol") == null)
         props.put("mail.store.protocol", glob.get("mail.store.protocol",
               "pop3", null, pluginConfig));

      // Pass "this" for POP3 authentication with Authenticator
      // For only sending mails we can pass null
      this.session = Session.getDefaultInstance(props, this);
      this.authentication = new PasswordAuthentication(user, password);

      // String from = user + '@' + host;
      if (log.isLoggable(Level.FINE))
         log.fine("Setting user='" + user + "' password='" + password + "'");
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
         String encoding) throws AddressException, MessagingException
          {
      sendEmail(new InternetAddress(from), new InternetAddress(to), subject,
            body, encoding);
   }

   /**
    * @param body
    *           Is assumed to be of mime type "text/plain"
    * @param encoding
    *           (charset) for example "UTF-8", will set the mail header:
    * <pre>
    *         Content-Type: text/plain; charset=UTF-8
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
    * @param attachmentName2 If not null this second attachment is added as "text/plain"
    * @param encoding For example "UTF-8"
    */
   public void sendEmail(InternetAddress from, InternetAddress to,
         String subject, String attachmentName, InputStream attachment,
         String attachmentName2, String attachment2,
         String encoding) throws XmlBlasterException {
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
         if (log.isLoggable(Level.FINE)) log.fine("Successful send email from=" + from.toString() + " to=" + to.toString());
      } catch (Exception e) {
         throw new XmlBlasterException(Global.instance(),
               ErrorCode.COMMUNICATION_NOCONNECTION, "SmtpClient",
               "Email sending failed, no mail sent to " + to.toString(), e);
      }
   }

   /**
    * Send a mail.
    * 
    * @param aMessageData
    *           Container holding the message to send
    */
   public void sendEmail(MessageData aMessageData) throws AddressException,
         MessagingException {
      MimeMessage message = new MimeMessage(getSession());
      try {
         message.setFrom(new InternetAddress(aMessageData.getFrom()));

         String[] recps = aMessageData.getAllRecipients();
         InternetAddress tos[] = new InternetAddress[recps.length];
         for (int i = 0; i < recps.length; i++)
            tos[i] = new InternetAddress(recps[i]);
         message.setRecipients(Message.RecipientType.TO, tos);

         message.setSubject(aMessageData.getSubject(), aMessageData
               .getEncoding());

         message.setText(aMessageData.getContent(), aMessageData.getEncoding());
         
      } catch (MessagingException e) {
         throw e;
      }
      send(message);
   }

   public synchronized void shutdown() {
      if (this.session != null) {
         log.info("Shutting down SMTP mail client");
         this.session = null;
      }
   }

   /**
    * java -Dmail.user=marcel -Dmail.password=marcel org.xmlBlaster.util.protocol.email.SmtpClient -from joe@localhost -to jack@localhost
    * java -Dmail.smtp.port=6025 -Dmail.debug=true ...
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

         MessageData msg = new MessageData(to, from,
               "Hi from java", "Some body text");
         mail.sendEmail(msg);
         System.out.println("Sent a message from '" + from + "' to '" + to + "'");
      } catch (Exception e) {
         System.out.println("Mail failed: " + e.toString());
      } finally {
         if (mail != null)
            mail.shutdown();
      }
   }
}
