/*------------------------------------------------------------------------------
Name:      ProtocolManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   ProtocolManager which loads protocol plugins
Version:   $Id: ProtocolManager.java,v 1.2 2002/06/15 16:47:09 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol;

import org.jutils.log.LogChannel;
import org.jutils.JUtilsException;

import org.xmlBlaster.engine.*;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.authentication.Authenticate;

import java.util.Vector;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.StringTokenizer;

/**
 * ProtocolManager loads the protocol plugins like CORBA/RMI/XmlRpc. 
 * <p />
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.html" target="others">protocol</a>
 */
public class ProtocolManager implements I_RunlevelListener
{
   private final String ME;
   private final Global glob;
   private final LogChannel log;

   /** Vector holding all protocol I_Driver.java implementations, e.g. CorbaDriver */
   private Vector protocols = new Vector();

   /** Vector holding all callback protocol I_CallbackDriver.java implementations, e.g. CallbackCorbaDriver */
   private Hashtable cbProtocolClasses = new Hashtable();

   public ProtocolManager(Global glob) {
      this.glob = glob;
      this.log = glob.getLog("protocol");
      this.ME = "ProtocolManager-" + this.glob.getId();
      if (log.CALL) log.call(ME, "Constructor ProtocolManager");
      glob.getRunlevelManager().addRunlevelListener(this);
   }

   /**
    * Load the drivers from xmlBlaster.properties.
    * <p />
    * Default is "Protocol.Drivers=<br />
    *   IOR:org.xmlBlaster.protocol.corba.CorbaDriver,<br />
    *   RMI:org.xmlBlaster.protocol.rmi.RmiDriver,<br />
    *   XML-RPC:org.xmlBlaster.protocol.xmlrpc.XmlRpcDriver,<br />
    *   JDBC:org.xmlBlaster.protocol.jdbc.JdbcDriver
    */
   private void initDrivers() {
      // A unique name for this xmlBlaster server instance, if running in a cluster
      String uniqueNodeIdName = null;

      String defaultDrivers = // See CbInfo.java for "Protocol.CallbackDrivers" default settings
                 "IOR:org.xmlBlaster.protocol.corba.CorbaDriver," +
                 "SOCKET:org.xmlBlaster.protocol.socket.SocketDriver," +
                 "RMI:org.xmlBlaster.protocol.rmi.RmiDriver," +
                 "XML-RPC:org.xmlBlaster.protocol.xmlrpc.XmlRpcDriver," +
                 "JDBC:org.xmlBlaster.protocol.jdbc.JdbcDriver";
      String drivers = glob.getProperty().get("Protocol.Drivers", defaultDrivers);
      StringTokenizer st = new StringTokenizer(drivers, ",");
      int numDrivers = st.countTokens();
      for (int ii=0; ii<numDrivers; ii++) {
         String token = st.nextToken().trim();
         int index = token.indexOf(":");
         if (index < 0) {
            log.error(ME, "Wrong syntax in xmlBlaster.properties Protocol.Drivers, driver ignored: " + token);
            continue;
         }
         String protocol = token.substring(0, index).trim();
         String driverId = token.substring(index+1).trim();
         try {
            I_Driver driver = loadDriver(protocol, driverId);
            //log.info(ME, "Loaded address " + driver.getRawAddress());

            if (driver.getRawAddress() != null) {
               // choose the shortest (human readable) unique name for this cluster node (xmlBlaster instance)
               if (uniqueNodeIdName == null)
                  uniqueNodeIdName = driver.getRawAddress();
               else if (uniqueNodeIdName.length() > driver.getRawAddress().length())
                  uniqueNodeIdName = driver.getRawAddress();
            }
         }
         catch (XmlBlasterException e) {
            log.error(ME, e.toString());
         }
         catch (Throwable e) {
            log.error(ME, e.toString());
            e.printStackTrace();
         }
      }

      if (glob.getNodeId() == null) {
         if (uniqueNodeIdName != null)
            glob.setUniqueNodeIdName(uniqueNodeIdName);
      }
   }


