/*------------------------------------------------------------------------------
Name:      TestPollerPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.client;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.jutils.log.LogChannel;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.test.MsgInterceptor;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

import junit.framework.TestCase;


/**
 * <p>
 * This is an interesting example, since it creates a XmlBlaster server instance
 * in the same JVM , but in a separate thread, talking over CORBA with it.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *   java junit.textui.TestRunner -noloading org.xmlBlaster.test.client.TestFilePollerPlugin
 *   java junit.swingui.TestRunner -noloading org.xmlBlaster.test.client.TestFilePollerPlugin
 * </pre>
 * @see org.xmlBlaster.client.I_XmlBlasterAccess
 */
public class TestFilePollerPlugin extends TestCase implements I_Callback {
   private static String ME = "TestFilePollerPlugin";
   
   private Global global;
   private LogChannel log;

   private MsgInterceptor[] updateInterceptors;

   public TestFilePollerPlugin() {
      this(null);
   }

   public TestFilePollerPlugin(Global global) {
      super("TestFilePollerPlugin");
      this.global = global;
      if (this.global == null) {
         this.global = new Global();
         this.global.init((String[])null);
      }
      this.log = this.global.getLog("test");
      this.updateInterceptors = new MsgInterceptor[1];
   }

   private void writeToFileSlowly(String filename, int numChunks, long chunkSize, long delayBetweenChunks) throws XmlBlasterException {
      char[] chunk = new char[numChunks];
      int sizeChanges = 0;
      int timeChanges = 0;
      long time = -1L;
      long size = -1L;
      for (int i=0; i < numChunks; i++) 
         chunk[i] = 'a';
      try {
         File file = new File(filename);
         if (file.exists()) {
            boolean ret = file.delete();
            if (!ret)
               throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".writeToFileSlowly", "can not remove old file '" + filename + "'");
         }
      }
      catch (SecurityException ex) {
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".writeToFileSlowly", "can not remove old file '" + filename + "' because of security exception: " + ex.getMessage());
      }
      int i = 0;
      try {
         FileWriter fileWriter = new FileWriter(filename);
         for (i=0; i < numChunks; i++) {
            fileWriter.write(chunk);
            File file = new File(filename);
            
            if (this.log.DUMP) {
               this.log.dump(ME, "writeToFileSlowly: chunk nr. " + i + " file exists   :'" + file.exists() + "'");
               this.log.dump(ME, "writeToFileSlowly: chunk nr. " + i + " file can read :'" + file.canRead() + "'");
               this.log.dump(ME, "writeToFileSlowly: chunk nr. " + i + " file can write:'" + file.canWrite() + "'");
            }
            
            long newSize = file.length();
            if (this.log.DUMP) {
               this.log.dump(ME, "writeToFileSlowly: chunk nr. " + i + " size='" + newSize + "'");
            }
            if (size > -1L) {
               if (newSize != size)
                  sizeChanges++;
            }
            size = newSize;
            
            long newTime = file.lastModified();
            if (this.log.DUMP) {
               this.log.dump(ME, "writeToFileSlowly: chunk nr. " + i + " time='" + newTime + "'");
            }
            if (time > -1L) {
               if (newTime != time)
                  timeChanges++;
            }
            time = newTime;
            
            try {
               Thread.sleep(delayBetweenChunks);
            }
            catch (Exception ex) {
            }
         }
         fileWriter.close();

         File file = new File(filename);
         long newSize = file.length();
         if (this.log.DUMP) {
            this.log.dump(ME, "writeToFileSlowly: closed file size='" + newSize + "'");
         }
         long newTime = file.lastModified();
         if (this.log.DUMP) {
            this.log.dump(ME, "writeToFileSlowly: closed file time='" + newTime + "'");
         }
      }
      catch (IOException ex) {
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".writeToFileSlowly", "IOException when writing chunk '" + i + "' to '" + filename + "' reason: " + ex.getMessage());
      }
      if (this.log.TRACE) {
         this.log.trace(ME, ""); 
         this.log.trace(ME, "writeToFileSlowly: size changes = " + sizeChanges); 
         this.log.trace(ME, "writeToFileSlowly: time changes = " + timeChanges); 
         this.log.trace(ME, "writeToFileSlowly: chunks       = " + numChunks); 
         this.log.trace(ME, ""); 
      }
   }
   
   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp() {
   }
   
   
   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown() {
      log.info(ME, "Entering tearDown(), test is finished");
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      String contentStr = new String(content);
      String cont = (contentStr.length() > 10) ? (contentStr.substring(0,10)+"...") : contentStr;
      this.log.info(ME, "Receiving update of a message oid=" + updateKey.getOid() +
                        " priority=" + updateQos.getPriority() +
                        " state=" + updateQos.getState() +
                        " content=" + cont);
      this.log.info(ME, "further log for receiving update of a message cbSessionId=" + cbSessionId +
                     updateKey.toXml() + "\n" + new String(content) + updateQos.toXml());
      this.log.error(ME, "update: should never be invoked (msgInterceptors take care of it since they are passed on subscriptions)");

      return "OK";
   }

   public void testSystem() {
      try {
         int numChunks = 10;
         long chunkSize = 10000L;
         long delayBetweenChunks = 2000L;
         writeToFileSlowly("testFilePollerPlugin.dat", numChunks, chunkSize, delayBetweenChunks);
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         assertTrue("Exception occured when it should not " + ex.getMessage(), false);
      }
   }
   
   
   /**
    * Invoke: java org.xmlBlaster.test.client.TestFilePollerPlugin
    * <p />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.client.TestFilePollerPlugin</pre>
    */
   public static void main(String args[]) {
      Global global = new Global();
      if (global.init(args) != 0) {
         System.out.println(ME + ": Init failed");
         System.exit(1);
      }

      TestFilePollerPlugin test = new TestFilePollerPlugin(global);

      test.setUp();
      test.testSystem();
      test.tearDown();

   }
}

