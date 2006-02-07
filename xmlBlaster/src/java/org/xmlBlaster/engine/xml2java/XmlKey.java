/*------------------------------------------------------------------------------
Name:      XmlKey.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.xml2java;

import org.jutils.text.StringHelper;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlToDom;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.I_MergeDomNode;
import org.xmlBlaster.util.key.KeyData;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.XmlNotPortable;
import org.xmlBlaster.engine.Global;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Attr;

import java.util.Enumeration;


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
 * @author xmlBlaster@marcelruff.info
 */
public final class XmlKey
{
   private String ME = "XmlKey";
   private Global glob;
   private LogChannel log;

   private XmlToDom xmlToDom = null;

   public final int XML_TYPE = 0;   // xml syntax
   public final int ASCII_TYPE = 1; // for trivial keys you can use a ASCII String (no XML)
   private int keyType = XML_TYPE;  // XmlKey uses XML syntax (default)

   protected KeyData keyData;

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
   //private com.fujitsu.xml.omquery.DomQueryMgr queryMgr = null;


   /**
    * Parses given xml string
    *
    * @param The original key in XML syntax, for example:<br>
    *        <pre><key oid="This is the unique attribute"></key></pre>
    */
   public XmlKey(Global glob, KeyData keyData) {
      this.glob = glob;
      this.log = glob.getLog("core");
      this.keyData = keyData;
   }

   /**
    * Parses given xml string
    *
    * @param The original key in XML syntax, for example:<br>
    *        <pre><key oid="This is the unique attribute"></key></pre>
    */
   public XmlKey(Global glob, String xmlKey_literal) throws XmlBlasterException {
      this.glob = glob;
      this.log = glob.getLog("core");
      
      xmlKey_literal = xmlKey_literal.trim();
      if (!xmlKey_literal.startsWith("<")) {
         keyType = ASCII_TYPE;  // eg "Airport/Runway1/WindVeloc3"
         this.keyData = new MsgKeyData(glob);
         this.keyData.setOid(xmlKey_literal);

         // Works well with ASCII, but is switched off for the moment
         // perhaps we should make it configurable through a property file !!!
         // Example: xmlKey_literal="Airport.*" as a regular expression

         log.warn(ME+".XML", "Invalid XmlKey syntax, only XML syntax beginning with \"<\" is supported: '" + xmlKey_literal + "'");
         Thread.dumpStack();
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + " Invalid XmlKey syntax, only XML syntax beginning with \"<\" is supported");
      }

