package org.xmlBlaster.test.classtest;

import java.util.Properties;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;

import junit.framework.*;
import org.xmlBlaster.engine.runlevel.RunLevelActionSaxFactory;
import org.xmlBlaster.engine.runlevel.RunLevelAction;
import org.xmlBlaster.engine.runlevel.PluginConfig;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.qos.MsgQosSaxFactory;
import org.xmlBlaster.engine.runlevel.PluginConfigSaxFactory;
import org.xmlBlaster.engine.runlevel.PluginHolderSaxFactory;
import org.xmlBlaster.engine.runlevel.PluginHolder;
import org.xmlBlaster.engine.runlevel.PluginConfigComparator;


/**
 * Test ConnectQos. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * TODO: http://xmlunit.sourceforge.net/
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.RunLevelTest
 * @see org.xmlBlaster.util.qos.ConnectQosData
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html" target="others">the interface.connect requirement</a>
 */
public class RunLevelTest extends TestCase {
   final static String ME = "RunLevelTest";
   protected Global glob;
   protected LogChannel log;
   int counter = 0;


   public RunLevelTest(String name, String[] args) {
      super(name);
      this.glob = Global.instance();
      this.glob.init(args);
      this.log = this.glob.getLog("test");
   }

   public RunLevelTest(String name) {
      super(name);
      this.glob = Global.instance();
      this.log = this.glob.getLog("test");
   }

   protected void setUp() {
   }

   protected void tearDown() {
   }

   public void testAction() {
      String me = ME + "-testAction";
      this.log.info(me, "start");

      try {
         String xml = "<action do='LOAD'\n" +
                      "        onStartupRunlevel='3'\n" +
                      "        sequence='5'\n" +
                      "        onFail='resource.configuration.pluginFailed'/>";
   
         RunLevelActionSaxFactory factory = new RunLevelActionSaxFactory(this.glob);
         RunLevelAction action = factory.readObject(xml);

         for (int i=0; i < 2; i++) {
            assertEquals(me + " checking do attribute", "LOAD", action.getDo());
            assertEquals(me + " checking onFail attribute", "resource.configuration.pluginFailed", action.getOnFail().getErrorCode());
            assertEquals(me + " checking onShutdownLevel attribute", -1, action.getOnShutdownRunlevel());
            assertEquals(me + " checking onStartupLevel attribute", 3, action.getOnStartupRunlevel());
            assertEquals(me + " checking sequence attribute", 5, action.getSequence());
            assertEquals(me + " checking hasOnFail", true, action.hasOnFail());
            assertEquals(me + " checking isOnShutdownLevel", false, action.isOnShutdownRunlevel());
            assertEquals(me + " checking isOnStartupLevel", true, action.isOnStartupRunlevel());
            xml = action.toXml();
            action = factory.readObject(xml);
            this.log.info(me, "going to test the second time ...");
         }

         // now test a null string

         try {
            xml = null;
            action = factory.readObject(xml);
            assertTrue(me + " a null string is not allowed here. Should have thrown an exception", true);
         }
         catch (XmlBlasterException ex) {
            this.log.info(me, "the exception is allowed here since a null string is not allowed here." + ex.getMessage());
         }
         try {
            xml = "";
            action = factory.readObject(xml);
            assertTrue(me + " an empty string is not allowed here. Should have thrown an exception", true);
         }
         catch (XmlBlasterException ex) {
            this.log.info(me, "the exception is allowed here since an empty string is not allowed here." + ex.getMessage());
         }
         try {
            xml = "xyz";
            action = factory.readObject(xml);
            assertTrue(me + " a non-xml string is not allowed here. Should have thrown an exception", true);
         }
         catch (XmlBlasterException ex) {
            this.log.info(me, "the exception is allowed here since a non-xml string is not allowed here." + ex.getMessage());
         }
         try {
            xml = "<xmlBlaster></xmlBlaster>";
            action = factory.readObject(xml);
            assertTrue(me + " a wrong tag name is not allowed here. Should have thrown an exception", true);
         }
         catch (XmlBlasterException ex) {
            this.log.info(me, "the exception is allowed here since  a wrong tag name is not allowed here." + ex.getMessage());
         }

         // this is allowed ...
         xml = "<action/>";
         action = factory.readObject(xml);

      }
      catch (XmlBlasterException e) {
         fail(ME+ " failed: " + e.toString());
      }
      this.log.info(ME, "successfully ended");
   }

