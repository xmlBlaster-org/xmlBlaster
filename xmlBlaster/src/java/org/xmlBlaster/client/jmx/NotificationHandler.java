/*------------------------------------------------------------------------------
Name:      NotificationHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.jmx;

import javax.management.*;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.util.admin.extern.*;
import org.xmlBlaster.client.I_XmlBlasterAccess;

import org.xmlBlaster.client.qos.*;
import org.xmlBlaster.client.key.*;

import org.xmlBlaster.util.Global;
import java.util.Properties;
import org.xmlBlaster.util.qos.address.Address;
import javax.swing.JPanel;

import org.jutils.log.LogChannel;
import java.io.*;
import org.xmlBlaster.jmxgui.JmxPlugin;

/**
 * Client-sided NotificationHandler<br>
 * Takes subscribtions to Notifications from clients. Invokes a serversided<br>
 * subscription<p>
 * Update is called, when Notification-event is sent back from the server
 */
public class NotificationHandler implements I_Callback  {

  private LogChannel log = null;
  private static int port = 3424;
  private I_XmlBlasterAccess returnCon;
  private Global glob;
  private SerializeHelper serHelp;
  private String ME = "NotificationHandler";
  private JmxPlugin panel;
  private String beanSource ="";
  private AsyncMBeanServer server;
  private ObjectName objectName;

  public NotificationHandler(String strObjectName, String className, JmxPlugin panel, AsyncMBeanServer server) {
    this.server = server;
    this.beanSource = strObjectName;
    try {
      this.objectName = new ObjectName(strObjectName);
      Global glob = new Global();
      this.glob = glob;
      serHelp = new SerializeHelper(glob);
      log = glob.getLog("jmxGUI");
      this.panel = panel;


      //connect to embedded xmlBlaster
/*      Address addr = new Address(glob);
      addr.setPort(port);*/
      Properties embeddedProp = new Properties();
      embeddedProp.setProperty("bootstrapPort","3424");
      glob.init(embeddedProp);


//      glob.setBootstrapAddress(addr);
      returnCon = glob.getXmlBlasterAccess();
      SubscribeKey subKey = new SubscribeKey(this.glob, "xmlBlasterMBeans_Notification");

      log.info(ME,"NotificationHandler... Trying to connect to service...");
      SubscribeQos sQos = new SubscribeQos(this.glob);
      sQos.setWantLocal(false);
      ConnectQos qos = new ConnectQos(glob, "InternalConnector", "connector");
      ConnectReturnQos rQos = returnCon.connect(qos, this);
      returnCon.subscribe(subKey, sQos);
      NotificationFilter filter = new UserFilter();
      server.addNotificationListener(objectName, className, filter);
    }
    catch (Exception ex) {
      log.error(ME,"Error when invoking internal Server for Connector: " + ex.toString());
      ex.printStackTrace();
    }
  }

  public void unregister() {
    server.removeNotificationListener(this.objectName);
  }


  public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
  {
    Notification notif = null;
    log.info(ME,"Received Notification....");
    try {
      notif = (Notification) serHelp.deserializeObject(content);
    }
    catch (IOException ex) {
    }
    if (this.beanSource.equals(notif.getSource().toString())) {
      log.info(ME,"sending update to panel: "+notif.getSource());
      panel.update();
    }
    return "";
  }

}