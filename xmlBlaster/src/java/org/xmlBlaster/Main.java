/*------------------------------------------------------------------------------
Name:      Main.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main class to invoke the xmlBlaster server
Version:   $Id: Main.java,v 1.11 1999/12/09 00:11:04 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster;

import org.xmlBlaster.util.*;
import org.xmlBlaster.engine.*;
import org.xmlBlaster.serverIdl.*;
import org.xmlBlaster.authenticateIdl.*;
import org.xmlBlaster.authentication.HttpIORServer;
import java.io.*;
import org.omg.CosNaming.*;


/**
 * Main class to invoke the xmlBlaster server
 */
public class Main
{
   final private String ME = "Main";
   static public org.omg.CORBA.ORB orb;
   private HttpIORServer httpIORServer = null;  // if xmlBlaster publish his AuthServer IOR

   public Main( String[] args )
   {
      orb = org.omg.CORBA.ORB.init(args, null);
      try {
         org.omg.PortableServer.POA rootPOA =
         org.omg.PortableServer.POAHelper.narrow(orb.resolve_initial_references("RootPOA"));

         // USING TIE:
         org.omg.PortableServer.Servant authServant = new AuthServerPOATie(new AuthServerImpl(orb));
         // NOT TIE:
         // org.omg.PortableServer.Servant authServant = new AuthServerImpl(orb);

         // org.omg.CORBA.Object authRef = rootPOA.servant_to_reference(authServant);
         // alternatively you can use (more performance)
         org.omg.CORBA.Object authRef = new AuthServerPOATie(new AuthServerImpl(orb))._this(orb);


         // There are three variants how xmlBlaster publishes its AuthServer IOR (object reference)

         // 1) Write IOR to given file
         String iorFile = Args.getArg(args, "-iorFile", null);
         if(iorFile != null) {
            PrintWriter ps = new PrintWriter(new FileOutputStream(new File(iorFile)));
            ps.println(orb.object_to_string(authRef));
            ps.close();
            Log.info(ME, "Published AuthServer IOR to file " + iorFile);
         }

         // 2) Publish IOR on given port (switch off this feature with '-iorPort -1'
         int iorPort = Args.getArg(args, "-iorPort", 7609); // default xmlBlaster IOR publishing port is 7609 (HTTP_PORT)
         if (iorPort > 0) {
            HttpIORServer httpIORServer = new HttpIORServer(iorPort, orb.object_to_string(authRef));
            Log.info(ME, "Published AuthServer IOR on port " + iorPort);
         }

         // 3) Publish IOR to a naming service
         try {
            NamingContext nc = getNamingService();
            NameComponent [] name = new NameComponent[1];
            name[0] = new NameComponent(); // name[0] = new NameComponent("AuthenticationService", "service");
            name[0].id = "xmlBlaster-Authenticate";
            name[0].kind = "MOM";

            nc.bind(name, authRef);
            Log.info(ME, "Published AuthServer IOR to naming service");
         }
         catch (XmlBlasterException e) {
            Log.info(ME, "AuthServer IOR is not published to naming service");
         }
      /*
      } catch (XmlBlasterException e) {
         e.printStackTrace();
         Log.panic(ME, "[" + e.id + "] " + e.reason);
      */
      } catch (Exception e) {
         e.printStackTrace();
         Log.panic(ME, e.toString());
      }

      orb.run();
   }


   /**
    * Locate the CORBA Name Service.
    * <p />
    * The found name service is cached, for better performance in subsequent calls
    * @return NamingContext, reference on name service
    * @exception XmlBlasterException
    *                    CORBA error handling if no naming service is found
    */
   private NamingContext getNamingService() throws XmlBlasterException
   {
      NamingContext nameService = null;

      if (Log.CALLS) Log.calls(ME, "getNamingService() ...");
      if (nameService != null)
         return nameService;

      try {
         // Get a reference to the Name Service, CORBA compliant:
         org.omg.CORBA.Object nameServiceObj = orb.resolve_initial_references("NameService");
         if (nameServiceObj == null) {
            Log.warning(ME + ".NoNameService", "Can't access naming service");
            throw new XmlBlasterException(ME + ".NoNameService", "Can't access naming service, is there any running?");
         }
         if (Log.TRACE) Log.trace(ME, "Successfully accessed initial orb references");

         nameService = org.omg.CosNaming.NamingContextHelper.narrow(nameServiceObj);
         if (nameService == null) {
            Log.error(ME + ".NoNameService", "Can't access naming service");
            throw new XmlBlasterException(ME + ".NoNameService", "Can't access naming service (narrow problem)");
         }
         if (Log.TRACE) Log.trace(ME, "Successfully found a naming service");
         return nameService;
      }
      catch (Exception e) {
         Log.warning(ME + ".NoNameService", "Can't access naming service");
         throw new XmlBlasterException(ME + ".NoNameService", e.toString());
      }
   }


   /**
    *  Invoke: jaco org.xmlBlaster.Main
    */
   public static void main( String[] args )
   {
      new Main(args);
   }
}