   /**
    * Load a protocol driver.
    * <p />
    * Usually invoked by entries in xmlBlaster.properties, but for example ProtocolManagerGUI.java
    * uses this directly.
    * @param protocol For example "IOR", "RMI", "XML-RPC"
    * @param driverId The class name of the driver, for example "org.xmlBlaster.protocol.corba.CorbaDriver"
    */
   public I_Driver loadDriver(String protocol, String driverId) throws XmlBlasterException
   {
      // Load the protocol driver ...
      I_Driver driver = null;
      try {
         if (log.TRACE) log.trace(ME, "Trying Class.forName('" + driverId + "') ...");
         Class cl = java.lang.Class.forName(driverId);
         driver = (I_Driver)cl.newInstance();
         driver.init(glob, glob.getAuthenticate(), glob.getAuthenticate().getXmlBlaster());
         protocols.addElement(driver);
         log.info(ME, "Found '" + protocol + "' driver '" + driverId + "'");
      }
      catch (IllegalAccessException e) {
         log.error(ME, "The driver class '" + driverId + "' is not accessible\n -> check the driver name and/or the CLASSPATH to the driver");
         throw new XmlBlasterException("Driver.NoClass", "The driver class '" + driverId + "' is not accessible\n -> check the driver name and/or the CLASSPATH to the driver");
      }
      catch (SecurityException e) {
         log.error(ME, "No right to access the driver class or initializer '" + driverId + "'");
         throw new XmlBlasterException("Driver.NoAccess", "No right to access the driver class or initializer '" + driverId + "'");
      }
      catch (ClassNotFoundException e) {
         log.error(ME, "The driver class or initializer '" + driverId + "' is invalid\n -> check the driver name and/or the CLASSPATH to the driver file: " + e.toString());
         throw new XmlBlasterException("Driver.Invalid", "The driver class or initializer '" + driverId + "' is invalid\n -> check the driver name and/or the CLASSPATH to the driver file: " + e.toString());
      }
      catch (Throwable e) {
         log.error(ME, "The driver class or initializer '" + driverId + "' is invalid\n -> check the driver name and/or the CLASSPATH to the driver file: " + e.toString());
         e.printStackTrace();
         throw new XmlBlasterException("Driver.Invalid", "The driver class or initializer '" + driverId + "' is invalid\n -> check the driver name and/or the CLASSPATH to the driver file: " + e.toString());
      }
      return driver;
   }

   private void activateDrivers() throws XmlBlasterException {
      for (int ii=0; ii<protocols.size(); ii++) {
         I_Driver driver = (I_Driver)protocols.elementAt(ii);
         activateDriver(driver);
      }
   }

   private void activateDriver(I_Driver driver) throws XmlBlasterException {
      if (driver != null) {
         try {
            driver.activate();
         } catch (XmlBlasterException e) {
            log.error(ME, "Initializing of driver " + driver.getName() + " failed:" + e.reason);
            //throw new XmlBlasterException("Driver.NoInit", "Initializing of driver " + driver.getName() + " failed:" + e.reason);
         }
      }
   }

   private void deactivateDrivers(boolean force) {
      for (int ii=0; ii<protocols.size(); ii++) {
         I_Driver driver = (I_Driver)protocols.elementAt(ii);
         try {
            driver.deActivate();
         }
         catch (Throwable e) {
            log.error(ME, "Shutdown of driver " + driver.getName() + " failed: " + e.toString());
         }
      }
   }

   private void shutdownDrivers(boolean force) throws XmlBlasterException {
      for (int ii=0; ii<protocols.size(); ii++) {
         I_Driver driver = (I_Driver)protocols.elementAt(ii);
         driver.shutdown(force);
      }
   }

