package org.xmlBlaster.test.classtest;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;

import org.jutils.log.LogChannel;
import org.xmlBlaster.client.protocol.http.common.BufferedInputStreamMicro;
import org.xmlBlaster.client.protocol.http.common.Msg;
import org.xmlBlaster.client.protocol.http.common.MsgHolder;
import org.xmlBlaster.client.protocol.http.common.ObjectInputStreamMicro;
import org.xmlBlaster.client.protocol.http.common.ObjectOutputStreamMicro;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.StringPairTokenizer;
import org.xmlBlaster.util.qos.ClientProperty;

import junit.framework.*;

/**
 * Test ConnectQos. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.StringPairTokenizerTest
 */
public class StringPairTokenizerTest extends TestCase {
   
   final static String ME = "StringPairTokenizerTest";
   protected Global glob;
   protected LogChannel log;
   int counter = 0;

   public StringPairTokenizerTest(String name, String[] args) {
      super(name);
      this.glob = Global.instance();
      this.glob.init(args);
      this.log = this.glob.getLog("test");
   }

   public StringPairTokenizerTest(String name) {
      super(name);
      this.glob = Global.instance();
      this.log = this.glob.getLog("test");
   }

   protected void setUp() {
   }

   protected void tearDown() {
   }

   public void testClientProperties() {
      try {
         int maxEntries = 0;
         long maxSize = 0L;
         boolean consumable = false;
         long waitingDelay = 4000L;
         
         String cmd = "maxEntries=4&maxSize=-1&consumable=true&waitingDelay=1000";
         Map props = StringPairTokenizer.parseToStringClientPropertyPairs(this.glob, cmd, "&", "=");
         
         ClientProperty prop = (ClientProperty)props.get("maxEntries");
         assertNotNull("property should not be null", prop);
         maxEntries = prop.getIntValue();
         assertEquals("maxEntries", 4, maxEntries);
         
         prop = (ClientProperty)props.get("maxSize");
         assertNotNull("property should not be null", prop);
         maxSize = prop.getLongValue();
         assertEquals("maxSize", -1L, maxSize);

         prop = (ClientProperty)props.get("consumable");
         assertNotNull("property should not be null", prop);
         consumable = prop.getBooleanValue();
         assertEquals("consumable", true, consumable);

         prop = (ClientProperty)props.get("waitingDelay");
         assertNotNull("property should not be null", prop);
         waitingDelay = prop.getLongValue();
         assertEquals("waitingDelay", 1000L, waitingDelay);
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not occur here", false);
      }
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.StringPairTokenizerTest
    * </pre>
    */
   public static void main(String args[])
   {
      StringPairTokenizerTest test = new StringPairTokenizerTest("StringPairTokenizerTest", args);

      test.setUp();
      test.testClientProperties();
      test.tearDown();

   }
}
