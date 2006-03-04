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
import java.util.Vector;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.client.protocol.http.common.BufferedInputStreamMicro;
import org.xmlBlaster.client.protocol.http.common.Msg;
import org.xmlBlaster.client.protocol.http.common.MsgHolder;
import org.xmlBlaster.client.protocol.http.common.ObjectInputStreamMicro;
import org.xmlBlaster.client.protocol.http.common.ObjectOutputStreamMicro;
import org.xmlBlaster.util.Global;

import junit.framework.*;

/**
 * Test ConnectQos. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.MicroEditionTest
 */
public class MicroEditionTest extends TestCase {
   
   public class Sender extends Thread {
      
      private int port;
      private byte[] content;
      private long delay;
      private InetAddress addr;
      
      public Sender(InetAddress addr, int port, byte[] content, long delay) {
         super();
         this.port = port;
         this.content = content;
         this.delay = delay;
         this.addr = addr;
         start();   
      }
      
      public void run() {
         try {
            Socket sock = new Socket(this.addr, this.port);
            OutputStream out = sock.getOutputStream();
            for (int i=0; i < this.content.length; i++) {
               out.write(this.content[i]);
               Thread.sleep(this.delay);
            }
            out.flush();
            sock.close();            
         }
         catch (Exception ex) {
            ex.printStackTrace();
         }
      }
      
   }
   
   public class Receiver extends Thread {
      
      private int port;
      private Vector lines = new Vector();
      private ServerSocket serverSocket;
      private boolean isMicro;
      private boolean finished;
      
      public Receiver(int port, boolean isMicro) throws IOException {
         super();
         this.port = port;
         this.serverSocket = new ServerSocket(this.port);
      }
      
      public InetAddress startListener() {
         start();
         return this.serverSocket.getInetAddress();
      }

      public String[] getLines() {
         return (String[])this.lines.toArray(new String[this.lines.size()]);
      }

      public boolean isFinished() {
         return this.finished;
      }

      public void run() {
         System.out.println("starting thread");
         try {
            Socket sock = this.serverSocket.accept();
            InputStream in = sock.getInputStream();
            
            if (this.isMicro) {
               BufferedInputStreamMicro br = new BufferedInputStreamMicro(in);
               while (true) {
                  String line = br.readLine();
                  if (line != null) this.lines.add(line);
                  else break;
               }
            }
            else {
               BufferedReader br = new BufferedReader(new InputStreamReader(in));
               while (true) {
                  String line = br.readLine();
                  if (line != null) this.lines.add(line);
                  else break;
               }
            }
            sock.close();            
         }
         catch (Exception ex) {
            ex.printStackTrace();
         }
         this.finished = true;
      }
      
   }
   
   final static String ME = "MicroEditionTest";
   protected Global glob;
   private static Logger log = Logger.getLogger(MicroEditionTest.class.getName());
   int counter = 0;

   public MicroEditionTest(String name, String[] args) {
      super(name);
      this.glob = Global.instance();
      this.glob.init(args);

   }

   public MicroEditionTest(String name) {
      super(name);
      this.glob = Global.instance();

   }

   protected void setUp() {
   }

   protected void tearDown() {
   }

   private void assertHashtableContent(String key, Hashtable ref, Hashtable is) {
      Object val1 = ref.get(key);
      Object val2 = is.get(key);
      assertEquals("wrong value for key '" + key + "'", val1, val2);
   }

