/*------------------------------------------------------------------------------
 Name:      EventPlugin.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import gnu.regexp.RE;
import gnu.regexp.REException;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.logging.LogRecord;
import java.util.logging.Level;

import javax.mail.internet.InternetAddress;
import javax.management.NotificationBroadcasterSupport;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_EventDispatcher;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.StringPairTokenizer;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBuffer;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.dispatch.DispatchManager;
import org.xmlBlaster.util.dispatch.I_ConnectionStatusListener;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.log.I_LogListener;
import org.xmlBlaster.util.log.XbNotifyHandler;
import org.xmlBlaster.util.plugin.I_PluginConfig;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.protocol.email.EmailData;
import org.xmlBlaster.util.protocol.email.SmtpClient;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.qos.TopicProperty;
import org.xmlBlaster.util.qos.storage.HistoryQueueProperty;
import org.xmlBlaster.util.queue.QueueEventHandler;
import org.xmlBlaster.util.queue.QueuePluginManager;
import org.xmlBlaster.util.queue.StorageEventHandler;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.authentication.ClientEvent;
import org.xmlBlaster.authentication.I_ClientListener;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.authentication.SubjectInfo;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.engine.msgstore.MapEventHandler;
import org.xmlBlaster.engine.msgstore.StoragePluginManager;
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
 *      logging/severe/*,
 *      logging/warning/*,
 *      service/RunlevelManager/event/startupRunlevel8,
 *      client/joe/session/1/event/connect
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
 * In the above example an email is send if any logging/severe/* (==log/error) or logging/warning/* occurs.
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
 * <tr><td>logging/severe/*</td><td>Captures all errors logged</td></tr>
 * <tr><td>logging/warning/*</td><td>Captures all warnings logged</td></tr>
 * <tr><td>service/RunlevelManager/event/startupRunlevel9</td><td>Captures event when startup run level reaches 9 (RUNNING), any other runlevel is possible as well (note that this plugin must be active beforehand)</td></tr>
 * <tr><td>service/RunlevelManager/event/shutdownRunlevel8</td><td>Captures event when shutdown runlevel reaches 8 (RUNNING_RPE), any other run level is possible as well (note that this plugin must be active beforehand)</td></tr>
 * <tr><td>client/* /session/* /event/connect</td><td>Captures event on client login (all clients)</td></tr>
 * <tr><td>client/[subjectId]/session/[publicSessionId]/event/connect</td><td>Captures event on given client login, e.g. "client/joe/session/1/event/connect"</td></tr>
 * <tr><td>client/* /session/* /event/disconnect</td><td>Captures event on client logout (all clients)</td></tr>
 * <tr><td>client/[subjectId]/session/[publicSessionId]/event/disconnect</td><td>Captures event on given client logout, e.g. "client/joe/session/1/event/disconnect"</td></tr>
 * <tr><td>topic/* /event/subscribe</td><td>Captures if subscribe() is invoked (on all topics)</td></tr>
 * <tr><td>topic/[topicId]/event/subscribe</td><td>Captures if subscribe() on the specified topic is invoked</td></tr>
 * <tr><td>client/[subjectId]/session/[publicSessionId]/event/subscribe</td><td>Captures if the given client has invoked subscribe(), e.g. "client/joe/session/1/event/subscribe". The publicSessionId can be a wildcard "*".</td></tr>
 * <tr><td>topic/* /event/unSubscribe</td><td>Captures if unSubscribe() is invoked (on all topics)</td></tr>
 * <tr><td>topic/[topicId]/event/unSubscribe</td><td>Captures if unSubscribe() on the specified topic is invoked</td></tr>
 * <tr><td>client/[subjectId]/session/[publicSessionId]/event/unSubscribe</td><td>Captures if the given client has invoked unSubscribe(), e.g. "client/joe/session/1/event/unSubscribe". The publicSessionId can be a wildcard "*".</td></tr>
 * <tr><td>topic/* /event/alive</td><td>Captures if a topic is created (on all topics)</td></tr>
 * <tr><td>topic/hello/event/alive</td><td>Captures event if the topic 'hello' is created</td></tr>
 * <tr><td>topic/* /event/dead</td><td>Captures if a topic is destroyed (on all topics)</td></tr>
 * <tr><td>topic/hello/event/dead</td><td>Captures event if the topic 'hello' is destroyed</td></tr>
 * <tr><td>client/[subjectId]/session/[publicSessionId]/event/callbackState</td><td>Captures event if the client callback server goes to ALIVE or POLLING. Note that the status change to DEAD is currently not implemented (it is reported as POLLING). Wildcards are not supported.</td></tr>
 * <tr><td>heartbeat.360000</td><td>Sends a heartbeat notification every given milli seconds. Setting <code>heartbeat</code> defaults to one notification per day (86400000 millis).</td></tr>
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
      I_LogListener, I_SubscriptionListener, I_TopicListener,
      I_ConnectionStatusListener, I_RemotePropertiesListener, I_EventDispatcher, Comparable {
   private final static String ME = EventPlugin.class.getName();

   private static Logger log = Logger.getLogger(EventPlugin.class.getName());

   /** My JMX registration */
   private Object mbeanHandle;

   private ContextNode contextNode;

   protected Global glob;

   protected I_PluginConfig pluginConfig;

   protected org.xmlBlaster.engine.ServerScope engineGlob;

   protected RequestBroker requestBroker;

   protected SessionInfo sessionInfo;

   protected boolean isActive;

   protected boolean isShutdown;

   protected String eventTypes;
   protected Set loggingSet;
   protected Set runlevelSet;
   protected Set clientSet;
   protected Set topicSet;

   protected Set pendingCallbackSessionInfoSet;
   protected Set callbackSessionStateSet;

   protected static int staticInstanceCounter;
   protected int instanceCounter;

   private String uniqueInstanceName;
   
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
            this.contentTemplate = "eventType:   $_{eventType}\ninstanceId:  $_{instanceId}\n\nsummary:     $_{summary}\ndescription: $_{description}\n\neventDate:   $_{datetime}\nversionInfo: $_{versionInfo}\n\n--\nhttp://www.xmlblaster.org/xmlBlaster/doc/requirements/admin.events.html";

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

   protected long heartbeatInterval;
   protected Timeout heartbeatTimeout;
   protected Timestamp heartbeatTimeoutHandle;

   /**
    * Helper class to publish messages.
    */
   class PublishDestinationHelper {
      String destination;
      String key, qos, keyOid;
      String contentTemplate;
      public PublishDestinationHelper(String destination) throws XmlBlasterException {
         Map map = StringPairTokenizer.parseLineToProperties(destination);
         if (map.containsKey("publish.key")) {
            this.key = (String) map.get("publish.key");
            MsgKeyData msgKey = engineGlob.getMsgKeyFactory().readObject(this.key);
            this.keyOid = msgKey.getOid();
         }
         if (map.containsKey("publish.qos"))
            this.qos = (String) map.get("publish.qos");
         if (map.containsKey("publish.content"))
            this.contentTemplate = (String) map.get("publish.content");
         else
            this.contentTemplate = "$_{eventType}";
      }
      String getKeyOid() {
         return this.keyOid;
      }
      MsgKeyData getPublishKey(String summary, String description,
            String eventType, String errorCode) throws XmlBlasterException {
         if (this.key != null) {
            return engineGlob.getMsgKeyFactory().readObject(this.key);
         }
         //PublishKey publishKey = new PublishKey(engineGlob, Constants.EVENT_OID_LOGIN/*"__sys__Login"*/, "text/plain");
         // TODO: invent an oid depending on the eventType:
         PublishKey publishKey = new PublishKey(engineGlob, "__sys__Event", "text/plain", "1.0");
         publishKey.setClientTags("<org.xmlBlaster><event/></org.xmlBlaster>");
         return publishKey.getData();
      }
      MsgQosData getPublishQos(String summary, String description,
            String eventType, String errorCode, SessionName sessionName) throws XmlBlasterException {
         MsgQosData msgQosData = null;
         if (this.qos != null) {
            msgQosData = engineGlob.getMsgQosFactory().readObject(this.qos);
         }
         else {
            PublishQos publishQos = new PublishQos(engineGlob);
            publishQos.setLifeTime(-1L);
            publishQos.setForceUpdate(true);
            // TODO: Configure history depth to 0 only on first publish
            TopicProperty topicProperty = new TopicProperty(engineGlob);
            HistoryQueueProperty historyQueueProperty = new HistoryQueueProperty(engineGlob, engineGlob.getId());
            historyQueueProperty.setMaxEntriesCache(2);
            historyQueueProperty.setMaxEntries(2);
            topicProperty.setHistoryQueueProperty(historyQueueProperty);
            publishQos.setTopicProperty(topicProperty);
            msgQosData = publishQos.getData();
         }
         if (summary != null && summary.length() > 0)
            msgQosData.addClientProperty(Constants.EVENTPLUGIN_PROP_SUMMARY, summary); // "_summary"
         if (description != null && description.length() > 0)
            msgQosData.addClientProperty(Constants.EVENTPLUGIN_PROP_DESCRIPTION, description);
         if (eventType != null && eventType.length() > 0)
            msgQosData.addClientProperty(Constants.EVENTPLUGIN_PROP_EVENTTYPE, eventType);
         if (errorCode != null && errorCode.length() > 0)
            msgQosData.addClientProperty(Constants.EVENTPLUGIN_PROP_ERRORCODE, errorCode);
         if (sessionName != null) {
            msgQosData.addClientProperty(Constants.EVENTPLUGIN_PROP_PUBSESSIONID,
                  sessionName.getPublicSessionId());
            msgQosData.addClientProperty(Constants.EVENTPLUGIN_PROP_SUBJECTID,
                  sessionName.getLoginName());
            msgQosData.addClientProperty(Constants.EVENTPLUGIN_PROP_ABSOLUTENAME,
                  sessionName.getAbsoluteName());
            /*
            // To be backwards compatible with loginEvent=true setting:
            // deprecated:
            msgQosData.addClientProperty("__publicSessionId",
                  sessionName.getPublicSessionId());
            msgQosData.addClientProperty("__subjectId",
                  sessionName.getLoginName());
            msgQosData.addClientProperty("__absoluteName",
                  sessionName.getAbsoluteName());
            // TODO: backwards compatible?
            //msgUnit.setContent(sessionName.getLoginName().getBytes());
            // To be backwards compatible with loginEvent=true setting:
            */
         }
         msgQosData.addClientProperty(Constants.EVENTPLUGIN_PROP_NODEID, engineGlob.getId());

         return msgQosData;
      }
      MsgUnit getMsgUnit(String summary, String description,
            String eventType, String errorCode, SessionName sessionName) throws XmlBlasterException {
         String content = replaceTokens(
            this.contentTemplate, summary, description, eventType, errorCode);
         return new MsgUnit(
               getPublishKey(summary, description, eventType, errorCode),
               content.getBytes(),
               getPublishQos(summary, description, eventType, errorCode, sessionName));

      }
   }
   protected PublishDestinationHelper publishDestinationHelper;
   protected String publishDestinationConfiguration;

   /**
    * Helper class to send a JMX notification.
    */
   class JmxDestinationHelper {
      String destination;
      String contentTemplate;
      public JmxDestinationHelper(String destination) throws XmlBlasterException {
         Map map = StringPairTokenizer.parseLineToProperties(destination);
         if (map.containsKey("jmx.content"))
            this.contentTemplate = (String) map.get("jmx.content");
         else
            this.contentTemplate = "$_{eventType}: $_{summary}";
      }
      String getMessage(String summary, String description,
            String eventType, String errorCode, SessionName sessionName) throws XmlBlasterException {
         String content = replaceTokens(
            this.contentTemplate, summary, description, eventType, errorCode);
         return content;

      }
   }
   protected JmxDestinationHelper jmxDestinationHelper;
   protected String jmxDestinationConfiguration;

   public EventPlugin() {
      synchronized (EventPlugin.class) {
         staticInstanceCounter++;
         this.instanceCounter = staticInstanceCounter;
         log.fine("instance #" + instanceCounter + " created");
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
      this.uniqueInstanceName = pluginInfo.getId() + "-" + instanceCounter;
      this.glob = utilGlob.getClone(utilGlob.getNativeConnectArgs());
      this.glob.addObjectEntry(Constants.OBJECT_ENTRY_ServerScope, utilGlob // "ServerNodeScope"
            .getObjectEntry(Constants.OBJECT_ENTRY_ServerScope));

      this.engineGlob = (org.xmlBlaster.engine.ServerScope) utilGlob
            .getObjectEntry(Constants.OBJECT_ENTRY_ServerScope);
      this.requestBroker = engineGlob.getRequestBroker();
      this.sessionInfo = requestBroker.getInternalSessionInfo();

      // For JMX instanceName may not contain ","
      this.contextNode = new ContextNode(ContextNode.SERVICE_MARKER_TAG,
            "EventPlugin[" + getType() + "]", this.engineGlob.getScopeContextNode());
      this.mbeanHandle = this.engineGlob.registerMBean(this.contextNode, this);

      this.eventTypes = this.glob.get("eventTypes", "", null,
            this.pluginConfig);
      if (this.eventTypes == null || this.eventTypes.trim().length() == 0) {
         log.warning("Please configure an attribute 'eventTypes', there is nothing to do for us.");
         return;
      }
      this.eventTypes = this.eventTypes.trim();

      StringBuffer destLogStr = new StringBuffer(1024);

      // Sending the events with email?
      this.smtpDestinationConfiguration = this.glob.get("destination.smtp", "", null,
            this.pluginConfig);
      if (this.smtpDestinationConfiguration != null && this.smtpDestinationConfiguration.trim().length() > 0) {
         this.smtpDestinationConfiguration = this.smtpDestinationConfiguration.trim();
         setupSmtpSink(this.smtpDestinationConfiguration);
         if (destLogStr.length() > 0) destLogStr.append(",");
         destLogStr.append("destination.smtp");
         if (this.smtpDestinationConfiguration.length() > 0)
            destLogStr.append("(").append(this.smtpDestinationConfiguration).append(")");
      }

      // Sending the events with publish()?
      this.publishDestinationConfiguration = this.glob.get("destination.publish", (String)null, null,
            this.pluginConfig);
      if (this.publishDestinationConfiguration != null) {
         this.publishDestinationConfiguration = this.publishDestinationConfiguration.trim();
         this.publishDestinationHelper = new PublishDestinationHelper(this.publishDestinationConfiguration);
         if (destLogStr.length() > 0) destLogStr.append(",");
         destLogStr.append("destination.publish");
         if (this.publishDestinationConfiguration.length() > 0)
            destLogStr.append("(").append(this.publishDestinationConfiguration).append(")");
         if (this.eventTypes.indexOf("logging/severe/*") != -1 || this.eventTypes.indexOf("log/error/*") != -1)
            log.warning("The combination of 'destination.publish' with 'logging/severe/*' is dangerous as it could loop forever, it is supressed.");
         else if (this.eventTypes.indexOf("logging/severe/") != -1 || this.eventTypes.indexOf("log/error/") != -1)
            log.warning("The combination of 'destination.publish' with 'logging/severe/xyz' is dangerous as it could loop forever.");
         if (this.eventTypes.indexOf("logging/warning") != -1 || this.eventTypes.indexOf("logging/warn") != -1)
            log.warning("The combination of 'destination.publish' with 'logging/warning' is very dangerous as it could loop forever, it is supressed.");
      }

      // Sending the events as a JMX notification?
      this.jmxDestinationConfiguration = this.glob.get("destination.jmx", (String)null, null,
            this.pluginConfig);
      if (this.jmxDestinationConfiguration != null) {
         this.jmxDestinationConfiguration = this.jmxDestinationConfiguration.trim();
         this.jmxDestinationHelper = new JmxDestinationHelper(this.jmxDestinationConfiguration);
         if (destLogStr.length() > 0) destLogStr.append(",");
         destLogStr.append("destination.jmx");
         if (this.jmxDestinationConfiguration.length() > 0)
            destLogStr.append("(").append(this.jmxDestinationConfiguration).append(")");
      }

      if (destLogStr.length() < 1) {
         log.warning("Please configure a data sink attribute 'destination.*', there is nothing to do for us.");
         return;
      }

      this.isActive = true;

      registerEventTypes(this.eventTypes);

      log.info("Configured to send core events of type '" + this.eventTypes.trim()
            + "' to '" + destLogStr.toString() + "'");
   } // init()

   /**
    * Find out which events to listen.
    * @param eventTypes A commas seperated list of supported events, e.g. <code>logging/severe/*,logging/warning/*</code>
    */
   private void registerEventTypes(String eventTypes) throws XmlBlasterException {
      String[] eventTypeArr = StringPairTokenizer.parseLine(eventTypes);

      ServerScope serverScope = requestBroker.getServerScope();
      QueueEventHandler queueEventHandler = null;
      MapEventHandler mapEventHandler = null;

      for (int i = 0; i < eventTypeArr.length; i++) {
         String event = eventTypeArr[i].trim();
         if (event.length() < 1) continue; // Allow ',' at end

         try {
            // "logging/severe/*"
            // TODO: support specific channels as "logging/severe/*" -> "logging/severe/core"
            if (event.startsWith(ContextNode.LOGGING_MARKER_TAG+"/severe/")
                  || event.startsWith("logging/error/")) {
               // We want to be notified if a log.error() is called, this will
               // notify our LogableDevice.log() method
               XbNotifyHandler.instance().register(Level.SEVERE.intValue(), this);
               if (this.loggingSet == null) this.loggingSet = new TreeSet();
               this.loggingSet.add(event);
            }
            // "logging/warning/*"
            else if (event.startsWith(ContextNode.LOGGING_MARKER_TAG+"/warning/")
                  || event.startsWith("logging/warn/")) {
               XbNotifyHandler.instance().register(Level.WARNING.intValue(), this);
               if (this.loggingSet == null) this.loggingSet = new TreeSet();
               this.loggingSet.add(event);
            }
            // "service/RunlevelManager/event/startupRunlevel8", "service/RunlevelManager/event/shutdownRunlevel7"
            else if (event.startsWith(this.engineGlob.getRunlevelManager().getContextNode().getRelativeName()+"/event/")) {
               log.fine("Register event = " + event);
               this.engineGlob.getRunlevelManager().addRunlevelListener(this);
               if (this.runlevelSet == null) this.runlevelSet = new TreeSet();
               this.runlevelSet.add(event);
            }
            else if (isCallbackStateEvent(event)) {
            //else if (event.endsWith("/event/callbackState") || event.endsWith("/event/callbackAlive") || event.endsWith("/event/callbackPolling") || event.endsWith("/event/callbackDead")) {
               // OK: "client/joe/session/1/event/callbackState"
               // Not yet supported: "client/joe/session/1/event/callbackAlive", "client/joe/session/1/event/callbackPolling"
               int index = event.lastIndexOf("/event/");
               String name = event.substring(0, index);
               SessionName sessionName = new SessionName(this.engineGlob, name);
               this.requestBroker.getAuthenticate().addClientListener(this);
               if (this.callbackSessionStateSet == null) this.callbackSessionStateSet = new TreeSet();
               if (event.startsWith(ContextNode.SUBJECT_MARKER_TAG+ContextNode.SEP+"*"+ContextNode.SEP) ||
                   event.endsWith(ContextNode.SESSION_MARKER_TAG+ContextNode.SEP+"*"+"/event/callbackState")) {
                  // "client/*/session/1/event/callbackState" or "client/joe/session/*/event/callbackState"
                  if (this.pendingCallbackSessionInfoSet == null) this.pendingCallbackSessionInfoSet = new TreeSet();
                  this.pendingCallbackSessionInfoSet.add(name);
                  this.callbackSessionStateSet.add(name);
                  SubjectInfo[] subs = null;
                  log.fine("Register existing wildcard callback session state event = " + event);
                  if (event.startsWith(ContextNode.SUBJECT_MARKER_TAG+ContextNode.SEP+"*"+ContextNode.SEP) &&
                		  !event.endsWith(ContextNode.SESSION_MARKER_TAG+ContextNode.SEP+"*"+"/event/callbackState")) {
	                  subs = this.requestBroker.getAuthenticate().getSubjectInfoArr();
	                  for (int sj=0; sj<subs.length; sj++) {
	                	  SubjectInfo subjectInfo = subs[sj];
	                	  if (!wildcardMatch(sessionName.getLoginName(), subjectInfo.getLoginName()))
                			  continue;
	                	  SessionInfo[] ses = subjectInfo.getSessions();
	                	  for (int se=0; se<ses.length; se++) {
	                		  if (!wildcardMatch(sessionName.getPublicSessionId(), ses[se].getPublicSessionId()))
	                			  continue;
	                		  DispatchManager mgr = ses[se].getDispatchManager();
	                          if (mgr != null) {
	                              mgr.addConnectionStatusListener(this);
	                           }
	                	  }
	                  }
                  }
               }
               else {
                  log.fine("Register callback session state event = " + event);
                  SessionInfo sessionInfo = this.requestBroker.getAuthenticate().getSessionInfo(sessionName);
                  DispatchManager mgr = null;
                  if (sessionInfo != null)
                     mgr = sessionInfo.getDispatchManager();
                  if (mgr != null) {
                     mgr.addConnectionStatusListener(this);
                  }
                  else {
                     if (this.pendingCallbackSessionInfoSet == null) this.pendingCallbackSessionInfoSet = new TreeSet();
                     this.pendingCallbackSessionInfoSet.add(sessionName.getAbsoluteName());
                  }
                  this.callbackSessionStateSet.add(sessionName.getRelativeName());
               }
            }
            else if (isQueueEvent(event)) {
               if (queueEventHandler == null) {
                  queueEventHandler = new QueueEventHandler(serverScope, this);
               }
               queueEventHandler.registerEventType(this, event);
            }
            else if (isPersistenceEvent(event)) {
               if (mapEventHandler == null) {
                  mapEventHandler = new MapEventHandler(serverScope, this);
               }
               mapEventHandler.registerEventType(this, event);
            }
            else if (event.startsWith(ContextNode.SUBJECT_MARKER_TAG+ContextNode.SEP)) {
               // REGEX: "client/.*/session/.*/event/.*"
               // "client/joe/session/1/event/connect", "client/*/session/*/event/disconnect"
               // "client/joe/session/1/event/subscribe"
               log.fine("Register login/logout event = " + event);
               if (event.endsWith("/event/subscribe") || event.endsWith("/event/unSubscribe"))
                  this.requestBroker.addSubscriptionListener(this);
               else if (event.endsWith("/event/remoteProperties"))
                  this.requestBroker.addRemotePropertiesListener(this); // I_RemotePropertiesListener
               else
                  this.requestBroker.getAuthenticate().addClientListener(this);
               if (this.clientSet == null) this.clientSet = new TreeSet();
               this.clientSet.add(event);
            }
            else if (event.startsWith(ContextNode.TOPIC_MARKER_TAG+ContextNode.SEP)) {
               // "topic/hello/event/alive", "topic/hello/event/subscribe" ...
               log.fine("Register topic event = " + event);
               if (event.endsWith("/event/subscribe") || event.endsWith("/event/unSubscribe"))
                  this.requestBroker.addSubscriptionListener(this);
               else
                  this.engineGlob.getTopicAccessor().addTopicListener(this);
               if (this.topicSet == null) this.topicSet = new TreeSet();
               this.topicSet.add(event);
            }
            else if (event.startsWith("heartbeat")) {
               // "heartbeat.360000
               log.fine("Register heartbeat event = " + event);
               int index = event.indexOf(".");
               if (index > 0 && index < event.length()-1)
                  this.heartbeatInterval = Long.valueOf(event.substring(index+1)).longValue();
               else
                  this.heartbeatInterval = Constants.DAY_IN_MILLIS;
               if (this.heartbeatInterval > 0) {
                  // send the first heartbeat directly after startup:
                  long initialInterval = (this.heartbeatInterval > 2000) ? 2000L : this.heartbeatInterval;
                  this.heartbeatTimeout = new Timeout("EventPlugin-HeartbeatTimer");
                  this.heartbeatTimeoutHandle = this.heartbeatTimeout.addTimeoutListener(new I_Timeout() {
                     public void timeout(Object userData) {
                        log.fine("Timeout happened " + userData + ": Sending now heartbeat");
                        newHeartbeatNotification((String)userData);
                        try {
                           heartbeatTimeout.addOrRefreshTimeoutListener(this, heartbeatInterval, userData, heartbeatTimeoutHandle);
                        }
                        catch (XmlBlasterException e) {
                           e.printStackTrace();
                        }

                     }
                   }, initialInterval, event);
               }
            }
            else {
               log.warning("Ignoring unknown '" + event
                     + "' from eventTypes='" + eventTypes + "'");
            }
         }
         catch (Throwable e) {
            e.printStackTrace();
            log.warning("Ignoring '" + event
                     + "' from eventTypes='" + eventTypes + "' because of " + e.toString());
         }
      }

      if (queueEventHandler != null)
         if (serverScope.getQueuePluginManager().setEventHandler(uniqueInstanceName, queueEventHandler) == false) {
         log.severe(ME+getType() + " Can't register queue threshold event, max one such event can be registered for '" + uniqueInstanceName + "'");
         }
      if (mapEventHandler != null)
         if (serverScope.getStoragePluginManager().setEventHandler(uniqueInstanceName, mapEventHandler) == false) {
            log.severe(ME+getType() + " Can't register msgUnitStore event, max one such event can be registered '" + uniqueInstanceName + "'");
         }
   }

   public static boolean isQueueEvent(String txt) {
      return matchesRegex(".*/queue/.*/event/threshold.*", txt);
   }

   public static boolean isPersistenceEvent(String txt) {
      return matchesRegex(".*/persistence/.*/event/threshold.*", txt);
   }

   public static boolean isCallbackStateEvent(String txt) {
	   return matchesRegex("client/.*/session/.*/event/callbackState", txt);
   }

	public final boolean isWildcard(String pattern) {
		return "*".equals(pattern);
	}

	public final boolean wildcardMatch(String pattern, String name) {
		if ("*".equals(pattern))
			return true;
		return pattern.equals(name);
	}

	/** SessionName.java parsed "client/joe/session/*" to pubSessionId=Long.MIN_VALUE */
	public final boolean wildcardMatch(long pattern, long pubSessionId) {
		if (pattern == Long.MIN_VALUE)
			return true;
		return pattern == pubSessionId;
	}

   private static boolean matchesRegex(String pattern, String txt) {
      try {
         RE regex = new RE(pattern, RE.REG_ICASE);
         return regex.isMatch(txt);
      }
      catch (REException ex) {
         ex.printStackTrace();
         return false;
      }
   }

   /**
    * Called when a client sends his remote properties, for example client side errors.
    * eventType == client/* /session/* /event/remoteProperties
    * Enforced by I_RemotePropertiesListener
    */
   public void update(SessionInfo sessionInfo, Map remoteProperties) {
      log.fine("Received new remote properties from client " + sessionInfo.getId());
      SessionName sessionName = sessionInfo.getSessionName();
      String relativeName = sessionName.getRelativeName();

      String event = ContextNode.SEP + ContextNode.EVENT_MARKER_TAG + ContextNode.SEP + "remoteProperties";
      String foundEvent = relativeName + event;  // "client/joe/session/1/event/remoteProperties"
      if (!this.clientSet.contains(foundEvent)) {
         // "client/joe/session/*/event/remoteProperties"
         foundEvent = ContextNode.SUBJECT_MARKER_TAG + ContextNode.SEP + sessionName.getLoginName() + ContextNode.SEP + ContextNode.SESSION_MARKER_TAG + ContextNode.SEP + "*" + event;
         if (!this.clientSet.contains(foundEvent)) {
            // "client/*/session/*/event/remoteProperties"
            foundEvent = ContextNode.SUBJECT_MARKER_TAG + ContextNode.SEP + "*" + ContextNode.SEP + ContextNode.SESSION_MARKER_TAG + ContextNode.SEP + "*" + event;
            if (!this.clientSet.contains(foundEvent)) {
               return;
            }
         }
      }

      try {
         String summary = "Remote properties change for client " + sessionName.getAbsoluteName();
         String description = summary;
         ClientProperty[] cp = sessionInfo.getRemotePropertyArr();
         for (int i=0; i<cp.length; i++)
            description += "\n   " + cp[i].toXml("", "remoteProperty").trim();
         String eventType = foundEvent;
         String errorCode = null;

         if (this.smtpDestinationHelper != null) {
            sendEmail(summary, description, eventType, null, false);
         }

         if (this.publishDestinationHelper != null) {
            sendMessage(summary, description,
                    eventType, errorCode, sessionName, cp);
         }

         if (this.jmxDestinationHelper != null) {
            sendJmxNotification(summary, description, eventType, null, false);
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }

   }

   /**
    * Send a heartbeat message/notification.
    * Called by timeout or by manual trigger (e.g. over jconsole)
    * @param eventType
    */
   protected void newHeartbeatNotification(String eventType) {
      try {
         ContextNode contextNode = this.engineGlob.getContextNode();
         int rl = this.engineGlob.getRunlevelManager().getCurrentRunlevel();
         String summary = "Heartbeat event from " + contextNode.getAbsoluteName() + ", runlevel=" + RunlevelManager.toRunlevelStr(rl) + " (" + rl + ")";
         String errorCode = null;
         String description = "Heartbeat event from " + contextNode.getAbsoluteName() + ", runlevel=" + RunlevelManager.toRunlevelStr(rl) + " (" + rl + ")";
         SessionName sessionName = null;

         if (this.smtpDestinationHelper != null) {
            // Ignores contentTemplate and forces the XML as last argument
            sendEmail(summary, description, eventType, null, false);
         }

         if (this.publishDestinationHelper != null) {
            // Uses XML as message content
            sendMessage(summary, description, eventType, errorCode, sessionName, null);
         }

         if (this.jmxDestinationHelper != null) {
            sendJmxNotification(summary, description, eventType, null, false);
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.engine.EventPluginMBean#triggerHeartbeatNotification()
    */
   public String triggerHeartbeatNotification() {
      String eventType = "heartbeat.manuallyTriggered";
      newHeartbeatNotification(eventType);
      return "Triggered event of type '" + eventType + "'";
   }

   /**
    * Create an XML xmlBlaster dump which contains the most important status informations.
    * Follows the admin.commands markup (without the root tag &lt;xmlBlaster>
    * @param g The global of the running server instance
    * @return The XML dump
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.commands.html">The admin.commands requirement</a>
    */
   public static String createStatusDump(org.xmlBlaster.engine.ServerScope g,
         String summary, String description,
         String eventType, String errorCode) {
      // Change to be configurable with ${amdincommands} replacements
      RequestBroker r = g.getRequestBroker();
      XmlBuffer buf = new XmlBuffer(10000);
      //buf.append("\n").append("<xmlBlaster>"); // Root tag not added, so we easily can collect different nodes to a big xml dump
      buf.append("\n ").append("<node id='").appendEscaped(g.getId()).append("'>");
      if (summary != null)
      buf.append("\n  ").append("<_summary>").appendEscaped(summary).append("</_summary>");
      if (description != null)
      buf.append("\n  ").append("<_description>").appendEscaped(description).append("</_description>");
      if (eventType != null)
      buf.append("\n  ").append("<_eventType>").append(eventType).append("</_eventType>");
      if (errorCode != null)
         buf.append("\n  ").append("<_errorCode>").append(errorCode).append("</_errorCode>");
      buf.append("\n  ").append("<uptime>").append(r.getUptime()).append("</uptime>");
      buf.append("\n  ").append("<runlevel>").append(g.getRunlevelManager().getCurrentRunlevel()).append("</runlevel>");
      buf.append("\n  ").append("<instanceId>").appendEscaped(g.getInstanceId()).append("</instanceId>");
      buf.append("\n  ").append("<version>").append(g.getVersion()).append("</version>");
      buf.append("\n  ").append("<revisionNumber>").append(g.getRevisionNumber()).append("</revisionNumber>");
      buf.append("\n  ").append("<freeMem>").append(r.getFreeMem()).append("</freeMem>");
      buf.append("\n  ").append("<maxFreeMem>").append(r.getMaxFreeMem()).append("</maxFreeMem>");
      buf.append("\n  ").append("<maxMem>").append(r.getMaxMem()).append("</maxMem>");
      buf.append("\n  ").append("<usedMem>").append(r.getUsedMem()).append("</usedMem>");
      buf.append("\n  ").append("<serverTimestamp>").append(new java.sql.Timestamp(new java.util.Date().getTime()).toString()).append("</serverTimestamp>");
      buf.append("\n  ").append("<numClients>").append(r.getNumClients()).append("</numClients>");
      buf.append("\n  ").append("<clientList>").appendEscaped(r.getClientList()).append("</clientList>");
      SubjectInfo[] clients = r.getAuthenticate().getSubjectInfoArr();
      for (int c=0; c<clients.length; c++) {
         SubjectInfo subjectInfo = clients[c];
         if (subjectInfo.getLoginName().startsWith("__")) continue;// Ignore internal sessions
         buf.append("\n  ").append("<client id='").appendEscaped(subjectInfo.getLoginName()).append("'>");
         SessionInfo[] sessions = subjectInfo.getSessions();
         for (int s=0; s<sessions.length; s++) {
            SessionInfo sessionInfo = sessions[s];
            buf.append("\n   ").append("<session id='").append(sessionInfo.getPublicSessionId()).append("'>");
            buf.append("\n    ").append("<state>").append(sessionInfo.getConnectionState()).append("</state>");
            ClientProperty[] props = sessionInfo.getRemotePropertyArr();
            for (int p=0; p<props.length; p++)
               buf.append(props[p].toXml("   ", "remoteProperty", true));
            buf.append("\n   ").append("</session>");
         }
         buf.append("\n  ").append("</client>");
      }
      buf.append("\n  ").append("<numTopics>").append(r.getNumTopics()).append("</numTopics>");
      buf.append("\n  ").append("<topicList>").appendEscaped(r.getTopicList()).append("</topicList>");
      buf.append("\n  ").append("<numGet>").append(r.getNumGet()).append("</numGet>");
      buf.append("\n  ").append("<numPublish>").append(r.getNumPublish()).append("</numPublish>");
      buf.append("\n  ").append("<numUpdate>").append(r.getNumUpdate()).append("</numUpdate>");
      // " encoding='base64'" if string contains CDATA?
      String warning = ReplaceVariable.replaceAll(r.getLastWarning(), "<![CDATA[", "&lt;![CDATA[");
      warning = ReplaceVariable.replaceAll(warning, "]]>", "]]&gt;");
      String error = ReplaceVariable.replaceAll(r.getLastError(), "<![CDATA[", "&lt;![CDATA[");
      error = ReplaceVariable.replaceAll(error, "]]>", "]]&gt;");
      buf.append("\n  ").append("<lastWarning><![CDATA[").append(warning).append("]]></lastWarning>");
      buf.append("\n  ").append("<lastError><![CDATA[").append(error).append("]]></lastError>");
      XmlBlasterException e = new XmlBlasterException(g,
            ErrorCode.COMMUNICATION_NOCONNECTION, ME, "");
      buf.append("\n  ").append("<versionInfo><![CDATA[").appendEscaped(e.getVersionInfo()).append("]]></versionInfo>");
      buf.append("\n  ").append("<see>").append("http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.events.html").append("</see>");
      buf.append("\n ").append("</node>");
      //buf.append("\n").append("</xmlBlaster>");
      return buf.toString();
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
   protected String replaceTokens(String str, String summary, String description, String eventType, String errorCode) {
      if (str == null || str.indexOf("$") == -1)
         return str;
      str = ReplaceVariable.replaceAll(str, "$_{datetime}",
            new java.sql.Timestamp(new java.util.Date().getTime()).toString());
      str = ReplaceVariable.replaceAll(str, "$_{summary}", (summary==null)?"":summary);
      str = ReplaceVariable.replaceAll(str, "$_{description}", (description==null)?"":description);
      str = ReplaceVariable.replaceAll(str, "$_{instanceId}", this.engineGlob.getInstanceId()); // "/xmlBlaster/node/heron/instanceId/1136220586692"
      str = ReplaceVariable.replaceAll(str, "$_{nodeId}", this.engineGlob.getId()); // "heron"
      str = ReplaceVariable.replaceAll(str, "$_{id}", this.engineGlob.getId());  // "heron"
      str = ReplaceVariable.replaceAll(str, "$_{eventType}", (eventType==null)?"":eventType);
      str = ReplaceVariable.replaceAll(str, "$_{errorCode}", (errorCode==null)?"":errorCode);
      if (str.indexOf("$_{clientList}") != -1) { // To support backward compatibility with "userListEvent=true" __sys__UserList
         str = ReplaceVariable.replaceAll(str, "$_{clientList}", this.requestBroker.getClientList());
      }
      if (str.indexOf("$_{versionInfo}") != -1) {
         XmlBlasterException e = new XmlBlasterException(this.engineGlob,
            ErrorCode.COMMUNICATION_NOCONNECTION, ME, "");
         str = ReplaceVariable.replaceAll(str, "$_{versionInfo}", e.getVersionInfo());
      }
      if (str.indexOf("$_{xml}") != -1) {
         str = ReplaceVariable.replaceAll(str, "$_{xml}", createStatusDump(this.engineGlob, summary, description,
            eventType, errorCode));
         return str;
      }
      // TODO: Support admin commands or JMX calls like
      // $_{org.xmlBlaster:nodeClass=node,node="heron"/action=getFreeMemStr'}
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
      // we shut down the resources for the queue and storage events
      ServerScope serverScope = requestBroker.getServerScope();
      StoragePluginManager storagePluginManager = serverScope.getStoragePluginManager();
      StorageEventHandler mapEventHandler = storagePluginManager.getEventHandler(uniqueInstanceName);
      if (mapEventHandler != null) {
         storagePluginManager.setEventHandler(uniqueInstanceName, null);
         mapEventHandler.unRegisterEventHelpers(this);
      }

      QueuePluginManager queuePluginManager = serverScope.getQueuePluginManager();
      StorageEventHandler queueEventHandler = queuePluginManager.getEventHandler(uniqueInstanceName);
      if (queueEventHandler != null) {
         queueEventHandler.unRegisterEventHelpers(this);
         queuePluginManager.setEventHandler(uniqueInstanceName, null);
      }

      if (this.loggingSet != null)
         this.loggingSet = null;

      XbNotifyHandler hh = XbNotifyHandler.instance();
      hh.unregister(Level.WARNING.intValue(), this);
      hh.unregister(Level.SEVERE.intValue(), this);

      if (this.engineGlob != null && this.mbeanHandle != null)
         this.engineGlob.unregisterMBean(this.mbeanHandle);

      if (this.runlevelSet != null)
         this.runlevelSet = null;
      if (this.engineGlob != null) {
         this.engineGlob.getRunlevelManager().removeRunlevelListener(this);
      }

      if (this.clientSet != null)
         this.clientSet = null;
      if (this.requestBroker != null)
         this.requestBroker.getAuthenticate().removeClientListener(this);

      this.requestBroker.removeRemotePropertiesListener(this);
      /*
      if (this.subscribeSet != null)
         this.subscribeSet = null;
      if (this.unSubscribeSet != null)
         this.unSubscribeSet = null;
      */
      this.requestBroker.removeSubscriptionListener(this);

      if (this.topicSet != null)
         this.topicSet = null;
      this.engineGlob.getTopicAccessor().removeTopicListener(this);

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
    * @see org.xmlBlaster.util.log.I_LogListener#log(LogRecord)
    */
   public void log(LogRecord record) {
      // We may not do any log.xxx() call here because of recursion!!
      //if (Logger.LOG_WARN != level && LogChannel.LOG_ERROR != level)
      //   return;

      if (record == null) return;
      if (this.loggingSet == null) return;
      Level level = record.getLevel();
      String source = record.getSourceClassName()+"."+record.getSourceMethodName();
      String str = record.getMessage();
      String found = (Level.WARNING.equals(level)) ? "logging/warning/" : "logging/severe/";
      String foundEvent = found + "*"; // "logging/warning/*"
      if (!this.loggingSet.contains(foundEvent))
         return;
      // How to extract the Logger name like "core"?

      try {
         if (source == null) source = "";
         String description = (str == null) ? "" : str;

         String summary =
             "[" + new java.sql.Timestamp(record.getMillis()).toString()
           + " " + level.toString()
           + " " + Thread.currentThread().getName()
           + " " + source + "]";

         String eventType = foundEvent;

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
            sendEmail(summary, description, eventType, errorCode, false);
         }

         /*
         if (this.publishDestinationHelper != null) {
            if (Logger.LOG_WARN == level) {
               // log.fine("Too dangerous as it could produce looping messages");
               // e.g. [WARN  XmlBlaster.DispatchWorkerPool.heron-1 RequestBroker] Generating dead message 'callback:/node/heron/client/subscribe/1/NORM/1136329068010000002/__sys__Event' from publisher=/node/heron/client/__RequestBroker_internal[heron]/1 because delivery with queue 'null' failed: XmlBlasterException errorCode=[communication.noConnection.dead] serverSideException=true location=[SOCKET-HandleClient-subscribe/1] message=[#14515:14516M update() invocation ignored, we are shutdown. : Original erroCode=communication.noConnection] [See URL http://www.xmlblaster.org/xmlBlaster/doc/requirements/admin.errorcodes.listing.html#communication.noConnection.dead]
            }
            else {
               // if (Logger.LOG_ERROR == level)
               // Should suppress as well ?
               // Lead to looping messages if during publish a log.error is done
               sendMessage(summary, description, eventType, errorCode, false);
            }
         }
         */

         if (this.jmxDestinationHelper != null) {
            sendJmxNotification(summary, description, eventType, errorCode, false);
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
         SessionName sessionName = null;
         //String message = eventType + ": " + summary; // Shows up under 'Message' in jconsole
         String message =this.jmxDestinationHelper.getMessage(summary, description,
               eventType, errorCode, sessionName);
         String attributeName = eventType;
         String oldValue = ""; // TODO what to put here?
         String newValue = eventType + ": " + summary; // Won't work if attributeName is illegal
         this.engineGlob.sendNotification(this, message,
               attributeName, "java.lang.String", oldValue, newValue);
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   /**
    * The xmlBlaster-message data sink.
    * Publishes a message with the current event occurred.
    * @param clientProperties Can be null
    * @param useEventTypeAsContent TODO
    * @see #replaceTokens(String str, String summary, String description, String eventType, String errorCode) {
    */
   protected void sendMessage(String summary, String description,
         String eventType, String errorCode, SessionName sessionName, ClientProperty[] clientProperties) {
      if (this.publishDestinationHelper == null) return;
      if (!this.isActive) return;

      try {
         MsgUnit msgUnit = this.publishDestinationHelper.getMsgUnit(summary, description,
              eventType, errorCode, sessionName);
         if (clientProperties != null)
            for (int i=0; i<clientProperties.length; i++)
               msgUnit.getQosData().addClientProperty(clientProperties[i]);
         /*
         if (clientProperties != null) {
            Iterator it = clientProperties.keySet().iterator();
            while (it.hasNext()) {
               String key = (String)it.next();
               msgUnit.getQosData().addClientProperty(key, clientProperties.get(key));
            }
         }
         */
         // Done already in getMsgUnit() above
         //msgUnit.getQosData().addClientProperty("_summary", summary);
         this.requestBroker.publish(this.sessionInfo, msgUnit);
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   /**
    * Sending email as configured with <code>destination.smtp</code>.
    * @param summary The email summary line to use, it is injected to the template as $_{summary}
    * @param description The event description to send, it is injected as $_{description}
    * @param eventType For example "logging/severe/*"
    * @param forceSending If true send directly and ignore the timeout
    * @see http://www.faqs.org/rfcs/rfc2822.html
    */
   protected void sendEmail(String summary, String description,
            String eventType, String errorCode, boolean forceSending) {
      try {
         if (this.smtpDestinationHelper == null) return;
         if (!this.isActive) return;

         if (summary == null) summary = "";
         if (description == null) description = "";

         if (forceSending) {
            EmailData emailData = this.smtpDestinationHelper.createEmailData();
            emailData.setSubject(replaceTokens(
                  this.smtpDestinationHelper.subjectTemplate, summary, description, eventType, errorCode));
            emailData.setContent(replaceTokens(
                  this.smtpDestinationHelper.contentTemplate, summary, description, eventType, errorCode));
            this.smtpDestinationHelper.smtpClient.sendEmail(emailData);
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
      } catch (Throwable e) {
         e.printStackTrace();
      }
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
      if (this.runlevelSet == null)
         return;

      // "service/RunlevelManager/event/startupRunlevel8", "service/RunlevelManager/event/shutdownRunlevel7"
      String found = this.engineGlob.getRunlevelManager().getContextNode().getRelativeName()+"/event/";
      String tmp = (to > from) ? ("startupRunlevel"+to) : ("shutdownRunlevel"+to);
      String foundEvent = found + tmp;
      if (!this.runlevelSet.contains(foundEvent)) {
         foundEvent = found + "*";
         if (!this.runlevelSet.contains(foundEvent))
            return;
      }

      try {
         String summary = null;
         String description = null;
         String eventType = null;
         if (to > from) { // startup
            summary = "Startup to " + RunlevelManager.toRunlevelStr(to) + " (" + to + ")";
            description = "xmlBlaster startup runlevel from " + RunlevelManager.toRunlevelStr(from) + " to " + RunlevelManager.toRunlevelStr(to);
            eventType = foundEvent;
         }
         if (to < from) { // shutdown
            summary = "Shutdown to " + RunlevelManager.toRunlevelStr(to) + " (" + to + ")";
            description = "xmlBlaster shutdown runlevel from " + RunlevelManager.toRunlevelStr(from) + " to " + RunlevelManager.toRunlevelStr(to);
            eventType = foundEvent;
         }

         if (eventType == null) return;

         if (this.smtpDestinationHelper != null) {
            sendEmail(summary, description, eventType, null, false);
         }

         if (this.publishDestinationHelper != null) {
            try {
               sendMessage(summary, description, eventType, null, null, null);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }

         if (this.jmxDestinationHelper != null) {
            sendJmxNotification(summary, description, eventType, null, false);
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }



   /**
    * Enforced by I_EventDispatcher
    * @param summary
    * @param description
    * @param eventType
    */public void dispatchEvent(String summary, String description, String eventType) {
      try {
         if (this.smtpDestinationHelper != null) {
            sendEmail(summary, description, eventType, null, false);
         }

         if (this.publishDestinationHelper != null) {
            try {
               sendMessage(summary, description, eventType, null, null, null);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }

         if (this.jmxDestinationHelper != null) {
            sendJmxNotification(summary, description, eventType, null, false);
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.authentication.I_ClientListener#sessionAdded(org.xmlBlaster.authentication.ClientEvent)
    */
   public void sessionAdded(ClientEvent clientEvent) throws XmlBlasterException {

      if (this.pendingCallbackSessionInfoSet != null) {
         synchronized (this.pendingCallbackSessionInfoSet) {
            try {
               SessionName currSessionName = clientEvent.getSessionInfo().getSessionName();
               String name = currSessionName.getAbsoluteName();
               boolean found = this.pendingCallbackSessionInfoSet.remove(name);
               if (!found) { // wild card entries remain
                  found = this.pendingCallbackSessionInfoSet.contains(currSessionName.getRelativeSubjectIdWildcard());
                  if (!found) {
                     found = this.pendingCallbackSessionInfoSet.contains(currSessionName.getRelativePubSessionIdWildcard());
                  }
                  if (!found) {
                     found = this.pendingCallbackSessionInfoSet.contains(currSessionName.getRelativeWildcard());
                  }
               }
               if (found) {
                  SessionName sessionName = new SessionName(this.engineGlob, name);
                  SessionInfo sessionInfo = this.requestBroker.getAuthenticate().getSessionInfo(sessionName);
                  DispatchManager mgr = null;
                  if (sessionInfo != null)
                     mgr = sessionInfo.getDispatchManager();
                  if (mgr != null) {
                     mgr.addConnectionStatusListener(this, true); // true: fire initial event
                     // done already:
                     //if (this.callbackSessionStateSet == null) this.callbackSessionStateSet = new TreeSet();
                     //this.callbackSessionStateSet.add(sessionName.getRelativeName());
                  }
                  else
                     System.err.println("EventPlugin.sessionAdded: Unexpected missing of " + name);
               }
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }
      }

      if (this.clientSet == null) return;

      SessionInfo sessionInfo = clientEvent.getSessionInfo();
      SessionName sessionName = sessionInfo.getSessionName();
      String relativeName = sessionName.getRelativeName();

      String event = ContextNode.SEP + ContextNode.EVENT_MARKER_TAG + ContextNode.SEP + "connect";
      String foundEvent = relativeName + event;  // "client/joe/session/1/event/connect"
      if (!this.clientSet.contains(foundEvent)) {
         // "client/joe/session/*/event/connect"
         foundEvent = sessionName.getRelativePubSessionIdWildcard() + event;
         if (!this.clientSet.contains(foundEvent)) {
            // "client/*/session/1/event/connect"
            foundEvent = sessionName.getRelativeSubjectIdWildcard() + event;
            if (!this.clientSet.contains(foundEvent)) {
               // "client/*/session/*/event/connect"
               foundEvent = sessionName.getRelativeWildcard() + event;
               if (!this.clientSet.contains(foundEvent)) {
                  return;
               }
            }
         }
      }

      try {
         //PublishKey(glob, Constants.EVENT_OID_LOGIN/*"__sys__Login"*/, "text/plain");
         // Key '__sys__UserList' for login/logout event
         // PublishKey(glob, Constants.EVENT_OID_USERLIST/*"__sys__UserList"*/, "text/plain");
         String summary = "Login of client " + sessionName.getAbsoluteName();
         String description = sessionInfo.toXml();
         String eventType = foundEvent;
         String errorCode = null;

         if (this.smtpDestinationHelper != null) {
            sendEmail(summary, description, eventType, null, false);
         }

         if (this.publishDestinationHelper != null) {
            sendMessage(summary, description,
                  eventType, errorCode, sessionName, sessionInfo.getRemotePropertyArr());
         }

         if (this.jmxDestinationHelper != null) {
            sendJmxNotification(summary, description, eventType, null, false);
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.authentication.I_ClientListener#sessionPreRemoved(org.xmlBlaster.authentication.ClientEvent)
    */
   public void sessionPreRemoved(ClientEvent clientEvent) throws XmlBlasterException {
      // Cleanup callbackDispatcher status listener
      if (this.pendingCallbackSessionInfoSet != null) {
         synchronized (this.pendingCallbackSessionInfoSet) {
            try {
               // Remember our event registration if client ever comes again
               String name = clientEvent.getSessionInfo().getSessionName().getAbsoluteName();
               boolean isNew = this.pendingCallbackSessionInfoSet.add(name);
               if (!isNew)
                  System.err.println("EventPlugin.sessionPreRemoved: Unexpected occurrence of " + name);

               // Remove the listener for now
               SessionName sessionName = new SessionName(this.engineGlob, name);
               SessionInfo sessionInfo = this.requestBroker.getAuthenticate().getSessionInfo(sessionName);
               DispatchManager mgr = null;
               if (sessionInfo != null)
                  mgr = sessionInfo.getDispatchManager();
               if (mgr != null) {
                  mgr.removeConnectionStatusListener(this);
                  // this.callbackSessionStateSet does not change as the client could login again
               }
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }
      }
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.authentication.I_ClientListener#sessionRemoved(org.xmlBlaster.authentication.ClientEvent)
    */
   public void sessionRemoved(ClientEvent clientEvent) throws XmlBlasterException {
      // TODO Auto-generated method stub
      //PublishKey(glob, Constants.EVENT_OID_LOGOUT/*"__sys__Logout"*/, "text/plain");
      // Key '__sys__UserList' for login/logout event
      // PublishKey(glob, Constants.EVENT_OID_USERLIST/*"__sys__UserList"*/, "text/plain");

      if (this.clientSet == null) return;

      SessionInfo sessionInfo = clientEvent.getSessionInfo();
      SessionName sessionName = sessionInfo.getSessionName();
      String relativeName = sessionName.getRelativeName();

      String event = ContextNode.SEP + ContextNode.EVENT_MARKER_TAG + ContextNode.SEP + "disconnect";
      String foundEvent = relativeName + event;  // "client/joe/session/1/event/disconnect"
      if (!this.clientSet.contains(foundEvent)) {
         // "client/joe/session/*/event/disconnect"
         foundEvent = sessionName.getRelativePubSessionIdWildcard() + event;
         if (!this.clientSet.contains(foundEvent)) {
            // "client/*/session/1/event/disconnect"
            foundEvent = sessionName.getRelativeSubjectIdWildcard() + event;
            if (!this.clientSet.contains(foundEvent)) {
               // "client/*/session/*/event/disconnect"
               foundEvent = sessionName.getRelativeWildcard() + event;
               if (!this.clientSet.contains(foundEvent)) {
                  return;
               }
            }
         }
      }

      try {
         String summary = "Logout of client " + sessionName.getAbsoluteName();
         String description = summary;
         String eventType = foundEvent;
         String errorCode = null;

         if (this.smtpDestinationHelper != null) {
            sendEmail(summary, description, eventType, null, false);
         }

         if (this.publishDestinationHelper != null) {
            sendMessage(summary, description,
                    eventType, errorCode, sessionName, sessionInfo.getRemotePropertyArr());
         }

         if (this.jmxDestinationHelper != null) {
            sendJmxNotification(summary, description, eventType, null, false);
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
            throw new XmlBlasterException(this.engineGlob,
                  ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME,
                  "Illegal email address '" + address + "': " + e.toString());
         }
      }
   }

   /**
    * JMX
    */
   public String sendTestEmail() {
      sendEmail("Test email", "Hello world :-) &<>?", "testEvent", null, true);
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
      if (this.topicSet == null && this.clientSet == null) return;

      SubscriptionInfo subscriptionInfo = subscriptionEvent.getSubscriptionInfo();
      SessionInfo sessionInfo = subscriptionInfo.getSessionInfo();
      SessionName sessionName = sessionInfo.getSessionName();
      String oid = subscriptionInfo.getKeyOid(); // is null for XPATH
      String url = subscriptionInfo.getKeyData().getUrl();

      // EXACT subscription recursion detection
      if (this.publishDestinationHelper != null && oid != null && oid.equals(this.publishDestinationHelper.getKeyOid())) {
         log.info("Ignoring subscribe event on topic '" + oid + "' from '" + sessionName.getRelativeName() + "' to avoid recursion");
         return;
      }

      // "/event/subscribe"
      String event = ContextNode.SEP + "event" + ContextNode.SEP + "subscribe";

      // "topic/hello/event/subscribe"
      String foundEvent = ContextNode.TOPIC_MARKER_TAG + ContextNode.SEP + oid + event;
      boolean found = true;
      if (this.topicSet != null) {
         if (!this.topicSet.contains(foundEvent)) {
            // "topic/*/event/subscribe"
            foundEvent = ContextNode.TOPIC_MARKER_TAG + ContextNode.SEP + "*" + event;
            if (!this.topicSet.contains(foundEvent)) {
               found = false;
            }
         }
      }
      /*
      if (found) {
         if (subscriptionInfo.isQuery() && wouldMatcheOurSysEventOid) {
            log.info("Ignoring XPATH subscribe event on topic '" + oid + "' from '" + sessionName.getRelativeName() + "' to avoid recursion");
            return;
         }
      }
      */
      if (!found) {
         if (this.clientSet == null) return;
         if (!this.clientSet.contains(foundEvent)) {
            // "client/joe/session/*/event/subscribe"
            foundEvent = ContextNode.SUBJECT_MARKER_TAG + ContextNode.SEP + sessionName.getLoginName() + ContextNode.SEP + ContextNode.SESSION_MARKER_TAG + ContextNode.SEP + "*" + event;
            if (!this.clientSet.contains(foundEvent)) {
               // "client/*/session/*/event/subscribe"
               foundEvent = ContextNode.SUBJECT_MARKER_TAG + ContextNode.SEP + "*" + ContextNode.SEP + ContextNode.SESSION_MARKER_TAG + ContextNode.SEP + "*" + event;
               if (!this.clientSet.contains(foundEvent)) {
                  return;
               }
            }
         }
      }

      try {
         String summary = "New subscription of client "
             + sessionInfo.getSessionName().getAbsoluteName()
             + " on topic " + url;
         String description = subscriptionInfo.toXml();
         String eventType = foundEvent;
         String errorCode = null;

         if (this.smtpDestinationHelper != null) {
            sendEmail(summary, description, eventType, null, false);
         }

         if (this.publishDestinationHelper != null) {
            try {
               MsgUnit msgUnit = this.publishDestinationHelper.getMsgUnit(summary, description,
                    eventType, errorCode, sessionName);
               msgUnit.getQosData().addClientProperty("_subscriptionId",
                     subscriptionInfo.getSubscriptionId());
               if (oid != null)
                  msgUnit.getQosData().addClientProperty("_oid",
                     oid);
               msgUnit.getQosData().addClientProperty("_url",
                  url);
               msgUnit.getQosData().addClientProperty("_topicId",
                     subscriptionInfo.getTopicId());
               // Add all user specific client properties
               Map props = subscriptionInfo.getSubscribeQosServer().getData().getClientProperties();
               if (props != null) {
                  Iterator it = props.keySet().iterator();
                  while (it.hasNext()) {
                     String key = (String)it.next();
                     msgUnit.getQosData().addClientProperty(key, props.get(key));
                  }
               }
               this.requestBroker.publish(this.sessionInfo, msgUnit);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }

         if (this.jmxDestinationHelper != null) {
            sendJmxNotification(summary, description, eventType, null, false);
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.engine.I_SubscriptionListener#subscriptionRemove(org.xmlBlaster.engine.SubscriptionEvent)
    */
   public void subscriptionRemove(SubscriptionEvent subscriptionEvent) throws XmlBlasterException {
      if (this.topicSet == null && this.clientSet == null) return;

      SubscriptionInfo subscriptionInfo = subscriptionEvent.getSubscriptionInfo();
      SessionInfo sessionInfo = subscriptionInfo.getSessionInfo();
      SessionName sessionName = sessionInfo.getSessionName();
      String oid = subscriptionInfo.getKeyOid(); // is null for XPATH
      String url = subscriptionInfo.getKeyData().getUrl();

      // EXACT subscription recursion detection
      if (this.publishDestinationHelper != null && oid != null && oid.equals(this.publishDestinationHelper.getKeyOid())) {
         log.info("Ignoring unSubscribe event on topic '" + oid + "' from '" + sessionName.getRelativeName() + "' to avoid recursion");
         return;
      }

      // "/event/unSubscribe"
      String event = ContextNode.SEP + "event" + ContextNode.SEP + "unSubscribe";

      // "topic/hello/event/unSubscribe"
      String foundEvent = ContextNode.TOPIC_MARKER_TAG + ContextNode.SEP + oid + event;
      boolean found = true;
      if (this.topicSet != null) {
         if (!this.topicSet.contains(foundEvent)) {
            // "topic/*/event/unSubscribe"
            foundEvent = ContextNode.TOPIC_MARKER_TAG + ContextNode.SEP + "*" + event;
            if (!this.topicSet.contains(foundEvent)) {
               found = false;
            }
         }
      }
      if (!found) {
         if (this.clientSet == null) return;
         if (!this.clientSet.contains(foundEvent)) {
            // "client/joe/session/*/event/unSubscribe"
            foundEvent = ContextNode.SUBJECT_MARKER_TAG + ContextNode.SEP + sessionName.getLoginName() + ContextNode.SEP + ContextNode.SESSION_MARKER_TAG + ContextNode.SEP + "*" + event;
            if (!this.clientSet.contains(foundEvent)) {
               // "client/*/session/*/event/unSubscribe"
               foundEvent = ContextNode.SUBJECT_MARKER_TAG + ContextNode.SEP + "*" + ContextNode.SEP + ContextNode.SESSION_MARKER_TAG + ContextNode.SEP + "*" + event;
               if (!this.clientSet.contains(foundEvent)) {
                  return;
               }
            }
         }
      }

      try {
         String summary = "unSubscribe of client "
             + sessionInfo.getSessionName().getAbsoluteName()
             + " on topic " + url;
         String description = subscriptionInfo.toXml();
         String eventType = foundEvent;
         String errorCode = null;

         if (this.smtpDestinationHelper != null) {
            sendEmail(summary, description, eventType, null, false);
         }

         if (this.publishDestinationHelper != null) {
            try {
               MsgUnit msgUnit = this.publishDestinationHelper.getMsgUnit(summary, description,
                     eventType, errorCode, sessionName);
               msgUnit.getQosData().addClientProperty("_subscriptionId",
                     subscriptionInfo.getSubscriptionId());
               if (oid != null)
                  msgUnit.getQosData().addClientProperty("_oid",
                     oid);
               msgUnit.getQosData().addClientProperty("_url",
                     url);
               msgUnit.getQosData().addClientProperty("_topicId",
                     subscriptionInfo.getTopicId());
               // Add all user specific client properties
               Map props = subscriptionInfo.getSubscribeQosServer().getData().getClientProperties();
               if (props != null) {
                  Iterator it = props.keySet().iterator();
                  while (it.hasNext()) {
                     String key = (String)it.next();
                     msgUnit.getQosData().addClientProperty(key, props.get(key));
                  }
               }
               this.requestBroker.publish(this.sessionInfo, msgUnit);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }

         if (this.jmxDestinationHelper != null) {
            sendJmxNotification(summary, description, eventType, null, false);
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.engine.EventPluginMBean#triggerTestLogSevere()
    */
   public String triggerTestLogSevere() {
      log.severe("This is a manually invoked logging output for testing purposes only");
      return "logging/severe/"+EventPlugin.class.getName()+" invoked";
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.engine.EventPluginMBean#triggerTestLogWarning()
    */
   public String triggerTestLogWarning() {
      log.warning("This is a manually invoked logging output for testing purposes only");
      return "logging/warning/"+EventPlugin.class.getName()+" invoked";
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.engine.I_TopicListener#changed(org.xmlBlaster.engine.TopicEvent)
    */
   public void changed(TopicEvent topicEvent) throws XmlBlasterException {
      if (this.topicSet == null) return;

      TopicHandler topicHandler = topicEvent.getTopicHandler();

      boolean newTopic = !topicHandler.isDead(); // UNCONFIGURED is treated as alive
      // "/event/alive", "/event/dead"
      String event = ContextNode.SEP + "event" + ContextNode.SEP + ((newTopic) ? "alive" : "dead");

      // "topic/hello/event/alive"
      String foundEvent = ContextNode.TOPIC_MARKER_TAG + ContextNode.SEP + topicHandler.getId() + event;
      if (!this.topicSet.contains(foundEvent)) {
         foundEvent = ContextNode.TOPIC_MARKER_TAG + ContextNode.SEP + "*" + event;
         if (!this.topicSet.contains(foundEvent)) {
            return;
         }
      }

      try {
         String summary = (newTopic) ? ("Creating new topic " + topicHandler.getId())
                          : ("Destroying topic " + topicHandler.getId());
         String description = topicHandler.toXml();
         String eventType = foundEvent;
         String errorCode = null;

         if (this.smtpDestinationHelper != null) {
            sendEmail(summary, description, eventType, null, false);
         }

         if (this.publishDestinationHelper != null) {
            try {
               MsgUnit msgUnit = this.publishDestinationHelper.getMsgUnit(summary, description,
                    eventType, errorCode, null);
               msgUnit.getQosData().addClientProperty("_topicId",
                     topicHandler.getId());
               this.requestBroker.publish(this.sessionInfo, msgUnit);
            } catch (Throwable e) {
               e.printStackTrace();
            }
         }

         if (this.jmxDestinationHelper != null) {
            sendJmxNotification(summary, description, eventType, null, false);
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   /* (non-Javadoc)
    * Needed so we can put this instance into a Set (to register for TopicListener).
    * @see java.lang.Comparable#compareTo(java.lang.Object)
    */
   public int compareTo(Object obj) {
        int thisVal = this.instanceCounter;
        int anotherVal = ((EventPlugin)obj).getInstanceCounter();
        return (thisVal<anotherVal ? -1 : (thisVal==anotherVal ? 0 : 1));
   }
   public boolean equals(Object obj) {
      if (obj instanceof EventPlugin) {
          return this.instanceCounter == ((EventPlugin)obj).getInstanceCounter();
      }
      return false;
   }

   /**
    * @return Returns the instanceCounter.
    */
   public int getInstanceCounter() {
      return this.instanceCounter;
   }

   protected void callbackStateChange(DispatchManager dispatchManager, ConnectionStateEnum oldState, ConnectionStateEnum newState) {
      if (this.callbackSessionStateSet == null) return;

      SessionName sessionName = dispatchManager.getSessionName();

      /*
      // Not yet implemented:
      // "/event/callbackAlive" "/event/callbackPolling"
      //String event = ContextNode.SEP + "event" + ContextNode.SEP + (newState.equals(ConnectionStateEnum.ALIVE)?"callbackAlive":"callbackPolling");

      // "client/joe/session/1/event/callbackAlive", "client/joe/session/1/event/callbackPolling"
      String foundEvent = sessionName.getRelativeName() + event;
      if (!this.callbackSessionStateSet.contains(foundEvent)) {
         // "client/joe/session/* /event/callbackAlive"
         foundEvent = ContextNode.SUBJECT_MARKER_TAG + ContextNode.SEP + sessionName.getLoginName() + ContextNode.SEP + ContextNode.SESSION_MARKER_TAG + ContextNode.SEP + "*" + event;
         if (!this.callbackSessionStateSet.contains(foundEvent)) {
            // "client/* /session/* /event/callbackAlive"
            foundEvent = ContextNode.SUBJECT_MARKER_TAG + ContextNode.SEP + "*" + ContextNode.SEP + ContextNode.SESSION_MARKER_TAG + ContextNode.SEP + "*" + event;
            if (!this.callbackSessionStateSet.contains(foundEvent)) {
               return;
            }
         }
      }
      */

      // "/event/callbackState"
      String event = ContextNode.SEP + "event" + ContextNode.SEP + "callbackState";
      // "client/joe/session/1/event/callbackState"
      String foundEvent = sessionName.getRelativeName();
      if (!this.callbackSessionStateSet.contains(foundEvent)) {
         // "client/joe/session/*"
         foundEvent = ContextNode.SUBJECT_MARKER_TAG + ContextNode.SEP + sessionName.getLoginName() + ContextNode.SEP + ContextNode.SESSION_MARKER_TAG + ContextNode.SEP + "*";
         if (!this.callbackSessionStateSet.contains(foundEvent)) {
            foundEvent = ContextNode.SUBJECT_MARKER_TAG + ContextNode.SEP + "*" + ContextNode.SEP + ContextNode.SESSION_MARKER_TAG + ContextNode.SEP + sessionName.getPublicSessionId();
            // "client/*/session/1"
            if (!this.callbackSessionStateSet.contains(foundEvent)) {
               // "client/*/session/*"
               foundEvent = ContextNode.SUBJECT_MARKER_TAG + ContextNode.SEP + "*" + ContextNode.SEP + ContextNode.SESSION_MARKER_TAG + ContextNode.SEP + "*";
               if (!this.callbackSessionStateSet.contains(foundEvent)) {
                  return;
               }
            }
         }
      }
      foundEvent += event;

      try {
         String summary = "Callback state has changed to "
             + newState.toString() + " for client "
             + sessionName.getAbsoluteName();
         String description = (oldState.equals(newState))?
               ("Callback has state changed"
               + " to "+ newState.toString() + " for client "
               + sessionName.getAbsoluteName())
               :
               ("Callback state has changed from " + oldState.toString()
               + " to "+ newState.toString() + " for client "
               + sessionName.getAbsoluteName());
         String eventType = foundEvent + " " + newState.toString();
         String errorCode = null;

         if (this.smtpDestinationHelper != null) {
            sendEmail(summary, description, eventType, null, false);
         }

         if (this.publishDestinationHelper != null) {
            ClientProperty[] clientProperties = null;
            if (newState == ConnectionStateEnum.ALIVE) {
               SessionInfo sessionInfo = this.requestBroker.getAuthenticate().getSessionInfo(sessionName);
               if (sessionInfo != null) {
                  clientProperties = sessionInfo.getRemotePropertyArr();
               }
            }
            sendMessage(summary, description, eventType, errorCode, sessionName, clientProperties);
         }

         if (this.jmxDestinationHelper != null) {
            sendJmxNotification(summary, description, eventType, null, false);
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.dispatch.I_ConnectionStatusListener#toAlive(org.xmlBlaster.util.dispatch.DispatchManager, org.xmlBlaster.util.dispatch.ConnectionStateEnum)
    */
   public void toAlive(DispatchManager dispatchManager, ConnectionStateEnum oldState) {
      callbackStateChange(dispatchManager, oldState, ConnectionStateEnum.ALIVE);
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.dispatch.I_ConnectionStatusListener#toPolling(org.xmlBlaster.util.dispatch.DispatchManager, org.xmlBlaster.util.dispatch.ConnectionStateEnum)
    */
   public void toPolling(DispatchManager dispatchManager, ConnectionStateEnum oldState) {
      callbackStateChange(dispatchManager, oldState, ConnectionStateEnum.POLLING);
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.dispatch.I_ConnectionStatusListener#toDead(org.xmlBlaster.util.dispatch.DispatchManager, org.xmlBlaster.util.dispatch.ConnectionStateEnum, java.lang.String)
    */
   public void toDead(DispatchManager dispatchManager, ConnectionStateEnum oldState, String errorText) {
      callbackStateChange(dispatchManager, oldState, ConnectionStateEnum.DEAD);
   }

   /**
    * JMX
    * @return Returns the jmxDestinationConfiguration.
    */
   public String getJmxDestinationConfiguration() {
      if (this.jmxDestinationHelper == null) return "<not active>";
      return (this.jmxDestinationConfiguration == null) ? "<default configuration>" : this.jmxDestinationConfiguration;
   }

   /**
    * JMX
    * @return Returns the publishDestinationConfiguration.
    */
   public String getPublishDestinationConfiguration() {
      if (this.publishDestinationHelper == null) return "<not active>";
      return (this.publishDestinationConfiguration == null) ? "<default configuration>" : this.publishDestinationConfiguration;
   }

   /**
    * @param jmxDestinationConfiguration The jmxDestinationConfiguration to set.
    */
   public void setJmxDestinationConfiguration(String jmxDestinationConfiguration) {
      this.jmxDestinationConfiguration = jmxDestinationConfiguration;
   }

   /**
    * @param publishDestinationConfiguration The publishDestinationConfiguration to set.
    */
   public void setPublishDestinationConfiguration(
         String publishDestinationConfiguration) {
      this.publishDestinationConfiguration = publishDestinationConfiguration;
   }

}
