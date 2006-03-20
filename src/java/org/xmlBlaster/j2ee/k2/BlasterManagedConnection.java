/*
 * Copyright (c) 2001 Peter Antman Tim <peter.antman@tim.se>
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

import java.util.Vector;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.io.PrintWriter;

import javax.security.auth.Subject;

import javax.transaction.xa.XAResource;

import javax.resource.ResourceException;
import javax.resource.NotSupportedException;
import javax.resource.spi.ManagedConnectionFactory;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ConnectionEventListener;
import javax.resource.spi.LocalTransaction;
import javax.resource.spi.ManagedConnectionMetaData;
import javax.resource.spi.CommException;
import javax.resource.spi.SecurityException;
import javax.resource.spi.IllegalStateException;
import javax.resource.spi.ConnectionEvent;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.j2ee.k2.client.BlasterConnection;

/**
   ManagedConnection for xmlBlaster.
   

   <p>The way I have interpreted the spec it is possible for one ManagedConection
 to hold one physical connection and no more.</p>

 <p>We now support connection sharing.</p>

 <p>This is the old note about it:
 This might not be totaly spec compliant, since it does not support
 connection sharing. However, according to a mail on the Connector list
 from the lead spec writer, one way to be spec compliant without support
 for connection sharing is throwing an exception in getConnection if
 there already is one active. This will also happen in asociatConnection.
 This is the way it is done here.
 
 Comment: this is NOT the way it is implemented int JBoss, therefore we
 need to suppport connection sharing!</p>

 *
 * <p>This mc now supports the session based login.</p>
 */

public class BlasterManagedConnection implements ManagedConnection {
   BlasterManagedConnectionFactory mcf;
   String user;
   String pwd;
   I_XmlBlasterAccess physicalPipe;
   PrintWriter logWriter;
   boolean isDestroyed = false;
   boolean closed = false;
   String me = null;
   Vector listeners = new Vector();
   Global clonedGlob = null;
   /** Holds all current  BlasterConnectionImpl handles. */
   private Set handles = Collections.synchronizedSet(new HashSet());
   

    public BlasterManagedConnection(BlasterManagedConnectionFactory mcf,
                                    String user, 
                                    String pwd) throws ResourceException{
        this.mcf = mcf;
        this.user = user;
        this.pwd = pwd;
            /*
              Some params:
              -dispatch/connection/protocol RMI | IOR | XMLRPC | SOCKET
              

              RMI:
              dispatch/clientside/plugin/rmi/hostname
              dispatch/connection/plugin/rmi/registryPort
              dispatch/connection/plugin/rmi/AuthServerUrl

              Memo: for RMI server:
             -Djava.rmi.server.codebase=file:///${XMLBLASTER_HOME}/classes/  
             -Djava.security.policy=${XMLBLASTER_HOME}/config/xmlBlaster.policy 
             -Djava.rmi.server.hostname=hostname.domainname
             

              CORBA:
              -dispatch/callback/plugin/ior/iorString OR string is directly given
              -dispatch/connection/plugin/ior/iorFile IOR string is given through a file</li>
              -bootstrapHostname host name or IP where xmlBlaster is running</li>
              -bootstrapPort where the internal xmlBlaster-http server publishes its Ior

              from/to system:
              -org.omg.CORBA.ORBClass=org.jacorb.orb.ORB
              -org.omg.CORBA.ORBSingletonClass=org.jacorb.orb.ORBSingleton

             */

            // Test with env
            /*
            String rmiEnv[] = new String[] {"-dispatch/connection/protocol","RMI"};
            String orbEnv[] = new String[] {"-dispatch/connection/protocol","IOR","-org.omg.CORBA.ORBClass","org.jacorb.orb.ORB","-org.omg.CORBA.ORBSingletonClass","org.jacorb.orb.ORBSingleton","-bootstrapHostname","151.177.109.74"};
            */
            
        // Set up physical pipe
            // physicalPipe = new I_XmlBlasterAccess(orbEnv);
        // From XmlBlasterAccess: You must use a cloned Global for each XmlBlasterAccess created, cloning is done in getConfig()
        clonedGlob = mcf.getConfig();
        physicalPipe = clonedGlob.getXmlBlasterAccess();
        System.out.println("Physical pipe: " + physicalPipe + " set up");
        doLogin();
    }

