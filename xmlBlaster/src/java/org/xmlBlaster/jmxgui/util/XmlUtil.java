/*------------------------------------------------------------------------------
Name:      UserFilter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jmxgui.util;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.*;

/**
 * Helperclass for Configuration
 */
public class XmlUtil {
   private static Logger log = Logger.getLogger(XmlUtil.class.getName());
  private Global glob = null;
  private static String ME = "XmlUtil";

  public XmlUtil() {
    this.glob = new Global();

  }

  private DocumentBuilder docBuilder = null;

  /**
   * Loads the configuration file.
   * Parses the configfile into a Document
   * @param filename file where to find configuration
   * @return
   */
  public Document loadConfig () {
  	String filename = "jmxgui.xml";
  	String propertyName = "jmxgui.config";
    log.info("loading config from file '" + filename + "' or property '" + propertyName + "'");
    
    java.net.URL oUrl = null;
    oUrl = (new FileLocator(this.glob)).findFileInXmlBlasterSearchPath(propertyName, filename);

    Document doc = null;
    try {
      try {
        docBuilder  = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      } catch (Exception e) {
        System.err.println(e.getMessage());
      }
      InputSource in = new InputSource(oUrl.openStream());
      doc = docBuilder.parse(in);
    }
    catch (SAXException ex) {
      log.severe("Error parsing xmlString from file " + filename);
      ex.printStackTrace();
    }
    catch (IOException ex) {
      log.severe("Error reading file " + filename);
      ex.printStackTrace();
    }
    return doc;
  }
}