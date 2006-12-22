/**
 * 
 */
package org.xmlBlaster.test.classtest;

import java.io.File;
import java.net.URL;
import java.util.logging.Logger;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.FileLocator;
import org.xmlBlaster.util.Global;

import junit.framework.TestCase;

/**
 * @author Marcel Ruff
 *
 */
public class FileLocatorTest extends TestCase {

   private Logger log = Logger.getLogger(FileLocatorTest.class.getName());
   
   public static void main(String[] args) {
      // junit.swingui.TestRunner.run(FileLocatorTest.class);
      
      FileLocatorTest test = new FileLocatorTest("file locator test");
      
      try {
         test.setUp();
         test.testFileInExternalJarFile();
         test.tearDown();
         
         test.setUp();
         test.testFileAccess();
         test.tearDown();
         
         test.setUp();
         test.testHttpAccess();
         test.tearDown();
      }
      catch (Exception ex) {
         ex.printStackTrace();
         fail();
      }
      
   }

   /**
    * Constructor for FileLocatorTest.
    * @param arg0
    */
   public FileLocatorTest(String arg0) {
      super(arg0);
   }

   /*
    * @see TestCase#setUp()
    */
   protected void setUp() throws Exception {
      super.setUp();
   }

   /*
    * @see TestCase#tearDown()
    */
   protected void tearDown() throws Exception {
      super.tearDown();
   }

   /**
    * This test must currently be done manually, the file to be found must reside in a jar file
    * outside the jar files normally used by xmlBlaster.
    * 
    * @throws XmlBlasterException
    */
   public void testFileInExternalJarFile() throws XmlBlasterException {
      String filename = "avitech/resources/axl/xsl/notamRequest.xsl";
      String txt = FileLocator.readAsciiFile(filename);
      log.info(txt);
      
   }

   
   public void testHttpAccess() throws XmlBlasterException {
      Global glob = Global.instance();
      FileLocator locator = new FileLocator(glob);
      URL url = locator.findFileInXmlBlasterSearchPath(null, "http://www.xmlblaster.org/empty.html");
      assertNotNull(url);
      String content = locator.read(url);
      assertTrue("Downloaded file is empty", content.length() > 5);
   }

   public void testFileAccess() throws XmlBlasterException {
      FileLocator.writeFile("FileLocatorTest.dummy", "Hello");
      Global glob = Global.instance();
      FileLocator locator = new FileLocator(glob);
      URL url = locator.findFileInXmlBlasterSearchPath(null, "file:FileLocatorTest.dummy");
      assertNotNull(url);
      String content = locator.read(url);
      assertNotNull(content);
      assertEquals("file://FileLocatorTest.dummy", "Hello", content.trim());
      File f = new File("FileLocatorTest.dummy");
      f.delete();
   }
}
