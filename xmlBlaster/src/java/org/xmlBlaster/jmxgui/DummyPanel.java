package org.xmlBlaster.jmxgui;

import java.awt.*;
import javax.swing.*;

public class DummyPanel extends JmxPlugin {
  private BorderLayout borderLayout1 = new BorderLayout();
  private JTabbedPane jTabbedPane1 = new JTabbedPane();
  private JTextArea jTextArea1 = new JTextArea();
  private ConnectorClient cc =null;

  public DummyPanel() {
    try {
      jbInit();
    }
    catch(Exception ex) {
      ex.printStackTrace();
    }

  }

  void jbInit() throws Exception {
  }

  public void setTargetServerName(String server){
  }

  public void update(){
  }

  public void setGlobal(org.xmlBlaster.util.Global glob){
  }

}