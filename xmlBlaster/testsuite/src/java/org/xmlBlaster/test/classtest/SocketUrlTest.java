package org.xmlBlaster.test.classtest;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.protocol.socket.SocketUrl;
import org.xmlBlaster.protocol.socket.ExecutorBase;
import org.xmlBlaster.util.qos.address.Address;

import java.io.*;

import junit.framework.*;

/**
 * Test FileIO class. 
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.classtest.SocketUrlTest
 *
 * @see org.xmlBlaster.protocol.socket.SocketUrl
 */
public class SocketUrlTest extends TestCase {
   private String ME = "SocketUrlTest";
   protected Global glob;
   protected LogChannel log;

   public SocketUrlTest(String name) {
      super(name);
   }

   protected void setUp() {
      glob = new Global();
      log = glob.getLog(null);
   }

   protected void tearDown() {
   }

   public void testBasic() {
      try {
         {
            String hostname = null;
            int port = -1;
            SocketUrl s = new SocketUrl(glob, hostname, port);
            assertEquals("", glob.getLocalIP(), s.getHostname());
            assertEquals("", port, s.getPort());
            assertEquals("", "socket://"+glob.getLocalIP()+":"+port, s.getUrl());
            log.info(ME, "SUCCESS testBasic(): hostname=" + hostname + " port=" + port + " resultUrl=" + s.getUrl());
         }

         {
            String hostname = "127.1.5.4";
            int port = 9999;
            SocketUrl s = new SocketUrl(glob, hostname, port);
            assertEquals("", hostname, s.getHostname());
            assertEquals("", port, s.getPort());
            assertEquals("", "socket://"+hostname+":"+port, s.getUrl());
            log.info(ME, "SUCCESS testBasic(): hostname=" + hostname + " port=" + port + " resultUrl=" + s.getUrl());
         }

         {
            SocketUrl s = new SocketUrl(glob, "192.1.1.5:911");
            SocketUrl other = new SocketUrl(glob, "192.1.1.5", 911);
            assertTrue("", s.equals(other));
            log.info(ME, "SUCCESS testBasic(): equals=true");
         }

         {
            SocketUrl s = new SocketUrl(glob, "192.1.1.5");
            SocketUrl other = new SocketUrl(glob, "192.1.1.5", ExecutorBase.DEFAULT_SERVER_PORT);
            assertTrue("", s.equals(other));
            log.info(ME, "SUCCESS testBasic(): equals=true");
         }

         {
            SocketUrl s = new SocketUrl(glob, "192.1.1.5:900");
            SocketUrl other = new SocketUrl(glob, "192.1.1.5", 911);
            assertTrue("", !s.equals(other));
            log.info(ME, "SUCCESS testBasic(): equals=false");
         }

         {
            SocketUrl s = new SocketUrl(glob, "192.1.1.5:911");
            SocketUrl other = new SocketUrl(glob, "192.1.77.5", 911);
            assertTrue("", !s.equals(other));
            log.info(ME, "SUCCESS testBasic(): equals=false");
         }
      }
      catch (XmlBlasterException e) {
         log.error(ME, "ERROR: " + e.toString());
         fail(e.toString());
      }
      log.info(ME, "SUCCESS testBasic()");
   }

