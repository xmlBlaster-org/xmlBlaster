/*------------------------------------------------------------------------------
Name:      XmlToDom.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper which parses a XML ASCII string into a DOM tree
Version:   $Id: XmlToDom.java,v 1.14 2001/02/24 23:17:04 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.util.Log;

import org.xml.sax.InputSource;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import java.util.StringTokenizer;


/**
 * Helper which parses a XML ASCII string into a DOM tree.
 * <p>
 */
public class XmlToDom
{
   private String ME = "XmlToDom";

   protected String xmlKey_literal;

   protected org.w3c.dom.Document xmlDoc = null;  // the parsed xmlKey_literal DOM
   protected org.w3c.dom.Node rootNode = null;    // this is always the <key ...>


   /**
    * Parses given xml string
    *
    * @param The original key in XML syntax, for example:<br>
    *        <pre><key oid="This is the unique attribute"></key></pre>
    */
   public XmlToDom(String xmlKey_literal) throws XmlBlasterException
   {
      create(xmlKey_literal);
   }


   /**
    * Creates the DOM tree, this is done delayed when the first access is done.
    * <p />
    * @param xmlKey_literal The ASCII XML string
    */
   public final void create(String xmlKey_literal) throws XmlBlasterException
   {
      if (Log.CALL) Log.trace(ME, "Creating DOM tree");

      if (this.xmlKey_literal != null) {
         xmlDoc = null;
         rootNode = null;
      }

      this.xmlKey_literal = xmlKey_literal.trim();

      if (!this.xmlKey_literal.startsWith("<")) {
         Log.error(ME+".XML", "Invalid XML syntax, only XML syntax beginning with \"<\" is supported");
         throw new XmlBlasterException(ME+".XML", "Invalid XML syntax, only XML syntax beginning with \"<\" is supported");

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
    * Fills the DOM tree, and assures that a valid <key oid="..."> is used
    */
   public final org.w3c.dom.Node getRootNode() throws XmlBlasterException
   {
      loadDomTree();
      return rootNode;
   }


   /**
    * Fills the DOM tree, and assures that a valid <key oid="..."> is used
    */
   public final org.w3c.dom.Document getXmlDoc() throws XmlBlasterException
   {
      loadDomTree();
      return xmlDoc;
   }


   /**
    * Fills the DOM tree, and assures that a valid <key oid="..."> is used.
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
      //input.setEncoding("ISO-8859-2");
      //input.setSystemId("9999999999");

      com.jclark.xsl.dom.XMLProcessorImpl xmlProc = XmlProcessor.getInstance().getXmlProcessorImpl();

      try {
         xmlDoc = xmlProc.load(input);
      } catch (java.io.IOException e) {
         Log.error(ME+".IO", "Problems when building DOM tree from your XML-ASCII string: " + e.toString() + "\n" + xmlKey_literal);
         throw new XmlBlasterException(ME+".IO", "Problems when building DOM tree from your XML-ASCII string: " + e.toString() + "\n" + xmlKey_literal);
      } catch (org.xml.sax.SAXException e) {
         Log.error(ME+".SAX", "Problems when building DOM tree from your XML-ASCII string: " + e.toString() + "\n" + xmlKey_literal);
         throw new XmlBlasterException(ME+".SAX", "Problems when building DOM tree from your XML-ASCII string: " + e.toString() + "\n" + xmlKey_literal);
      }

      rootNode = xmlDoc.getDocumentElement();
   }


   /**
    * Should be called by publish() to merge the local XmlKey DOM into the big xmlBlaster DOM tree
    */
   public final void mergeRootNode(I_MergeDomNode merger) throws XmlBlasterException
   {
      org.w3c.dom.Node tmpRootNode = rootNode;
      if (Log.TRACE) Log.trace(ME, "Entering mergeRootNode() ...");
      org.w3c.dom.Node node = merger.mergeNode(tmpRootNode);
      rootNode = tmpRootNode;  // everything successful, assign the rootNode
   }


   /**
    * Dump DOM tree to XML ASCII String.
    * <p />
    * @param offset indenting of tags with given blanks e.g. "   "
    * @return string with key meta data in XML syntax
    */
   public String domToXml(String offset)
   {
      StringBuffer sb = new StringBuffer();
      try {
         java.io.ByteArrayOutputStream out = XmlNotPortable.write(xmlDoc);
         StringTokenizer st = new StringTokenizer(out.toString(), "\n");
         while (st.hasMoreTokens()) {
            sb.append(offset + st.nextToken());
         }
      } catch (Exception e) {
         return "";
      }
      return sb.toString();
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @return XML state of MessageUnitHandler
    */
   public final StringBuffer printOn() throws XmlBlasterException
   {
      return printOn((String)null);
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of MessageUnitHandler
    */
   public final StringBuffer printOn(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<XmlToDom>");
      sb.append(domToXml(offset + "   "));
      sb.append(offset + "</XmlToDom>\n");
      return sb;
   }
}