   /**
    * Access all known I_Driver instances. 
    * NOTE: Please don't manipulate the returned Vector
    * @return The vector with protocol drivers, to be handled as immutable objects.
    */
   public Vector getProtocolDrivers() {
      return protocols;
   }

   /**
    * Access all I_Driver instances which have a public available address. 
    * NOTE: Please don't manipulate the returned drivers
    * @return Protocol drivers, to be handled as immutable objects.
    */
   public I_Driver[] getPublicProtocolDrivers() {
      int num = 0;
      if (log.TRACE) log.trace(ME, "Checking " + protocols.size() + " drivers for raw address");
      for (int ii=0; ii<protocols.size(); ii++) {
         I_Driver driver = (I_Driver)protocols.elementAt(ii);
         if (driver.getRawAddress() != null)
            num++;
      }
      I_Driver[] drivers = new I_Driver[num];
      int count = 0;
      for (int ii=0; ii<protocols.size(); ii++) {
         I_Driver driver = (I_Driver)protocols.elementAt(ii);
         if (driver.getRawAddress() != null)
            drivers[count++] = driver;
      }
      return drivers;
   }

   /**
    * Load the callback drivers from xmlBlaster.properties.
    * <p />
    * Accessing the CallbackDriver for this client, supporting the
    * desired protocol (CORBA, EMAIL, HTTP, RMI).
    * <p />
    * Default is support for IOR, XML-RPC, RMI and the JDBC service (ODBC bridge)
    * <p />
    * This is done once and than cached in the static protocols Hashtable.
    */
   private final void initCbDrivers() {
      String defaultDrivers = // See Main.java for "Protocol.Drivers" default settings
               "IOR:org.xmlBlaster.protocol.corba.CallbackCorbaDriver," +
               "SOCKET:org.xmlBlaster.protocol.socket.CallbackSocketDriver," +
               "RMI:org.xmlBlaster.protocol.rmi.CallbackRmiDriver," +
               "XML-RPC:org.xmlBlaster.protocol.xmlrpc.CallbackXmlRpcDriver," +
               "JDBC:org.xmlBlaster.protocol.jdbc.CallbackJdbcDriver";

      String drivers = glob.getProperty().get("Protocol.CallbackDrivers", defaultDrivers);
      StringTokenizer st = new StringTokenizer(drivers, ",");
      int numDrivers = st.countTokens();
      for (int ii=0; ii<numDrivers; ii++) {
         String token = st.nextToken().trim();
         int index = token.indexOf(":");
         if (index < 0) {
            log.error(ME, "Wrong syntax in xmlBlaster.properties Protocol.CallbackDrivers, driver ignored: " + token);
            continue;
         }
         String protocol = token.substring(0, index).trim();
         String driverId = token.substring(index+1).trim();

         if (driverId.equalsIgnoreCase("NATIVE")) { // We can mark in xmlBlaster.properties e.g. SOCKET:native
            continue;
         }

         // Load the protocol driver ...
         try {
            if (log.TRACE) log.trace(ME, "Trying Class.forName('" + driverId + "') ...");
            Class cl = java.lang.Class.forName(driverId);
            cbProtocolClasses.put(protocol, cl);
            if (log.TRACE) log.trace(ME, "Found callback driver class '" + driverId + "' for protocol '" + protocol + "'");
         }
         catch (SecurityException e) {
            log.error(ME, "No right to access the protocol driver class or initializer '" + driverId + "'");
         }
         catch (Throwable e) {
            log.error(ME, "The protocol driver class or initializer '" + driverId + "' is invalid\n -> check the driver name in xmlBlaster.properties and/or the CLASSPATH to the driver file: " + e.toString());
         }
      }
   }

   public final Class getCbProtocolDriverClass(String driverType) {
      return (Class)cbProtocolClasses.get(driverType);
   }

