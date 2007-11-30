/*------------------------------------------------------------------------------
 Name:      TestDbStorage.java
 Project:   org.xmlBlasterProject:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.test.contrib.db;

import java.util.Set;
import java.util.logging.Logger;

import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.db.DbPool;
import org.xmlBlaster.contrib.db.DbStorage;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.util.qos.ClientProperty;

/**
 * Test helper classes as for example beans used for the configuration.
 * Does not need a DB nor xmlBlaster running.
 * 
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class TestDbStorage extends XMLTestCase {
   private static Logger log = Logger.getLogger(TestDbStorage.class.getName());
   
   private I_DbPool pool;
   private DbStorage dbStorage;
   
   /**
    * Start the test.
    * <pre>
    *  java -Ddb=oracle junit.swingui.TestRunner -noloading org.xmlBlaster.test.contrib.db.TestDbStorage
    * </pre>
    * @param args  Command line settings
    */
   public static void main(String[] args) {
      
      TestDbStorage test = new TestDbStorage();
      try {

         test.setUp();
         test.testAllOperations();
         test.tearDown();

      } 
      catch (Exception ex) {
         ex.printStackTrace();
         fail();
      }
   }

   /**
    * Default ctor.
    */
   public TestDbStorage() {
      super();
      XMLUnit.setIgnoreWhitespace(true);
   }

   /**
    * Constructor for TestDbStorage.
    * 
    * @param arg0
    */
   public TestDbStorage(String arg0) {
      super(arg0);
      XMLUnit.setIgnoreWhitespace(true);
   }

   /**
    * Configure database access.
    * @see TestCase#setUp()
    */
   protected void setUp() throws Exception {
      super.setUp();
      I_Info info = new PropertiesInfo(System.getProperties());
      this.pool = new DbPool();
      this.pool.init(info);
      String context = null; // shall be set to '/'
      info.put("dbs.table", "TEST_DB_STORAGE");
      this.dbStorage = new DbStorage(info, this.pool, context);
      
   }

   /*
    * @see TestCase#tearDown()
    */
   protected void tearDown() throws Exception {
      this.dbStorage = null;
      this.pool.shutdown();
      super.tearDown();
   }

   
   /**
    * 
    */
   public final void testAllOperations() {
      log.info("Start testAllOperations");
      
      try {
         String name = "one";
         String value = "some value";
         String encoding = null; // no encoding 
         String type = null; // this is a string
         ClientProperty prop = new ClientProperty(name, type, encoding, value);
         
         this.dbStorage.addProperty(prop);
         try {
            this.dbStorage.addProperty(prop);
            assertTrue("an exception shall occur when trying to a addProperty on a property which has already been stored", false);
         }
         catch (Exception ex) {
            log.info("An exception is ok at this location since the entry existed already.");
         }
         try {
            this.dbStorage.put(prop);
         }
         catch (Exception ex) {
            assertTrue("an exception shall not occur when trying to a put on an entry which already exists", false);
         }
         String newTxt = "THIS IS ANOTHER VALUE";
         prop.setValue(newTxt, null);
         this.dbStorage.put(prop);
         
         ClientProperty prop1 = this.dbStorage.getProperty(name);
         assertTrue("The property has been stored, it shall not be null when doing a 'get'", prop1 != null);
         assertEquals("comparing the two values", prop.getValueRaw(), prop1.getValueRaw());

         this.dbStorage.remove(name);
         prop1 = this.dbStorage.getProperty(name);
         assertTrue("The property has been removed, it should now be null.", prop1 == null);
         
         this.dbStorage.put(new ClientProperty("2-two", null, null, "2"));
         this.dbStorage.put(new ClientProperty("4-four", null, null, "4"));
         this.dbStorage.addProperty(new ClientProperty("5-five", null, null, "5"));
         this.dbStorage.addProperty(new ClientProperty("3-three", null, null, "3"));

         Set set = this.dbStorage.getKeys();
         assertEquals("Wrong number of keys found", 4, set.size());
         
         assertTrue("Wrong content", set.contains("2-two"));
         assertTrue("Wrong content", set.contains("3-three"));
         assertTrue("Wrong content", set.contains("4-four"));
         assertTrue("Wrong content", set.contains("5-five"));
         
         this.dbStorage.clean();
         set = this.dbStorage.getKeys();
         assertEquals("Wrong number of keys found", 0, set.size());
         
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }

      log.info("SUCCESS");
   }

}
