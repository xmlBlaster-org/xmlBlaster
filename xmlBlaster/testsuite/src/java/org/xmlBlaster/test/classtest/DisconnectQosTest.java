package org.xmlBlaster.test.classtest;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.DisconnectQos;

import junit.framework.*;

/**
 * Test DisconnectQos. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * TODO: http://xmlunit.sourceforge.net/
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.DisconnectQosTest
 * @see org.xmlBlaster.util.DisconnectQos
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.disconnect.html" target="others">the interface.disconnect requirement</a>
 */
public class DisconnectQosTest extends TestCase {
   protected Global glob;
   int counter = 0;

   public DisconnectQosTest(String name) {
      super(name);
   }

   protected void setUp() {
      this.glob = Global.instance();
   }

   public void testParse() {
      System.out.println("***DisconnectQosTest: testParse ...");
      
      try {
         DisconnectQos qos = new DisconnectQos("<qos><deleteSubjectQueue>false</deleteSubjectQueue><clearSessions>true</clearSessions></qos>");
         assertEquals("deleteSubjectQueue failed", false, qos.deleteSubjectQueue());
         assertEquals("clearSessions failed", true, qos.clearSessions());

         qos = new DisconnectQos("<qos/>");
         assertEquals("deleteSubjectQueue failed", true, qos.deleteSubjectQueue());
         assertEquals("clearSessions failed", false, qos.clearSessions());
      }
      catch (XmlBlasterException e) {
         fail("testParse failed: " + e.toString());
      }

      System.out.println("***DisconnectQosTest: testParse [SUCCESS]");
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.DisconnectQosTest
    * </pre>
    */
   public static void main(String args[])
   {
      DisconnectQosTest testSub = new DisconnectQosTest("DisconnectQosTest");
      testSub.setUp();
      testSub.testParse();
      //testSub.tearDown();
   }
}