   public void testPluginConfig() {
      String me = ME + "-testPluginConfig";
      try {
         this.log.info(me, "start");
         PluginConfig config = new PluginConfig(this.glob, "queueJDBC", true, "org.xmlBlaster.util.queue.jdbc.JDBCQueueCommonTablePlugin");
         config.addAttribute("url", "jdbc:oracle:thin:@localhost:1521:noty");
         config.addAttribute("user", "joe");
         config.addAttribute("password", "secret");
         config.addAttribute("connectionBusyTimeout", "90000");
         config.addAttribute("maxWaitingThreads", "300");
         RunLevelAction action = new RunLevelAction(this.glob, "LOAD", 3, -1, ErrorCode.toErrorCode("internal.unknown"), 5);
         config.addAction(action);
         action = new RunLevelAction(this.glob, "STOP", -1, 2, null, 4);
         config.addAction(action);
     
         String xml = config.toXml();
         this.log.info(me, xml);
     
         PluginConfigSaxFactory factory = new PluginConfigSaxFactory(this.glob);
         config = factory.readObject(xml);
         RunLevelAction[] actions = config.getActions();
         assertEquals(me + " number of actions", 2, actions.length);
      }
      catch (XmlBlasterException e) {
         fail(ME + " failed: " + e.toString());
      }
      this.log.info(me, "successfully ended");
   }

   private MsgQosData getQosData(String attrVal) throws XmlBlasterException {
      PluginConfigSaxFactory factory = new PluginConfigSaxFactory(this.glob);
      String xml = "<plugin id='FilePollerPlugin' className='org.xmlBlaster.client.filepoller.FilePollerPlugin'>\n" +
                   "  <attribute id='qosTest'>" + attrVal + "</attribute>\n" + 
                   "  <action do='LOAD' onStartupRunlevel='9' sequence='6' onFail='resource.configuration.pluginFailed'/>\n" +
                   "  <action do='STOP' onShutdownRunlevel='6' sequence='5'/>\n" + 
                   "</plugin>\n";
      PluginConfig config = factory.readObject(xml);
      Properties prop = config.getPluginInfo().getParameters();
      String txt = prop.getProperty("qosTest", null);
      if (txt == null) {
         prop.list(System.err);
         assertTrue("the qosTest is null when it should not", false);
      }
      MsgQosSaxFactory msgFactory = new MsgQosSaxFactory(this.glob);
      return msgFactory.readObject(txt);
   }

   public void testPluginConfigParser() {
      String me = ME + "-testPluginConfigParser";
      this.log.info(ME, "start");
      String xml = "<![CDATA[<qos><expiration lifeTime='4000'/></qos>]]>";
      try {
         MsgQosData data = getQosData(xml);
         assertEquals("Wrong lifetime", 4000L, data.getLifeTime()); 
      }
      catch (XmlBlasterException e) {
         assertTrue(ME + " parsing failed for '" + xml + "'", false);
      }
      /*
      xml = "&lt;![CDATA[<qos><expiration lifeTime='4000'/></qos>]]&gt;";
      try {
         MsgQosData data = getQosData(xml);
         assertEquals("Wrong lifetime", 4000L, data.getLifeTime()); 
      }
      catch (XmlBlasterException e) {
         assertTrue(ME + " parsing failed for '" + xml + "'", false);
      }
      */

      xml = "<qos><expiration lifeTime='4000'/></qos>";
      try {
         MsgQosData data = getQosData(xml);
         assertEquals("Wrong lifetime", 4000L, data.getLifeTime()); 
      }
      catch (XmlBlasterException e) {
         assertTrue(ME + " parsing failed for '" + xml + "'", false);
      }

      xml = "<qos><![CDATA[<expiration lifeTime='4000'/>]]></qos>";
      try {
         MsgQosData data = getQosData(xml);
         // unless you change the parsing in MsgQosData
         assertEquals("Wrong lifetime", -1L, data.getLifeTime()); 
      }
      catch (XmlBlasterException e) {
         assertTrue(ME + " parsing failed for '" + xml + "'", false);
      }
      
   }


