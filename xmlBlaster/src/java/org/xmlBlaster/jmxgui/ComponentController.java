/*------------------------------------------------------------------------------
Name:      ComponentController.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jmxgui;

import org.w3c.dom.Document;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.NamedNodeMap;
import org.xml.sax.*;

import java.util.Hashtable;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.jmxgui.util.XmlUtil;

/**
 * Class that loads the basic configuration and wraps the classes that can be
 * obtained from the GUI within a Hashtable
 */
public class ComponentController {

  private XmlUtil xmlUtil = null;
   private static Logger log = Logger.getLogger(ComponentController.class.getName());
  private Global glob = null;
  private Hashtable hObjects;
  private static String ME = "ComponentController";

  public ComponentController(Global glob) {
    if (glob == null) glob = Global.instance();
    xmlUtil = new XmlUtil();

    hObjects = new Hashtable();
    Document doc = xmlUtil.loadConfig();
    buildDOM(doc);
  }

  private void buildDOM(Document doc) throws DOMException {
    NodeList nl = doc.getElementsByTagName("component");
    for (int i=0; i<nl.getLength(); i++) {
      Node n = nl.item(i);
      NamedNodeMap nn = n.getAttributes();
      String className = nn.getNamedItem("class").getNodeValue();
      String key = nn.getNamedItem("name").getNodeValue();
      log.info(" name of the class to load: " + className);
      log.info("class will be stored with key " + key+ " in hashtable");
      try {
        Class cl = java.lang.Class.forName(className);
        if (cl!=null){
          hObjects.put(key, cl);
        }
      }
      catch (ClassNotFoundException ex) {
        log.warning("class "+ className+ " not found in classpath!");
      }
    }
  }
/**
 * Returns classes that are defined within configfile
 */
  public Hashtable getClasses() {
    return this.hObjects;
  }
}
