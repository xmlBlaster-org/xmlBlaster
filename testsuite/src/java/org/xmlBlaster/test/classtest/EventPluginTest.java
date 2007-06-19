package org.xmlBlaster.test.classtest;

import java.io.FileWriter;
import java.util.logging.Logger;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.engine.EventPlugin;
import org.xmlBlaster.test.MsgInterceptor;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;

import junit.framework.TestCase;

/**
 * Test Timeout class (scheduling for timeouts). 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.EventPluginTest
 * @see org.xmlBlaster.util.Timeout
 */
public class EventPluginTest extends TestCase implements I_Callback {
   private static Logger log = Logger.getLogger(EventPluginTest.class.getName());

   private EmbeddedXmlBlaster embeddedServer;
   private MsgInterceptor msgInterceptor;
   
   public EventPluginTest(String name) {
      super(name);
      this.msgInterceptor = new MsgInterceptor(new Global(), log, null);
   }

   private void writePluginsFile(String port, String eventTypes) {
      try {
         String filename = System.getProperty("user.home") + "/tmp/xmlBlasterPluginsTest.xml";
         FileWriter file = new FileWriter(filename);
         file.append("<xmlBlaster>\n");
         file.append("<plugin create='true' id='SOCKET' className='org.xmlBlaster.protocol.socket.SocketDriver'>\n");
         file.append("   <action do='LOAD' onStartupRunlevel='4' sequence='20' \n");
         file.append("           onFail='resource.configuration.pluginFailed'/>\n");
         file.append("   <action do='STOP' onShutdownRunlevel='3' sequence='50'/>   \n");
         file.append("   <attribute id='port'>" + port + "</attribute>\n");
         file.append("   <attribute id='compress/type'></attribute>\n");
         file.append("</plugin>\n");
         file.append("<plugin create='true' id='coreEvents' className='org.xmlBlaster.engine.EventPlugin'>\n");
         file.append("   <action do='LOAD' onStartupRunlevel='8' sequence='4'/>\n");
         file.append("   <action do='STOP' onShutdownRunlevel='7' sequence='4'/>\n");
         file.append("   <attribute id='eventTypes'>\n");
         file.append(eventTypes);
         file.append("   </attribute>\n");
         file.append("   <attribute id='destination.publish'>\n");
         file.append("      publish.content=$_{xml}\n");
         file.append("   </attribute>\n");
         file.append("</plugin>\n");
         file.append("</xmlBlaster>\n");
         file.close();
      }
      catch (Exception ex) {
         ex.printStackTrace();
         fail(ex.getMessage());
      }
      
      
   }
   
   private void startServer() {
      String dir = System.getProperty("user.home") + "/tmp/";
      String[] args = new String[] {
            "-pluginsFile",
            dir + "/xmlBlasterPluginsTest.xml",
            /* "-propertyFile", */
            /* dir + "/xmlBlasterTest.properties", */
            "-admin.remoteconsole.port", "0",
            "-queue/history/maxEntriesCache", "10",
            "-queue/history/maxEntries", "10",
            "-queue/callback/maxEntriesCache", "10",
            "-queue/callback/maxEntries", "10",
            "-queue/subject/maxEntriesCache", "10",
            "-queue/subject/maxEntries", "10",
            "-xmlBlaster/jmx/HtmlAdaptor", "true"
      };
      this.embeddedServer = EmbeddedXmlBlaster.startXmlBlaster(args);
      log.info("The XmlBlaster Server has been started");
      if (this.embeddedServer.getMain().isHalted())
         fail("The xmlBlaster is not running");
   }


   private void stopServer() {
      final boolean sync = true; // shutting down and waiting
      if (this.embeddedServer != null)
         this.embeddedServer.stopServer(sync);
      this.embeddedServer = null;
      log.info("The XmlBlaster Server has been stopped");
   }


   /**
    * Test a simple timeout
    */
   public void testRegex() {
      // regex used: ".*/queue/.*/event/threshold.*"
      // positive tests
      String txt = "client/*/session/[publicSessionId]/queue/callback/event/threshold.90%";
      assertTrue("must be a callback queue plugin", EventPlugin.isQueueEvent(txt));
      txt = "client/[subjectId]/session/[publicSessionId]/queue/callback/event/threshold.90%";
      assertTrue("must be a specific callback queue plugin", EventPlugin.isQueueEvent(txt));
      txt = "topic/[topicId]/queue/history/event/threshold.90%";
      assertTrue("must be a topic queue plugin", EventPlugin.isQueueEvent(txt));
      // negative tests
      txt = "topic/[topicId]/quieue/history/event/threshold.90%"; // must fail
      assertFalse("must be a topic queue plugin", EventPlugin.isQueueEvent(txt));

      txt = "topic/[topicId]/quiue/history/event/threshold.90%"; // must fail
      assertFalse("must be a topic queue plugin", EventPlugin.isQueueEvent(txt));

      txt = "topic/[topicId]/queue/historyevent/threshold.90%"; // must fail
      assertFalse("must be a topic queue plugin", EventPlugin.isQueueEvent(txt));

      txt = "topicqueue/history/event/threshold"; // must fail
      assertFalse("must be a topic queue plugin", EventPlugin.isQueueEvent(txt));
      
      log.info("***EventPluginTest: testTimeout [SUCCESS]");
   }
   
