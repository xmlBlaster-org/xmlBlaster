/*------------------------------------------------------------------------------
Name:      EmbeddedXmlBlaster.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to create/start/stop a xmlBlaster server in a thread
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.I_Main;
import org.xmlBlaster.engine.runlevel.RunlevelManager;
import org.xmlBlaster.util.classloader.ClassLoaderFactory;

import java.net.URLClassLoader;


/**
 * Helper to create/start/stop a xmlBlaster server instance in a thread.
 * <p />
 * TODO: change to use util.Global (or engine.Global as xmlBlaster.Main needs it).
 */
public class EmbeddedXmlBlaster
{
   private static Logger log = Logger.getLogger(EmbeddedXmlBlaster.class.getName());
   private Global glob;
   /** Main.java renames the thread name, so we remember the original and restore it on shutdown */
   private String origThreadName;
   /** Key/value array, containing command line arguments or xmlBlaster.properties variables to be used */
   private org.xmlBlaster.I_Main xmlBlasterMain;


   /**
    * Creates an instance of xmlBlaster and starts the server.
    * <p />
    * The returned xmlBlaster handle allows to control more than one
    * xmlBlaster server simultaneously (on different ports).
    * @param serverPort Default bootstrapPort is 3412
    * @return the xmlBlaster handle, pass this to method stopXmlBlaster
    *         to shutdown the server again.
    */
   public static EmbeddedXmlBlaster startXmlBlaster(int serverPort)
   {
      Global glob = Global.instance().getClone(null);
      String[] args = {
         "-bootstrapPort", "" + serverPort,
         "-doBlocking", "false",
         "-xmlBlaster.isEmbedded", "true"
         };
      glob.init(args);
      EmbeddedXmlBlaster serverThread = new EmbeddedXmlBlaster(glob);
      serverThread.run();
      while(!serverThread.isReady()) {
         try { Thread.sleep(200L); } catch( InterruptedException i) {}
      }
      log.info("Server is up and ready");
      return serverThread;
   }


   /**
    * Creates an instance of xmlBlaster and starts the server.
    * <p />
    * @param args Key/value array, containing command line arguments or xmlBlaster.properties variables to be used
    * @return the xmlBlaster handle, pass this to method stopXmlBlaster
    *         to shutdown the server again.
    */
   public static EmbeddedXmlBlaster startXmlBlaster(String[] args)
   {
      return startXmlBlaster(args, (String)null);
   }

   public static EmbeddedXmlBlaster startXmlBlaster(String[] args, String clusterNodeId)
   {
      Global glob = new Global(args); 
      // Global glob = Global.instance().getClone(args);
      glob.setId(clusterNodeId);
      String[] args2 = {
         "-doBlocking", "false",
         "-xmlBlaster.isEmbedded", "true"
         };
      glob.init(args2);
      EmbeddedXmlBlaster serverThread = new EmbeddedXmlBlaster(glob);
      serverThread.run();
      while(!serverThread.isReady()) {
         try { Thread.sleep(200L); } catch( InterruptedException i) {}
      }
      log.info("Server is up and ready.");
      return serverThread;
   }


   /**
    * Creates an instance of xmlBlaster and starts the server.
    * <p />
    * @param glob The specific handle for this xmlBlaster server
    * @return the xmlBlaster handle, pass this to method stopXmlBlaster
    *         to shutdown the server again.
    */
   public static EmbeddedXmlBlaster startXmlBlaster(Global glob)
   {
      String[] args = {
         "-doBlocking", "false",
         "-xmlBlaster.isEmbedded", "true"
         };
      glob.init(args);
      EmbeddedXmlBlaster serverThread = new EmbeddedXmlBlaster(glob);
      serverThread.run();
      while(!serverThread.isReady()) {
         try { Thread.sleep(200L); }
         catch( InterruptedException i) {
            log.info("Server has been interrupted.");
         }
      }
      log.info("Server is up and ready.");
      return serverThread;
   }


   /**
    * Stop xmlBlaster server.
    * @param serverThread The handle you got from startXmlBlaster()
    */
   public static void stopXmlBlaster(EmbeddedXmlBlaster serverThread)
   {
      serverThread.stopServer(true);
   }


   /*
    * Constructor is private, create an instance through EmbeddedXmlBlaster.starXmlBlaster()
    */
   private EmbeddedXmlBlaster(Global glob) {
      this.glob = (glob == null) ? Global.instance() : glob;

      this.origThreadName = Thread.currentThread().getName(); // Main.java renames the thread, we remember the original name
   }

   /**
    * @return true if xmlBlaster has started and is ready for requests
    */
   private boolean isReady() {
      return this.xmlBlasterMain != null;
   }

   public org.xmlBlaster.I_Main getMain() {
      return this.xmlBlasterMain;
   }

