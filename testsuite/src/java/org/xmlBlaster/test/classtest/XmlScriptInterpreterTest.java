/*------------------------------------------------------------------------------
Name:      XmlScriptInterpreterTest.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.test.classtest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.XmlBlasterAccess;
import org.xmlBlaster.client.qos.*;
import org.xmlBlaster.client.script.XmlScriptClient;
import org.xmlBlaster.client.script.XmlScriptInterpreter;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * Test ClientProperty. 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.XmlScriptInterpreterTest
 * @see org.xmlBlaster.client.script.XmlScriptInterpreter
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.qos.xmlScript.html">The client.xmlScript requirement</a>
 */
public class XmlScriptInterpreterTest extends XMLTestCase {
   protected final static String ME = "XmlScriptInterpreterTest";
   protected Global glob;
   private static Logger log = Logger.getLogger(XmlScriptInterpreterTest.class.getName());
   protected XmlScriptInterpreter interpreter;
   private TestAccessor accessor;
   private ByteArrayOutputStream out;
   private HashMap attachments;

   
   public class TestAccessor extends XmlBlasterAccess {
      
      private boolean doRemoteCalls;
      private String key, qos;
      private byte[] content;
      
      public TestAccessor(Global global, boolean doRemoteCalls) {
         super(global);
         this.doRemoteCalls = doRemoteCalls;
      }
      
      public String getQos() {
         return this.qos;
      }
      
      public String getKey() {
         return this.key;
      }

      public byte[] getContent() {
         return this.content;
      }
      
      public ConnectReturnQos connect(ConnectQos qos, I_Callback callback) 
         throws XmlBlasterException {
         this.qos = qos.toXml();
         if (this.doRemoteCalls) return super.connect(qos, callback);
         return null;         
      }
      
      public SubscribeReturnQos subscribe(String key, String qos) 
         throws XmlBlasterException {
         this.qos = qos;
         this.key = key;
         log.fine("subscribe: " + key + " " + qos);
         if (this.doRemoteCalls) return super.subscribe(key, qos);
         return null;
      }

      public UnSubscribeReturnQos[] unSubscribe(String key, String qos) 
         throws XmlBlasterException {
         this.qos = qos;
         this.key = key;
         log.fine("unSubscribe: " + key + " " + qos);
         if (this.doRemoteCalls) return super.unSubscribe(key, qos);
         return null;
      }

      public PublishReturnQos publish(MsgUnit msgUnit) 
         throws XmlBlasterException {
         this.qos = msgUnit.getQos();
         this.key = msgUnit.getKey();
         this.content = msgUnit.getContent();
         log.fine("publish: " + key + " " + qos);
         if (this.doRemoteCalls) return super.publish(msgUnit);
         return null;
      }
   
      public PublishReturnQos[] publishArr(MsgUnit[] msgUnits) throws XmlBlasterException {
         // currently only store the first ones ...
         this.qos = msgUnits[0].getQos();
         this.key = msgUnits[0].getKey();
         this.content = msgUnits[0].getContent();
         log.fine("publishArr: " + key + " " + qos);
         if (this.doRemoteCalls) return super.publishArr(msgUnits);
         return null;
      }
      
      public MsgUnit[] get(String key, String qos) throws XmlBlasterException {
         this.qos = qos;
         this.key = key;
         log.fine("get: " + key + " " + qos);
         if (this.doRemoteCalls) return super.get(key, qos);
         return null;
      }

      public EraseReturnQos[] erase(String key, String qos) throws XmlBlasterException {
         this.qos = qos;
         this.key = key;
         log.fine("erase: " + key + " " + qos);
         if (this.doRemoteCalls) return super.erase(key, qos);
         return null;
      }

      public boolean disconnect(DisconnectQos qos) {
         this.qos = qos.toXml();
         log.fine("disconnect: " + key + " " + qos);
         if (this.doRemoteCalls) return super.disconnect(qos);
         return false;
      }
   }

   public XmlScriptInterpreterTest(String name) {
      this(new Global(), name);
   }

   public XmlScriptInterpreterTest(Global global, String name) {
      super(name);
      boolean doRemoteCalls = false;
      this.accessor = new TestAccessor(global, doRemoteCalls);
      this.out = new ByteArrayOutputStream();
      this.attachments = new HashMap();
      String contentRef = "QmxhQmxhQmxh"; // this is used in testPublishArr
      this.attachments.put("attachment1", contentRef);
      this.interpreter = new XmlScriptClient(global, this.accessor, out, out, this.attachments); 
      XMLUnit.setIgnoreWhitespace(true);
   }

   protected void setUp() {
      this.glob = Global.instance();

   }

