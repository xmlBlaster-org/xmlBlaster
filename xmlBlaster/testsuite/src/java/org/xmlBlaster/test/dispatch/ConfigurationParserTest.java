package org.xmlBlaster.test.dispatch;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.dispatch.plugins.prio.*;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.XmlBlasterException;

import java.util.Map;
import java.util.Iterator;

import junit.framework.*;

/**
 * Test ConfigurationParser. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * TODO: http://xmlunit.sourceforge.net/
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.dispatch.ConfigurationParserTest
 * @see org.xmlBlaster.util.dispatch.plugins.prio.ConfigurationParser
 * @see org.xmlBlaster.util.dispatch.plugins.prio.PriorizedDispatchPlugin
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/dispatch.plugin.priorizedDispatch.html" target="others">the dispatch.plugin.priorizedDispatch requirement</a>
 * @author xmlBlaster@marcelruff.info
 */
public class ConfigurationParserTest extends TestCase {
   protected Global glob;
   private static Logger log = Logger.getLogger(ConfigurationParserTest.class.getName());
   int counter = 0;

   public ConfigurationParserTest(String name) {
      super(name);
   }

   protected void setUp() {
      this.glob = Global.instance();

   }

   protected void tearDown() {
      this.glob = null;
      this.log = null;
      Global.instance().shutdown();
   }

