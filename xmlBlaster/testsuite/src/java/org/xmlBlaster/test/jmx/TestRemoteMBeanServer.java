package org.xmlBlaster.test.jmx;

import junit.framework.*;

import javax.management.*;
import org.xmlBlaster.client.jmx.AsyncMBeanServer;
import org.xmlBlaster.client.jmx.ConnectorFactory;
import org.xmlBlaster.client.jmx.*;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import java.rmi.*;

public class TestRemoteMBeanServer extends TestCase{

  private final static String ME = "TestRemoteMBeanServer";
  private Global glob = null;
   private static Logger log = Logger.getLogger(TestRemoteMBeanServer.class.getName());
  protected ObjectName RequestBrokerName;
  protected AsyncMBeanServer server;
  ObjectName JmxLoggerName;
  
   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestRemoteMBeanServer("testRemoteMBeanServer"));
       return suite;
   }


   public TestRemoteMBeanServer() {
       super(ME);
   }


  public TestRemoteMBeanServer(String testName)
   {
       super(testName);
   }

  public static void main(String[] args) {
    TestRemoteMBeanServer testRemoteMBeanServer1 = new TestRemoteMBeanServer();
    testRemoteMBeanServer1.setUp();
    testRemoteMBeanServer1.testRemoteMBeanServer();
    testRemoteMBeanServer1.tearDown();
  }

  protected void setUp() {
    this.glob = (this.glob == null) ? new Global() : this.glob;

    try {
      log.info("setUp of TestRemoteMBeanServer...");
      JmxLoggerName = new ObjectName("xmlBlaster:name=JmxLogChannel");
      RequestBrokerName = new ObjectName("xmlBlaster:name=requestBroker");
   }
    catch (MalformedObjectNameException ex) {
      assertTrue("Object not found!! " + ex.toString(), false);
      log.severe("Object not found!! " + ex.toString());
      ex.printStackTrace();
    }
    catch (Exception ex) {
    assertTrue("Error creating ObjectName!! " + ex.toString(), false);
    log.severe("Error creating ObjectName!! " + ex.toString());
    ex.printStackTrace();
    }
  }

  protected void tearDown() {
    log.info("shutting down server...");
    server.close();
  }

  public void testRemoteMBeanServer() {
    log.info("creating server on localhost");
    try {
      server = ConnectorFactory.getInstance(this.glob).getMBeanServer("localhost");
    }
    catch (ConnectorException ex) {
      assertTrue("Error connecting to server! " + ex.toString(), false);
    }
    testCreateMBean();
    Callback cb2 = server.getObjectInstance(JmxLoggerName);
    log.info("Playing around with Bean...");
    testInvoke();
    boolean registered = testIsRegistered();
    testGetAttribute();
    testGetMBeanInfo();
    testGetMBeanCount();

    testUnregisterMBean(registered);


  }

  private boolean testIsRegistered() {
    log.info("Is JmxLoggerMBean still registered?");
    boolean registered = false;
    try {
      registered  = ( (Boolean) server.isRegistered(JmxLoggerName).get()).booleanValue();
    }
    catch (Exception ex) {
      log.severe("Error when checking for JmxLogger! " + ex.toString());
      assertTrue("Error when checking for JmxLogger " + ex.toString(), false);
    }
    if (registered) log.info("success, JmxLogger still registered");
    else {
      log.severe("JmxLogger no longer registered!");
      assertTrue("JmxLogger no longer registered!", false);
    }
    return registered;
  }

  private void testInvoke() {
    log.info("Invoking addDumpLevel on JmxLoggerMBean");
    try {
      server.invoke(JmxLoggerName, "addDumpLevel", null,null);
    }
    catch (Exception ex) {
      log.severe("Error when invoking addDumpLevel on JmxLoggerMBean! " + ex.toString());
      assertTrue("Error when invoking addDumpLevel on JmxLoggerMBean! " + ex.toString(), false);
    }
  }

  private void testCreateMBean() {
    log.info("creating MBean org.xmlBlaster.util.admin.extern.JmxLogger");
    server.createMBean("org.xmlBlaster.util.admin.extern.JmxLogger", JmxLoggerName);
    Callback cb = server.getDefaultDomain();
    if (cb==null) {
      assertTrue("Error when receiving callback...", false);
      log.warning("Error when receiving callback...");
    }
  }

  private void testGetMBeanCount() {
    log.info("counting MBeans on server");
    try {
      server.getMBeanCount().get();
    }
    catch (RemoteException ex) {
      log.severe("Error when counting MBeans on server! " + ex.toString());
      assertTrue("Error when counting MBeans on server! " + ex.toString(), false);
    }
  }

  private void testGetAttribute() {
    log.info("Reading attribute");
    try {
      server.getAttribute(JmxLoggerName, "LogText");
    }
    catch (Exception ex) {
      log.severe("Attribute not found!");
      assertTrue("Attribute not found!", false);
    }
  }

  private void testUnregisterMBean(boolean registered) {
    log.info("Unregistering MBean");
    try {
      server.unregisterMBean(JmxLoggerName);
    }
    catch (Exception ex) {
      log.severe("Error when unregistering JmxLogger! " + ex.toString());
      assertTrue("Error when unregistering JmxLogger " + ex.toString(), false);

    }

    log.info("Is JmxLoggerMBean still registered?");

    try {
      registered  = ( (Boolean) server.isRegistered(JmxLoggerName).get()).booleanValue();
    }
    catch (Exception ex) {
      log.severe("Error when checking for JmxLogger! " + ex.toString());
      assertTrue("Error when checking for JmxLogger " + ex.toString(), false);
    }
    if (!(registered)) log.info("success, JmxLogger removed");
    else {
      log.severe("JmxLogger not removed!");
      assertTrue("JmxLogger not removed", false);
    }
  }

  private void testGetMBeanInfo() {
    log.info("Reading MBeanInfo..");
    MBeanInfo info = null;
    try {
      info = (MBeanInfo) server.getMBeanInfo(JmxLoggerName).get();
    }
    catch (Exception ex) {
      log.severe("MBeanInfo not found!");
      assertTrue("MBeanInfo not found!", false);
    }
    if (info==null) {
      log.severe("MBeanInfo not found!");
      assertTrue("MBeanInfo not found!", false);
    }
  }
}
