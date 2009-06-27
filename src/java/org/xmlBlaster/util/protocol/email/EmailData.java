/*------------------------------------------------------------------------------
 Name:      EmailData.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 Comment:   javac EmailData.java SmtpClient.java
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.util.protocol.email;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.xmlBlaster.util.IsoDateParser;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.StringPairTokenizer;
import org.xmlBlaster.util.XmlNotPortable;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.xbformat.MsgInfoParserFactory;

/**
 * Value object holding the most commonly used email fields.
 * <p>
 * Add/access/delete attachments is not simultaneous possible (not thread save)
 * </p>
 * Example:
 * <pre>
From:    demo@localhost
To:      xmlBlaster@localhost
Subject: Hello World
attachement {
   fileName: messageId.mid
   content:  <messageId><sessionId>abcd</sessionId><requestId>5</requestId></messageId>
}
attachement {
   fileName: xmlBlaster.xbf
   content:  [the binary xmlBlaster format similar to that used with SOCKET]
}
 * </pre>
 * <p>
 * Note on max. header length from RFC 2822:
 * <p>
 * "There are two limits that this standard places on the number of
   characters in a line. Each line of characters MUST be no more than
   998 characters, and SHOULD be no more than 78 characters, excluding
   the CRLF."
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @see http://www.faqs.org/rfcs/rfc2822.html
 */
public class EmailData {
   private static Logger log = Logger.getLogger(EmailData.class.getName());

   protected String encoding = Constants.UTF8_ENCODING; // "text/plain; charset=UTF-8"

   protected InternetAddress[] recipients;

   protected InternetAddress[] cc;

   protected InternetAddress[] bcc;

   protected InternetAddress from;
   
   protected String subject;
   
   // TODO: Not yet supported
   protected boolean sendContentAsText;

   protected String content;

   /** Containts AttachmentHolder instances */
   protected ArrayList attachments;

   /** Contains sessionId * */
   protected String sessionId;

   /** Contains requestId * */
   protected String requestId;
   
   protected Timestamp expiryTime;
   
   /** The origination date from the email header, this field exists always for incoming emails */
   protected Date sentDate;
   
   /** Remember if we got an explicit requestId or if we extracted it from the sentDate */
   protected boolean requestIdFromSentDate;
   
   //Currently not supported
   protected InternetAddress[] replyTo;

   /** The root tag &lt;messageId> */
   public static final String MESSAGEID_TAG = "messageId";

   public static final String METHODNAME_TAG = "methodName";
   
   public static final String REQUESTID_TAG = "requestId";

   public static final String SESSIONID_TAG = "sessionId";

   public static final String EXPIRES_TAG = "expires";

   /*
    * The value has the format <code>yyyy-mm-dd hh:mm:ss.fffffffff</code>,
    * the .f* are optional
    */
   //public static final String EXPIRES_HEADER = "X-xmlBlaster-ExpiryDate";
   /**
    * Expiry Date Indication
    * Supported as new RFC 822 header (Expires:).  In general, no
    * automatic action can be expected.
    * @see http://www.faqs.org/rfcs/rfc2156.html
    */
   public static final String EXPIRES_HEADER_RFC2156 = "Expires";

   /** Holding the relevant email meta info like a request identifier */
   public static final String MESSAGEID_EXTENSION = ".mid";
   
   public boolean sendAsync;

   /**
    * @return the sendAsync
    */
   public boolean isSendAsync() {
      return this.sendAsync;
   }

   /**
    * Set to true if you want to send the mail over a producer/consumer pattern
    * from another thread.
    * This is helpful to protect against blocking SMTP servers.
    * @param sendAsync the sendAsync to set, defaults to false
    */
   public void setSendAsync(boolean sendAsync) {
      this.sendAsync = sendAsync;
   }

