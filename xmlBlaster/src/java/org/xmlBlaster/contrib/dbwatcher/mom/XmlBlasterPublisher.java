/*------------------------------------------------------------------------------
Name:      XmlBlasterPublisher.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwatcher.mom;

import java.util.Map;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.contrib.dbwatcher.detector.I_AlertProducer;
import org.xmlBlaster.contrib.dbwatcher.detector.I_ChangeDetector;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.CallbackAddress;


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
public class XmlBlasterPublisher implements I_ChangePublisher, I_AlertProducer, I_Callback
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

   /**
    * Default constructor. 
    * You need to call  {@link #init(I_Info)} thereafter.
    */
   public XmlBlasterPublisher() {
      // void
   }

   /**
    * Convenience constructor which calls {@link #init(I_Info)}.  
    * @param info The configuration environment
    */
   public XmlBlasterPublisher(I_Info info) throws Exception {
      init(info);
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
      boolean alertsAreAvailable = registerAlertListener(new I_MomCb() {
         public void update(String topic, String content, Map attrMap) {
            try {
                if (log.isLoggable(Level.FINE)) log.fine("Alert notification arrived '" + topic + "' with " + ((attrMap==null)?0:attrMap.size()) + " attributes");
                changeDetector.checkAgain(attrMap);
            }
            catch (Exception e) {
                log.warning("Ignoring alert notification message '" + topic + "': " + e.toString());
            }
         }
      });
   }

   /**
    * Unsubscribes from the alert topic. 
    * @see org.xmlBlaster.contrib.dbwatcher.detector.I_AlertProducer#stopProducing
    */
   public void stopProducing() throws Exception {
      if (this.alertSubscriptionId != null) {
         UnSubscribeKey sk = new UnSubscribeKey(glob, this.alertSubscriptionId);
         UnSubscribeQos sq = new UnSubscribeQos(glob);
         UnSubscribeReturnQos[] subRet = this.con.unSubscribe(sk, sq);
         this.alertSubscriptionId = null;
      }
   }
   
   /**
    * If a global is passed with <tt>info.getObject("org.xmlBlaster.engine.Global")</tt>
    * we take a clone and reuse it. 
    * @see org.xmlBlaster.contrib.dbwatcher.mom.I_ChangePublisher#init(I_Info)
    */
   public void init(I_Info info) throws Exception {
      if (this.con != null) return;
      
      boolean isRunningNative = true;
      Global globOrig = (Global)info.getObject("org.xmlBlaster.engine.Global");
      if (globOrig == null) {
         isRunningNative = false;
         this.glob = new Global();
      }
      else
         this.glob = globOrig.getClone(null);

      this.topicNameTemplate = info.get("mom.topicName", "db.change.event.${groupColValue}");
      this.loginName = info.get("mom.loginName", "dbWatcher/1");
      this.password  = info.get("mom.password", "secret");

      this.eraseOnDrop = info.getBoolean("mom.eraseOnDrop", false);

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
         this.connectQos.setMaxSessions(100);
         if (isRunningNative) {
            Address address = this.connectQos.getAddress();
            this.glob.addObjectEntry("ServerNodeScope", globOrig.getObjectEntry("ServerNodeScope"));
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
      }
      
      this.con = this.glob.getXmlBlasterAccess();
      this.con.connect(this.connectQos, this);
      
      // Make myself available
      info.putObject("org.xmlBlaster.contrib.dbwatcher.mom.XmlBlasterPublisher", this);
      info.putObject("org.xmlBlaster.contrib.dbwatcher.mom.I_ChangePublisher", this);
   }
   
   /**
    * @see org.xmlBlaster.contrib.dbwatcher.mom.I_ChangePublisher#shutdown
    */
   public void shutdown() {
      log.fine("Closing xmlBlaster connection");
      if (this.con != null) {
         this.con.disconnect(null);
         this.con = null;
         this.glob = null;
      }
   }

   /**
    * The send message is configured with <tt>mom.publishKey</tt> and <tt>mom.publishQos</tt>. 
    * A DROP command erases the topic.  
    * @see org.xmlBlaster.contrib.dbwatcher.mom.I_ChangePublisher#publish(String, String, Map)
    */
   public String publish(String changeKey, String out, Map attrMap) throws Exception {
      if (out == null) out = "";
      String pk = DbWatcher.replaceVariable(this.publishKey, changeKey);
      String command = (attrMap != null) ? (String)attrMap.get("_command") : (String)null;
      if (this.eraseOnDrop && "DROP".equals(command)) {
         String oid = this.glob.getMsgKeyFactory().readObject(pk).getOid();
         EraseKey ek = new EraseKey(glob, oid);
         EraseQos eq = new EraseQos(glob);
         EraseReturnQos[] eraseArr = con.erase(ek, eq);
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
    * @see org.xmlBlaster.contrib.dbwatcher.mom.I_ChangePublisher#registerAlertListener(I_MomCb)
    * @throws Exception Typically a XmlBlasterException
    */
   public boolean registerAlertListener(final I_MomCb momCb) throws Exception {
      if (momCb == null) throw new IllegalArgumentException("I_MomCb==null");
      if (this.alertSubscribeKey == null)
         return false;
      try {
         log.info("Registering on '" + this.alertSubscribeKey + "' for alerts");
         SubscribeReturnQos subRet = this.con.subscribe(this.alertSubscribeKey, this.alertSubscribeQos, new I_Callback() {
            public String update(String s, UpdateKey k, byte[] c, UpdateQos q) {
               log.fine("Receiving alert message '" + k.getOid() + "'");
               Map attrMap = clientPropertiesToMap(q.getClientProperties());
               momCb.update(k.getOid(), new String(c), attrMap);
               return "";
            }
          });
          this.alertSubscriptionId = subRet.getSubscriptionId();
          return true;
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
   public String subscribe(String topic, final I_MomCb momCb) throws Exception {
      if (momCb == null) throw new IllegalArgumentException("I_MomCb==null");
      if (topic == null) throw new IllegalArgumentException("topic==null");
        try {
          SubscribeKey sk = topic.startsWith("XPATH:") ?
                          new SubscribeKey(glob, topic.substring(6), "XPATH") :
                          new SubscribeKey(glob, topic);
          SubscribeQos sq = new SubscribeQos(glob);
          SubscribeReturnQos subRet = this.con.subscribe(sk, sq, new I_Callback() {
            public String update(String s, UpdateKey k, byte[] c, UpdateQos q) {
               if (log.isLoggable(Level.FINE)) log.fine("Receiving xmlBlaster message " + k.getOid());
               Map attrMap = clientPropertiesToMap(q.getClientProperties());
               momCb.update(k.getOid(), new String(c), attrMap);
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
   public String update(String s, UpdateKey k, byte[] c, UpdateQos q) {
      log.warning("No update message expected, ignoring received " + k.toXml());
      return "";
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
}
