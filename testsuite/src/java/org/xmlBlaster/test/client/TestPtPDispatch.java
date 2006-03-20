/*------------------------------------------------------------------------------
Name:      TestPtPDispatch.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.client;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.MsgUnit;

import org.xmlBlaster.test.util.PtPDestination;

import junit.framework.TestCase;


/**
 * <p>
 * Invoke examples:<br />
 * <pre>
 *   java junit.textui.TestRunner -noloading org.xmlBlaster.test.client.TestPtPDispatch
 *   java junit.swingui.TestRunner -noloading org.xmlBlaster.test.client.TestPtPDispatch
 * </pre>
 * @see org.xmlBlaster.client.I_XmlBlasterAccess
 */
public class TestPtPDispatch extends TestCase {

   private static String ME = "TestPtPDispatch";
   private final static long TIMEOUT = 5000L;
   private Global glob;
   private static Logger log = Logger.getLogger(TestPtPDispatch.class.getName());
   private PtPDestination[] destinations;
   private int numDestinations = 4;
   private int counter = 0;
   private String subjectName;
   //private boolean persistentMsg = true; 

   public TestPtPDispatch(String testName) {
      this(null, testName);
   }

   public TestPtPDispatch(Global glob, String testName) {
      super(testName);
      this.glob = glob;
      this.subjectName = testName;
   }

   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp() {
      this.glob = (this.glob == null) ? Global.instance() : this.glob;

      // this.counter = 0;

      this.destinations = new PtPDestination[this.numDestinations];
      for (int i=0; i < this.numDestinations; i++) 
         this.destinations[i] = new PtPDestination(this.glob, this.subjectName + "/" + (i+1));
      log.info("XmlBlaster is ready for testing");
      try {
         I_XmlBlasterAccess con = glob.getXmlBlasterAccess(); // Find orb
         String passwd = "secret";
         ConnectQos connectQos = new ConnectQos(glob, "src_" + this.subjectName, passwd); // == "<qos>...</qos>";
         if (log.isLoggable(Level.FINE)) log.fine("setUp: connectQos '" + connectQos.toXml() + "'");
         con.connect(connectQos, null);  // Login to xmlBlaster, register for updates

      }
      catch (XmlBlasterException e) {
          log.warning("setUp() - login failed: " + e.getMessage());
          fail("setUp() - login fail: " + e.getMessage());
      }
      catch (Exception e) {
          log.severe("setUp() - login failed: " + e.toString());
          e.printStackTrace();
          fail("setUp() - login fail: " + e.toString());
      }
   }

   private void prepare(boolean shutdownCb) {
      try {
         this.destinations[0].init(true, shutdownCb, 1, 1, 3, 1);
         this.destinations[1].init(false, shutdownCb, 1, 1, 3, 1);
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         assertTrue(false);
      }
   }
   
