/*------------------------------------------------------------------------------
Name:      CorbaDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   CorbaDriver class to invoke the xmlBlaster server using CORBA.
Version:   $Id: CorbaDriver.java,v 1.63 2003/04/17 13:30:33 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.corba;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.JdkCompatible;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.engine.*;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.protocol.corba.authenticateIdl.AuthServerPOATie;
import org.xmlBlaster.protocol.corba.AuthServerImpl;
import org.xmlBlaster.authentication.HttpIORServer;
import org.jutils.io.FileUtil;
import java.io.PrintWriter;
import java.io.FileOutputStream;
import java.io.File;
import java.util.Properties;
import org.omg.CosNaming.NamingContext;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NameComponent;

import org.xmlBlaster.authentication.Authenticate;

/**
 * CorbaDriver class to invoke the xmlBlaster server using CORBA.
 * Note the IANA assigned official CORBA ports:
 * <pre>
 *  corba-iiop      683/tcp    CORBA IIOP 
 *  corba-iiop      683/udp    CORBA IIOP 
 *  corba-iiop-ssl  684/tcp    CORBA IIOP SSL
 *  corba-iiop-ssl  684/udp    CORBA IIOP SSL
 *  
 *  corbaloc        2809/tcp   CORBA LOC
 *  corbaloc        2809/udp   CORBA LOC
 * </pre>
 * We use the following CORBA specific ports:
 * <pre>
 *   7608 as the default port to look for a naming service
 *   3412 is the xmlBlaster assigned port, used for bootstrapping (optional)
 * </pre>
 * JacORB CORBA socket:<br />
 *  org.jacorb.util.Environment.getProperty("OAIAddr");<br />
 *  org.jacorb.util.Environment.getProperty("OAPort");
 */
public class CorbaDriver implements I_Driver
{
   private String ME = "CorbaDriver";
   private org.omg.CORBA.ORB orb;
   private Global glob;
   private LogChannel log;
   private NamingContextExt namingContextExt;
   private NameComponent [] nameXmlBlaster;
   private NameComponent [] nameNode;
   private String iorFile;
   /** The singleton handle for this xmlBlaster server */
   private AuthServerImpl authServer;
   /** The singleton handle for this xmlBlaster server */
   private I_Authenticate authenticate;
   /** The singleton handle for this xmlBlaster server */
   private I_XmlBlaster xmlBlasterImpl;
   private org.omg.PortableServer.POA rootPOA;
   private org.omg.CORBA.Object authRef;
   /** The URL path over which the IOR can be accessed (via our http bootstrap server) */
   private final String urlPath = "/AuthenticationService.ior";

   private static boolean first=true;
   private static String origORBClass;
   private static String origORBSingletonClass;