   /** Test a valid configuration */
   public void testParse() {
      System.out.println("***ConfigurationParserTest: testParse ...");

      try {
         String xml =
           "<msgDispatch defaultStatus='64k' defaultAction='send'>\n" +
           "  <onStatus oid='_bandwidth.status' content='64k' defaultAction='destroy'>\n" +
           "    <action do='send'  ifPriority='7-9'/>\n" +
           "    <action do='queue'  ifPriority='3-6'/>\n" +
           "    <action do='queue|notifySender'  ifPriority='2'/>\n" +
           "  </onStatus>\n" +
           "  <onStatus oid='_bandwidth.status' content='2M'>\n" +
           "    <action do='send'/>\n" + // defaults to  ifPriority='0-9'
           "  </onStatus>\n" +
           "  <onStatus oid='_bandwidth.status' content='DOWN' defaultAction='queue' connectionState='polling'>\n" +
           "    <action do='destroy'/>\n" + // defaults to  ifPriority='0-9'
           "  </onStatus>\n" +
           "</msgDispatch>\n";

         ConfigurationParser parser = new ConfigurationParser(glob, xml);

         assertEquals("", "send", parser.getDefaultDispatchAction().getAction());
         assertEquals("", "64k", parser.getDefaultStatus());
         Map confMap = parser.getStatusConfigurationMap();
         assertEquals("Illegal map size", 3, confMap.size());
         assertTrue("Missing key", confMap.containsKey("64k"));
         assertTrue("Missing key", confMap.containsKey("2M"));
         assertTrue("Missing key", confMap.containsKey("DOWN"));
         //Iterator it = confMap.keySet().iterator();
         //while (it.hasNext()) {
         //   StatusConfiguration conf = (StatusConfiguration)it.next();
         //}
         StatusConfiguration conf = (StatusConfiguration)confMap.get("64k");
         assertEquals("", "_bandwidth.status", conf.getOid());
         assertEquals("", "64k", conf.getContent());
         assertEquals("", (String)null, conf.getConnectionState());
         assertEquals("", "destroy", conf.getDefaultDispatchAction().getAction());
         assertEquals("", false, conf.defaultActionOnly());
         for (int i=7; i<=9; i++) {
            DispatchAction action = conf.getDispatchAction(PriorityEnum.toPriorityEnum(i));
            assertEquals("Wrong action string", "send", action.getAction());
            assertEquals("Wrong boolean action", true, action.doSend());
            assertEquals("Wrong boolean action", false, action.doQueue());
            assertEquals("Wrong boolean action", false, action.doDestroy());
            assertEquals("Wrong boolean action", false, action.doNotifySender());
         }
         for (int i=3; i<=6; i++) {
            DispatchAction action = conf.getDispatchAction(PriorityEnum.toPriorityEnum(i));
            assertEquals("Wrong action string", "queue", action.getAction());
            assertEquals("Wrong boolean action", false, action.doSend());
            assertEquals("Wrong boolean action", true, action.doQueue());
            assertEquals("Wrong boolean action", false, action.doDestroy());
            assertEquals("Wrong boolean action", false, action.doNotifySender());
         }
         for (int i=2; i<=2; i++) {
            DispatchAction action = conf.getDispatchAction(PriorityEnum.toPriorityEnum(i));
            assertEquals("Wrong action string", "queue|notifySender", action.getAction());
            assertEquals("Wrong boolean action", false, action.doSend());
            assertEquals("Wrong boolean action", true, action.doQueue());
            assertEquals("Wrong boolean action", false, action.doDestroy());
            assertEquals("Wrong boolean action", true, action.doNotifySender());
         }
         for (int i=0; i<=1; i++) {
            DispatchAction action = conf.getDispatchAction(PriorityEnum.toPriorityEnum(i));
            assertEquals("Wrong action string", "destroy", action.getAction());
            assertEquals("Wrong boolean action", false, action.doSend());
            assertEquals("Wrong boolean action", false, action.doQueue());
            assertEquals("Wrong boolean action", true, action.doDestroy());
            assertEquals("Wrong boolean action", false, action.doNotifySender());
         }


         conf = (StatusConfiguration)confMap.get("2M");
         assertEquals("", "_bandwidth.status", conf.getOid());
         assertEquals("", "2M", conf.getContent());
         assertEquals("", (String)null, conf.getConnectionState());
         assertEquals("", "send", conf.getDefaultDispatchAction().getAction());
         assertEquals("", true, conf.defaultActionOnly());
         for (int i=0; i<=9; i++) {
            DispatchAction action = conf.getDispatchAction(PriorityEnum.toPriorityEnum(i));
            assertEquals("Wrong action string", "send", action.getAction());
            assertEquals("Wrong boolean action", true, action.doSend());
            assertEquals("Wrong boolean action", false, action.doQueue());
            assertEquals("Wrong boolean action", false, action.doDestroy());
            assertEquals("Wrong boolean action", false, action.doNotifySender());
         }

         conf = (StatusConfiguration)confMap.get("DOWN");
         assertEquals("", "_bandwidth.status", conf.getOid());
         assertEquals("", "DOWN", conf.getContent());
         assertEquals("", "POLLING", conf.getConnectionState().toString());
         assertEquals("", "queue", conf.getDefaultDispatchAction().getAction());
         assertEquals("", false, conf.defaultActionOnly());
         for (int i=0; i<=9; i++) {
            DispatchAction action = conf.getDispatchAction(PriorityEnum.toPriorityEnum(i));
            assertEquals("Wrong action string", "destroy", action.getAction());
            assertEquals("Wrong boolean action", false, action.doSend());
            assertEquals("Wrong boolean action", false, action.doQueue());
            assertEquals("Wrong boolean action", true, action.doDestroy());
            assertEquals("Wrong boolean action", false, action.doNotifySender());
         }
      }
      catch (XmlBlasterException e) {
         fail("testParse failed: " + e.toString());
      }

      System.out.println("***ConfigurationParserTest: testParse [SUCCESS]");
   }

