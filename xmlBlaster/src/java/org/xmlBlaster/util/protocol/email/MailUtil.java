/*------------------------------------------------------------------------------
 Name:      MailUtil.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 Copyright: Parts learned from javamail-demo:
 1996-2003 Sun Microsystems, Inc. All Rights Reserved.
 Comment:   Converter used to convert native data to protocol-specific data.
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.util.protocol.email;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Logger;

import javax.mail.Address;
import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.URLName;
import javax.mail.event.StoreEvent;
import javax.mail.event.StoreListener;
import javax.mail.internet.ContentType;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.ParseException;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.xbformat.ByteArray;

/**
 * Utilities to dump mail messages. We provide a main() to play with POP3
 * access. Show information about and contents of messages.
 * 
 * @author John Mani
 * @author Bill Shannon
 * @author Marcel Ruff
 */
public class MailUtil {

   private static Logger log = Logger.getLogger(MailUtil.class.getName());

   static String url = null;

   static boolean verbose = false;

   static boolean debug = false;

   static boolean showStructure = false;

   static boolean showAlert = false;

   static boolean saveAttachments = false;

   static int attnum = 1;

   /**
    * Reading POP3 messages and dump them (for testing only). Usage:<br/>
    * <pre>
    *java org.xmlBlaster.util.protocol.email.MailUtil -L pop3://marcel:marcel@localhost/INBOX
    * </pre>
    * @param argv
    */
   public static void main(String argv[]) {
      for (int i = 0; i < argv.length; i++) {
         if (argv[i].equals("-v")) {
            verbose = true;
         } else if (argv[i].equals("-D")) {
            debug = true;
         } else if (argv[i].equals("-L")) {
            url = argv[++i];
         } else if (argv[i].equals("-s")) {
            showStructure = true;
         } else if (argv[i].equals("-S")) {
            saveAttachments = true;
         } else if (argv[i].equals("-a")) {
            showAlert = true;
         } else if (argv[i].equals("--")) {
            i++;
            break;
         } else if (argv[i].startsWith("-")) {
            System.out
                  .println("Usage:   java org.xmlBlaster.util.protocol.MailUtil [-L url]");
            System.out
                  .println("Example: java org.xmlBlaster.util.protocol.MailUtil -L pop3://marcel:marcel@localhost/INBOX");
            System.exit(1);
         } else {
            break;
         }
      }
      testPOP3Read();

   }

   private static void testPOP3Read() {
      try {
         final String mbox = "INBOX";
         // Get a Properties object
         Properties props = System.getProperties();

         // Get a Session object
         Session session = Session.getInstance(props, null);
         session.setDebug(debug);

         // Get a Store object
         Store store = null;
         if (url != null) {
            URLName urln = new URLName(url);
            store = session.getStore(urln);
            if (showAlert) {
               store.addStoreListener(new StoreListener() {
                  public void notification(StoreEvent e) {
                     String s;
                     if (e.getMessageType() == StoreEvent.ALERT)
                        s = "ALERT: ";
                     else
                        s = "NOTICE: ";
                     System.out.println(s + e.getMessage());
                  }
               });
            }
            store.connect();
         }

         Folder folder = store.getDefaultFolder();
         if (folder == null) {
            System.out.println("No default folder");
            System.exit(1);
         }

         folder = folder.getFolder(mbox);
         if (folder == null) {
            System.out.println("Invalid folder");
            System.exit(1);
         }

         // try to open read/write and if that fails try read-only
         try {
            folder.open(Folder.READ_WRITE);
         } catch (MessagingException ex) {
            folder.open(Folder.READ_ONLY);
         }
         int totalMessages = folder.getMessageCount();

         if (totalMessages == 0) {
            System.out.println("Empty folder");
            folder.close(false);
            store.close();
            System.exit(1);
         }

         if (verbose) {
            int newMessages = folder.getNewMessageCount();
            System.out.println("Total messages = " + totalMessages);
            System.out.println("New messages = " + newMessages);
            System.out.println("===============================");
         }

         // Attributes & Flags for all messages ..
         Message[] msgs = folder.getMessages();

         // Use a suitable FetchProfile
         FetchProfile fp = new FetchProfile();
         fp.add(FetchProfile.Item.ENVELOPE);
         fp.add(FetchProfile.Item.FLAGS);
         fp.add("X-Mailer");
         folder.fetch(msgs, fp);

         for (int i = 0; i < msgs.length; i++) {
            int level = 0;
            System.out.println("\n=========MESSAGE #" + (i + 1) + ":=================");
            dumpEnvelope(msgs[i], level);
            level = dumpPart(msgs[i], level);
         }

         folder.close(false);
         store.close();
      } catch (Exception ex) {
         System.out.println("Oops, got exception! " + ex.getMessage());
         ex.printStackTrace();
         System.exit(1);
      }
      System.exit(0);
   }

