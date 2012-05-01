package org.xmlBlaster.engine.event;

import java.util.Map;
import java.util.logging.Logger;

import org.xmlBlaster.engine.EventPlugin;
import org.xmlBlaster.util.StringPairTokenizer;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.protocol.email.EmailData;
import org.xmlBlaster.util.protocol.email.SmtpClient;

/**
 * Helper class to send emails
 */
public class SmtpDestinationHelper {
   private static Logger log = Logger.getLogger(SmtpDestinationHelper.class.getName());

   private final EventPlugin eventPlugin;

   private SmtpClient smtpClient;

   private String to, from, subjectTemplate, cc, bcc, contentTemplate, contentSeparator;

   private long collectIntervall = Constants.DAY_IN_MILLIS / 2;
   
   private boolean sendAsync = true;
   
   //private boolean blockOnOverflow = false;

   public SmtpDestinationHelper(EventPlugin eventPlugin, SmtpClient smtpClient, String destination)
         throws XmlBlasterException {
      this.eventPlugin = eventPlugin;
      this.smtpClient = smtpClient;
      @SuppressWarnings("unchecked")
      Map<String, String> map = StringPairTokenizer.parseLineToProperties(destination);

      if (map.containsKey("mail.smtp.to"))
         this.to = (String) map.get("mail.smtp.to");
      this.eventPlugin.verifyInternetAddress(this.to);

      if (map.containsKey("mail.smtp.from"))
         this.from = (String) map.get("mail.smtp.from");
      if (this.from == null)
         this.from = "xmlBlaster@localhost";
      this.eventPlugin.verifyInternetAddress(this.from);

      // Each line of characters MUST be no more than 998 characters,
      // and SHOULD be no more than 78 characters, excluding the CRLF
      if (map.containsKey("mail.subject"))
         this.subjectTemplate = (String) map.get("mail.subject");
      else
         this.subjectTemplate = "[XmlBlaster event: $_{eventType}] $_{nodeId}";
         //this.subjectTemplate = "[XmlBlaster generated email] $_{nodeId} $_{summary}";
      if (map.containsKey("mail.content"))
         this.contentTemplate = (String) map.get("mail.content");
      else
         this.contentTemplate = "eventType:   $_{eventType}\ninstanceId:  $_{instanceId}\n\nsummary:     $_{summary}\ndescription: $_{description}\n\neventDate:   $_{datetime}\nversionInfo: $_{versionInfo}\n\n--\nhttp://www.xmlblaster.org/xmlBlaster/doc/requirements/admin.events.html";

      if (map.containsKey("mail.contentSeparator"))
         this.contentSeparator = (String) map.get("mail.contentSeparator");
      else
         this.contentSeparator = "\n\n========== NEXT ============\n\n";

      if (map.containsKey("mail.smtp.cc"))
         this.cc = (String) map.get("mail.smtp.cc");
      if (this.cc != null && this.cc.trim().length() > 0)
         this.eventPlugin.verifyInternetAddress(this.cc);

      if (map.containsKey("mail.smtp.bcc"))
         this.bcc = (String) map.get("mail.smtp.bcc");
      if (this.bcc != null && this.bcc.trim().length() > 0)
         this.eventPlugin.verifyInternetAddress(this.bcc);

      if (map.containsKey("mail.sendAsync")) {
         String tmp = (String) map.get("mail.sendAsync");
         this.sendAsync = Boolean.valueOf(tmp.trim()).booleanValue();
      }

      //if (map.containsKey("mail.blockOnOverflow")) {
      //   String tmp = (String) map.get("mail.blockOnOverflow");
      //   this.blockOnOverflow = Boolean.valueOf(tmp.trim()).booleanValue();
      //}

      if (map.containsKey("mail.collectMillis")) {
         String tmp = (String) map.get("mail.collectMillis");
         this.collectIntervall = Long.valueOf(tmp.trim()).longValue();
      }
      if (this.collectIntervall < 0) this.collectIntervall = 0;
   }

   public EmailData createEmailData() {
      EmailData emailData = new EmailData(this.to, this.from,
            "", "");
      emailData.setCc(this.cc);
      emailData.setBcc(this.bcc);
      emailData.setSendAsync(this.sendAsync);
      // emailData.setExpiryTime(expiryTimestamp);
      // emailData.addAttachment(new AttachmentHolder(payloadFileName,
      // payloadMimetype, payload));
      return emailData;
   }

   public void sendEmail(EmailData emailData) throws XmlBlasterException {
      log.info("Sending event email: " + emailData.toString());
      this.smtpClient.sendEmail(emailData);
   }

   public SmtpClient getSmtpClient() {
      return smtpClient;
   }

   public String getTo() {
      return to;
   }

   public String getFrom() {
      return from;
   }

   public String getSubjectTemplate() {
      return subjectTemplate;
   }

   public String getContentTemplate() {
      return contentTemplate;
   }

   public String getContentSeparator() {
      return contentSeparator;
   }

   public long getCollectIntervall() {
      return collectIntervall;
   }

   public void setCollectIntervall(long collectIntervall) {
      this.collectIntervall = collectIntervall;
   }
} // end of helper class SmtpDestination