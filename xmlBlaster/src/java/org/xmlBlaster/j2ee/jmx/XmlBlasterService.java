/*
 * Copyright (c) 2002 Peter Antman, Teknik i Media  <peter.antman@tim.se>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package org.xmlBlaster.j2ee.jmx;
import java.util.Properties;
import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.rmi.RMISecurityManager;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.util.classloader.ClassLoaderFactory;
import org.xmlBlaster.util.classloader.XmlBlasterClassLoader;
import org.jutils.init.Property;
import org.jutils.init.Property.FileInfo;
import org.jutils.JUtilsException;
import org.jutils.log.LogChannel;
/**
 * XmlBlaster for embedded use in a JMX server.
 *
 * <p>You may use this MBean to start one or more XmlBlaster instances in a
 JMX container. It has, however, only been tested with the JBoss 3.0 server. To start it in JBoss copy the xmlBlaster-sar.ear archive into deploy. If you need to change the settings either edit the enbedded xmlBlaster.properties file or change the name of the property file in META-INF/jboss-service.xml and make it available int the XmlBlaster search path or embed it in the sar.</p>
 
<h3>Requirements</h3>
<p>.You need to copy the file concurrent.jar from xmlBlaster/lib to the system lib directory of JBoss, overwriting the older version distributed with JBoss.</p><p>When using the RMIDriver JBoss must be run with a security policy file specified, eg, sh run.sh -Djava.security.policy=../server/default/conf/server.policy.</p>
 
 *
 *
 * @author Peter Antman
 * @version $Revision: 1.1 $ $Date: 2002/09/19 09:28:19 $
 */

public class XmlBlasterService implements XmlBlasterServiceMBean {
   private EmbeddedXmlBlaster blaster = null;
   private Global glob;
   private String propFile;
   private LogChannel log;
   private static final String ME = "XmlBlasterService";
   private XmlBlasterClassLoader cl;

   public XmlBlasterService() {
      Global g = Global.instance(); 
      this.glob = g.getClone(null);
   }
   /**
    * Set the name of a propertyfile to read settings from.
    *
    * <p>if this option is set, all properties specifyed in it will <i>overwrite</i> any properties sett on this MBean, since the file will be loaded last.</p>
    * <p>The context classloader will be searched first, then normal XmlBlaster search algoritm will be used.</p>
    */
   public void setPropertyFileName(String fileName) {
      propFile = fileName;
   }

   public String getPropertyFileName() {
      return propFile;
   }

   /**
    * Set the port the instance should run at.
    * @param port Default port is 3412
    */
   public void setPort(String port) {
      try {
         glob.getProperty().set("port", port);
      }catch(org.jutils.JUtilsException ex) {
         IllegalStateException x = new IllegalStateException("Could not set: " + port + "-" + ex);
         throw x;
      }
   }

   public String getPort() {
      return glob.getProperty().get("port", (String)null);
   }

   /**
    * Start the embeddeb XmlBlaster.
    */
   public void start() throws Exception {

      //glob.getProperty().set("trace", "true");
      log = glob.getLog(null);
      log.info(ME,"Starting XmlBlasterService");
      glob.getProperty().set("classloader.xmlBlaster","true");
      loadPropertyFile();
      setupSecurityManager();

      //setUpClassLoader(); we skip this for now, does not help with concurrent.

      ClassLoader currCl = Thread.currentThread().getContextClassLoader();
      if (cl != null)
         Thread.currentThread().setContextClassLoader(cl);
      blaster = EmbeddedXmlBlaster.startXmlBlaster(glob);
      Thread.currentThread().setContextClassLoader(currCl);
   }

   public void stop() throws Exception {
      log.info(ME,"Stopping XmlBlaster service");
      if (blaster != null ) {
         ClassLoader currCl = Thread.currentThread().getContextClassLoader();
         if (cl != null)
            Thread.currentThread().setContextClassLoader(cl);
         EmbeddedXmlBlaster.stopXmlBlaster(blaster);
         Thread.currentThread().setContextClassLoader(currCl);
      } // end of if ()
      
   }

   private void setupSecurityManager() throws Exception {
      // This is really only interesting if we are loading an RMIDriver
      if (glob.getProperty().get("ProtocolPlugin[RMI][1.0]",(String)null) == null) {
         return;// We only care about this if the RMI driver should be loaded
         
      } // end of if ()
      

      if (System.getSecurityManager() == null) {
         String exist = System.getProperty("java.security.policy");
         if (exist == null) {
            throw new Exception("You must specify a -Djava.security.policy when starting the server to be able to use the RMI driver");
         }else {         
            System.setSecurityManager(new RMISecurityManager());
         } // end of else
      }
   }
   
   private void loadPropertyFile() throws IllegalStateException{
      //Only of not null
      if (propFile== null ) 
         return;
      try {
         
         
         Property p = glob.getProperty();
         InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(propFile);
         if ( is == null) {
            // Use xmlBlaster way of searching
         FileInfo i = p.findPath(propFile);
         is = i.getInputStream();
         } // end of if ()
         
         if ( is != null) {
            Properties prop = new Properties();
            prop.load(is);
            String[] args = Property.propsToArgs(prop);
            p.addArgs2Props( args != null ? args : new String[0] ); 
         } // end of if ()

         log.trace(ME,"Setting properties: " + p.toXml());
         
      } catch (IOException e) {
         IllegalStateException x = x = new IllegalStateException("Could not load properties from file " + propFile + " :"+e);
         throw x;
         
      } catch (JUtilsException e) {
         IllegalStateException x = x = new IllegalStateException("Could not load properties into Property: " + e);
         throw x;
      } // end of try-catch
      
   }

   /**
    * Here we try to get direct access to the local jar, especially concurrent.jar. But it does not seem to work: only a few classes is actually loaded through the XmlBlasterClassLoader! Keeping it to work more on this later.
    */
   private void setUpClassLoader() throws Exception {

      ClassLoaderFactory factory = glob.getClassLoaderFactory();
      cl = factory.getXmlBlasterClassLoader();
      
      // Wont work UCL ALLWAYS return empty array
      //      URL[] blasterJars = ((URLClassLoader)getClass().getClassLoader()).getURLs();
      URL[] blasterJars = cl.getURLs();
      File blasterFile = null;
      if (blasterJars != null && blasterJars.length > 0) {
         for (int i = 0;i<blasterJars.length;i++) {
            URL jar = blasterJars[i];
            System.err.println("Checking url " + jar);
            File bj = new File(jar.getFile());
            if ("xmlBlaster.jar".equals( bj.getName())) {
               log.trace(ME,"Found blaster URL");
               blasterFile = bj;
                
            }
         }

      }

      if (blasterFile != null) {
         File tmp = new File( blasterFile.getParent(), "concurrent.jar");
         log.trace(ME,"Appending " + tmp);
         cl.appendURL(tmp.toURL());
         
      }
      
   }
   
}

