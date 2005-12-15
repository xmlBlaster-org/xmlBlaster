/*------------------------------------------------------------------------------
Name:      EmailDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   EmailDriver class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: EmailDriver.java 13924 2005-11-02 22:07:52Z ruff $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.email;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.authentication.ClientEvent;
import org.xmlBlaster.authentication.I_ClientListener;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.ConnectReturnQosServer;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.protocol.email.EmailExecutor;
import org.xmlBlaster.util.protocol.socket.SocketUrl;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.xbformat.MsgInfo;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Email driver class to invoke the xmlBlaster server over ordinary emails. 
 * <p />
 * This "email:" driver needs to be registered in xmlBlasterPlugins.xml
 * and will be started on xmlBlaster startup by the runlevel manager as configured.
 * <p />
 * The interface I_Driver is needed by xmlBlaster to instantiate and shutdown
 * this driver implementation.
 * <p />
 * All adjustable parameters are explained in {@link org.xmlBlaster.protocol.email.EmailDriver#usage()}
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 *
 * @see org.xmlBlaster.util.xbformat.MsgInfo
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.email.html">The protocol.email requirement</a>
 */
public class EmailDriver extends EmailExecutor implements I_Driver, I_ClientListener /* which extends I_Plugin */
{
   private static Logger log = Logger.getLogger(EmailDriver.class.getName());
   private String ME = "";
   private Global glob;
   /** The singleton handle for this authentication server */
   private I_Authenticate authenticate;
   /** The singleton handle for this xmlBlaster server */
   private I_XmlBlaster xmlBlasterImpl;

   /** The address configuration */
   private AddressServer addressServer;

   private PluginInfo pluginInfo;

   /**
    * Creates the driver.
    * Note: getName() is enforced by interface I_Driver, but is already defined in Thread class
    */
   public EmailDriver() {
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver.
    * @return The configured [type] in xmlBlaster.properties, defaults to "email"
    */
   public String getProtocolId() {
      return (this.pluginInfo == null) ? "email" : this.pluginInfo.getType();
   }

   /**
    * Enforced by I_Plugin
    * @return The configured type in xmlBlaster.properties, defaults to "email"
    */
   public String getType() {
      return getProtocolId();
   }

   /**
    * The command line key prefix
    * @return The configured type in xmlBlasterPlugins.xml, defaults to "plugin/email"
    */
   public String getEnvPrefix() {
      return (addressServer != null) ? addressServer.getEnvPrefix() : "plugin/"+getType().toLowerCase();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return (this.pluginInfo == null) ? "1.0" : this.pluginInfo.getVersion();
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin).
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo)
      throws XmlBlasterException {
      this.pluginInfo = pluginInfo;
      this.glob = glob;
      org.xmlBlaster.engine.Global engineGlob = (org.xmlBlaster.engine.Global)glob.getObjectEntry("ServerNodeScope");
      if (engineGlob == null)
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "could not retreive the ServerNodeScope. Am I really on the server side ?");

      // For JMX instanceName may not contain ","
      super.contextNode = new ContextNode(ContextNode.SERVICE_MARKER_TAG,
            "EmailDriver[" + getType() + "]",
            glob.getContextNode());
      super.mbeanHandle = this.glob.registerMBean(super.contextNode, this);
      
      engineGlob.getRequestBroker().getAuthenticate(null).addClientListener(this);