   public static void dumpMessage(Message msg) throws Exception {
      int level = 0;
      dumpEnvelope(msg, level);
      level = dumpPart(msg, level);
   }
   
   public static void dumpMessages(Message[] msgs) throws Exception {
      for (int i = 0; i < msgs.length; i++) {
         int level = 0;
         System.out.println("\n=========MESSAGE #" + (i + 1) + ":=================");
         dumpEnvelope(msgs[i], level);
         level = dumpPart(msgs[i], level);
      }
   }
   
   /**
    * Access all attachments. 
    * @param p
    * @return a list of AttachmentHolder instances
    * @throws Exception
    */
   public static ArrayList accessAttachments(Part p) throws XmlBlasterException {
      ArrayList attachments = new ArrayList();
      int level = 0;
      accessPart(p, level, attachments);
      return attachments;
   }

   public static int accessPart(Part p, int level, ArrayList attachments) throws XmlBlasterException {
      if (level > 0 && p instanceof Message) {
         log.warning("Unexpected Message type in level " + level);
         return level;
      }

      try {
         String ct = p.getContentType();
         String fileName = p.getFileName();
   
         /*
          * Using isMimeType to determine the content type avoids fetching the
          * actual content data until we need it.
          */
         if (p.isMimeType("text/plain")) { // All "UTF-8"
            AttachmentHolder a = new AttachmentHolder(fileName, ct, ((String)p.getContent()).getBytes(Constants.UTF8_ENCODING));
            attachments.add(a);
         } else if (p.isMimeType("multipart/*")) { // Go one level deeper ...
            Multipart mp = (Multipart) p.getContent();
            level++;
            int count = mp.getCount();
            for (int i = 0; i < count; i++)
               level = accessPart(mp.getBodyPart(i), level, attachments);
            level--;
         } else if (p.isMimeType("message/rfc822")) {
            level++;
            level = accessPart((Part) p.getContent(), level, attachments);
            level--;
         } else {
               Object o = p.getContent();
               if (o instanceof String) {
                  AttachmentHolder a = new AttachmentHolder(fileName, ct, ((String)o).getBytes(Constants.UTF8_ENCODING));
                  attachments.add(a);
               } else if (o instanceof InputStream) {
                  InputStream is = (InputStream) o;
                  ByteArray ba = new ByteArray(p.getSize() > 0 ? p.getSize() : 1024, is);
                  AttachmentHolder a = new AttachmentHolder(fileName, ct, ba.getByteArray());
                  attachments.add(a);
               } else {
                  AttachmentHolder a = new AttachmentHolder(fileName, ct, (o.toString()).getBytes(Constants.UTF8_ENCODING));
                  attachments.add(a);
               }
         }
      }catch (Exception e) {
         throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, MailUtil.class.getName(), "Can't access email attachments", e);
      }
      return level;
   }
   
   public static int dumpPart(Part p, int level) throws Exception {
      if (level > 0 && p instanceof Message)
         dumpEnvelope((Message) p, level);

      String ct = p.getContentType();
      try {
         pr("CONTENT-TYPE: " + (new ContentType(ct)).toString(), level);
      } catch (ParseException pex) {
         pr("BAD CONTENT-TYPE: " + ct, level);
      }
      String filename = p.getFileName();
      if (filename != null)
         pr("FILENAME: " + filename, level);

      /*
       * Using isMimeType to determine the content type avoids fetching the
       * actual content data until we need it.
       */
      if (p.isMimeType("text/plain")) {
         pr("", level);
         pr("=========This is plain text, level="+level+"===========", level);
         if (!showStructure && !saveAttachments)
            System.out.println((String) p.getContent());
      } else if (p.isMimeType("multipart/*")) {
         pr("=========This is a Multipart " + level + "==================", level);
         Multipart mp = (Multipart) p.getContent();
         level++;
         int count = mp.getCount();
         for (int i = 0; i < count; i++)
            level = dumpPart(mp.getBodyPart(i), level);
         level--;
      } else if (p.isMimeType("message/rfc822")) {
         pr("This is a Nested Message", level);
         pr("===========================", level);
         level++;
         level = dumpPart((Part) p.getContent(), level);
         level--;
      } else {
         if (!showStructure && !saveAttachments) {
            /*
             * If we actually want to see the data, and it's not a MIME type we
             * know, fetch it and check its Java type.
             */
            Object o = p.getContent();
            if (o instanceof String) {
               pr("===============This is a string, level="+level+"============", level);
               System.out.println((String) o);
            } else if (o instanceof InputStream) {
               pr("========This is a binary input stream, level="+level+"======", level);
               InputStream is = (InputStream) o;
               int c;
               while ((c = is.read()) != -1)
                  System.out.write(c);
            } else {
               pr("================This is an unknown type, level="+level+"===========", level);
               pr(o.toString(), level);
            }
         } else {
            // just a separator
            pr("********************************", level);
         }
      }

      /*
       * If we're saving attachments, write out anything that looks like an
       * attachment into an appropriately named file. Don't overwrite existing
       * files to prevent mistakes.
       */
      if (saveAttachments && level != 0 && !p.isMimeType("multipart/*")) {
         String disp = p.getDisposition();
         // many mailers don't include a Content-Disposition
         if (disp == null || disp.equalsIgnoreCase(Part.ATTACHMENT)) {
            if (filename == null)
               filename = "Attachment" + attnum++;
            pr("Saving attachment to file " + filename, level);
            try {
               File f = new File(filename);
               if (f.exists())
                  // XXX - could try a series of names
                  throw new IOException("file exists");
               OutputStream os = new BufferedOutputStream(new FileOutputStream(
                     f));
               InputStream is = p.getInputStream();
               int c;
               while ((c = is.read()) != -1)
                  os.write(c);
               os.close();
            } catch (IOException ex) {
               pr("Failed to save attachment: " + ex, level);
            }
            pr("---------------------------", level);
         }
      }
      return level;
   }

   public static void dumpEnvelope(Message m, int level) throws Exception {
      pr("------ This is the message envelope START ------", level);
      Address[] a;
      // FROM
      if ((a = m.getFrom()) != null) {
         for (int j = 0; j < a.length; j++)
            pr("FROM: " + a[j].toString(), level);
      }

      // TO
      if ((a = m.getRecipients(Message.RecipientType.TO)) != null) {
         for (int j = 0; j < a.length; j++) {
            pr("TO: " + a[j].toString(), level);
            InternetAddress ia = (InternetAddress) a[j];
            if (ia.isGroup()) {
               InternetAddress[] aa = ia.getGroup(false);
               for (int k = 0; k < aa.length; k++)
                  pr("  GROUP: " + aa[k].toString(), level);
            }
         }
      }

      // SUBJECT
      pr("SUBJECT: " + m.getSubject(), level);

      // DATE
      Date d = m.getSentDate();
      pr("SendDate: " + (d != null ? d.toString() : "UNKNOWN"), level);

      // FLAGS (not supported by POP3)
      Flags flags = m.getFlags();
      StringBuffer sb = new StringBuffer();
      Flags.Flag[] sf = flags.getSystemFlags(); // get the system flags

      boolean first = true;
      for (int i = 0; i < sf.length; i++) {
         String s;
         Flags.Flag f = sf[i];
         if (f == Flags.Flag.ANSWERED)
            s = "\\Answered";
         else if (f == Flags.Flag.DELETED)
            s = "\\Deleted";
         else if (f == Flags.Flag.DRAFT)
            s = "\\Draft";
         else if (f == Flags.Flag.FLAGGED)
            s = "\\Flagged";
         else if (f == Flags.Flag.RECENT)
            s = "\\Recent";
         else if (f == Flags.Flag.SEEN)
            s = "\\Seen";
         else
            continue; // skip it
         if (first)
            first = false;
         else
            sb.append(' ');
         sb.append(s);
      }

      String[] uf = flags.getUserFlags(); // get the user flag strings
      for (int i = 0; i < uf.length; i++) {
         if (first)
            first = false;
         else
            sb.append(' ');
         sb.append(uf[i]);
      }
      pr("FLAGS: " + sb.toString(), level);

      pr("HEADERS", level);
      Enumeration e = m.getAllHeaders();
      while (e.hasMoreElements()) {
         javax.mail.Header head = (javax.mail.Header) e.nextElement();
         pr(head.getName() + ": " + head.getValue(), level);
      }
      pr("------ This is the message envelope END ------", level);
   }

   static String indentStr = "                                               ";

   /**
    * Print a, possibly indented, string.
    */
   public static void pr(String s, int level) {
      if (showStructure)
         System.out.print(indentStr.substring(0, level * 2));
      System.out.println(s);
   }
}

