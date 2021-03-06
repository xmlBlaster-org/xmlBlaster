// xmlBlaster/demo/javaclients/HelloWorldGet.java
package javaclients;

import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

import org.xmlBlaster.util.FileLocator;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
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
 * java javaclients.HelloWorldGet -xpath //key  -historyNum 2
 *
 * java javaclients.HelloWorldGet -interactive false -oid Hello  -historyNum -1
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
   private static Logger log = Logger.getLogger(HelloWorldGet.class.getName());

   public HelloWorldGet(Global glob) {

      try {
         boolean interactive = glob.getProperty().get("interactive", true);
         String oid = glob.getProperty().get("oid", "");
         String domain = glob.getProperty().get("domain", (String)null);
         String xpath = glob.getProperty().get("xpath", "");
         int historyNumUpdates = glob.getProperty().get("historyNum", 1);
         boolean historyNewestFirst = glob.getProperty().get("historyNewestFirst", true);
         String filterType = glob.getProperty().get("filter.type", "GnuRegexFilter");// XPathFilter | ContentLenFilter
         String filterVersion = glob.getProperty().get("filter.version", "1.0");
         String filterQuery = glob.getProperty().get("filter.query", "");
         boolean content = glob.getProperty().get("content", true);
         boolean saveToFile = glob.getProperty().get("saveToFile", false);
         boolean disconnect = glob.getProperty().get("disconnect", true);
         String queryOid = glob.getProperty().get("query.oid", (String)null);
         int queryMaxEntries = glob.getProperty().get("query.maxEntries", 1);
         long queryTimeout = glob.getProperty().get("query.timeout", 0L);
         boolean queryConsumable = glob.getProperty().get("query.consumable", true);

         Map clientPropertyMap = glob.getProperty().get("clientProperty", (Map)null);
         Map connectQosClientPropertyMap = glob.getProperty().get("connect/qos/clientProperty", (Map)null);

         if (oid.length() < 1 && xpath.length() < 1) {
            log.warning("No -oid or -xpath given, we subscribe to oid='Hello'.");
            oid = "Hello";
         }

         log.info("Used settings are:");
         log.info("   -interactive        " + interactive);
         log.info("   -oid                " + oid);
         log.info("   -domain             " + domain);
         log.info("   -xpath              " + xpath);
         log.info("   -historyNum         " + historyNumUpdates);
         log.info("   -historyNewestFirst " + historyNewestFirst);
         log.info("   -content            " + content);
         log.info("   -disconnect         " + disconnect);
         log.info("   -filter.type        " + filterType);
         log.info("   -filter.version     " + filterVersion);
         log.info("   -filter.query       " + filterQuery);
         
         //            "topic/hello" to access a history queue,
         //            "client/joe" to access a subject queue or
         //            "client/joe/session/1"
         log.info("   -query.oid          " + queryOid);
         log.info("   -query.maxEntries   " + queryMaxEntries);
         log.info("   -query.timeout      " + queryTimeout);
         log.info("   -query.consumable   " + queryConsumable);
         log.info("   -saveToFile         " + saveToFile);
         if (clientPropertyMap != null) {
            Iterator it = clientPropertyMap.keySet().iterator();
            while (it.hasNext()) {
               String key = (String)it.next();
               log.info("   -clientProperty["+key+"]   " + clientPropertyMap.get(key).toString());
            }
         }
         else {
            log.info("   -clientProperty[]   ");
         }
         if (connectQosClientPropertyMap != null) {
            Iterator it = connectQosClientPropertyMap.keySet().iterator();
            while (it.hasNext()) {
               String key = (String)it.next();
               log.info("   -connect/qos/clientProperty["+key+"]   " + connectQosClientPropertyMap.get(key).toString());
            }
         }
         else {
            log.info("   -connect/qos/clientProperty[]   ");
         }
         log.info("For more info please read:");
         log.info("   http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.get.html");

         I_XmlBlasterAccess con = glob.getXmlBlasterAccess();

         // ConnectQos checks -session.name and -passwd from command line
         log.info("============= CreatingConnectQos");
         ConnectQos qos = new ConnectQos(glob);
         if (connectQosClientPropertyMap != null) {
            Iterator it = connectQosClientPropertyMap.keySet().iterator();
            while (it.hasNext()) {
               String key = (String)it.next();
               qos.addClientProperty(key, connectQosClientPropertyMap.get(key).toString());
            }
         }
         log.info("ConnectQos is " + qos.toXml());
         ConnectReturnQos crq = con.connect(qos, null);  // Login to xmlBlaster
         log.info("Connect success as " + crq.toXml());
         
         MsgUnit[] msgs = null;
         if (queryOid != null) {
            // http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.qos.queryspec.QueueQuery.html
            if (interactive) {
               log.info("Hit a key to get '" + queryOid + "' maxEntries=" + queryMaxEntries + " timeout=" + queryTimeout + " consumable=" + queryConsumable);
               try { System.in.read(); } catch(java.io.IOException e) {}
            }
            msgs = con.receive(queryOid, queryMaxEntries, queryTimeout, queryConsumable);
         }
         else {
            GetKey gk = (oid.length() > 0) ? new GetKey(glob, oid) : new GetKey(glob, xpath, Constants.XPATH);
            if (domain != null) gk.setDomain(domain);
            GetQos gq = new GetQos(glob);
            gq.setWantContent(content);
            
            HistoryQos historyQos = new HistoryQos(glob);
            historyQos.setNumEntries(historyNumUpdates);
            historyQos.setNewestFirst(historyNewestFirst);
            gq.setHistoryQos(historyQos);
   
            if (filterQuery.length() > 0) {
               AccessFilterQos filter = new AccessFilterQos(glob, filterType, filterVersion, filterQuery);
               gq.addAccessFilter(filter);
            }
            if (clientPropertyMap != null) {
               Iterator it = clientPropertyMap.keySet().iterator();
               while (it.hasNext()) {
                  String key = (String)it.next();
                  gq.addClientProperty(key, clientPropertyMap.get(key).toString());
               }
               //Example for a typed property:
               //pq.getData().addClientProperty("ALONG", (new Long(12)));
            }

            log.info("GetKey=\n" + gk.toXml());
            log.info("GetQos=\n" + gq.toXml());

            if (interactive) {
               log.info("Hit a key to get '" + ((oid.length() > 0) ? oid : xpath) + "'");
               try { System.in.read(); } catch(java.io.IOException e) {}
            }

            msgs = con.get(gk.toXml(), gq.toXml());
         }

         for(int imsg=0; imsg<msgs.length; imsg++) {
            GetReturnKey grk = new GetReturnKey(glob, msgs[imsg].getKey());
            GetReturnQos grq = new GetReturnQos(glob, msgs[imsg].getQos());

            System.out.println("");
            System.out.println("============= START #" + (imsg+1) + " '" + grk.getOid() + "' =======================");
            log.info("Receiving update #" + (imsg+1) + " of a message ...");
            System.out.println("<xmlBlaster>");
            System.out.println(msgs[imsg].toXml("", 100, true));
            System.out.println("</xmlBlaster>");
            System.out.println("============= END #" + (imsg+1) + " '" + grk.getOid() + "' =========================");
            System.out.println("");

            if (saveToFile) {
               String fileName = grk.getOid()+"-"+grq.getRcvTimestamp().getTimestamp();
               try {
                  FileLocator.writeFile(fileName, msgs[imsg].toXml("").getBytes());
               }
               catch (XmlBlasterException e) {
                  System.out.println("Can't dump content to file '" + fileName + "': " + e.toString());
               }
            }
            
         }
         if (msgs.length == 0) {
            log.info("Sorry, no message found for '" + ((oid.length() > 0) ? oid : xpath) + "'");
         }

         log.info("Hit a key to exit");
         try { System.in.read(); } catch(java.io.IOException e) {}

         if (disconnect) {
            DisconnectQos dq = new DisconnectQos(glob);
            con.disconnect(dq);
         }
      }
      catch (XmlBlasterException e) {
         log.severe(e.getMessage());
      }
      catch (Exception e) {
         e.printStackTrace();
         log.severe(e.toString());
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
         System.err.println("  java javaclients.HelloWorldGet  -clientProperty[myString] Hello -clientProperty[correlationId] 100\n");
         System.exit(1);
      }

      new HelloWorldGet(glob);
   }
}