   public void testPluginHolder() {
      String me = ME + "-testPluginHolder";
      try {
         this.log.info(me, "start");

         PluginHolder holder = new PluginHolder(this.glob);
         
         PluginConfig tmp = new PluginConfig(this.glob, "queueJDBC", true, "org.xmlBlaster.util.queue.jdbc.JDBCQueueCommonTablePlugin");
         holder.addDefaultPluginConfig(tmp);
         tmp = new PluginConfig(this.glob, "queueRAM", true, "org.xmlBlaster.util.queue.ram.RAMQueuePlugin");
         holder.addPluginConfig("avalon", tmp);

         tmp = holder.getPluginConfig("avalon", "queueRAM");
         if (tmp == null) assertTrue(me + " getting 'avalon queueRAM'", false);
         this.log.info(me, tmp.toXml());

         tmp = holder.getPluginConfig("avalon", "queueJDBC");
         if (tmp == null) assertTrue(me + " getting 'avalon queueJDBC'", false);
         this.log.info(me, tmp.toXml());

         PluginConfig[] help = holder.getAllPluginConfig("avalon");
         assertEquals(me + " get all plugins for avalon", 2, help.length);


         String xml = new String();
         xml += "<xmlBlaster>\n" +
                "   <!-- A typical plugin which is loaded by client request -->\n" +
                "   <plugin id='dispatchPriority'\n" +
                "           className='org.xmlBlaster.util.dispatch.plugins.prio.PriorizedDispatchPlugin'\n" +
                "           jar='/tmp/my.jar'>\n" +
                "      <attribute id='config'>\n" +
                "         <![CDATA[\n" +
                "         <msgDispatch defaultStatus='64k' defaultAction='send'>\n" +
                "         <onStatus oid='_bandwidth.status' content='64k' defaultAction='destroy'>\n" +
                "            <action do='send'  ifPriority='7-9'/>\n" +
                "            <action do='queue'  ifPriority='2-6'/>\n" +
                "         </onStatus>\n" +
                "         <onStatus oid='_bandwidth.status' content='2M'>\n" +
                "            <action do='send'  ifPriority='0-9'/>\n" +
                "         </onStatus>\n" +
                "         </msgDispatch>\n" +
                "         ]]>\n" +
                "      </attribute>\n" +
                "   </plugin>\n" +
                "\n" +
                "   <plugin id='queueCACHE' className='org.xmlBlaster.util.queue.cache.CacheQueueInterceptorPlugin'>\n" +
                "      <attribute id='transientQueue'>queueRAM</attribute>\n" +
                "      <attribute id='persistentQueue'>queueJDBC</attribute>\n" +
                "   </plugin>\n" +
                "   \n" +
                "  <plugin id='queueRAM' className='org.xmlBlaster.util.queue.ram.RamQueuePlugin'/>\n" +
                "\n" +
                "   <plugin id='storage:CACHE' className='org.xmlBlaster.engine.msgstore.cache.PersistenceCachePlugin'>\n" +
                "      <attribute id='transientQueue'>storage:RAM</attribute>\n" +
                "      <attribute id='persistentQueue'>storage:JDBC</attribute>\n" +
                "   </plugin>\n" +
                "   \n" +
                "   <plugin id='storage:RAM' className='org.xmlBlaster.engine.msgstore.ram.MapPlugin'/>\n" +
                "   \n" +
                "   <!-- and here the declarations which are specific to the given nodes -->\n" +
                "   <node id='heron'>\n" +
                "      <plugin id='protocol:SOCKET:admin' \n" +
                "              className='org.xmlBlaster.protocol.socket.SocketDriver'>\n" +
                "         <attribute id='port'>69000</attribute>\n" +
                "      </plugin>\n" +
                "     \n" +
                "      <!-- /node/heron/plugin/protocol:SOCKET:users/attribute/port=6901 -->\n" +
                "      <!-- /node/heron/plugin/protocol:SOCKET:users/action/LOAD/onStartupRunlevel=3 -->\n" +
                "      <!-- /node/heron/plugin/protocol:SOCKET:users/action/LOAD/sequence=5 -->\n" +
                "      <plugin id='protocol:SOCKET:users' className='org.xmlBlaster.protocol.socket.SocketDriver'>\n" +
                "         <attribute id='port'>6901</attribute>\n" +
                "         <action do='LOAD' onStartupRunlevel='3' sequence='5' onFail='resource.configuration.pluginFailed'/>\n" +
                "         <action do='STOP' onShutdownRunlevel='2' sequence='4'/>\n" +
                "      </plugin>\n" +
                "     \n" +
                "      <plugin id='queueJDBC' className='org.xmlBlaster.util.queue.jdbc.JDBCQueueCommonTablePlugin'>\n" +
                "         <attribute id='url'>jdbc:oracle:thin:@localhost:1521:noty</attribute>\n" +
                "         <attribute id='user'>joe</attribute>\n" +
                "         <attribute id='password'>secret</attribute>\n" +
                "         <attribute id='connectionBusyTimeout'>90000</attribute>\n" +
                "         <attribute id='maxWaitingThreads'>300</attribute>\n" +
                "      </plugin>\n" +
                "     \n" +
                "      <plugin id='storage:JDBC' className='org.xmlBlaster.engine.msgstore.cache.PersistenceCachePlugin'>\n" +
                "         <attribute id='url'>jdbc:oracle:thin:@localhost:1521:noty</attribute>\n" +
                "         <attribute id='user'>joe</attribute>\n" +
                "         <attribute id='password'>secret</attribute>\n" +
                "         <attribute id='connectionBusyTimeout'>90000</attribute>\n" +
                "         <attribute id='maxWaitingThreads'>300</attribute>\n" +
                "      </plugin>\n" +
                "    </node> <!-- heron -->\n" +
                " \n" +
                "    <node id='avalon'>\n" +
                "       ...\n" +
                "      <plugin id='queueJDBC' className='org.xmlBlaster.util.queue.jdbc.JDBCQueueCommonTablePlugin'>\n" +
                "         <attribute id='url'>jdbc:oracle:thin:@localhost:1521:noty</attribute>\n" +
                "         <attribute id='user'>joe</attribute>\n" +
                "         <attribute id='password'>secret</attribute>\n" +
                "         <attribute id='connectionBusyTimeout'>90000</attribute>\n" +
                "         <attribute id='maxWaitingThreads'>300</attribute>\n" +
                "        <attribute id='tableNamePrefix'>AVALON_</attribute>\n" +
                "      </plugin>\n" +
                "      ...\n" +
                "    </node>\n" +
                "</xmlBlaster>\n";  

         PluginHolderSaxFactory factory = new PluginHolderSaxFactory(this.glob);
         PluginHolder pluginHolder = null;
         for (int i=0; i < 2; i++) {
            this.log.info(me, "looping through the loop. sweep '" + i + "'");
            pluginHolder = factory.readObject(xml);
            PluginConfig[] plugins = pluginHolder.getAllPluginConfig("avalon");
            assertEquals(me + " number of plugins for 'avalon' in plugin holder", 6, plugins.length);

            PluginConfig pluginConfig = null;
            pluginConfig = pluginHolder.getPluginConfig("avalon","dispatchPriority");
            if (pluginConfig == null) 
               assertTrue(me + " getting plugin 'dispatchPriority' for avalon gives null", false);
            String id = pluginConfig.getId();
            assertEquals(me + " id for avalon/dispatchPriority", "dispatchPriority", id);
            String className = pluginConfig.getClassName();
            assertEquals(me + " className for avalon/dispatchPriority", "org.xmlBlaster.util.dispatch.plugins.prio.PriorizedDispatchPlugin", className);

            pluginConfig = pluginHolder.getPluginConfig("avalon","queueCACHE");
            pluginConfig = pluginHolder.getPluginConfig("avalon","queueRAM");
            pluginConfig = pluginHolder.getPluginConfig("avalon","queueJDBC");
            pluginConfig = pluginHolder.getPluginConfig("avalon","storage:CACHE");
            pluginConfig = pluginHolder.getPluginConfig("avalon","storage:RAM");

            //should not exist
            pluginConfig = pluginHolder.getPluginConfig("avalon","storage:JDBC");

            //should be the individual of heron (not from xmlBlaster)
            pluginConfig = pluginHolder.getPluginConfig("heron","queueJDBC");

            xml = pluginHolder.toXml();
            this.log.info(ME, xml);
         }
      }
      catch (XmlBlasterException e) {
         fail(me + " failed: " + e.toString());
      }
      this.log.info(me, "successfully ended");
   }


