/*------------------------------------------------------------------------------
Name:      TestInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.test.contrib;

import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Logger;

import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.xmlBlaster.contrib.ClientPropertiesInfo;
import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.dbwatcher.Info;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.PluginInfo;


/**
 * TestInfo
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class TestInfo  extends XMLTestCase {

   private class OwnGlobalInfo extends GlobalInfo {
      public OwnGlobalInfo() {
         super(new String[] {});
      }

      protected void doInit(Global global, PluginInfo pluginInfo) throws XmlBlasterException {
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
      } 
      catch (Exception ex) {
         ex.printStackTrace();
         fail();
      }
   }

}

