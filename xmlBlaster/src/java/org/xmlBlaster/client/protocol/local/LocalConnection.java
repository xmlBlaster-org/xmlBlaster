/*------------------------------------------------------------------------------
Name:      LocalConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.local;

import java.io.IOException;
import java.util.Vector;

import org.jutils.text.StringHelper;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.util.protocol.ProtoConverter;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;

import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.engine.xml2java.*;
import org.xmlBlaster.engine.qos.GetQosServer;
import org.xmlBlaster.engine.qos.EraseQosServer;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.protocol.I_XmlBlasterConnection;
import org.xmlBlaster.util.qos.address.Address;

import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;

/**
 * A local connections.
 * <p>This driver may be used to get in vm direct calls into XmlBlaster. Some sutiations this may be used is to write a plgin for the client that gets started in the standalone XmlBlaster, or to be used when XMlBlaster is access in the same embedded anvironment, such as JBoss.</p>
 * <p>There is one very important requirement for this to work: The client
 that gets the XmlBlasterAccess from wich this driver is instantiated MUST
 have access to the serverside engine.Global singleton, it MUST use a cloned Global for each connection that contains the engine.Global in the cloned globals objectEntry at key: ServerNodeScope.</p>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @author <a href="mailto:pra@tim.se">Peter Antman</a>.
 * @see LocalCallbackImpl
 * @see org.xmlBlaster.protocol.local.CallbackLocalDriver
 */
public class LocalConnection implements I_XmlBlasterConnection
{
   private String ME = "LocalConnection";
   private Global glob;
   private LogChannel log;
   private String sessionId;
   protected ConnectReturnQos connectReturnQos;
   protected Address clientAddress;
   private I_Authenticate authenticate;
   private I_XmlBlaster xmlBlasterImpl;

   /**
    * Called by plugin loader which calls init(Global, PluginInfo) thereafter. 
    */
   public LocalConnection() {
   }