   /** Test invalid configuration */
   public void testInvalidParse() {
      System.out.println("***ConfigurationParserTest: testInvalidParse ...");

      try {
         String xml = "<msgDispatch defaultStatus='64k' defaultAction='sendXXXX'/>";
         ConfigurationParser parser = new ConfigurationParser(glob, xml);
         fail("testInvalidParse failed, invalid xml should not be parseable");
      }
      catch (XmlBlasterException e) {
         System.out.println("Success: Expected exception: " + e.toString());
      }

      try {
         String xml =
           "<msgDispatch defaultStatus='64k' defaultAction='send'>\n" +
           "  <onStatus oid='_bandwidth.status' content='64k' defaultAction='destroy'>\n" +
           "    <action do='send'  ifPriority='7-99'/>\n" +
           "  </onStatus>\n" +
           "</msgDispatch>\n";
         ConfigurationParser parser = new ConfigurationParser(glob, xml);
         fail("testInvalidParse failed, invalid xml should not be parseable, prio is not allowed");
      }
      catch (XmlBlasterException e) {
         System.out.println("Success: Expected exception: " + e.toString());
      }

      try {
         String xml =
           "<msgDispatch defaultStatus='64k' defaultAction='send'>\n" +
           "  <onStatus oid='_bandwidth.status' content='64k' defaultAction='destroy'>\n" +
           "    <action do='sendXXX'  ifPriority='7'/>\n" +
           "  </onStatus>\n" +
           "</msgDispatch>\n";
         ConfigurationParser parser = new ConfigurationParser(glob, xml);
         fail("testInvalidParse failed, invalid xml should not be parseable, action is invalid");
      }
      catch (XmlBlasterException e) {
         System.out.println("Success: Expected exception: " + e.toString());
      }

      try {
         String xml =
           "<msgDispatch defaultStatus='64k' defaultAction='send'>\n" +
           "  <onStatus oid='_bandwidth.status' content='64k' defaultAction='XXXXdestroy'>\n" +
           "    <action do='send'  ifPriority='7'/>\n" +
           "  </onStatus>\n" +
           "</msgDispatch>\n";
         ConfigurationParser parser = new ConfigurationParser(glob, xml);
         fail("testInvalidParse failed, invalid xml should not be parseable");
      }
      catch (XmlBlasterException e) {
         System.out.println("Success: Expected exception: " + e.toString());
      }

      try {
         String xml =
           "<msgDispatch>\n" +
           "  <onStatus oid='_bandwidth.status' content='64k'>\n" +
           "    <action do='send|queue'  ifPriority='7'/>\n" +
           "  </onStatus>\n" +
           "</msgDispatch>\n";
         ConfigurationParser parser = new ConfigurationParser(glob, xml);
         fail("testInvalidParse failed, some combination of actions are invalid");
      }
      catch (XmlBlasterException e) {
         System.out.println("Success: Expected exception: " + e.toString());
      }

      System.out.println("***ConfigurationParserTest: testInvalidParse [SUCCESS]");
   }

   /** Test empty parse -> default settings */
   public void testDefault() {
      System.out.println("***ConfigurationParserTest: testDefault ...");
      
      try {
         ConfigurationParser parser = new ConfigurationParser(glob, (String)null);
         assertEquals("", DispatchAction.SEND, parser.getDefaultDispatchAction().getAction());
         assertTrue("", null == parser.getDefaultStatus());
         Map confMap = parser.getStatusConfigurationMap();
         assertEquals("Illegal map size", 0, confMap.size());
         StatusConfiguration conf = parser.getStatusConfiguration((String)null);
         assertTrue("", null == conf.getOid());
         assertTrue("", null == conf.getContent());
         assertEquals("", (String)null, conf.getConnectionState());
         assertEquals("", true, conf.defaultActionOnly());
         assertEquals("", DispatchAction.SEND, conf.getDefaultDispatchAction().getAction());
         for (int i=0; i<=9; i++) {
            DispatchAction action = conf.getDispatchAction(PriorityEnum.toPriorityEnum(i));
            assertEquals("Wrong action string", "send", action.getAction());
            assertEquals("Wrong boolean action", true, action.doSend());
            assertEquals("Wrong boolean action", false, action.doQueue());
            assertEquals("Wrong boolean action", false, action.doDestroy());
            assertEquals("Wrong boolean action", false, action.doNotifySender());
         }
      }
      catch (XmlBlasterException e) {
         fail("testDefault failed: " + e.toString());
      }

      System.out.println("***ConfigurationParserTest: testDefault [SUCCESS]");
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.dispatch.ConfigurationParserTest
    * </pre>
    */
   public static void main(String args[])
   {
      try {
         ConfigurationParserTest testSub = new ConfigurationParserTest("ConfigurationParserTest");
         testSub.setUp();
         testSub.testDefault();
         testSub.testParse();
         testSub.testInvalidParse();
         testSub.tearDown();
      }
      catch (Throwable e) {
         e.printStackTrace();
         System.err.println("ERROR: " + e.toString());
      }
   }
}