   protected void testConnect() throws Exception {
      String tmp = "<xmlBlaster>\n" +
                   "  <connect>\n" + 
                   "      <qos>\n" +
                   "         <securityService type='htpasswd' version='1.0'>\n" +
                   "           <![CDATA[" +
                   "           <user>michele</user>" +
                   "           <passwd>secret</passwd>" +
                   "           ]]>" +
                   "         </securityService>\n" +
                   "         <session name='client/joe/3' timeout='3600000' maxSessions='10'" +
                   "                     clearSessions='false' reconnectSameClientOnly='false'/>\n" +
                   "         <ptp>true</ptp>\n" +
                   "         <duplicateUpdates>false</duplicateUpdates>\n" +
                   "         <queue relating='callback' maxEntries='1000' maxBytes='4000000'>\n" +
                   "            <callback type='IOR' sessionId='4e56890ghdFzj0' pingInterval='10000'\n" +
                   "                retries='-1' delay='10000' oneway='false' dispatchPlugin='undef'>\n" +
                   "               IOR:10000010033200000099000010....\n" +
                   "               <burstMode collectTime='400' />\n" +
                   "               <compress type='gzip' minSize='3000'/>\n" +
                   "               <ptp>true</ptp>\n" +
                   "            </callback>\n" +
                   "         </queue>\n" + 
                   "         <!-- a client specific property: here it could be the bean to invoke on updates -->\n" +
                   "         <clientProperty name='onMessageDefault'>beanName</clientProperty>\n" +
                   "      </qos>\n" +
                   "   </connect>\n" + 
                   "</xmlBlaster>\n";

      ByteArrayInputStream in = new ByteArrayInputStream(tmp.getBytes());
      this.interpreter.parse(new InputStreamReader(in));
      String qos = this.accessor.getQos();

      assertXpathExists("/qos/securityService[@type='htpasswd']", qos);
      assertXpathExists("/qos/session", qos);
      assertXpathExists("/qos/ptp", qos);
      assertXpathExists("/qos/duplicateUpdates", qos);
      assertXpathExists("/qos/queue/callback", qos);
      assertXpathExists("/qos/clientProperty[@name='onMessageDefault']", qos);
      assertXpathExists("/qos/duplicateUpdates", qos);
      assertXpathExists("/qos/duplicateUpdates", qos);
   }

   protected void testSubscribe() throws Exception {
      String qosRef = "" +	
                   "    <qos>\n" +
                   "      <subscribe id='_subId:1'/>\n" +
                   "      <erase forceDestroy='true'/>\n" +
                   "      <meta>false</meta>\n" +
                   "      <content>false</content>\n" +
                   "      <multiSubscribe>false</multiSubscribe>\n" +
                   "      <local>false</local>\n" +
                   "      <initialUpdate>false</initialUpdate>\n" +
                   "      <notify>false</notify>\n" +
                   "      <filter type='GnuRegexFilter' version='1.0'>^H.*$</filter>\n" +
                   "      <history numEntries='20'/>\n" +
                   "    </qos>\n";
      String keyRef = "    <key oid='' queryType='XPATH'> /xmlBlaster/key[starts-with(@oid,'radar.')] </key>\n";

      String cmd = "<xmlBlaster>\n  <subscribe>\n" + qosRef + keyRef + 
                   "  </subscribe>\n" +
                   "</xmlBlaster>\n";
      
      ByteArrayInputStream in = new ByteArrayInputStream(cmd.getBytes());
      this.interpreter.parse(new InputStreamReader(in));
      String qos = this.accessor.getQos();
      String key = this.accessor.getKey();

      log.info("testSubscribe: qos: '" + qos + "'");
      log.info("testSubscribe: key: '" + key + "'");
      assertXMLEqual(qosRef, qos);
      assertXMLEqual(keyRef, key);
   }

   protected void testPublish() throws Exception {
      String keyRef = "<key oid='MyMessageOid' contentMime='text/xml'><some><qos type='xxx'><content /></qos><key>xxx</key></some></key>";
      // String keyRef = "<key oid='MyMessageOid' contentMime='text/xml'><some><qos type='xxx'><content /></qos>xxx</some></key>";
      String qosRef = "<qos><priority>HIGH</priority></qos>";
      String contentRef = "<some><content>Hello World</content><qos></qos><key><key><qos><qos></qos></qos></key></key></some>";
      
      String cmd = "<xmlBlaster id='xxyyzz'><publish>" + keyRef + "<content>" + contentRef + "</content>" + 
                   qosRef + "</publish></xmlBlaster>\n";
      
      ByteArrayInputStream in = new ByteArrayInputStream(cmd.getBytes());

      this.out.reset();
      this.interpreter.parse(new InputStreamReader(in));
      String qos = this.accessor.getQos();
      String key = this.accessor.getKey();
      String content = new String(this.accessor.getContent()).trim();
      log.info("testPublish: qos: '" + qos + "' '" + qosRef + "'");
      log.info("testPublish: key: '" + key + "' '" + keyRef + "'");
      log.info("testPublish: content: '" + content + "' and should be '" + contentRef);
      assertXMLEqual(keyRef, key);
      assertXMLEqual(qosRef, qos);
      assertEquals(contentRef, content);   
      String response = new String(this.out.toByteArray());
      log.info("testPublish: response: '" + response + "'");
      assertXpathExists("/xmlBlasterResponse[@id='xxyyzz']", response);
   }

