/*------------------------------------------------------------------------------
Name:      LogPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jmxgui.plugins.logPlugin;

import java.awt.*;
import org.xmlBlaster.jmxgui.*;

import javax.management.*;
import org.xmlBlaster.client.jmx.*;
import javax.swing.*;
import org.xmlBlaster.util.Global;
import java.rmi.*;

public class LogPlugin extends JmxPlugin {
  private ConnectorClient cc = null;
  private String MBean = "xmlBlaster:name=log";
  private GridBagLayout gridBagLayout1 = new GridBagLayout();
  private JButton jButton1;
//  public MBeanOperationInfo[] op = null;
  private Global glob;

  private String serverName ="";

  private NotificationListener listener = null;
  private NotificationFilter filter =null;
  String str = "";
  JTextArea text = new JTextArea();
  AsyncMBeanServer server;
  NotificationHandler nh;

  public LogPlugin() {
    try {
      this.cc = new ConnectorClient(null, serverName);
      jbInit();
    }
    catch(Exception ex) {
      ex.printStackTrace();
    }
  }

  public void setTargetServerName(String server) {
    this.serverName = server;
  }

  void jbInit() throws Exception {
    this.setLayout(gridBagLayout1);


    this.server = cc.getServer();

    nh = new NotificationHandler(MBean, "org.xmlBlaster.util.admin.extern.JmxLogger", this, server);
    JButton[] buttons = createButtonsForOperation(MBean, "org.xmlBlaster.util.admin.extern.JmxLogger");
    GridBagLayout gbl = new GridBagLayout();
    GridBagConstraints gbc = new GridBagConstraints();

    Insets inset = new Insets(1,1,1,1);
    this.setLayout(gbl);
    JButton button = new JButton();
    int yPos=0;
    for (int i = 0; i<buttons.length; i++) {
      try {
        button = buttons[i];
        button.setPreferredSize(new Dimension(180,27));
        this.add(button, new GridBagConstraints(0,i,1,1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, inset,0,0));
        yPos++;
      }
      catch (Exception ex) {
        ex.printStackTrace();
      }
    }
    text.setPreferredSize(new Dimension(400,108));
    update();
    this.add(text, new GridBagConstraints(0,yPos++,1,1, 0.0, 0.0, GridBagConstraints.SOUTH, GridBagConstraints.NONE, inset,0,0));

  }

  public void update() {
    try {
      server.createMBean("org.xmlBlaster.util.admin.extern.JmxLogger",new ObjectName(MBean));
      str =  (String) server.getAttribute(new ObjectName(MBean), "LogText").get();
      text.append(str);
    }
    catch (RemoteException ex) {
    }catch (MalformedObjectNameException ex) {
    }
  }

  public void finalize() {
    cc.logout();
    nh.unregister();
  }

  public void setGlobal(Global glob) {
    this.glob = glob;
  }
}