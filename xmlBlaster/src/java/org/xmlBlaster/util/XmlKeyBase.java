/*------------------------------------------------------------------------------
Name:      XmlKeyBase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey, knows how to parse it with SAX
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.util.Log;

import org.xml.sax.InputSource;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Attr;

import java.util.StringTokenizer;

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
 *    &lt;?xml version='1.0' encoding='ISO-8859-1' ?>
 *    &lt;key oid=\"KEY_FOR_SMILEY\" contentMime='text/plain' contentMimeExtended='1.5'>
 *    &lt;/key>
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
 * <p />
 * More examples you find in xmlBlaster/src/dtd/XmlKey.xml
 * <p />
 * @see <a href="http://www.w3.org/TR/xpath">The W3C XPath specification</a>
 * @see org.xmlBlaster.client.UpdateKey
 */
public class XmlKeyBase
{
   private String ME = "XmlKeyBase";

   private XmlToDom xmlToDom = null;

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

   /** The XML ASCII string */
   protected String xmlKey_literal;
   /** A nicer formatted ASCII XML string (same content as xmlKey_literal but indented) */
   protected String xmlKey_nice;

   /** Value from attribute &lt;key oid="..."> */
   protected String keyOid = null;

   /** The MIME type of the content, RFC1521 */
   protected String contentMime = "text/plain";

   /** Some further content info, e.g. the version number */
   protected String contentMimeExtended = "";

   /** Is the internal state of xmlBlaster queried? */
   protected boolean isInternalStateQuery = false;

   /** IP address to generate unique oid */
   private String ip_addr = null; // jacorb.util.Environment.getProperty("OAIAddr");

   private String oa_port = null; // jacorb.util.Environment.getProperty("OAPort");


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


   /**
    */
   private void init(String xmlKey_literal, boolean isPublish) throws XmlBlasterException
   {
      this.isPublish = isPublish;
      queryType = (isPublish) ? PUBLISH : EXACT_QUERY;

      if (Log.CALL) Log.trace(ME, "Creating new XmlKey for isPublish=" + isPublish);

      this.xmlKey_literal = xmlKey_literal.trim();

      if (!this.xmlKey_literal.startsWith("<")) {
         keyType = ASCII_TYPE;  // eg "Airport/Runway1/WindVeloc3"
         keyOid = this.xmlKey_literal;

         // Works well with ASCII, but is switched off for the moment
         // perhaps we should make it configurable through a property file !!!
         // Example: xmlKey_literal="Airport.*" as a regular expression

         if (Log.DUMP) Log.dump(ME+".XML", "Invalid XmlKey syntax, only XML syntax beginning with \"<\" is supported: '" + this.xmlKey_literal + "'");
         throw new XmlBlasterException(ME+".XML", "Invalid XmlKey syntax, only XML syntax beginning with \"<\" is supported");

      }
   }


   /**
    * Access the literal ASCII xmlKey.
    * @return the literal ASCII xmlKey
    */
   public String toString()
   {
      return toXml();
   }


   /**
    * Access the literal ASCII xmlKey.
    * @return the literal ASCII xmlKey
    */
   public String toXml()
   {
      return xmlKey_literal;
   }


   /**
    * Access the literal XML-ASCII xmlKey.
    * <p />
    * Note that this may vary from the original ASCII string:<br />
    * When the key oid was generated locally, the literal string contains
    * this new generated oid as well.
    * @return the literal ASCII xmlKey
    */
   public String literal()
   {
      return xmlKey_literal;
   }


   /**
    * Synonym for getKeyOid().
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
    * Find out which mime type (syntax) of the XmlKey_literal String.
    * @return "text/xml" only XML is supported
    */
    /*
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
     */

   /**
    * Find out which mime type (syntax) the content of the message has.
    * @return The MIME type, for example "text/xml" in &lt;key oid='' contentMime='text/xml'><br />
    *         default is "text/plain" if not set
    * @see <a href="ftp://ftp.std.com/customers3/src/mail/imap-3.3/RFC1521.TXT">RFC1521 - MIME (Multipurpose Internet Mail Extensions)</a>
    */
   public String getContentMime() throws XmlBlasterException
   {
      loadDomTree();
      return contentMime;
   }


   /**
    * Some further specifying information of the content.
    * <p />
    * For example the application version number the document in the content.<br />
    * You may use this attribute for you own purposes.
    * @return The MIME-extended info, for example<br />
    *         "Version 1.1" in &lt;key oid='' contentMime='text/xml' contentMimeExtended='Version 1.1'><br />
    *         or "" (empty string) if not known
    */
   public String getContentMimeExtended() throws XmlBlasterException
   {
      loadDomTree();
      return contentMimeExtended;
   }


   /**
    * Accessing the unique oid of <key oid="...">.
    *
    * @return oid
    */
   public String getKeyOid() throws XmlBlasterException
   {
      loadDomTree();
      return keyOid;
   }


   /**
    * Accessing the internal state of xmlBlaster.
    * <br />
    * @return true if accessing <__sys__xy>
    */
   public boolean isInternalStateQuery() throws XmlBlasterException
   {
      loadDomTree();
      return isInternalStateQuery;
   }


