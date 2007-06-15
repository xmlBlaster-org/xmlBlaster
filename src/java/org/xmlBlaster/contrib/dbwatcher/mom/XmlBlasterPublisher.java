/*------------------------------------------------------------------------------
Name:      XmlBlasterPublisher.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwatcher.mom;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.contrib.ClientPropertiesInfo;
import org.xmlBlaster.contrib.ContribConstants;
import org.xmlBlaster.contrib.I_ChangePublisher;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.I_Update;
import org.xmlBlaster.contrib.MomEventEngine;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.contrib.dbwatcher.DbWatcherConstants;
import org.xmlBlaster.contrib.dbwatcher.detector.I_AlertProducer;
import org.xmlBlaster.contrib.dbwatcher.detector.I_ChangeDetector;
import org.xmlBlaster.jms.XBSession;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.MsgUnit;


/**
 * Implementation to send change events to xmlBlaster.
 * <p>
 * This plugin plays two roles, first it is the gateway to xmlBlaster and second
 * if can be configured to listen on a alert topic and use incoming messages as
 * alerts to check the database again.
 * </p>
 * <p> 
 * Supported configuration:
 * </p>
 * <ul>
 *   <li><tt>mom.topicName</tt> for example <i>db.change.event.${colGroupValue}</i>
 *       where ${} variables will be replaced by the current
 *       <tt>colGroupValue</tt>.
 *   </li>
 *   <li><tt>mom.loginName</tt> the login name
 *   </li>
 *   <li><tt>mom.password</tt> the password
 *   </li>
 *   <li><tt>mom.publishKey</tt> for example <tt>&lt;key oid='db.change.event.${colGroupValue}'/></tt></li>
 *   <li><tt>mom.publishQos</tt> for example <tt>&lt;qos/></tt></li>
 *   <li><tt>mom.alertSubscribeKey</tt> for example <tt>&lt;key oid='db.change.alert'/></tt><br />
 *        To use XmlBlasterPublisher as an alert notifier register with <tt>alertProducer.class=org.xmlBlaster.contrib.dbwatcher.mom.XmlBlasterPublisher</tt></li>
 *   <li><tt>mom.alertSubscribeQos</tt> for example <tt>&lt;qos/></tt></li>
 *   <li><tt>mom.connectQos</tt> if given it is stronger than the <tt>mom.loginName</tt> 
 *           and <tt>mom.password</tt> settings</li>
 * </ul>
 *
 * @author Marcel Ruff
 */
