/*------------------------------------------------------------------------------
Name:      Main.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main class to invoke the xmlBlaster server
Version:   $Id: Main.java,v 1.91 2002/06/13 13:22:12 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster;

import org.jutils.log.LogChannel;
import org.jutils.JUtilsException;
import org.jutils.init.Args;
import org.jutils.io.FileUtil;
import org.jutils.runtime.Memory;
import org.jutils.runtime.ThreadLister;

import org.xmlBlaster.engine.*;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.authentication.Authenticate;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Vector;
import java.util.StringTokenizer;
import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.Method;


/**
 * Main class to invoke the xmlBlaster server.
 * <p />
 * There are many command line parameters supported
 * please invoke with "-?" to get a complete list of the supported parameters.
 * <br />
 * Every parameter may be set in the xmlBlaster.property file as a system property or at the command line,
 * the command line is strongest, xmlBlaster.properties weakest. The leading "-" from the command line key
 * parameters are stripped (see org.jutils.init.XmlBlasterProperty.java).
 * <p />
 * Examples how to start the xmlBlaster server:
 * <p />
 * <code>   java org.xmlBlaster.Main -port 3412</code>
 * <p />
 * <code>   java org.xmlBlaster.Main -ior.file /tmp/XmlBlaster_Ref</code>
 * <p />
 * <code>   java org.xmlBlaster.Main -trace true -dump true -call true -time true</code>
 * <p />
 * <code>   java org.xmlBlaster.Main -xmlrpc.hostname 102.24.64.60 -xmlrpc.port 8081</code>
 * <p />
 * <code>   java org.xmlBlaster.Main -?</code>
 *
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/admin.telnet.html" target="others">admin.telnet</a>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/util.property.html" target="others">util.property</a>
 */
public class Main
{
   final private String ME = "Main";
   /** The singleton handle for this xmlBlaster server */
   private Authenticate authenticate = null;
   /** The singleton handle for this xmlBlaster server */
   private I_XmlBlaster xmlBlasterImpl = null;
   /** Version string, please change for new releases (4 digits) */
   private Global glob = null;
   private LogChannel log;
   /** A unique name for this xmlBlaster server instance, if running in a cluster */
   private String uniqueNodeIdName = null;
   /** Version string, please change for new releases (4 digits) */
   private String version = "0.79e";

   /**
    * true: If instance created by control panel<br />
    * false: running without GUI
    */
   static MainGUI controlPanel = null;


   public Main(String[] args, MainGUI controlPanel)
   {
      this.controlPanel = controlPanel;
      controlPanel.xmlBlasterMain = this;
      init(new Global(args));
   }


   /**
    * Start xmlBlaster.
    * @param args The command line parameters
    */
   public Main(org.xmlBlaster.util.Global utilGlob)
   {
      Global g = new Global(utilGlob); // engine.Global
      init(g);
   }

   /**
    * Start xmlBlaster.
    * @param args The command line parameters
    */
   public Main(String[] args)
   {
      init(new Global(args));
   }

