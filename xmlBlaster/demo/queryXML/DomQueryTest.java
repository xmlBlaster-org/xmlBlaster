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
Invoke:    java DomQueryTest AgentBig.xml xmlBlaster/key/AGENT[@id=\"192.168.124.10\"] xmlBlaster/key/AGENT/DRIVER[@id=\"FileProof\"] xmlBlaster/key[@oid=\"2\"]
Version:   $Id: DomQueryTest.java,v 1.1 1999/11/16 22:05:11 ruff Exp $
------------------------------------------------------------------------------*/

import com.jclark.xsl.om.*;

import java.io.File;
import java.io.IOException;
import org.xmlBlaster.util.*;

import java.util.Properties;
import java.util.Enumeration;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.jclark.xsl.dom.XMLProcessorImpl;
import com.jclark.xsl.dom.SunXMLProcessorImpl;

import org.w3c.dom.Document;

import com.fujitsu.xml.omquery.DomQueryMgr;
import com.fujitsu.xml.omquery.FujitsuXMLProcessorImpl;

class DomQueryTest
{
   public static void main(String argv[])
   {
      final String ME = "DomQueryTester";
      boolean dumpIt = false; // Set to true if you want to see the query results

      if (argv.length < 2)
      Log.panic(ME, "Usage:\n\n   java DomQueryTest <XML-file> <Query-String>\n\nExample:\n   java DomQueryTest Agent.xml xmlBlaster/key/AGENT[@id=\\\"192.168.124.10\\\"]\n");

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
         Log.info(ME, "Read file" + inputTime.nice());

         StopWatch xmlprocTime = new StopWatch();
         XMLProcessorImpl xmlproc = new SunXMLProcessorImpl();          // [ 75 millis ] [ 60 millis ]
         Log.info(ME, "Instantiate SunXMLProcessorImpl" + xmlprocTime.nice());

         {
            StopWatch docTime = new StopWatch();
            doc = xmlproc.load(input);
            Log.info(ME, "Create DOM - Document" + docTime.nice());     // [ 1 sec 608 millis ] [ 3 sec 69 millis ]

            StopWatch mgrTime = new StopWatch();
            query_mgr = new DomQueryMgr(doc);             
            Log.info(ME, "Instantiate DomQueryMgr" + mgrTime.nice());   // [ 240 millis ] [ 204 millis ]

            if (argv.length > 1) {
               StopWatch queryTime = new StopWatch();
               iter = query_mgr.getNodesByXPath(doc, argv[1]);          // [ 2 sec 630 millis ] [ 2 sec 516 millis ]
               Log.info(ME, "Query time" + queryTime.nice());

               num_nodes = getNumNodes(iter, dumpIt);
               System.out.println(num_nodes + " nodes matches for XPath " + "\"" + argv[1] + "\"");
            }

            if (argv.length > 2) {
               StopWatch queryTime2 = new StopWatch();
               iter = query_mgr.getNodesByXPath(doc, argv[2]);          // [ 2 millis ] [ 2 millis ]
               Log.info(ME, "Query time" + queryTime2.nice());

               num_nodes = getNumNodes(iter, dumpIt);
               System.out.println(num_nodes + " nodes matches for XPath " + "\"" + argv[2] + "\"");
            }

            if (argv.length > 3) {
               StopWatch queryTime2 = new StopWatch();
               iter = query_mgr.getNodesByXPath(doc, argv[3]);          // [ 1 millis ] [ 1 millis ]
               Log.info(ME, "Query time" + queryTime2.nice());

               num_nodes = getNumNodes(iter, dumpIt);
               System.out.println(num_nodes + " nodes matches for XPath " + "\"" + argv[3] + "\"");
            }
         }

         {
            StopWatch docTime = new StopWatch();
            doc = xmlproc.load(input);
            Log.info(ME, "Create DOM - Document" + docTime.nice());     // [ 28 millis ] [ 1 sec 487 millis ]

            StopWatch mgrTime = new StopWatch();
            query_mgr = new DomQueryMgr(doc);             
            Log.info(ME, "Instantiate DomQueryMgr" + mgrTime.nice());   // [ 1 millis ] [ 1 millis ]

            if (argv.length > 1) {
               StopWatch queryTime = new StopWatch();
               iter = query_mgr.getNodesByXPath(doc, argv[1]);          // [ 1 millis ] [ 1 millis ]
               Log.info(ME, "Query time" + queryTime.nice());

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

   static private int getNumNodes(Enumeration nodeIter, boolean dumpIt) throws XSLException
   {
      int n = 0;

      while (nodeIter.hasMoreElements())
      {
         n++;
         Object obj = nodeIter.nextElement();
         com.sun.xml.tree.ElementNode node = (com.sun.xml.tree.ElementNode)obj;
         if (dumpIt)
            System.out.println(node.toString());
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

   private static String createURL(String path)
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
}