   protected void testPublishArr() throws Exception {
      String keyRef = "<key oid='MyMessageOid' contentMime='text/xml'/>";
      String qosRef = "<qos><priority>HIGH</priority></qos>";
      String contentRef = "QmxhQmxhQmxh"; // this is in the attachment
      String contentShould = "BlaBlaBla";
      
      String cmd = "<xmlBlaster><publishArr><message>" + keyRef + "<content link='attachment1' encoding='base64'>" + "</content>" + 
                   qosRef + "</message></publishArr></xmlBlaster>\n";
      
      ByteArrayInputStream in = new ByteArrayInputStream(cmd.getBytes());
      this.interpreter.parse(new InputStreamReader(in));
      String qos = this.accessor.getQos();
      String key = this.accessor.getKey();
      String content = new String(this.accessor.getContent()).trim();
      log.info("testPublishArr: qos: '" + qos + "' '" + qosRef + "'");
      log.info("testPublishArr: key: '" + key + "' '" + keyRef + "'");
      log.info("testPublishArr: content: '" + content + "'");

      assertXMLEqual(keyRef, key);
      assertXMLEqual(qosRef, qos);
      assertEquals(contentShould, content);   
   }

   protected void testDisconnect() throws Exception {
      String qosRef = "<qos><deleteSubjectQueue/><clearSessions>false</clearSessions></qos>";
      String cmd = "<xmlBlaster><disconnect>" + qosRef + "</disconnect></xmlBlaster>";
      
      ByteArrayInputStream in = new ByteArrayInputStream(cmd.getBytes());
      this.interpreter.parse(new InputStreamReader(in));
      String qos = this.accessor.getQos();
      log.info("testDisconnect: qos: '" + qos + "' '" + qosRef + "'");
      assertXMLEqual(qosRef, qos);
   }

   protected void testUnSubscribe() throws Exception {
      String qosRef = "<qos/>"; 
      String keyRef = "<key queryType='XPATH'>//key</key>";

      String cmd = "<xmlBlaster>\n  <unSubscribe>\n" + qosRef + keyRef + 
                   "  </unSubscribe>\n" +
                   "</xmlBlaster>\n";
      
      ByteArrayInputStream in = new ByteArrayInputStream(cmd.getBytes());
      this.interpreter.parse(new InputStreamReader(in));
      String qos = this.accessor.getQos();
      String key = this.accessor.getKey();

      log.info("testUnSubscribe: qos: '" + qos + "'");
      log.info("testUnSubscribe: key: '" + key + "'");
      assertXMLEqual(qosRef, qos);
      assertXMLEqual(keyRef, key);
   }


   protected void testErase() throws Exception {
      String qosRef = "<qos><erase forceDestroy='false'/></qos>"; 
      String keyRef = "<key oid='MyTopic'/>";

      String cmd = "<xmlBlaster>\n  <erase>\n" + qosRef + keyRef + 
                   "  </erase>\n" +
                   "</xmlBlaster>\n";
      
      ByteArrayInputStream in = new ByteArrayInputStream(cmd.getBytes());
      this.interpreter.parse(new InputStreamReader(in));
      String qos = this.accessor.getQos();
      String key = this.accessor.getKey();

      log.info("testErase: qos: '" + qos + "'");
      log.info("testErase: key: '" + key + "'");
      assertXMLEqual(qosRef, qos);
      assertXMLEqual(keyRef, key);
   }

   protected void testGet() throws Exception {
      String qosRef = "<qos><content>false</content>" +                      "<filter type='GnuRegexFilter' version='1.0'>^H.*$</filter>" +
                      "<history numEntries='20'/></qos>";      
      String keyRef = "<key oid='MyMessage' />";

      String cmd = "<xmlBlaster>\n  <get>\n" + qosRef + keyRef + 
                   "  </get>\n" +
                   "</xmlBlaster>\n";
      
      ByteArrayInputStream in = new ByteArrayInputStream(cmd.getBytes());
      this.interpreter.parse(new InputStreamReader(in));
      String qos = this.accessor.getQos();
      String key = this.accessor.getKey();

      log.info("testGet: qos: '" + qos + "'");
      log.info("testGet: key: '" + key + "'");
      assertXMLEqual(qosRef, qos);
      assertXMLEqual(keyRef, key);
   }




   /**
     * <pre>
     *  java org.xmlBlaster.test.classtest.XmlScriptInterpreterTest
     * </pre>
     */
    public static void main(String args[])
    {
       try {
          Global global = new Global(args);
          XmlScriptInterpreterTest test = new XmlScriptInterpreterTest(global, "XmlScriptInterpreterTest");
          test.setUp();
          test.testConnect();
          test.testSubscribe();
          test.testPublishArr();
          test.testDisconnect();
          test.testGet();
          test.testErase();
          test.testUnSubscribe();
          test.testPublish();
          // test.testWait();
          //testSub.tearDown();
       }
       catch(Throwable e) {
          e.printStackTrace();
          //fail(e.toString());
       }
    }
}