   /**
    * Create a simple message.
    * 
    * @param recipient
    *           For example "jack@gmx.net" or "jack@gmx.net,jeff@gmx.net"
    * @param from
    *           For example "sue@gmx.net"
    * @param subject
    *           For example "Hi"
    * @param content
    *           For example "Best regards, Sue"
    */
   public EmailData(String recipient, String from, String subject,
         String content) {
      this.recipients = new InternetAddress[0];
      if (recipient != null) {
   	     String[] arr = StringPairTokenizer.toArray(recipient, ",");
         this.recipients = new InternetAddress[arr.length];
         for (int i=0; i<arr.length; i++)
        	 this.recipients[i] = toInternetAddress(arr[i].trim());
      }
      this.from = toInternetAddress(from);
      this.subject = subject;
      this.content = content;
   }

   /**
    * Create a simple message for any number of recipients.
    * 
    * @see #EmailData(String, String, String, String)
    */
   public EmailData(String[] recipients, String from, String subject,
         String content) {
      if (recipients == null) {
         this.recipients = new InternetAddress[0];
      }
      else {
         this.recipients = new InternetAddress[recipients.length];
         for (int i=0; i<recipients.length; i++)
            this.recipients[i] = toInternetAddress(recipients[i]);
      }
      this.from = toInternetAddress(from);
      this.subject = subject;
      this.content = content;
   }

   public EmailData(InternetAddress recipient, InternetAddress from, String subject) {
      if (recipient == null) {
         this.recipients = new InternetAddress[0];
      }
      else {
         this.recipients = new InternetAddress[1];
         this.recipients[0] = recipient;
      }
      this.from = from;
      this.subject = subject;
      this.content = null;
   }
   
   private InternetAddress toInternetAddress(String address) throws IllegalArgumentException {
      try {
         return new InternetAddress(address);
      } catch (AddressException e) {
         throw new IllegalArgumentException("Illegal email address '" + address + "': " + e.toString());
      }
   }

   public void addAttachment(AttachmentHolder attachmentHolder) {
      if (this.attachments == null) this.attachments = new ArrayList();
      this.attachments.add(attachmentHolder);
   }

   public void setAttachments(ArrayList attachmentHolders) {
      this.attachments = attachmentHolders;
   }

   /**
    * Access all attachements. 
    * @return Never null
    */
   public AttachmentHolder[] getAttachments() {
      if (this.attachments == null) return new AttachmentHolder[0];
      return (AttachmentHolder[]) this.attachments
            .toArray(new AttachmentHolder[this.attachments.size()]);
   }
   
   /**
    * Lookup attachment. 
    * @param extension For example XbfParser.XBFORMAT_EXTENSION=".xbf"
    * @return null if no such attachment was found
    */
   public byte[] getContentByExtension(String extension) {
      AttachmentHolder[] atts = getAttachments();
      for (int j = 0; j < atts.length; j++) {
         if (atts[j].hasExtension(extension)) {
            return atts[j].getContent();
         }
      }
      return null;
   }
   
   /**
    * Lookup attachment. 
    * If extension is not found try to find extensionBackup.
    * @param extension For example XbfParser.XBFORMAT_EXTENSION=".xbf"
    * @param extensionZ For example XbfParser.XBFORMAT_ZLIB_EXTENSION=".xbfz"
    * @param extensionBackup For example ".xml"
    * @return null if no such attachment was found
    */
//   public AttachmentHolder getEncodedMsgUnitByExtension(String extension, String extensionZ, String extensionBackup) {
   public AttachmentHolder getMsgUnitAttachment() {
      MsgInfoParserFactory fac = MsgInfoParserFactory.instance();
      AttachmentHolder[] atts = getAttachments();
      for (int j = 0; j < atts.length; j++) {
         if (fac.parserExists(atts[j].getFileName(), atts[j].getContentType())) {
            return atts[j];
         }
      }
      return null;
   }
   
   public boolean isMsgUnitAttachment(AttachmentHolder holder) {
      AttachmentHolder msgUnitHolder = getMsgUnitAttachment();
      if (msgUnitHolder == null) return false;
      return msgUnitHolder.equals(holder);
   }
   
   /**
    * Comma separated value list of all attachment file names (unquoted) for logging. 
    * @return For example "a.xbf, b.xml, m.mid"
    */
   public String getFileNameList() {
      StringBuffer buf = new StringBuffer();
      AttachmentHolder[] atts = getAttachments();
      for (int j = 0; j < atts.length; j++) {
         if (j > 0) buf.append(",");
         buf.append(atts[j].getFileName());
      }
      return buf.toString();
   }
   
