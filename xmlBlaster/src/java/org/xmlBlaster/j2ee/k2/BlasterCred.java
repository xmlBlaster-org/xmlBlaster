/*
 * Copyright (c) 2001 Peter Antman, TiM <peter.antman@tim.se>
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
 * Carries user credentials inside this ra.
 *
 * @author Peter Antman
 * @version $Revision: 1.3 $
 */

public class BlasterCred  {
   /**
    * The user name, from either the ConnectionRequestInfo, the Subject or the default configuration.
    */
    public String name;
   /**
    * The password, from either the ConnectionRequestInfo, the Subject or the default configuration.
    */
    public String pwd;

    public BlasterCred() {
        
    }
   /**
    * Get our own simple cred.
    *
    * <p>The search order is this: info, subject, default config from mcf. If no user is found in any of these a SecurityException is thrown.
    */
    public static BlasterCred getBlasterCred(ManagedConnectionFactory mcf,
                                             Subject subject, 
                                             ConnectionRequestInfo info) 
        throws SecurityException {

       String defaultUser = ((BlasterManagedConnectionFactory)mcf).getUserName();
       String password = ((BlasterManagedConnectionFactory)mcf).getPassword();
       
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
          if(pwdc != null) {
             bc.name = pwdc.getUserName();
             bc.pwd = new String(pwdc.getPassword());
          }

       }

       // Check if we have  username at all
       // If both derived and default is empty we are fucked
       if (bc.name == null && defaultUser == null)
          throw new SecurityException("No Subject or ConnectionRequestInfo set, not even default values available. Could not get credentials");
       else if (bc.name == null) {
          // We do know tha default user is not null if we are here
          bc.name = defaultUser;
          bc.pwd = password;

       }
       // Either we had a name in bc or in defaultUser ;-)
       return bc;
    }
} // BlasterCred







