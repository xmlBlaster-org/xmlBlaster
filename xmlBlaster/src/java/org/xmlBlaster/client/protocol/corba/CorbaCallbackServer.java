/*------------------------------------------------------------------------------
Name:      CorbaCallbackServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Version:   $Id: CorbaCallbackServer.java,v 1.30 2002/12/20 16:32:02 ruff Exp $
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.corba;

import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.client.protocol.I_CallbackServer;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.protocol.corba.CorbaDriver;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallback;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackPOATie;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackHelper;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.client.PluginLoader;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;


/**
 * Example for a CORBA callback implementation.
 * <p />
 * You can use this default callback handling with your clients,
 * but if you need other handling of callbacks, take a copy
 * of this Callback implementation and add your own code.
 * <p />
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
 */
public class CorbaCallbackServer implements org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackOperations, I_CallbackServer
{
   /** made static that we can recycle the orb */
   private static org.omg.CORBA.ORB orb = null;
   private org.omg.PortableServer.POA rootPOA = null;
   private BlasterCallback callback = null;

   private String ME;
   private Global glob;
   private LogChannel log;
   private I_CallbackExtended boss;

   public CorbaCallbackServer() {}

   /**
    * Construct a CORBA callback server for xmlBlaster, used by java/corba clients.
    * <p />
    * @param name The login name of the client, for logging and identification with update() callbacks.
    * @param boss My client which wants to receive the update() calls.
    * @param orb  A handle to my initialized orb
    */
   public CorbaCallbackServer(Global glob, String name, I_CallbackExtended boss, org.omg.CORBA.ORB orb_) throws XmlBlasterException
   {
      orb = orb_;
      initialize(glob, name, boss);
   }

