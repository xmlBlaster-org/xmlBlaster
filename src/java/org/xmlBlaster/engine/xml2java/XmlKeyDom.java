/*------------------------------------------------------------------------------
Name:      XmlKeyDom.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.xml2java;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.engine.RequestBroker;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.util.I_MergeDomNode;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.XmlNotPortable;
import org.xmlBlaster.util.def.Constants;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.StringTokenizer;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Building a DOM tree for XmlKeys.
 * <p />
 * This DOM tree contains the meta data from XmlKey:<br />
 * <pre>&lt;![CDATA[
 *  &lt;?xml version='1.0' encoding='UTF-8' ?>
 *  &lt;xmlBlaster>
 *    &lt;key oid='abc' contentMime='text/plain'>
 *      &lt;Hello/>
 *    &lt;/key>
 *    &lt;key oid='xyz' contentMime='text/xml'>
 *      &lt;World/>
 *    &lt;/key>
 *  &lt;/xmlBlaster>]]>
 * </pre>
 * @author xmlBlaster@marcelruff.info
 */
public class XmlKeyDom implements I_MergeDomNode
{
   final private static String ME = "XmlKeyDom";

   private final ServerScope serverScope;
   private static Logger log = Logger.getLogger(XmlKeyDom.class.getName());
   protected Document xmlKeyDoc = null;
   protected String encoding = Constants.UTF8_ENCODING; // Before xmlBlaster 1.3: "ISO-8859-1";
   protected final RequestBroker requestBroker;


   /**
    * Constructor to handle a DOM with XPath query.
    */
   protected XmlKeyDom(RequestBroker requestBroker) throws XmlBlasterException
   {
      this.requestBroker = requestBroker;
      this.serverScope = this.requestBroker.getServerScope();
      this.encoding = this.serverScope.getProperty().get("xmlBlaster/topicDom/encoding", encoding);

      // Instantiate the xmlBlaster DOM tree with <xmlBlaster> root node (DOM portable)
      String xml = "<?xml version='1.0' encoding='"+this.encoding+"' ?>\n" +
                   "<xmlBlaster></xmlBlaster>";
      java.io.StringReader reader = new java.io.StringReader(xml);
      org.xml.sax.InputSource input = new org.xml.sax.InputSource(reader);

      try {
         DocumentBuilderFactory dbf = requestBroker.getServerScope().getDocumentBuilderFactory();
         //dbf.setNamespaceAware(true);
         //dbf.setCoalescing(true);
         //dbf.setValidating(false);
         //dbf.setIgnoringComments(true);
         DocumentBuilder db = dbf.newDocumentBuilder ();
         xmlKeyDoc = db.parse(input);
      } catch (Exception e) {
         log.severe("Problems when building DOM tree from your XmlKey: " + e.toString());
         throw new XmlBlasterException(serverScope, ErrorCode.INTERNAL_ILLEGALSTATE, ME, "Problems when building DOM tree from your XmlKey: " + e.toString());
      }
   }

   /**
    * Adding a new &lt;key> node to the xmlBlaster xmlKey tree.
    * <p />
    * This method is forced by the interface I_MergeDomNode
    * @param the node to merge into the DOM tree, it is invalid after this call
    * @return the node added or null
    */
   public final org.w3c.dom.Node mergeNode(org.w3c.dom.Node node) throws XmlBlasterException
   {
      // !!! synchronize is missing !!!
      // !!! PENDING: If same key oid exists, remove the old and replace with new
      org.w3c.dom.Node newNode = XmlNotPortable.mergeNode(xmlKeyDoc, node);
      return newNode;
   }


