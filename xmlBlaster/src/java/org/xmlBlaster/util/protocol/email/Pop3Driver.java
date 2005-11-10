/*------------------------------------------------------------------------------
 Name:      Pop3Driver.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 Comment:   javac MessageData.java Pop3Driver.java
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.util.protocol.email;

import javax.mail.NoSuchProviderException;
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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_ResponseListener;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.I_PluginConfig;
import org.xmlBlaster.util.plugin.PluginInfo;

/**
 * This class is capable to poll for emails using the POP3 protocol.
 * 
 * Configuration is done in <code>xmlBlasterPlugins.xml</code>:
 * 
 * <pre>
 *   &lt;plugin id='pop3' className='org.xmlBlaster.util.protocol.email.Pop3Driver'&gt;
 *   &lt;action do='LOAD' onStartupRunlevel='7' sequence='2' 
 *   onFail='resource.configuration.pluginFailed'/&gt;
 *   &lt;action do='STOP' onShutdownRunlevel='7' sequence='5'/&gt;   
 *   &lt;attribute id='mail.pop3.url'>pop3://xmlBlaster:xmlBlaster@localhost:110/INBOX&lt;/attribute>
 *   &lt;attribute id='pop3PollingInterval'>500&lt;/attribute>
 *   &lt;/plugin&gt;
 * </pre>
 * 
 * <p>
 * Switch on logging with
 * <pre>-trace[org.xmlBlaster.util.protocol.email.Pop3Driver] true</pre>
 * and add to xmlBlasterJdk14Logging.properties:
 * <pre>handlers = org.xmlBlaster.util.log.XmlBlasterJdk14LoggingHandler
 * .level= FINEST</pre>
 *
 * @see <a
 *      href="http://www-106.ibm.com/developerworks/java/library/j-james1.html">James
 *      MTA</a>
 * @see <a href="http://java.sun.com/products/javamail/javadocs/index.html">Java
 *      Mail API</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.email.html">The protocol.email requirement</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class Pop3Driver extends Authenticator implements I_Plugin, I_Timeout, Pop3DriverMBean {
   private static Logger log = Logger.getLogger(Pop3Driver.class.getName());

   private Global glob;

   private ContextNode contextNode;

   private Session session;

   private String pop3Url;

   private PluginInfo pluginInfo;

   private Timeout timeout;

   private Timestamp timeoutHandle;

   private long pollingInterval;
   
   private Properties props;

   private final Map listeners = new HashMap();

   private boolean firstException = true;

   // Avoid too many logging output lines
   private boolean isConnected;
   
   /** My JMX registration */
   private Object mbeanHandle;
   
   // Not tested and currently switched off
   private Map holdbackMap = new HashMap();
   private long holdbackExpireTimeout;

   public static final boolean CLEAR_MESSAGES = true;

   public static final boolean LEAVE_MESSAGES = false;
   
   public static final String POP3_FOLDER = "inbox";

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
    * 
    * @param key
    *           <secretSessionId>:<requestId>
    * @param listener
    */
   public void registerForEmail(String secretSessionId, String requestId,
         I_ResponseListener listener) {
      if (secretSessionId == null)
         secretSessionId = "";
      if (requestId == null)
         requestId = "";
      if (secretSessionId.length() == 0 && requestId.length() == 0)
         throw new IllegalArgumentException(
               "registerForEmail with null arguments");
      String key = secretSessionId + requestId;
      synchronized (this.listeners) {
         this.listeners.put(key, listener);
      }
      if (log.isLoggable(Level.FINE)) log.fine("Added listener with key=" + key);
   }

   public Object deregisterForEmail(String secretSessionId, String requestId) {
      if (secretSessionId == null && requestId == null)
         throw new IllegalArgumentException(
               "deregisterForEmail with null arguments");
      if (secretSessionId == null)
         secretSessionId = "";
      if (requestId == null)
         requestId = "";
      String key = secretSessionId + requestId;
      synchronized (this.listeners) {
         return this.listeners.remove(key);
      }
   }
   
   public String[] getListenerKeys() {
      synchronized (this.listeners) {
         return (String[])this.listeners.keySet().toArray(new String[this.listeners.size()]);
      }
   }

   public String getListeners() {
      String[] arr = getListenerKeys();
      StringBuffer buf = new StringBuffer();
      for (int i=0; i<arr.length; i++) {
         buf.append(arr[i]);
         if (i < arr.length - 1)
            buf.append(", ");
      }
      return buf.toString();
   }

   /**
    * Notify a listener about a new email. The registration remains The listener
    * is searched as a "sessionId-requestId" and as a general "sessionId"
    * 
    * @param messageData
    * @return The listener notified or null if none was found
    */
   private String notify(MessageData messageData) {
      if (messageData == null)
         return null;
      String key = messageData.getSessionId() + messageData.getRequestId();
      I_ResponseListener listenerSession = null;
      I_ResponseListener listenerRequest = null;
      I_ResponseListener listenerClusterNodeId = null;
      synchronized (this.listeners) {
         listenerRequest = (I_ResponseListener) this.listeners.get(key);
         if (listenerRequest  == null) {
            listenerSession = (I_ResponseListener) this.listeners.get(messageData
                  .getSessionId());
            if (listenerSession  == null) {
               listenerClusterNodeId = (I_ResponseListener) this.listeners.get(this.glob.getId());
            }
         }
      }

      // A request/reply handler is interested in specific messages only
      if (listenerRequest != null) {
         if (log.isLoggable(Level.FINER)) log.finer("Request specific listener found for key=" + key + ", email is " + messageData.toString());
         listenerRequest.responseEvent(messageData.getRequestId(), messageData);
         return key;
      }

      // A session is interested in all messages
      if (listenerSession != null) {
         if (log.isLoggable(Level.FINER)) log.finer("SessRequest specific listener found for key=" + messageData
               .getSessionId() + ", email is " + messageData.toString());
         listenerSession.responseEvent(messageData.getRequestId(), messageData);
         return messageData.getSessionId();
      }
      
      // A cluster node is interested in all messages (EmailDriver.java)
      if (listenerClusterNodeId != null) {
         if (log.isLoggable(Level.FINER)) log.finer("Node specific listener found for key=" + this.glob.getId()
                  + ", email is " + messageData.toString());
         listenerClusterNodeId.responseEvent(messageData.getRequestId(), messageData);
         return messageData.getSessionId();
      }
      
      if (this.holdbackExpireTimeout > 0) {
         Timestamp timestamp = new Timestamp();
         this.holdbackMap.put(new Long(timestamp.getTimestamp()), messageData);
      }
      
      log.warning("None of our registered listeners '" + getListeners() + "' matches for key=" + key + ", email is " + messageData.toString());
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
    * This method is called by the PluginManager (enforced by I_Plugin). The
    * Pop3Driver singleton is registered in the Global object store.
    * 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo)
         throws XmlBlasterException {
      this.glob = glob;
      this.pluginInfo = pluginInfo;

      // For JMX instanceName may not contain ","
      this.contextNode = new ContextNode(this.glob, ContextNode.SERVICE_MARKER_TAG, 
                          "Pop3Driver", this.glob.getContextNode());
      this.mbeanHandle = this.glob.registerMBean(this.contextNode, this);

      this.pollingInterval = glob.get("pop3PollingInterval", 5000L, null,
            this.pluginInfo);

      boolean activate = glob.get("activate", true, null,
            this.pluginInfo);

      // Default is off
      this.holdbackExpireTimeout = glob.get("holdbackExpireTimeout", 0, null,
            this.pluginInfo);
      
      setSessionProperties(null, glob, pluginInfo);

      // Make this singleton available for others
      // key="org.xmlBlaster.util.protocol.email.Pop3Driver"
      glob.addObjectEntry(Pop3Driver.class.getName(), this);

      this.timeout = new Timeout("POP3Driver-pollingTimer");
      if (activate) { 
         try { activate(); } catch (Exception e) { throw (XmlBlasterException)e; }
      }
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
   public synchronized void setSessionProperties(Properties properties, Global glob,
         I_PluginConfig pluginConfig) throws XmlBlasterException {
      this.props = properties;
      if (this.props == null)
         this.props = new Properties();

      if (this.props.getProperty("mail.pop3.debug") == null)
         this.props.put("mail.pop3.debug", glob.get("mail.pop3.debug", System
               .getProperty("mail.pop3.debug", "false"), null, pluginConfig));

      if (this.props.getProperty("mail.pop3.user") == null)
         this.props.put("mail.pop3.user", glob.get("mail.pop3.user", System
               .getProperty("user.name"), null, pluginConfig));
      String user = this.props.getProperty("mail.pop3.user").trim();

      if (this.props.getProperty("mail.pop3.password") == null)
         this.props.put("mail.pop3.password", glob.get("mail.pop3.password", user,
               null, pluginConfig));
      String password = this.props.getProperty("mail.pop3.password").trim();

      if (this.props.getProperty("mail.store.protocol") == null)
         this.props.put("mail.store.protocol", glob.get("mail.store.protocol",
               "pop3", null, pluginConfig));
      if (this.props.getProperty("mail.pop3.host") == null)
         this.props.put("mail.pop3.host", glob.get("mail.pop3.host", "127.0.0.1",
               null, pluginConfig));
      String host = this.props.getProperty("mail.pop3.host").trim();
      if (this.props.getProperty("mail.pop3.port") == null)
         this.props.put("mail.pop3.port", glob.get("mail.pop3.port", "110", null,
               pluginConfig));
      String port = this.props.getProperty("mail.pop3.port").trim();

      // "pop3://user:password@host:port/INBOX"
      this.pop3Url = glob.get("mail.pop3.url", "pop3://" + user + ":"
            + password + "@" + host + ":" + port + "/INBOX ", null,
            pluginConfig);

      this.session = Session.getInstance(this.props);

      try { // Produces a success logging output
         getStore();
      } catch (XmlBlasterException e) {
         log.warning(e.getMessage() + " We poll every "
              + this.pollingInterval + " milliseconds again.");
      }
   }
   
   private Long[] getHoldbackTimestamps() {
      return (Long[])this.holdbackMap.keySet().toArray(new Long[this.holdbackMap.size()]);
   }

   /**
    * Polling for response messages.
    */
   public void timeout(Object userData) {
      if (log.isLoggable(Level.FINER)) log.finer("Timeout: Reading POP3 messages from " + getPop3Url());
      
      // First try to deliver hold back messages
      // This happens if on startup we access POP3 messages but nobody has registered yet
      if (this.holdbackExpireTimeout > 0 && this.holdbackMap.size() > 0) {
         Long[] keys = getHoldbackTimestamps();
         Timestamp now = new Timestamp(); // nano seconds
         long expireNanos = this.holdbackExpireTimeout * Timestamp.MILLION;
         for (int i=0; i<keys.length; i++) {
            long tt = keys[i].longValue();
            if ((tt + expireNanos) > now.getTimestamp()) {
               this.holdbackMap.remove(keys[i]);
            }
            else {
               String listenerKey = notify((MessageData)this.holdbackMap.get(keys[i]));
               if (listenerKey != null) {
                  this.holdbackMap.remove(keys[i]);
               }
            }
         }
      }

      try {
         MessageData[] msgs = readInbox(Pop3Driver.CLEAR_MESSAGES);
         this.firstException = true;

         boolean responseArrived = false;
         for (int i = 0; i < msgs.length; i++) {
            MessageData messageData = msgs[i];
            String notifiedListener = notify(messageData);
            if (notifiedListener == null) {
               if (log.isLoggable(Level.FINE)) log.fine("None of the registered listeners (" + getListeners() + ") wants this email: " + messageData.toXml());
            }
         }

         if (!responseArrived) {
            if (log.isLoggable(Level.FINER)) log.finer("No mails via POP3 found");
         }

      } catch (XmlBlasterException e) {
         if (this.firstException
               && !e.isErrorCode(ErrorCode.RESOURCE_CONFIGURATION_CONNECT))
            log.severe("[" + this.pop3Url + "] POP3 polling failed: "
                  + e.getMessage());
         else { // RESOURCE_CONFIGURATION_CONNECT is logged already
            if (log.isLoggable(Level.FINE)) log.fine("[" + this.pop3Url + "] POP3 polling failed: "
                  + e.getMessage());
         }
         this.firstException = false;
      }
      catch (Throwable e) {
         log.severe("[" + this.pop3Url + "] POP3 polling failed: "
               + e.toString());
      }

      try {
         this.timeoutHandle = this.timeout.addOrRefreshTimeoutListener(this,
               this.pollingInterval, userData, this.timeoutHandle);
      } catch (XmlBlasterException e) {
         log.severe("Waiting on mail response failed: " + e.getMessage());
      }
   }

   /**
    * Access the mailing session.
    */
   public Session getSession() {
      if (this.session == null) { // after a previous shutdown()
         synchronized (this) {
            if (this.session == null)
               this.session = Session.getInstance(this.props);
         }
      }
      return this.session;
   }

   public Message getMessage() {
      return new MimeMessage(getSession());
   }

   /**
    * Returns for example "demo@localhost" which is extracted from
    * pop3Url="pop3://demo:secret@localhost:110/INBOX"
    * 
    * @return
    */
   public String getMyEmailAddress() {
      URLName urln = new URLName(this.pop3Url);
      return urln.getUsername() + "@" + urln.getHost();
   }

   /**
    * Returns for example "pop3://demo@localhost:110/INBOX" which is extracted
    * from pop3Url="pop3://demo:secret@localhost:110/INBOX"
    * 
    * @return
    */
   public String getUrlWithoutPassword() {
      URLName urln = new URLName(this.pop3Url);
      return urln.getProtocol() + "://"
              + urln.getUsername() + "@" + urln.getHost()
              + ((urln.getPort() > 0) ? (":" + urln.getPort()) : "")
              + "/" + urln.getFile();
   }

   /**
    * Connect to POP3 store.
    * 
    * @return never null
    * @throws XmlBlasterException
    */
   private Store getStore() throws XmlBlasterException {
      Store store = null;
      URLName urln = new URLName(this.pop3Url);
      try {
         store = getSession().getStore(urln);
      } catch (NoSuchProviderException e) {
         throw new XmlBlasterException(this.glob,
               ErrorCode.RESOURCE_CONFIGURATION, Pop3Driver.class.getName(),
               "No POP3 provider for url '" + getUrlWithoutPassword()
                     + "' found", e);
      }
      try {
         store.connect();
         if (!this.isConnected) { // Avoid too many logging output
            log.info("Successfully contacted POP3 server '"
                  + getUrlWithoutPassword() + "', we poll every "
                  + this.pollingInterval + " milliseconds for emails.");
            this.isConnected = true;
         }
         return store;
      } catch (MessagingException e) {
         if (this.isConnected) { // Avoid too many logging output
            log.warning("No POP3 server '" + this.pop3Url
                  + "' found, we poll every " + this.pollingInterval
                  + " milliseconds again:" + e.toString());
            this.isConnected = false;
         }
         if (e instanceof javax.mail.AuthenticationFailedException)
            throw new XmlBlasterException(this.glob,
               ErrorCode.RESOURCE_CONFIGURATION_CONNECT, Pop3Driver.class
                     .getName(), "The POP3 server '" + this.pop3Url
                     + "' is not available", e);
         else
            throw new XmlBlasterException(this.glob,
                  ErrorCode.RESOURCE_CONFIGURATION_CONNECT, Pop3Driver.class
                        .getName(), "The POP3 server '" + getUrlWithoutPassword()
                        + "' is not available", e);
      }
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
   public MessageData[] readInbox(boolean clear) throws XmlBlasterException {
      //if (isShutdown()) Does it recover automatically after a shutdown?
      //   throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALSTATE, Pop3Driver.class.getName(), "The plugin is shutdown");
      Store store = null;
      Folder inbox = null;
      try {
         store = getStore();
         Folder root = store.getDefaultFolder();
         inbox = root.getFolder(POP3_FOLDER);
         inbox.open(Folder.READ_WRITE);
         Message[] msgs = inbox.getMessages();
         if (msgs == null)
            msgs = new Message[0];

         MessageData[] datas = new MessageData[msgs.length];
         for (int i = 0; i < msgs.length; i++) {
            log.fine("Readig message #" + (i+1) + "/" + msgs.length + " from INBOX");
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
      } catch (MessagingException e) {
         throw new XmlBlasterException(this.glob,
               ErrorCode.RESOURCE_CONFIGURATION, Pop3Driver.class.getName(),
               "Problems to read POP3 email from '" + getUrlWithoutPassword()
                     + "'", e);
      } catch (IOException e) {
         throw new XmlBlasterException(this.glob,
               ErrorCode.RESOURCE_CONFIGURATION, Pop3Driver.class.getName(),
               "Problems to read POP3 email from '" + getUrlWithoutPassword()
                     + "'", e);
      } finally {
         try {
            if (inbox != null)
               inbox.close(true);
         } catch (Exception e) { // MessagingException, IOException
            log.warning("Ignoring inbox close problem: " + e.toString());
         }
         try {
            if (store != null)
               store.close();
         } catch (Exception e) { // MessagingException, IOException
            log.warning("Ignoring store close problem: " + e.toString());
         }
      }
   }

   /**
    * @return Syntax is "pop3://user:password@host:port/INBOX"
    */
   public String getPop3Url() {
      return this.pop3Url;
   }

   /**
    * @param pop3Url Syntax is "pop3://user:password@host:port/INBOX"
    */
   public void setPop3Url(String pop3Url) {
      this.pop3Url = pop3Url;
   }

   /**
    * @return Returns the pollingInterval.
    */
   public long getPollingInterval() {
      return this.pollingInterval;
   }

   /**
    * @param pollingInterval
    *           The timeout in milliseconds.
    */
   public void setPollingInterval(long pollingInterval) {
      this.pollingInterval = pollingInterval;
   }

   /**
    * Activate xmlBlaster access through this protocol.
    * Triggers an immediate POP3 access and starts polling thereafter
    */
   public void activate() throws Exception {
      log.fine("Entering activate()");
      try {
         this.timeoutHandle = this.timeout.addOrRefreshTimeoutListener(this,
               0, null, this.timeoutHandle);
      } catch (XmlBlasterException e) {
         log.severe("Activating timeout listener failed: " + e.getMessage());
         throw new Exception("Activating timeout listener failed: " + e.getMessage());
      }
   }
   
   public boolean isActive() {
      return this.timeoutHandle != null;
   }

   /**
    * Deactivate xmlBlaster access (standby), no clients can connect.
    */
   public void deActivate() {
      log.fine("Entering deActivate()");
      this.timeout.removeTimeoutListener(this.timeoutHandle);
      this.timeoutHandle = null;
   }


   /**
    * Halt the plugin. 
    */
   public synchronized void shutdown() {
      if (this.session != null) {
         log.info("Shutting down POP3 mail client, removing listeners");
         deActivate();
         synchronized (this.listeners) {
            this.listeners.clear();
         }
         this.glob.unregisterMBean(this.mbeanHandle);
         this.session = null;
      }
   }
   
   public boolean isShutdown() {
      return this.session == null;
   }

   /**
    * @return a human readable usage help string
    */
   public java.lang.String usage() {
      return "The pop3Url has the syntax 'pop3://user:password@host:port/INBOX'"
      +"\nCalling shutdown destroys the service (you can't start it again)"
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