   /**
    * @param sync if true the method blocks until the server is shutdown
    * In this case global.shutdown() is called after shutdown and the Global is not usable anymore.
    */
   public void stopServer(boolean sync) {
      try {
         log.info("Stopping the xmlBlaster server instance (sync=" + sync + ") ...");
         this.xmlBlasterMain.destroy();  // does a glob.shutdown() as well
         if (sync) {
            while(true) {
               if (this.xmlBlasterMain == null)
                  break;
               if (this.xmlBlasterMain.isHalted())
                  break;
               try { Thread.sleep(100L); }
               catch( InterruptedException i) {
                  log.info("Server has been interrupted");
               }
            }
            this.xmlBlasterMain = null;
            Thread.currentThread().setName(this.origThreadName);
            log.info("Server is down!");
         }
         else
            log.info("Server is processing shutdown!");
      }
      finally {
         if (log.isLoggable(Level.FINE)) log.fine("stopServer done");
         this.glob = null;
         this.xmlBlasterMain = null;
      }
   }

   /*
    * Start the server
    */
   public void run() {
      ClassLoaderFactory factory = glob.getClassLoaderFactory();
      URLClassLoader cl = null;

      try {
         cl = factory.getXmlBlasterClassLoader();
         if( log.isLoggable(Level.FINE)) log.fine("Get first instance of org.xmlBlaster.Main via own class loader.");

         this.xmlBlasterMain = (I_Main)cl.loadClass("org.xmlBlaster.Main").newInstance();
         // java.util.Properties props = glob.getProperty().getProperties();
         // this.xmlBlasterMain.init(props);
         this.xmlBlasterMain.init(glob);
         /*
         Class[] paramClasses = { glob.getArgs().getClass() };
         Object[] params = { glob.getArgs() };
         java.lang.reflect.Constructor constructor = mainClass.getDeclaredConstructor( paramClasses );
         mainObject = constructor.newInstance( params );
         if ( mainObject instanceof org.xmlBlaster.Main ) {
         }
         else {
            log.error( ME, "Error in constructing org.xmlBlaster.Main!");
            System.exit(-1);
         }
         */
         log.info("Successfully loaded org.xmlBlaster.Main instance with specific classloader");
      } catch(Throwable e) {
         if (cl != null)
            log.severe("Problems loading org.xmlBlaster.Main with ClassLoader "+cl.getClass().getName()+": " + e.toString() + " -> using default classloader");
         else
            log.severe("Problems loading org.xmlBlaster.Main (classloader = 'null' ???): " + e.toString() + " -> using default classloader");
         this.xmlBlasterMain = new org.xmlBlaster.Main(glob);
         log.info("Successfully loaded org.xmlBlaster.Main instance with default classloader");
      }
   }

   /**
    * Change the run level to the given newRunlevel.
    * <p />
    * See RUNLEVEL_HALTED etc.
    * <p />
    * Note that there are four main run levels:
    * <ul>
    *   <li>RUNLEVEL_HALTED</li>
    *   <li>RUNLEVEL_STANDBY</li>
    *   <li>RUNLEVEL_CLEANUP</li>
    *   <li>RUNLEVEL_RUNNING</li>
    * </ul>
    * and every RUNLEVEL sends a pre and a post run level event, to allow
    * the listeners to prepare or log before or after successfully changing levels.<br />
    * NOTE that the pre/post events are <b>no</b> run level states - they are just events.
    * @param newRunlevel The new run level we want to switch to
    * @param force Ignore exceptions during change, currently only force == true is supported
    * @return numErrors
    * @exception XmlBlasterException for invalid run level
    * @see org.xmlBlaster.engine.runlevel.RunlevelManager#changeRunlevel(int, boolean)
    */
   public int changeRunlevel(int newRunlevel, boolean force) throws XmlBlasterException {
      int numErrors = 1;
      if (newRunlevel == RunlevelManager.RUNLEVEL_HALTED ||
          newRunlevel == RunlevelManager.RUNLEVEL_STANDBY ||
          newRunlevel == RunlevelManager.RUNLEVEL_CLEANUP ||
          newRunlevel == RunlevelManager.RUNLEVEL_RUNNING)
         numErrors = getMain().getGlobal().getRunlevelManager().changeRunlevel(newRunlevel, force);
      return numErrors;
   }

   /**
    * Invoke: java org.xmlBlaster.util.EmbeddedXmlBlaster
    * <p />
    * instead of the JacORB ORB, which won't work.
    */
   public static void main(String args[])
   {
      EmbeddedXmlBlaster xmlBlaster = EmbeddedXmlBlaster.startXmlBlaster(args);
      EmbeddedXmlBlaster.stopXmlBlaster(xmlBlaster);
   }
} // class EmbeddedXmlBlaster

