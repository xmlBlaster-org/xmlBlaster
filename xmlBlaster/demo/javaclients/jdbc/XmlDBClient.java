/*--- formatted by Jindent 2.1, (www.c-lab.de/~jindent) ---*/

package javaclients.jdbc;

import org.jutils.log.Log;
import org.jutils.init.Args;
import org.jutils.JUtilsException;

import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.protocol.jdbc.*;
import org.xmlBlaster.client.*;
import org.xmlBlaster.client.UpdateQoS;

import java.io.*;

/**
 * Class declaration
 *
 *
 * @author
 * @version %I%, %G%
 */
public class XmlDBClient implements I_Callback
 {

   private static String   ME = "XmlDBClient";
   private CorbaConnection corbaConnection = null;
   private String          results;
   private boolean         done = false;

   /**
    * Constructor declaration
    */
   public XmlDBClient(String args[]) throws JUtilsException
    {
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      initBlaster(args);
      query(args);
      waitOnResults();
   }


   /**
    */
   private void waitOnResults()
    {
      while (!done)  {
         try {
            Thread.sleep(500);
         }
         catch (InterruptedException e) {}
         Log.plain("Waiting...");
      }
      Log.plain(results);
      logout();
   }


   /**
    * CallBack of xmlBlaster
    */
   public void update(String cust, UpdateKey key, byte[] content, UpdateQoS updateQos)
    {
      results = new String(content);
      done = true;
   }


   /**
    * Find xmlBlaster server and login.
    */
   public void initBlaster(String[] args)
   {
      try {
         corbaConnection = new CorbaConnection(args); // find ORB
         String loginName = Args.getArg(args, "-name", ME);
         String passwd = Args.getArg(args, "-passwd", "secret");
         corbaConnection.login(loginName, passwd, null, this);
         Log.info(ME, "Connected to xmlBlaster as '" + loginName + "'");
      }
      catch (Exception e) {
         e.printStackTrace();
         Log.exit(ME, "Login to xmlBlaster failed");
      }
   }


   /**
    * unsubsrcibe and logout from xmlBlaster
    */
   public void logout()
   {
      if (corbaConnection == null) return;
      Log.info(ME, "Logout ...");
      corbaConnection.logout();
   }

   /**
    * @param args Command line
    */
   public static void main(String args[])
   {
      try {
         XmlDBClient client = new XmlDBClient(args);
      } catch (JUtilsException e) {
         Log.panic("DBClient", e.toString());
      }
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
         String oid = corbaConnection.publish(wrap.toMessage());
         Log.info(ME, "Published query ...");
         if (Log.DUMP) Log.dump(ME, wrap.toXml());
      }
      catch (Exception e) { Log.error(ME, e.getMessage()); }

      if (!queryStr.equalsIgnoreCase("query") && !confirm) {
         logout();
         Log.exit(ME, "Done, no waiting on confirmation");
      }
   }

}
