/*------------------------------------------------------------------------------
Name:      Authenticate.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Login for clients
           $Revision: 1.6 $
           $Date: 1999/11/16 18:16:24 $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.StopWatch;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.serverIdl.ServerHelper;
import org.xmlBlaster.serverIdl.ServerImpl;
import org.xmlBlaster.serverIdl.ServerPOATie;
import org.xmlBlaster.serverIdl.MessageUnit;
import org.xmlBlaster.authenticateIdl.AuthServerImpl;
import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.clientIdl.BlasterCallback;
import java.util.*;

import org.omg.PortableServer.*;

/**
 * Authenticate. 
 *
 * Authenticate a client via login<br>
 * The login method serves as a factory for a xmlBlaster.Server Reference
 */
public class Authenticate
{
   final private String ME = "Authenticate";

   private static Authenticate authenticate = null; // Singleton pattern

   private AuthServerImpl authServerImpl;

   final private Map clientInfoMap = Collections.synchronizedMap(new HashMap());

   private final String xmlBlasterPOA_name = "xmlBlaster-POA"; //  This specialized POA controlles the xmlBlaster server
   private POA xmlBlasterPOA;                                  // We use our own, customized POA

   private org.omg.CORBA.ORB orb;

   private org.omg.PortableServer.POA rootPOA;

   // USING TIE:
   // private ServerPOATie xmlBlasterServant;  // extends org.omg.PortableServer.Servant
   // NOT TIE
   private ServerImpl xmlBlasterServant;  // extends org.omg.PortableServer.Servant


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
    * private Constructor for Singleton Pattern
    *
    * Authenticate creates a single instance of the xmlBlaster.Server.
    * Clients need first to do a login, from where they get
    * an IOR which serves them, one thread for each request.<p>
    *
    * Every client has its own IOR, but in reality this IOR is mapped
    * to a single servant.<p>
    * This allows:<br>
    * - Identification of the client thru its unique IOR<br>
    * - Only a few threads are enough to serve many clients
    */
   private Authenticate(AuthServerImpl authServerImpl)
   {
      this.authServerImpl = authServerImpl;

      if (Log.CALLS) Log.trace(ME, "Entering constructor");

      orb = authServerImpl.getOrb();

      try {
         rootPOA = org.omg.PortableServer.POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
         POAManager poaMgr  = rootPOA.the_POAManager();


         // Create a customized POA:
         // - Allows a single servant for multiple clients
         // - Allows one thread per request (per invocation of a server method)
         // - Allows to recognize the calling client (thru one IOR per client)
         //   so the clients do not need to send a sessionId as a method parameter
         // - Allows thousands of clients simultaneously, as there is only one servant
         org.omg.CORBA.Policy [] policies = new org.omg.CORBA.Policy[2];
         policies[0] = rootPOA.create_request_processing_policy(RequestProcessingPolicyValue.USE_DEFAULT_SERVANT);
         policies[1] = rootPOA.create_id_uniqueness_policy(IdUniquenessPolicyValue.MULTIPLE_ID);
         // policies[] = rootPOA.create_id_assignment_policy(IdAssignmentPolicyValue.USER_ID);
         // policies[] = rootPOA.create_lifespan_policy(LifespanPolicyValue.PERSISTENT);
         xmlBlasterPOA = rootPOA.create_POA(xmlBlasterPOA_name, poaMgr, policies);
         for (int i=0; i<policies.length; i++) policies[i].destroy();

         // This single servant handles all requests (with the policies from above)

         // USING TIE:
         // xmlBlasterServant = new ServerPOATie(new ServerImpl(orb, this));
         // NOT TIE:
         xmlBlasterServant = new ServerImpl(orb, this);

         xmlBlasterPOA.set_servant(xmlBlasterServant); // set as default servant
         poaMgr.activate();
         
         // orb.run();
         // Log.info(ME, "Default Active Object Map ID=" + default_oid);
         if (Log.TRACE) Log.trace(ME, "Default xmlBlasterServant activated");
      }
      catch ( Exception e ) {
         e.printStackTrace();
         Log.error(ME, e.toString());
      }

      if (Log.CALLS) Log.trace(ME, "Leaving constructor");
   }


