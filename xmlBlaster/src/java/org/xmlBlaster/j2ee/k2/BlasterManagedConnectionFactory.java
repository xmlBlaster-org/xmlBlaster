/*
 * Copyright (c) 2001,2003 Peter Antman, Teknik i Media  <peter.antman@tim.se>
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
package org.xmlBlaster.j2ee.k2;

import java.util.Set;
import java.util.Iterator;
import java.util.Properties;
import java.util.logging.Logger;
import java.io.PrintWriter;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.security.auth.Subject;

import javax.resource.ResourceException;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.j2ee.util.JacorbUtil;
import org.xmlBlaster.j2ee.util.GlobalUtil;

import javax.resource.spi.ManagedConnectionFactory;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.IllegalStateException;

/**
 * Factory for a specific XmlBlaster instance. 
 *
 * <p>Set the configuration up in ra.xml. <b>OBS</b> At least in JBoss this is not possible, you have to configure all properties in the *-service.xml file.</p> * <p>if given a jndiName, the connector will try to lookup a GlobalUtil wich contains the serverside engine.Global. This way, the in vm LOCAL protocol is possible to use.</p>
 * <p>The loading order of properties is: engine.Global, propertyFile, arguments set on the resource adapter.</p>
 * <p>if the protocol used is IOR, a jacorb.properties file will be loaded through the context classloader if found. This is beacuse jacorb tries to load the file from the system classloader, and its not available there when embedding xmlBlaster in JBoss.</p>
 * @author Peter Antman
 */

public class BlasterManagedConnectionFactory implements ManagedConnectionFactory {
   private static Logger log = Logger.getLogger(BlasterManagedConnectionFactory.class.getName());
   private static final long serialVersionUID = 1L;
   // Id from my global instance.
   public String myName ="Blaster";
   private Global glob;
   private GlobalUtil globalUtil;
   
   private String propFile = "xmlBlaster.properties";
   private String jndiName;
   private PrintWriter logWriter = null;
   private Properties props;
   
   public BlasterManagedConnectionFactory() throws ResourceException{
      props = new Properties();
      // We use the global to get an Id and then we throw it away
      glob = new Global(new String[]{},false,false);
      this.myName = this.myName + "[" + glob.getId() + "]";
   }
   
   /**
    * Create a "non managed" connection factory. No appserver involved
    */
   public Object createConnectionFactory() throws ResourceException {
      loadPropertyFile();
      return new BlasterConnectionFactoryImpl(this, null);
   }
   
   /**
    * Create a ConnectionFactory with appserver hook
    */ 
   public Object createConnectionFactory(ConnectionManager cxManager)
      throws ResourceException {
      loadPropertyFile();
      return new BlasterConnectionFactoryImpl(this, cxManager);
   }
   
   /**
    * Create a new connection to manage in pool
    */
   public ManagedConnection createManagedConnection(Subject subject, 
                                                    ConnectionRequestInfo info) throws ResourceException {
      BlasterCred bc = BlasterCred.getBlasterCred(this,subject, info);
      // OK we got autentication stuff
      BlasterManagedConnection mc = new BlasterManagedConnection
         (this, bc.name, bc.pwd);
      // Set default logwriter according to spec
      mc.setLogWriter(logWriter);
      return mc;

   }

   /**
    * Match a set of connections from the pool
    */
   public ManagedConnection
      matchManagedConnections(Set connectionSet,
                              Subject subject,
                              ConnectionRequestInfo info) 
      throws ResourceException {
      // Get cred
      BlasterCred bc = BlasterCred.getBlasterCred(this,subject, info);

      // Traverse the pooled connections and look for a match, return
      // first found
      Iterator connections = connectionSet.iterator();
      while (connections.hasNext()) {
         Object obj = connections.next();
            
         // We only care for connections of our own type
         if (obj instanceof BlasterManagedConnection) {
            // This is one from the pool
            BlasterManagedConnection mc = (BlasterManagedConnection) obj;
                
            // Check if we even created this on
            ManagedConnectionFactory mcf =
               mc.getManagedConnectionFactory();
                
            // Only admit a connection if it has the same username as our
            // asked for creds
            if (mc.getUserName().equals(bc.name) &&
                mcf.equals(this)) {
               return mc;
            }
         }
      }
      return null;
   }

   /**
    * FIXME
    */
   public void setLogWriter(PrintWriter out)
      throws ResourceException {
      log.severe("NOT IMPLEMENTED");
      /*
      this.logWriter = out;
      if ( logger != null) {
         logger.setLogWriter(out);
      } // end of if ()
      */
   }
   /**
    * 
    */
   public PrintWriter getLogWriter() throws ResourceException {
      return logWriter;    
   }

   public boolean equals(Object obj) {
      if (obj == null) return false;
      if (obj instanceof BlasterManagedConnectionFactory) {
         String you = ((BlasterManagedConnectionFactory) obj).
            myName;
         String me = this.myName;
         return (you == null) ? (me == null) : (you.equals(me));
      } else {
         return false;
      }
   }

   public int hashCode() {
      if (myName == null) {
         return (new String("")).hashCode();
      } else {
         return myName.hashCode();
      }
   }

   //---- Configuration API----
   /*
     Confguration is static global in XmlBlaster. There is no way
     to get around this in the highlevel api, therefor we might as
     well use that here to.
   */

   /**
    * Set a default user name.
    */
   public void setUserName(String arg){
      props.setProperty("j2ee.k2.username", arg);
   }

   public String getUserName() {
      return props.getProperty("j2ee.k2.username");
   }

   /**
    * Set a default password name.
    */
   public void setPassword(String arg){
      props.setProperty("j2ee.k2.password", arg);
   }

