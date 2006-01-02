/*------------------------------------------------------------------------------
 Name:      EventPlugin.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.mail.internet.InternetAddress;
import javax.management.NotificationBroadcasterSupport;

import org.jutils.log.LogChannel;
import org.jutils.log.LogableDevice;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.StringPairTokenizer;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.log.LogNotifierDeviceFactory;
import org.xmlBlaster.util.plugin.I_PluginConfig;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.protocol.email.EmailData;
import org.xmlBlaster.util.protocol.email.SmtpClient;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.authentication.ClientEvent;
import org.xmlBlaster.authentication.I_ClientListener;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.engine.runlevel.I_RunlevelListener;

/**
 * Registers for events from the xmlBlaster core and forwards them as
 * configured.
 * <p>
 * This is useful for clients or administrators to be notified on certain core events.
 * </p>
 * <p>
 * This <tt>EventPlugin</tt> plugin is started with the run level manager as
 * configured in <code>xmlBlasterPlugins.xml</code>, for example:
 * </p>
 * 
 * <pre>
 *&lt;plugin id='EventPlugin' className='org.xmlBlaster.engine.EventPlugin'&gt;
 *   &lt;action do='LOAD' onStartupRunlevel='7' sequence='1'
 *                        onFail='resource.configuration.pluginFailed'/&gt;
 *   &lt;action do='STOP' onShutdownRunlevel='6' sequence='1'/&gt;
 *   &lt;attribute id='eventTypes'>log.severe,log.warning&lt;/attribute>
 *   &lt;attribute id='destination.smtp'>mail.smtp.from=xmlBlaster@localhost,mail.smtp.to=demo@localhost,mail.collectMillis=10000&lt;/attribute>
 *&lt;/plugin&gt;
 * </pre>
 * 
 * <p>
 * In the above example an email is send if any log.severe (==log.error) or log.warning occurres.
 * </p>
 * <p>
 * List of supported event sources:
 * </p>
 * <table border="1">
 * <tr><td>log.severe</td><td>Captures all errors logged</td></tr>
 * <tr><td>log.warning</td><td>Captures all warnings logged</td></tr>
 * <tr><td>startupRunlevel.9</td><td>Captures event when startup runlevel reaches 9 (RUNNING), any other runlevel is possible as well (note that this plugin must be active beforehand)</td></tr>
 * <tr><td>startupRunlevel.8</td><td>Captures event when shutdown runlevel reaches 8 (RUNNING_RPE), any other runlevel is possible as well (note that this plugin must be active beforehand)</td></tr>
 * </table>
 * <p>
 * List of supported event sinks:
 * </p>
 * <table border="1">
 * <tr><td>destination.smtp</td><td>Sends an email about the occurred event, collects multiple events to one mail depending on configuration</td></tr>
 * </table>
 * 
 * <p>
 * We use the <tt>LOCAL</tt> protocol driver to talk to xmlBlaster, therefor
 * this plugin works only if the client and server is in the same virtual
 * machine (JVM).
 * </p>
 * 
 * @author <a href="mailto:xmlblast@marcelruff.info">Marcel Ruff</a>
 * @see <a
 *      href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.events.html">The
 *      admin.events requirement</a>
 */
