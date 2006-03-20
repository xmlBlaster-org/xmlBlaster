/*------------------------------------------------------------------------------
Name:      NodeInfoPanel.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jmxgui.plugins.infoPlugin;

import org.xmlBlaster.jmxgui.*;

import java.awt.*;
import javax.swing.*;
import javax.swing.event.TableModelListener;
import javax.swing.event.TableModelEvent;

import org.xmlBlaster.client.jmx.*;

import org.xmlBlaster.jmxgui.plugins.GenericJmxModel;
import org.xmlBlaster.util.Global;

/**
 * Panel that displays basic information about a node
 */
public class NodeInfoPanel extends JmxPlugin implements TableModelListener {
  private GridBagLayout gridBagLayout1 = new GridBagLayout();
  private String MBean = "xmlBlaster:name=requestBroker";
  private String MBeanClass = "org.xmlBlaster.engine.RequestBroker";
  private JTable jTable;
  private ConnectorClient connectorClient;
  private JButton button = null;
  private Global glob;
  private GenericJmxModel model2;

  private String serverName="";
  JPanel panelOne = new JPanel();
  JTabbedPane tp = new JTabbedPane();

  public void setGlobal(Global glob) {
    this.glob = glob;
  }

  public NodeInfoPanel() {
    this.connectorClient = new ConnectorClient(null, serverName);

    try {
      AsyncMBeanServer server = connectorClient.getServer();
      model2 = new GenericJmxModel(connectorClient, MBean, MBeanClass);
      model2.addTableModelListener(this);
      jTable = new JTable(model2);
      jTable.setAutoscrolls(true);
      jTable.setColumnSelectionAllowed(true);
      jTable.setAutoResizeMode(jTable.AUTO_RESIZE_ALL_COLUMNS);
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }

    try {
      jbInit();
    }
    catch(Exception ex) {
      ex.printStackTrace();
    }
  }
  void jbInit() throws Exception {
    panelOne.add(new JScrollPane(jTable));
    tp.add(panelOne, "Info");
    this.add(tp);
  }

  public void setTargetServerName(String server) {
    this.serverName = server;
  }

  public void finalize() {
//    connectorClient.logout();
  }

  public void update() {
    model2.loadData();
  }

  public void tableChanged(TableModelEvent e) {
  }


}