   /**
    * Comma separated value list of all recipient email addresses for logging. 
    * @return For example "joe@locahost,demo@localhost"
    */
   public String getRecipientsList() {
      if (this.recipients == null) return "";
      StringBuffer buf = new StringBuffer();
      for (int j = 0; j < this.recipients.length; j++) {
         if (j > 0) buf.append(",");
         buf.append(this.recipients[j]);
      }
      return buf.toString();
   }

   /**
    * @see #getEncoding()
    */
   public void setEncoding(String aEncoding) {
      if (aEncoding != null) {
         this.encoding = aEncoding;
      }
   }

   /**
    * Encoding (charset) for example "UTF-8". To support Japanese, English and
    * German, use "UTF-8", this sets for example the mail header:
    * 
    * <pre>
    *  Content-Type: text/plain; charset=UTF-8
    * </pre>
    * 
    * "ISO-8859-1" is good enough for German and English
    * 
    * @return Never null, defaults to "ISO-8859-1"
    */
   public String getEncoding() {
      return this.encoding;
   }

   /**
    * @return All destinations of the message, never null
    */
   public String[] getAllRecipients() {
      String[] ret = new String[this.recipients.length];
      for (int i=0; i<this.recipients.length; i++)
         ret[i] = this.recipients[i].toString();
      return ret;
   }

   /**
    * @return The from of the message, never null
    */
   public String getFrom() {
      return (this.from == null) ? "" : this.from.getAddress();
   }

   /**
    * @return Never null
    */
   public InternetAddress getFromAddress() {
      return this.from;
   }

   public InternetAddress[] getToAddresses() {
      return this.recipients;
   }

   public String getToAddressesStr() {
	   if (this.recipients == null) {
		   return "";
	   }
	   StringBuffer buf = new StringBuffer();
	   for (int i=0; i<this.recipients.length; i++) {
		   if (i>0) buf.append(";");
		   buf.append(this.recipients[i].getAddress());
	   }
	   return buf.toString();
   }

   /**
    * @return The subject of the message, never null
    */
   public String getSubject() {
      return (this.subject == null) ? "" : this.subject;
   }

   /**
    * @param subject The subject to set.
    */
   public void setSubject(String subject) {
      this.subject = subject;
   }

   /**
    * @return The content of the message, never null
    */
   public String getContent() {
      if (this.content == null || this.content.length() == 0) {
         AttachmentHolder h = getAttachment(MailUtil.BODY_NAME);
         if (h != null)
            this.content = new String(h.getContent());
      }
      return (this.content == null) ? "" : this.content;
   }
   
   public AttachmentHolder getAttachment(String fileName) {
      if (fileName == null) return null;
      AttachmentHolder[] arr = getAttachments();
      for (int i=0; i<arr.length; i++)
         if (fileName.equals(arr[i].getFileName()))
            return arr[i];
      return null;
   }
   

   /**
    * Dumps message to xml.
    * @param readable If true '\0' are replaced by '*' 
    */
   public String toXml(boolean readable) {
      String offset = "\n";
      StringBuffer sb = new StringBuffer(1024);
      sb.append(offset).append("<message>");
      
      if (getExpiryTime() != null)
         sb.append(offset).append("  ").append(createMessageId(null, getExpiryTime()));
      
      sb.append(offset).append("  <from>").append(XmlNotPortable.escape(getFrom())).append(
            "</from>");
      for (int i = 0; i < this.recipients.length; i++) {
         sb.append(offset).append("  <to>").append(XmlNotPortable.escape(this.recipients[i].toString()))
               .append("</to>");
      }
      if (this.recipients.length == 0) {
         sb.append(offset).append("  <to></to>");
      }
      for (int i = 0; this.cc!=null && i < this.cc.length; i++) {
         sb.append(offset).append("  <cc>").append(XmlNotPortable.escape(this.cc[i].toString()))
               .append("</cc>");
      }
      for (int i = 0; this.bcc!=null && i < this.bcc.length; i++) {
         sb.append(offset).append("  <bcc>").append(XmlNotPortable.escape(this.bcc[i].toString()))
               .append("</bcc>");
      }
      sb.append(offset).append("  <subject>").append(XmlNotPortable.escape(getSubject()))
            .append("</subject>");
      if (this.content != null && this.content.length() > 0) {
         String con = this.content;
         /*
         if (this.content instanceof javax.mail.internet.MimeMultipart) {
            MimeMultipart part = (MimeMultipart)this.content;
            if (part.getCount() > 0)
               con = part.getBodyPart(0).getContent();
         }
         */
         sb.append(offset).append("  <content>").append(XmlNotPortable.escape(con)).append("</content>");
      }
      AttachmentHolder[] att = getAttachments();
      for (int i = 0; i < att.length; i++)
         sb.append(att[i].toXml(readable));
      sb.append(offset).append("</message>");
      return sb.toString();
   }

