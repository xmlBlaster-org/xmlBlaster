/*------------------------------------------------------------------------------
Name:      JdbcDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   JdbcDriver class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: JdbcDriver.java,v 1.31 2002/08/12 13:31:48 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.jdbc;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.util.DisconnectQos;

import java.util.StringTokenizer;


/**
 * JDBC driver class using the native interface.
 * <p />
 * The jdbc driver needs to be registered in xmlBlaster.properties
 * and will be started on xmlBlaster startup, for example:
 * <pre>
 *   Protocol.Drivers=IOR:org.xmlBlaster.protocol.corba.CorbaDriver,\
 *                    RMI:org.xmlBlaster.protocol.rmi.RmiDriver,\
 *                    JDBC:org.xmlBlaster.protocol.jdbc.JdbcDriver
 *
 *   Protocol.CallbackDrivers=IOR:org.xmlBlaster.protocol.corba.CallbackCorbaDriver,\
 *                            RMI:org.xmlBlaster.protocol.rmi.CallbackRmiDriver,\
 *                            JDBC:org.xmlBlaster.protocol.jdbc.CallbackJdbcDriver
 * </pre>
 * The interface I_Driver is needed by xmlBlaster to instantiate and shutdown
 * this driver implementation.
 * @author ruff@swand.lake.de
 */
public class JdbcDriver implements I_Driver, I_Publish
{
   private String ME = "JdbcDriver";
   private Global glob = null;
   private LogChannel log = null;
   /** The singleton handle for this xmlBlaster server */
   private I_Authenticate authenticate = null;
   /** The singleton handle for this xmlBlaster server */
   private I_XmlBlaster xmlBlasterImpl = null;
   /** The authentication session identifier */
   private String sessionId = null;
   /** JDBC connection pooling, a pool for every user */
   private static NamedConnectionPool namedPool = null;
   /** key under which my callback is registered */
   private String cbRegistrationKey = null;

   private String loginName = null;
   private String passwd = null;

   /** Get a human readable name of this driver.
    * <p />
    * Enforced by interface I_Driver.
    */
   public String getName()
   {
      return ME;
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return "JDBC"
    */
   public String getProtocolId()
   {
      return "JDBC";
   }

   /**
    * Get the address how to access this driver. 
    * @return null
    */
   public String getRawAddress()
   {
      if (log.TRACE) log.trace(ME+".getRawAddress()", "No external access address available");
      return null;
   }

   /**
    * Start xmlBlaster jdbc access.
    * <p />
    * Enforced by interface I_Driver.
    * @param glob Global handle to access logging, property and commandline args
    */
   public void init(Global glob, I_Authenticate authenticate, I_XmlBlaster xmlBlasterImpl) throws XmlBlasterException
   {
      this.glob = glob;
      this.ME = "JdbcDriver" + this.glob.getLogPraefixDashed();
      this.log = glob.getLog("jdbc");
      this.glob.addObjectEntry("JdbcDriver-"+glob.getId(), this);
      this.authenticate = authenticate;
      this.xmlBlasterImpl = xmlBlasterImpl;
      this.namedPool = (NamedConnectionPool)this.glob.getObjectEntry("NamedConnectionPool-"+glob.getId());
      if (this.namedPool == null) {
         this.namedPool = new NamedConnectionPool(this.glob);
         this.glob.addObjectEntry("NamedConnectionPool-"+glob.getId(), this.namedPool);
      }

      initDrivers();

      if (log.TRACE) log.trace(ME, "Initialized successfully JDBC driver.");
   }

   /**
    * Activate xmlBlaster access through this protocol.
    */
   public synchronized void activate() throws XmlBlasterException {

      if (log.CALL) log.call(ME, "Entering activate");

      // login and get a session id ...
      loginName = glob.getProperty().get("JdbcDriver.loginName", "__sys__jdbc");
      passwd = glob.getProperty().get("JdbcDriver.password", "secret");

      if (loginName==null || passwd==null) {
         log.error(ME+"InvalidArguments", "login failed: please use no null arguments for connect()");
         throw new XmlBlasterException("LoginFailed.InvalidArguments", "login failed: please use no null arguments for connect()");
      }

      // "JDBC" below is the 'callback protocol type', which results in instantiation of the given class:
      CallbackAddress cbAddress = new CallbackAddress(glob, "JDBC");
      cbAddress.setAddress("org.xmlBlaster.protocol.jdbc.CallbackJdbcDriver");

      // Register the native callback driver
      CallbackJdbcDriver cbDriver = new CallbackJdbcDriver();
      cbDriver.init(glob, cbAddress);
      cbRegistrationKey = cbAddress.getType() + cbAddress.getAddress();
      glob.addNativeCallbackDriver(cbRegistrationKey, cbDriver); // tell that we are the callback driver as well

      ConnectQos connectQos = new ConnectQos(glob, cbAddress);
      connectQos.setSecurityPluginData("htpasswd", "1.0", loginName, passwd);
      connectQos.setSessionTimeout(0L);

      ConnectReturnQos returnQos = authenticate.connect(connectQos);
      sessionId = returnQos.getSessionId();

      log.info(ME, "Started successfully JDBC driver with loginName=" + loginName);
   }

   /**
    * Deactivate xmlBlaster access (standby), no clients can connect. 
    */
   public synchronized void deActivate() throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering deActivate");
      glob.removeNativeCallbackDriver(cbRegistrationKey);
   }

