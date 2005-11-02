/*------------------------------------------------------------------------------
 Name:      Pop3Driver.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 Comment:   javac MessageData.java Pop3Driver.java
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.util.protocol.email;

import javax.mail.Session;
import javax.mail.Message;
import javax.mail.Store;
import javax.mail.Folder;
import javax.mail.Flags;
import javax.mail.Address;
import javax.mail.Authenticator;
import javax.mail.MessagingException;
import javax.mail.URLName;
import javax.mail.internet.MimeMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_ResponseListener;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.I_PluginConfig;
import org.xmlBlaster.util.plugin.PluginInfo;

/**
 * This class is capable to receive emails.
 * <p>
 * It extends Authenticator to support receiving mails with POP3.
 * </p>
 * 
 * @see <a
 *      href="http://www-106.ibm.com/developerworks/java/library/j-james1.html">James
 *      MTA</a>
 * @see <a href="http://java.sun.com/products/javamail/javadocs/index.html">Java
 *      Mail API</a>
 * @author Marcel Ruff (mrf)
 */
public class Pop3Driver extends Authenticator implements I_Plugin, I_Timeout {
   private static Logger log = Logger.getLogger(Pop3Driver.class.getName());

   private Session session;

   private String pop3Url;

   private PluginInfo pluginInfo;

   private Timeout timeout;

   private Timestamp timeoutHandle;

   private long pop3PollingInterval;
   
   private final Map listeners = new HashMap();

   public static final boolean CLEAR_MESSAGES = true;

   public static final boolean LEAVE_MESSAGES = false;

   public static final String UTF8 = "UTF-8";

   /**
    * You need to call setSessionProperties() thereafter.
    */
   public Pop3Driver() {
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver.
    * 
    * @return The configured [type] in xmlBlaster.properties, defaults to "pop3"
    */
   public String getProtocolId() {
      return (this.pluginInfo == null) ? "pop3" : this.pluginInfo.getType();
   }

   /**
    * Enforced by I_Plugin
    * 
    * @return The configured type in xmlBlaster.properties, defaults to "pop3"
    */
   public String getType() {
      return getProtocolId();
   }
   
   /**
    * If you are interested in an email register it here.
    * @param key <secretSessionId>:<requestId>
    * @param listener
    */
   public void registerForEmail(String secretSessionId, String requestId, I_ResponseListener listener) {
      if (secretSessionId==null&&requestId==null)
         throw new IllegalArgumentException("registerForEmail with null arguments");
      if (secretSessionId==null) secretSessionId = "";
      if (requestId==null) requestId = "";
      String key = secretSessionId+requestId;
      synchronized (this.listeners) {
         this.listeners.put(key, listener);
      }
   }

   public Object deregisterForEmail(String secretSessionId, String requestId) {
      if (secretSessionId==null&&requestId==null)
         throw new IllegalArgumentException("deregisterForEmail with null arguments");
      if (secretSessionId==null) secretSessionId = "";
      if (requestId==null) requestId = "";
      String key = secretSessionId+requestId;
      synchronized (this.listeners) {
         return this.listeners.remove(key);
      }
   }
   
   /**
    * Notify a listener about a new email. 
    * The registration remains
    * The listener is searched as a "sessionId-requestId" and as a general "sessionId"
    * @param messageData
    * @return The listener notified or null if none was found
    */
   private String notify(MessageData messageData) {
      if (messageData == null) return null;
      String key = messageData.getSessionId()+messageData.getRequestId();
      I_ResponseListener listenerSession = null;
      I_ResponseListener listenerRequest = null;
      synchronized (this.listeners) {
         listenerRequest = (I_ResponseListener)this.listeners.get(key);
         listenerSession = (I_ResponseListener)this.listeners.get(messageData.getSessionId());
      }
      
      // A request/reply handler is interested in specific messages only
      if (listenerRequest != null) {
         listenerRequest.responseEvent(messageData.getRequestId(), messageData);
         return key;
      }
      
      // A session is interested in all messages
      if (listenerSession != null) {
         listenerSession.responseEvent(messageData.getRequestId(), messageData);
         return messageData.getSessionId();
      }
      
      return null;
   }
   
   /**
    * The command line key prefix
    * 
    * @return The configured type in xmlBlasterPlugins.xml, defaults to
    *         "plugin/pop3"
    */
   public String getEnvPrefix() {
      return "plugin/" + getType().toLowerCase();
      // return (addressServer != null) ? addressServer.getEnvPrefix() :
      // "plugin/"+getType().toLowerCase();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return (this.pluginInfo == null) ? "1.0" : this.pluginInfo.getVersion();
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin).
    * The Pop3Driver singleton is registered in the Global object store.
    * 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo)
         throws XmlBlasterException {
      this.pluginInfo = pluginInfo;
      
      setSessionProperties(null, glob, pluginInfo);

      this.timeout = new Timeout("CallbackEmail-POP3PollingTimer");
      this.pop3PollingInterval = glob.get("pop3PollingInterval", 5000L,
            null, this.pluginInfo);

      this.timeoutHandle = this.timeout.addTimeoutListener(this,
            this.pop3PollingInterval, null);
      
      // Make this singleton available for others
      // key="org.xmlBlaster.util.protocol.email.Pop3Driver"
      glob.addObjectEntry(Pop3Driver.class.getName(), this);
   }

