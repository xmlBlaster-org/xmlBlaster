 /*------------------------------------------------------------------------------
Name:      ReplManagerPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file

Switch on finer logging in xmlBlaster.properties:
trace[org.xmlBlaster.contrib.dbwatcher.convert.ResultSetToXmlConverter]=true
trace[org.xmlBlaster.contrib.db.DbPool]=true
trace[org.xmlBlaster.contrib.dbwatcher.detector.MD5ChangeDetector]=true
trace[org.xmlBlaster.contrib.dbwatcher.detector.AlertScheduler]=true
trace[org.xmlBlaster.contrib.dbwatcher.detector.TimestampChangeDetector]=true
trace[org.xmlBlaster.contrib.dbwatcher.plugin.ReplManagerPlugin]=true
trace[org.xmlBlaster.contrib.dbwatcher.mom.XmlBlasterPublisher]=true
trace[org.xmlBlaster.contrib.dbwatcher.DbWatcher]=true
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.replication.impl;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwatcher.PropertiesInfo;
import org.xmlBlaster.contrib.replication.I_ReplSlave;
import org.xmlBlaster.contrib.replication.ReplSlave;
import org.xmlBlaster.contrib.replication.ReplicationConstants;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.ClientProperty;

import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * ReplManagerPlugin is a plugin wrapper if you want to run DbWatcher inside xmlBlaster. 
 * <p />
 * DbWatcher checks a database for changes and publishes these to the MoM
 * <p />
 * This plugin needs to be registered in <tt>xmlBlasterPlugins.xml</tt>
 * to be available on xmlBlaster server startup.
 * <pre>
&lt;plugin id='ReplManagerPlugin.TEST_TS' className='org.xmlBlaster.contrib.dbwatcher.plugin.ReplManagerPlugin'>
   &lt;attribute id='jdbc.drivers'>oracle.jdbc.driver.OracleDriver&lt;/attribute>
   &lt;attribute id='db.url'>${db.url}&lt;/attribute>
   &lt;attribute id='db.user'>${db.user}&lt;/attribute>
   &lt;attribute id='db.password'>${db.password}&lt;/attribute>
   &lt;attribute id='db.queryMeatStatement'>SELECT * FROM TEST_TS WHERE TO_CHAR(ts, 'YYYY-MM-DD HH24:MI:SSXFF') > '${oldTimestamp}' ORDER BY ICAO_ID&lt;/attribute>
   &lt;attribute id='mom.topicName'>db.change.event.${groupColValue}&lt;/attribute>
   &lt;attribute id='mom.loginName'>dbWatcher/3&lt;/attribute>
   &lt;attribute id='mom.password'>secret&lt;/attribute>
   &lt;attribute id='mom.alertSubscribeKey'>&lt;key oid=''/>&lt;/attribute>
   &lt;attribute id='mom.alertSubscribeQos'>&lt;qos/>&lt;/attribute>
   &lt;attribute id='changeDetector.class'>org.xmlBlaster.contrib.dbwatcher.detector.TimestampChangeDetector&lt;/attribute>
   &lt;attribute id='alertScheduler.pollInterval'>10000&lt;/attribute>
   &lt;attribute id='changeDetector.groupColName'>ICAO_ID&lt;/attribute>
   &lt;attribute id='changeDetector.detectStatement'>SELECT MAX(TO_CHAR(ts, 'YYYY-MM-DD HH24:MI:SSXFF')) FROM TEST_TS&lt;/attribute>
   &lt;attribute id='converter.class'>org.xmlBlaster.contrib.dbwatcher.convert.ResultSetToXmlConverter&lt;/attribute>
   &lt;attribute id='converter.addMeta'>true&lt;/attribute>
   &lt;attribute id='transformer.class'>&lt;/attribute>
   &lt;action do='LOAD' onStartupRunlevel='9' sequence='6' onFail='resource.configuration.pluginFailed'/>
   &lt;action do='STOP' onShutdownRunlevel='6' sequence='5'/>
&lt;/plugin>
 * </pre>
 *
 * <p>
 * This plugin uses <tt>java.util.logging</tt> and redirects the logging to xmlBlasters default
 * logging framework. You can switch this off by setting the attribute <tt>xmlBlaster/jdk14loggingCapture</tt> to false.
 * </p>
 * 
 * @author <a href="mailto:xmlblast@marcelruff.info">Marcel Ruff</a>
 */
public class ReplManagerPlugin extends GlobalInfo implements ReplManagerPluginMBean, I_Callback {
   
   public final static String SESSION_ID = "replManager/1";
   private static Logger log = Logger.getLogger(ReplManagerPlugin.class.getName());
   // private I_ChangePublisher publisher;
   private Map replications;
   private Object mbeanHandle;
   private String user = "replManager";
   private String password = "secret";
   
   /**
    * Default constructor, you need to call <tt>init()<tt> thereafter.
    */
   public ReplManagerPlugin() {
      super(new String[] {});
      this.replications = new TreeMap();
   }
   
   /**
    * Never returns null. It returns a list of keys identifying the ongoing replications.
    * @return
    */
   public String[] getReplications() {
      return (String[])this.replications.keySet().toArray(new String[this.replications.size()]);
   }
   
