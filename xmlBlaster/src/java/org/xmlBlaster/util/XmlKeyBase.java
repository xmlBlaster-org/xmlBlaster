/*------------------------------------------------------------------------------
Name:      XmlKeyBase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey, knows how to parse it with SAX
Version:   $Id: XmlKeyBase.java,v 1.12 1999/11/22 23:05:03 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.engine.RequestBroker;

import org.xml.sax.InputSource;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Attr;


/**
 * XmlKeyBase. 
 * <p>
 * All XmlKey's have the same XML minimal structure:<p>
 * <pre>
 *    &lt;key oid="12345">
 *       &lt;!-- application specific tags -->
 *    &lt;/key>
 * </pre>
 *
 * where oid is a unique key.
 * <p>
 * A typical <b>publish()</b> would look like:
 * <pre>
 *    &lt;lt;?xml version='1.0' encoding='ISO-8859-1' ?>
 *    &lt;lt;key oid=\"KEY_FOR_SMILEY\" contentMime='text/plain'>
 *    &lt;lt;/key>
 * </pre>
 * <br>
 * or
 * <br>
 * <pre>
 *    &lt;?xml version='1.0' encoding='ISO-8859-1' ?>
 *    &lt;key oid='' contentMime='text/xml'>
 *       &lt;AGENT id='192.168.124.10' subId='1' type='generic'>
 *          &lt;DRIVER id='FileProof' pollingFreq='10'>
 *          &lt;/DRIVER>
 *       &lt;/AGENT>
 *    &lt;/key>
 * </pre>
 * A typical <b>subscribe()</b> would look like:
 * <pre>
 *    &lt;?xml version='1.0' encoding='ISO-8859-1' ?>
 *    &lt;key oid=\"KEY_FOR_SMILEY\" queryType='EXACT'>
 *    &lt;/key>
 * </pre>
 * <br>
 * or
 * <pre>
 *    &lt;?xml version='1.0' encoding='ISO-8859-1' ?>
 *    &lt;key oid='' queryType='XPATH'>
 *       //DRIVER[@id='FileProof']
 *    &lt;/key>
 * </pre>
 *
 */
public class XmlKeyBase
{
   private String ME = "XmlKeyBase";

   private static long uniqueCounter = 1L;

   public final static int PUBLISH     = 0; // no query: <key oid='myWhitePaper'><book><paper></paper></book></key> + content
   public final static int EXACT_QUERY = 1; // <key oid="myCarPrice" queryType="EXACT"></key>
   public final static int XPATH_QUERY = 2; // <key oid=""           queryType="XPATH">xmlBlaster/key/AGENT/DRIVER[@id!='FileProof']</key>
   public final static int REGEX_QUERY = 3; // <key oid="my.*"       queryType="REGEX"></key>
   private int queryType = PUBLISH;
   private String queryString = "";


   public final int XML_TYPE = 0;   // xml syntax
   public final int ASCII_TYPE = 1; // for trivial keys you can use a ASCII String (no XML)
   private int keyType = XML_TYPE;  // XmlKey uses XML syntax (default)

   private boolean isGeneratedOid = false;  // is oid new generated by xmlBlaster?
   private boolean isPublish = false;       // called via subscribe()

   protected String xmlKey_literal;

   protected org.w3c.dom.Document doc = null;  // the parsed xmlKey_literal DOM
   protected org.w3c.dom.Node rootNode = null; // this is always the <key ...>
   protected String keyOid = null;             // value from attribute <key oid="...">


   /**
    * Parses given xml string
    * DON'T use this constructor for publish() Messages
    *
    * @param The original key in XML syntax, for example:<br>
    *        <pre><key oid="This is the unique attribute"></key></pre>
    */
   public XmlKeyBase(String xmlKey_literal) throws XmlBlasterException
   {
      init(xmlKey_literal, false);
   }


   /**
    * Parses given xml string. 
    * USE THIS constructor when publish() is invoked (needs redesign)!
    *
    * @param The original key in XML syntax, for example:<br>
    *        <pre><key oid="This is the unique attribute"></key></pre>
    * @param isPublish true:  when invoked by publish()
    *                  false: all the other cases
    */
   public XmlKeyBase(String xmlKey_literal, boolean isPublish) throws XmlBlasterException
   {
      init(xmlKey_literal, isPublish);
   }


