/*------------------------------------------------------------------------------
Name:      XmlKeyDom.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Building a huge DOM tree for all known MessageUnit xmlKey
Version:   $Id: XmlKeyDom.java,v 1.8 2000/07/02 17:21:33 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.xml2java;

import org.jutils.log.Log;

import org.xmlBlaster.engine.RequestBroker;
import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.util.I_MergeDomNode;
import org.xmlBlaster.util.XmlQoSBase;
import org.xmlBlaster.util.XmlBlasterException;

import java.util.Vector;
import java.util.Enumeration;
import java.util.StringTokenizer;

/**
 * Building a DOM tree for XmlKeys.
 * <p />
 * This DOM tree contains the meta data from XmlKey.<br />
 */
public class XmlKeyDom implements I_MergeDomNode
{
   final private static String ME = "XmlKeyDom";

   protected com.fujitsu.xml.omquery.DomQueryMgr queryMgr = null;

   protected com.sun.xml.tree.XmlDocument xmlKeyDoc = null;// Sun's DOM extensions, no portable
   //protected org.w3c.dom.Document xmlKeyDoc = null;      // Document with the root node
   protected org.w3c.dom.Node xmlKeyRootNode = null;       // Root node <xmlBlaster></xmlBlaster>

   protected String encoding = "ISO-8859-1";             // !!! TODO: access from xmlBlaster.properties file
                                                         // default is "UTF-8"
   protected RequestBroker requestBroker = null;


   /**
    * Constructor to handle a DOM with XPath query.
    */
   protected XmlKeyDom(RequestBroker requestBroker) throws XmlBlasterException
   {
      this.requestBroker = requestBroker;

      /*
      // Instantiate the xmlBlaster DOM tree with <xmlBlaster> root node (DOM portable)
      String xml = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                   "<xmlBlaster></xmlBlaster>";
      java.io.StringReader reader = new java.io.StringReader(xml);
      org.xml.sax.InputSource input = new org.xml.sax.InputSource(reader);

      try {
         xmlKeyDoc = XmlProcessor.getInstance().load(input);
      } catch (java.io.IOException e) {
         Log.error(ME+".IO", "Problems when building DOM tree from your XmlKey: " + e.toString());
         throw new XmlBlasterException(ME+".IO", "Problems when building DOM tree from your XmlKey: " + e.toString());
      } catch (org.xml.sax.SAXException e) {
         Log.error(ME+".SAX", "Problems when building DOM tree from your XmlKey: " + e.toString());
         throw new XmlBlasterException(ME+".SAX", "Problems when building DOM tree from your XmlKey: " + e.toString());
      }
      */

      // Using Sun's approach to be able to use  com.sun.xml.tree.XmlDocument::changeNodeOwner(node) later
      xmlKeyDoc = new com.sun.xml.tree.XmlDocument ();
      com.sun.xml.tree.ElementNode root = (com.sun.xml.tree.ElementNode) xmlKeyDoc.createElement("xmlBlaster");
      xmlKeyDoc.appendChild(root);
      xmlKeyRootNode = xmlKeyDoc.getDocumentElement();
   }


   /**
    * Accesing the query manager for XPath.
    * <p />
    * queryMgr is instantiated if null
    * @return the query manager
    */
   protected final com.fujitsu.xml.omquery.DomQueryMgr getQueryMgr()
   {
      if (queryMgr == null)
         queryMgr = new com.fujitsu.xml.omquery.DomQueryMgr(xmlKeyDoc);
      return queryMgr;
   }


   /**
    * Adding a new &lt;key> node to the xmlBlaster xmlKey tree.
    * <p />
    * This method is forced by the interface I_MergeDomNode
    * @param the node to merge into the DOM tree
    * @return the node added
    */
   public final org.w3c.dom.Node mergeNode(org.w3c.dom.Node node) throws XmlBlasterException
   {
      try {     // !!! synchronize is missing !!!
         if (Log.TRACE) Log.trace(ME, "mergeNode=" + node.toString());

         xmlKeyDoc.changeNodeOwner(node);  // com.sun.xml.tree.XmlDocument::changeNodeOwner(node) // not DOM portable

         // !!! PENDING: If same key oid exists, remove the old and replace with new

         xmlKeyRootNode.appendChild(node);

         if (Log.TRACE) Log.trace(ME, "Successfully merged tree");

         // if (Log.DUMP) Log.dump(ME, printOn().toString());  // dump the whole tree

         queryMgr = null; // needs to be reloaded, since the Document changed

         return node;

      } catch (Exception e) {
         Log.error(ME+".mergeNode", "Problems adding new key tree: " + e.toString());
         e.printStackTrace();
         throw new XmlBlasterException(ME+".mergeNode", "Problems adding new key tree: " + e.toString());
      }
   }


