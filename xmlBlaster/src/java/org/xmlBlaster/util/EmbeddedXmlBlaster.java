/*------------------------------------------------------------------------------
Name:      EmbeddedXmlBlaster.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to create/start/stop a xmlBlaster server in a thread
Version:   $Id: EmbeddedXmlBlaster.java,v 1.2 2002/09/11 16:38:05 kkrafft2 Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.jutils.log.LogChannel;
import org.xmlBlaster.I_Main;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.classloader.ClassLoaderFactory;
import org.xmlBlaster.util.classloader.XmlBlasterClassLoader;

import org.xmlBlaster.client.*;


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
   /** Key/value array, containing command line arguments or xmlBlaster.properties variables to be used */
   private String[] args;
   private org.xmlBlaster.I_Main xmlBlasterMain = null;


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
      String[] args = new String[4];
      args[0] = "-port";
      args[1] = "" + serverPort;
      args[2] = "-doBlocking";
      args[3] = "false";
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
      String[] args2 = { "-doBlocking", "false" };
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
      String[] args = { "-doBlocking", "false" };
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
      this.glob = glob;
      this.log = glob.getLog(glob.getId());
   }

   /**
    * @return true if xmlBlaster has started and is ready for requests
    */
   private boolean isReady() {
      return xmlBlasterMain != null;
   }

   public org.xmlBlaster.I_Main getMain() {
      return xmlBlasterMain;
   }

   /**
    * @param sync if true the method blocks until the server is shutdown
    */
   public void stopServer(boolean sync) {
      log.info(ME, "Stopping the xmlBlaster server instance ...");
      xmlBlasterMain.shutdown();
      if (sync) {
         while(true) {
            if (xmlBlasterMain == null)
               break;
            if (xmlBlasterMain.isHalted())
               break;
            try { Thread.currentThread().sleep(100L); }
            catch( InterruptedException i) {
               log.info(ME, "Server has been interrupted");
            }
         }
         log.info(ME, "Server is down!");
      }
      else
         log.info(ME, "Server is processing shutdown!");
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

         xmlBlasterMain = (I_Main)cl.loadClass("org.xmlBlaster.Main").newInstance();
         java.util.Properties props = glob.getProperty().getProperties();
         xmlBlasterMain.init(props);
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
         xmlBlasterMain = new org.xmlBlaster.Main(glob);
         log.info(ME, "Successfully loaded org.xmlBlaster.Main instance with default classloader");
      }
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

