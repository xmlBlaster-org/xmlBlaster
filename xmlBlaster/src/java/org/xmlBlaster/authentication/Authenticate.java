/*------------------------------------------------------------------------------
Name:      Authenticate.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login for clients
Version:   $Id: Authenticate.java,v 1.19 1999/12/09 00:11:05 ruff Exp $
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
import jacorb.poa.util.POAUtil;


/**
 * Authenticate a client via login.
 * <p>
 * The login method serves as a factory for a xmlBlaster.Server Reference
 */
public class Authenticate
{
   final private static String ME = "Authenticate";

   private static Authenticate authenticate = null; // Singleton pattern

   private AuthServerImpl authServerImpl;

   /**
    * With this map you can find a client using a uniqueClientKey.
    *
    * key   = uniqueClientKey, the byte[] from the POA active object map (aom)
    * value = ClientInfo object, containing all data about a client
    */
   final private Map aomClientInfoMap = Collections.synchronizedMap(new HashMap());

   /**
    * With this map you can find a client using his login name.
    *
    * key   = loginName, the unique login name of a client
    * value = ClientInfo object, containing all data about a client
    */
   final private Map loginNameClientInfoMap = Collections.synchronizedMap(new HashMap());

   /**
    * For listeners who want to be informed about login/logout
    */
   final private Set clientListenerSet = Collections.synchronizedSet(new HashSet());

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
    * Access to Authenticate singleton
    */
   public static Authenticate getInstance()
   {
      synchronized (Authenticate.class) {
         if (authenticate == null) {
            Log.panic(ME, "Use other getInstance first");
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
    *
    * @param The CORBA interface implementing object
    */
   private Authenticate(AuthServerImpl authServerImpl)
   {
      this.authServerImpl = authServerImpl;

      if (Log.CALLS) Log.calls(ME, "Entering constructor");

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
    * Authentication of a client.
    * <p>
    * @param xmlQoS_literal
    *     <pre>
    *        &lt;client>
    *           &lt;compress type='gzip'>
    *              1000
    *           &lt;/compress>
    *           &lt;queue>
    *              &lt;size>
    *                 1000
    *              &lt;/size>
    *              &lt;timeout>
    *                 3600
    *              &lt;/timeout>
    *           &lt;/queue>
    *        &lt;/client>
    *     </pre>
    * @param callback The Callback interface of the client
    * @return The xmlBlaster.Server interface
    * @exception XmlBlasterException Access denied
    */
   public org.xmlBlaster.serverIdl.Server login(String loginName, String passwd,
                       BlasterCallback callback, String callbackIOR,
                       String xmlQoS_literal) throws XmlBlasterException
   {
      String uniqueClientKey;
      org.omg.CORBA.Object certificatedServerRef = null;

      ClientInfo clientInfo = getClientInfoByName(loginName);

      if (clientInfo != null && clientInfo.isLoggedIn()) {
         Log.warning(ME+".AlreadyLoggedIn", "Client " + loginName + " is already logged in");
         // throw new XmlBlasterException(ME+".AlreadyLoggedIn", "Sorry, you are already logged in");
         // allowing re-login: if the client crashed without proper logout, he should
         // be allowed to login again, so - first logout the last session:
         try {
            logout(clientInfo.getAuthenticationInfo().getXmlBlaster());
         } catch (XmlBlasterException e) {
            Log.warning(ME, "Exception during forced logout: " + e.toString());
         }
      }

      try {
         // set up a association between the new created object reference (oid is sufficient)
         // and the callback object reference
         certificatedServerRef = xmlBlasterPOA.create_reference(ServerHelper.id());
         byte[] oid = xmlBlasterPOA.reference_to_id(certificatedServerRef);
         uniqueClientKey = new String(oid);

         // The bytes at IOR position 234 and 378 are increased (there must be the object_id)
         Log.info(ME, "Login for " + loginName + " oid=<" + POAUtil.convert(oid, true) + ">");
      } catch ( Exception e ) {
         e.printStackTrace();
         Log.error(ME, e.toString());
         throw new XmlBlasterException(ME+"Unknown", "login failed: " + e.toString());
      }

      org.xmlBlaster.serverIdl.Server xmlBlaster = org.xmlBlaster.serverIdl.ServerHelper.narrow(certificatedServerRef);
      ClientQoS xmlQoS = new ClientQoS(xmlQoS_literal);
      AuthenticationInfo authInfo = new AuthenticationInfo(uniqueClientKey, loginName, passwd, xmlBlaster, callback, callbackIOR, xmlQoS);

      if (clientInfo != null) {
         clientInfo.notifyAboutLogin(authInfo); // clientInfo object exists, maybe with a queue of messages
      }
      else {                               // login of yet unknown client
         clientInfo = new ClientInfo(authInfo);
         synchronized(loginNameClientInfoMap) {
            loginNameClientInfoMap.put(loginName, clientInfo);
         }
      }

      synchronized(aomClientInfoMap) {
         aomClientInfoMap.put(uniqueClientKey, clientInfo);
      }

      fireClientEvent(clientInfo, true);

      return xmlBlaster;
   }


   /**
    * Access a clientInfo with the unique login name
    * @return the ClientInfo object<br />
    *         null if not found
    */
   public final ClientInfo getOrCreateClientInfoByName(String loginName) throws XmlBlasterException
   {
      if (loginName == null || loginName.length() < 2) {
         Log.warning(ME + ".InvalidClientName", "Given loginName='" + loginName + "' is invalid");
         throw new XmlBlasterException(ME + ".InvalidClientName", "Your given loginName is null or shorter 2 chars, loginName rejected");
      }

      ClientInfo clientInfo = getClientInfoByName(loginName);
      if (clientInfo == null) {
         clientInfo = new ClientInfo(loginName);
         synchronized(loginNameClientInfoMap) {
            loginNameClientInfoMap.put(loginName, clientInfo);
         }
      }

      return clientInfo;
   }


   /**
    * Access a clientInfo with the unique login name
    * @return the ClientInfo object<br />
    *         null if not found
    */
   public final ClientInfo getClientInfoByName(String loginName)
   {
      synchronized(loginNameClientInfoMap) {
         return (ClientInfo)loginNameClientInfoMap.get(loginName);
      }
   }


   /**
    * Logout of a client.
    * <p>
    * @exception XmlBlasterException If client is unknown
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
      } catch (Exception e) {
         e.printStackTrace();
         Log.error(ME, e.toString());
      }

      Object obj;
      synchronized(aomClientInfoMap) {
         obj = aomClientInfoMap.remove(uniqueClientKey);
      }

      if (obj == null) {
         Log.error(ME+".Unknown", "Sorry, you are not known, no logout");
         throw new XmlBlasterException(ME+".Unknown", "Sorry, you are not known, no logout");
      }

      ClientInfo clientInfo = (ClientInfo)obj;

      synchronized(loginNameClientInfoMap) {
         loginNameClientInfoMap.remove(clientInfo.getLoginName());
      }

      fireClientEvent(clientInfo, false); // informs all ClientListener

      Log.info(ME, "Successfull logout for client " + clientInfo.toString());
      clientInfo.notifyAboutLogout();
      clientInfo = null;
   }


   /**
    * Used to fire an event if a client does a login / logout
    */
   private final void fireClientEvent(ClientInfo clientInfo, boolean login) throws XmlBlasterException
   {
      synchronized (clientListenerSet) {
         if (clientListenerSet.size() == 0)
            return;

         ClientEvent event = new ClientEvent(clientInfo);
         Iterator iterator = clientListenerSet.iterator();

         while (iterator.hasNext()) {
            ClientListener cli = (ClientListener)iterator.next();
            if (login)
               cli.clientAdded(event);
            else
               cli.clientRemove(event);
         }

         event = null;
      }
   }


   /**
    * Use this method to check a clients authentication.
    * <p>
    * This method can only be called from an invoked xmlBlaster-server
    * method (like subscribe()), because only there the
    * unique POA 'active object identifier' is available to identify the caller.
    *
    * @return ClientInfo - if the client is OK
    * @exception XmlBlasterException Access denied
    */
   public ClientInfo check() throws XmlBlasterException
   {
      byte[] active_oid;
      String uniqueClientKey;
      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();
      try {
         // who is it?
         // find out by asking the xmlBlasterPOA

         // org.omg.PortableServer.Current poa_current = xmlBlasterPOA.getORB().orb.getPOACurrent();
         org.omg.PortableServer.Current poa_current = org.omg.PortableServer.CurrentHelper.narrow(
                                                      orb.resolve_initial_references("POACurrent"));
         active_oid = poa_current.get_object_id();
         uniqueClientKey = new String(active_oid);
      } catch (Exception e) {
         Log.error(ME+".AccessCheckProblem", "Sorry, can't find out who you are, access denied");
         throw new XmlBlasterException(ME+".AccessCheckProblem", "Sorry, can't find out who you are, access denied");
      }

      Object obj = null;
      synchronized(aomClientInfoMap) {
         obj = aomClientInfoMap.get(uniqueClientKey);
      }

      if (obj == null) {
         Log.error(ME+".AccessDenied", "Sorry, uniqueClientKey is invalid");
         throw new XmlBlasterException(ME+".AccessDenied", "Sorry, uniqueClientKey is invalid");
      }
      ClientInfo clientInfo = (ClientInfo)obj;

      if (Log.TIME) Log.time(ME, "Elapsed time in check()" + stop.nice());
      if (Log.TRACE) Log.trace(ME, "Succesfully granted access for " + clientInfo.toString() +
                      " oid=<" + POAUtil.convert(active_oid, true) + ">" + stop.nice());

      return clientInfo;
   }


   /**
    * Adds the specified client listener to receive login/logout events
    */
   public void addClientListener(ClientListener l) {
      if (l == null) {
         return;
      }
      synchronized (clientListenerSet) {
         clientListenerSet.add(l);
      }
   }


   /**
    * Removes the specified listener
    */
   public synchronized void removeClientListener(ClientListener l) {
      if (l == null) {
         return;
      }
      synchronized (clientListenerSet) {
         clientListenerSet.remove(l);
      }
   }


}
