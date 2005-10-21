/*------------------------------------------------------------------------------
Name:      ReplSlave.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import java.util.Map;
import java.util.logging.Logger;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.admin.I_AdminSession;
import org.xmlBlaster.engine.admin.I_AdminSubject;
import org.xmlBlaster.engine.mime.I_AccessFilter;
import org.xmlBlaster.engine.mime.Query;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.util.qos.address.Destination;


/**
 * ReplSlave
 * 
 * Used Topics:
 * <ul>
 *    <li><b>com.avitech-ag.repl.${replName}.data</b><br/>
 *        This is the topic used to send the replication data to the slaves.
 *    </li>
 *    <li><b>com.avitech-ag.repl.${replName}.status</b><br/>
 *        This is the topic used to send the replication data to the slaves.
 *    </li>
 *    <li></li>
 * </ul>   
 *  

 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class ReplSlave implements I_ReplSlave, I_AccessFilter, I_Callback, I_Plugin {

   private static Logger log = Logger.getLogger(ReplSlave.class.getName());
   private String slaveSessionId;
   private String pluginName;
   private String statusTopic;
   private String dataTopic;
   private org.xmlBlaster.engine.Global global;
   boolean initialized; 
   private String pluginVersion = "1.0";

   
   public ReplSlave(String slaveSessionId, String replName, I_Info info) {
      this.slaveSessionId = slaveSessionId;
      this.pluginName = "replSlave-" + replName + "-" + slaveSessionId;
      this.initialized = false;
      this.dataTopic = info.get("mom.topicName", "replication." + replName);
      this.statusTopic = info.get("mom.statusTopicName", this.dataTopic + ".status");
   }
   
   public void run() throws Exception {
      prepareForRequest();
   }
   
   /**
    * This is the first step in the process of requesting the initial Data.
    * <ul>
    *   <li>It clears the callback queue of the real slave</li>
    *   <li>It sends a message to the real slave to inform him that 
    *       a new initial update has been initiated. This is a PtP 
    *       message with a well defined topic, so administrators can 
    *       subscribe to it too.
    *   </li>
    *   <li>It then deactivates the callback dispatcher of the real slave</li>
    *   <li>makes a persistent subscription on behalf of the real slave
    *       by passing as a mime access filter an identifier for himself.
    *   </li>
    * </ul>
    * @see org.xmlBlaster.contrib.replication.I_ReplSlave#prepareForRequest()
    */
   public void prepareForRequest() throws Exception {
      if (!this.initialized)
         throw new Exception("prepareForRequest: '" + this.pluginName + "' has not been initialized properly or is already shutdown, check your logs");
      deativateCbClearAndDelegateSubscribe();
   }
   
   private void sendStatusInformation(String status) throws Exception {
      log.info("send status information '" + status + "'");
      I_XmlBlasterAccess conn = this.global.getXmlBlasterAccess();
      PublishKey pubKey = new PublishKey(this.global, this.statusTopic);
      Destination destination = new Destination(new SessionName(this.global, this.slaveSessionId));
      PublishQos pubQos = new PublishQos(this.global, destination);
      pubQos.setPersistent(true);
      MsgUnit msg = new MsgUnit(pubKey, status.getBytes(), pubQos);
      conn.publish(msg);
   }
   
   /**
    * Perfoms a delegate subscribe on behalf of the real slave.
    * @throws Exception
    */
   private void deativateCbClearAndDelegateSubscribe() throws Exception {
      log.info("deativateCbClearAndDelegateSubscribe");
      I_Authenticate auth = this.global.getAuthenticate();
      if (auth == null)
         throw new Exception(this.pluginName + " prepareForRequest: could not retreive the Authenticator object. Can not continue.");
      SessionName sessionName = new SessionName(this.global, this.slaveSessionId);
      I_AdminSubject subject = auth.getSubjectInfoByName(sessionName);
      if (subject == null)
         throw new Exception(this.pluginName + " prepareForRequest: no subject (slave) found with the session name '" + this.slaveSessionId + "'");
      I_AdminSession session = subject.getSessionByPubSessionId(sessionName.getPublicSessionId());
      if (session == null)
         throw new Exception(this.pluginName + " prepareForRequest: no session '" + this.slaveSessionId + "' found. Valid sessions for this user are '" + subject.getSessionList() + "'");

      long clearedMsg = session.clearCallbackQueue();
      log.info("clearing of callback queue before initiating: '" + clearedMsg + "' where removed since obsolete");

      sendStatusInformation("dbInitStart");
      session.setDispatcherActive(false); // stop the dispatcher
      
      SubscribeQos subQos = new SubscribeQos(this.global);
      subQos.setMultiSubscribe(false);
      subQos.setWantInitialUpdate(false);
      
      AccessFilterQos accessFilterQos = new AccessFilterQos(this.global, this.pluginName, this.pluginVersion, new Query(this.global, ""));
      subQos.addAccessFilter(accessFilterQos);
      session.subscribe(this.dataTopic, subQos.toXml());
   }

   /**
    * @see org.xmlBlaster.contrib.replication.I_ReplSlave#requestInitialData()
    */
   public void requestInitialData() throws Exception {
      // TODO Auto-generated method stub
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.contrib.replication.I_ReplSlave#reactivateDestination()
    */
   public void reactivateDestination() throws Exception {
      // TODO Auto-generated method stub
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.contrib.replication.I_ReplSlave#checkForDestroy(java.lang.String)
    */
   public boolean checkForDestroy(String replKey) throws Exception {
      // TODO Auto-generated method stub
      return false;
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.contrib.dbwriter.I_ContribPlugin#shutdown()
    */
   public synchronized void shutdown() {
      if (!this.initialized)
         return;
      try {
         PluginInfo pluginInfo = new PluginInfo(this.global, null, pluginName, this.pluginVersion);
         this.global.getPluginRegistry().unRegister(pluginInfo.getId());
         this.initialized = false;
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
      }
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.contrib.dbwriter.I_EventHandler#update(java.lang.String, byte[], java.util.Map)
    */
   public void update(String topic, byte[] content, Map attrMap) throws Exception {
      // TODO Auto-generated method stub
   }
   
   
   // for the I_AccessFilter

   public String[] getMimeExtended() {
      return new String[] { "*" };
   }

   public String[] getMimeTypes() {
      return new String[] { "*" };
   }

   public String getName() {
      return getVersion();
   }

   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#getType()
    */
   public String getType() {
      return this.pluginName;
   }

   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#getVersion()
    */
   public String getVersion() {
      return this.pluginVersion;
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) throws XmlBlasterException {
      initialize(global);
   }

   public synchronized void initialize(Global global) {
      if (this.initialized)
         return;
      String user = "replSlave";
      String pwd = "secret";
      try {
         if (!(global instanceof org.xmlBlaster.engine.Global))
            throw new Exception(this.pluginName + " prepareForRequest: the global used is not of the type engine.Global. Can not work as a delegate for the slave");
         this.global = (org.xmlBlaster.engine.Global)global;
         I_XmlBlasterAccess conn = this.global.getXmlBlasterAccess();
         // register the plugin first (so that it can be found on the subscription)
         PluginInfo pluginInfo = new PluginInfo(this.global, null, pluginName, this.pluginVersion);
         this.global.getPluginRegistry().register(pluginInfo.getId(), this);
         
         ConnectQos connectQos = new ConnectQos(this.global, user, pwd);
         connectQos.setPersistent(true);
         conn. connect(connectQos, this);
         this.initialized = true;
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }

   public boolean match(SessionInfo receiver, MsgUnit msgUnit, Query query) throws XmlBlasterException {
      // TODO Auto-generated method stub
      return false;
   }

   // I_Callback

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      // TODO Auto-generated method stub
      return null;
   }
   
}
