/*------------------------------------------------------------------------------
Name:      CorbaDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   CorbaDriver class to invoke the xmlBlaster server using CORBA.
Version:   $Id: CorbaDriver.java,v 1.8 2000/09/21 08:53:58 ruff Exp $
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
import java.io.*;
import org.omg.CosNaming.*;


/**
 * CorbaDriver class to invoke the xmlBlaster server using CORBA.
 */
public class CorbaDriver implements I_Driver
{
   private static final String ME = "CorbaDriver";
   private static org.omg.CORBA.ORB orb = null;
   private HttpIORServer httpIORServer = null;  // xmlBlaster publishes his AuthServer IOR
   private NamingContext nc = null;
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
            httpIORServer = new HttpIORServer(iorPort, orb.object_to_string(authRef));
            Log.info(ME, "Published AuthServer IOR on port " + iorPort);
         }

         // 3) Publish IOR to a naming service
         boolean useNameService = XmlBlasterProperty.get("ns", true);  // default is to publish myself to the naming service
         if (useNameService) {
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
                               "You don't need the naming service, i'll switch to builtin http IOR download");
               }
               else if (iorFile != null) {
                  Log.info(ME, "Can't publish AuthServer to naming service, is your naming service really running?\n" +
                               e.toString() +
                               "You don't need the naming service, i'll switch to iorFile = " + iorFile);
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
    */
   public void shutdown()
   {
      if (Log.CALL) Log.call(ME, "Shutting down ...");
      boolean wait_for_completion = true;

      try {
         if (httpIORServer != null) httpIORServer.shutdown();
         if (nc != null) nc.unbind(name);
         if (iorFile != null) FileUtil.deleteFile(null, iorFile);
      }
      catch (Throwable e) {
         Log.warn(ME, "Problems during ORB cleanup: " + e.toString());
         e.printStackTrace();
      }

      if (rootPOA != null && authRef != null) {
         try {
            Log.trace(ME, "Deactivate POA ...");
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
    * @return NamingContext, reference on name service<br />
    *         Note that this reference may be invalid, because the naming service is not running any more
    * @exception XmlBlasterException
    *                    CORBA error handling if no naming service is found
    */
   private NamingContext getNamingService() throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "getNamingService() ...");
      if (nc != null)
         return nc;

      NamingContext nameService = null;
      try {
         // Get a reference to the Name Service, CORBA compliant:
         org.omg.CORBA.Object nameServiceObj = orb.resolve_initial_references("NameService");
         if (nameServiceObj == null) {
            Log.warn(ME + ".NoNameService", "Can't access naming service, is there any running?");
            throw new XmlBlasterException(ME + ".NoNameService", "Can't access naming service, is there any running?");
         }
         if (Log.TRACE) Log.trace(ME, "Successfully accessed initial orb references for naming service (IOR)");

         nameService = org.omg.CosNaming.NamingContextHelper.narrow(nameServiceObj);
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
         Log.warn(ME + ".NoNameService", "Can't access naming service: " + e.toString());
         throw new XmlBlasterException(ME + ".NoNameService", e.toString());
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
    * Command line usage.
    */
   public String usage()
   {
      String text = "\n";
      text += "CorbaDriver options:\n";
      text += "   -iorFile            Specify a file where to dump the IOR of the AuthServer (for client access).\n";
      text += "   -iorPort            Specify a port number where the builtin http server publishes its AuthServer IOR.\n";
      text += "                       Default is port "+DEFAULT_HTTP_PORT+", the port 0 switches this feature off.\n";
      text += "   -ns false           Don't publish the IOR to a naming service.\n";
      text += "                       Default is to publish the IOR to a naming service.\n";
      text += "\n";
      return text;
   }
}
