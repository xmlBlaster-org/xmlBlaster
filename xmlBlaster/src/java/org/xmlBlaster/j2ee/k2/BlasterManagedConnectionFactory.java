/*
 * Copyright (c) 2001 Peter Antman, Teknik i Media  <peter.antman@tim.se>
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
import java.io.PrintWriter;
import java.io.InputStream;
import java.io.IOException;
import javax.security.auth.Subject;

import javax.resource.ResourceException;

import org.xmlBlaster.util.Global;
import org.jutils.init.Property;
import org.jutils.init.Property.FileInfo;
import org.jutils.JUtilsException;

import javax.resource.spi.ManagedConnectionFactory;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.SecurityException;
import javax.resource.spi.IllegalStateException;

import javax.resource.spi.security.PasswordCredential;

/**
 * Factory for a specific XmlBlaster instance. 
 *
 * <p>Set the configuration up in ra.xml. <b>OBS</b> At least in JBoss this is not possible, you have to configure all properties in the *-service.xml file.</p>
 *
 * @author Peter Antman
 */

public class BlasterManagedConnectionFactory implements ManagedConnectionFactory {
   // Id from my global instance.
   public String myName ="Blaster";
   private final Global glob;

   private String propFile = null;
   private PrintWriter logWriter = null;

   private BlasterLogger logger;
    
