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

import java.io.Serializable;

import javax.naming.Reference;

import javax.resource.Referenceable;
import javax.resource.ResourceException;
import javax.resource.spi.ManagedConnectionFactory;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;

import org.xmlBlaster.j2ee.k2.client.*;
/**
 * BlasterConnectionFactoryImpl.java
 *
 *
 * Created: Fri Jan 26 13:28:54 2001
 *
 * @author Peter Antman
 */

public class BlasterConnectionFactoryImpl  implements BlasterConnectionFactory,
Serializable, Referenceable{
    
    private Reference reference;

    /**
     * Blasters own factory
     */
    private ManagedConnectionFactory mcf;

    /**
     * Hook to the appserver
     */
    private ConnectionManager cm;
    
    public BlasterConnectionFactoryImpl(ManagedConnectionFactory mcf,
                          ConnectionManager cm) {
        this.mcf = mcf;
        this.cm = cm;
        if (cm == null) {
            // This is standalone usage, no appserver
            this.cm = new BlasterConnectionManager();
        } else {
            this.cm = cm;
        }
    }
    /**
     * Container managed login
     */
    public BlasterConnection getConnection() throws ResourceException{
        return (BlasterConnection) cm.allocateConnection(mcf, null);
    }
    
    /**
     * Client managed login
     */
    public BlasterConnection getConnection(String userName, String password) throws ResourceException{
        ConnectionRequestInfo info =
            new BlasterConnectionRequestInfo(userName, password);
        Object o = cm.allocateConnection(mcf, info);
        System.out.println(o);
        return  (BlasterConnection)o;
        //return (BlasterConnection) cm.allocateConnection(mcf, info);
            
    }

    public void setReference(Reference reference) {
        this.reference = reference;
    }

    public Reference getReference() {
        return reference;
    }
    
    
} // BlasterConnectionFactory