   /**
    * Fills the DOM tree, and assures that a valid <key oid="..."> is used.
    */
   public org.w3c.dom.Node getRootNode() throws XmlBlasterException
   {
      loadDomTree();
      return xmlToDom.getRootNode();
   }


   /**
    * Fills the DOM tree, and assures that a valid <key oid="..."> is used.
    */
   public org.w3c.dom.Document getXmlDoc() throws XmlBlasterException
   {
      loadDomTree();
      return xmlToDom.getXmlDoc();
   }


   /**
    * The mode how a subscribe() or get() is formulated.
    * @return EXACT_QUERY or XPATH_QUERY
    */
   public final int getQueryType() throws XmlBlasterException
   {
      loadDomTree();
      return queryType;
   }


   /**
    * The mode how a subscribe() or get() is formulated.
    * @return EXACT_QUERY or XPATH_QUERY
    */
   public final String getQueryTypeStr() throws XmlBlasterException
   {
      int type = getQueryType();
      if (type == XPATH_QUERY)
         return "XPATH";
      else if (type == EXACT_QUERY)
         return "EXACT";
      else if (type == REGEX_QUERY)
         return "REGEX";
      return "";
   }


   /**
    * Was subscribe() or get() formulated as a query?
    * @return true if XPATH_QUERY (or other, not exact query types)
    */
   public final boolean isQuery() throws XmlBlasterException
   {
      int type = getQueryType();
      if (type == XPATH_QUERY || type == REGEX_QUERY)
         return true;
      return false;
   }


   /**
    * Was subscribe() or get() invoked with an exact oid?
    * @return true if EXACT_QUERY
    */
   public final boolean isExact() throws XmlBlasterException
   {
      if (getQueryType() == EXACT_QUERY)
         return true;
      return false;
   }


   public String getQueryString() throws XmlBlasterException
   {
      loadDomTree();
      return queryString;
   }


   /**
    * Fills the DOM tree, and assures that a valid <pre>&lt;key oid="..."></pre> is used.
    * <p>
    * keyOid will be set properly if no error occurs
    * xmlToDom will be set properly if no error occurs
    */
   private void loadDomTree() throws XmlBlasterException
   {
      if (xmlToDom != null)
         return;       // DOM tree is already loaded

      if (keyType == ASCII_TYPE)
         return;       // no XML -> no DOM

      xmlToDom = new XmlToDom(xmlKey_literal);
      org.w3c.dom.Node node = xmlToDom.getRootNode();

      // Finds the <key oid="..." queryType="..."> attributes, or inserts a unique oid if empty
      if (node == null) {
         Log.error(ME+".Internal", "root node = null");
         throw new XmlBlasterException(ME+"Internal", "root node = null");
      }

      String nodeName = node.getNodeName();

      if (!nodeName.equalsIgnoreCase("key")) {
         Log.error(ME+".WrongRootNode", "The root node must be named \"key\"\n" + xmlKey_literal);
         throw new XmlBlasterException(ME+".WrongRootNode", "The root node must be named \"key\"\n" + xmlKey_literal);
      }

      isInternalStateQuery = false;
      if (xmlKey_literal.indexOf("<__sys__internal>") != -1 && xmlKey_literal.indexOf("</__sys__internal>") != -1)
         isInternalStateQuery = true;

      keyOid = null;
      queryType = (isPublish) ? PUBLISH : EXACT_QUERY;

      NamedNodeMap attributes = node.getAttributes();
      if (attributes != null && attributes.getLength() > 0) {
         int attributeCount = attributes.getLength();
         for (int i = 0; i < attributeCount; i++) {
            Attr attribute = (Attr)attributes.item(i);

            if (attribute.getNodeName().equalsIgnoreCase("oid")) {
               String val = attribute.getNodeValue();
               if (val.length() < 1) {
                  keyOid = generateKeyOid();
                  attribute.setNodeValue(keyOid);
                  if (Log.TRACE) Log.trace(ME, "Generated key oid=\"" + keyOid + "\"");
               }
               else {
                  keyOid = val;
                  if (Log.TRACE) Log.trace(ME, "Found key oid=\"" + keyOid + "\"");
                  if (keyOid.indexOf("__sys__") != -1)
                     isInternalStateQuery = true;
               }
            }

            if (isPublish && attribute.getNodeName().equalsIgnoreCase("contentMime")) {
               contentMime = attribute.getNodeValue();
               if (contentMime == null || contentMime.length()<1) contentMime = "text/plain";
            }

            if (isPublish && attribute.getNodeName().equalsIgnoreCase("contentMimeExtended")) {
               contentMimeExtended = attribute.getNodeValue();
               if (contentMimeExtended == null) contentMimeExtended = "";
            }

            if (!isPublish && attribute.getNodeName().equalsIgnoreCase("queryType")) {
               String val = attribute.getNodeValue();
               if (val.equalsIgnoreCase("EXACT"))
                  queryType = EXACT_QUERY;
               else if (val.equalsIgnoreCase("XPATH"))
                  queryType = XPATH_QUERY;
               else if (val.equalsIgnoreCase("REGEX"))
                  queryType = REGEX_QUERY;
               else {
                  Log.warn(ME+".UnknownQueryType", "Unknown queryType " + val + ", setting default to EXACT");
                  //throw new XmlBlasterException(ME+".UnknownQueryType", "Unknown queryType " + val + ", your xmlKey is invalid");
               }
            }
         }
      }

      if (keyOid == null) {
         Log.error(ME+".WrongRootNode", "Missing \"oid\" attribute in \"key\" tag");
         throw new XmlBlasterException(ME+".WrongRootNode", "Missing \"oid\" attribute in \"key\" tag");
      }

      if (isPublish && contentMime == null) {
         Log.warn(ME+".MissingContentMime", "Missing \"contentMime\" attribute in \"key\" tag");
         contentMime = "text/plain";
      }

      //xmlKey_nice = toNiceXml("");

      // extract the query string <key ...>'The query string'</key>
      if (!isPublish && queryType != EXACT_QUERY) {
         NodeList children = node.getChildNodes();
         if (children != null) {
            int len = children.getLength();
            for (int i = 0; i < len; i++) {
               Node childNode = children.item(i);
               if (childNode.getNodeType() == Node.TEXT_NODE) {
                  queryString = childNode.getNodeValue().trim();
               }
            }
         }
         if (queryString==null || queryString.length() < 1) {
            Log.error(ME+".MissingQuery", "Missing query string in <key> ... </key> tag");
            throw new XmlBlasterException(ME+".MissingQuery", "Missing query string in <key> ... </key> tag");
         }
      }

      //if (/*isPublish && */isGeneratedOid) We do it allways to have nice formatting for emails etc.
         xmlKey_literal = xmlToDom.domToXml("\n"); // write the generated key back to literal string
   }


