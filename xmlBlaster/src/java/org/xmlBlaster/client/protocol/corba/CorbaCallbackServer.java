/*------------------------------------------------------------------------------
Name:      CorbaCallbackServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Version:   $Id: CorbaCallbackServer.java,v 1.6 2001/02/15 21:11:23 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.corba;

import org.xmlBlaster.client.protocol.I_CallbackExtended;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.protocol.corba.CorbaDriver;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallback;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackPOATie;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackHelper;


/**
 * Example for a CORBA callback implementation.
 * <p />
 * You can use this default callback handling with your clients,
 * but if you need other handling of callbacks, take a copy
 * of this Callback implementation and add your own code.
 * <p />
 */
public class CorbaCallbackServer implements org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackOperations
{
   private org.omg.CORBA.ORB orb = null;
   private org.omg.PortableServer.POA rootPOA = null;
   private BlasterCallback callback = null;

   private final String ME;
   private final I_CallbackExtended boss;
   private final String loginName;


   /**
    * Construct a CORBA callback server for xmlBlaster, used by java/corba clients.
    * <p />
    * @param name The login name of the client, for logging and identification with update() callbacks.
    * @param boss My client which wants to receive the update() calls.
    * @param orb  A handle to my initialized orb
    */
   public CorbaCallbackServer(String name, I_CallbackExtended boss, org.omg.CORBA.ORB orb) throws XmlBlasterException
   {
      this.ME = "CorbaCallbackServer-" + name;
      this.boss = boss;
      this.loginName = name;
      this.orb = orb;
      createCallbackServer();
      Log.info(ME, "Success, created CORBA callback server for " + loginName);
   }


   /**
    * Building a Callback server, using the tie approach.
    *
    * @return the BlasterCallback server
    * @exception XmlBlasterException if the BlasterCallback server can't be created
    *            id="CallbackCreationError"
    */
   private void createCallbackServer() throws XmlBlasterException
   {
      BlasterCallbackPOATie callbackTie = new BlasterCallbackPOATie(this);

      // Getting the default POA implementation "RootPOA"
      try {
         rootPOA = org.omg.PortableServer.POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
      } catch (org.omg.CORBA.COMM_FAILURE e) {
         throw new XmlBlasterException("InitCorbaFailed", "Could not initialize CORBA, do you use the SUN-JDK delivered ORB instead of JacORB or ORBaccus? Try 'jaco' instead of 'java' and read instructions in xmlBlaster/bin/jaco or xmlBlaster/config/orb.properties: " + e.toString());
      } catch (Exception e) {
         Log.error(ME + ".CallbackCreationError", "Can't create a BlasterCallback server, RootPOA not found: " + e.toString());
         throw new XmlBlasterException("CallbackCreationError", e.toString());
      }

      try {
         this.callback = BlasterCallbackHelper.narrow(rootPOA.servant_to_reference( callbackTie ));
         rootPOA.the_POAManager().activate();
         // necessary for orbacus
         if (orb.work_pending()) orb.perform_work();
      } catch (Exception e) {
         Log.error(ME + ".CallbackCreationError", "Can't create a BlasterCallback server, narrow failed: " + e.toString());
         throw new XmlBlasterException("CallbackCreationError", e.toString());
      }
   }


   /**
    * Shutdown the callback server.
    */
   public void shutdown()
   {
      if (this.callback == null) {
         if (Log.TRACE) Log.trace(ME, "No callback server to shutdown.");
         return;
      }

      if (rootPOA != null && this.callback != null) {
         try {
            callback._release();
            rootPOA.deactivate_object(rootPOA.reference_to_id(callback));
         } catch(Exception e) { Log.warn(ME, "POA deactivate callback failed"); }
         callback = null;
      }

      // HACK May,24 2000 !!! (search 'Thread leak' in this file to remove the hack again and remove the two 'static' qualifiers below.)
      // Thread leak from JacORB 1.2.2, the threads
      //   - JacORB Listener Thread
      //   - JacORB ReplyReceptor
      //   - JacORB Request Receptor
      // are never released on orb.shutdown() and rootPoa.deactivate()
      //
      // So we use a orb and poa singleton and recycle it.
      // The drawback is that a running client can't change the
      // orb behavior
      // Thread leak !!!
      /*
      if (rootPOA != null) {
         try {
            rootPOA.the_POAManager().deactivate(true, true);
         } catch(Exception e) { Log.warn(ME, "POA deactivate failed"); }
         rootPOA = null;
      }
      */
      Log.info(ME, "The callback server is shutdown.");
   }


   /**
    * @return The IOR of this server, which can be used for the loginQoS
    */
   public CallbackAddress getCallbackIOR()
   {
      // Add the stringified IOR to QoS ...
      CallbackAddress addr = new CallbackAddress("IOR");
      addr.setAddress(orb.object_to_string(this.callback));
      return addr;
   }


   /**
    * This is the callback method invoked from the CORBA server
    * informing the client in an asynchronous mode about new messages.
    * <p />
    * It implements the interface BlasterCallbackOperations.
    * <p />
    * The call is converted to the native MessageUnit, and the other update()
    * method of this class is invoked.
    *
    * @param msgUnitArr Contains a MessageUnit structs (your message) for CORBA
    * @see xmlBlaster.idl
    */
   public void update(org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] msgUnitArr)
   {
      if (msgUnitArr == null) return;

      try {
         // convert Corba to internal MessageUnit and call update() ...
         boss.update(loginName, CorbaDriver.convert(msgUnitArr));
      }
      catch(XmlBlasterException e) {  // TODO: remove CORBA "oneway" and send Exception back to xmlBlaster.
         Log.error(ME, "Delivering message to client failed, message is lost.");
      }
   }

} // class CorbaCallbackServer