public class XmlBlasterPublisher implements 
      I_ChangePublisher, 
      I_AlertProducer, 
      I_Callback, 
      I_ConnectionStateListener, 
      DbWatcherConstants,
      XmlBlasterPublisherMBean {
   
   private static Logger log = Logger.getLogger(XmlBlasterPublisher.class.getName());

   protected I_ChangeDetector changeDetector;
   protected Global glob;
   protected I_XmlBlasterAccess con;
   protected String topicNameTemplate;
   protected String loginName;
   protected String password;
   protected String publishKey;
   protected String publishQos;
   protected String alertSubscribeKey;
   protected String alertSubscribeQos;
   protected String alertSubscriptionId;
   protected ConnectQos connectQos;
   protected boolean eraseOnDrop;
   protected boolean eraseOnDelete;
   private int initCount = 0; 
   private I_Update defaultUpdate;
   private String adminKey = "<key oid='mom.publisher.adminMsg'/>";
   private int compressSize;
   private boolean throwAwayMessages;
   private long lastPublishTime;
   
   /** 
    * Can be null, taken out of the info object if the owner of this object has set the
    * parameter _connectionStateListener.
    */
   private I_ConnectionStateListener connectionStateListener;
   
   /**
    * Default constructor. 
    * You need to call  {@link #init(I_Info)} thereafter.
    */
   public XmlBlasterPublisher() {
      // void
   }

   /**
    * If called we shall subcribe to xmlBlaster for alert messages
    * which notifies us that there may be new changes available, we call
    * {@link I_ChangeDetector#checkAgain} in such a case.
    * @see org.xmlBlaster.contrib.dbwatcher.detector.I_AlertProducer#init(I_Info,I_ChangeDetector)
    */
   public void init(I_Info info, I_ChangeDetector changeDetector) throws Exception {
      this.changeDetector = changeDetector;
   }

   /**
    * Subscribes on the alert topic as configured with <tt>mom.alertSubscribeKey</tt>.  
    * @see org.xmlBlaster.contrib.dbwatcher.detector.I_AlertProducer#startProducing
    */
   public void startProducing() throws Exception {
      registerAlertListener(new I_Update() {
         public void update(String topic, java.io.InputStream is, Map attrMap) {
            try {
                if (log.isLoggable(Level.FINE)) log.fine("Alert notification arrived '" + topic + "' with " + ((attrMap==null)?0:attrMap.size()) + " attributes");
                changeDetector.checkAgain(attrMap);
            }
            catch (Exception e) {
                log.warning("Ignoring alert notification message '" + topic + "': " + e.toString());
            }
         }
      }, null);
   }

   /**
    * Unsubscribes from the alert topic. 
    * @see org.xmlBlaster.contrib.dbwatcher.detector.I_AlertProducer#stopProducing
    */
   public void stopProducing() throws Exception {
      if (this.alertSubscriptionId != null) {
         UnSubscribeKey sk = new UnSubscribeKey(glob, this.alertSubscriptionId);
         UnSubscribeQos sq = new UnSubscribeQos(glob);
         this.con.unSubscribe(sk, sq);
         this.alertSubscriptionId = null;
      }
   }


   /**
    * @see org.xmlBlaster.contrib.I_ContribPlugin#getUsedPropertyKeys()
    */
   public Set getUsedPropertyKeys() {
      Set set = new HashSet();
      set.add(MOM_TOPIC_NAME);
      set.add(MOM_LOGIN_NAME);
      set.add(MOM_PASSWORD);
      set.add(MOM_ERASE_ON_DROP);
      set.add(MOM_ERASE_ON_DELETE);
      set.add(MOM_PUBLISH_KEY);
      set.add(MOM_PUBLISH_QOS);
      set.add(MOM_ALERT_SUBSCRIBE_KEY);
      set.add(MOM_ALERT_SUBSCRIBE_QOS);
      set.add(MOM_CONNECT_QOS);
      set.add(MOM_PROPS_TO_ADD_TO_CONNECT);
      set.add(MOM_MAX_SESSIONS);
      return set;
   }

   /**
    * If a global is passed with <tt>info.getObject("org.xmlBlaster.engine.Global")</tt>
    * we take a clone and reuse it. 
    * @see org.xmlBlaster.contrib.dbwatcher.mom.I_ChangePublisher#init(I_Info)
    */
   public synchronized void init(I_Info info) throws Exception {
      // here because if somebody makes it as a second object it still works
      if (this.connectionStateListener == null) {
         log.info("The connection status listener will be added");
         this.connectionStateListener = (I_ConnectionStateListener)info.getObject("_connectionStateListener");
      }
      else
         log.warning("The connection status listener for this info has already been defined, ignoring this new request");

      if (this.initCount > 0) {
         this.initCount++;
         return;
      }
      
      Global globOrig = (Global)info.getObject("org.xmlBlaster.engine.Global");
      if (globOrig == null) {
         this.glob = new Global();
      }
      else {
         if (globOrig instanceof org.xmlBlaster.engine.ServerScope) {
            this.glob = globOrig.getClone(globOrig.getNativeConnectArgs());
            this.glob.addObjectEntry(Constants.OBJECT_ENTRY_ServerScope, globOrig.getObjectEntry(Constants.OBJECT_ENTRY_ServerScope)); //"ServerNodeScope"
         }
         else {
            this.glob = globOrig;
         }
      }

      this.topicNameTemplate = info.get(MOM_TOPIC_NAME, "db.change.event.${groupColValue}");
      this.loginName = info.get(MOM_LOGIN_NAME, "dbWatcher/1");
      this.password  = info.get(MOM_PASSWORD, "secret");

      this.eraseOnDrop = info.getBoolean(MOM_ERASE_ON_DROP, false);
      this.eraseOnDelete = info.getBoolean(MOM_ERASE_ON_DELETE, false);

      this.publishKey = info.get(MOM_PUBLISH_KEY, (String)null);
      if (this.publishKey != null && this.topicNameTemplate != null) {
         log.warning("constructor: since 'mom.publishKey' is defined, 'mom.topicName' will be ignored");
      }
      if (this.publishKey == null && this.topicNameTemplate == null) {
         //throw new XmlBlasterException(this.global, ErrorCode.USER_CONFIGURATION, ME, "at least one of the properties 'mom.topicName' or 'mom.publishKey' must be defined");
         throw new IllegalArgumentException("At least one of the properties 'mom.topicName' or 'mom.publishKey' must be defined");
      }
      if (this.publishKey == null) {
         this.publishKey = (new PublishKey(this.glob, this.topicNameTemplate)).toXml(); 
      }
      
      this.publishQos = info.get(MOM_PUBLISH_QOS, "<qos/>");

      this.alertSubscribeKey = info.get(MOM_ALERT_SUBSCRIBE_KEY, (String)null);
      if (this.alertSubscribeKey != null && this.alertSubscribeKey.length() < 1)
          this.alertSubscribeKey = null;
      this.alertSubscribeQos = info.get(MOM_ALERT_SUBSCRIBE_QOS, "<qos/>");

      String hardConnectQos  = info.get(MOM_CONNECT_QOS, (String)null);
      if (hardConnectQos != null) {
         this.connectQos = new ConnectQos(this.glob, this.glob.getConnectQosFactory().readObject(hardConnectQos));
      }
      else {
         this.connectQos = new ConnectQos(this.glob, this.loginName, this.password);
         int maxSessions = info.getInt(MOM_MAX_SESSIONS, 100);
         this.connectQos.setMaxSessions(maxSessions);
         this.connectQos.getAddress().setRetries(-1);
         this.connectQos.setSessionTimeout(0L);
         CallbackAddress cb = this.connectQos.getData().getCurrentCallbackAddress();
         cb.setRetries(-1);
         /*
         if (isRunningNative) {
            Address address = this.connectQos.getAddress();
            address.setType("LOCAL");
            address.setPingInterval(0L);
            address.setCollectTime(0L);
            this.connectQos.getClientQueueProperty().setType("RAM");
            this.connectQos.getClientQueueProperty().setVersion("1.0");
            CallbackAddress cb = this.connectQos.getData().getCurrentCallbackAddress();
            cb.setPingInterval(0L);
            cb.setCollectTime(0L);
            cb.setType("LOCAL");
            this.connectQos.getData().getSessionCbQueueProperty().setType("RAM");
            this.connectQos.getData().getSessionCbQueueProperty().setVersion("1.0");
            this.connectQos.getData().getSubjectQueueProperty().setType("RAM");
            this.connectQos.getData().getSubjectQueueProperty().setVersion("1.0");
         }
         */
         this.compressSize = info.getInt(MOM_COMPRESS_SIZE, 0);
      }

      String propKeysToAdd = info.get(MOM_PROPS_TO_ADD_TO_CONNECT, "").trim();
      if (propKeysToAdd.length() > 0) {
         if ("*".equals(propKeysToAdd)) { // then all properties are added to the connectQos
            if (hardConnectQos != null)
               log.warning("The property '" + MOM_PROPS_TO_ADD_TO_CONNECT + "' was is set to '*' and and '" + MOM_CONNECT_QOS + "' was set too (some of the properties could be overwritten");
            // fill the client properties of the connectQos with the info object
            new ClientPropertiesInfo(this.connectQos.getData().getClientProperties(), info);
         }
         else {
            StringTokenizer tokenizer = new StringTokenizer(propKeysToAdd, ",");
            while (tokenizer.hasMoreTokens()) {
               String key = tokenizer.nextToken().trim();
               String val = info.get(key, null);
               if (val == null)
                  log.warning("The property '" + key + "' shall be added to the connectQos but was not found among the properties");
               else {
                  if (this.connectQos.getClientProperty(key) != null)
                     log.warning("The property '" + key + "' is already set in the client properties of the connect qos. Will be overwritten with the value '" + val + "'");
                  this.connectQos.addClientProperty(key, val);
               }
                  
            }
         }
      }
      
      this.con = this.glob.getXmlBlasterAccess();
      this.con.registerConnectionListener(this);
      this.con.connect(this.connectQos, this);
      this.initCount++;
      // Make myself available
      info.putObject("org.xmlBlaster.contrib.dbwatcher.mom.XmlBlasterPublisher", this);
      info.putObject("org.xmlBlaster.contrib.dbwatcher.mom.I_ChangePublisher", this);

      // Add JMX Registration 
      String jmxName = I_Info.JMX_PREFIX + "xmlBlasterPublisher";
      info.putObject(jmxName, this);
      log.info("Added object '" + jmxName + "' to I_Info to be added as an MBean");
      
      
   }
   
   /**
    * @see org.xmlBlaster.contrib.dbwatcher.mom.I_ChangePublisher#shutdown
    */
   public synchronized void shutdown() {
      this.initCount--;
      if (this.initCount > 0)
         return;
      log.fine("Closing xmlBlaster connection");
      if (this.con != null) {
         this.con.disconnect(null);
         this.con = null;
         this.glob = null;
      }
   }

   private void addStringPropToQos(Map attrMap, MsgQosData qos) {
      synchronized (attrMap) {
         String[] keys = (String[])attrMap.keySet().toArray(new String[attrMap.size()]);
         for (int i=0; i < keys.length; i++) {
            Object val = attrMap.get(keys[i]);
            if (val != null && val instanceof String)
               qos.addClientProperty(keys[i], val);
         }
      }
   }
   
   /**
    * The send message is configured with <tt>mom.publishKey</tt> and <tt>mom.publishQos</tt>. 
    * A DROP command erases the topic.  
    * @see org.xmlBlaster.contrib.dbwatcher.mom.I_ChangePublisher#publish(String, String, Map)
    */
   public String publish(String changeKey, byte[] out, Map attrMap) throws Exception {
      // this is only for testing purposes
      if (this.throwAwayMessages) {
         log.fine("The message '" + changeKey + "' has been thrown away (not published)");
         return (new Timestamp()).toString() + "thrownAway";
      }
      long t0 = System.currentTimeMillis();
      if (out == null) out = "".getBytes();
      out = MomEventEngine.compress(out, attrMap, this.compressSize, null);

      String pk = (changeKey.indexOf("${") == -1) ? DbWatcher.replaceVariable(this.publishKey, changeKey) : this.publishKey;
      String command = null;
      if (attrMap != null) 
         command = (String)attrMap.get("_command");
      else
         command = "";
      
      String destLiteral = null;
      Destination destination = null;
      List additionalDestinations = null;
      if (attrMap != null)
         destLiteral = (String)attrMap.get("_destination");
      
      if (destLiteral != null) {
         if (destLiteral.indexOf(',') < 0) {
            destination = new Destination(new SessionName(this.glob, destLiteral));
            destination.forceQueuing(true); // to ensure it works even if this comes before manager
         }
         else {
            StringTokenizer tokenizer = new StringTokenizer(destLiteral, ","); // comma separated list
            destination = new Destination(new SessionName(this.glob, tokenizer.nextToken().trim()));
            destination.forceQueuing(true);
            additionalDestinations = new ArrayList();
            while (tokenizer.hasMoreTokens()) {
               Destination tmp = new Destination(new SessionName(this.glob, tokenizer.nextToken().trim()));
               tmp.forceQueuing(true);
            }
         }
      }
      
      // this is used to register the owner of this object (typically the DbWatcher)
      if ("INITIAL_DATA_RESPONSE".equals(command) || "STATEMENT".equals(command)) {
         PublishQos qos = null;
         if (destination != null) {
            qos = new PublishQos(this.glob, destination);
            if (additionalDestinations != null) {
               for (int i=0; i < additionalDestinations.size(); i++)
                  qos.addDestination((Destination)additionalDestinations.get(i));
            }
         }
         else
            qos = new PublishQos(this.glob);
         qos.setSubscribable(true);
         // to force to fill the client properties map !!
         ClientPropertiesInfo tmpInfo = new ClientPropertiesInfo(attrMap);
         new ClientPropertiesInfo(qos.getData().getClientProperties(), tmpInfo);
         addStringPropToQos(attrMap, qos.getData());

         PublishKey key = null;
         if (changeKey != null && changeKey.length() > 0)
            key = new PublishKey(this.glob, changeKey);
         else 
            key = new PublishKey(this.glob, "dbWatcherUnspecified");
         key.setContentMime("text/xml");
         MsgUnit msg = new MsgUnit(key, out, qos);
         PublishReturnQos prq = this.con.publish(msg);
         String id = (prq.getRcvTimestamp()!=null)?prq.getRcvTimestamp().toString():"queued";
         if (log.isLoggable(Level.FINE)) log.fine("Published '" + prq.getKeyOid() + "' '" + id + "'");
         return id;
      }
      if (this.eraseOnDrop && "DROP".equals(command)) {
         String oid = this.glob.getMsgKeyFactory().readObject(pk).getOid();
         EraseKey ek = new EraseKey(glob, oid);
         EraseQos eq = new EraseQos(glob);
         con.erase(ek, eq);
         log.info("Topic '" + pk + "' is erased:" + out);
         return "0";
      }
      if (this.eraseOnDelete && "DELETE".equals(command)) {
         String oid = this.glob.getMsgKeyFactory().readObject(pk).getOid();
         EraseKey ek = new EraseKey(glob, oid);
         EraseQos eq = new EraseQos(glob);
         con.erase(ek, eq);
         log.info("Topic '" + pk + "' is erased:" + out);
         return "0";
      }
      if (log.isLoggable(Level.FINER)) 
         log.finer("Topic '" + pk + "' is published: " + out);
      try {
         String oid = (String)attrMap.remove(ContribConstants.TOPIC_NAME); // consume it since only used to inform this method
         if (destination != null) {
            pk = this.adminKey;
         }
         if (oid != null)
            pk = "<key oid='" + oid + "'/>";
         MsgUnit msgUnit = new MsgUnit(pk, out, this.publishQos);
         String tmp = msgUnit.getKeyData().getContentMime();
         // FIXME pass this in the map and set only if explicitly set in the map
         if (tmp == null || tmp.equals("text/plain")) {
            msgUnit.getKeyData().setContentMime("text/xml");
         }
 
         if (destination != null)
            ((MsgQosData)msgUnit.getQosData()).addDestination(destination);
         // to force to fill the client properties map !!
         ClientPropertiesInfo tmpInfo = new ClientPropertiesInfo(attrMap);
         new ClientPropertiesInfo(msgUnit.getQosData().getClientProperties(), tmpInfo);
         addStringPropToQos(attrMap, (MsgQosData)msgUnit.getQosData());

         PublishReturnQos prq = this.con.publish(msgUnit);
         String id = (prq.getRcvTimestamp()!=null)?prq.getRcvTimestamp().toString():"queued";
         if (log.isLoggable(Level.FINE)) 
            log.fine("Published '" + prq.getKeyOid() + "' '" + id + "'");
         this.lastPublishTime = System.currentTimeMillis() - t0;
         return id;
      }
      catch (XmlBlasterException e) {
         log.severe("Can't publish to xmlBlaster: " + e.getMessage());
         throw e;
      }
   }
   
   /**
    * Subscribes on the alert topic as configured with <tt>mom.alertSubscribeKey</tt>.
    * @see org.xmlBlaster.contrib.dbwatcher.mom.I_ChangePublisher#registerAlertListener(I_Update)
    * @param attrs it currently accepts a null (old behaviour) or if it is not null, then
    * the attribute ptp must be set (does not matter to what).
    * 
    * @throws Exception Typically a XmlBlasterException
    */
   public boolean registerAlertListener(final I_Update momCb, Map attrs) throws Exception {
      if (momCb == null) throw new IllegalArgumentException("I_Update==null");
      try {
         if (attrs == null) { // 'old' behaviour
            if (this.alertSubscribeKey == null)
               return false;
            log.info("Registering on '" + this.alertSubscribeKey + "' for alerts");
            SubscribeReturnQos subRet = this.con.subscribe(this.alertSubscribeKey, this.alertSubscribeQos, new I_Callback() {
               public String update(String s, UpdateKey k, byte[] c, UpdateQos q) throws XmlBlasterException {
                  log.fine("Receiving alert message '" + k.getOid() + "'");
                  Map attrMap = clientPropertiesToMap(q.getClientProperties());
                  try {
                     momCb.update(k.getOid(), new ByteArrayInputStream(c), attrMap);
                  }
                  catch (Exception e) {
                     log.severe("Can't subscribe from xmlBlaster: " + e.getMessage());
                     throw new XmlBlasterException(glob, ErrorCode.USER_UPDATE_ERROR, "alertListener", "", e);
                  }
                  return "";
               }
             });
             this.alertSubscriptionId = subRet.getSubscriptionId();
             return true;
         }
         else { // could be for ptp or in future for additional subscriptions
            Object ptp = attrs.get("ptp");
            if (ptp != null) {
               synchronized (this) {
                  this.defaultUpdate = momCb;
                  return true;
               }
            }
            else {
               Thread.dumpStack();
               throw new XmlBlasterException(this.glob, ErrorCode.USER_CONFIGURATION, "XmlBlasterPublisher.registerAlertListener", "non-ptp are not implemented. Please assign to the attrs a 'ptp' attribute (no matter which value)");
            }
         }
         
      }
      catch (XmlBlasterException e) {
         log.severe("Can't subscribe from xmlBlaster: " + e.getMessage());
         throw e;
      }
   }
   
   /**
    * Not available via interface, used by test suite only.  
    * @param topic If the topic starts with "XPATH:" the prefix will be stripped
    *        and an Xpath subscription is done. 
    * @param momCb Incoming messages are forwarded to this interface
    * @return A unique identifier of the subscription
    * @throws Exception Typically a XmlBlasterException
    */
   public String subscribe(String topic, final I_Update momCb) throws Exception {
      if (momCb == null) throw new IllegalArgumentException("I_Update==null");
      if (topic == null) throw new IllegalArgumentException("topic==null");
        try {
          SubscribeKey sk = topic.startsWith("XPATH:") ?
                          new SubscribeKey(glob, topic.substring(6), "XPATH") :
                          new SubscribeKey(glob, topic);
          SubscribeQos sq = new SubscribeQos(glob);
          SubscribeReturnQos subRet = this.con.subscribe(sk, sq, new I_Callback() {
            public String update(String s, UpdateKey k, byte[] c, UpdateQos q) throws XmlBlasterException {
               if (log.isLoggable(Level.FINE)) log.fine("Receiving xmlBlaster message " + k.getOid());
               Map attrMap = clientPropertiesToMap(q.getClientProperties());
               try {
                  momCb.update(k.getOid(), new ByteArrayInputStream(c), attrMap);
               }
               catch (Exception e) {
                  log.severe("Can't subscribe from xmlBlaster: " + e.getMessage());
                  throw new XmlBlasterException(glob, ErrorCode.USER_UPDATE_ERROR, "alertListener", "", e);
               }
               
               return "";
            }
          });
          log.info("Subscribed on " + topic);
          return subRet.getSubscriptionId();
      }
      catch (XmlBlasterException e) {
          log.severe("Can't subscribe from xmlBlaster: " + e.getMessage());
          throw e;
      }
   }
   
   /**
    * Dummy implementation, PtP messages could arrive here which are ignored. 
    * @param s The sessionId
    * @param k The xml key
    * @param c The message content
    * @param q The message QoS
    * @return The UpdateReturnQos 
    * @see I_Callback#update
    */
   public String update(String s, UpdateKey k, byte[] content, UpdateQos q) throws XmlBlasterException {
      InputStream is = MomEventEngine.decompress(new ByteArrayInputStream(content), q.getClientProperties());
      if (this.defaultUpdate == null) { 
         log.warning("No update message expected, ignoring received " + k.toXml());
         return Constants.RET_OK;
      }
      synchronized(this) {
         try {
            // TODO Add here the specific qos attributes to the map.
            q.getData().addClientProperty("_sender", q.getSender().getRelativeName());
            this.defaultUpdate.update(s, is, q.getClientProperties());
            return Constants.RET_OK;
         }
         catch (Exception ex) {
            ex.printStackTrace();
            log.severe("Exception occured in the update method for key='" + s + "'");
            throw new XmlBlasterException(this.glob, ErrorCode.USER_UPDATE_HOLDBACK, "XmlBlasterPublisher.update", "user exception", ex);
         }
         catch (Throwable ex) {
            ex.printStackTrace();
            log.severe("Throwable occured in the update method for key='" + s + "'");
            throw new XmlBlasterException(this.glob, ErrorCode.USER_UPDATE_HOLDBACK, "XmlBlasterPublisher.update", "user exception", ex);
         }

      }
   }
   
   /**
    * Copy a map<String, ClientProperty> to a map<String, String>. 
    * @param clp The xmlBlaster ClientProperties
    * @return The simple string map
    */
   protected Map clientPropertiesToMap(Map clp) {
      Map attrMap = new TreeMap();
      Iterator it = clp.keySet().iterator();
      while (it.hasNext()) {
         String key = (String)it.next();
         ClientProperty prop = (ClientProperty)clp.get(key);
         attrMap.put(key, prop.getStringValue());
      }
      return attrMap;
   }

   /**
    * @see org.xmlBlaster.contrib.I_ChangePublisher#getJmsSession()
    */
   public XBSession getJmsSession() {
      return new XBSession(this.glob, XBSession.AUTO_ACKNOWLEDGE, false);
   }

   /**
    * @see org.xmlBlaster.client.I_ConnectionStateListener#reachedAlive(org.xmlBlaster.util.dispatch.ConnectionStateEnum, org.xmlBlaster.client.I_XmlBlasterAccess)
    */
   public void reachedAlive(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      if (this.connectionStateListener != null) {
         log.info("reached alive for user '" + this.con.getId() + "'");
         this.connectionStateListener.reachedAlive(oldState, connection);
      }
   }

   /**
    * @see org.xmlBlaster.client.I_ConnectionStateListener#reachedDead(org.xmlBlaster.util.dispatch.ConnectionStateEnum, org.xmlBlaster.client.I_XmlBlasterAccess)
    */
   public void reachedDead(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      if (this.connectionStateListener != null) {
         log.info("reached dead for user '" + this.con.getId() + "'");
         this.connectionStateListener.reachedDead(oldState, connection);
      }
   }

   /**
    * @see org.xmlBlaster.client.I_ConnectionStateListener#reachedPolling(org.xmlBlaster.util.dispatch.ConnectionStateEnum, org.xmlBlaster.client.I_XmlBlasterAccess)
    */
   public void reachedPolling(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      if (this.connectionStateListener != null) {
         log.info("reached polling for user '" + this.con.getId() + "'");
         this.connectionStateListener.reachedPolling(oldState, connection);
      }
   }

   
   // for jmx
   
   public String getAdminKey() {
      return adminKey;
   }

   public void setAdminKey(String adminKey) {
      this.adminKey = adminKey;
   }

   public String getAlertSubscribeKey() {
      return alertSubscribeKey;
   }

   public void setAlertSubscribeKey(String alertSubscribeKey) {
      this.alertSubscribeKey = alertSubscribeKey;
   }

   public String getAlertSubscribeQos() {
      return alertSubscribeQos;
   }

   public void setAlertSubscribeQos(String alertSubscribeQos) {
      this.alertSubscribeQos = alertSubscribeQos;
   }

   public String getAlertSubscriptionId() {
      return alertSubscriptionId;
   }

   public void setAlertSubscriptionId(String alertSubscriptionId) {
      this.alertSubscriptionId = alertSubscriptionId;
   }

   public int getCompressSize() {
      return compressSize;
   }

   public void setCompressSize(int compressSize) {
      this.compressSize = compressSize;
   }

   public String getConnectQos() {
      return connectQos.toXml();
   }

   public boolean isEraseOnDelete() {
      return eraseOnDelete;
   }

   public void setEraseOnDelete(boolean eraseOnDelete) {
      this.eraseOnDelete = eraseOnDelete;
   }

   public boolean isEraseOnDrop() {
      return eraseOnDrop;
   }

   public void setEraseOnDrop(boolean eraseOnDrop) {
      this.eraseOnDrop = eraseOnDrop;
   }

   public String getPublishKey() {
      return publishKey;
   }

   public void setPublishKey(String publishKey) {
      this.publishKey = publishKey;
   }

   public String getPublishQos() {
      return publishQos;
   }

   public void setPublishQos(String publishQos) {
      this.publishQos = publishQos;
   }

   public boolean isThrowAwayMessages() {
      return throwAwayMessages;
   }

   public void setThrowAwayMessages(boolean throwAwayMessages) {
      this.throwAwayMessages = throwAwayMessages;
   }

   public String getTopicNameTemplate() {
      return topicNameTemplate;
   }

   public void setTopicNameTemplate(String topicNameTemplate) {
      this.topicNameTemplate = topicNameTemplate;
   }

   public String getLoginName() {
      return loginName;
   }
   
   /**
    * Returns the time in ms it took for the last real publish. Real publish is meant
    * the last publish of messages which are not drop or delete
    */
   public long getLastPublishTime() {
      return this.lastPublishTime;
   }
   
}
