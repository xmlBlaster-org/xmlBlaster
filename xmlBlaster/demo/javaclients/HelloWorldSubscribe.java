// xmlBlaster/demo/javaclients/HelloWorldSubscribe.java
package javaclients;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.qos.HistoryQos;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.UpdateReturnQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;


/**
 * This client connects to xmlBlaster and subscribes to messages. 
 * <p>
 * This is a nice client to experiment and play with xmlBlaster as there are many
 * command line options to specify the type and amount of messages published.
 * </p>
 * <p>
 * Try using 'java javaclients.HelloWorldPublish' in another window to publish some
 * messages.
 * Further you can type 'd' in the window running xmlBlaster to get a server dump.
 * </p>
 *
 * Invoke (after starting the xmlBlaster server):
 * <pre>
 * java javaclients.HelloWorldSubscribe -xpath //key -initialUpdate true -unSubscribe true
 *
 * java javaclients.HelloWorldSubscribe -interactive false -oid Hello -initialUpdate true -unSubscribe true
 *
 * java javaclients.HelloWorldSubscribe -session.name joeSubscriber/5 -passwd secret -initialUpdate true -dump[HelloWorldSubscribe] true
 * </pre>
 * <p>
 * If unSubscribe=false the message is not unsubscribed at the end, if disconnect=false we don't logout at the end.
 * </p>
 * @see java javaclients.HelloWorldPublish
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 */
public class HelloWorldSubscribe implements I_Callback
{
   private final String ME = "HelloWorldSubscribe";
   private final Global glob;
   private final LogChannel log;
   private int updateCounter = 0;

   public HelloWorldSubscribe(Global glob) {
      this.glob = glob;
      this.log = glob.getLog("HelloWorldSubscribe");
      try {
         boolean interactive = glob.getProperty().get("interactive", true);
         String oid = glob.getProperty().get("oid", "");
         String xpath = glob.getProperty().get("xpath", "");
         boolean local = glob.getProperty().get("local", true);
         boolean initialUpdate = glob.getProperty().get("initialUpdate", true);
         int historyNumUpdates = glob.getProperty().get("historyNumUpdates", 1);
         boolean content = glob.getProperty().get("content", true);
         boolean unSubscribe = glob.getProperty().get("unSubscribe", true);
         boolean disconnect = glob.getProperty().get("disconnect", true);

         if (oid.length() < 1 && xpath.length() < 1) {
            log.warn(ME, "No -oid or -xpath given, we subscribe to oid='Hello'.");
            oid = "Hello";
            /*
            log.error(ME, "Please specify the message oid or an xpath query");
            log.info(ME, "Example:");
            log.info(ME, "  java javaclients.HelloWorldSubscribe -oid HelloMsg");
            log.info(ME, "  java javaclients.HelloWorldSubscribe -xpath //key");
            log.info(ME, "  java javaclients.HelloWorldSubscribe -help    (more help)");
            System.exit(1);
            */
         }

         log.info(ME, "Used settings are:");
         log.info(ME, "   -interactive       " + interactive);
         log.info(ME, "   -oid               " + oid);
         log.info(ME, "   -xpath             " + xpath);
         log.info(ME, "   -local             " + local);
         log.info(ME, "   -initialUpdate     " + initialUpdate);
         log.info(ME, "   -historyNumUpdates " + historyNumUpdates);
         log.info(ME, "   -content           " + content);
         log.info(ME, "   -unSubscribe       " + unSubscribe);
         log.info(ME, "   -disconnect        " + disconnect);
         log.info(ME, "For more info please read:");
         log.info(ME, "   http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html");

         XmlBlasterConnection con = new XmlBlasterConnection(glob);

         // ConnectQos checks -session.name and -passwd from command line
         log.info(ME, "============= CreatingConnectQos");
         ConnectQos qos = new ConnectQos(glob);
         log.info(ME, "ConnectQos is " + qos.toXml());
         ConnectReturnQos crq = con.connect(qos, this);  // Login to xmlBlaster, register for updates
         log.info(ME, "Connect success as " + crq.toXml());

         if (interactive) {
            log.info(ME, "Hit a key to subscribe '" + ((oid.length() > 0) ? oid : xpath) + "'");
            try { System.in.read(); } catch(java.io.IOException e) {}
         }

         SubscribeKey sk = (oid.length() > 0) ? new SubscribeKey(glob, oid) : new SubscribeKey(glob, xpath, Constants.XPATH);
         SubscribeQos sq = new SubscribeQos(glob);
         sq.setWantInitialUpdate(initialUpdate);
         sq.setWantLocal(local);
         sq.setWantContent(content);
         
         HistoryQos historyQos = new HistoryQos(glob);
         historyQos.setNumEntries(historyNumUpdates);
         sq.setHistoryQos(historyQos);

         SubscribeReturnQos srq = con.subscribe(sk.toXml(), sq.toXml());

         log.info(ME, "Subscribed on topic '" + ((oid.length() > 0) ? oid : xpath) +
                      "', got subscription id='" + srq.getSubscriptionId() + "'");
         if (log.DUMP) log.dump("", "Subscribed: " + sk.toXml() + sq.toXml() + srq.toXml());
         log.info(ME, "Waiting on update ...");

         if (unSubscribe) {
            if (interactive) {
               log.info(ME, "Hit a key to unSubscribe");
               try { System.in.read(); } catch(java.io.IOException e) {}
            }

            UnSubscribeKey uk = (oid.length() > 0) ? new UnSubscribeKey(glob, oid) : new UnSubscribeKey(glob, xpath, Constants.XPATH);
            UnSubscribeQos uq = new UnSubscribeQos(glob);
            UnSubscribeReturnQos[] urqArr = con.unSubscribe(uk.toXml(), uq.toXml());
            log.info(ME, "UnSubscribe on " + urqArr.length + " subscriptions done");
         }

         log.info(ME, "Hit a key to exit");
         try { System.in.read(); } catch(java.io.IOException e) {}

         DisconnectQos dq = new DisconnectQos(glob);
         con.disconnect(dq);
      }
      catch (XmlBlasterException e) {
         log.error(ME, e.getMessage());
      }
      catch (Exception e) {
         e.printStackTrace();
         log.error(ME, e.toString());
      }
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content,
                        UpdateQos updateQos) {
      ++updateCounter;
      System.out.println("");
      System.out.println("============= START #" + updateCounter + " '" + updateKey.getOid() + "' =======================");
      log.info(ME, "Receiving update #" + updateCounter + " of a message ...");
      System.out.println("<xmlBlaster>");
      System.out.println(updateKey.toXml());
      System.out.println("");
      System.out.println("<content>");
      if (content.length > 100) {
         System.out.println("  <length>"+content.length+"</length>");
      }
      else {
         System.out.println(new String(content));
      }
      System.out.println("</content>");
      System.out.println(updateQos.toXml());
      System.out.println("</xmlBlaster>");
      System.out.println("============= END #" + updateCounter + " '" + updateKey.getOid() + "' =========================");
      System.out.println("");
      return Constants.RET_OK; // "<qos><state id='OK'/></qos>";
   }

   /**
    * Try
    * <pre>
    *   java javaclients.HelloWorldSubscribe -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         XmlBlasterConnection.usage();
         System.err.println("\nExample:");
         System.err.println("  java javaclients.HelloWorldSubscribe -oid Hello -initialUpdate true\n");
         System.exit(1);
      }

      new HelloWorldSubscribe(glob);
   }
}