   public void testVectorIO() {
      try {
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         ObjectOutputStreamMicro oosm = new ObjectOutputStreamMicro(baos);
         
         Hashtable key = new Hashtable();
         key.put("one", "1");
         key.put("two", "2");
         key.put("three", "3");
         key.put("four", "4");
         
         Hashtable qos = new Hashtable();
         qos.put("one-qos", "1");
         qos.put("two-qos", "2");
         qos.put("three-qos", "3");
         
         byte[] content = "This is the content".getBytes();
         Msg msg = new Msg(key, content, qos);
         Vector inVec = new Vector();
         inVec.add(key);
         inVec.add(qos);
         inVec.add(content);
         inVec.add(key);
         inVec.add(qos);
         inVec.add(content);
         oosm.writeObject(inVec);
         
         ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
         ObjectInputStreamMicro oism = new ObjectInputStreamMicro(bais);
         Object obj = oism.readObject();
         assertTrue("hashtable is not of type 'Vector', it is " + obj.getClass().getName(), obj instanceof Vector);
         Vector vec = (Vector)obj;
         assertEquals("wrong number of entries in vector", 6, vec.size());
         Hashtable keyOut = (Hashtable)vec.get(0);
         Hashtable qosOut = (Hashtable)vec.get(1);
         byte[] contentOut = (byte[])vec.get(2);
         assertHashtableContent("one", key, keyOut);
         assertHashtableContent("two", key, keyOut);
         assertHashtableContent("three", key, keyOut);
         assertHashtableContent("four", key, keyOut);
         assertHashtableContent("one-qos", qos, qosOut);
         assertHashtableContent("two-qos", qos, qosOut);
         assertHashtableContent("three-qos", qos, qosOut);
         
         keyOut = (Hashtable)vec.get(3);
         qosOut = (Hashtable)vec.get(4);
         contentOut = (byte[])vec.get(5);
         assertHashtableContent("one", key, keyOut);
         assertHashtableContent("two", key, keyOut);
         assertHashtableContent("three", key, keyOut);
         assertHashtableContent("four", key, keyOut);
         assertHashtableContent("one-qos", qos, qosOut);
         assertHashtableContent("two-qos", qos, qosOut);
         assertHashtableContent("three-qos", qos, qosOut);
         log.info("testVectorIO successfully completed");
      }
      catch (Exception ex) {
         ex.printStackTrace();         
         assertTrue("exception occured: " + ex.getMessage(), false);
      }
      
   }

   public void testHashtableIO() {
      try {
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         ObjectOutputStreamMicro oosm = new ObjectOutputStreamMicro(baos);
         
         Hashtable hashtable = new Hashtable();
         hashtable.put("one", "1");
         hashtable.put("two", "2");
         hashtable.put("three", "3");
         hashtable.put("four", "4");
         
         oosm.writeObject(hashtable);
         ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
         ObjectInputStreamMicro oism = new ObjectInputStreamMicro(bais);
         Object obj = oism.readObject();
         assertTrue("hashtable is not of type 'Hashtable', it is " + obj.getClass().getName(), obj instanceof Hashtable);
         Hashtable table = (Hashtable)obj;
         assertEquals("wrong number of entries in hashtable", hashtable.size(), table.size());
         assertHashtableContent("one", hashtable, table);
         assertHashtableContent("two", hashtable, table);
         assertHashtableContent("three", hashtable, table);
         assertHashtableContent("four", hashtable, table);
         log.info("testHashtableIO successfully completed");
      }
      catch (Exception ex) {
         ex.printStackTrace();         
         assertTrue("exception occured: " + ex.getMessage(), false);
      }
      
   }

   public void testStringIO() {
      try {
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         ObjectOutputStreamMicro oosm = new ObjectOutputStreamMicro(baos);
         
         String testString = "this is\n\na simple\nmultiline string\n";
         oosm.writeObject(testString);
         ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
         ObjectInputStreamMicro oism = new ObjectInputStreamMicro(bais);
         Object obj = oism.readObject();
         assertTrue("string is not of type 'String', it is " + obj.getClass().getName(), obj instanceof String);
         String response = (String)obj;
         assertEquals("wrong content for the string", testString, response);
         log.info("testStringIO successfully completed");
      }
      catch (Exception ex) {
         ex.printStackTrace();         
         assertTrue("exception occured: " + ex.getMessage(), false);
      }
   }

   public void testReadLine() {
      try {
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         PrintStream ps = new PrintStream(baos);
         ps.println("first line");
         ps.println("");
         ps.println("");
         ps.println("");
         ps.println("second line");
         ps.println("third line");
         ps.println("");
         ps.println("");
         ps.close();
         byte[] buf = baos.toByteArray();
         byte[] buf1 = new byte[buf.length];
         for (int i=0; i < buf.length; i++) buf1[i] = buf[i];
         
         BufferedInputStreamMicro bism = new BufferedInputStreamMicro(new ByteArrayInputStream(buf));
         BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(buf1)));
         while (true) {
            String referenceLine = br.readLine(); // of the normal reader
            if (referenceLine == null) 
               break;
            String lineToCheck = bism.readLine(); // must be the same as the corresponding reference since we expect same behaviour
            if (lineToCheck == null) 
               assertTrue("the line to be checked is unexpectedly null", false);
            assertEquals("wrong content", referenceLine, lineToCheck);
         }
         
