/*------------------------------------------------------------------------------
Name:      XmlToDom.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper which parses a XML ASCII string into a DOM tree
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.util.def.ErrorCode;
import org.jutils.log.LogChannel;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;

import org.xml.sax.InputSource;
import org.w3c.dom.NamedNodeMap;
import java.util.StringTokenizer;


/**
 * Helper which parses a XML ASCII string into a DOM tree.
 * <p>
 */
public class XmlToDom
{
   private String ME = "XmlToDom";
   private final LogChannel log;

   protected String xmlKey_literal;

   protected org.w3c.dom.Document xmlDoc = null;  // the parsed xmlKey_literal DOM
   protected org.w3c.dom.Node rootNode = null;    // this is always the <key ...>

   protected Global glob;
   /**
    * Parses given xml string
    *
    * @param The original key in XML syntax, for example:<br>
    *        <pre><key oid="This is the unique attribute"></key></pre>
    */
   public XmlToDom(Global glob, String xmlKey_literal) throws XmlBlasterException
   {
      this.glob = glob;
      log = this.glob.getLog("core");
      create(xmlKey_literal);
   }


   /**
    * Creates the DOM tree, this is done delayed when the first access is done.
    * <p />
    * @param xmlKey_literal The ASCII XML string
    */
   public final void create(String xmlKey_literal) throws XmlBlasterException
   {
      if (log.CALL) log.trace(ME, "Creating DOM tree");

      if (this.xmlKey_literal != null) {
         xmlDoc = null;
         rootNode = null;
      }

      this.xmlKey_literal = xmlKey_literal.trim();

      if (!this.xmlKey_literal.startsWith("<")) {
         log.error(ME+".XML", "Invalid XML syntax, only XML syntax beginning with \"<\" is supported");
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Invalid XML syntax, only XML syntax beginning with \"<\" is supported");

      }
   }


   /**
    * Access the unparsed, literal ASCII xmlKey.
    * @return the literal ASCII xmlKey
    */
   public final String toString()
   {
      return xmlKey_literal == null ? "" : xmlKey_literal;
   }


   /**
    * Access the unparsed, literal ASCII xmlKey.
    * @return the literal ASCII xmlKey
    */
   public final String literal()
   {
      return xmlKey_literal == null ? "" : xmlKey_literal;
   }


   /**
    * The syntax of the XmlKey_literal String.
    */
   public final NamedNodeMap getRootAttributes() throws XmlBlasterException
   {
      return getRootNode().getAttributes();
   }


   /**
    * Fills the DOM tree, and assures that a valid <key oid=""> is used. 
    */
   public final org.w3c.dom.Node getRootNode() throws XmlBlasterException
   {
      loadDomTree();
      return rootNode;
   }


   /**
    * Fills the DOM tree, and assures that a valid <key oid=""> is used. 
    */
   public final org.w3c.dom.Document getXmlDoc() throws XmlBlasterException
   {
      loadDomTree();
      return xmlDoc;
   }


   /**
    * Fills the DOM tree, and assures that a valid <key oid=""> is used. 
    * <p>
    * The keyOid will be set properly if no error occurs
    * The rootNode will be set properly if no error occurs
    */
   private void loadDomTree() throws XmlBlasterException
   {
      if (xmlDoc != null)
         return;       // DOM tree is already loaded

      java.io.StringReader reader = new java.io.StringReader(xmlKey_literal);
      InputSource input = new InputSource(reader);
      //input.setEncoding("UTF-8");
      //input.setEncoding("ISO-8859-2");
      //input.setSystemId("9999999999");

      try {
         DocumentBuilderFactory dbf = glob.getDocumentBuilderFactory();
         DocumentBuilder db = dbf.newDocumentBuilder();
         xmlDoc = db.parse(input);
      } catch (javax.xml.parsers.ParserConfigurationException e) {
         log.error(ME+".IO", "Problems when building DOM parser: " + e.toString() + "\n" + xmlKey_literal);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Problems when building DOM tree from your XML-ASCII string\n" + xmlKey_literal, e);
      } catch (java.io.IOException e) {
         log.error(ME+".IO", "Problems when building DOM tree from your XML-ASCII string: " + e.toString() + "\n" + xmlKey_literal);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Problems when building DOM tree from your XML-ASCII string:\n" + xmlKey_literal, e);
      } catch (org.xml.sax.SAXException e) {
         log.warn(ME+".SAX", "Problems when building DOM tree from your XML-ASCII string: " + e.toString() + "\n" + xmlKey_literal);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Problems when building DOM tree from your XML-ASCII string:\n" + xmlKey_literal, e);
      }

      rootNode = xmlDoc.getDocumentElement();
   }


   /**
    * Should be called by publish() to merge the local XmlKey DOM into the big xmlBlaster DOM tree
    */
   public final void mergeRootNode(I_MergeDomNode merger) throws XmlBlasterException
   {
      org.w3c.dom.Node tmpRootNode = rootNode;
      if (log.TRACE) log.trace(ME, "Entering mergeRootNode() ...");
      /*org.w3c.dom.Node node = */merger.mergeNode(tmpRootNode);
      rootNode = tmpRootNode;  // everything successful, assign the rootNode
   }


   /**
    * Dump DOM tree to XML ASCII String. 
    * <p />
    * A header like "<?xml version="1.0" encoding="UTF-8"?>" is removed
    * @param offset indenting of tags with given blanks e.g. "   "
    * @return string with key meta data in XML syntax
    */
   public String domToXml(String offset)
   {
      StringBuffer sb = new StringBuffer();
      try {
         java.io.ByteArrayOutputStream out = XmlNotPortable.write(getXmlDoc());
         StringTokenizer st = new StringTokenizer(out.toString(), "\n");
         while (st.hasMoreTokens()) {
            sb.append(offset).append("   ").append(st.nextToken());
         }
      } catch (Exception e) { log.error(ME, "Problems in writing DOM"); return ""; }
      String nice = sb.toString();
      int index = nice.indexOf("?>");   // Remove header line "<?xml version="1.0" encoding="UTF-8"?>"
      if (index > 0)
         return sb.substring(index+2);
      return nice;
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @return XML string
    */
   public final StringBuffer printOn() throws XmlBlasterException
   {
      return printOn((String)null);
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML string
    */
   public final StringBuffer printOn(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<XmlToDom>");
      sb.append(domToXml(offset + "   "));
      sb.append(offset + "</XmlToDom>");
      return sb;
   }
}
