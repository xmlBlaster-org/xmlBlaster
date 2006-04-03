package org.xmlBlaster.test.client;

import java.util.logging.Logger;

import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.test.MsgInterceptor;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;

import junit.framework.TestCase;

/**
 * Testing administrative commands. 
 * @author xmlblast@marcelruff.info
 */
public class TestCommand extends TestCase {
   private final Global global;
   private static Logger log = Logger.getLogger(TestCommand.class.getName());
   private Global connGlobal;
   private MsgInterceptor updateInterceptor;

   public static void main(String[] args) {
      junit.swingui.TestRunner.run(TestCommand.class);
   }

   /**
    * Constructor for TestCommand.
    * @param arg0
    */
   public TestCommand(String name) {
      super(name);
      this.global = Global.instance();
   }

   /*
    * @see TestCase#setUp()
    */
   protected void setUp() throws Exception {
      super.setUp();
      log.info("Trying to connect to xmlBlaster with Java client lib " + global.getVersion());
      try {
         this.connGlobal = this.global.getClone(null);
         this.updateInterceptor = new MsgInterceptor(this.connGlobal, log, null);
         this.connGlobal.getXmlBlasterAccess().connect(new ConnectQos(this.connGlobal), this.updateInterceptor);
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         fail("aborting since exception ex: " + ex.getMessage());
      }
   }

   /*
    * @see TestCase#tearDown()
    */
   protected void tearDown() throws Exception {
      super.tearDown();
      log.info("Entering tearDown(), test is finished");
      this.connGlobal.getXmlBlasterAccess().disconnect(new DisconnectQos(this.connGlobal));
      this.connGlobal.shutdown();
      this.connGlobal = null;
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      log.severe("update: should never be invoked (msgInterceptors take care of it since they are passed on subscriptions)");
      return "OK";
   }
   
   public void testSetCallbackDispatcherActive() {
      log.fine("setCallbackDispatcherActive() ...");
      try {
         this.connGlobal.getXmlBlasterAccess().setCallbackDispatcherActive(false);
         log.info("Success: setCallbackDispatcherActive(false)");

         //Global.waitOnKeyboardHit("Hit a key to activate again ...");

         this.connGlobal.getXmlBlasterAccess().setCallbackDispatcherActive(true);
         log.info("Success: setCallbackDispatcherActive(true)");

         //Global.waitOnKeyboardHit("Hit a key to finish test ...");
      }
      catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.toXml());
         fail(e.getMessage());
      }
   }

   public void testSendAdministrativeCommand() {
      log.fine("sendAdministrativeCommand() ...");
      I_XmlBlasterAccess con = this.connGlobal.getXmlBlasterAccess();
      String sessionName = con.getSessionName().getAbsoluteName();
      {
         String command = sessionName+"/?dispatcherActive";
         log.info("Trying command '" + command + "'");
         try {
            String ret = con.sendAdministrativeCommand(command);
            log.info("Success: " + command + " returned '" + ret + "'");
            assertEquals("true", ret);
         }
         catch(XmlBlasterException e) {
            fail(e.getMessage());
         }
      }
      {
         String command = sessionName+"/?dispatcherActive=false";
         log.info("Trying command '" + command + "'");
         try {
            String ret = con.sendAdministrativeCommand(command);
            log.info("Success: " + command + " returned '" + ret + "'");
            assertEquals("OK", ret);
         }
         catch(XmlBlasterException e) {
            fail(e.getMessage());
         }
      }
      {
         String command = sessionName+"/?dispatcherActive";
         log.info("Trying command '" + command + "'");
         try {
            String ret = con.sendAdministrativeCommand(command);
            log.info("Success: " + command + " returned '" + ret + "'");
            assertEquals("false", ret);
         }
         catch(XmlBlasterException e) {
            fail(e.getMessage());
         }
      }
      {
         String command = "set " + sessionName+"/?dispatcherActive=true";
         log.info("Trying command '" + command + "'");
         try {
            String ret = con.sendAdministrativeCommand(command);
            log.info("Success: " + command + " returned '" + ret + "'");
            assertEquals("OK", ret);
         }
         catch(XmlBlasterException e) {
            fail(e.getMessage());
         }
      }
      {
         String command = "get " + sessionName+"/?dispatcherActive";
         log.info("Trying command '" + command + "'");
         try {
            String ret = con.sendAdministrativeCommand(command);
            log.info("Success: " + command + " returned '" + ret + "'");
            assertEquals("true", ret);
         }
         catch(XmlBlasterException e) {
            fail(e.getMessage());
         }
      }
   }
}
