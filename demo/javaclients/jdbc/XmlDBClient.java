package javaclients.jdbc;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.XmlDbMessageWrapper;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;


/**
 * Example code how to access the xmlBlaster JDBC service
 * asynchronous with the subscribe() method.
 * <p />
 * The result of the query is delivered asynchronously
 * with the callback update() method.
 * <p />
 * The publishing of the query is not blocking.
 * <p />
 * See README for usage
 *
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.service.rdbms.html">Requirement engine.service.rdbms</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.service.rdbms.jdbcpool.html">Requirement engine.service.rdbms.jdbcpool</a>
 */
public class XmlDBClient implements I_Callback
{
   private static String ME = "XmlDBClient";
   private final Global glob;
   private static Logger log = Logger.getLogger(XmlDBClient.class.getName());
   private I_XmlBlasterAccess con = null;
   private String results;
   private boolean done = false;

   /**
    * Constructor declaration
    */
   public XmlDBClient(Global glob) {
      this.glob = glob;

      initBlaster();
      query();
      waitOnResults();
   }


   /**
    */
   private void waitOnResults() {
      while (!done) {
         try {
            Thread.sleep(1000);
         }
         catch (InterruptedException e) {}
         System.out.println("Waiting...");
      }
      System.out.println(results);
      logout();
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey key, byte[] content, UpdateQos updateQos) {
      results = new String(content);
      log.info("Receiving message oid=" + key.getOid() + " state=" + updateQos.getState());
      done = true;
      return "";
   }

   /**
    * Find xmlBlaster server and login.
    */
   public void initBlaster() {
      try {
         con = glob.getXmlBlasterAccess();
         con.connect(null, this);
         log.info("Connected to xmlBlaster");
      }
      catch (Exception e) {
         e.printStackTrace();
         log.severe("Login to xmlBlaster failed");
         System.exit(1);
      }
   }

   /**
    * Logout from xmlBlaster
    */
   public void logout() {
      if (con == null) return;
      log.info("Logout ...");
      con.disconnect(null);
   }

   /**
    * Send the SQL message.
    */
   private void query() {
      XmlDbMessageWrapper wrap = new XmlDbMessageWrapper(glob,
         glob.getProperty().get("user", "postgres"),
         glob.getProperty().get("pass", ""),
         glob.getProperty().get("url",  "jdbc:postgresql://24.3.47.214/postgres"));

      boolean confirm = glob.getProperty().get("confirm", true);
      String type = glob.getProperty().get("type", "query");
      int limit = glob.getProperty().get("limit", 50);
      String queryStr = glob.getProperty().get("query", "select * from intrauser");

      wrap.init(type, limit, confirm, queryStr);

      try {
         con.publish(wrap.toMessage());
         log.info("Published query ...");
         if (log.isLoggable(Level.FINEST)) log.finest(wrap.toXml());
      }
      catch (Exception e) { log.severe(e.getMessage()); }

      if (!queryStr.equalsIgnoreCase("query") && !confirm) {
         logout();
         log.info("Done, no waiting on confirmation");
         System.exit(0);
      }
   }

   /**
    * @param args Command line
    */
   public static void main(String args[]) {
      XmlDBClient client = new XmlDBClient(new Global(args));
   }
}
