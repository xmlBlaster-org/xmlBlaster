/*------------------------------------------------------------------------------
Name:      EmbeddedXmlBlaster.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to create/start/stop a xmlBlaster server in a thread
Version:   $Id: EmbeddedXmlBlaster.java,v 1.9 2003/03/27 10:26:14 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.jutils.log.LogChannel;
import org.xmlBlaster.I_Main;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.classloader.ClassLoaderFactory;
import org.xmlBlaster.util.classloader.XmlBlasterClassLoader;

import org.xmlBlaster.client.key.SubscribeKey;


/**
 * Helper to create/start/stop a xmlBlaster server instance in a thread.
 * <p />
 * TODO: change to use util.Global (or engine.Global as xmlBlaster.Main needs it).
 */
public class EmbeddedXmlBlaster
{
   private static final String ME = "EmbeddedXmlBlaster";
   private Global glob;
   LogChannel log;
   /** Main.java renames the thread name, so we remember the original and restore it on shutdown */
   private String origThreadName;
   /** Key/value array, containing command line arguments or xmlBlaster.properties variables to be used */
   private String[] args;
   private org.xmlBlaster.I_Main xmlBlasterMain;


   /**
    * Creates an instance of xmlBlaster and starts the server.
    * <p />
    * The returned xmlBlaster handle allows to control more than one
    * xmlBlaster server simultaneously (on different ports).
    * @param port Default port is 3412
    * @return the xmlBlaster handle, pass this to method stopXmlBlaster
    *         to shutdown the server again.
    */
   public static EmbeddedXmlBlaster startXmlBlaster(int serverPort)
   {
      Global glob = Global.instance().getClone(null);
      String[] args = {
         "-port", "" + serverPort,
         "-doBlocking", "false",
         "-xmlBlaster.isEmbedded", "true"
         };
      glob.init(args);
      EmbeddedXmlBlaster serverThread = new EmbeddedXmlBlaster(glob);
      serverThread.run();
      while(!serverThread.isReady()) {
         try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}
      }
      glob.getLog(glob.getId()).info(ME, "Server is up and ready");
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
      Global glob = Global.instance().getClone(args);
      glob.setId(clusterNodeId);
      String[] args2 = {
         "-doBlocking", "false",
         "-xmlBlaster.isEmbedded", "true"
         };
      glob.init(args2);
      EmbeddedXmlBlaster serverThread = new EmbeddedXmlBlaster(glob);
      serverThread.run();
      while(!serverThread.isReady()) {
         try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}
      }
      glob.getLog(glob.getId()).info(ME, "Server is up and ready.");
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
         try { Thread.currentThread().sleep(200L); }
         catch( InterruptedException i) {
            glob.getLog(glob.getId()).info(ME, "Server has been interrupted.");
         }
      }
      glob.getLog(glob.getId()).info(ME, "Server is up and ready.");
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
      this.log = glob.getLog(glob.getId());
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
         log.info(ME, "Stopping the xmlBlaster server instance ...");
         this.xmlBlasterMain.shutdown();
         if (sync) {
            while(true) {
               if (this.xmlBlasterMain == null)
                  break;
               if (this.xmlBlasterMain.isHalted())
                  break;
               try { Thread.currentThread().sleep(100L); }
               catch( InterruptedException i) {
                  log.info(ME, "Server has been interrupted");
               }
            }
            this.xmlBlasterMain = null;
            Thread.currentThread().setName(this.origThreadName);
            log.info(ME, "Server is down!");
         }
         else
            log.info(ME, "Server is processing shutdown!");
      }
      finally {
         if (sync)
            glob.shutdown();
      }
   }

   /*
    * Start the server
    */
   public void run() {
      ClassLoaderFactory factory = glob.getClassLoaderFactory();
      XmlBlasterClassLoader cl = null;

      try {
         cl = factory.getXmlBlasterClassLoader();
         if( log.TRACE ) log.trace(ME, "Get first instance of org.xmlBlaster.Main via own class loader.");

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
         log.info(ME, "Successfully loaded org.xmlBlaster.Main instance with specific classloader");
      } catch(Throwable e) {
         if (cl != null)
            log.error(ME, "Problems loading org.xmlBlaster.Main with ClassLoader "+cl.getClass().getName()+": " + e.toString() + " -> using default classloader");
         else
            log.error(ME, "Problems loading org.xmlBlaster.Main (classloader = 'null' ???): " + e.toString() + " -> using default classloader");
         this.xmlBlasterMain = new org.xmlBlaster.Main(glob);
         log.info(ME, "Successfully loaded org.xmlBlaster.Main instance with default classloader");
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
   public int changeRunlevel(String newRunlevel, boolean force) throws XmlBlasterException {
      int numErrors = getMain().getGlobal().getRunlevelManager().changeRunlevel(org.xmlBlaster.engine.runlevel.RunlevelManager.RUNLEVEL_STANDBY, force);
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

