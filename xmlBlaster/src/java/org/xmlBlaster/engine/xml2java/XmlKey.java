/*------------------------------------------------------------------------------
Name:      XmlKey.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey, knows how to parse it with SAX
Version:   $Id: XmlKey.java,v 1.30 2002/11/26 12:38:57 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.xml2java;

import org.jutils.text.StringHelper;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlToDom;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.I_MergeDomNode;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.AccessFilterQos;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Attr;

import java.util.Enumeration;
import java.util.Vector;


/**
 * This class encapsulates the Message meta data and unique identifier.
 * <p />
 * All XmlKey's have the same XML minimal structure:<p>
 * <pre>
 *    &lt;key oid="12345"/>
 * </pre>
 * or
 * <pre>
 *    &lt;key oid="12345">
 *       &lt;!-- application specific tags -->
 *    &lt;/key>
 * </pre>
 *
 * where oid is a unique key.
 * <p />
 * A typical <b>publish</b> key could look like this:<br />
 * <pre>
 *     &lt;key oid='4711' contentMime='text/xml'>
 *        &lt;AGENT id='192.168.124.20' subId='1' type='generic'>
 *           &lt;DRIVER id='FileProof' pollingFreq='10'>
 *           &lt;/DRIVER>
 *        &lt;/AGENT>
 *     &lt;/key>
 * </pre>
 * <br />
 * Note that the AGENT and DRIVER tags are application know how, which you have to supply.<br />
 * A well designed xml hierarchy of your problem domain is essential for a proper working xmlBlaster
 * <p />
 * A typical <b>subscribe</b> key could look like this:<br />
 * <pre>
 *     &lt;key oid='4711' queryType='EXACT'/>
 * </pre>
 * <br />
 * In this example you would subscribe on message 4711.
 * <p />
 * A typical <b>subscribe</b> using XPath query syntax could look like this:<br />
 * <pre>
 *     &lt;key oid='' queryType='XPATH'>
 *        //DRIVER[@id='FileProof']
 *     &lt;/key>
 * </pre>
 * <br />
 * In this example you would subscribe on all DRIVERs which have the attribute 'FileProof'
 * <p />
 * A cluster query checking the <b>domain</b> attribute could look like this:<br />
 * <pre>
 *     &lt;key oid='' queryType='DOMAIN' domain='RUGBY'/>
 * </pre>
 * <p />
 * NOTE: The 'XPATH' query covers the 'DOMAIN' and 'EXACT' query, but is far slower.
 * Therefore you should try to use EXACT or in a cluster environment DOMAIN queries
 * first.
 * <br />
 * More examples you find in xmlBlaster/src/dtd/XmlKey.xml
 * <p />
 *
 * @see <a href="http://www.w3.org/TR/xpath" target="others">The W3C XPath specification</a>
 */
public final class XmlKey
{
   private String ME = "XmlKey";
   private Global glob;
   private LogChannel log;

   private XmlToDom xmlToDom = null;

   private static long uniqueCounter = 1L;

   public final static int PUBLISH     = 0;  // no query: <key oid='myWhitePaper'><book><paper></paper></book></key> + content
   public final static int EXACT_QUERY = 1;  // <key oid="myCarPrice" queryType="EXACT"></key>
   public final static int XPATH_QUERY = 2;  // <key oid=""           queryType="XPATH">xmlBlaster/key/AGENT/DRIVER[@id!='FileProof']</key>
   public final static int REGEX_QUERY = 3;  // <key oid="my.*"       queryType="REGEX"></key>
   public final static int DOMAIN_QUERY = 4; // <key oid=""           queryType="DOMAIN" domain="RUBGY"/>
   private int queryType = -1;
   private String queryString = "";

   public final int XML_TYPE = 0;   // xml syntax
   public final int ASCII_TYPE = 1; // for trivial keys you can use a ASCII String (no XML)
   private int keyType = XML_TYPE;  // XmlKey uses XML syntax (default)

   private int isGeneratedOid = -1;      // is oid new generated by xmlBlaster?
   private boolean isPublish = false;    // called via subscribe()

   /** The XML ASCII string */
   protected String xmlKey_literal;
   /** A nicer formatted ASCII XML string (same content as xmlKey_literal but indented) */
   protected String xmlKey_nice;

   /** Value from attribute &lt;key oid="..."> */
   protected String keyOid = null;

   /** The MIME type of the content, RFC1521 */
   public static final String DEFAULT_contentMime = "text/plain";
   protected String contentMime = null;

