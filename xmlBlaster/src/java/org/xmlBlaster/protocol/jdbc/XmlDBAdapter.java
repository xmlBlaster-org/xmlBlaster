/*
 * Name:      XmlDBAdapter.java
 * Project:   xmlBlaster.org
 * Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 * Comment:   Main class for xml database adapter
 * Version:   $Id: XmlDBAdapter.java,v 1.11 2000/07/02 18:06:47 ruff Exp $
 * ------------------------------------------------------------------------------
 */
package org.xmlBlaster.protocol.jdbc;

import org.jutils.log.Log;

import org.xmlBlaster.util.pool.jdbc.*;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.client.*;
import org.xmlBlaster.client.UpdateQoS;

import java.io.*;
import java.util.*;

/**
 * Class declaration
 *
 *
 * @author
 * @version %I%, %G%
 */
public class XmlDBAdapter implements I_Callback, I_Publish {

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

      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }

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
      XmlDBAdapterWorker   worker = new XmlDBAdapterWorker(cust, content, this);

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
         corbaConnection.login("__sys__jdbc", passwd, null, this);

      }
      catch (Exception e) {
         e.printStackTrace();
      }
   }

   public String publish(MessageUnit msgUnit) throws XmlBlasterException
   {
      return corbaConnection.publish(msgUnit);
   }

   /**
    * unsubsrcibe and logout from xmlBlaster
    */
   public void logout() {
      ConnectionManager.getInstance().release();
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
      String            drivers = XmlBlasterProperty.get("JdbcDriver.drivers", "");
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

