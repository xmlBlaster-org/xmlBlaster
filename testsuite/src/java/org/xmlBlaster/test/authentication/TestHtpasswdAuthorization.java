package org.xmlBlaster.test.authentication;

import java.util.logging.Logger;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.util.FileLocator;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;

import org.xmlBlaster.test.Util;

import junit.framework.*;

public class TestHtpasswdAuthorization extends TestCase implements I_Callback {
   private static Logger log = Logger.getLogger(TestHtpasswdAuthorization.class
         .getName());

   private EmbeddedXmlBlaster serverThread = null;

   private final String RIGHT_PASSWORD = "secret";

   private String userhome;
   
   private String passwdFileName;

   private Global glob;

   private I_XmlBlasterAccess con;

   private int serverPort = 7604;

   public final String ME = "TestAuthenticationHtPassWd";

   public TestHtpasswdAuthorization(String name) {
      super(name);
      this.glob = new Global();

      this.userhome = glob.getProperty().get("user.home", "") + java.io.File.separatorChar;
      this.passwdFileName = this.userhome + "testAuthorize.htpasswd";

      try {
         FileLocator.writeFile(this.passwdFileName,
               "guest:yZ24stvIel1j6:connect,disconnect,publish(tennis;sailing;jogging),subscribe(surfing),unSubscribe(surfing),erase(tennis)\n" +
               "admin:yZ24stvIel1j6:!erase\n" +
               "other:yZ24stvIel1j6:! subscribe,unSubscribe\n" +
               "weird:yZ24stvIel1j6:connect,disconnect,subscribe,subscribe(),()\n" +
               "strange:yZ24stvIel1j6:someStrangeMethod\n" +
               "all:yZ24stvIel1j6::\n" +
               "__sys__jdbc:yZ24stvIel1j6\n");
      } catch (Exception ex) {
         assertTrue("Could not create password file '" + this.passwdFileName
               + "'. Tests won't work!", false);
      }
   }

   protected void setUp() {
      String[] ports = Util.getOtherServerPorts(serverPort);
      String[] args = new String[4 + ports.length];
      args[0] = "-Security.Server.Plugin.htpasswd.secretfile";
      args[1] = this.passwdFileName;
      args[2] = "-Security.Server.Plugin.htpasswd.allowPartialUsername";
      args[3] = "false";
      for (int i = 0; i < ports.length; i++) {
         args[i + 4] = ports[i];
      }
      glob.init(args);
      serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
   }

   protected void tearDown() {
      try {
         Thread.sleep(1000);
      } catch (Exception ex) {}
      if (serverThread != null)
         serverThread.stopServer(true);
      glob.init(Util.getDefaultServerPorts());
      Util.resetPorts(glob);
      this.glob = null;
      this.con = null;
      Global.instance().shutdown();
   }

   public void testMethodNameAuthorization() {
      log.info("testMethodNameAuthorization()");
      try {
         con = glob.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(glob, "admin", RIGHT_PASSWORD);
         con.connect(qos, this);
         con.publish(new MsgUnit("<key oid='Hello'/>", "hi".getBytes(), "<qos/>"));
         con.subscribe("<key oid='Hello'/>", "<qos/>");
         con.unSubscribe("<key oid='Hello'/>", "<qos/>");
         try {
            con.erase("<key oid='Hello'/>", "<qos/>");
            fail("Expected to get a authorization exception for erase() invocation");
         }
         catch (XmlBlasterException e) {
            log.info("OK, expected this exception: " + e.getMessage());
         }
         con.disconnect(null);
      } catch (XmlBlasterException ex) {
         fail("Could not connect: " + ex.toString());
      }
   }

   public void testMethodNameAuthorizationNegation() {
      log.info("testMethodNameAuthorizationNegation()");
      try {
         con = glob.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(glob, "other", RIGHT_PASSWORD);
         con.connect(qos, this);
         con.publish(new MsgUnit("<key oid='Hello'/>", "hi".getBytes(), "<qos/>"));
         try {
            con.subscribe("<key oid='Hello'/>", "<qos/>");
            fail("Expected to get a authorization exception for subscribe() invocation");
         }
         catch (XmlBlasterException e) {
            log.info("OK, expected this exception: " + e.getMessage());
         }
         try {
            con.unSubscribe("<key oid='Hello'/>", "<qos/>");
            fail("Expected to get a authorization exception for unSubscribe() invocation");
         }
         catch (XmlBlasterException e) {
            log.info("OK, expected this exception: " + e.getMessage());
         }
         con.erase("<key oid='Hello'/>", "<qos/>");
         con.disconnect(null);
      } catch (XmlBlasterException ex) {
         fail("Could not connect: " + ex.toString());
      }
   }

   public void testTopicAuthorization() {
      log.info("testTopicAuthorization()");
      try {
         con = glob.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(glob, "guest", RIGHT_PASSWORD);
         con.connect(qos, this);

         con.publish(new MsgUnit("<key oid='tennis'/>", "hi".getBytes(), "<qos/>"));
         con.publish(new MsgUnit("<key oid='sailing'/>", "hi".getBytes(), "<qos/>"));
         try {
            con.publish(new MsgUnit("<key oid='Hello'/>", "hi".getBytes(), "<qos/>"));
            fail("Expected to get a authorization exception for illgeal topic publish() invocation");
         }
         catch (XmlBlasterException e) {
            log.info("OK, expected this exception: " + e.getMessage());
         }

         con.subscribe("<key oid='surfing'/>", "<qos/>");
         try {
            con.subscribe("<key oid='Hello'/>", "<qos/>");
            fail("Expected to get a authorization exception for subscribe() invocation");
         }
         catch (XmlBlasterException e) {
            log.info("OK, expected this exception: " + e.getMessage());
         }

         con.unSubscribe("<key oid='surfing'/>", "<qos/>");
         try {
            con.unSubscribe("<key oid='Hello'/>", "<qos/>");
            fail("Expected to get a authorization exception for unSubscribe() invocation");
         }
         catch (XmlBlasterException e) {
            log.info("OK, expected this exception: " + e.getMessage());
         }
         
         con.erase("<key oid='tennis'/>", "<qos/>");
         try {
            con.erase("<key oid='Hello'/>", "<qos/>");
            fail("Expected to get a authorization exception for erase() invocation");
         }
         catch (XmlBlasterException e) {
            log.info("OK, expected this exception: " + e.getMessage());
         }
         con.disconnect(null);
      } catch (XmlBlasterException ex) {
         fail("Could not connect: " + ex.toString());
      }
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      log.info("Receiving callback message: " + updateKey.getOid());
      return "";
   }
}
