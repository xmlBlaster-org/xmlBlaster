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
import java.rmi.RMISecurityManager;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.j2ee.util.JacorbUtil;
import org.jutils.init.Property;
import org.jutils.init.Property.FileInfo;
import org.jutils.JUtilsException;
import org.jutils.log.LogChannel;
/**
 * XmlBlaster for embedded use in a JMX server.
 *
 * <p>You may use this MBean to start one or more XmlBlaster instances in a
 JMX container. It has, however, only been tested with the JBoss 3.0 server. To start it in JBoss copy the xmlBlaster.sar archive into deploy. If you need to change the settings either edit the enbedded xmlBlaster.properties file or change the name of the property file in META-INF/jboss-service.xml and make it availabl1e in the XmlBlaster search path or embed it in the sar.</p>
<p>To get better control ower the XmlBlaster setup process, the xmlBlaster.jar
that's embedded in the sar has had its xmlBlaster.properties and xmlBlasterPlugin.xml files removed. It's recomended to do this also in any xmlBlaster.jar that is placed in the global classpath of JBoss, otherwise it might screw up client.</p>

<h3>Requirements</h3>
<p>.You need to copy the file concurrent.jar from xmlBlaster/lib to the system lib directory of JBoss, overwriting the older version distributed with JBoss.</p><p>When using the RMIDriver JBoss must be run with a security policy file specified, eg, sh run.sh -Djava.security.policy=../server/default/conf/server.policy.</p>

 *
 *
 * @author Peter Antman
 * @version $Revision: 1.7 $ $Date: 2003/09/10 08:04:05 $
 */

public class XmlBlasterService implements XmlBlasterServiceMBean {
   private EmbeddedXmlBlaster blaster = null;
   private Global glob;
   private String propFile;
   private LogChannel log;
   private static final String ME = "XmlBlasterService";

   public XmlBlasterService() {
      // Create a global wothout loading the xmlBlaster.properties file but check it's instance
      glob= new Global(new String[]{},false,false);
      try {
         glob.getProperty().set("trace", "true");//DEBUG 
      } catch (Exception e) {
   
      } // end of try-catch

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
    * Set the bootstrap port the instance should run at.
    * @param port Default bootstrapPort is 3412
    */
   public void setPort(String port) {
      try {
         glob.getProperty().set("bootstrapPort", port);
      }catch(org.jutils.JUtilsException ex) {
         IllegalStateException x = new IllegalStateException("Could not set: " + port + "-" + ex);
         throw x;
      }
   }

   public String getPort() {
      return glob.getProperty().get("bootstrapPort", (String)null);
   }

   /**
    * Start the embeddeb XmlBlaster.
    */
   public void start() throws Exception {
      log = glob.getLog(null);
      //Get all properties
      loadJacorbProperties();
      loadPropertyFile();
      setupSecurityManager();

      // Since we relly on external configuration, we need to do this
      // for global to really get correct logging information.
      Global runglob = new Global(Property.propsToArgs( glob.getProperty().getProperties()),false , false );

      runglob.getProperty().set("xmlBlaster.isEmbedded", "true");
      runglob.getProperty().set("useSignalCatcher","false");
      runglob.getProperty().set("classLoaderFactory","org.xmlBlaster.util.classloader.ContextClassLoaderFactory");
      
      log = runglob.getLog("XmlBlasterService");
      log.info(ME,"Starting XmlBlasterService");

      blaster = EmbeddedXmlBlaster.startXmlBlaster(runglob);
   }

   public void stop() throws Exception {
      log.info(ME,"Stopping XmlBlaster service");
      if (blaster != null ) {
         EmbeddedXmlBlaster.stopXmlBlaster(blaster);
      } // end of if ()

   }

   public String dumpProperties() {
      return glob.getProperty().toXml();
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

   /**
    * Jacorb is not capable of finding its jacorb.properties in the
    * context classpath (actually it uses the system classloader.
    * Remember that jacorb.properties is in xmlBlaster.jar.
    */
   private void loadJacorbProperties() throws Exception {
      JacorbUtil.loadJacorbProperties("jacorb.properties",glob);
   }

   /**
    * If propertyFile is not null, try load it first from the context class loader, then using the standard xmlBlaster algoritm.
    */
   private void loadPropertyFile() throws IllegalStateException{
      //Only of not null
      if (propFile== null )
         return;
      try {


         Property p = glob.getProperty();
         URL url = Thread.currentThread().getContextClassLoader().getResource(propFile);
         InputStream is = null;
         try {
            is= url.openStream();
         }catch(java.io.IOException ex) {
            is = null;
         }
            //Thread.currentThread().getContextClassLoader().getResourceAsStream(propFile);
         if ( is == null) {
            // Use xmlBlaster way of searching
            FileInfo i = p.findPath(propFile);
            is = i.getInputStream();
         } // end of if ()

         if ( is != null) {
            log.info(ME,"Loading properties from " + url);
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



}

