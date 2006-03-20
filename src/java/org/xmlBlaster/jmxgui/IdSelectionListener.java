/*------------------------------------------------------------------------------
Name:      IdSelectionListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jmxgui;

import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.*;


/**
 * Right now handeling status-bar, depending on the selection done in the tree-view
 */
public class IdSelectionListener implements TreeSelectionListener {

  MainFrame parentFrame = null;

  public IdSelectionListener(MainFrame parentFrame){
    this.parentFrame = parentFrame;
  }

/**
 * Changes the text in the status bar at the bottom of the mainWindow
 */
  public void valueChanged(TreeSelectionEvent e) {
    try {
      TreePath path = e.getPath();
      Object[] nodes = path.getPath();
      String oid = "";
      for (int k=0; k<nodes.length; k++) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)nodes[k];
        BasicNode bn = (BasicNode)node.getUserObject();
        oid += ":"+bn.getName();
      }
      System.out.println(oid);
      parentFrame.m_display.setText(oid);
    }
    catch (Exception ex) {
      System.out.println("Fehler: " + e.toString());
    }
  }
}