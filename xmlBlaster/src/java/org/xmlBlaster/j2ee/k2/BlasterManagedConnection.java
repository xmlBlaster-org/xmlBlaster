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


import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.protocol.ConnectionException;

import org.xmlBlaster.j2ee.k2.client.BlasterConnection;

/**
   The way I have interpreted the spec it is possible for one ManagedConection
 to hold one physical connection and no more.

 This might not be totaly spec compliant, since it does not support
 connection sharing. However, according to a mail on the Connector list
 from the lead spec writer, one way to be spec compliant without support
 for connection sharing is throwing an exception in getConnection if
 there already is one active. This will also happen in asociatConnection.
 This is the way it is done here.
 
 Here are some old notes:
 
   Nop, this was wrong. The spec says (a) that an mc may represent *one*
   physical connection to the resource, but (b) that an mc must be able
   to creat many connections without invalidating the one that it already
   have created. But that if one inactive is use, it may throw an exception.

   I thing wed better not allow for reauthentication!!!

   I thinkt we should use this stragey:

   If the appserver is stupid enought to call an acticated mc several times,
   it will not get a real physical connection the backend, but one and the
   same he left to other. WE WILL HAVE TO FIX SYNCHRONIZATION!

   So here is latest deal. 
   - An mc have one pysical connection: XmlBlasterConnetion
   - It may have one or more application wrappers around that connection, wich
   must be synchronized somehow
   - As long as we have a closed connection in standby we leav that out,
   but we do not ever keep more that one in stand by. A connection will go
   into standby if when close() is called on it and the standby entry is 
   empty (do have have to use Slot here?)
 *
 * It is also up to the impl if it is able to reautenticate.
 *
 * A special twist with xmlBlaster is that to be able to have many
 * connection for one user, one have to use a trick: to actually use
 * several users. Here each logical user will be mapped to user_n.
 *
 *
 * Created: Fri Jan 26 21:03:54 2001
 *
 * @author 
 * @version
 */

public class BlasterManagedConnection implements ManagedConnection {
    BlasterManagedConnectionFactory mcf;
    String user;
    String pwd;
    String pseudoUser;
    XmlBlasterConnection physicalPipe;
    PrintWriter logWriter;
    BlasterConnectionImpl handle = null;
    boolean isDestroyed = false;
    boolean closed = false;
    Vector listeners = new Vector();

    public BlasterManagedConnection(BlasterManagedConnectionFactory mcf,
                                    String user, 
                                    String pwd) throws ResourceException{
        this.mcf = mcf;
        this.user = user;
        this.pwd = pwd;
        
        try {
            /*
              Some params:
              -client.protocol RMI | IOR | XML-RPC
              

              RMI:
              rmi.hostname
              rmi.registryPort
              rmi.AuthServer.url

              Memo: for RMI server:
             -Djava.rmi.server.codebase=file:///${XMLBLASTER_HOME}/classes/  
             -Djava.security.policy=${XMLBLASTER_HOME}/config/xmlBlaster.policy 
             -Djava.rmi.server.hostname=hostname.domainname
             

              CORBA:
              -ior OR string is directly given
              -iorFile IOR string is given through a file</li>
              -iorHost hostName or IP where xmlBlaster is running</li>
              -iorPort where the internal xmlBlaster-http server publishes its Ior

              from/to system:
              -org.omg.CORBA.ORBClass=org.jacorb.orb.ORB
              -org.omg.CORBA.ORBSingletonClass=org.jacorb.orb.ORBSingleton

             */

            // Test with env
            /*
            String rmiEnv[] = new String[] {"-client.protocol","RMI"};
            String orbEnv[] = new String[] {"-client.protocol","IOR","-org.omg.CORBA.ORBClass","org.jacorb.orb.ORB","-org.omg.CORBA.ORBSingletonClass","org.jacorb.orb.ORBSingleton","-iorHost","151.177.109.74"};
            */
            
        // Set up physical pipe
            // physicalPipe = new XmlBlasterConnection(orbEnv);
            physicalPipe = new XmlBlasterConnection();
            System.out.println("Physical pipe: " + physicalPipe + " set up");
        }catch(XmlBlasterException ex) {
            throw new CommException("Could not create connection: " + ex);
        }
        
    }

    //---- ManagedConnection ----
    /**
     Get the physical connection handler.
 
     This bummer will be called in two situations. 
     1. When a new mc has bean created and a connection is needed
     2. When an mc has been fetched from the pool (returned in match*)
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

        // Check if we have or might produce a valid handle
        if (handle != null && !closed){
            // We already have a handle, but it is active
            throw new IllegalStateException("Connection sharing not supported");
        }else if (handle == null) {
            // Create a new one
            handle = new BlasterConnectionImpl(this);
            // login to physical connection
            doLogin();
            closed = false;
        } else if (handle != null && closed) {
            // Reactivate
            handle.open();
        } else {
            // Here we shoudl never be
            throw new IllegalStateException("Hoops, how did we end up here. Physcial pipe is probabky null");
        }
        return handle;
    }
    
    /**
     * Destroy the physical connection
     */
    public void destroy() throws ResourceException {
        if (isDestroyed) return;
        isDestroyed = true;
        //
        // Destroy handle
        handle.destroy();

        // Clean the used pseudouser
        releasePseudoUser();
        // Try logout first
        physicalPipe.logout();
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
        handle.cleanup();
        
    }


    /**
     * Move a handler from one mc to this one
     */ 
    
    public void associateConnection(Object connection)
        throws ResourceException {

        if(!isDestroyed &&
           handle == null &&
           connection instanceof BlasterConnectionImpl) {
            BlasterConnectionImpl h = (BlasterConnectionImpl) connection;
            h.setBlasterManagedConnection(this);
            handle = h;
        }else {
            throw new IllegalStateException("ManagedConnection in an illegal state");
        }

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
        handle = null;
        closed = true;
    }


    XmlBlasterConnection getBlasterConnection() throws  XmlBlasterException{
        return physicalPipe;
    }
    /**
     * This is used both internaly and by handles. We do  lot of sanity checks
     * here (failover ;-) Move to failoverGetConnection
     */
    XmlBlasterConnection getFailoverBlasterConnection()throws XmlBlasterException {
        // Do some checks first
        /* These are not needed here, keep for the sake of memmory ;-)
        boolean doLogin = false;
        if (pysicalPipe == null) {
            pysicalPipe = new XmlBlasterConnection();
            
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

    // --- internal helper methods ----

    private void doLogin() throws CommException {
        // Every time we login we map a user to a pseudo-user
            pseudoUser = getPseudoUser();
            try {
                System.out.println("Physical pipe: " + physicalPipe);
                physicalPipe.login(
                                   // This is a pseudouser, user-n
                                   pseudoUser,
                                   pwd,
                                   // No callback allowed for now, use message
                                   // driven beans
                                   null);
            }catch(XmlBlasterException ex) {
                throw new CommException("Could not login : " +ex);
            }
            
    }

    private String getPseudoUser() {
        if (pseudoUser == null) 
            pseudoUser = mcf.getUserPool().popPseudoUser(user);
        return pseudoUser;
    
    }

    private void releasePseudoUser() {
        mcf.getUserPool().push(user,pseudoUser);
    }
} // BlasterManagedConnection