/* Without attachment:
 * 
------ This is the message envelope START ------
FROM: Marcel Ruff <marcel@localhost>
TO: marcel@localhost
SUBJECT: Subject, without attachment
SendDate: Thu Oct 27 17:00:10 CEST 2005
FLAGS:
HEADERS
Return-Path: <marcel@localhost>
Received: from localhost ([127.0.0.1])
          by noty (JAMES SMTP Server 2.2.0) with SMTP ID 204
          for <marcel@localhost>;
          Thu, 27 Oct 2005 17:00:11 +0200 (CEST)
Message-ID: <4360EB7A.5060401@localhost>
Date: Thu, 27 Oct 2005 17:00:10 +0200
From: Marcel Ruff <marcel@localhost>
User-Agent: Mozilla Thunderbird 1.0.6 (X11/20050716)
X-Accept-Language: en-us, en
MIME-Version: 1.0
To: marcel@localhost
Subject: Subject, without attachment
Content-Type: text/plain; charset=ISO-8859-1; format=flowed
Content-Transfer-Encoding: 7bit
Delivered-To: marcel@localhost
------ This is the message envelope END ------
CONTENT-TYPE: text/plain; format=flowed; charset=ISO-8859-1

=========This is plain text, level=0===========
Some body text!
 */

/*
 * Example with a zip attachment:
=========MESSAGE #1:=================
------ This is the message envelope START ------
FROM: Marcel Ruff <marcel@localhost>
TO: marcel@localhost
SUBJECT: Subject with binary attachment
SendDate: Thu Oct 27 16:51:00 CEST 2005
FLAGS:
HEADERS
Return-Path: <marcel@localhost>
Received: from localhost ([127.0.0.1])
          by noty (JAMES SMTP Server 2.2.0) with SMTP ID 137
          for <marcel@localhost>;
          Thu, 27 Oct 2005 16:51:00 +0200 (CEST)
Message-ID: <4360E954.4060409@localhost>
Date: Thu, 27 Oct 2005 16:51:00 +0200
From: Marcel Ruff <marcel@localhost>
User-Agent: Mozilla Thunderbird 1.0.6 (X11/20050716)
X-Accept-Language: en-us, en
MIME-Version: 1.0
To: marcel@localhost
Subject: Subject with binary attachment
Content-Type: multipart/mixed;
 boundary="------------050806000406010507060909"
Delivered-To: marcel@localhost
------ This is the message envelope END ------
CONTENT-TYPE: multipart/mixed; boundary=------------050806000406010507060909
=========This is a Multipart 0==================
CONTENT-TYPE: text/plain; format=flowed; charset=ISO-8859-1

=========This is plain text, level=1===========
This is the normal body text!


CONTENT-TYPE: application/zip; name=something.zip
FILENAME: something.zip
========This is a binary input stream, level=1======
PK
j[3�

   sometextUT   �C�CUxfHello World
PK
*/


