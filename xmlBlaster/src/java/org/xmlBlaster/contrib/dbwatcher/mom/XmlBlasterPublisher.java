/*------------------------------------------------------------------------------
Name:      XmlBlasterPublisher.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwatcher.mom;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
import org.xmlBlaster.contrib.I_ChangePublisher;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.I_Update;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.contrib.dbwatcher.detector.I_AlertProducer;
import org.xmlBlaster.contrib.dbwatcher.detector.I_ChangeDetector;
import org.xmlBlaster.jms.XBSession;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.qos.ClientProperty;
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
public class XmlBlasterPublisher implements I_ChangePublisher, I_AlertProducer, I_Callback, I_ConnectionStateListener
{
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
         public void update(String topic, byte[] content, Map attrMap) {
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
      set.add("mom.topicName");
      set.add("mom.loginName");
      set.add("mom.password");
      set.add("mom.eraseOnDrop");
      set.add("mom.eraseOnDelete");
      set.add("mom.publishKey");
      set.add("mom.publishQos");
      set.add("mom.alertSubscribeKey");
      set.add("mom.alertSubscribeQos");
      set.add("mom.connectQos");
      set.add("mom.maxSessions");
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
         if (globOrig instanceof org.xmlBlaster.engine.Global) {
            this.glob = globOrig.getClone(globOrig.getNativeConnectArgs());
            this.glob.addObjectEntry("ServerNodeScope", globOrig.getObjectEntry("ServerNodeScope"));
         }
         else {
            this.glob = globOrig;
         }
      }

      this.topicNameTemplate = info.get("mom.topicName", "db.change.event.${groupColValue}");
      this.loginName = info.get("mom.loginName", "dbWatcher/1");
      this.password  = info.get("mom.password", "secret");

      this.eraseOnDrop = info.getBoolean("mom.eraseOnDrop", false);
      this.eraseOnDelete = info.getBoolean("mom.eraseOnDelete", false);

      this.publishKey = info.get("mom.publishKey", (String)null);
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
      
      this.publishQos = info.get("mom.publishQos", "<qos/>");

      this.alertSubscribeKey = info.get("mom.alertSubscribeKey", (String)null);
      if (this.alertSubscribeKey != null && this.alertSubscribeKey.length() < 1)
          this.alertSubscribeKey = null;
      this.alertSubscribeQos = info.get("mom.alertSubscribeQos", "<qos/>");

      String tmp  = info.get("mom.connectQos", (String)null);
      if (tmp != null) {
         this.connectQos = new ConnectQos(this.glob, this.glob.getConnectQosFactory().readObject(tmp));
      }
      else {
         this.connectQos = new ConnectQos(this.glob, this.loginName, this.password);
         int maxSessions = info.getInt("mom.maxSessions", 100);
         this.connectQos.setMaxSessions(maxSessions);
         this.connectQos.getAddress().setRetries(-1);
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
      }
      
      this.con = this.glob.getXmlBlasterAccess();
      this.con.registerConnectionListener(this);
      this.con.connect(this.connectQos, this);
      this.initCount++;
      // Make myself available
      info.putObject("org.xmlBlaster.contrib.dbwatcher.mom.XmlBlasterPublisher", this);
      info.putObject("org.xmlBlaster.contrib.dbwatcher.mom.I_ChangePublisher", this);
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

   private void addStringPropToQos(Map attrMap, PublishQos qos) {
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
      
      if (out == null) out = "".getBytes();
      String pk = (changeKey.indexOf("${") == -1) ? DbWatcher.replaceVariable(this.publishKey, changeKey) : this.publishKey;
      String command = null;
      if (attrMap != null) 
         command = (String)attrMap.get("_command");
      else
         command = "";
      // this is used to register the owner of this object (typically the DbWatcher)
      if ("REGISTER".equals(command) || "UNREGISTER".equals(command) || "INITIAL_DATA_RESPONSE".equals(command)) {
         
         String destination = null;
         if (attrMap != null)
            destination = (String)attrMap.get("_destination");
         PublishQos qos = null;
         if (destination != null) {
            Destination dest = new Destination(new SessionName(this.glob, destination));
            dest.forceQueuing(true); // to ensure it works even if this comes before manager
            qos = new PublishQos(this.glob, dest);
         }
         else
            qos = new PublishQos(this.glob);
         if (!"UNREGISTER".equals(command)) // unregister are not persistent
            qos.setPersistent(true);
         qos.setSubscribable(true);
         // to force to fill the client properties map !!
         ClientPropertiesInfo tmpInfo = new ClientPropertiesInfo(attrMap);
         new ClientPropertiesInfo(qos.getData().getClientProperties(), tmpInfo);
         addStringPropToQos(attrMap, qos);

         PublishKey key = null;
         if (changeKey != null && changeKey.length() > 0)
            key = new PublishKey(this.glob, changeKey);
         else 
            key = new PublishKey(this.glob);
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
      if (log.isLoggable(Level.FINER)) log.finer("Topic '" + pk + "' is published: " + out);
      try {
         MsgUnit msgUnit = new MsgUnit(pk, out, this.publishQos);
         PublishReturnQos prq = this.con.publish(msgUnit);
         String id = (prq.getRcvTimestamp()!=null)?prq.getRcvTimestamp().toString():"queued";
         if (log.isLoggable(Level.FINE)) log.fine("Published '" + prq.getKeyOid() + "' '" + id + "'");
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
                     momCb.update(k.getOid(), c, attrMap);
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
                  momCb.update(k.getOid(), c, attrMap);
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
   public String update(String s, UpdateKey k, byte[] c, UpdateQos q) throws XmlBlasterException {
      if (this.defaultUpdate == null) { 
         log.warning("No update message expected, ignoring received " + k.toXml());
         return Constants.RET_OK;
      }
      synchronized(this) {
         try {
            // TODO Add here the specific qos attributes to the map.
            q.getData().addClientProperty("_sender", q.getSender().getRelativeName());
            this.defaultUpdate.update(s, c, q.getClientProperties());
            return Constants.RET_OK;
         }
         catch (Exception ex) {
            ex.printStackTrace();
            log.severe("Exception occured in the update method for key='" + s + "'");
            throw new XmlBlasterException(this.glob, ErrorCode.USER_HOLDBACK, "XmlBlasterPublisher.update", "user exception", ex);
         }
         catch (Throwable ex) {
            ex.printStackTrace();
            log.severe("Throwable occured in the update method for key='" + s + "'");
            throw new XmlBlasterException(this.glob, ErrorCode.USER_HOLDBACK, "XmlBlasterPublisher.update", "user exception", ex);
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
      if (this.connectionStateListener != null)
         this.connectionStateListener.reachedAlive(oldState, connection);
   }

   /**
    * @see org.xmlBlaster.client.I_ConnectionStateListener#reachedDead(org.xmlBlaster.util.dispatch.ConnectionStateEnum, org.xmlBlaster.client.I_XmlBlasterAccess)
    */
   public void reachedDead(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      if (this.connectionStateListener != null)
         this.connectionStateListener.reachedDead(oldState, connection);
   }

   /**
    * @see org.xmlBlaster.client.I_ConnectionStateListener#reachedPolling(org.xmlBlaster.util.dispatch.ConnectionStateEnum, org.xmlBlaster.client.I_XmlBlasterAccess)
    */
   public void reachedPolling(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      if (this.connectionStateListener != null)
         this.connectionStateListener.reachedPolling(oldState, connection);
   }
}
