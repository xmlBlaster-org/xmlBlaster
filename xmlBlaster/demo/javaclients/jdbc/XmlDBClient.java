/*--- formatted by Jindent 2.1, (www.c-lab.de/~jindent) ---*/

package javaclients.jdbc;

import org.xmlBlaster.util.*;
import org.xmlBlaster.protocol.jdbc.*;
import org.xmlBlaster.util.pool.jdbc.*;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.corba.clientIdl.*;
import org.xmlBlaster.client.*;
import org.omg.CosNaming.*;
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

   private Server          xmlBlaster = null;
   private static String   ME = "XmlDBClient";
   private static String   adapter = "XmlDBAdapter";
   private static String   passwd = "some";
   private String          publishOid = "XmlDBClient";
   private String          xmlKey = "";
   private CorbaConnection corbaConnection = null;
   private static String   qos = "" + "<qos>" 
                                 + " <destination queryType='EXACT'>" 
                                 + adapter + " </destination>" + "</qos>";

   private String          args[];
   private String          results;
   private boolean         done = false;

   /**
    * Constructor declaration
    * 
    * 
    * @param args
    * 
    * @see
    */
   public XmlDBClient(String args[])
    {
      this.args = args;

      initBlaster();
      query();
      waitOnResults();
   }

   /**
    * Method declaration
    * 
    * 
    * @see
    */
   private void waitOnResults()
    {
      while (!done)
       {
         try
          {
            Thread.sleep(500);
         } 
         catch (InterruptedException e) {}

         System.out.println("Waiting...");
      } 

      System.out.println(results);
      logout();
   } 


   /**
    * CallBack of xmlBlaster
    */
   public void update(String cust, UpdateKey key, byte[] content, 
                      UpdateQoS updateQos)
    {
      results = new String(content);
      done = true;

   } 

   /**
    * find xmlBlaster server, login and subscribe
    */
   public void initBlaster()
    {
      try
       {

         // ----------- Find orb ----------------------------------
         corbaConnection = new CorbaConnection(args);

         // ---------- Building a Callback server ----------------------
         // Getting the default POA implementation "RootPOA"
         org.omg.PortableServer.POA poa = 
            org.omg.PortableServer.POAHelper.narrow(corbaConnection.getOrb().resolve_initial_references("RootPOA"));

         // ----------- Login to xmlBlaster -----------------------
         xmlBlaster = corbaConnection.login(ME, passwd, qos, this);

      } 
      catch (Exception e)
       {
         e.printStackTrace();
      } 
   } 

   /**
    * unsubsrcibe and logout from xmlBlaster
    */
   public void logout()
    {
      if (xmlBlaster == null)
       {
         return;

         // ----------- Logout --------------------------------------
      } 

      Log.trace(ME, "Logout ...");
      corbaConnection.logout(xmlBlaster);
   } 

   /**
    * Method declaration
    * 
    * 
    * @param args
    * 
    * @see
    */
   public static void main(String args[])
    {
      XmlDBClient client = new XmlDBClient(args);
   } 

   /**
    * Method declaration
    * 
    * 
    * @see
    */
   private void query()
    {
      String   user = Args.getArg(args, "-user", "postgres");
      String   pass = Args.getArg(args, "-pass", "");
      String   type = Args.getArg(args, "-type", "query");
      String   limit = Args.getArg(args, "-limit", "50");
      String   confirm = Args.getArg(args, "-confirm", "true");
      String   queryStr = Args.getArg(args, "-query", 
                                      "select * from intrauser");
      String   url = Args.getArg(args, "-url", 
                                 "jdbc:postgresql://24.3.47.214/postgres");
      String   query = "" + "<database:adapter>" + " <database:url>" + url 
                       + "</database:url>" + " <database:username>" + user 
                       + "</database:username>" + " <database:password>" 
                       + pass + "</database:password>" 
                       + " <database:interaction type='" + type + "'/>" 
                       + " <database:command>" + queryStr 
                       + "</database:command>" 
                       + " <database:connectionlifespan ttl='1'/>" 
                       + " <database:rowlimit max='" + limit + "'/>" 
                       + " <database:confirmation confirm='" + confirm 
                       + "'/>" + "</database:adapter>";

      String   xmlKey = "" + "<?xml version='1.0' encoding='ISO-8859-1' ?>" 
                        + "<key oid='XmlDBClient' contentMime='text/plain'>" 
                        + "</key>";

      try
       {
         MessageUnit mu = new MessageUnit(xmlKey, query.getBytes());
         String      oid = xmlBlaster.publish(mu, qos);

         System.out.println("Published query...");
         System.out.println("qos =>" + qos);
      } 
      catch (Exception e) {}
   } 

}







/*--- formatting done in "My Own Convention" style on 02-21-2000 ---*/