   public String getPassword() {
      return props.getProperty("j2ee.k2.password");
   }
   /**
    * The driver to use: IOR | RMI | LOCAL
     
    * Have to verify the others to. Don't forget to configure the server.
    */
   public void setClientProtocol(String arg){
      props.setProperty("protocol", arg);
   }
    
   /**
    * Null if not
    */
   public String getClientProtocol() {
      return props.getProperty("protocol");
   }

   /**
      Set the rmi hostname. Only when driver RMI.
   */
   public void setRmiHostname(String arg) {
      props.setProperty("dispatch/clientside/plugin/rmi/hostname", arg);
   }
    
   /**
    * Null if not
    */
   public String getRmiHostname() {
      return props.getProperty("dispatch/clientside/plugin/rmi/hostname");
   }

   /**
      Set the rmi registry port. Only when driver RMI.
   */
   public void setRmiRegistryPort(String arg){
      props.setProperty("dispatch/connection/plugin/rmi/registryPort", arg);
   }
    
   /**
    * Null if not
    */
   public String getRmiRegistryPort() {
      return props.getProperty("dispatch/connection/plugin/rmi/registryPort");
   }


   /**
      Set the rmi registry port. Only when driver RMI.
   */
   public void setRmiAuthserverUrl(String arg)  {
      props.setProperty("dispatch/connection/plugin/rmi/AuthServerUrl",arg);
   }
    
   /**
    * Null if not
    */
   public String getRmiAuthserverUrl() {
      return props.getProperty("dispatch/connection/plugin/rmi/AuthServerUrl");
   }

   /**
      Set the ior string. Only when driver IOR
   */
   public void setIor(String arg) {
      props.setProperty("dispatch/callback/plugin/ior/iorString",arg);
   }
    
   /**
    * Null if not
    */
   public String getIor() {
      return props.getProperty("dispatch/callback/plugin/ior/iorString");
   }

   /**
      Set the ior string through a file. Only when driver IOR
   */
   public void setIorFile(String arg) {
      props.setProperty("dispatch/connection/plugin/ior/iorFile", arg);
   }

   /**
      Set the hostName or IP where xmlBlaster is running. Only when driver IOR
   */
   public void setIorHost(String arg) {
      props.setProperty("bootstrapHostname", arg);
   }
    
   /**
    * Null if not
    */
   public String getIorHost() {
      return props.getProperty("bootstrapHostname");
   }

   /**
      Set bootstrapPort where the internal xmlBlaster-http server publishes its Ior. Only when driver IOR
   */
   public void setIorPort(String arg) {
      props.setProperty("bootstrapPort", arg);
   }
    
   /**
    * Null if not
    */
   public String getIorPort() {
      return props.getProperty("bootstrapPort");
   }
    
   /**
    * Null if not
    */
   public String getIorFile() {
      return props.getProperty("dispatch/connection/plugin/ior/iorFile");
   }
   /**
    * Set the security plugin to use, see {@link org.xmlBlaster.authentication.plugins}.
    */
   public void setSecurityPlugin(String arg) {
      props.setProperty("Security.Client.DefaultPlugin", arg);
   }

   /**
    * Null if not.
    */
   public String getSecurityPlugin() {
      return props.getProperty("Security.Client.DefaultPlugin");
   }
   
   /**
    * Set the session login timeout.
    */
   public void setSessionTimeout(String arg) {
      props.setProperty("session.timeout", arg);
   }

   /**
    * Null if not.
    */
   public String getSessionTimeout() {
      return props.getProperty("session.timeout");
   }
   
   /**
    * Set the maximum number of sessions a user is allowed to have opened. This must be coordinated with the JCA pooling settings.
    */
   public void setMaxSessions(String arg){
      props.setProperty("session.maxSessions", arg);
   }
   
   /**
    * Null if not.
    */
   public String getMaxSession() {
      return props.getProperty("session.maxSessions");
   }
   

   /**
    * Set the name of a propertyfile to read settings from.
    *
    * <p>if this option is set, all properties specifyed in it will <i>overwrite</i> any properties sett on this ra, since the file will be loaded last.</p>
    * <p>The context classloader will be searched first, then normal XmlBlaster search algoritm will be used.
    */
   public void setPropertyFileName(String fileName) {
      propFile = fileName;
   }

   public String getPropertyFileName() {
      return propFile;
   }
   /**
    * Set a JNDI name where a GlobalUtil will be lookedup.
    */
   public void setJNDIName(String jndiName) {
      this.jndiName = jndiName;
   }

   public String getJNDIName() {
      return jndiName;
   }

   private void loadPropertyFile() throws IllegalStateException{
      globalUtil = new GlobalUtil();
      if ( jndiName != null) {
         try {
            globalUtil = (GlobalUtil)new InitialContext().lookup(jndiName);
         } catch (NamingException e) {
            throw new IllegalStateException("Could not lookup GlobalUtil with JNDI " + jndiName + ": "+e);
         } // end of try-catch
      } // end of if ()

      glob = globalUtil.newGlobal( propFile, props );
      
      if ( "IOR".equals(glob.getProperty().get("protocol", "IOR")) ) {
         //Start by loading jacorb.properties, without it corba protocol does
         // not work well.
         try {
            JacorbUtil.loadJacorbProperties("jacorb.properties",glob);
         } catch (XmlBlasterException e) {
            IllegalStateException x = new IllegalStateException("Could not load jacorn properties, needed for IOR protocol to work: "+e);
            x.setLinkedException(e);
            throw x;
         } // end of try-catch
         
      } // end of if ()
      
   }

   

   //--- Api betwen mcf and mc

   /**
    * Return a clone of the Global, so that new XmlBlasterAccess instances may be created.
    */
   Global getConfig() {
      return globalUtil.getClone( glob );
   }
} // BlasterManagedConnectionFactory




