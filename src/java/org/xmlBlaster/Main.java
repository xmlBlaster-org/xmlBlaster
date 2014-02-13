/*------------------------------------------------------------------------------
Name:      Main.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main class to invoke the xmlBlaster server
------------------------------------------------------------------------------*/
package org.xmlBlaster;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.engine.runlevel.I_RunlevelListener;
import org.xmlBlaster.engine.runlevel.RunlevelManager;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.util.FileLocator;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_SignalListener;
import org.xmlBlaster.util.I_XmlBlasterExceptionHandler;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.SignalCatcher;
import org.xmlBlaster.util.ThreadLister;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.admin.extern.JmxWrapper;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.log.StdoutStderrRedirector;
import org.xmlBlaster.util.log.XbFormatter;
import org.xmlBlaster.util.property.Property;

/**
 * Main class to invoke the xmlBlaster server.
 * <p />
 * There are many command line parameters supported
 * please invoke with "-?" to get a complete list of the supported parameters.
 * <br />
 * Every parameter may be set in the xmlBlaster.property file as a system property or at the command line,
 * the command line is strongest, xmlBlaster.properties weakest. The leading "-" from the command line key
 * parameters are stripped (see Property.java).
 * <p />
 * Examples how to start the xmlBlaster server:
 * <p />
 * <code>   java org.xmlBlaster.Main -bootstrapPort 3412</code>
 * <p />
 * <code>   java org.xmlBlaster.Main -plugin/ior/iorFile /tmp/XmlBlaster_Ref</code>
 * <p />
 * <code>   java org.xmlBlaster.Main -logging FINEST</code>
 * <p />
 * <code>   java org.xmlBlaster.Main -plugin/xmlrpc/hostname 102.24.64.60 -plugin/xmlrpc/port 8081</code>
 * <p />
 * <code>   java org.xmlBlaster.Main -?</code>
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/admin.telnet.html" target="others">admin.telnet</a>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/util.property.html" target="others">util.property</a>
 */
public class Main implements I_RunlevelListener, I_Main, I_SignalListener, I_XmlBlasterExceptionHandler
{
   private String ME = "Main";

   private ServerScope glob = null;

   private static Logger log = Logger.getLogger(Main.class.getName());

   /** Starts/stops xmlBlaster */
   private RunlevelManager runlevelManager = null;

   private boolean showUsage = false;
   
   /** Incarnation time of this object instance in millis */
   private long startupTime;

   private boolean inShutdownProcess = false;
   private SignalCatcher signalCatcher;
   /**
    * Comma separate list of errorCodes which to an immediate System.exit(1);
    * Used by our default implementation of I_XmlBlasterExceptionHandler
    * TODO: If you use JdbcManagerCommonTableDelegate.java you may NOT
    * use ErrorCode.RESOURCE_DB_UNKNOWN as this will retry one time the operation!
    * How to assure this if configured different???
    */
   //private String panicErrorCodes = ErrorCode.RESOURCE_DB_UNAVAILABLE.getErrorCode();
   private String panicErrorCodes = ErrorCode.RESOURCE_DB_UNKNOWN.getErrorCode()+","+ErrorCode.RESOURCE_DB_UNAVAILABLE.getErrorCode();

   /**
    * true: If instance created by control panel<br />
    * false: running without GUI
    */
   static MainGUI controlPanel = null;
   
   private StdoutStderrRedirector stdoutStderrRedirector;

   /**
    * You need to call init() after construction.
    * Currently used by XmlBlasterClassLoader
    */
    public Main() {
       //System.out.println("Default constructor called ...");
    }


   public Main(ServerScope glob, MainGUI controlPanel_) {
      controlPanel = controlPanel_;
      controlPanel.xmlBlasterMain = this;
      init(glob);
   }


