/*------------------------------------------------------------------------------
Name:      TestJdbcAccess.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Test JDBC plugin
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.jdbc;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.XmlDbMessageWrapper;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.test.Util;

import junit.framework.*;

import java.util.Vector;


/**
 * This client tests the <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.service.rdbms.html">
 * JDBC plugin framework</a> with get() invocations. 
 * <p />
 * We start our own xmlBlaster server in a thread.
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.jdbc.TestJdbcAccess
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.jdbc.TestJdbcAccess
 * </pre>
 */
public class TestJdbcAccess extends TestCase
{
   private static String ME = "TestJdbcAccess";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestJdbcAccess.class.getName());

   private I_XmlBlasterAccess con = null;
   private String name;
   private String passwd = "secret";
   private EmbeddedXmlBlaster serverThread;
   private int serverPort = 7615;

   private XmlDbMessageWrapper wrap = null;

   /**
    * Constructs the TestJdbcAccess object. 
    * <p />
    * @param testName   The name used in the test suite
    */
   public TestJdbcAccess(String testName) {
      super(testName);
      this.glob = Global.instance();

      this.name = testName; // name to login to xmlBlaster
   }

   /**
    * Sets up the fixture.
    * <p />
    * We start an own xmlBlaster server in a separate thread,
    * it is configured to load the tinySQL JDBC driver to test SQL access (with dBase files)
    * <p />
    * Then we connect as a client
    */
   protected void setUp()
   {
      // We register here the demo plugin with xmlBlaster server, supplying an argument to the plugin
      Vector argsVec = Util.getOtherServerPortVec(serverPort);
      String tmp = glob.getProperty().get("JdbcDriver.drivers", (String)null);
      if (tmp == null || tmp.indexOf("ORG.as220.tinySQL.dbfFileDriver") < 0) {
         argsVec.add("-JdbcDriver.drivers");
         argsVec.add("ORG.as220.tinySQL.dbfFileDriver");
      }
      glob.init((String[])argsVec.toArray(new String[argsVec.size()]));

      serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
      log.info("XmlBlaster is ready for testing JDBC access");

      try {
         log.info("Connecting ...");
         con = glob.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(glob, name, passwd);
         con.connect(qos, null); // Login to xmlBlaster
      }
      catch (Exception e) {
         Thread.currentThread().dumpStack();
         log.severe("Can't connect to xmlBlaster: " + e.toString());
      }

      wrap = new XmlDbMessageWrapper(glob, "joe", "secret", "jdbc:dbfFile:.");
      wrap.initUpdate(true, "DROP TABLE IF EXISTS cars");
      String result = invokeSyncQuery(wrap, 1, null);
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      if (wrap != null) {
         wrap.initUpdate(true, "DROP TABLE IF EXISTS cars");
         String result = invokeSyncQuery(wrap, 1, null);
         wrap = null;
      }

      con.disconnect(null);
      con=null;

      try { Thread.sleep(100L); } catch( InterruptedException i) {}
      EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
      this.serverThread = null;


      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }

   /**
    * Create a RDBMS table, fill some data and destroy it again. 
    * We use the tinySQL dBase JDBC driver for testing.
    */
   public void testQueries()
   {
      log.info("######## Start testQueries()");
      String request = "CREATE TABLE cars (name CHAR(25), id NUMERIC(4,0))";
      log.info("*** REQUEST=" + request);
      wrap.initUpdate(true, request);
      String result = invokeSyncQuery(wrap, 1, null);
   
      String[] brands = { "Fiat", "Audi", "BMW", "Porsche", "Mercedes", "Renault", "Citroen" };
      for (int ii=0; ii<brands.length; ii++) {
         request = "INSERT INTO cars (name, id) VALUES('" + brands[ii] + "', " + (ii+1) + ")";
         log.info("*** REQUEST=" + request);
         wrap.initUpdate(true, request);
         result = invokeSyncQuery(wrap, 1, null);
      }

      request = "SELECT * from cars";
      log.info("*** REQUEST=" + request);
      wrap.initQuery(100, true, request);
      result = invokeSyncQuery(wrap, 1, "BMW");
      log.info("Successful retrieved cars, dump ommitted to not disturb JUNIT test report generation");
      log.fine("Retrieved cars:\n" + result);
   }

   /**
    * get() blocks until the SQL query is finished ...
    */
   private String invokeSyncQuery(XmlDbMessageWrapper wrap, int numResultRowsExpected, String token) {
      try {
         if (log.isLoggable(Level.FINE)) log.fine("Sending command string:\n" + wrap.toXml()); // Junit report does not like it
         GetKey key = new GetKey(glob, "__sys__jdbc");
         key.wrap(wrap.toXml());
         GetQos qos = new GetQos(glob);
         MsgUnit[] msgUnitArr = con.get(key.toXml(), qos.toXml());
         if (msgUnitArr.length > 0) {
            String result = new String(msgUnitArr[0].getContent());
            if (log.isLoggable(Level.FINE)) log.fine(result);
            if (token != null && result.indexOf(token) < 0)
               fail("Token " + token + " not found in result");
         }
         else {
            log.info("No results for your query");
         }
         assertEquals("Wrong number of results", numResultRowsExpected, msgUnitArr.length);
         return new String(msgUnitArr[0].getContent());
      }
      catch (Exception e) { 
         fail("Query failed: " + e.toString());
         return "";
      }
   }

   /**
    * Invoke: java org.xmlBlaster.test.jdbc.TestJdbcAccess
    * @deprecated Use the TestRunner from the testsuite to run it
    */
   public static void main(String args[]) {
      Global glob = Global.instance();
      if (glob.init(args) != 0) {
         System.err.println("Init failed");
         System.exit(1);
      }
      TestJdbcAccess test = new TestJdbcAccess("TestJdbcAccess");
      test.setUp();
      test.testQueries();
      test.tearDown();
   }
}

