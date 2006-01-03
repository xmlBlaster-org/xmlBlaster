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
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.StringPairTokenizer;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.log.LogNotifierDeviceFactory;
import org.xmlBlaster.util.plugin.I_PluginConfig;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.protocol.email.EmailData;
import org.xmlBlaster.util.protocol.email.SmtpClient;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.qos.TopicProperty;
import org.xmlBlaster.util.qos.storage.HistoryQueueProperty;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.authentication.ClientEvent;
import org.xmlBlaster.authentication.I_ClientListener;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.engine.runlevel.I_RunlevelListener;
import org.xmlBlaster.engine.runlevel.RunlevelManager;

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
 *   &lt;action do='LOAD' onStartupRunlevel='7' sequence='11'
 *                        onFail='resource.configuration.pluginFailed'/&gt;
 *   &lt;action do='STOP' onShutdownRunlevel='6' sequence='11'/&gt;
 *   
 *   &lt;attribute id='eventTypes'>
 *      log.severe,
 *      log.warning,
 *      startupRunlevel.8,
 *      client.sessionAdded
 *   &lt;/attribute>
 *   
 *   &lt;attribute id='destination.smtp'>
 *      mail.smtp.from=xmlBlaster@localhost,
 *      mail.smtp.to=demo@localhost,
 *      mail.collectMillis=10000
 *   &lt;/attribute>
 *   &lt;attribute id='destination.jmx'/>
 *&lt;/plugin&gt;
 * </pre>
 * 
 * <p>
 * In the above example an email is send if any log.severe (==log.error) or log.warning occurs.
 * Further an event is emitted on xmlBlaster startup in run level 8
 * and if a new client logs in.
 * Those events are send as JMX notifications as well.
 * Adding <code>&lt;attribute id='destination.publish'/></code> would send
 * the event as a xmlBlaster message as well, but take care to not send logging events
 * as such messages will most certainly loop (if they log something they will trigger another message and so forth)!
 * </p>
 * <p>
 * List of supported event sources, note that this plugin must be active on
 * a runlevel early enough depending on the event you want to capture:
 * </p>
 * <table border="1">
 * <tr><td>log.severe</td><td>Captures all errors logged</td></tr>
 * <tr><td>log.warning</td><td>Captures all warnings logged</td></tr>
 * <tr><td>startupRunlevel.9</td><td>Captures event when startup runlevel reaches 9 (RUNNING), any other runlevel is possible as well (note that this plugin must be active beforehand)</td></tr>
 * <tr><td>startupRunlevel.8</td><td>Captures event when shutdown runlevel reaches 8 (RUNNING_RPE), any other runlevel is possible as well (note that this plugin must be active beforehand)</td></tr>
 * <tr><td>client.sessionAdded</td><td>Captures event on client login</td></tr>
 * <tr><td>client.sessionRemoved</td><td>Captures event on client logout</td></tr>
 * <tr><td>subscribe.*</td><td>Captures if subscribe() is invoked (on all topics)</td></tr>
 * <tr><td>subscribe.[topicId]</td><td>Captures if subscribe() on the specified topic is invoked</td></tr>
 * <tr><td>subscribe.[relativeName]</td><td>Captures if the given client has invoked subscribe(), e.g. "subscribe.client/joe/1"</td></tr>
 * <tr><td>unSubscribe.*</td><td>Captures if unSubscribe() is invoked (on all topics)</td></tr>
 * <tr><td>unSubscribe.[topicId]</td><td>Captures if unSubscribe() on the specified topic is invoked</td></tr>
 * <tr><td>unSubscribe.[relativeName]</td><td>Captures if the given client has invoked unSubscribe(), e.g. "unSubscribe.client/joe/1"</td></tr>
 * </table>
 * <p>
 * List of supported event sinks:
 * </p>
 * <table border="1">
 * <tr>
 *    <td>destination.smtp</td>
 *    <td>Sends an email about the occurred event.
 *    Collects multiple events to one mail depending on configuration.
 *    You need to configure at least the email address parameters
 *    <code>mail.stmp.from</code> and <code>mail.smtp.to</code> and
 *    activate the <code>SmtpClient</code> plugin in <code>xmlBlasterPlugins.xml</code>.
 *    If you have a reasonable email provider you can configure it to 
 *    forward the mail as an SMS (mine offers this feature).</td>
 * </tr>
 * <tr>
 *    <td>destination.publish</td>
 *    <td>Publishes an xmlBlaster message which contains the occurred event,
 *     currently all messages are published into a topic named '__sys__Event'</td>
 * </tr>
 * <tr>
 *    <td>destination.jmx</td>
 *    <td>Emits an JMX notification for the occurred event.
 *     Open 'jconsole' and 'MBeans->org.xmlBlaster->node->xxx->service->EventPlugin[yyy]'
 *     there choose the 'Notifications[0]' tabulator and click the 'Subscribe' button.
 *     Now you receive the configured events.</td>
 * </tr>
 * </table>
 * 
 * <p>
 * We access the xmlBlaster core directly to register the supported internal
 * events, hence this plugin works only if it is in the same virtual
 * machine (JVM) as the xmlBlaster server.
 * </p>
 * <p>
 * All events don't throw any exceptions as this plugin should have
 * no influence on the regular work-flow of xmlBlaster.
 * </p>
 * 
 * @author <a href="mailto:xmlblast@marcelruff.info">Marcel Ruff</a>
 * @see <a
 *      href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.events.html">The
 *      admin.events requirement</a>
 */
