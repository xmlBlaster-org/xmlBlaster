/*------------------------------------------------------------------------------
Name:      AuthServerImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementing the CORBA xmlBlaster-server interface
Version:   $Id$
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.corba;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.corba.authenticateIdl.*;
import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
import org.xmlBlaster.protocol.corba.serverIdl.ServerHelper;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.ConnectReturnQosServer;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.engine.qos.DisconnectQosServer;
import org.xmlBlaster.util.qos.address.ServerRef;
import org.xmlBlaster.protocol.corba.serverIdl.ServerPOATie;

import org.omg.PortableServer.*;

/* Obtain remote IP address (from jacorb mailing list):

I'm not sure, but I think, the only standard way to achieve this, is to use
PortableInterceptors on the client side too, adding some information to
PortableInterceptor::ClientRequestInfo_ptr, or not?
Then, you don't need the cast. 


public void receive_request(ServerRequestInfo requestInfo) throws ForwardRequest {
    java.net.InetAddress remoteAddress = getRemoteAddress(requestInfo);
    ... etc ...
 

public java.net.InetAddress getRemoteAddress(ServerRequestInfo requestInfo) {
  try {
            org.jacorb.orb.portableInterceptor.ServerRequestInfoImpl serverRequestInfo =
        (org.jacorb.orb.portableInterceptor.ServerRequestInfoImpl)requestInfo;
                org.jacorb.orb.dsi.ServerRequest serverRequest = serverRequestInfo.request;
    org.jacorb.orb.giop.GIOPConnection giopConnection = serverRequest.getConnection();
    org.jacorb.orb.iiop.ServerIIOPConnection serverIIOPTransport = (org.jacorb.orb.iiop.ServerIIOPConnection)giopConnection.getTransport();
                java.net.Socket socket = serverIIOPTransport.getSocket();

    java.net.InetSocketAddress remoteSockAddr = (java.net.InetSocketAddress)socket.getRemoteSocketAddress();
    java.net.InetAddress remoteInetAddr = remoteSockAddr.getAddress();

    return remoteInetAddr;
  }
  catch(ClassCastException e) {
    e.printStackTrace();
  }
  return null;
}
*/

/**
 * Implements the xmlBlaster AuthServer CORBA Interface.
 * <br>
 * All real work is directly delegated to org.xmlBlaster.authentication.Authenticate
 */
//public class AuthServerImpl extends ServerPOA {            // inheritance approach
public class AuthServerImpl implements AuthServerOperations {    // tie approach

   private final static String ME = "AuthServerImpl";
   private final Global glob;
   private static Logger log = Logger.getLogger(AuthServerImpl.class.getName());
   private final org.omg.CORBA.ORB orb;
   private final I_Authenticate authenticate;
   /**  This specialized POA controlles the xmlBlaster server */
   private final String xmlBlasterPOA_name = "xmlBlaster-POA";
   /** We use our own, customized POA */
   private POA xmlBlasterPOA;
   /** The root POA */
   private org.omg.PortableServer.POA rootPOA;
   // USING TIE:
   private ServerPOATie xmlBlasterServant;  // extends org.omg.PortableServer.Servant
   // NOT TIE
   /** extends org.omg.PortableServer.Servant */
   //private ServerImpl xmlBlasterServant;
   private AddressServer addressServer;

   /**
    * One instance implements a server.
    *
    * Authenticate creates a singlorg.xmlBlaster.SimpleRunLevelTeste instance of the xmlBlaster.Server.
    * Clients need first to do a login, from where they get
    * an IOR which serves them, one thread for each request.<p>
    *
    * Every client has its own IOR, but in reality this IOR is mapped
    * to a single servant.<p>
    * This allows:<br>
    * - Identification of the client thru its unique IOR<br>
    * - Only a few threads are enough to serve many clients
    *
    * @param The orb
    * @param authenticate The authentication service
    * @param blaster The interface to access xmlBlaster
    */
   public AuthServerImpl(Global glob, org.omg.CORBA.ORB orb, AddressServer addressServer, I_Authenticate authenticate, I_XmlBlaster blaster)
   {
      this.glob = glob;

      this.orb = orb;
      this.authenticate = authenticate;
      this.addressServer = addressServer;
      if (log.isLoggable(Level.FINER)) log.finer("Entering constructor with ORB argument");

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
         xmlBlasterServant = new ServerPOATie(new ServerImpl(glob, orb, this.addressServer, blaster));
         // NOT TIE:
         //xmlBlasterServant = new ServerImpl(glob, orb, blaster);

         xmlBlasterPOA.set_servant(xmlBlasterServant); // set as default servant
         poaMgr.activate();

         // orb.run();
         // log.info(ME, "Default Active Object Map ID=" + default_oid);
         if (log.isLoggable(Level.FINE)) log.fine("Default xmlBlasterServant activated");
      }
      catch ( Exception e ) {
         e.printStackTrace();
         log.severe(e.toString());
      }

