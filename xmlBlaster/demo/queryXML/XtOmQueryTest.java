/*------------------------------------------------------------------------------
Name:      XtOmQueryTest.java
Project:   xmlBlaster.org
Copyright: many people
Comment:   Syntax for Query:
              XPath: http://www.w3.org/TR/xpath

           XT implementation:
              http://www.jclark.com/xml/xt.html

           XPath interface (contains everything):
              http://www.246.ne.jp/~kamiya/pub/omquery.zip

Compile:   jikes *.java  (put local directory into CLASSPATH)
Invoke:    java XtOmQueryTest Agent.xml xmlBlaster/key/AGENT[@id=\"192.168.124.10\"] xmlBlaster/key/AGENT/DRIVER[@id=\"FileProof\"] xmlBlaster/key[@oid=\"2\"]
Version:   $Id$
------------------------------------------------------------------------------*/

import com.jclark.xsl.om.*;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.jutils.time.StopWatch;

import java.io.File;
import java.io.IOException;

import java.util.Enumeration;
import java.util.Properties;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.fujitsu.xml.omquery.XtOmQueryMgr;

class XtOmQueryTest
{
   static private final String parser_name = "org.apache.crimson.parser.Parser2";
   static private final String ME = "XtOmQueryTest";

   public XtOmQueryTest(String argv[])
   {
      if (argv.length < 2) {
         System.out.println("Usage:\n\n   java XtOmQueryTest <XML-file> <Query-String>\n\nExample:\n   java XtOmQueryTest Agent.xml xmlBlaster/key/AGENT[@id=\\\"192.168.124.10\\\"]\n");
      }

      boolean dumpIt = false;
      if (argv.length == 2) dumpIt = true;

      Enumeration iter;
      int num_nodes;
      InputSource input;

      Properties prop = System.getProperties();
      prop.put("org.xml.sax.parser", parser_name);
      System.setProperties(prop);

      // Query: xmlBlaster/key/AGENT[@id=\"192.168.124.10\"]  xmlBlaster/key/AGENT/DRIVER[@id=\"FileProof\"]  xmlBlaster/key[@oid=\"2\"]
      // Time 1: For 7 <key> blocks on 266 MHz AMD Linux, JDK 1.2
      // Time 2: For 600 <key> blocks on 266 MHz AMD Linux, JDK 1.2

      try
      {
         StopWatch inputTime = new StopWatch();
         input = new InputSource(createURL(argv[0]));           // [ 29 millis ] [ 28 millis ]
         System.out.println("Read file" + inputTime.nice());

         StopWatch mgrTime = new StopWatch();
         XtOmQueryMgr query_mgr = new XtOmQueryMgr();           // [ 588 millis ] [ 612 millis ]
         System.out.println("Instantiate DomQueryMgr" + mgrTime.nice());

         {
            StopWatch loadTime = new StopWatch();
            com.jclark.xsl.om.Node node = query_mgr.load(input);// [ 738 millis ] [ 1 sec 987 millis ]
            System.out.println("Load nodes" + loadTime.nice());

            if (argv.length > 1) {
               StopWatch queryTime = new StopWatch();
               iter = query_mgr.getNodesByXPath(node, argv[1]); // [ 2 sec 422 millis ] [ 2 sec 577 millis ]
               System.out.println("Query time" + queryTime.nice());

               num_nodes = getNumNodes(iter, dumpIt);
               System.out.println(num_nodes + " nodes matches for XPath " + "\"" + argv[1] + "\"");
            }

            if (dumpIt)
               System.exit(1);

            if (argv.length > 2) {
               StopWatch queryTime2 = new StopWatch();
               iter = query_mgr.getNodesByXPath(node, argv[2]); // [ 3 millis ] [ 1 millis ]
               System.out.println("Query time" + queryTime2.nice());

               num_nodes = getNumNodes(iter, dumpIt);
               System.out.println(num_nodes + " nodes matches for XPath " + "\"" + argv[2] + "\"");
            }

            if (argv.length > 3) {
               StopWatch queryTime2 = new StopWatch();
               iter = query_mgr.getNodesByXPath(node, argv[3]); // [ 1 millis ] [ 0 millis ]
               System.out.println("Query time" + queryTime2.nice());

               num_nodes = getNumNodes(iter, dumpIt);
               System.out.println(num_nodes + " nodes matches for XPath " + "\"" + argv[3] + "\"");
            }
         }

         {
            StopWatch loadTime = new StopWatch();
            Node node = query_mgr.load(input);                  // [ 22 millis ] [ 1 sec 211 millis ]
            System.out.println("Load nodes" + loadTime.nice());

            StopWatch queryTime = new StopWatch();
            iter = query_mgr.getNodesByXPath(node, argv[1]);    // [ 0 millis ] [ 1 millis ]
            System.out.println("Query time" + queryTime.nice());

            num_nodes = getNumNodes(iter, dumpIt);
            System.out.println(num_nodes + " nodes matches for XPath " + "\"" + argv[1] + "\"");
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

      //com.jclark.xsl.om.Name key_id = new com.jclark.xsl.om.Name("oid");

      while (nodeIter.hasMoreElements())
      {
         n++;
         Object obj = nodeIter.nextElement();
         Node node = (Node)obj;
         if (dumpIt) {
            //NameTableImpl nti = (NameTableImpl) node.getCreator();

            System.out.println("Processing node " + node.getName() + ": " + node.getData());
            System.out.println("Processing node " + node.toString());

            SafeNodeIterator siter = node.getAttributes();

            Object aobj = siter.next();
            while (aobj != null) {
               System.out.println("Attibutes:  " + aobj.toString());

               aobj = siter.next();
            }

            com.jclark.xsl.om.Node parent = node.getParent();

            if (parent == null)
               System.out.println("No parent");
            else {
               System.out.println("Got parent");
            }
         }
      }

      return n;
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
      new XtOmQueryTest(argv);
   }
}
