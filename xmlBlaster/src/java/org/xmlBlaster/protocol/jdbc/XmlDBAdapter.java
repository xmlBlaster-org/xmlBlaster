/*--- formatted by Jindent 2.1, (www.c-lab.de/~jindent) ---*/

/*
 * ------------------------------------------------------------------------------Name:      XmlDBAdapter.java
 * Project:   xmlBlaster.org
 * Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 * Comment:   Main class for xml database adapter
 * Version:   $Id: XmlDBAdapter.java,v 1.1 2000/02/22 04:23:23 jsrbirch Exp $
 * ------------------------------------------------------------------------------
 */

package org.xmlBlaster.protocol.jdbc;

import org.xmlBlaster.util.*;
import org.xmlBlaster.util.pool.jdbc.*;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.corba.clientIdl.*;
import org.xmlBlaster.client.*;
import org.omg.CosNaming.*;
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
public class XmlDBAdapter implements I_Callback {

   private Server          xmlBlaster = null;
   private static String   ME = "XmlDBAdapter";
   private static String   passwd = "some";
   private static String   qos = "";
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

      initDrivers();
      initBlaster();
      checkForKeyboardInput();
   }


   /**
    * CallBack of xmlBlaster
    */
   public void update(String login, UpdateKey key, byte[] content, 
                      UpdateQoS updateQos) {

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
         xmlBlaster = corbaConnection.login(ME, passwd, qos, this);

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

            Class.forName(driver);
         } 
         catch (Exception e) {
            Log.warning(ME, "Couldn't initialize dirver =>" + driver);
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