   public BlasterManagedConnectionFactory() throws ResourceException{
      Global g = Global.instance(); // TODO: Pass arguments or glob handle from outside
      this.glob = g.getClone(null);
      this.myName = this.myName + "[" + glob.getId() + "]";
      // Start logger, will be turned of by default
      logger = new BlasterLogger(glob);
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
    * 
    */
   public void setLogWriter(PrintWriter out)
      throws ResourceException {
      this.logWriter = out;
      logger.setLogWriter(out);
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
   public void setUserName(String arg)throws IllegalStateException {
      try {
         glob.getProperty().set("j2ee.k2.username", arg);
      }catch(org.jutils.JUtilsException ex) {
         IllegalStateException x = new IllegalStateException("Could not set: " + arg + "-" + ex);
         x.setLinkedException(ex);
         throw x;
      }
   }

   public String getUserName() {
      return glob.getProperty().get("j2ee.k2.username", (String)null);
   }

   /**
    * Set a default password name.
    */
   public void setPassword(String arg)throws IllegalStateException {
      try {
         glob.getProperty().set("j2ee.k2.password", arg);
      }catch(org.jutils.JUtilsException ex) {
         IllegalStateException x = new IllegalStateException("Could not set: " + arg + "-" + ex);
         x.setLinkedException(ex);
         throw x;
      }
   }

   public String getPassword() {
      return glob.getProperty().get("j2ee.k2.password", (String)null);
   }
   /**
    * The driver to use: IOR | RMI
     
    * Have to verify the others to. Don't forget to configure the server.
    */
   public void setClientProtocol(String arg) throws IllegalStateException {
      try {
         glob.getProperty().set("client.protocol", arg);
      }catch(org.jutils.JUtilsException ex) {
         IllegalStateException x = new IllegalStateException("Could not set: " + arg + "-" + ex);
         x.setLinkedException(ex);
         throw x;
      }
   }
    
   /**
    * Null if not
    */
   public String getClientProtocol() {
      return glob.getProperty().get("client.protocol", (String)null);
   }

   /**
      Set the rmi hostname. Only when driver RMI.
   */
   public void setRmiHostname(String arg) throws IllegalStateException {
      try {
         glob.getProperty().set("dispatch/clientside/protocol/rmi/hostname", arg);
      }catch(org.jutils.JUtilsException ex) {
         IllegalStateException x = new IllegalStateException("Could not set: " + arg + "-" + ex);
         x.setLinkedException(ex);
         throw x;
      }
   }
    
   /**
    * Null if not
    */
   public String getRmiHostname() {
      return glob.getProperty().get("dispatch/clientside/protocol/rmi/hostname", (String)null);
   }

   /**
      Set the rmi registry port. Only when driver RMI.
   */
   public void setRmiRegistryPort(String arg) throws IllegalStateException {
      try {
         glob.getProperty().set("dispatch/clientSide/protocol/rmi/registryPort", arg);
      }catch(org.jutils.JUtilsException ex) {
         IllegalStateException x = new IllegalStateException("Could not set: " + arg + "-" + ex);
         x.setLinkedException(ex);
         throw x;
      }
   }
    
   /**
    * Null if not
    */
   public String getRmiRegistryPort() {
      return glob.getProperty().get("dispatch/clientSide/protocol/rmi/registryPort", (String)null);
   }


   /**
      Set the rmi registry port. Only when driver RMI.
   */
   public void setRmiAuthserverUrl(String arg) throws IllegalStateException {
      try {
         glob.getProperty().set("dispatch/clientSide/protocol/rmi/AuthServerUrl", arg);
      }catch(org.jutils.JUtilsException ex) {
         IllegalStateException x = new IllegalStateException("Could not set: " + arg + "-" + ex);
         x.setLinkedException(ex);
         throw x;
      }
   }
    
   /**
    * Null if not
    */
   public String getRmiAuthserverUrl() {
      return glob.getProperty().get("dispatch/clientSide/protocol/rmi/AuthServerUrl", (String)null);
   }

   /**
      Set the ior string. Only when driver IOR
   */
   public void setIor(String arg) throws IllegalStateException {
      try {
         glob.getProperty().set("dispatch/callback/protocol/ior/iorString", arg);
      }catch(org.jutils.JUtilsException ex) {
         IllegalStateException x = new IllegalStateException("Could not set: " + arg + "-" + ex);
         x.setLinkedException(ex);
         throw x;
      }
   }
    
   /**
    * Null if not
    */
   public String getIor() {
      return glob.getProperty().get("dispatch/callback/protocol/ior/iorString", (String)null);
   }

   /**
      Set the ior string through a file. Only when driver IOR
   */
   public void setIorFile(String arg) throws IllegalStateException {
      try {
         glob.getProperty().set("dispatch/clientSide/protocol/ior/iorFile", arg);
      }catch(org.jutils.JUtilsException ex) {
         IllegalStateException x = new IllegalStateException("Could not set: " + arg + "-" + ex);
         x.setLinkedException(ex);
         throw x;
      }
   }

   /**
      Set the hostName or IP where xmlBlaster is running. Only when driver IOR
   */
   public void setIorHost(String arg) throws IllegalStateException {
      try {
         glob.getProperty().set("bootstrapHostname", arg);
      }catch(org.jutils.JUtilsException ex) {
         IllegalStateException x = new IllegalStateException("Could not set: " + arg + "-" + ex);
         x.setLinkedException(ex);
         throw x;
      }
   }
    
   /**
    * Null if not
    */
   public String getIorHost() {
      return glob.getProperty().get("bootstrapHostname", (String)null);
   }

   /**
      Set bootstrapPort where the internal xmlBlaster-http server publishes its Ior. Only when driver IOR
   */
   public void setIorPort(String arg) throws IllegalStateException {
      try {
         glob.getProperty().set("bootstrapPort", arg);
      }catch(org.jutils.JUtilsException ex) {
         IllegalStateException x = new IllegalStateException("Could not set: " + arg + "-" + ex);
         x.setLinkedException(ex);
         throw x;
      }
   }
    
   /**
    * Null if not
    */
   public String getIorPort() {
      return glob.getProperty().get("bootstrapPort", (String)null);
   }
    
   /**
    * Null if not
    */
   public String getIorFile() {
      return glob.getProperty().get("dispatch/clientSide/protocol/ior/iorFile", (String)null);
   }
   /**
    * Set the security plugin to use, see {@link org.xmlBlaster.authentication.plugins}.
    */
   public void setSecurityPlugin(String arg) throws IllegalStateException{
      try {
         glob.getProperty().set("Security.Client.DefaultPlugin", arg);
      }catch(org.jutils.JUtilsException ex) {
         IllegalStateException x = new IllegalStateException("Could not set: " + arg + "-" + ex);
         x.setLinkedException(ex);
         throw x;
      }
   }

   /**
    * Null if not.
    */
   public String getSecurityPlugin() {
      return glob.getProperty().get("Security.Client.DefaultPlugin", (String)null);
   }
   
   /**
    * Set the session login timeout.
    */
   public void setSessionTimeout(String arg) throws IllegalStateException{
      try {
         glob.getProperty().set("session.timeout", arg);
      }catch(org.jutils.JUtilsException ex) {
         IllegalStateException x = new IllegalStateException("Could not set: " + arg + "-" + ex);
         x.setLinkedException(ex);
         throw x;
      }
   }

   /**
    * Null if not.
    */
   public String getSessionTimeout() {
      return glob.getProperty().get("session.timeout", (String)null);
   }
   
   /**
    * Set the maximum number of sessions a user is allowed to have opened. This must be coordinated with the JCA pooling settings.
    */
   public void setMaxSessions(String arg) throws IllegalStateException{
      try {
         glob.getProperty().set("session.maxSessions", arg);
      }catch(org.jutils.JUtilsException ex) {
         IllegalStateException x = new IllegalStateException("Could not set: " + arg + "-" + ex);
         x.setLinkedException(ex);
         throw x;
      }
   }
   
   /**
    * Null if not.
    */
   public String getMaxSession() {
      return glob.getProperty().get("session.maxSessions", (String)null);
   }
   

   /**
    * Set the name of a propertyfile to read settings from.
    *
    * <p>if this option is set, all properties psecifyed in it will <i>overwrite</i> any properties sett on this ra, since the file will be loaded last.</p>
    * <p>The context classloader will be searched first, then normal XmlBlaster search algoritm will be used.
    */
   public void setPropertyFileName(String fileName) {
      propFile = fileName;
   }

   public String getPropertyFileName() {
      return propFile;
   }

   /**
      <p>
      Decides if logging should be done at al. Cant set log levels for now.
      </p>
      <p>
      If ConnectionManager does not set a printWriter and the loggin is on,
      logging will be done to the console.
      </p>

     
   */
   public void setLogging(String loggingOn) {
      logger.setLogging(new Boolean(loggingOn).booleanValue());
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
            is = i != null ?i.getInputStream(): null;
         } // end of if ()
         
         if ( is != null) {
            Properties prop = new Properties();
            prop.load(is);
            String[] args = Property.propsToArgs(prop);
            p.addArgs2Props( args != null ? args : new String[0] ); 
         } // end of if ()
         
      } catch (IOException e) {
         IllegalStateException x = x = new IllegalStateException("Could not load properties from file " + propFile + " :"+e);
         x.setLinkedException(e);
         throw x;
         
      } catch (JUtilsException e) {
         IllegalStateException x = x = new IllegalStateException("Could not load properties into Property: " + e);
         x.setLinkedException(e);
         throw x;
      } // end of try-catch
      
   }

   //--- Api betwen mcf and mc

   Global getConfig() {
      return glob;
   }
} // BlasterManagedConnectionFactory




