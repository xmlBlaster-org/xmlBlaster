/*------------------------------------------------------------------------------
Name:      TestLogout.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.authentication;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.util.MsgUnit;

import junit.framework.*;


/**
 */
public class TestLogout extends TestCase implements I_Callback
{
   private static String ME = "TestLogout";
   private final Global glob;
   private final LogChannel log;

   private I_XmlBlasterAccess con;

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
      this.glob = new Global();
      this.log = glob.getLog(null);
   }

   /**
    * Connect to xmlBlaster. 
    */
   protected void setUp() {
      try {
         con = glob.getXmlBlasterAccess(); // Find orb
         /*
         con.initFailSave(new I_ConnectionStateListener() {
               public void reConnected() {
                  log.info(ME, "I_ConnectionStateListener: We were lucky, reconnected to " + glob.getId());
                  try {
                     con.flushQueue();    // send all tailback messages
                  } catch (XmlBlasterException e) {
                     log.error(ME, "Exception during reconnection recovery: " + e.getMessage());
                  }
               }
               public void lostConnection() {
                  log.warn(ME, "I_ConnectionStateListener: Lost connection to " + glob.getId());
               }
            });
         */
         ConnectQos qos = new ConnectQos(glob, ME, "secret");
         con.connect(qos, this); // Login to xmlBlaster
         log.info(ME, "Successful login");
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
      if (con.disconnect(null) == false) {
         log.error(ME, "Expected successful disconnect");
         fail("Expected successful disconnect");
      }
      if (con.disconnect(null) == true) {
         log.error(ME, "Expected disconnect to fail, we have disconnected already");
         fail("Expected disconnect to fail, we have disconnected already");
      }
      con = null;
      log.info(ME, "*** Leave testDisconnect() ...");
   }

   /**
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      fail("Receiving update of a message " + updateKey.getOid());
      return "";
   }

   /**
    * Invoke: java org.xmlBlaster.test.authentication.TestLogout
    * <p />
    * <pre>java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.authentication.TestLogout</pre>
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

