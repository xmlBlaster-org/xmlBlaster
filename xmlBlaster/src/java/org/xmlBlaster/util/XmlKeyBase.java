/*------------------------------------------------------------------------------
Name:      XmlKeyBase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey, knows how to parse it with SAX
Version:   $Id: XmlKeyBase.java,v 1.3 1999/11/17 13:51:25 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.engine.RequestBroker;

import org.xml.sax.InputSource;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Attr;


/**
 * XmlKeyBase. 
 * <p>
 * All XmlKey's have the same XML minimal structure:<p>
 * <pre>
 *    <key oid="12345">
 *       <!-- application specific tags -->
 *    </key>
 * </pre>
 *
 * where oid is a unique key
 *
 */
public class XmlKeyBase
{
   private String ME = "XmlKeyBase";

   private static long uniqueCounter = 1L;

   public final int XML_TYPE = 0;   // xml syntax
   public final int ASCII_TYPE = 1; // for trivial keys you can use a ASCII String (no XML)
   private int keyType = XML_TYPE;  // XmlKey uses XML syntax (default)

   protected String xmlKey_literal;

   protected org.w3c.dom.Document doc = null;  // the parsed xmlKey_literal DOM
   protected org.w3c.dom.Node rootNode = null; // this is always the <key ...>
   protected String keyOid = null;             // value from attribute <key oid="...">



   /**
    * Parses given xml string
    *
    * @param The original key in XML syntax, for example:<br>
    *        <pre><key oid="This is the unique attribute"></key></pre>
    */
   public XmlKeyBase(String xmlKey_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.trace(ME, "Creating new XmlKey");

      this.xmlKey_literal = xmlKey_literal.trim();

      if (!this.xmlKey_literal.startsWith("<")) {
         keyType = ASCII_TYPE;  // eg "Airport/Runway1/WindVeloc3"
         keyOid = xmlKey_literal;
         
         // Works well with ASCII, but is switched of for the moment
         // perhaps we should make it configureable thru a porperty file !!!

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
    * Synonym for getKeyOid()
    *
    * @return oid
    */
   public String getUniqueKey() throws XmlBlasterException
   {
      return getKeyOid();
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


   /**
    * Fills the DOM tree, and assures that a valid <key oid="..."> is used. 
    * <p>
    * The keyOid will be set properly if no error occures
    * The rootNode will be set properly if no error occures
    */
   private void loadDomTree() throws XmlBlasterException
   {
      if (doc != null)
         return;

      if (keyType == ASCII_TYPE)
         return;

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

      keyOid = getOrAddKeyOid(tmpRootNode);

      rootNode = tmpRootNode;  // everything successfull, assign the rootNode
   }


   /**
    * Finds the <key oid="..."> attribute, or inserts a unique one if empty
    */
   private String getOrAddKeyOid(org.w3c.dom.Node node) throws XmlBlasterException
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

      NamedNodeMap attributes = node.getAttributes();
      if (attributes != null && attributes.getLength() > 0) {
         int attributeCount = attributes.getLength();
         for (int i = 0; i < attributeCount; i++) {
            Attr attribute = (Attr)attributes.item(i);
            if (attribute.getNodeName().equals("oid")) {
               String val = attribute.getNodeValue();
               if (val.length() < 1) {
                  val = generateKeyOid();
                  attribute.setNodeValue(val);
                  Log.trace(ME, "Generated key oid=\"" + val + "\"");
               }
               else {
                  Log.trace(ME, "Found key oid=\"" + val + "\"");
               }
               return val;
            }
         }
      }

      Log.error(ME+".WrongRootNode", "Missing \"oid\" attribute in \"key\" tag");
      throw new XmlBlasterException(ME+".WrongRootNode", "Missing \"oid\" attribute in \"key\" tag");
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
}
