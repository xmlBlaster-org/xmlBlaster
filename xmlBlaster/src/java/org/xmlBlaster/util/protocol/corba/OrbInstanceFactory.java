/*------------------------------------------------------------------------------
Name:      OrbInstanceFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.protocol.corba;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.omg.CosNaming.NameComponent;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
//import org.xmlBlaster.util.JdkCompatible;
import java.util.Properties;
import java.util.Enumeration;

import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.qos.address.AddressBase;

/**
 * OrbInstanceFactory singleton to create a CORBA orb instance. 
 *
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
public final class OrbInstanceFactory
{
   private static Logger log = Logger.getLogger(OrbInstanceFactory.class.getName());
   private static boolean first=true;
   private static String origORBClass;
   private static boolean nameServiceStarted;

   /**
    * Sets the environment for CORBA. 
    * <p />
    * Example for JacORB:
    * <pre>
    *  org.omg.CORBA.ORBClass=org.jacorb.orb.ORB
    *  org.omg.CORBA.ORBSingletonClass=org.jacorb.orb.ORBSingleton
    *  -ORBInitRef NameService=corbaloc:iiop:localhost:7608/StandardNS/NameServer-POA/_root
    * </pre>
    *
    * Forces to use JacORB instead of JDK internal ORB (which is outdated)
    * and looks for NamingService on port 7608
    *
    * @param glob Handle to access logging, properties etc.
    * @param address The address configuration
    * @return The used properties for the ORB
    */
   private synchronized static Properties initializeOrbEnv(Global glob, AddressBase address)
   {
      glob = (glob == null) ? Global.instance() : glob;

      Properties props = new Properties();
      //props.put("org.omg.CORBA.ORBClass", "SomeORBImplementation");
 
      if (first) {
         first = false;
         origORBClass = System.getProperty("org.omg.CORBA.ORBClass");
      //   if (origORBClass==null) origORBClass=""; // System.setProperties does not like null values
      //   origORBSingletonClass = System.getProperty("org.omg.CORBA.ORBSingletonClass");
      //   if (origORBSingletonClass==null) origORBSingletonClass="";
      }
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

      /* OpenOrb:
         "org.omg.CORBA.ORBClass=org.openorb.CORBA.ORB"
         "org.omg.CORBA.ORBSingletonClass=org.openorb.CORBA.ORBSingleton"
         java -Dorg.omg.CORBA.ORBClass=org.openorb.CORBA.ORB -Dorg.omg.CORBA.ORBSingletonClass=org.openorb.CORBA.ORBSingleton org.xmlBlaster.Main
      */

      // If not set, force to use JacORB instead of JDK internal ORB (which is outdated)
      if (origORBClass == null || origORBClass.length() < 1) {
         String tmp = glob.getProperty().get("org.omg.CORBA.ORBClass", "org.jacorb.orb.ORB");
         if (tmp.length() < 1) {      // As with JDK 1.4.1 i get NPE when trying to reset System.setProperty(key, null) we use an empty string
            tmp = "org.jacorb.orb.ORB";
         }
         //JdkCompatible.setSystemProperty("org.omg.CORBA.ORBClass", tmp);
         props.put("org.omg.CORBA.ORBClass", tmp);

         tmp = glob.getProperty().get("org.omg.CORBA.ORBSingletonClass", "org.jacorb.orb.ORBSingleton");
         if (tmp.length() < 1) {
            tmp = "org.jacorb.orb.ORBSingleton";
         }
         //JdkCompatible.setSystemProperty("org.omg.CORBA.ORBSingletonClass", tmp);
         props.put("org.omg.CORBA.ORBSingletonClass", tmp);
      }
      
      // -dispatch/connection/plugin/ior/hostname   
      String hostname = address.getEnv("hostname", (String)null).getValue();
      if (log.isLoggable(Level.FINE)) log.fine("initializeOrbEnv(" + address.getEnvLookupKey("hostname") + "=" + hostname);

      String orbClass = (String)props.get("org.omg.CORBA.ORBClass");
      if (orbClass != null && orbClass.indexOf("jacorb") >= 0) {
         // Setting JacORB
         if (hostname != null) {
            //JdkCompatible.setSystemProperty("OAIAddr", hostname);
            props.put("OAIAddr", hostname);
            if (log.isLoggable(Level.FINE)) log.fine("Using OAIAddr=" + address.getEnvLookupKey("hostname") + "=" + props.getProperty("OAIAddr"));
         }
         
         int port = address.getEnv("port", 0).getValue();
         if (port > 0) {
            //JdkCompatible.setSystemProperty("OAPort", ""+port);
            props.put("OAPort", ""+port);
            if (log.isLoggable(Level.FINE)) log.fine("Using OAPort=" + address.getEnvLookupKey("port") + "=" + props.getProperty("OAPort"));
         }

         int verbose = glob.getProperty().get("jacorb.log.default.verbosity", -1);
         if (verbose >= 0) {
            //JdkCompatible.setSystemProperty("jacorb.log.default.verbosity", ""+verbose);
            props.put("jacorb.log.default.verbosity", ""+verbose);
            if (log.isLoggable(Level.FINE)) log.fine("Using jacorb.log.default.verbosity=" + props.getProperty("jacorb.log.default.verbosity"));
         }

         // Bug workaround: with JacORB 2.1 we get logging from POA,
         // Setting jacorb.poa.log.verbosity=0 in jacorb.properties does not help
         // setting ' java -Djacorb.poa.log.verbosity=0 ... ' helps:
         int verbosePoa = glob.getProperty().get("jacorb.poa.log.verbosity", 0);
         props.put("jacorb.poa.log.verbosity", ""+verbosePoa);
         if (log.isLoggable(Level.FINE)) log.fine("Using jacorb.poa.log.verbosity=" + verbosePoa);
      }

      if (log.isLoggable(Level.FINE)) log.fine("Using org.omg.CORBA.ORBClass=" + props.getProperty("org.omg.CORBA.ORBClass"));
      if (log.isLoggable(Level.FINE)) log.fine("Using org.omg.CORBA.ORBSingletonClass=" + props.getProperty("org.omg.CORBA.ORBSingletonClass"));

      /*
      CHANGED 2003-02-27 Marcel:
      The jacorb.properties file has no impact anymore if we set the System.properties
      // We use default Port 7608 for naming service to listen ...
      // Start Naming service
      //    jaco -DOAPort=7608  org.jacorb.naming.NameServer /tmp/ns.ior
      // and xmlBlaster will find it automatically if on same host
      */
      if (glob.getProperty().get("ORBInitRef", (String)null) != null) {
         String tmp = glob.getProperty().get("ORBInitRef", "NameService=corbaloc:iiop:localhost:7608/StandardNS/NameServer-POA/_root");
         // -ORBInitRef "NameService=corbaloc:iiop:localhost:7608/StandardNS/NameServer-POA/_root"
         //JdkCompatible.setSystemProperty("ORBInitRef", tmp);
         props.put("ORBInitRef", tmp);
         if (log.isLoggable(Level.FINE)) log.fine("Using corbaloc -ORBInitRef NameService="+glob.getProperty().get("ORBInitRef",(String)null)+" to find a naming service");
      }

      return props;
   }

   /**
    * @param glob
    * @param args command line args, see org.omg.CORBA.ORB.init(), use glob.getProperty().getProperties()
    * @param props application-specific properties; may be <code>null</code>, see org.omg.CORBA.ORB.init(String[], Properties)
    * @param address The address configuration
    * @return Access to a new created orb handle
    * @see org.omg.CORBA.ORB#init(String[], Properties)
    */
   public static org.omg.CORBA.ORB createOrbInstance(Global glob, String[] args, Properties props, AddressBase address) {
      synchronized (System.class) {
         Properties properties = initializeOrbEnv(glob, address);
         if (props != null) {
            Enumeration e = props.propertyNames();
            while (e.hasMoreElements()) {
               String key = (String)e.nextElement();
               properties.put(key, props.get(key));
            }
         }
         org.omg.CORBA.ORB anOrb = org.omg.CORBA.ORB.init(args, properties);
         /*
         if (origORBClass != null) {
            JdkCompatible.setSystemProperty("org.omg.CORBA.ORBClass", origORBClass);
         }
         if (origORBSingletonClass != null) {
            JdkCompatible.setSystemProperty("org.omg.CORBA.ORBSingletonClass", origORBSingletonClass);
         }
         */

         boolean startNamingService = glob.getProperty().get("plugin/ior/useNameService", false);
         if (startNamingService) {
            startNameService(glob);
         }

         return anOrb;
      }
   }

   /**
    * Get (or create) an OrbInstanceWrapper object which is useful to handle one
    * CORBA orb instance with reference counting
    * @param glob
    * @param prefix A unique identifier for the orb, using the same prefix will return
    *   the same orb on second and further calls
    */
   public static OrbInstanceWrapper getOrbInstanceWrapper(Global glob, String prefix) {
      if (glob == null) throw new IllegalArgumentException("You called OrbInstanceFactory.getOrbInstanceWrapper() with glob==null");
      synchronized (glob) {
         OrbInstanceWrapper orbInstanceWrapper =
               (OrbInstanceWrapper)glob.getObjectEntry(prefix+":org.xmlBlaster.util.protocol.corba.OrbInstanceWrapper");
         if (orbInstanceWrapper == null) {
            orbInstanceWrapper = new OrbInstanceWrapper(glob);
            glob.addObjectEntry(prefix+":org.xmlBlaster.util.protocol.corba.OrbInstanceWrapper", orbInstanceWrapper);
         }
         return orbInstanceWrapper;
      }
   }

   /**
    * Obtain the host information from the given CORBA object.
    * This is accomplished by parsing the object's IOR using CORBA classes
    * org.omg.IOP.IORHelper and org.omg.IIOP.ProfileBody_1_1Helper.
    * Consequently, CORBA version is 2.x (or higher) is required.
    * @param obj the CORBA object to interrogate
    * @param orb the ORB to use during interrogation
    * @returns the host where the given object resides, or null on error.
    * @author Code from adavis@cadrc.calpoly.edu from JacORB mailing list
    */
   public static String extractHost(org.omg.CORBA.Object obj, org.omg.CORBA.ORB orb)
   {
      String host = null;
      if ( obj != null && orb != null )
      {
         try
         {
            String objRef = orb.object_to_string(obj);
            org.omg.CORBA.portable.OutputStream out =
               orb.create_output_stream();
            int cnt = (objRef.length() - 4) / 2;
            int o1;
            int o2;
            Integer b;
            for (int i=0; i<cnt; i++)
            {
               o1 = i*2+4;
               o2 = o1+2;
               try
               {
                  b = Integer.valueOf(objRef.substring(o1,o2), 16);
                  out.write_octet(b.byteValue());
               }
               catch (NumberFormatException nfe)
               {
                  //ErrorLog.logWarning("ObjectUtil", "extractHost",
                  //   "'"+objRef.substring(o1,o2)+"' not a valid integer");
                  throw nfe;
               }
            }
            org.omg.CORBA.portable.InputStream in = out.create_input_stream();
            /*boolean littleEndian =*/ in.read_boolean();
            org.omg.IOP.IOR ior = org.omg.IOP.IORHelper.read(in);
            org.omg.IIOP.ProfileBody_1_1 body;
            for (int i=0; host==null && i<ior.profiles.length; i++)
            {
               if ( ior.profiles[i].tag == org.omg.IOP.TAG_INTERNET_IOP.value )
               {
                  org.omg.CORBA.portable.OutputStream prof_out =
                     orb.create_output_stream();
                  byte[] prof_data = ior.profiles[i].profile_data;
                  prof_out.write_octet_array(prof_data, 0, prof_data.length);
                  org.omg.CORBA.portable.InputStream prof_in =
                     prof_out.create_input_stream();
                  body = org.omg.IIOP.ProfileBody_1_1Helper.read(prof_in);
                  host = body.host;
               }
            }
         }
         catch (Exception e)
         {
            //ErrorLog.logWarning("ObjectUtil", "extractHost",
            //   "exception '"+e+"' during hostname extraction");
            System.out.println("OrbInstanceFactory:" + e.toString());
         }
      }
      return host;
   }

   /**
    * Note: This code is not yet tested to be functional (Marcel 2003-12-12)
    */
   public static void startNameService(Global glob_) {
      if (nameServiceStarted)
         return;
      final String[] s = new String[0]; // new String[]{"-p","1077"}; // See OAPort discussion below
      new Thread() {
         public void run() {
            log.info("Starting embedded Jacorb namingService");
            org.xmlBlaster.util.JdkCompatible.setSystemProperty("org.omg.CORBA.ORBClass", "org.jacorb.orb.ORB");
            org.xmlBlaster.util.JdkCompatible.setSystemProperty("org.omg.CORBA.ORBSingletonClass", "org.jacorb.orb.ORBSingleton");
            // This does not work as the name service will collide with our ORB:
            // OAPort=7608 forces both to the same port
            // So we need to let the NamingService to choose its own port
            //org.xmlBlaster.util.JdkCompatible.setSystemProperty("OAPort", "7608");
            org.jacorb.naming.NameServer.main(s);
            // How to get the NameServer IOR?
         }
      }.start();
      nameServiceStarted = true;
      try { Thread.sleep(2000L); } catch( InterruptedException i) {}
   }

   /**
    * Converts the internal CORBA message unit to the internal representation.
    */
   public static final org.xmlBlaster.util.MsgUnitRaw convert(Global glob, org.xmlBlaster.protocol.corba.serverIdl.MessageUnit mu) throws XmlBlasterException
   {
      return new org.xmlBlaster.util.MsgUnitRaw(mu.xmlKey, mu.content, mu.qos);
   }

   /**
    * Converts the internal MsgUnitRaw to the CORBA message unit.
    */
   public static final org.xmlBlaster.protocol.corba.serverIdl.MessageUnit convert(org.xmlBlaster.util.MsgUnitRaw mu)
   {
      return new org.xmlBlaster.protocol.corba.serverIdl.MessageUnit(mu.getKey(), mu.getContent(), mu.getQos());
   }

   /**
    * Converts the internal CORBA XmlBlasterException to the util.XmlBlasterException. 
    */
   public static final org.xmlBlaster.util.XmlBlasterException convert(Global glob, org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException eCorba) {
      boolean isServerSide = !glob.isServerSide();
      org.xmlBlaster.util.XmlBlasterException ex = 
         new XmlBlasterException(glob, ErrorCode.toErrorCode(eCorba.errorCodeStr),
                               eCorba.node, eCorba.location, eCorba.lang, eCorba.message, eCorba.versionInfo,
                               Timestamp.valueOf(eCorba.timestampStr),
                               eCorba.stackTrace, eCorba.embeddedMessage,
                               eCorba.transactionInfo, isServerSide);
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
                 ""
                 /*eUtil.isServerSide() IS MISSING */); // transform native exception to Corba exception
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
         internalUnitArr[ii] = OrbInstanceFactory.convert(glob, msgUnitArr[ii]);
      }
      return internalUnitArr;
   }


   /**
    * Converts the internal MsgUnitRaw array to the CORBA message unit array.
    */
   public static final org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] convert(org.xmlBlaster.util.MsgUnitRaw[] msgUnitArr)
   {
      // convert internal to Corba ...
      org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] corbaUnitArr = new org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[msgUnitArr.length];
      for (int ii=0; ii<msgUnitArr.length; ii++) {
         corbaUnitArr[ii] = OrbInstanceFactory.convert(msgUnitArr[ii]);
      }
      return corbaUnitArr;
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
}
