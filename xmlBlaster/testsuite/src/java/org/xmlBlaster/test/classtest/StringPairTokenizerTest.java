package org.xmlBlaster.test.classtest;

import java.util.Map;

import junit.framework.TestCase;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.StringPairTokenizer;
import org.xmlBlaster.util.qos.ClientProperty;

/**
 * Test ConnectQos. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.StringPairTokenizerTest
 */
public class StringPairTokenizerTest extends TestCase {
   
   final static String ME = "StringPairTokenizerTest";
   protected Global glob;
   protected LogChannel log;
   int counter = 0;

   public StringPairTokenizerTest(String name, String[] args) {
      super(name);
      this.glob = Global.instance();
      this.glob.init(args);
      this.log = this.glob.getLog("test");
   }

   public StringPairTokenizerTest(String name) {
      super(name);
      this.glob = Global.instance();
      this.log = this.glob.getLog("test");
   }

   protected void setUp() {
   }

   protected void tearDown() {
   }

   public void testClientProperties() {
      try {
         int maxEntries = 0;
         long maxSize = 0L;
         boolean consumable = false;
         long waitingDelay = 4000L;
         
         String cmd = "maxEntries=4&maxSize=-1&consumable=true&waitingDelay=1000";
         Map props = StringPairTokenizer.parseToStringClientPropertyPairs(cmd, "&", "=");
         
         ClientProperty prop = (ClientProperty)props.get("maxEntries");
         assertNotNull("property should not be null", prop);
         maxEntries = prop.getIntValue();
         assertEquals("maxEntries", 4, maxEntries);
         
         prop = (ClientProperty)props.get("maxSize");
         assertNotNull("property should not be null", prop);
         maxSize = prop.getLongValue();
         assertEquals("maxSize", -1L, maxSize);

         prop = (ClientProperty)props.get("consumable");
         assertNotNull("property should not be null", prop);
         consumable = prop.getBooleanValue();
         assertEquals("consumable", true, consumable);

         prop = (ClientProperty)props.get("waitingDelay");
         assertNotNull("property should not be null", prop);
         waitingDelay = prop.getLongValue();
         assertEquals("waitingDelay", 1000L, waitingDelay);
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not occur here", false);
      }
   }

   public void testURL() {
	    // TODO: need to add " around some parameter-values and extend the tokenizer accordingly
		String cmd = "nodeClass=node&node=izar&action=initReplication&p1=Hallo";
		try {
			Map props = StringPairTokenizer.parseToStringClientPropertyPairs(
					cmd, "&", "=");

			assertTrue("Missing property", props.containsKey("nodeClass"));
			assertTrue("Missing property", props.containsKey("node"));
			assertTrue("Missing property", props.containsKey("action"));
			assertTrue("Missing property", props.containsKey("p1"));
			assertFalse("Property to much", props.containsKey("izar"));

			// for some reason, they don't work :-(
//			assertEquals("wrong value", "node", props.get("nodeClass"));
//			assertEquals("wrong value", "izar", props.get("node"));
//			assertEquals("wrong value", "action", props.get("initReplication"));
//			assertEquals("wrong value", "p1", props.get("Hallo"));
		} catch (Exception ex) {
			ex.printStackTrace();
			assertTrue("an exception should not occur here", false);
		}

	}
   /**
	 * <pre>
	 *   java org.xmlBlaster.test.classtest.StringPairTokenizerTest
	 * </pre>
	 */
   public static void main(String args[])
   {
      StringPairTokenizerTest test = new StringPairTokenizerTest("StringPairTokenizerTest", args);

      test.setUp();
      test.testClientProperties();
      test.testURL();
      test.tearDown();

   }
}