   /** Some further content info, e.g. the version number */
   public static final String DEFAULT_contentMimeExtended = "";
   protected String contentMimeExtended = null;

   /** The domain attribute, can be used to classify the message for simple clustering */
   public static final String DEFAULT_domain = "";
   protected String domain = null; // set first to null to force parsing

   /**
    * A DOM tree containing exactly one (this) message to allow XPath subscriptions to check if this message matches
    * <pre>
    *    &lt;xmlBlaster>
    *       &lt;key oid='xx'>
    *           ...
    *       &lt;/key>
    *    &lt;/xmlBlaster>
    * </pre>
    */
   private org.w3c.dom.Document xmlKeyDoc = null;// Document with the root node

   /**
    *  We need this query manager to allow checking if an existing XPath subscription matches this new message type.
    */
   private com.fujitsu.xml.omquery.DomQueryMgr queryMgr = null;

   /**
    * subscribe(), get() and cluster configuration keys may contain a filter rule
    */
   protected Vector filterVec = null;                      // To collect the <filter> when sax parsing
   protected transient AccessFilterQos[] filterArr = null; // To cache the filters in an array


   /**
    * Parses given xml string
    * DON'T use this constructor for publish() Messages
    *
    * @param The original key in XML syntax, for example:<br>
    *        <pre><key oid="This is the unique attribute"></key></pre>
    */
   public XmlKey(Global glob, String xmlKey_literal) throws XmlBlasterException {
      init(glob, xmlKey_literal, false);
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
   public XmlKey(Global glob, String xmlKey_literal, boolean isPublish) throws XmlBlasterException {
      init(glob, xmlKey_literal, isPublish);
   }

   /**
    */
   private void init(Global glob, String xmlKey_literal, boolean isPublish) throws XmlBlasterException {
      if (glob == null) {
         this.glob = new Global();
         glob.getLog("core").warn(ME, "Created new Global");
      }
      else
         this.glob = glob;
      this.log = glob.getLog("core");

      this.isPublish = isPublish;

      if (log.CALL) log.trace(ME, "Creating new XmlKey for isPublish=" + isPublish);

      this.xmlKey_literal = xmlKey_literal.trim();

      if (!this.xmlKey_literal.startsWith("<")) {
         keyType = ASCII_TYPE;  // eg "Airport/Runway1/WindVeloc3"
         keyOid = this.xmlKey_literal;

         // Works well with ASCII, but is switched off for the moment
         // perhaps we should make it configurable through a property file !!!
         // Example: xmlKey_literal="Airport.*" as a regular expression

         log.warn(ME+".XML", "Invalid XmlKey syntax, only XML syntax beginning with \"<\" is supported: '" + this.xmlKey_literal + "'");
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(ME+".XML", "Invalid XmlKey syntax, only XML syntax beginning with \"<\" is supported");

      }
   }

   /*
    * Try to find out if this is an internal message only
   public boolean isLocalMessage() throws XmlBlasterException
   {
      if (getKeyOid().startsWith(Constants.INTERNAL_OID_PREFIX) &&
          getKeyOid().indexOf("["+glob.getId()+"]") >= 0)
         return true;

      if (isDefaultDomain())
         return true;

      return false;
   }
    */

   /**
    * Test if oid is '__sys__deadMessage'. 
    * <p />
    * Dead letters are unrecoverable lost messages, usually an administrator
    * should subscribe to those messages.
    */
   public final boolean isDeadMessage() throws XmlBlasterException {
      return getUniqueKey().equals(Constants.OID_DEAD_LETTER);
   }

   /**
    * Access the literal ASCII xmlKey.
    * @return the literal ASCII xmlKey
    * @see #literal()
    */
   public final String toString() {
      return toXml();
   }

   /**
    * Access the literal ASCII xmlKey.
    * @return the literal ASCII xmlKey
    * @see #literal()
    */
   public String toXml() {
      log.warn(ME, "Accessing raw xml key string");
      Thread.currentThread().dumpStack();
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
   public final String literal() {
      return xmlKey_literal;
   }

   /**
    * Synonym for getKeyOid().
    *
    * @return oid
    */
   public final String getUniqueKey() throws XmlBlasterException {
      return getKeyOid();
   }

   /**
    * Is the key oid generated or given by the client?
    *
    * @return true generated oid by xmlBlaster
    */
   public final boolean isGeneratedOid() throws XmlBlasterException {
      if (isGeneratedOid == -1)
         loadDomTree();
      return (isGeneratedOid==1) ? true : false;
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
   public final String getContentMime() throws XmlBlasterException {
      if (contentMime == null) {
         parseRaw();
      }
      if (contentMime != null) {
         return contentMime;
      }
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
   public final String getContentMimeExtended() throws XmlBlasterException {
      if (contentMimeExtended == null || contentMimeExtended.length() < 1) {
         parseRaw();
      }
      if (contentMimeExtended != null) {
         return contentMimeExtended;
      }
      loadDomTree();
      return contentMimeExtended;
   }

   /**
    * Access the domain for this message, can be used for a simple grouping of
    * messages to their master node with xmlBlaster clusters. 
    * @return The domain, any chosen string in your problem domain, e.g. "RUGBY" or "RADAR_TRACK"
    *         defaults to "" where the local xmlBlaster instance is the master of the message.
    * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/cluster.html">The cluster requirement</a>
    */
   public final String getDomain() throws XmlBlasterException {
      if (domain == null) {
         parseRaw();
      }
      if (domain != null) {
         return domain;
      }
      loadDomTree();
      return domain;
   }

   /**
    * @return true if no domain is given (null or empty string). 
    */
   public final boolean isDefaultDomain() throws XmlBlasterException {
      String domain = getDomain();
      if (domain == null || domain.equals(DEFAULT_domain))
         return true;
      return false;
   }

   /**
    * Accessing the unique oid of <key oid="...">.
    *
    * @return oid
    */
   public final String getKeyOid() throws XmlBlasterException {
      if (keyOid == null) {
         parseRaw();
      }
      if (keyOid != null) {
         return keyOid;
      }
      //log.info(ME, "keyOid='" + keyOid + "'");
      loadDomTree();
      return keyOid;
   }

   /**
    * Try to parse keyOid and queryType ourself
    */
   private final void parseRaw() throws XmlBlasterException {
      keyOid = parseRaw(xmlKey_literal, "<key oid=");
      if (keyOid != null) {
         if (keyOid.length() < 1) {
            keyOid = null;
            return; // failed
         }
         isGeneratedOid = 0;

         // try to find the queryType etc.:
         int start = xmlKey_literal.indexOf("<key oid=");
         int close = xmlKey_literal.indexOf('>', start);
         String keyToken = xmlKey_literal.substring(start, close);
         String tmp;

         tmp = parseRaw(keyToken, "queryType=");
         if (tmp != null && tmp.length() > 0) {
            setQueryType(tmp);
            //log.info(ME, "queryType='" + tmp + "'");
         }
         else {
            queryType = (isPublish) ? PUBLISH : EXACT_QUERY;
         }

         tmp = parseRaw(keyToken, "contentMime=");
         if (tmp != null && tmp.length() > 0) {
            contentMime = tmp;
            //log.info(ME, "contentMime='" + tmp + "'");
         }
         else {
            contentMime = DEFAULT_contentMime;
         }

         tmp = parseRaw(keyToken, "contentMimeExtended=");
         if (tmp != null && tmp.length() > 0) {
            contentMimeExtended = tmp;
            //log.info(ME, "contentMimeExtended='" + tmp + "'");
         }
         else {
            contentMimeExtended = DEFAULT_contentMimeExtended;
         }

         tmp = parseRaw(keyToken, "domain=");
         if (tmp != null && tmp.length() > 0) {
            domain = tmp;
            //log.info(ME, "domain='" + tmp + "'");
         }
         else {
            domain = DEFAULT_domain;
         }
      }
   }

   /**
    * Parse xml ourself, to gain performance
    */
   private final String parseRaw(String str, String token) {
      int index = str.indexOf(token);
      if (index >= 0) {
         int from = index+token.length();
         char apo = str.charAt(from);
         int end = str.indexOf(apo, from+1);
         if (end > 0) {
            return str.substring(from+1, end);
         }
      }
      return null;
   }

   /**
    * Messages starting with "_" are reserved for usage in plugins
    */
   public final boolean isPluginInternal() {
      try {
         return (getUniqueKey() == null) ? false : (getUniqueKey().startsWith(Constants.INTERNAL_OID_PREFIX_FOR_PLUGINS) && !getUniqueKey().startsWith(Constants.INTERNAL_OID_PREFIX_FOR_CORE));
      } catch (XmlBlasterException e) {
         return false;
      }
   }

   /**
    * Messages starting with "__" are reserved for internal usage
    */
   public final boolean isInternal() {
      try {
         return (getUniqueKey() == null) ? false : getUniqueKey().startsWith(Constants.INTERNAL_OID_PREFIX_FOR_CORE);
      } catch (XmlBlasterException e) {
         return false;
      }
   }

   /**
    * Fills the DOM tree, and assures that a valid <key oid="..."> is used.
    */
   public org.w3c.dom.Node getRootNode() throws XmlBlasterException {
      loadDomTree();
      return xmlToDom.getRootNode();
   }

   /**
    * Fills the DOM tree, and assures that a valid <key oid="..."> is used.
    */
   public org.w3c.dom.Document getXmlDoc() throws XmlBlasterException {
      loadDomTree();
      return xmlToDom.getXmlDoc();
   }

   /**
    * The mode how a subscribe() or get() is formulated.
    * @return EXACT_QUERY or XPATH_QUERY or for cluster setup DOMAIN_QUERY
    */
   public final int getQueryType() throws XmlBlasterException {
      if (queryType == -1) {
         parseRaw();
      }
      if (queryType != -1) {
         return queryType;
      }

      loadDomTree();
      return queryType;
   }

   /**
    * The mode how a subscribe() or get() is formulated.
    * @return "EXACT" or "XPATH" or for cluster setup "DOMAIN"
    */
   public final String getQueryTypeStr() throws XmlBlasterException {
      int type = getQueryType();
      if (type == XPATH_QUERY)
         return Constants.XPATH;
      else if (type == EXACT_QUERY)
         return Constants.EXACT;
      else if (type == DOMAIN_QUERY)
         return Constants.DOMAIN;
      else if (type == REGEX_QUERY)
         return Constants.REGEX;
      return "";
   }


   /**
    * Was subscribe() or get() formulated as a query?
    * @return true if XPATH_QUERY (or other, not exact query types)
    */
   public final boolean isQuery() throws XmlBlasterException {
      int type = getQueryType();
      if (type == XPATH_QUERY || type == REGEX_QUERY)
         return true;
      return false;
   }

   /**
    * Was subscribe() or get() invoked with an exact oid?
    * @return true if EXACT_QUERY
    */
   public final boolean isExact() throws XmlBlasterException {
      if (getQueryType() == EXACT_QUERY)
         return true;
      return false;
   }

   /**
    * Was subscribe() or get() invoked to query the domain?
    * @return true if DOMAIN_QUERY
    */
   public final boolean isDomain() throws XmlBlasterException {
      if (getQueryType() == DOMAIN_QUERY)
         return true;
      return false;
   }

   /**
    * Was subscribe() or get() invoked to query with XPath?
    * @return true if XPATH_QUERY
    */
   public final boolean isXPath() throws XmlBlasterException {
      if (getQueryType() == XPATH_QUERY)
         return true;
      return false;
   }

   private final void setQueryType(String val) throws XmlBlasterException {
      if (val.equalsIgnoreCase(Constants.EXACT))
         queryType = EXACT_QUERY;
      else if (val.equalsIgnoreCase(Constants.XPATH))
         queryType = XPATH_QUERY;
      else if (val.equalsIgnoreCase(Constants.DOMAIN))
         queryType = DOMAIN_QUERY;
      else if (val.equalsIgnoreCase(Constants.REGEX))
         queryType = REGEX_QUERY;
      else {
         log.warn(ME+".UnknownQueryType", "Unknown queryType " + val + ", setting default to EXACT");
         //throw new XmlBlasterException(ME+".UnknownQueryType", "Unknown queryType " + val + ", your xmlKey is invalid");
      }
   }

   /*
    * Access the query string, for example the XPath string "/xmlBlaster/key[@contentMime='text/plain']"
    */
   public final String getQueryString() throws XmlBlasterException {
      loadDomTree();
      return queryString;
   }

   private final void setQueryString(String queryString) {
      if (queryString == null) return;
      this.queryString = queryString;
   }

   /**
    * Fills the DOM tree, and assures that a valid <pre>&lt;key oid="..."></pre> is used.
    * <p>
    * keyOid will be set properly if no error occurs
    * xmlToDom will be set properly if no error occurs
    */
   private synchronized void loadDomTree() throws XmlBlasterException {
      if (xmlToDom != null)
         return;       // DOM tree is already loaded

      if (keyType == ASCII_TYPE)
         return;       // no XML -> no DOM

      xmlToDom = new XmlToDom(glob,xmlKey_literal);
      org.w3c.dom.Node node = xmlToDom.getRootNode();

      // Finds the <key oid="..." queryType="..."> attributes, or inserts a unique oid if empty
      if (node == null) {
         log.error(ME+".Internal", "root node = null");
         throw new XmlBlasterException(ME+"Internal", "root node = null");
      }

      String nodeName = node.getNodeName();

      if (!nodeName.equalsIgnoreCase("key")) {
         log.error(ME+".WrongRootNode", "The root node must be named \"key\"\n" + xmlKey_literal);
         throw new XmlBlasterException(ME+".WrongRootNode", "The root node must be named \"key\"\n" + xmlKey_literal);
      }

      /*
      isInternalStateQuery = false;
      if (xmlKey_literal.indexOf("<__sys__internal>") != -1 && xmlKey_literal.indexOf("</__sys__internal>") != -1)
         isInternalStateQuery = true;
      */

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
                  if (keyOid == null)
                     keyOid = generateKeyOid();
                  attribute.setNodeValue(keyOid);
                  if (log.TRACE) log.trace(ME, "Generated key oid=\"" + keyOid + "\"");
               }
               else {
                  keyOid = val;
                  isGeneratedOid = 0;
                  if (log.TRACE) log.trace(ME, "Found key oid=\"" + keyOid + "\"");
               }
            }

            if (isPublish && attribute.getNodeName().equalsIgnoreCase("contentMime")) {
               contentMime = attribute.getNodeValue();
               if (contentMime == null || contentMime.length()<1) contentMime = DEFAULT_contentMime;
            }

            if (isPublish && attribute.getNodeName().equalsIgnoreCase("contentMimeExtended")) {
               contentMimeExtended = attribute.getNodeValue();
               if (contentMimeExtended == null) contentMimeExtended = DEFAULT_contentMimeExtended;
            }

            if (attribute.getNodeName().equalsIgnoreCase("domain")) {
               domain = attribute.getNodeValue();
               if (domain == null || domain.length()<1) domain = DEFAULT_domain;
            }

            if (!isPublish && attribute.getNodeName().equalsIgnoreCase("queryType")) {
               String val = attribute.getNodeValue();
               setQueryType(val);
            }
         }
      }

      if (keyOid == null) {
         keyOid = generateKeyOid();
         //log.error(ME+".WrongRootNode", "Missing \"oid\" attribute in \"key\" tag");
         //throw new XmlBlasterException(ME+".WrongRootNode", "Missing \"oid\" attribute in \"key\" tag");
      }

      //log.info(ME+".DOM", "parsing DOM: " + keyOid);
      //Thread.currentThread().dumpStack();

      if (isPublish && contentMime == null) {
         log.warn(ME+".MissingContentMime", "Missing \"contentMime\" attribute in \"key\" tag");
         contentMime = "text/plain";
      }

      //xmlKey_nice = toNiceXml("");

      // extract the query string <key ...>'The query string'</key>
      if (!isPublish && isQuery()) {
         NodeList children = node.getChildNodes();
         if (children != null) {
            int len = children.getLength();
            for (int i = 0; i < len; i++) {
               Node childNode = children.item(i);
               if (childNode.getNodeType() == Node.TEXT_NODE) {
                  String txt = childNode.getNodeValue().trim();
                  if (txt.length() > 0)
                     queryString = txt;
               }
            }
         }
         if (queryString==null || queryString.length() < 1) {
            log.error(ME+".MissingQuery", "Missing query string in <key queryType='XPATH'>//key</key> tag");
            throw new XmlBlasterException(ME+".MissingQuery", "Missing query string in <key queryType='XPATH'>//key</key> tag");
         }
      }

      //log.info(ME, "DOM parsed the XmlKey");
      //Thread.currentThread().dumpStack();

      //if (/*isPublish && */isGeneratedOid) We do it allways to have nice formatting for emails etc.
         xmlKey_literal = xmlToDom.domToXml("\n"); // write the generated key back to literal string
   }

