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

import javax.resource.ResourceException;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;

import org.xmlBlaster.j2ee.k2.client.BlasterConnection;

/**
 * An implementing handler to a physical connection. One or more handles may
  be sychronized over a single physical connection.
 *
 *
 * Created: Sat Jan 27 20:34:17 2001
 */

public class BlasterConnectionImpl implements BlasterConnection {
    private BlasterManagedConnection mc = null;
    private boolean closed = true;
    
    public BlasterConnectionImpl(BlasterManagedConnection mc) {
        this.mc = mc;
        closed = false;
    }

    //---- BlasterConnection---
    // Howto do with exceptions, is all exceptions a comm execption in these
    // methods, I think so
   /**
    * Get Global from connection
    */
   public Global getGlobal() throws XmlBlasterException {
      checkSanity();
      return mc.getGlobal();
      
   }

    /**
     * Fetch a message from server
     */
    public MsgUnit[] get(String xmlKey, String qos) 
        throws XmlBlasterException {
        checkSanity();
        MsgUnit[] ret = null;
        try {
            ret = mc.getBlasterConnection().get(xmlKey,qos);
        }catch(XmlBlasterException ex) {
            //Try one more time
            try {
                ret = mc.getFailoverBlasterConnection().get(xmlKey,qos);
            }catch(XmlBlasterException exx) {
                //CommException ce = new CommException("Error in blaster communication");
                //ce.setLinkedException(ex);
                mc.handleError(this,exx);

                throw exx;
            }
        }
        return ret;
    }
    
    /**
     * Publish one message
     */
    public PublishReturnQos publish(MsgUnit msgUnit)
        throws XmlBlasterException {
        checkSanity();
        PublishReturnQos ret = null;
        try {
            ret = mc.getBlasterConnection().publish(msgUnit);
        }catch(XmlBlasterException ex) {
                    //Try one more time
            try {
                ret = mc.getFailoverBlasterConnection().publish(msgUnit);
            }catch(XmlBlasterException exx) {
                //CommException ce = new CommException("Error in blaster communication");
                //ce.setLinkedException(ex);
                mc.handleError(this,exx);

                throw exx;
            }
        }
        return ret;
    }
    
    /**
     * Publish one or more message
     */
    public PublishReturnQos[] publishArr(MsgUnit[] msgUnitArr) 
        throws  XmlBlasterException{
        checkSanity();
        PublishReturnQos[] ret = null;
        try {
            ret = mc.getBlasterConnection().publishArr(msgUnitArr);
        }catch(XmlBlasterException ex) {
    //Try one more time
            try {
                ret = mc.getFailoverBlasterConnection().publishArr(msgUnitArr);
            }catch(XmlBlasterException exx) {
                //CommException ce = new CommException("Error in blaster communication");
                //ce.setLinkedException(ex);
                mc.handleError(this,exx);

                throw exx;
            }
        }
        return ret;
    }
    
    /**
     * Erase message(s), I think
     */
    public EraseReturnQos[] erase(String xmlKey,String qos) 
        throws XmlBlasterException {
        checkSanity();
        EraseReturnQos[] ret = null;
        try {
            ret = mc.getBlasterConnection().erase(xmlKey,qos);
        }catch(XmlBlasterException ex) {
            //Try one more time
            try {
                ret = mc.getFailoverBlasterConnection().erase(xmlKey,qos);
            }catch(XmlBlasterException exx) {
                //CommException ce = new CommException("Error in blaster communication");
                //ce.setLinkedException(ex);
                mc.handleError(this,exx);
                
                throw exx;
            }
        }
        return ret;
    }
    
    /**
     * Close the connection. After this the client may not use the connection
     again, but should get a new from the ConnectionFactory.
     */
    public void close() throws XmlBlasterException {
        if (mc == null)
            throw new XmlBlasterException(null,"Connection invalid, no ManagedConnection available");
        // Listener stuff!!
        closed = true;
        mc.handleClose(this);
    }

    // ---- API between handler and mc
    
    void open() {
        closed = false;
    }
    
    void destroy() {
        closed = true;
        mc = null;
    }

    void cleanup() {
        closed = true;
    }
    /**
     * Set an mc. This will handle two cases. 
     1. When an mc want to retain a closed or cleaned upp handle
     2. When it is reasociated with a new mc
     */
    void setBlasterManagedConnection(BlasterManagedConnection mc) {
        if (this.mc !=null) {
            this.mc.removeHandle(this);
        }
        this.mc = mc;   
    }


    // ---- helper methods ----
    private void checkSanity() throws XmlBlasterException{
        if (mc == null)
            throw new XmlBlasterException(null,"Connection invalid, no ManagedConnection available");
        // Nothing to logg to
    }



 
    
} // BlasterConnectionImpl





