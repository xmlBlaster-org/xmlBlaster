/*------------------------------------------------------------------------------
Name:      Main.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main class to invoke the xmlBlaster server
Version:   $Id: Main.java,v 1.41 2000/06/13 13:03:57 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster;

import org.xmlBlaster.util.*;
import org.xmlBlaster.engine.*;
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
 * Every parameter may be set in the xmlBlaster.property file or at the command line,
 * but the command line is stronger. The leading "-" or "+" from the command line key
 * parameters are stripped (see org.xmlBlaster.util.Property.java).
 * <p />
 * Examples how to start the xmlBlaster server:
 * <p />
 * <code>   ${JacORB_HOME}/bin/jaco org.xmlBlaster.Main -iorPort 8080</code>
 * <p />
 * <code>   ${JacORB_HOME}/bin/jaco org.xmlBlaster.Main -iorFile /tmp/XmlBlaster_Ref</code>
 * <p />
 * <code>   jaco org.xmlBlaster.Main +trace +dump +calls +time</code>
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
      if (Args.getArg(args, "-?") == true || Args.getArg(args, "-h") == true) {
         usage();
         return;
      }
      Log.setLogLevel(args); // initialize log level and xmlBlaster.property file

      try {
         authenticate = new Authenticate();
         xmlBlasterImpl = new XmlBlasterImpl(authenticate);

         loadDrivers();

         // Loop through protocol drivers and start them
         for (int ii=0; ii<protocols.size(); ii++) {
            I_Driver driver = (I_Driver)protocols.elementAt(ii);
            try {
               driver.init(args, authenticate, xmlBlasterImpl);
            } catch (XmlBlasterException e) {
               Log.error(ME, "Initializing of driver " + driver.getName() + " failed:" + e.reason);
               continue;
            }
         }

         Log.info(ME, Memory.getStatistic());

         if (controlPanel == null) {
            Log.info(ME, "#####################################");
            Log.info(ME, "# xmlBlaster is ready for requests  #");
            Log.info(ME, "# press <?> and <enter> for options #");
            Log.info(ME, "#####################################");
         }
         else
            Log.info(ME, "xmlBlaster is ready for requests");
      } catch (Throwable e) {
         e.printStackTrace();
         Log.panic(ME, e.toString());
      }

      boolean useKeyboard = Property.getProperty("useKeyboard", true);
      if (!useKeyboard) {
         try { Thread.currentThread().wait();
         } catch(InterruptedException e) { Log.warning(ME, "Caught exception: " + e.toString()); }
      }

      // Used by testsuite to switch off blocking, this Main method is by default never returning:
      boolean doBlocking = Property.getProperty("doBlocking", true);

      if (doBlocking) {
         checkForKeyboardInput();
         // orb.run();
      }
   }


   /**
    * Load the drivers from xmlBlaster.properties.
    * <p />
    * Default is "Protocol.Drivers=IOR:org.xmlBlaster.protocol.corba.CorbaDriver"
    */
   private void loadDrivers()
   {
      String drivers = Property.getProperty("Protocol.Drivers", "IOR:org.xmlBlaster.protocol.corba.CorbaDriver");
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

         // Load the protocol driver ...
         try {
            if (Log.TRACE) Log.trace(ME, "Trying Class.forName('" + driverId + "') ...");
            Class cl = java.lang.Class.forName(driverId);
            I_Driver driver = (I_Driver)cl.newInstance();
            protocols.addElement(driver);
            Log.info(ME, "Found protocol driver '" + driverId + "'");
         }
         catch (IllegalAccessException e) {
            Log.error(ME, "The driver class '" + driverId + "' is not accessible\n -> check the driver name and/or the CLASSPATH to the driver");
         }
         catch (SecurityException e) {
            Log.error(ME, "No right to access the driver class or initializer '" + driverId + "'");
         }
         catch (Throwable e) {
            Log.error(ME, "The driver class or initializer '" + driverId + "' is invalid\n -> check the driver name and/or the CLASSPATH to the driver file: " + e.toString());
         }
      }
   }


   /**
    *  Instructs the ORB to shut down, which causes all object adapters to shut down.
    */
   public void shutdown(boolean wait_for_completion)
   {
      Log.info(ME, "Shutting down xmlBlaster ...");
      for (int ii=0; ii<protocols.size(); ii++) {
         I_Driver driver = (I_Driver)protocols.elementAt(ii);
         driver.shutdown();
      }
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
            }
            else if (line.toLowerCase().equals("q")) {
               shutdown(true);
               Log.exit(ME, "Good bye");
            }
            else // if (keyChar == '?' || Character.isLetter(keyChar) || Character.isDigit(keyChar))
               keyboardUsage();
         }
         catch (IOException e) {
            Log.warning(ME, e.toString());
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
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "jaco org.xmlBlaster.Main <options>");
      Log.plain(ME, "----------------------------------------------------------");
      for (int ii=0; ii<protocols.size(); ii++) {
         I_Driver driver = (I_Driver)protocols.elementAt(ii);
         Log.plain(ME, driver.usage());
      }
      Log.usage();
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "Example:");
      Log.plain(ME, "   jaco org.xmlBlaster.Main -iorPort 8080");
      Log.plain(ME, "   jaco org.xmlBlaster.Main -iorFile /tmp/XmlBlaster_Ref");
      Log.plain(ME, "   jaco org.xmlBlaster.Main +trace +dump +calls +time");
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