   public void testAddress() {
      try {
         {
            String hostname = "168.2.2.2";
            int port = 8888;
            String type = "socket";
            String[] args = {
               "-plugin/"+type+"/hostname",
               hostname,
               "-plugin/"+type+"/port",
               ""+port 
            };
            glob.init(args);
            Address address = new Address(glob, type);
            SocketUrl s = new SocketUrl(glob, address);
            assertEquals("", hostname, s.getHostname());
            assertEquals("", port, s.getPort());
            assertEquals("", "socket://"+hostname+":"+port, s.getUrl());
            log.info(ME, "SUCCESS testAddress(): resultUrl=" + s.getUrl());
         }

         {
            String hostname = "168.99.55.2";
            int port = 6666;
            Address address = new Address(glob);
            address.setPluginProperty("hostname", hostname);
            address.setPluginProperty("port", ""+port);
            SocketUrl s = new SocketUrl(glob, address);
            assertEquals("", hostname, s.getHostname());
            assertEquals("", port, s.getPort());
            assertEquals("", "socket://"+hostname+":"+port, s.getUrl());
            log.info(ME, "SUCCESS testAddress(): resultUrl=" + s.getUrl());
         }
      }
      catch (XmlBlasterException e) {
         log.error(ME, "ERROR: " + e.toString());
         fail(e.toString());
      }
      log.info(ME, "SUCCESS testAddress()");
   }

   public void testParseUrl() {
      try {
         try {
            String url = null;
            SocketUrl s = new SocketUrl(glob, url);
            fail("Null url is not allowed");
         } catch (XmlBlasterException e) {
            log.info(ME, "SUCCESS testParseUrl(): expected exception: " + e.toString());
         }

         {
            String url = "";
            SocketUrl s = new SocketUrl(glob, url);
            assertEquals("", glob.getLocalIP(), s.getHostname());
            assertEquals("", ExecutorBase.DEFAULT_SERVER_PORT, s.getPort());
            assertEquals("", "socket://"+glob.getLocalIP()+":"+ExecutorBase.DEFAULT_SERVER_PORT, s.getUrl());
            log.info(ME, "SUCCESS testParseUrl(): url=" + url + " resultUrl=" + s.getUrl());
         }

         {
            String url = "127.1.1.1";
            SocketUrl s = new SocketUrl(glob, url);
            assertEquals("", url, s.getHostname());
            assertEquals("", ExecutorBase.DEFAULT_SERVER_PORT, s.getPort());
            assertEquals("", "socket://"+url+":"+ExecutorBase.DEFAULT_SERVER_PORT, s.getUrl());
            log.info(ME, "SUCCESS testParseUrl(): url=" + url + " resultUrl=" + s.getUrl());
         }

         {
            String url = "127.1.1.1:8080";
            SocketUrl s = new SocketUrl(glob, url);
            assertEquals("", "127.1.1.1", s.getHostname());
            assertEquals("", 8080, s.getPort());
            assertEquals("", "socket://"+url, s.getUrl());
            log.info(ME, "SUCCESS testParseUrl(): url=" + url + " resultUrl=" + s.getUrl());
         }

         {
            String url = "socket:127.1.1.1:8080";
            SocketUrl s = new SocketUrl(glob, url);
            assertEquals("", "127.1.1.1", s.getHostname());
            assertEquals("", 8080, s.getPort());
            assertEquals("", "socket://127.1.1.1:8080", s.getUrl());
            log.info(ME, "SUCCESS testParseUrl(): url=" + url + " resultUrl=" + s.getUrl());
         }

         {
            String url = "socket://127.1.1.1:8080";
            SocketUrl s = new SocketUrl(glob, url);
            assertEquals("", "127.1.1.1", s.getHostname());
            assertEquals("", 8080, s.getPort());
            assertEquals("", url, s.getUrl());
            log.info(ME, "SUCCESS testParseUrl(): url=" + url + " resultUrl=" + s.getUrl());
         }
      }
      catch (XmlBlasterException e) {
         log.error(ME, "ERROR: " + e.toString());
         fail(e.toString());
      }
      log.info(ME, "SUCCESS testParseUrl()");
   }

   /**
    * Invoke: java org.xmlBlaster.test.classtest.SocketUrlTest
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.classtest.SocketUrlTest</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println("******* SocketUrlTest: Init failed");
      }
      SocketUrlTest testSub = new SocketUrlTest("SocketUrlTest");
      testSub.setUp();
      testSub.testBasic();
      testSub.testAddress();
      testSub.testParseUrl();
      testSub.tearDown();
   }
}
