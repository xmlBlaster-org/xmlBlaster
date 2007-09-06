/*------------------------------------------------------------------------------
Name:      XmlMethodsTest.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.test.classtest;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import java.util.logging.Logger;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlNotPortable;
import org.xmlBlaster.util.def.ErrorCode;

/**
 * Test ClientProperty. 
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.XmlMethodsTest
 * @see org.xmlBlaster.client.script.XmlScriptInterpreter
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.qos.xmlScript.html">The client.xmlScript requirement</a>
 */
public class XmlMethodsTest extends XMLTestCase {
   protected final static String ME = "XmlMethodsTest";
   protected Global glob;
   private static Logger log = Logger.getLogger(XmlMethodsTest.class.getName());

   public XmlMethodsTest(String name) {
      this(new Global(), name);
   }

   public XmlMethodsTest(Global global, String name) {
      super(name);
      XMLUnit.setIgnoreWhitespace(true);
   }

   protected void setUp() {
      this.glob = Global.instance();

   }

   protected void testWriteNode() throws Exception {
      String txt = "<xmlBlaster>\n" +
                   "  <connect>\n" + 
                   "      <qos>\n" +
                   "         <securityService type='htpasswd' version='1.0'>\n" +
                   "           <![CDATA[\n" +
                   "           <user>michele</user>\n" +
                   "           <passwd>secret</passwd>\n" +
                   "           ]]>" +
                   "         </securityService>\n" +
                   "         <session name='client/joe/3' timeout='3600000' maxSessions='10'" +
                   "                     clearSessions='false' reconnectSameClientOnly='false'/>\n" +
                   "         <ptp>true</ptp>\n" +
                   "         <duplicateUpdates>false</duplicateUpdates>\n" +
                   "         <queue relating='callback' maxEntries='1000' maxBytes='4000000'>\n" +
                   "            <callback type='IOR' sessionId='4e56890ghdFzj0' pingInterval='10000'\n" +
                   "                retries='-1' delay='10000' oneway='false' dispatchPlugin='undef'>\n" +
                   "               IOR:10000010033200000099000010....\n" +
                   "               <burstMode collectTime='400' />\n" +
                   "               <compress type='gzip' minSize='3000'/>\n" +
                   "               <ptp>true</ptp>\n" +
                   "            </callback>\n" +
                   "         </queue>\n" + 
                   "         <!-- a client specific property: here it could be the bean to invoke on updates -->\n" +
                   "         <clientProperty name='onMessageDefault'>beanName</clientProperty>\n" +
                   "      </qos>\n" +
                   "   </connect>\n" + 
                   "</xmlBlaster>\n";

      // assertXpathExists("/qos/securityService[@type='htpasswd']", qos);
      
      StringReader reader = new StringReader(txt);
      InputSource input = new InputSource(reader);
      //input.setEncoding("UTF-8");
      //input.setEncoding("ISO-8859-2");
      //input.setSystemId("9999999999");

      try {
         DocumentBuilderFactory dbf = glob.getDocumentBuilderFactory();
         DocumentBuilder db = dbf.newDocumentBuilder();
         Document xmlDoc = db.parse(input);
         
         ByteArrayOutputStream out = XmlNotPortable.writeNode(xmlDoc.getDocumentElement());
         String response = new String(out.toByteArray());
         log.info(response);
         reader = new StringReader(response);
         input = new InputSource(reader);
         Document xmlDoc1 = db.parse(input);
         this.assertXMLEqual("", xmlDoc, xmlDoc1);
      } 
      catch (ParserConfigurationException e) {
         log.severe("Problems when building DOM parser: " + e.toString() + "\n" + txt);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Problems when building DOM tree from your XML-ASCII string\n" + txt, e);
      } 
      catch (java.io.IOException e) {
         log.severe("Problems when building DOM tree from your XML-ASCII string: " + e.toString() + "\n" + txt);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Problems when building DOM tree from your XML-ASCII string:\n" + txt, e);
      } 
      catch (org.xml.sax.SAXException e) {
         log.warning("Problems when building DOM tree from your XML-ASCII string: " + e.toString() + "\n" + txt);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Problems when building DOM tree from your XML-ASCII string:\n" + txt, e);
      }

   }


   /**
     * <pre>
     *  java org.xmlBlaster.test.classtest.XmlMethodsTest
     * </pre>
     */
    public static void main(String args[])
    {
       try {
          Global global = new Global(args);
          XmlMethodsTest test = new XmlMethodsTest(global, "XmlMethodsTest");
          test.setUp();
          test.testWriteNode();
          test.tearDown();
       }
       catch(Throwable e) {
          e.printStackTrace();
          fail(e.toString());
       }
    }
}
