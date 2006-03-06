/*
 * Copyright (c) 2003 Peter Antman, Teknik i Media  <peter.antman@tim.se>
 *
 * $Id$
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
package org.xmlBlaster.j2ee.util;
import java.util.Properties;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.rmi.RMISecurityManager;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.property.Property;
import org.xmlBlaster.util.property.Property.FileInfo;

/**
 * A Global helper class to make it easier to work with Global in an embedded
 * J2EE environment.
 * <p>The helper may be used in two major ways: created standalone, or looked up through JNDI. When looked up trough JNDI it might be possible to have access to
 * the engine.Global if accessed in the same VM as the engine.</p>
 *
 * @author <a href="mailto:pra@tim.se">Peter Antman</a>
 * @version $Revision: 1.2 $
 */

public class GlobalUtil implements java.io.Serializable {
   /**
    * 
    */
   private static final long serialVersionUID = 1L;
   private transient org.xmlBlaster.engine.ServerScope engineGlobal;

   /**
    * Create a utility class without any engine global.
    */ 
   public GlobalUtil (){
      
   }

   /**
    * Create a utility class with access to engine global, use this to
    * bind into jndi.
    */
   public GlobalUtil(org.xmlBlaster.engine.ServerScope engineGlobal) {
      this.engineGlobal = engineGlobal;
   }

   /**
    * Create a new Global without any automatic background loading of
    * xmlBlaster.properties.
    * <p>if engineGlobal is available, its set as a ServerNode.</p>
    * <p>This Global will be setup to be used in a server environment.</p>
    * <p>if an engine global exists, the properties from that one will be
    * set first in the created Global.</p>
    */
   public Global newGlobal(String propertyFileName, Properties args) throws IllegalStateException {
      Global glob = new Global(new String[]{},false,false);
      Global clone = getClone(glob);
      addEngineProperties(clone);
      addServerProperties(clone);
      loadPropertyFile(clone,propertyFileName);
      addArguments(clone,args);
      return clone;
   }

   /**
    * Set the properties from the serverside engine global in the given global if it exists.
    */
   public void addEngineProperties(Global glob) throws IllegalStateException{
      if ( engineGlobal != null) {
         Property p = glob.getProperty();
         String[] args = Property.propsToArgs(engineGlobal.getProperty().getProperties() );
         try {
            p.addArgs2Props( args != null ? args : new String[0] );
         } catch (XmlBlasterException e) {
            IllegalStateException x = new IllegalStateException("Could not engine properties into global: " + e);
         throw x;
         } // end of try-catch

      } // end of if ()
      
   }

   /**
    * Clone the given Global, check if it contains a ServerNode, or set
    * this engineGlobal if not null as ServerNode.
    */
   public Global getClone(Global global) {
      Global g = global.getClone(null);
      
      Object engine = global.getObjectEntry("ServerNodeScope");
      Object eg = engine != null ? engine : engineGlobal;
      if ( eg != null) {
         g.addObjectEntry("ServerNodeScope", eg);
      } // end of if ()
      
      // Should we perhaps also clone POA???

      return g;
   }

   /**
    * Load properties from propertyFile into global.
    * <p>The property file is first looked up the the context classpath, next the xmlBlaster lookup algorithm is used.</p>
    * <p>The global is returned as is, that means that any properties found
    * in propertyFile, such as logging, is ignored until a cloned copy of
    * the global is retained.</p>
    */
   public void loadPropertyFile(Global glob, String propFile) throws IllegalStateException{
      if (propFile== null )
         return;
      
      try { 
         Property p = glob.getProperty();
         URL url = Thread.currentThread().getContextClassLoader().getResource(propFile);
         InputStream is = null;
         if ( url != null) {
            try {
               is= url.openStream();
               
            }catch(java.io.IOException ex) {
               is = null;
            }
         } // end of if ()
         
         if ( is == null) {
            // Use xmlBlaster way of searching
            FileInfo i = p.findPath(propFile);
            if ( i != null) {
               is = i.getInputStream();
            } // end of if ()

         } // end of if ()
         
         if ( is != null) {
            Properties prop = new Properties();
            prop.load(is);
            addArguments(glob, prop);
         } // end of if ()
         
      } catch (IOException e) {
         IllegalStateException x = new IllegalStateException("Could not load properties from file " + propFile + " :"+e);
         throw x;
         
      } catch (XmlBlasterException e) {
         IllegalStateException x = new IllegalStateException("Could not load properties into Property: " + e);
         throw x;
      } // end of try-catch
   }
   

   /**
    * Ad arguments found in props to the global.
    * <p>The global is returned as is, that means that any properties found
    * in props, such as logging, is ignored until a cloned copy of
    * the global is retained.</p>
    */
   public void addArguments(Global glob, Properties props) throws IllegalStateException{
      if ( props == null) {
         return; 
      } // end of if ()
      try {                
         Property p = glob.getProperty();
         String[] args = Property.propsToArgs(props);
         p.addArgs2Props( args != null ? args : new String[0] );
      } catch (XmlBlasterException e) {
         IllegalStateException x = new IllegalStateException("Could not load properties into Property: " + e);
         throw x;
      } // end of try-catch
   }

   /**
    * Ad typical properties needed to run embedded in a J2EE/JBoss server.
    */
   public void addServerProperties(Global glob) throws IllegalStateException {
      try {
         glob.getProperty().set("classLoaderFactory","org.xmlBlaster.util.classloader.ContextClassLoaderFactory");
         glob.getProperty().set("xmlBlaster.isEmbedded", "true");
         glob.getProperty().set("useSignalCatcher","false");
      } catch (XmlBlasterException e) {
         IllegalStateException x = new IllegalStateException("Could not set serverside properties: " + e);
         throw x;
      } // end of try-catch

      
   }

   /**
    * Check and possibly setup a security manager if an RMI driver is loaded.
    */
   public void setupSecurityManager(Global glob) throws SecurityException{
      // This is really only interesting if we are loading an RMIDriver
      if (glob.getProperty().get("ProtocolPlugin[RMI][1.0]",(String)null) == null) {
         return;// We only care about this if the RMI driver should be loaded
         
      } // end of if ()
      
      
      if (System.getSecurityManager() == null) {
         String exist = System.getProperty("java.security.policy");
         if (exist == null) {
            throw new SecurityException("You must specify a -Djava.security.policy when starting the server to be able to use the RMI driver");
         }else {
            System.setSecurityManager(new RMISecurityManager());
         } // end of else
      }
   }
}// GlobalUtil
