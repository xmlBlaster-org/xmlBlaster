package javaclients.jdbc;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.GetKeyWrapper;
import org.xmlBlaster.client.GetQosWrapper;
import org.xmlBlaster.client.XmlDbMessageWrapper;
import org.xmlBlaster.engine.helper.MessageUnit;


/**
 * Example code how to access the xmlBlaster JDBC service
 * synchronous with the get() method.
 *
 * get() requests on key oid="__sys__jdbc" are handled by xmlBlaster (see RequestBroker.java)
 * directly and the result set is delivered as the return value of the get() request.
 *
 * See README for usage
 *
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.service.rdbms.html">Requirement engine.service.rdbms</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.service.rdbms.jdbcpool.html">Requirement engine.service.rdbms.jdbcpool</a>
 */
public class XmlDBClientSync
{
   private static String   ME = "XmlDBClientSync";
   private final Global glob;
   private XmlBlasterConnection corbaConnection = null;

   /**
    * Constructor declaration
    */
   public XmlDBClientSync(Global glob) {
      this.glob = glob;
      initBlaster();
      query();
      logout();
   }


   /**
    * Find xmlBlaster server and login.
    */
   public void initBlaster() {
      try {
         corbaConnection = new XmlBlasterConnection(glob);
         corbaConnection.connect(null, null);
         Log.info(ME, "Connected to xmlBlaster");
      }
      catch (Exception e) {
         e.printStackTrace();
         Log.exit(ME, "Login to xmlBlaster failed");
      }
   }


   /**
    * Logout from xmlBlaster.
    */
   public void logout() {
      if (corbaConnection == null) return;
      Log.info(ME, "Logout ...");
      corbaConnection.disconnect(null);
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
         GetKeyWrapper key = new GetKeyWrapper("__sys__jdbc");
         key.wrap(wrap.toXml());
         GetQosWrapper qos = new GetQosWrapper();
         // get() blocks until the query is finished ...
         MessageUnit[] msgUnitArr = corbaConnection.get(key.toXml(), qos.toXml());
         if (msgUnitArr.length > 0)
            Log.plain(new String(msgUnitArr[0].content));
         else
            Log.info(ME, "No results for your query");
      }
      catch (Exception e) { Log.error(ME, "Query failed: " + e.toString()); }
   }

   /**
    * java javaclients.jdbc.XmlDBClientSync \
    *    -url "jdbc:postgresql://24.3.47.214/postgres" \
    *    -user postgres \
    *    -pass secret \
    *    -query "select * from foo_table" \
    *    -limit 50
    * @param args Command line
    */
   public static void main(String args[]) {
      new XmlDBClientSync(new Global(args));
   }
}