   public void testQueueEvents() {
      try {
         String userName = "eventTester";
         String topicName = "eventTest";
         String sessionId = "1";
         String port = "7617";
         String eventTypes = "client/eventTester/session/1/queue/callback/event/threshold.70%,";
         eventTypes +="client/" + userName + "/session/" + sessionId + "/queue/callback/event/threshold.70%,";
         eventTypes +="topic/" + topicName + "/queue/history/event/threshold.4";
         writePluginsFile(port, eventTypes);
         startServer();
         String[] args = new String[] {
               "-dispatch/connection/plugin/socket/port", port,
               "-dispatch/connection/retries", "-1",
               "-dispatch/callback/retries", "-1"
               };
         {
            Global global = new Global(args);
            ConnectQos qos = new ConnectQos(global, userName + "/" + sessionId, "secret");
            I_XmlBlasterAccess conn = global.getXmlBlasterAccess();
            conn.connect(qos, this);
            SubscribeKey subKey = new SubscribeKey(global, topicName);
            SubscribeQos subQos = new SubscribeQos(global);
            conn.subscribe(subKey, subQos);
            conn.leaveServer(null);
         }

         {
            Global secondGlobal = new Global(args);
            ConnectQos qos = new ConnectQos(secondGlobal, "tester/1", "secret");
            I_XmlBlasterAccess conn2 = secondGlobal.getXmlBlasterAccess();
            conn2.connect(qos, this.msgInterceptor);
            SubscribeKey subKey = new SubscribeKey(secondGlobal, "__sys__Event");
            SubscribeQos subQos = new SubscribeQos(secondGlobal);
            conn2.subscribe(subKey, subQos);
         }
         
         this.msgInterceptor.clear();

         {
            // publish now
            Global global = new Global(args);
            ConnectQos qos = new ConnectQos(global, "testPublisher/1", "secret");
            I_XmlBlasterAccess conn = global.getXmlBlasterAccess();
            conn.connect(qos, this);
            PublishKey pubKey = new PublishKey(global, topicName);
            PublishQos pubQos = new PublishQos(global);
            for (int i=0; i < 5; i++) {
               String content = "This is test " + i;
               conn.publish(new MsgUnit(pubKey, content.getBytes(), pubQos));
            }
            
            int ret = this.msgInterceptor.waitOnUpdate(1000L, 1);
            assertEquals("We expected one message for the excess of the history queue", 1, ret);
            this.msgInterceptor.clear();
            for (int i=5; i < 8; i++) {
               String content = "This is test " + i;
               conn.publish(new MsgUnit(pubKey, content.getBytes(), pubQos));
            }
            ret = this.msgInterceptor.waitOnUpdate(1000L, 1);
            assertEquals("We expected one message for the excess of the callback queue", 1, ret);
            this.msgInterceptor.clear();
            conn.disconnect(new DisconnectQos(global));
         }

         {
            Global global = new Global(args);
            ConnectQos qos = new ConnectQos(global, userName + "/" + sessionId, "secret");
            I_XmlBlasterAccess conn = global.getXmlBlasterAccess();
            conn.connect(qos, this);
            Thread.sleep(1000L);
            conn.disconnect(new DisconnectQos(global));
         }
      }
      catch (Exception ex) {
         ex.printStackTrace();
         fail(ex.getMessage());
      }
      finally {
         stopServer();
      }
   }

   /**
    * here come the updates for the test client.
    * @param cbSessionId
    * @param updateKey
    * @param content
    * @param updateQos
    * @return
    * @throws XmlBlasterException
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      log.info(updateQos.toXml());
      return "OK";
   }
   
   
   public static void main(String[] args) {
      EventPluginTest test = new EventPluginTest("EventPluginTest");

      try {
         test.setUp();
         test.testRegex();
         test.tearDown();

         test.setUp();
         test.testQueueEvents();
         test.tearDown();
         
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }


}
