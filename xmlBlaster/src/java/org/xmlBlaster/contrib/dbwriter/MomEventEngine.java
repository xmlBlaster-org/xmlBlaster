/*------------------------------------------------------------------------------
Name:      MomEventHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.contrib.I_ContribPlugin;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.I_Update;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

public class MomEventEngine implements I_ContribPlugin, I_Callback {

   private static Logger log = Logger.getLogger(MomEventEngine.class.getName());
   protected Global glob;
   protected I_XmlBlasterAccess con;
   protected String loginName;
   protected String password;
   protected List subscribeKeyList;
   protected List subscribeQosList;
   protected ConnectQos connectQos;
   protected I_Update eventHandler;
   
   public MomEventEngine() {
      this.subscribeKeyList = new ArrayList();
      this.subscribeQosList = new ArrayList();
   }
   
   /**
    * @see org.xmlBlaster.contrib.I_ContribPlugin#getUsedPropertyKeys()
    */
   public Set getUsedPropertyKeys() {
      Set set = new HashSet();
      set.add("mom.loginName");
      set.add("mom.password");
      set.add("mom.subscriptions");
      set.add("mom.connectQos");
      set.add("mom.maxSessions");
      return set;
   }

   public void init(I_Info info) throws Exception {
      if (this.con != null) return;
      
      this.eventHandler = (I_Update)info.getObject("org.xmlBlaster.contrib.dbwriter.DbWriter");
      if (this.eventHandler == null)
         throw new Exception("init: the event handler is null (probably not correctly registered)");
      
      Global globOrig = (Global)info.getObject("org.xmlBlaster.engine.Global");
      if (globOrig == null) {
         this.glob = new Global();
      }
      else {
         this.glob = globOrig.getClone(globOrig.getNativeConnectArgs());
         this.glob.addObjectEntry("ServerNodeScope", globOrig.getObjectEntry("ServerNodeScope"));
      }

      this.loginName = info.get("mom.loginName", "dbWriter/1");
      this.password  = info.get("mom.password", "secret");

      /* comma separated list of names for the subscriptions */
      String subscriptionNames = info.get("mom.subscriptions", (String)null);
      if (subscriptionNames != null) {
         StringTokenizer tokenizer = new StringTokenizer(subscriptionNames.trim(), ",");
         while (tokenizer.hasMoreTokens()) {
            String name = tokenizer.nextToken();
            if (name != null) {
               name = name.trim();
               if (name.length() > 0) {
                  String tmp = "mom.subscribeKey[" + name + "]";
                  String key = info.get(tmp, null);
                  if (key == null)
                     throw new Exception(".init: the attribute '" + tmp + "' has not been found but '" + name +"' was listed in 'mom.subscriptions' solve the inconsistency");
                  tmp = "mom.subscribeQos[" + name + "]";
                  String qos = info.get(tmp, "<qos/>");
                  log.info(".init: adding subscription '" + name + "' to the list: key='" + key + "' and qos='" + qos + "'");
                  this.subscribeKeyList.add(key);
                  this.subscribeQosList.add(qos);
               }
            }
         }
      }
      // Either subscriptionNames are null or not we use the mom.subscriptionKey and mom.subscriptionQos
      String tmp = "mom.subscribeKey";
      String key = info.get(tmp, null);
      if (key != null) {
         tmp = "mom.subscribeQos";
         String qos = info.get(tmp, "<qos/>");
         log.info(".init: adding unnamed subscription to the list: key='" + key + "' and qos='" + qos + "'");
         this.subscribeKeyList.add(key);
         this.subscribeQosList.add(qos);
      }


      tmp  = info.get("mom.connectQos", (String)null);
      if (tmp != null) {
         this.connectQos = new ConnectQos(this.glob, this.glob.getConnectQosFactory().readObject(tmp));
      }
      else {
         this.connectQos = new ConnectQos(this.glob, this.loginName, this.password);
         int maxSessions = info.getInt("mom.maxSessions", 100);
         this.connectQos.setMaxSessions(maxSessions);
      }
      
      this.con = this.glob.getXmlBlasterAccess();
      this.con.connect(this.connectQos, this);

      // TODO cleanup in an own method and avoid unsubscribe and disconnect on shutdown ...
      if (this.subscribeKeyList.size() < 1)
         log.severe("init: no subscription has been registered, please check your configuration");
      for (int i=0; i < this.subscribeKeyList.size(); i++) {
         this.con.subscribe((String)this.subscribeKeyList.get(i), (String)this.subscribeQosList.get(i), this);
      }
      
      
      // Make myself available
      info.putObject("org.xmlBlaster.contrib.dbwriter.mom.MomEventEngine", this);
      info.putObject("org.xmlBlaster.contrib.dbwatcher.mom.I_EventEngine", this);
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      try {
         String timestamp = "" + updateQos.getRcvTimestamp().getTimestamp();
         updateQos.getData().addClientProperty("_timestamp", timestamp);
         this.eventHandler.update(updateKey.getOid(), content, updateQos.getClientProperties());
         return "OK";
      }
      catch (Exception ex) {
         ex.printStackTrace();
         throw new XmlBlasterException(this.glob, ErrorCode.USER_CLIENTCODE, "MomEventEngine.update", "user exception", ex);
      }
      catch (Throwable ex) {
         ex.printStackTrace();
         throw new XmlBlasterException(this.glob, ErrorCode.USER_CLIENTCODE, "MomEventEngine.update", "user throwable", ex);
      }
   }


   public void shutdown() throws Exception {
      log.fine("Closing xmlBlaster connection");
      if (this.con != null) {
         this.con.disconnect(null);
         this.con = null;
         this.glob = null;
      }
   }

}
