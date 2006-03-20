package org.xmlBlaster.test.memoryleak;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.*;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.MsgUnit;

import java.io.*;


/**
 * You can use this client to test with tools like OptimizeIt if we
 * have memory leaks with volatile messages.
 *
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
 */
public class PublishSame
{
   private final String ME = "PublishSame";
   private static Logger log = Logger.getLogger(PublishSame.class.getName());
   private I_XmlBlasterAccess con = null;
   private ConnectReturnQos conRetQos = null;
   private boolean connected;
   private int bulkSize = 100;
   private boolean interactive = true;

   public PublishSame(final Global glob) {
      

      long lCount = 0L;
      bulkSize = glob.getProperty().get("bulkSize", bulkSize);
      interactive = glob.getProperty().get("interactive", interactive);

      try {
         con = glob.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(glob);
         conRetQos = con.connect(qos, new I_Callback() {

            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               log.info("Receiving asynchronous message '" + updateKey.getOid() +
                               "' state=" + updateQos.getState() + " in default handler");
               return "";
            }

         });  // Login to xmlBlaster, default handler for updates

         String xmlKey = null;
         PublishQos qw = new PublishQos(glob);
         System.out.println("qos = " + qw.toXml() );
         byte[] b = new byte[1024];
         while(true) {
            lCount++;
            xmlKey =  "<key oid='OneMessage'> <topic id='aaaa'/> </key>";
            con.publish(new MsgUnit(xmlKey,b,qw.toXml()));
            // System.out.println(new Timestamp(System.currentTimeMillis())+":"+lCount);
            if ((lCount % bulkSize) == 0) {
               try {
                  if (interactive) {
                     log.info("Sent " + lCount + " identical messages, enter return to continue, enter 'q' to quit");
                     BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
                     String line = in.readLine(); // Blocking in I/O
                     if (line == null) continue;
                     line = line.trim();
                     if (line.toLowerCase().equals("q")) {
                        break;
                     }
                  }
                  else {
                     log.info("Sent " + lCount + " identical messages, sleeping 10 sec");
                     Thread.sleep(10000);
                  }
               }
               catch(Exception e) {
                  log.severe(e.toString());
                  break;
               }
            }

            try { Thread.sleep(5L); } catch( InterruptedException i) {}
         }
      }
      catch (XmlBlasterException e) {
         log.severe("Houston, we have a problem count=" + lCount + ": " + e.toString());
      }
      finally {
         log.info("Success, hit a key to logout and exit");
         try { System.in.read(); } catch(java.io.IOException e) {}
         con.disconnect(new DisconnectQos(glob));
      }
   }

   /**
    * Try
    * <pre>
    *   java org.xmlBlaster.test.memoryleak.PublishSame -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         System.out.println(glob.usage());
         System.out.println("Example: java PublishSame -loginName Jeff\n");
         System.exit(1);
      }

      new PublishSame(glob);
   }
}
