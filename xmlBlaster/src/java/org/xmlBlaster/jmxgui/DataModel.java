/*------------------------------------------------------------------------------
Name:      DataModel.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jmxgui;

import java.util.Vector;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.ImageIcon;
import java.awt.Image;

import org.w3c.dom.Document;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.FileLocator;
import org.xmlBlaster.util.XmlBlasterException;

import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;

import org.xmlBlaster.jmxgui.util.XmlUtil;
import org.xmlBlaster.client.jmx.ConnectorFactory;

/**
 * Encapsulates the TreeModel for the TreeView from the GUI
 */
public class DataModel {

  private XmlUtil xmlUtil = null;
  private DefaultMutableTreeNode top = null;
  private DefaultMutableTreeNode parent = null;
  private Global glob = null;
   private static Logger log = Logger.getLogger(DataModel.class.getName());
  private final String ME = "DataModel";
  private Document config = null;
  private Vector vSubNodes;

  public DataModel(Global glob) throws Exception {
    xmlUtil = new XmlUtil();
    //get Globals and Log
//    if (glob == null) glob = Global.instance();
    this.glob = glob;

    log.info("building new tree...");
    addServerNodes();
    buildTree();
  }

  public DefaultMutableTreeNode getTree() {
    return this.top;
  }


  public void buildTree() {
    vSubNodes = new Vector();
    log.info("Trying to retrieve config from file");
    try {
      config = xmlUtil.loadConfig();
    }
    catch (Exception ex) {
      log.severe("Error when loading plugin config " + ex.toString());
      ex.printStackTrace();
    }

    NodeList nl = config.getElementsByTagName("component");
    for (int i=0; i<nl.getLength(); i++) {
       Node n = nl.item(i);
       NodeList nlParam = n.getChildNodes();
       for (int j =0; j<nlParam.getLength(); j++) {
         Node nParam = nlParam.item(j);
         NodeList nlParamChild = nParam.getChildNodes();
         for (int k =0; k<nlParamChild.getLength(); k++) {
           Node nodeName = nlParamChild.item(k);
           vSubNodes.addElement(nlParamChild.item(k).getNodeValue());
         }
       }
    }


    DefaultMutableTreeNode node = null;
    for (int i=0; i<vSubNodes.size(); i++) {
      BasicNode bNode = new BasicNode(null, (String) vSubNodes.elementAt(i));
//TODO      bNode.setClassName(nodes[i].getClassName());
      bNode.setExpandedIcon(loadIcon("question.gif"));
      bNode.setNormalIcon(loadIcon("question.gif"));
      node = new DefaultMutableTreeNode(bNode);
      parent.add(node);
    }
  }


  public DefaultMutableTreeNode addServerNodes() throws Exception {
      Object[] nodes = new Object[5];
      BasicNode rootNode = new BasicNode(null, "xmlBlaster");
      rootNode.setExpandedIcon(loadIcon("globe.gif"));
      rootNode.setNormalIcon(loadIcon("globe.gif"));
      top = new DefaultMutableTreeNode(rootNode);

      parent = top;
      nodes[0] = top;
      DefaultMutableTreeNode node = null;
      
      ConnectorFactory connectorFactory = ConnectorFactory.getInstance(this.glob);
      connectorFactory.getMBeanServer("127.0.0.1");
      String[] servers = connectorFactory.getMBeanServerList();
      
      if (servers == null) throw new Exception("could not connect to any host! ");
      for (int i=0; i<servers.length; i++) {
        BasicNode bNode = new BasicNode(null, servers[i]);
        bNode.setExpandedIcon(loadIcon("node.gif"));
        bNode.setNormalIcon(loadIcon("node.gif"));
        node = new DefaultMutableTreeNode(bNode);
        parent.add(node);
      }
      parent = node;
      nodes[1] = parent;

  return top;

}

public ImageIcon loadIcon(String filename){
  ImageIcon icon = null;
  java.net.URL oUrl;
  oUrl = this.getClass().getResource(filename);
  Image img;
  img = java.awt.Toolkit.getDefaultToolkit().getImage(oUrl);
  if(img != null)
  {
    icon = new ImageIcon(img);
  }
  else
  {
    log.warning(filename +" not found");
  }
  return icon;
}


}
