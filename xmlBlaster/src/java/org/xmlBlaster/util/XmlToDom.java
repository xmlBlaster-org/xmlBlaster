/*------------------------------------------------------------------------------
Name:      XmlToDom.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper which parses a XML ASCII string into a DOM tree
Version:   $Id: XmlToDom.java,v 1.3 1999/11/30 10:37:35 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xml.sax.InputSource;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import java.util.StringTokenizer;


/**
 * XmlToDom.
 * <p>
 * Helper which parses a XML ASCII string into a DOM tree
 *
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
    * Creates the DOM tree, this is done delayed when the first access is done
    */
   public final void create(String xmlKey_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.trace(ME, "Creating DOM tree");

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
    * @return the literal ASCII xmlKey
    */
   public final String toString()
   {
      return xmlKey_literal == null ? "" : xmlKey_literal;
   }


   /**
    * @return the literal ASCII xmlKey
    */
   public final String literal()
   {
      return xmlKey_literal == null ? "" : xmlKey_literal;
   }


   /**
    * The syntax of the XmlKey_literal String
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

      // !!! TODO: add a singleton for XMLProcessor
      com.jclark.xsl.dom.XMLProcessorImpl xmlProc = org.xmlBlaster.engine.MessagesDOM.getInstance().getXMLProcessorImpl();

      try {
         xmlDoc = xmlProc.load(input);
      } catch (java.io.IOException e) {
         Log.error(ME+".IO", "Problems when building DOM tree from your XML-ASCII string: " + e.toString());
         throw new XmlBlasterException(ME+".IO", "Problems when building DOM tree from your XML-ASCII string: " + e.toString());
      } catch (org.xml.sax.SAXException e) {
         Log.error(ME+".SAX", "Problems when building DOM tree from your XML-ASCII string: " + e.toString());
         throw new XmlBlasterException(ME+".SAX", "Problems when building DOM tree from your XML-ASCII string: " + e.toString());
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
      try {
         java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
         ((com.sun.xml.tree.XmlDocument)xmlDoc).write(out);
         StringTokenizer st = new StringTokenizer(out.toString(), "\n");
         while (st.hasMoreTokens()) {
            sb.append(offset + "   " + st.nextToken());
         }
      } catch (Exception e) { }
      sb.append(offset + "</XmlToDom>\n");
      return sb;
   }
}
