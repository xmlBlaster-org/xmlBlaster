package org.xmlBlaster.test.memoryleak;
import org.xmlBlaster.util.ThreadLister;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.util.qos.SessionQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.test.Util;

import junit.framework.*;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.ArrayList;
import java.util.Iterator;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;

/**
 * This does a twofold test by creating a number of connection, an
 * almost infinite number of times. It tests two stuff:
 * 1. if the client side is leaking threads.
 * 2. if Jacorb contains a locking bug.
 *
 * @author <a href="mailto:pra@tim.se">Peter Antman</a>
 * @version $Revision: 1.4 $
 */

public class TestThreadLeak extends TestCase implements I_Callback {
   private static String ME = "TestThreadLeak";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestThreadLeak.class.getName());
   private String fileName;
   private int noConnections = 10;
   private boolean noError = true;
   private ArrayList connections = new ArrayList();
   private int maxThreadDiff = 500;
   private String pid;
   private String osName;
   /** Time a connection should live, before beeing taken down */
   private long cttl = 5000;
   
   public TestThreadLeak (Global glob, String testName) throws Exception{
      super(testName);
      this.glob = glob;

      fileName = glob.getProperty().get("pidFileName", (String)null);
   }

   /**
    * Sets up the fixture. 
   */
   protected void setUp() throws Exception 
   {
      String[] args = {
         "-protocol", 
         "SOCKET", //"SOCKET",
         "-session.maxSessions",
         "20"
      };
      glob.init(args);

      // if we have a filename where a pid is, wait until that file has shown up
      // But now more that 5 times
      if ( fileName != null) {
         int i = 0;
         File file = new File(fileName);
         while (!file.exists()) {
         i++;
         if ( i > 4) {
            Assert.fail("We where given a pid filename " + file + " but could not find it, giving up");
         } // end of if ()
         
         Thread.sleep(2000);
         } // end of while ()
         BufferedReader r = new BufferedReader(new FileReader(file));
         pid = r.readLine();
      } // end of if ()

      osName = System.getProperty("os.name");

   }
   

   void dumpThreadStack() throws Exception {
      if ( pid != null && !osName.startsWith("Window")) {
         Runtime runtime = Runtime.getRuntime();
         Process p = runtime.exec("kill -3 " + pid);
         p.waitFor();
      } else {
         log.info("Could not dump stack pid="+pid+" os="+osName);
      } // end of else
      

   }

   void handleLock(ConnectorWorker wr) throws Exception {
      dumpThreadStack();
      //Assert.fail("Thread lock in connector worker "+wr + " giving up");
   }

   public void testThreadLeakage() throws Exception {
      int startNoThreads = -1;
      int lastNoThreads = -1;
      int round = 0;
      while ( noError ) {
         round++;
         log.info("Doing a new connection round no " + round);
         for ( int i = 0; i< noConnections;i++) {
            ConnectorWorker conn = new ConnectorWorker(glob, cttl);
            connections.add(conn);
         } // end of for ()
         
         // Wait a while
         System.gc();
         Thread.sleep(1000);
         System.gc();
         Thread.sleep(1000);
         
         // Count threads, if more than maxThreadDiff has been created since
         // the first round: fail
         int noThreads = ThreadLister.countThreads();
         if (startNoThreads != -1 ) {
            startNoThreads = noThreads;
            lastNoThreads = noThreads;
         } else {
            // Check how many since first round
            int firstDiff = noThreads - startNoThreads;
            int lastDiff = noThreads - lastNoThreads;
            log.info("No of thread created since start:"+firstDiff+"; number of threads created since last round: " + lastDiff);
            lastNoThreads = noThreads;
            if ( firstDiff > maxThreadDiff) {
               ThreadLister.listAllThreads(System.out);
               Assert.fail("Max number of new threads reached " +firstDiff +
                           " number of threads created since first round: XmlBlaster is leaking huge numbers of threads. Happened in round " + round);
            } // end of if ()
            
         } // end of else
         
         // Wait a  while for connections to stop
         Thread.sleep( noConnections*1000 );
         
         // Check that all connections are finished.
         Iterator c = connections.iterator();
         while ( c.hasNext() ) {
            ConnectorWorker w = (ConnectorWorker)c.next();

            // Check that its NOT alive
            if ( w.isAlive() ) {
               // Opps, do we have a lock here

               // We give it five rounds if its still in Connecting state we abort
               int j = 0;
               while (w.isAlive() && j < 4) {
                  log.warning("Possible lock of connection " + w + " detected, waiting 30 s round "+j);
                  j++;
                  Thread.sleep(30*1000);
                  if ( j > 3) {
                     log.severe("Possible lock of connection " + w + " detected, aborting");
                     noError = false;
                     handleLock(w);
                  } // end of if ()
                  
               } // end of while ()
               
            } // end of if ()
            Throwable t = w.getException();
            if ( t != null) {
               log.severe("Connection had exception, giving up : "+t);
               t.printStackTrace();
               Assert.fail("Connection had exception, giving up : "+t);
            } // end of if ()
            
         } // end of while ()
         connections.clear();



      } // end of while ()
      


   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey,
byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[]
content, UpdateQos updateQos)
   {
      log.info("Receiving update of a message " + updateKey.getOid() + " for subId: " + updateQos.getSubscriptionId() );
      log.fine("Got message " + new String(content));
      return "";
   }
   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() throws Exception 
   {
      
      TestSuite suite= new TestSuite();
      suite.addTest(new TestThreadLeak(new Global(),
"testThreadLeakage"));
      return suite;
   }

   /**
    * Invoke: 
    * <pre>
    *   java org.xmlBlaster.test.mime.TestXPathSubscribeFilter
    *   java -Djava.compiler= junit.textui.TestRunner -noloading
org.xmlBlaster.test.mime.TestXPathSubscribeFilter
    * <pre>
    */
   public static void main(String args[]) throws Exception 
   {
      try {
         Global glob = new Global();
         if (glob.init(args) != 0) {
            System.err.println(ME + ": Init failed");
            System.exit(1);
         }
         TestThreadLeak testSub = new TestThreadLeak(glob, "testThreadLeak");
         testSub.setUp();
         testSub.testThreadLeakage();
         testSub.tearDown();
      } catch (Throwable e) {
         e.printStackTrace();
         System.exit(0);
      } // end of try-catch

   }
   

   /**
    * Connect to XmlBlaster and disconnect after timeout milis.
    */
   class ConnectorWorker implements Runnable {
      Global glob;
      long timeout;
      private I_XmlBlasterAccess con = null;
      Thread internalThread;
      Throwable ie;
      ConnectReturnQos retQos;
      volatile String state = "CREATED";
      volatile long started;
      public ConnectorWorker(Global glob, long timeout) {
         this.glob = glob.getClone(null);
         this.timeout = timeout;
         internalThread = new Thread(this);
         internalThread.start();
      }

      public void run() {
         started = System.currentTimeMillis();
         state = "RUNNING";
         if ( Thread.currentThread() != internalThread ) {
            ie = new RuntimeException("Only internal thread allowed");
            throw (RuntimeException)ie;
         } // end of if ()
         try {
            // Connect
            state = "CONNECTING";
            con = glob.getXmlBlasterAccess();
            ConnectQos qos = new ConnectQos(glob, "test", "dummy");

            retQos = con.connect(qos, TestThreadLeak.this); // Login to xmlBlaster
            log.info("Connected "+ this);
            state = "CONNECTED";
            Thread.sleep(timeout);

            // Disconnect
            state = "DISCONNECTING";
            con.disconnect(null);
            state = "DISCONNECTED";
            con=null;
            glob.shutdown();
            glob = null;
         } catch (Throwable e) {
            ie = e;
            log.severe("Giving up " + e);
         } // end of try-catch
         
         
      }
      public long getAgeSeconds() {
         return (System.currentTimeMillis()-started)/1000;
         
      }

      public String getState() {
         return state;
      }

      public String toString() {
         String s = super.toString();
         String q = "";
         if ( retQos != null) {
            SessionQos ses = retQos.getSessionQos() ;
            q =  "secretId="+ses.getSecretSessionId() + " publicId="+ses.getPublicSessionId();
         } // end of if ()
         
         return s+":"+state+" age="+getAgeSeconds()+" seconds old, (thread="+internalThread.getName()+") "+q;
      }
      
      public Throwable getException() {
         return ie;
      }

      public boolean isAlive() {
         return internalThread.isAlive();
      }
      
   }
} // TestThreadLeak
