/*------------------------------------------------------------------------------
Name:      Authenticate.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Handling the Client data
           $Revision: 1.2 $
           $Date: 1999/11/14 21:53:18 $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.serverIdl.ServerImpl;
import org.xmlBlaster.serverIdl.ServerPOATie;
import org.xmlBlaster.serverIdl.MessageUnit;
import org.xmlBlaster.authenticateIdl.AuthServerImpl;
import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.clientIdl.BlasterCallback;
import java.util.*;

import org.omg.PortableServer.*;

/**
 * Authenticate
 */
public class Authenticate
{
   final private String ME = "Authenticate";

   private static Authenticate authenticate = null; // Singleton pattern

   private AuthServerImpl authServerImpl;

   final private Map clientInfoMap = Collections.synchronizedMap(new HashMap());

   private POA xmlBlasterPOA; // We use our own, customized POA

   private org.omg.CORBA.ORB orb;

   private ServerPOATie xmlBlasterServant;  // extends org.omg.PortableServer.Servant


   /**
    * Singleton access method
    */
   public static Authenticate getInstance(AuthServerImpl authServerImpl)
   {
      synchronized (Authenticate.class)
      {
         if (authenticate == null) {
            authenticate = new Authenticate(authServerImpl);
         }
      }
      return authenticate;
   }


   /**
    * Singleton access method
    */
   public static Authenticate getInstance()
   {
      synchronized (Authenticate.class)
      {
         if (authenticate == null) {
            Log.panic("Authenticate", "Wrong constructor for instantiation");
         }
      }
      return authenticate;
   }


   /**
    * private Constructor for Singleton Pattern
    */
   private Authenticate(AuthServerImpl authServerImpl)
   {
      this.authServerImpl = authServerImpl;

      if (Log.CALLS) Log.trace(ME, "Entering constructor");

      String myPOA_name = "xmlBlaster-POA"; //  This specialized POA controlles the xmlBlaster server

      orb = authServerImpl.getOrb();

      try {
         org.omg.PortableServer.POA parent = 
             org.omg.PortableServer.POAHelper.narrow(orb.resolve_initial_references("RootPOA"));

         // Create a customized POA:
         // - Allows a single servant for multiple clients
         // - Allows one thread per request (per invocation of a server method)
         // - Allows to recognize the calling client (thru one IOR per client)
         //   so the clients do not need to send a sessionId as a method parameter
         // - Allows thousands of clients simultaneously, as there is only one servant
         org.omg.CORBA.Policy [] policies = new org.omg.CORBA.Policy[2];
         policies[0] = parent.create_request_processing_policy(RequestProcessingPolicyValue.USE_DEFAULT_SERVANT);
         policies[1] = parent.create_id_uniqueness_policy(IdUniquenessPolicyValue.MULTIPLE_ID);
         // policies[] = parent.create_id_assignment_policy(IdAssignmentPolicyValue.USER_ID);
         // policies[] = parent.create_lifespan_policy(LifespanPolicyValue.PERSISTENT);
         xmlBlasterPOA = parent.create_POA(myPOA_name, parent.the_POAManager(), policies);
         for (int i=0; i<policies.length; i++) policies[i].destroy();

         // This servant handles all requests:
         // USING TIE:
         xmlBlasterServant = new ServerPOATie(new ServerImpl(orb, this));
         // NOT TIE:
         // xmlBlasterServant = new ServerImpl(orb);

         //xmlBlasterPOA.set_servant(xmlBlasterServant);
         //org.omg.CORBA.Object o = xmlBlasterPOA.servant_to_reference(xmlBlasterServant);
         byte[] default_oid = xmlBlasterPOA.activate_object(xmlBlasterServant);
      }
      catch ( Exception e ) {
         e.printStackTrace();
         Log.error(ME, e.toString());
      }

      if (Log.CALLS) Log.trace(ME, "Leaving constructor");
   }


   /**
    * Authentication of a client
    * @param cb The Callback interface of the client
    * @return a unique sessionId for the client, to be used in following calls
    */
   public String login(String loginName, String passwd,
                       BlasterCallback callback,
                       String xmlQoS_literal, String callbackIOR) throws XmlBlasterException
   {

      String sessionId;
      org.omg.CORBA.Object objRef;
      try {
         byte[] oid = xmlBlasterPOA.activate_object(xmlBlasterServant);
         //byte[] oid = xmlBlasterPOA.servant_to_id(xmlBlasterServant);
         Log.info(ME, "New Active Object Map ID=" + oid + " for client " + loginName);
         objRef = xmlBlasterPOA.id_to_reference(oid);
         sessionId = orb.object_to_string(objRef);
         Log.info(ME, "New Active Object Map IOR=" + sessionId);
      } catch ( Exception e ) {
         e.printStackTrace();
         Log.error(ME, e.toString());
         throw new XmlBlasterException(ME+"Unknown", "login failed: " + e.toString());
      }

      XmlQoSClient xmlQoS = new XmlQoSClient(xmlQoS_literal);
      ClientInfo clientInfo = new ClientInfo(sessionId, loginName, passwd, callback, callbackIOR, xmlQoS);
      synchronized(clientInfoMap) {
         clientInfoMap.put(clientInfo.getUniqueKey(), clientInfo);
      }
      return sessionId;  // !!!!!
   }


   /**
    * Logout of a client
    * @param the unique sessionId for the client
    */
   public void logout(String sessionId) throws XmlBlasterException
   {
      synchronized(clientInfoMap) {
         // byte[] oid = ??
         // xmlBlasterPOA.deactivate_object(oid);  // !!!
         Object obj = clientInfoMap.remove(sessionId);
         if (obj == null) {
            throw new XmlBlasterException(ME+"Unknown", "Sorry, you are not known, no logout");
         }
         obj = null;
      }
   }


   public ClientInfo check(String sessionId) throws XmlBlasterException
   {
      synchronized(clientInfoMap) {
         Object obj = clientInfoMap.get(sessionId);
         if (obj == null) {
            throw new XmlBlasterException(ME+"AccessDenied", "Sorry, sessionId is invalid");
         }
         return (ClientInfo)obj;
      }
   }
}
