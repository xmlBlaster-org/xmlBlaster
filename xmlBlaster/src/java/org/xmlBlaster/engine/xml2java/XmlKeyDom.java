/*------------------------------------------------------------------------------
Name:      XmlKeyDom.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Building a huge DOM tree for all known MessageUnit xmlKey
Version:   $Id: XmlKeyDom.java,v 1.15 2002/07/09 18:06:55 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.xml2java;

import org.jutils.log.LogChannel;

import org.xmlBlaster.engine.RequestBroker;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.util.I_MergeDomNode;
import org.xmlBlaster.util.XmlQoSBase;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlNotPortable;

import java.util.Vector;
import java.util.Enumeration;
import java.util.StringTokenizer;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.Element;

/**
 * Building a DOM tree for XmlKeys.
 * <p />
 * This DOM tree contains the meta data from XmlKey:<br />
 * <pre>
 *  <?xml version='1.0' encoding='ISO-8859-1' ?>
 *  <xmlBlaster>
 *    <key oid='abc' contentMime='text/plain'>
 *      <Hello/>
 *    </key>
 *    <key oid='xyz' contentMime='text/xml'>
 *      <World/>
 *    </key>
 *  </xmlBlaster>
 * </pre>
 */
public class XmlKeyDom implements I_MergeDomNode
{
   final private static String ME = "XmlKeyDom";

   private final LogChannel log;

   protected com.fujitsu.xml.omquery.DomQueryMgr queryMgr = null;

   protected Document xmlKeyDoc = null;

   protected String encoding = "ISO-8859-1";             // !!! TODO: access from xmlBlaster.properties file
                                                         // default is "UTF-8"
   protected final RequestBroker requestBroker;


   /**
    * Constructor to handle a DOM with XPath query.
    */
   protected XmlKeyDom(RequestBroker requestBroker) throws XmlBlasterException
   {
      this.requestBroker = requestBroker;
      this.log = requestBroker.getGlobal().getLog("core");

      // Instantiate the xmlBlaster DOM tree with <xmlBlaster> root node (DOM portable)
      String xml = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                   "<xmlBlaster></xmlBlaster>";
      java.io.StringReader reader = new java.io.StringReader(xml);
      org.xml.sax.InputSource input = new org.xml.sax.InputSource(reader);

      try {
         DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance ();
         //dbf.setNamespaceAware(true);
         //dbf.setCoalescing(true);
         //dbf.setValidating(false);
         //dbf.setIgnoringComments(true);
         DocumentBuilder db = dbf.newDocumentBuilder ();
         xmlKeyDoc = db.parse(input);
      } catch (Exception e) {
         log.error(ME+".IO", "Problems when building DOM tree from your XmlKey: " + e.toString());
         throw new XmlBlasterException(ME, "Problems when building DOM tree from your XmlKey: " + e.toString());
      }
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
      // !!! synchronize is missing !!!
      // !!! PENDING: If same key oid exists, remove the old and replace with new

      XmlNotPortable.mergeNode(xmlKeyDoc, node);
      queryMgr = null; // needs to be reloaded, since the Document changed
      return node;
   }


   /**
    * This method does the XPath query.
    *
    * @param clientName is only needed for nicer logging output
    * @return Array of matching XmlKey objects (may contain null elements)
    *
    * TODO: a query Handler, allowing drivers for REGEX, XPath, SQL, etc. queries
    */
   public final Vector parseKeyOid(SessionInfo sessionInfo, XmlKey xmlKey, XmlQoSBase qos)  throws XmlBlasterException
   {
      Vector xmlKeyVec = new Vector();
      String clientName = sessionInfo.toString();

      if (xmlKey.getQueryType() == XmlKey.XPATH_QUERY) { // query: subscription without a given oid

         Enumeration nodeIter;
         try {
            if (log.TRACE) log.trace(ME, "Goin' to query DOM tree with XPATH = " + xmlKey.getQueryString());
            nodeIter = getQueryMgr().getNodesByXPath(xmlKeyDoc, xmlKey.getQueryString());
         } catch (Exception e) {
            log.warn(ME + ".InvalidQuery", "Sorry, can't access, query snytax is wrong for '" + xmlKey.getQueryString() + "' : " + e.toString());
            e.printStackTrace();
            throw new XmlBlasterException(ME + ".InvalidQuery", "Sorry, can't access, query snytax is wrong");
         }
         int n = 0;
         while (nodeIter.hasMoreElements()) {
            n++;
            Object obj = nodeIter.nextElement();
            Element node = (Element)obj;
            try {
               String uniqueKey = getKeyOid(node);
               if (log.TRACE) log.info(ME, "Client " + clientName + " is accessing message oid=\"" + uniqueKey + "\" after successful query");
               xmlKeyVec.addElement(requestBroker.getXmlKeyFromOid(uniqueKey));
            } catch (Exception e) {
               e.printStackTrace();
               log.error(ME, e.toString());
            }
         }
         if (log.TRACE) log.info(ME, n + " MessageUnits matched to subscription \"" + xmlKey.getQueryString() + "\"");
      }

      else {
         log.warn(ME + ".UnsupportedQueryType", "Sorry, can't access, query snytax is unknown: " + xmlKey.getQueryType());
         throw new XmlBlasterException(ME + ".UnsupportedQueryType", "Sorry, can't access, query snytax is unknown: " + xmlKey.getQueryType());
      }

      return xmlKeyVec;
   }


   /**
    * Given a node <key>, extract its attribute oid='...'
    * @return oid = unique object id of the MessageUnit
    */
   protected final String getKeyOid(org.w3c.dom.Node node) throws XmlBlasterException
   {
      if (node == null) {
         log.warn(ME+".NoParentNode", "no parent node found");
         throw new XmlBlasterException(ME+".NoParentNode", "no parent node found");
      }

      String nodeName = node.getNodeName();

      if (nodeName.equals("xmlBlaster")) {       // ERROR: the root node, must be specially handled
         log.warn(ME+".NodeNotAllowed", "<xmlBlaster> node not allowed");
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

      // w3c conforming code:
      org.w3c.dom.NamedNodeMap attributes = node.getAttributes();
      if (attributes != null && attributes.getLength() > 0) {
         int attributeCount = attributes.getLength();
         for (int i = 0; i < attributeCount; i++) {
            org.w3c.dom.Attr attribute = (org.w3c.dom.Attr)attributes.item(i);
            if (attribute.getNodeName().equals("oid")) {
               String val = attribute.getNodeValue();
               // log.trace(ME, "Found key oid=\"" + val + "\"");
               return val;
            }
         }
      }

      log.warn(ME+".InternalKeyOid", "Internal getKeyOid() error");
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
         StringTokenizer st = new StringTokenizer(XmlNotPortable.write(xmlKeyDoc).toString(), "\n");
         while (st.hasMoreTokens()) {
            sb.append(offset + "   " + st.nextToken());
         }
      } catch (Exception e) { }
      sb.append(offset + "</XmlKeyDom>\n");

      return sb;
   }
}
