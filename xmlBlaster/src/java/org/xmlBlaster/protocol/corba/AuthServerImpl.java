/*------------------------------------------------------------------------------
Name:      AuthServerImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementing the CORBA xmlBlaster-server interface
Version:   $Id: AuthServerImpl.java,v 1.4 2000/05/16 20:57:38 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.corba;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.StopWatch;
import org.xmlBlaster.protocol.corba.authenticateIdl.*;
import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallback;


/**
 * Implements the xmlBlaster AuthServer CORBA Interface.
 * <br>
 * All real work is directly delegated to org.xmlBlaster.authentication.Authenticate
 */
//public class AuthServerImpl extends ServerPOA {            // inheritance approach
public class AuthServerImpl implements AuthServerOperations {    // tie approach

   private final String ME = "AuthServerImpl";
   private org.omg.CORBA.ORB orb;
   private Authenticate authenticate;

   /**
    * Construct a persistently named object.
    */
   public AuthServerImpl(org.omg.CORBA.ORB orb)
   {
      if (Log.CALLS) Log.calls(ME, "Entering constructor with ORB argument");
      this.orb = orb;
      this.authenticate = new Authenticate(this);
   }


   public org.omg.CORBA.ORB getOrb()
   {
      return orb;
   }


   public Authenticate getAuthenticationService()
   {
      return authenticate;
   }


   /**
    * Authentication of a client.
    * @return The Server reference
    */
   public org.xmlBlaster.protocol.corba.serverIdl.Server login(String loginName, String passwd,
                       String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering login(loginName=" + loginName/* + ", qos=" + qos_literal */ + ")");

      if (loginName==null || passwd==null || qos_literal==null) {
         Log.error(ME+"InvalidArguments", "login failed: please use no null arguments for login()");
         throw new XmlBlasterException(ME+"InvalidArguments", "login failed: please use no null arguments for login()");
      }

      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();

      org.xmlBlaster.protocol.corba.serverIdl.Server server =
            authenticate.login(loginName, passwd, qos_literal);

      if (Log.TIME) Log.time(ME, "Elapsed time in login()" + stop.nice());

      return server;
   }


   /**
    * Logout of a client.
    * @param xmlServer The handle you got from the login call
    */
   public void logout(org.xmlBlaster.protocol.corba.serverIdl.Server xmlServer) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering logout()");

      authenticate.logout(xmlServer);
   }


}