   /**
    * Should be called by publish() to merge the local XmlKey DOM into the big xmlBlaster DOM tree
    */
   public final void mergeRootNode(I_MergeDomNode merger) throws XmlBlasterException {
      if (isPublish) {
         if (log.TRACE) log.trace(ME, "Created DOM tree for " + getUniqueKey() + ", adding it to <xmlBlaster> tree");
         loadDomTree();
         xmlToDom.mergeRootNode(merger);
      }
      else {
         log.plain("", org.jutils.runtime.StackTrace.getStackTrace());
         log.warn(ME, "You should call mergeNode only for publish");
      }
   }

   /**
    * Generates a unique key.
    * <p />
    * TODO: include IP adress and PID for global uniqueness
    */
   private final String generateKeyOid() {
      StringBuffer oid = new StringBuffer(80);

      // Windows does not like ":" and Unix does not like "/" when written to harddisk with FileDriver
      oid.append(glob.getStrippedId()).append("-").append(System.currentTimeMillis()).append("-").append(uniqueCounter);
      synchronized (XmlKey.class) {
         uniqueCounter++;
      }
      isGeneratedOid = 1;
      return oid.toString();
   }

   /**
    * Dump state of this object into XML.
    * <br>
    * @return XML state of MessageUnitHandler
    */
   public final StringBuffer printOn() {
      return printOn((String)null);
   }

   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of MessageUnitHandler
    */
   public final StringBuffer printOn(String extraOffset) {
      StringBuffer sb = new StringBuffer(256);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      try {
         sb.append(offset).append("<XmlKey oid='").append(getUniqueKey()).append("'");
         if (!DEFAULT_contentMime.equals(getContentMime()))
            sb.append(" contentMime='").append(contentMime).append("'");
         if (!DEFAULT_contentMimeExtended.equals(getContentMimeExtended()))
            sb.append(" contentMimeExtended='").append(contentMimeExtended).append("'");
         if (!DEFAULT_domain.equals(getDomain()))
            sb.append(" domain='").append(domain).append("'");
         if (queryType != PUBLISH)
            sb.append(" queryType='").append(getQueryTypeStr()).append("'");
         sb.append(">");

         if (queryString.length() > 0)
            sb.append(offset).append("   <queryString>").append(queryString).append("</queryString>");
         sb.append(offset).append("   <keyType>").append(keyType).append("</keyType>");
         sb.append(offset).append("   <isGeneratedOid>").append(isGeneratedOid()).append("</isGeneratedOid>");
         sb.append(offset).append("   <isPublish>").append(isPublish).append("</isPublish>");
         sb.append(offset).append("   <isInternal>").append(isInternal()).append("</isInternal>");
         if (xmlToDom != null)
            sb.append(xmlToDom.printOn(extraOffset + "   ").toString());
         sb.append(offset).append("</XmlKey>");
      } catch (XmlBlasterException e) {
         log.warn(ME, "Caught exception in printOn()");
      }
      return sb;
   }

