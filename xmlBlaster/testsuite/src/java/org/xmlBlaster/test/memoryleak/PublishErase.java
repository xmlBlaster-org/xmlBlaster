package org.xmlBlaster.test.memoryleak;

// xmlBlaster/demo/javaclients/PublishErase.java
import org.jutils.log.LogChannel;
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
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.util.MsgUnit;

import java.io.*;

/**
 * This client connects to xmlBlaster in fail save mode and uses specific update handlers. 
 * <p />
 * In fail save mode the client will poll for the xmlBlaster server and
 * queue messages until the server is available.
 * <p />
 * Invoke: java PublishErase
 * <p />
 * Invoke: java PublishErase -loginName joe -passwd secret
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
 */
public class PublishErase
{
   private final String ME = "PublishErase";
   private final LogChannel log;
   private XmlBlasterConnection con = null;
   private ConnectReturnQos conRetQos = null;
   private boolean connected;

   public PublishErase(final Global glob) {
      
      log = glob.getLog(null);

      try {
         con = new XmlBlasterConnection(glob);
         ConnectQos qos = new ConnectQos(glob);
         conRetQos = con.connect(qos, new I_Callback() {

            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               log.info(ME, "Receiving asynchronous message '" + updateKey.getOid() +
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
            if ((lCount % 1000L) == 0) {
               log.info(ME, "Published and erased " + lCount + " messages, enter return to continue, enter 'q' to quit");
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
                  log.error(ME, e.toString());
                  break;
               }
            }
         }
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Houston, we have a problem: " + e.toString());
      }
      finally {
         log.info(ME, "Success, hit a key to logout and exit");
         try { System.in.read(); } catch(java.io.IOException e) {}
         con.disconnect(new DisconnectQos(glob));
      }
   }

   /**
    * Try
    * <pre>
    *   java org.xmlBlaster.test.memoryleak.PublishErase -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         XmlBlasterConnection.usage();
         System.err.println("Example: java PublishErase -loginName Jeff\n");
         System.exit(1);
      }

      new PublishErase(glob);
   }
}