/* Example with one text attachment
 *
------ This is the message envelope START ------
FROM: Marcel Ruff <marcel@localhost>
TO: marcel@localhost
SUBJECT: Subject with text attachment
SendDate: Thu Oct 27 16:59:36 CEST 2005
FLAGS:
HEADERS
Return-Path: <marcel@localhost>
Received: from localhost ([127.0.0.1])
          by noty (JAMES SMTP Server 2.2.0) with SMTP ID 687
          for <marcel@localhost>;
          Thu, 27 Oct 2005 16:59:36 +0200 (CEST)
Message-ID: <4360EB58.7060205@localhost>
Date: Thu, 27 Oct 2005 16:59:36 +0200
From: Marcel Ruff <marcel@localhost>
User-Agent: Mozilla Thunderbird 1.0.6 (X11/20050716)
X-Accept-Language: en-us, en
MIME-Version: 1.0
To: marcel@localhost
Subject: Subject with text attachment
Content-Type: multipart/mixed;
 boundary="------------060303010309020708030505"
Delivered-To: marcel@localhost
------ This is the message envelope END ------
CONTENT-TYPE: multipart/mixed; boundary=------------060303010309020708030505
=========This is a Multipart 0==================
CONTENT-TYPE: text/plain; format=flowed; charset=ISO-8859-1

=========This is plain text, level=1===========
Some body text!


CONTENT-TYPE: application/unknown; name=sometext.txt
FILENAME: sometext.txt
========This is a binary input stream, level=1======
Hello World


*/