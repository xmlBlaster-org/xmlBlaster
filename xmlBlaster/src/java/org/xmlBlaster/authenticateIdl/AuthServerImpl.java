/*------------------------------------------------------------------------------
Name:      AuthServerImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Implementing the CORBA xmlBlaster-server interface
           $Revision: 1.4 $  $Date: 1999/11/15 14:47:54 $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authenticateIdl;

//import org.xmlBlaster.serverIdl.*;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.clientIdl.BlasterCallback;
import org.xmlBlaster.engine.XmlQoS;


/**
 * Implements the xmlBlaster server CORBA Interface. 
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
      if (Log.CALLS) Log.trace(ME, "Entering constructor with ORB argument");
      this.orb = orb;
      this.authenticate = Authenticate.getInstance(this);
   }


   public org.omg.CORBA.ORB getOrb()
   {
      return orb;
   }

   /**
    * Construct a transient object.
    */
   public AuthServerImpl()
   {
      super();
      if (Log.CALLS) Log.trace(ME, "Entering constructor without ORB argument");
      this.authenticate = Authenticate.getInstance(this);
   }


   /**
    * Authentication of a client
    * @param cb The Callback interface of the client
    * @return The Server reference
    */
   public org.xmlBlaster.serverIdl.Server login(String loginName, String passwd,
                       BlasterCallback cb,
                       String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.trace(ME, "Entering login(loginName=" + loginName/* + ", qos=" + qos_literal + ")"*/);
      return authenticate.login(loginName, passwd, cb, qos_literal, orb.object_to_string(cb));
   }


   /**
    * Logout of a client
    */
   public void logout() throws XmlBlasterException
   {
      if (Log.CALLS) Log.trace(ME, "Entering logout()");
      authenticate.logout();
   }


}

