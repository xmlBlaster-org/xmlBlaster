/*------------------------------------------------------------------------------
Name:      Main.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main class to invoke the xmlBlaster server
Version:   $Id: Main.java,v 1.84 2002/05/11 09:36:19 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster;

import org.jutils.JUtilsException;
import org.xmlBlaster.util.Log;
import org.jutils.init.Args;
import org.jutils.io.FileUtil;
import org.jutils.runtime.Memory;
import org.jutils.runtime.ThreadLister;

import org.xmlBlaster.engine.*;
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

import remotecons.RemoteServer;


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
   /** command line arguments */
   private String[] args = null;
   /** Version string, please change for new releases (4 digits) */
   private Global glob = null;
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
      init(args);
   }


   /**
    * Start xmlBlaster.
    * @param args The command line parameters
    */
   public Main(String[] args)
   {
      init(args);
   }

   private void init(String args[])
   {
      this.args = args;
      boolean showUsage = false;
      Thread.currentThread().setName("XmlBlaster MainThread");

      glob = new Global();
      int ret = glob.init(args);
      if (ret > 0)
         showUsage = true;
      else if (ret < 0) {
         usage();
         Log.panic(ME, "Bye");
      }

      try {
         authenticate = new Authenticate(glob);
         xmlBlasterImpl = new XmlBlasterImpl(authenticate);

         catchSignals();

         loadDrivers();

         if (glob.getNodeId() == null) {
            if (uniqueNodeIdName != null)
               glob.setUniqueNodeIdName(uniqueNodeIdName);
            else
               glob.setUniqueNodeIdName(createNodeId());
         }

         if (showUsage) {
            usage();  // Now we can display the complete usage of all loaded drivers
            Log.exit(ME, "Good bye.");
         }
         
         createRemoteConsole();

         Log.info(ME, Memory.getStatistic());

         if (controlPanel == null) {
            Log.info(ME, "###########################################");
            Log.info(ME, "# xmlBlaster " + version + " is ready for requests  #");
            Log.info(ME, "# press <?> and <enter> for options       #");
            Log.info(ME, "###########################################");
         }
         else
            Log.info(ME, "xmlBlaster is ready for requests");
      } catch (Throwable e) {
         e.printStackTrace();
         Log.panic(ME, e.toString());
      }

      if (Log.DUMP) { ThreadLister.listAllThreads(System.out); }

      boolean useKeyboard = glob.getProperty().get("useKeyboard", true);
      if (!useKeyboard) {
         while (true) {
            try { Thread.currentThread().sleep(100000000L);
            } catch(InterruptedException e) { Log.warn(ME, "Caught exception: " + e.toString()); }
         }
         /*
         //  Exception in thread "main" java.lang.IllegalMonitorStateException:
         try { Thread.currentThread().wait();
         } catch(InterruptedException e) { Log.warn(ME, "Caught exception: " + e.toString()); }
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
   private void loadDrivers()
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
            Log.error(ME, "Wrong syntax in xmlBlaster.properties Protocol.Drivers, driver ignored: " + token);
            continue;
         }
         String protocol = token.substring(0, index).trim();
         String driverId = token.substring(index+1).trim();
         try {
            I_Driver driver = loadDriver(protocol, driverId);
            //Log.info(ME, "Loaded address " + driver.getRawAddress());
            if (driver.getRawAddress() != null) {
               // choose the shortest (human readable) unique name for this cluster node (xmlBlaster instance)
               if (uniqueNodeIdName == null)
                  uniqueNodeIdName = driver.getRawAddress();
               else if (uniqueNodeIdName.length() > driver.getRawAddress().length())
                  uniqueNodeIdName = driver.getRawAddress();
            }
         }
         catch (XmlBlasterException e) {
            Log.error(ME, e.toString());
         }
         catch (Throwable e) {
            Log.error(ME, e.toString());
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
         if (Log.TRACE) Log.trace(ME, "Trying Class.forName('" + driverId + "') ...");
         Class cl = java.lang.Class.forName(driverId);
         driver = (I_Driver)cl.newInstance();
         glob.addProtocolDriver(driver);
         Log.info(ME, "Found '" + protocol + "' driver '" + driverId + "'");
      }
      catch (IllegalAccessException e) {
         Log.error(ME, "The driver class '" + driverId + "' is not accessible\n -> check the driver name and/or the CLASSPATH to the driver");
         throw new XmlBlasterException("Driver.NoClass", "The driver class '" + driverId + "' is not accessible\n -> check the driver name and/or the CLASSPATH to the driver");
      }
      catch (SecurityException e) {
         Log.error(ME, "No right to access the driver class or initializer '" + driverId + "'");
         throw new XmlBlasterException("Driver.NoAccess", "No right to access the driver class or initializer '" + driverId + "'");
      }
      catch (Throwable e) {
         Log.error(ME, "The driver class or initializer '" + driverId + "' is invalid\n -> check the driver name and/or the CLASSPATH to the driver file: " + e.toString());
         throw new XmlBlasterException("Driver.Invalid", "The driver class or initializer '" + driverId + "' is invalid\n -> check the driver name and/or the CLASSPATH to the driver file: " + e.toString());
      }

      // Start the driver
      if (driver != null) {
         try {
            driver.init(glob, authenticate, xmlBlasterImpl);
         } catch (XmlBlasterException e) {
            //Log.error(ME, "Initializing of driver " + driver.getName() + " failed:" + e.reason);
            throw new XmlBlasterException("Driver.NoInit", "Initializing of driver " + driver.getName() + " failed:" + e.reason);
         }
      }
      return driver;
   }


   /**
    * Instructs the ORB to shut down, which causes all object adapters to shut down.
    * <p />
    * The drivers are removed.
    */
   public void shutdown()
   {
      if (glob.getProtocolDrivers().size() > 0) {
         Log.info(ME, "Shutting down xmlBlaster ...");
         if (Log.DUMP) ThreadLister.listAllThreads(System.out);
      }

      glob.shutdownProtocolDrivers();
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
    * Creates a server which is accessible with telnet. 
    * This allows you to access xmlBlaster and query for example the free memory:
    * <pre>
    *  telnet 192.168.1.2 2702
    *  mem
    * </pre>
    * Enter 'help' for all available commands.
    */
   public void createRemoteConsole()
   {
      int port = glob.getProperty().get("remoteconsole.port", 0); // 2702;
      if (port > 1000) {
         RemoteServer rs = new RemoteServer();
         rs.setServer_port(port);
         try {
           rs.initialize(null);
           Log.info(ME, "Started remote console server, try 'telnet " + glob.getLocalIP() + " " + port + "' to access it and type 'help'.");
         } catch (IOException e) {
           e.printStackTrace();
           Log.error(ME, "Initializing of remote console failed:" + e.toString());
         }
      }
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
                  Log.info(ME, "Invoking control panel GUI ...");
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
                     Log.plain(ME, authenticate.toXml());
                     Log.plain(ME, xmlBlasterImpl.toXml());
                     Log.info(ME, "Dump done");
                  }
                  else {
                     FileUtil.writeFile(fileName, authenticate.toXml());
                     FileUtil.appendToFile(fileName, xmlBlasterImpl.toXml());
                     Log.info(ME, "Dumped internal state to '" + fileName + "'");
                  }
               }
               catch(XmlBlasterException e) {
                  Log.error(ME, "Sorry, dump failed: " + e.reason);
               }
               catch(JUtilsException e) {
                  Log.error(ME, "Sorry, dump failed: " + e.reason);
               }
            }
            else if (line.toLowerCase().equals("q")) {
               shutdown();
               Log.exit(ME, "Good bye");
            }
            else // if (keyChar == '?' || Character.isLetter(keyChar) || Character.isDigit(keyChar))
               keyboardUsage();
         }
         catch (IOException e) {
            Log.warn(ME, e.toString());
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
         Log.warn(ME, "Can't determin your IP address");
         ip = "localhost";
      }

      StringBuffer buf = new StringBuffer(256);
      buf.append("ClusterNodeId-").append(ip).append("-").append(System.currentTimeMillis());
      String nodeName = buf.toString();
      if (Log.TRACE) Log.trace(ME, "Created node id='" + nodeName + "'");
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
            Log.info(ME, "Shutdown forced by user or signal (Ctrl-C).");
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
         Log.trace(ME, "No shutdown hook established");
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
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "Following interactive keyboard input is recognized:");
      Log.plain(ME, "Key:");
      Log.plain(ME, "   g             Popup the control panel GUI.");
      Log.plain(ME, "   d <file name> Dump internal state of xmlBlaster to file.");
      Log.plain(ME, "   q             Quit xmlBlaster.");
      Log.plain(ME, "----------------------------------------------------------");
   }


   /**
    * Command line usage.
    */
   private void usage()
   {
      Log.plain(ME, "-----------------------" + version + "-------------------------------");
      Log.plain(ME, "java org.xmlBlaster.Main <options>");
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "   -h                  Show the complete usage.");
      Vector protocols = glob.getProtocolDrivers();
      for (int ii=0; ii<protocols.size(); ii++) {
         I_Driver driver = (I_Driver)protocols.elementAt(ii);
         Log.plain(ME, driver.usage());
      }
      Log.plain(ME, "Cluster support:");
      Log.plain(ME, "   -cluster.node.id    A unique name for this xmlBlaster instance, e.g. 'com.myCompany.myHost'");
      Log.plain(ME, "                       If not specified a unique name is choosen and displayed on command line");
      Log.usage();
      Log.plain(ME, "Other stuff:");
      Log.plain(ME, "   -useKeyboard false  Switch off keyboard input, to allow xmlBlaster running in background.");
      Log.plain(ME, "   -useKeyboard false  Switch off keyboard input, to allow xmlBlaster running in background.");
      Log.plain(ME, "   -doBlocking  false  Switch off blocking, the main method is by default never returning.");
      Log.plain(ME, "   -remoteconsole.port If given port > 1000, a server is started which is available with telnet [0].");
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "Example:");
      Log.plain(ME, "   java org.xmlBlaster.Main -port 3412");
      Log.plain(ME, "   java org.xmlBlaster.Main -ior.file /tmp/XmlBlaster_Ref");
      Log.plain(ME, "   java org.xmlBlaster.Main -trace true -dump true -call true -time true");
      Log.plain(ME, "   java org.xmlBlaster.Main -xmlrpc.hostname 102.24.64.60 -xmlrpc.port 8081");
      Log.plain(ME, "   java org.xmlBlaster.Main -?");
      Log.plain(ME, "");
   }


   /**
    *  Invoke: java org.xmlBlaster.Main
    */
   public static void main( String[] args )
   {
      new Main(args);
   }
}
