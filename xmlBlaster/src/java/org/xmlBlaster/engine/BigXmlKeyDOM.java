/*------------------------------------------------------------------------------
Name:      BigXmlKeyDOM.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Building a huge DOM tree for all known MessageUnit xmlKey
Version:   $Id: BigXmlKeyDOM.java,v 1.8 2000/01/13 06:18:25 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlToDom;
import org.xmlBlaster.util.I_MergeDomNode;
import org.xmlBlaster.util.XmlKeyBase;
import org.xmlBlaster.util.XmlQoSBase;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.serverIdl.MessageUnit;
import org.xmlBlaster.clientIdl.BlasterCallback;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.authentication.ClientListener;
import org.xmlBlaster.authentication.ClientEvent;
import java.util.*;
import java.io.*;

/**
 * Building a huge DOM tree for all known MessageUnit.xmlKeys.
 * <p />
 * This huge DOM tree contains all meta data about the known messages.<br />
 * Since the message content is a BLOB, messages may only be queried through<br />
 * this DOM tree using XPath.
 * <p />
 * Full text search scanning the content BLOB may be available through MIME based plugins.
 */
public class BigXmlKeyDOM implements ClientListener, MessageEraseListener, I_MergeDomNode
{
   final private static String ME = "BigXmlKeyDOM";

   private static BigXmlKeyDOM bigXmlKeyDOM = null;        // Singleton pattern

   private RequestBroker requestBroker = null;
   private Authenticate authenticate = null;

   private com.fujitsu.xml.omquery.DomQueryMgr queryMgr = null;

   private com.sun.xml.tree.XmlDocument xmlKeyDoc = null;// Sun's DOM extensions, no portable
   //private org.w3c.dom.Document xmlKeyDoc = null;      // Document with the root node
   private org.w3c.dom.Node xmlKeyRootNode = null;       // Root node <xmlBlaster></xmlBlaster>

   private String encoding = "ISO-8859-1";               // !!! TODO: access from xmlBlaster.properties file
                                                         // default is "UTF-8"


   /**
    * Access to BigXmlKeyDOM singleton
    */
   public static BigXmlKeyDOM getInstance(RequestBroker requestBroker, Authenticate authenticate) throws XmlBlasterException
   {
      synchronized (BigXmlKeyDOM.class) {
         if (bigXmlKeyDOM == null) {
            bigXmlKeyDOM = new BigXmlKeyDOM(requestBroker, authenticate);
         }
      }
      return bigXmlKeyDOM;
   }


   /**
    * private Constructor for Singleton Pattern
    */
   private BigXmlKeyDOM(RequestBroker requestBroker, Authenticate authenticate) throws XmlBlasterException
   {
      this.requestBroker = requestBroker;
      this.authenticate = authenticate;

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
      com.sun.xml.tree.ElementNode root = (com.sun.xml.tree.ElementNode) xmlKeyDoc.createElement ("xmlBlaster");
      xmlKeyDoc.appendChild(root);
      xmlKeyRootNode = xmlKeyDoc.getDocumentElement();

      authenticate.addClientListener(this);
      requestBroker.addMessageEraseListener(this);
   }


   /**
    * Accesing the query manager for XPath.
    * <p />
    * queryMgr is instantiated if null
    * @return the query manager
    */
   private final com.fujitsu.xml.omquery.DomQueryMgr getQueryMgr()
   {
      if (queryMgr == null)
         queryMgr = new com.fujitsu.xml.omquery.DomQueryMgr(xmlKeyDoc);
      return queryMgr;
   }


   /**
    * Adding a new node to the xmlBlaster xmlKey tree.
    * <p />
    * This method will be invoked when a new message is arriving to
    * make its describing meta data available for XPath queries.
    * @param the node to merge into the DOM tree
    * @return the node added
    */
   public org.w3c.dom.Node mergeNode(org.w3c.dom.Node node) throws XmlBlasterException
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
    * Removing a node from the xmlBlaster xmlKey tree
    * @param The node removed
    */
   public org.w3c.dom.Node removeKeyNode(org.w3c.dom.Node node)
   {
      return xmlKeyRootNode.removeChild(node);
   }


   /**
    * This method does the XPath query.
    *
    * @param clientName is only needed for nicer logging output
    * @return Array of matching XmlKey objects (may contain null elements)
    *
    * TODO: a query Handler, allowing drivers for REGEX, XPath, SQL, etc. queries
    */
   Vector parseKeyOid(ClientInfo clientInfo, XmlKey xmlKey, XmlQoSBase qos)  throws XmlBlasterException
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
   private String getKeyOid(org.w3c.dom.Node/*com.sun.xml.tree.ElementNode*/ node) throws XmlBlasterException
   {
      if (node == null) {
         Log.warning(ME+".NoParentNode", "no parent node found");
         throw new XmlBlasterException(ME+".NoParentNode", "no parent node found");
      }

      String nodeName = node.getNodeName();      // com.sun.xml.tree.ElementNode: getLocalName();

      if (nodeName.equals("xmlBlaster")) {       // ERROR: the root node, must be specialy handled
         Log.warning(ME+".NodeNotAllowed", "<xmlBlaster> node not allowed");
         throw new XmlBlasterException(ME+".NodeNotAllowed", "<xmlBlaster> node not allowed");
      }

      if (!nodeName.equals("key")) {
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
    * Invoked on successful client login (interface ClientListener)
    */
   public void clientAdded(ClientEvent e) throws XmlBlasterException
   {
      ClientInfo clientInfo = e.getClientInfo();
      if (Log.TRACE) Log.trace(ME, "Login event for client " + clientInfo.toString());
   }


   /**
    * Invoked when client does a logout (interface ClientListener)
    */
   public void clientRemove(ClientEvent e) throws XmlBlasterException
   {
      ClientInfo clientInfo = e.getClientInfo();
      if (Log.TRACE) Log.trace(ME, "Logout event for client " + clientInfo.toString());
   }


   /**
    * Invoked on message erase() invocation (interface MessageEraseListener)
    */
   public void messageErase(MessageEraseEvent e) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Erase event occured ...");
      MessageUnitHandler messageUnitHandler = e.getMessageUnitHandler();
      org.w3c.dom.Node node = removeKeyNode(messageUnitHandler.getRootNode());
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @return XML state of BigXmlKeyDOM
    */
   public final StringBuffer printOn() throws XmlBlasterException
   {
      return printOn((String)null);
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of BigXmlKeyDOM
    */
   public final StringBuffer printOn(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<BigXmlKeyDOM>");
      try {
         java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
         xmlKeyDoc.write(out/*, encoding*/); // !!!
         StringTokenizer st = new StringTokenizer(out.toString(), "\n");
         while (st.hasMoreTokens()) {
            sb.append(offset + "   " + st.nextToken());
         }
      } catch (Exception e) { }
      sb.append(offset + "</BigXmlKeyDOM>\n");

      return sb;
   }
}
