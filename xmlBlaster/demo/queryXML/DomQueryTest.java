/*------------------------------------------------------------------------------
Name:      DomQueryTest.java
Project:   xmlBlaster.org
Copyright: many people
Comment:   Syntax for Query:
              XPath: http://www.w3.org/TR/xpath

           XT implementation:
              http://www.jclark.com/xml/xt.html

           XPath interface (contains everything):
              http://www.246.ne.jp/~kamiya/pub/omquery.zip

Compile:   jikes *.java  (put local directory into CLASSPATH)
Invoke:    With key oid:
             java DomQueryTest AgentBig.xml xmlBlaster/key/AGENT[@id=\"192.168.124.10\"] xmlBlaster/key/AGENT/DRIVER[@id=\"FileProof\"] xmlBlaster/key[@oid=\"2\"]
           Normal XPath checks:
             java -DtestAgentNavigation=false DomQueryTest Qos.xml /qos/expiration
Version:   $Id$
------------------------------------------------------------------------------*/

import com.jclark.xsl.om.*;

import java.io.File;
import java.io.IOException;

import org.xmlBlaster.util.StopWatch;

import java.util.Properties;
import java.util.Enumeration;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.jclark.xsl.dom.XMLProcessorImpl;
import com.jclark.xsl.dom.SunXMLProcessorImpl;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Attr;

import com.fujitsu.xml.omquery.DomQueryMgr;

class DomQueryTest
{
   final String ME = "DomQueryTester";
   boolean testAgentNavigation = true;  // only to be true with Agent.xml or AgentBig.xml

   public DomQueryTest(String argv[])
   {
      Object tmp = System.getProperty("testAgentNavigation");
      if (tmp != null) {
         testAgentNavigation = (Boolean.valueOf((String)tmp)).booleanValue();
      }
      if (argv.length < 2) {
         System.out.println("Usage:\n\n   java DomQueryTest <XML-file> <Query-String>\n\nExample:\n   java DomQueryTest Agent.xml xmlBlaster/key/AGENT[@id=\\\"192.168.124.10\\\"]\n");
         System.exit(1);
      }

      boolean dumpIt = false;
      if (argv.length == 2) dumpIt = true;

      try
      {
         Document doc;
         DomQueryMgr query_mgr;
         Enumeration iter;
         int num_nodes;

         // Query: xmlBlaster/key/AGENT[@id=\"192.168.124.10\"]  xmlBlaster/key/AGENT/DRIVER[@id=\"FileProof\"]  xmlBlaster/key[@oid=\"2\"]
         // Time 1: For 7 <key> blocks on 266 MHz AMD Linux, JDK 1.2
         // Time 2: For 600 <key> blocks on 266 MHz AMD Linux, JDK 1.2

         StopWatch inputTime = new StopWatch();
         InputSource input = new InputSource(createURL(argv[0]));       // [ 20 millis ]
         System.out.println("Read file" + inputTime.nice());

         StopWatch xmlprocTime = new StopWatch();
         XMLProcessorImpl xmlproc = new SunXMLProcessorImpl();          // [ 75 millis ] [ 60 millis ]
         System.out.println("Instantiate SunXMLProcessorImpl" + xmlprocTime.nice());

         {
            StopWatch docTime = new StopWatch();
            doc = xmlproc.load(input);
            System.out.println("Create DOM - Document" + docTime.nice());     // [ 1 sec 608 millis ] [ 3 sec 69 millis ]

            StopWatch mgrTime = new StopWatch();
            query_mgr = new DomQueryMgr(doc);
            System.out.println("Instantiate DomQueryMgr" + mgrTime.nice());   // [ 240 millis ] [ 204 millis ]

            if (argv.length > 1) {
               StopWatch queryTime = new StopWatch();
               iter = query_mgr.getNodesByXPath(doc, argv[1]);          // [ 2 sec 630 millis ] [ 2 sec 516 millis ]
               System.out.println("Query time" + queryTime.nice());

               num_nodes = getNumNodes(iter, dumpIt);
               System.out.println(num_nodes + " nodes matches for XPath " + "\"" + argv[1] + "\"");
            }

            if (dumpIt) {
               StopWatch queryTime = new StopWatch();
               iter = query_mgr.getNodesByXPath(doc, argv[1]);
               System.out.println("Query a second time encreases performance to" + queryTime.nice());
               System.exit(0);
            }

            if (argv.length > 2) {
               StopWatch queryTime2 = new StopWatch();
               iter = query_mgr.getNodesByXPath(doc, argv[2]);          // [ 2 millis ] [ 2 millis ]
               System.out.println("Query time" + queryTime2.nice());

               num_nodes = getNumNodes(iter, dumpIt);
               System.out.println(num_nodes + " nodes matches for XPath " + "\"" + argv[2] + "\"");
            }

            if (argv.length > 3) {
               StopWatch queryTime2 = new StopWatch();
               iter = query_mgr.getNodesByXPath(doc, argv[3]);          // [ 1 millis ] [ 1 millis ]
               System.out.println("Query time" + queryTime2.nice());

               num_nodes = getNumNodes(iter, dumpIt);
               System.out.println(num_nodes + " nodes matches for XPath " + "\"" + argv[3] + "\"");
            }
         }

         {
            StopWatch docTime = new StopWatch();
            doc = xmlproc.load(input);
            System.out.println("Create DOM - Document" + docTime.nice());     // [ 28 millis ] [ 1 sec 487 millis ]

            StopWatch mgrTime = new StopWatch();
            query_mgr = new DomQueryMgr(doc);
            System.out.println("Instantiate DomQueryMgr" + mgrTime.nice());   // [ 1 millis ] [ 1 millis ]

            if (argv.length > 1) {
               StopWatch queryTime = new StopWatch();
               iter = query_mgr.getNodesByXPath(doc, argv[1]);          // [ 1 millis ] [ 1 millis ]
               System.out.println("Query time" + queryTime.nice());

               num_nodes = getNumNodes(iter, dumpIt);
               System.out.println(num_nodes + " nodes matches for XPath " + "\"" + argv[1] + "\"");
            }
         }
      }
      catch (IOException e)
      {
         System.err.println(e.getMessage());
         e.printStackTrace();
      }
      catch (SAXException e)
      {
         System.err.println(e.getMessage());
         e.printStackTrace();
      }
      catch (XSLException e)
      {
         System.err.println(e.getMessage());
         e.printStackTrace();
      }
   }

