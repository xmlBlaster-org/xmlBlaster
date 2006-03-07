/*------------------------------------------------------------------------------
Name:      XmlBlasterSecurityManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   XmlBlasterSecurityManager class to invoke the xmlBlaster server using RMI.
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.JdkCompatible;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

import java.rmi.RMISecurityManager;


/**
 * XmlBlasterSecurityManager class to create a RMI SecurityManager with the config/xmlBlaster.policy file.
 * <p />
 */
public class XmlBlasterSecurityManager
{
   private static Logger log = Logger.getLogger(XmlBlasterSecurityManager.class.getName());
   private static boolean createdTemporary = false;


   /**
    * Create and install a security manager.  
    * <p />
    * The security manager uses <code>xmlBlaster.policy</code> which is searched as follows:
    * <ol>
    *   <li>In the java.security.policy environment, set e.g. java -Djava.security.policy=lib/xmlBlaster.policy</li>
    *   <li>From XmlBlasterProperty (searched on command line and xmlBlaster.property)</li>
    *   <li>In the CLASSPATH</li>
    *   <li>If found in a jar file, it is read and written to the local directory for the SecurityManager to access</li>
    * </ol>
    */
   public static void createSecurityManager(Global glob) throws XmlBlasterException
   {
      if (System.getSecurityManager() == null) {
         if (System.getProperty("java.security.policy") != null) {
            // use the given policy file (java -Djava.security.policy=...)
            log.info("Setting security policy from file " + System.getProperty("java.security.policy"));
         }
         else if (glob.getProperty().get("java.security.policy", (String)null) != null) {
            String file = glob.getProperty().get("java.security.policy", (String)null);
            log.info("Setting security policy from file " + file);
            JdkCompatible.setSystemProperty("java.security.policy", file);
         }
         else {
            // try to find the policy file in the CLASSPATH
            ClassLoader loader = XmlBlasterSecurityManager.class.getClassLoader();
            if (loader != null) {
               java.net.URL serverPolicyURL = loader.getResource("xmlBlaster.policy");
               if (serverPolicyURL != null ) {
                  String serverPolicy = serverPolicyURL.getFile();
                  if (serverPolicy.indexOf("!") == -1) {
                     JdkCompatible.setSystemProperty("java.security.policy", serverPolicy);
                     log.info("Setting security policy " + serverPolicy + ", found it in your CLASSPATH.");
                  }
                  else {
                     //  xmlBlaster.policy from xmlBlaster.jar is not read correctly:
                     // file:/home/ruff/xmlBlaster/lib/xmlBlaster.jar!/xmlBlaster.policy
                     //  so we write it to hard disk and use this temporary file:
                     try {
                        java.io.InputStream is = serverPolicyURL.openStream();
                        String data = "";
                        java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                        String sep = System.getProperty("line.separator");
                        String tmp;
                        while ((tmp = in.readLine()) != null) {
                           data += tmp;
                           data += sep;
                        }
                        FileLocator.writeFile("xmlBlaster.policy", data);
                        JdkCompatible.setSystemProperty("java.security.policy", "xmlBlaster.policy");
                        log.info("Using security policy " + serverPolicy + ", found it in your CLASSPATH.");
                        if (log.isLoggable(Level.FINE)) log.fine("Wrote xmlBlaster.policy temporary into local directory to be useful for security manager");
                        if (log.isLoggable(Level.FINEST)) log.finest(data);
                        createdTemporary = true;
                     } catch (java.io.IOException e) {
                        log.warning("Can't read xmlBlaster.policy:" + e.toString());
                     } catch (XmlBlasterException e) {
                        log.warning("Can't write xmlBlaster.policy temporary to local directory:" + e.toString());
                     }
                  }
               }
            }
         }

         // Check if there was any policy file found
         if (System.getProperty("java.security.policy") == null) {
            //String text = "java.security.policy is not set, please include config/xmlBlaster.policy into your CLASSPATH or pass the file on startup like 'java -Djava.security.policy=<path>xmlBlaster.policy'...";
            //throw new XmlBlasterException("XmlBlasterSecurityManagerFailed", text);

            try {
               // Allow everything for now
               String sep = System.getProperty("line.separator");
               String data = "grant {" + sep +
                             "   permission java.security.AllPermission;" + sep +
                             "};";
               FileLocator.writeFile("xmlBlaster.policy", data);
               JdkCompatible.setSystemProperty("java.security.policy", "xmlBlaster.policy");
               log.info("java.security.policy is not set, please include config/xmlBlaster.policy into your CLASSPATH or pass the file on startup like 'java -Djava.security.policy=<path>xmlBlaster.policy'...");
               log.info("Caution: granting all rights for now ...");
               if (log.isLoggable(Level.FINE)) log.fine("Wrote xmlBlaster.policy temporary into local directory to be useful for security manager");
               if (log.isLoggable(Level.FINEST)) log.finest(data);
               createdTemporary = true;
            } catch (XmlBlasterException e) {
               log.warning("Can't write xmlBlaster.policy temporary to local directory:" + e.toString());
               String text = "java.security.policy is not set, please include config/xmlBlaster.policy into your CLASSPATH or pass the file on startup like 'java -Djava.security.policy=<path>xmlBlaster.policy'...";
               throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, "XmlBlasterSecurityManagerFailed", text);
            }
         }

         System.setSecurityManager(new RMISecurityManager());
         if (log.isLoggable(Level.FINE)) log.fine("Started RMISecurityManager");
         if (createdTemporary) {
            if (log.isLoggable(Level.FINE)) log.fine("Removed temporary xmlBlaster.policy from local directory.");
            FileLocator.deleteFile(".", "xmlBlaster.policy");
         }
      }
      else
         log.warning("Another security manager is running already, no config/xmlBlaster.policy bound");
   }
}
