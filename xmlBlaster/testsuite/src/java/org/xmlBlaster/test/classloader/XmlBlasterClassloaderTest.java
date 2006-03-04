package org.xmlBlaster.test.classloader;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.*;
import java.net.URL;
import java.net.URLClassLoader;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.MsgUnit;


import junit.framework.*;
import java.net.*;

/**
 * Test SNMP (simple network management protocol) to insert data.
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.snmp.InsertTest
 *
 * @see org.xmlBlaster.engine.admin.extern.snmp.NodeEntryImpl
 */
public class XmlBlasterClassloaderTest extends TestCase {
   protected Global glob;
   private static Logger log = Logger.getLogger(XmlBlasterClassloaderTest.class.getName());
   private MsgUnit msgUnit;     // a message to play with

   public XmlBlasterClassloaderTest(String name) {
      super(name);
   }

   protected void setUp() {
      this.glob = Global.instance();

      URLClassLoader cl = (URLClassLoader)this.getClass().getClassLoader();
      URL[] urls = cl.getURLs();
      String path = "";
      for( int i = 0; i < urls.length; i++ ) {
         String file = urls[i].getFile();
         // TODO: parser.jar is not used anymore, remove code below
         if( file.endsWith("parser.jar") ) {
            int pos = file.indexOf("parser.jar");
            path = urls[i].getProtocol()+"://"+file.substring(0,pos);
            break;
         }
      }

      // Add a xerces.jar at the beginning, so that CLASSPATH contains another XML-Parser in front.
      try {
         urls = new URL[2];
         urls[0] = new URL(path+"ant/xerces.jar");
         urls[1] = new URL(path+"xmlBlaster.jar");
      }
      catch (MalformedURLException ex) {
         log.severe("error. >>>"+ex.toString());
      }

      cl = cl.newInstance(urls);
      try {
         Class clazz = cl.loadClass("org.xmlBlaster.util.EmbeddedXmlBlaster");
      }
      catch (ClassNotFoundException ex) {
         assertTrue(ex.getMessage(), true);
      }

   }

   public void testClassloader() {
      System.out.println("***XmlBlasterClassloaderTest: testClassloader ...");

      try {
         EmbeddedXmlBlaster embed = EmbeddedXmlBlaster.startXmlBlaster(glob);

         I_XmlBlasterAccess conn = glob.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(glob, "marcel", "secret");
         conn.connect( qos, null ); // Login to xmlBlaster

         // a sample message unit
         String xmlKey = "<key oid='123' contentMime='text/plain' contentMimeExtended='myMime'>\n" +
                           "   <TestLogin-AGENT>" +
                           "   </TestLogin-AGENT>" +
                           "</key>";
         String senderContent = "Some content";
         MsgUnit msgUnit = new MsgUnit(xmlKey, senderContent.getBytes(), "<qos></qos>");
         conn.publish(msgUnit);

      }
      catch (Throwable t) {
         assertTrue( "Some error occured:"+t.toString(), true );
      }

      System.out.println("***XmlBlasterClassloaderTest: testClassloader [SUCCESS]");
   }


   protected void tearDown() {
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.snmp.InsertTest
    * </pre>
    */
   public static void main(String args[])
   {
      XmlBlasterClassloaderTest testCl = new XmlBlasterClassloaderTest("XmlBlasterClassloaderTest");
      testCl.setUp();
      testCl.testClassloader();
      testCl.tearDown();
   }
}
