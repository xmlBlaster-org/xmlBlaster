/*------------------------------------------------------------------------------
Name:      Main.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main class to invoke the xmlBlaster server
Version:   $Id: Main.java,v 1.97 2002/06/25 07:38:56 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster;

import org.jutils.log.LogChannel;
import org.jutils.JUtilsException;
import org.jutils.io.FileUtil;
import org.jutils.runtime.Memory;
import org.jutils.runtime.ThreadLister;

import org.xmlBlaster.engine.*;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.authentication.Authenticate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
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
public class Main implements I_RunlevelListener
{
   final private String ME = "Main";
   
   private Global glob = null;
   
   private LogChannel log;

   /** Starts/stops xmlBlaster */
   private RunlevelManager runlevelManager = null;

   private boolean showUsage = false;

   private boolean inShutdownProcess = false;


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
      showUsage = glob.wantsHelp();
      Thread.currentThread().setName("XmlBlaster MainThread");

      if (glob.wantsHelp())
         showUsage = true;
      else if (glob.getErrorText() != null) {
         usage();
         log.error(ME, glob.getErrorText());
         System.exit(0);
      }

      int runlevel = glob.getProperty().get("runlevel", RunlevelManager.RUNLEVEL_RUNNING);
      try {
         runlevelManager = glob.getRunlevelManager();
         runlevelManager.addRunlevelListener(this);
         runlevelManager.initPluginManagers();
         runlevelManager.changeRunlevel(runlevel, false);
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
    * Instructs the ORB to shut down, which causes all object adapters to shut down.
    * <p />
    * The drivers are removed.
    */
   public void shutdown()
   {
      if (inShutdownProcess)
         return;
      
      inShutdownProcess = true;

      int errors = 0;
      try {
         errors = runlevelManager.changeRunlevel(RunlevelManager.RUNLEVEL_HALTED, true);
      }
      catch(XmlBlasterException e) {
         log.error(ME, "Problem during shutdown: " + e.toString());
      }
      if (errors > 0)
         log.warn(ME, "There were " + errors + " errors during shutdown.");
   }

   /**
    * Access the authentication singleton.
    */
   public Authenticate getAuthenticate()
   {
      return glob.getAuthenticate();
   }

   /**
    * Access the xmlBlaster singleton.
    */
   public I_XmlBlaster getXmlBlaster()
   {
      return getAuthenticate().getXmlBlaster();
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
            else if (line.toLowerCase().startsWith("r")) {
               if (line.length() > 1) {
                  String tmp = line.substring(1).trim();
                  int runlevel = -10;
                  try { runlevel = Integer.parseInt(tmp.trim()); } catch(NumberFormatException e) { log.error(ME, "Invalid run level '" + tmp + "', it should be a number."); };
                  try { runlevelManager.changeRunlevel(runlevel, true); } catch(XmlBlasterException e) { log.error(ME, e.toString()); }
               }
            }
            else if (line.toLowerCase().startsWith("d")) {
               try {
                  String fileName = null;
                  if (line.length() > 1) fileName = line.substring(1).trim();

                  if (fileName == null) {
                     log.plain(ME, glob.getDump());
                     log.info(ME, "Dump done");
                  }
                  else {
                     FileUtil.writeFile(fileName, glob.getDump());
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

   public boolean isHalted() {
      return runlevelManager.isHalted();
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
         if (to == RunlevelManager.RUNLEVEL_STANDBY_PRE) {
            catchSignals();
         }
         if (to == RunlevelManager.RUNLEVEL_STANDBY) {
         }
         if (to == RunlevelManager.RUNLEVEL_STANDBY_POST) {
            if (glob.getNodeId() == null)
               glob.setUniqueNodeIdName(createNodeId());
            if (showUsage) {
               usage();  // Now we can display the complete usage of all loaded drivers
               shutdown();
               System.exit(0);
            }
         }
         if (to == RunlevelManager.RUNLEVEL_CLEANUP) {
         }
         if (to == RunlevelManager.RUNLEVEL_RUNNING) {
         }
         if (to == RunlevelManager.RUNLEVEL_RUNNING_POST) {
            log.info(ME, Memory.getStatistic());
            if (controlPanel == null) {
               log.info(ME, "###########################################");
               log.info(ME, "# xmlBlaster " + glob.getVersion() + " is ready for requests  #");
               log.info(ME, "# press <?> and <enter> for options       #");
               log.info(ME, "###########################################");
            }
            else
               log.info(ME, "xmlBlaster is ready for requests");
         }
      }
      if (to < from) { // shutdown
         if (to == RunlevelManager.RUNLEVEL_RUNNING_PRE) {
            if (log.TRACE) log.trace(ME, "Shutting down xmlBlaster to runlevel " + RunlevelManager.toRunlevelStr(to) + " ...");
         }
         if (to == RunlevelManager.RUNLEVEL_HALTED_PRE) {
            if (log.DUMP) ThreadLister.listAllThreads(System.out);
            log.info(ME, "XmlBlaster halted.");
         }
      }
   }

   /**
    * Keyboard input usage.
    */
   private void keyboardUsage() {
      log.plain(ME, "----------------------------------------------------------");
      log.plain(ME, "XmlBlaster " + glob.getVersion() + " build " + glob.getBuildTimestamp());
      log.plain(ME, "Following interactive keyboard input is recognized:");
      log.plain(ME, "Key:");
      log.plain(ME, "   g             Popup the control panel GUI.");
      log.plain(ME, "   r <run level> Change to run level (0,3,6,9).");
      log.plain(ME, "   d <file name> Dump internal state of xmlBlaster to file.");
      log.plain(ME, "   q             Quit xmlBlaster.");
      log.plain(ME, "----------------------------------------------------------");
   }

   /**
    * Command line usage.
    */
   private void usage() {
      log.plain(ME, "-----------------------" + glob.getVersion() + "-------------------------------");
      log.plain(ME, "java org.xmlBlaster.Main <options>");
      log.plain(ME, "----------------------------------------------------------");
      log.plain(ME, "   -h                  Show the complete usage.");
      log.plain(ME, "");
      try { log.plain(ME, glob.getProtocolManager().usage()); } catch (XmlBlasterException e) { log.warn(ME, "No usage: " + e.toString()); }
      log.plain(ME, "");
      log.plain(ME, org.xmlBlaster.engine.cluster.ClusterManager.usage());
      log.plain(ME, "");
      log.plain(ME, glob.usage());
      log.plain(ME, "");
      log.plain(ME, "Other stuff:");
      log.plain(ME, "   -useKeyboard false  Switch off keyboard input, to allow xmlBlaster running in background.");
      log.plain(ME, "   -doBlocking  false  Switch off blocking, the main method is by default never returning.");
      log.plain(ME, "   -admin.remoteconsole.port If port > 1000 a server is started which is available with telnet [2702].");
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
