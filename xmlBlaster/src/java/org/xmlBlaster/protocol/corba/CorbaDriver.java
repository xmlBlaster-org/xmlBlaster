/*------------------------------------------------------------------------------
Name:      CorbaDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   CorbaDriver class to invoke the xmlBlaster server using CORBA.
Version:   $Id: CorbaDriver.java,v 1.18 2001/08/31 15:30:48 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.corba;

import org.xmlBlaster.util.Log;
import org.jutils.io.FileUtil;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.engine.*;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.protocol.corba.authenticateIdl.AuthServerPOATie;
import org.xmlBlaster.protocol.corba.AuthServerImpl;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.authentication.HttpIORServer;
import java.io.PrintWriter;
import java.io.FileOutputStream;
import java.io.File;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NameComponent;


/**
 * CorbaDriver class to invoke the xmlBlaster server using CORBA.
 */
public class CorbaDriver implements I_Driver
{
   private static final String ME = "CorbaDriver";
   private static org.omg.CORBA.ORB orb = null;
   private HttpIORServer httpIORServer = null;  // xmlBlaster publishes his AuthServer IOR
   private NamingContextExt nc = null;
   private NameComponent [] name = null;
   private String iorFile = null;
   /** XmlBlaster internal http listen port is 7609, to access IOR for bootstrapping */
   public static final int DEFAULT_HTTP_PORT = 7609;
   /** The singleton handle for this xmlBlaster server */
   private AuthServerImpl authServer = null;
   /** The singleton handle for this xmlBlaster server */
   private Authenticate authenticate = null;
   /** The singleton handle for this xmlBlaster server */
   private I_XmlBlaster xmlBlasterImpl = null;
   private org.omg.PortableServer.POA rootPOA = null;
   private org.omg.CORBA.Object authRef = null;


   /** Get a human readable name of this driver */
   public String getName()
   {
      return ME;
   }