   /**
    * Allows to check if this xmlKey matches the given query. 
    * @param queryKey An XmlKey object containing a query (XPATH, EXACT or DOMAIN)
    * @return true if this message key matches the query
    */
   public final boolean match(XmlKey queryKey) throws XmlBlasterException {
      if (queryKey.isDomain()) {
         if (queryKey.getDomain() == null) {
            log.error(ME, "Your query is of type DOMAIN but you have not specified a domain: " + queryKey.literal());
            throw new XmlBlasterException(ME, "Your query is of type DOMAIN but you have not specified a domain: " + queryKey.literal());
         }
         if (queryKey.getDomain().equals("*") || queryKey.getDomain().equals(getDomain())) {
            if (log.TRACE) log.trace(ME, "Message oid='" + getUniqueKey() + "' matched for domain='" + getDomain() + "'.");
            return true;
         }
      }
      else if (queryKey.isExact()) {
         if (queryKey.getUniqueKey().equals(getUniqueKey())) {
            if (log.TRACE) log.trace(ME, "Message oid='" + getUniqueKey() + "' matched.");
            return true;
         }
      }
      else if (queryKey.isXPath()) {
         if (match(queryKey.getQueryString())) {
            if (log.TRACE) log.trace(ME, "Message oid='" + getUniqueKey() + "' matched with XPath query '" + queryKey.getQueryString() + "'");
            return true;
         }
      }
      else {
         log.error(ME, "Don't know queryType '" + queryKey.getQueryTypeStr() + "' for message oid='" + getUniqueKey() + "'. I'll return false for match.");
         return false;
      }
      
      if (log.TRACE) log.trace(ME, "Message oid='" + getUniqueKey() + "' does not match with query");
      return false;
   }

