/*------------------------------------------------------------------------------
Name:      AuthenticateImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementing the xmlBlaster interface for xml-rpc.
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

import org.xmlBlaster.util.Log;
import org.jutils.time.StopWatch;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.engine.xml2java.LoginReturnQoS;
import org.xmlBlaster.engine.xml2java.LoginReturnQoS;
import org.xmlBlaster.client.LogoutQosWrapper;


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
   private I_Authenticate authenticate;


   /**
    * Constructor.
    */
   public AuthenticateImpl(I_Authenticate authenticate)
      throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering constructor ...");
      this.authenticate = authenticate;
   }


   /**
    * Do login to xmlBlaster.
    * @see org.xmlBlaster.authentication.Authenticate.login()
    */
   public String login(String loginName, String passwd,
                       String qos_literal, String sessionId)
                          throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering login() ...");
      if (Log.DUMP) Log.dump(ME, qos_literal);
 
      if (loginName==null || passwd==null || qos_literal==null) {
         Log.error(ME+"InvalidArguments", "login failed: please use no null arguments for login()");
         throw new XmlBlasterException("LoginFailed.InvalidArguments", "login failed: please use no null arguments for login()");
      }

      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();

      ConnectQos connectQos = new ConnectQos(qos_literal);
      connectQos.setSecurityPluginData("simple", "1.0", loginName, passwd);

      LoginReturnQoS returnQos = authenticate.connect(connectQos);
      if (Log.TIME) Log.time(ME, "Elapsed time in login()" + stop.nice());
      return returnQos.getSessionId();
   }


   /**
    * Logout of a client.
    * <p />
    * void return is not allowed so we return an empty string instead
    * <p>
    * @exception XmlBlasterException If client is unknown
    */
   public String logout(String sessionId) throws XmlBlasterException
   {
      authenticate.disconnect(sessionId, (new LogoutQosWrapper()).toXml());
      return "";
   }

   /**
    * Login to xmlBlaster.
    * @parameter qos_literal See LoginQosWrapper.java
    * @return The xml string from LoginReturnQoS.java<br />
    * @see org.xmlBlaster.client.LoginQosWrapper
    * @see org.xmlBlaster.engine.xml2java.LoginReturnQoS
    */
   public String connect(String qos_literal) throws XmlBlasterException
   {
      String returnValue = null;
      if (Log.CALL) Log.call(ME, "Entering connect(qos=" + qos_literal + ")");

      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();
      try {
         ConnectQos connectQos = new ConnectQos(qos_literal);
         LoginReturnQoS qos = authenticate.connect(connectQos);
         returnValue = qos.toXml();
         if (Log.TIME) Log.time(ME, "Elapsed time in connect()" + stop.nice());
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }

      return returnValue;
   }

   public void disconnect(final String sessionId, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering logout()");
      authenticate.disconnect(sessionId, qos_literal);
      if (Log.CALL) Log.call(ME, "Exiting logout()");
   }

   /**
    * Test the xml-rpc connection.
    * @return 1
    */
   public int ping() throws XmlBlasterException
   {
      return 1;
   }

   //   public String toXml() throws XmlBlasterException;
   /*
   public String toXml(String extraOffset) throws XmlBlasterException
   {
      return authenticate.toXml(extraOffset);
   }
   */
}