      try {
         this.authenticate = engineGlob.getAuthenticate();
         if (this.authenticate == null) {
            throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "authenticate object is null");
         }
         I_XmlBlaster xmlBlasterImpl = this.authenticate.getXmlBlaster();
         if (xmlBlasterImpl == null) {
            throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "xmlBlasterImpl object is null");
         }

         init(glob, new AddressServer(glob, getType(), glob.getId(), pluginInfo.getParameters()), this.authenticate, xmlBlasterImpl);

         activate();
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "init. Could'nt initialize the driver.", ex);
      }
   }

   /**
    * Get the address how to access this driver.
    * @return "server.mars.univers:6701"
    */
   public String getRawAddress() {
      return (this.addressServer == null) ? "" : this.addressServer.getRawAddress(); 
   }

   /**
    * Access the handle to the xmlBlaster authenication core
    */
   I_Authenticate getAuthenticate() {
      return this.authenticate;
   }

   /**
    * Access the handle to the xmlBlaster core
    */
   I_XmlBlaster getXmlBlaster() {
      return this.xmlBlasterImpl;
   }

   AddressServer getAddressServer() {
      return this.addressServer;
   }

   /**
    * Start xmlBlaster EMAIL access.
    * <p />
    * Enforced by interface I_Driver.<br />
    * This method returns as soon as the listener email is alive and ready or on error.
    * @param glob Global handle to access logging, property and commandline args
    * @param authenticate Handle to access authentication server
    * @param xmlBlasterImpl Handle to access xmlBlaster core
    */
   private synchronized void init(Global glob, AddressServer addressServer, I_Authenticate authenticate, I_XmlBlaster xmlBlasterImpl)
      throws XmlBlasterException
   {
      this.glob = glob;
      this.ME = "EmailDriver" + this.glob.getLogPrefixDashed();
      if (log.isLoggable(Level.FINEST)) log.finest("Entering init()");
      this.addressServer = addressServer;
      if (this.pluginInfo != null)
         this.addressServer.setPluginInfoParameters(this.pluginInfo.getParameters());
      this.authenticate = authenticate;
      this.xmlBlasterImpl = xmlBlasterImpl;

      if (Constants.COMPRESS_ZLIB_STREAM.equals(this.addressServer.getCompressType())) {
         log.info("Full stream compression enabled with '" + Constants.COMPRESS_ZLIB_STREAM + "' for " + getType());
      }
      else if (Constants.COMPRESS_ZLIB.equals(this.addressServer.getCompressType())) {
         log.info("Message compression enabled with  '" + Constants.COMPRESS_ZLIB + "', minimum size for compression is " + this.addressServer.getMinSize() + " bytes for " + getType());
      }
      
      // POP3 poller, SMTP access etc
      super.setXmlBlasterCore(xmlBlasterImpl);
      super.init(glob, this.addressServer, this.pluginInfo);
      
      // TODO: Handle CONNECT and DISCONNECT separately
   }

   /**
    * Activate xmlBlaster access through this protocol.
    */
   public synchronized void activate() throws XmlBlasterException {
      if (log.isLoggable(Level.FINE)) log.fine("Entering activate()");
      // Register under my cluster node id 'heron'
      String key = this.glob.getId();
      getPop3Driver().registerForEmail(key, null, this);
      // Usually the pop3Driver is set to <attribute id='activate'>false</attribute> on
      // startup to not loose any messages until we have registered
      //try { getPop3Driver().activate(); } catch (Exception e) { throw (XmlBlasterException)e; }
      // this is now solved by holdbackExpireTimeout
      log.info("Initialized email listener with key '" + key + "' and email address '" + super.fromAddress.toString() + "'");
   }

   /*
    * Notification by Pop3Driver when a (response) message arrives. Enforced by
    * I_ResponseListener
   public void responseEvent(String requestId, Object response) {
      EmailData messageData = (EmailData) response;
   }
    */

   /**
    * Overwrites EmailExecutor as we are a singleton handling different clients.  
    * @param msgInfo
    * @return
    */
   public String getEmailSessionId(MsgInfo msgInfo) {
      if (msgInfo.getSecretSessionId() != null && msgInfo.getSecretSessionId().length() > 0) {
         // We send the secretSessionId in the SUBJECT of a ConnectReturnQos
         return msgInfo.getSecretSessionId();
      }
      return getEmailSessionId();
   }
   
   /**
    * Handle connect/disconnect
    */
   public final boolean receiveReply(MsgInfo receiver, boolean udp) throws XmlBlasterException, IOException {
      try {
         if (log.isLoggable(Level.FINE)) log.fine("Receiving message " + receiver.getMethodName() + "(" + receiver.getRequestId() + ")");
         receiver.setBounceObject(BOUNCE_MAILTO_KEY, receiver.getBounceObject(BOUNCE_MAILFROM_KEY));
         
         // super.receive() processes all invocations, only connect()/disconnect() we do locally ...
         if (super.receiveReply(receiver, udp) == false) {
            if (MethodName.CONNECT == receiver.getMethodName()) {
               ConnectQosServer conQos = new ConnectQosServer(this.glob, receiver.getQos());
               conQos.setAddressServer(getAddressServer());
               //setLoginName(conQos.getUserId());
               ConnectReturnQosServer retQos = this.authenticate.connect(getAddressServer(), conQos);
               //As we are a singleton there is no need to remember the secretSessionId of this client
               //super.setSecretSessionId(retQos.getSecretSessionId());
               receiver.setSecretSessionId(retQos.getSecretSessionId()); // executeResponse needs it
               executeResponse(receiver, retQos.toXml(), SocketUrl.SOCKET_TCP);
             }
            else if (MethodName.DISCONNECT == receiver.getMethodName()) {
               executeResponse(receiver, Constants.RET_OK, SocketUrl.SOCKET_TCP);   // ACK the disconnect to the client and then proceed to the server core
               // Note: the disconnect will call over the CbInfo our shutdown as well
               // setting sessionId = null prevents that our shutdown calls disconnect() again.
               this.authenticate.disconnect(getAddressServer(), receiver.getSecretSessionId(), receiver.getQos());
            }
         }
      }
      catch (XmlBlasterException e) {
         log.fine("Can't handle message, throwing exception back to client: " + e.toString());
         try {
            if (receiver.getMethodName() != MethodName.PUBLISH_ONEWAY)
               executeException(receiver, e, false);
            else
               log.warning("Can't handle publishOneway message, ignoring exception: " + e.toString());
         }
         catch (Throwable e2) {
            log.severe("Lost connection, can't deliver exception message: " + e.toString() + " Reason is: " + e2.toString());
         }
      }
      catch (IOException e) {
         log.warning("Lost connection to client: " + e.toString());
      }
      catch (Throwable e) {
         e.printStackTrace();
         log.severe("Lost connection to client: " + e.toString());
      }
      return true;
   }

   

   /**
    * Deactivate xmlBlaster access (standby), no clients can connect.
    */
   public synchronized void deActivate() throws XmlBlasterException {
      if (log.isLoggable(Level.FINE)) log.fine("Entering deActivate()");
      getPop3Driver().deregisterForEmail(this.glob.getId(), null);
   }

   /**
    * Close the listener port, the driver shuts down.
    */
   public void shutdown() {
      log.fine("Entering shutdown");
      try {
         deActivate();
      } catch (XmlBlasterException e) {
         log.warning(e.toString());
      }
      this.glob.unregisterMBean(this.mbeanHandle);
      log.info("Email driver '" + getType() + "' stopped, all resources released.");
   }

   /**
    * Command line usage.
    * <p />
    * <ul>
    *  <li><i>-dump[email]</i>       true switches on detailed 'email' debugging [false]</li>
    * </ul>
    * <p />
    * Enforced by interface I_Driver.
    */
   public String usage()
   {
      String text = "\n";
      text += "EmailDriver options:\n";
      text += "   -"+getEnvPrefix()+"mail.pop3.url\n";
      text += "                       The EMAIL POP3 connection URL 'pop3://user:password@host:port/INBOX' [-].\n";
      text += "   -"+getEnvPrefix()+"pop3PollingInterval\n";
      text += "                       How often to check for new emails in milli seconds [1000].\n";
      text += "   -"+getEnvPrefix()+"responseTimeout\n";
      text += "                       Max wait for the method return value/exception in msec.\n";
      text += "                       The default is " +getDefaultResponseTimeout() + ".\n";
      text += "   -"+getEnvPrefix()+"compress/type\n";
      text += "                       Valid values are: '', '"+Constants.COMPRESS_ZLIB_STREAM+"', '"+Constants.COMPRESS_ZLIB+"' [].\n";
      text += "                       '' disables compression, '"+Constants.COMPRESS_ZLIB_STREAM+"' compresses whole stream.\n";
      text += "                       '"+Constants.COMPRESS_ZLIB+"' only compresses flushed chunks bigger than 'compress/minSize' bytes.\n";
      text += "   -"+getEnvPrefix()+"compress/minSize\n";
      text += "                       Compress message bigger than given bytes, see above.\n";
      text += "   -dump[email]       true switches on detailed "+getType()+" debugging [false].\n";
      text += "\n";
      return text;
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.protocol.I_Driver#getName()
    */
   public String getName() {
      return "EmailDriver";
   }
   
   /**
    * Implements I_ClientListener
    */
   public void sessionAdded(ClientEvent e) {}
   public void subjectAdded(ClientEvent e) {}
   public void sessionPreRemoved(ClientEvent e) {
      CallbackAddress[] cba = e.getConnectQos().getSessionCbQueueProperty().getCallbackAddresses();
      for (int i=0; i<cba.length; i++) {
         EmailExecutor.LoopProtection l = removeFromLoopProtection(cba[i].getRawAddress());
         if (l != null)
            log.severe("DEBUG ONLY: " + l.key);
      }
   }
   public void sessionRemoved(ClientEvent e) {}
   public void subjectRemoved(ClientEvent e) {}
}
