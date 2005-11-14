/*------------------------------------------------------------------------------
 Name:      EmailData.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 Comment:   javac EmailData.java SmtpClient.java
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.util.protocol.email;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.MethodName;

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
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class EmailData {
   private static Logger log = Logger.getLogger(EmailData.class.getName());

   protected String encoding = Constants.UTF8_ENCODING; // "text/plain; charset=UTF-8"

   protected String[] recipients;

   protected String from;
   
   protected String subject;

   protected String content;

   /** Containts AttachmentHolder instances */
   protected ArrayList attachments;

   /** Contains sessionId * */
   protected String sessionId;

   /** Contains requestId * */
   protected String requestId;

   /** The root tag &lt;messageId> */
   public static final String MESSAGEID_TAG = "messageId";

   public static final String REQUESTID_TAG = "requestId";

   public static final String SESSIONID_TAG = "sessionId";

   /** Holding the relevant email meta info like a request identifier */
   public static final String MESSAGEID_EXTENSION = ".mid";

   /**
    * Create a simple message.
    * 
    * @param aRecipient
    *           For example "jack@gmx.net"
    * @param aFrom
    *           For example "sue@gmx.net"
    * @param aSubject
    *           For example "Hi"
    * @param aContent
    *           For example "Best regards, Sue"
    */
   public EmailData(String aRecipient, String aFrom, String aSubject,
         String aContent) {
      this.recipients = new String[aRecipient == null ? 0 : 1];
      if (aRecipient != null)
         this.recipients[0] = aRecipient;
      this.from = aFrom;
      this.subject = aSubject;
      this.content = aContent;
   }

   /**
    * Create a simple message for any number of recipients.
    * 
    * @see #EmailData(String, String, String, String)
    */
   public EmailData(String[] aRecipients, String aFrom, String aSubject,
         String aContent) {
      this.recipients = aRecipients;
      this.from = aFrom;
      this.subject = aSubject;
      this.content = aContent;
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
         if (atts[j].getFileName().endsWith(extension)) {
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
   public byte[] getEncodedMsgUnitByExtension(String extension, String extensionZ, String extensionBackup) {
      AttachmentHolder[] atts = getAttachments();
      for (int j = 0; j < atts.length; j++) {
         if (atts[j].getFileName().endsWith(extension) || atts[j].getFileName().endsWith(extensionZ)) {
            return atts[j].getContent();
         }
      }
      for (int j = 0; j < atts.length; j++) {
         if (atts[j].getFileName().endsWith(extensionBackup)) {
            return atts[j].getContent();
         }
      }
      return null;
   }
   
   /**
    * Comma separated value list of all attachment file names for logging. 
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
    * @return For example "joe@locahost,a.xbf, b.xml, m.mid"
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
      return (this.recipients == null) ? new String[0] : this.recipients;
   }

   /**
    * @return The from of the message, never null
    */
   public String getFrom() {
      return (this.from == null) ? "" : this.from;
   }

   /**
    * @return The subject of the message, never null
    */
   public String getSubject() {
      return (this.subject == null) ? "" : this.subject;
   }

   /**
    * @return The content of the message, never null
    */
   public String getContent() {
      return (this.content == null) ? "" : this.content;
   }

   /**
    * Encapsulate the given string with CDATA, escapes "]]>" tokens in str.
    */
   public static String escape(String str) {
      str = (str == null) ? "" : str;
      int index;
      while ((index = str.indexOf("]]>")) != -1) {
         String tmp = str;
         str = tmp.substring(0, index + 2);
         str += "&gt;";
         str += tmp.substring(index + 3);
         System.out.println("Can't handle strings containing a CDATA end"
               + " section ']]>', i'll escape it to: " + str);
      }
      return "<![CDATA[" + str + "]]>";
   }

   /**
    * Dumps message to xml.
    */
   public String toXml() {
      String offset = "\n";
      StringBuffer sb = new StringBuffer(1024);
      sb.append(offset).append("<message>");
      sb.append(offset).append("  <from>").append(escape(getFrom())).append(
            "</from>");
      for (int i = 0; i < this.recipients.length; i++) {
         sb.append(offset).append("  <to>").append(escape(this.recipients[i]))
               .append("</to>");
      }
      if (this.recipients.length == 0) {
         sb.append(offset).append("  <to></to>");
      }
      sb.append(offset).append("  <subject>").append(escape(getSubject()))
            .append("</subject>");
      AttachmentHolder[] att = getAttachments();
      for (int i = 0; i < att.length; i++)
         sb.append(att[i].toXml());
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
    * For manual tests. java org.xmlBlaster.util.protocol.email.EmailData
    */
   public static void main(String[] args) {
      String[] receivers = { "Receiver1", "Receiver2" };
      EmailData msg = new EmailData(receivers, "Sender", "A subject",
            "A content");
      System.out.println("ORIG:\n" + msg.toXml());
      msg = EmailData.parseXml(msg.toXml());
      System.out.println("NEW:\n" + msg.toXml());
   }

   /**
    * @return Returns the requestId, never null
    */
   public String getRequestId() {
      if (this.requestId == null) {
         this.requestId = extractMessageId(REQUESTID_TAG);
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
      String str = getSubject();
      final String startToken = "<" + MESSAGEID_TAG + ">";
      if (str.indexOf(startToken) == -1) {
         str = null;
         // The <messageId> is not in the subject,
         // search in an attachment with extension ".mid"
         // or in an attachment without extension
         AttachmentHolder[] atts = getAttachments();
         for (int i = 0; i < atts.length; i++) {
            if (atts[i].getFileName().endsWith(MESSAGEID_EXTENSION)) {
               str = new String(atts[i].getContent());
               break; // strongest
            }
            if (atts[i].getFileName().indexOf(".") == -1) {
               str = new String(atts[i].getContent());
            }
         }
      }
      return str;
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
            + toXml());
      return null;
   }

   /**
    * Use together with extractMessageId(EmailData messageData, String tag).
    * 
    * @param methodName Can be null
    * @return A well formatted XML
    * <messageId><sessionId>abcd</sessionId><requestId>5</requestId><methodName>update</methodName></messageId>
    */
   public String createMessageId(MethodName methodName) {
      return createMessageId(this.sessionId, this.requestId, methodName);
   }
   
   public static String createMessageId(String sessionId, String requestId, MethodName methodName) {
      if (methodName == null)
         return "<messageId><sessionId>" + sessionId
               + "</sessionId><requestId>" + requestId
               + "</requestId></messageId>";
      else
         return "<messageId><sessionId>" + sessionId
               + "</sessionId><requestId>" + requestId
               + "</requestId><methodName>" + methodName.toString()
               + "</methodName></messageId>";
   }
   
   public String toString() {
      return "from: " + this.from + " to: " + getRecipientsList() + " subject:" + this.subject + " attachments:" + getFileNameList();
   }

}