   /**
    * Internal helper for parsing.
    * 
    * @param startIndex >=
    *           0
    * @param value
    *           The wanted text between the tags
    * @return The current start position and -1 if not found
    */
   private static int parseTag(int startIndex, String startToken,
         String endToken, String xml, StringBuffer value) {
      value.setLength(0);

      int start = xml.indexOf(startToken, startIndex);
      if (start == -1) {
         return start;
      }
      int end = xml.indexOf(endToken, start);
      if (end == -1) {
         throw new IllegalArgumentException("EmailData token '" + endToken
               + "' is missing");
      }
      value.append(xml.substring(start + startToken.length(), end));
      return end + endToken.length();
   }

   /**
    * Internal helper for parsing.
    * 
    * @param startIndex >=
    *           0
    * @param token
    *           The tag name without "<", ">"
    * @param value
    *           The wanted text between the tags
    * @return The current start position and -1 if not found
    */
   private static int parseTag(int startIndex, String token, String xml,
         StringBuffer value) {
      int start = parseTag(startIndex, "<" + token + "><![CDATA[", "]]></"
            + token + ">", xml, value);
      if (start == -1) { // Try again without CDATA section
         start = parseTag(startIndex, "<" + token + ">", "</" + token + ">",
               xml, value);
      }
      return start;
   }

   /**
    * Hand made parser to parse xml, much faster than SAX. Will fail if a tag is
    * omitted and this tag occurs for example in the CDATA section of the
    * content. Therefor we always dump the complete xml in toXml().
    * Not for production use, Attachments are not yet supported!
    * <p />
    * Multiple &lt;to> tags are allowed or a single with comma separated addresses.
    */
   public static EmailData parseXml(String xml) {
      int start = 0;
      StringBuffer sb = new StringBuffer(256);

      start = parseTag(start, "from", xml, sb);
      String from = sb.toString();

      ArrayList toList = new ArrayList();
      int startTmp;
      while (true) {
         startTmp = parseTag(start, "to", xml, sb);
         if (startTmp == -1) {
            break;
         }
         start = startTmp;
         if (sb.length() > 0) {
        	if (sb.indexOf(",") != -1) {
        		String[] arr = StringPairTokenizer.toArray(sb.toString(), ",");
        		for (int i=0; i<arr.length; i++)
        			toList.add(arr[i].trim());
        	}
        	else
        		toList.add(sb.toString());
         }
      }
      String[] recipients = (String[]) toList
            .toArray(new String[toList.size()]);

      start = parseTag(start, "subject", xml, sb);
      String subject = sb.toString();

      start = parseTag(start, "content", xml, sb);
      String content = sb.toString();

      EmailData msg = new EmailData(recipients, from, subject, content);
      return msg;
   }

   /**
    * The requestId from the &lt;messageId>&lt;requestId>123456&lt;/requestId>&lt;/messageId> markup. 
    * 
    * If not found the sent-date from the email header is used,
    * note that this is not unique if more than one emails per seconds are send.
    * It is more safe to explicitely use our requestId markup.
    * @return Returns the requestId, never null
    */
   public String getRequestId() {
      if (this.requestId == null) {
         this.requestId = extractMessageId(REQUESTID_TAG);
         if (this.requestId == null) {
            if (this.sentDate != null) {
               this.requestIdFromSentDate = true;
               this.requestId = ""+this.sentDate.getTime();
            }
         }
      }
      return (this.requestId == null) ? "" : this.requestId;
   }

