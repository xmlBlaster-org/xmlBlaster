package org.xmlBlaster.test.memoryleak;

// xmlBlaster/demo/javaclients/PublishErase.java
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.MsgUnit;

import java.io.*;

/**
 * Creating/destroying topic in bulks of 100. 
 */
public class PublishErase
{
   private final String ME = "PublishErase";
   private static Logger log = Logger.getLogger(PublishErase.class.getName());
   private I_XmlBlasterAccess con = null;
   private ConnectReturnQos conRetQos = null;
   private boolean connected;
   private int bulkSize = 100;

   public PublishErase(final Global glob) {
      

      bulkSize = glob.getProperty().get("bulkSize", bulkSize);

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
         EraseQos eq = new EraseQos(glob);
         System.out.println("qos = " + qw.toXml() );
         byte[] b = new byte[1024];
         long lCount = 0L;
         while(true) {
            lCount++;
            xmlKey =  "<key oid='" + lCount +
                           "'> <topic id='aaaa'/>" +
                           "</key>";
            con.publish(new MsgUnit(xmlKey,b,qw.toXml()));

            try { Thread.currentThread().sleep(5L); } catch( InterruptedException i) {}

            EraseKey ek = new EraseKey(glob, "" + lCount);
            EraseReturnQos[] er = con.erase(ek.toXml(), eq.toXml());
         
            // System.out.println(new Timestamp(System.currentTimeMillis())+":"+lCount);
            if ((lCount % bulkSize) == 0) {
               log.info("Published and erased " + lCount + " topics, enter return to continue, enter 'q' to quit");
               try {
                  BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
                  String line = in.readLine(); // Blocking in I/O
                  if (line == null) continue;
                  line = line.trim();
                  if (line.toLowerCase().equals("q")) {
                     break;
                  }
               }
               catch(Exception e) {
                  log.severe(e.toString());
                  break;
               }
            }
         }
      }
      catch (XmlBlasterException e) {
         log.severe("Houston, we have a problem: " + e.toString());
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
    *   java org.xmlBlaster.test.memoryleak.PublishErase -bulkSize 200
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         System.out.println(glob.usage());
         System.err.println("Example: java PublishErase -loginName Jeff\n");
         System.exit(1);
      }

      new PublishErase(glob);
   }
}