   /**
    * Start xmlBlaster CORBA access.
    * @param args The command line parameters
    */
   public void init(String args[], Authenticate authenticate, I_XmlBlaster xmlBlasterImpl) throws XmlBlasterException
   {
      this.authenticate = authenticate;
      this.xmlBlasterImpl = xmlBlasterImpl;

      /*
      # orb.properties file for JacORB, copy to JAVA_HOME/lib
      #
      # Switches off the default CORBA in JDK (which is outdated),
      # and replaces it with JacORB implementation
      #
      # JDK 1.2 checks following places to replace the builtin Orb:
      #  1. check in Applet parameter or application string array, if any
      #  2. check in properties parameter, if any
      #  3. check in the System properties
      #  4. check in the orb.properties file located in the java.home/lib directory
      #  5. fall back on a hardcoded default behavior (use the Java IDL implementation)
      */
      // If not set, force to use JacORB instead of JDK internal ORB (which is outdated)
      if (System.getProperty("org.omg.CORBA.ORBClass") == null) {
         System.setProperty("org.omg.CORBA.ORBClass", XmlBlasterProperty.get("org.omg.CORBA.ORBClass", "org.jacorb.orb.ORB"));
         System.setProperty("org.omg.CORBA.ORBSingletonClass", XmlBlasterProperty.get("org.omg.CORBA.ORBSingletonClass", "org.jacorb.orb.ORBSingleton"));
      }

      orb = org.omg.CORBA.ORB.init(args, null);
      try {
         rootPOA = org.omg.PortableServer.POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
         rootPOA.the_POAManager().activate();

         authServer = new AuthServerImpl(orb, authenticate, xmlBlasterImpl);

         // USING TIE:
         org.omg.PortableServer.Servant authServant = new AuthServerPOATie(authServer);
         authRef = ((AuthServerPOATie)(authServant))._this(orb);
         // NOT TIE:
         // org.omg.PortableServer.Servant authServant = new AuthServerImpl(orb);
         // authRef = rootPOA.servant_to_reference(authServant);


         // There are three variants how xmlBlaster publishes its AuthServer IOR (object reference)

         // 1) Write IOR to given file
         String iorFile = XmlBlasterProperty.get("iorFile", (String)null);
         if(iorFile != null) {
            PrintWriter ps = new PrintWriter(new FileOutputStream(new File(iorFile)));
            ps.println(orb.object_to_string(authRef));
            ps.close();
            Log.info(ME, "Published AuthServer IOR to file " + iorFile);
         }

         // 2) Publish IOR on given port (switch off this feature with '-iorPort 0'
         int iorPort = XmlBlasterProperty.get("iorPort", DEFAULT_HTTP_PORT); // default xmlBlaster IOR publishing port is 7609 (HTTP_PORT)
         if (iorPort > 0) {
            String ip_addr = getLocalIP();
            httpIORServer = new HttpIORServer(ip_addr, iorPort, orb.object_to_string(authRef));
            Log.info(ME, "Published AuthServer IOR on " + ip_addr + ":" + iorPort);
         }

         // 3) Publish IOR to a naming service
         boolean useNameService = XmlBlasterProperty.get("ns", true);  // default is to publish myself to the naming service
         if (useNameService) {

            /*
            // We check if a name server is running, if not we just create and start one:
            Class nameServer = Class.forName("jacorb.naming.NameServer");
            if (nameServer != null) {
               try {
                  nc = getNamingService();
               }
               catch (XmlBlasterException e) {
                  class MyNameServer extends Thread {
                     public void run() {
                        Thread.currentThread().setName("XmlBlaster CorbaDriver NameServerThread");
                        String[] aa = new String[1];
                        // !!! where do we get the document root from (see jacorb.NameServerURL in jacorb.properties)?
                        aa[0] = "/home/ruff/xmlBlaster/demo/html/NS_Ref";
                        // !!! using reflection in future to avoid JacORB dependency (nameServer. Method main):
                        jacorb.naming.NameServer.main(aa);
                        Log.info(ME, "Created Name server");
                     }
                  }
                  MyNameServer thr = new MyNameServer();
                  thr.start();
                  org.jutils.runtime.Sleeper.sleep(500);
                  Log.info(ME, "Started CORBA naming service");
               }
            }
            */

            // Register xmlBlaster with a name server:
            try {
               nc = getNamingService();
               name = new NameComponent[1];
               name[0] = new NameComponent(); // name[0] = new NameComponent("AuthenticationService", "service");
               name[0].id = "xmlBlaster-Authenticate";
               name[0].kind = "MOM";

               nc.bind(name, authRef);
               Log.info(ME, "Published AuthServer IOR to naming service");
            }
            catch (XmlBlasterException e) {
               Log.warn(ME + ".NoNameService", e.reason);
               nc = null;
               if (iorPort > 0) {
                  Log.info(ME, "You don't need the naming service, i'll switch to builtin http IOR download");
               }
               else if (iorFile != null) {
                  Log.info(ME, "You don't need the naming service, i'll switch to iorFile = " + iorFile);
               }
               else {
                  usage();
                  Log.panic(ME, "You switched off the internal http server and you didn't specify a file name for IOR dump! Sorry - good bye.");
               }
            } catch (org.omg.CORBA.COMM_FAILURE e) {
               nc = null;
               if (iorPort > 0) {
                  Log.info(ME, "Can't publish AuthServer to naming service, is your naming service really running?\n" +
                               e.toString() +
                               "\nYou don't need the naming service, i'll switch to builtin http IOR download");
               }
               else if (iorFile != null) {
                  Log.info(ME, "Can't publish AuthServer to naming service, is your naming service really running?\n" +
                               e.toString() +
                               "\nYou don't need the naming service, i'll switch to iorFile = " + iorFile);
               }
               else {
                  usage();
                  Log.panic(ME, "Can't publish AuthServer to naming service, is your naming service really running?\n" +
                               e.toString() +
                               "\nYou switched off the internal http server and you didn't specify a file name for IOR dump! Sorry - good bye.");
               }
            }
         }
      }
      catch (org.omg.CORBA.COMM_FAILURE e) {
         throw new XmlBlasterException("InitCorbaFailed", "Could not initialize CORBA, do you use the SUN-JDK delivered ORB instead of JacORB or ORBaccus? Try 'jaco org.xmlBlaster.Main' and read instructions in xmlBlaster/bin/jaco : " + e.toString());
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw new XmlBlasterException("InitCorbaFailed", "Could not initialize CORBA: " + e.toString());
      }
      // orbacus needs this
      if (orb.work_pending()) orb.perform_work();
   }