   /**
    * Instructs jdbc driver to shut down.
    * <p />
    * Enforced by interface I_Driver.
    */
   public void shutdown(boolean force)
   {
      try { authenticate.disconnect(sessionId, (new DisconnectQos()).toXml()); } catch(XmlBlasterException e) { }
      namedPool.destroy();
      log.info(ME, "JDBC service stopped, resources released.");
   }

   /**
    * Command line usage.
    * <p />
    * Enforced by interface I_Driver.
    */
   public String usage()
   {
      String text = "\n";
      text += "JdbcDriver options:\n";
      text += "   -JdbcDriver.password  The internal xmlBlaster-password for the JDBC driver.\n";
      text += "   -JdbcDriver.drivers   List of all jdbc drivers to initalize, e.g.\n";
      text += "                         oracle.jdbc.driver.OracleDriver,org.gjt.mm.mysql.Driver,postgresql.Driver.\n";
      text += "\n";
      return text;
   }


   /**
    * Callback of xmlBlaster, a client wants to do a query ...
    */
   public void update(String sender, byte[] content)
   {
      if (log.CALL) log.call(ME, "SQL message from '" + sender + "' received");
      XmlDBAdapterWorker worker = new XmlDBAdapterWorker(glob, sender, content, this, namedPool);
      worker.start();     // In future use callback thread !!!!!
   }


   /**
    * Send the XML based result set to the client.
    */
   public String publish(MessageUnit msgUnit) throws XmlBlasterException
   {
      return xmlBlasterImpl.publish(sessionId, msgUnit);
   }


   /**
    * Load the JDBC drivers from xmlBlaster.properties.
    * <p />
    * Default is JdbcDriver.drivers=sun.jdbc.odbc.JdbcOdbcDriver
    */
   private void initDrivers() {
      String            drivers = glob.getProperty().get("JdbcDriver.drivers", "sun.jdbc.odbc.JdbcOdbcDriver");
      StringTokenizer   st = new StringTokenizer(drivers, ",");
      int               numDrivers = st.countTokens();
      String            driver = "";

      for (int i = 0; i < numDrivers; i++) {
         try {
            driver = st.nextToken().trim();
            if (log.TRACE) log.trace(ME, "Trying JDBC driver Class.forName('" + driver + "') ...");
            Class cl = Class.forName(driver);
            java.sql.Driver dr = (java.sql.Driver)cl.newInstance();
            java.sql.DriverManager.registerDriver(dr);
            log.info(ME, "Jdbc driver '" + driver + "' loaded.");
         }
         catch (Throwable e) {
            log.warn(ME, "Couldn't initialize driver <" + driver + ">, please check your CLASSPATH");
         }
      }
      if (numDrivers == 0) {
         log.warn(ME, "No JDBC driver in xmlBlaster.properties given, set 'JdbcDriver.drivers' to point to your DB drivers if wanted, e.g. JdbcDriver.drivers=oracle.jdbc.driver.OracleDriver,org.gjt.mm.mysql.Driver,postgresql.Driver");
      }
   }
}