   /**
    * Construct a CORBA callback server for xmlBlaster, used by java/corba clients.
    * <p />
    * @param name The login name of the client, for logging only
    * @param boss My client which wants to receive the update() calls.
    */
   public void initialize(Global glob, String name, I_CallbackExtended boss) throws XmlBlasterException
   {
      this.glob = glob;
      if (this.glob == null)
         this.glob = new Global();
      this.log = glob.getLog("corba");

      String cbHostname = null;
      if (orb == null) {
         cbHostname = CorbaDriver.initializeOrbEnv(glob, true);
         orb = org.omg.CORBA.ORB.init(glob.getArgs(), null);
      }

      this.ME = "CorbaCallbackServer-" + name;
      this.boss = boss;
      this.orb = orb;
      createCallbackServer();
      log.info(ME, "Success, created CORBA callback server on host " + cbHostname);
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
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CALLBACKSERVER_CREATION, ME, "Could not initialize CORBA, do you use the SUN-JDK delivered ORB instead of JacORB or ORBaccus? Try 'jaco' instead of 'java' and read instructions in xmlBlaster/bin/jaco or xmlBlaster/config/orb.properties: " + e.toString());
      } catch (Exception e) {
         log.error(ME + ".CallbackCreationError", "Can't create a BlasterCallback server, RootPOA not found: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CALLBACKSERVER_CREATION, ME, e.toString());
      }

      try {
         rootPOA.the_POAManager().activate();
         this.callback = BlasterCallbackHelper.narrow(rootPOA.servant_to_reference( callbackTie ));
         // necessary for orbacus
         if (orb.work_pending()) orb.perform_work();
      } catch (Exception e) {
         log.error(ME + ".CallbackCreationError", "Can't create a BlasterCallback server, narrow failed: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CALLBACKSERVER_CREATION, ME, e.toString());
      }
   }

   /**
    * Shutdown the callback server.
    */
   public boolean shutdownCb()
   {
      if (this.callback == null) {
         if (log.TRACE) log.trace(ME, "No callback server to shutdown.");
         return false;
      }

      if (rootPOA != null && this.callback != null) {
         try {
            callback._release();
            rootPOA.deactivate_object(rootPOA.reference_to_id(callback));
         } catch(Exception e) { log.warn(ME, "POA deactivate callback failed"); }
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
         } catch(Exception e) { log.warn(ME, "POA deactivate failed"); }
         rootPOA = null;
      }
      */
      log.info(ME, "The callback server is shutdown.");
      return true;
   }

   /**
    * @return The protocol name "IOR"
    */
   public final String getCbProtocol()
   {
      return "IOR";
   }

   /**
    * @return The stringified IOR of this server, which can be used for the connectQos
    */
   public String getCbAddress() throws XmlBlasterException
   {
      return orb.object_to_string(this.callback);
   }

   /**
    * This is the callback method invoked from the CORBA server
    * informing the client in an asynchronous mode about new messages.
    * <p />
    * It implements the interface BlasterCallbackOperations.
    * <p />
    * The call is converted to the native MsgUnitRaw, and the other update()
    * method of this class is invoked.
    * <p />
    * This oneway method does not return something, it is high performing but
    * you loose the application level hand shake.
    *
    * @param msgUnitArr Contains a MsgUnitRaw structs (your message) for CORBA
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public void updateOneway(String cbSessionId, org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] msgUnitArr)
   {
      if (msgUnitArr == null) {
         log.warn(ME, "Receiving in updateOneway(" + cbSessionId + ") null message");
         return;
      }
      if (log.CALL) log.call(ME, "Entering updateOneway(" + cbSessionId + ") of " + msgUnitArr.length + " messages");

      try {
         // convert Corba to internal MsgUnitRaw and call update() ...
         MsgUnitRaw[] localMsgUnitRawArr = CorbaDriver.convert(glob, msgUnitArr);
         boss.updateOneway(cbSessionId, localMsgUnitRawArr);
      }
      catch (Throwable e) {
         log.error(ME, "updateOneway() failed, exception is not sent to xmlBlaster: " + e.toString());
         e.printStackTrace();
      }
   }

   /**
    * This is the callback method invoked from the CORBA server
    * informing the client in an asynchronous mode about new messages.
    * <p />
    * It implements the interface BlasterCallbackOperations.
    * <p />
    * The call is converted to the native MsgUnitRaw, and the other update()
    * method of this class is invoked.
    *
    * @param msgUnitArr Contains a MsgUnitRaw structs (your message) for CORBA
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String[] update(String cbSessionId, org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] msgUnitArr)
                        throws org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException
   {
      if (msgUnitArr == null) {
         throw CorbaDriver.convert(new XmlBlasterException(glob, ErrorCode.USER_UPDATE_ILLEGALARGUMENT, ME, "Received update of null message"));
      }
      if (log.CALL) log.call(ME, "Entering update(" + cbSessionId + ") of " + msgUnitArr.length + " messages");
      if (log.DUMP) {
         for (int ii=0; ii< msgUnitArr.length; ii++)
            log.dump(ME, "update()\n" + msgUnitArr[ii].xmlKey + "\n" + msgUnitArr[ii].qos);
      }

      try {
         // convert Corba to internal MsgUnitRaw and call update() ...
         MsgUnitRaw[] localMsgUnitRawArr = CorbaDriver.convert(glob, msgUnitArr);
         return boss.update(cbSessionId, localMsgUnitRawArr);
      }
      catch(XmlBlasterException e) {
         log.error(ME, "Delivering message to client failed, message is lost: " + e.toString());
         throw CorbaDriver.convert(e); // convert to org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException
      }
      catch (Throwable e) {
         log.error(ME, "Delivering message to client failed, message is lost: " + e.toString());
         e.printStackTrace();
         throw CorbaDriver.convert(new XmlBlasterException(glob, ErrorCode.USER_UPDATE_ERROR, ME,
                                   "Delivering message to client failed, message is lost: " + e.toString()));
      }
   }

   /**
    * Ping to check if the callback server is alive.
    * @param qos ""
    * @return ""
    */
   public String ping(String qos)
   {
      if (log.CALL) log.call(ME, "Entering ping() ...");
      return "";
   }
} // class CorbaCallbackServer