   /**
    * @param requestId
    *           The requestId to set.
    */
   public void setRequestId(String requestId) {
      this.requestId = requestId;
   }

   /**
    * The emails session id.
    * Can (but must not) be identical to the SessionInfo-secretSessionId
    * @return Returns the sessionId, never null
    */
   public String getSessionId() {
      if (this.sessionId == null) {
         this.sessionId = extractMessageId(SESSIONID_TAG);
      }
      return (this.sessionId == null) ? "" : this.sessionId;
   }

   /**
    * @param sessionId
    *           The sessionId to set.
    */
   public void setSessionId(String sessionId) {
      this.sessionId = sessionId;
   }

   /**
    * Find the messageId of this message. 
    * 
    * This is usually in the subject or in an attachment
    * with extension ".mid" 
    * 
    * @return null if none is found
    * or for example <messageId><sessionId>somesecret</sessionId><requestId>5</requestId><methodName>update</methodName></messageId>
    */
   public String getMessageId() {
      AttachmentHolder attachmentHolder = getMessageIdAttachment();
      if (attachmentHolder == null) return null;
      return new String(attachmentHolder.getContent());
   }

   /**
    * Find the messageId of this message. 
    * 
    * This is usually in the subject or in an attachment
    * with extension ".mid" 
    * 
    * @return null if none is found
    * or for example <messageId><sessionId>somesecret</sessionId><requestId>5</requestId><methodName>update</methodName></messageId>
    */
   public AttachmentHolder getMessageIdAttachment() {
      String subject = getSubject();
      final String startToken = "<" + MESSAGEID_TAG + ">";
      if (subject.indexOf(startToken) == -1) {
         // Look into attachment ...
         // The <messageId> is not in the subject,
         // search in an attachment with extension ".mid"
         // or in an attachment without extension
         AttachmentHolder[] atts = getAttachments();
         for (int i = 0; i < atts.length; i++) {
            if (atts[i].hasExtension(MESSAGEID_EXTENSION)) {
               return atts[i]; // strongest
            }
         }
         for (int i = 0; i < atts.length; i++) {
            if (atts[i].getFileName().indexOf(".") == -1) {
               // Trying extensionless attachments
               String str = new String(atts[i].getContent());
               if (str.indexOf(startToken) == -1) {
                  log.warning("Can't guess messageId, trying this failed: '" + str + "'");
               }
               else {
                  return atts[i];
               }
            }
         }
      }
      else {
         // strip other text in subject
         final String endToken = "</" + MESSAGEID_TAG + ">";
         int startIndex = subject.indexOf(startToken);
         int endIndex = subject.indexOf(endToken);
         if (endIndex > startIndex)
            subject = subject.substring(startIndex, endIndex+endToken.length());
         // "messageId.mid"
         return new AttachmentHolder(MESSAGEID_TAG+MESSAGEID_EXTENSION,subject);
      }
      return null;
   }
   
   public boolean isMessageIdAttachment(AttachmentHolder holder) {
      AttachmentHolder messageIdHolder = getMessageIdAttachment();
      if (messageIdHolder == null) return false;
      return messageIdHolder.equals(holder);
   }
   
   /**
    * Find the given tag in the messageId of this message. 
    * 
    * Subject: <messageId><sessionId>abcd</sessionId><requestId>5</requestId><methodName>update</methodName></messageId>
    * 
    * @param tag
    *           "requestId" or "sessionId" or "methodName"
    * @return null if none is found
    */
   public String extractMessageId(String tag) {
      String str = getMessageId();
      final String startToken = "<" + tag + ">";

      if (str != null) {
         final String endToken = "</" + tag + ">";
         int start = str.indexOf(startToken);
         int end = str.indexOf(endToken);
         if (start != -1 && end != -1) {
            return str.substring(start + startToken.length(), end);
         }
      }
      if (log.isLoggable(Level.FINE)) log.fine("No <" + tag + "> found for "
            + toXml(true));
      return null;
   }

