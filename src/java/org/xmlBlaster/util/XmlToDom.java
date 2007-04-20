/*------------------------------------------------------------------------------
Name:      XmlToDom.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper which parses a XML ASCII string into a DOM tree
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.util.def.ErrorCode;
import java.util.logging.Logger;
import java.util.logging.Level;

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
   protected Global glob;
   private static Logger log = Logger.getLogger(XmlToDom.class.getName());

   protected String xmlKey_literal;
   protected org.w3c.dom.Node rootNode;    // this is always the <key ...>

   /**
    * Parses given xml string
    *
    * @param The original key in XML syntax, for example:<br>
    *        <pre><key oid="This is the unique attribute"></key></pre>
    */
   public XmlToDom(Global glob, String xmlKey_literal) throws XmlBlasterException
   {
      this.glob = (glob == null) ? Global.instance() : glob;

      create(xmlKey_literal);
   }


   /**
    * Creates the DOM tree, this is done delayed when the first access is done.
    * <p />
    * @param xmlKey_literal The ASCII XML string
    */
   public final void create(String xmlKey_literal) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.fine("Creating DOM tree");

      if (this.xmlKey_literal != null) {
         this.rootNode = null;
      }

      this.xmlKey_literal = xmlKey_literal.trim();

      if (!this.xmlKey_literal.startsWith("<")) {
         log.severe("Invalid XML syntax, only XML syntax beginning with \"<\" is supported");
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
      return this.rootNode;
   }


   /**
    * Fills the DOM tree, and assures that a valid <key oid=""> is used.
    * <p>
    * The keyOid will be set properly if no error occurs
    * The rootNode will be set properly if no error occurs
    */
   private void loadDomTree() throws XmlBlasterException
   {
      if (this.rootNode != null)
         return;       // DOM tree is already loaded
      this.rootNode = parseToDomTree(glob, this.xmlKey_literal).getDocumentElement();
   }


   public static org.w3c.dom.Document parseToDomTree(Global glob, String xmlKey_literal) throws XmlBlasterException
   {
      java.io.StringReader reader = new java.io.StringReader(xmlKey_literal);
      InputSource input = new InputSource(reader);
      //input.setEncoding("UTF-8");
      //input.setEncoding("ISO-8859-2");
      //input.setSystemId("9999999999");
      final String ME = "DOMParser";
      if (glob == null) glob = Global.instance();
      try {
         DocumentBuilderFactory dbf = glob.getDocumentBuilderFactory();
         DocumentBuilder db = dbf.newDocumentBuilder();
         return db.parse(input);
      } catch (javax.xml.parsers.ParserConfigurationException e) {
         log.severe("Problems when building DOM parser: " + e.toString() + "\n" + xmlKey_literal);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Problems when building DOM tree from your XML-ASCII string\n" + xmlKey_literal, e);
      } catch (java.io.IOException e) {
         log.severe("Problems when building DOM tree from your XML-ASCII string: " + e.toString() + "\n" + xmlKey_literal);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Problems when building DOM tree from your XML-ASCII string:\n" + xmlKey_literal, e);
      } catch (org.xml.sax.SAXException e) {
         log.warning("Problems when building DOM tree from your XML-ASCII string: " + e.toString() + "\n" + xmlKey_literal);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Problems when building DOM tree from your XML-ASCII string:\n" + xmlKey_literal, e);
      }
   }


   /**
    * Should be called by publish() to merge the local XmlKey DOM into the big xmlBlaster DOM tree
    */
   public final void mergeRootNode(I_MergeDomNode merger) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINE)) log.fine("Entering mergeRootNode() ...");
      this.rootNode = merger.mergeNode(this.rootNode);
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
         java.io.ByteArrayOutputStream out = XmlNotPortable.write(getRootNode());
         StringTokenizer st = new StringTokenizer(out.toString(), "\n");
         while (st.hasMoreTokens()) {
            sb.append(offset).append("   ").append(st.nextToken());
         }
      } catch (Exception e) { log.severe("Problems in writing DOM"); return ""; }
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
