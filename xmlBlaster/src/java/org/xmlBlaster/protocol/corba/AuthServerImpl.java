/*------------------------------------------------------------------------------
Name:      AuthServerImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementing the CORBA xmlBlaster-server interface
Version:   $Id: AuthServerImpl.java,v 1.16 2001/09/05 12:21:27 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.corba;

import org.xmlBlaster.util.Log;
import org.jutils.time.StopWatch;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.corba.authenticateIdl.*;
import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
import org.xmlBlaster.protocol.corba.serverIdl.ServerHelper;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallback;
import org.xmlBlaster.client.LogoutQosWrapper;
import org.xmlBlaster.engine.xml2java.*;
import org.xmlBlaster.engine.helper.ServerRef;

import org.omg.PortableServer.*;


/**
 * Implements the xmlBlaster AuthServer CORBA Interface.
 * <br>
 * All real work is directly delegated to org.xmlBlaster.authentication.Authenticate
 */
//public class AuthServerImpl extends ServerPOA {            // inheritance approach
public class AuthServerImpl implements AuthServerOperations {    // tie approach

   private final static String ME = "AuthServerImpl";
   private org.omg.CORBA.ORB orb;
   private I_Authenticate authenticate;
   /**  This specialized POA controlles the xmlBlaster server */
   private final String xmlBlasterPOA_name = "xmlBlaster-POA";
   /** We use our own, customized POA */
   private POA xmlBlasterPOA;
   /** The root POA */
   private org.omg.PortableServer.POA rootPOA;
   // USING TIE:
   // private ServerPOATie xmlBlasterServant;  // extends org.omg.PortableServer.Servant
   // NOT TIE
   /** extends org.omg.PortableServer.Servant */
   private ServerImpl xmlBlasterServant;

   /**
    * One instance implements a server.
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
    * @param The orb
    * @parma authenticate The authentication service
    * @param blaster The interface to access xmlBlaster
    */
   public AuthServerImpl(org.omg.CORBA.ORB orb, I_Authenticate authenticate, I_XmlBlaster blaster)
   {
      if (Log.CALL) Log.call(ME, "Entering constructor with ORB argument");
      this.orb = orb;
      this.authenticate = authenticate;

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
         xmlBlasterServant = new ServerImpl(orb, blaster);

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

      if (Log.CALL) Log.trace(ME, "Leaving constructor");
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
      if (Log.CALL) Log.call(ME, "Entering login(loginName=" + loginName/* + ", qos=" + qos_literal */ + ")");

      if (loginName==null || passwd==null || qos_literal==null) {
         Log.error(ME+"InvalidArguments", "login failed: please use no null arguments for login()");
         throw new XmlBlasterException("LoginFailed.InvalidArguments", "login failed: please use no null arguments for login()");
      }

      try {
         // Extend qos to contain security credentials ...
         ConnectQos loginQos = new ConnectQos(qos_literal);
         loginQos.setSecurityPluginData("simple", "1.0", loginName, passwd);

         // No login using the connect() method ...
         ConnectReturnQos returnQos = connect(loginQos);

         // Build return handle ...
         ServerRef ref = returnQos.getServerRef();
         if (ref == null) throw new XmlBlasterException(ME+".internal", "Can't determine server reference.");
         String xmlBlasterIOR = ref.getAddress();
         org.xmlBlaster.protocol.corba.serverIdl.Server xmlBlaster =
                            ServerHelper.narrow(orb.string_to_object(xmlBlasterIOR));
         return xmlBlaster;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }
   }

   /**
    * If qos_literal transports another sessionId (e.g. from a2Blaster)
    * we leave this untouched.
    * This CORBA sessionId (transported hidden in the IOR) is used as well
    */
   public String connect(String qos_literal) throws XmlBlasterException
   {
      try {
         return connect(new ConnectQos(qos_literal)).toXml();
      } catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }
   }

   private ConnectReturnQos connect(ConnectQos loginQos) throws XmlBlasterException
   {
      ConnectReturnQos returnQos = null;
      String sessionId = null;

      StopWatch stop = null; if (Log.TIME) stop = new StopWatch();

      org.omg.CORBA.Object certificatedServerRef = null;
      try {
         // set up a association between the new created object reference (oid is sufficient)
         // and the callback object reference
         certificatedServerRef = xmlBlasterPOA.create_reference(ServerHelper.id());
         sessionId = getSessionId(certificatedServerRef);
         // The bytes at IOR position 234 and 378 are increased (there must be the object_id)
      } catch (Exception e) {
         e.printStackTrace();
         Log.error(ME+".Corba", e.toString());
         throw new XmlBlasterException(ME + ".Corba.connectFailed", "connect failed: " + e.toString());
      }

      try {
         returnQos = authenticate.connect(loginQos, sessionId);

         org.xmlBlaster.protocol.corba.serverIdl.Server xmlBlaster = org.xmlBlaster.protocol.corba.serverIdl.ServerHelper.narrow(certificatedServerRef);
         String serverIOR = orb.object_to_string(xmlBlaster);
         returnQos.setServerRef(new ServerRef("IOR", serverIOR));
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }

      if (Log.TIME) Log.time(ME, "Elapsed time in connect()" + stop.nice());
      if (Log.DUMP) Log.dump(ME, "Returning from login-connect()" + returnQos.toXml());

      return returnQos;
   }

   public void disconnect(String sessionId, String qos_literal) throws XmlBlasterException {
      if (Log.CALL) Log.call(ME, "Entering disconnect()");
      try {
         authenticate.disconnect(sessionId, qos_literal); // throws XmlBlasterException (eg if not connected (init not called, timeout etc.) or someone else than the session owner called disconnect!)
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }
      if (Log.CALL) Log.call(ME, "Exiting disconnect()");
   }

   /**
    * Logout of a client.
    * @param xmlServer The handle you got from the login call
    */
   public void logout(org.xmlBlaster.protocol.corba.serverIdl.Server xmlServer) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering logout()");
      disconnect(getSessionId(xmlServer), (new LogoutQosWrapper()).toXml());
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
         if (Log.TRACE) Log.trace(ME, "POA oid=<" + sessionId + ">");
      } catch (Exception e) {
         Log.error(ME+".Unknown", "Sorry, you are unknown. No logout possible.");
         throw new XmlBlasterException("CorbaUnknown", "Sorry, you are not known with CORBA");
      }
      return sessionId;
   }

   private String addToQoS(String qos, String add) {
      qos = qos.substring(0, qos.lastIndexOf("</qos>"));
      qos += add + "\n</qos>\n";
      return qos;
   }
}