public class EventPlugin extends NotificationBroadcasterSupport implements
      I_Plugin, EventPluginMBean, I_ClientListener, I_RunlevelListener,
      LogableDevice {
   private final static String ME = EventPlugin.class.getName();

   private static Logger log = Logger.getLogger(DbWatcher.class.getName());

   /** My JMX registration */
   private Object mbeanHandle;

   private ContextNode contextNode;

   protected Global glob;

   protected I_PluginConfig pluginConfig;

   protected SmtpClient smtpClient;

   private I_XmlBlasterAccess connection;

   protected org.xmlBlaster.engine.Global engineGlob;

   protected RequestBroker requestBroker;

   protected SessionInfo sessionInfo;
   
   protected boolean isActive;

   protected boolean isShutdown;
   
   protected String eventTypes;

   protected Set startupRunlevelSet;
   protected Set shutdownRunlevelSet;
   
   /**
    * Helper class to send emails
    */
   class SmtpDestinationHelper {
      SmtpClient smtpClient;

      String destination;

      String to, from, subjectTemplate, cc, bcc, contentTemplate;
      
      long collectIntervall = Constants.DAY_IN_MILLIS / 2;

      public SmtpDestinationHelper(SmtpClient smtpClient, String destination)
            throws XmlBlasterException {
         this.smtpClient = smtpClient;
         Map map = StringPairTokenizer.parseLineToProperties(destination);

         if (map.containsKey("mail.smtp.to"))
            this.to = (String) map.get("mail.smtp.to");
         verifyInternetAddress(this.to);

         if (map.containsKey("mail.smtp.from"))
            this.from = (String) map.get("mail.smtp.from");
         if (this.from == null)
            this.from = "xmlBlaster@localhost";
         verifyInternetAddress(this.from);

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
            this.contentTemplate = "$_{nodeId}\n\n$_{summary}\n$_{description}\n\nEventDate:$_{datetime}\n$_{versionInfo}";

         if (map.containsKey("mail.smtp.cc"))
            this.cc = (String) map.get("mail.smtp.cc");
         if (this.cc != null && this.cc.trim().length() > 0)
            verifyInternetAddress(this.cc);

         if (map.containsKey("mail.smtp.bcc"))
            this.bcc = (String) map.get("mail.smtp.bcc");
         if (this.bcc != null && this.bcc.trim().length() > 0)
            verifyInternetAddress(this.bcc);

         if (map.containsKey("mail.collectMillis")) {
            String tmp = (String) map.get("mail.collectMillis");
            this.collectIntervall = Long.valueOf(tmp).longValue();
         }
         if (this.collectIntervall < 0) this.collectIntervall = 0;
      }

      EmailData createEmailData() {
         EmailData emailData = new EmailData(this.to, this.from,
               "", "");
         emailData.setCc(this.cc);
         emailData.setBcc(this.bcc);
         // emailData.setExpiryTime(expiryTimestamp);
         // emailData.addAttachment(new AttachmentHolder(payloadFileName,
         // payloadMimetype, payload));
         return emailData;
      }
   } // end of helper class SmtpDestination

   protected final Object smtpDestinationMonitor = new Object();
   protected SmtpDestinationHelper smtpDestinationHelper;
   protected String smtpDestinationConfiguration;
   protected Timeout smtpTimeout;
   protected Timestamp smtpTimeoutHandle;
   protected EmailData currentEmailData;

   /**
    * Initializes the plugin
    * 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,
    *      org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global utilGlob, PluginInfo pluginInfo)
         throws XmlBlasterException {
      this.pluginConfig = pluginInfo;
      this.glob = utilGlob.getClone(utilGlob.getNativeConnectArgs());
      this.glob.addObjectEntry("ServerNodeScope", utilGlob
            .getObjectEntry("ServerNodeScope"));

      this.engineGlob = (org.xmlBlaster.engine.Global) utilGlob
            .getObjectEntry("ServerNodeScope");
      this.requestBroker = engineGlob.getRequestBroker();
      this.sessionInfo = requestBroker.getInternalSessionInfo();

      // For JMX instanceName may not contain ","
      this.contextNode = new ContextNode(ContextNode.SERVICE_MARKER_TAG,
            "EventPlugin[" + getType() + "]", this.engineGlob.getScopeContextNode());
      this.mbeanHandle = this.engineGlob.registerMBean(this.contextNode, this);

      String destLogStr = "";

      // Sending the events with email?
      this.smtpDestinationConfiguration = this.glob.get("destination.smtp", "", null,
            this.pluginConfig);
      setupSmtpSink(this.smtpDestinationConfiguration);
      if (this.smtpDestinationConfiguration != null && this.smtpDestinationConfiguration.trim().length() > 0)
            destLogStr += "destination.smtp:" + this.smtpDestinationConfiguration.trim();

      if (destLogStr.length() < 1) {
         log.warning("Please configure an attribute 'destination.smtp', there is nothing to do for us.");
         return;
      }

      this.eventTypes = this.glob.get("eventTypes", "", null,
            this.pluginConfig);
      if (this.eventTypes == null || this.eventTypes.trim().length() == 0) {
         log.warning("Please configure an attribute 'eventTypes', there is nothing to do for us.");
         return;
      }
      registerEventTypes(this.eventTypes);
      
      this.isActive = true;

      log.info("Configured to send core events of type '" + this.eventTypes.trim()
            + "' to destination '" + destLogStr + "'");
   } // init()

   /**
    * Find out which events to listen. 
    * @param eventTypes A commas seperated list of supported events, e.g. <code>log.severe,log.warning</code>
    */
   public void registerEventTypes(String eventTypes) throws XmlBlasterException {
      String[] eventTypeArr = StringPairTokenizer.parseLine(eventTypes);
      for (int i = 0; i < eventTypeArr.length; i++) {
         String event = eventTypeArr[i];
         if ("log.severe".equals(event)
               || "log.error".equals(event)) {
            // Please use JDK14 notation for configuration
            // We want to be notified if a log.error() is called, this will
            // notify our LogableDevice.log() method
            LogNotifierDeviceFactory lf = this.engineGlob
                  .getLogNotifierDeviceFactory();
            lf.register(LogChannel.LOG_ERROR, this);
         }
         else if ("log.warning".equals(event)
               || "log.warn".equals(event)) {
            LogNotifierDeviceFactory lf = this.engineGlob
                  .getLogNotifierDeviceFactory();
            lf.register(LogChannel.LOG_WARN, this);
         }
         else if (event.startsWith("startupRunlevel.")) {
            String rl = event.substring(event.indexOf(".")+1);
            log.fine("Register startupRunlevel = " + rl);
            this.engineGlob.getRunlevelManager().addRunlevelListener(this);
            if (this.startupRunlevelSet == null) this.startupRunlevelSet = new TreeSet();
            this.startupRunlevelSet.add(rl);
         }
         else if (event.startsWith("shutdownRunlevel.")) {
            String rl = event.substring(event.indexOf(".")+1);
            log.fine("Register shutdownRunlevel = " + rl);
            this.engineGlob.getRunlevelManager().addRunlevelListener(this);
            if (this.shutdownRunlevelSet == null) this.shutdownRunlevelSet = new TreeSet();
            this.shutdownRunlevelSet.add(rl);
         }
         else {
            log.warning("Ignoring unknown '" + eventTypeArr[i]
                  + "' from eventTypes='" + eventTypes + "'");
         }
      }
   }

   /**
    * Initialize email sending. 
    * @param destination The configuration string, a comma separated list of key/value properties, e.g.
    * <code>mail.smtp.from=xmlBlaster@localhost,mail.smtp.to=demo@localhost,mail.collectMillis=10000</code>
    * @throws XmlBlasterException
    */
   public void setupSmtpSink(String destination) throws XmlBlasterException {
      if (destination != null && destination.trim().length() > 0) {
         synchronized(this.smtpDestinationMonitor) {
            this.smtpDestinationHelper = new SmtpDestinationHelper(getSmtpClient(), destination);
            //if (this.smtpDestination.collectIntervall > 0)
            this.smtpTimeout = new Timeout("EventPlugin-SmtpTimer"); // we need it allways to synchronize
         }
      }
   }

   /**
    * Replace some $_{} tokens.
    * 
    * @param str The string to check and replace
    * @param summary The value for a $_{summary}
    * @return Resolved string
    */
   private String replaceTokens(String str, String summary, String description, String eventType) {
      if (str == null || str.indexOf("$") == -1)
         return str;
      str = ReplaceVariable.replaceAll(str, "$_{datetime}",
            new java.sql.Timestamp(new java.util.Date().getTime()).toString());
      str = ReplaceVariable.replaceAll(str, "$_{summary}", summary);
      str = ReplaceVariable.replaceAll(str, "$_{description}", description);
      str = ReplaceVariable.replaceAll(str, "$_{nodeId}", this.engineGlob.getInstanceId());
      str = ReplaceVariable.replaceAll(str, "$_{eventType}", eventType);
      if (str.indexOf("$_{versionInfo}") != -1) {
         XmlBlasterException e = new XmlBlasterException(this.engineGlob,
            ErrorCode.COMMUNICATION_NOCONNECTION, ME, "");
         str = ReplaceVariable.replaceAll(str, "$_{versionInfo}", e.getVersionInfo());
      }
      return str;
   }

   /**
    * @return the plugin type, defaults to "EventPlugin"
    * @see org.xmlBlaster.util.plugin.I_Plugin#getType()
    */
   public String getType() {
      if (this.pluginConfig != null)
         return this.pluginConfig.getType();
      return ME;
   }

   /**
    * @return the plugin version, defaults to "1.0"
    * @see org.xmlBlaster.util.plugin.I_Plugin#getVersion()
    */
   public String getVersion() {
      if (this.pluginConfig != null)
         return this.pluginConfig.getVersion();
      return "1.0";
   }

   /**
    * Shutdown the plugin
    * 
    * @see org.xmlBlaster.util.plugin.I_Plugin#shutdown()
    */
   public void shutdown() throws XmlBlasterException {
      if (this.isShutdown) return;
      // TODO: Unregister everything!
      LogNotifierDeviceFactory lf = this.engineGlob
            .getLogNotifierDeviceFactory();
      lf.unregister(LogChannel.LOG_WARN, this);
      lf.unregister(LogChannel.LOG_ERROR, this);
      if (this.engineGlob != null && this.mbeanHandle != null)
         this.engineGlob.unregisterMBean(this.mbeanHandle);
      if (connection != null)
         connection.disconnect(null);
      if (this.engineGlob != null) {
         this.engineGlob.getRunlevelManager().removeRunlevelListener(this);
      }
      this.isShutdown = true;
   }

   /**
    * TODO: Put into engine.Global and util.Global (see EmailExecutor.java)
    * 
    * @return
    * @throws XmlBlasterException
    */
   public SmtpClient getSmtpClient() throws XmlBlasterException {
      if (this.smtpClient == null) {
         this.smtpClient = (SmtpClient) this.engineGlob
               .getObjectEntry(SmtpClient.OBJECTENTRY_KEY);
         if (this.smtpClient == null) {
            String text = "Please register SmtpClient in xmlBlasterPlugins.xml to have 'email' support";
            throw new XmlBlasterException(this.engineGlob,
                  ErrorCode.COMMUNICATION_NOCONNECTION, ME, text);
         }
      }
      return this.smtpClient;
   }

   /**
    * Redirect logging.
    * 
    * @see org.jutils.log.LogableDevice#log(int, java.lang.String,
    *      java.lang.String)
    */
   public void log(int level, String source, String str) {
      // We may not do any log.xxx() call here because of recursion!!
      try {
         if (LogChannel.LOG_WARN == level || LogChannel.LOG_ERROR == level) {
            /*
             * // Emit JMX notification this.glob.sendNotification(this, "New " +
             * LogChannel.bitToLogLevel(level) + " logging occurred",
             * "lastError", "java.lang.String", this.lastError, newLog);
             */

            if (this.smtpDestinationHelper != null) {
               if (source == null) source = "";
               String description = (str == null) ? "" : str;

               String summary = 
                   "[" + new java.sql.Timestamp(new java.util.Date().getTime()).toString()
                 + " " + LogChannel.bitToLogLevel(level)
                 + " " + Thread.currentThread().getName()
                 + " " + source + "]";

               String eventType = (LogChannel.LOG_WARN == level) ? "log.warning" : "log.severe";
               sendEmail(summary, description, eventType, false);
            }
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   /**
    * Sending email as configured with <code>destination.smtp</code>. 
    * @param summary The email summary line to use, it is injected to the template as $_{summary}
    * @param description The event description to send, it is injected as $_{description}
    * @param eventType For example "log.severe"
    * @param forceSending If true send directly and ignore the timeout
    * @see http://www.faqs.org/rfcs/rfc2822.html
    */
   protected void sendEmail(String summary, String description, String eventType, boolean forceSending) {
      if (summary == null) summary = "";
      if (description == null) description = "";
      
      if (forceSending) {
         try {
            EmailData emailData = this.smtpDestinationHelper.createEmailData();
            emailData.setSubject(replaceTokens(
                  this.smtpDestinationHelper.subjectTemplate, summary, description, eventType));
            emailData.setContent(replaceTokens(
                  this.smtpDestinationHelper.contentTemplate, summary, description, eventType));
            this.smtpDestinationHelper.smtpClient.sendEmail(emailData);
         } catch (Throwable e) {
            throw new IllegalArgumentException(e.toString());
         }
         return;
      }

      synchronized(this.smtpDestinationMonitor) {
         // Build the email, if timer is active append new logging to the content of the existing mail ...
         EmailData emailData = (this.currentEmailData == null) ? this.smtpDestinationHelper.createEmailData() : this.currentEmailData;
         emailData.setSubject(replaceTokens(
               this.smtpDestinationHelper.subjectTemplate, summary, description, eventType));
         String old = (emailData.getContent().length() == 0) ? "" :
               emailData.getContent() + "\n\n========== NEXT ============\n\n";  
         emailData.setContent(old
               + replaceTokens(
               this.smtpDestinationHelper.contentTemplate, summary, description, eventType));
         
         // If no timer was active send immeditately (usually the first email)
         if (this.smtpTimeoutHandle == null) {
            try {
               smtpDestinationHelper.smtpClient.sendEmail(emailData);
            } catch (Throwable e) {
               e.printStackTrace();
            }
            finally {
               this.currentEmailData = null;
            }
         }
         else {
            // If a timer is active return, the timout will send the mail
            this.currentEmailData = emailData;
            return;
         }
         
         // Now span timer, other emails are collected until this timer elapses 
         if (this.smtpDestinationHelper.collectIntervall > 0) {
            this.smtpTimeoutHandle = this.smtpTimeout.addTimeoutListener(new I_Timeout() {
               public void timeout(Object userData) {
                  synchronized(smtpDestinationMonitor) {
                     smtpTimeoutHandle = null;
                     //System.out.println("Timeout happened");
                     if (currentEmailData == null) return;
                     try {
                        smtpDestinationHelper.smtpClient.sendEmail(currentEmailData);
                        // todo: Probably respan timer here to have the same minimal gap again
                     } catch (Throwable e) {
                        e.printStackTrace();
                     }
                     finally {
                        currentEmailData = null;
                     }
                  }
               }
            }, this.smtpDestinationHelper.collectIntervall, null);
         }
      } // sync
   }
   
   /* (non-Javadoc)
    * @see org.xmlBlaster.engine.runlevel.I_RunlevelListener#getName()
    */
   public String getName() {
      return ME;
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.engine.runlevel.I_RunlevelListener#runlevelChange(int,
    *      int, boolean)
    */
   public void runlevelChange(int from, int to, boolean force)
         throws XmlBlasterException {
      if (to == from)
         return;

      String summary = null;
      String description = null;
      String eventType = null;
      if (to > from) { // startup
         if (this.startupRunlevelSet != null && this.startupRunlevelSet.contains(""+to)) {
            summary = "Startup to " + to;
            description = "xmlBlaster startup runlevel from " + from + " to " + to;
            eventType = "startupRunlevel."+to;
         }
      }
      if (to < from) { // shutdown
         if (this.shutdownRunlevelSet != null && this.shutdownRunlevelSet.contains(""+to)) {
            summary = "Shutdown to " + to;
            description = "xmlBlaster shutdown runlevel from " + from + " to " + to;
            eventType = "shutdownRunlevel."+to;
         }
      }
      
      if (eventType == null) return;

      sendEmail(summary, description, eventType, true);
   }

   /*
    * (non-Javadoc)
    * 
    * @see org.xmlBlaster.authentication.I_ClientListener#sessionAdded(org.xmlBlaster.authentication.ClientEvent)
    */
   public void sessionAdded(ClientEvent e) throws XmlBlasterException {
      // TODO Auto-generated method stub
      /*
       * SessionInfo sessionInfo = e.getSessionInfo(); if (log.TRACE)
       * log.trace(ME, "Login event for client " + sessionInfo.toString());
       * 
       * this.glob.sendNotification(this, "Client '" +
       * sessionInfo.getSessionName().getAbsoluteName() + "' logged in",
       * "clientNew", "java.lang.String", "",
       * sessionInfo.getSessionName().getAbsoluteName());
       * 
       * if (this.publishLoginEvent) { this.publishQosLoginEvent.clearRoutes();
       * MsgQosData msgQosData =
       * (MsgQosData)this.publishQosLoginEvent.getData().clone(); // __sessionId
       * is deprecated, please use __publicSessionId
       * msgQosData.addClientProperty("__sessionId",
       * sessionInfo.getPublicSessionId());
       * msgQosData.addClientProperty("__publicSessionId",
       * sessionInfo.getPublicSessionId());
       * msgQosData.addClientProperty("__absoluteName",
       * sessionInfo.getSessionName().getAbsoluteName());
       * 
       * MsgUnit msgUnit = new MsgUnit(this.xmlKeyLoginEvent,
       * sessionInfo.getLoginName().getBytes(), msgQosData);
       * publish(this.unsecureSessionInfo, msgUnit); // publish that this client
       * has logged in
       * this.publishQosLoginEvent.getData().setTopicProperty(null); // only the
       * first publish needs to configure the topic }
       * 
       * if (log.TRACE) log.trace(ME, " client
       * added:"+sessionInfo.getLoginName());
       */
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.authentication.I_ClientListener#sessionPreRemoved(org.xmlBlaster.authentication.ClientEvent)
    */
   public void sessionPreRemoved(ClientEvent e) throws XmlBlasterException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.authentication.I_ClientListener#sessionRemoved(org.xmlBlaster.authentication.ClientEvent)
    */
   public void sessionRemoved(ClientEvent e) throws XmlBlasterException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.authentication.I_ClientListener#subjectAdded(org.xmlBlaster.authentication.ClientEvent)
    */
   public void subjectAdded(ClientEvent e) throws XmlBlasterException {
      // TODO Auto-generated method stub
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.authentication.I_ClientListener#subjectRemoved(org.xmlBlaster.authentication.ClientEvent)
    */
   public void subjectRemoved(ClientEvent e) throws XmlBlasterException {
      // TODO Auto-generated method stub

   }

   /**
    * Check string addresses if they have valid email syntax. 
    * @param address Can be a comma separated list of email addresses
    * @throws XmlBlasterException If one of the addresses has invalid syntax
    */
   public void verifyInternetAddress(String address) throws XmlBlasterException {
      String[] arr = StringPairTokenizer.parseLine(address);
      for (int i = 0; i < arr.length; i++) {
         try {
            new InternetAddress(arr[i]);
         } catch (Throwable e) {
            throw new XmlBlasterException(this.glob,
                  ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME,
                  "Illegal email address '" + address + "': " + e.toString());
         }
      }
   }
   
   /**
    * JMX
    */
   public String sendTestEmail() {
      sendEmail("Test email", "Hello world :-)", "testEvent", true);
      synchronized(this.smtpDestinationMonitor) {
         if (this.smtpDestinationHelper != null)
            return "Send email from '" + this.smtpDestinationHelper.from + "' to '" + this.smtpDestinationHelper.to + "'";
         else
            return "No sending of emails configured";
      }
   }
   
   /**
    * JMX
    */
   public String dumpPendingEmails() {
      synchronized(this.smtpDestinationMonitor) {
         if (this.currentEmailData == null) return "No emails found";
         return this.currentEmailData.toXml(true);
      }
   }

   /**
    * JMX
    */
   public int clearPendingEmails() {
      synchronized(this.smtpDestinationMonitor) {
         int ret = (this.currentEmailData == null) ? 0 : 1;
         this.currentEmailData = null;
         return ret;
      }
   }
   
   /**
    * JMX
    */
   public int getNumOfPendingEmails() {
      return (this.currentEmailData == null) ? 0 : 1;
   }

   /**
    * JMX
    */
   public String sendPendingEmails() {
      synchronized(this.smtpDestinationMonitor) {
         if (this.smtpDestinationHelper != null && this.smtpDestinationHelper.collectIntervall > 0) {
            if (this.currentEmailData != null) {
               try {
                  this.smtpTimeoutHandle = this.smtpTimeout.refreshTimeoutListener(this.smtpTimeoutHandle, 0);
               }
               catch (XmlBlasterException e) {
                  throw new IllegalArgumentException(e.getMessage()); 
               }
               return "Send email from '" + this.smtpDestinationHelper.from + "' to '" + this.smtpDestinationHelper.to + "'";
            }
         }
      }
      return (this.smtpDestinationHelper == null) ? 
              "No sending of emails configured" :
                 "Currently there is no pending email to send";
   }
   
   /* (non-Javadoc)
    * @see org.xmlBlaster.util.admin.I_AdminService#activate()
    */
   public void activate() throws Exception {
      // TODO Auto-generated method stub
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.admin.I_AdminService#deActivate()
    */
   public void deActivate() {
      // TODO Auto-generated method stub
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.admin.I_AdminService#isActive()
    */
   public boolean isActive() {
      return  this.isActive;
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.admin.I_AdminPlugin#isShutdown()
    */
   public boolean isShutdown() {
      return this.isShutdown;
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.admin.I_AdminUsage#usage()
    */
   public String usage() {
      return "Registers for events from the xmlBlaster core and forwards them as configured in xmlBlasterPlugins.xml"
            + Global.getJmxUsageLinkInfo(this.getClass().getName(), null);
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.admin.I_AdminUsage#getUsageUrl()
    */
   public String getUsageUrl() {
      return Global.getJavadocUrl(this.getClass().getName(), null);
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.admin.I_AdminUsage#setUsageUrl(java.lang.String)
    */
   public void setUsageUrl(String url) {
   }

   /**
    * How long to collect outgoing emails?
    * @return Returns the mailCollectMillis or -1 if no email sink is configured
    */
   public long getMailCollectMillis() {
      if (this.smtpDestinationHelper != null)
         return this.smtpDestinationHelper.collectIntervall;
      return -1;
   }

   /**
    * @param mailCollectMillis The mailCollectMillis to set.
    */
   public void setMailCollectMillis(long mailCollectMillis) {
      if (this.smtpDestinationHelper != null)
         this.smtpDestinationHelper.collectIntervall = (mailCollectMillis < 0) ? 0 : mailCollectMillis;
   }

   /**
    * @return Returns the eventTypes.
    */
   public String getEventTypes() {
      return this.eventTypes;
   }

   /**
    * @param eventTypes The eventTypes to set.
    */
   public void setEventTypes(String eventTypes) {
      this.eventTypes = eventTypes;
   }

   /**
    * @return Returns the smtpDestinationConfiguration.
    */
   public String getSmtpDestinationConfiguration() {
      return this.smtpDestinationConfiguration;
   }

   /**
    * @param smtpDestinationConfiguration The smtpDestinationConfiguration to set.
    */
   public void setSmtpDestinationConfiguration(String smtpDestinationConfiguration) {
      this.smtpDestinationConfiguration = smtpDestinationConfiguration;
   }
}