   /**
    * Use together with extractMessageId(EmailData messageData, String tag).
    * 
    * @param methodName Can be null
    * @param expiryTimestamp Can be null
    * @return A well formatted XML
    * <messageId><sessionId>abcd</sessionId><requestId>5</requestId><methodName>update</methodName></messageId>
    */
   public String createMessageId(MethodName methodName, Timestamp expiryTimestamp) {
      return createMessageId(this.sessionId, this.requestId, methodName, expiryTimestamp);
   }
   
   /**
    * If any of the params is null no markup for this param is added. 
    * If any of the param is empty "", an empty markup is added 
    * @param methodName Can be null
    * @param expiryTimestamp Can be null
    * @return A well formatted XML, the timestamp follows the ASCII ISO notation
    * <messageId><sessionId>abcd</sessionId><requestId>5</requestId><methodName>update</methodName><expires>2005-11-30T12:42:24.200Z</expires></messageId>
    */
   public static String createMessageId(String sessionId, String requestId, MethodName methodName, Timestamp expiryTimestamp) {
      if (sessionId == null && requestId == null && methodName == null && expiryTimestamp == null)
         return "";
      StringBuffer sb = new StringBuffer(512);
      sb.append("<").append(MESSAGEID_TAG).append(">");
      if (sessionId != null)
         sb.append("<").append(SESSIONID_TAG).append(">").append(sessionId).append("</").append(SESSIONID_TAG).append(">");
      if (requestId != null)
         sb.append("<").append(REQUESTID_TAG).append(">").append(requestId).append("</").append(REQUESTID_TAG).append(">");
      if (methodName != null)
         sb.append("<").append(METHODNAME_TAG).append(">").append(methodName).append("</").append(METHODNAME_TAG).append(">");
      if (expiryTimestamp != null)
         sb.append("<").append(EXPIRES_TAG).append(">").append(IsoDateParser.getUTCTimestampT(expiryTimestamp)).append("</").append(EXPIRES_TAG).append(">");
      sb.append("</").append(MESSAGEID_TAG).append(">");
      return sb.toString();
   }
   
   public String toString() {
      return "from: " + this.from 
            + " to: " + getRecipientsList() 
            + " subject:" + this.subject
            + ((this.expiryTime != null) ? (" " + EXPIRES_HEADER_RFC2156 + ":" + MailUtil.dateTime(this.expiryTime)) : "")
            + " attachments:" + getFileNameList();
   }

   public void setContent(String content) {
      this.content = content;
   }

   /**
    * @return Returns the bcc array, is never null
    */
   public InternetAddress[] getBcc() {
      return (this.bcc==null) ? new InternetAddress[0] : this.bcc;
   }

   /**
    * @param bcc The bcc to set.
    */
   public void setBcc(String bcc) {
      String[] bccs = StringPairTokenizer.parseLine(bcc);
      this.bcc = new InternetAddress[bccs.length];
      for (int i=0; i<bccs.length; i++)
         this.bcc[i] = toInternetAddress(bccs[i]);
   }

   /**
    * @return Returns the cc array, is never null
    */
   public InternetAddress[] getCc() {
      return (this.cc==null) ? new InternetAddress[0] : this.cc;
   }

   /**
    * @param cc The cc to set.
    */
   public void setCc(String cc) {
      String[] ccs = StringPairTokenizer.parseLine(cc);
      this.cc = new InternetAddress[ccs.length];
      for (int i=0; i<ccs.length; i++)
         this.cc[i] = toInternetAddress(ccs[i]);
   }

