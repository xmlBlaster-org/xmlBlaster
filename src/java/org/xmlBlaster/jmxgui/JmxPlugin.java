/*------------------------------------------------------------------------------
Name:      JmxPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jmxgui;

import org.xmlBlaster.util.Global;
import javax.management.*;
import javax.swing.*;
import java.awt.event.*;
import java.util.Vector;
import org.xmlBlaster.client.jmx.*;
import org.xmlBlaster.util.Global;

/**
 * SuperClass for Panels that handle with MBeans.
 * Offers generic operation for the creation of Buttons that are based
 * on the MBeanOperations.

 */
public abstract class JmxPlugin extends javax.swing.JPanel{
  private String globalMBeanName ="";
  protected ConnectorClient cc = null;
  protected String currentServer ="";
  private Global glob;

//TODO: Retrieve current server!

  public JmxPlugin() {
    super();
//    ConnectorClient cc = new ConnectorClient(Global.instance());
  }

  public JmxPlugin(ConnectorClient cc ) {
    super();
    this.cc = cc;
  }

  public void setConnectorClient(ConnectorClient cc) {
    this.cc = cc;
  }

  public abstract void setTargetServerName(String server);

  public String getServer() {
    return this.currentServer;
  }

  public JButton[] createButtonsForOperation(String MBeanName, String MBeanClassName) {
    Vector vecButtons = new Vector();
    JButton jButton1;
    MBeanOperationInfo[] op = null;

    this.globalMBeanName = MBeanName;
    if (this.cc == null) cc = new ConnectorClient(null, currentServer);

    try {
      ObjectName name = new ObjectName(MBeanName);
      boolean isLoaded = ((Boolean) (cc.getServer().isRegistered(name).get())).booleanValue();
      if (!isLoaded) {
        loadMBean(MBeanName, MBeanClassName);
      }
      else{
      }
    }
    catch (Exception ex) {
    }
    op = cc.exploreMBeanOperationsByObjectName(MBeanName);
    for (int i=0; i<op.length; i++) {
      jButton1 = new JButton();
      jButton1.setText(op[i].getName());
      jButton1.setToolTipText(op[i].getDescription());
      jButton1.setText(op[i].getName());
      jButton1.setToolTipText(op[i].getDescription());
      jButton1.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(ActionEvent e) {
          try {
            cc.getServer().invoke(new ObjectName(globalMBeanName), e.getActionCommand(), new Object[] {}, new String[] {});
          }
          catch (Exception ex) {
            ex.printStackTrace();
          }
        }
      });
      vecButtons.addElement(jButton1);
    }
    JButton[] buttons = new JButton[vecButtons.size()];
    vecButtons.copyInto(buttons);
    return buttons;
  }


  public void loadMBean(String objectName, String className) {
  try {
    ObjectName myName = new ObjectName(objectName);
    cc.getServer().createMBean(className ,myName,null);
  }
  catch (Exception ex) {
  }
  }

/*  public void setGlobal(Global glob) {
  this.glob = glob;
  }*/

  public abstract void setGlobal(Global glob);

  public abstract void update();


}