   /**
    * Set session properties and create a session.
    * <p>
    * Example settings:
    * </p>
    * 
    * <pre>
    * Properties props = System.getProperties();
    * props.put(&quot;mail.pop3.url&quot;, &quot;pop3://joe:secret@localhost/INBOX&quot;);
    * props.put(&quot;mail.pop3.debug&quot;, &quot;false&quot;);
    * </pre>
    * 
    * <p>
    * Usage is "pop3://user:password@host:port/INBOX". Only 'INBOX' is supported
    * for pop3. If a property is not found <tt>System.getProperty()</tt> is
    * consulted.
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

      if (props.getProperty("mail.pop3.debug") == null)
         props.put("mail.pop3.debug", glob.get("mail.pop3.debug", System
               .getProperty("mail.pop3.debug", "false"), null, pluginConfig));

      if (props.getProperty("mail.pop3.user") == null)
         props.put("mail.pop3.user", glob.get("mail.pop3.user", System
               .getProperty("user.name"), null, pluginConfig));
      String user = props.getProperty("mail.pop3.user").trim();

      if (props.getProperty("mail.pop3.password") == null)
         props.put("mail.pop3.password", glob.get("mail.pop3.password", user,
               null, pluginConfig));
      String password = props.getProperty("mail.pop3.password").trim();

      if (props.getProperty("mail.store.protocol") == null)
         props.put("mail.store.protocol", glob.get("mail.store.protocol",
               "pop3", null, pluginConfig));
      if (props.getProperty("mail.pop3.host") == null)
         props.put("mail.pop3.host", glob.get("mail.pop3.host", "127.0.0.1",
               null, pluginConfig));
      String host = props.getProperty("mail.pop3.host").trim();
      if (props.getProperty("mail.pop3.port") == null)
         props.put("mail.pop3.port", glob.get("mail.pop3.port", "110", null,
               pluginConfig));
      String port = props.getProperty("mail.pop3.port").trim();

      // "pop3://user:password@host:port/INBOX"
      this.pop3Url = glob.get("mail.pop3.url", "pop3://" + user + ":"
            + password + "@" + host + ":" + port + "/INBOX ", null,
            pluginConfig);

      this.session = Session.getInstance(props);

      // String from = user + '@' + host;
      if (log.isLoggable(Level.FINE))
         log.fine("Setting mail.pop3.url='" + this.pop3Url + "'");
   }

   /**
    * Polling for response messages.
    */
   public void timeout(Object userData) {
      log.fine("Timeout: Reading POP3 messages from " + getPop3Url());
      try {
         MessageData[] msgs = readInbox(Pop3Driver.CLEAR_MESSAGES);

         boolean responseArrived = false;
         for (int i = 0; i < msgs.length; i++) {
            MessageData messageData = msgs[i];
            notify(messageData);
         }

         if (!responseArrived) {
            log.fine("No mails via POP3 found");
         }

      } catch (Exception e) {
         e.printStackTrace();
         log.severe("[" + this.pop3Url + "] POP3 polling failed: " + e.toString());
      }

      try {
         this.timeoutHandle = timeout.addOrRefreshTimeoutListener(this,
               this.pop3PollingInterval, userData, this.timeoutHandle);
      } catch (XmlBlasterException e) {
         log.severe("Waiting on mail response failed: " + e.getMessage());
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
    * Returns for example "demo@localhost" which is extracted
    * from pop3Url="pop3://demo:secret@localhost:110/INBOX"
    * @return
    */
   public String getMyEmailAddress() {
      URLName urln = new URLName(this.pop3Url);
      return urln.getUsername() + "@" + urln.getHost();
   }

   /**
    * Read messages from mail server with POP3.
    * <p>
    * Convenience method which returns the most important fields only
    * </p>
    * 
    * @param clear
    *           If CLEAR_MESSAGES=true the messages are destroyed on the server
    * @return Never null
    */
   public MessageData[] readInbox(boolean clear) throws MessagingException,
         IOException, Exception {
      Store store = null;
      Folder inbox = null;
      try {
         URLName urln = new URLName(this.pop3Url);
         store = getSession().getStore(urln);
         store.connect();

         Folder root = store.getDefaultFolder();
         inbox = root.getFolder("inbox");
         inbox.open(Folder.READ_WRITE);
         Message[] msgs = inbox.getMessages();
         if (msgs == null)
            msgs = new Message[0];

         MessageData[] datas = new MessageData[msgs.length];
         for (int i = 0; i < msgs.length; i++) {
            MimeMessage msg = (MimeMessage) msgs[i];
            if (clear)
               msg.setFlag(Flags.Flag.DELETED, true);

            Address[] froms = msg.getFrom();
            String from = (froms != null && froms.length > 0) ? froms[0]
                  .toString() : "";

            Address[] arr = msg.getAllRecipients();
            if (arr == null)
               arr = new Address[0];
            String[] recips = new String[arr.length];
            for (int j = 0; j < arr.length; j++)
               recips[j] = arr[j].toString();

            datas[i] = new MessageData(recips, from, msg.getSubject(), msg
                  .getContent().toString());

            datas[i].setAttachments(MailUtil.accessAttachments(msg));
         }
         return datas;
      } finally {
         try {
            if (inbox != null)
               inbox.close(true);
         } catch (Exception e) { // MessagingException, IOException
            log.severe("Ignoring inbox close problem: " + e.toString());
         }
         try {
            if (store != null)
               store.close();
         } catch (Exception e) { // MessagingException, IOException
            log.severe("Ignoring store close problem: " + e.toString());
         }
      }
   }

   /**
    * @return Returns the pop3Url.
    */
   public String getPop3Url() {
      return this.pop3Url;
   }

   /**
    * @param pop3Url
    *           The pop3Url to set.
    */
   public void setPop3Url(String pop3Url) {
      this.pop3Url = pop3Url;
   }

   /**
    * @return Returns the pop3PollingInterval.
    */
   public long getPop3PollingInterval() {
      return this.pop3PollingInterval;
   }

   /**
    * @param pop3PollingInterval The timeout in milliseconds.
    */
   public void setPop3PollingInterval(long pop3PollingInterval) {
      this.pop3PollingInterval = pop3PollingInterval;
   }

   public synchronized void shutdown() {
      if (this.session != null) {
         log.info("Shutting down mail client");
         this.session = null;
      }
   }

   /**
    * java -Dmail.pop3.url=pop3://blue:blue@localhost/INBOX
    * org.xmlBlaster.util.protocol.email.Pop3Driver -receivePolling
    * <p>
    * 
    * @see #setSessionProperties(Properties) for other properties
    */
   public static void main(String[] args) {
      boolean receivePolling = false;
      if (args.length > 0) {
         if ("-receivePolling".equalsIgnoreCase(args[0])) {
            receivePolling = true;
         }
      }
      Global glob = new Global(args);

      Pop3Driver pop3Client = new Pop3Driver();
      try {
         final boolean debug = false;

         // Here we create the pop3Client Session manually without a JNDI lookup
         Properties props = System.getProperties();
         props.put("pop3Client.debug", "" + debug);
         pop3Client.setSessionProperties(props, glob, null);

         System.out.println("Reading POP3 messages");
         while (true) {
            long start = System.currentTimeMillis();
            MessageData[] msgs = pop3Client
                  .readInbox(Pop3Driver.CLEAR_MESSAGES);
            long diff = System.currentTimeMillis() - start;

            for (int i = 0; i < msgs.length; i++)
               System.out.println(msgs[i].toXml());
            if (msgs.length == 0) {
               System.out.println("[" + pop3Client.getPop3Url()
                     + "] No mails over POP3 found");
            }
            if (!receivePolling)
               break;
            int ch = Global.waitOnKeyboardHit("[" + pop3Client.getPop3Url()
                  + "] Hit a key for next polling (" + diff + " millis) >");
            if (ch == 'q')
               break;
         }
      } catch (Exception e) {
         System.out.println(pop3Client.getPop3Url() + ": pop3Client failed: "
               + e.toString());
      } finally {
         if (pop3Client != null)
            pop3Client.shutdown();
      }
   }
}