   /** Get a human readable name of this driver */
   public String getName() {
      return ME;
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return "IOR"
    */
   public String getProtocolId() {
      return "IOR";
   }

   /** Enforced by I_Plugin */
   public String getType() {
      return getProtocolId();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return "1.0";
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) 
      throws XmlBlasterException {
      org.xmlBlaster.engine.Global engineGlob = (org.xmlBlaster.engine.Global)glob.getObjectEntry("ServerNodeScope");
      if (engineGlob == null)
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "could not retreive the ServerNodeScope. Am I really on the server side ?");
      try {
         Authenticate authenticate = engineGlob.getAuthenticate();
         if (authenticate == null) {
            throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "authenticate object is null");
         }
         I_XmlBlaster xmlBlasterImpl = authenticate.getXmlBlaster();
         if (xmlBlasterImpl == null) {
            throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "xmlBlasterImpl object is null");
         }
         init(glob, authenticate, xmlBlasterImpl);
         activate();
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "init. Could'nt initialize the driver.", ex);
      }
   }


   /**
    * Get the address how to access this driver. 
    * @return "IOR:00034500350..."
    */
   public String getRawAddress()
   {
      if (this.orb == null || authRef == null)
         return null;
      return this.orb.object_to_string(authRef);
   }

   /**
    * Start xmlBlaster CORBA access. 
    * Is called after plugin is created
    * @param args The command line parameters
    */
   public synchronized void init(Global glob, I_Authenticate authenticate, I_XmlBlaster xmlBlasterImpl) throws XmlBlasterException
   {
      this.glob = glob;
      this.ME = "CorbaDriver" + this.glob.getLogPrefixDashed();
      this.log = glob.getLog("corba");
      this.authenticate = authenticate;
      this.xmlBlasterImpl = xmlBlasterImpl;

      this.orb = OrbInstanceFactory.createOrbInstance(this.glob, glob.getArgs(), (Properties)null, false);

      try {
         rootPOA = org.omg.PortableServer.POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
         rootPOA.the_POAManager().activate();

         authServer = new AuthServerImpl(glob, orb, authenticate, xmlBlasterImpl);

         // USING TIE:
         org.omg.PortableServer.Servant authServant = new AuthServerPOATie(authServer);
         authRef = ((AuthServerPOATie)(authServant))._this(orb);
      }
      catch (org.omg.CORBA.COMM_FAILURE e) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Could not initialize CORBA, do you use the SUN-JDK delivered ORB instead of JacORB or ORBaccus? Try 'jaco org.xmlBlaster.Main' and read instructions in xmlBlaster/bin/jaco", e);
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Could not initialize CORBA", e);
      }
   }

   /**
    * Activate xmlBlaster access through this protocol.
    */
   public synchronized void activate() throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering activate");
      try {
         // NOT TIE:
         // org.omg.PortableServer.Servant authServant = new AuthServerImpl(orb);
         // authRef = rootPOA.servant_to_reference(authServant);

         // There are three variants how xmlBlaster publishes its AuthServer IOR (object reference)

         // 1) Write IOR to given file
         iorFile = glob.getProperty().get("ior.file", (String)null);
         if(iorFile != null) {
            PrintWriter ps = new PrintWriter(new FileOutputStream(new File(iorFile)));
            //if (log.DUMP) log.dump(ME, "Dumping authRef=" + authRef + " to " + iorFile + ": " + orb.object_to_string(authRef));
            ps.println(orb.object_to_string(authRef));
            ps.close();
            log.info(ME, "Published AuthServer IOR to file " + iorFile + ", this will be deleted on shutdown.");
         }

         // 2) Publish IOR on given port (switch off this feature with '-port 0'
         if (glob.getBootstrapAddress().getPort() > 0) {
            glob.getHttpServer().registerRequest(urlPath, orb.object_to_string(authRef));
            log.info(ME, "Published AuthServer IOR on " + glob.getBootstrapAddress().getAddress());
         }

         // 3) Publish IOR to a naming service
         boolean useNameService = glob.getProperty().get("ns", true);  // default is to publish myself to the naming service
         if (useNameService) {

            /*
            // We check if a name server is running, if not we just create and start one:
            Class nameServer = Class.forName("jacorb.naming.NameServer");
            if (nameServer != null) {
               try {
                  namingContextExt = getNamingService();
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
                        log.info(ME, "Created Name server");
                     }
                  }
                  MyNameServer thr = new MyNameServer();
                  thr.start();
                  org.jutils.runtime.Sleeper.sleep(500);
                  log.info(ME, "Started CORBA naming service");
               }
            }
            */

            // Register xmlBlaster with a name server:
            // NameService entry is e.g. xmlBlaster.MOM/heron.MOM
            try {
               namingContextExt = getNamingService();

               String contextId = glob.getProperty().get("NameService.context.id", "xmlBlaster");
               String contextKind = glob.getProperty().get("NameService.context.kind", "MOM");

               nameXmlBlaster = new NameComponent[1];
               nameXmlBlaster[0] = new NameComponent();
               nameXmlBlaster[0].id = contextId;      // old style: "xmlBlaster-Authenticate"
               nameXmlBlaster[0].kind = contextKind;  // kind is like a file extension (does not make much sense here)
               NamingContext relativeContext = null;
               int numTries = 5;  // We need to retry
               for(int i=0; i<numTries; i++) {
                  try {
                     relativeContext = namingContextExt.bind_new_context(nameXmlBlaster);
                     if (relativeContext != null) {
                        break;
                     }
                  }
                  catch (org.omg.CosNaming.NamingContextPackage.AlreadyBound ex) {
                     if (log.TRACE) log.trace(ME, "Can't register CORBA NameService context '" +
                                    getString(nameXmlBlaster) + "': " + ex.toString());
                     try {
                        org.omg.CORBA.Object obj = namingContextExt.resolve(nameXmlBlaster);
                        relativeContext = org.omg.CosNaming.NamingContextExtHelper.narrow(obj);
                        break;
                     }
                     catch (Throwable e) {
                        log.error(ME, "Can't register CORBA NameService context '" +
                                   getString(nameXmlBlaster) + "', #"+i+"/"+numTries+": " + e.toString());
                     }
                  }
                  catch (org.omg.CORBA.NO_IMPLEMENT ex) {  // JacORB 1.3.x bug (remove this catch when updated to JacORB 1.4x)
                     if (log.TRACE) log.trace(ME, "Can't register CORBA NameService context '" +
                                    getString(nameXmlBlaster) + "': " + ex.toString());
                     try {
                        org.omg.CORBA.Object obj = namingContextExt.resolve(nameXmlBlaster);
                        relativeContext = org.omg.CosNaming.NamingContextExtHelper.narrow(obj);
                        break;
                     }
                     catch (Throwable e) {
                        log.error(ME, "Can't register CORBA NameService context '" +
                                   getString(nameXmlBlaster) + "', #"+i+"/"+numTries+": " + e.toString());
                     }
                  }
               }
               if (relativeContext != null) {
                  String clusterId = glob.getProperty().get("NameService.node.id", glob.getStrippedId());
                  String clusterKind = glob.getProperty().get("NameService.node.kind", "MOM");
                  nameNode = new NameComponent[] { new NameComponent(clusterId, clusterKind) };
                  relativeContext.rebind(nameNode, authRef);
               }
               else {
                  // delegate error handling
                  throw new XmlBlasterException(glob, ErrorCode.RESOURCE_UNAVAILABLE, ME, "Can't bind to naming service");
               }

               log.info(ME, "Published AuthServer IOR to NameService '" + System.getProperty("ORBInitRef") +
                            "' with name '" + getString(nameXmlBlaster) + "/" + getString(nameNode) + "'");
            }
            catch (XmlBlasterException e) {
               log.warn(ME + ".NoNameService", e.getMessage());
               namingContextExt = null;
               if (glob.getBootstrapAddress().getPort() > 0) {
                  log.info(ME, "You don't need the naming service, i'll switch to builtin http IOR download");
               }
               else if (iorFile != null) {
                  log.info(ME, "You don't need the naming service, i'll switch to ior.file = " + iorFile);
               }
               else {
                  usage();
                  log.error(ME, "You switched off the internal http server and you didn't specify a file name for IOR dump!");
               }
            } catch (org.omg.CORBA.COMM_FAILURE e) {
               namingContextExt = null;
               if (glob.getBootstrapAddress().getPort() > 0) {
                  log.info(ME, "Can't publish AuthServer to naming service, is your naming service really running?\n" +
                               e.toString() +
                               "\nYou don't need the naming service, i'll switch to builtin http IOR download");
               }
               else if (iorFile != null) {
                  log.info(ME, "Can't publish AuthServer to naming service, is your naming service really running?\n" +
                               e.toString() +
                               "\nYou don't need the naming service, i'll switch to ior.file = " + iorFile);
               }
               else {
                  usage();
                  log.error(ME, "Can't publish AuthServer to naming service, is your naming service really running?\n" +
                               e.toString() +
                               "\nYou switched off the internal http server and you didn't specify a file name for IOR dump!");
               }
            }
         } // if useNameService
      }
      catch (org.omg.CORBA.COMM_FAILURE e) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Could not initialize CORBA, do you use the SUN-JDK delivered ORB instead of JacORB or ORBaccus? Try 'jaco org.xmlBlaster.Main' and read instructions in xmlBlaster/bin/jaco", e);
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Could not initialize CORBA", e);
      }
      // orbacus needs this
      if (orb.work_pending()) orb.perform_work();
   }

   /**
    * Deactivate xmlBlaster access (standby), no clients can connect. 
    */
   public synchronized void deActivate() throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering deActivate");

      glob.getHttpServer().removeRequest(urlPath);

      try {
         if (namingContextExt != null && nameXmlBlaster != null) {
            NamingContext relativeContext = null;
            try {
               org.omg.CORBA.Object obj = namingContextExt.resolve(nameXmlBlaster);
               relativeContext = org.omg.CosNaming.NamingContextExtHelper.narrow(obj);
            }
            catch (Throwable e) {
               log.warn(ME, "Can't unregister CORBA NameService context id=" + nameXmlBlaster[0].id + " kind=" + nameXmlBlaster[0].kind + " failed: " + e.toString());
            }
            if (relativeContext != null) {
               relativeContext.unbind(nameNode);
            }
         }
         namingContextExt = null;
      }
      catch (Throwable e) {
         log.warn(ME, "Problems during ORB cleanup: " + e.toString());
         e.printStackTrace();
      }

      try {
         if (iorFile != null) FileUtil.deleteFile(null, iorFile);
         iorFile = null;
      }
      catch (Throwable e) {
         log.warn(ME, "Problems during ORB cleanup: " + e.toString());
      }

      authRef._release();

   }

   /**
    * Creates a string representation of a NameService name hierarchy. 
    * This is useful for logging
    * @return e.g. "xmlBlaster.MOM/heron.MOM"
    */ 
   public static String getString(NameComponent [] nameComponent) {
      String ret = "";
      for(int i=0; i<nameComponent.length; i++) {
         if (i > 0) {
            ret += "/";
         }
         ret += nameComponent[i].id + ((nameComponent[i].kind != null && nameComponent[i].kind.length()>0) ? "." + nameComponent[i].kind : "");
      }
      return ret;
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
   public void shutdown() throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Shutting down ...");
      try {
         deActivate();
      } catch (XmlBlasterException e) {
         log.error(ME, e.toString());
      }

      if (this.authServer != null) {
         this.authServer.shutdown();
      }

      if (rootPOA != null && authRef != null) {
         try {
            log.trace(ME, "Deactivate POA ...");
            authRef._release();
            // poa.deactivate_object(poa.servant_to_id(authRef));
            rootPOA.deactivate_object(rootPOA.reference_to_id(authRef));
         } catch(Exception e) { log.warn(ME, "POA deactivate authentication servant failed"); }
      }

      if (rootPOA != null) {
         /*
         try {
            log.trace(ME, "Deactivate POA Manager ...");
            rootPOA.the_POAManager().deactivate(false, true);
         } catch(Exception e) { log.warn(ME, "rootPOA deactivate failed: " + e.toString()); }
         rootPOA deactivate failed: org.omg.PortableServer.POAManagerPackage.AdapterInactive: IDL:omg.org/PortableServer/POAManager/AdapterInactive:1.0
         */
         /*
         try {
            log.trace(ME, "_release POA Manager ...");
            rootPOA.the_POAManager()._release();
         } catch(Exception e) { log.warn(ME, "rootPOA _release failed: " + e.toString()); }
         rootPOA _release failed: org.omg.CORBA.NO_IMPLEMENT: This is a locally constrained object.  vmcid: 0x0  minor code: 0  completed: No
         */
         try {
            this.rootPOA.destroy(true, true);
         }
         catch (Exception ex) {
            this.log.warn(ME, "shutdown:exception occured rootPOA.destroy(): " + ex.toString());
         }
         rootPOA = null;
      }

      authRef = null;

      if (this.orb != null) {
         boolean wait_for_completion = false;
         try {
            this.orb.shutdown(wait_for_completion);
            this.orb = null;
         }
         catch (Throwable ex) {
            this.log.warn(ME, "shutdown: Exception occured during orb.shutdown("+wait_for_completion+"): " + ex.toString());
         }
      }

      log.info(ME, "POA and ORB are down, CORBA resources released.");
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
      if (log.CALL) log.call(ME, "getNamingService() ...");
      if (namingContextExt != null)
         return namingContextExt;

      NamingContextExt nameService = null;
      try {
         // Get a reference to the Name Service, CORBA compliant:
         org.omg.CORBA.Object nameServiceObj = orb.resolve_initial_references("NameService");
         if (nameServiceObj == null) {
            //log.warn(ME + ".NoNameService", "Can't access naming service, is there any running?");
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".NoNameService", "Can't access naming service, is there any running?");
         }
         if (log.TRACE) log.trace(ME, "Successfully accessed initial orb references for naming service (IOR)");

         nameService = org.omg.CosNaming.NamingContextExtHelper.narrow(nameServiceObj);
         if (nameService == null) {
            log.error(ME + ".NoNameService", "Can't access naming service == null");
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".NoNameService", "Can't access naming service (narrow problem)");
         }
         if (log.TRACE) log.trace(ME, "Successfully narrowed handle for naming service");

         return nameService; // Note: the naming service IOR is successfully evaluated (from a IOR),
                             // but it is not sure that the naming service is really running
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Exception e) {
         if (log.TRACE) log.trace(ME + ".NoNameService", e.toString() + ": " + e.getMessage());
         throw XmlBlasterException.convert(glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".NoNameService", "No CORBA naming service found - start <xmlBlaster/bin/ns ns.ior> and specify <-ORBInitRef NameService=...> if you want one.", e);
         //throw new XmlBlasterException(ME + ".NoNameService", "No CORBA naming service found - read docu at <http://www.jacorb.org> if you want one.");
      }
   }

   /**
    * Converts the internal CORBA XmlBlasterException to the util.XmlBlasterException. 
    */
   public static final org.xmlBlaster.util.XmlBlasterException convert(Global glob, org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException eCorba) {
      org.xmlBlaster.util.XmlBlasterException ex = 
         new XmlBlasterException(glob, ErrorCode.toErrorCode(eCorba.errorCodeStr),
                               eCorba.node, eCorba.location, eCorba.lang, eCorba.message, eCorba.versionInfo,
                               Timestamp.valueOf(eCorba.timestampStr),
                               eCorba.stackTrace, eCorba.embeddedMessage, eCorba.transactionInfo);
      return ex;
   }

   /**
    * Converts the util.XmlBlasterException to the internal CORBA XmlBlasterException. 
    */
   public static final org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException convert(org.xmlBlaster.util.XmlBlasterException eUtil) {
      return new org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException(
                 eUtil.getErrorCodeStr(),
                 eUtil.getNode(),
                 eUtil.getLocation(),
                 eUtil.getLang(),
                 eUtil.getRawMessage(),
                 eUtil.getVersionInfo(),
                 eUtil.getTimestamp().toString(),
                 eUtil.getStackTraceStr(),
                 eUtil.getEmbeddedMessage(),
                 eUtil.getTransactionInfo(),
                 ""); // transform native exception to Corba exception
   }

   /**
    * Converts the internal CORBA message unit to the internal representation.
    */
   public static final org.xmlBlaster.util.MsgUnitRaw convert(Global glob, org.xmlBlaster.protocol.corba.serverIdl.MessageUnit mu) throws XmlBlasterException
   {
      return new org.xmlBlaster.util.MsgUnitRaw(mu.xmlKey, mu.content, mu.qos);
   }


   /**
    * Converts the internal CORBA message unit array to the internal representation.
    */
   public static final org.xmlBlaster.util.MsgUnitRaw[] convert(Global glob, org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] msgUnitArr)
               throws XmlBlasterException
   {
      // convert Corba to internal ...
      org.xmlBlaster.util.MsgUnitRaw[] internalUnitArr = new org.xmlBlaster.util.MsgUnitRaw[msgUnitArr.length];
      for (int ii=0; ii<msgUnitArr.length; ii++) {
         internalUnitArr[ii] = CorbaDriver.convert(glob, msgUnitArr[ii]);
      }
      return internalUnitArr;
   }


   /**
    * Converts the internal MsgUnitRaw to the CORBA message unit.
    */
   public static final org.xmlBlaster.protocol.corba.serverIdl.MessageUnit convert(org.xmlBlaster.util.MsgUnitRaw mu)
   {
      return new org.xmlBlaster.protocol.corba.serverIdl.MessageUnit(mu.getKey(), mu.getContent(), mu.getQos());
   }


   /**
    * Converts the internal MsgUnitRaw array to the CORBA message unit array.
    */
   public static final org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] convert(org.xmlBlaster.util.MsgUnitRaw[] msgUnitArr)
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
      text += "   -ior.file           Specify a file where to dump the IOR of the AuthServer (for client access).\n";
      text += "   -ior                Clients can specify the raw IOR string directly (for client access).\n";
      text += "   -hostname           IP address where the builtin http server publishes its AuthServer IOR\n";
      text += "                       This is useful for multihomed hosts or dynamic dial in IPs.\n";
      text += "   -port               Port number where the builtin http server publishes its AuthServer IOR.\n";
      text += "                       Default is port "+Constants.XMLBLASTER_PORT+", the port 0 switches this feature off.\n";
      text += "   -ns false           Don't publish the IOR to a naming service.\n";
      text += "                       Default is to publish the IOR to a naming service.\n";
      text += "   -ior.hostname       Allows to set the corba server IP address for multi-homed hosts.\n";
      text += "   -ior.port           Allows to set the corba server port number.\n";
      text += " For JacORB only:\n";
      text += "   java -DOAIAddr=<ip> Use '-ior.hostname'\n";
      text += "   java -DOAPort=<nr>  Use '-ior.port'\n";
      text += "   java -Djacorb.verbosity=3  Switch CORBA debugging on\n";
      text += "   java ... -ORBInitRef NameService=corbaloc:iiop:localhost:7608/StandardNS/NameServer-POA/_root\n";
      text += "\n";
      return text;
   }
}