public class EventPlugin extends NotificationBroadcasterSupport implements
      I_Plugin, EventPluginMBean, I_ClientListener, I_RunlevelListener,
      LogableDevice, I_SubscriptionListener {
   private final static String ME = EventPlugin.class.getName();

   private static Logger log = Logger.getLogger(EventPlugin.class.getName());

   /** My JMX registration */
   private Object mbeanHandle;

   private ContextNode contextNode;

   protected Global glob;

   protected I_PluginConfig pluginConfig;

   protected org.xmlBlaster.engine.Global engineGlob;

   protected RequestBroker requestBroker;

   protected SessionInfo sessionInfo;
   
   protected boolean isActive;

   protected boolean isShutdown;
   
   protected String eventTypes;
   protected Set startupRunlevelSet;
   protected Set shutdownRunlevelSet;
   protected Set loginLogoutSet;
   protected Set subscribeSet;
   protected Set unSubscribeSet;
   
   protected static int staticInstanceCounter;
   protected int instanceCounter;
   
   /**
    * Helper class to send emails
    */
   class SmtpDestinationHelper {
      SmtpClient smtpClient;

      String to, from, subjectTemplate, cc, bcc, contentTemplate, contentSeparator;
      
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

         if (map.containsKey("mail.contentSeparator"))
            this.contentSeparator = (String) map.get("mail.contentSeparator");
         else
            this.contentSeparator = "\n\n========== NEXT ============\n\n";

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
            this.collectIntervall = Long.valueOf(tmp.trim()).longValue();
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

   protected SmtpClient smtpClient;
   protected final Object smtpDestinationMonitor = new Object();
   protected SmtpDestinationHelper smtpDestinationHelper;
   protected String smtpDestinationConfiguration;
   protected Timeout smtpTimeout;
   protected Timestamp smtpTimeoutHandle;
   protected EmailData currentEmailData;

   /**
    * Helper class to publish messages. 
    */
   class PublishDestinationHelper {
      String destination;
      String key, qos;
      public PublishDestinationHelper(String destination) throws XmlBlasterException {
         Map map = StringPairTokenizer.parseLineToProperties(destination);
         if (map.containsKey("publish.key"))
            this.key = (String) map.get("publish.key");
         if (map.containsKey("publish.qos"))
            this.qos = (String) map.get("publish.qos");
      }
      MsgKeyData getPublishKey(String summary, String description,
            String eventType, String errorCode) throws XmlBlasterException {
         if (this.key != null) {
            return engineGlob.getMsgKeyFactory().readObject(this.key);
         }
         //PublishKey publishKey = new PublishKey(glob, Constants.EVENT_OID_LOGIN/*"__sys__Login"*/, "text/plain");
         // TODO: invent an oid depending on the eventType:
         PublishKey publishKey = new PublishKey(glob, "__sys__Event", "text/plain", "1.0");
         publishKey.setClientTags("<org.xmlBlaster><event/></org.xmlBlaster>");
         return publishKey.getData();
      }
      MsgQosData getPublishQos(String summary, String description,
            String eventType, String errorCode) throws XmlBlasterException {
         MsgQosData msgQosData = null;
         if (this.qos != null) {
            msgQosData = engineGlob.getMsgQosFactory().readObject(this.qos);
         }
         else {
            PublishQos publishQos = new PublishQos(glob);
            publishQos.setLifeTime(-1L);
            publishQos.setForceUpdate(true);
            // TODO: Configure history depth to 0 only on first publish
            TopicProperty topicProperty = new TopicProperty(glob);
            HistoryQueueProperty historyQueueProperty = new HistoryQueueProperty(glob, glob.getId());
            historyQueueProperty.setMaxEntriesCache(2);
            historyQueueProperty.setMaxEntries(2);
            topicProperty.setHistoryQueueProperty(historyQueueProperty);
            publishQos.setTopicProperty(topicProperty);
            msgQosData = publishQos.getData();
         }
         msgQosData.addClientProperty("_summary", summary);
         msgQosData.addClientProperty("_description", description);
         msgQosData.addClientProperty("_eventType", eventType);
         msgQosData.addClientProperty("_errorCode", errorCode);
         return msgQosData;
      }
      MsgUnit getMsgUnit(String summary, String description,
            String eventType, String errorCode) throws XmlBlasterException {
         String content = description;
         return new MsgUnit(
               getPublishKey(summary, description, eventType, errorCode),
               content.getBytes(),
               getPublishQos(summary, description, eventType, errorCode));
         
      }
   }
   protected PublishDestinationHelper publishDestinationHelper;
   protected String publishDestinationConfiguration;

   /**
    * Helper class to send a JMX notification.
    */
   class JmxDestinationHelper {
      String destination;
      public JmxDestinationHelper(String destination) throws XmlBlasterException {
         //Map map = StringPairTokenizer.parseLineToProperties(destination);
      }
   }
   protected JmxDestinationHelper jmxDestinationHelper;
   protected String jmxDestinationConfiguration;
   
   public EventPlugin() {
      synchronized (EventPlugin.class) {
         staticInstanceCounter++;
         this.instanceCounter = staticInstanceCounter;
      }
   }
   
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
      if (this.smtpDestinationConfiguration != null && this.smtpDestinationConfiguration.trim().length() > 0) {
         this.smtpDestinationConfiguration = this.smtpDestinationConfiguration.trim();
         setupSmtpSink(this.smtpDestinationConfiguration);
         destLogStr += "destination.smtp:" + this.smtpDestinationConfiguration;
      }

      // Sending the events with publish()?
      this.publishDestinationConfiguration = this.glob.get("destination.publish", (String)null, null,
            this.pluginConfig);
      if (this.publishDestinationConfiguration != null) {
         this.publishDestinationConfiguration = this.publishDestinationConfiguration.trim();
         this.publishDestinationHelper = new PublishDestinationHelper(this.publishDestinationConfiguration);
         this.requestBroker.getAuthenticate().addClientListener(this);
         destLogStr += "destination.publish:" + this.publishDestinationConfiguration;
      }

      // Sending the events as a JMX notification?
      this.jmxDestinationConfiguration = this.glob.get("destination.jmx", "", null,
            this.pluginConfig);
      if (this.jmxDestinationConfiguration != null) {
         this.jmxDestinationConfiguration = this.jmxDestinationConfiguration.trim();
         this.jmxDestinationHelper = new JmxDestinationHelper(this.jmxDestinationConfiguration);
         destLogStr += "destination.jmx:" + this.jmxDestinationConfiguration;
      }

      if (destLogStr.length() < 1) {
         log.warning("Please configure a data sink attribute 'destination.*', there is nothing to do for us.");
         return;
      }

      this.eventTypes = this.glob.get("eventTypes", "", null,
            this.pluginConfig);
      if (this.eventTypes == null || this.eventTypes.trim().length() == 0) {
         log.warning("Please configure an attribute 'eventTypes', there is nothing to do for us.");
         return;
      }
      this.eventTypes = this.eventTypes.trim();
      registerEventTypes(this.eventTypes);
      
      this.isActive = true;

      log.info("Configured to send core events of type '" + this.eventTypes.trim()
            + "' to '" + destLogStr + "'");
   } // init()

   /**
    * Find out which events to listen. 
    * @param eventTypes A commas seperated list of supported events, e.g. <code>log.severe,log.warning</code>
    */
   public void registerEventTypes(String eventTypes) throws XmlBlasterException {
      String[] eventTypeArr = StringPairTokenizer.parseLine(eventTypes);
      for (int i = 0; i < eventTypeArr.length; i++) {
         String event = eventTypeArr[i].trim();
         try {
            if ("log.severe".equals(event)
                  || "log.error".equals(event)) {
               // Please use JDK14 notation for configuration
               // We want to be notified if a log.error() is called, this will
               // notify our LogableDevice.log() method
               log.fine("Register logging status " + event);
               LogNotifierDeviceFactory lf = this.engineGlob
                     .getLogNotifierDeviceFactory();
               lf.register(LogChannel.LOG_ERROR, this);
            }
            else if ("log.warning".equals(event)
                  || "log.warn".equals(event)) {
               log.fine("Register logging status " + event);
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
            else if (event.startsWith("client.")) {
               String ev = event.substring(event.indexOf(".")+1);
               log.fine("Register login/logout event = " + ev);
               if (this.loginLogoutSet == null) this.loginLogoutSet = new TreeSet();
               this.loginLogoutSet.add(ev);
            }
            else if (event.startsWith("subscribe.")) {
               String ev = event.substring(event.indexOf(".")+1);
               log.fine("Register subscribe event = " + ev);
               this.requestBroker.addSubscriptionListener(this);
               if (this.subscribeSet == null) this.subscribeSet = new TreeSet();
               this.subscribeSet.add(ev);
            }
            else if (event.startsWith("unSubscribe.")) {
               String ev = event.substring(event.indexOf(".")+1);
               log.fine("Register unSubscribe event = " + ev);
               this.requestBroker.addSubscriptionListener(this);
               if (this.unSubscribeSet == null) this.unSubscribeSet = new TreeSet();
               this.unSubscribeSet.add(ev);
            }
            else {
               log.warning("Ignoring unknown '" + event
                     + "' from eventTypes='" + eventTypes + "'");
            }
         }
         catch (Throwable e) {
            log.warning("Ignoring '" + event
                     + "' from eventTypes='" + eventTypes + "' because of " + e.toString());
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
    * @param summary The value for a $_{summary}, can be null
    * @param description The value for a $_{description}, can be null
    * @param eventType The value for a $_{eventType}, can be null
    * @param errorCode The value for a $_{errorCode}, can be null
    * @return Resolved string
    */
   private String replaceTokens(String str, String summary, String description, String eventType, String errorCode) {
      if (str == null || str.indexOf("$") == -1)
         return str;
      str = ReplaceVariable.replaceAll(str, "$_{datetime}",
            new java.sql.Timestamp(new java.util.Date().getTime()).toString());
      str = ReplaceVariable.replaceAll(str, "$_{summary}", (summary==null)?"":summary);
      str = ReplaceVariable.replaceAll(str, "$_{description}", (description==null)?"":description);
      str = ReplaceVariable.replaceAll(str, "$_{nodeId}", this.engineGlob.getInstanceId()); // "/xmlBlaster/node/heron/instanceId/1136220586692"
      str = ReplaceVariable.replaceAll(str, "$_{id}", this.engineGlob.getId());  // "heron"
      str = ReplaceVariable.replaceAll(str, "$_{eventType}", (eventType==null)?"":eventType);
      str = ReplaceVariable.replaceAll(str, "$_{errorCode}", (errorCode==null)?"":errorCode);
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
      
      // TODO: Check if we unregister everything!
      // TODO: Protect each call with catch Throwable
      
      LogNotifierDeviceFactory lf = this.engineGlob
            .getLogNotifierDeviceFactory();
      lf.unregister(LogChannel.LOG_WARN, this);
      lf.unregister(LogChannel.LOG_ERROR, this);
      
      if (this.engineGlob != null && this.mbeanHandle != null)
         this.engineGlob.unregisterMBean(this.mbeanHandle);

      if (this.shutdownRunlevelSet != null)
         this.shutdownRunlevelSet = null;
      if (this.startupRunlevelSet != null)
         this.startupRunlevelSet = null;
      if (this.engineGlob != null) {
         this.engineGlob.getRunlevelManager().removeRunlevelListener(this);
      }
      
      if (this.loginLogoutSet != null)
         this.loginLogoutSet = null;
      if (this.requestBroker != null)
         this.requestBroker.getAuthenticate().removeClientListener(this);

      if (this.subscribeSet != null)
         this.subscribeSet = null;
      if (this.unSubscribeSet != null)
         this.unSubscribeSet = null;
      this.requestBroker.removeSubscriptionListener(this);
      
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
      if (LogChannel.LOG_WARN != level && LogChannel.LOG_ERROR != level)
         return;

      try {
         if (source == null) source = "";
         String description = (str == null) ? "" : str;

         String summary = 
             "[" + new java.sql.Timestamp(new java.util.Date().getTime()).toString()
           + " " + LogChannel.bitToLogLevel(level)
           + " " + Thread.currentThread().getName()
           + " " + source + "]";

         String eventType = (LogChannel.LOG_WARN == level) ? "log.warning" : "log.severe";
         
         // extract errorCode e.g. "... errorCode=communication.noConnection bla bla..."
         String errorCode=null;
         try {
            int index = str.lastIndexOf("errorCode=");
            if (index != -1) {
               errorCode = str.substring(index+10);
               int end = errorCode.indexOf(" ");
               if (end != -1)
                  errorCode = errorCode.substring(0,end);
            }
         }
         catch (Throwable e) {}

         if (this.smtpDestinationHelper != null) {
            try {
               sendEmail(summary, description, eventType, errorCode, false);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }
   
         if (this.publishDestinationHelper != null) {
            try {
               sendMessage(summary, description, eventType, errorCode, false);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }

         if (this.jmxDestinationHelper != null) {
            try {
               sendJmxNotification(summary, description, eventType, errorCode, false);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   /**
    * The xmlBlaster-JMX notification data sink. 
    * Sends a JMX notification with the current event occurred. 
    * <p>
    * Open <code>jconsole</code> and from the menue
    * 'MBeans->org.xmlBlaster->node->xxx->service->EventPlugin[yyy]',
    * there choose the 'Notifications[0]' tabulator and click the 'Subscribe' button.
    * Now you receive the configured events.
    * </p>
    * @see #replaceTokens(String str, String summary, String description, String eventType, String errorCode) {
    */
   protected void sendJmxNotification(String summary, String description,
         String eventType, String errorCode, boolean forceSending) {
      if (this.jmxDestinationHelper == null) return;
      if (!this.isActive) return;

      try {
         String message = eventType + ": " + summary; // Shows up under 'Message' in jconsole
         //String message = eventType + ": " + description; // Shows up under 'Message' in jconsole
         String attributeName = eventType; // TODO shutdownRunlevel.8 is no allowed attribute
         String oldValue = ""; // TODO what to put here?
         String newValue = eventType + ": " + summary; // Won't work if attributeName is illegal
         this.engineGlob.sendNotification(this, message,
               attributeName, "java.lang.String", oldValue, newValue);
      } catch (Throwable e) {
         throw new IllegalArgumentException(e.toString());
      }
   }

   /**
    * The xmlBlaster-message data sink. 
    * Publishes a message with the current event occurred. 
    * @see #replaceTokens(String str, String summary, String description, String eventType, String errorCode) {
    */
   protected void sendMessage(String summary, String description,
         String eventType, String errorCode, boolean forceSending) {
      if (this.publishDestinationHelper == null) return;
      if (!this.isActive) return;

      try {
         MsgUnit msgUnit = this.publishDestinationHelper.getMsgUnit(summary, description,
              eventType, errorCode);
         // Done already in getMsgUnit() above
         //msgUnit.getQosData().addClientProperty("_summary", summary);
         //msgUnit.getQosData().addClientProperty("_description", description);
         //msgUnit.getQosData().addClientProperty("_eventType", eventType);
         //msgUnit.getQosData().addClientProperty("_errorCode", errorCode);
         this.requestBroker.publish(this.sessionInfo, msgUnit);
      } catch (Throwable e) {
         throw new IllegalArgumentException(e.toString());
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
   protected void sendEmail(String summary, String description,
            String eventType, String errorCode, boolean forceSending) {
      if (this.smtpDestinationHelper == null) return;
      if (!this.isActive) return;

      if (summary == null) summary = "";
      if (description == null) description = "";
      
      if (forceSending) {
         try {
            EmailData emailData = this.smtpDestinationHelper.createEmailData();
            emailData.setSubject(replaceTokens(
                  this.smtpDestinationHelper.subjectTemplate, summary, description, eventType, errorCode));
            emailData.setContent(replaceTokens(
                  this.smtpDestinationHelper.contentTemplate, summary, description, eventType, errorCode));
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
               this.smtpDestinationHelper.subjectTemplate, summary, description, eventType, errorCode));
         String old = (emailData.getContent().length() == 0) ? "" :
               emailData.getContent() + this.smtpDestinationHelper.contentSeparator;  
         emailData.setContent(old
               + replaceTokens(
               this.smtpDestinationHelper.contentTemplate, summary, description, eventType, errorCode));
         
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
   public void runlevelChange(int from, int to, boolean force) {
      if (to == from)
         return;

      try {
         String summary = null;
         String description = null;
         String eventType = null;
         if (to > from) { // startup
            if (this.startupRunlevelSet != null && this.startupRunlevelSet.contains(""+to)) {
               summary = "Startup to " + RunlevelManager.toRunlevelStr(to) + " (" + to + ")";
               description = "xmlBlaster startup runlevel from " + RunlevelManager.toRunlevelStr(from) + " to " + RunlevelManager.toRunlevelStr(to);
               eventType = "startupRunlevel."+to;
            }
         }
         if (to < from) { // shutdown
            if (this.shutdownRunlevelSet != null && this.shutdownRunlevelSet.contains(""+to)) {
               summary = "Shutdown to " + RunlevelManager.toRunlevelStr(to) + " (" + to + ")";
               description = "xmlBlaster shutdown runlevel from " + RunlevelManager.toRunlevelStr(from) + " to " + RunlevelManager.toRunlevelStr(to);
               eventType = "shutdownRunlevel."+to;
            }
         }
         
         if (eventType == null) return;
   
         if (this.smtpDestinationHelper != null) {
            try {
               sendEmail(summary, description, eventType, null, false);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }
   
         if (this.publishDestinationHelper != null) {
            try {
               sendMessage(summary, description, eventType, null, false);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }

         if (this.jmxDestinationHelper != null) {
            try {
               sendJmxNotification(summary, description, eventType, null, false);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.authentication.I_ClientListener#sessionAdded(org.xmlBlaster.authentication.ClientEvent)
    */
   public void sessionAdded(ClientEvent clientEvent) throws XmlBlasterException {

      if (this.loginLogoutSet == null || !this.loginLogoutSet.contains("sessionAdded")) {
         return;
      }

      SessionInfo sessionInfo = clientEvent.getSessionInfo();
      try {
         //PublishKey(glob, Constants.EVENT_OID_LOGIN/*"__sys__Login"*/, "text/plain");
         // Key '__sys__UserList' for login/logout event
         // PublishKey(glob, Constants.EVENT_OID_USERLIST/*"__sys__UserList"*/, "text/plain");
         String summary = "Login of client " + sessionInfo.getSessionName().getAbsoluteName();
         String description = summary;
         String eventType = "client.sessionAdded";
         String errorCode = null;

         if (this.smtpDestinationHelper != null) {
            try {
               sendEmail(summary, description, eventType, null, false);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }
   
         if (this.publishDestinationHelper != null) {
            try {
               MsgUnit msgUnit = this.publishDestinationHelper.getMsgUnit(summary, description,
                    eventType, errorCode);
               // To be backwards compatible with loginEvent=true setting:
               msgUnit.getQosData().addClientProperty("__publicSessionId",
                     sessionInfo.getPublicSessionId());
               msgUnit.getQosData().addClientProperty("__subjectId",
                     sessionInfo.getLoginName());
               msgUnit.getQosData().addClientProperty("__nodeId",
                     this.engineGlob.getId());
               msgUnit.getQosData().addClientProperty("__absoluteName",
                     sessionInfo.getSessionName().getAbsoluteName());
               // TODO: backwards compatible?
               //msgUnit.setContent(sessionInfo.getLoginName().getBytes());
               this.requestBroker.publish(this.sessionInfo, msgUnit);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }

         if (this.jmxDestinationHelper != null) {
            try {
               sendJmxNotification(summary, description, eventType, null, false);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.authentication.I_ClientListener#sessionPreRemoved(org.xmlBlaster.authentication.ClientEvent)
    */
   public void sessionPreRemoved(ClientEvent e) throws XmlBlasterException {}

   /* (non-Javadoc)
    * @see org.xmlBlaster.authentication.I_ClientListener#sessionRemoved(org.xmlBlaster.authentication.ClientEvent)
    */
   public void sessionRemoved(ClientEvent clientEvent) throws XmlBlasterException {
      // TODO Auto-generated method stub
      //PublishKey(glob, Constants.EVENT_OID_LOGOUT/*"__sys__Logout"*/, "text/plain");
      // Key '__sys__UserList' for login/logout event
      // PublishKey(glob, Constants.EVENT_OID_USERLIST/*"__sys__UserList"*/, "text/plain");
      if (this.loginLogoutSet == null || !this.loginLogoutSet.contains("sessionRemoved")) {
         return;
      }
      SessionInfo sessionInfo = clientEvent.getSessionInfo();
      try {
         //PublishKey(glob, Constants.EVENT_OID_LOGIN/*"__sys__Login"*/, "text/plain");
         // Key '__sys__UserList' for login/logout event
         // PublishKey(glob, Constants.EVENT_OID_USERLIST/*"__sys__UserList"*/, "text/plain");
         String summary = "Logout of client " + sessionInfo.getSessionName().getAbsoluteName();
         String description = summary;
         String eventType = "client.sessionRemoved";
         String errorCode = null;

         if (this.smtpDestinationHelper != null) {
            try {
               sendEmail(summary, description, eventType, null, false);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }
   
         if (this.publishDestinationHelper != null) {
            try {
               MsgUnit msgUnit = this.publishDestinationHelper.getMsgUnit(summary, description,
                    eventType, errorCode);
               // To be backwards compatible with loginEvent=true setting:
               msgUnit.getQosData().addClientProperty("__publicSessionId",
                     sessionInfo.getPublicSessionId());
               msgUnit.getQosData().addClientProperty("__subjectId",
                     sessionInfo.getLoginName());
               msgUnit.getQosData().addClientProperty("__nodeId",
                     this.engineGlob.getId());
               msgUnit.getQosData().addClientProperty("__absoluteName",
                     sessionInfo.getSessionName().getAbsoluteName());
               // TODO: backwards compatible?
               //msgUnit.setContent(sessionInfo.getLoginName().getBytes());
               this.requestBroker.publish(this.sessionInfo, msgUnit);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }

         if (this.jmxDestinationHelper != null) {
            try {
               sendJmxNotification(summary, description, eventType, null, false);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }
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
      sendEmail("Test email", "Hello world :-)", "testEvent", null, true);
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
      this.isActive = true;
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.admin.I_AdminService#deActivate()
    */
   public void deActivate() {
      this.isActive = false;
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

   /* (non-Javadoc)
    * @see org.xmlBlaster.engine.I_SubscriptionListener#getPriority()
    */
   public Integer getPriority() {
      // Support multiple plugins
      return new Integer(this.instanceCounter+120);
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.engine.I_SubscriptionListener#subscriptionAdd(org.xmlBlaster.engine.SubscriptionEvent)
    */
   public void subscriptionAdd(SubscriptionEvent subscriptionEvent) throws XmlBlasterException {
      if (this.subscribeSet == null) return;

      SubscriptionInfo subscriptionInfo = subscriptionEvent.getSubscriptionInfo();
      SessionInfo sessionInfo = subscriptionInfo.getSessionInfo();
      String relativeName = sessionInfo.getSessionName().getRelativeName();
      String oid = subscriptionInfo.getKeyOid();
      
      String foundEvent = subscriptionInfo.getSubscriptionId();
      if (!this.subscribeSet.contains(foundEvent)) {
         foundEvent = oid;
         if (!this.subscribeSet.contains(foundEvent)) {
            foundEvent = relativeName;
            if (!this.subscribeSet.contains(foundEvent)) {
               foundEvent = "*";
               if (!this.subscribeSet.contains(foundEvent)) {
                  return;
               }
            }
         }
      }
      
      try {
         String summary = "New subscription of client " 
             + sessionInfo.getSessionName().getAbsoluteName()
             + " on topic " + oid;
         String description = subscriptionInfo.toXml();
         String eventType = "subscribe." + foundEvent;
         String errorCode = null;

         if (this.smtpDestinationHelper != null) {
            try {
               sendEmail(summary, description, eventType, null, false);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }
   
         if (this.publishDestinationHelper != null) {
            try {
               MsgUnit msgUnit = this.publishDestinationHelper.getMsgUnit(summary, description,
                    eventType, errorCode);
               msgUnit.getQosData().addClientProperty("__publicSessionId",
                     sessionInfo.getPublicSessionId());
               msgUnit.getQosData().addClientProperty("__subjectId",
                     sessionInfo.getLoginName());
               msgUnit.getQosData().addClientProperty("__nodeId",
                     this.engineGlob.getId());
               msgUnit.getQosData().addClientProperty("__absoluteName",
                     sessionInfo.getSessionName().getAbsoluteName());
               msgUnit.getQosData().addClientProperty("__subscriptionId",
                     subscriptionInfo.getSubscriptionId());
               msgUnit.getQosData().addClientProperty("__oid",
                     oid);
               msgUnit.getQosData().addClientProperty("__topicId",
                     subscriptionInfo.getTopicId());
               this.requestBroker.publish(this.sessionInfo, msgUnit);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }

         if (this.jmxDestinationHelper != null) {
            try {
               sendJmxNotification(summary, description, eventType, null, false);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.engine.I_SubscriptionListener#subscriptionRemove(org.xmlBlaster.engine.SubscriptionEvent)
    */
   public void subscriptionRemove(SubscriptionEvent subscriptionEvent) throws XmlBlasterException {
      if (this.unSubscribeSet == null) return;

      SubscriptionInfo subscriptionInfo = subscriptionEvent.getSubscriptionInfo();
      SessionInfo sessionInfo = subscriptionInfo.getSessionInfo();
      String relativeName = sessionInfo.getSessionName().getRelativeName();
      String oid = subscriptionInfo.getKeyOid();
      
      String foundEvent = subscriptionInfo.getSubscriptionId();
      if (!this.unSubscribeSet.contains(foundEvent)) {
         foundEvent = oid;
         if (!this.unSubscribeSet.contains(foundEvent)) {
            foundEvent = relativeName;
            if (!this.unSubscribeSet.contains(foundEvent)) {
               foundEvent = "*";
               if (!this.unSubscribeSet.contains(foundEvent)) {
                  return;
               }
            }
         }
      }
      
      try {
         String summary = "unSubscribe of client " 
             + sessionInfo.getSessionName().getAbsoluteName()
             + " on topic " + oid;
         String description = subscriptionInfo.toXml();
         String eventType = "unSubscribe." + foundEvent;
         String errorCode = null;

         if (this.smtpDestinationHelper != null) {
            try {
               sendEmail(summary, description, eventType, null, false);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }
   
         if (this.publishDestinationHelper != null) {
            try {
               MsgUnit msgUnit = this.publishDestinationHelper.getMsgUnit(summary, description,
                    eventType, errorCode);
               msgUnit.getQosData().addClientProperty("__publicSessionId",
                     sessionInfo.getPublicSessionId());
               msgUnit.getQosData().addClientProperty("__subjectId",
                     sessionInfo.getLoginName());
               msgUnit.getQosData().addClientProperty("__nodeId",
                     this.engineGlob.getId());
               msgUnit.getQosData().addClientProperty("__absoluteName",
                     sessionInfo.getSessionName().getAbsoluteName());
               msgUnit.getQosData().addClientProperty("__subscriptionId",
                     subscriptionInfo.getSubscriptionId());
               msgUnit.getQosData().addClientProperty("__oid",
                     oid);
               msgUnit.getQosData().addClientProperty("__topicId",
                     subscriptionInfo.getTopicId());
               this.requestBroker.publish(this.sessionInfo, msgUnit);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }

         if (this.jmxDestinationHelper != null) {
            try {
               sendJmxNotification(summary, description, eventType, null, false);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.engine.EventPluginMBean#triggerTestLogSevere()
    */
   public String triggerTestLogSevere() {
      log.severe("This is a manually invoked logging output for testing purposes only");// TODO Auto-generated method stub
      return "log.severe invoked";
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.engine.EventPluginMBean#triggerTestLogWarning()
    */
   public String triggerTestLogWarning() {
      log.warning("This is a manually invoked logging output for testing purposes only");// TODO Auto-generated method stub
      return "log.warning invoked";
   }
}
