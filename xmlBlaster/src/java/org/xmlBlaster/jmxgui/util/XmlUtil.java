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
import org.jutils.log.LogChannel;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.*;

/**
 * Helperclass for Configuration
 */
public class XmlUtil {
  private LogChannel log = null;
  private Global glob = null;
  private static String ME = "XmlUtil";

  public XmlUtil() {
    this.glob = new Global();
    this.log = this.glob.getLog("jmxGUI");
  }

  private DocumentBuilder docBuilder = null;

  /**
   * Loads configurationfile from local ressourcepath.
   * Parses the configfile into a Document
   * @param filename file where to find configuration
   * @return
   */
  public Document loadConfig (String filename) {
    log.info(ME,"loading config from file " + filename);
    java.net.URL oUrl = null;
    oUrl = this.getClass().getResource(filename);

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
      log.error(ME,"Error parsing xmlString from file " + filename);
      ex.printStackTrace();
    }
    catch (IOException ex) {
      log.error(ME,"Error reading file " + filename);
      ex.printStackTrace();
    }
    return doc;
  }
}