   public String toString() {
      return me;
   }

    //---- ManagedConnection ----
    /**
     *Get the physical connection handler.
     *
     * <p>This bummer will be called in two situations. 
     * <p>1. When a new mc has bean created and a connection is needed
     * <p>2. When an mc has been fetched from the pool (returned in match*)

     * <p>It may also be called multiple time without a cleanup, to support
     *    connection sharing.
     */
    public Object getConnection(Subject subject, 
                                ConnectionRequestInfo info) 
        throws ResourceException {

        // Check user first
        BlasterCred cred = BlasterCred.getBlasterCred(mcf,subject,info);

        // Should we throw on null user here?
        // Check cred, only the same user as original is allowed
        if (cred.name == null)
            throw new SecurityException("UserName not allowed to be null");
        if (user != null && !user.equals(cred.name) )
            throw new SecurityException("Password credentials not the same, reauthentication not allowed");
        
        // If we are here we may set the user if its null
        if (user == null)
            user = cred.name;
        
        if (isDestroyed) {
           throw new IllegalStateException("ManagedConnection already destroyd");
        }

              
      // Create a handle
      BlasterConnectionImpl handle = new BlasterConnectionImpl(this);
      handles.add(handle);
      return handle;
      
    }
   /**
    * Destroy all handles.
    *
    * @throws ResourceException    Failed to close one or more handles.
    */
   private void destroyHandles() throws ResourceException {
      Iterator iter = handles.iterator();
      
      while (iter.hasNext()) {
         ((BlasterConnectionImpl)iter.next()).destroy();
      }

      // clear the handles map
      handles.clear();
   }
    /**
     * Destroy the physical connection
     */
    public void destroy() throws ResourceException {
        if (isDestroyed) return;
        isDestroyed = true;

        // destory handles
        destroyHandles();
   
        // Try logout first
        //physicalPipe.logout();
        physicalPipe.disconnect(new DisconnectQos());
        physicalPipe = null;// Is this good?
    }
    

    /**
     * Cleans up the connection.

     * Not shure what to do here. The spec says: clean al client specific
     * states out, but keep the physical pipe up. 
     * This is not possible with xmlBlaster.
     *
     * One thing in spec is clear. A client may not use a connection 
     * that was cleaned!!
     *
     */
    public void cleanup() throws ResourceException {
        
        if(isDestroyed)
            throw new IllegalStateException("ManagedConnection already destroyd");
        // 
        closed = true;
        // destory handles      
        destroyHandles();
    }


    /**
     * Move a handler from one mc to this one
     */ 
    
   public void associateConnection(Object obj)
      throws ResourceException {
      if (!(obj instanceof BlasterConnectionImpl))
         throw new IllegalStateException("Cant call associateConnection with a handle that is not of type BlasterConnectionImp: " + obj.getClass().getName());
      
      if (isDestroyed)
         throw new IllegalStateException("ManagedConnection in an illegal state, is destroyed");
      
      BlasterConnectionImpl h = (BlasterConnectionImpl)obj;
      h.setBlasterManagedConnection(this);
      handles.add(h);
   }
   
   
   public void addConnectionEventListener(ConnectionEventListener listener) {
        listeners.addElement(listener);
    }


    public void removeConnectionEventListener(ConnectionEventListener listener) {
        
        listeners.removeElement(listener);
    }
    
    
    public XAResource getXAResource() throws ResourceException {
        throw new NotSupportedException("XA transaction not supported");
    }

