/*------------------------------------------------------------------------------
Name:      TestLogout.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login/logout test for xmlBlaster
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.I_ConnectionProblems;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.engine.helper.MessageUnit;

import junit.framework.*;


/**
 */
public class TestLogout extends TestCase implements I_Callback
{
   private static String ME = "Tim";
   private final Global glob;
   private final LogChannel log;

   private XmlBlasterConnection con;

   /**
    * Constructs the TestLogout object from out main().
    */
   public TestLogout(Global glob) {
      super("TestLogout");
      this.glob = glob;
      this.log = glob.getLog(null);
   }

   /**
    * Constructs the TestLogout object from junit.
    */
   public TestLogout(String name) {
      super(name);
      this.glob = Global.instance();
      this.log = glob.getLog(null);
   }

   /**
    * Connect to xmlBlaster. 
    */
   protected void setUp() {
      try {
         con = new XmlBlasterConnection(glob); // Find orb
         con.initFailSave(new I_ConnectionProblems() {
               public void reConnected() {
                  log.info(ME, "I_ConnectionProblems: We were lucky, reconnected to " + glob.getId());
                  try {
                     con.flushQueue();    // send all tailback messages
                  } catch (XmlBlasterException e) {
                     log.error(ME, "Exception during reconnection recovery: " + e.reason);
                  }
               }
               public void lostConnection() {
                  log.warn(ME, "I_ConnectionProblems: Lost connection to " + glob.getId());
               }
            });

         ConnectQos qos = new ConnectQos(glob);
         con.connect(qos, this); // Login to xmlBlaster
      }
      catch (XmlBlasterException e) {
         log.error(ME, e.toString());
         e.printStackTrace();
      }
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown() {
      if (con != null) con.disconnect(null);
   }


   /**
    */
   public void testDisconnect() {
      log.info(ME, "*** Enter testDisconnect() ...");
      if (con.disconnect(null) == false)
         fail("Expected successful disconnect");
      if (con.disconnect(null) == true)
         fail("Expected disconnect to fail, we have disconnected already");
      con = null;
      log.info(ME, "*** Leave testDisconnect() ...");
   }

   /**
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      fail("Receiving update of a message " + updateKey.getUniqueKey());
      return "";
   }

   /**
    * Invoke: java testsuite.org.xmlBlaster.TestLogout
    * <p />
    * Note you need 'java' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner testsuite.org.xmlBlaster.TestLogout</pre>
    */
   public static void main(String args[]) {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.exit(1);
      }
      TestLogout testSub = new TestLogout(glob);
      testSub.setUp();
      testSub.testDisconnect();
      testSub.tearDown();
   }
}

