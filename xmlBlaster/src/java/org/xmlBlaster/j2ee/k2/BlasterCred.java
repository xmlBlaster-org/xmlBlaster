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

import java.util.Set;
import java.util.Iterator;

import javax.security.auth.Subject;

import javax.resource.spi.ManagedConnectionFactory;
import javax.resource.spi.SecurityException;
import javax.resource.spi.ConnectionRequestInfo;

import javax.resource.spi.security.PasswordCredential;
/**
 * BlasterCred.java
 *
 *
 * Created: Fri Jan 26 21:04:58 2001
 *
 * @author 
 * @version
 */

public class BlasterCred  {
    public String name;
    public String pwd;

    public BlasterCred() {
	
    }
        /**
     * Get our own simple cred
     */
    public static BlasterCred getBlasterCred(ManagedConnectionFactory mcf,
					     Subject subject, 
					     ConnectionRequestInfo info) 
	throws SecurityException {

	BlasterCred bc = new BlasterCred();
	if (subject == null && info !=null ) {
	    // Credentials specifyed on connection request
	    bc.name = ((BlasterConnectionRequestInfo)info).getUserName();
	    bc.pwd = ((BlasterConnectionRequestInfo)info).getPassword();
	} else if (subject != null) {
	    // Credentials from appserver
	    Set creds = 
		subject.getPrivateCredentials(PasswordCredential.class);
	    PasswordCredential pwdc = null;
	    Iterator credentials = creds.iterator();
	    while(credentials.hasNext()) {
		PasswordCredential curCred = 
		    (PasswordCredential) credentials.next();
		if (curCred.getManagedConnectionFactory().equals(mcf)) {
		    pwdc = curCred;
		    break;
		}
	    }
	    if(pwdc == null) {
		// No hit - we do need creds
		throw new SecurityException("No Passwdord credentials found");
	    }
	    bc.name = pwdc.getUserName();
	    bc.pwd = new String(pwdc.getPassword());
	} else {
	    throw new SecurityException("No Subject or ConnectionRequestInfo set, could not get credentials");
	}
	return bc;
    }
} // BlasterCred