      if (log.isLoggable(Level.FINER)) log.fine("Leaving constructor");
   }


   public org.omg.CORBA.ORB getOrb()
   {
      return orb;
   }


   public I_Authenticate getAuthenticationService()
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
      if (log.isLoggable(Level.FINER)) log.finer("Entering login(loginName=" + loginName/* + ", qos=" + qos_literal */ + ")");

      if (loginName==null || passwd==null || qos_literal==null) {
         log.severe("login failed: please use no null arguments for login()");
         throw CorbaDriver.convert(new org.xmlBlaster.util.XmlBlasterException(glob,
                     ErrorCode.USER_SECURITY_AUTHENTICATION_ILLEGALARGUMENT, ME,
                     "Login failed: please use no null arguments for login()"));
      }

      try {
         // Extend qos to contain security credentials ...
         ConnectQosServer loginQos = new ConnectQosServer(glob, qos_literal);
         loginQos.loadClientPlugin(null, null, loginName, passwd);

         // No login using the connect() method ...
         ConnectReturnQosServer returnQos = connectIntern(loginQos.toXml());

         // Build return handle ...
         ServerRef ref = returnQos.getServerRef();
         if (ref == null) {
            throw CorbaDriver.convert(new org.xmlBlaster.util.XmlBlasterException(glob,
                     ErrorCode.INTERNAL_CONNECTIONFAILURE,
                     ME,
                     "Can't determine server reference."));
         }
         String xmlBlasterIOR = ref.getAddress();
         org.xmlBlaster.protocol.corba.serverIdl.Server xmlBlaster =
                            ServerHelper.narrow(orb.string_to_object(xmlBlasterIOR));
         return xmlBlaster;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw CorbaDriver.convert(e); // transform native exception to Corba exception
      }
   }

   /**
    * Ping to check if xmlBlaster is alive.
    * @see org.xmlBlaster.protocol.I_XmlBlaster#ping(String)
    */
   public String ping(String qos)
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering ping("+qos+") ...");
      return authenticate.ping(this.addressServer, qos);
   }

   /**
    * Called by the CORBA layer. 
    * If qos_literal transports another sessionId (e.g. from a2Blaster)
    * we leave this untouched.
    * This CORBA sessionId (transported hidden in the IOR) is used as well
    */
   public String connect(String qos_literal) throws XmlBlasterException
   {
      try {
         return connectIntern(qos_literal).toXml();
      } catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw CorbaDriver.convert(e); // transform native exception to Corba exception
      }
   }

   private ConnectReturnQosServer connectIntern(String qos_literal) throws org.xmlBlaster.util.XmlBlasterException
   {
      ConnectReturnQosServer returnQos = null;
      String sessionId = null;

      org.omg.CORBA.Object certificatedServerRef = null;
      try {
         // set up a association between the new created object reference (oid is sufficient)
         // and the callback object reference
         certificatedServerRef = xmlBlasterPOA.create_reference(ServerHelper.id());
         sessionId = getSessionId(certificatedServerRef);
         // The bytes at IOR position 234 and 378 are increased (there must be the object_id)
         if (log.isLoggable(Level.FINE)) log.fine("Created sessionId="+sessionId);
      } catch (Exception e) {
         e.printStackTrace();
         log.severe(e.toString());
         throw new org.xmlBlaster.util.XmlBlasterException(glob, ErrorCode.INTERNAL_CONNECTIONFAILURE,
                                ME, "connect failed: " + e.toString());
      }

      String returnQosStr = authenticate.connect(this.addressServer, qos_literal, sessionId);
      returnQos = new ConnectReturnQosServer(glob, returnQosStr);
      if (returnQos.isReconnected()) {
         // How to detect outdated server IORs??
         // Here we assume max one connection of type=CORBA which is probably wrong?
         if (log.isLoggable(Level.FINE)) log.fine("Destroying old server addresses because of reconnect");
         returnQos.removeServerRef("IOR");
      }

      org.xmlBlaster.protocol.corba.serverIdl.Server xmlBlaster = org.xmlBlaster.protocol.corba.serverIdl.ServerHelper.narrow(certificatedServerRef);
      String serverIOR = orb.object_to_string(xmlBlaster);
      returnQos.addServerRef(new ServerRef("IOR", serverIOR));

      if (log.isLoggable(Level.FINEST)) log.finest("Returning from login-connect()" + returnQos.toXml());

      return returnQos;
   }

   public void disconnect(String sessionId, String qos_literal) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering disconnect()");
      try {
         authenticate.disconnect(this.addressServer, sessionId, qos_literal); // throws XmlBlasterException (eg if not connected (init not called, timeout etc.) or someone else than the session owner called disconnect!)
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw CorbaDriver.convert(e); // transform native exception to Corba exception
      }
      if (log.isLoggable(Level.FINER)) log.finer("Exiting disconnect()");
   }

   /**
    * logout of a client.
    * @param xmlServer The handle you got from the login call
    */
   public void logout(org.xmlBlaster.protocol.corba.serverIdl.Server xmlServer) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering logout()");
      disconnect(getSessionId(xmlServer), (new DisconnectQosServer(glob)).toXml());
   }


   /**
    * @param xmlServer org.xmlBlaster.protocol.corba.serverIdl.Server
    */
   public final String getSessionId(org.omg.CORBA.Object xmlServer) throws XmlBlasterException
   {
      String sessionId = null;
      try {
         byte[] oid = xmlBlasterPOA.reference_to_id(xmlServer);
         sessionId = ServerImpl.convert(oid);
         if (log.isLoggable(Level.FINE)) log.fine("POA oid=<" + sessionId + ">");
      } catch (Exception e) {
         log.severe("Sorry, you are unknown. No logout possible.");
         throw CorbaDriver.convert(new org.xmlBlaster.util.XmlBlasterException(glob,
                     ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED,
                     ME,
                     "Sorry, you are not known with CORBA"));
      }
      return sessionId;
   }

   /*
   private String addToQoS(String qos, String add) {
      qos = qos.substring(0, qos.lastIndexOf("</qos>"));
      qos += add + "\n</qos>\n";
      return qos;
   }
   */
   
   public void shutdown() {
      if (log.isLoggable(Level.FINE)) log.fine("shutdown has been invoked");
      if (this.xmlBlasterPOA != null) {
         if (log.isLoggable(Level.FINE)) log.fine("shutdown has been invoked and servant is not null");
//         xmlBlasterPOA.deactivate_object(xmlBlasterPOA.reference_to_id(xmlBlasterServant));
         // deserialize object, wait for competion
         /*
         try {
            this.xmlBlasterPOA.deactivate_object(xmlBlasterPOA.servant_to_id(xmlBlasterServant));
         }
         catch (Exception ex) {
            log.warn(ME, "shutdown:exception occured when deactivating the servant: " + ex.toString());
         shutdown:exception occured when deactivating the servant: org.omg.PortableServer.POAPackage.ServantNotActive: IDL:omg.org/PortableServer/POA/ServantNotActive:1.0
         }
         */
         try {
            xmlBlasterPOA.the_POAManager().deactivate(true, true);
         }
         catch (Exception ex) {
            log.warning("shutdown:exception occured deactivate(): " + ex.toString());
         }
         /*
         try {
            this.xmlBlasterPOA._release();
         }
         catch (Exception ex) {
            log.warn(ME, "shutdown:exception occured _release(): " + ex.toString());
         shutdown:exception occured _release(): org.omg.CORBA.NO_IMPLEMENT: This is a locally constrained object.  vmcid: 0x0  minor code: 0  completed: No
         }
         */
         try {
            this.xmlBlasterPOA.destroy(true, true);
         }
         catch (Exception ex) {
            log.warning("shutdown:exception occured destroy(): " + ex.toString());
         }
      }
   }
}

