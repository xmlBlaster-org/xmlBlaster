/*--- formatted by Jindent 2.1, (www.c-lab.de/~jindent) ---*/

/*
 * ------------------------------------------------------------------------------Name:      XmlDBAdapter.java
 * Project:   xmlBlaster.org
 * Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 * Comment:   Main class for xml database adapter
 * Version:   $Id: XmlDBAdapter.java,v 1.5 2000/06/13 13:04:02 ruff Exp $
 * ------------------------------------------------------------------------------
 */

package org.xmlBlaster.protocol.jdbc;

import org.xmlBlaster.util.*;
import org.xmlBlaster.util.pool.jdbc.*;
import org.xmlBlaster.client.*;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.omg.CosNaming.*;
import java.io.*;
import java.util.*;

/**
 * Class declaration
 *
 *
 * @author
 * @version %I%, %G%
 */
public class XmlDBAdapter implements I_Callback {

   private Server          xmlBlaster = null;
   private static String   ME = "XmlDBAdapter";
   private static String   passwd = "some";
   private String          publishOid = "XmlDBAdapter";
   private String          xmlKey = "";
   private CorbaConnection corbaConnection = null;

   private String          args[];

   /**
    * Constructor declaration
    *
    *
    * @param args
    *
    * @see
    */
   public XmlDBAdapter(String args[]) {
      this.args = args;
      Log.setLogLevel(args); // initialize log level and xmlBlaster.property file

      initDrivers();
      initBlaster();
      checkForKeyboardInput();
   }


   /**
    * CallBack of xmlBlaster
    */
   public void update(String login, UpdateKey key, byte[] content,
                      UpdateQoS updateQos) {
      if (Log.CALLS) Log.calls(ME, "Message '" + key.getUniqueKey() + "' from '" + updateQos.getSender() + "' received");
      String               cust = updateQos.getSender();
      String               qos = updateQos.printOn().toString();
      XmlDBAdapterWorker   worker = new XmlDBAdapterWorker(args, cust,
              content, qos, xmlBlaster);

      worker.start();
   }

   /**
    * find xmlBlaster server, login and subscribe
    */
   public void initBlaster() {
      try {

         // ----------- Find orb ----------------------------------
         corbaConnection = new CorbaConnection(args);

         // ---------- Building a Callback server ----------------------
         // Getting the default POA implementation "RootPOA"
         org.omg.PortableServer.POA poa =
            org.omg.PortableServer.POAHelper.narrow(corbaConnection.getOrb().resolve_initial_references("RootPOA"));

         // ----------- Login to xmlBlaster -----------------------
         xmlBlaster = corbaConnection.login(ME, passwd, null, this);

      }
      catch (Exception e) {
         e.printStackTrace();
      }
   }

   /**
    * unsubsrcibe and logout from xmlBlaster
    */
   public void logout() {
      ConnectionManager.getInstance().release();

      if (xmlBlaster == null) {
         return;

         // ----------- Logout --------------------------------------
      }

      Log.trace(ME, "Logout ...");
      corbaConnection.logout();
   }

   /**
    * Method declaration
    *
    *
    * @param args
    *
    * @see
    */
   public static void main(String args[]) {
      XmlDBAdapter   db = new XmlDBAdapter(args);
   }

   /**
    * Method declaration
    *
    *
    * @see
    */
   private void initDrivers() {
      String            drivers = Property.getProperty("JDBCDrivers", "");
      StringTokenizer   st = new StringTokenizer(drivers, ",");
      int               numDrivers = st.countTokens();

      String            driver = "";

      for (int i = 0; i < numDrivers; i++) {
         try {
            driver = st.nextToken().trim();
            if (Log.TRACE) Log.trace(ME, "Trying JDBC driver Class.forName(´" + driver + "´) ...");
            Class cl = Class.forName(driver);
            java.sql.Driver dr = (java.sql.Driver)cl.newInstance();
            java.sql.DriverManager.registerDriver(dr);
            Log.info(ME, "Jdbc driver '" + driver + "' loaded.");
         }
         catch (Throwable e) {
            Log.warning(ME, "Couldn't initialize driver =>" + driver);
         }
      }
   }

   /**
    * Method declaration
    *
    *
    * @see
    */
   private void checkForKeyboardInput() {
      BufferedReader in =
         new BufferedReader(new InputStreamReader(System.in));

      while (true) {
         try {
            String   line = in.readLine().trim();

            if (line.toLowerCase().equals("q")) {
               logout();
               System.exit(0);
            }
         }
         catch (IOException e) {
            Log.warning(ME, e.toString());
         }
      }
   }

}



/*--- formatting done in "xmlBlaster Convention" style on 02-21-2000 ---*/