   /**
    * This method does the XPath query.
    *
    * @param clientName is only needed for nicer logging output
    * @return Array of matching XmlKey objects (may contain null elements)
    *
    * TODO: a query Handler, allowing drivers for REGEX, XPath, SQL, etc. queries
    */
   public final Vector parseKeyOid(ClientInfo clientInfo, XmlKey xmlKey, XmlQoSBase qos)  throws XmlBlasterException
   {
      Vector xmlKeyVec = new Vector();
      String clientName = clientInfo.toString();

      if (xmlKey.getQueryType() == XmlKey.XPATH_QUERY) { // query: subscription without a given oid

         Enumeration nodeIter;
         try {
            if (Log.TRACE) Log.trace(ME, "Goin' to query DOM tree with XPATH = " + xmlKey.getQueryString());
            nodeIter = getQueryMgr().getNodesByXPath(xmlKeyDoc, xmlKey.getQueryString());
         } catch (Exception e) {
            Log.warning(ME + ".InvalidQuery", "Sorry, can't access, query snytax is wrong for '" + xmlKey.getQueryString() + "' : " + e.toString());
            e.printStackTrace();
            throw new XmlBlasterException(ME + ".InvalidQuery", "Sorry, can't access, query snytax is wrong");
         }
         int n = 0;
         while (nodeIter.hasMoreElements()) {
            n++;
            Object obj = nodeIter.nextElement();
            com.sun.xml.tree.ElementNode node = (com.sun.xml.tree.ElementNode)obj;
            try {
               String uniqueKey = getKeyOid(node);
               Log.info(ME, "Client " + clientName + " is accessing message oid=\"" + uniqueKey + "\" after successful query");
               xmlKeyVec.addElement(requestBroker.getXmlKeyFromOid(uniqueKey));
            } catch (Exception e) {
               e.printStackTrace();
               Log.error(ME, e.toString());
            }
         }
         Log.info(ME, n + " MessageUnits matched to subscription \"" + xmlKey.getQueryString() + "\"");
      }

      else {
         Log.warning(ME + ".UnsupportedQueryType", "Sorry, can't access, query snytax is unknown: " + xmlKey.getQueryType());
         throw new XmlBlasterException(ME + ".UnsupportedQueryType", "Sorry, can't access, query snytax is unknown: " + xmlKey.getQueryType());
      }

      return xmlKeyVec;
   }


   /**
    * Given a node <key>, extract its attribute oid='...'
    * @return oid = unique object id of the MessageUnit
    */
   protected final String getKeyOid(org.w3c.dom.Node/*com.sun.xml.tree.ElementNode*/ node) throws XmlBlasterException
   {
      if (node == null) {
         Log.warning(ME+".NoParentNode", "no parent node found");
         throw new XmlBlasterException(ME+".NoParentNode", "no parent node found");
      }

      String nodeName = node.getNodeName();      // com.sun.xml.tree.ElementNode: getLocalName();

      if (nodeName.equals("xmlBlaster")) {       // ERROR: the root node, must be specially handled
         Log.warning(ME+".NodeNotAllowed", "<xmlBlaster> node not allowed");
         throw new XmlBlasterException(ME+".NodeNotAllowed", "<xmlBlaster> node not allowed");
      }

      // check if we have found the <documentRoot><xmlBlaster><key oid=''> element
      boolean foundKey = false;
      if (nodeName.equals("key")) {
         org.w3c.dom.Node parent = node.getParentNode();
         if (parent == null) throw new XmlBlasterException(ME+".InvalidDom", "DOM tree is invalid");
         //if (parent.getNodeName().equals("xmlBlaster"))
         if (parent.getParentNode().getParentNode() == null)
            foundKey = true;
      }

      if (!foundKey) {
         return getKeyOid(node.getParentNode()); // w3c: getParentNode() sun: getParentImpl()
      }

      /* com.sun.xml.tree.ElementNode:
      org.w3c.dom.Attr keyOIDAttr = node.getAttributeNode("oid");
      if (keyOIDAttr != null)
         return keyOIDAttr.getValue();
      */

      // w3c conforming code:
      org.w3c.dom.NamedNodeMap attributes = node.getAttributes();
      if (attributes != null && attributes.getLength() > 0) {
         int attributeCount = attributes.getLength();
         for (int i = 0; i < attributeCount; i++) {
            org.w3c.dom.Attr attribute = (org.w3c.dom.Attr)attributes.item(i);
            if (attribute.getNodeName().equals("oid")) {
               String val = attribute.getNodeValue();
               // Log.trace(ME, "Found key oid=\"" + val + "\"");
               return val;
            }
         }
      }

      Log.warning(ME+".InternalKeyOid", "Internal getKeyOid() error");
      throw new XmlBlasterException(ME+".InternalKeyOid", "Internal getKeyOid() error");
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @return XML state of XmlKeyDom
    */
   public StringBuffer printOn() throws XmlBlasterException
   {
      return printOn((String)null);
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of XmlKeyDom
    */
   public StringBuffer printOn(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<XmlKeyDom>");
      try {
         java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
         xmlKeyDoc.write(out/*, encoding*/); // !!!
         StringTokenizer st = new StringTokenizer(out.toString(), "\n");
         while (st.hasMoreTokens()) {
            sb.append(offset + "   " + st.nextToken());
         }
      } catch (Exception e) { }
      sb.append(offset + "</XmlKeyDom>\n");

      return sb;
   }
}
