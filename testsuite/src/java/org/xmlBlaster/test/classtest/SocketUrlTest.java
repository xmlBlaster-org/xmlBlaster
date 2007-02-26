package org.xmlBlaster.test.classtest;

import java.util.logging.Logger;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.protocol.socket.SocketUrl;
import org.xmlBlaster.util.qos.address.Address;

import junit.framework.*;

/**
 * Invoke: java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.classtest.SocketUrlTest
 *
 * @see org.xmlBlaster.protocol.socket.SocketUrl
 */
public class SocketUrlTest extends TestCase {
   protected Global glob;
   private static Logger log = Logger.getLogger(SocketUrlTest.class.getName());

   public SocketUrlTest(String name) {
      super(name);
   }

   protected void setUp() {
      glob = new Global();

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
            log.info("SUCCESS testBasic(): hostname=" + hostname + " port=" + port + " resultUrl=" + s.getUrl());
         }

         {
            String hostname = "127.1.5.4";
            int port = 9999;
            SocketUrl s = new SocketUrl(glob, hostname, port);
            assertEquals("", hostname, s.getHostname());
            assertEquals("", port, s.getPort());
            assertEquals("", "socket://"+hostname+":"+port, s.getUrl());
            log.info("SUCCESS testBasic(): hostname=" + hostname + " port=" + port + " resultUrl=" + s.getUrl());
         }

         {
            SocketUrl s = new SocketUrl(glob, "192.1.1.5:911");
            SocketUrl other = new SocketUrl(glob, "192.1.1.5", 911);
            assertTrue("", s.equals(other));
            log.info("SUCCESS testBasic(): equals=true");
         }

         {
            SocketUrl s = new SocketUrl(glob, "192.1.1.5");
            SocketUrl other = new SocketUrl(glob, "192.1.1.5", SocketUrl.DEFAULT_SERVER_PORT);
            assertTrue("", s.equals(other));
            log.info("SUCCESS testBasic(): equals=true");
         }

         {
            SocketUrl s = new SocketUrl(glob, "192.1.1.5:900");
            SocketUrl other = new SocketUrl(glob, "192.1.1.5", 911);
            assertTrue("", !s.equals(other));
            log.info("SUCCESS testBasic(): equals=false");
         }

         {
            SocketUrl s = new SocketUrl(glob, "192.1.1.5:911");
            SocketUrl other = new SocketUrl(glob, "192.1.77.5", 911);
            assertTrue("", !s.equals(other));
            log.info("SUCCESS testBasic(): equals=false");
         }
      }
      catch (XmlBlasterException e) {
         log.severe("ERROR: " + e.toString());
         fail(e.toString());
      }
      log.info("SUCCESS testBasic()");
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
            log.info("SUCCESS testAddress(): resultUrl=" + s.getUrl());
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
            log.info("SUCCESS testAddress(): resultUrl=" + s.getUrl());
         }
      }
      catch (XmlBlasterException e) {
         log.severe("ERROR: " + e.toString());
         fail(e.toString());
      }
      log.info("SUCCESS testAddress()");
   }

   public void testParseUrl() {
      try {
         try {
            String url = null;
            new SocketUrl(glob, url);
            fail("Null url is not allowed");
         } catch (XmlBlasterException e) {
            log.info("SUCCESS testParseUrl(): expected exception: " + e.toString());
         }

         {
            String url = "";
            SocketUrl s = new SocketUrl(glob, url);
            assertEquals("", glob.getLocalIP(), s.getHostname());
            assertEquals("", SocketUrl.DEFAULT_SERVER_PORT, s.getPort());
            assertEquals("", "socket://"+glob.getLocalIP()+":"+SocketUrl.DEFAULT_SERVER_PORT, s.getUrl());
            log.info("SUCCESS testParseUrl(): url=" + url + " resultUrl=" + s.getUrl());
         }

         {
            String url = "127.1.1.1";
            SocketUrl s = new SocketUrl(glob, url);
            assertEquals("", url, s.getHostname());
            assertEquals("", SocketUrl.DEFAULT_SERVER_PORT, s.getPort());
            assertEquals("", "socket://"+url+":"+SocketUrl.DEFAULT_SERVER_PORT, s.getUrl());
            log.info("SUCCESS testParseUrl(): url=" + url + " resultUrl=" + s.getUrl());
         }

         {
            String url = "127.1.1.1:8080";
            SocketUrl s = new SocketUrl(glob, url);
            assertEquals("", "127.1.1.1", s.getHostname());
            assertEquals("", 8080, s.getPort());
            assertEquals("", "socket://"+url, s.getUrl());
            log.info("SUCCESS testParseUrl(): url=" + url + " resultUrl=" + s.getUrl());
         }

         {
            String url = "socket:127.1.1.1:8080";
            SocketUrl s = new SocketUrl(glob, url);
            assertEquals("", "127.1.1.1", s.getHostname());
            assertEquals("", 8080, s.getPort());
            assertEquals("", "socket://127.1.1.1:8080", s.getUrl());
            log.info("SUCCESS testParseUrl(): url=" + url + " resultUrl=" + s.getUrl());
         }

         {
            String url = "socket://127.1.1.1:8080";
            SocketUrl s = new SocketUrl(glob, url);
            assertEquals("", "127.1.1.1", s.getHostname());
            assertEquals("", 8080, s.getPort());
            assertEquals("", url, s.getUrl());
            log.info("SUCCESS testParseUrl(): url=" + url + " resultUrl=" + s.getUrl());
         }
      }
      catch (XmlBlasterException e) {
         log.severe("ERROR: " + e.toString());
         fail(e.toString());
      }
      log.info("SUCCESS testParseUrl()");
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