   /**
    * Start xmlBlaster using the properties from utilGlob
    * without loading xmlBlaster.properties again
    * @param utilGlob The environment for this server instance
    */
   public Main(Global utilGlob) {
      if (utilGlob instanceof ServerScope)
         init(utilGlob);
      else
         init(new ServerScope(Property.propsToArgs(utilGlob.getProperty().getProperties())));
   }

   /**
    * Start xmlBlaster using the given properties
    * and load xmlBlaster.properties.
    * @param args The command line parameters
    */
   public Main(String[] args) {
      // The setting 'java -DxmlBlaster/initClassName=mypackage.MyClass ...' allows to load an initial class instance
      String initClass = System.getProperty("xmlBlaster/initClassName", "");
      if (initClass.length() > 0) {
        try {
          this.getClass().getClassLoader().loadClass(initClass).newInstance();
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
      init(new ServerScope(args));
   }

   public ServerScope getGlobal() {
      return this.glob;
   }

   /*
    * Start xmlBlaster using the properties from utilGlob
    * without loading <tt>xmlBlaster.properties</tt> again
    * @param utilGlob The environment for this server instance
    */
   public void init(org.xmlBlaster.util.Global utilGlob) {
      org.xmlBlaster.engine.ServerScope gg =
          new org.xmlBlaster.engine.ServerScope(utilGlob.getProperty().getProperties(), false);
      utilGlob.setId(gg.getId()); // Inherit backwards the cluster node id
      init(gg);
   }

   /*
    * Start xmlBlaster using the given properties
    * and load <tt>xmlBlaster.properties</tt>.
    * @param props The environment for this server instance
    */
   public void init(java.util.Properties props) {
      this.init(new org.xmlBlaster.engine.ServerScope(props, true));
   }

   public final void init(ServerScope glob)
   {
      this.startupTime = System.currentTimeMillis();

      this.glob = glob;

      this.ME = "Main" + glob.getLogPrefixDashed();
      //try { log.info(ME, glob.getDump()); } catch (Throwable e) { System.out.println(ME + ": " + e.toString()); e.printStackTrace(); }

      showUsage = glob.wantsHelp();
      Thread.currentThread().setName("XmlBlaster.MainThread");

      if (glob.wantsHelp())
         showUsage = true;
      else if (glob.getErrorText() != null) {
         usage();
         log.severe(glob.getErrorText());
         if (glob.isEmbedded())
            throw new IllegalArgumentException(glob.getErrorText());
         else
            System.exit(0);
      }
      
      boolean redirect = glob.getProperty().get("xmlBlaster/stdoutStderrToLogging", false);
      if (redirect) {
          String filterSetOut = glob.getProperty().get("xmlBlaster/stdoutSuppressSet", (String)null);
          String filterSetErr = glob.getProperty().get("xmlBlaster/stderrSuppressSet", (String)null);
          log.info("Redirecting stdout and stderr to java.util.logging as -xmlBlaster/stdoutStderrToLogging=" + redirect + " -xmlBlaster/stdoutSuppressSet=" + filterSetOut + " -xmlBlaster/stderrSuppressSet=" + filterSetErr);
    	  this.stdoutStderrRedirector = new StdoutStderrRedirector(filterSetOut, filterSetErr, ";");
    	  this.stdoutStderrRedirector.redirect();
    	  //System.out.println("First stdout redirect test");
    	  //System.err.println("First stderr redirect test");
    	  //Exception e = new Exception("First stderr exception redirect test");
    	  //e.printStackTrace();
      }

      long sleepOnStartup = glob.getProperty().get("xmlBlaster/sleepOnStartup", 0L);
      if (sleepOnStartup > 0L) {
        log.info("Going to sleep as configured xmlBlaster/sleepOnStartup=" + sleepOnStartup);
        try { Thread.sleep(sleepOnStartup);
        } catch(InterruptedException e) { log.warning("Caught exception during xmlBlaster/sleepOnStartup=" + sleepOnStartup + ": " + e.toString()); }
      }

      boolean useJdbcManagerDelegate = glob.getProperty().get("xmlBlaster/useJdbcManagerDelegate", true);
      if (useJdbcManagerDelegate) {
         this.panicErrorCodes = ErrorCode.RESOURCE_DB_UNAVAILABLE.getErrorCode();
      }
      else {
         this.panicErrorCodes = ErrorCode.RESOURCE_DB_UNKNOWN.getErrorCode()+","+ErrorCode.RESOURCE_DB_UNAVAILABLE.getErrorCode();
      }

      this.panicErrorCodes = glob.getProperty().get("xmlBlaster/panicErrorCodes", this.panicErrorCodes);
      log.fine("Following errorCodes do an immediate exit: " + this.panicErrorCodes);
      if (useJdbcManagerDelegate && this.panicErrorCodes.indexOf(ErrorCode.RESOURCE_DB_UNKNOWN.toString()) != -1)
          log.severe("You can not use 'xmlBlaster/panicErrorCodes' " +
                          ErrorCode.RESOURCE_DB_UNKNOWN.toString() +
                          " with xmlBlaster/useJdbcManagerDelegate=true. This could result in DB-less operation with persistent entries handled as transient entries which are lost on restart");
      // Add us as an I_XmlBlasterExceptionHandler ... (done again in changeRunlevel() below, but this is too late as first JDBC access can be in RL0
      if (XmlBlasterException.getExceptionHandler() == null)
         XmlBlasterException.setExceptionHandler(this); // see public void newException(XmlBlasterException e);

      int runlevel = glob.getProperty().get("runlevel", RunlevelManager.RUNLEVEL_RUNNING);
      try {
         runlevelManager = glob.getRunlevelManager();
         runlevelManager.addRunlevelListener(this);
         runlevelManager.initPluginManagers();
         runlevelManager.changeRunlevel(runlevel, false);
      } catch (Throwable e) {
         if (e instanceof XmlBlasterException) {
            log.severe(e.getMessage());
         }
         else {
            e.printStackTrace();
            log.severe(e.toString());
         }
         if (glob.isEmbedded()) {
            throw new IllegalArgumentException(e.toString());
         }
         else {
            log.severe("Changing runlevel to '" + RunlevelManager.toRunlevelStr(runlevel) + "' failed, good bye");
            System.exit(1);
         }
      }

      boolean useKeyboard = glob.getProperty().get("useKeyboard", true);
      if (!useKeyboard) {
         blockThread();
      }

      // Used by testsuite to switch off blocking, this Main method is by default never returning:
      boolean doBlocking = glob.getProperty().get("doBlocking", true);

      if (doBlocking) {
         checkForKeyboardInput();
      }
   }

   public void blockThread() {
      while (true) {
         try { Thread.sleep(100000000L);
         } catch(InterruptedException e) { log.warning("Caught exception: " + e.toString()); }
      }
      /*
      //  Exception in thread "main" java.lang.IllegalMonitorStateException:
      try { Thread.currentThread().wait();
      } catch(InterruptedException e) { log.warn(ME, "Caught exception: " + e.toString()); }
      */
      //orb.run();
   }

   /** Same as shutdown() but does additionally an engine.global.shutdown() */
   public synchronized void destroy() {
      shutdown();
      if (this.glob != null) {
         this.glob.shutdown();
         this.glob = null;
      }
   }

   /**
    * Instructs the RunlevelManager to shut down, which causes all object adapters to shut down.
    * <p />
    * The drivers are removed.
    */
   public synchronized void shutdown()
   {
      if (inShutdownProcess)
         return;

      inShutdownProcess = true;

      int errors = 0;
      try {
         errors = runlevelManager.changeRunlevel(RunlevelManager.RUNLEVEL_HALTED, true);
      }
      catch(XmlBlasterException e) {
         log.severe("Problem during shutdown: " + e.toString());
      }
      if (errors > 0) {
         log.warning("There were " + errors + " errors during shutdown.");
      }
      else {
         if (log.isLoggable(Level.FINE)) log.fine("shutdown() done");
      }
   }

   /**
    * Access the authentication singleton.
    */
   public I_Authenticate getAuthenticate() {
      return glob.getAuthenticate();
   }

   /**
    * Access the xmlBlaster singleton.
    */
   public I_XmlBlaster getXmlBlaster() {
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
   private void checkForKeyboardInput() {
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
                  log.info("Invoking control panel GUI ...");
                  controlPanel = new MainGUI(glob, this); // the constructor sets the variable controlPanel
                  controlPanel.run();
               }
               else
                  controlPanel.showWindow();
            }
            else if (line.toLowerCase().equals("gc")) {
               long totalMem = Runtime.getRuntime().totalMemory();
               long freeMem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
               System.gc();
               log.info("Garbage collector has run, total/free bytes before="+totalMem+"/"+freeMem+", after="+Runtime.getRuntime().totalMemory()+"/"+(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
            }
            else if (line.toLowerCase().startsWith("r")) {
               if (line.length() > 1) {
                  String tmp = line.substring(1).trim();
                  int runlevel = -10;
                  try { runlevel = Integer.parseInt(tmp.trim()); } catch(NumberFormatException e) { log.severe("Invalid run level '" + tmp + "', it should be a number."); };
                  try { runlevelManager.changeRunlevel(runlevel, true); } catch(XmlBlasterException e) { log.severe(e.toString()); }
               }
               else
                  log.info("Current runlevel is " + RunlevelManager.toRunlevelStr(runlevelManager.getCurrentRunlevel()) + "=" + runlevelManager.getCurrentRunlevel() + "");
            }
            else if (line.toLowerCase().startsWith("j")) {
               if (line.length() > 1) {
                  // ObjectName = org.xmlBlaster:nodeClass=node,node="heron"
                  // j org.xmlBlaster:nodeClass=node,node="heron"/action=getFreeMemStr
                  // j org.xmlBlaster:nodeClass=node,node="heron"/action=usage?action=usage

                  // java  -Djmx.invoke.getters=set ... org.xmlBlaster.Main
                  // j org.xmlBlaster:nodeClass=node,node="heron"/action=getLastWarning?action=getLastWarning
                  // j org.xmlBlaster:nodeClass=node,node="heron"/action=getLastWarning
                  // j org.xmlBlaster:nodeClass=node,node="avalon_mycomp_com",clientClass=client,client="heron.mycomp.com",sessionClass=session,session="1"/action=getConnectionState
                  String tmp = line.substring(1).trim();
                  try {
                     System.out.println("Invoking: " + tmp);
                     Object obj = JmxWrapper.getInstance(this.glob).invokeCommand(tmp);
                     if (obj instanceof String[]) {
                        String[] str = (String[])obj;
                        for(int i=0; i<str.length; i++)
                           System.out.println(str[i]);
                     }
                     else {
                        System.out.println(obj);
                     }
                  } catch(XmlBlasterException e) { log.severe(e.toString()); }
               }
               else
                  log.info("Please pass a JMX object name to query");
            }
            else if (line.toLowerCase().startsWith("d")) {
               try {
                  String fileName = null;
                  if (line.length() > 1) fileName = line.substring(1).trim();

                  if (fileName == null) {
                     System.out.println(glob.getDump());
                     log.info("Dump done");
                  }
                  else {
                     FileLocator.writeFile(fileName, glob.getDump());
                     log.info("Dumped internal state to '" + fileName + "'");
                  }
               }
               catch(XmlBlasterException e) {
                  log.severe("Sorry, dump failed: " + e.getMessage());
               }
               catch(Throwable e) {
                  log.severe("Sorry, dump failed: " + e.toString());
               }
            }
            else if (line.toLowerCase().equals("q")) {
               shutdown();
               if (glob == null || !glob.isEmbedded())
                  System.exit(0);
            }
            else // if (keyChar == '?' || Character.isLetter(keyChar) || Character.isDigit(keyChar))
               keyboardUsage();
         }
         catch (IOException e) {
            log.warning(e.toString() + " Keyboard input is disabled, we block this thread now");
            break;
         }
      }
      blockThread();
   }

   public boolean isHalted() {
      if( runlevelManager != null )
         return runlevelManager.isHalted();
      else return true;
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
    * @see org.xmlBlaster.engine.runlevel.I_RunlevelListener#runlevelChange(int, int, boolean)
    */
   public void runlevelChange(int from, int to, boolean force) throws XmlBlasterException {
      //if (log.isLoggable(Level.FINER)) log.call(ME, "Changing from run level=" + from + " to level=" + to + " with force=" + force);
      if (to == from)
         return;

      if (to > from) { // startup
         //if (to == RunlevelManager.RUNLEVEL_HALTED) {
         //   log.error(ME, "DEBUG ONLY ........");
         //   if (glob.getNodeId() == null)
         //      glob.setUniqueNodeIdName(createNodeId());
         //}
         if (to == RunlevelManager.RUNLEVEL_HALTED_POST) {
            this.startupTime = System.currentTimeMillis();
            boolean useSignalCatcher = glob.getProperty().get("useSignalCatcher", true);
            if (useSignalCatcher) {
               try {
                  this.signalCatcher = SignalCatcher.instance();
                  this.signalCatcher.register(this);
                  this.signalCatcher.catchSignals();
               }
               catch (Throwable e) {
                  log.warning("Can't register signal catcher: " + e.toString());
               }
            }
            // Add us as an I_XmlBlasterExceptionHandler ...
            if (XmlBlasterException.getExceptionHandler() == null)
               XmlBlasterException.setExceptionHandler(this); // see public void newException(XmlBlasterException e);
         }
         if (to == RunlevelManager.RUNLEVEL_STANDBY) {
         }
         if (to == RunlevelManager.RUNLEVEL_STANDBY_POST) {
            if (showUsage) {
               usage();  // Now we can display the complete usage of all loaded drivers
               shutdown();
               if (!glob.isEmbedded())
                  System.exit(0);
            }
         }
         if (to == RunlevelManager.RUNLEVEL_CLEANUP) {
         }
         if (to == RunlevelManager.RUNLEVEL_RUNNING) {
         }
         if (to == RunlevelManager.RUNLEVEL_RUNNING_POST) {
            log.info(Global.getMemoryStatistic());
            String duration = Timestamp.millisToNice(System.currentTimeMillis() - this.startupTime);
            // TEST
            //new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getXBStore", "", null); 
            if (controlPanel == null) {
               if (XbFormatter.withXtermColors()) System.out.println(XbFormatter.BLACK_GREEN);
               final String bound = "|";
               String ver = bound + " XmlBlaster cluster node <" + glob.getId() + "> v" + glob.getReleaseId() + " " + glob.getBuildTimestamp();
               int width = ver.length() + 6;
               if (width < 48) width = 48;
               ReplaceVariable sh = new ReplaceVariable();
               String line = sh.charChain('-', width-2);
               System.out.println("");
               System.out.println(" "+line+" ");
               System.out.println(ver + sh.charChain(' ', width-ver.length()-1) + bound);
               boolean useKeyboard = glob.getProperty().get("useKeyboard", true);
               if (useKeyboard) {
                 String help = bound + " READY " + duration + " - press <enter> for options";
                 System.out.println(help + sh.charChain(' ', width-help.length()-1) + bound);
               } else {
                 String help = bound + " READY " + duration + " - no keyboard input available";
                 System.out.println(help + sh.charChain(' ', width-help.length()-1) + bound);
               }
               System.out.println(" "+line+" ");
               if (XbFormatter.withXtermColors()) System.out.println(XbFormatter.ESC);
            }
            else
               log.info("xmlBlaster is ready for requests " + duration);
         }
      }
      if (to <= from) { // shutdown
         if (to == RunlevelManager.RUNLEVEL_RUNNING_PRE) {
            if (log.isLoggable(Level.FINE)) log.fine("Shutting down xmlBlaster to runlevel " + RunlevelManager.toRunlevelStr(to) + " ...");
         }
         if (to == RunlevelManager.RUNLEVEL_HALTED_PRE) {
            synchronized (this) {
               if (this.glob != null) {
                 this.glob.shutdown();
               }
            }
            log.info("XmlBlaster halted.");
         }

         if (to == RunlevelManager.RUNLEVEL_HALTED) {
            synchronized (this) {
               if (this.signalCatcher != null) {
                  this.signalCatcher.removeSignalCatcher();
                  this.signalCatcher = null;
               }
            }
         }
      }
   }

   public void newException(XmlBlasterException e) {
      boolean serverScope = (e.getGlobal() != null && e.getGlobal().getObjectEntry("org.xmlBlaster.engine.Global") != null);
      if (!e.isServerSide() && !serverScope) // isServerSide checks if we are ServerScope implementation, serverScope checks if we are a util.Global in the context of a server
         return;
      // Typically if the DB is lost: ErrorCode.RESOURCE_DB_UNKNOWN
      if (this.panicErrorCodes.indexOf(e.getErrorCodeStr()) != -1) {
         log.severe("PANIC: Doing immediate shutdown caused by exception: " + e.getMessage());
         e.printStackTrace();
         log.severe(Global.getStackTraceAsString(e));
         log.severe("Complete stack trace (all threads at the time of shutdown: " + ThreadLister.getAllStackTraces());
         SignalCatcher sc = this.signalCatcher;
         if (sc != null) {
            sc.removeSignalCatcher();
         }
         System.exit(1);
      }
   }

   /**
   * You will be notified when the runtime exits.
   * @see I_SignalListener#shutdownHook()
   */
   public void shutdownHook() {
      destroy();
   }

   /**
    * Keyboard input usage.
    */
   private void keyboardUsage() {
      if (XbFormatter.withXtermColors()) System.out.println(XbFormatter.BLACK_LTGREEN);
      System.out.println("");
      System.out.println("----------------------------------------------------------");
      System.out.println("XmlBlaster " + ((glob != null) ? glob.getVersion() : "") +
                    ((glob != null) ? (" build " + glob.getBuildTimestamp()) : ""));
      System.out.println("Following interactive keyboard input is recognized:");
      System.out.println("Key:");
      System.out.println("   g             Popup the control panel GUI.");
      System.out.println("   r <run level> Change to run level (0,3,6,9).");
      try {
         if (JmxWrapper.getInstance(this.glob).isActivated())
            System.out.println("   j <JMX call>  For example 'j org.xmlBlaster:nodeClass=node,node=\""+this.glob.getStrippedId()+"\"/action=getFreeMemStr'");
      } catch (XmlBlasterException e) {
         e.printStackTrace();
      }
      System.out.println("   d <file name> Dump internal state of xmlBlaster to file.");
      System.out.println("   q             Quit xmlBlaster.");
      System.out.println("----------------------------------------------------------");
      if (XbFormatter.withXtermColors()) System.out.println(XbFormatter.ESC);
   }

   /**
    * Command line usage.
    */
   private void usage() {
      System.out.println("-----------------------" + glob.getVersion() + "-------------------------------");
      System.out.println("java org.xmlBlaster.Main <options>");
      System.out.println("----------------------------------------------------------");
      System.out.println("   -h                  Show the complete usage.");
      System.out.println("");
//      try { System.out.println(glob.getProtocolManager().usage()); } catch (XmlBlasterException e) { log.warn(ME, "No usage: " + e.toString()); }
      // Depending on the current run level not all drivers may be visible:
      I_Driver[] drivers = glob.getPluginRegistry().getPluginsOfInterfaceI_Driver(); // getPluginsOfGroup("protocol");
      for (int i=0; i < drivers.length; i++)
         System.out.println(drivers[i].usage());

      System.out.println("");
      System.out.println(org.xmlBlaster.engine.cluster.ClusterManager.staticUsage());
      System.out.println("");
      System.out.println(glob.usage());
      System.out.println("");
      System.out.println("Other stuff:");
      System.out.println("   -xmlBlaster/acceptWrongSenderAddress/<subjectId>  <subjectId> is for example 'joe' [false]");
      System.out.println("                              true: Allows user 'joe' to send wrong sender address in PublishQos");
      System.out.println("   -xmlBlaster/sleepOnStartup Number of milli seconds to sleep before startup [0]");
      System.out.println("   -useKeyboard false         Switch off keyboard input, to allow xmlBlaster running in background [true]");
      System.out.println("   -doBlocking  false         Switch off blocking, the main method is by default never returning [true]");
      System.out.println("   -admin.remoteconsole.port  If port > 1000 a server is started which is available with telnet [2702]");
      System.out.println("   -xmlBlaster.isEmbedded     If set to true no System.exit() is possible [false]");
      System.out.println("   -wipeOutJdbcDB true        Destroy the complete JDBC persistence store entries of prefix=XMLBLASTER (DANGER)");
      System.out.println("   -xmlBlaster/jmx/HtmlAdaptor       Set to true to enable JMX HTTP access on 'http://localhost:8082' [false]");
      System.out.println("   -xmlBlaster/jmx/XmlBlasterAdaptor Set to true to enable JMX xmlBlaster adaptor access for swing GUI 'org.xmlBlaster.jmxgui.Main' [false].");
      System.out.println("   java -Dcom.sun.management.jmxremote ...  Switch on JMX support with jconsole (JDK >= 1.5).");
      System.out.println("   -xmlBlaster/jmx/observeLowMemory      Write a log error when 90% of the JVM memory is used (JDK >= 1.5) [true]");
      System.out.println("   -xmlBlaster/jmx/memoryThresholdFactor Configure the log error memory threshhold (defaults to 90%) (JDK >= 1.5) [0.9]");
      System.out.println("   -xmlBlaster/jmx/exitOnMemoryThreshold If true xmlBlaster stops if the memoryThresholdFactor is reached (JDK >= 1.5) [false]");
      System.out.println("----------------------------------------------------------");
      System.out.println("Example:");
      System.out.println("   java org.xmlBlaster.Main -cluster false");
      System.out.println("   java org.xmlBlaster.Main -cluster.node.id heron");
      System.out.println("   java org.xmlBlaster.Main -propertyFile somewhere/xmlBlaster.properties -pluginsFile somewhere/plugins.xml");
      System.out.println("   java org.xmlBlaster.Main -bootstrapPort 3412");
      System.out.println("   java org.xmlBlaster.Main -plugin/ior/iorFile /tmp/XmlBlaster_Ref.ior");
      System.out.println("   java org.xmlBlaster.Main -logging/org.xmlBlaster.engine FINE");
      System.out.println("   java org.xmlBlaster.Main -logging/org.xmlBlaster.util.protocol.RequestReplyExecutor FINEST   (dumps SOCKET messages)");
      System.out.println("   java org.xmlBlaster.Main -plugin/xmlrpc/hostname 102.24.64.60 -plugin/xmlrpc/port 8081");
      System.out.println("   java -Dcom.sun.management.jmxremote org.xmlBlaster.Main");
      System.out.println("   java -Djava.util.logging.config.file=config/logging.properties org.xmlBlaster.Main");
      System.out.println("   java org.xmlBlaster.Main -?");
      System.out.println("See xmlBlaster.properties for more options");
      System.out.println("");
   }


   /**
    *  Invoke: java org.xmlBlaster.Main
    */
   public static void main( String[] args )
   {
      new Main(args);
   }
}
