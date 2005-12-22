/*------------------------------------------------------------------------------
Name:      AuthenticateImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementing the xmlBlaster interface for xml-rpc.
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.jutils.time.StopWatch;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.ConnectReturnQosServer;
import org.xmlBlaster.engine.qos.DisconnectQosServer;
import org.jutils.text.StringHelper;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;

/**
 * The methods of this class are callable bei XMLRPC clients.
 * <p />
 * void return is not allowed so we return an empty string instead
 * <p />
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public class AuthenticateImpl
{
   private final String ME = "XmlRpc.AuthenticateImpl";
   private final Global glob;
   private LogChannel log;
   private final I_Authenticate authenticate;
   private final AddressServer addressServer;


   /**
    * Constructor.
    */
   public AuthenticateImpl(Global glob, XmlRpcDriver driver, I_Authenticate authenticate)
      throws XmlBlasterException
   {
      this.glob = glob;
      this.log = glob.getLog("xmlrpc");
      if (log.CALL) log.call(ME, "Entering constructor ...");
      this.authenticate = authenticate;
      this.addressServer = driver.getAddressServer();
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
      if (log.CALL) log.call(ME, "Entering login() ...");
      if (log.DUMP) log.dump(ME, qos_literal);

      if (loginName==null || passwd==null || qos_literal==null) {
         log.error(ME+"InvalidArguments", "login failed: please use no null arguments for login()");
         throw new XmlBlasterException("LoginFailed.InvalidArguments", "login failed: please use no null arguments for login()");
      }

      StopWatch stop=null; if (log.TIME) stop = new StopWatch();

      ConnectQosServer connectQos = new ConnectQosServer(glob, qos_literal);
      connectQos.setAddressServer(this.addressServer);
      I_SecurityQos securityQos = connectQos.getSecurityQos();


      if (securityQos == null)
         connectQos.loadClientPlugin(null, null, loginName, passwd);
      else {
         securityQos.setUserId(loginName);
         securityQos.setCredential(passwd);
         if (log.TRACE) log.trace(ME, "login() method uses supplied loginName=" + loginName + " and password");
      }
         

      ConnectReturnQosServer returnQos = authenticate.connect(this.addressServer, connectQos);
      if (log.TIME) log.time(ME, "Elapsed time in login()" + stop.nice());
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
   if (log.CALL) log.call(ME, "Entering logout(sessionId=" + sessionId + ")");
      authenticate.disconnect(this.addressServer, sessionId, (new DisconnectQosServer(glob)).toXml());
      return Constants.RET_OK; // "<qos><state id='OK'/></qos>";
   }

   /**
    * Login to xmlBlaster.
    * @param qos_literal See ConnectQosServer.java
    * @return The xml string from ConnectReturnQos.java<br />
    * @see org.xmlBlaster.engine.qos.ConnectQosServer
    * @see org.xmlBlaster.engine.qos.ConnectReturnQosServer
    */
   public String connect(String qos_literal) throws org.apache.xmlrpc.XmlRpcException
   {
      try {
      String returnValue = null, returnValueStripped = null;
      if (log.CALL) log.call(ME, "Entering connect(qos=" + qos_literal + ")");

      StopWatch stop=null; if (log.TIME) stop = new StopWatch();
      returnValue = authenticate.connect(this.addressServer, qos_literal);

      returnValueStripped = StringHelper.replaceAll(returnValue, "<![CDATA[", "");
      returnValueStripped = StringHelper.replaceAll(returnValueStripped, "]]>", "");
      if (!returnValueStripped.equals(returnValue)) {
         log.trace(ME, "Stripped CDATA tags surrounding security credentials, XMLRPC does not like it (Helma does not escape ']]>'). " +
                        "This shouldn't be a problem as long as your credentials doesn't contain '<'");
      }
      if (log.TIME) log.time(ME, "Elapsed time in connect()" + stop.nice());

      return returnValueStripped;
      }
      catch (XmlBlasterException e) {
         throw new org.apache.xmlrpc.XmlRpcException(99, e.getMessage());
      }
   }

   public String disconnect(final String sessionId, String qos_literal) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering logout()");
      authenticate.disconnect(this.addressServer, sessionId, qos_literal);
      if (log.CALL) log.call(ME, "Exiting logout()");
      return Constants.RET_OK;
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