   /** Enforced by I_Plugin */
   public String getType() {
      return getProtocol();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return "1.0";
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin).
    * <p>The given global must contain the serverside org.xmlBlaster.engine.Global in its ObjectEntry "ServerNodeScope"</p>
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob_, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) throws XmlBlasterException {
      this.glob = (glob_ == null) ? Global.instance() : glob_;
      this.log = this.glob.getLog("local");
      org.xmlBlaster.engine.Global engineGlob = (org.xmlBlaster.engine.Global)this.glob.getObjectEntry("ServerNodeScope");
      if (engineGlob == null)
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "could not retreive the ServerNodeScope. Am I really on the server side ?");
      try {
         this.authenticate = engineGlob.getAuthenticate();
         if (this.authenticate == null) {
            throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "authenticate object is null");
         }
         this.xmlBlasterImpl = this.authenticate.getXmlBlaster();
         if (xmlBlasterImpl == null) {
            throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "xmlBlasterImpl object is null");
         }
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "init. Could'nt initialize the driver.", ex);
      }
      
      log.info(ME, "Created '" + getProtocol() + "' protocol plugin to connect to xmlBlaster server");
   }

   /**
    * @return The connection protocol name "LOCAL"
    */
   public final String getProtocol()
   {
      return "LOCAL";
   }

   /**
    * @see I_XmlBlasterConnection#connectLowlevel(Address)
    */
   public void connectLowlevel(Address address) throws XmlBlasterException {
      if (log.TRACE) log.trace(ME, "Entering connectLowlevel("+address.getRawAddress()+")");
   }

   public void resetConnection() {
      if (log.TRACE) log.trace(ME, "LocalCLient is initialized, no connection available");
      this.sessionId = null;
   }

   /**
    * Login to the server. 
    * <p />
    * @param connectQos The encrypted connect QoS 
    * @exception XmlBlasterException if login fails
    */
   public String connect(String connectQos) throws XmlBlasterException {
      if (connectQos == null)
         throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME, "Please specify a valid ConnectQoS");

      if (log.CALL) log.call(ME, "Entering login");
      if (isLoggedIn()) {
         log.warn(ME, "You are already logged in, no relogin possible.");
         return "";
      }
      String retQos_literal = this.authenticate.connect(connectQos);

      this.connectReturnQos = new ConnectReturnQos(this.glob, retQos_literal);
      this.sessionId = this.connectReturnQos.getSecretSessionId();

      if (log.TRACE) log.trace(ME, "connect("+this.connectReturnQos.getData().getAddress().getType()+")" + this.connectReturnQos.getSessionName().toString());
      return retQos_literal;
   }

   /**
    * @see I_XmlBlasterConnection#setConnectReturnQos(ConnectReturnQos)
    */
   public void setConnectReturnQos(ConnectReturnQos connectReturnQos) {
      this.sessionId = connectReturnQos.getSecretSessionId();
      this.ME = "LocalConnection-"+connectReturnQos.getSessionName().toString();
   }

   /**
    * Does a logout. 
    * <p />
    * @param sessionId The client sessionId
    */
   public boolean disconnect(String disconnectQos) {
      if (log.CALL) log.call(ME, "Entering logout");

      if (!isLoggedIn()) {
         log.warn(ME, "You are not logged in, no logout possible.");
      }

      try {
         this.authenticate.disconnect(this.sessionId, disconnectQos);
      }
      catch(XmlBlasterException e) {
         log.error(ME, e.getMessage());
      }

      if (log.TRACE) log.trace(ME, "disconnect() done");
      return true;
   }


   /**
    * Shut down. 
    * Is called by logout()
    */
   public void shutdown() throws XmlBlasterException {
   }


   /**
    * @return true if you are logged in
    */
   public boolean isLoggedIn() {
      return this.xmlBlasterImpl != null && this.sessionId != null;
   }

   /**
    * Enforced by I_XmlBlasterConnection interface (failsafe mode).
    * Subscribe to messages.
    * <p />
    */
   public final String subscribe (String xmlKey_literal, String qos_literal) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering subscribe(id=" + this.sessionId + ")");
      return this.xmlBlasterImpl.subscribe(this.sessionId, xmlKey_literal, qos_literal);
   }

   /**
    * Unsubscribe from messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] unSubscribe (String xmlKey_literal,
                                 String qos_literal) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering unsubscribe(): id=" + this.sessionId);
      return this.xmlBlasterImpl.unSubscribe(this.sessionId, xmlKey_literal, qos_literal);
   }

   /**
    * Publish a message.
    */
   public final String publish(MsgUnitRaw msgUnit) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering publish(): id=" + this.sessionId);
      return this.xmlBlasterImpl.publish(this.sessionId, msgUnit);
   }

   /**
    * Publish multiple messages in one sweep.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] publishArr(MsgUnitRaw[] msgUnitArr) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering publishArr: id=" + this.sessionId);
      if (msgUnitArr == null) {
         log.error(ME + ".InvalidArguments", "The argument of method publishArr() are invalid");
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME,
                                       "The argument of method publishArr() are invalid");
      }
      return this.xmlBlasterImpl.publishArr(this.sessionId, msgUnitArr);
   }

   /**
    * Publish multiple messages in one sweep.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final void publishOneway(MsgUnitRaw[] msgUnitArr) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering publishOneway: id=" + this.sessionId);

      if (msgUnitArr == null) {
         log.error(ME + ".InvalidArguments", "The argument of method publishOneway() are invalid");
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME,
                                       "The argument of method publishOneway() are invalid");
      }

      this.xmlBlasterImpl.publishOneway(this.sessionId, msgUnitArr);
   }

   /**
    * Delete messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] erase(String xmlKey_literal, String qos_literal) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering erase() id=" + this.sessionId);
      return this.xmlBlasterImpl.erase(this.sessionId, xmlKey_literal, qos_literal);
   }

   /**
    * Synchronous access a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final MsgUnitRaw[] get(String xmlKey_literal,
                                  String qos_literal) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering get() xmlKey=" + xmlKey_literal.trim() + ") ...");
      return this.xmlBlasterImpl.get(this.sessionId, xmlKey_literal, qos_literal);
   }

   /**
    * Check server.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String ping(String str) throws XmlBlasterException {
      return "";
   }

   public String toXml() throws XmlBlasterException {
      return toXml("");
   }

   /**
    * Dump of the server, remove in future.
    */
   public String toXml(String extraOffset) throws XmlBlasterException
   {
      if (!isLoggedIn()) return "<noConnection />";
      return "<LocalConnection/>";
   }

   /**
    * Command line usage.
    * <p />
    * These variables may be set in xmlBlaster.properties as well.
    * Don't use the "-" prefix there.
    */
   public static String usage()
   {
      String text = "\n";
      text += "LocalConnection 'LOCAL' options:\n";
      text += "   -plugin/local/debug\n";
      text += "                       true switches on detailed LOCAL debugging [false].\n";
      text += "\n";
      return text;
   }
}