   /**
    * Creates a new instance of the given protocol driver type. 
    * <p />
    * You need to call cbDriver.init(glob, cbAddress) on it.
    * @return The uninitialized driver, never null
    * @exception XmlBlasterException on problems
    */
   public final I_CallbackDriver getNewCbProtocolDriverInstance(String driverType) throws XmlBlasterException {
      Class cl = getCbProtocolDriverClass(driverType);
      String err = null;
      try {
         I_CallbackDriver cbDriver = (I_CallbackDriver)cl.newInstance();
         if (log.TRACE) log.trace(ME, "Created callback driver for protocol '" + driverType + "'");
         return cbDriver;
      }
      catch (IllegalAccessException e) {
         err = "The protocol driver class '" + driverType + "' is not accessible\n -> check the driver name and/or the CLASSPATH to the driver";
      }
      catch (SecurityException e) {
         err = "No right to access the protocol driver class or initializer '" + driverType + "'";
      }
      catch (Throwable e) {
         err = "The protocol driver class or initializer '" + driverType + "' is invalid\n -> check the driver name and/or the CLASSPATH to the driver file: " + e.toString();
      }
      log.error(ME, err);
      throw new XmlBlasterException(ME, err);
   }

   private void activateCbDrivers() throws XmlBlasterException {
      if (log.TRACE) log.trace(ME, "Don't know how to activate the callback drivers, they are created for each client and session separately");
   }

   private final void deactivateCbDrivers(boolean force) {
      if (log.TRACE) log.trace(ME, "Don't know how to deactivate the callback drivers, they are created for each client and session separately");
   }

   private void shutdownCbDrivers(boolean force) throws XmlBlasterException {
      if (log.TRACE) log.trace(ME, "Don't know how to shutdown the callback drivers, they are created for each client and session separately");
   }

   /**
    * Protocol driver usage. 
    */
   public String usage() {
      StringBuffer sb = new StringBuffer(2048);
      try {
         Vector protocols = getProtocolDrivers();
         for (int ii=0; ii<protocols.size(); ii++) {
            I_Driver driver = (I_Driver)protocols.elementAt(ii);
            sb.append(driver.usage());
         }
      }
      catch(Exception e) {
         sb.append("Sorry - no help: " + e.toString());
      }
      return sb.toString();
   }

   /**
    * A human readable name of the listener for logging. 
    * <p />
    * Enforced by I_RunlevelListener
    */
   public String getName() {
      return ME;
   }

   /**
    * Invoked on run level change, see RunlevelManager.RUNLEVEL_HALTED and RunlevelManager.RUNLEVEL_RUNNING
    * <p />
    * Enforced by I_RunlevelListener
    */
   public void runlevelChange(int from, int to, boolean force) throws org.xmlBlaster.util.XmlBlasterException {
      //if (log.CALL) log.call(ME, "Changing from run level=" + from + " to level=" + to + " with force=" + force);
      if (to == from)
         return;

      if (to > from) { // startup
         if (to == RunlevelManager.RUNLEVEL_STANDBY) {
            initDrivers();
            initCbDrivers();
            glob.getHttpServer(); // incarnate allow http based access (is currently only used by CORBA)
         }
         if (to == RunlevelManager.RUNLEVEL_CLEANUP) {
            activateCbDrivers(); // not implemented: is done for each client on callback
         }
         if (to == RunlevelManager.RUNLEVEL_RUNNING) {
            activateDrivers();
         }
      }
      else if (to < from) { // shutdown
         if (to == RunlevelManager.RUNLEVEL_CLEANUP) {
            deactivateDrivers(force);
         }
         if (to == RunlevelManager.RUNLEVEL_STANDBY) {
            shutdownDrivers(force);
            deactivateCbDrivers(force); // not implemented: is done for each client on callback
            shutdownCbDrivers(force);  // not implemented: is done for each client on callback
         }
         if (to == RunlevelManager.RUNLEVEL_HALTED) {
            protocols.clear();
            cbProtocolClasses.clear();
            glob.shutdownHttpServer();
         }
      }
   }
}