   private void cleanup() {
      for (int i=0; i < this.numDestinations-2; i++) this.destinations[i].shutdown(true);         
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown() {
      log.info("Entering tearDown(), test is finished");
      I_XmlBlasterAccess con = this.glob.getXmlBlasterAccess();
      try {
         EraseKey key = new EraseKey(this.glob, "testPtPDispatch"); 
         EraseQos qos = new EraseQos(this.glob);
         con.erase(key, qos);
      }
      catch(XmlBlasterException ex) {
         ex.printStackTrace();
      }
      finally {
         con.disconnect(null);
         // Global.instance().shutdown();
         // the unknown destinations must be handled inside the specific tests
         this.glob.shutdown();
         this.glob = null;
      }
   }

   /**
    * 
    * @param destNum the number of the destination int the destinations array
    *        for which the message is intended. If you want to send it to the
    *        subject (i.e. no specific session) you pass a negative value.
    * @param forceQueuing if true it will force queuing (refering to the destination).
    * @param expectEx true if you expect an exception here, false otherwise.
    * @param counts an int[] containing the expected amount of updates for each
    *        destination. NOTE this has to be filled out even if you expect an
    */
   private void doPublish(int destNum, boolean forceQueuing, boolean expectEx, int[] counts, long timeout, boolean persistent, String contentPrefix) {

      SessionName toSessionName = null;
      if (destNum < 0) toSessionName = new SessionName(this.glob, this.subjectName);
      else toSessionName = this.destinations[destNum].getSessionName();
      
      // String oid = "Message" + "-" + counter;
      log.info("Publishing a message " + toSessionName.getRelativeName() + " ...");
      PublishKey key = new PublishKey(this.glob, "testPtPDispatch");
      
      Destination destination = new Destination(this.glob, toSessionName);
      destination.forceQueuing(forceQueuing);
      PublishQos qos = new PublishQos(this.glob, destination);
      qos.setPersistent(persistent);
   
      String content = contentPrefix + "-" + this.counter;
      this.counter++;
      MsgUnit msgUnit = new MsgUnit(key, content.getBytes(), qos);

      try {
         this.glob.getXmlBlasterAccess().publish(msgUnit);
         assertTrue("did expect an exception after publishing to " + toSessionName.getRelativeName() + " here but got none", !expectEx);
      }
      catch (XmlBlasterException ex) {
         if (!expectEx) ex.printStackTrace();
         assertTrue("did'nt expect an exception after publishing to " + toSessionName.getRelativeName() + " here but got one: " + ex.getMessage(), expectEx);
      }
      log.info("Success: Publishing of message for " + toSessionName.getRelativeName() + " done");

      for (int i=0; i < this.destinations.length; i++) 
         this.destinations[i].check(timeout, counts[i]);
   }


   private void checkWithReconnect(int dest, boolean wantsPtP, int expected, long delay) {
      try {
         this.destinations[dest].shutdown(false);
         String sessionName = this.destinations[dest].getSessionName().getRelativeName();
         this.destinations[dest] = new PtPDestination(this.glob, sessionName);
         this.destinations[dest] .init(wantsPtP, false, 1, 1, 3, 1);
         this.destinations[dest] .check(delay, expected);
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         assertTrue(false);
      }
   }

   /**
    * Does a connect, waits for updates, compares the number of updates
    * with the expected and makes a disconnect.
    * 
    * @param dest the destination to use (to check)
    * @param expected the number of updates expected after a connect
    * @param delay the time in ms to wait between connect and check
    */
   private void checkWithoutPublish(PtPDestination dest, boolean wantsPtP, int expected, long delay) {
      try {
         dest.init(wantsPtP, false, 1, 1, 3, 1);
         dest.check(delay, expected);
         dest.shutdown(true);
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         assertTrue(false);
      }
   }

// -----------------------------------------------------------------------
   /** 5 messages are sent */
   private void noQueuingNoOverflow(boolean isPersistent, String msgPrefix) {
      boolean forceQueuing = false;
      boolean shutdownCb = false;
      prepare(shutdownCb);
      doPublish(-1, forceQueuing, false, new int[] {1,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(0 , forceQueuing, false, new int[] {1,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(1 , forceQueuing, true , new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(2 , forceQueuing, true , new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(3 , forceQueuing, true , new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      
      checkWithoutPublish(this.destinations[2], true, 0, TIMEOUT);
      checkWithoutPublish(this.destinations[3], false,0, TIMEOUT);
      cleanup();
   }

   /**
    * TEST: <br />
    */
   public void testNoQueuingNoOverflowTransient() {
      noQueuingNoOverflow(false, "NoQueuingNoOverflowTransient");
   }

   /**
    * TEST: <br />
    */
   public void testNoQueuingNoOverflowPersistent() {
      noQueuingNoOverflow(true, "NoQueuingNoOverflowPersistent");
   }

// -----------------------------------------------------------------------
   /** 12 messages are sent */
   private void noQueuingOverflow(boolean isPersistent, String msgPrefix) {
      boolean forceQueuing = false;
      boolean shutdownCb = true;
      prepare(shutdownCb);
      
      doPublish(0 , forceQueuing, false, new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(0 , forceQueuing, false, new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      // allow one overflow but now an exception should come ...
      doPublish(0 , forceQueuing, true, new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      // ... and again just to make sure ...
      doPublish(0 , forceQueuing, true, new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);

      doPublish(1 , forceQueuing, true , new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(1 , forceQueuing, true , new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(1 , forceQueuing, true , new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(1 , forceQueuing, true , new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      
      doPublish(2 , forceQueuing, true , new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(2 , forceQueuing, true , new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(2 , forceQueuing, true , new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(2 , forceQueuing, true , new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);

      // TODO add the tests on subject queue overflow here (configure subject queue first)
      //doPublish(-1, forceQueuing, false, new int[] {0,0,0,0}, TIMEOUT);
      //doPublish(-1, forceQueuing, false, new int[] {0,0,0,0}, TIMEOUT);
      //doPublish(-1, forceQueuing, false, new int[] {0,0,0,0}, TIMEOUT);
      //doPublish(-1, forceQueuing, false, new int[] {0,0,0,0}, TIMEOUT);

      cleanup();
   }

   /**
    * TEST: <br />
    */
   public void testNoQueuingOverflowTransient() {
      noQueuingOverflow(false, "NoQueuingOverflowTransient");
   }

   /**
    * TEST: <br />
    */
   public void testNoQueuingOverflowPersistent() {
      noQueuingOverflow(true, "NoQueuingOverflowPersistent");
   }

// -----------------------------------------------------------------------
   /** 5 messages are sent */
   private void queuingNoOverflow(boolean isPersistent, String msgPrefix) {
      boolean forceQueuing = true;
      boolean shutdownCb = false;
      prepare(shutdownCb);
      doPublish(-1, forceQueuing, false, new int[] {1,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(0 , forceQueuing, false, new int[] {1,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(1 , forceQueuing, true , new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(2 , forceQueuing, false, new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(3 , forceQueuing, false, new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);

      checkWithoutPublish(this.destinations[2], true, 1, TIMEOUT);
      checkWithoutPublish(this.destinations[3], false,0, TIMEOUT);
      // TODO check for dead letters. There should be one here  

      cleanup();
   }

   /**
    * TEST: <br />
    */
   public void testQueuingNoOverflowTransient() {
      queuingNoOverflow(false, "QueuingNoOverflowTransient");
   }
   
   /**
    * TEST: <br />
    */
   public void testQueuingNoOverflowPersistent() {
      queuingNoOverflow(true, "QueuingNoOverflowPersistent");
   }
   
// -----------------------------------------------------------------------
   /** 12 messages are sent */
   private void queuingOverflow(boolean isPersistent, String msgPrefix) {
      boolean forceQueuing = true;
      boolean shutdownCb = true;
      prepare(shutdownCb);
      doPublish(0 , forceQueuing, false, new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(0 , forceQueuing, false, new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(0 , forceQueuing, true , new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(0 , forceQueuing, true , new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);

      doPublish(1 , forceQueuing, true , new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(1 , forceQueuing, true , new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(1 , forceQueuing, true , new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(1 , forceQueuing, true , new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
   
      checkWithReconnect(0, true, 2, TIMEOUT);
   
      // this should not throw an exception since default queue configuration 
      // which allows many entries
      //doPublish(2 , forceQueuing, false, new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      //doPublish(2 , forceQueuing, false, new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      //doPublish(2 , forceQueuing, false, new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      //doPublish(2 , forceQueuing, false, new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);

      //doPublish(-1, forceQueuing, false, new int[] {1,0,0,0}, TIMEOUT);

      //checkForUnknown(this.destinations[2], true, 1, TIMEOUT);
      //checkForUnknown(this.destinations[3], false,0, TIMEOUT);
      // TODO check for dead letters. There should be one here  

      cleanup();
   }

   public void testQueuingOverflowTransient() {
      queuingOverflow(false, "QueuingOverflowTransient");
   }
   
   public void testQueuingOverflowPersistent() {
      queuingOverflow(true, "QueuingOverflowPersistent");
   }
   
// -----------------------------------------------------------------------

   private void subjectQueueNoOverflow(boolean isPersistent, String msgPrefix) {
      boolean forceQueuing = false;
      boolean shutdownCb = false;
      prepare(shutdownCb);
      
      doPublish(-1 , forceQueuing, false, new int[] {1,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(-1 , forceQueuing, false, new int[] {1,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      cleanup();
   }

   public void testSubjectQueueNoOverflowTransient() {
      subjectQueueNoOverflow(false, "SubjectQueueNoOverflowTransient");
   }
   
   public void testSubjectQueueNoOverflowPersistent() {
      subjectQueueNoOverflow(true, "SubjectQueueNoOverflowPersistent");
   }
   
// ------------------------------------------------------------------------

   private void subjectQueueOverflow(boolean isPersistent, String msgPrefix) {
      boolean shutdownCb = true;
      prepare(shutdownCb);
      
      boolean forceQueuing = false;
      doPublish(-1 , forceQueuing, true, new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);

      forceQueuing = true;
      doPublish(-1 , forceQueuing, false, new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(-1 , forceQueuing, false, new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(-1 , forceQueuing, false, new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(-1 , forceQueuing, false, new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(-1 , forceQueuing, true, new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      doPublish(-1 , forceQueuing, true, new int[] {0,0,0,0}, TIMEOUT, isPersistent, msgPrefix);
      cleanup();
   }

   public void testSubjectQueueOverflowTransient() {
      subjectQueueNoOverflow(false, "SubjectQueueNoOverflowTransient");
   }
   
   public void testSubjectQueueOverflowPersistent() {
      subjectQueueNoOverflow(true, "SubjectQueueNoOverflowPersistent");
   }
   
// -----------------------------------------------------------------------

   /**
    * Invoke: java org.xmlBlaster.test.client.TestPtPDispatch
    * <p />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.client.TestPtPDispatch</pre>
    */
   public static void main(String args[]) {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.out.println(ME + ": Init failed");
         System.exit(1);
      }

      TestPtPDispatch testSub = new TestPtPDispatch(glob, "TestPtPDispatch");

      testSub.setUp();
      testSub.testNoQueuingNoOverflowPersistent();
      testSub.tearDown();

      testSub.setUp();
      testSub.testNoQueuingNoOverflowTransient();
      testSub.tearDown();

      testSub.setUp();
      testSub.testQueuingNoOverflowPersistent();
      testSub.tearDown();
      
      testSub.setUp();
      testSub.testQueuingNoOverflowTransient();
      testSub.tearDown();

      testSub.setUp();
      testSub.testNoQueuingOverflowPersistent();
      testSub.tearDown();
      
      testSub.setUp();
      testSub.testNoQueuingOverflowTransient();
      testSub.tearDown();

      testSub.setUp();
      testSub.testQueuingOverflowPersistent();
      testSub.tearDown();
      
      testSub.setUp();
      testSub.testQueuingOverflowTransient();
      testSub.tearDown();

      testSub.setUp();
      testSub.testSubjectQueueNoOverflowPersistent();
      testSub.tearDown();

      testSub.setUp();
      testSub.testSubjectQueueNoOverflowTransient();
      testSub.tearDown();
      
      testSub.setUp();
      testSub.testSubjectQueueOverflowPersistent();
      testSub.tearDown();

      testSub.setUp();
      testSub.testSubjectQueueOverflowTransient();
      testSub.tearDown();
      
   }
}