   /**
    * We need this to allow checking if an existing XPath subscription matches this new message type.
    * @param xpath The XPath query, check if it matches to this xmlKey
    * @return true if this message meta data matches the XPath query
    */
   public final boolean match(String xpath) throws XmlBlasterException {
      if (xmlKeyDoc == null) {
         try {
            if (log.TRACE) log.trace(ME, "Creating tiny DOM tree and a query manager ...");
            // Add the <xmlBlaster> root element ...
            String tmp = StringHelper.replaceFirst(xmlKey_literal, "<key", "<xmlBlaster><key") + "</xmlBlaster>";
            XmlToDom tinyDomHandle = new XmlToDom(glob,tmp);
            xmlKeyDoc = tinyDomHandle.getXmlDoc();
            queryMgr = new com.fujitsu.xml.omquery.DomQueryMgr(xmlKeyDoc);
         } catch (Exception e) {
            String text = "Problems building tiny key DOM tree\n" + xmlKey_literal + "\n for XPath subscriptions check: " + e.toString();
            log.error(ME + ".MergeNodeError", text);
            e.printStackTrace();
            throw new XmlBlasterException("MergeNodeError", text);
         }
      }
      try {
         Enumeration nodeIter = queryMgr.getNodesByXPath(xmlKeyDoc, xpath);
         if (nodeIter != null && nodeIter.hasMoreElements()) {
            log.info(ME, "XPath subscription '" + xpath + "' matches message '" + getKeyOid() + "'");
            return true;
         }
      }
      catch (Exception e) {
         String text = "XPath query on tiny key DOM tree\n" + xmlKey_literal + "\nfailed: " + e.toString();
         log.error(ME + ".XPathError", text);
         e.printStackTrace();
         throw new XmlBlasterException("XPathError", text);
      }
      if (log.TRACE) log.trace(ME, "XPath subscription '" + xpath + "' does NOT match message '" + getKeyOid() + "'");
      return false;
   }

