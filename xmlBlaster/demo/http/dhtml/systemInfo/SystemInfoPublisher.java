/*------------------------------------------------------------------------------
Name:      SystemInfoPublisher.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a client to publish system infos to xmlBlaster
Version:   $Id: SystemInfoPublisher.java,v 1.8 2002/12/18 13:52:46 ruff Exp $
------------------------------------------------------------------------------*/
package http.dhtml.systemInfo;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.jutils.io.FileUtil;
import org.jutils.time.StopWatch;
import org.jutils.JUtilsException;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.util.MsgUnit;

import java.io.File;
import java.util.Random;


/**
 * Publish system infos to xmlBlaster.
 * <br />
 * Use this as a command line tool to publish cpu load and memory load.
 * <br />
 * Note: This publisher only works on Linux!
 * <br />
 * Invoke examples:<br />
 * <pre>
 *    jaco html.systemInfo.SystemInfoPublisher
 * </pre>
 */
public class SystemInfoPublisher
{
   private static final String ME = "SystemInfoPublisher";
   private final Global glob;
   private final LogChannel log;
   private XmlBlasterConnection con;
   private Random random = new Random();

   /**
    * Constructs the SystemInfoPublisher object.
    * <p />
    * @param args      Command line arguments
    */
   public SystemInfoPublisher(Global glob) {
      this.glob = glob;
      this.log = glob.getLog("client");
      String osName = System.getProperty("os.name");     // "Linux" "Windows NT" ...
      if (!osName.startsWith("Linux")) {
         log.error(ME, "This system load publisher runs only on Linux, sorry about that\n" +
                   "Note that you can use own Perl or C++ or Java publishers to do this task");
         System.exit(1);
      }

      setUp();  // login

      while(true) {
         try {
            int val = random.nextInt(6000); // between 0 and 6 sec
            if (val < 2000) val = 2000;
            try { Thread.currentThread().sleep(val); } catch( InterruptedException i) {}
            int cpu = getCpuload();
            publish("cpuinfo", cpu);

            val = random.nextInt(6000); // between 0 and 6 sec
            if (val < 2000) val = 2000;
            try { Thread.currentThread().sleep(val); } catch( InterruptedException i) {}
            int mem = getMeminfo();
            publish("meminfo", mem);
         }
         catch (XmlBlasterException e) {
            log.error(ME, e.getMessage());
         }
      }

      //tearDown();  // logout
   }

   /**
    * The Linux way ...
    * <p />
    * For now it is a random hack
    */
   private int getCpuload() throws XmlBlasterException {
      // String text = FileUtil.readAsciiFile("/proc/cpuinfo");
      // log.info(ME, "cpuinfo=\n" + text);
      return random.nextInt(100); // hack!
   }

   /**
    * The Linux way ...
    * <p />
    * For now it is a random hack
    */
   private int getMeminfo() throws XmlBlasterException {
      // String text = FileUtil.readAsciiFile("/proc/meminfo");
      // log.info(ME, "meminfo=\n" + text);
      int val = random.nextInt(100); // hack!
      if (val < 11) val = 11;
      if (val > 96) val = 96;
      return val;
   }

   /**
    * Connect to xmlBlaster and login
    */
   private void setUp() {
      try {
         con = new XmlBlasterConnection(glob);
         con.connect(null, null); // Login to xmlBlaster
      }
      catch (Exception e) {
          log.error(ME, e.toString());
          e.printStackTrace();
      }
   }

   /**
    * Logout from xmlBlaster
    */
   private void tearDown() {
      con.disconnect(null);
   }


   /**
    * Construct a message and publish it.
    */
   private void publish(String oid, int value) {
      if (con == null)
         return;

      String content = "" + value;
      /*
      String xmlKey = "<key oid='" + oid + "' contentMime='text/plain' contentMimeExtended='systemInfo'>\n" +
                      "   <systemInfo />" +
                      "</key>";
      */
      PublishKey key = new PublishKey(glob, oid, "text/plain", "systemInfo");
      key.setClientTags("<systemInfo />");
      PublishQos qos = new PublishQos(glob);
      MsgUnit msgUnit = new MsgUnit(glob, key, content.getBytes(), qos);

      try {
         con.publish(msgUnit);
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
      }

      log.info(ME, "Published message " + oid + " with value " + content);
   }

   /**
    * java html.systemInfo.SystemInfoPublisher
    */
   public static void main(String args[]) {
      new SystemInfoPublisher(new Global(args));
   }
}

