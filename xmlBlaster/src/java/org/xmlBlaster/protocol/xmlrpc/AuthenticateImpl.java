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
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.util.DisconnectQos;
import org.jutils.text.StringHelper;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;

/**
 * The methods of this class are callable bei XML-RPC clients.
 * <p />
 * void return is not allowed so we return an empty string instead
 * <p />
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 */
public class AuthenticateImpl
{
   private final String ME = "XmlRpc.AuthenticateImpl";
   private final Global glob;
   private LogChannel log;
   private final I_Authenticate authenticate;


   /**
    * Constructor.
    */
   public AuthenticateImpl(Global glob, I_Authenticate authenticate)
      throws XmlBlasterException
   {
      this.glob = glob;
      this.log = glob.getLog("xmlrpc");
      if (log.CALL) log.call(ME, "Entering constructor ...");
      this.authenticate = authenticate;
   }


   /**
    * Do login to xmlBlaster.
    * @see org.xmlBlaster.authentication.Authenticate#connect(ConnectQos,String)
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

      ConnectQos connectQos = new ConnectQos(glob, qos_literal);
      I_SecurityQos securityQos = connectQos.getSecurityQos();
      if (securityQos == null)
         connectQos.setSecurityPluginData(null, null, loginName, passwd);
      else {
         loginName = securityQos.getUserId();
         passwd = "";
         if (log.TRACE) log.trace(ME, "login() method uses security plugin from qos instead of supplied loginName/password");
      }
         

      ConnectReturnQos returnQos = authenticate.connect(connectQos);
      if (log.TIME) log.time(ME, "Elapsed time in login()" + stop.nice());
      return returnQos.getSessionId();
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
      authenticate.disconnect(sessionId, (new DisconnectQos()).toXml());
      return Constants.RET_OK; // "<qos><state id='OK'/></qos>";
   }

   /**
    * Login to xmlBlaster.
    * @parameter qos_literal See ConnectQos.java
    * @return The xml string from ConnectReturnQos.java<br />
    * @see org.xmlBlaster.util.ConnectQos
    * @see org.xmlBlaster.util.ConnectReturnQos
    */
   public String connect(String qos_literal) throws XmlBlasterException
   {
      String returnValue = null, returnValueStripped = null;
      if (log.CALL) log.call(ME, "Entering connect(qos=" + qos_literal + ")");

      StopWatch stop=null; if (log.TIME) stop = new StopWatch();
      try {
         ConnectQos connectQos = new ConnectQos(glob, qos_literal);
         ConnectReturnQos returnQos = authenticate.connect(connectQos);
         returnValue = returnQos.toXml();

         returnValueStripped = StringHelper.replaceAll(returnValue, "<![CDATA[", "");
         returnValueStripped = StringHelper.replaceAll(returnValueStripped, "]]>", "");
         if (!returnValueStripped.equals(returnValue)) {
            log.trace(ME, "Stripped CDATA tags surrounding security credentials, XML-RPC does not like it (Helma does not escape ']]>'). " +
                           "This shouldn't be a problem as long as your credentials doesn't contain '<'");
         }
         if (log.TIME) log.time(ME, "Elapsed time in connect()" + stop.nice());
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }

      return returnValueStripped;
   }

   public String disconnect(final String sessionId, String qos_literal) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering logout()");
      authenticate.disconnect(sessionId, qos_literal);
      if (log.CALL) log.call(ME, "Exiting logout()");
      return Constants.RET_OK;
   }

   /**
    * Test the xml-rpc connection.
    * @return ""
    */
   public String ping(String qos)
   {
      return "";
   }

   //   public String toXml() throws XmlBlasterException;
   /*
   public String toXml(String extraOffset) throws XmlBlasterException
   {
      return authenticate.toXml(extraOffset);
   }
   */
}

