/*------------------------------------------------------------------------------
Name:      SystemInfoPublisher.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a client to publish system infos to xmlBlaster
Version:   $Id: SystemInfoPublisher.java,v 1.3 2001/12/16 20:32:45 ruff Exp $
------------------------------------------------------------------------------*/
package html.systemInfo;

import org.xmlBlaster.util.Log;
import org.jutils.init.Args;
import org.jutils.io.FileUtil;
import org.jutils.time.StopWatch;
import org.jutils.JUtilsException;

import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.PublishKeyWrapper;
import org.xmlBlaster.client.PublishQosWrapper;
import org.xmlBlaster.engine.helper.MessageUnit;

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
   private XmlBlasterConnection corbaConnection;
   private String loginName;
   private String passwd;
   private Random random = new Random();

   /**
    * Constructs the SystemInfoPublisher object.
    * <p />
    * @param args      Command line arguments
    */
   public SystemInfoPublisher(String[] args) throws JUtilsException
   {
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      if (Args.getArg(args, "-?") == true || Args.getArg(args, "-h") == true) {
         usage();
         return;
      }

      loginName = Args.getArg(args, "-name", ME);
      passwd = Args.getArg(args, "-passwd", "secret");

      String osName = System.getProperty("os.name");     // "Linux" "Windows NT" ...
      if (!osName.startsWith("Linux")) {
         Log.panic(ME, "This system load publisher runs only on Linux, sorry about that\n" +
                   "Note that you can use own Perl or C++ or Java publishers to do this task");
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
            Log.error(ME, e.reason);
         }
      }

      //tearDown();  // logout
   }


   /**
    * The Linux way ...
    * <p />
    * For now it is a random hack
    */
   private int getCpuload() throws XmlBlasterException
   {
      // String text = FileUtil.readAsciiFile("/proc/cpuinfo");
      // Log.info(ME, "cpuinfo=\n" + text);
      return random.nextInt(100); // hack!
   }


   /**
    * The Linux way ...
    * <p />
    * For now it is a random hack
    */
   private int getMeminfo() throws XmlBlasterException
   {
      // String text = FileUtil.readAsciiFile("/proc/meminfo");
      // Log.info(ME, "meminfo=\n" + text);
      int val = random.nextInt(100); // hack!
      if (val < 11) val = 11;
      if (val > 96) val = 96;
      return val;
   }


   /**
    * Connect to xmlBlaster and login
    */
   private void setUp()
   {
      try {
         corbaConnection = new XmlBlasterConnection(); // Find orb
         corbaConnection.login(loginName, passwd, null); // Login to xmlBlaster
      }
      catch (Exception e) {
          Log.error(ME, e.toString());
          e.printStackTrace();
      }
   }


   /**
    * Logout from xmlBlaster
    */
   private void tearDown()
   {
      corbaConnection.logout();
   }


   /**
    * Construct a message and publish it.
    */
   private void publish(String oid, int value)
   {
      if (corbaConnection == null)
         return;

      String content = "" + value;
      String xmlKey = "<key oid='" + oid + "' contentMime='text/plain' contentMimeExtended='systemInfo'>\n" +
                      "   <systemInfo />" +
                      "</key>";
      PublishQosWrapper qosWrapper = new PublishQosWrapper();
      String qos = qosWrapper.toXml(); // == "<qos></qos>"
      MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes(), qos);

      try {
         corbaConnection.publish(msgUnit);
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
      }

      Log.info(ME, "Published message " + oid + " with value " + content);
   }


   /**
    * Command line usage.
    */
   private void usage()
   {
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "jaco html.systemInfo.SystemInfoPublisher <options>");
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "Options:");
      Log.plain(ME, "   -?                  Print this message.");
      Log.plain(ME, "");
      Log.plain(ME, "   -name <LoginName>   Your xmlBlaster login name.");
      Log.plain(ME, "   -passwd <Password>  Your xmlBlaster password.");
      Log.plain(ME, "");
      XmlBlasterConnection.usage();
      Log.usage();
      Log.plain(ME, "");
   }


   /**
    * jaco html.systemInfo.SystemInfoPublisher
    */
   public static void main(String args[])
   {
      try {
         SystemInfoPublisher publishFile = new SystemInfoPublisher(args);
      } catch (org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      Log.exit(SystemInfoPublisher.ME, "Good bye");
   }
}

