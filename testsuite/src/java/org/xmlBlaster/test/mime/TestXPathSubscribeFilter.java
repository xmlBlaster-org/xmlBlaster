/*------------------------------------------------------------------------------
Name:      TestXPathSubscribeFilter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Test XPath filter.
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.mime;

import java.util.logging.Logger;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.test.Util;

import junit.framework.*;

import java.util.HashMap;

/**
 * This client does test of XPathFilter based queries.<br />
 * <p />
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner -noloading org.xmlBlaster.test.mime.TestXPathSubscribeFilter
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.mime.TestXPathSubscribeFilter
 * </pre>
 *
 * @author Peter Antman
 * @see org.xmlBlaster.engine.mime.xpath.XPathFilter
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/mime.plugin.access.xpath.html
 */
public class TestXPathSubscribeFilter extends TestCase implements I_Callback
{
   private static String ME = "Tim";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestXPathSubscribeFilter.class.getName());

   private I_XmlBlasterAccess con = null;
   private String name;
   private String passwd = "secret";
   private EmbeddedXmlBlaster serverThread;
   private int serverPort = 7624;

   private HashMap subscriberTable = new HashMap();
   private int[] subRec = new int[4];
   String subscribeOid;
   String subscribeOid2;
   String subscribeOid3;
   String subscribeOid4;
   /**
    * Constructs the TestXPathSubscribeFilter object.
    * <p />
    * @param testName   The name used in the test suite
    * @param name       The name to login to the xmlBlaster
    */
   public TestXPathSubscribeFilter(Global glob, String testName, String name)
   {
      super(testName);
      this.glob = glob;

      this.name = name;
   }

   /**
    * Sets up the fixture.
    * <p />
    * We start an own xmlBlaster server in a separate thread,
    * it has configured to load our XPath MIME filter plugin with extensions.
    * <p />
    * Then we connect as a client
    */
   protected void setUp()
   {
      String[] args = {
         "-bootstrapPort",        // For all protocol we may use set an alternate server port
         "" + serverPort,
         "-plugin/socket/port",
         "" + (serverPort-1),
         "-plugin/rmi/registryPort",
         "" + (serverPort-2),
         "-plugin/xmlrpc/port",
         "" + (serverPort-3),
         "-MimeAccessPlugin[XPathFilter][1.0]",
         "org.xmlBlaster.engine.mime.xpath.XPathFilter,engine.mime.xpath.extension_functions=:contains-ignore-case:org.xmlBlaster.engine.mime.xpath.ContainsIgnoreCaseFunction;:recursive-text:org.xmlBlaster.engine.mime.xpath.RecursiveTextFunction",
         //,classpath=jaxen.jar
         "-admin.remoteconsole.port",
         "0"
      };
      /*
,
         "-logging",
         "INFO"
      */
      glob.init(args);

      serverThread = EmbeddedXmlBlaster.startXmlBlaster(args);
      log.info("XmlBlaster is ready for testing subscribe MIME filter");

      try {
         log.info("Connecting ...");
         con = glob.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(glob, name, passwd);
         con.connect(qos, this); // Login to xmlBlaster
      }
      catch (Exception e) {
         Thread.dumpStack();
         log.severe("Can't connect to xmlBlaster: " + e.toString());
      }

      // Subscribe to a message with a supplied filter
      try {
         // One sport subscriber
         SubscribeQos qos = new SubscribeQos(glob);
         qos.addAccessFilter(new AccessFilterQos(glob, "XPathFilter", "1.0", "/news[@type='sport']"));
         
         subscribeOid = con.subscribe("<key oid='MSG'/>", qos.toXml()).getSubscriptionId();
         subscriberTable.put(subscribeOid, new Integer(0));
         log.info("Success: Subscribe subscription-id=" + subscribeOid + " done");
         // One culture subscriber
         qos = new SubscribeQos(glob);
         qos.addAccessFilter(new AccessFilterQos(glob, "XPathFilter", "1.0", "/news[@type='culture']"));
         
         subscribeOid2 = con.subscribe("<key oid='MSG'/>", qos.toXml()).getSubscriptionId();
         subscriberTable.put(subscribeOid2, new Integer(1));
         log.info("Success: Subscribe subscription-id2=" + subscribeOid2 + " done");

         // And one on another msg type but with the same xpath
         qos = new SubscribeQos(glob);
         qos.addAccessFilter(new AccessFilterQos(glob, "XPathFilter", "1.0", "/news[@type='sport' or @type='culture']"));
         
         
         subscribeOid3 = con.subscribe("<key oid='AnotherMsG'/>", qos.toXml()).getSubscriptionId();
         subscriberTable.put(subscribeOid3, new Integer(2));
         log.info("Success: Subscribe subscription-id3=" + subscribeOid3 + " done");

         // Ad with extention functions
         qos = new SubscribeQos(glob);
         qos.addAccessFilter(new AccessFilterQos(glob, "XPathFilter", "1.0", "/news[ contains-ignore-case( recursive-text(body), 'needle')]"));
         
         
         subscribeOid4 = con.subscribe("<key oid='AnotherMsG'/>", qos.toXml()).getSubscriptionId();
         subscriberTable.put(subscribeOid4, new Integer(3));
         log.info("Success: Subscribe subscription-id4=" + subscribeOid4 + " done");
         
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      log.info("TEST: tearing down");
      try { Thread.sleep(200L); } catch( InterruptedException i) {}   // Wait 200 milli seconds, until all updates are processed ...
      
      try {
         con.unSubscribe("<key oid='"+subscribeOid+"'/>",
                          "<qos/>");
         con.unSubscribe("<key oid='"+subscribeOid2+"'/>",
                          "<qos/>");
         con.unSubscribe("<key oid='"+subscribeOid3+"'/>",
                          "<qos/>");
         con.unSubscribe("<key oid='"+subscribeOid4+"'/>",
                          "<qos/>");
         EraseReturnQos[] arr = con.erase("<key oid='MSG'/>", "<qos/>");
         assertEquals("Erase", 1, arr.length);
         arr = con.erase("<key oid='AnotherMsG'/>", "<qos/>");
         assertEquals("Erase", 1, arr.length);
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }
      
      con.disconnect(null);
      con=null;
      
      try { Thread.sleep(500L); } catch( InterruptedException i) {}    // Wait some time
      EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
      this.serverThread = null;
      
      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }

   /**
    *
    *
    */
   public void testFilter()
   {
      log.info("testFilter() with XPath filter /news[@type='sport'] ...");

      log.info("TEST 1: Testing sport message");
      try {
         con.publish(new MsgUnit("<key oid='MSG' contentMime='text/xml'/>", "<news type='sport'></news>".getBytes(), null));
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
      waitOnUpdate(subscribeOid,4000L, 1);


      log.info("TEST 2: Testing culture message");
      try {
         con.publish(new MsgUnit("<key oid='MSG' contentMime='text/xml'/>", "<news type='culture'></news>".getBytes(), null));
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
      waitOnUpdate(subscribeOid2,4000L, 1);

      log.info("TEST 3: Testing AnotherMsG message");
      try {
         con.publish(new MsgUnit("<key oid='AnotherMsG' contentMime='text/xml'/>", "<news type='culture'></news>".getBytes(), null));
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
      waitOnUpdate(subscribeOid3,4000L, 1);
      
      log.info("TEST 4: Testing extention functions");
      try {
         con.publish(new MsgUnit("<key oid='AnotherMsG' contentMime='text/xml'/>", "<news><body><p>A little message</p><p>With a Needle in second paragraph wich normal XPath string function would not see</p></body></news>".getBytes(), null));
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
      waitOnUpdate(subscribeOid4,4000L, 1);
      /* See TestSubscribeFilter.java for this test
      log.info("TEST 4: Test what happens if the plugin throws an exception");
      try {   
         con.publish(new MsgUnit("<key oid='MSG'/>", "<broken><xml></broken>".getBytes(), null));
         waitOnUpdate(subscribeOid,4000L, 1); // a dead message should come if we would subscribe on it
      } catch(XmlBlasterException e) {
         fail("publish forced the plugin to throw an XmlBlasterException, but it should not reach the publisher: " + e.toString());
      }
      */
      
      log.info("Success in testFilter()");
   }
   
   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      log.info("Receiving update of a message " + updateKey.getOid() + " for subId: " + updateQos.getSubscriptionId() + " content=" + new String(content));
      int ii = ((Integer)subscriberTable.get(updateQos.getSubscriptionId())).intValue();
      log.fine("Got message " + new String(content));
      subRec[ii]++;
      return "";
   }
   
   /**
    * Little helper, waits until the wanted number of messages are arrived
    * or returns when the given timeout occurs.
    * <p />
    * @param timeout in milliseconds
    * @param numWait how many messages to wait
    */
   private void waitOnUpdate(String subId,final long timeout, final int numWait)
   {
      long pollingInterval = 50L;  // check every 0.05 seconds
      if (timeout < 50)  pollingInterval = timeout / 10L;
      long sum = 0L;
      int ii = ((Integer)subscriberTable.get(subId)).intValue();
      // check if too few are arriving
      while (subRec[ii] < numWait) {
         try { Thread.sleep(pollingInterval); } catch( InterruptedException i) {}
         sum += pollingInterval;
         assertTrue("Timeout of " + timeout + " occurred without update", sum <= timeout);
      }

      // check if too many are arriving
      try { Thread.sleep(timeout); } catch( InterruptedException i) {}
      assertEquals("Wrong number of messages arrived", numWait, subRec[ii]);
      log.info("Found correct rec messages for: " + subId);
      subRec[ii]= 0;
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestXPathSubscribeFilter(new Global(), "testFilter", "TestXPathSubscribeFilter"));
       return suite;
   }

   /**
    * Invoke: 
    * <pre>
    *   java org.xmlBlaster.test.mime.TestXPathSubscribeFilter
    *   java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.mime.TestXPathSubscribeFilter
    * <pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }
      TestXPathSubscribeFilter testSub = new TestXPathSubscribeFilter(glob, "TestXPathSubscribeFilter", "TestXPathSubscribeFilter");
      testSub.setUp();
      testSub.testFilter();
      testSub.tearDown();
   }
}

