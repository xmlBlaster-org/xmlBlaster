/*------------------------------------------------------------------------------
Name:      XmlKey.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey, knows how to parse it with SAX
Version:   $Id: XmlKey.java,v 1.14 2002/04/26 21:31:52 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.xml2java;

import org.xmlBlaster.util.Log;
import org.jutils.text.StringHelper;
import org.xmlBlaster.util.XmlToDom;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;

import java.util.*;

/**
 * This class encapsulates the Message meta data and unique identifier.
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
 *     &lt;key oid='4711' queryType='EXACT'>
 *     &lt;/key>
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
 * More examples you find in xmlBlaster/src/dtd/XmlKey.xml
 * <p />
 *
 * @see org.xmlBlaster.util.XmlKeyBase
 * @see <a href="http://www.w3.org/TR/xpath">The W3C XPath specification</a>
 */
public class XmlKey extends org.xmlBlaster.util.XmlKeyBase
{
   private String ME = "XmlKey";

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
    * Construct a handler object for this xml message key.
    * @param xmlKey_literal The xml based message meta data
    */
   public XmlKey(Global glob, String xmlKey_literal) throws XmlBlasterException
   {
      super(glob, xmlKey_literal);
      this.xmlKey_literal=xmlKey_literal;
   }


   /**
    * Construct a handler object for this xml message key.
    * @param xmlKey_literal The xml based message meta data
    * @param isPublish Invoked from a client publish()
    */
   public XmlKey(Global glob, String xmlKey_literal, boolean isPublish) throws XmlBlasterException
   {
      super(glob, xmlKey_literal, isPublish);
      this.xmlKey_literal=xmlKey_literal;
   }

   /*
    * Try to find out if this is an internal message only
   public boolean isLocalMessage() throws XmlBlasterException
   {
      if (getKeyOid().startsWith(Constants.INTERNAL_OID_PRAEFIX) &&
          getKeyOid().indexOf("["+glob.getId()+"]") >= 0)
         return true;

      if (isDefaultDomain())
         return true;

      return false;
   }
    */

   /**
    * We need this to allow checking if an existing XPath subscription matches this new message type.
    * @param xpath The XPath query, check if it matches to this xmlKey
    * @return true if this message meta data matches the XPath query
    */
   public boolean match(String xpath) throws XmlBlasterException
   {
      if (xmlKeyDoc == null) {
         try {
            if (Log.TRACE) Log.trace(ME, "Creating tiny DOM tree and a query manager ...");
            // Add the <xmlBlaster> root element ...
            String tmp = StringHelper.replaceFirst(xmlKey_literal, "<key", "<xmlBlaster><key") + "</xmlBlaster>";
            XmlToDom tinyDomHandle = new XmlToDom(tmp);
            xmlKeyDoc = tinyDomHandle.getXmlDoc();
            queryMgr = new com.fujitsu.xml.omquery.DomQueryMgr(xmlKeyDoc);
         } catch (Exception e) {
            String text = "Problems building tiny key DOM tree\n" + xmlKey_literal + "\n for XPath subscriptions check: " + e.toString();
            Log.error(ME + ".MergeNodeError", text);
            e.printStackTrace();
            throw new XmlBlasterException("MergeNodeError", text);
         }
      }
      try {
         Enumeration nodeIter = queryMgr.getNodesByXPath(xmlKeyDoc, xpath);
         if (nodeIter != null && nodeIter.hasMoreElements()) {
            Log.info(ME, "XPath subscription '" + xpath + "' matches message '" + getKeyOid() + "'");
            return true;
         }
      }
      catch (Exception e) {
         String text = "XPath query on tiny key DOM tree\n" + xmlKey_literal + "\nfailed: " + e.toString();
         Log.error(ME + ".XPathError", text);
         e.printStackTrace();
         throw new XmlBlasterException("XPathError", text);
      }
      if (Log.TRACE) Log.trace(ME, "XPath subscription '" + xpath + "' does NOT match message '" + getKeyOid() + "'");
      return false;
   }


   /**
    * After the existing XPath subscriptions have queried this message
    * we should release the DOM tree.
    */
   public void cleanupMatch()
   {
      if (Log.TRACE) Log.trace(ME, "Releasing tiny DOM tree");
      queryMgr = null;
      xmlKeyDoc = null;
   }

   public String toString() {
      return xmlKey_literal;
   }

   public String toXml() {
      return xmlKey_literal;
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
               //System.out.println(key.toXml()); // oid="192.168.1.2-7609-1015227424082-660"
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

      }
      catch (XmlBlasterException e) {
         System.out.println(e.toString());
      }
   }
}