   /**
    * Should be called by publish() to merge the local XmlKey DOM into the big xmlBlaster DOM tree
    */
   public void mergeRootNode(I_MergeDomNode merger) throws XmlBlasterException
   {
      if (isPublish) {
         if (Log.TRACE) Log.trace(ME, "Created DOM tree for " + getUniqueKey() + ", adding it to <xmlBlaster> tree");
         xmlToDom.mergeRootNode(merger);
      }
      else {
         Log.plain(org.jutils.runtime.StackTrace.getStackTrace());
         Log.warn(ME, "You should call mergeNode only for publish");
      }
   }


   /**
    * Generates a unique key.
    * <p />
    * TODO: include IP adress and PID for global uniqueness
    */
   private String generateKeyOid()
   {
      StringBuffer oid = new StringBuffer(60);

      if (ip_addr == null) {
         try {
            ip_addr = java.net.InetAddress.getLocalHost().getHostAddress(); // "192.168.1.1" from "swand.lake.de/192.168.1.1"
         } catch (java.net.UnknownHostException e) {
            if (Log.TRACE) Log.trace(ME, e.toString());
         }
      }
      if (ip_addr == null)
         ip_addr = "127.0.0.0";

      if (oa_port == null)
         oa_port = XmlBlasterProperty.get("iorPort", "7609"); // default xmlBlaster IOR publishing port is 7609 (HTTP_PORT)
         //  java.net.ServerSocket.getLocalPort();

      long currentTime = System.currentTimeMillis();

      oid.append(ip_addr).append("-").append(oa_port).append("-").append(currentTime);

      generateKeyOidCounter(oid);

      isGeneratedOid = true;
      return oid.toString();
   }


   /**
    * Little helper method, which is synchronized.
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
   public final StringBuffer printOn()
   {
      return printOn((String)null);
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of MessageUnitHandler
    */
   public final StringBuffer printOn(String extraOffset)
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      try {
         sb.append(offset).append("<XmlKeyBase oid='").append(getUniqueKey()).append("'");
         sb.append(" contentMime='").append(contentMime).append("'");
         sb.append(" contentMimeExtended='").append(contentMimeExtended).append("'");
         if (queryType != PUBLISH)
            sb.append(" queryType='").append(getQueryTypeStr()).append("'");
         sb.append(">\n");

         if (queryString.length() > 0)
            sb.append(offset).append("   <queryString>").append(queryString).append("</queryString>\n");
         sb.append(offset).append("   <keyType>").append(keyType).append("</keyType>\n");
         sb.append(offset).append("   <isGeneratedOid>").append(isGeneratedOid).append("</isGeneratedOid>\n");
         sb.append(offset).append("   <isPublish>").append(isPublish).append("</isPublish>\n");
         sb.append(offset).append("   <isInternalStateQuery>").append(isInternalStateQuery).append("</isInternalStateQuery>\n");
         sb.append(xmlToDom.printOn(extraOffset + "   ").toString());
         sb.append(offset).append("</XmlKeyBase>\n");
      } catch (XmlBlasterException e) {
         Log.warn(ME, "Caught exception in printOn()");
      }
      return sb;
   }

}
