/*------------------------------------------------------------------------------
 Name:      EventPlugin.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import gnu.regexp.RE;
import gnu.regexp.REException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.mail.internet.InternetAddress;
import javax.management.NotificationBroadcasterSupport;

import org.xmlBlaster.authentication.ClientEvent;
import org.xmlBlaster.authentication.I_ClientListener;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.authentication.SubjectInfo;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.XmlBlasterAccess;
import org.xmlBlaster.contrib.ClientPropertiesInfo;
import org.xmlBlaster.engine.admin.I_AdminSession;
import org.xmlBlaster.engine.cluster.ClusterManager;
import org.xmlBlaster.engine.cluster.ClusterNode;
import org.xmlBlaster.engine.dispatch.CbDispatchConnection;
import org.xmlBlaster.engine.event.ExecHelper;
import org.xmlBlaster.engine.event.JmxDestinationHelper;
import org.xmlBlaster.engine.event.PublishDestinationHelper;
import org.xmlBlaster.engine.event.SmtpDestinationHelper;
import org.xmlBlaster.engine.msgstore.MapEventHandler;
import org.xmlBlaster.engine.msgstore.StoragePluginManager;
import org.xmlBlaster.engine.runlevel.I_RunlevelListener;
import org.xmlBlaster.engine.runlevel.RunlevelManager;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_EventDispatcher;
import org.xmlBlaster.util.I_ReplaceVariable;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.StringPairTokenizer;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBuffer;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.dispatch.I_ConnectionStatusListener;
import org.xmlBlaster.util.dispatch.I_DispatchManager;
import org.xmlBlaster.util.log.I_LogListener;
import org.xmlBlaster.util.log.XbNotifyHandler;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.I_PluginConfig;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.protocol.email.EmailData;
import org.xmlBlaster.util.protocol.email.SmtpClient;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.QueueEventHandler;
import org.xmlBlaster.util.queue.QueuePluginManager;
import org.xmlBlaster.util.queue.StorageEventHandler;

/**
 * Registers for events from the xmlBlaster core and forwards them as
 * configured.
 * <p>
 * This is useful for clients or administrators to be notified on certain core
 * events.
 * </p>
 * <p>
 * This <tt>EventPlugin</tt> plugin is started with the run level manager as
 * configured in <code>xmlBlasterPlugins.xml</code>, for example:
 * </p>
 * 
 * <pre>
 * lt;plugin id='EventPlugin' className='org.xmlBlaster.engine.EventPlugin'&gt;
 *   &lt;action do='LOAD' onStartupRunlevel='7' sequence='11'
 *                        onFail='resource.configuration.pluginFailed'/&gt;
 *   &lt;action do='STOP' onShutdownRunlevel='6' sequence='11'/&gt;
 *   &lt;attribute id='eventTypes'&gt;
 *      logging/severe/*,
 *      logging/warning/*,
 *      service/RunlevelManager/event/startupRunlevel8,
 *      client/joe/session/1/event/connect
 *   &lt;/attribute&gt;
 *   &lt;attribute id='destination.smtp'&gt;
 *      mail.smtp.from=xmlBlaster@localhost,
 *      mail.smtp.to=demo@localhost,
 *      mail.collectMillis=10000
 *   &lt;/attribute&gt;
 *   &lt;attribute id='destination.jmx'/&gt;
 * lt;/plugin&gt;
 * </pre>
 * 
 * <p>
 * In the above example an email is send if any logging/severe/* (==log/error)
 * or logging/warning/* occurs. Further an event is emitted on xmlBlaster
 * startup in run level 8 and if a new client logs in. Those events are send as
 * JMX notifications as well. Adding <code>&lt;attribute id='destination.publish'/></code> would send the
 * event as a xmlBlaster message as well, but take care to not send logging
 * events as such messages will most certainly loop (if they log something they
 * will trigger another message and so forth)!
 * </p>
 * <p>
 * List of supported event sources, note that this plugin must be active on a
 * runlevel early enough depending on the event you want to capture:
 * </p>
 * <table border="1">
 * <tr>
 * <td>logging/severe/*</td>
 * <td>Captures all errors logged</td>
 * </tr>
 * <tr>
 * <td>logging/warning/*</td>
 * <td>Captures all warnings logged</td>
 * </tr>
 * <tr>
 * <td>service/RunlevelManager/event/startupRunlevel9</td>
 * <td>Captures event when startup run level reaches 9 (RUNNING), any other
 * runlevel is possible as well (note that this plugin must be active
 * beforehand)</td>
 * </tr>
 * <tr>
 * <td>service/RunlevelManager/event/shutdownRunlevel8</td>
 * <td>Captures event when shutdown runlevel reaches 8 (RUNNING_RPE), any other
 * run level is possible as well (note that this plugin must be active
 * beforehand)</td>
 * </tr>
 * <tr>
 * <td>client/* /session/* /event/connect</td>
 * <td>Captures event on client login (all clients)</td>
 * </tr>
 * <tr>
 * <td>client/[subjectId]/session/[publicSessionId]/event/connect</td>
 * <td>Captures event on given client login, e.g.
 * "client/joe/session/1/event/connect"</td>
 * </tr>
 * <tr>
 * <td>client/* /session/* /event/disconnect</td>
 * <td>Captures event on client logout (all clients)</td>
 * </tr>
 * <tr>
 * <td>client/[subjectId]/session/[publicSessionId]/event/disconnect</td>
 * <td>Captures event on given client logout, e.g.
 * "client/joe/session/1/event/disconnect"</td>
 * </tr>
 * <tr>
 * <td>topic/* /event/subscribe</td>
 * <td>Captures if subscribe() is invoked (on all topics)</td>
 * </tr>
 * <tr>
 * <td>topic/[topicId]/event/subscribe</td>
 * <td>Captures if subscribe() on the specified topic is invoked</td>
 * </tr>
 * <tr>
 * <td>client/[subjectId]/session/[publicSessionId]/event/subscribe</td>
 * <td>Captures if the given client has invoked subscribe(), e.g.
 * "client/joe/session/1/event/subscribe". The publicSessionId can be a wildcard
 * "*".</td>
 * </tr>
 * <tr>
 * <td>topic/* /event/unSubscribe</td>
 * <td>Captures if unSubscribe() is invoked (on all topics)</td>
 * </tr>
 * <tr>
 * <td>topic/[topicId]/event/unSubscribe</td>
 * <td>Captures if unSubscribe() on the specified topic is invoked</td>
 * </tr>
 * <tr>
 * <td>client/[subjectId]/session/[publicSessionId]/event/unSubscribe</td>
 * <td>Captures if the given client has invoked unSubscribe(), e.g.
 * "client/joe/session/1/event/unSubscribe". The publicSessionId can be a
 * wildcard "*".</td>
 * </tr>
 * <tr>
 * <td>topic/* /event/alive</td>
 * <td>Captures if a topic is created (on all topics)</td>
 * </tr>
 * <tr>
 * <td>topic/hello/event/alive</td>
 * <td>Captures event if the topic 'hello' is created</td>
 * </tr>
 * <tr>
 * <td>topic/* /event/dead</td>
 * <td>Captures if a topic is destroyed (on all topics)</td>
 * </tr>
 * <tr>
 * <td>topic/hello/event/dead</td>
 * <td>Captures event if the topic 'hello' is destroyed</td>
 * </tr>
 * <tr>
 * <td>client/[subjectId]/session/[publicSessionId]/event/connectionState</td>
 * <td>Captures event if the cluster client connection chages between ALIVE |
 * POLLING | DEAD. Wildcards are supported.</td>
 * </tr>
 * <tr>
 * <td>client/[subjectId]/session/[publicSessionId]/event/callbackState</td>
 * <td>Captures event if the client callback server goes to ALIVE or POLLING.
 * Note that the status change to DEAD is currently not implemented (it is
 * reported as POLLING). Wildcards are not supported.</td>
 * </tr>
 * <tr>
 * <td>heartbeat.360000</td>
 * <td>Sends a heartbeat notification every given milli seconds. Setting
 * <code>heartbeat</code> defaults to one notification per day (86400000
 * millis).</td>
 * </tr>
 * </table>
 * <p>
 * List of supported event sinks:
 * </p>
 * <table border="1">
 * <tr>
 * <td>destination.smtp</td>
 * <td>Sends an email about the occurred event. Collects multiple events to one
 * mail depending on configuration. You need to configure at least the email
 * address parameters <code>mail.stmp.from</code> and <code>mail.smtp.to</code>
 * and activate the <code>SmtpClient</code> plugin in
 * <code>xmlBlasterPlugins.xml</code>. If you have a reasonable email provider
 * you can configure it to forward the mail as an SMS (mine offers this
 * feature).</td>
 * </tr>
 * <tr>
 * <td>destination.publish</td>
 * <td>Publishes an xmlBlaster message which contains the occurred event,
 * currently all messages are published into a topic named '__sys__Event'</td>
 * </tr>
 * <tr>
 * <td>destination.jmx</td>
 * <td>Emits an JMX notification for the occurred event. Open 'jconsole' and
 * 'MBeans->org.xmlBlaster->node->xxx->service->EventPlugin[yyy]' there choose
 * the 'Notifications[0]' tabulator and click the 'Subscribe' button. Now you
 * receive the configured events.</td>
 * </tr>
 * </table>
 * 
 * <p>
 * We access the xmlBlaster core directly to register the supported internal
 * events, hence this plugin works only if it is in the same virtual machine
 * (JVM) as the xmlBlaster server.
 * </p>
 * <p>
 * All events don't throw any exceptions as this plugin should have no influence
 * on the regular work-flow of xmlBlaster.
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
 I_ConnectionStatusListener, I_RemotePropertiesListener, I_EventDispatcher, I_ConnectionStateListener,
      Comparable<Object> {
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
   protected Set<String> loggingSet;
   protected Set<String> runlevelSet;
   protected Set<String> clientSet;
   protected Set<String> topicSet;

   protected Set<String> pendingCallbackSessionInfoSet;
   protected Set<String> callbackSessionStateSet;

   protected static int staticInstanceCounter;
   protected int instanceCounter;

   private String uniqueInstanceName;
   private String[] addClientProperties;
   
   public ServerScope getServerScope() {
      return this.engineGlob;
   }

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

   protected PublishDestinationHelper publishDestinationHelper;
   protected String publishDestinationConfiguration;

   protected JmxDestinationHelper jmxDestinationHelper;
   protected String jmxDestinationConfiguration;

   protected ExecHelper execHelper;
   
   protected long logInfinitLoopSuppressDelayMillis;

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

      this.logInfinitLoopSuppressDelayMillis = this.glob.get("logInfinitLoopSuppressDelayMillis", 1000, null,this.pluginConfig);

      String add = this.glob.get("addClientProperties", "", null, this.pluginConfig);
      this.addClientProperties = ReplaceVariable.toArray(add.trim(), ",");

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
         this.publishDestinationHelper = new PublishDestinationHelper(this, this.publishDestinationConfiguration);
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
         this.jmxDestinationHelper = new JmxDestinationHelper(this, this.jmxDestinationConfiguration);
         if (destLogStr.length() > 0) destLogStr.append(",");
         destLogStr.append("destination.jmx");
         if (this.jmxDestinationConfiguration.length() > 0)
            destLogStr.append("(").append(this.jmxDestinationConfiguration).append(")");
      }

      // Doing a Runtime.exec?
      String execConfiguration = this.glob.get("destination.exec", (String) null, null, this.pluginConfig);
      if (execConfiguration != null) {
         execConfiguration = execConfiguration.trim();
         this.execHelper = new ExecHelper(this, execConfiguration);
         if (destLogStr.length() > 0)
            destLogStr.append(",");
         destLogStr.append("destination.exec");
         if (execConfiguration.length() > 0)
            destLogStr.append("(").append(execConfiguration).append(")");
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
               if (this.loggingSet == null)
                  this.loggingSet = new TreeSet<String>();
               this.loggingSet.add(event);
            }
            // "logging/warning/*"
            else if (event.startsWith(ContextNode.LOGGING_MARKER_TAG+"/warning/")
                  || event.startsWith("logging/warn/")) {
               XbNotifyHandler.instance().register(Level.WARNING.intValue(), this);
               if (this.loggingSet == null)
                  this.loggingSet = new TreeSet<String>();
               this.loggingSet.add(event);
            }
            // "service/RunlevelManager/event/startupRunlevel8", "service/RunlevelManager/event/shutdownRunlevel7"
            else if (event.startsWith(this.engineGlob.getRunlevelManager().getContextNode().getRelativeName()+"/event/")) {
               log.fine("Register event = " + event);
               this.engineGlob.getRunlevelManager().addRunlevelListener(this);
               if (this.runlevelSet == null)
                  this.runlevelSet = new TreeSet<String>();
               this.runlevelSet.add(event);
            }
            else if (isConnectionStateEvent(event)) {
               // client/[subjectId]/session/[publicSessionId]/event/connectionState
               ClusterManager clusterManager = requestBroker.getServerScope().getClusterManager();
               if (clusterManager == null) {
                  log.warning("Configuration of '" + event + "' is ignored, no cluster manager available");
                  continue;
               }
               if (!clusterManager.isReady()) {
                  log.warning("Configuration of '" + event + "' is ignored, cluster manager is not ready");
                  continue;
               }
               int index = event.lastIndexOf("/event/");
               if (index == -1) {
                  log.warning("Configuration of '" + event + "' is ignored, wrong syntax");
                  continue;
               }
               // strip "event/connectionState"
               String name = event.substring(0, index);
               ClusterNode[] nodes = clusterManager.getClusterNodes();
               for (int ic=0; ic<nodes.length; ic++) {
                  ClusterNode node = nodes[ic];
                  SessionName destination = node.getSessionName();
                  if (destination != null && destination.matchRelativeName(name)) {
                     node.registerConnectionListener(this);
                  }
               }
            }
            else if (isCallbackStateEvent(event)) {
            //else if (event.endsWith("/event/callbackState") || event.endsWith("/event/callbackAlive") || event.endsWith("/event/callbackPolling") || event.endsWith("/event/callbackDead")) {
               // OK: "client/joe/session/1/event/callbackState"
               // Not yet supported: "client/joe/session/1/event/callbackAlive", "client/joe/session/1/event/callbackPolling"
               int index = event.lastIndexOf("/event/");
               if (index == -1) {
                  log.warning("Configuration of '" + event + "' is ignored, wrong syntax");
                  continue;
               }
               String name = event.substring(0, index);
               SessionName sessionName = new SessionName(this.engineGlob, name);
               this.requestBroker.getAuthenticate().addClientListener(this);
               if (this.callbackSessionStateSet == null)
                  this.callbackSessionStateSet = new TreeSet<String>();
               if (event.startsWith(ContextNode.SUBJECT_MARKER_TAG+ContextNode.SEP+"*"+ContextNode.SEP) ||
                   event.endsWith(ContextNode.SESSION_MARKER_TAG+ContextNode.SEP+"*"+"/event/callbackState")) {
                  // "client/*/session/1/event/callbackState" or "client/joe/session/*/event/callbackState"
                  if (this.pendingCallbackSessionInfoSet == null)
                     this.pendingCallbackSessionInfoSet = new TreeSet<String>();
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
	                		 I_DispatchManager mgr = ses[se].getDispatchManager();
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
                  I_DispatchManager mgr = null;
                  if (sessionInfo != null)
                     mgr = sessionInfo.getDispatchManager();
                  if (mgr != null) {
                     mgr.addConnectionStatusListener(this);
                  }
                  else {
                     if (this.pendingCallbackSessionInfoSet == null)
                        this.pendingCallbackSessionInfoSet = new TreeSet<String>();
                     this.pendingCallbackSessionInfoSet.add(sessionName.getAbsoluteName());
                  }
                  this.callbackSessionStateSet.add(sessionName.getRelativeName());
               }
            }
            else if (isConnectionQueueEvent(event)) {
               // client/[subjectId]/session/[publicSessionId]/queue/connection/event/threshold.90%
               // TODO: register in xmlBlasterAccess of each ClusterNode client
               log.severe("Event " + event + " is not implemented");
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
               if (this.clientSet == null)
                  this.clientSet = new TreeSet<String>();
               this.clientSet.add(event);
            }
            else if (event.startsWith(ContextNode.TOPIC_MARKER_TAG+ContextNode.SEP)) {
               // "topic/hello/event/alive", "topic/hello/event/subscribe" ...
               log.fine("Register topic event = " + event);
               if (event.endsWith("/event/subscribe") || event.endsWith("/event/unSubscribe"))
                  this.requestBroker.addSubscriptionListener(this);
               else
                  this.engineGlob.getTopicAccessor().addTopicListener(this);
               if (this.topicSet == null)
                  this.topicSet = new TreeSet<String>();
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

   // client/*/session/[publicSessionId]/queue/callback/event/threshold.90%
   // client/[subjectId]/session/[publicSessionId]/queue/connection/event/threshold.90%
   // client/[subjectId]/session/[publicSessionId]/queue/callback/event/threshold.90%
   // topic/[topicId]/queue/history/event/threshold.90%
   // */queue/*/event/threshold*
   public static boolean isQueueEvent(String txt) {
      return matchesRegex(".*/queue/.*/event/threshold.*", txt);
   }

   // client/[subjectId]/session/[publicSessionId]/queue/connection/event/threshold.90%
   public static boolean isConnectionQueueEvent(String txt) {
      return matchesRegex(".*/queue/connection/event/threshold.*", txt);
   }

   public static boolean isPersistenceEvent(String txt) {
      return matchesRegex(".*/persistence/.*/event/threshold.*", txt);
   }

   public static boolean isCallbackStateEvent(String txt) {
	   return matchesRegex("client/.*/session/.*/event/callbackState", txt);
   }
   
   /** Cluster client side (ClusterNode.java) */
   public static boolean isConnectionStateEvent(String txt) {
      // client/[subjectId]/session/[publicSessionId]/event/connectionState
      return matchesRegex("client/.*/session/.*/event/connectionState", txt);
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
   public void update(SessionInfo sessionInfo, @SuppressWarnings("rawtypes") Map remoteProperties) {
      // Map<String, ClientProperty>
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
            sendEmail(summary, description, eventType, null, sessionName, false, sessionInfo.getRemoteProperties());
         }

         if (this.publishDestinationHelper != null) {
            sendMessage(summary, description,
                    eventType, errorCode, sessionName, cp);
         }

         if (this.jmxDestinationHelper != null) {
            sendJmxNotification(summary, description, eventType, null, false);
         }

         if (this.execHelper != null) {
            doExec(summary, description, eventType, null, sessionName);
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
            sendEmail(summary, description, eventType, null, sessionName, false, null);
         }

         if (this.publishDestinationHelper != null) {
            // Uses XML as message content
            sendMessage(summary, description, eventType, errorCode, sessionName, null);
         }

         if (this.jmxDestinationHelper != null) {
            sendJmxNotification(summary, description, eventType, null, false);
         }

         if (this.execHelper != null) {
            doExec(summary, description, eventType, null, sessionName);
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
            I_Queue sessionQueue = sessionInfo.getSessionQueue();
            if (sessionQueue != null) {
               buf.append("\n    <queue relating='callback'");
               buf.append(" numOfEntries='").append(sessionQueue.getNumOfEntries()).append("'");
               buf.append(" numOfBytes='").append(sessionQueue.getNumOfBytes()).append("'");
               buf.append("/>");
               //buf.append(sessionQueue.toXml("\n    "));
            }
            buf.append(sessionInfo.getDispatchStatistic().toXml("   "));
            buf.append("\n   ").append("</session>");
         }
         buf.append("\n  ").append("</client>");
      }

      ClusterManager clusterManager = g.getClusterManagerNoEx();
      if (clusterManager != null && clusterManager.isReady()) {
         ClusterNode[] nodes = clusterManager.getClusterNodes();
         for (int ic = 0; ic < nodes.length; ic++) {
            ClusterNode node = nodes[ic];
            SessionName destination = node.getRemoteSessionName();
            if (destination == null)
               continue;
            buf.append("\n  ").append("<connection clusterId='").appendEscaped(destination.getNodeIdStr()).append(
                  "' id='").appendEscaped(destination.getLoginName()).append("'>");
            buf.append("\n   ").append("<session id='").append(destination.getPublicSessionId()).append("'>");
            buf.append("\n    ").append("<state>").append(node.getConnectionStateStr()).append("</state>");
            I_Queue clientQueue = node.getConnectionQueue();
            if (clientQueue != null) {
               buf.append("\n    <queue relating='" + Constants.RELATING_CLIENT + "'"); // "connection"
               buf.append(" numOfEntries='").append(clientQueue.getNumOfEntries()).append("'");
               buf.append(" maxNumOfEntries='").append(clientQueue.getMaxNumOfEntries()).append("'");
               buf.append(" numOfBytes='").append(clientQueue.getNumOfBytes()).append("'");
               buf.append(" maxNumOfBytes='").append(clientQueue.getMaxNumOfBytes()).append("'");
               buf.append("/>");
            }
            // buf.append(clientQueue.getDispatchStatistic().toXml("   "));
            buf.append("\n   ").append("</session>");
            buf.append("\n  ").append("</connection>");
         }
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
            this.smtpDestinationHelper = new SmtpDestinationHelper(this, getSmtpClient(), destination);
            //if (this.smtpDestination.collectIntervall > 0)
            this.smtpTimeout = new Timeout("EventPlugin-SmtpTimer"); // we need it always to synchronize
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
 * @param sessionName TODO
    * @return Resolved string
    */
   public String replaceTokens(String str, String summary, String description, String eventType, String errorCode,
         SessionName sessionName) {
      if (str == null || str.indexOf("$") == -1)
         return str;
      str = ReplaceVariable.replaceAll(str, "$_{datetime}",
            new java.sql.Timestamp(new java.util.Date().getTime()).toString());
      str = ReplaceVariable.replaceAll(str, "$_{summary}", (summary==null)?"":summary);
      str = ReplaceVariable.replaceAll(str, "$_{description}", (description==null)?"":description);
      str = ReplaceVariable.replaceAll(str, "$_{instanceId}", this.engineGlob.getInstanceId()); // "/xmlBlaster/node/heron/instanceId/1136220586692"
      // own cluster node id
      str = ReplaceVariable.replaceAll(str, "$_{nodeId}", this.engineGlob.getId()); // "heron"
      str = ReplaceVariable.replaceAll(str, "$_{id}", this.engineGlob.getId());  // "heron"
      str = ReplaceVariable.replaceAll(str, "$_{eventType}", (eventType==null)?"":eventType);
      str = ReplaceVariable.replaceAll(str, "$_{errorCode}", (errorCode==null)?"":errorCode);
      // remote cluster node id
      str = ReplaceVariable.replaceAll(str, "$_{clusterId}", (sessionName == null) ? "" : sessionName.getNodeIdStr());
      str = ReplaceVariable.replaceAll(str, "$_{loginName}", (sessionName==null)?"":sessionName.getLoginName());
      str = ReplaceVariable.replaceAll(str, "$_{pubSessionId}", (sessionName==null)?"":""+sessionName.getPublicSessionId());
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
   
   private long lastMillisLog_LoopProtection = 0;

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
      
	  // Fatal case: a log.severe triggers this log() which processing triggers again a log.severe
	  // infinite loop -> java.lang.StackOverflowError
      boolean protectAgainstInfiniteLoop = false;
      Throwable reason = record.getThrown();
      if (reason != null) {
    	  if (reason.getStackTrace().length > 100) {
    		  protectAgainstInfiniteLoop = true;
    	  }
      }
      long currMillis = System.currentTimeMillis();
      if (currMillis - this.lastMillisLog_LoopProtection < this.logInfinitLoopSuppressDelayMillis) {
		  protectAgainstInfiniteLoop = true;
      }
      this.lastMillisLog_LoopProtection = currMillis;
      if (protectAgainstInfiniteLoop) {
    	  return;  // no better idea how to inform innocent user
      }
      
      Level level = record.getLevel();
      String source = record.getSourceClassName()+"."+record.getSourceMethodName();
      String str = record.getMessage();
      String found = (Level.WARNING.equals(level)) ? "logging/warning/" : "logging/severe/";
      String foundEvent = found + "*"; // "logging/warning/*"
      if (!this.loggingSet.contains(foundEvent))
         return;
      // How to extract the Logger name like "core"?

      try {
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
            sendEmail(summary, description, eventType, errorCode, null, false, null);
         }

         /*
         if (this.publishDestinationHelper != null) {
            if (Logger.LOG_WARN == level) {
               // log.fine("Too dangerous as it could produce looping messages");
               // e.g. [WARN  XmlBlaster.DispatchWorkerPool.heron-1 RequestBroker] Generating dead message 'callback:/node/heron/client/subscribe/1/NORM/1136329068010000002/__sys__Event' from publisher=/node/heron/client/__RequestBroker_internal[heron]/1 because delivery with queue 'null' failed: XmlBlasterException errorCode=[communication.noConnection.dead] serverSideException=true location=[SOCKET-HandleClient-subscribe/1] message=[#14515:14516M update() invocation ignored, we are shutdown. : Original errorCode=communication.noConnection] [See URL http://www.xmlblaster.org/xmlBlaster/doc/requirements/admin.errorcodes.listing.html#communication.noConnection.dead]
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

         if (this.execHelper != null) {
            doExec(summary, description, eventType, errorCode, null);
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
    * @see #replaceTokens(String str, String summary, String description, String eventType, String errorCode, SessionName sessionName) {
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
    * The xmlBlaster-message data sink. Publishes a message with the current
    * event occurred.
    * 
    * @param clientProperties
    *           Can be null
    * @param useEventTypeAsContent
    * @see #replaceTokens(String str, String summary, String description, String
    *      eventType, String errorCode, SessionName sessionName) {
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

   protected void doExec(String summary, String description, String eventType, String errorCode, SessionName sessionName) {
      if (this.execHelper == null)
         return;
      if (!this.isActive)
         return;

      try {
         this.execHelper.execute(summary, description, eventType, errorCode, sessionName);
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   private String replaceVariable(final ClientPropertiesInfo clientPropertiesInfo, String value) {
      if (clientPropertiesInfo == null) {
    	  return value;
      }
  	  ReplaceVariable replaceVariable = new ReplaceVariable("$_{", "}");
	  value = replaceVariable.replace(value, new I_ReplaceVariable() {
		//@Override
		public String get(String token) { // token is "MyKey" for value="Hello ${MyKey} world"
			String val = clientPropertiesInfo.get(token, token);
			return val;
		}
	  });
	  return value;
   }

   /**
    * Sending email as configured with <code>destination.smtp</code>.
    * @param summary The email summary line to use, it is injected to the template as $_{summary}
 * @param description The event description to send, it is injected as $_{description}
 * @param eventType For example "logging/severe/*"
 * @param sessionName TODO
 * @param forceSending If true send directly and ignore the timeout
 * @param clientPropertiesInfo TODO
 * @see http://www.faqs.org/rfcs/rfc2822.html
    */
   protected void sendEmail(String summary, String description,
            String eventType, String errorCode, SessionName sessionName, boolean forceSending, ClientPropertiesInfo clientPropertiesInfo) {
      try {
         if (this.smtpDestinationHelper == null) return;
         if (!this.isActive) return;

         if (summary == null) summary = "";
         if (description == null) description = "";

         if (forceSending) {
            EmailData emailData = this.smtpDestinationHelper.createEmailData();
            emailData.setSubject(replaceTokens(
this.smtpDestinationHelper.getSubjectTemplate(), summary, description,
                  eventType, errorCode, sessionName));
            emailData.setContent(replaceTokens(
                  this.smtpDestinationHelper.getContentTemplate(), summary, description, eventType, errorCode, sessionName));
            this.smtpDestinationHelper.sendEmail(emailData);
            return;
         }

         // Must be outside of sync as getting data from inside xmlBlaster e..g. on queue can block long time
         // and prevent other mails from being send
         
         // Build the email, if timer is active append new logging to the content of the existing mail ...
         final EmailData emailData = (this.currentEmailData == null) ? this.smtpDestinationHelper.createEmailData() : this.currentEmailData;
         
         String value = replaceTokens(
        		 this.smtpDestinationHelper.getSubjectTemplate(), summary, description,
        		                eventType, errorCode, sessionName);
         value = replaceVariable(clientPropertiesInfo, value);
         emailData.setSubject(value);
         
         String old = (emailData.getContent().length() == 0) ? "" : emailData.getContent() + this.smtpDestinationHelper.getContentSeparator();
         String content = old
                 + replaceTokens(
                		 this.smtpDestinationHelper.getContentTemplate(), summary, description, eventType,
                		                      errorCode, sessionName);
         content = replaceVariable(clientPropertiesInfo, content);
         emailData.setContent(content);

         synchronized(this.smtpDestinationMonitor) {
            // If no timer was active send immeditately (usually the first email)
            if (this.smtpTimeoutHandle == null) {
               this.currentEmailData = null;
               this.smtpTimeout.addTimeoutListener(new I_Timeout() {
                  public void timeout(Object userData) {
                     try {
                        smtpDestinationHelper.sendEmail(emailData);
                     } catch (Throwable e) {
                        log.warning("Email sending failed: " + e.toString());
                        e.printStackTrace();
                     }
                  }
               }, 1, null); // after 1 milli, dispatch to timer thread
            }
            else {
               // If a timer is active return, the timout will send the mail
               this.currentEmailData = emailData;
               return;
            }

            // Now span timer, other emails are collected until this timer elapses
            if (this.smtpDestinationHelper.getCollectIntervall() > 0) {
               this.smtpTimeoutHandle = this.smtpTimeout.addTimeoutListener(new I_Timeout() {
                  public void timeout(Object userData) {
                     synchronized(smtpDestinationMonitor) {
                        smtpTimeoutHandle = null;
                        if (currentEmailData == null) return;
                        try {
                           smtpDestinationHelper.sendEmail(currentEmailData);
                           // todo: Probably respan timer here to have the same minimal gap again
                        } catch (Throwable e) {
                           log.warning("Email sending failed: " + e.toString());
                           e.printStackTrace();
                        }
                        finally {
                           currentEmailData = null;
                        }
                     }
                  }
               }, this.smtpDestinationHelper.getCollectIntervall(), null);
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
            sendEmail(summary, description, eventType, null, null, false, null);
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

         if (this.execHelper != null) {
            doExec(summary, description, eventType, null, null);
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }



   /**
    * Enforced by I_EventDispatcher, notifies about I_Storage changes. 
    * @param summary
    * @param description
    * @param eventType
    */public void dispatchEvent(String summary, String description, String eventType) {
      try {
         if (this.smtpDestinationHelper != null) {
            sendEmail(summary, description, eventType, null, null, false, null);
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

         if (this.execHelper != null) {
            doExec(summary, description, eventType, null, null);
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
                  I_DispatchManager mgr = null;
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

      try {
         ConnectQosData cd = sessionInfo.getConnectQos().getData();
         // A client can on connect send a event/connect=false clientProperty to suppress the event fired
         if (cd.getClientProperty(ContextNode.EVENT_MARKER_TAG + ContextNode.SEP + "connect", true) == false) {
            if (log.isLoggable(Level.FINE)) log.fine("Found " + ContextNode.EVENT_MARKER_TAG + ContextNode.SEP + "connect=true for "+sessionName.toString());
    	    return;
         }
      }
      catch (Throwable e) {
 	     e.printStackTrace();
 	     log.warning("Accessing connectQos failed: " + e.toString());
      }
      
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
            sendEmail(summary, description, eventType, null, sessionName, false, sessionInfo.getRemoteProperties());
         }

         if (this.publishDestinationHelper != null) {
            sendMessage(summary, description,
                  eventType, errorCode, sessionName, sessionInfo.getRemotePropertyArr());
         }

         if (this.jmxDestinationHelper != null) {
            sendJmxNotification(summary, description, eventType, null, false);
         }

         if (this.execHelper != null) {
            doExec(summary, description, eventType, null, sessionName);
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   /**
    * Invoked on successful client re-login (interface I_ClientListener)
    */
   public void sessionUpdated(ClientEvent e) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Session update event for client " + e.getSessionInfo().toString() + ", nothing to do");
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
               I_DispatchManager mgr = null;
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
      
      try {
         ConnectQosData cd = sessionInfo.getConnectQos().getData();
         // A client can on connect send a event/disconnect=false clientProperty to suppress the event fired
         if (cd.getClientProperty(ContextNode.EVENT_MARKER_TAG + ContextNode.SEP + "disconnect", true) == false) {
            if (log.isLoggable(Level.FINE))  log.fine("Found " + ContextNode.EVENT_MARKER_TAG + ContextNode.SEP + "disconnect=true for "+sessionName.toString());
    	    return;
         }
      }
      catch (Throwable e) {
 	     e.printStackTrace();
 	     log.warning("Accessing connectQos failed: " + e.toString());
      }
      
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
            ClientPropertiesInfo clientPropertiesInfo = (sessionInfo != null) ? sessionInfo.getRemoteProperties() : null;
            sendEmail(summary, description, eventType, null, sessionName, false, clientPropertiesInfo);
         }

         if (this.publishDestinationHelper != null) {
            sendMessage(summary, description,
                    eventType, errorCode, sessionName, sessionInfo.getRemotePropertyArr());
         }

         if (this.jmxDestinationHelper != null) {
            sendJmxNotification(summary, description, eventType, null, false);
         }

         if (this.execHelper != null) {
            doExec(summary, description, eventType, null, sessionName);
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
      sendEmail("Test email", "Hello world :-) &<>?", "testEvent", null, null, true, null);
      synchronized(this.smtpDestinationMonitor) {
         if (this.smtpDestinationHelper != null)
            return "Send email from '" + this.smtpDestinationHelper.getFrom() + "' to '"
                  + this.smtpDestinationHelper.getTo() + "'";
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
         if (this.smtpDestinationHelper != null && this.smtpDestinationHelper.getCollectIntervall() > 0) {
            if (this.currentEmailData != null) {
               try {
                  this.smtpTimeoutHandle = this.smtpTimeout.refreshTimeoutListener(this.smtpTimeoutHandle, 0);
               }
               catch (XmlBlasterException e) {
                  throw new IllegalArgumentException(e.getMessage());
               }
               return "Send email from '" + this.smtpDestinationHelper.getFrom() + "' to '"
                     + this.smtpDestinationHelper.getTo() + "'";
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
         return this.smtpDestinationHelper.getCollectIntervall();
      return -1;
   }

   /**
    * @param mailCollectMillis The mailCollectMillis to set.
    */
   public void setMailCollectMillis(long mailCollectMillis) {
      if (this.smtpDestinationHelper != null)
         this.smtpDestinationHelper.setCollectIntervall((mailCollectMillis < 0) ? 0 : mailCollectMillis);
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
            sendEmail(summary, description, eventType, null, sessionName, false, sessionInfo.getRemoteProperties());
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
               Map<String, ClientProperty> props = subscriptionInfo.getSubscribeQosServer().getData().getClientProperties();
               if (props != null) {
                  Iterator<String> it = props.keySet().iterator();
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

         if (this.execHelper != null) {
            doExec(summary, description, eventType, null, sessionName);
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
            sendEmail(summary, description, eventType, null, sessionName, false, sessionInfo.getRemoteProperties());
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
               Map<String, ClientProperty> props = subscriptionInfo.getSubscribeQosServer().getData().getClientProperties();
               if (props != null) {
                  Iterator<String> it = props.keySet().iterator();
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

         if (this.execHelper != null) {
            doExec(summary, description, eventType, null, sessionName);
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
            sendEmail(summary, description, eventType, null, null, false, null);
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

         if (this.execHelper != null) {
            doExec(summary, description, eventType, null, null);
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

   protected void callbackStateChange(I_DispatchManager dispatchManager, ConnectionStateEnum oldState, ConnectionStateEnum newState) {
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

      try {
         //ConnectQosData cd = clientEvent.getSessionInfo().getConnectQos().getData();
         //dispatchManager.getSecurityInterceptor().getConnectQos();
         //dispatchManager.getDispatchConnectionsHandler().getCurrentDispatchConnection().getSessionInfoProtector().getConnectQos();
    	 //log.severe("Debug only: lookup " + sessionName.toString());
         CbDispatchConnection cbd = (CbDispatchConnection)dispatchManager.getDispatchConnectionsHandler().getCurrentDispatchConnection();
         if (cbd != null) {
            I_AdminSession is = cbd.getAdminSession();
            ConnectQosData cd = is.getConnectQos().getData();
            //ConnectQosData cd = clientEvent.getSessionInfo().getConnectQos().getData();
            // A client can on connect send a event/callbackState=false clientProperty to suppress the event fired
            if (cd.getClientProperty(ContextNode.EVENT_MARKER_TAG + ContextNode.SEP + "callbackState", true) == false) {
               if (log.isLoggable(Level.FINE))  log.fine("Found " + ContextNode.EVENT_MARKER_TAG + ContextNode.SEP + "callbackState=true for "+sessionName.toString());
    	       return;
            }
           //else
           //    log.severe("Debug only: Not found " + ContextNode.EVENT_MARKER_TAG + ContextNode.SEP + "callbackState");
        }
      }
      catch (Throwable e) {
    	  e.printStackTrace();
    	  log.warning("Accessing connectQos failed: " + e.toString());
      }
      
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
         
         ClientPropertiesInfo clientPropertiesInfo = null; 
         SessionInfo sessionInfo = this.requestBroker.getAuthenticate().getSessionInfo(sessionName);
         if (sessionInfo != null) {
            // If ConnectQos sends __remoteProperties=true to forward those
            clientPropertiesInfo = sessionInfo.getRemoteProperties();
         }

         if (this.smtpDestinationHelper != null) {
            sendEmail(summary, description, eventType, null, sessionName, false, clientPropertiesInfo);
         }

         if (this.publishDestinationHelper != null) {
            ClientProperty[] clientProperties = null;
            if (newState == ConnectionStateEnum.ALIVE) {
               if (sessionInfo != null) {
                  // If ConnectQos sends __remoteProperties=true to forward those
                  clientProperties = sessionInfo.getRemotePropertyArr();
                  ArrayList<ClientProperty> list = new ArrayList<ClientProperty>();
                  for (int i=0; i<clientProperties.length; i++) {
                	  list.add(clientProperties[i]);
                  }
                  list.add(new ClientProperty("_DispatchStatistic", 
                		  Constants.TYPE_STRING,
                		  Constants.ENCODING_NONE,
                		  dispatchManager.getDispatchStatistic().toXml("")));
                  
                  // You can configure in xmlBlasterPlugins.xml to add specific ConnectQos clientProperties
                  // <attribute id='addClientProperties'>version,deviceName,platform.model,platform.name,batterylevel,version,version.OS,deviceGuid</attribute>
                  ConnectQosData data = sessionInfo.getConnectQos().getData();
                  if (data != null && this.addClientProperties != null) {
                     for (int i=0; i<this.addClientProperties.length; i++) {
                        String key=this.addClientProperties[i];
                        ClientProperty cp = data.getClientProperty(key);
                        if (cp != null) {
                        	list.add(cp);
                        }
                     }
                  }
                  
                  clientProperties = (ClientProperty[])list.toArray(new ClientProperty[list.size()]);
               }
            }
            sendMessage(summary, description, eventType, errorCode, sessionName, clientProperties);
         }

         if (this.jmxDestinationHelper != null) {
            sendJmxNotification(summary, description, eventType, null, false);
         }

         if (this.execHelper != null) {
            doExec(summary, description, eventType, null, sessionName);
         }

      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   // Cluster client connections "/event/connectionState"
   protected void connectionStateChange(I_XmlBlasterAccess connection, ConnectionStateEnum oldState,
         ConnectionStateEnum newState) {
      SessionName sessionName = connection.getSessionName();
      String foundEvent = "/event/connectionState";

      try {
         XmlBlasterAccess xb = (XmlBlasterAccess) connection;
         SessionName absoluteName = new SessionName(glob, xb.getContextNode().getSessionNameCompatible());
         String clname = absoluteName.getAbsoluteName();
         // connection.getServerNodeId().toString()+ "/" +
         // sessionName.getAbsoluteName();
         String summary = "Connection state has changed to " + newState.toString() + " for cluster client "
               + clname;
         String description = (oldState.equals(newState)) ? ("Connection has state changed" + " to "
               + newState.toString() + " for client " + clname)
               : ("Connection state has changed from " + oldState.toString() + " to " + newState.toString()
                     + " for cluster client " + " " + clname);
         String eventType = foundEvent + " " + newState.toString();
         String errorCode = null;

         if (this.smtpDestinationHelper != null) {
        	ClientPropertiesInfo clientPropertiesInfo = null; 
        	SessionInfo sessionInfo = this.requestBroker.getAuthenticate().getSessionInfo(sessionName);
            if (sessionInfo != null) {
            	clientPropertiesInfo = sessionInfo.getRemoteProperties();
            }
            sendEmail(summary, description, eventType, null, absoluteName, false, clientPropertiesInfo);
         }

         if (this.publishDestinationHelper != null) {
            ClientProperty[] clientProperties = null;
            if (newState == ConnectionStateEnum.ALIVE) {
               SessionInfo sessionInfo = this.requestBroker.getAuthenticate().getSessionInfo(sessionName);
               if (sessionInfo != null) {
                  clientProperties = sessionInfo.getRemotePropertyArr();
                  ArrayList<ClientProperty> list = new ArrayList<ClientProperty>();
                  for (int i = 0; i < clientProperties.length; i++)
                     list.add(clientProperties[i]);
                  list.add(new ClientProperty("_DispatchStatistic", Constants.TYPE_STRING, Constants.ENCODING_NONE,
                        ((XmlBlasterAccess) connection).getDispatchStatistic().toXml("")));
                  clientProperties = (ClientProperty[]) list.toArray(new ClientProperty[list.size()]);
               }
            }
            sendMessage(summary, description, eventType, errorCode, sessionName, clientProperties);
         }

         if (this.jmxDestinationHelper != null) {
            sendJmxNotification(summary, description, eventType, null, false);
         }

         if (this.execHelper != null) {
            doExec(summary, description, eventType, null, absoluteName);
         }

      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.dispatch.I_ConnectionStatusListener#toAlive(org.xmlBlaster.util.dispatch.DispatchManager, org.xmlBlaster.util.dispatch.ConnectionStateEnum)
    */
   public void toAlive(I_DispatchManager dispatchManager, ConnectionStateEnum oldState) {
      callbackStateChange(dispatchManager, oldState, ConnectionStateEnum.ALIVE);
   }

   public void toAliveSync(I_DispatchManager dispatchManager, ConnectionStateEnum oldState) {
   }
   
   /* (non-Javadoc)
    * @see org.xmlBlaster.util.dispatch.I_ConnectionStatusListener#toPolling(org.xmlBlaster.util.dispatch.DispatchManager, org.xmlBlaster.util.dispatch.ConnectionStateEnum)
    */
   public void toPolling(I_DispatchManager dispatchManager, ConnectionStateEnum oldState) {
      callbackStateChange(dispatchManager, oldState, ConnectionStateEnum.POLLING);
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.dispatch.I_ConnectionStatusListener#toDead(org.xmlBlaster.util.dispatch.DispatchManager, org.xmlBlaster.util.dispatch.ConnectionStateEnum, java.lang.String)
    */
   public void toDead(I_DispatchManager dispatchManager, ConnectionStateEnum oldState, XmlBlasterException xmlBlasterException) {
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
    * JMX
    * 
    * @return Returns the destination.exec configuration.
    */
   public String getExecConfiguration() {
      if (this.execHelper == null)
         return "<not active>";
      return this.execHelper.getConfiguration();
   }

   /**
    * @param jmxDestinationConfiguration
    *           The jmxDestinationConfiguration to set.
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

   /**
    * This is the callback method invoked from I_XmlBlasterAccess for cluster
    * client connections "client/heron/session/1/event/connectionState"
    * <p />
    * This method is enforced through interface I_ConnectionStateListener
    */
   public void reachedAlive(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      connectionStateChange(connection, oldState, ConnectionStateEnum.ALIVE);
   }

   public void reachedAliveSync(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
   }

   /**
    * This is the callback method invoked from I_XmlBlasterAccess informing the
    * client in an asynchronous mode if the connection was lost. For cluster
    * client connections
    * <p />
    * This method is enforced through interface I_ConnectionStateListener
    */
   public void reachedPolling(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      connectionStateChange(connection, oldState, ConnectionStateEnum.POLLING);
   }

   /**
    * This is the callback method invoked from I_XmlBlasterAccess informing the
    * client in an asynchronous mode if the connection was lost. For cluster
    * client connections
    * <p />
    * This method is enforced through interface I_ConnectionStateListener
    */
   public void reachedDead(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      connectionStateChange(connection, oldState, ConnectionStateEnum.DEAD);
   }

public String getUniqueInstanceName() {
	return uniqueInstanceName;
}

public I_PluginConfig getPluginConfig() {
	return pluginConfig;
}
}