         String lineToCheck = bism.readLine(); // must be the same as the corresponding reference since we expect same behaviour
         assertTrue("The line to check must also be null", lineToCheck == null);
         
         log.info("testReadLine successfully completed");
      }
      catch (Exception ex) {
         ex.printStackTrace();         
         assertTrue("exception occured: " + ex.getMessage(), false);
      }
   }

   public void testReadLineDelayed() {
      try {
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         PrintStream ps = new PrintStream(baos);
         ps.println("first line");
         ps.println("");
         ps.println("");
         ps.println("");
         ps.println("second line");
         ps.println("third line");
         ps.println("");
         ps.println("");
         ps.close();
         byte[] buf = baos.toByteArray();
         
         int port1 = 26666;
         int port2 = 26667;
         Receiver receiver1 = new Receiver(port1, false);
         Receiver receiver2 = new Receiver(port2, true);
         
         log.info("testReadLineDelayed create sender 1");
         Sender sender1 = new Sender(receiver1.startListener(), port1, buf, 1L);
         log.info("testReadLineDelayed create sender 2");
         Sender sender2 = new Sender(receiver2.startListener(), port2, buf, 10L);

         while (!receiver1.isFinished() || !receiver2.isFinished()) {
            Thread.sleep(200L);
         }
         
         String[] resp1 = receiver1.getLines();
         String[] resp2 = receiver2.getLines();
         
         assertEquals("wrong number of lines returned", resp1.length, resp2.length);
         for (int i=0; i < resp1.length; i++) {
            log.info(".testReadLineDelayed '" + resp1[i] + "' '" + resp2[i] + "'");
         }
         for (int i=0; i < resp1.length; i++) {
            assertEquals("wrong content of line " + i, resp1[i], resp2[i]);
         }
         
      }
      catch (Exception ex) {
         ex.printStackTrace();         
         assertTrue("exception occured: " + ex.getMessage(), false);
      }
   }

   public void testMessageIO() {
      try {
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         ObjectOutputStreamMicro oosm = new ObjectOutputStreamMicro(baos);

         String oid = "someOid";         
         String key = "<key oid='100'></key>";
         String qos = "<qos><persistent/></qos>";
         byte[] content = "This is the content".getBytes();
         
         int length = ObjectOutputStreamMicro.writeMessage(baos, oid, key, qos, content);
         assertEquals("wrong length returned", oid.length() + key.length() + qos.length() + content.length + 3, length);
         ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
         ObjectInputStreamMicro oism = new ObjectInputStreamMicro(bais);

         int size = bais.available();
         assertEquals("wrong length of bytes available", length, size);
         byte[] msg = new byte[size];
         bais.read(msg);
         MsgHolder msgHolder = ObjectInputStreamMicro.readMessage(msg);
         
         assertEquals("wrong content for the oid", oid, msgHolder.getOid());
         assertEquals("wrong content for the key", key, msgHolder.getKey());
         assertEquals("wrong content for the qos", qos, msgHolder.getQos());
         assertEquals("wrong content for the content", new String(content), new String(msgHolder.getContent()));
         
         log.info("testMessageIO successfully completed");
      }
      catch (Exception ex) {
         ex.printStackTrace();         
         assertTrue("exception occured: " + ex.getMessage(), false);
      }
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.MicroEditionTest
    * </pre>
    */
   public static void main(String args[])
   {
      MicroEditionTest test = new MicroEditionTest("MicroEditionTest", args);

      test.setUp();
      test.testStringIO();
      test.tearDown();

      test.setUp();
      test.testHashtableIO();
      test.tearDown();

      test.setUp();
      test.testVectorIO();
      test.tearDown();

      test.setUp();
      test.testReadLine();
      test.tearDown();

      test.setUp();
      test.testReadLineDelayed();
      test.tearDown();

      test.setUp();
      test.testMessageIO();
      test.tearDown();
   }
}
