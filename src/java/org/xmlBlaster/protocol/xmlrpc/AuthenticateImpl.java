/*------------------------------------------------------------------------------
Name:      AuthenticateImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementing the xmlBlaster interface for xml-rpc.
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.ConnectReturnQosServer;
import org.xmlBlaster.engine.qos.DisconnectQosServer;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;

/**
 * The methods of this class are callable bei XMLRPC clients.
 * <p />
 * void return is not allowed so we return an empty string instead
 * <p />
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public class AuthenticateImpl {
   private final Global glob;
   private static Logger log = Logger.getLogger(AuthenticateImpl.class.getName());
   private final I_Authenticate authenticate;
   private final AddressServer addressServer;
   private XmlBlasterImpl xblImpl;

   /**
    * Constructor.
    */
   public AuthenticateImpl(Global glob, XmlRpcDriver driver, I_Authenticate authenticate, XmlBlasterImpl xblImpl) throws XmlBlasterException {
      this.glob = glob;
      if (log.isLoggable(Level.FINER)) log.finer("Entering constructor ...");
      this.authenticate = authenticate;
      this.addressServer = driver.getAddressServer();
      this.xblImpl = xblImpl;
   }


   /**
    * Do login to xmlBlaster.
    * @see org.xmlBlaster.authentication.Authenticate#connect(ConnectQosServer,String)
    * @deprecated Use connect() instead
    * @return The secret sessionId as a raw string
    */
   public String login(String loginName, String passwd,
                       String qos_literal, String sessionId)
                          throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering login() ...");
      if (log.isLoggable(Level.FINEST)) log.finest(qos_literal);

      if (loginName==null || passwd==null || qos_literal==null) {
         log.severe("login failed: please use no null arguments for login()");
         throw new XmlBlasterException("LoginFailed.InvalidArguments", "login failed: please use no null arguments for login()");
      }

      ConnectQosServer connectQos = new ConnectQosServer(glob, qos_literal);
      connectQos.setAddressServer(this.addressServer);
      I_SecurityQos securityQos = connectQos.getSecurityQos();


      if (securityQos == null)
         connectQos.loadClientPlugin(null, null, loginName, passwd);
      else {
         securityQos.setUserId(loginName);
         securityQos.setCredential(passwd);
         if (log.isLoggable(Level.FINE)) log.fine("login() method uses supplied loginName=" + loginName + " and password");
      }
         

      ConnectReturnQosServer returnQos = authenticate.connect(connectQos);
      return returnQos.getSecretSessionId();
   }


   /**
    * Logout of a client.
    * <p />
    * void return is not allowed so we return an empty string instead
    * <p>
    * @exception XmlBlasterException If client is unknown
    * @deprecated Use disconnect() instead
    */
   public String logout(String sessionId) throws XmlBlasterException
   {
   if (log.isLoggable(Level.FINER)) log.finer("Entering logout(sessionId=" + sessionId + ")");
      authenticate.disconnect(this.addressServer, sessionId, (new DisconnectQosServer(glob)).toXml());
      return Constants.RET_OK; // "<qos><state id='OK'/></qos>";
   }

   private final String extractSessionIdFromQos(String literal) {
      if (literal == null)
         return null;
      final String token = "sessionId=";
      final int length = token.length() + 1;
      int pos = literal.indexOf(token);
      if (pos < 0)
         return null;
      String tmp = literal.substring(pos + length);
      pos = tmp.indexOf('\'');
      if (pos < 0)
         return null;
      return tmp.substring(0, pos);
   }
   
   /**
    * Login to xmlBlaster.
    * @param qos_literal See ConnectQosServer.java
    * @return The xml string from ConnectReturnQos.java<br />
    * @see org.xmlBlaster.engine.qos.ConnectQosServer
    * @see org.xmlBlaster.engine.qos.ConnectReturnQosServer
    */
   private String connectInternal(String sessionId, String qos_literal, boolean singleChannel) throws org.apache.xmlrpc.XmlRpcException {
      try {
         String returnValue = null, returnValueStripped = null;
         if (log.isLoggable(Level.FINER)) 
            log.finer("Entering connect(qos=" + qos_literal + ")");

         returnValue = authenticate.connect(addressServer, qos_literal, sessionId);

         returnValueStripped = ReplaceVariable.replaceAll(returnValue, "<![CDATA[", "");
         returnValueStripped = ReplaceVariable.replaceAll(returnValueStripped, "]]>", "");
         if (!returnValueStripped.equals(returnValue)) {
            log.fine("Stripped CDATA tags surrounding security credentials, XMLRPC does not like it (Helma does not escape ']]>'). " +
                        "This shouldn't be a problem as long as your credentials doesn't contain '<'");
         }
      
         sessionId = extractSessionIdFromQos(returnValueStripped);
         boolean useCDATA = addressServer.getEnv("useCDATA", false).getValue();
         xblImpl.registerSessionId(sessionId, singleChannel, useCDATA);
         return returnValueStripped;
      }
      catch (XmlBlasterException e) {
         throw new org.apache.xmlrpc.XmlRpcException(99, e.getMessage(), e);
      }
   }

   /**
    * Login to xmlBlaster. This is the old connect method
    * @param qos_literal See ConnectQosServer.java
    * @return The xml string from ConnectReturnQos.java<br />
    * @see org.xmlBlaster.engine.qos.ConnectQosServer
    * @see org.xmlBlaster.engine.qos.ConnectReturnQosServer
    */
   public String connect(String qos_literal) throws org.apache.xmlrpc.XmlRpcException {
      return connectInternal(null, qos_literal, false);
   }

   /**
    * Login to xmlBlaster.
    * @param qos_literal See ConnectQosServer.java
    * @return The xml string from ConnectReturnQos.java<br />
    * @see org.xmlBlaster.engine.qos.ConnectQosServer
    * @see org.xmlBlaster.engine.qos.ConnectReturnQosServer
    */
   public String connect(String sessionId, String qos_literal) throws org.apache.xmlrpc.XmlRpcException {
      return connectInternal(sessionId, qos_literal, false);
   }

   /**
    * Login to xmlBlaster.
    * @param qos_literal See ConnectQosServer.java
    * @return The xml string from ConnectReturnQos.java<br />
    * @see org.xmlBlaster.engine.qos.ConnectQosServer
    * @see org.xmlBlaster.engine.qos.ConnectReturnQosServer
    */
   public String connectSingleChannel(String sessionId, String qos_literal) throws org.apache.xmlrpc.XmlRpcException  {
      return connectInternal(sessionId, qos_literal, true);
   }

   public String disconnect(final String sessionId, String qos_literal) throws org.apache.xmlrpc.XmlRpcException  {
      if (log.isLoggable(Level.FINER)) log.finer("Entering logout()");
      try {
         xblImpl.interrupt(sessionId);
         authenticate.disconnect(this.addressServer, sessionId, qos_literal);
         if (log.isLoggable(Level.FINER)) log.finer("Exiting logout()");
         return Constants.RET_OK;
      }
      catch (XmlBlasterException e) {
         throw new org.apache.xmlrpc.XmlRpcException(99, e.getMessage(), e);
      }
   }

   /**
    * Test the xml-rpc connection and xmlBlaster availability. 
    * @see org.xmlBlaster.protocol.I_XmlBlaster#ping(String)
    */
   public String ping(String qos)
   {
      return authenticate.ping(this.addressServer, qos);
   }

   //   public String toXml() throws XmlBlasterException;
   /*
   public String toXml(String extraOffset) throws XmlBlasterException
   {
      return authenticate.toXml(extraOffset);
   }
   */
   
}

