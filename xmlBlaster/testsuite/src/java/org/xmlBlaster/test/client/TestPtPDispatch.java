/*------------------------------------------------------------------------------
Name:      TestPtPDispatch.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.client;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.MsgUnit;

import org.xmlBlaster.test.MsgInterceptor;

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

   public class PtPDestination {
      private Global global;
      private MsgInterceptor updateInterceptor;
      private SessionName sessionName;
      
      public PtPDestination(Global parentGlobal, String sessionName) {
         this.global = parentGlobal.getClone(null);
         this.sessionName = new SessionName(this.global, sessionName);
         this.updateInterceptor = new MsgInterceptor(this.global, this.global.getLog("test"), null);
      }
      
      public void init(boolean wantsPtP) throws XmlBlasterException {
         this.updateInterceptor.clear();
         ConnectQos qos = new ConnectQos(this.global);
         qos.setSessionName(this.sessionName);
         qos.setPtpAllowed(wantsPtP);
         this.global.getXmlBlasterAccess().connect(qos, updateInterceptor);
      }

      public void shutdown() {
         this.global.getXmlBlasterAccess().disconnect(new DisconnectQos(this.global));
         this.global.shutdown();
         this.global = null;
      }
      
      public SessionName getSessionName() {
         return this.sessionName;
      }
      
      public void check(long timeout, int expected) {
         TestCase.assertEquals(this.getSessionName().getRelativeName(), expected, this.updateInterceptor.waitOnUpdate(timeout, expected));
         this.updateInterceptor.clear();         
      }
   }

   private static String ME = "TestPtPDispatch";
   private final static long TIMEOUT = 5000L;
   private Global glob;
   private LogChannel log;
   private PtPDestination[] destinations;
   private int numDestinations = 4;
   private int counter;
   private String subjectName;
   private boolean persistentMsg = false; 

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
      this.log = this.glob.getLog("test");
      this.counter = 0;

      this.destinations = new PtPDestination[this.numDestinations];
      for (int i=0; i < this.numDestinations; i++) 
         this.destinations[i] = new PtPDestination(this.glob, this.subjectName + "/" + (i+1));
      log.info(ME, "XmlBlaster is ready for testing");
      try {
         I_XmlBlasterAccess con = glob.getXmlBlasterAccess(); // Find orb
         String passwd = "secret";
         ConnectQos connectQos = new ConnectQos(glob, "src_" + this.subjectName, passwd); // == "<qos>...</qos>";
         if (this.log.TRACE) this.log.trace(ME, "setUp: connectQos '" + connectQos.toXml() + "'");
         con.connect(connectQos, null);  // Login to xmlBlaster, register for updates

         this.destinations[0].init(true);
         this.destinations[1].init(false);
                  
      }
      catch (XmlBlasterException e) {
          log.warn(ME, "setUp() - login failed: " + e.getMessage());
          fail("setUp() - login fail: " + e.getMessage());
      }
      catch (Exception e) {
          log.error(ME, "setUp() - login failed: " + e.toString());
          e.printStackTrace();
          fail("setUp() - login fail: " + e.toString());
      }
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown() {
      log.info(ME, "Entering tearDown(), test is finished");
      this.glob.getXmlBlasterAccess().disconnect(null);
      // Global.instance().shutdown();
      // the unknown destinations must be handled inside the specific tests
      for (int i=0; i < this.numDestinations-2; i++) this.destinations[i].shutdown();         
      this.glob.shutdown();
      this.glob = null;
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
   private void doPublish(int destNum, boolean forceQueuing, boolean expectEx, int[] counts, long timeout) {

      SessionName toSessionName = null;
      if (destNum < 0) toSessionName = new SessionName(this.glob, this.subjectName);
      else toSessionName = this.destinations[destNum].getSessionName();
      
      // String oid = "Message" + "-" + counter;
      log.info(ME, "Publishing a message " + toSessionName.getRelativeName() + " ...");
      PublishKey key = new PublishKey(this.glob);
      
      Destination destination = new Destination(this.glob, toSessionName);
      destination.forceQueuing(forceQueuing);
      PublishQos qos = new PublishQos(this.glob, destination);
      qos.setPersistent(this.persistentMsg);
   
      String content = "" + this.counter;
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
      log.info(ME, "Success: Publishing of message for " + toSessionName.getRelativeName() + " done");

      for (int i=0; i < this.destinations.length; i++) 
         this.destinations[i].check(timeout, counts[i]);
   }


   /**
    * Does a connect, waits for updates, compares the number of updates
    * with the expected and makes a disconnect.
    * 
    * @param dest the destination to use (to check)
    * @param expected the number of updates expected after a connect
    * @param delay the time in ms to wait between connect and check
    */
   private void checkForUnknown(PtPDestination dest, boolean wantsPtP, int expected, long delay) {
      try {
         dest.init(wantsPtP);
         dest.check(delay, expected);
         dest.shutdown();
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         assertTrue(false);
      }
   }

   /**
    * TEST: <br />
    */
   public void testNoQueuingNoOverflow() {
      boolean forceQueuing = false;
      doPublish(-1, forceQueuing, false, new int[] {1,0,0,0}, TIMEOUT);
      doPublish(0 , forceQueuing, false, new int[] {1,0,0,0}, TIMEOUT);
      doPublish(1 , forceQueuing, true , new int[] {0,0,0,0}, TIMEOUT);
      doPublish(2 , forceQueuing, true , new int[] {0,0,0,0}, TIMEOUT);
      doPublish(3 , forceQueuing, true , new int[] {0,0,0,0}, TIMEOUT);
      
      checkForUnknown(this.destinations[2], true, 0, TIMEOUT);
      checkForUnknown(this.destinations[3], false,0, TIMEOUT);
   }

   /**
    * TEST: <br />
    */
   public void testQueuingNoOverflow() {
      boolean forceQueuing = true;
      doPublish(-1, forceQueuing, false, new int[] {1,0,0,0}, TIMEOUT);
      doPublish(0 , forceQueuing, false, new int[] {1,0,0,0}, TIMEOUT);
      doPublish(1 , forceQueuing, true , new int[] {0,0,0,0}, TIMEOUT);
      doPublish(2 , forceQueuing, false, new int[] {0,0,0,0}, TIMEOUT);
      doPublish(3 , forceQueuing, false, new int[] {0,0,0,0}, TIMEOUT);

      checkForUnknown(this.destinations[2], true, 1, TIMEOUT);
      checkForUnknown(this.destinations[3], false,0, TIMEOUT);
      // TODO check for dead letters. There should be one here  

   }

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
      testSub.testNoQueuingNoOverflow();
      testSub.tearDown();

      testSub.setUp();
      testSub.testQueuingNoOverflow();
      testSub.tearDown();
   }
}

