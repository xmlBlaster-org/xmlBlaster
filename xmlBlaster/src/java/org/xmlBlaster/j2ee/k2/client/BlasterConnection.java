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
package org.xmlBlaster.j2ee.k2.client;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;

/**
 * K2 Connection interface for xmlBlaster. 

 <p>Only a subset of I_XmlBlasterAccess. Be aware that this is a connection 
 interface whose underlying physical pipe typically is pooled.</p>
 *
 */

public interface BlasterConnection  {
   /**
    * The the preconfigured global of the connection, good to have to be able to
    * create Qos and keys for example.
    * Ought to be readonly!.
    */
   public Global getGlobal() throws XmlBlasterException;
    
    /**
       Don't know yet if we really should allow asyncronous stuff here
       
       public SubscribeReturnQos subscribe(String xmlKey, String qos) 
    throws XmlBlasterException;
        
        public void unSubscribe(String xmlKey, String qos) 
    throws XmlBlasterException;
    */

    /**
     * Fetch a message from server
     */
    public MsgUnit[] get(String xmlKey, String qos) 
        throws XmlBlasterException;
    
    /**
     * Publish one message
     */
    public PublishReturnQos publish(MsgUnit msgUnit)
       throws XmlBlasterException;
    
    /**
     * Publish one or more message
     */
    public PublishReturnQos[] publishArr(MsgUnit[] msgUnitArr) 
       throws XmlBlasterException;
    
    /**
     * Erase message(s), I think
     */
    public EraseReturnQos[] erase(String xmlKey,String qos) 
       throws XmlBlasterException;
    
    /**
     * Close the connection. After this the client may not use the connection
     again, but should get a new from the ConnectionFactory.
     */
    public void close()
       throws XmlBlasterException;
    
} // BlasterConnection