   /**
    * This method does the XPath query.
    *
    * @param clientName is only needed for nicer logging output
    * @return Array of matching key oid objects
    *
    * TODO: a query Handler, allowing drivers for REGEX, XPath, SQL, etc. queries
    */
   public final ArrayList parseKeyOid(SessionInfo sessionInfo, String xpathQuery, QueryQosData qos)  throws XmlBlasterException
   {
      ArrayList list = new ArrayList();
      String clientName = sessionInfo.toString();

      if (xpathQuery == null || xpathQuery.length() < 1) {
         log.warning("Sorry, can't access message, you supplied an empty XPATH query '" + xpathQuery + "', please check your query string");
         throw new XmlBlasterException(this.serverScope, ErrorCode.USER_QUERY_INVALID, ME,
          "Sorry, can't access message, you supplied an empty XPATH query '" + xpathQuery + "', please check your query string");
      }

      Enumeration nodeIter;
      try {
         if (log.isLoggable(Level.FINE)) log.fine("Goin' to query DOM tree with XPATH = '" + xpathQuery + "'");
         nodeIter = XmlNotPortable.getNodeSetFromXPath(xpathQuery, xmlKeyDoc);
         if (log.isLoggable(Level.FINE)) log.fine("Node iter done");
      } catch (XmlBlasterException e) {
         log.warning("Sorry, can't access, query syntax is wrong for '" + xpathQuery + "' : " + e.getMessage());
         throw new XmlBlasterException(this.serverScope, ErrorCode.USER_QUERY_INVALID, ME, "Sorry, can't access, query syntax of '" + xpathQuery + "' is wrong", e);
      }
      int n = 0;
      boolean wantsAll = false;
      while (nodeIter.hasMoreElements()) {
         n++;
         Object obj = nodeIter.nextElement();
         if (obj instanceof org.w3c.dom.Document) { // A query like "/" or "/xmlBlaster"
            if (log.isLoggable(Level.FINE)) log.fine("Query on document root " + obj.toString());
            wantsAll = true;
            break;
         }
         Element node = (Element)obj;
         if ("xmlBlaster".equals(node.getNodeName())) {
            if (log.isLoggable(Level.FINE)) log.fine("Query on root node " + obj.toString());
            wantsAll = true;
            break;
         }
         try {
            String uniqueKey = getKeyOid(node);
            if (log.isLoggable(Level.FINE)) log.fine("Client " + clientName + " is accessing topic oid='" + uniqueKey + "' after successful xpath query");
            list.add(uniqueKey);
         } catch (XmlBlasterException e) {
            throw e;
         } catch (Exception e) {
            e.printStackTrace();
            log.severe(e.getMessage());
            XmlBlasterException.convert(serverScope, ME, "XPath DOM lookup problems", e);
         }
      }

      if (log.isLoggable(Level.FINE)) log.info(n + " MsgUnits matched to subscription \"" + xpathQuery + "\"");

      if (wantsAll) {
         list.clear();
         return parseKeyOid(sessionInfo, "/xmlBlaster/key", qos);
      }

      return list;
   }


   /**
    * Given a node <key>, extract its attribute oid='...'
    * @return oid = unique object id of the MsgUnit
    */
   protected final String getKeyOid(org.w3c.dom.Node node) throws XmlBlasterException
   {
      if (node == null) {
         log.warning("no parent node found");
         throw new XmlBlasterException(serverScope, ErrorCode.INTERNAL_ILLEGALSTATE, ME+".NoParentNode", "no parent node found");
      }

      String nodeName = node.getNodeName();
      if (log.isLoggable(Level.FINEST)) log.finest("Checking node name=" + nodeName);

      if ("xmlBlaster".equals(nodeName) && (node.getParentNode() == null || "#document".equals(node.getParentNode().getNodeName()))) {       // ERROR: the root node, must be specially handled
         log.warning("<xmlBlaster> node not allowed");
         throw new XmlBlasterException(serverScope, ErrorCode.INTERNAL_UNKNOWN, ME, "<xmlBlaster> node not allowed");
      }

      // check if we have found the <documentRoot><xmlBlaster><key oid=''> element
      boolean foundKey = false;
      if (nodeName.equalsIgnoreCase("key")) {
         org.w3c.dom.Node parent = node.getParentNode();
         if (parent == null) throw new XmlBlasterException(serverScope, ErrorCode.INTERNAL_ILLEGALSTATE, ME+".InvalidDom", "DOM tree is invalid");
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
            if (attribute.getNodeName().equalsIgnoreCase("oid")) {
               String val = attribute.getNodeValue();
               // log.trace(ME, "Found key oid=\"" + val + "\"");
               return val;
            }
         }
      }

      log.warning("Internal getKeyOid() error");
      throw new XmlBlasterException(serverScope, ErrorCode.INTERNAL_UNKNOWN, ME, "Internal getKeyOid() error");
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @return XML state of XmlKeyDom
    */
   public String toXml() throws XmlBlasterException {
      return toXml((String)null, false);
   }

   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @param stripDeclaration if true '&lt;?xml version="1.0" encoding="UTF-8"?>' is not dumped
    * @return XML state of XmlKeyDom
    */
   public String toXml(String extraOffset, boolean stripDeclaration) throws XmlBlasterException {
      StringBuffer sb = new StringBuffer(2048);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<XmlKeyDom>");
      try {
         StringTokenizer st = new StringTokenizer(XmlNotPortable.write(xmlKeyDoc).toString(), "\n");
         while (st.hasMoreTokens()) {
            String line = st.nextToken().trim();
            if (stripDeclaration && line.startsWith("<?xml")) {
               continue;
            }
            sb.append(offset).append(Constants.INDENT).append(line);
         }
      } catch (Exception e) { }
      sb.append(offset).append("</XmlKeyDom>\n");

      return sb.toString();
   }
}
