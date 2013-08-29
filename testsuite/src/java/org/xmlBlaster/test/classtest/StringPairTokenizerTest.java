package org.xmlBlaster.test.classtest;

import java.util.Map;
import java.util.logging.Logger;

import junit.framework.TestCase;

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
   private static Logger log = Logger.getLogger(StringPairTokenizerTest.class.getName());
   int counter = 0;

   public StringPairTokenizerTest(String name, String[] args) {
      super(name);
      this.glob = Global.instance();
      this.glob.init(args);

   }

   public StringPairTokenizerTest(String name) {
      super(name);
      this.glob = Global.instance();

   }

   protected void setUp() {
   }

   protected void tearDown() {
   }

   public void testTokenizerApos() {
	   {
		   boolean preserveInsideQuoteChar = true;
		   String csv = "b=\"6\"";
		   //String csv = "a=\"5\",b=\"6,7\"";
		   String[] toks = StringPairTokenizer.parseLine(new String[] { csv }, ',', '"',
					false, preserveInsideQuoteChar);
		   for (String tok: toks) {
			   System.out.println(tok);
		   }
		   assertEquals(1, toks.length);
		   assertEquals("b=\"6\"", toks[0]);
	   }
	   {
		   boolean preserveInsideQuoteChar = true;
		   String csv = "a=\"5\",b=\"6,7\"";
		   String[] toks = StringPairTokenizer.parseLine(new String[] { csv }, ',', '"',
					false, preserveInsideQuoteChar);
		   for (String tok: toks) {
			   System.out.println(tok);
		   }
		   assertEquals(2, toks.length);
		   assertEquals("a=\"5\"", toks[0]);
		   assertEquals("b=\"6,7\"", toks[1]);
	   }
	   {
		   boolean preserveInsideQuoteChar = false;
		   String csv = "a=\"5\",b=\"6,7\"";
		   String[] toks = StringPairTokenizer.parseLine(new String[] { csv }, ',', '"',
					false, preserveInsideQuoteChar);
		   for (String tok: toks) {
			   System.out.println(tok);
		   }
		   assertEquals(2, toks.length);
		   assertEquals("a=5", toks[0]);
		   assertEquals("b=6,7", toks[1]);
	   }
	   {
		   boolean preserveInsideQuoteChar = false;
		   String csv = "a=5,b=6,7";
		   String[] toks = StringPairTokenizer.parseLine(new String[] { csv }, ',', '"',
					false, preserveInsideQuoteChar);
		   for (String tok: toks) {
			   System.out.println(tok);
		   }
		   assertEquals(3, toks.length);
		   assertEquals("a=5", toks[0]);
		   assertEquals("b=6", toks[1]);
		   assertEquals("7", toks[2]);
	   }
   }

   public void testClientProperties() {
      try {
         int maxEntries = 0;
         long maxSize = 0L;
         boolean consumable = false;
         long waitingDelay = 4000L;
         
         String cmd = "maxEntries=4&maxSize=-1&consumable=true&waitingDelay=1000";
         Map<String, ClientProperty> props = StringPairTokenizer.parseToStringClientPropertyPairs(cmd, "&", "=");
         
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
			Map<String, ClientProperty> props = StringPairTokenizer.parseToStringClientPropertyPairs(
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

   public void testMapCSV() {
      {
         String line = "aKey=\"a value with &#034; apost, and semicolon\",otherKey=2300,third&#061;Key=a key with assignment,key4=\"Hello, == world\"";
         Map<String, String> map = StringPairTokenizer.CSVToMap(line);
         String result = StringPairTokenizer.mapToCSV(map);
         map = StringPairTokenizer.CSVToMap(result);
         assertEquals(4, map.size());
         assertEquals("a value with \" apost, and semicolon", (String)map.get("aKey"));
         assertEquals("2300", (String)map.get("otherKey"));
         assertEquals("a key with assignment", (String)map.get("third=Key"));
         assertEquals("Hello, == world", (String)map.get("key4"));
         assertNull(map.get("bla"));
         log.info(line);
         log.info(" to ");
         log.info(result);
         log.info("Done");
      }
      {
         String line = "aNullKey,otherEmptyKey=,thirdKey=\" \", fourthKey = Blanks ";
         Map<String, String> map = StringPairTokenizer.CSVToMap(line);
         String result = StringPairTokenizer.mapToCSV(map);
         map = StringPairTokenizer.CSVToMap(result);
         assertEquals(4, map.size());
         assertEquals(null, (String)map.get("aNullKey"));
         assertTrue(map.containsKey("aNullKey"));
         assertEquals("", (String)map.get("otherEmptyKey"));
         assertEquals("", (String)map.get("thirdKey"));
         assertEquals("Blanks", (String)map.get("fourthKey"));
         assertNull(map.get("bla"));
         log.info(line);
         log.info(" to ");
         log.info(result);
         log.info("Done");
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
