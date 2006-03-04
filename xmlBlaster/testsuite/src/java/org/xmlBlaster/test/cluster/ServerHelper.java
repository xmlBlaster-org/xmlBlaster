package org.xmlBlaster.test.cluster;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.EmbeddedXmlBlaster;

// for client connections:
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;


import java.util.Vector;
import java.io.File;

import junit.framework.*;

/**
 * Set up the cluster nodes. 
 * <p />
 * Don't forget to call tearDown() when you are done.
 *
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/cluster.html" target="others">Cluster requirement</a>
 */
public class ServerHelper {
   private String ME = "ServerHelper";
   private Global glob_;
   private static Logger log = Logger.getLogger(ServerHelper.class.getName());
   public static int heronPort = 7600;
   public static int avalonPort = 7601;
   public static int golanPort = 7602;
   public static int frodoPort = 7603;
   public static int bilboPort = 7604;

   private EmbeddedXmlBlaster heronThread, avalonThread, golanThread, frodoThread, bilboThread;

   private Global heronGlob, avalonGlob, golanGlob, frodoGlob, bilboGlob;

   public ServerHelper(Global glob, Logger log, String name) {
      ME = "ServerHelper-"+name;
      this.glob_ = glob;
      this.log = log;
      setUp();
   }

   /**
    * Find the property files, we look in the current directory
    * and in ./cluster
    * @exception IllegalArgumentException if you are in the wrong directory
    */
   private String findPropertyFile(String fn) {
      File f = new File(fn);
      if (f.canRead())
         return fn;
      f = new File("cluster" + File.separatorChar + fn);
      if (f.canRead())
         return f.getPath();
      log.severe("Can't locate property file " + fn + ". Please check your current directory or cluster directory");
      throw new IllegalArgumentException("Can't locate property file " + fn + ". Please check your current directory or cluster directory");
   }

   public Global getHeronGlob() {
      return heronGlob;
   }

   public Global getAvalonGlob() {
      return avalonGlob;
   }

   public Global getGolanGlob() {
      return golanGlob;
   }

   public Global getFrodoGlob() {
      return frodoGlob;
   }

   public Global getBilboGlob() {
      return bilboGlob;
   }

   public void initHeron() {
      String[] args = { "-propertyFile", findPropertyFile("heron.properties"), "-info[heron]", "true", "-call[heron]", "true" };
      heronGlob = this.glob_.getClone(args);
   }

   public void initAvalon() {
      String[] args = { "-propertyFile", findPropertyFile("avalon.properties") };
      avalonGlob = this.glob_.getClone(args);
   }

   public void initGolan() {
      String[] args = { "-propertyFile", findPropertyFile("golan.properties") };
      golanGlob = this.glob_.getClone(args);
   }

   public void initFrodo() {
      String[] args = { "-propertyFile", findPropertyFile("frodo.properties") };
      frodoGlob = this.glob_.getClone(args);
   }

   public void initBilbo() {
      String[] args = { "-propertyFile", findPropertyFile("bilbo.properties"), "-call[bilbo]", "false" };
      bilboGlob = this.glob_.getClone(args);
      if (!"bilbo".equals(bilboGlob.getId())) {
         String tmp = "Invalid cluster node id, check biblo.properties or" +
                   " change to the directory where the property files are!";
         log.severe(tmp);
         throw new IllegalArgumentException(tmp); // just to be shure
      }
   }

   public void startHeron() {
      heronThread = EmbeddedXmlBlaster.startXmlBlaster(heronGlob);
      log.info("'heron' is ready for testing on bootstrapPort " + heronPort);
   }

   public void startAvalon() {
      avalonThread = EmbeddedXmlBlaster.startXmlBlaster(avalonGlob);
      log.info("'avalon' is ready for testing on bootstrapPort " + avalonPort);
   }

   public void startGolan() {
      golanThread = EmbeddedXmlBlaster.startXmlBlaster(golanGlob);
      log.info("'golan' is ready for testing on bootstrapPort " + golanPort);
   }

   public void startFrodo() {
      frodoThread = EmbeddedXmlBlaster.startXmlBlaster(frodoGlob);
      log.info("'frodo' is ready for testing on bootstrapPort " + frodoPort);
   }

   public void startBilbo() {
      bilboThread = EmbeddedXmlBlaster.startXmlBlaster(bilboGlob);
      log.info("'bilbo' is ready for testing on bootstrapPort " + bilboPort);
   }

   public void stopHeron() {
      if (heronThread != null) { heronThread.stopServer(true); heronThread=null; }
   }

   public void stopAvalon() {
      if (avalonThread != null) { avalonThread.stopServer(true); avalonThread=null; }
   }

   public void stopGolan() {
      if (golanThread != null) { golanThread.stopServer(true); golanThread=null; }
   }

   public void stopFrodo() {
      if (frodoThread != null) { frodoThread.stopServer(true); frodoThread=null; }
   }

   public void stopBilbo() {
      if (bilboThread != null) { bilboThread.stopServer(true); bilboThread=null; }
   }

   /** Connect in fail save mode to a server node (as given in glob.getId()) */
   public I_XmlBlasterAccess connect(final Global glob, I_Callback cb) throws XmlBlasterException {
      final String clientName = "ClientTo[" + glob.getId() + "]";
      if (glob.getId() == null || glob.getId().length() < 1) log.severe("glob.getId() is not set");
      I_XmlBlasterAccess con = glob.getXmlBlasterAccess();

      con.registerConnectionListener(new I_ConnectionStateListener() {
            public void reachedAlive(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
               log.info("Changed from connection state " + oldState +
                                     " to " + ConnectionStateEnum.ALIVE + " with " +
                                     connection.getQueue().getNumOfEntries() + " queue entries pending" +
                                     ": We were lucky, reconnected to " + connection.getGlobal().getId());
            }
            public void reachedPolling(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
               log.warning("DEBUG ONLY: Changed from connection state " + oldState + " to " +
                                    ConnectionStateEnum.POLLING + ": Lost connection to " + connection.getGlobal().getId());
            }
            public void reachedDead(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
               log.severe("DEBUG ONLY: Changed from connection state " + oldState + " to " +
                                     ConnectionStateEnum.DEAD + ": Lost connection to " + connection.getGlobal().getId());
            }
         });

      ConnectQos qos = new ConnectQos(glob, clientName, "secret");
      ConnectReturnQos conRetQos = con.connect(qos, cb);

      log.info("Connected to xmlBlaster.");
      return con;
   }

   /**
    * Initialize the server setup ...
    * <p />
    * Is done automatically in constructor
    */
   private void setUp() {
      log.info("Entering setUp(), test starts");

      // The init is used for server nodes but used for client connections as well
      initHeron();
      initAvalon();
      initGolan();
      initFrodo();
      initBilbo();

      // Starts a cluster node
      //startHeron();
      //startAvalon();
      //startGolan();
      //startFrodo();
      //startBilbo();
      // Do it yourself
   }

   /**
    * Cleaning up ...
    * <p />
    * You have to call this when you are done.
    */
   public void tearDown() {
      log.info("Entering tearDown(), test is finished");

      try { Thread.currentThread().sleep(200); } catch( InterruptedException i) {} // Wait some time

      stopHeron();
      stopAvalon();
      stopGolan();
      stopFrodo();
      stopBilbo();
   }
}
