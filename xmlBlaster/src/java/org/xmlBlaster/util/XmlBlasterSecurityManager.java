/*------------------------------------------------------------------------------
Name:      XmlBlasterSecurityManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   XmlBlasterSecurityManager class to invoke the xmlBlaster server using RMI.
Version:   $Id: XmlBlasterSecurityManager.java,v 1.4 2002/02/07 13:12:21 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.JdkCompatible;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.jutils.io.FileUtil;

import java.rmi.RemoteException;
import java.rmi.RMISecurityManager;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.Naming;
import java.rmi.AlreadyBoundException;


/**
 * XmlBlasterSecurityManager class to create a SecurityManager with the config/xmlBlaster.policy file.
 * <p />
 */
public class XmlBlasterSecurityManager
{
   private static final String ME = "XmlBlasterSecurityManager";
   private static boolean createdTemporary = false;


   /**
    * Create and install a security manager, using xmlBlaster.policy
    * <p />
    * xmlBlaster.policy is searched as follows:
    * <ol>
    *   <li>In the java.security.policy environment, set e.g. java -Djava.security.policy=lib/xmlBlaster.policy</li>
    *   <li>From XmlBlasterProperty (searched on command line and xmlBlaster.property)</li>
    *   <li>In the CLASSPATH</li>
    *   <li>If found in a jar file, it is read and written to the local directory for the SecurityManager to access</li>
    * </ol>
    */
   public static void createSecurityManager() throws XmlBlasterException
   {
      if (System.getSecurityManager() == null) {
         if (System.getProperty("java.security.policy") != null) {
            // use the given policy file (java -Djava.security.policy=...)
            Log.info(ME, "Setting security policy from file " + System.getProperty("java.security.policy"));
         }
         else if (XmlBlasterProperty.get("java.security.policy", (String)null) != null) {
            String file = XmlBlasterProperty.get("java.security.policy", (String)null);
            Log.info(ME, "Setting security policy from file " + file);
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
                     Log.info(ME, "Setting security policy " + serverPolicy + ", found it in your CLASSPATH.");
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
                        FileUtil.writeFile("xmlBlaster.policy", data);
                        JdkCompatible.setSystemProperty("java.security.policy", "xmlBlaster.policy");
                        Log.info(ME, "Using security policy " + serverPolicy + ", found it in your CLASSPATH.");
                        if (Log.TRACE) Log.trace(ME, "Wrote xmlBlaster.policy temporary into local directory to be useful for security manager");
                        if (Log.DUMP) Log.dump(ME, data);
                        createdTemporary = true;
                     } catch (java.io.IOException e) {
                        Log.warn(ME, "Can't read xmlBlaster.policy:" + e.toString());
                     } catch (org.jutils.JUtilsException e) {
                        Log.warn(ME, "Can't write xmlBlaster.policy temporary to local directory:" + e.toString());
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
               FileUtil.writeFile("xmlBlaster.policy", data);
               JdkCompatible.setSystemProperty("java.security.policy", "xmlBlaster.policy");
               Log.info(ME, "java.security.policy is not set, please include config/xmlBlaster.policy into your CLASSPATH or pass the file on startup like 'java -Djava.security.policy=<path>xmlBlaster.policy'...");
               Log.info(ME, "Caution: granting all rights for now ...");
               if (Log.TRACE) Log.trace(ME, "Wrote xmlBlaster.policy temporary into local directory to be useful for security manager");
               if (Log.DUMP) Log.dump(ME, data);
               createdTemporary = true;
            } catch (org.jutils.JUtilsException e) {
               Log.warn(ME, "Can't write xmlBlaster.policy temporary to local directory:" + e.toString());
               String text = "java.security.policy is not set, please include config/xmlBlaster.policy into your CLASSPATH or pass the file on startup like 'java -Djava.security.policy=<path>xmlBlaster.policy'...";
               throw new XmlBlasterException("XmlBlasterSecurityManagerFailed", text);
            }
         }

         System.setSecurityManager(new RMISecurityManager());
         if (Log.TRACE) Log.trace(ME, "Started RMISecurityManager");
         if (createdTemporary) {
            if (Log.TRACE) Log.trace(ME, "Removed temporary xmlBlaster.policy from local directory.");
            FileUtil.deleteFile(".", "xmlBlaster.policy");
         }
      }
      else
         Log.warn(ME, "Another security manager is running already, no config/xmlBlaster.policy bound");
   }
}