   /**
    * After the existing XPath subscriptions have queried this message
    * we should release the DOM tree.
    */
   public void cleanupMatch()
   {
      if (log.TRACE) log.trace(ME, "Releasing tiny DOM tree");
      queryMgr = null;
      xmlKeyDoc = null;
   }

   /**
    * Do a sax parse, currently only needed for filter rules
    */
   private void saxParse() throws XmlBlasterException {
      filterVec = new Vector(5);
      XmlKeySax sax = new XmlKeySax(glob, this);
      sax.init(xmlKey_literal);
   }

   /**
    * Return the filters or array with size==0 if none is specified. 
    * <p />
    * For subscribe() and get() and cluster messages.
    * @return never null
    */
   public final AccessFilterQos[] getAccessFilterArr() throws XmlBlasterException {
      if (filterVec == null)
         saxParse(); // initialize

      if (filterArr != null)
         return filterArr;

      filterArr = new AccessFilterQos[filterVec.size()];
      filterVec.toArray(filterArr);
      return filterArr;
   }

   final void addFilter(AccessFilterQos qos) {
      if (filterVec == null) filterVec = new Vector();
      filterVec.addElement(qos);
   }

   /** For testing: java org.xmlBlaster.engine.xml2java.XmlKey */
   public static void main(String[] args)
   {
      int count = 1000;
      int runs = 5;
      long startTime;
      long elapsed;
      String testName;
      XmlKey key;
      Global glob = new Global(args);
      // Test on 600 MHz Linux 2.4 with SUN Jdk 1.3.1 beta 15
      try {
         key = new XmlKey(glob, "<key oid='Hello' queryType='XPATH'>//key</key>");
         System.out.println("keyOid=|" + key.getKeyOid() + "| queryType=" + key.getQueryTypeStr() + "\n" + key.toXml());
         
         key = new XmlKey(glob, "<key oid=\"Hello\" queryType=''><Hacker /></key>");
         System.out.println("keyOid=|" + key.getKeyOid() + "| queryType=" + key.getQueryTypeStr() + "\n" + key.toXml());

         key = new XmlKey(glob, "<key   oid='' queryType='EXACT'/>");
         System.out.println("keyOid=|" + key.getKeyOid() + "| queryType=" + key.getQueryTypeStr() + "\n" + key.toXml());

         key = new XmlKey(glob, "<key oid='' contentMime='application/dummy' contentMimeExtended='1.0' domain='RUGBY'><Hacker /></key>");
         System.out.println("keyOid=|" + key.getKeyOid() + "| queryType=" + key.getQueryTypeStr() + "\n" + key.toXml());

         for (int kk=0; kk<runs; kk++) {
            testName = "DomParseGivenOid";
            startTime = System.currentTimeMillis();
            for (int ii=0; ii<count; ii++) {
               key = new XmlKey(glob, "<key oid='Hello'><Hacker /></key>");
               key.getQueryType(); // Force DOM parse
               String oid = key.getKeyOid();
               String query = key.getQueryString(); // Force a DOM parse
               //System.out.println(key.toXml());
            }
            elapsed = System.currentTimeMillis() - startTime;
            System.out.println(testName + ": For " + count + " runs " + elapsed + " millisec -> " + ((double)elapsed*1000.)/((double)count) + " mycrosec/inout");
            /*
               DomParseGivenOid: For 1000 runs 1518 millisec -> 1518.0 mycrosec/inout
               DomParseGivenOid: For 1000 runs 1087 millisec -> 1087.0 mycrosec/inout
               DomParseGivenOid: For 1000 runs 731 millisec -> 731.0 mycrosec/inout
               DomParseGivenOid: For 1000 runs 711 millisec -> 711.0 mycrosec/inout
               DomParseGivenOid: For 1000 runs 730 millisec -> 730.0 mycrosec/inout
            */
         }

         for (int kk=0; kk<runs; kk++) {
            testName = "DomParseGeneratedOid";
            startTime = System.currentTimeMillis();
            for (int ii=0; ii<count; ii++) {
               key = new XmlKey(glob, "<key oid=''><Hacker /></key>");
               String oid = key.getKeyOid();
               //System.out.println(key.toXml()); // oid="192.168.1.2-3412-1015227424082-660"
            }
            elapsed = System.currentTimeMillis() - startTime;
            System.out.println(testName + ": For " + count + " runs " + elapsed + " millisec -> " + ((double)elapsed*1000.)/((double)count) + " mycrosec/inout");
            /*
               DomParseGeneratedOid: For 1000 runs 808 millisec -> 808.0 mycrosec/inout
               DomParseGeneratedOid: For 1000 runs 807 millisec -> 807.0 mycrosec/inout
               DomParseGeneratedOid: For 1000 runs 781 millisec -> 781.0 mycrosec/inout
               DomParseGeneratedOid: For 1000 runs 773 millisec -> 773.0 mycrosec/inout
               DomParseGeneratedOid: For 1000 runs 784 millisec -> 784.0 mycrosec/inout
            */
         }
 
         for (int kk=0; kk<runs; kk++) {
            testName = "SimpleParseGivenOid";
            startTime = System.currentTimeMillis();
            for (int ii=0; ii<count; ii++) {
               key = new XmlKey(glob, "<key oid='Hello'><Hacker /></key>");
               String oid = key.getKeyOid();
               String domain = key.getDomain();  // the key attributes we parse ourself (without DOM)
               String contentMime = key.getContentMime();
               String contentMimeExtended = key.getContentMimeExtended();
               int queryType = key.getQueryType();
               //System.out.println(key.toXml());
            }
            elapsed = System.currentTimeMillis() - startTime;
            System.out.println(testName + ": For " + count + " runs " + elapsed + " millisec -> " + ((double)elapsed*1000.)/((double)count) + " mycrosec/inout");
            /*
               SimpleParseGivenOid: For 1000 runs 5 millisec -> 5.0 mycrosec/inout
               SimpleParseGivenOid: For 1000 runs 16 millisec -> 16.0 mycrosec/inout
               SimpleParseGivenOid: For 1000 runs 6 millisec -> 6.0 mycrosec/inout
               SimpleParseGivenOid: For 1000 runs 7 millisec -> 7.0 mycrosec/inout
               SimpleParseGivenOid: For 1000 runs 6 millisec -> 6.0 mycrosec/inout
            */
         }

         for (int kk=0; kk<runs; kk++) {
            testName = "SAX2ParseOfFilter";
            key = new XmlKey(glob, "<key oid='Hello'><filter type='ContentLength'>8000</filter></key>");
            String oid = key.getKeyOid();
            startTime = System.currentTimeMillis();
            for (int ii=0; ii<count; ii++) {
               AccessFilterQos[] qosArr = key.getAccessFilterArr();
               key.filterVec = null; // force new parsing
            }
            elapsed = System.currentTimeMillis() - startTime;
            System.out.println(testName + ": For " + count + " runs " + elapsed + " millisec -> " + ((double)elapsed*1000.)/((double)count) + " mycrosec/inout");
            /* SAX2 Sun-Crimson
               SAX2ParseOfFilter: For 1000 runs 941 millisec -> 941.0 mycrosec/inout
               SAX2ParseOfFilter: For 1000 runs 946 millisec -> 946.0 mycrosec/inout
               SAX2ParseOfFilter: For 1000 runs 782 millisec -> 782.0 mycrosec/inout
               SAX2ParseOfFilter: For 1000 runs 577 millisec -> 577.0 mycrosec/inout
               SAX2ParseOfFilter: For 1000 runs 603 millisec -> 603.0 mycrosec/inout
            */
         }
      }
      catch (XmlBlasterException e) {
         System.out.println(e.toString());
      }
   }
}