   /**
    * Check if an email can be deleted.
    * <p /> 
    * RFC 2822 defines:
    * The header field "Date:" must exist in an email
    * exactly once, example: "Fri, 21 Nov 1997 09:55:06 -0600"
    * "The origination date specifies the date and time at which the creator
    * of the message indicated that the message was complete and ready to
    * enter the mail delivery system."
    * @param emailData email to check
    * @return true if is expired
    */
   public boolean isExpired() {
      // Currently we have two approaches to transport expiryDate:
      // first as an email-Header: "Expires: "
      // second in our messageId XML markup: <expires>2005-12-24T16:45:12Z</expires>
      if (this.expiryTime != null) {
         Date now = new Date();
         if (now.getTime() > this.expiryTime.getTime()) {
            if (log.isLoggable(Level.FINE)) log.fine("Email is epxired, we discard it: " + toString());
            return true;
         }
         return false;
      }

      String expires = extractMessageId(EmailData.EXPIRES_TAG);
      if (expires != null) {
         /* The string may contain CR LF as shown here (added by any MTA):
         <messageId><sessionId>lm4e560ghdFzj</sessionId><requestId>1138093430247000000</requestId><methodName>ping</methodName><expires>2006-01-24T
          09:04:50.248Z</expires></messageId>
          
          or somethin like MimeUtility.decode(expires, "quoted-printable"); ?
         */
         expires = ReplaceVariable.replaceAll(expires, "\r\n", "");
         try {
            Timestamp timestamp = new Timestamp(IsoDateParser.parse(expires).getTime());
            Date now = new Date();
            if (now.getTime() > timestamp.getTime()) {
               if (log.isLoggable(Level.FINE)) log.fine("Email is epxired, we discard it: " + toString());
               return true;
            }
         }
         catch (Throwable e) {
            log.warning("Ignoring expires setting '" + expires + "':" + e.toString());
         }
      }
      return false;
   }
   
   /**
    * Is transported in the email header "Expires: "
    * @return Returns the expiryTime or null if none is defined
    */
   public Timestamp getExpiryTime() {
      return this.expiryTime;
   }

   /**
    * Set an absolute time in future when this email is regarded as obsolete. 
    * @param expiryTime The expiryTime to set.
    */
   public void setExpiryTime(Timestamp expiryTime) {
      this.expiryTime = expiryTime;
   }

   /**
    * Returns the value of the RFC 822 "Date" field. This is the date 
    * on which this message was sent. Returns null if this field is 
    * unavailable or its value is absent. <p>
    * According to RC 822 this field exists always for incoming emails.
    * @return Returns the sentDate.
    */
   public Date getSentDate() {
      return this.sentDate;
   }

   /**
    * @param sentDate The sentDate to set.
    */
   public void setSentDate(Date sentDate) {
      this.sentDate = sentDate;
   }

   /**
    * Currenlty not supported!
    * @param replyTo The address to set.
    */
   public void setReplyTo(InternetAddress[] replyTo) {
      this.replyTo = replyTo;
   }

   /**
    * @return Returns the requestIdFromSentDate.
    */
   public boolean isRequestIdFromSentDate() {
      return this.requestIdFromSentDate;
   }

   /**
    * @param requestIdFromSentDate The requestIdFromSentDate to set.
    */
   public void setRequestIdFromSentDate(boolean requestIdFromSentDate) {
      this.requestIdFromSentDate = requestIdFromSentDate;
   }
   
   /**
    * For manual tests. java org.xmlBlaster.util.protocol.email.EmailData
    */
   public static void main(String[] args) {
      if (false) {
         String[] receivers = { "Receiver1", "Receiver2" };
         EmailData msg = new EmailData(receivers, "Sender", "A subject",
               "A content");
         msg.addAttachment(new AttachmentHolder("xy.xbf", "application/xmlBlaster", "Hello World".getBytes()));
         System.out.println("ORIG:\n" + msg.toXml(true));
         msg = EmailData.parseXml(msg.toXml(true));
         System.out.println("NEW:\n" + msg.toXml(true));
      }
      {
         String[] receivers = { "Receiver" };
         String subject = "<messageId><expires>2006-01-24\r\nT09:04:50.248Z</expires></messageId>";      
         EmailData msg = new EmailData(receivers, "Sender", subject, "A content");
         System.out.println("Is " + ((msg.isExpired()) ? "" : "not ") + "expired");
      }
   }

public boolean isSendContentAsText() {
	return sendContentAsText;
}

public void setSendContentAsText(boolean sendContentAsText) {
	this.sendContentAsText = sendContentAsText;
}
}
