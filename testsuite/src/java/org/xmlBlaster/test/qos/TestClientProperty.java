/*------------------------------------------------------------------------------
Name:      TestClientProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import java.util.Map;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.qos.ConnectQosSaxFactory;
import org.xmlBlaster.util.qos.DisconnectQosData;
import org.xmlBlaster.util.qos.DisconnectQosSaxFactory;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.qos.MsgQosSaxFactory;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.qos.QueryQosSaxFactory;
import org.xmlBlaster.util.qos.ClientProperty;

import junit.framework.*;


/**
 *
 *  * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.qos.TestClientProperty
 *    java junit.swingui.TestRunner org.xmlBlaster.test.qos.TestClientProperty
 * </pre>
 */
public class TestClientProperty extends TestCase implements I_Callback
{
   private static String ME = "TestClientProperty";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestClientProperty.class.getName());

   private boolean messageArrived = false;

   private I_XmlBlasterAccess senderConnection;

   /**
    * Constructs the TestClientProperty object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestClientProperty(Global glob, String name) {
      super(name);
      this.glob = glob;

   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp() {
   }


   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown() {
   }

   private void checkValues(Map map) {
//      for (Object key: map.keySet().toArray(new Object[map.size()])) {
//         log.info("Got Update Key = " + key.toString());
//      }
      int count = map.containsKey("__isInitialUpdate") ? 4 : 3;
      assertEquals("", count, map.size());
      assertEquals("", "oneValue", ((ClientProperty)map.get("oneKey")).getStringValue());
      assertEquals("", "twoValue", ((ClientProperty)map.get("twoKey")).getStringValue());
      assertEquals("", 55, ((ClientProperty)map.get("threeKey")).getIntValue());
   }


   public void testConnectQos()
   {
      if (log.isLoggable(Level.FINE)) log.fine("TestConnectQos");
      try {
         ConnectQos qos = new ConnectQos(this.glob);
         qos.addClientProperty("oneKey", "oneValue");
         qos.addClientProperty("twoKey", "twoValue");
         qos.addClientProperty("threeKey", new Integer(55));
         String literal = qos.toXml();
         
         ConnectQosSaxFactory factory = new ConnectQosSaxFactory(this.glob);
         ConnectQosData data = factory.readObject(literal);
         checkValues(data.getClientProperties());
      }
      catch (XmlBlasterException ex) {
         assertTrue("Exeption occured : " + ex.getMessage(), false);
      }
   }

   public void testDisconnectQos()
   {
      if (log.isLoggable(Level.FINE)) log.fine("TestDisconnectQos");
      DisconnectQos qos = new DisconnectQos(this.glob);
      qos.addClientProperty("oneKey", "oneValue");
      qos.addClientProperty("twoKey", "twoValue");
      qos.addClientProperty("threeKey", new Integer(55));
      String literal = qos.toXml();
      
      DisconnectQosSaxFactory factory = new DisconnectQosSaxFactory(this.glob);
      try {
         DisconnectQosData data = factory.readObject(literal);
         checkValues(data.getClientProperties());
      }
      catch (XmlBlasterException ex) {
         assertTrue("Exeption occured : " + ex.getMessage(), false);
      }
   }


   public void testPublishQos()
   {
      if (log.isLoggable(Level.FINE)) log.fine("TestPublishQos");
      PublishQos qos = new PublishQos(this.glob);
      qos.addClientProperty("oneKey", "oneValue");
      qos.addClientProperty("twoKey", "twoValue");
      qos.addClientProperty("threeKey", new Integer(55));
      String literal = qos.toXml();
      
      MsgQosSaxFactory factory = new MsgQosSaxFactory(this.glob);
      try {
         MsgQosData data = factory.readObject(literal);
         checkValues(data.getClientProperties());
      }
      catch (XmlBlasterException ex) {
         assertTrue("Exeption occured : " + ex.getMessage(), false);
      }
   }


   public void testSubscribeQos()
   {
      if (log.isLoggable(Level.FINE)) log.fine("TestSubscribeQos");
      SubscribeQos qos = new SubscribeQos(this.glob);
      qos.addClientProperty("oneKey", "oneValue");
      qos.addClientProperty("twoKey", "twoValue");
      qos.addClientProperty("threeKey", new Integer(55));
      String literal = qos.toXml();
      
      QueryQosSaxFactory factory = new QueryQosSaxFactory(this.glob);
      try {
         QueryQosData data = factory.readObject(literal);
         checkValues(data.getClientProperties());
      }
      catch (XmlBlasterException ex) {
         assertTrue("Exeption occured : " + ex.getMessage(), false);
      }
   }


   public void testUnSubscribeQos()
   {
      if (log.isLoggable(Level.FINE)) log.fine("TestUnSubscribeQos");
      UnSubscribeQos qos = new UnSubscribeQos(this.glob);
      qos.addClientProperty("oneKey", "oneValue");
      qos.addClientProperty("twoKey", "twoValue");
      qos.addClientProperty("threeKey", new Integer(55));
      String literal = qos.toXml();
      
      ConnectQosSaxFactory factory = new ConnectQosSaxFactory(this.glob);
      try {
         ConnectQosData data = factory.readObject(literal);
         checkValues(data.getClientProperties());
      }
      catch (XmlBlasterException ex) {
         assertTrue("Exeption occured : " + ex.getMessage(), false);
      }
   }


   public void testGetQos()
   {
      if (log.isLoggable(Level.FINE)) log.fine("TestGetQos");
      try {
         ConnectQos qos = new ConnectQos(this.glob);
         qos.addClientProperty("oneKey", "oneValue");
         qos.addClientProperty("twoKey", "twoValue");
         qos.addClientProperty("threeKey", new Integer(55));
         String literal = qos.toXml();
         
         QueryQosSaxFactory factory = new QueryQosSaxFactory(this.glob);
         QueryQosData data = factory.readObject(literal);
         checkValues(data.getClientProperties());
      }
      catch (XmlBlasterException ex) {
         assertTrue("Exeption occured : " + ex.getMessage(), false);
      }
   }

   public void testEraseQos()
   {
      if (log.isLoggable(Level.FINE)) log.fine("TestEraseQos");
      EraseQos qos = new EraseQos(this.glob);
      qos.addClientProperty("oneKey", "oneValue");
      qos.addClientProperty("twoKey", "twoValue");
      qos.addClientProperty("threeKey", new Integer(55));
      String literal = qos.toXml();
      
      QueryQosSaxFactory factory = new QueryQosSaxFactory(this.glob);
      try {
         QueryQosData data = factory.readObject(literal);
         checkValues(data.getClientProperties());
      }
      catch (XmlBlasterException ex) {
         assertTrue("Exeption occured : " + ex.getMessage(), false);
      }
   }

   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid is checked
    */
   public void testUpdateQos()
   {
      if (log.isLoggable(Level.FINE)) log.fine("Testing the update qos ...");

      try {
         senderConnection = glob.getXmlBlasterAccess(); // Find orb
         String passwd = "secret";
         ConnectQos connQos = new ConnectQos(glob, "clientProperty", passwd);
         if (log.isLoggable(Level.FINE)) log.fine("the connect qos is: " + connQos.toXml());
         senderConnection.connect(connQos, this); // Login to xmlBlaster

         // publish 
         PublishKey key = new PublishKey(this.glob, "clientProp");
         PublishQos qos = new PublishQos(this.glob);
         qos.addClientProperty("oneKey", "oneValue");
         qos.addClientProperty("twoKey", "twoValue");
         qos.addClientProperty("threeKey", new Integer(55));
         MsgUnit msg = new MsgUnit(key, "message".getBytes(), qos);
         senderConnection.publish(msg);

         // subscribe
         senderConnection.subscribe(new SubscribeKey(this.glob, "clientProp"), new SubscribeQos(this.glob));

         waitOnUpdate(10000);

         senderConnection.erase(new EraseKey(this.glob, "clientProperty"), new EraseQos(this.glob));
         senderConnection.disconnect(new DisconnectQos(this.glob));
         
      }
      catch (Exception e) {
          log.severe("Login failed: " + e.toString());
          e.printStackTrace();
          assertTrue("Login failed: " + e.toString(), false);
      }
   }



   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId_, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      log.info("Receiving update of message oid=" + updateKey.getOid() + "...");

      if (updateQos.isErased()) return "";

      assertEquals("Wrong sender", "clientProperty", updateQos.getSender().getLoginName());
      assertEquals("Wrong oid of message returned", "clientProp", updateKey.getOid());


      Map map = updateQos.getData().getClientProperties();

      this.checkValues(map);
      this.messageArrived = true;
      return "";
   }


   /**
    * Little helper, waits until the variable 'messageArrive' is set
    * to true, or returns when the given timeout occurs.
    * @param timeout in milliseconds
    */
   private void waitOnUpdate(final long timeout)
   {
      long pollingInterval = 50L;  // check every 0.05 seconds
      if (timeout < 50)  pollingInterval = timeout / 10L;
      long sum = 0L;
      while (!messageArrived) {
         try {
            Thread.sleep(pollingInterval);
         }
         catch( InterruptedException i)
         {}
         sum += pollingInterval;
         if (sum > timeout) {
            log.warning("Timeout of " + timeout + " occurred");
            break;
         }
      }
      assertTrue("The message never arrived", messageArrived);
      messageArrived = false;
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
      suite.addTest(new TestClientProperty(new Global(), "testConnectQos"));
      suite.addTest(new TestClientProperty(new Global(), "testDisconnectQos"));
      suite.addTest(new TestClientProperty(new Global(), "testPublishQos"));
      suite.addTest(new TestClientProperty(new Global(), "testSubscribeQos"));
      suite.addTest(new TestClientProperty(new Global(), "testUnSubscribeQos"));
      suite.addTest(new TestClientProperty(new Global(), "testGetQos"));
      suite.addTest(new TestClientProperty(new Global(), "testEraseQos"));
      suite.addTest(new TestClientProperty(new Global(), "testUpdateQos"));
       return suite;
   }


   /**
    * Invoke: java org.xmlBlaster.test.qos.TestClientProperty
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestClientProperty</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }
      TestClientProperty test = new TestClientProperty(glob, "testClientProperty");
      test.setUp();
      test.testConnectQos();
      test.tearDown();

      test.setUp();
      test.testDisconnectQos();
      test.tearDown();

      test.setUp();
      test.testPublishQos();
      test.tearDown();

      test.setUp();
      test.testSubscribeQos();
      test.tearDown();

      test.setUp();
      test.testUnSubscribeQos();
      test.tearDown();

      test.setUp();
      test.testGetQos();
      test.tearDown();

      test.setUp();
      test.testEraseQos();
      test.tearDown();

      test.setUp();
      test.testUpdateQos();
      test.tearDown();
   }
}