   private int getNumNodes(Enumeration nodeIter, boolean dumpIt) throws XSLException
   {
      int n = 0;

      while (nodeIter.hasMoreElements())
      {
         n++;
         Object obj = nodeIter.nextElement();
         if (dumpIt) {
            if (obj instanceof String) {
               String val = (String)obj;
               System.out.println("TagValue="+val);
            }
            /* Does not compile with JDK 1.3, need 1.4:
            else if (obj instanceof org.apache.crimson.tree.AttributeNode) {
               org.apache.crimson.tree.AttributeNode attr = (org.apache.crimson.tree.AttributeNode)obj;
               System.out.println("AttibuteValue="+attr.getValue());
            }
            */
            else {
               System.out.println(obj.toString());
            }

            if (testAgentNavigation) {
               try {
                  Element node = (Element)obj;
                  System.out.println("Found key oid=\"" + getKeyOID(node) + "\"\n");
               } catch (Exception e) {
                  System.out.println("ERROR: Found no xmlBlaster key oid. " + e.toString());
               }
            }
         }

         /*
         System.out.println("Processing nodeName=" + node.getNodeName() + ", " +
                              "localName=" + node.getLocalName() + ", " +
                              "tagName=" + node.getTagName() + ", " +
                              "" + node.toString()
                              );
            */
      }

      return n;
   }

   private String getKeyOID(org.w3c.dom.Node node) throws Exception
   {
      if (node == null)
         throw new Exception("no parent node found");

      String nodeName = node.getNodeName();

      if (nodeName.equals("xmlBlaster"))       // ERROR: the root node, must be specialy handled
         throw new Exception("xmlBlaster node not allowed");

      if (!nodeName.equals("key")) {
         return getKeyOID(node.getParentNode());  // w3c: getParentNode() sun: getParentImpl()
      }

      /* !!! Element
      org.w3c.dom.Attr keyOIDAttr = node.getAttributeNode("oid");
      if (keyOIDAttr != null)
         return keyOIDAttr.getValue();
      */

      // w3c conforming code:
      NamedNodeMap attributes = node.getAttributes();
      if (attributes != null && attributes.getLength() > 0) {
         int attributeCount = attributes.getLength();
         for (int i = 0; i < attributeCount; i++) {
            Attr attribute = (Attr)attributes.item(i);
            if (attribute.getNodeName().equals("oid")) {
               String val = attribute.getNodeValue();
               return val;
            }
         }
      }

      throw new Exception("Internal getKeyOID() error");
   }

   private String createURL(String path)
   {
      File f = new File(path);
      String uri = f.getAbsolutePath();

      char sep = System.getProperty("file.separator").charAt(0);
      uri = uri.replace(sep, '/');
      if (uri.charAt(0) != '/')
      uri = '/' + uri;

      uri = "file://" + uri;

      return uri;
   }

   public static void main(String argv[])
   {
      new DomQueryTest(argv);
   }
}
