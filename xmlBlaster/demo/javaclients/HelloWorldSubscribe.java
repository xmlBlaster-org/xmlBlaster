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
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;


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
 *
 * java javaclients.HelloWorldSubscribe -xpath //key -filter.type GnuRegexFilter -filter.query "^__sys__jdbc.*"
 *
 * java javaclients.HelloWorldSubscribe -xpath //key -filter.type XPathFilter -filter.query "//tomato"
 *
 * java javaclients.HelloWorldSubscribe -xpath //key -filter.type ContentLenFilter -filter.query "10"
 * </pre>
 * <p>
 * If unSubscribe=false the message is not unsubscribed at the end, if disconnect=false we don't logout at the end.
 * </p>
 * @see java javaclients.HelloWorldPublish
 * @see java javaclients.HelloWorldGet
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 */
public class HelloWorldSubscribe implements I_Callback
{
   private final String ME = "HelloWorldSubscribe";
   private final Global glob;
   private final LogChannel log;
   private int updateCounter;
   private boolean interactiveUpdate;
   private long updateSleep;

   public HelloWorldSubscribe(Global glob) {
      this.glob = glob;
      this.log = glob.getLog("HelloWorldSubscribe");
      I_XmlBlasterAccess con = null;
      boolean disconnect = glob.getProperty().get("disconnect", true);
      try {
         boolean interactive = glob.getProperty().get("interactive", true);
         this.interactiveUpdate = glob.getProperty().get("interactiveUpdate", false);
         this.updateSleep = glob.getProperty().get("updateSleep", 0L);
         String oid = glob.getProperty().get("oid", "");
         String domain = glob.getProperty().get("domain", "");
         String xpath = glob.getProperty().get("xpath", "");
         boolean multiSubscribe = glob.getProperty().get("multiSubscribe", true);
         boolean local = glob.getProperty().get("local", true);
         boolean initialUpdate = glob.getProperty().get("initialUpdate", true);
         boolean wantContent = glob.getProperty().get("wantContent", true);
         int historyNumUpdates = glob.getProperty().get("historyNumUpdates", 1);
         String filterType = glob.getProperty().get("filter.type", "GnuRegexFilter");// XPathFilter | ContentLenFilter
         String filterVersion = glob.getProperty().get("filter.version", "1.0");
         String filterQuery = glob.getProperty().get("filter.query", "");
         boolean unSubscribe = glob.getProperty().get("unSubscribe", true);

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

         if (this.updateSleep > 0L && interactiveUpdate == true) {
            log.warn(ME, "You can't set 'updateSleep' and  'interactiveUpdate' simultaneous, we reset interactiveUpdate to false");
            this.interactiveUpdate = false;
         }

         log.info(ME, "Used settings are:");
         log.info(ME, "   -interactive       " + interactive);
         log.info(ME, "   -interactiveUpdate " + this.interactiveUpdate);
         log.info(ME, "   -updateSleep       " + this.updateSleep);
         log.info(ME, "   -oid               " + oid);
         log.info(ME, "   -domain            " + domain);
         log.info(ME, "   -xpath             " + xpath);
         log.info(ME, "   -multiSubscribe    " + multiSubscribe);
         log.info(ME, "   -local             " + local);
         log.info(ME, "   -initialUpdate     " + initialUpdate);
         log.info(ME, "   -historyNumUpdates " + historyNumUpdates);
         log.info(ME, "   -wantContent       " + wantContent);
         log.info(ME, "   -unSubscribe       " + unSubscribe);
         log.info(ME, "   -disconnect        " + disconnect);
         log.info(ME, "   -filter.type       " + filterType);
         log.info(ME, "   -filter.version    " + filterVersion);
         log.info(ME, "   -filter.query      " + filterQuery);
         log.info(ME, "For more info please read:");
         log.info(ME, "   http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html");

         con = glob.getXmlBlasterAccess();

         // ConnectQos checks -session.name and -passwd from command line
         log.info(ME, "============= CreatingConnectQos");
         ConnectQos qos = new ConnectQos(glob);
         log.info(ME, "ConnectQos is " + qos.toXml());
         ConnectReturnQos crq = con.connect(qos, this);  // Login to xmlBlaster, register for updates
         log.info(ME, "Connect success as " + crq.toXml());

         SubscribeKey sk = null;
         String qStr = null;
         if (domain.length() > 0) {
            sk = new SubscribeKey(glob, "", Constants.DOMAIN);
            sk.setDomain(domain);
            qStr = domain;
         }
         else if (oid.length() > 0) {
            sk = new SubscribeKey(glob, oid);
            qStr = oid;
         }
         else if (xpath.length() > 0) {
            sk = new SubscribeKey(glob, xpath, Constants.XPATH);
            qStr = xpath;
         }
         SubscribeQos sq = new SubscribeQos(glob);
         sq.setWantInitialUpdate(initialUpdate);
         sq.setMultiSubscribe(multiSubscribe);
         sq.setWantLocal(local);
         sq.setWantContent(wantContent);
         
         HistoryQos historyQos = new HistoryQos(glob);
         historyQos.setNumEntries(historyNumUpdates);
         sq.setHistoryQos(historyQos);

         if (filterQuery.length() > 0) {
            AccessFilterQos filter = new AccessFilterQos(glob, filterType, filterVersion, filterQuery);
            sq.addAccessFilter(filter);
         }

         log.info(ME, "SubscribeKey=\n" + sk.toXml());
         log.info(ME, "SubscribeQos=\n" + sq.toXml());

         if (interactive) {
            log.info(ME, "Hit a key to subscribe '" + qStr + "'");
            try { System.in.read(); } catch(java.io.IOException e) {}
         }

         SubscribeReturnQos srq = con.subscribe(sk, sq);

         log.info(ME, "Subscribed on topic '" + ((oid.length() > 0) ? oid : xpath) +
                      "', got subscription id='" + srq.getSubscriptionId() + "'");
         if (log.DUMP) log.dump("", "Subscribed: " + sk.toXml() + sq.toXml() + srq.toXml());
         log.info(ME, "Waiting on update ...");

         if (interactiveUpdate) {
            try { Thread.currentThread().sleep(1000000000); } catch( InterruptedException i) {}
         }

         if (unSubscribe) {
            if (interactive) {
               log.info(ME, "Hit a key to unSubscribe");
               try { System.in.read(); } catch(java.io.IOException e) {}
            }

            UnSubscribeKey uk = new UnSubscribeKey(glob, srq.getSubscriptionId());
            UnSubscribeQos uq = new UnSubscribeQos(glob);
            UnSubscribeReturnQos[] urqArr = con.unSubscribe(uk, uq);
            log.info(ME, "UnSubscribe on " + urqArr.length + " subscriptions done");
         }

         log.info(ME, "Hit a key to exit");
         try { System.in.read(); } catch(java.io.IOException e) {}
      }
      catch (XmlBlasterException e) {
         log.error(ME, e.getMessage());
      }
      catch (Exception e) {
         e.printStackTrace();
         log.error(ME, e.toString());
      }
      finally {
         if (con != null && disconnect) {
            DisconnectQos dq = new DisconnectQos(glob);
            con.disconnect(dq);
            log.info(ME, "Disconnected");
         }
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

      if (this.updateSleep > 0L) {
         log.info(ME, "Sleeping for " + this.updateSleep + " millis ...");
         try { Thread.currentThread().sleep(this.updateSleep); } catch( InterruptedException i) {}
         log.info(ME, "Waking up.");
      } else if (this.interactiveUpdate) {
         log.info(ME, "Hit a key to return from update() (we are blocking the server callback) ...");
         try { System.in.read(); } catch(java.io.IOException e) {}
         log.info(ME, "Returning update() - control goes back to server");
      }
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
         System.out.println(glob.usage());
         System.err.println("\nExample:");
         System.err.println("  java javaclients.HelloWorldSubscribe -oid Hello -initialUpdate true\n");
         System.exit(1);
      }

      new HelloWorldSubscribe(glob);
   }
}
