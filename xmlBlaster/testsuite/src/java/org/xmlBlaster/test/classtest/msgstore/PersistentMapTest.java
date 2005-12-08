package org.xmlBlaster.test.classtest.msgstore;

import org.jutils.log.LogChannel;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.PersistentMap;
import java.util.Map;

import junit.framework.TestCase;

/**
 * Test I_Map e.g. MapPlugin which allows to store randomly messages. 
 * <p>
 * Invoke: java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.classtest.msgstore.I_MapTest
 * </p>
 * @see org.xmlBlaster.engine.msgstore.I_Map
 * @see org.xmlBlaster.engine.msgstore.ram.MapPlugin
 * @see org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin
 */
public class PersistentMapTest extends TestCase {
   private String ME = "I_MapTest";
   protected Global glob;
   protected LogChannel log;

   public PersistentMapTest(Global global) {
      super();
      this.glob = global;
      if (this.glob == null)
         this.glob = new Global();
   }
   
   public PersistentMapTest() {
      this(null);
   }
   
   /**
    * Tests overflow of maxNumOfBytes() of a CACHE. 
    */
   public void testMap() {
      try {
         
         ME = "I_MapTest.testMap";
         System.out.println("***" + ME);
         boolean isNewMap = true;
         Map map = new PersistentMap(this.glob, "test_persistentProps", 1000L, 1000000L);
         if (isNewMap) {
            map.put("one", "1");
            map.put("two", "2");
            map.put("three", "3");
            map.put("four", "4");
         }
         
         assertEquals("testing one entry", "1", (String)map.get("one"));
         assertEquals("testing one entry", "2", (String)map.get("two"));
         assertEquals("testing one entry", "3", (String)map.get("three"));
         assertEquals("testing one entry", "4", (String)map.get("four"));

         Map map1 = new PersistentMap(this.glob, "test_persistentProps", -1L, -1L);
         assertEquals("testing one entry", "1", (String)map1.get("one"));
         assertEquals("testing one entry", "2", (String)map1.get("two"));
         assertEquals("testing one entry", "3", (String)map1.get("three"));
         assertEquals("testing one entry", "4", (String)map1.get("four"));
      }
      catch(Exception e) {
         e.printStackTrace();
         log.error(ME, "Exception thrown: " + e.getMessage());
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
   }

   public void setUp() {
   }

   public void tearDown() {
      
   }
   
   /**
    * <pre>
    *  java -Dtrace=true org.xmlBlaster.test.classtest.msgstore.I_MapTest  > test.log
    * </pre>
    */
   public static void main(String args[]) {
      PersistentMapTest testSub = new PersistentMapTest();
      testSub.setUp();
      testSub.testMap();
      testSub.tearDown();
   }
}

