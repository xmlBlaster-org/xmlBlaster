package org.xmlBlaster.test.cluster;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.EmbeddedXmlBlaster;

// for client connections:
import org.xmlBlaster.util.*;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_ConnectionProblems;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.util.MsgUnit;


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
   private Global glob;
   private LogChannel log;
   public static int heronPort = 7600;
   public static int avalonPort = 7601;
   public static int golanPort = 7602;
   public static int frodoPort = 7603;
   public static int bilboPort = 7604;

   private EmbeddedXmlBlaster heronThread, avalonThread, golanThread, frodoThread, bilboThread;

   private Global heronGlob, avalonGlob, golanGlob, frodoGlob, bilboGlob;

   public ServerHelper(Global glob, LogChannel log, String name) {
      ME = "ServerHelper-"+name;
      this.glob = glob;
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
      log.error(ME, "Can't locate property file " + fn + ". Please check your current directory or cluster directory");
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
      heronGlob = glob.getClone(args);
   }

   public void initAvalon() {
      String[] args = { "-propertyFile", findPropertyFile("avalon.properties") };
      avalonGlob = glob.getClone(args);
   }

   public void initGolan() {
      String[] args = { "-propertyFile", findPropertyFile("golan.properties") };
      golanGlob = glob.getClone(args);
   }

   public void initFrodo() {
      String[] args = { "-propertyFile", findPropertyFile("frodo.properties") };
      frodoGlob = glob.getClone(args);
   }

   public void initBilbo() {
      String[] args = { "-propertyFile", findPropertyFile("bilbo.properties"), "-call[bilbo]", "false" };
      bilboGlob = glob.getClone(args);
      if (!"bilbo".equals(bilboGlob.getId())) {
         String tmp = "Invalid cluster node id, check biblo.properties or" +
                   " change to the directory where the property files are!";
         log.error(ME, tmp);
         throw new IllegalArgumentException(tmp); // just to be shure
      }
   }

   public void startHeron() {
      heronThread = EmbeddedXmlBlaster.startXmlBlaster(heronGlob);
      log.info(ME, "'heron' is ready for testing on port " + heronPort);
   }

   public void startAvalon() {
      avalonThread = EmbeddedXmlBlaster.startXmlBlaster(avalonGlob);
      log.info(ME, "'avalon' is ready for testing on port " + avalonPort);
   }

   public void startGolan() {
      golanThread = EmbeddedXmlBlaster.startXmlBlaster(golanGlob);
      log.info(ME, "'golan' is ready for testing on port " + golanPort);
   }

   public void startFrodo() {
      frodoThread = EmbeddedXmlBlaster.startXmlBlaster(frodoGlob);
      log.info(ME, "'frodo' is ready for testing on port " + frodoPort);
   }

   public void startBilbo() {
      bilboThread = EmbeddedXmlBlaster.startXmlBlaster(bilboGlob);
      log.info(ME, "'bilbo' is ready for testing on port " + bilboPort);
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
   public XmlBlasterConnection connect(final Global glob, I_Callback cb) throws XmlBlasterException {
      final String clientName = "ClientTo[" + glob.getId() + "]";
      if (glob.getId() == null || glob.getId().length() < 1) log.error(ME, "glob.getId() is not set");
      XmlBlasterConnection con = new XmlBlasterConnection(glob);

      con.initFailSave(new I_ConnectionProblems() {
            public void reConnected() {
               log.info(clientName, "I_ConnectionProblems: We were lucky, reconnected to " + glob.getId());
            }
            public void lostConnection() {
               log.warn(clientName, "I_ConnectionProblems: Lost connection to " + glob.getId());
            }
         });

      ConnectQos qos = new ConnectQos(glob, clientName, "secret");
      ConnectReturnQos conRetQos = con.connect(qos, cb);

      log.info(clientName, "Connected to xmlBlaster.");
      return con;
   }

   /**
    * Initialize the server setup ...
    * <p />
    * Is done automatically in constructor
    */
   private void setUp() {
      log.info(ME, "Entering setUp(), test starts");

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
      log.info(ME, "Entering tearDown(), test is finished");

      try { Thread.currentThread().sleep(200); } catch( InterruptedException i) {} // Wait some time

      stopHeron();
      stopAvalon();
      stopGolan();
      stopFrodo();
      stopBilbo();
   }
}