   private void init(Global glob)
   {
      this.glob = glob;
      this.log = glob.getLog("core");
      boolean showUsage = glob.wantsHelp();
      Thread.currentThread().setName("XmlBlaster MainThread");

      if (glob.wantsHelp())
         showUsage = true;
      else if (glob.getErrorText() != null) {
         usage();
         log.error(ME, glob.getErrorText());
         System.exit(0);
      }

      try {
         authenticate = new Authenticate(glob);
         xmlBlasterImpl = new XmlBlasterImpl(authenticate);

         catchSignals();

         glob.fireRunlevelEvent(Constants.RUNLEVEL_STANDBY, false);

         loadCbProtocolDrivers();
         loadProtocolDrivers();

         glob.getRequestBroker().postInit();

         if (glob.getNodeId() == null) {
            if (uniqueNodeIdName != null)
               glob.setUniqueNodeIdName(uniqueNodeIdName);
            else
               glob.setUniqueNodeIdName(createNodeId());
         }

         if (showUsage) {
            usage();  // Now we can display the complete usage of all loaded drivers
            System.exit(0);
         }
         
         glob.fireRunlevelEvent(Constants.RUNLEVEL_RUNNING, false);

         log.info(ME, Memory.getStatistic());

         if (controlPanel == null) {
            log.info(ME, "###########################################");
            log.info(ME, "# xmlBlaster " + version + " is ready for requests  #");
            log.info(ME, "# press <?> and <enter> for options       #");
            log.info(ME, "###########################################");
         }
         else
            log.info(ME, "xmlBlaster is ready for requests");
      } catch (Throwable e) {
         e.printStackTrace();
         log.error(ME, e.toString());
         System.exit(1);
      }

      if (log.DUMP) { ThreadLister.listAllThreads(System.out); }

      boolean useKeyboard = glob.getProperty().get("useKeyboard", true);
      if (!useKeyboard) {
         while (true) {
            try { Thread.currentThread().sleep(100000000L);
            } catch(InterruptedException e) { log.warn(ME, "Caught exception: " + e.toString()); }
         }
         /*
         //  Exception in thread "main" java.lang.IllegalMonitorStateException:
         try { Thread.currentThread().wait();
         } catch(InterruptedException e) { log.warn(ME, "Caught exception: " + e.toString()); }
         */
      }

      // Used by testsuite to switch off blocking, this Main method is by default never returning:
      boolean doBlocking = glob.getProperty().get("doBlocking", true);

      if (doBlocking) {
         checkForKeyboardInput();
         // orb.run();
      }
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
   private void loadProtocolDrivers()
   {
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
   }


   /**
    * Load a protocol driver.
    * <p />
    * Usually invoked by entries in xmlBlaster.properties, but for example MainGUI.java
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
         glob.addProtocolDriver(driver);
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
      catch (Throwable e) {
         log.error(ME, "The driver class or initializer '" + driverId + "' is invalid\n -> check the driver name and/or the CLASSPATH to the driver file: " + e.toString());
         throw new XmlBlasterException("Driver.Invalid", "The driver class or initializer '" + driverId + "' is invalid\n -> check the driver name and/or the CLASSPATH to the driver file: " + e.toString());
      }

      // Start the driver
      if (driver != null) {
         try {
            driver.init(glob, authenticate, xmlBlasterImpl);
         } catch (XmlBlasterException e) {
            //log.error(ME, "Initializing of driver " + driver.getName() + " failed:" + e.reason);
            throw new XmlBlasterException("Driver.NoInit", "Initializing of driver " + driver.getName() + " failed:" + e.reason);
         }
      }
      return driver;
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
   private final void loadCbProtocolDrivers() {
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
            glob.addCbProtocolDriverClass(protocol, cl);
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

   /**
    * Instructs the ORB to shut down, which causes all object adapters to shut down.
    * <p />
    * The drivers are removed.
    */
   public void shutdown()
   {
      if (glob.isHalted())
         return;

      if (glob.getCurrentRunlevel() > Constants.RUNLEVEL_STANDBY) {
         try {
            glob.fireRunlevelEvent(Constants.RUNLEVEL_STANDBY, true);
         }
         catch (Throwable e) {
            log.error(ME, "Problems during shutdown: " + e.toString());
         }
      }

      // TODO: The protocol drivers should add I_RunlevelListener.java
      if (glob.getProtocolDrivers().size() > 0) {
         log.info(ME, "Shutting down xmlBlaster ...");
         if (log.DUMP) ThreadLister.listAllThreads(System.out);
      }
      glob.shutdownProtocolDrivers();

      try {
         glob.fireRunlevelEvent(Constants.RUNLEVEL_HALTED, true);
      }
      catch (Throwable e) {
         log.error(ME, "Problems during shutdown: " + e.toString());
      }
   }


   public boolean isHalted()
   {
      return glob.isHalted();
   }


   /**
    * Access the authentication singleton.
    */
   public Authenticate getAuthenticate()
   {
      return authenticate;
   }


   /**
    * Access the xmlBlaster singleton.
    */
   public I_XmlBlaster getXmlBlaster()
   {
      return xmlBlasterImpl;
   }

   /**
    * Check for keyboard entries from console.
    * <p />
    * Supported input is:
    * &lt;ul>
    *    &lt;li>'g' to pop up the control panel GUI&lt;/li>
    *    &lt;li>'d' to dump the internal state of xmlBlaster&lt;/li>
    *    &lt;li>'q' to quit xmlBlaster&lt;/li>
    * &lt;/ul>
    * <p />
    * NOTE: This method never returns, only on exit for 'q'
    */
   private void checkForKeyboardInput()
   {
      BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
      while (true) {
         // orbacus needs this !!! Michele?
         // if (orb.work_pending()) orb.perform_work();
         try {
            String line = in.readLine(); // Blocking in I/O
            if (line == null) continue;
            line = line.trim();
            if (line.toLowerCase().equals("g")) {
               if (controlPanel == null) {
                  log.info(ME, "Invoking control panel GUI ...");
                  controlPanel = new MainGUI(new String[0], this); // the constructor sets the variable controlPanel
                  controlPanel.run();
               }
               else
                  controlPanel.showWindow();
            }
            else if (line.toLowerCase().startsWith("d")) {
               try {
                  String fileName = null;
                  if (line.length() > 1) fileName = line.substring(1).trim();

                  if (fileName == null) {
                     log.plain(ME, authenticate.toXml());
                     log.plain(ME, xmlBlasterImpl.toXml());
                     log.info(ME, "Dump done");
                  }
                  else {
                     FileUtil.writeFile(fileName, authenticate.toXml());
                     FileUtil.appendToFile(fileName, xmlBlasterImpl.toXml());
                     log.info(ME, "Dumped internal state to '" + fileName + "'");
                  }
               }
               catch(XmlBlasterException e) {
                  log.error(ME, "Sorry, dump failed: " + e.reason);
               }
               catch(JUtilsException e) {
                  log.error(ME, "Sorry, dump failed: " + e.reason);
               }
            }
            else if (line.toLowerCase().equals("q")) {
               shutdown();
               System.exit(0);
            }
            else // if (keyChar == '?' || Character.isLetter(keyChar) || Character.isDigit(keyChar))
               keyboardUsage();
         }
         catch (IOException e) {
            log.warn(ME, e.toString());
         }
      }
   }

   /**
    * Generate a unique xmlBlaster instance ID. 
    * This is the last fallback to create a cluster node id:
    * <ol>
    *   <li>cluster.node.id : The environment is checked for a given cluster node id</li>
    *   <li>address :         The protocol drivers are checked if one has established a socket</li>
    *   <li>createNodeId :    We generate a unique node id</li>
    * </ol>
    *  @return unique ID
    */
   private String createNodeId() throws XmlBlasterException
   {
      String ip;
      try  {
         java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
         ip = addr.getHostAddress();
      } catch (Exception e) {
         log.warn(ME, "Can't determin your IP address");
         ip = "localhost";
      }

      StringBuffer buf = new StringBuffer(256);
      buf.append("ClusterNodeId-").append(ip).append("-").append(System.currentTimeMillis());
      String nodeName = buf.toString();
      if (log.TRACE) log.trace(ME, "Created node id='" + nodeName + "'");
      return nodeName;
   }

   /**
    * Add shutdown hook.
    * <p />
    * Catch signals, e.g. Ctrl C to stop xmlBlaster.<br />
    * Uses reflection since only JDK 1.3 supports it.
    * <p />
    * NOTE: On Linux build 1.3.0, J2RE 1.3.0 IBM build cx130-20000815 (JIT enabled: jitc) fails with Ctrl-C
    *
    * @return true: Shutdown hook is established
    */
   public boolean catchSignals()
   {
      class Shutdown extends Thread {
         public void run() {
            log.info(ME, "Shutdown forced by user or signal (Ctrl-C).");
            shutdown();
         }
      }

      Method method;
      try  {
         Class cls = Runtime.getRuntime().getClass();
         Class[] paramCls = new Class[1];
         paramCls[0] = Class.forName("java.lang.Thread");
         method = cls.getDeclaredMethod("addShutdownHook", paramCls);
      }
      catch (java.lang.ClassNotFoundException e) {
         return false;
      }
      catch (java.lang.NoSuchMethodException e) {
         log.trace(ME, "No shutdown hook established");
         return false;
      }

      try {
         if (method != null) {
            Object[] params = new Object[1];
            params[0] = new Shutdown();
            method.invoke(Runtime.getRuntime(), params);
         }
      }
      catch (java.lang.reflect.InvocationTargetException e) {
         return false;
      }
      catch (java.lang.IllegalAccessException e) {
         return false;
      }
      return true;
   }



   /**
    * Keyboard input usage.
    */
   private void keyboardUsage()
   {
      log.plain(ME, "----------------------------------------------------------");
      log.plain(ME, "Following interactive keyboard input is recognized:");
      log.plain(ME, "Key:");
      log.plain(ME, "   g             Popup the control panel GUI.");
      log.plain(ME, "   d <file name> Dump internal state of xmlBlaster to file.");
      log.plain(ME, "   q             Quit xmlBlaster.");
      log.plain(ME, "----------------------------------------------------------");
   }


   /**
    * Command line usage.
    */
   private void usage()
   {
      log.plain(ME, "-----------------------" + version + "-------------------------------");
      log.plain(ME, "java org.xmlBlaster.Main <options>");
      log.plain(ME, "----------------------------------------------------------");
      log.plain(ME, "   -h                  Show the complete usage.");
      log.plain(ME, "");
      Vector protocols = glob.getProtocolDrivers();
      for (int ii=0; ii<protocols.size(); ii++) {
         I_Driver driver = (I_Driver)protocols.elementAt(ii);
         log.plain(ME, driver.usage());
      }
      log.plain(ME, org.xmlBlaster.engine.cluster.ClusterManager.usage());
      log.plain(ME, "");
      log.plain(ME, org.xmlBlaster.util.Global.usage());
      log.plain(ME, "");
      log.plain(ME, "Other stuff:");
      log.plain(ME, "   -useKeyboard false  Switch off keyboard input, to allow xmlBlaster running in background.");
      log.plain(ME, "   -useKeyboard false  Switch off keyboard input, to allow xmlBlaster running in background.");
      log.plain(ME, "   -doBlocking  false  Switch off blocking, the main method is by default never returning.");
      log.plain(ME, "   -admin.remoteconsole.port If given port > 1000 (e.g. 2702), a server is started which is available with telnet [0].");
      log.plain(ME, "----------------------------------------------------------");
      log.plain(ME, "Example:");
      log.plain(ME, "   java org.xmlBlaster.Main -port 3412");
      log.plain(ME, "   java org.xmlBlaster.Main -ior.file /tmp/XmlBlaster_Ref");
      log.plain(ME, "   java org.xmlBlaster.Main -trace true -dump true -call true -time true");
      log.plain(ME, "   java org.xmlBlaster.Main -trace[mime] true -call[cluster] true -dump[corba] true");
      log.plain(ME, "   java org.xmlBlaster.Main -xmlrpc.hostname 102.24.64.60 -xmlrpc.port 8081");
      log.plain(ME, "   java org.xmlBlaster.Main -?");
      log.plain(ME, "See xmlBlaster.properties for more options");
      log.plain(ME, "");
   }


   /**
    *  Invoke: java org.xmlBlaster.Main
    */
   public static void main( String[] args )
   {
      new Main(args);
   }
}
