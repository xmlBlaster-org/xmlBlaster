package org.xmlBlaster.test.classtest.qos;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.address.Address;

import junit.framework.*;

/**
 * Test AddressBase. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * TODO: http://xmlunit.sourceforge.net/
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.qos.AddressBaseTest
 * @see org.xmlBlaster.util.qos.address.AddressBase
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html" target="others">the interface.connect requirement</a>
 */
public class AddressBaseTest extends TestCase {
   private final String ME = "AddressBaseTest";
   protected Global glob;
   private static Logger log = Logger.getLogger(AddressBaseTest.class.getName());
   int counter = 0;

   public AddressBaseTest(String name) {
      super(name);
   }

   protected void setUp() {
      this.glob = Global.instance();

   }

   /**
    * Tries with all known tags
    */
   public void testSet() {
      System.out.println("***AddressBaseTest: testSet ...");
      
      try {
         {
            Global glob = new Global();
            Address a = new Address(glob);
            a.setType("SOCKET");
            a.setBootstrapHostname("oioihost");
            a.setBootstrapPort(9999);
            a.setRawAddress("127.0.0.1:7600");
            a.setCollectTime(12345L);
            a.setPingInterval(54321L);
            a.setRetries(17);
            a.setDelay(7890L);
            a.setOneway(true);
            a.setSecretSessionId("0x4546hwi89");
            System.out.println(a.toXml());
            assertEquals("", "SOCKET", a.getType());
            assertEquals("", "oioihost", a.getBootstrapHostname());
            assertEquals("", 9999, a.getBootstrapPort());
            assertEquals("", "127.0.0.1:7600", a.getRawAddress());
            assertEquals("", 12345L, a.getCollectTime());
            assertEquals("", 54321L, a.getPingInterval());
            assertEquals("", 17, a.getRetries());
            assertEquals("", 7890L, a.getDelay());
            assertEquals("", true, a.oneway());
            assertEquals("", "0x4546hwi89", a.getSecretSessionId());
         }
         {
            Global glob = new Global();
            Address a = new Address(glob);
            a.setRawAddress("127.0.0.1:7600"); // A setRawAddress() should not be modified
            a.setBootstrapHostname("oioihost");
            a.setBootstrapPort(9999);
            System.out.println(a.toXml());
            assertEquals("", "oioihost", a.getBootstrapHostname());
            assertEquals("", 9999, a.getBootstrapPort());
            assertEquals("", "127.0.0.1:7600", a.getRawAddress());
         }
         {
            Global glob = new Global();
            Address a = new Address(glob);
            a.setBootstrapHostname("oioihost");
            a.setBootstrapPort(9999);
            System.out.println(a.toXml());
            assertEquals("", "oioihost", a.getBootstrapHostname());
            assertEquals("", 9999, a.getBootstrapPort());
            assertEquals("", "", a.getRawAddress());
         }
         {
            String nodeId = "heron";
            
            java.util.Vector vec = new java.util.Vector();
            vec.addElement("-sessionId");
            vec.addElement("ERROR");
            vec.addElement("-sessionId["+nodeId+"]");
            vec.addElement("OK");
            vec.addElement("-pingInterval");
            vec.addElement("8888");
            vec.addElement("-delay["+nodeId+"]");
            vec.addElement("8888");
            String[] args = (String[])vec.toArray(new String[0]);

            Global glob = new Global(args);
            Address a = new Address(glob, "RMI", nodeId);
            System.out.println(a.toXml());
         }
      }
      catch(Throwable e) {
         e.printStackTrace();
         System.err.println("TestFailed: " + e.toString());
         fail(e.toString());
      }

      System.out.println("***AddressBaseTest: testSet [SUCCESS]");
   }

   public void testDefault() {
      System.out.println("***AddressBaseTest: testDefault ...");
      
      Address a = new Address(glob);
      assertEquals("", "xmlBlaster://"+glob.getLocalIP()+":3412", glob.getBootstrapAddress().getRawAddress());
      assertEquals("", "", a.getRawAddress());

      System.out.println("***AddressBaseTest: testDefault [SUCCESS]");
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.qos.AddressBaseTest
    * </pre>
    */
   public static void main(String args[])
   {
      AddressBaseTest testSub = new AddressBaseTest("AddressBaseTest");
      testSub.setUp();
      testSub.testSet();
      testSub.testDefault();
      //testSub.tearDown();
   }
}
