/*------------------------------------------------------------------------------
Name:      CorbaCallbackServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Version:   $Id$
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.corba;

import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.client.protocol.I_CallbackServer;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.protocol.corba.CorbaDriver;
import org.xmlBlaster.protocol.corba.OrbInstanceFactory;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallback;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackPOATie;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackHelper;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.client.PluginLoader;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.util.plugin.PluginInfo;


/**
 * Example for a CORBA callback implementation.
 * <p />
 * You can use this default callback handling with your clients,
 * but if you need other handling of callbacks, take a copy
 * of this Callback implementation and add your own code.
 * <p />
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
 */
public final class CorbaCallbackServer implements org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackOperations, I_CallbackServer
{
   private org.omg.CORBA.ORB orb;
   private org.omg.PortableServer.POA rootPOA;
   private BlasterCallback callback;

   private String ME;
   private Global glob;
   private static Logger log = Logger.getLogger(CorbaCallbackServer.class.getName());
   private I_CallbackExtended boss;
   private CallbackAddress callbackAddress;
   private PluginInfo pluginInfo;

   /**
    * Called by plugin loader which calls init(Global, PluginInfo) thereafter. 
    */
   public CorbaCallbackServer() {
   }

   /**
    * Construct a CORBA callback server for xmlBlaster, used by java/corba clients.
    * <p />
    * @param name The login name of the client, for logging and identification with update() callbacks.
    * @param boss My client which wants to receive the update() calls.
    * @param orb  A handle to my initialized orb
    */
   public CorbaCallbackServer(Global glob, String name, CallbackAddress callbackAddress,
                              I_CallbackExtended boss, org.omg.CORBA.ORB orb_) throws XmlBlasterException
   {
      //this.orb = orb_; // We create a new orb each time (Marcel 2003-03-27)
      initialize(glob, name, callbackAddress, boss);
   }

   /**
    * Construct a CORBA callback server for xmlBlaster, used by java/corba clients.
    * <p />
    * @param name The login name of the client, for logging only
    * @param boss My client which wants to receive the update() calls.
    */
   public void initialize(Global glob, String name, CallbackAddress callbackAddress,
                          I_CallbackExtended boss) throws XmlBlasterException
   {
      this.glob = glob;
      if (this.glob == null)
         this.glob = new Global();

      this.callbackAddress = callbackAddress;
      if (this.pluginInfo != null)
         this.callbackAddress.setPluginInfoParameters(this.pluginInfo.getParameters());

      this.orb = OrbInstanceFactory.createOrbInstance(this.glob,(String[])null,
                                         glob.getProperty().getProperties(),this.callbackAddress);

      this.ME = "CorbaCallbackServer-" + name;
      this.boss = boss;
      createCallbackServer();
      log.info("Success, created CORBA callback server");
   }

   /** Enforced by I_Plugin */
   public String getType() {
      return getCbProtocol();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return "1.0";
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
      this.pluginInfo = pluginInfo;
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
         rootPOA = org.omg.PortableServer.POAHelper.narrow(this.orb.resolve_initial_references("RootPOA"));
      } catch (org.omg.CORBA.COMM_FAILURE e) {
         //e.printStackTrace();
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CALLBACKSERVER_CREATION, ME, "Could not initialize CORBA, do you use the SUN-JDK delivered ORB instead of JacORB or ORBaccus? Try 'jaco' instead of 'java' and read instructions in xmlBlaster/bin/jaco or xmlBlaster/config/orb.properties: " + e.toString());
      } catch (Exception e) {
         //e.printStackTrace();
         log.severe("Can't create a BlasterCallback server, RootPOA not found: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CALLBACKSERVER_CREATION, ME, e.toString());
      }

      try {
         rootPOA.the_POAManager().activate();
         this.callback = BlasterCallbackHelper.narrow(rootPOA.servant_to_reference( callbackTie ));
         // necessary for orbacus
         if (this.orb.work_pending()) this.orb.perform_work();
      } catch (Exception e) {
         log.severe("Can't create a BlasterCallback server, narrow failed: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CALLBACKSERVER_CREATION, ME, e.toString());
      }
   }

   /**
    * Shutdown the callback server.
    */
   public void shutdown()
   {
      if (this.callback == null) {
         if (log.isLoggable(Level.FINE)) log.fine("No callback server to shutdown.");
         return;
      }

      if (rootPOA != null && this.callback != null) {
         try {
            callback._release();
            rootPOA.deactivate_object(rootPOA.reference_to_id(callback));
         } catch(Exception e) { log.warning("POA deactivate callback failed"); }
         callback = null;
         if (log.isLoggable(Level.FINE)) log.fine("Doing orb.shutdown()");
         try {
            this.orb.shutdown(false);
         }
         catch (Exception ex) {
            log.warning("shutdown:exception occured destroy(): " + ex.toString());
         }
      }

      this.callback = null;
      log.info("The callback server is shutdown.");
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
         log.warning("Receiving in updateOneway(" + cbSessionId + ") null message");
         return;
      }
      if (log.isLoggable(Level.FINER)) log.finer("Entering updateOneway(" + cbSessionId + ") of " + msgUnitArr.length + " messages");

      try {
         // convert Corba to internal MsgUnitRaw and call update() ...
         MsgUnitRaw[] localMsgUnitRawArr = CorbaDriver.convert(glob, msgUnitArr);
         boss.updateOneway(cbSessionId, localMsgUnitRawArr);
      }
      catch (Throwable e) {
         log.warning("updateOneway() failed in client code, exception is not sent to xmlBlaster: " + e.toString());
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
      if (log.isLoggable(Level.FINER)) log.finer("Entering update(" + cbSessionId + ") of " + msgUnitArr.length + " messages");
      if (log.isLoggable(Level.FINEST)) {
         for (int ii=0; ii< msgUnitArr.length; ii++)
            log.finest("update()\n" + msgUnitArr[ii].xmlKey + "\n" + msgUnitArr[ii].qos);
      }

      try {
         // convert Corba to internal MsgUnitRaw and call update() ...
         MsgUnitRaw[] localMsgUnitRawArr = CorbaDriver.convert(glob, msgUnitArr);
         //log.error(ME, "DEBUG ONLY: " + localMsgUnitRawArr[0].toXml());
         return boss.update(cbSessionId, localMsgUnitRawArr);
      }
      catch(XmlBlasterException e) {
         log.warning("Delivering message to client failed, message is not handled by client: " + e.toString());
         throw CorbaDriver.convert(e); // convert to org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException
      }
      catch (Throwable e) {
         log.warning("Delivering message to client failed, message is not handled by client: " + e.toString());
         e.printStackTrace();
         throw CorbaDriver.convert(new XmlBlasterException(glob, ErrorCode.USER_UPDATE_ERROR, ME,
                                   "Delivering message to client failed, message is not handled by client: " + e.toString()));
      }
   }

   /**
    * Ping to check if the callback server is alive.
    * @see org.xmlBlaster.protocol.I_CallbackDriver#ping(String)
    */
   public String ping(String qos)
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering ping("+qos+") ...");
      return Constants.RET_OK;
   }
} // class CorbaCallbackServer

