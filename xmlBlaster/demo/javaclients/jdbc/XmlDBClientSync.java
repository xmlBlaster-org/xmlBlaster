package javaclients.jdbc;

import org.xmlBlaster.util.Log;
import org.jutils.init.Args;
import org.jutils.JUtilsException;

import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.GetKeyWrapper;
import org.xmlBlaster.client.GetQosWrapper;
import org.xmlBlaster.client.XmlDbMessageWrapper;
import org.xmlBlaster.engine.helper.MessageUnit;


/**
 * Example code how to access the xmlBlaster JDBC service
 * synchronous with the get() method.
 *
 * @see README for usage
 */
public class XmlDBClientSync
{
   private static String   ME = "XmlDBClientSync";
   private XmlBlasterConnection corbaConnection = null;

   /**
    * Constructor declaration
    */
   public XmlDBClientSync(String args[]) throws JUtilsException
    {
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      initBlaster(args);
      query(args);
      logout();
   }


   /**
    * Find xmlBlaster server and login.
    */
   public void initBlaster(String[] args)
   {
      try {
         corbaConnection = new XmlBlasterConnection(args); // find ORB
         String loginName = Args.getArg(args, "-name", ME);
         String passwd = Args.getArg(args, "-passwd", "secret");
         corbaConnection.login(loginName, passwd, null);
         Log.info(ME, "Connected to xmlBlaster as '" + loginName + "'");
      }
      catch (Exception e) {
         e.printStackTrace();
         Log.exit(ME, "Login to xmlBlaster failed");
      }
   }


   /**
    * Logout from xmlBlaster.
    */
   public void logout()
   {
      if (corbaConnection == null) return;
      Log.info(ME, "Logout ...");
      corbaConnection.logout();
   }

   /**
    * Send the SQL message.
    */
   private void query(String[] args) throws JUtilsException
   {
      XmlDbMessageWrapper wrap = new XmlDbMessageWrapper(
         Args.getArg(args, "-user", "postgres"),
         Args.getArg(args, "-pass", ""),
         Args.getArg(args, "-url",  "jdbc:postgresql://24.3.47.214/postgres"));

      boolean confirm = Args.getArg(args, "-confirm", true);
      String type = Args.getArg(args, "-type", "query");
      int limit = Args.getArg(args, "-limit", 50);
      String queryStr = Args.getArg(args, "-query", "select * from intrauser");

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
   public static void main(String args[])
   {
      try {
         XmlDBClientSync client = new XmlDBClientSync(args);
      } catch (JUtilsException e) {
         Log.panic("DBClient", e.toString());
      }
   }
}
