/*------------------------------------------------------------------------------
Name:      Main.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main class to invoke the xmlBlaster server
Version:   $Id: Main.java,v 1.54 2000/10/11 07:47:20 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster;

import org.jutils.JUtilsException;
import org.xmlBlaster.util.Log;
import org.jutils.init.Args;
import org.jutils.io.FileUtil;
import org.jutils.runtime.Memory;

import org.xmlBlaster.engine.*;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.authentication.Authenticate;

import java.io.*;
import java.util.*;


/**
 * Main class to invoke the xmlBlaster server.
 * <p />
 * Command line parameters supported are for example (CORBA driver):
 * <p />
 * <ul>
 *    <li><code>-iorFile 'file name'   </code>default is no dumping of IOR<br />
 *        Specify a file where to dump the IOR of the AuthServer (for client access)
 *    </li>
 *    <li><code>-iorPort 'port number'   </code>default is port 7609<br />
 *        Specify a port number where the builtin http server publishes its AuthServer IOR<br />
 *        the port 0 switches this feature off
 *    </li>
 * </ul>
 * Please invoke with "-?" to get a more complete list of the supported parameters.
 * <br />
 * Every parameter may be set in the xmlBlaster.property file as a system property or at the command line,
 * the command line is strongest, xmlBlaster.properties weakest. The leading "-" from the command line key
 * parameters are stripped (see org.jutils.init.XmlBlasterProperty.java).
 * <p />
 * Examples how to start the xmlBlaster server:
 * <p />
 * <code>   ${JacORB_HOME}/bin/jaco org.xmlBlaster.Main -iorPort 8080</code>
 * <p />
 * <code>   ${JacORB_HOME}/bin/jaco org.xmlBlaster.Main -iorFile /tmp/XmlBlaster_Ref</code>
 * <p />
 * <code>   jaco org.xmlBlaster.Main -trace true -dump true -calls true -time true</code>
 *
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 */
public class Main
{
   final private String ME = "Main";
   /** The singleton handle for this xmlBlaster server */
   private Authenticate authenticate = null;
   /** The singleton handle for this xmlBlaster server */
   private I_XmlBlaster xmlBlasterImpl = null;
   /** Vector holding all protocol I_Driver.java implementations, e.g. CorbaDriver */
   private Vector protocols = new Vector();
   /** command line arguments */
   private String[] args = null;
   /** Version string, please change for new releases (4 digits) */
   private String version = "0.79";

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
      try {
         showUsage = XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         usage();
         Log.panic(ME, e.toString());
      }

      try {
         authenticate = new Authenticate();
         xmlBlasterImpl = new XmlBlasterImpl(authenticate);

         /* Marcel: Runs fine with JDK 1.3, not yet tested what happens in JDK 1.2 (does it compile?)
         try {  // Add shutdown hook (since JDK 1.3, catch signals)
            Runtime.getRuntime().addShutdownHook(new Thread() {
               public void run() {
                  Log.info(ME, "Shutdown forced by user or signal (Ctrl-C).");
                  shutdown();
               }
            });
            Log.info(ME, "Shutdown hook added, Ctrl-C will work to stop the server on UNIX.");
         } catch (Throwable e)  { // JDK 1.2.. ignore!
            Log.info(ME, "Could not add shutdown hook for JDK 1.2, Ctrl-C wont work.");
         }
         */

         loadDrivers();

         if (showUsage) {
            usage();  // Now we can display the complete usage of all loaded drivers
            Log.exit(ME, "Good bye.");
         }

         Log.info(ME, Memory.getStatistic());

         if (controlPanel == null) {
            Log.info(ME, "##########################################");
            Log.info(ME, "# xmlBlaster " + version + " is ready for requests  #");
            Log.info(ME, "# press <?> and <enter> for options      #");
            Log.info(ME, "##########################################");
         }
         else
            Log.info(ME, "xmlBlaster is ready for requests");
      } catch (Throwable e) {
         e.printStackTrace();
         Log.panic(ME, e.toString());
      }

      boolean useKeyboard = XmlBlasterProperty.get("useKeyboard", true);
      if (!useKeyboard) {
         try { Thread.currentThread().wait();
         } catch(InterruptedException e) { Log.warn(ME, "Caught exception: " + e.toString()); }
      }

      // Used by testsuite to switch off blocking, this Main method is by default never returning:
      boolean doBlocking = XmlBlasterProperty.get("doBlocking", true);

      if (doBlocking) {
         checkForKeyboardInput();
         // orb.run();
      }
   }


   /**
    * Load the drivers from xmlBlaster.properties.
    * <p />
    * Default is "Protocol.Drivers=IOR:org.xmlBlaster.protocol.corba.CorbaDriver,JDBC:org.xmlBlaster.protocol.jdbc.JdbcDriver,XML-RPC:org.xmlBlaster.protocol.xmlrpc.XmlRpcDriver"
    */
   private void loadDrivers()
   {
      String drivers = XmlBlasterProperty.get("Protocol.Drivers", "IOR:org.xmlBlaster.protocol.corba.CorbaDriver,JDBC:org.xmlBlaster.protocol.jdbc.JdbcDriver,XML-RPC:org.xmlBlaster.protocol.xmlrpc.XmlRpcDriver");
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
            loadDriver(protocol, driverId);
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
         protocols.addElement(driver);
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
            driver.init(args, authenticate, xmlBlasterImpl);
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
      Log.info(ME, "Shutting down xmlBlaster ...");
      for (int ii=0; ii<protocols.size(); ii++) {
         I_Driver driver = (I_Driver)protocols.elementAt(ii);
         try {
            driver.shutdown();
         }
         catch (Throwable e) {
            Log.error(ME, "Shutdown of driver " + driver.getName() + " failed: " + e.toString());
         }
      }
      protocols.clear();
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
            String line = in.readLine().trim(); // Blocking in I/O
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
      Log.plain(ME, "jaco org.xmlBlaster.Main <options>");
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "   -h                  Show the complete usage.");
      for (int ii=0; ii<protocols.size(); ii++) {
         I_Driver driver = (I_Driver)protocols.elementAt(ii);
         Log.plain(ME, driver.usage());
      }
      Log.usage();
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "Example:");
      Log.plain(ME, "   jaco org.xmlBlaster.Main -iorPort 8080");
      Log.plain(ME, "   jaco org.xmlBlaster.Main -iorFile /tmp/XmlBlaster_Ref");
      Log.plain(ME, "   jaco org.xmlBlaster.Main -trace true -dump true -calls true -time true");
      Log.plain(ME, "");
   }


   /**
    *  Invoke: jaco org.xmlBlaster.Main
    */
   public static void main( String[] args )
   {
      new Main(args);
   }
}