      this.keyData = glob.getMsgKeyFactory().readObject(xmlKey_literal);
   }

   public KeyData getKeyData() {
      return this.keyData;
   }

   /**
    * @see KeyData#isDeadMessage()
    */
   public final boolean isDeadMessage() throws XmlBlasterException {
      return this.keyData.isDeadMessage();
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
      Thread.dumpStack();
      return this.keyData.toXml();
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
      return this.keyData.toXml();
   }

   /**
    * @deprecated use getOid()
    */
   public final String getUniqueKey() {
      return this.keyData.getOid();
   }

   /**
    * @deprecated use getOid()
    */
   public final String getKeyOid() {
      return this.keyData.getOid();
   }

   /**
    * Accessing the unique oid of <key oid="...">.
    * @return oid
    */
   public final String getOid() {
      return this.keyData.getOid();
   }

   public final String getContentMime() {
      return this.keyData.getContentMime();
   }

   public final String getContentMimeExtended() {
      return this.keyData.getContentMimeExtended();
   }

   public final String getDomain() {
      return this.keyData.getDomain();
   }

   public final boolean isDefaultDomain() {
      return this.keyData.isDefaultDomain();
   }

   /**
    * Messages starting with "_" are reserved for usage in plugins
    */
   public final boolean isPluginInternal() {
      return this.keyData.isPluginInternal();
   }

   /**
    * Messages starting with "__" are reserved for internal usage
    */
   public final boolean isInternal() {
      return this.keyData.isInternal();
   }

   public final boolean isAdministrative() {
      return this.keyData.isAdministrative();
   }

   /**
    * Fills the DOM tree, and assures that a valid <key oid=""> is used. 
    */
   public org.w3c.dom.Node getRootNode() throws XmlBlasterException {
      loadDomTree();
      return this.xmlToDom.getRootNode();
   }

   /**
    * Fills the DOM tree, and assures that a valid <pre>&lt;key oid=""></pre> is used.
    */
   private synchronized void loadDomTree() throws XmlBlasterException {
      if (this.xmlToDom != null)
         return;       // DOM tree is already loaded

      if (keyType == ASCII_TYPE)
         return;       // no XML -> no DOM

      this.xmlToDom = new XmlToDom(glob, this.keyData.toXml());
      org.w3c.dom.Node node = this.xmlToDom.getRootNode();

      // Finds the <key oid="..." queryType="..."> attributes, or inserts a unique oid if empty
      if (node == null) {
         log.error(ME+".Internal", "root node = null");
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME + " Internal", "root node = null");
      }

      String nodeName = node.getNodeName();

      if (!nodeName.equalsIgnoreCase("key")) {
         log.error(ME+".WrongRootNode", "The root node must be named \"key\"\n" + this.keyData.toXml());
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME+".WrongRootNode", "The root node must be named \"key\"\n" + this.keyData.toXml());
      }

      NamedNodeMap attributes = node.getAttributes();
      if (attributes != null && attributes.getLength() > 0) {
         int attributeCount = attributes.getLength();
         for (int i = 0; i < attributeCount; i++) {
            Attr attribute = (Attr)attributes.item(i);

            if (attribute.getNodeName().equalsIgnoreCase("oid")) {
               //String val = attribute.getNodeValue();
               attribute.setNodeValue(getOid());
            }
            else if (attribute.getNodeName().equalsIgnoreCase("contentMime")) {
               //String contentMime = attribute.getNodeValue();
               attribute.setNodeValue(getContentMime());
            }
            else if (attribute.getNodeName().equalsIgnoreCase("contentMimeExtended")) {
               // String contentMimeExtended = attribute.getNodeValue();
               attribute.setNodeValue(getContentMimeExtended());
            }
            else if (attribute.getNodeName().equalsIgnoreCase("domain")) {
               // String domain = attribute.getNodeValue();
               attribute.setNodeValue(getDomain());
            }
         }
      }

      log.info(ME, "DOM parsed the XmlKey " + getOid());
   }

   /**
    * Should be called by publish() to merge the local XmlKey DOM into the big xmlBlaster DOM tree
    */
   public final void mergeRootNode(I_MergeDomNode merger) throws XmlBlasterException {
      loadDomTree();
      this.xmlToDom.mergeRootNode(merger);
   }

   /**
    * Allows to check if this xmlKey matches the given query. 
    * @param queryKey An XmlKey object containing a query (XPATH, EXACT or DOMAIN)
    * @return true if this message key matches the query
    */
   public final boolean match(QueryKeyData queryKey) throws XmlBlasterException {
      if (log.TRACE) log.trace(ME, "Trying query=" + queryKey.toXml() + "\non key=" + literal());
      if (queryKey.isDomain()) {
         if (queryKey.getDomain() == null) {
            log.warn(ME, "Your query is of type DOMAIN but you have not specified a domain: " + queryKey.toXml());
            throw new XmlBlasterException(glob, ErrorCode.USER_QUERY_INVALID, ME, "Your query is of type DOMAIN but you have not specified a domain: " + queryKey.toXml());
         }
         if (queryKey.getDomain().equals("*") || queryKey.getDomain().equals(getDomain())) {
            if (log.TRACE) log.trace(ME, "Message oid='" + getOid() + "' matched for domain='" + getDomain() + "'.");
            return true;
         }
      }
      else if (queryKey.isExact()) {
         if (queryKey.getOid().equals(getOid())) {
            if (log.TRACE) log.trace(ME, "Message oid='" + getOid() + "' matched.");
            return true;
         }
      }
      else if (queryKey.isXPath()) {
         if (match(queryKey.getQueryString())) {
            if (log.TRACE) log.trace(ME, "Message oid='" + getOid() + "' matched with XPath query '" + queryKey.getQueryString() + "'");
            return true;
         }
      }
      else {
         log.error(ME, "Don't know queryType '" + queryKey.getQueryType() + "' for message oid='" + getOid() + "'. I'll return false for match.");
         return false;
      }
      
      if (log.TRACE) log.trace(ME, "Message oid='" + getOid() + "' does not match with query");
      return false;
   }

   /**
    * We need this to allow checking if an existing XPath subscription matches this new message type. 
    * <p/>
    * Note that we manipulate the XML key and add a surrounding root node &lt;xmlBlaster>
    * @param xpath The XPath query, check if it matches to this xmlKey
    * @return true if this message meta data matches the XPath query
    */
   public final boolean match(String xpath) throws XmlBlasterException {
      String xmlKey_literal = this.keyData.toXml();
      if (this.xmlKeyDoc == null) {
         try {
            if (log.TRACE) log.trace(ME, "Creating tiny DOM tree and a query manager ...");
            // Add the <xmlBlaster> root element ...
            String tmp = StringHelper.replaceFirst(xmlKey_literal, "<key", "<xmlBlaster><key") + "</xmlBlaster>";
            this.xmlKeyDoc = XmlToDom.parseToDomTree(glob, tmp);
         } catch (Exception e) {
            String text = "Problems building tiny key DOM tree\n" + xmlKey_literal + "\n for XPath subscriptions check: " + e.getMessage();
            log.warn(ME + ".MergeNodeError", text);
            throw new XmlBlasterException(glob, ErrorCode.USER_QUERY_INVALID, ME, text, e);
         }
      }
      try {
         Enumeration nodeIter = XmlNotPortable.getNodeSetFromXPath(xpath, this.xmlKeyDoc);
         if (nodeIter != null && nodeIter.hasMoreElements()) {
            log.info(ME, "XPath subscription '" + xpath + "' matches message '" + getKeyOid() + "'");
            return true;
         }
      }
      catch (Exception e) {
         log.warn(ME + ".XPathError", "XPath query '" + xpath + "' on tiny key DOM tree" + xmlKey_literal + "failed: " + e.getMessage());
         throw new XmlBlasterException(glob, ErrorCode.USER_QUERY_INVALID, ME, "XPath query '" + xpath + "' on tiny key DOM tree" + xmlKey_literal + "failed", e);
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
      this.xmlKeyDoc = null;
   }
}