   /**
    *  Instructs the ORB to shut down, which causes all object adapters to shut down. 
    * <p />
    * JacORB behavior:<br />
    * The POA is not "connected" to the ORB in any particular way
    * other than that is exists. You can call destroy() on a POA
    * to make it disappear. Calling shutdown() on the ORB will 
    * implicitly destroy all POAs.
    * <p />
    * Ports are not linked to POAs in JacORB. Rather, there is a single
    * master port in any server-side ORB which gets created when the
    * root poa is retrieved for the first time. The server process
    * accepts incoming connections on this port and creates new
    * ports for every client process. Because of this connection
    * multiplexing, ports are not released when POAs are destroyed, 
    * but when clients exit, or when server-side timouts occur.
    */
   public void shutdown()
   {
      if (Log.CALL) Log.call(ME, "Shutting down ...");
      boolean wait_for_completion = true;

      try {
         if (httpIORServer != null) httpIORServer.shutdown();
      }
      catch (Throwable e) {
         Log.warn(ME, "Problems during ORB cleanup: " + e.toString());
         e.printStackTrace();
      }

      try {
         if (nc != null) nc.unbind(name);
      }
      catch (Throwable e) {
         Log.warn(ME, "Problems during ORB cleanup: " + e.toString());
         e.printStackTrace();
      }

      try {
         if (iorFile != null) FileUtil.deleteFile(null, iorFile);
      }
      catch (Throwable e) {
         Log.warn(ME, "Problems during ORB cleanup: " + e.toString());
      }

      if (rootPOA != null && authRef != null) {
         try {
            Log.trace(ME, "Deactivate POA ...");
            authRef._release();
            // poa.deactivate_object(poa.servant_to_id(authRef));
            rootPOA.deactivate_object(rootPOA.reference_to_id(authRef));
         } catch(Exception e) { Log.warn(ME, "POA deactivate authentication servant failed"); }
      }

      if (rootPOA != null) {
         try {
            Log.trace(ME, "Deactivate POA Manager ...");
            rootPOA.the_POAManager().deactivate(false, true);
         } catch(Exception e) { Log.warn(ME, "POA deactivate failed"); }
         rootPOA = null;
      }

      authRef = null;

      orb.shutdown(wait_for_completion);

      Log.info(ME, "POA and ORB are down, CORBA resources released.");
   }


