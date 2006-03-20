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

import javax.resource.spi.ConnectionRequestInfo;

/**
 * BlasterConnectionRequestInfo.java
 *
 *
 * Created: Fri Jan 26 20:55:32 2001
 */

public class BlasterConnectionRequestInfo implements ConnectionRequestInfo {
    private String userName;
    private String password;
    public BlasterConnectionRequestInfo(String userName, String passwd) {
        this.userName = userName;
        this.password =passwd;
    }
    
    public String getUserName() {
        return userName;
    }
    
    public String getPassword() {
        return password;
    }
    
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj instanceof BlasterConnectionRequestInfo) {
            BlasterConnectionRequestInfo you = (BlasterConnectionRequestInfo) obj;
            return (isEqual(this.userName, you.getUserName()) &&
                    isEqual(this.password, you.getPassword()));
        } else {
            return false;
        }
    }
    
    public int hashCode() {
        String result = "" + userName + password;
        return result.hashCode();
    }
    
    private boolean isEqual(Object o1, Object o2) {
        if (o1 == null) {
            return (o2 == null);
        } else {
            return o1.equals(o2);
        }
    }
} // BlasterConnectionRequestInfo
