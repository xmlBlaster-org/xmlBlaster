/*------------------------------------------------------------------------------
Name:      UserFilter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jmxgui.util;

import org.xml.sax.InputSource;
import org.xml.sax.Parser;
import org.xml.sax.SAXException;
import org.w3c.dom.Document;

import org.xml.sax.helpers.XMLReaderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.*;

import org.xmlBlaster.util.FileLocator;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.*;

/**
 * Helperclass for Configuration
 */
public class XmlUtil {

  private DocumentBuilder docBuilder = null;

  public Document loadConfig (String filename) {
  FileLocator locator = new FileLocator(new Global().instance());
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
    ex.printStackTrace();
  }
  catch (IOException ex) {
    ex.printStackTrace();
  }
  return doc;
}

}