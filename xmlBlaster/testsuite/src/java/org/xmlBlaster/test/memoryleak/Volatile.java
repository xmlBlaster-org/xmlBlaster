package org.xmlBlaster.test.memoryleak;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;

import java.io.*;


/**
 * You can use this client to test with tools like OptimizeIt if we
 * have memory leaks with volatile messages.
 *
 * Invoke: java Volatile -loginName joe -passwd secret
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
 */
public class Volatile
{
   private final String ME = "Volatile";
   private final LogChannel log;
   private I_XmlBlasterAccess con = null;
   private ConnectReturnQos conRetQos = null;
   private boolean connected;

   public Volatile(final Global glob) {
      
      log = glob.getLog(null);
      long lCount = 0L;

      try {
         con = glob.getXmlBlasterAccess();
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
         qw.setVolatile(true);
         System.out.println("qos = " + qw.toXml() );
         byte[] b = new byte[1024];
         while(true) {
            lCount++;
            xmlKey =  "<key oid='" + lCount +
                           "'> <topic id='aaaa'/>" +
                           "</key>";
            con.publish(new MsgUnit(xmlKey,b,qw.toXml()));
            // System.out.println(new Timestamp(System.currentTimeMillis())+":"+lCount);
            if ((lCount % 100L) == 0) {
               log.info(ME, "Sent " + lCount + " messages, enter return to continue, enter 'q' to quit");
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

            try { Thread.currentThread().sleep(5L); } catch( InterruptedException i) {}
         }
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Houston, we have a problem count=" + lCount + ": " + e.toString());
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
    *   java org.xmlBlaster.test.memoryleak.Volatile -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         System.out.println(glob.usage());
         System.out.println("Example: java Volatile -loginName Jeff\n");
         System.exit(1);
      }

      new Volatile(glob);
   }
}