   /**
    * Authentication of a client
    *
    * @param cb The Callback interface of the client
    * @return The xmlBlaster.Server interface
    */
   public org.xmlBlaster.serverIdl.Server login(String loginName, String passwd,
                       BlasterCallback callback, String callbackIOR,
                       String xmlQoS_literal) throws XmlBlasterException
   {
      String uniqueClientKey;
      org.omg.CORBA.Object certificatedServerRef = null;

      try {
         // set up a association between the new created object reference (oid is sufficient)
         // and the callback object reference
         certificatedServerRef = xmlBlasterPOA.create_reference(ServerHelper.id());
         byte[] oid = xmlBlasterPOA.reference_to_id(certificatedServerRef);
         uniqueClientKey = new String(oid);

         // The bytes at IOR position 234 and 378 are increased (there must be the object_id)
         Log.info(ME, "Login for " + loginName + " oid=<" + uniqueClientKey + ">");
      } catch ( Exception e ) {
         e.printStackTrace();
         Log.error(ME, e.toString());
         throw new XmlBlasterException(ME+"Unknown", "login failed: " + e.toString());
      }

      XmlQoSClient xmlQoS = new XmlQoSClient(xmlQoS_literal);
      ClientInfo clientInfo = new ClientInfo(uniqueClientKey, loginName, passwd, callback, callbackIOR, xmlQoS);
      synchronized(clientInfoMap) {
         clientInfoMap.put(uniqueClientKey, clientInfo);
      }
      return org.xmlBlaster.serverIdl.ServerHelper.narrow(certificatedServerRef);
   }


   /**
    * Logout of a client
    */
   public void logout(org.xmlBlaster.serverIdl.Server xmlServer) throws XmlBlasterException
   {

      byte[] oid;

      try {
         oid = xmlBlasterPOA.reference_to_id(xmlServer);
      } catch (Exception e) {
         Log.error(ME+".Unknown", "Sorry, you are not known, no logout possible");
         throw new XmlBlasterException(ME+".Unknown", "Sorry, you are not known, no logout possible");
      }

      String uniqueClientKey = new String(oid);

      try {
         xmlServer._release();
         //xmlBlasterPOA.deactivate_object(oid);
         Log.warning(ME, "Logout and freeing resources is not yet tested!");
      } catch (Exception e) {
         e.printStackTrace();
         Log.error(ME, e.toString());
      }

      synchronized(clientInfoMap) {
         Object obj = clientInfoMap.remove(uniqueClientKey);
         if (obj == null) {
            Log.error(ME+".Unknown", "Sorry, you are not known, no logout");
            throw new XmlBlasterException(ME+".Unknown", "Sorry, you are not known, no logout");
         }
         ClientInfo clientInfo = (ClientInfo)obj;
         Log.info(ME, "Successfull logout for client " + clientInfo.toString());
         obj = null;
      }
   }


   /**
    * Use this method to check a clients authentication
    *
    * @return ClientInfo - if the client is OK
    * @exception Access denied
    */
   public ClientInfo check() throws XmlBlasterException
   {
      String uniqueClientKey;
      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();
      try { 
         // who is it?
         // find out by asking the xmlBlasterPOA
         
         // org.omg.PortableServer.Current poa_current = xmlBlasterPOA.getORB().orb.getPOACurrent();
         org.omg.PortableServer.Current poa_current = org.omg.PortableServer.CurrentHelper.narrow(
                                                      orb.resolve_initial_references("POACurrent"));
         byte[] active_oid = poa_current.get_object_id();
         uniqueClientKey = new String(active_oid);
      } catch (Exception e) {
         Log.error(ME+".AccessCheckProblem", "Sorry, can't find out who you are, access denied");
         throw new XmlBlasterException(ME+".AccessCheckProblem", "Sorry, can't find out who you are, access denied");
      }

      Object obj = null;
      synchronized(clientInfoMap) {
         obj = clientInfoMap.get(uniqueClientKey);
      }

      if (obj == null) {
         Log.error(ME+".AccessDenied", "Sorry, uniqueClientKey is invalid");
         throw new XmlBlasterException(ME+".AccessDenied", "Sorry, uniqueClientKey is invalid");
      }
      ClientInfo clientInfo = (ClientInfo)obj;

      Log.info(ME, "Succesfully granted access for " + clientInfo.toString() + " oid=<" + uniqueClientKey + ">" + stop.nice());
      return clientInfo;
   }
}
