/*------------------------------------------------------------------------------
Name:      MyExpansionListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jmxgui;

import javax.swing.event.*;
import javax.swing.tree.*;
import javax.swing.*;

import java.awt.event.*;

import java.util.Hashtable;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;


/**
 * Listener for TreeSelectionEvents:<br>
 * If another leaf of the Tree is selected, the class that is associated with
 * the current leaf, is loaded and displayed in the right Pane
 */
public class MyExpansionListener implements TreeSelectionListener {

  private LogChannel log = null;
  private Global glob = null;
  private final String ME = "MyExpansionListener";

  private MainFrame parentFrame = null;

  private JmxPlugin myPanel;

  private Hashtable ht = null;
  public static ComponentController cc = null;
//  private ConnectorClient connectorClient = null;

  public MyExpansionListener(MainFrame parentFrame, Global glob) {
//    this.connectorClient = connectorClient;
    if (glob == null) glob = Global.instance();
    log = glob.getLog("jmxGUI");
    cc = new ComponentController(glob);
    this.parentFrame = parentFrame;
  }

  public void valueChanged(TreeSelectionEvent event) {

    if (myPanel!=null)  {
      parentFrame.removePanel();
    }

    TreePath path = event.getPath();
    Object[] nodes = path.getPath();
    Object obj = nodes[nodes.length-1];
    DefaultMutableTreeNode node = (DefaultMutableTreeNode) obj;

    BasicNode bn = (BasicNode)node.getUserObject();
    String strNode = bn.getName();

    Class c;
    try {
    ht = cc.getClasses();
    c = (Class)ht.get(strNode.trim());
    if ( c != null) {
      myPanel = (JmxPlugin) c.newInstance();
      //TODO: Retrieve servername from tree
      myPanel.setTargetServerName("localhost");
      myPanel.setGlobal(glob);
      parentFrame.PanelContent.add( myPanel );
      }
      else {
        DummyPanel dp = new DummyPanel();
        myPanel =  dp;
        parentFrame.PanelContent.add(myPanel);
      }
    }
    catch (Exception ex) { ex.printStackTrace();
    }
    parentFrame.pack();
  }

  public void treeCollapsed(TreeExpansionEvent event) {
    parentFrame.remove( myPanel );

  }

  DefaultMutableTreeNode getTreeNode(TreePath path) {
    return (DefaultMutableTreeNode) (path.getLastPathComponent());
  }

}
