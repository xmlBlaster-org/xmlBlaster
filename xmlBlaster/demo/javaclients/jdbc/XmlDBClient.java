package javaclients.jdbc;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.XmlDbMessageWrapper;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;


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
   private XmlBlasterConnection con = null;
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
         Log.plain("Waiting...");
      }
      Log.plain(results);
      logout();
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey key, byte[] content, UpdateQos updateQos) {
      results = new String(content);
      done = true;
      return "";
   }

   /**
    * Find xmlBlaster server and login.
    */
   public void initBlaster() {
      try {
         con = new XmlBlasterConnection(glob);
         con.connect(null, this);
         Log.info(ME, "Connected to xmlBlaster");
      }
      catch (Exception e) {
         e.printStackTrace();
         Log.exit(ME, "Login to xmlBlaster failed");
      }
   }

   /**
    * Logout from xmlBlaster
    */
   public void logout() {
      if (con == null) return;
      Log.info(ME, "Logout ...");
      con.disconnect(null);
   }

   /**
    * Send the SQL message.
    */
   private void query() {
      XmlDbMessageWrapper wrap = new XmlDbMessageWrapper(
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
         Log.info(ME, "Published query ...");
         if (Log.DUMP) Log.dump(ME, wrap.toXml());
      }
      catch (Exception e) { Log.error(ME, e.getMessage()); }

      if (!queryStr.equalsIgnoreCase("query") && !confirm) {
         logout();
         Log.exit(ME, "Done, no waiting on confirmation");
      }
   }

   /**
    * @param args Command line
    */
   public static void main(String args[]) {
      XmlDBClient client = new XmlDBClient(new Global(args));
   }
}
