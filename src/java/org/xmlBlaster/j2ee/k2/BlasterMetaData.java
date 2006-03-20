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
import javax.resource.spi.ManagedConnectionMetaData;
/**
 * BlasterMetaData.java
 *
 *
 * Created: Mon Jan 29 22:09:23 2001
 */

public class BlasterMetaData  implements ManagedConnectionMetaData{
    private BlasterManagedConnection mc;
    
    public BlasterMetaData(BlasterManagedConnection mc) {
        this.mc = mc;
    }
    public String getEISProductName() throws ResourceException {
        return "XmlBlaster";
    }

    public String getEISProductVersion() throws ResourceException {
        return "0.79";//Is this possible to get another way
    }

    public int getMaxConnections() throws ResourceException {
        // Dont know how to get this, from XmlBlaster, we
        // set it to unlimited
        return 0;
    }
    
    public String getUserName() throws ResourceException {
        return mc.getUserName();
    }
} // BlasterMetaData
