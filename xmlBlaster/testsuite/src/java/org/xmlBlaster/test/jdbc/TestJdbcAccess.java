/*------------------------------------------------------------------------------
Name:      TestJdbcAccess.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Test JDBC plugin
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.jdbc;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.GetKeyWrapper;
import org.xmlBlaster.client.GetQosWrapper;
import org.xmlBlaster.client.XmlDbMessageWrapper;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.test.Util;

import junit.framework.*;


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
 *    java junit.swingui.TestRunner org.xmlBlaster.test.jdbc.TestJdbcAccess
 * </pre>
 */
public class TestJdbcAccess extends TestCase
{
   private static String ME = "TestJdbcAccess";
   private final Global glob;
   private final LogChannel log;

   private XmlBlasterConnection con = null;
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
      this.log = glob.getLog(null);
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
      String[] args = {
         "-port",        // For all protocol we may use set an alternate server port
         "" + serverPort,
         "-socket.port",
         "" + (serverPort-1),
         "-rmi.registryPort",
         "" + (serverPort-2),
         "-xmlrpc.port",
         "" + (serverPort-3),
         "-client.port",
         "" + serverPort,
         "-ProtocolPlugin[IOR][1.0]",
         "org.xmlBlaster.protocol.corba.CorbaDriver",
         "-ProtocolPlugin[JDBC][1.0]",
         "org.xmlBlaster.protocol.jdbc.JdbcDriver",
         "-CbProtocolPlugin[IOR][1.0]",
         "org.xmlBlaster.protocol.corba.CallbackCorbaDriver",
         "-CbProtocolPlugin[JDBC][1.0]",
         "org.xmlBlaster.protocol.jdbc.CallbackJdbcDriver",
         "-JdbcDriver.drivers",
         "ORG.as220.tinySQL.dbfFileDriver",
      };
      glob.init(args);

      serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
      log.info(ME, "XmlBlaster is ready for testing JDBC access");

      try {
         log.info(ME, "Connecting ...");
         con = new XmlBlasterConnection(glob);
         ConnectQos qos = new ConnectQos(glob, name, passwd);
         con.connect(qos, null); // Login to xmlBlaster
      }
      catch (Exception e) {
         Thread.currentThread().dumpStack();
         log.error(ME, "Can't connect to xmlBlaster: " + e.toString());
      }

      wrap = new XmlDbMessageWrapper("joe", "secret", "jdbc:dbfFile:.");
      wrap.initUpdate(true, "DROP TABLE IF EXISTS cars");
      String result = invokeSyncQuery(wrap);
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
         String result = invokeSyncQuery(wrap);
         wrap = null;
      }

      con.disconnect(null);

      try { Thread.currentThread().sleep(100L); } catch( InterruptedException i) {}
      EmbeddedXmlBlaster.stopXmlBlaster(serverThread);

      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }

   /**
    * Create a RDBMS table, fill some data and destroy it again. 
    * We use the tinySQL dBase JDBC driver for testing.
    */
   public void testQueries()
   {
      log.info(ME, "######## Start testQueries()");
      wrap.initUpdate(true, "CREATE TABLE cars (name CHAR(25), id NUMERIC(4,0))");
      String result = invokeSyncQuery(wrap);
   
      String[] brands = { "Fiat", "Audi", "BMW", "Porsche", "Mercedes", "Renault", "Citroen" };
      for (int ii=0; ii<brands.length; ii++) {
         wrap.initUpdate(true, "INSERT INTO cars (name, id) VALUES('" + brands[ii] + "', " + (ii+1) + ")");
         result = invokeSyncQuery(wrap);
      }

      wrap.initQuery(100, true, "SELECT * from cars");
      result = invokeSyncQuery(wrap);
      log.info(ME, "Successful retrieved cars, dump ommitted to not disturb JUNIT test report generation");
      log.trace(ME, "Retrieved cars:\n" + result);
   }

   /**
    * get() blocks until the SQL query is finished ...
    */
   private String invokeSyncQuery(XmlDbMessageWrapper wrap) {
      try {
         log.info(ME, "Sending command string");
         log.trace(ME, "Sending command string:\n" + wrap.toXml()); // Junit report does not like it
         GetKeyWrapper key = new GetKeyWrapper("__sys__jdbc");
         key.wrap(wrap.toXml());
         GetQosWrapper qos = new GetQosWrapper();
         MessageUnit[] msgUnitArr = con.get(key.toXml(), qos.toXml());
         if (msgUnitArr.length > 0)
            log.plain(ME, new String(msgUnitArr[0].content));
         else
            log.info(ME, "No results for your query");
         return new String(msgUnitArr[0].content);
      }
      catch (Exception e) { 
         fail("Query failed: " + e.toString());
         return "";
      }
   }
}

