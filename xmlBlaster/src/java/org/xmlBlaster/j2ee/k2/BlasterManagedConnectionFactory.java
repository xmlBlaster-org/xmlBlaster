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

import java.io.PrintWriter;

import javax.security.auth.Subject;

import javax.resource.ResourceException;

import org.xmlBlaster.util.XmlBlasterProperty;

import javax.resource.spi.ManagedConnectionFactory;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.SecurityException;
import javax.resource.spi.IllegalStateException;

import javax.resource.spi.security.PasswordCredential;

/**
 * Factory for a specific XmlBlaster instance. This impl will NOT
   work if the appserver instantiates more that one copy of this
   for one physical XmlBlaster server.

   This is because a user will get mapped to so called "pseudouser" to
   be able to hande more that one active physical connection to the
   xmlBlaster server for every logical user.
 *
 *
 * Created: Fri Jan 26 14:57:16 2001
 *
 * @author Peter Antman
 * @version
 */

public class BlasterManagedConnectionFactory implements ManagedConnectionFactory {
    // Hm, don't know how to set the uniq id of this instance, 
    // Check if we have any special thing we need to set in xmlBlaster
    // that make different instance not look like each other
    public String myName ="Blaster";

    private PrintWriter logWriter = null;

    private PseudoUserPool userPool = new PseudoUserPool();

    private BlasterLogger logger;
    
    public BlasterManagedConnectionFactory() throws ResourceException{
        // Start logger, will be turned of by default
        logger = new BlasterLogger();
    }
    
    /**
     * Create a "non managed" connection factory. No appserver involved
     */
    public Object createConnectionFactory() throws ResourceException {
        return new BlasterConnectionFactoryImpl(this, null);
    }

    /**
     * Create a ConnectionFactory with appserver hook
     */ 
    public Object createConnectionFactory(ConnectionManager cxManager)
        throws ResourceException {
        
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
     * The driver to use: IOR | RMI
     
     * Have to verify the others to. Don't forget to configure the server.
     */
    public void setClientProtocol(String arg) throws IllegalStateException {
        try {
            XmlBlasterProperty.set("client.protocol", arg);
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
        return XmlBlasterProperty.get("client.protocol", (String)null);
    }

    /**
       Set the rmi hostname. Only when driver RMI.
     */
    public void setRmiHostname(String arg) throws IllegalStateException {
        try {
            XmlBlasterProperty.set("rmi.hostname", arg);
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
        return XmlBlasterProperty.get("rmi.hostname", (String)null);
    }

    /**
       Set the rmi registry port. Only when driver RMI.
     */
    public void setRmiRegistryPort(String arg) throws IllegalStateException {
        try {
            XmlBlasterProperty.set("rmi.registryPort", arg);
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
        return XmlBlasterProperty.get("rmi.registryPort", (String)null);
    }


    /**
       Set the rmi registry port. Only when driver RMI.
     */
    public void setRmiAuthserverUrl(String arg) throws IllegalStateException {
        try {
            XmlBlasterProperty.set("rmi.AuthServer.url", arg);
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
        return XmlBlasterProperty.get("rmi.AuthServer.url", (String)null);
    }

    /**
       Set the ior string. Only when driver IOR
     */
    public void setIor(String arg) throws IllegalStateException {
        try {
            XmlBlasterProperty.set("ior", arg);
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
        return XmlBlasterProperty.get("ior", (String)null);
    }

     /**
       Set the ior string through a file. Only when driver IOR
     */
    public void setIorFile(String arg) throws IllegalStateException {
        try {
            XmlBlasterProperty.set("ior.file", arg);
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
            XmlBlasterProperty.set("hostname", arg);
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
        return XmlBlasterProperty.get("hostname", (String)null);
    }

    /**
       Set port where the internal xmlBlaster-http server publishes its Ior. Only when driver IOR
     */
    public void setIorPort(String arg) throws IllegalStateException {
        try {
            XmlBlasterProperty.set("port", arg);
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
        return XmlBlasterProperty.get("port", (String)null);
    }
    
    /**
     * Null if not
     */
    public String getIorFile() {
        return XmlBlasterProperty.get("ior.file", (String)null);
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

    //--- Api betwen mcf and mc
    PseudoUserPool getUserPool() {
        return userPool;
    }
} // BlasterManagedConnectionFactory




