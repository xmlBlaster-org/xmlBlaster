package org.xmlBlaster.test.snmp;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.util.qos.address.Destination;

import junit.framework.*;

/**
 * Test SNMP (simple network management protocol) to insert data. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.snmp.InsertTest
 *
 * @see org.xmlBlaster.engine.admin.extern.snmp.NodeEntryImpl
 */
public class InsertTest extends TestCase {
   protected Global glob;
   private static Logger log = Logger.getLogger(InsertTest.class.getName());

   public InsertTest(String name) {
      super(name);
   }

   protected void setUp() {
      this.glob = Global.instance();

   }

   public void testInsert() {
      System.out.println("***InsertTest: testInsert ...");
      
      int a = 10;
      int b = 10;
      assertEquals("Numbers are different", a, b);

      System.out.println("***InsertTest: testInsert [SUCCESS]");
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.snmp.InsertTest
    * </pre>
    */
   public static void main(String args[])
   {
      InsertTest testSub = new InsertTest("InsertTest");
      testSub.setUp();
      testSub.testInsert();
      //testSub.tearDown();
   }
}