   /**
    * Intiates the replication for the given slave.
    * 
    * @param slaveSessionName
    * @param replicationKey
    * @throws Exception
    */
   public void initiateReplication(String slaveSessionName, String replicationKey) throws Exception {
      log.info("initiateReplication invoked for slave '" + slaveSessionName + "' and on replication '" + replicationKey + "'");

      I_Info individualInfo = (I_Info)this.replications.get(replicationKey);
      if (individualInfo != null) {
         I_ReplSlave slave = new ReplSlave(slaveSessionName, replicationKey, individualInfo);
         slave.run();
      }
      else 
         throw new Exception("initiateReplication failed for '" + slaveSessionName + "' with replication key '" + replicationKey + "' since not known. Known are '" + this.getReplications() + "'");
   }
   
   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(Global global, PluginInfo pluginInfo) throws XmlBlasterException {
      super.init(global, pluginInfo);
      try {
         // String momClass = get("mom.class", "org.xmlBlaster.contrib.MomEventEngine").trim();
         // String registryName = "mom.publisher";
         String instanceName = "replication";
         ContextNode contextNode = new ContextNode(this.global, ContextNode.CONTRIB_MARKER_TAG,
               instanceName, this.global.getContextNode());
         this.mbeanHandle = this.global.registerMBean(contextNode, this);
         this.global.getJmxWrapper().registerMBean(contextNode, this);
         
         I_XmlBlasterAccess conn = this.global.getXmlBlasterAccess();
         ConnectQos connectQos = new ConnectQos(this.global, this.user, this.password);
         connectQos.setPersistent(true);
         connectQos.setMaxSessions(1);
         connectQos.setPtpAllowed(true);
         connectQos.setSessionTimeout(0L);
         String sessionName = ReplicationConstants.REPL_MANAGER_SESSION;
         connectQos.setSessionName(new SessionName(this.global, sessionName));
         conn.connect(connectQos, this);
      }
      catch (Throwable e) {
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_CONFIGURATION, "ReplManagerPlugin", "init failed", e); 
      }
      log.info("Loaded ReplManagerPlugin '" + getType() + "'");
   }

   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#shutdown()
    */
   public void shutdown() throws XmlBlasterException {
      super.shutdown();
      try {
         this.global.unregisterMBean(this.mbeanHandle);
         this.global.getXmlBlasterAccess().disconnect(new DisconnectQos(this.global));
      }
      catch (Throwable e) {
         log.warning("Ignoring shutdown problem: " + e.toString());
      }
      log.info("Stopped DbWatcher plugin '" + getType() + "'");
   }

   /**
    * Adds all the client properties found in the clientProperties map to the info 
    * object. Note that all entries in the map must be ClientPropery object,
    * this code will not check that.
    * 
    * @param info
    * @param clientProperties
    */
   private void addClientProperties(I_Info info, Map clientProperties) {
      if (info == null)
         log.warning("adding client properties was not possible since info object was null");
      if (clientProperties == null)
         log.warning("adding client properties had nothing to do since clientProperty map was null");
      synchronized (clientProperties) {
         Iterator iter = clientProperties.keySet().iterator();
         while (iter.hasNext()) {
            String key = (String)iter.next();
            ClientProperty prop = (ClientProperty)clientProperties.get(key);
            if (prop != null) {
               String val = prop.getStringValue();
               info.put(key, val);
            }
         }
      }
   }
   
   public synchronized void register(String senderSession, String replId, I_Info info) {
      I_Info oldInfo = (I_Info)this.replications.get(replId);
      info.put("_senderSession", senderSession);
      if (oldInfo != null) {
         String oldSenderSession = oldInfo.get("_senderSession", senderSession);
         if (oldSenderSession.equals(senderSession)) {
            log.warning("register '" + replId + "' by senderSession='" + senderSession + "' will overwrite old registration done previously");
            this.replications.put(replId, info);
         }
         else {
            log.warning("register '" + replId + "' by senderSession='" + senderSession + "' was not done since there is a registration done by '" + oldSenderSession + "'. Please remove first old registration" + "");
         }
      }
      else
         this.replications.put(replId, info);
   }
   
   public synchronized void unregister(String senderSession, String replId) {
      I_Info oldInfo = (I_Info)this.replications.get(replId);
      if (oldInfo == null)
         log.warning("unregister '" + replId + "' by senderSession='" + senderSession + "' is ignored since there is no such registration done");
      else {
         String oldSenderSession = oldInfo.get("_senderSession", senderSession);
         if (oldSenderSession.equals(senderSession)) {
            this.replications.remove(replId);
         }
         else {
            log.warning("unregister '" + replId + "' by senderSession='" + senderSession + "' was not done since there is a registration done by '" + oldSenderSession + "'. Please do it with the correct Session");
         }
      }
   }
   
   /**
    * It becomes events from all ReplicationConverter instances which want to register themselves for
    * administration of initial updates.
    * 
    * @see org.xmlBlaster.client.I_Callback#update(java.lang.String, org.xmlBlaster.client.key.UpdateKey, byte[], org.xmlBlaster.client.qos.UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      SessionName senderSession = updateQos.getSender();
      if (content == null)
         content = new byte[0];
      String request = new String(content);
      log.info("The master Replicator whith session '" + senderSession.getAbsoluteName() + "' is sending '" + request + "'");
      String replId = updateQos.getClientProperty(ReplicationConstants.REPL_MANAGER_REPL_ID, (String)null);
      if (replId == null || replId.length() < 1)
         log.severe(request + ": the client property '" + ReplicationConstants.REPL_MANAGER_REPL_ID + "' must be defined but is empty");
      else {
         if (request.equals(ReplicationConstants.REPL_MANAGER_REGISTER)) {
            PropertiesInfo info = new PropertiesInfo(new Properties());
            addClientProperties(info, updateQos.getClientProperties());
            register(senderSession.getAbsoluteName(), replId, info);
         }
         else if (request.equals(ReplicationConstants.REPL_MANAGER_UNREGISTER)) {
            unregister(senderSession.getAbsoluteName(), replId);
         }
      }
      return "OK";
   }

}