   public void testPluginConfigComparator() {
      String me = ME + "-testPluginConfigConparator";
      this.log.info(me, "start");

      PluginConfigComparator upComparator = new PluginConfigComparator(this.glob, true);
      PluginConfigComparator downComparator = new PluginConfigComparator(this.glob, false);

      PluginConfig config1 = new PluginConfig(this.glob, "test:PLUGIN1", true, "org.universe.Plugin1");
      RunLevelAction action = new RunLevelAction(this.glob, "LOAD", 3, -1, null, 5);
      config1.addAction(action);
      action = new RunLevelAction(this.glob, "STOP", -1, 2, null, 4);
      config1.addAction(action);
     
      PluginConfig config2 = new PluginConfig(this.glob, "test:PLUGIN2", true, "org.universe.Plugin2");
      action = new RunLevelAction(this.glob, "LOAD", 3, -1, null, 5);
      config2.addAction(action);
      action = new RunLevelAction(this.glob, "STOP", -1, 2, null, 4);
      config2.addAction(action);
     
      int cmp = upComparator.compare(config1, config2);
      assertTrue(me + " number of actions", cmp < 0);

      cmp = downComparator.compare(config1, config2);
      assertTrue(me + " number of actions", cmp > 0);

      PluginConfig config3 = new PluginConfig(this.glob, "test:PLUGIN3", true, "org.universe.Plugin3");
      action = new RunLevelAction(this.glob, "LOAD", 2, -1, null, 3);
      config3.addAction(action);
      action = new RunLevelAction(this.glob, "STOP", -1, 1, null, 3);
      config3.addAction(action);
      cmp = upComparator.compare(config1, config3);
      assertTrue(me + " number of actions", cmp > 0);
      cmp = downComparator.compare(config1, config3);
      assertTrue(me + " number of actions", cmp < 0);

      PluginConfig config4 = new PluginConfig(this.glob, "test:PLUGIN4", true, "org.universe.Plugin4");
      action = new RunLevelAction(this.glob, "LOAD", 2, -1, null, 4);
      config4.addAction(action);
      action = new RunLevelAction(this.glob, "STOP", -1, 1, null, 4);
      config4.addAction(action);
      cmp = upComparator.compare(config3, config4);
      assertTrue(me + " number of actions", cmp < 0);
      cmp = downComparator.compare(config3, config4);
      assertTrue(me + " number of actions", cmp < 0);

      try {
         cmp = upComparator.compare(config3, (PluginConfig)null);
         assertTrue(me + " number of actions", true);
      }
      catch (ClassCastException ex) {
         this.log.info(me, "exception is OK and expected in this context");
      }
      try {
         cmp = upComparator.compare(config3, (PluginConfig)null);
         assertTrue(me + " number of actions", true);
      }
      catch (ClassCastException ex) {
         this.log.info(me, "exception is OK and expected in this context");
      }
      try {
         cmp = upComparator.compare((PluginConfig)null, config4);
         assertTrue(me + " number of actions", true);
      }
      catch (ClassCastException ex) {
         this.log.info(me, "exception is OK and expected in this context");
      }
      try {
         cmp = downComparator.compare((PluginConfig)null, config4);
         assertTrue(me + " number of actions", true);
      }
      catch (ClassCastException ex) {
         this.log.info(me, "exception is OK and expected in this context");
      }

      this.log.info(me, "successfully ended");
   }



   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.RunLevelTest
    * </pre>
    */
   public static void main(String args[])
   {
      RunLevelTest testSub = new RunLevelTest("RunLevelTest", args);

      testSub.setUp();
      testSub.testAction();
      testSub.tearDown();

      testSub.setUp();
      testSub.testPluginConfig();
      testSub.tearDown();

      testSub.setUp();
      testSub.testPluginConfigParser();
      testSub.tearDown();

      testSub.setUp();
      testSub.testPluginHolder();
      testSub.tearDown();

      testSub.setUp();
      testSub.testPluginConfigComparator();
      testSub.tearDown();

   }
}
