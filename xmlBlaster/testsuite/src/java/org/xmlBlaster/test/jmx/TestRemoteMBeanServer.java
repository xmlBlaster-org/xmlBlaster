package org.xmlBlaster.test.jmx;

import junit.framework.*;

import java.util.*;
import javax.management.*;
import org.xmlBlaster.client.jmx.AsyncMBeanServer;
import org.xmlBlaster.client.jmx.ConnectorFactory;
import org.xmlBlaster.client.jmx.*;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import java.rmi.*;

public class TestRemoteMBeanServer extends TestCase{

  private final static String ME = "TestRemoteMBeanServer";
  private Global glob = null;
  private LogChannel log = null;
  protected ObjectName RequestBrokerName;
  protected AsyncMBeanServer server;
  ObjectName JmxLogChannelName;
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
    this.log = this.glob.getLog("test");
    try {
      log.info(ME,"setUp of TestRemoteMBeanServer...");
      JmxLogChannelName = new ObjectName("xmlBlaster:name=JmxLogChannel");
      RequestBrokerName = new ObjectName("xmlBlaster:name=requestBroker");
   }
    catch (MalformedObjectNameException ex) {
      assertTrue("Object not found!! " + ex.toString(), false);
      log.error(ME,"Object not found!! " + ex.toString());
      ex.printStackTrace();
    }
    catch (Exception ex) {
    assertTrue("Error creating ObjectName!! " + ex.toString(), false);
    log.error(ME,"Error creating ObjectName!! " + ex.toString());
    ex.printStackTrace();
    }
  }

  protected void tearDown() {
    log.info(ME,"shutting down server...");
    server.close();
  }

  public void testRemoteMBeanServer() {
    log.info(ME,"creating server on localhost");
    try {
      server = ConnectorFactory.createAsyncConnector("xmlblaster", "localhost");
    }
    catch (ConnectorException ex) {
      assertTrue("Error connecting to server! " + ex.toString(), false);
    }
    testCreateMBean();
    Callback cb2 = server.getObjectInstance(JmxLogChannelName);
    log.info(ME, "Playing around with Bean...");
    testInvoke();
    boolean registered = testIsRegistered();
    testGetAttribute();
    testGetMBeanInfo();
    testGetMBeanCount();

    testUnregisterMBean(registered);


  }

  private boolean testIsRegistered() {
    log.info(ME,"Is JmxLogChannelMBean still registered?");
    boolean registered = false;
    try {
      registered  = ( (Boolean) server.isRegistered(JmxLogChannelName).get()).booleanValue();
    }
    catch (Exception ex) {
      log.error(ME,"Error when checking for JmxLogChannel! " + ex.toString());
      assertTrue("Error when checking for JmxLogChannel " + ex.toString(), false);
    }
    if (registered) log.info(ME,"success, JmxLogChannel still registered");
    else {
      log.error(ME,"JmxLogChannel no longer registered!");
      assertTrue("JmxLogChannel no longer registered!", false);
    }
    return registered;
  }

  private void testInvoke() {
    log.info(ME, "Invoking addDumpLevel on JmxLogChannelMBean");
    try {
      server.invoke(JmxLogChannelName, "addDumpLevel", null,null);
    }
    catch (Exception ex) {
      log.error(ME,"Error when invoking addDumpLevel on JmxLogChannelMBean! " + ex.toString());
      assertTrue("Error when invoking addDumpLevel on JmxLogChannelMBean! " + ex.toString(), false);
    }
  }

  private void testCreateMBean() {
    log.info(ME,"creating MBean org.xmlBlaster.util.admin.extern.JmxLogChannel");
    server.createMBean("org.xmlBlaster.util.admin.extern.JmxLogChannel",JmxLogChannelName);
    Callback cb = server.getDefaultDomain();
    if (cb==null) {
      assertTrue("Error when receiving callback...", false);
      log.warn(ME, "Error when receiving callback...");
    }
  }

  private void testGetMBeanCount() {
    log.info(ME,"counting MBeans on server");
    try {
      server.getMBeanCount().get();
    }
    catch (RemoteException ex) {
      log.error(ME,"Error when counting MBeans on server! " + ex.toString());
      assertTrue("Error when counting MBeans on server! " + ex.toString(), false);
    }
  }

  private void testGetAttribute() {
    log.info(ME,"Reading attribute");
    try {
      server.getAttribute(JmxLogChannelName, "LogText");
    }
    catch (Exception ex) {
      log.error(ME,"Attribute not found!");
      assertTrue("Attribute not found!", false);
    }
  }

  private void testUnregisterMBean(boolean registered) {
    log.info(ME,"Unregistering MBean");
    try {
      server.unregisterMBean(JmxLogChannelName);
    }
    catch (Exception ex) {
      log.error(ME,"Error when unregistering JmxLogChannel! " + ex.toString());
      assertTrue("Error when unregistering JmxLogChannel " + ex.toString(), false);

    }

    log.info(ME,"Is JmxLogChannelMBean still registered?");

    try {
      registered  = ( (Boolean) server.isRegistered(JmxLogChannelName).get()).booleanValue();
    }
    catch (Exception ex) {
      log.error(ME,"Error when checking for JmxLogChannel! " + ex.toString());
      assertTrue("Error when checking for JmxLogChannel " + ex.toString(), false);
    }
    if (!(registered)) log.info(ME,"success, JmxLogChannel removed");
    else {
      log.error(ME,"JmxLogChannel not removed!");
      assertTrue("JmxLogChannel not removed", false);
    }
  }

  private void testGetMBeanInfo() {
    log.info(ME,"Reading MBeanInfo..");
    MBeanInfo info = null;
    try {
      info = (MBeanInfo) server.getMBeanInfo(JmxLogChannelName).get();
    }
    catch (Exception ex) {
      log.error(ME,"MBeanInfo not found!");
      assertTrue("MBeanInfo not found!", false);
    }
    if (info==null) {
      log.error(ME,"MBeanInfo not found!");
      assertTrue("MBeanInfo not found!", false);
    }
  }
}