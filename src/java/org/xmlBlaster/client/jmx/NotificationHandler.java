/*------------------------------------------------------------------------------
Name:      NotificationHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.client.jmx;
import javax.management. *;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.util.admin.extern. *;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.qos. *;
import org.xmlBlaster.client.key. *;
import org.xmlBlaster.util.Global;
import java.util.Properties;
import org.xmlBlaster.util.qos.address.Address;
import javax.swing.JPanel;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io. *;
import org.xmlBlaster.jmxgui.JmxPlugin;

/**
 * Client-sided NotificationHandler<br>
 * Takes subscribtions to Notifications from clients. Invokes a server-sided<br>
 * subscription<p>
 * Update is called, whenever a Notification-event is sent back from the server
 */
public class NotificationHandler implements I_Callback {
   private static Logger log = Logger.getLogger(NotificationHandler.class.getName());
   private static int port = 3424;
   private I_XmlBlasterAccess returnCon;
   private Global glob;
   private SerializeHelper serHelp;

   private String ME = "NotificationHandler";
   private JmxPlugin panel;

   private String beanSource = "";
   private AsyncMBeanServer server;
   private ObjectName objectName;

   public NotificationHandler(String strObjectName, String className, JmxPlugin panel, AsyncMBeanServer server) {
      this.server = server;
      this.beanSource = strObjectName;
      try {
         this.glob = Global.instance();

         if (log.isLoggable(Level.FINER))
            log.severe("Constructor for '" + strObjectName + "' of class '" + className + "'");

         this.objectName = new ObjectName(strObjectName);
//      this.glob = Global.instance().getClone(null);

         serHelp = new SerializeHelper(glob);
         this.panel = panel;
         //connect to embedded xmlBlaster
/*      Address addr = new Address(glob);
      addr.setPort(port);*/

//      Properties prop = new Properties();
//      prop.setProperty("bootstrapHostname","127.0.0.1");
//      prop.setProperty("bootstrapPort","3424");

//      this.glob.init(prop);

//      glob.setBootstrapAddress(addr);
         returnCon = glob.getXmlBlasterAccess();
         SubscribeKey subKey = new SubscribeKey(this.glob, "xmlBlasterMBeans_Notification");
         log.info("NotificationHandler... Trying to connect to service...");
         SubscribeQos	 sQos = new SubscribeQos(this.glob);

         sQos.setWantLocal(false);
//      if (!returnCon.isConnected()) {
//         ConnectQos qos = new ConnectQos(this.glob, "InternalConnector", "connector");
//         ConnectReturnQos rQos = returnCon.connect(qos, this);

//         ConnectReturnQos rQos = returnCon.connect(qos, null);
//      }
//      returnCon.subscribe(subKey, sQos, this);
         returnCon.subscribe(subKey, sQos, this);
         NotificationFilter filter = new UserFilter();

         server.addNotificationListener(objectName, className, filter);
      }
      catch (Exception ex) {
         log.severe("Error when invoking internal Server for Connector: " + ex.toString());
         ex.printStackTrace();
      }
   }
   public void unregister() {
      server.removeNotificationListener(this.objectName);
   }
   public String update(String cbSessionId, UpdateKey updateKey, byte [] content, UpdateQos updateQos)
   {
      Notification    notif = null;

      log.info("Received Notification....");
      try {
         notif = (Notification) serHelp.deserializeObject(content);
      }
      catch (IOException ex) {
      }
      if (this.beanSource.equals(notif.getSource().toString())) {
         log.info("sending update to panel: " + notif.getSource());
         panel.update();
      }
      return "";
   }
   }
