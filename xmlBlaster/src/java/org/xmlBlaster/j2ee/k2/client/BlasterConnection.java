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
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.client.PublishRetQos;
import org.xmlBlaster.client.SubscribeRetQos;
import org.xmlBlaster.client.EraseRetQos;

/**
 * K2 Connection interface for xmlBlaster. 

 Only a subset of XmlBlasterConnection. Be aware that this is a connection 
 interface whose underlying physical pipe typically is pooled.
 *
 *
 * Created: Sat Jan 27 20:22:32 2001
 */

public interface BlasterConnection  {
    
    /**
       Don't know yet if we really should allow asyncronous stuff here
       
       public SubscribeRetQos subscribe(String xmlKey, String qos) 
    throws XmlBlasterException;
        
        public void unSubscribe(String xmlKey, String qos) 
    throws XmlBlasterException;
    */

    /**
     * Fetch a message from server
     */
    public MessageUnit[] get(String xmlKey, String qos) 
        throws XmlBlasterException;
    
    /**
     * Publish one message
     */
    public PublishRetQos publish(MessageUnit msgUnit)
       throws XmlBlasterException;
    
    /**
     * Publish one or more message
     */
    public PublishRetQos[] publishArr(MessageUnit[] msgUnitArr) 
       throws XmlBlasterException;
    
    /**
     * Erase message(s), I think
     */
    public EraseRetQos[] erase(String xmlKey,String qos) 
       throws XmlBlasterException;
    
    /**
     * Close the connection. After this the client may not use the connection
     again, but should get a new from the ConnectionFactory.
     */
    public void close()
       throws XmlBlasterException;
    
} // BlasterConnection