   private void init(String xmlKey_literal, boolean isPublish) throws XmlBlasterException
   {
      this.isPublish = isPublish;
      queryType = (isPublish) ? PUBLISH : EXACT_QUERY;

      if (Log.CALLS) Log.trace(ME, "Creating new XmlKey for isPublish=" + isPublish);

      this.xmlKey_literal = xmlKey_literal.trim();

      if (!this.xmlKey_literal.startsWith("<")) {
         keyType = ASCII_TYPE;  // eg "Airport/Runway1/WindVeloc3"
         keyOid = xmlKey_literal;

         // Works well with ASCII, but is switched of for the moment
         // perhaps we should make it configureable thru a porperty file !!!
         // Example: xmlKey_literal="Airport.*" as a regulaer expression

         Log.error(ME+".XML", "Invalid XmlKey syntax, only XML syntax beginning with \"<\" is supported");
         throw new XmlBlasterException(ME+".XML", "Invalid XmlKey syntax, only XML syntax beginning with \"<\" is supported");

      }
   }


   /**
    * @return the literal ASCII xmlKey
    */
   public String toString()
   {
      return xmlKey_literal;
   }


   /**
    * @return the literal ASCII xmlKey
    */
   public String literal()
   {
      return xmlKey_literal;
   }


   /**
    * Synonym for getKeyOid()
    *
    * @return oid
    */
   public String getUniqueKey() throws XmlBlasterException
   {
      return getKeyOid();
   }


   /**
    * Is the key oid generated or given by the client?
    *
    * @return true generated oid by xmlBlaster
    */
   public boolean isGeneratedOid() throws XmlBlasterException
   {
      loadDomTree();
      return isGeneratedOid;
   }


   /**
    * The syntax of the XmlKey_literal String
    */
   public String getMimeType() throws XmlBlasterException
   {
      loadDomTree();
      if (keyType == XML_TYPE)
         return "text/xml";
      else if (keyType == ASCII_TYPE) // not supported!
         return "text/plain";
      else
         return "text/plain";         // not supported!
   }


   /**
    * Accessing the unique oid of <key oid="...">
    *
    * @return oid
    */
   public String getKeyOid() throws XmlBlasterException
   {
      loadDomTree();
      return keyOid;
   }


   /**
    * Fills the DOM tree, and assures that a valid <key oid="..."> is used
    */
   public org.w3c.dom.Node getRootNode() throws XmlBlasterException
   {
      loadDomTree();
      return rootNode;
   }


   public final int getQueryType() throws XmlBlasterException
   {
      loadDomTree();
      return queryType;
   }


   public String getQueryString() throws XmlBlasterException
   {
      loadDomTree();
      return queryString;
   }


   /**
    * Fills the DOM tree, and assures that a valid <key oid="..."> is used. 
    * <p>
    * The keyOid will be set properly if no error occures
    * The rootNode will be set properly if no error occures
    */
   private void loadDomTree() throws XmlBlasterException
   {
      if (doc != null)
         return;       // DOM tree is already loaded

      if (keyType == ASCII_TYPE)
         return;       // no XML -> no DOM

      java.io.StringReader reader = new java.io.StringReader(xmlKey_literal);
      InputSource input = new InputSource(reader);
      //input.setEncoding("ISO-8859-2");
      //input.setSystemId("9999999999");

      com.jclark.xsl.dom.XMLProcessorImpl xmlProc = RequestBroker.getInstance().getXMLProcessorImpl();

      try {
         doc = xmlProc.load(input);
      } catch (java.io.IOException e) {
         Log.error(ME+".IO", "Problems when building DOM tree from your XmlKey: " + e.toString());
         throw new XmlBlasterException(ME+".IO", "Problems when building DOM tree from your XmlKey: " + e.toString());
      } catch (org.xml.sax.SAXException e) {
         Log.error(ME+".SAX", "Problems when building DOM tree from your XmlKey: " + e.toString());
         throw new XmlBlasterException(ME+".SAX", "Problems when building DOM tree from your XmlKey: " + e.toString());
      }

      org.w3c.dom.Node tmpRootNode = doc.getDocumentElement(); 

      checkForKeyAttr(tmpRootNode);

      rootNode = tmpRootNode;  // everything successfull, assign the rootNode
   }


   /**
    * Should be called by publish() to merge the local XmlKey DOM into the big xmlBlaster DOM tree
    */
   public void mergeRootNode() throws XmlBlasterException
   {
      org.w3c.dom.Node tmpRootNode = rootNode;
      if (isPublish) {
         if (Log.TRACE) Log.trace(ME, "Created DOM tree for " + getUniqueKey() + ", adding it to <xmlBlaster> tree");
         org.w3c.dom.Node node = RequestBroker.getInstance().addKeyNode(tmpRootNode);
      }
      rootNode = tmpRootNode;  // everything successfull, assign the rootNode
   }