    public LocalTransaction getLocalTransaction() throws ResourceException {
        throw new NotSupportedException("Local transaction not supported");
    }
    
    public ManagedConnectionMetaData getMetaData() throws ResourceException {
        if(isDestroyed)
            throw new IllegalStateException("ManagedConnection already destroyd");
        return new BlasterMetaData(this);
    }
    
    public void setLogWriter(PrintWriter out) throws ResourceException {
        this.logWriter = out;
    }

    public PrintWriter getLogWriter() throws ResourceException {
        return logWriter;
    }

    //---- Api between mc and handle
    void removeHandle(BlasterConnectionImpl handle) {
       handles.remove(handle);
       //handle = null;
       //closed = true;
    }


    I_XmlBlasterAccess getBlasterConnection() throws  XmlBlasterException{
        return physicalPipe;
    }
    /**
     * This is used both internaly and by handles. We do  lot of sanity checks
     * here (failover ;-) Move to failoverGetConnection
     */
    I_XmlBlasterAccess getFailoverBlasterConnection()throws XmlBlasterException {
        // Do some checks first
        /* These are not needed here, keep for the sake of memmory ;-)
        boolean doLogin = false;
        if (pysicalPipe == null) {
            pysicalPipe = new I_XmlBlasterAccess();
            
        }
        if(!pysicalPipe.isLoggedIn()) {
            doLogin = true;
        }
        */
        // OK if we where not logged in, do that now
        //if(doLogin) 
        //    doLogin();
        
        //Ping - for sanity check
        //try {
            // There is a bug in XmlBalaster here
        //    physicalPipe.ping();// FIXME - what the f-ck does this bugger do?
        //}catch(ConnectionException ex) {
            // Try a new round
        /*
            if (!physicalPipe.logout()) {
                // Invalidate this connection
                destroy();
                throw new XmlBlasterException("Could not logout, connection probaly down. Invalidating connection");
            }
            doLogin();
        }
        */
        return physicalPipe;
    }
    
    void handleClose(BlasterConnection impl) {
       closed = true;
        ConnectionEvent ev = new ConnectionEvent(this, ConnectionEvent.CONNECTION_CLOSED);
        ev.setConnectionHandle(impl);
        Vector list = (Vector) listeners.clone();
        int size = list.size();
        for (int i=0; i<size; i++) {
            ConnectionEventListener l = 
                (ConnectionEventListener) list.elementAt(i);
            l.connectionClosed(ev);
        }
    }

    void handleError(BlasterConnection impl, Exception ex) {
        ConnectionEvent ev = null;
        if (ex != null)
            ev = new ConnectionEvent(this, ConnectionEvent.CONNECTION_ERROR_OCCURRED, ex);
        else 
            ev = new ConnectionEvent(this, ConnectionEvent.CONNECTION_ERROR_OCCURRED);
        Vector list = (Vector) listeners.clone();
        int size = list.size();
        for (int i=0; i<size; i++) {
            ConnectionEventListener l = 
                (ConnectionEventListener) list.elementAt(i);
            l.connectionErrorOccurred(ev);
        }
    }
    // ---- for mcf
    String getUserName() {
        return user;
    }

    BlasterManagedConnectionFactory getManagedConnectionFactory() {
        return mcf;
    }

   Global getGlobal() {
      return clonedGlob;
   }

    // --- internal helper methods ----

   private void doLogin() throws CommException {
      try {
         ConnectQos qos = new ConnectQos(clonedGlob, user,pwd); 
         qos.setPtpAllowed(false);
         
         System.out.println("Physical pipe: " + physicalPipe+"/CQos:"+qos);
         
         ConnectReturnQos ret = physicalPipe.connect(qos,null);
         me = "BlasterManagedConnection/"+user+"/"+ret.getSecretSessionId();
         
      }catch(XmlBlasterException ex) {
         throw new CommException("Could not login : " +ex);
      }
      
   }
   
} // BlasterManagedConnection

