/*------------------------------------------------------------------------------
Name:      TestInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.test.contrib;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.xmlBlaster.contrib.ClientPropertiesInfo;
import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.InfoHelper;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.dbwatcher.Info;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.PluginInfo;


/**
 * TestInfo
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class TestInfo  extends XMLTestCase {

   private class OwnGlobalInfo extends GlobalInfo {
      public OwnGlobalInfo() {
         super(new String[] {});
      }

      protected void doInit(Global global, PluginInfo pluginInfo) throws XmlBlasterException {
         Map map = InfoHelper.getPropertiesStartingWith("test.one.two.", this, null);
         String[] keys = (String[])map.keySet().toArray(new String[map.size()]);
         for (int i=0; i < keys.length; i++) {
            log.info("KEY " + keys[i] + " : value " + (String)map.get(keys[i]));
         }
         
      }

   }

   private final static Logger log = Logger.getLogger(TestInfo.class.getName());
   
   public TestInfo() {
      super();
      XMLUnit.setIgnoreWhitespace(true);
   }
   
   /**
    * Configure database access.
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

   public void testRemoveEntry() {
      I_Info[] infos = { 
                         new Info("id"), 
                         new PropertiesInfo(new Properties()),
                         new ClientPropertiesInfo(new HashMap()), 
                         /* new DbInfo(new HashMap()), */
                         new OwnGlobalInfo()
                       }; 
      String[] names = new String[] {"Info", "PropertiesInfo", "ClientPropertiesInfo", /*.*/ "GlobalInfo"};
      
      for (int i=0; i < infos.length; i++) 
         doTestRemoveEntry(names[i], infos[i]);
   }

   public void testReplaceKey() {
      try {
         Global global = new Global();

         global.getProperty().set("test1", "key1");
         global.getProperty().set("test2", "key2");
         global.getProperty().set("test3", "key3");
         
         global.getProperty().set("test.one.two.${test0}", "test0");
         global.getProperty().set("test.one.two.${test1}", "test1");
         global.getProperty().set("test.one.two.${test2}", "test2");
         global.getProperty().set("test.one.two.${test3}", "test3");

         global.getProperty().set("${test.replace.key}", "testReplaceKey");
         global.getProperty().set("${test.replace.key1}", "testReplaceKey1");
         global.getProperty().set("someKey3", "testReplaceKey1");
         global.getProperty().set("${test.replace.key}", "testReplaceKey");
         global.getProperty().set("test.replace.key", "someKey");
         GlobalInfo info = new OwnGlobalInfo();
         info.init(global, null);
         String val = info.get("someKey", null);
         assertNotNull("The value must be set", val);
         assertEquals("wrong value of replaced key", "testReplaceKey", val);

         val = info.get("${test.replace.key1}", null);
         assertNotNull("The value must be set", val);
         
         val = global.getProperty().get("someKey3", (String)null);
         assertNotNull("The value must be set", val);

         
         Properties props = new Properties();
         props.put("one", "one");
         val = props.getProperty("one");
         assertNotNull("The value must be set", val);

         props.remove("one");
         val = props.getProperty("one");
         assertNull("The value must NOT be set", val);
         
         
         global.getProperty().removeProperty("someKey3");
         val = global.getProperty().get("someKey3", (String)null);
         assertNull("The value must NOT be set", val);
         
         val = info.get("${test.replace.key}", null);
         assertNotNull("The value must be set", val);
         
         val = global.getProperty().get("someKey3", (String)null);
         assertNull("The value must NOT be set", val);
         
         
      }
      catch (XmlBlasterException ex) {
         fail(ex.getMessage());
      }
      
   }

   public void doTestRemoveEntry(String name, I_Info info) {
      log.info("doTestRemoveEntry: Start with '" + name + "'");
      String obj = "testValue";
      String key = "test";
      info.putObject(key, obj);
      
      Object ret = info.putObject(key, null);
      assertNotNull(name + " object must not be null since it existed before removing", ret);
      assertEquals(name + " the object returned must be the one associated initially", obj, (String)ret);
      ret = info.getObject(key);
      assertNull(name + " object must be null after deletion", ret);
      log.info("doTestRemoveEntry: Successfully ended with '" + name + "'");
   }

   /**
    * @param args
    */
   public static void main(String[] args) {
      // junit.swingui.TestRunner.run(TestDbBasics.class);
      
      TestInfo test = new TestInfo();
      try {
         test.setUp();
         test.testRemoveEntry();
         test.tearDown();

         test.setUp();
         test.testReplaceKey();
         test.tearDown();

      } 
      catch (Exception ex) {
         ex.printStackTrace();
         fail();
      }
   }

}