   /**
    * Finds the <key oid="..." queryType="..."> attributes, or inserts a unique oid if empty
    */
   private void checkForKeyAttr(org.w3c.dom.Node node) throws XmlBlasterException
   {
      if (node == null) {
         Log.error(ME+".Internal", "root node = null");
         throw new XmlBlasterException(ME+"Internal", "root node = null");
      }

      String nodeName = node.getNodeName();    // com.sun.xml.tree.ElementNode: getLocalName();

      if (!nodeName.equals("key")) {
         Log.error(ME+".WrongRootNode", "The root node must be named \"key\"");
         throw new XmlBlasterException(ME+".WrongRootNode", "The root node must be named \"key\"");
      }

      keyOid = null;
      queryType = (isPublish) ? PUBLISH : EXACT_QUERY;

      NamedNodeMap attributes = node.getAttributes();
      if (attributes != null && attributes.getLength() > 0) {
         int attributeCount = attributes.getLength();
         for (int i = 0; i < attributeCount; i++) {
            Attr attribute = (Attr)attributes.item(i);

            if (attribute.getNodeName().equals("oid")) {
               String val = attribute.getNodeValue();
               if (val.length() < 1) {
                  keyOid = generateKeyOid();
                  attribute.setNodeValue(keyOid);
                  if (isPublish) {
                     Log.warning(ME, "Generated key oid=\"" + keyOid + "\" for publish mode seems to be strange");
                  }
                  else {  
                     if (Log.TRACE) Log.trace(ME, "Generated key oid=\"" + keyOid + "\"");
                  }
               }
               else {
                  keyOid = val;
                  if (Log.TRACE) Log.trace(ME, "Found key oid=\"" + keyOid + "\"");
               }
            }

            if (!isPublish && attribute.getNodeName().equals("queryType")) {
               String val = attribute.getNodeValue();
               if (val.equals("EXACT"))
                  queryType = EXACT_QUERY;
               else if (val.equals("XPATH"))
                  queryType = XPATH_QUERY;
               else if (val.equals("REGEX"))
                  queryType = REGEX_QUERY;
               else {
                  Log.warning(ME+".UnknownQueryType", "Unknown queryType " + val + ", setting default to EXACT");
                  //throw new XmlBlasterException(ME+".UnknownQueryType", "Unknown queryType " + val + ", your xmlKey is invalid");
               }
            }
         }
      }

      if (keyOid == null) {
         Log.error(ME+".WrongRootNode", "Missing \"oid\" attribute in \"key\" tag");
         throw new XmlBlasterException(ME+".WrongRootNode", "Missing \"oid\" attribute in \"key\" tag");
      }

      // extract the query string <key ...>The query string</key>
      if (!isPublish && queryType != EXACT_QUERY) {
         NodeList children = node.getChildNodes();
         if (children != null) {
            int len = children.getLength();
            for (int i = 0; i < len; i++) {
               Node childNode = children.item(i);
               if (childNode.getNodeType() == Node.TEXT_NODE) {
                  queryString = childNode.getNodeValue();
               }
            }
         }
         if (queryString==null || queryString.length() < 1) {
            Log.error(ME+".MissingQuery", "Missing query string in <key> ... </key> tag");
            throw new XmlBlasterException(ME+".MissingQuery", "Missing query string in <key> ... </key> tag");
         }
      }

   }


   /**
    * Generates a unique key
    * TODO: include IP adress and PID for global uniqueness
    */
   private String generateKeyOid()
   {
      StringBuffer oid = new StringBuffer(60);

      String ip_addr = jacorb.orb.Environment.getProperty("OAIAddr");
      String oa_port = jacorb.orb.Environment.getProperty("OAPort");
      long currentTime = System.currentTimeMillis();

      oid.append(ip_addr).append("-").append(oa_port).append("-").append(currentTime);

      generateKeyOidCounter(oid);

      isGeneratedOid = true;
      return oid.toString();
   }


   /**
    * Little helper method, which is synchronized
    */
   synchronized private void generateKeyOidCounter(StringBuffer oid)
   {
      oid.append("-").append(uniqueCounter);
      uniqueCounter++;
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

      sb.append(offset + "<XmlKeyBase oid='" + getUniqueKey() + "'>");
      sb.append(offset + "   <queryString>" + queryString + "</queryString>");
      sb.append(offset + "   <keyType>" + keyType + "</keyType>");
      sb.append(offset + "   <isGeneratedOid>" + isGeneratedOid + "</isGeneratedOid>");
      sb.append(offset + "   <isPublish>" + isPublish + "</isPublish>");
      sb.append(offset + "</XmlKeyBase>\n");
      return sb;
   }
}