   /**
    * Locate the CORBA Naming Service.
    * <p />
    * The found naming service is cached, for better performance in subsequent calls
    * @return NamingContextExt, reference on name service<br />
    *         Note that this reference may be invalid, because the naming service is not running any more
    * @exception XmlBlasterException
    *                    CORBA error handling if no naming service is found
    */
   private NamingContextExt getNamingService() throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "getNamingService() ...");
      if (nc != null)
         return nc;

      NamingContextExt nameService = null;
      try {
         // Get a reference to the Name Service, CORBA compliant:
         org.omg.CORBA.Object nameServiceObj = orb.resolve_initial_references("NameService");
         if (nameServiceObj == null) {
            //Log.warn(ME + ".NoNameService", "Can't access naming service, is there any running?");
            throw new XmlBlasterException(ME + ".NoNameService", "Can't access naming service, is there any running?");
         }
         if (Log.TRACE) Log.trace(ME, "Successfully accessed initial orb references for naming service (IOR)");

         nameService = org.omg.CosNaming.NamingContextExtHelper.narrow(nameServiceObj);
         if (nameService == null) {
            Log.error(ME + ".NoNameService", "Can't access naming service == null");
            throw new XmlBlasterException(ME + ".NoNameService", "Can't access naming service (narrow problem)");
         }
         if (Log.TRACE) Log.trace(ME, "Successfully narrowed handle for naming service");

         return nameService; // Note: the naming service IOR is successfully evaluated (from a IOR),
                             // but it is not sure that the naming service is really running
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Exception e) {
         if (Log.TRACE) Log.trace(ME + ".NoNameService", e.toString());
         throw new XmlBlasterException(ME + ".NoNameService", "No CORBA naming service found - read docu at <http://jacorb.inf.fu-berlin.de> if you want one.");
      }
   }


   /**
    * @return Access to our orb handle
    */
   public static org.omg.CORBA.ORB getOrb()
   {
      if (orb != null)
         return orb;
      Log.warn(ME, "orb was not initialized");
      return org.omg.CORBA.ORB.init(new String[0], null);
   }


   /**
    * Converts the internal CORBA message unit to the internal representation.
    */
   public static final org.xmlBlaster.engine.helper.MessageUnit convert(org.xmlBlaster.protocol.corba.serverIdl.MessageUnit mu)
   {
      return new org.xmlBlaster.engine.helper.MessageUnit(mu.xmlKey, mu.content, mu.qos);
   }


   /**
    * Converts the internal CORBA message unit array to the internal representation.
    */
   public static final org.xmlBlaster.engine.helper.MessageUnit[] convert(org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] msgUnitArr)
   {
      // convert Corba to internal ...
      org.xmlBlaster.engine.helper.MessageUnit[] internalUnitArr = new org.xmlBlaster.engine.helper.MessageUnit[msgUnitArr.length];
      for (int ii=0; ii<msgUnitArr.length; ii++) {
         internalUnitArr[ii] = CorbaDriver.convert(msgUnitArr[ii]);
      }
      return internalUnitArr;
   }


   /**
    * Converts the internal MessageUnit to the CORBA message unit.
    */
   public static final org.xmlBlaster.protocol.corba.serverIdl.MessageUnit convert(org.xmlBlaster.engine.helper.MessageUnit mu)
   {
      return new org.xmlBlaster.protocol.corba.serverIdl.MessageUnit(mu.xmlKey, mu.content, mu.qos);
   }


   /**
    * Converts the internal MessageUnit array to the CORBA message unit array.
    */
   public static final org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] convert(org.xmlBlaster.engine.helper.MessageUnit[] msgUnitArr)
   {
      // convert internal to Corba ...
      org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] corbaUnitArr = new org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[msgUnitArr.length];
      for (int ii=0; ii<msgUnitArr.length; ii++) {
         corbaUnitArr[ii] = CorbaDriver.convert(msgUnitArr[ii]);
      }
      return corbaUnitArr;
   }


   /**
    * The IP address where the HTTP server publishes the IOR.
    * <p />
    * You can specify the local IP address with e.g. -iorHost 192.168.10.1
    * on command line, useful for multi-homed hosts.
    *
    * @return The local IP address, defaults to '127.0.0.1' if not known.
    */
   public String getLocalIP()
   {
      String ip_addr = XmlBlasterProperty.get("iorHost", (String)null);
      if (ip_addr == null) {
         try {
            ip_addr = java.net.InetAddress.getLocalHost().getHostAddress(); // e.g. "204.120.1.12"
         } catch (java.net.UnknownHostException e) {
            Log.warn(ME, "Can't determine local IP address, try e.g. '-iorHost 192.168.10.1' on command line: " + e.toString());
         }
         if (ip_addr == null) ip_addr = "127.0.0.1";
      }
      return ip_addr;
   }


   /**
    * Command line usage.
    */
   public String usage()
   {
      String text = "\n";
      text += "CorbaDriver options:\n";
      text += "   -iorFile            Specify a file where to dump the IOR of the AuthServer (for client access).\n";
      text += "   -iorHost            IP address where the builtin http server publishes its AuthServer IOR (useful for multihomed hosts).\n";
      text += "   -iorPort            Port number where the builtin http server publishes its AuthServer IOR.\n";
      text += "                       Default is port "+DEFAULT_HTTP_PORT+", the port 0 switches this feature off.\n";
      text += "   -ns false           Don't publish the IOR to a naming service.\n";
      text += "                       Default is to publish the IOR to a naming service.\n";
      text += "   java -DOAIAddr=<ip> For JacORB only, allows to set the corba server IP address\n";
      text += "                       for multi-homed hosts\n";
      text += "\n";
      return text;
   }
}
