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
package org.xmlBlaster.j2ee.k2.client;

import javax.resource.ResourceException;
/**
 * BlasterConnectionFactory.java
 *
 *
 * Created: Fri Jan 26 13:28:54 2001
 *
 * @author Peter Antman
 * @version
 */

public interface BlasterConnectionFactory  {
    public BlasterConnection getConnection()throws ResourceException;
    public BlasterConnection getConnection(String username, String password)throws ResourceException;
      
    
} // BlasterConnectionFactory
