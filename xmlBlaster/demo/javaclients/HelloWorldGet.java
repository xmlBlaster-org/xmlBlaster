// xmlBlaster/demo/javaclients/HelloWorldGet.java
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
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.key.GetReturnKey;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.GetReturnQos;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.XmlBlasterAccess;


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
 * java javaclients.HelloWorldGet -xpath //key  -numHistory 2
 *
 * java javaclients.HelloWorldGet -interactive false -oid Hello  -numHistory -1
 *
 * java javaclients.HelloWorldGet -session.name joeGet/5 -passwd secret
 *
 * java javaclients.HelloWorldGet -xpath //key -filter.type GnuRegexFilter -filter.query "^__sys__jdbc.*"
 *
 * java javaclients.HelloWorldGet -xpath //key -filter.type XPathFilter -filter.query "//tomato"
 *
 * java javaclients.HelloWorldGet -xpath //key -filter.type ContentLenFilter -filter.query "10"
 * </pre>
 * <p>
 * If disconnect=false we don't logout at the end.
 * </p>
 * @see java javaclients.HelloWorldPublish
 * @see java javaclients.HelloWorldSubscribe
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.get.html" target="others">xmlBlaster get interface</a>
 */
public class HelloWorldGet
{
   private final String ME = "HelloWorldGet";
   private final Global glob;
   private final LogChannel log;
   private int updateCounter = 0;

   public HelloWorldGet(Global glob) {
      this.glob = glob;
      this.log = glob.getLog("HelloWorldGet");
      try {
         boolean interactive = glob.getProperty().get("interactive", true);
         String oid = glob.getProperty().get("oid", "");
         String xpath = glob.getProperty().get("xpath", "");
         int numHistory = glob.getProperty().get("numHistory", 1);
         String filterType = glob.getProperty().get("filter.type", "GnuRegexFilter");// XPathFilter | ContentLenFilter
         String filterVersion = glob.getProperty().get("filter.version", "1.0");
         String filterQuery = glob.getProperty().get("filter.query", "");
         boolean content = glob.getProperty().get("content", true);
         boolean disconnect = glob.getProperty().get("disconnect", true);

         if (oid.length() < 1 && xpath.length() < 1) {
            log.warn(ME, "No -oid or -xpath given, we subscribe to oid='Hello'.");
            oid = "Hello";
         }

         log.info(ME, "Used settings are:");
         log.info(ME, "   -interactive       " + interactive);
         log.info(ME, "   -oid               " + oid);
         log.info(ME, "   -xpath             " + xpath);
         log.info(ME, "   -numHistory        " + numHistory);
         log.info(ME, "   -content           " + content);
         log.info(ME, "   -disconnect        " + disconnect);
         log.info(ME, "   -filter.type       " + filterType);
         log.info(ME, "   -filter.version    " + filterVersion);
         log.info(ME, "   -filter.query      " + filterQuery);
         log.info(ME, "For more info please read:");
         log.info(ME, "   http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.get.html");

         I_XmlBlasterAccess con = glob.getXmlBlasterAccess();

         // ConnectQos checks -session.name and -passwd from command line
         log.info(ME, "============= CreatingConnectQos");
         ConnectQos qos = new ConnectQos(glob);
         log.info(ME, "ConnectQos is " + qos.toXml());
         ConnectReturnQos crq = con.connect(qos, null);  // Login to xmlBlaster
         log.info(ME, "Connect success as " + crq.toXml());

         GetKey gk = (oid.length() > 0) ? new GetKey(glob, oid) : new GetKey(glob, xpath, Constants.XPATH);
         GetQos gq = new GetQos(glob);
         gq.setWantContent(content);
         
         HistoryQos historyQos = new HistoryQos(glob);
         historyQos.setNumEntries(numHistory);
         gq.setHistoryQos(historyQos);

         if (filterQuery.length() > 0) {
            AccessFilterQos filter = new AccessFilterQos(glob, filterType, filterVersion, filterQuery);
            gq.addAccessFilter(filter);
         }

         log.info(ME, "GetKey=\n" + gk.toXml());
         log.info(ME, "GetQos=\n" + gq.toXml());

         if (interactive) {
            log.info(ME, "Hit a key to get '" + ((oid.length() > 0) ? oid : xpath) + "'");
            try { System.in.read(); } catch(java.io.IOException e) {}
         }

         MsgUnit[] msgs = con.get(gk.toXml(), gq.toXml());
         for(int imsg=0; imsg<msgs.length; imsg++) {
            GetReturnKey grk = new GetReturnKey(glob, msgs[imsg].getKey());
            GetReturnQos grq = new GetReturnQos(glob, msgs[imsg].getQos());
            String contentStr = msgs[imsg].getContentStr();

            System.out.println("");
            System.out.println("============= START #" + (imsg+1) + " '" + grk.getOid() + "' =======================");
            log.info(ME, "Receiving update #" + (imsg+1) + " of a message ...");
            System.out.println("<xmlBlaster>");
            System.out.println(msgs[imsg].toXml("", 100));
            System.out.println("</xmlBlaster>");
            System.out.println("============= END #" + (imsg+1) + " '" + grk.getOid() + "' =========================");
            System.out.println("");
            
         }
         if (msgs.length == 0) {
            log.info(ME, "Sorry, no message found for '" + ((oid.length() > 0) ? oid : xpath) + "'");
         }

         log.info(ME, "Hit a key to exit");
         try { System.in.read(); } catch(java.io.IOException e) {}

         if (disconnect) {
            DisconnectQos dq = new DisconnectQos(glob);
            con.disconnect(dq);
         }
      }
      catch (XmlBlasterException e) {
         log.error(ME, e.getMessage());
      }
      catch (Exception e) {
         e.printStackTrace();
         log.error(ME, e.toString());
      }
   }

   /**
    * Try
    * <pre>
    *   java javaclients.HelloWorldGet -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         System.out.println(glob.usage());
         System.err.println("\nExample:");
         System.err.println("  java javaclients.HelloWorldGet -oid Hello -initialUpdate true\n");
         System.exit(1);
      }

      new HelloWorldGet(glob);
   }
}
